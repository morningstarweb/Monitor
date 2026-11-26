use monitor_core::CoreError;
use std::path::{Component, Path, PathBuf};

/// Validates that a user-provided path is safe and strictly relative to the repository root.
pub fn validate_repo_relative_path(raw_path: &str) -> Result<PathBuf, CoreError> {
    if raw_path.is_empty() || raw_path.chars().any(char::is_control) {
        return Err(CoreError::InvalidArgument(
            "Path contains prohibited null byte".to_string(),
        ));
    }

    // Prohibit leading slashes or Windows drive specifiers
    if raw_path.starts_with('/') || raw_path.starts_with('\\') {
        return Err(CoreError::InvalidArgument(
            "Path must be relative, leading slash is prohibited".to_string(),
        ));
    }

    if raw_path.contains(':') {
        return Err(CoreError::InvalidArgument(
            "Path must not contain drive specifiers or namespaces".to_string(),
        ));
    }

    let p = Path::new(raw_path);
    if p.components().all(|part| part == Component::CurDir) {
        return Err(CoreError::InvalidArgument(
            "Select a file inside the repository".to_string(),
        ));
    }

    // Check components
    for comp in p.components() {
        match comp {
            Component::ParentDir => {
                return Err(CoreError::InvalidArgument(
                    "Path traversal ('..') is strictly prohibited".to_string(),
                ));
            }
            Component::Prefix(_) | Component::RootDir => {
                return Err(CoreError::InvalidArgument(
                    "Absolute or rooted path components are prohibited".to_string(),
                ));
            }
            Component::Normal(os_str) => {
                let s = os_str.to_string_lossy();
                if s.ends_with(['.', ' ']) {
                    return Err(CoreError::InvalidArgument(
                        "Windows path aliases are prohibited".to_string(),
                    ));
                }
                let stem = s.split('.').next().unwrap_or_default().to_ascii_lowercase();
                if matches!(stem.as_str(), "con" | "prn" | "aux" | "nul")
                    || (stem.len() == 4
                        && (stem.starts_with("com") || stem.starts_with("lpt"))
                        && matches!(stem.as_bytes()[3], b'1'..=b'9'))
                {
                    return Err(CoreError::InvalidArgument(
                        "Windows device paths are prohibited".to_string(),
                    ));
                }
                // Prohibit .git administrative directories and 8.3 short aliases
                if s.eq_ignore_ascii_case(".git") || s.eq_ignore_ascii_case("git~1") {
                    return Err(CoreError::InvalidArgument(
                        "Access to internal .git administrative directory is prohibited"
                            .to_string(),
                    ));
                }
            }
            Component::CurDir => {}
        }
    }

    Ok(p.to_path_buf())
}

/// Resolves an existing relative path against the canonical repository root,
/// proving that the target does not escape via NTFS junctions or symlinks.
pub fn resolve_safe_target_path(
    repo_root: &Path,
    relative_path: &str,
) -> Result<PathBuf, CoreError> {
    let clean_rel = validate_repo_relative_path(relative_path)?;
    let joined = repo_root.join(clean_rel);

    // Check the nearest existing ancestor even when several new folders are absent.
    let existing = joined
        .ancestors()
        .find(|path| path.symlink_metadata().is_ok())
        .ok_or_else(|| {
            CoreError::InvalidArgument("Repository target has no existing ancestor".to_string())
        })?;
    let canonical_target = existing
        .canonicalize()
        .map_err(|e| CoreError::Internal(format!("Failed to canonicalize path: {e}")))?;
    let canonical_root = repo_root
        .canonicalize()
        .map_err(|e| CoreError::Internal(format!("Failed to canonicalize root: {e}")))?;

    if !canonical_target.starts_with(&canonical_root) {
        return Err(CoreError::InvalidArgument(
            "Symlink or junction escapes canonical repository root".to_string(),
        ));
    }

    if canonical_target != canonical_root {
        let relative = canonical_target
            .strip_prefix(&canonical_root)
            .map_err(|e| CoreError::InvalidArgument(format!("Invalid target boundary: {e}")))?;
        validate_repo_relative_path(&relative.to_string_lossy())?;
    }
    Ok(joined)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_valid_paths() {
        assert!(validate_repo_relative_path("src/main.rs").is_ok());
        assert!(validate_repo_relative_path("docs/readme.md").is_ok());
        assert!(validate_repo_relative_path("components/Button.tsx").is_ok());
    }

    #[test]
    fn test_windows_device_aliases_and_trailing_aliases_are_rejected() {
        for path in [
            "COM9.log",
            "lPt1",
            "aux",
            "PRN.txt",
            "nul.txt",
            "file.",
            "dir /file.txt",
            "file.txt ",
            ".GIT/config",
            "Git~1/config",
            "dir/.git./HEAD",
            "file\u{0007}name",
        ] {
            assert!(
                validate_repo_relative_path(path).is_err(),
                "{path:?} must be rejected"
            );
        }
    }

    #[test]
    fn test_dot_components_and_unicode_names_are_accepted() {
        assert!(validate_repo_relative_path("./src/./main.rs").is_ok());
        assert!(validate_repo_relative_path("docs/café/notes.md").is_ok());
        assert!(validate_repo_relative_path("a/b/c.txt").is_ok());
        assert!(validate_repo_relative_path("src/../secret.txt").is_err());
    }

    #[cfg(windows)]
    #[test]
    fn test_windows_backslash_traversal_is_rejected() {
        for path in [
            "src\\..\\secret.txt",
            "..\\..\\outside",
            "src\\.\\..\\secret",
            ".git\\config",
        ] {
            assert!(
                validate_repo_relative_path(path).is_err(),
                "{path:?} must be rejected"
            );
        }
    }

    #[test]
    fn test_prohibited_traversals() {
        assert!(validate_repo_relative_path("../secret.txt").is_err());
        assert!(validate_repo_relative_path("src/../../secret.txt").is_err());
        assert!(validate_repo_relative_path("C:\\Windows\\System32").is_err());
        assert!(validate_repo_relative_path("/etc/passwd").is_err());
        assert!(validate_repo_relative_path("\\\\server\\share\\file").is_err());
        assert!(validate_repo_relative_path(".git/config").is_err());
        assert!(validate_repo_relative_path(".git/HEAD").is_err());
        assert!(validate_repo_relative_path("GIT~1/config").is_err());
        assert!(validate_repo_relative_path("file\0null").is_err());
        assert!(validate_repo_relative_path("file.txt:stream").is_err());
        assert!(validate_repo_relative_path("file.txt::$DATA").is_err());
        for path in [
            "",
            ".",
            "./",
            ".git./config",
            "dir/NUL.txt",
            "COM1",
            "file\nname",
        ] {
            assert!(validate_repo_relative_path(path).is_err(), "{path:?}");
        }
    }
}
