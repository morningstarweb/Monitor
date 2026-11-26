use super::super::icons::{
    ICON_BELL, ICON_CHECK, ICON_CHIP, ICON_SHIELD_ALERT, ICON_TRASH, svg_icon,
};
use super::super::state::FlyoutState;
use super::super::theme::FlyoutTheme;
use super::header::render_panel_header;
use crate::client::Command;
use crate::flyout::MonitorFlyout;
use gpui_kit::{prelude::FluentBuilder, *};
use std::sync::mpsc::Sender;

pub fn render_notifications_panel<FBack>(
    state: &FlyoutState,
    command_sender: &Option<Sender<Command>>,
    cx: &mut Context<MonitorFlyout>,
    on_back: FBack,
) -> impl IntoElement
where
    FBack: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    let theme = FlyoutTheme::for_mode(state.is_dark_mode);
    let has_items = !state.alerts.is_empty() || !state.active_agent_sessions.is_empty();
    let cmd = command_sender.clone();
    let right_action = if has_items {
        Some(
            div()
                .id("btn-clear-alerts")
                .flex()
                .flex_row()
                .items_center()
                .gap(px(4.0))
                .px(px(7.0))
                .py(px(2.5))
                .rounded(px(6.0))
                .bg(rgba(0xef444415))
                .border_1()
                .border_color(rgba(0xef444430))
                .cursor_pointer()
                .hover(|s| s.bg(rgba(0xef444425)))
                .on_click(cx.listener(move |this, _, _, cx| {
                    if let Some(ref tx) = cmd {
                        let _ = tx.send(Command::ClearAlerts);
                    }
                    this.state.alerts.clear();
                    this.state.active_agent_sessions.clear();
                    cx.notify();
                }))
                .child(svg_icon(ICON_TRASH, 10.0, rgba(0xef4444ff)))
                .child(
                    div()
                        .text_color(rgba(0xef4444ff))
                        .font_weight(FontWeight::SEMIBOLD)
                        .text_size(px(10.0))
                        .child("Clear Logs"),
                )
                .into_any_element(),
        )
    } else {
        None
    };

    div()
        .flex()
        .flex_col()
        .gap(px(8.0))
        .w_full()
        .h_full()
        // Header
        .child(render_panel_header("Watchdog & Alerts", state.is_dark_mode, on_back, right_action))
        // Scrollable Body
        .child(
            div()
                .id("notifications-scroll-container")
                .flex()
                .flex_col()
                .gap(px(8.0))
                .w_full()
                .flex_1()
                .overflow_y_scroll()
                // 1. Active AI Agent Sessions (if any detected by Watchdog)
                .children(state.active_agent_sessions.iter().map(|sess| {
                    div()
                        .flex()
                        .flex_row()
                        .items_start()
                        .gap(px(8.0))
                        .p(px(10.0))
                        .rounded(px(10.0))
                        .bg(theme.card_bg)
                        .border_1()
                        .border_color(theme.accent_blue.opacity(0.3))
                        .child(svg_icon(ICON_CHIP, 13.0, theme.accent_blue))
                        .child(
                            div()
                                .flex()
                                .flex_col()
                                .gap(px(2.0))
                                .child(
                                    div()
                                        .flex()
                                        .flex_row()
                                        .items_center()
                                        .gap(px(6.0))
                                        .child(
                                            div()
                                                .text_color(theme.text_primary)
                                                .font_weight(FontWeight::BOLD)
                                                .text_size(px(11.0))
                                                .child(format!("AI Agent: {}", sess.profile_name))
                                        )
                                        .child(
                                            div()
                                                .px(px(5.0))
                                                .py(px(1.0))
                                                .rounded(px(4.0))
                                                .bg(rgba(0x22c55e20))
                                                .text_color(rgba(0x22c55eff))
                                                .text_size(px(9.0))
                                                .font_weight(FontWeight::SEMIBOLD)
                                                .child(format!("PID {}", sess.pid))
                                        )
                                )
                                .child(
                                    div()
                                        .text_color(theme.text_secondary)
                                        .text_size(px(9.5))
                                        .child(format!("State: {} • Monitored by Watchdog", sess.state))
                                )
                        )
                }))
                // 2. Real Stored Alerts from Pipeline
                .children(state.alerts.iter().map(|alert| {
                    let is_crit = alert.severity.eq_ignore_ascii_case("critical");
                    let icon_color = if is_crit {
                        rgba(0xef4444ff)
                    } else {
                        rgba(0xf59e0bff)
                    };
                    let border_color = if is_crit {
                        rgba(0xef444430)
                    } else {
                        theme.card_border
                    };

                    div()
                        .flex()
                        .flex_row()
                        .items_start()
                        .gap(px(8.0))
                        .p(px(10.0))
                        .rounded(px(10.0))
                        .bg(theme.card_bg)
                        .border_1()
                        .border_color(border_color)
                        .child(svg_icon(ICON_SHIELD_ALERT, 13.0, icon_color))
                        .child(
                            div()
                                .flex()
                                .flex_col()
                                .gap(px(2.0))
                                .child(
                                    div()
                                        .text_color(theme.text_primary)
                                        .font_weight(FontWeight::BOLD)
                                        .text_size(px(11.0))
                                        .child(alert.title.clone())
                                )
                                .child(
                                    div()
                                        .text_color(theme.text_secondary)
                                        .text_size(px(9.5))
                                        .child(alert.body.clone())
                                )
                        )
                }))
                // 3. Fallback normal status card if no alerts exist
                .when(state.alerts.is_empty(), |this| {
                    this.child(
                        div()
                            .flex()
                            .flex_row()
                            .items_start()
                            .gap(px(8.0))
                            .p(px(10.0))
                            .rounded(px(10.0))
                            .bg(theme.card_bg)
                            .border_1()
                            .border_color(theme.card_border)
                            .child(svg_icon(ICON_CHECK, 12.0, rgba(0x22c55eff)))
                            .child(
                                div()
                                    .flex()
                                    .flex_col()
                                    .child(
                                        div()
                                            .text_color(theme.text_primary)
                                            .font_weight(FontWeight::MEDIUM)
                                            .text_size(px(11.0))
                                            .child("Watchdog Active")
                                    )
                                    .child(
                                        div()
                                            .text_color(theme.text_secondary)
                                            .text_size(px(9.5))
                                            .child("No runaway CPU or unauthorized file changes detected.")
                                    )
                            )
                    )
                })
                // 4. Device Connection Status Card
                .child(
                    div()
                        .flex()
                        .flex_row()
                        .items_start()
                        .gap(px(8.0))
                        .p(px(10.0))
                        .rounded(px(10.0))
                        .bg(theme.card_bg)
                        .border_1()
                        .border_color(theme.card_border)
                        .child(svg_icon(ICON_BELL, 12.0, theme.accent_blue))
                        .child(
                            div()
                                .flex()
                                .flex_col()
                                .child(
                                    div()
                                        .text_color(theme.text_primary)
                                        .font_weight(FontWeight::MEDIUM)
                                        .text_size(px(11.0))
                                        .child("Paired Companion")
                                )
                                .child(
                                    div()
                                        .text_color(theme.text_secondary)
                                        .text_size(px(9.5))
                                        .child(if state.connected_clients > 0 {
                                            format!("{} phone currently connected and streaming telemetry.", state.connected_clients)
                                        } else {
                                            format!("{} phone(s) paired and recognized.", state.mobile_devices_count)
                                        })
                                )
                        )
                )
        )
}
