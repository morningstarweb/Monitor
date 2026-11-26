use anyhow::{Context, Result};
use std::path::{Path, PathBuf};
#[cfg(windows)]
use winreg::RegKey;
#[cfg(windows)]
use winreg::enums::{HKEY_CURRENT_USER, KEY_READ, KEY_SET_VALUE};

const RUN_KEY: &str = r"Software\Microsoft\Windows\CurrentVersion\Run";
const STARTUP_APPROVED_RUN_KEY: &str =
    r"Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\Run";
const STARTUP_APPROVED_FOLDER_KEY: &str =
    r"Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\StartupFolder";
const UNINSTALL_KEY: &str = r"Software\Microsoft\Windows\CurrentVersion\Uninstall\Monitor";
const PREFS_KEY: &str = r"Software\Morning Star Web\Monitor";

#[derive(Clone, Debug, Default)]
pub struct InstallerPreferences {
    pub create_desktop_shortcut: Option<bool>,
    pub start_with_windows: Option<bool>,
    pub license_accepted_version: Option<String>,
}

pub fn set_startup_registry(exe_path: &Path) -> Result<()> {
    #[cfg(windows)]
    {
        let hkcu = RegKey::predef(HKEY_CURRENT_USER);
        let (key, _) = hkcu
            .create_subkey(RUN_KEY)
            .context("opening Run registry key")?;
        let formatted = format!("\"{}\"", exe_path.to_string_lossy());
        key.set_value("Monitor", &formatted)
            .context("setting startup registry value")?;

        let enabled_bytes: [u8; 12] = [2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
        if let Ok((approved_run, _)) = hkcu.create_subkey(STARTUP_APPROVED_RUN_KEY) {
            let _ = approved_run.set_raw_value(
                "Monitor",
                &winreg::RegValue {
                    vtype: winreg::enums::REG_BINARY,
                    bytes: enabled_bytes.to_vec(),
                },
            );
        }
        if let Ok((approved_folder, _)) = hkcu.create_subkey(STARTUP_APPROVED_FOLDER_KEY) {
            let _ = approved_folder.set_raw_value(
                "Monitor.lnk",
                &winreg::RegValue {
                    vtype: winreg::enums::REG_BINARY,
                    bytes: enabled_bytes.to_vec(),
                },
            );
        }
    }
    #[cfg(not(windows))]
    {
        let _ = exe_path;
    }
    Ok(())
}

pub fn remove_startup_registry() -> Result<()> {
    #[cfg(windows)]
    {
        let hkcu = RegKey::predef(HKEY_CURRENT_USER);
        if let Ok(key) = hkcu.open_subkey_with_flags(RUN_KEY, KEY_SET_VALUE) {
            let _ = key.delete_value("Monitor");
        }
        if let Ok(key) = hkcu.open_subkey_with_flags(STARTUP_APPROVED_RUN_KEY, KEY_SET_VALUE) {
            let _ = key.delete_value("Monitor");
        }
        if let Ok(key) = hkcu.open_subkey_with_flags(STARTUP_APPROVED_FOLDER_KEY, KEY_SET_VALUE) {
            let _ = key.delete_value("Monitor.lnk");
        }
    }
    Ok(())
}

pub fn register_uninstaller(
    install_dir: &Path,
    version: &str,
    estimated_size_kb: u32,
) -> Result<()> {
    #[cfg(windows)]
    {
        let hkcu = RegKey::predef(HKEY_CURRENT_USER);
        let (key, _) = hkcu
            .create_subkey(UNINSTALL_KEY)
            .context("creating Uninstall registry key")?;

        let uninstall_exe = install_dir.join("uninstall.exe");
        let uninstall_str = format!("\"{}\" --uninstall", uninstall_exe.to_string_lossy());
        let quiet_uninstall_str = format!(
            "\"{}\" --uninstall --silent",
            uninstall_exe.to_string_lossy()
        );
        let icon_path = format!(
            "\"{}\",0",
            install_dir.join("monitor.ico").to_string_lossy()
        );
        let install_date = chrono::Local::now().format("%Y%m%d").to_string();

        key.set_value("DisplayName", &"Monitor")?;
        key.set_value("DisplayVersion", &version)?;
        key.set_value("Publisher", &"Morning Star Web")?;
        key.set_value("URLInfoAbout", &"https://morningstarweb.space")?;
        key.set_value("HelpLink", &"https://morningstarweb.space")?;
        key.set_value("InstallDate", &install_date)?;
        key.set_value("InstallLocation", &install_dir.to_string_lossy().as_ref())?;
        key.set_value("DisplayIcon", &icon_path.as_str())?;
        key.set_value("UninstallString", &uninstall_str.as_str())?;
        key.set_value("QuietUninstallString", &quiet_uninstall_str.as_str())?;
        key.set_value("NoModify", &1u32)?;
        key.set_value("NoRepair", &1u32)?;
        key.set_value("EstimatedSize", &estimated_size_kb.max(1024))?;
    }
    #[cfg(not(windows))]
    {
        let _ = (install_dir, version, estimated_size_kb);
    }
    Ok(())
}

pub fn unregister_uninstaller() -> Result<()> {
    #[cfg(windows)]
    {
        let hkcu = RegKey::predef(HKEY_CURRENT_USER);
        let _ = hkcu.delete_subkey_all(UNINSTALL_KEY);
    }
    Ok(())
}

/// Reads existing `(InstallLocation, DisplayVersion)` from HKCU Uninstall registry if installed.
pub fn read_installed_metadata() -> Option<(PathBuf, Option<String>)> {
    #[cfg(windows)]
    {
        let hkcu = RegKey::predef(HKEY_CURRENT_USER);
        let key = hkcu.open_subkey_with_flags(UNINSTALL_KEY, KEY_READ).ok()?;
        let loc: String = key.get_value("InstallLocation").ok()?;
        let trimmed = loc.trim();
        if trimmed.is_empty() {
            return None;
        }
        let ver: Option<String> = key.get_value("DisplayVersion").ok();
        Some((PathBuf::from(trimmed), ver))
    }
    #[cfg(not(windows))]
    {
        None
    }
}

pub fn save_installer_preferences(
    create_desktop_shortcut: bool,
    start_with_windows: bool,
    version: &str,
) {
    #[cfg(windows)]
    {
        let hkcu = RegKey::predef(HKEY_CURRENT_USER);
        if let Ok((key, _)) = hkcu.create_subkey(PREFS_KEY) {
            let _ = key.set_value("CreateDesktopShortcut", &u32::from(create_desktop_shortcut));
            let _ = key.set_value("StartWithWindows", &u32::from(start_with_windows));
            let _ = key.set_value("LicenseAcceptedVersion", &version);
        }
    }
    #[cfg(not(windows))]
    {
        let _ = (create_desktop_shortcut, start_with_windows, version);
    }
}

pub fn read_installer_preferences() -> InstallerPreferences {
    #[cfg(windows)]
    {
        let hkcu = RegKey::predef(HKEY_CURRENT_USER);
        let Ok(key) = hkcu.open_subkey_with_flags(PREFS_KEY, KEY_READ) else {
            return InstallerPreferences::default();
        };
        InstallerPreferences {
            create_desktop_shortcut: key
                .get_value::<u32, _>("CreateDesktopShortcut")
                .ok()
                .map(|v| v != 0),
            start_with_windows: key
                .get_value::<u32, _>("StartWithWindows")
                .ok()
                .map(|v| v != 0),
            license_accepted_version: key.get_value::<String, _>("LicenseAcceptedVersion").ok(),
        }
    }
    #[cfg(not(windows))]
    {
        InstallerPreferences::default()
    }
}

pub fn remove_installer_preferences() {
    #[cfg(windows)]
    {
        let hkcu = RegKey::predef(HKEY_CURRENT_USER);
        let _ = hkcu.delete_subkey_all(PREFS_KEY);
    }
}
