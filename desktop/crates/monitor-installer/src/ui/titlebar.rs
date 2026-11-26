use crate::icons::{ICON_CLOSE, ICON_MINIMIZE, ICON_MONITOR_LOGO, svg_icon};
use crate::theme::InstallerTheme;
use gpui_kit::*;

pub fn minimize_window(window: &mut Window) {
    #[cfg(windows)]
    {
        use raw_window_handle::{HasWindowHandle, RawWindowHandle};
        if let Ok(handle) = window.window_handle()
            && let RawWindowHandle::Win32(h) = handle.as_raw()
        {
            #[link(name = "user32")]
            unsafe extern "system" {
                fn ShowWindow(hwnd: *mut std::ffi::c_void, n_cmd_show: i32) -> i32;
            }
            const SW_MINIMIZE: i32 = 6;
            // SAFETY: Valid Win32 HWND retrieved from GPUI window handle.
            unsafe {
                ShowWindow(h.hwnd.get() as *mut std::ffi::c_void, SW_MINIMIZE);
            }
        }
    }
    #[cfg(not(windows))]
    {
        let _ = window;
    }
}

pub fn render_titlebar<FMin, FClose>(
    title: &str,
    theme: &InstallerTheme,
    on_minimize: FMin,
    on_close: FClose,
) -> impl IntoElement
where
    FMin: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FClose: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    div()
        .flex()
        .flex_row()
        .items_center()
        .justify_between()
        .w_full()
        .h(px(34.0))
        .bg(theme.titlebar_bg)
        .border_b_1()
        .border_color(theme.titlebar_border)
        // Draggable left + center title strip
        .child(
            div()
                .id("titlebar-drag-strip")
                .flex()
                .flex_1()
                .h_full()
                .flex_row()
                .items_center()
                .px(px(14.0))
                .gap(px(8.0))
                .window_control_area(WindowControlArea::Drag)
                .on_mouse_down(MouseButton::Left, |_, window, _| {
                    window.start_window_move();
                })
                .child(svg_icon(ICON_MONITOR_LOGO, 14.0, theme.accent_purple))
                .child(
                    div()
                        .text_color(theme.text_secondary)
                        .text_size(px(11.5))
                        .font_weight(FontWeight::MEDIUM)
                        .child(title.to_string()),
                ),
        )
        // Window Control Buttons (Minimize, Close)
        .child(
            div()
                .flex()
                .flex_row()
                .items_center()
                .h_full()
                .child(
                    div()
                        .id("btn-win-min")
                        .flex()
                        .items_center()
                        .justify_center()
                        .w(px(38.0))
                        .h_full()
                        .cursor_pointer()
                        .hover(|s| s.bg(rgba(0xffffff12)))
                        .on_click(on_minimize)
                        .child(svg_icon(ICON_MINIMIZE, 10.0, theme.text_secondary)),
                )
                .child(
                    div()
                        .id("btn-win-close")
                        .flex()
                        .items_center()
                        .justify_center()
                        .w(px(38.0))
                        .h_full()
                        .cursor_pointer()
                        .hover(|s| s.bg(rgba(0xef4444ee)))
                        .on_click(on_close)
                        .child(svg_icon(ICON_CLOSE, 10.0, theme.text_secondary)),
                ),
        )
}
