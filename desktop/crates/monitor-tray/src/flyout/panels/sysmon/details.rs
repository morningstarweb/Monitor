use crate::client::Command;
use crate::flyout::icons::{ICON_BATTERY, ICON_REFRESH, svg_icon};
use crate::flyout::state::FlyoutState;
use crate::flyout::theme::FlyoutTheme;
use gpui_kit::{prelude::FluentBuilder, *};
use std::sync::mpsc::Sender;

pub(super) fn render_top_processes_card(
    state: &FlyoutState,
    theme: &FlyoutTheme,
) -> impl IntoElement {
    div()
        .flex()
        .flex_col()
        .gap(px(4.0))
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
                .justify_between()
                .child(
                    div()
                        .text_color(theme.text_secondary)
                        .text_size(px(10.0))
                        .child("TOP PROCESSES"),
                )
                .child(
                    div()
                        .text_color(theme.text_secondary)
                        .text_size(px(10.0))
                        .child("CPU • RAM"),
                ),
        )
        .children(state.top_processes.iter().take(3).map(|proc| {
            let mem_mb = proc.memory_bytes as f32 / (1024.0 * 1024.0);
            div()
                .flex()
                .flex_row()
                .justify_between()
                .items_center()
                .child(
                    div()
                        .text_color(theme.text_primary)
                        .text_size(px(10.0))
                        .font_weight(FontWeight::MEDIUM)
                        .child(proc.name.clone()),
                )
                .child(
                    div()
                        .text_color(theme.text_secondary)
                        .text_size(px(9.5))
                        .child(format!("{:.1}% • {:.0} MB", proc.cpu_percent, mem_mb)),
                )
        }))
}

pub(super) fn render_specs_card(state: &FlyoutState, theme: &FlyoutTheme) -> impl IntoElement {
    let uptime_hrs = state.uptime_seconds / 3600;
    let uptime_mins = (state.uptime_seconds % 3600) / 60;

    div()
        .flex()
        .flex_col()
        .gap(px(4.0))
        .w_full()
        .p(px(10.0))
        .rounded(px(12.0))
        .bg(theme.card_bg)
        .border_1()
        .border_color(theme.card_border)
        .child(render_spec_row(
            "Hostname",
            state.hostname.clone(),
            theme.text_secondary,
            theme.text_primary,
        ))
        .child(render_spec_row(
            "OS Platform",
            state.os_name.clone(),
            theme.text_secondary,
            theme.text_primary,
        ))
        .when_some(
            state.battery.as_ref().filter(|b| b.has_battery),
            |this, bat| {
                let pct = bat.percentage.unwrap_or(100.0);
                this.child(
                    div()
                        .flex()
                        .flex_row()
                        .justify_between()
                        .child(
                            div()
                                .flex()
                                .flex_row()
                                .items_center()
                                .gap(px(4.0))
                                .child(svg_icon(ICON_BATTERY, 11.0, rgba(0x22c55eff)))
                                .child(
                                    div()
                                        .text_color(theme.text_secondary)
                                        .text_size(px(10.0))
                                        .child("Battery"),
                                ),
                        )
                        .child(
                            div()
                                .text_color(theme.text_primary)
                                .text_size(px(10.0))
                                .child(format!("{pct:.0}%")),
                        ),
                )
            },
        )
        .child(render_spec_row(
            "System Uptime",
            format!("{uptime_hrs}h {uptime_mins}m"),
            theme.text_secondary,
            rgba(0x22c55eff),
        ))
}

fn render_spec_row(
    label: &'static str,
    value: String,
    label_color: Rgba,
    value_color: Rgba,
) -> impl IntoElement {
    div()
        .flex()
        .flex_row()
        .justify_between()
        .child(
            div()
                .text_color(label_color)
                .text_size(px(10.0))
                .child(label),
        )
        .child(
            div()
                .text_color(value_color)
                .text_size(px(10.0))
                .child(value),
        )
}

pub(super) fn render_refresh_button<V: 'static>(
    tx_poll: Option<Sender<Command>>,
    theme: &FlyoutTheme,
    cx: &mut Context<V>,
) -> impl IntoElement {
    div()
        .id("btn-refresh-sysmon")
        .flex()
        .items_center()
        .justify_center()
        .gap(px(6.0))
        .w_full()
        .h(px(30.0))
        .rounded(px(10.0))
        .bg(theme.card_bg)
        .border_1()
        .border_color(theme.card_border)
        .cursor_pointer()
        .hover(|s| s.bg(theme.tile_hover))
        .on_click(cx.listener(move |_, _, _, _| {
            if let Some(ref tx) = tx_poll {
                let _ = tx.send(Command::Poll);
            }
        }))
        .child(svg_icon(ICON_REFRESH, 12.0, theme.icon_primary))
        .child(
            div()
                .text_color(theme.text_primary)
                .font_weight(FontWeight::MEDIUM)
                .text_size(px(10.5))
                .child("Refresh System Telemetry"),
        )
}
