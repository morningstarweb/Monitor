use super::GitExecutor;
use std::path::{Path, PathBuf};

impl GitExecutor {
    /// Attempts to resolve a Scoop shim path to the actual Git binary.
    ///
    /// Scoop shims (e.g. `scoop\shims\git.exe`) are small wrapper executables that fail
    /// when launched without an attached console or with `CREATE_NO_WINDOW` flags (such as
    /// inside GUI / tray applications), returning errors like:
    /// `Shim: Could not determine if target is a GUI app...`.
    ///
    /// This function inspects the sibling `.shim` file or navigates to the underlying
    /// `apps\git\current\cmd\git.exe` or `apps\git\current\bin\git.exe` binary.
    pub fn resolve_scoop_shim(shim_path: &Path) -> Option<PathBuf> {
        // 1. Try parsing the companion .shim file (e.g. git.shim)
        let shim_meta = shim_path.with_extension("shim");
        if shim_meta.is_file()
            && let Ok(content) = std::fs::read_to_string(&shim_meta)
        {
            for line in content.lines() {
                let trimmed = line.trim();
                let target_opt = trimmed
                    .strip_prefix("path = ")
                    .or_else(|| trimmed.strip_prefix("path="));
                if let Some(target) = target_opt {
                    let cleaned = target.trim().trim_matches('"').trim_matches('\'');
                    let target_path = PathBuf::from(cleaned);
                    let resolved = if target_path.is_relative() {
                        shim_path
                            .parent()
                            .map(|p| p.join(&target_path))
                            .unwrap_or(target_path)
                    } else {
                        target_path
                    };
                    if resolved.exists() {
                        let cmd_sibling = resolved
                            .parent()
                            .and_then(|p| p.parent())
                            .map(|p| p.join(r"cmd\git.exe"));
                        if let Some(ref cmd_path) = cmd_sibling
                            && cmd_path.exists()
                        {
                            return Some(cmd_path.clone());
                        }
                        return Some(resolved);
                    }
                }
            }
        }

        // 2. Direct folder traversal: scoop\shims\git.exe -> scoop\apps\git\current\(cmd|bin)\git.exe
        if let Some(parent) = shim_path.parent() {
            let is_shims_dir = parent
                .file_name()
                .and_then(|n| n.to_str())
                .is_some_and(|n| n.eq_ignore_ascii_case("shims"));
            if is_shims_dir && let Some(scoop_root) = parent.parent() {
                let candidates = [
                    scoop_root.join(r"apps\git\current\cmd\git.exe"),
                    scoop_root.join(r"apps\git\current\bin\git.exe"),
                    scoop_root.join(r"apps\git\current\mingw64\bin\git.exe"),
                ];
                for candidate in candidates {
                    if candidate.exists() {
                        return Some(candidate);
                    }
                }
            }
        }

        // 3. Environment-based Scoop apps search
        for env_key in &["USERPROFILE", "HOME", "LOCALAPPDATA"] {
            if let Ok(val) = std::env::var(env_key) {
                let user_root = PathBuf::from(val);
                for sub in &[
                    r"scoop\apps\git\current\cmd\git.exe",
                    r"scoop\apps\git\current\bin\git.exe",
                    r"scoop\apps\git\current\mingw64\bin\git.exe",
                ] {
                    let candidate = user_root.join(sub);
                    if candidate.exists() {
                        return Some(candidate);
                    }
                }
            }
        }

        None
    }

    /// Determines if a given executable path appears to be a Scoop or similar shim.
    pub fn is_shim_path(path: &Path) -> bool {
        if path.with_extension("shim").is_file() {
            return true;
        }
        if let Some(parent) = path.parent()
            && let Some(name) = parent.file_name().and_then(|n| n.to_str())
            && name.eq_ignore_ascii_case("shims")
        {
            return true;
        }
        let s = path.to_string_lossy().to_lowercase();
        s.contains(r"\shims\")
            || s.contains("/shims/")
            || s.ends_with(r"\shims")
            || s.ends_with("/shims")
    }
}
