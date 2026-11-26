use crate::icons::{ICON_CHECK, svg_icon};
use crate::theme::InstallerTheme;
use gpui_kit::{prelude::FluentBuilder, *};
use std::path::Path;

pub fn render_finish_page<FFinish, FOpenFolder, FLaunch>(
    install_dir: &Path,
    launch_now: bool,
    is_upgrade: bool,
    theme: &InstallerTheme,
    on_finish: FFinish,
    on_open_folder: FOpenFolder,
    on_toggle_launch: FLaunch,
) -> impl IntoElement
where
    FFinish: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FOpenFolder: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FLaunch: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    let dir_display = install_dir.to_string_lossy().to_string();

    div()
        .flex()
        .flex_col()
        .justify_between()
        .w_full()
        .h_full()
        // Top Content
        .child(
            div()
                .flex()
                .flex_col()
                .gap(px(16.0))
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .gap(px(4.0))
                        .child(
                            div()
                                .text_color(theme.text_primary)
                                .font_weight(FontWeight::BOLD)
                                .text_size(px(23.0))
                                .child(if is_upgrade {
                                    "Upgrade Completed"
                                } else {
                                    "Setup Completed"
                                }),
                        )
                        .child(
                            div()
                                .text_color(theme.accent_purple)
                                .font_weight(FontWeight::SEMIBOLD)
                                .text_size(px(13.0))
                                .child(concat!(
                                    "Monitor v",
                                    env!("CARGO_PKG_VERSION"),
                                    " is ready on your Windows workstation."
                                )),
                        ),
                )
                // Success Info Card
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .gap(px(8.0))
                        .p(px(12.0))
                        .rounded(px(10.0))
                        .bg(theme.card_bg)
                        .border_1()
                        .border_color(theme.card_border)
                        .child(
                            div()
                                .flex()
                                .flex_row()
                                .items_center()
                                .gap(px(8.0))
                                .child(
                                    div()
                                        .flex()
                                        .items_center()
                                        .justify_center()
                                        .w(px(22.0))
                                        .h(px(22.0))
                                        .rounded_full()
                                        .bg(theme.success_bg)
                                        .border_1()
                                        .border_color(theme.success_border)
                                        .child(svg_icon(ICON_CHECK, 11.0, theme.success_fg)),
                                )
                                .child(
                                    div()
                                        .text_color(theme.text_primary)
                                        .font_weight(FontWeight::SEMIBOLD)
                                        .text_size(px(12.5))
                                        .child("Installation destination:"),
                                ),
                        )
                        .child(
                            div()
                                .text_color(theme.text_secondary)
                                .text_size(px(11.0))
                                .px(px(4.0))
                                .child(dir_display),
                        ),
                )
                // Launch Now Checkbox
                .child(
                    div()
                        .id("row-launch-now")
                        .flex()
                        .flex_row()
                        .items_center()
                        .gap(px(10.0))
                        .p(px(10.0))
                        .rounded(px(8.0))
                        .bg(theme.card_bg)
                        .border_1()
                        .border_color(theme.card_border)
                        .cursor_pointer()
                        .hover(|s| s.bg(rgba(0xffffff08)))
                        .on_click(on_toggle_launch)
                        .child(
                            div()
                                .flex()
                                .items_center()
                                .justify_center()
                                .w(px(16.0))
                                .h(px(16.0))
                                .rounded(px(4.0))
                                .bg(if launch_now {
                                    theme.accent_purple
                                } else {
                                    rgba(0x00000000)
                                })
                                .border_1()
                                .border_color(if launch_now {
                                    theme.accent_purple
                                } else {
                                    theme.step_track
                                })
                                .when(launch_now, |s| {
                                    s.child(svg_icon(ICON_CHECK, 9.5, rgba(0xffffffff)))
                                }),
                        )
                        .child(
                            div()
                                .flex()
                                .flex_col()
                                .gap(px(1.0))
                                .child(
                                    div()
                                        .text_color(theme.text_primary)
                                        .font_weight(FontWeight::SEMIBOLD)
                                        .text_size(px(12.5))
                                        .child("Launch Monitor now"),
                                )
                                .child(
                                    div()
                                        .text_color(theme.text_secondary)
                                        .text_size(px(10.5))
                                        .child("Start the background host service and open the system tray"),
                                ),
                        ),
                ),
        )
        // Bottom Action Bar
        .child(
            div()
                .flex()
                .flex_row()
                .items_center()
                .justify_between()
                .w_full()
                .pt(px(12.0))
                .border_t_1()
                .border_color(theme.card_border)
                .child(
                    div()
                        .id("btn-open-folder")
                        .flex()
                        .items_center()
                        .justify_center()
                        .px(px(14.0))
                        .py(px(7.0))
                        .rounded(px(7.0))
                        .bg(theme.btn_secondary_bg)
                        .border_1()
                        .border_color(theme.btn_secondary_border)
                        .cursor_pointer()
                        .hover(|s| s.bg(theme.btn_secondary_hover))
                        .on_click(on_open_folder)
                        .child(
                            div()
                                .text_color(theme.btn_secondary_text)
                                .font_weight(FontWeight::MEDIUM)
                                .text_size(px(11.5))
                                .child("Open Folder"),
                        ),
                )
                .child(
                    div()
                        .id("btn-finish")
                        .flex()
                        .items_center()
                        .justify_center()
                        .px(px(24.0))
                        .py(px(7.5))
                        .rounded(px(7.0))
                        .bg(theme.btn_primary_bg)
                        .cursor_pointer()
                        .hover(|s| s.bg(theme.btn_primary_hover))
                        .on_click(on_finish)
                        .child(
                            div()
                                .text_color(theme.btn_primary_text)
                                .font_weight(FontWeight::BOLD)
                                .text_size(px(12.5))
                                .child("Finish"),
                        ),
                ),
        )
}
