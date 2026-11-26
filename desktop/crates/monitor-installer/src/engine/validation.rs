use std::fs::OpenOptions;
use std::path::Path;
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct PathValidationResult {
    pub error: Option<String>,
    pub warning: Option<String>,
}

/// Returns `true` if `path` exists as a symbolic link or Windows junction/reparse point.
pub fn is_reparse_or_symlink(path: &Path) -> bool {
    let Ok(meta) = std::fs::symlink_metadata(path) else {
        return false;
    };
    if meta.file_type().is_symlink() {
        return true;
    }
    #[cfg(windows)]
    {
        use std::os::windows::fs::MetadataExt;
        const FILE_ATTRIBUTE_REPARSE_POINT: u32 = 0x0400;
        if (meta.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT) != 0 {
            return true;
        }
    }
    false
}

/// Validates target installation directory for syntax, safety, permissions, reparse points, and path length.
pub fn validate_install_directory(dir: &Path) -> PathValidationResult {
    use std::path::Component;

    let raw = dir.to_string_lossy().trim().to_string();
    if raw.is_empty() {
        return PathValidationResult {
            error: Some("Installation folder cannot be empty.".to_string()),
            warning: None,
        };
    }

    if !dir.is_absolute()
        || (cfg!(windows) && !matches!(dir.components().next(), Some(Component::Prefix(_))))
    {
        return PathValidationResult {
            error: Some("Please choose an absolute installation path (for example, C:\\Users\\...\\Monitor).".to_string()),
            warning: None,
        };
    }

    if dir.parent().is_none() {
        return PathValidationResult {
            error: Some("Cannot install directly into a drive root. Choose a dedicated subfolder such as Monitor.".to_string()),
            warning: None,
        };
    }

    for component in dir.components() {
        if let Component::Normal(seg_os) = component {
            let seg = seg_os.to_string_lossy();
            if let Some(reason) = validate_windows_component(&seg) {
                return PathValidationResult {
                    error: Some(reason),
                    warning: None,
                };
            }
        }
    }

    if is_protected_system_path(&normalize_path_for_prefix_check(dir)) {
        return PathValidationResult {
            error: Some("Cannot install inside protected Windows system folders. Choose a user-writable folder.".to_string()),
            warning: None,
        };
    }

    let path_chars = raw.chars().count();
    if path_chars > 170 {
        return PathValidationResult {
            error: Some(format!(
                "Installation path is too long ({path_chars} characters; max 170) for bundled Git tools. Choose a shorter path."
            )),
            warning: None,
        };
    }

    if is_reparse_or_symlink(dir) {
        return PathValidationResult {
            error: Some(
                "Target folder cannot be a symbolic link or NTFS junction point.".to_string(),
            ),
            warning: None,
        };
    }

    if dir.exists() && !dir.is_dir() {
        return PathValidationResult {
            error: Some(
                "Selected path points to an existing file instead of a folder.".to_string(),
            ),
            warning: None,
        };
    }

    if let Err(reason) = probe_directory_writable(dir) {
        return PathValidationResult {
            error: Some(reason),
            warning: None,
        };
    }

    let mut warning = None;
    if path_chars > 125 {
        warning = Some(format!(
            "Long installation path ({path_chars} characters). Keeping paths under 125 characters improves Git reliability."
        ));
    } else if dir.is_dir()
        && !dir.join("Monitor.exe").is_file()
        && !dir.join(".monitor-install-manifest.txt").is_file()
        && directory_has_unrelated_entries(dir)
    {
        warning = Some("Selected folder already contains existing files. Monitor will track only its own files, or use Browse to create a Monitor subfolder.".to_string());
    }

    PathValidationResult {
        error: None,
        warning,
    }
}

fn validate_windows_component(segment: &str) -> Option<String> {
    const INVALID_CHARS: [char; 7] = ['<', '>', ':', '"', '|', '?', '*'];
    const RESERVED_DOS: [&str; 22] = [
        "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8",
        "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
    ];

    if segment.is_empty()
        || segment
            .chars()
            .any(|c| INVALID_CHARS.contains(&c) || c.is_control())
        || segment.ends_with('.')
        || segment.ends_with(' ')
    {
        return Some(format!(
            "Folder name '{segment}' contains invalid Windows characters or trailing spaces/dots."
        ));
    }

    let stem = segment.split('.').next().unwrap_or(segment);
    if RESERVED_DOS
        .iter()
        .any(|reserved| stem.eq_ignore_ascii_case(reserved))
    {
        return Some(format!(
            "Folder name '{segment}' uses a reserved Windows device name."
        ));
    }
    None
}

fn normalize_path_for_prefix_check(dir: &Path) -> String {
    let s = dir.to_string_lossy().replace('/', "\\");
    let no_drive = if s.len() >= 2 && s.as_bytes()[1] == b':' {
        &s[2..]
    } else {
        s.as_str()
    };
    no_drive.trim_end_matches('\\').to_ascii_lowercase()
}

fn is_protected_system_path(normalized_no_drive_lower: &str) -> bool {
    if normalized_no_drive_lower == "\\users" {
        return true;
    }
    const PROTECTED_PREFIXES: [&str; 6] = [
        "\\windows",
        "\\programdata",
        "\\$recycle.bin",
        "\\system volume information",
        "\\recovery",
        "\\perflogs",
    ];
    PROTECTED_PREFIXES.iter().any(|prefix| {
        normalized_no_drive_lower == *prefix
            || normalized_no_drive_lower.starts_with(&format!("{prefix}\\"))
    })
}

fn probe_directory_writable(dir: &Path) -> std::result::Result<(), String> {
    let mut cursor = Some(dir);
    let mut deepest_existing = None;
    while let Some(path) = cursor {
        if path.exists() {
            deepest_existing = Some(path.to_path_buf());
            break;
        }
        cursor = path.parent();
    }

    let Some(probe_base) = deepest_existing else {
        return Err("Target drive or parent folder does not exist.".to_string());
    };
    if !probe_base.is_dir() {
        return Err(format!(
            "Cannot create folder beneath {} because it is a file.",
            probe_base.display()
        ));
    }
    if probe_base == dir && is_reparse_or_symlink(&probe_base) {
        return Err("Target folder cannot be a symbolic link or junction.".to_string());
    }

    let nanos = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_nanos())
        .unwrap_or(0);
    let probe_path = probe_base.join(format!(
        ".monitor_write_probe_{}_{}",
        std::process::id(),
        nanos
    ));

    match OpenOptions::new().create_new(true).write(true).open(&probe_path) {
        Ok(file) => {
            drop(file);
            let _ = std::fs::remove_file(&probe_path);
            Ok(())
        }
        Err(err) if err.kind() == std::io::ErrorKind::PermissionDenied => Err(
            "Cannot write to this folder without administrator permissions. Choose a user-writable folder such as %LOCALAPPDATA%\\Programs\\Monitor."
                .to_string(),
        ),
        Err(err) => Err(format!("Target drive or folder is not writable ({err}). Choose another location.")),
    }
}

fn directory_has_unrelated_entries(dir: &Path) -> bool {
    let Ok(entries) = std::fs::read_dir(dir) else {
        return false;
    };
    entries.flatten().any(|entry| {
        !entry
            .file_name()
            .to_string_lossy()
            .eq_ignore_ascii_case(".monitor-install-tx.dat")
    })
}
