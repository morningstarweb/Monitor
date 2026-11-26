#[cfg(windows)]
use raw_window_handle::{HasWindowHandle, RawWindowHandle};

#[cfg(windows)]
const DWMWA_WINDOW_CORNER_PREFERENCE: u32 = 33;
#[cfg(windows)]
const DWMWA_BORDER_COLOR: u32 = 34;
#[cfg(windows)]
const DWMWCP_DONOTROUND: u32 = 1;
#[cfg(windows)]
const DWMWA_COLOR_NONE: u32 = 0xFFFFFFFE;

#[cfg(windows)]
const TABLET_DISABLE_PRESSANDHOLD: usize = 0x00000001;
#[cfg(windows)]
const TABLET_DISABLE_PENTAPFEEDBACK: usize = 0x00000008;
#[cfg(windows)]
const TABLET_DISABLE_PENBARRELFEEDBACK: usize = 0x00000010;
#[cfg(windows)]
const TABLET_DISABLE_TOUCHUIFORCEOFF: usize = 0x00000200;
#[cfg(windows)]
const TABLET_DISABLE_FLICKS: usize = 0x00010000;
#[cfg(windows)]
const TABLET_DISABLE_SMOOTHSCROLLING: usize = 0x00080000;

#[cfg(windows)]
const WM_TABLET_QUERYSYSTEMGESTURESTATUS: u32 = 0x02CC;

#[cfg(windows)]
#[repr(C)]
struct Margins {
    cx_left_width: i32,
    cx_right_width: i32,
    cy_top_height: i32,
    cy_bottom_height: i32,
}

#[cfg(windows)]
#[link(name = "dwmapi")]
unsafe extern "system" {
    fn DwmSetWindowAttribute(
        hwnd: *mut std::ffi::c_void,
        dw_attribute: u32,
        pv_attribute: *const std::ffi::c_void,
        cb_attribute: u32,
    ) -> i32;
    fn DwmExtendFrameIntoClientArea(
        hwnd: *mut std::ffi::c_void,
        p_mar_inset: *const Margins,
    ) -> i32;
}

#[cfg(windows)]
#[link(name = "user32")]
unsafe extern "system" {
    fn SetPropW(
        hwnd: *mut std::ffi::c_void,
        lp_string: *const u16,
        h_data: *mut std::ffi::c_void,
    ) -> i32;
}

#[cfg(windows)]
#[link(name = "comctl32")]
unsafe extern "system" {
    fn SetWindowSubclass(
        hwnd: *mut std::ffi::c_void,
        pfn_subclass: Option<
            unsafe extern "system" fn(
                hwnd: *mut std::ffi::c_void,
                u_msg: u32,
                w_param: usize,
                l_param: isize,
                u_id_subclass: usize,
                dw_ref_data: usize,
            ) -> isize,
        >,
        u_id_subclass: usize,
        dw_ref_data: usize,
    ) -> i32;
    fn DefSubclassProc(
        hwnd: *mut std::ffi::c_void,
        u_msg: u32,
        w_param: usize,
        l_param: isize,
    ) -> isize;
}

#[cfg(windows)]
unsafe extern "system" fn tablet_gesture_subclass_proc(
    hwnd: *mut std::ffi::c_void,
    msg: u32,
    w_param: usize,
    l_param: isize,
    _id: usize,
    ref_data: usize,
) -> isize {
    if msg == WM_TABLET_QUERYSYSTEMGESTURESTATUS {
        // Return gesture disable mask to completely suppress the press-and-hold square box!
        return ref_data as isize;
    }
    unsafe { DefSubclassProc(hwnd, msg, w_param, l_param) }
}

#[cfg(windows)]
pub fn remove_window_border(window: &gpui_kit::Window) {
    if let Ok(handle) = HasWindowHandle::window_handle(window)
        && let RawWindowHandle::Win32(win32) = handle.as_raw()
    {
        let hwnd = win32.hwnd.get() as *mut std::ffi::c_void;
        unsafe {
            // 1. Explicit square outer window box for classy layered aesthetic
            let corner_pref = DWMWCP_DONOTROUND;
            let _ = DwmSetWindowAttribute(
                hwnd,
                DWMWA_WINDOW_CORNER_PREFERENCE,
                &corner_pref as *const _ as *const _,
                std::mem::size_of_val(&corner_pref) as u32,
            );

            // 2. Suppress the 1px DWM window border entirely
            let border_color = DWMWA_COLOR_NONE;
            let _ = DwmSetWindowAttribute(
                hwnd,
                DWMWA_BORDER_COLOR,
                &border_color as *const _ as *const _,
                std::mem::size_of_val(&border_color) as u32,
            );

            // 3. Extend frame across entire window to create outer square glass plate
            let margins = Margins {
                cx_left_width: -1,
                cx_right_width: -1,
                cy_top_height: -1,
                cy_bottom_height: -1,
            };
            let _ = DwmExtendFrameIntoClientArea(hwnd, &margins);

            // 4. Disable Windows Press-and-Hold gesture feedback (the black-bordered square box)
            let tablet_flags = TABLET_DISABLE_PRESSANDHOLD
                | TABLET_DISABLE_PENTAPFEEDBACK
                | TABLET_DISABLE_PENBARRELFEEDBACK
                | TABLET_DISABLE_TOUCHUIFORCEOFF
                | TABLET_DISABLE_FLICKS
                | TABLET_DISABLE_SMOOTHSCROLLING;

            // UTF-16 "MicrosoftTabletPenServiceProperty\0"
            let prop_name: [u16; 34] = [
                0x004D, 0x0069, 0x0063, 0x0072, 0x006F, 0x0073, 0x006F, 0x0066, 0x0074, 0x0054,
                0x0061, 0x0062, 0x006C, 0x0065, 0x0074, 0x0050, 0x0065, 0x006E, 0x0053, 0x0065,
                0x0072, 0x0076, 0x0069, 0x0063, 0x0065, 0x0050, 0x0072, 0x006F, 0x0070, 0x0065,
                0x0072, 0x0074, 0x0079, 0x0000,
            ];
            let _ = SetPropW(
                hwnd,
                prop_name.as_ptr(),
                tablet_flags as *mut std::ffi::c_void,
            );

            let _ = SetWindowSubclass(hwnd, Some(tablet_gesture_subclass_proc), 4242, tablet_flags);
        }
    }
}

#[cfg(not(windows))]
pub fn remove_window_border(_window: &gpui_kit::Window) {}
