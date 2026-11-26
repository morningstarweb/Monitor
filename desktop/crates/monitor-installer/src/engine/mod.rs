pub mod process;
pub mod registry;
pub mod shortcut;
pub mod uninstall;
pub mod validation;

pub use process::terminate_running_monitor;
pub use validation::{PathValidationResult, is_reparse_or_symlink, validate_install_directory};

use std::path::{Path, PathBuf};

#[derive(Clone, Debug)]
pub struct InstallOptions {
    pub target_dir: PathBuf,
    pub create_desktop_shortcut: bool,
    pub start_with_windows: bool,
    pub clean_install: bool,
}

#[derive(Clone, Debug)]
pub struct ExistingInstall {
    pub version: Option<String>,
    pub install_dir: PathBuf,
    pub create_desktop_shortcut: bool,
    pub start_with_windows: bool,
}

#[derive(Clone, Debug)]
pub enum InstallProgressEvent {
    StepStarted {
        step_index: usize,
    },
    Progress {
        percent: f32,
        message: String,
        files_done: Option<(u64, u64)>,
    },
    Completed {
        estimated_size_kb: u32,
        is_upgrade: bool,
    },
    Failed {
        error: String,
    },
    Cancelled,
}

/// Resolves standard Windows system binaries (`taskkill.exe`, `cmd.exe`, `explorer.exe`)
/// from `%SystemRoot%` / `GetSystemWindowsDirectoryW` to avoid working-directory DLL/EXE hijacking.
pub fn resolve_windows_system_exe(exe_name: &str) -> PathBuf {
    #[cfg(windows)]
    {
        let win_root = get_windows_root_dir();
        if exe_name.eq_ignore_ascii_case("explorer.exe") {
            let candidate = win_root.join("explorer.exe");
            if candidate.is_file() {
                return candidate;
            }
        }
        let sys32 = win_root.join("System32").join(exe_name);
        if sys32.is_file() {
            return sys32;
        }
        let direct = win_root.join(exe_name);
        if direct.is_file() {
            return direct;
        }
        sys32
    }
    #[cfg(not(windows))]
    {
        PathBuf::from(exe_name)
    }
}

#[cfg(windows)]
fn get_windows_root_dir() -> PathBuf {
    use windows::Win32::System::SystemInformation::GetSystemWindowsDirectoryW;

    let mut buf = [0u16; 512];
    let len = unsafe { GetSystemWindowsDirectoryW(Some(&mut buf)) };
    if len > 0 && (len as usize) <= buf.len() {
        let dir = String::from_utf16_lossy(&buf[..len as usize]);
        let path = PathBuf::from(dir);
        if path.is_dir() {
            return path;
        }
    }
    if let Some(root) = std::env::var_os("SystemRoot").or_else(|| std::env::var_os("WINDIR")) {
        let path = PathBuf::from(root);
        if path.is_dir() {
            return path;
        }
    }
    PathBuf::from(r"C:\Windows")
}

/// Detects an existing Monitor installation via registry metadata or default folder presence.
pub fn detect_existing_install() -> Option<ExistingInstall> {
    let prefs = registry::read_installer_preferences();
    if let Some((reg_dir, reg_ver)) = registry::read_installed_metadata()
        && reg_dir.join("Monitor.exe").is_file()
    {
        return Some(ExistingInstall {
            version: reg_ver,
            install_dir: reg_dir,
            create_desktop_shortcut: prefs
                .create_desktop_shortcut
                .unwrap_or_else(|| shortcut::desktop_shortcut_path().is_some_and(|p| p.exists())),
            start_with_windows: prefs.start_with_windows.unwrap_or(true),
        });
    }

    let default_dir = crate::state::default_install_directory();
    if default_dir.join("Monitor.exe").is_file() {
        return Some(ExistingInstall {
            version: None,
            install_dir: default_dir,
            create_desktop_shortcut: prefs
                .create_desktop_shortcut
                .unwrap_or_else(|| shortcut::desktop_shortcut_path().is_some_and(|p| p.exists())),
            start_with_windows: prefs.start_with_windows.unwrap_or(true),
        });
    }

    None
}

/// Queries free bytes available on the filesystem containing `target_path` (climbing ancestors if non-existent).
pub fn available_disk_bytes(target_path: &Path) -> Option<u64> {
    #[cfg(windows)]
    {
        use std::os::windows::ffi::OsStrExt;
        use windows::Win32::Storage::FileSystem::GetDiskFreeSpaceExW;
        use windows::core::PCWSTR;

        let mut probe = target_path.to_path_buf();
        while !probe.exists() {
            if let Some(parent) = probe.parent() {
                probe = parent.to_path_buf();
            } else {
                break;
            }
        }
        let mut wide: Vec<u16> = probe.as_os_str().encode_wide().collect();
        wide.push(0);
        let mut free_to_caller = 0u64;
        let res = unsafe {
            GetDiskFreeSpaceExW(
                PCWSTR::from_raw(wide.as_ptr()),
                Some(&mut free_to_caller),
                None,
                None,
            )
        };
        res.ok().map(|()| free_to_caller)
    }
    #[cfg(not(windows))]
    {
        let _ = target_path;
        None
    }
}

/// Deletes `%LOCALAPPDATA%\Monitor` user state when clean wipe is explicitly requested.
pub fn wipe_user_data_if_requested(clean_install: bool) {
    if clean_install && let Ok(local_appdata) = std::env::var("LOCALAPPDATA") {
        let user_data = PathBuf::from(local_appdata).join("Monitor");
        if user_data.exists() {
            let _ = std::fs::remove_dir_all(user_data);
        }
    }
}
