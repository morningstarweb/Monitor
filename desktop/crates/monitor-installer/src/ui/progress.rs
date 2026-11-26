use crate::icons::{ICON_CHECK, ICON_SPINNER, ICON_WARNING, svg_icon};
use crate::theme::InstallerTheme;
use gpui_kit::{prelude::FluentBuilder, *};

#[allow(clippy::too_many_arguments)]
pub fn render_progress_page<FCancel, FRetry, FCloseError>(
    progress_pct: f32,
    status_msg: &str,
    files_progress: Option<(u64, u64)>,
    task_host_done: bool,
    task_git_done: bool,
    task_shortcuts_done: bool,
    is_cancelling: bool,
    install_error: Option<&str>,
    theme: &InstallerTheme,
    on_cancel: FCancel,
    on_retry: FRetry,
    on_close_error: FCloseError,
) -> impl IntoElement
where
    FCancel: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FRetry: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FCloseError: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    let clamped_pct = progress_pct.clamp(0.0, 100.0);
    let fill_fraction = (clamped_pct / 100.0).clamp(0.02, 1.0);
    let files_label = files_progress
        .map(|(done, total)| format!("{done}/{total} files"))
        .unwrap_or_default();

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
                .gap(px(14.0))
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
                                .child(if install_error.is_some() {
                                    "Installation Interrupted"
                                } else {
                                    "Installing Monitor"
                                }),
                        )
                        .child(
                            div()
                                .text_color(theme.text_secondary)
                                .text_size(px(12.0))
                                .child(if install_error.is_some() {
                                    "An error prevented installation from completing."
                                } else {
                                    "Unpacking verified components and configuring the Windows host."
                                }),
                        ),
                )
                // Full-width responsive Progress Bar Container
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .gap(px(7.0))
                        .w_full()
                        .pt(px(4.0))
                        .child(
                            div()
                                .flex()
                                .items_center()
                                .w_full()
                                .h(px(10.0))
                                .rounded_full()
                                .bg(theme.progress_track)
                                .overflow_hidden()
                                .child(
                                    div()
                                        .h_full()
                                        .w(relative(fill_fraction))
                                        .rounded_full()
                                        .bg(if install_error.is_some() {
                                            theme.danger_fg
                                        } else {
                                            theme.accent_purple
                                        }),
                                ),
                        )
                        .child(
                            div()
                                .flex()
                                .flex_row()
                                .items_center()
                                .justify_between()
                                .w_full()
                                .gap(px(8.0))
                                .child(
                                    div()
                                        .flex_1()
                                        .min_w(px(0.0))
                                        .text_color(theme.text_secondary)
                                        .text_size(px(11.0))
                                        .overflow_hidden()
                                        .child(status_msg.to_string()),
                                )
                                .when(!files_label.is_empty(), |el| {
                                    el.child(
                                        div()
                                            .text_color(theme.text_muted)
                                            .text_size(px(10.5))
                                            .child(files_label),
                                    )
                                })
                                .child(
                                    div()
                                        .text_color(theme.text_primary)
                                        .font_weight(FontWeight::BOLD)
                                        .text_size(px(12.0))
                                        .child(format!("{:.0}%", clamped_pct)),
                                ),
                        ),
                )
                .when_some(install_error, |el, err_msg| {
                    el.child(
                        div()
                            .flex()
                            .flex_row()
                            .items_start()
                            .gap(px(10.0))
                            .p(px(12.0))
                            .rounded(px(8.0))
                            .bg(theme.danger_bg)
                            .border_1()
                            .border_color(theme.danger_border)
                            .child(svg_icon(ICON_WARNING, 16.0, theme.danger_fg))
                            .child(
                                div()
                                    .flex_1()
                                    .text_color(theme.danger_fg)
                                    .text_size(px(11.0))
                                    .whitespace_normal()
                                    .child(err_msg.to_string()),
                            ),
                    )
                })
                .when(install_error.is_none(), |el| {
                    el.child(
                        div()
                            .flex()
                            .flex_col()
                            .gap(px(10.0))
                            .pt(px(6.0))
                            .child(render_task_item(
                                task_host_done,
                                !task_host_done && clamped_pct < 86.0,
                                "Monitor host binary & runtime libraries",
                                theme,
                            ))
                            .child(render_task_item(
                                task_git_done,
                                task_host_done && !task_git_done,
                                "Bundled Git tools & MinGit runtime",
                                theme,
                            ))
                            .child(render_task_item(
                                task_shortcuts_done,
                                task_git_done && !task_shortcuts_done,
                                "Windows shortcuts & autostart integration",
                                theme,
                            )),
                    )
                }),
        )
        // Bottom Action Bar
        .child(
            div()
                .flex()
                .flex_row()
                .items_center()
                .justify_end()
                .w_full()
                .pt(px(12.0))
                .border_t_1()
                .border_color(theme.card_border)
                .when_some(install_error, |el, _| {
                    el.child(
                        div()
                            .flex()
                            .flex_row()
                            .items_center()
                            .gap(px(10.0))
                            .child(
                                div()
                                    .id("btn-error-close")
                                    .flex()
                                    .items_center()
                                    .justify_center()
                                    .px(px(16.0))
                                    .py(px(7.0))
                                    .rounded(px(7.0))
                                    .bg(theme.btn_secondary_bg)
                                    .border_1()
                                    .border_color(theme.btn_secondary_border)
                                    .cursor_pointer()
                                    .hover(|s| s.bg(theme.btn_secondary_hover))
                                    .on_click(on_close_error)
                                    .child(
                                        div()
                                            .text_color(theme.btn_secondary_text)
                                            .font_weight(FontWeight::MEDIUM)
                                            .text_size(px(12.0))
                                            .child("Close"),
                                    ),
                            )
                            .child(
                                div()
                                    .id("btn-error-retry")
                                    .flex()
                                    .items_center()
                                    .justify_center()
                                    .px(px(20.0))
                                    .py(px(7.0))
                                    .rounded(px(7.0))
                                    .bg(theme.btn_primary_bg)
                                    .cursor_pointer()
                                    .hover(|s| s.bg(theme.btn_primary_hover))
                                    .on_click(on_retry)
                                    .child(
                                        div()
                                            .text_color(theme.btn_primary_text)
                                            .font_weight(FontWeight::BOLD)
                                            .text_size(px(12.0))
                                            .child("Retry"),
                                    ),
                            ),
                    )
                })
                .when(install_error.is_none(), |el| {
                    let cancel_btn = div()
                        .id("btn-cancel-install")
                        .flex()
                        .items_center()
                        .justify_center()
                        .px(px(16.0))
                        .py(px(7.0))
                        .rounded(px(7.0))
                        .bg(theme.btn_secondary_bg)
                        .border_1()
                        .border_color(theme.btn_secondary_border)
                        .opacity(if is_cancelling { 0.55 } else { 1.0 });

                    let cancel_el = if is_cancelling {
                        cancel_btn.child(
                            div()
                                .text_color(theme.text_muted)
                                .font_weight(FontWeight::MEDIUM)
                                .text_size(px(12.0))
                                .child("Cancelling…"),
                        )
                    } else {
                        cancel_btn
                            .cursor_pointer()
                            .hover(|s| s.bg(theme.btn_secondary_hover))
                            .on_click(on_cancel)
                            .child(
                                div()
                                    .text_color(theme.btn_secondary_text)
                                    .font_weight(FontWeight::MEDIUM)
                                    .text_size(px(12.0))
                                    .child("Cancel"),
                            )
                    };
                    el.child(cancel_el)
                }),
        )
}

fn render_task_item(
    completed: bool,
    active: bool,
    label: &'static str,
    theme: &InstallerTheme,
) -> impl IntoElement {
    div()
        .flex()
        .flex_row()
        .items_center()
        .gap(px(10.0))
        .child(
            div()
                .flex()
                .items_center()
                .justify_center()
                .w(px(16.0))
                .h(px(16.0))
                .rounded(px(4.0))
                .bg(if completed {
                    theme.accent_purple
                } else if active {
                    theme.accent_purple_light
                } else {
                    rgba(0x00000000)
                })
                .border_1()
                .border_color(if completed || active {
                    theme.accent_purple
                } else {
                    theme.step_track
                })
                .child(if completed {
                    div()
                        .child(svg_icon(ICON_CHECK, 9.5, rgba(0xffffffff)))
                        .into_any_element()
                } else if active {
                    div()
                        .child(svg_icon(ICON_SPINNER, 10.0, theme.accent_purple))
                        .into_any_element()
                } else {
                    div().into_any_element()
                }),
        )
        .child(
            div()
                .text_color(if completed || active {
                    theme.text_primary
                } else {
                    theme.text_muted
                })
                .font_weight(if active {
                    FontWeight::SEMIBOLD
                } else {
                    FontWeight::NORMAL
                })
                .text_size(px(12.0))
                .child(label),
        )
}
