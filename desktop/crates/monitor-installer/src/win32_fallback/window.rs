use super::controls::create_child_controls;
use super::events::window_proc;
use super::state::{FallbackContext, SharedCtx, sync_ui_from_state};
use super::{FallbackOutcome, FallbackRunRequest, to_wide_z};
use anyhow::{Result, anyhow};
use std::cell::RefCell;
use std::rc::Rc;
use windows::Win32::Graphics::Gdi::{COLOR_BTNFACE, HBRUSH, UpdateWindow};
use windows::Win32::System::LibraryLoader::GetModuleHandleW;
use windows::Win32::UI::Controls::{
    ICC_PROGRESS_CLASS, ICC_STANDARD_CLASSES, INITCOMMONCONTROLSEX, InitCommonControlsEx,
};
use windows::Win32::UI::WindowsAndMessaging::{
    CS_HREDRAW, CS_VREDRAW, CreateWindowExW, DispatchMessageW, GWLP_USERDATA, GetMessageW,
    GetSystemMetrics, IDC_ARROW, LoadCursorW, MSG, RegisterClassExW, SM_CXSCREEN, SM_CYSCREEN,
    SW_SHOW, SetWindowLongPtrW, ShowWindow, TranslateMessage, WINDOW_EX_STYLE, WNDCLASSEXW,
    WS_CAPTION, WS_MINIMIZEBOX, WS_OVERLAPPED, WS_SYSMENU, WS_VISIBLE,
};
use windows::core::PCWSTR;

pub(super) fn run_win32_window(request: FallbackRunRequest) -> Result<FallbackOutcome> {
    let icc = INITCOMMONCONTROLSEX {
        dwSize: std::mem::size_of::<INITCOMMONCONTROLSEX>() as u32,
        dwICC: ICC_PROGRESS_CLASS | ICC_STANDARD_CLASSES,
    };
    unsafe {
        let _ = InitCommonControlsEx(&icc);
    }

    let hinstance =
        unsafe { GetModuleHandleW(None) }.map_err(|e| anyhow!("GetModuleHandleW failed: {e}"))?;

    let class_name = to_wide_z("MonitorFallbackInstallerWnd");
    let wc = WNDCLASSEXW {
        cbSize: std::mem::size_of::<WNDCLASSEXW>() as u32,
        style: CS_HREDRAW | CS_VREDRAW,
        lpfnWndProc: Some(window_proc),
        hInstance: hinstance.into(),
        hCursor: unsafe { LoadCursorW(None, IDC_ARROW) }.unwrap_or_default(),
        hbrBackground: HBRUSH((COLOR_BTNFACE.0 + 1) as *mut std::ffi::c_void),
        lpszClassName: PCWSTR::from_raw(class_name.as_ptr()),
        ..Default::default()
    };
    unsafe {
        let _ = RegisterClassExW(&wc);
    }

    let width = 620;
    let height = if request.initial_state.is_uninstall_mode {
        340
    } else {
        530
    };
    let screen_w = unsafe { GetSystemMetrics(SM_CXSCREEN) };
    let screen_h = unsafe { GetSystemMetrics(SM_CYSCREEN) };
    let pos_x = ((screen_w - width) / 2).max(0);
    let pos_y = ((screen_h - height) / 2).max(0);

    let title = if request.initial_state.is_uninstall_mode {
        concat!(
            "Monitor Uninstaller v",
            env!("CARGO_PKG_VERSION"),
            " (Compatibility Mode)"
        )
    } else {
        concat!(
            "Monitor Setup v",
            env!("CARGO_PKG_VERSION"),
            " (Compatibility Mode)"
        )
    };
    let title_w = to_wide_z(title);

    let ctx: SharedCtx = Rc::new(RefCell::new(FallbackContext {
        state: request.initial_state.clone(),
        install_runner: request.install_runner,
        controls: None,
        rx: None,
        cancel_flag: None,
        outcome: FallbackOutcome::Cancelled,
        fatal_error: None,
        finished: false,
    }));

    let hwnd = unsafe {
        CreateWindowExW(
            WINDOW_EX_STYLE(0),
            PCWSTR::from_raw(class_name.as_ptr()),
            PCWSTR::from_raw(title_w.as_ptr()),
            WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX | WS_VISIBLE,
            pos_x,
            pos_y,
            width,
            height,
            None,
            None,
            Some(hinstance.into()),
            None,
        )
    }
    .map_err(|e| anyhow!("CreateWindowExW failed for GDI fallback window: {e}"))?;

    let raw_ctx = Rc::into_raw(Rc::clone(&ctx)) as isize;
    unsafe {
        SetWindowLongPtrW(hwnd, GWLP_USERDATA, raw_ctx);
    }

    let controls = create_child_controls(hwnd, hinstance.into(), &request)?;
    ctx.borrow_mut().controls = Some(controls);
    sync_ui_from_state(&ctx);

    unsafe {
        let _ = ShowWindow(hwnd, SW_SHOW);
        let _ = UpdateWindow(hwnd);
    }

    let mut msg = MSG::default();
    loop {
        let status = unsafe { GetMessageW(&mut msg, None, 0, 0) }.0;
        if status <= 0 {
            break;
        }
        unsafe {
            let _ = TranslateMessage(&msg);
            DispatchMessageW(&msg);
        }
    }

    let guard = ctx.borrow();
    if let Some(ref err) = guard.fatal_error {
        return Err(anyhow!("{err}"));
    }
    Ok(guard.outcome)
}
