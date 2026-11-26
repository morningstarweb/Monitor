use super::state::{
    Controls, ID_BTN_ACTION, ID_BTN_BROWSE, ID_BTN_CANCEL, ID_CHECK_CLEAN, ID_CHECK_DESKTOP,
    ID_CHECK_EULA, ID_CHECK_LAUNCH, ID_CHECK_STARTUP, ID_CHECK_WIPE_DATA, ID_EDIT_DIR,
};
use super::{FallbackRunRequest, to_wide_z};
use anyhow::{Result, anyhow};
use windows::Win32::Foundation::{HINSTANCE, HWND, LPARAM, WPARAM};
use windows::Win32::Graphics::Gdi::{DEFAULT_GUI_FONT, GetStockObject};
use windows::Win32::UI::Controls::{PBM_SETRANGE32, PROGRESS_CLASSW};
use windows::Win32::UI::WindowsAndMessaging::{
    BS_AUTOCHECKBOX, BS_DEFPUSHBUTTON, BS_PUSHBUTTON, CreateWindowExW, ES_AUTOHSCROLL,
    ES_MULTILINE, ES_READONLY, HMENU, SW_HIDE, SW_SHOW, SendMessageW, ShowWindow, WINDOW_EX_STYLE,
    WINDOW_STYLE, WM_SETFONT, WS_BORDER, WS_CHILD, WS_DISABLED, WS_TABSTOP, WS_VISIBLE, WS_VSCROLL,
};
use windows::core::PCWSTR;

const EULA_TEXT: &str = include_str!("../../../../../installer/EULA.txt");

#[allow(clippy::too_many_arguments)]
fn make_control(
    parent: HWND,
    hinst: HINSTANCE,
    font: windows::Win32::Graphics::Gdi::HGDIOBJ,
    cls: &[u16],
    text: &str,
    style: WINDOW_STYLE,
    x: i32,
    y: i32,
    w: i32,
    h: i32,
    id: usize,
) -> Result<HWND> {
    let text_w = to_wide_z(text);
    let child = unsafe {
        CreateWindowExW(
            WINDOW_EX_STYLE(0),
            PCWSTR::from_raw(cls.as_ptr()),
            PCWSTR::from_raw(text_w.as_ptr()),
            WS_CHILD | WS_VISIBLE | style,
            x,
            y,
            w,
            h,
            Some(parent),
            Some(HMENU(id as *mut std::ffi::c_void)),
            Some(hinst),
            None,
        )
    }
    .map_err(|e| anyhow!("CreateWindowExW child failed ({text}): {e}"))?;
    unsafe {
        SendMessageW(
            child,
            WM_SETFONT,
            Some(WPARAM(font.0 as usize)),
            Some(LPARAM(1)),
        );
    }
    Ok(child)
}

pub(super) fn create_child_controls(
    hwnd: HWND,
    hinst: HINSTANCE,
    request: &FallbackRunRequest,
) -> Result<Controls> {
    let font = unsafe { GetStockObject(DEFAULT_GUI_FONT) };
    let static_cls = to_wide_z("STATIC");
    let edit_cls = to_wide_z("EDIT");
    let button_cls = to_wide_z("BUTTON");
    let is_uninstall = request.initial_state.is_uninstall_mode;

    let header_title = if is_uninstall {
        "Uninstall Monitor (Compatibility UI)"
    } else if request.initial_state.is_upgrade_target() {
        "Update Monitor (Compatibility UI)"
    } else {
        "Install Monitor (Compatibility UI)"
    };
    let subtitle = if request.triggered_by_gpu_failure {
        "Hardware graphics acceleration was unavailable; using safe Win32 compatibility mode."
    } else {
        "Morning Star Web · Source-Available (PolyForm Noncommercial 1.0.0)"
    };

    make_control(
        hwnd,
        hinst,
        font,
        &static_cls,
        header_title,
        WINDOW_STYLE(0),
        20,
        16,
        560,
        24,
        0,
    )?;
    make_control(
        hwnd,
        hinst,
        font,
        &static_cls,
        subtitle,
        WINDOW_STYLE(0),
        20,
        40,
        560,
        20,
        0,
    )?;

    let license_normalized = EULA_TEXT.replace("\r\n", "\n").replace('\n', "\r\n");
    let license_edit = make_control(
        hwnd,
        hinst,
        font,
        &edit_cls,
        &license_normalized,
        WINDOW_STYLE(ES_MULTILINE as u32 | ES_READONLY as u32) | WS_VSCROLL | WS_BORDER,
        20,
        68,
        564,
        106,
        0,
    )?;
    let chk_eula = make_control(
        hwnd,
        hinst,
        font,
        &button_cls,
        "I accept the PolyForm Noncommercial 1.0.0 License and Privacy Policy",
        WINDOW_STYLE(BS_AUTOCHECKBOX as u32) | WS_TABSTOP,
        20,
        182,
        564,
        22,
        ID_CHECK_EULA,
    )?;

    let dir_y = if is_uninstall { 74 } else { 216 };
    make_control(
        hwnd,
        hinst,
        font,
        &static_cls,
        if is_uninstall {
            "Installation folder to remove:"
        } else {
            "Installation folder:"
        },
        WINDOW_STYLE(0),
        20,
        dir_y,
        400,
        18,
        0,
    )?;
    let edit_dir = make_control(
        hwnd,
        hinst,
        font,
        &edit_cls,
        &request.initial_state.install_dir.to_string_lossy(),
        WINDOW_STYLE(ES_AUTOHSCROLL as u32 | ES_READONLY as u32) | WS_BORDER | WS_TABSTOP,
        20,
        dir_y + 22,
        466,
        24,
        ID_EDIT_DIR,
    )?;
    let btn_browse = make_control(
        hwnd,
        hinst,
        font,
        &button_cls,
        "Browse...",
        WINDOW_STYLE(BS_PUSHBUTTON as u32) | WS_TABSTOP,
        496,
        dir_y + 21,
        88,
        26,
        ID_BTN_BROWSE,
    )?;
    let lbl_validation = make_control(
        hwnd,
        hinst,
        font,
        &static_cls,
        "",
        WINDOW_STYLE(0),
        20,
        dir_y + 52,
        564,
        34,
        0,
    )?;

    let chk_desktop = make_control(
        hwnd,
        hinst,
        font,
        &button_cls,
        "Create Desktop shortcut",
        WINDOW_STYLE(BS_AUTOCHECKBOX as u32) | WS_TABSTOP,
        20,
        310,
        260,
        22,
        ID_CHECK_DESKTOP,
    )?;
    let chk_startup = make_control(
        hwnd,
        hinst,
        font,
        &button_cls,
        "Start Monitor with Windows",
        WINDOW_STYLE(BS_AUTOCHECKBOX as u32) | WS_TABSTOP,
        300,
        310,
        260,
        22,
        ID_CHECK_STARTUP,
    )?;
    let chk_clean = make_control(
        hwnd,
        hinst,
        font,
        &button_cls,
        "Clean install (wipe old user config)",
        WINDOW_STYLE(BS_AUTOCHECKBOX as u32) | WS_TABSTOP,
        20,
        336,
        260,
        22,
        ID_CHECK_CLEAN,
    )?;
    let chk_launch = make_control(
        hwnd,
        hinst,
        font,
        &button_cls,
        "Launch Monitor after setup finishes",
        WINDOW_STYLE(BS_AUTOCHECKBOX as u32) | WS_TABSTOP,
        300,
        336,
        260,
        22,
        ID_CHECK_LAUNCH,
    )?;
    let chk_wipe_data = make_control(
        hwnd,
        hinst,
        font,
        &button_cls,
        "Also remove saved pairings, keys, and local logs (%LOCALAPPDATA%\\Monitor)",
        WINDOW_STYLE(BS_AUTOCHECKBOX as u32) | WS_TABSTOP,
        20,
        132,
        560,
        24,
        ID_CHECK_WIPE_DATA,
    )?;

    let prog_y = if is_uninstall { 174 } else { 374 };
    let prog_cls = to_wide_z("msctls_progress32");
    let progress_bar = unsafe {
        CreateWindowExW(
            WINDOW_EX_STYLE(0),
            PCWSTR::from_raw(prog_cls.as_ptr()),
            PCWSTR::null(),
            WS_CHILD | WS_VISIBLE,
            20,
            prog_y,
            564,
            22,
            Some(hwnd),
            None,
            Some(hinst),
            None,
        )
    }
    .map_err(|_| anyhow!("creating Win32 progress control ({:?})", PROGRESS_CLASSW))?;
    unsafe {
        SendMessageW(
            progress_bar,
            PBM_SETRANGE32,
            Some(WPARAM(0)),
            Some(LPARAM(100)),
        );
    }

    let lbl_status = make_control(
        hwnd,
        hinst,
        font,
        &static_cls,
        "Ready.",
        WINDOW_STYLE(0),
        20,
        prog_y + 28,
        564,
        36,
        0,
    )?;

    let btn_y = if is_uninstall { 250 } else { 446 };
    let btn_action = make_control(
        hwnd,
        hinst,
        font,
        &button_cls,
        if is_uninstall { "Uninstall" } else { "Install" },
        WINDOW_STYLE(BS_DEFPUSHBUTTON as u32) | WS_TABSTOP | WS_DISABLED,
        396,
        btn_y,
        92,
        30,
        ID_BTN_ACTION,
    )?;
    let btn_cancel = make_control(
        hwnd,
        hinst,
        font,
        &button_cls,
        "Cancel",
        WINDOW_STYLE(BS_PUSHBUTTON as u32) | WS_TABSTOP,
        496,
        btn_y,
        88,
        30,
        ID_BTN_CANCEL,
    )?;

    let show = |h: HWND, visible: bool| unsafe {
        let _ = ShowWindow(h, if visible { SW_SHOW } else { SW_HIDE });
    };
    if is_uninstall {
        for h in [
            license_edit,
            chk_eula,
            btn_browse,
            lbl_validation,
            chk_desktop,
            chk_startup,
            chk_clean,
            chk_launch,
        ] {
            show(h, false);
        }
        show(chk_wipe_data, true);
    } else {
        show(chk_wipe_data, false);
    }

    Ok(Controls {
        hwnd,
        chk_eula,
        edit_dir,
        btn_browse,
        lbl_validation,
        chk_desktop,
        chk_startup,
        chk_clean,
        chk_launch,
        chk_wipe_data,
        progress_bar,
        lbl_status,
        btn_action,
        btn_cancel,
    })
}
