use super::state::{
    FallbackContext, ID_BTN_ACTION, ID_BTN_BROWSE, ID_BTN_CANCEL, ID_CHECK_CLEAN, ID_CHECK_DESKTOP,
    ID_CHECK_EULA, ID_CHECK_LAUNCH, ID_CHECK_STARTUP, ID_CHECK_WIPE_DATA, SharedCtx,
    WM_APP_WORKER_POLL, is_checked, sync_ui_from_state,
};
use super::{FallbackOutcome, can_start_fallback_action, show_notice_box};
use crate::engine::uninstall::{execute_clean_wipe, execute_uninstallation_at};
use crate::engine::{InstallOptions, InstallProgressEvent};
use std::cell::RefCell;
use std::path::{Path, PathBuf};
use std::rc::Rc;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc::channel;
use windows::Win32::Foundation::{HWND, LPARAM, LRESULT, WPARAM};
use windows::Win32::UI::WindowsAndMessaging::{
    DefWindowProcW, DestroyWindow, GWLP_USERDATA, GetWindowLongPtrW, PostMessageW, PostQuitMessage,
    SetWindowLongPtrW, WM_CLOSE, WM_COMMAND, WM_NCDESTROY,
};

fn handle_browse(ctx: &SharedCtx) {
    let start_dir = {
        let guard = ctx.borrow();
        if guard.state.install_dir.exists() {
            guard.state.install_dir.clone()
        } else {
            guard
                .state
                .install_dir
                .parent()
                .map(Path::to_path_buf)
                .unwrap_or_else(|| PathBuf::from(r"C:\"))
        }
    };

    if let Some(folder) = rfd::FileDialog::new()
        .set_title("Choose installation folder for Monitor")
        .set_directory(&start_dir)
        .pick_folder()
    {
        let normalized = if folder.join("Monitor.exe").is_file()
            || folder
                .file_name()
                .is_some_and(|n| n.to_string_lossy().eq_ignore_ascii_case("Monitor"))
        {
            folder
        } else if std::fs::read_dir(&folder)
            .ok()
            .and_then(|mut it| it.next())
            .is_some()
        {
            folder.join("Monitor")
        } else {
            folder
        };

        let mut guard = ctx.borrow_mut();
        guard.state.install_dir = normalized;
        guard.state.refresh_location_checks();
        drop(guard);
        sync_ui_from_state(ctx);
    }
}

fn start_worker(ctx: &SharedCtx) {
    let (hwnd, is_uninstall, install_runner, options, uninstall_dir, wipe_data) = {
        let mut guard = ctx.borrow_mut();
        let Some(ref c) = guard.controls else {
            return;
        };
        let hwnd = c.hwnd;
        if guard.state.is_uninstall_mode {
            if guard.state.uninstall_done {
                guard.outcome = FallbackOutcome::Completed;
                drop(guard);
                unsafe {
                    let _ = DestroyWindow(hwnd);
                }
                return;
            }
            guard.state.is_uninstalling = true;
            guard.state.uninstall_error = None;
            (
                hwnd,
                true,
                None,
                None,
                Some(guard.state.uninstall_dir.clone()),
                guard.state.uninstall_wipe_data,
            )
        } else if guard.finished {
            let launch = guard.state.launch_on_finish;
            let exe = guard.state.install_dir.join("Monitor.exe");
            let cwd = guard.state.install_dir.clone();
            guard.outcome = FallbackOutcome::Completed;
            drop(guard);
            if launch && exe.is_file() {
                let _ = std::process::Command::new(&exe).current_dir(cwd).spawn();
            }
            unsafe {
                let _ = DestroyWindow(hwnd);
            }
            return;
        } else {
            if !can_start_fallback_action(&guard.state) {
                return;
            }
            guard.state.prepare_for_install();
            guard.fatal_error = None;
            let opts = InstallOptions {
                target_dir: guard.state.install_dir.clone(),
                create_desktop_shortcut: guard.state.create_desktop_shortcut,
                start_with_windows: guard.state.start_with_windows,
                clean_install: guard.state.clean_install,
            };
            (hwnd, false, guard.install_runner, Some(opts), None, false)
        }
    };

    let (tx, rx) = channel::<InstallProgressEvent>();
    let cancel_flag = Arc::new(AtomicBool::new(false));
    {
        let mut guard = ctx.borrow_mut();
        guard.rx = Some(rx);
        guard.cancel_flag = Some(Arc::clone(&cancel_flag));
    }
    sync_ui_from_state(ctx);

    let hwnd_raw = hwnd.0 as isize;
    let notify = move || unsafe {
        let _ = PostMessageW(
            Some(HWND(hwnd_raw as *mut std::ffi::c_void)),
            WM_APP_WORKER_POLL,
            WPARAM(0),
            LPARAM(0),
        );
    };

    if is_uninstall {
        let Some(dir) = uninstall_dir else {
            return;
        };
        let _ = std::thread::Builder::new()
            .name("monitor-gdi-uninstall".to_string())
            .spawn(move || {
                match execute_uninstallation_at(&dir) {
                    Ok(()) => {
                        if wipe_data {
                            execute_clean_wipe();
                        }
                        let _ = tx.send(InstallProgressEvent::Completed {
                            estimated_size_kb: 0,
                            is_upgrade: false,
                        });
                    }
                    Err(err) => {
                        let _ = tx.send(InstallProgressEvent::Failed {
                            error: format!("{err:#}"),
                        });
                    }
                }
                notify();
            });
    } else if let (Some(runner), Some(opts)) = (install_runner, options) {
        let _ = std::thread::Builder::new()
            .name("monitor-gdi-install".to_string())
            .spawn(move || {
                let (inner_tx, inner_rx) = channel::<InstallProgressEvent>();
                let tx_fwd = tx.clone();
                let fwd_handle = std::thread::Builder::new()
                    .name("monitor-gdi-fwd".to_string())
                    .spawn(move || {
                        while let Ok(ev) = inner_rx.recv() {
                            let _ = tx_fwd.send(ev);
                            unsafe {
                                let _ = PostMessageW(
                                    Some(HWND(hwnd_raw as *mut std::ffi::c_void)),
                                    WM_APP_WORKER_POLL,
                                    WPARAM(0),
                                    LPARAM(0),
                                );
                            }
                        }
                    });
                if let Err(err) = runner(opts, &cancel_flag, inner_tx) {
                    let _ = tx.send(InstallProgressEvent::Failed {
                        error: format!("{err:#}"),
                    });
                    notify();
                }
                if let Ok(h) = fwd_handle {
                    let _ = h.join();
                }
                notify();
            });
    }
}

fn drain_worker_events(ctx: &SharedCtx) {
    let mut events = Vec::new();
    {
        let guard = ctx.borrow();
        if let Some(ref rx) = guard.rx {
            while let Ok(ev) = rx.try_recv() {
                events.push(ev);
            }
        }
    }
    if events.is_empty() {
        return;
    }

    let mut error_to_alert = None;
    {
        let mut guard = ctx.borrow_mut();
        for ev in events {
            if guard.state.is_uninstall_mode {
                match ev {
                    InstallProgressEvent::Completed { .. } => {
                        guard.state.is_uninstalling = false;
                        guard.state.uninstall_done = true;
                        guard.state.uninstall_error = None;
                        guard.outcome = FallbackOutcome::Completed;
                        guard.finished = true;
                    }
                    InstallProgressEvent::Failed { error } => {
                        guard.state.is_uninstalling = false;
                        guard.state.uninstall_error = Some(error.clone());
                        guard.fatal_error = Some(error.clone());
                        error_to_alert = Some(error);
                    }
                    _ => {}
                }
            } else {
                match &ev {
                    InstallProgressEvent::Completed { .. } => {
                        guard.finished = true;
                        guard.outcome = FallbackOutcome::Completed;
                        guard.cancel_flag = None;
                    }
                    InstallProgressEvent::Failed { error } => {
                        guard.fatal_error = Some(error.clone());
                        guard.cancel_flag = None;
                        error_to_alert = Some(error.clone());
                    }
                    InstallProgressEvent::Cancelled => {
                        guard.cancel_flag = None;
                    }
                    _ => {}
                }
                guard.state.apply_progress_event(ev);
            }
        }
    }

    sync_ui_from_state(ctx);
    if let Some(err) = error_to_alert {
        show_notice_box("Monitor Setup Error", &err, true);
    }
}

pub(super) unsafe extern "system" fn window_proc(
    hwnd: HWND,
    msg: u32,
    wparam: WPARAM,
    lparam: LPARAM,
) -> LRESULT {
    let ptr = unsafe { GetWindowLongPtrW(hwnd, GWLP_USERDATA) };
    if ptr == 0 {
        return unsafe { DefWindowProcW(hwnd, msg, wparam, lparam) };
    }
    if msg == WM_NCDESTROY {
        unsafe {
            SetWindowLongPtrW(hwnd, GWLP_USERDATA, 0);
            let _ = Rc::from_raw(ptr as *const RefCell<FallbackContext>);
            PostQuitMessage(0);
        }
        return LRESULT(0);
    }

    let rc = unsafe { Rc::from_raw(ptr as *const RefCell<FallbackContext>) };
    let rc_clone = Rc::clone(&rc);
    std::mem::forget(rc);

    match msg {
        WM_APP_WORKER_POLL => {
            drain_worker_events(&rc_clone);
            LRESULT(0)
        }
        WM_COMMAND => {
            match wparam.0 & 0xFFFF {
                ID_CHECK_EULA => {
                    let h = rc_clone.borrow().controls.as_ref().map(|c| c.chk_eula);
                    if let Some(h) = h {
                        rc_clone.borrow_mut().state.eula_agreed = is_checked(h);
                    }
                    sync_ui_from_state(&rc_clone);
                }
                ID_CHECK_DESKTOP => {
                    let h = rc_clone.borrow().controls.as_ref().map(|c| c.chk_desktop);
                    if let Some(h) = h {
                        rc_clone.borrow_mut().state.create_desktop_shortcut = is_checked(h);
                    }
                }
                ID_CHECK_STARTUP => {
                    let h = rc_clone.borrow().controls.as_ref().map(|c| c.chk_startup);
                    if let Some(h) = h {
                        rc_clone.borrow_mut().state.start_with_windows = is_checked(h);
                    }
                }
                ID_CHECK_CLEAN => {
                    let h = rc_clone.borrow().controls.as_ref().map(|c| c.chk_clean);
                    if let Some(h) = h {
                        rc_clone.borrow_mut().state.clean_install = is_checked(h);
                    }
                }
                ID_CHECK_LAUNCH => {
                    let h = rc_clone.borrow().controls.as_ref().map(|c| c.chk_launch);
                    if let Some(h) = h {
                        rc_clone.borrow_mut().state.launch_on_finish = is_checked(h);
                    }
                }
                ID_CHECK_WIPE_DATA => {
                    let h = rc_clone.borrow().controls.as_ref().map(|c| c.chk_wipe_data);
                    if let Some(h) = h {
                        rc_clone.borrow_mut().state.uninstall_wipe_data = is_checked(h);
                    }
                }
                ID_BTN_BROWSE => handle_browse(&rc_clone),
                ID_BTN_ACTION => start_worker(&rc_clone),
                ID_BTN_CANCEL => request_cancel_or_close(hwnd, &rc_clone),
                _ => {}
            }
            LRESULT(0)
        }
        WM_CLOSE => {
            request_cancel_or_close(hwnd, &rc_clone);
            LRESULT(0)
        }
        _ => unsafe { DefWindowProcW(hwnd, msg, wparam, lparam) },
    }
}

fn request_cancel_or_close(hwnd: HWND, ctx: &SharedCtx) {
    let (busy_install, busy_uninstall, cancel_arc) = {
        let mut guard = ctx.borrow_mut();
        if guard.state.is_installing {
            guard.state.is_cancelling = true;
            guard.state.status_message = "Cancelling and rolling back changes...".to_string();
            (true, false, guard.cancel_flag.clone())
        } else {
            (false, guard.state.is_uninstalling, None)
        }
    };
    if busy_install {
        if let Some(flag) = cancel_arc {
            flag.store(true, Ordering::Relaxed);
        }
        sync_ui_from_state(ctx);
    } else if !busy_uninstall {
        unsafe {
            let _ = DestroyWindow(hwnd);
        }
    }
}
