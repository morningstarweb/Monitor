mod complete;

use crate::icons::{ICON_CHECK, ICON_SPINNER, ICON_TRASH, ICON_WARNING, svg_icon};
use crate::theme::InstallerTheme;
use complete::{
    bullet_row, bullet_row_owned, cancel_btn, glass_card, page_header, render_uninstall_complete,
};
use gpui_kit::{prelude::FluentBuilder, *};
use std::path::Path;

#[allow(clippy::too_many_arguments)]
pub fn render_uninstaller_page<FC, FT, FU>(
    uninstall_dir: &Path,
    wipe_data: bool,
    done: bool,
    in_progress: bool,
    error_msg: Option<&str>,
    theme: &InstallerTheme,
    on_cancel: FC,
    on_toggle_wipe: FT,
    on_uninstall: FU,
) -> impl IntoElement
where
    FC: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FT: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FU: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    if done {
        render_uninstall_complete(uninstall_dir, wipe_data, theme, on_cancel).into_any_element()
    } else {
        render_uninstall_confirm(
            uninstall_dir,
            wipe_data,
            in_progress,
            error_msg,
            theme,
            on_cancel,
            on_toggle_wipe,
            on_uninstall,
        )
        .into_any_element()
    }
}

#[allow(clippy::too_many_arguments)]
fn render_uninstall_confirm<FC, FT, FU>(
    uninstall_dir: &Path,
    wipe_data: bool,
    in_progress: bool,
    error_msg: Option<&str>,
    theme: &InstallerTheme,
    on_cancel: FC,
    on_toggle_wipe: FT,
    on_uninstall: FU,
) -> impl IntoElement
where
    FC: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FT: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FU: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    let remove_msg = format!("Removes program files from {}.", uninstall_dir.display());

    div()
        .flex()
        .flex_col()
        .justify_between()
        .w_full()
        .h_full()
        .child(
            div()
                .flex()
                .flex_col()
                .gap(px(12.0))
                .child(page_header(
                    "Uninstall Monitor",
                    "Remove Monitor host service, components, and files from this PC",
                    theme,
                ))
                .child(
                    glass_card(theme)
                        .child(
                            div()
                                .text_color(theme.text_primary)
                                .font_weight(FontWeight::SEMIBOLD)
                                .text_size(px(12.5))
                                .child("The uninstaller will perform the following actions:"),
                        )
                        .child(bullet_row(
                            "Terminates active Monitor Windows host and background processes.",
                            theme,
                        ))
                        .child(bullet_row_owned(remove_msg, theme))
                        .child(bullet_row(
                            "Deletes Start Menu and Desktop shortcuts, and unregisters auto-startup.",
                            theme,
                        )),
                )
                .child(
                    div()
                        .id("uninstaller-wipe-checkbox")
                        .flex()
                        .flex_row()
                        .items_center()
                        .gap(px(12.0))
                        .p(px(12.0))
                        .rounded(px(10.0))
                        .bg(theme.card_bg)
                        .border_1()
                        .border_color(if wipe_data {
                            theme.accent_purple
                        } else {
                            theme.card_border
                        })
                        .cursor_pointer()
                        .hover(|s| s.bg(rgba(0xffffff08)))
                        .on_click(on_toggle_wipe)
                        .child(
                            div()
                                .flex()
                                .items_center()
                                .justify_center()
                                .w(px(18.0))
                                .h(px(18.0))
                                .rounded(px(4.0))
                                .border_1()
                                .border_color(if wipe_data {
                                    theme.accent_purple
                                } else {
                                    theme.input_border
                                })
                                .bg(if wipe_data {
                                    theme.accent_purple
                                } else {
                                    rgba(0x00000000)
                                })
                                .when(wipe_data, |el| {
                                    el.child(svg_icon(ICON_CHECK, 10.0, rgba(0xffffffff)))
                                }),
                        )
                        .child(
                            div()
                                .flex()
                                .flex_col()
                                .gap(px(2.0))
                                .child(
                                    div()
                                        .text_color(theme.text_primary)
                                        .font_weight(FontWeight::SEMIBOLD)
                                        .text_size(px(12.5))
                                        .child("Also remove configuration, logs, and device pairings"),
                                )
                                .child(
                                    div()
                                        .text_color(theme.text_muted)
                                        .text_size(px(11.0))
                                        .child(
                                            "Permanently deletes local cache, metrics, and pairings from %LOCALAPPDATA%\\Monitor",
                                        ),
                                ),
                        ),
                )
                .when_some(error_msg, |el, err| {
                    el.child(
                        div()
                            .flex()
                            .flex_row()
                            .items_center()
                            .gap(px(8.0))
                            .p(px(10.0))
                            .rounded(px(8.0))
                            .bg(theme.danger_bg)
                            .border_1()
                            .border_color(theme.danger_border)
                            .child(svg_icon(ICON_WARNING, 14.0, theme.danger_fg))
                            .child(
                                div()
                                    .flex_1()
                                    .text_color(theme.danger_fg)
                                    .text_size(px(11.0))
                                    .whitespace_normal()
                                    .child(err.to_string()),
                            ),
                    )
                }),
        )
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
                        .text_color(theme.text_muted)
                        .text_size(px(11.0))
                        .child("Click Uninstall to proceed or Cancel to exit."),
                )
                .child(
                    div()
                        .flex()
                        .flex_row()
                        .items_center()
                        .gap(px(10.0))
                        .child(cancel_btn("btn-uninstall-cancel", "Cancel", theme, on_cancel))
                        .child(uninstall_action_btn(in_progress, on_uninstall)),
                ),
        )
}

fn uninstall_action_btn<FU>(in_progress: bool, on_uninstall: FU) -> impl IntoElement
where
    FU: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    let btn = div()
        .id("btn-uninstall-confirm")
        .flex()
        .items_center()
        .justify_center()
        .gap(px(6.0))
        .w(px(114.0))
        .h(px(34.0))
        .rounded(px(6.0))
        .bg(rgba(0xef4444ee))
        .border_1()
        .border_color(rgba(0xef444488))
        .child(if in_progress {
            svg_icon(ICON_SPINNER, 12.0, rgba(0xffffffff)).into_any_element()
        } else {
            svg_icon(ICON_TRASH, 12.0, rgba(0xffffffff)).into_any_element()
        })
        .child(
            div()
                .text_color(rgba(0xffffffff))
                .font_weight(FontWeight::SEMIBOLD)
                .text_size(px(12.0))
                .child(if in_progress {
                    "Removing…"
                } else {
                    "Uninstall"
                }),
        );
    if in_progress {
        btn.opacity(0.6)
    } else {
        btn.cursor_pointer()
            .hover(|s| s.bg(rgba(0xdc2626ff)))
            .on_click(on_uninstall)
    }
}
