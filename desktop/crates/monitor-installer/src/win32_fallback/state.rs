use super::{FallbackOutcome, InstallRunnerFn, can_start_fallback_action, to_wide_z};
use crate::engine::InstallProgressEvent;
use crate::state::InstallerState;
use std::cell::RefCell;
use std::rc::Rc;
use std::sync::Arc;
use std::sync::atomic::AtomicBool;
use std::sync::mpsc::Receiver;
use windows::Win32::Foundation::{HWND, LPARAM, WPARAM};
use windows::Win32::UI::Controls::PBM_SETPOS;
use windows::Win32::UI::Input::KeyboardAndMouse::EnableWindow;
use windows::Win32::UI::WindowsAndMessaging::{
    BM_GETCHECK, BM_SETCHECK, GetWindowTextLengthW, GetWindowTextW, SendMessageW, SetWindowTextW,
    WM_APP,
};
use windows::core::PCWSTR;

pub(super) const WM_APP_WORKER_POLL: u32 = WM_APP + 42;
pub(super) const ID_CHECK_EULA: usize = 1001;
pub(super) const ID_EDIT_DIR: usize = 1002;
pub(super) const ID_BTN_BROWSE: usize = 1003;
pub(super) const ID_CHECK_DESKTOP: usize = 1004;
pub(super) const ID_CHECK_STARTUP: usize = 1005;
pub(super) const ID_CHECK_CLEAN: usize = 1006;
pub(super) const ID_CHECK_LAUNCH: usize = 1007;
pub(super) const ID_BTN_ACTION: usize = 1008;
pub(super) const ID_BTN_CANCEL: usize = 1009;
pub(super) const ID_CHECK_WIPE_DATA: usize = 1010;

pub(super) struct Controls {
    pub(super) hwnd: HWND,
    pub(super) chk_eula: HWND,
    pub(super) edit_dir: HWND,
    pub(super) btn_browse: HWND,
    pub(super) lbl_validation: HWND,
    pub(super) chk_desktop: HWND,
    pub(super) chk_startup: HWND,
    pub(super) chk_clean: HWND,
    pub(super) chk_launch: HWND,
    pub(super) chk_wipe_data: HWND,
    pub(super) progress_bar: HWND,
    pub(super) lbl_status: HWND,
    pub(super) btn_action: HWND,
    pub(super) btn_cancel: HWND,
}

pub(super) struct FallbackContext {
    pub(super) state: InstallerState,
    pub(super) install_runner: Option<InstallRunnerFn>,
    pub(super) controls: Option<Controls>,
    pub(super) rx: Option<Receiver<InstallProgressEvent>>,
    pub(super) cancel_flag: Option<Arc<AtomicBool>>,
    pub(super) outcome: FallbackOutcome,
    pub(super) fatal_error: Option<String>,
    pub(super) finished: bool,
}

pub(super) type SharedCtx = Rc<RefCell<FallbackContext>>;

pub(super) fn set_text(hwnd: HWND, text: &str) {
    let w = to_wide_z(text);
    unsafe {
        let _ = SetWindowTextW(hwnd, PCWSTR::from_raw(w.as_ptr()));
    }
}

pub(super) fn set_check(hwnd: HWND, checked: bool) {
    unsafe {
        SendMessageW(
            hwnd,
            BM_SETCHECK,
            Some(WPARAM(if checked { 1 } else { 0 })),
            Some(LPARAM(0)),
        );
    }
}

pub(super) fn is_checked(hwnd: HWND) -> bool {
    unsafe { SendMessageW(hwnd, BM_GETCHECK, None, None).0 == 1 }
}

pub(super) fn set_enabled(hwnd: HWND, enabled: bool) {
    unsafe {
        let _ = EnableWindow(hwnd, enabled);
    }
}

fn read_text(hwnd: HWND) -> String {
    let len = unsafe { GetWindowTextLengthW(hwnd) };
    if len <= 0 {
        return String::new();
    }
    let mut buf = vec![0u16; (len as usize) + 1];
    let copied = unsafe { GetWindowTextW(hwnd, &mut buf) };
    String::from_utf16_lossy(&buf[..copied.max(0) as usize])
}

fn format_mb(bytes: u64) -> String {
    format!("{:.1} MB", (bytes as f64 / (1024.0 * 1024.0)).max(0.1))
}

pub(super) fn sync_ui_from_state(ctx: &SharedCtx) {
    let guard = ctx.borrow();
    let Some(ref c) = guard.controls else {
        return;
    };
    let s = &guard.state;

    let dir_text = if s.is_uninstall_mode {
        s.uninstall_dir.to_string_lossy().to_string()
    } else {
        s.install_dir.to_string_lossy().to_string()
    };
    if read_text(c.edit_dir) != dir_text {
        set_text(c.edit_dir, &dir_text);
    }

    if s.is_uninstall_mode {
        sync_uninstall_controls(c, s);
        return;
    }

    set_check(c.chk_eula, s.eula_agreed);
    set_check(c.chk_desktop, s.create_desktop_shortcut);
    set_check(c.chk_startup, s.start_with_windows);
    set_check(c.chk_clean, s.clean_install);
    set_check(c.chk_launch, s.launch_on_finish);

    let editing_allowed = !s.is_installing && !guard.finished;
    set_enabled(c.chk_eula, editing_allowed);
    set_enabled(c.btn_browse, editing_allowed);
    set_enabled(c.chk_desktop, editing_allowed);
    set_enabled(c.chk_startup, editing_allowed);
    set_enabled(c.chk_clean, editing_allowed);
    set_enabled(c.chk_launch, !s.is_installing);

    let req_mb = format_mb(s.required_space_bytes);
    let avail_mb = s
        .available_space_bytes
        .map(format_mb)
        .unwrap_or_else(|| "Unknown".to_string());
    let val_msg = if let Some(ref err) = s.path_error {
        format!("Blocked: {err}")
    } else if !s.has_sufficient_disk_space() {
        format!("Blocked: Not enough disk space ({avail_mb} free, {req_mb} required).")
    } else if let Some(ref warn) = s.path_warning {
        format!("Notice: {warn} (Space: {avail_mb} free / {req_mb} required)")
    } else if s.is_upgrade_target() {
        format!(
            "Existing Monitor install detected (upgrade). Space: {avail_mb} free / {req_mb} required."
        )
    } else {
        format!("Valid folder. Required: {req_mb} | Available: {avail_mb}")
    };
    set_text(c.lbl_validation, &val_msg);

    let pct = s.install_progress.clamp(0.0, 100.0).round() as usize;
    unsafe {
        SendMessageW(c.progress_bar, PBM_SETPOS, Some(WPARAM(pct)), None);
    }

    if let Some(ref err) = s.install_error {
        set_text(c.lbl_status, &format!("Error: {err}"));
    } else {
        let suffix = s
            .files_progress
            .map(|(done, total)| format!(" ({done}/{total} files)"))
            .unwrap_or_default();
        set_text(c.lbl_status, &format!("{}{suffix}", s.status_message));
    }

    sync_install_buttons(c, s, guard.finished);
}

fn sync_uninstall_controls(c: &Controls, s: &InstallerState) {
    set_check(c.chk_wipe_data, s.uninstall_wipe_data);
    set_enabled(c.chk_wipe_data, !s.is_uninstalling && !s.uninstall_done);
    let pos = if s.uninstall_done {
        100
    } else if s.is_uninstalling {
        55
    } else {
        0
    };
    unsafe {
        SendMessageW(c.progress_bar, PBM_SETPOS, Some(WPARAM(pos)), None);
    }
    if let Some(ref err) = s.uninstall_error {
        set_text(c.lbl_status, &format!("Error: {err}"));
    } else if s.uninstall_done {
        set_text(
            c.lbl_status,
            "Monitor has been cleanly removed from this system.",
        );
    } else if s.is_uninstalling {
        set_text(c.lbl_status, "Stopping Monitor and removing files...");
    } else {
        set_text(c.lbl_status, "Ready to uninstall Monitor.");
    }
    set_text(
        c.btn_action,
        if s.uninstall_done {
            "Finish"
        } else {
            "Uninstall"
        },
    );
    set_enabled(c.btn_action, !s.is_uninstalling);
    set_enabled(c.btn_cancel, !s.is_uninstalling && !s.uninstall_done);
}

fn sync_install_buttons(c: &Controls, s: &InstallerState, finished: bool) {
    if finished {
        set_text(c.btn_action, "Finish");
        set_enabled(c.btn_action, true);
        set_text(c.btn_cancel, "Close");
        set_enabled(c.btn_cancel, true);
    } else if s.is_installing {
        set_text(c.btn_action, "Installing...");
        set_enabled(c.btn_action, false);
        set_text(
            c.btn_cancel,
            if s.is_cancelling {
                "Stopping..."
            } else {
                "Cancel"
            },
        );
        set_enabled(c.btn_cancel, !s.is_cancelling);
    } else {
        set_text(
            c.btn_action,
            if s.is_upgrade_target() {
                "Update"
            } else {
                "Install"
            },
        );
        set_enabled(c.btn_action, can_start_fallback_action(s));
        set_text(c.btn_cancel, "Cancel");
        set_enabled(c.btn_cancel, true);
    }
}
