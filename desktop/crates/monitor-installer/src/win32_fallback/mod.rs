use crate::engine::{InstallOptions, InstallProgressEvent};
use crate::state::InstallerState;
use anyhow::Result;
use std::sync::atomic::AtomicBool;
use std::sync::mpsc::Sender;

#[cfg(windows)]
mod controls;
#[cfg(windows)]
mod events;
#[cfg(windows)]
mod state;
#[cfg(windows)]
mod window;

pub type InstallRunnerFn =
    fn(InstallOptions, &AtomicBool, Sender<InstallProgressEvent>) -> Result<()>;

#[derive(Clone, Debug)]
pub struct FallbackRunRequest {
    pub initial_state: InstallerState,
    pub install_runner: Option<InstallRunnerFn>,
    pub triggered_by_gpu_failure: bool,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum FallbackOutcome {
    Completed,
    Cancelled,
}

/// Prints/displays CLI usage documentation cleanly even when built under Windows GUI subsystem.
pub fn show_cli_help(is_uninstaller_bin: bool) {
    let binary = if is_uninstaller_bin {
        "uninstall.exe"
    } else {
        "Monitor_Setup.exe"
    };
    let help = format!(
        "Monitor Setup & Uninstaller v{}\n\
         Publisher: Morning Star Web (https://morningstarweb.space)\n\
         License: Source-Available (PolyForm Noncommercial 1.0.0)\n\n\
         Usage:\n\
           {binary} [options]\n\n\
         Options:\n\
           --silent, -s, /S        Run unattended install or uninstall\n\
           --dir=<PATH>, /D=<PATH> Override installation folder\n\
           --uninstall, /uninstall Run in uninstaller mode\n\
           --clean, /clean         Remove existing user config before installing\n\
           --wipe-data             Remove user config/logs during uninstall\n\
           --no-desktop            Do not create Desktop shortcut\n\
           --no-startup            Disable Windows login startup entry\n\
           --launch / --no-launch  Control automatic launch after silent install\n\
           --fallback-ui, --gdi    Force GPU-independent Win32 GDI interface\n\
           --help, -h, /?          Show this help message\n",
        env!("CARGO_PKG_VERSION")
    );

    #[cfg(windows)]
    {
        if try_write_to_parent_console(&help) {
            return;
        }
        show_notice_box("Monitor Setup Help", &help, false);
    }
    #[cfg(not(windows))]
    {
        let _ = help;
    }
}

/// Displays a native OS dialog when GUI initialization fails fatally.
pub fn show_fatal_error_notice(title: &str, message: &str) {
    #[cfg(windows)]
    {
        let _ = try_write_to_parent_console(&format!("{title}: {message}\n"));
        show_notice_box(title, message, true);
    }
    #[cfg(not(windows))]
    {
        let _ = (title, message);
    }
}

/// Computes whether the GDI fallback Install/Uninstall primary action should be enabled.
pub fn can_start_fallback_action(state: &InstallerState) -> bool {
    if state.is_uninstall_mode {
        !state.is_uninstalling && !state.uninstall_done
    } else {
        !state.is_installing && state.eula_agreed && state.can_proceed_from_location()
    }
}

/// Runs a pure Win32 GDI / Common-Controls installer or uninstaller window with zero GPU dependency.
pub fn run_fallback_gui(request: FallbackRunRequest) -> Result<FallbackOutcome> {
    #[cfg(windows)]
    {
        window::run_win32_window(request)
    }
    #[cfg(not(windows))]
    {
        let _ = request;
        anyhow::bail!("Win32 GDI fallback UI is only supported on Windows.")
    }
}

#[cfg(windows)]
pub(super) fn to_wide_z(s: &str) -> Vec<u16> {
    use std::os::windows::ffi::OsStrExt;
    std::ffi::OsStr::new(s)
        .encode_wide()
        .chain(std::iter::once(0))
        .collect()
}

#[cfg(windows)]
fn try_write_to_parent_console(text: &str) -> bool {
    use windows::Win32::System::Console::{
        ATTACH_PARENT_PROCESS, AttachConsole, FreeConsole, GetStdHandle, STD_OUTPUT_HANDLE,
        WriteConsoleW,
    };

    // SAFETY: Standard Win32 optional attach to parent console when invoked from cmd/PowerShell.
    if unsafe { AttachConsole(ATTACH_PARENT_PROCESS) }.is_err() {
        return false;
    }
    let handle_res = unsafe { GetStdHandle(STD_OUTPUT_HANDLE) };
    let wrote = if let Ok(handle) = handle_res {
        let wide: Vec<u16> = text.encode_utf16().collect();
        let mut written = 0u32;
        // SAFETY: `wide` buffer stays valid during `WriteConsoleW`.
        unsafe { WriteConsoleW(handle, &wide, Some(&mut written), None) }.is_ok()
    } else {
        false
    };
    // SAFETY: Paired detach for `AttachConsole`.
    unsafe {
        let _ = FreeConsole();
    }
    wrote
}

#[cfg(windows)]
pub(super) fn show_notice_box(title: &str, message: &str, is_error: bool) {
    use windows::Win32::UI::WindowsAndMessaging::{
        MB_ICONERROR, MB_ICONINFORMATION, MB_OK, MessageBoxW,
    };
    use windows::core::PCWSTR;

    let title_w = to_wide_z(title);
    let msg_w = to_wide_z(message);
    let flags = MB_OK
        | if is_error {
            MB_ICONERROR
        } else {
            MB_ICONINFORMATION
        };
    // SAFETY: NUL-terminated UTF-16 strings outlive synchronous `MessageBoxW`.
    unsafe {
        MessageBoxW(
            None,
            PCWSTR::from_raw(msg_w.as_ptr()),
            PCWSTR::from_raw(title_w.as_ptr()),
            flags,
        );
    }
}
