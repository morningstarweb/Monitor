use crate::client::Device;
use monitor_core::RepoSummary;
use std::path::PathBuf;

/// Check Windows Personalize registry to detect Light Mode vs Dark Mode
pub fn is_system_dark_mode() -> bool {
    #[cfg(windows)]
    {
        use winreg::RegKey;
        use winreg::enums::HKEY_CURRENT_USER;
        let hkcu = RegKey::predef(HKEY_CURRENT_USER);
        if let Ok(key) =
            hkcu.open_subkey("Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize")
        {
            if let Ok(val) = key.get_value::<u32, _>("AppsUseLightTheme") {
                return val == 0;
            }
            if let Ok(val) = key.get_value::<u32, _>("SystemUsesLightTheme") {
                return val == 0;
            }
        }
    }
    false
}

#[cfg(windows)]
const SPI_GETDESKWALLPAPER: u32 = 0x0073;

#[cfg(windows)]
#[link(name = "user32")]
unsafe extern "system" {
    fn SystemParametersInfoW(
        ui_action: u32,
        ui_param: u32,
        pv_param: *mut std::ffi::c_void,
        f_win_ini: u32,
    ) -> i32;
}

#[cfg(windows)]
fn query_win32_wallpaper() -> Option<PathBuf> {
    let mut buffer = [0u16; 512];
    let ok = unsafe {
        SystemParametersInfoW(
            SPI_GETDESKWALLPAPER,
            buffer.len() as u32,
            buffer.as_mut_ptr() as *mut std::ffi::c_void,
            0,
        )
    };
    if ok != 0 {
        let len = buffer.iter().position(|&c| c == 0).unwrap_or(buffer.len());
        let path_str = String::from_utf16_lossy(&buffer[..len]);
        let trimmed = path_str.trim();
        if !trimmed.is_empty() {
            let p = PathBuf::from(trimmed);
            if p.exists() {
                return Some(p);
            }
        }
    }
    None
}

#[cfg(windows)]
fn cache_wallpaper_image(source: &PathBuf) -> Option<PathBuf> {
    if !source.exists() {
        return None;
    }
    let mtime_secs = std::fs::metadata(source)
        .and_then(|m| m.modified())
        .map(|t| {
            t.duration_since(std::time::UNIX_EPOCH)
                .unwrap_or_default()
                .as_secs()
        })
        .unwrap_or(0);
    let file_stem = source
        .file_stem()
        .and_then(|s| s.to_str())
        .unwrap_or("active");
    let clean_stem: String = file_stem
        .chars()
        .filter(|c| c.is_alphanumeric() || *c == '_' || *c == '-')
        .collect();
    let cached_name = format!("monitor_wp_{}_{}.jpg", clean_stem, mtime_secs);
    let target = std::env::temp_dir().join(&cached_name);
    if !target.exists() {
        let _ = std::fs::copy(source, &target);
        if let Ok(entries) = std::fs::read_dir(std::env::temp_dir()) {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().to_string();
                if name.starts_with("monitor_wp_") && name != cached_name {
                    let _ = std::fs::remove_file(entry.path());
                }
            }
        }
    }
    if target.exists() {
        Some(target)
    } else {
        Some(source.clone())
    }
}

/// Fetch the actual running desktop wallpaper path from Windows
pub fn get_windows_wallpaper_path() -> Option<PathBuf> {
    #[cfg(windows)]
    {
        // 1. Authoritative Win32 SystemParametersInfo (captures Themes, Spotlight, Slideshow, Custom pictures)
        if let Some(p) = query_win32_wallpaper() {
            return cache_wallpaper_image(&p);
        }

        use winreg::RegKey;
        use winreg::enums::HKEY_CURRENT_USER;
        let hkcu = RegKey::predef(HKEY_CURRENT_USER);

        // 2. Control Panel Desktop Wallpaper registry key
        if let Ok(key) = hkcu.open_subkey("Control Panel\\Desktop")
            && let Ok(path_str) = key.get_value::<String, _>("Wallpaper")
        {
            let p = PathBuf::from(path_str.trim());
            if p.exists() {
                return cache_wallpaper_image(&p);
            }
        }

        // 3. TranscodedWallpaper in AppData Themes
        if let Ok(appdata) = std::env::var("APPDATA") {
            let p = PathBuf::from(appdata).join(r"Microsoft\Windows\Themes\TranscodedWallpaper");
            if p.exists() {
                return cache_wallpaper_image(&p);
            }
        }

        // 4. Fallback to BackgroundHistoryPath0 if present
        if let Ok(key) =
            hkcu.open_subkey(r"Software\Microsoft\Windows\CurrentVersion\Explorer\Wallpapers")
            && let Ok(path_str) = key.get_value::<String, _>("BackgroundHistoryPath0")
        {
            let p = PathBuf::from(path_str.trim());
            if p.exists() {
                return cache_wallpaper_image(&p);
            }
        }

        // 5. Default Windows 11 / Windows 10 Web Wallpapers
        let win11 = PathBuf::from(r"C:\WINDOWS\web\wallpaper\Windows\img0.jpg");
        if win11.exists() {
            return Some(win11);
        }
    }
    None
}

#[cfg(windows)]
#[repr(C)]
struct Win32Point {
    x: i32,
    y: i32,
}

#[cfg(windows)]
#[link(name = "user32")]
unsafe extern "system" {
    fn MonitorFromPoint(pt: Win32Point, dw_flags: u32) -> isize;
}

#[cfg(windows)]
#[link(name = "shcore")]
unsafe extern "system" {
    fn GetDpiForMonitor(hmonitor: isize, dpi_type: i32, dpi_x: *mut u32, dpi_y: *mut u32) -> i32;
}

/// Queries the real Windows per-monitor DPI scale factor at physical coordinates (`AUD-024`).
pub fn query_dpi_scale_for_point(x: i32, y: i32) -> f64 {
    #[cfg(windows)]
    {
        const MONITOR_DEFAULTTONEAREST: u32 = 2;
        const MDT_EFFECTIVE_DPI: i32 = 0;
        let hmon = unsafe { MonitorFromPoint(Win32Point { x, y }, MONITOR_DEFAULTTONEAREST) };
        if hmon != 0 {
            let mut dpi_x: u32 = 96;
            let mut dpi_y: u32 = 96;
            let hr = unsafe { GetDpiForMonitor(hmon, MDT_EFFECTIVE_DPI, &mut dpi_x, &mut dpi_y) };
            if hr == 0 && dpi_x >= 96 {
                return (dpi_x as f64 / 96.0).clamp(1.0, 4.0);
            }
        }
    }
    #[cfg(not(windows))]
    {
        let _ = (x, y);
    }
    1.0
}

/// Telemetry and UI state passed into the Flyout components.
#[derive(Clone, Debug)]
pub struct FlyoutState {
    pub hostname: String,
    pub os_name: String,
    pub cpu_percent: f32,
    pub status_text: String,
    pub mobile_devices_count: usize,
    pub is_cloud_synced: bool,
    pub cloud_email: Option<String>,
    pub projects_count: usize,
    pub autostart_enabled: bool,
    pub pairing_code: Option<String>,
    pub devices: Vec<Device>,
    pub projects: Vec<RepoSummary>,
    pub addresses: Vec<String>,
    pub git_access_allowed: bool,
    pub delete_access_allowed: bool,
    pub cloud_access_allowed: bool,
    pub local_access_allowed: bool,
    pub terminal_access_allowed: bool,
    pub checkpoint_access_allowed: bool,
    pub connected_clients: usize,
    pub alerts: Vec<crate::client::AlertItem>,
    pub active_agent_sessions: Vec<crate::client::AgentSessionItem>,
    pub disks: Vec<monitor_core::DiskSnapshot>,
    pub top_processes: Vec<monitor_core::ProcessSnapshot>,
    pub battery: Option<monitor_core::BatterySnapshot>,
    pub memory_used_gb: f32,
    pub memory_total_gb: f32,
    pub uptime_seconds: u64,
    pub is_dark_mode: bool,
    pub wallpaper_path: Option<PathBuf>,
    pub show_qr_code: bool,
    pub listen_port: u16,
    pub host_cloud_device_id: Option<String>,
}

impl Default for FlyoutState {
    fn default() -> Self {
        Self {
            hostname: sysinfo::System::host_name().unwrap_or_else(|| "DESKTOP-PC".to_string()),
            os_name: sysinfo::System::long_os_version().unwrap_or_else(|| "Windows 11".to_string()),
            cpu_percent: 0.0,
            status_text: "Initializing...".to_string(),
            mobile_devices_count: 0,
            is_cloud_synced: false,
            cloud_email: None,
            projects_count: 0,
            autostart_enabled: monitor_tray::is_auto_start_enabled(),
            pairing_code: None,
            show_qr_code: false,
            devices: Vec::new(),
            projects: Vec::new(),
            addresses: Vec::new(),
            // NEW-12: never display an optimistic "granted" state. Authoritative
            // values arrive from /local/status; until then everything is denied.
            git_access_allowed: false,
            delete_access_allowed: false,
            cloud_access_allowed: false,
            local_access_allowed: false,
            terminal_access_allowed: false,
            checkpoint_access_allowed: false,
            connected_clients: 0,
            alerts: Vec::new(),
            active_agent_sessions: Vec::new(),
            disks: Vec::new(),
            top_processes: Vec::new(),
            battery: None,
            memory_used_gb: 0.0,
            memory_total_gb: 0.0,
            uptime_seconds: 0,
            is_dark_mode: is_system_dark_mode(),
            wallpaper_path: get_windows_wallpaper_path(),
            listen_port: monitor_core::brand_config::DEFAULT_PORT,
            host_cloud_device_id: None,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_wallpaper_detection() {
        let path = get_windows_wallpaper_path();
        if let Some(p) = path {
            assert!(
                p.exists(),
                "Detected wallpaper file must exist on disk: {:?}",
                p
            );
        }
    }
}
