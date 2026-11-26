use crate::icons::{ICON_CHECK, ICON_FOLDER, ICON_WARNING, svg_icon};
use crate::theme::InstallerTheme;
use gpui_kit::{prelude::FluentBuilder, *};
use std::path::Path;

fn format_bytes_human(bytes: u64) -> String {
    const GB: f64 = 1024.0 * 1024.0 * 1024.0;
    const MB: f64 = 1024.0 * 1024.0;
    if bytes as f64 >= GB {
        format!("{:.2} GB", bytes as f64 / GB)
    } else {
        format!("{:.1} MB", (bytes as f64 / MB).max(0.1))
    }
}

#[allow(clippy::too_many_arguments)]
pub fn render_location_page<FBack, FCancel, FNext, FBrowse>(
    install_dir: &Path,
    required_bytes: u64,
    available_bytes: Option<u64>,
    is_upgrade_at_target: bool,
    has_sufficient_space: bool,
    path_error: Option<&str>,
    path_warning: Option<&str>,
    theme: &InstallerTheme,
    on_back: FBack,
    on_cancel: FCancel,
    on_next: FNext,
    on_browse: FBrowse,
) -> impl IntoElement
where
    FBack: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FCancel: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FNext: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FBrowse: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    let dir_display = install_dir.to_string_lossy().to_string();
    let req_label = format_bytes_human(required_bytes);
    let avail_label = available_bytes
        .map(format_bytes_human)
        .unwrap_or_else(|| "Unknown".to_string());

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
                                .child("Choose install location"),
                        )
                        .child(
                            div()
                                .text_color(theme.text_secondary)
                                .text_size(px(12.0))
                                .child("Monitor installs cleanly for your Windows user account."),
                        ),
                )
                // Install Location Input Box
                .child(
                    div()
                        .flex()
                        .flex_row()
                        .items_center()
                        .gap(px(10.0))
                        .w_full()
                        .pt(px(4.0))
                        .child(
                            div()
                                .flex()
                                .flex_1()
                                .min_w(px(0.0))
                                .flex_row()
                                .items_center()
                                .gap(px(10.0))
                                .px(px(12.0))
                                .h(px(40.0))
                                .rounded(px(8.0))
                                .bg(theme.input_bg)
                                .border_1()
                                .border_color(theme.input_border)
                                .child(svg_icon(ICON_FOLDER, 16.0, theme.text_secondary))
                                .child(
                                    div()
                                        .flex_1()
                                        .min_w(px(0.0))
                                        .text_color(theme.text_primary)
                                        .text_size(px(12.0))
                                        .overflow_hidden()
                                        .child(dir_display),
                                ),
                        )
                        .child(
                            div()
                                .id("btn-browse")
                                .flex()
                                .items_center()
                                .justify_center()
                                .px(px(16.0))
                                .h(px(40.0))
                                .rounded(px(8.0))
                                .bg(theme.btn_secondary_bg)
                                .border_1()
                                .border_color(theme.btn_secondary_border)
                                .cursor_pointer()
                                .hover(|s| s.bg(theme.btn_secondary_hover))
                                .on_click(on_browse)
                                .child(
                                    div()
                                        .text_color(theme.btn_secondary_text)
                                        .font_weight(FontWeight::MEDIUM)
                                        .text_size(px(12.0))
                                        .child("Browse…"),
                                ),
                        ),
                )
                // Disk Space & Upgrade Info Card
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .gap(px(8.0))
                        .p(px(12.0))
                        .rounded(px(8.0))
                        .bg(theme.card_bg)
                        .border_1()
                        .border_color(if has_sufficient_space {
                            theme.card_border
                        } else {
                            theme.danger_border
                        })
                        .child(
                            div()
                                .flex()
                                .flex_row()
                                .items_center()
                                .justify_between()
                                .child(
                                    div()
                                        .text_color(theme.text_secondary)
                                        .text_size(px(11.5))
                                        .child("Space required (Monitor + bundled Git):"),
                                )
                                .child(
                                    div()
                                        .text_color(theme.text_primary)
                                        .font_weight(FontWeight::SEMIBOLD)
                                        .text_size(px(11.5))
                                        .child(req_label),
                                ),
                        )
                        .child(
                            div()
                                .flex()
                                .flex_row()
                                .items_center()
                                .justify_between()
                                .child(
                                    div()
                                        .text_color(theme.text_secondary)
                                        .text_size(px(11.5))
                                        .child("Space available on drive:"),
                                )
                                .child(
                                    div()
                                        .text_color(if has_sufficient_space {
                                            theme.success_fg
                                        } else {
                                            theme.danger_fg
                                        })
                                        .font_weight(FontWeight::SEMIBOLD)
                                        .text_size(px(11.5))
                                        .child(avail_label),
                                ),
                        ),
                )
                .when(is_upgrade_at_target, |el| {
                    el.child(
                        div()
                            .flex()
                            .flex_row()
                            .items_center()
                            .gap(px(8.0))
                            .px(px(10.0))
                            .py(px(7.0))
                            .rounded(px(7.0))
                            .bg(theme.success_bg)
                            .border_1()
                            .border_color(theme.success_border)
                            .child(svg_icon(ICON_CHECK, 11.0, theme.success_fg))
                            .child(
                                div()
                                    .flex_1()
                                    .text_color(theme.text_primary)
                                    .text_size(px(11.0))
                                    .whitespace_normal()
                                    .child("Existing Monitor installation detected here. Pairings and config will be preserved."),
                            ),
                    )
                })
                .when(!has_sufficient_space, |el| {
                    el.child(
                        div()
                            .flex()
                            .flex_row()
                            .items_center()
                            .gap(px(8.0))
                            .px(px(10.0))
                            .py(px(7.0))
                            .rounded(px(7.0))
                            .bg(theme.danger_bg)
                            .border_1()
                            .border_color(theme.danger_border)
                            .child(svg_icon(ICON_WARNING, 13.0, theme.danger_fg))
                            .child(
                                div()
                                    .flex_1()
                                    .text_color(theme.danger_fg)
                                    .text_size(px(11.0))
                                    .whitespace_normal()
                                    .child("Not enough free disk space on the selected drive. Choose another folder."),
                            ),
                    )
                })
                .when_some(path_error, |el, msg| {
                    el.child(
                        div()
                            .flex()
                            .flex_row()
                            .items_center()
                            .gap(px(8.0))
                            .px(px(10.0))
                            .py(px(7.0))
                            .rounded(px(7.0))
                            .bg(theme.danger_bg)
                            .border_1()
                            .border_color(theme.danger_border)
                            .child(svg_icon(ICON_WARNING, 13.0, theme.danger_fg))
                            .child(
                                div()
                                    .flex_1()
                                    .text_color(theme.danger_fg)
                                    .text_size(px(11.0))
                                    .whitespace_normal()
                                    .child(msg.to_string()),
                            ),
                    )
                })
                .when_some(path_warning, |el, msg| {
                    el.child(
                        div()
                            .flex()
                            .flex_row()
                            .items_center()
                            .gap(px(8.0))
                            .px(px(10.0))
                            .py(px(7.0))
                            .rounded(px(7.0))
                            .bg(theme.warning_bg)
                            .border_1()
                            .border_color(theme.warning_border)
                            .child(svg_icon(ICON_WARNING, 13.0, theme.warning_fg))
                            .child(
                                div()
                                    .flex_1()
                                    .text_color(theme.warning_fg)
                                    .text_size(px(11.0))
                                    .whitespace_normal()
                                    .child(msg.to_string()),
                            ),
                    )
                }),
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
                        .text_color(theme.text_muted)
                        .text_size(px(10.5))
                        .child("No administrator access required."),
                )
                .child(
                    div()
                        .flex()
                        .flex_row()
                        .items_center()
                        .gap(px(10.0))
                        .child(secondary_btn("btn-back", "Back", theme, on_back))
                        .child(secondary_btn("btn-cancel", "Cancel", theme, on_cancel))
                        .child(next_btn(
                            has_sufficient_space && path_error.is_none(),
                            theme,
                            on_next,
                        )),
                ),
        )
}

fn secondary_btn(
    id: &'static str,
    label: &'static str,
    theme: &InstallerTheme,
    on_click: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
) -> impl IntoElement {
    div()
        .id(id)
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
        .on_click(on_click)
        .child(
            div()
                .text_color(theme.btn_secondary_text)
                .font_weight(FontWeight::MEDIUM)
                .text_size(px(12.0))
                .child(label),
        )
}

fn next_btn(
    enabled: bool,
    theme: &InstallerTheme,
    on_next: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
) -> impl IntoElement {
    let btn = div()
        .id("btn-next")
        .flex()
        .items_center()
        .justify_center()
        .px(px(22.0))
        .py(px(7.0))
        .rounded(px(7.0))
        .bg(if enabled {
            theme.btn_primary_bg
        } else {
            theme.step_pending
        })
        .opacity(if enabled { 1.0 } else { 0.45 });

    if enabled {
        btn.cursor_pointer()
            .hover(|s| s.bg(theme.btn_primary_hover))
            .on_click(on_next)
            .child(
                div()
                    .text_color(theme.btn_primary_text)
                    .font_weight(FontWeight::BOLD)
                    .text_size(px(12.0))
                    .child("Next"),
            )
    } else {
        btn.child(
            div()
                .text_color(theme.text_muted)
                .font_weight(FontWeight::BOLD)
                .text_size(px(12.0))
                .child("Next"),
        )
    }
}
