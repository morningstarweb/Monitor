use super::super::icons::{ICON_CLOUD, svg_icon};
use super::super::state::FlyoutState;
use super::super::theme::FlyoutTheme;
use super::header::render_panel_header;
use crate::client::Command;
use crate::flyout::MonitorFlyout;
use gpui_kit::{prelude::FluentBuilder, *};
use std::sync::mpsc::Sender;

pub fn render_cloud_panel<FBack>(
    state: &FlyoutState,
    command_sender: &Option<Sender<Command>>,
    cx: &mut Context<MonitorFlyout>,
    on_back: FBack,
) -> impl IntoElement
where
    FBack: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    let theme = FlyoutTheme::for_mode(state.is_dark_mode);
    let tx_login = command_sender.clone();
    let tx_logout = command_sender.clone();
    let tx_toggle = command_sender.clone();
    let is_logged_in = state.is_cloud_synced && state.cloud_email.is_some();
    let is_cloud_allowed = state.cloud_access_allowed;

    div()
        .flex()
        .flex_col()
        .gap(px(8.0))
        .w_full()
        .h_full()
        // Header
        .child(render_panel_header("Cloud Sync", state.is_dark_mode, on_back, None))
        // Scrollable Body
        .child(
            div()
                .id("cloud-scroll-container")
                .flex()
                .flex_col()
                .gap(px(8.0))
                .w_full()
                .flex_1()
                .overflow_y_scroll()
                // 1. Account Status Card
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .gap(px(8.0))
                        .w_full()
                        .p(px(10.0))
                        .rounded(px(12.0))
                        .bg(theme.card_bg)
                        .border_1()
                        .border_color(theme.card_border)
                        .shadow_sm()
                        .child(
                            div()
                                .flex()
                                .flex_row()
                                .items_center()
                                .gap(px(8.0))
                                .child(svg_icon(ICON_CLOUD, 18.0, theme.accent_blue))
                                .child(
                                    div()
                                        .flex()
                                        .flex_col()
                                        .child(
                                            div()
                                                .text_color(theme.text_primary)
                                                .font_weight(FontWeight::BOLD)
                                                .text_size(px(12.0))
                                                .child(if is_logged_in { "Cloud Connected" } else { "Cloud Disconnected" })
                                        )
                                        .child(
                                            div()
                                                .text_color(theme.text_secondary)
                                                .text_size(px(10.0))
                                                .child(
                                                    state.cloud_email
                                                        .clone()
                                                        .unwrap_or_else(|| "Sign in with Google to sync mobile anywhere.".to_string())
                                                )
                                        )
                                )
                        )
                        // Action Button
                        .child(
                            if is_logged_in {
                                div()
                                    .id("btn-cloud-disconnect")
                                    .flex()
                                    .items_center()
                                    .justify_center()
                                    .w_full()
                                    .h(px(28.0))
                                    .rounded(px(8.0))
                                    .bg(theme.quit_bg)
                                    .border_1()
                                    .border_color(theme.quit_border)
                                    .cursor_pointer()
                                    .hover(|s| s.bg(theme.quit_hover))
                                    .on_click(cx.listener(move |_, _, _, _| {
                                        if let Some(ref tx) = tx_logout {
                                            let _ = tx.send(Command::CloudLogout);
                                        }
                                    }))
                                    .child(
                                        div()
                                            .text_color(theme.quit_text)
                                            .font_weight(FontWeight::MEDIUM)
                                            .text_size(px(10.5))
                                            .child("Disconnect Cloud")
                                    )
                                    .into_any_element()
                            } else {
                                div()
                                    .id("btn-cloud-login")
                                    .flex()
                                    .items_center()
                                    .justify_center()
                                    .w_full()
                                    .h(px(28.0))
                                    .rounded(px(8.0))
                                    .bg(rgba(0x38bdf820))
                                    .border_1()
                                    .border_color(rgba(0x38bdf855))
                                    .cursor_pointer()
                                    .hover(|s| s.bg(rgba(0x38bdf835)))
                                    .on_click(cx.listener(move |_, _, _, _| {
                                        if let Some(ref tx) = tx_login {
                                            let _ = tx.send(Command::CloudLogin);
                                        }
                                    }))
                                    .child(
                                        div()
                                            .text_color(theme.accent_blue)
                                            .font_weight(FontWeight::MEDIUM)
                                            .text_size(px(10.5))
                                            .child("Sign In with Google…")
                                    )
                                    .into_any_element()
                            }
                        )
                )
                // 2. Cloud Permission Toggle (Entire row clickable + Apple switch)
                .child(
                    div()
                        .id("row-cloud-toggle")
                        .flex()
                        .flex_row()
                        .items_center()
                        .justify_between()
                        .px(px(10.0))
                        .py(px(9.0))
                        .rounded(px(10.0))
                        .bg(theme.card_bg)
                        .border_1()
                        .border_color(theme.card_border)
                        .shadow_sm()
                        .cursor_pointer()
                        .hover(|s| s.bg(theme.tile_hover))
                        .on_click(cx.listener(move |this, _, _, cx| {
                            this.state.cloud_access_allowed = !this.state.cloud_access_allowed;
                            cx.notify();
                            if let Some(ref tx) = tx_toggle {
                                let _ = tx.send(Command::ToggleCloudAccess);
                            }
                        }))
                        .child(
                            div()
                                .flex()
                                .flex_col()
                                .child(
                                    div()
                                        .text_color(theme.text_primary)
                                        .font_weight(FontWeight::MEDIUM)
                                        .text_size(px(11.0))
                                        .child("Allow Mobile Cloud Sync")
                                )
                                .child(
                                    div()
                                        .text_color(theme.text_secondary)
                                        .text_size(px(9.5))
                                        .child("Allow paired phones to reach this PC via cloud relay")
                                )
                        )
                        // Apple-Style Pill Toggle Switch
                        .child(
                            div()
                                .flex()
                                .items_center()
                                .w(px(32.0))
                                .h(px(18.0))
                                .p(px(2.0))
                                .rounded_full()
                                .bg(if is_cloud_allowed { theme.toggle_active_bg } else { theme.toggle_inactive_bg })
                                .border_1()
                                .border_color(if is_cloud_allowed { theme.toggle_active_border } else { theme.toggle_inactive_border })
                                .child(
                                    div()
                                        .w(px(12.0))
                                        .h(px(12.0))
                                        .rounded_full()
                                        .bg(rgba(0xffffffff))
                                        .shadow_sm()
                                        .when(is_cloud_allowed, |s| s.ml(px(14.0)))
                                )
                        )
                )
                // 3. Information & Network Transport Card
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .gap(px(5.0))
                        .p(px(10.0))
                        .rounded(px(10.0))
                        .bg(if state.is_dark_mode { rgba(0x38bdf80a) } else { rgba(0x0284c70d) })
                        .border_1()
                        .border_color(if state.is_dark_mode { rgba(0x38bdf820) } else { rgba(0x0284c725) })
                        .child(
                            div()
                                .flex()
                                .flex_row()
                                .items_center()
                                .justify_between()
                                .child(
                                    div()
                                        .text_color(theme.accent_blue)
                                        .font_weight(FontWeight::MEDIUM)
                                        .text_size(px(10.5))
                                        .child("Adaptive Quad-Path Tunnel")
                                )
                                .child(
                                    div()
                                        .text_color(if state.connected_clients > 0 { rgba(0x22c55eff) } else { theme.text_secondary })
                                        .text_size(px(9.5))
                                        .child(if state.connected_clients > 0 { format!("{} stream active", state.connected_clients) } else { "Ready to Pair".to_string() })
                                )
                        )
                        .child(
                            div()
                                .text_color(theme.text_secondary)
                                .text_size(px(9.5))
                                .child(format!("Host: {} • Noise IK E2EE & Cloudflare Calls TURN.", state.addresses.first().map(String::as_str).unwrap_or("127.0.0.1:9480")))
                        )
                )
        )
}
