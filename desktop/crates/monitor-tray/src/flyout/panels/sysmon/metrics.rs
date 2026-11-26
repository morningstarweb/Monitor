use crate::flyout::icons::{ICON_HARD_DRIVE, ICON_PULSE, svg_icon};
use crate::flyout::state::FlyoutState;
use crate::flyout::theme::FlyoutTheme;
use gpui_kit::*;

pub(super) fn render_cpu_card(
    state: &FlyoutState,
    theme: &FlyoutTheme,
    track_bg: Rgba,
) -> impl IntoElement {
    div()
        .flex()
        .flex_col()
        .gap(px(6.0))
        .w_full()
        .p(px(10.0))
        .rounded(px(12.0))
        .bg(theme.card_bg)
        .border_1()
        .border_color(theme.card_border)
        .child(
            div()
                .flex()
                .flex_row()
                .items_center()
                .justify_between()
                .child(
                    div()
                        .flex()
                        .flex_row()
                        .items_center()
                        .gap(px(6.0))
                        .child(svg_icon(ICON_PULSE, 14.0, rgba(0xa855f7ff)))
                        .child(
                            div()
                                .text_color(theme.text_primary)
                                .font_weight(FontWeight::MEDIUM)
                                .text_size(px(11.0))
                                .child("CPU Load"),
                        ),
                )
                .child(
                    div()
                        .text_color(rgba(0xa855f7ff))
                        .font_weight(FontWeight::BOLD)
                        .text_size(px(13.0))
                        .child(format!("{:.0}%", state.cpu_percent)),
                ),
        )
        .child(
            div()
                .w_full()
                .h(px(5.0))
                .rounded_full()
                .bg(track_bg)
                .overflow_hidden()
                .child(
                    div()
                        .h_full()
                        .w(px(2.8 * state.cpu_percent.clamp(0.0, 100.0)))
                        .rounded_full()
                        .bg(rgba(0xa855f7ff)),
                ),
        )
}

pub(super) fn render_ram_card(
    state: &FlyoutState,
    theme: &FlyoutTheme,
    track_bg: Rgba,
) -> impl IntoElement {
    let mem_pct = if state.memory_total_gb > 0.0 {
        ((state.memory_used_gb / state.memory_total_gb) * 100.0).clamp(0.0, 100.0)
    } else {
        35.0
    };

    div()
        .flex()
        .flex_col()
        .gap(px(6.0))
        .w_full()
        .p(px(10.0))
        .rounded(px(12.0))
        .bg(theme.card_bg)
        .border_1()
        .border_color(theme.card_border)
        .child(
            div()
                .flex()
                .flex_row()
                .items_center()
                .justify_between()
                .child(
                    div().flex().flex_row().items_center().gap(px(6.0)).child(
                        div()
                            .text_color(theme.text_primary)
                            .font_weight(FontWeight::MEDIUM)
                            .text_size(px(11.0))
                            .child("RAM Memory"),
                    ),
                )
                .child(
                    div()
                        .text_color(theme.accent_blue)
                        .font_weight(FontWeight::BOLD)
                        .text_size(px(11.0))
                        .child(format!(
                            "{:.1} / {:.0} GB",
                            state.memory_used_gb, state.memory_total_gb
                        )),
                ),
        )
        .child(
            div()
                .w_full()
                .h(px(5.0))
                .rounded_full()
                .bg(track_bg)
                .overflow_hidden()
                .child(
                    div()
                        .h_full()
                        .w(px(2.8 * mem_pct))
                        .rounded_full()
                        .bg(theme.accent_blue),
                ),
        )
}

pub(super) fn render_disk_cards<'a>(
    state: &'a FlyoutState,
    theme: &'a FlyoutTheme,
    track_bg: Rgba,
) -> impl Iterator<Item = Div> + 'a {
    state.disks.iter().take(2).map(move |disk| {
        let total_gb = disk.total_bytes as f32 / (1024.0 * 1024.0 * 1024.0);
        let avail_gb = disk.available_bytes as f32 / (1024.0 * 1024.0 * 1024.0);
        let used_gb = (total_gb - avail_gb).max(0.0);
        let used_pct = if total_gb > 0.0 {
            ((used_gb / total_gb) * 100.0).clamp(0.0, 100.0)
        } else {
            0.0
        };

        div()
            .flex()
            .flex_col()
            .gap(px(5.0))
            .w_full()
            .p(px(10.0))
            .rounded(px(12.0))
            .bg(theme.card_bg)
            .border_1()
            .border_color(theme.card_border)
            .child(
                div()
                    .flex()
                    .flex_row()
                    .items_center()
                    .justify_between()
                    .child(
                        div()
                            .flex()
                            .flex_row()
                            .items_center()
                            .gap(px(6.0))
                            .child(svg_icon(ICON_HARD_DRIVE, 12.0, theme.accent_blue))
                            .child(
                                div()
                                    .text_color(theme.text_primary)
                                    .font_weight(FontWeight::MEDIUM)
                                    .text_size(px(10.5))
                                    .child(format!("Disk ({})", disk.mount_point)),
                            ),
                    )
                    .child(
                        div()
                            .text_color(theme.text_secondary)
                            .text_size(px(10.0))
                            .child(format!("{avail_gb:.0} GB free of {total_gb:.0} GB")),
                    ),
            )
            .child(
                div()
                    .w_full()
                    .h(px(4.0))
                    .rounded_full()
                    .bg(track_bg)
                    .overflow_hidden()
                    .child(div().h_full().w(px(2.8 * used_pct)).rounded_full().bg(
                        if used_pct > 90.0 {
                            rgba(0xef4444ff)
                        } else {
                            theme.accent_blue
                        },
                    )),
            )
    })
}
