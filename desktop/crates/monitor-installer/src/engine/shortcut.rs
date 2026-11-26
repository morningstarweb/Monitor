use anyhow::{Context, Result};
use std::path::{Path, PathBuf};

pub fn desktop_shortcut_path() -> Option<PathBuf> {
    std::env::var("USERPROFILE")
        .ok()
        .map(|profile| PathBuf::from(profile).join("Desktop").join("Monitor.lnk"))
}

pub fn start_menu_shortcut_path() -> Option<PathBuf> {
    std::env::var("APPDATA").ok().map(|appdata| {
        PathBuf::from(appdata)
            .join("Microsoft")
            .join("Windows")
            .join("Start Menu")
            .join("Programs")
            .join("Monitor.lnk")
    })
}

/// Creates a Windows shell shortcut natively via COM (`IShellLinkW` + `IPersistFile`)
/// without spawning PowerShell or cmd subprocesses.
pub fn create_shortcut(
    shortcut_path: &Path,
    target_exe: &Path,
    working_dir: &Path,
    icon_path: &Path,
) -> Result<()> {
    if let Some(parent) = shortcut_path.parent() {
        std::fs::create_dir_all(parent)
            .with_context(|| format!("creating shortcut directory {}", parent.display()))?;
    }

    #[cfg(windows)]
    {
        use std::os::windows::ffi::OsStrExt;
        use windows::Win32::Foundation::RPC_E_CHANGED_MODE;
        use windows::Win32::System::Com::{
            CLSCTX_INPROC_SERVER, COINIT_APARTMENTTHREADED, CoCreateInstance, CoInitializeEx,
            CoUninitialize, IPersistFile,
        };
        use windows::Win32::UI::Shell::{IShellLinkW, ShellLink};
        use windows::core::{Interface, PCWSTR};

        fn to_wide(s: &std::ffi::OsStr) -> Vec<u16> {
            s.encode_wide().chain(std::iter::once(0)).collect()
        }

        struct ComGuard(bool);
        impl Drop for ComGuard {
            fn drop(&mut self) {
                if self.0 {
                    // SAFETY: Only uninitializes when this scope initialized COM with `is_ok()`.
                    unsafe {
                        CoUninitialize();
                    }
                }
            }
        }

        // SAFETY: Standard apartment-threaded COM initialization for IShellLinkW creation.
        let co_hr = unsafe { CoInitializeEx(None, COINIT_APARTMENTTHREADED) };
        let should_uninit = if co_hr.is_ok() {
            true
        } else if co_hr == RPC_E_CHANGED_MODE {
            false
        } else {
            co_hr
                .ok()
                .context("initializing Windows COM for shortcut")?;
            false
        };
        let _guard = ComGuard(should_uninit);

        // SAFETY: Instantiating documented in-proc `ShellLink` COM class after `CoInitializeEx`.
        let link: IShellLinkW = unsafe { CoCreateInstance(&ShellLink, None, CLSCTX_INPROC_SERVER) }
            .context("creating ShellLink COM object")?;

        let target_w = to_wide(target_exe.as_os_str());
        let workdir_w = to_wide(working_dir.as_os_str());
        let icon_w = to_wide(icon_path.as_os_str());
        let desc_w = to_wide(std::ffi::OsStr::new(
            "Monitor Remote Development & System Monitor",
        ));
        let shortcut_w = to_wide(shortcut_path.as_os_str());

        // SAFETY: All wide buffers are NUL-terminated UTF-16 and outlive each COM setter call.
        unsafe {
            link.SetPath(PCWSTR::from_raw(target_w.as_ptr()))
                .context("setting shortcut target path")?;
            link.SetWorkingDirectory(PCWSTR::from_raw(workdir_w.as_ptr()))
                .context("setting shortcut working directory")?;
            link.SetIconLocation(PCWSTR::from_raw(icon_w.as_ptr()), 0)
                .context("setting shortcut icon")?;
            link.SetDescription(PCWSTR::from_raw(desc_w.as_ptr()))
                .context("setting shortcut description")?;

            let persist: IPersistFile =
                link.cast().context("casting IShellLinkW to IPersistFile")?;
            persist
                .Save(PCWSTR::from_raw(shortcut_w.as_ptr()), true)
                .with_context(|| format!("saving shortcut to {}", shortcut_path.display()))?;
        }
    }

    #[cfg(not(windows))]
    {
        let _ = (shortcut_path, target_exe, working_dir, icon_path);
    }

    Ok(())
}

pub fn remove_shortcut(shortcut_path: &Path) {
    if shortcut_path.exists() {
        let _ = std::fs::remove_file(shortcut_path);
    }
}
