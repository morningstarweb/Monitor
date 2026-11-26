use super::MobilePalette;
use crate::client::{Command, Device};
use crate::flyout::MonitorFlyout;
use crate::flyout::icons::{ICON_COPY, ICON_REFRESH, svg_icon};
use crate::flyout::qr::render_qr_code;
use crate::flyout::state::FlyoutState;
use crate::flyout::theme::FlyoutTheme;
use gpui_kit::{prelude::FluentBuilder, *};
use std::sync::mpsc::Sender;

fn build_qr_payload(
    host: &str,
    port: u16,
    code: &str,
    host_cloud_id: Option<&str>,
    target_device_id: Option<&str>,
) -> String {
    let mut map = serde_json::Map::new();
    map.insert(
        "host".to_string(),
        serde_json::Value::String(host.to_string()),
    );
    map.insert(
        "port".to_string(),
        serde_json::Value::String(port.to_string()),
    );
    map.insert(
        "code".to_string(),
        serde_json::Value::String(code.to_string()),
    );
    if let Some(hid) = host_cloud_id.filter(|s| !s.is_empty()) {
        map.insert(
            "host_id".to_string(),
            serde_json::Value::String(hid.to_string()),
        );
    }
    if let Some(did) = target_device_id.filter(|s| !s.is_empty()) {
        map.insert(
            "device_id".to_string(),
            serde_json::Value::String(did.to_string()),
        );
    }
    serde_json::Value::Object(map).to_string()
}

pub(super) fn render_qr_otp_view(
    state: &FlyoutState,
    theme: &FlyoutTheme,
    palette: &MobilePalette,
    selected_otp_device_id: Option<&str>,
    selected_device: Option<&Device>,
    command_sender: &Option<Sender<Command>>,
    cx: &mut Context<MonitorFlyout>,
) -> impl IntoElement {
    let is_device_otp_view = selected_otp_device_id.is_some();
    let host = state
        .addresses
        .first()
        .cloned()
        .unwrap_or_else(|| "127.0.0.1".to_string());
    let active_target_id = selected_otp_device_id.map(str::to_string);
    let active_target_name = selected_device
        .map(|d| d.display_name.clone())
        .unwrap_or_else(|| "Quick Pair".to_string());

    let otp_opt = if let Some(dev) = selected_device {
        dev.active_otp.clone()
    } else if is_device_otp_view {
        None
    } else {
        state.pairing_code.clone()
    };
    let has_code = otp_opt.is_some();
    let code_clean = otp_opt.as_deref().unwrap_or("").replace(' ', "");
    let code_str = otp_opt.unwrap_or_else(|| "------".to_string());
    let code_for_copy = code_clean.clone();
    let qr_payload = build_qr_payload(
        &host,
        state.listen_port,
        &code_clean,
        state.host_cloud_device_id.as_deref(),
        active_target_id.as_deref(),
    );

    let subtitle = if has_code {
        if is_device_otp_view {
            format!("Enter OTP or scan QR on {active_target_name} for T3 Admin access")
        } else {
            "Scan QR or enter 6-digit code on Monitor Android".to_string()
        }
    } else if is_device_otp_view {
        "Click 'New' below to issue a unique OTP for this device".to_string()
    } else {
        "Click 'New' below to generate a one-time pairing code".to_string()
    };

    let regen_id = active_target_id;
    let regen_name = active_target_name;
    let tx_regen = command_sender.clone();
    let copy_bg = palette.copy_bg;
    let copy_border = palette.copy_border;
    let new_code_bg = palette.new_code_bg;
    let new_code_border = palette.new_code_border;
    let new_code_text = palette.new_code_text;

    div()
        .flex()
        .flex_col()
        .items_center()
        .justify_center()
        .gap(px(7.0))
        .w_full()
        .flex_1()
        .p(px(6.0))
        .child(
            div()
                .flex()
                .flex_col()
                .items_center()
                .gap(px(7.0))
                .w_full()
                .p(px(10.0))
                .rounded(px(14.0))
                .bg(theme.card_bg)
                .border_1()
                .border_color(theme.card_border)
                .children(if has_code {
                    render_qr_code(&qr_payload, 3.4)
                } else {
                    None
                })
                .child(
                    div()
                        .text_color(theme.text_secondary)
                        .text_size(px(9.5))
                        .font_weight(FontWeight::MEDIUM)
                        .child(subtitle),
                )
                .child(
                    div()
                        .flex()
                        .flex_row()
                        .items_center()
                        .justify_between()
                        .w_full()
                        .px(px(10.0))
                        .py(px(6.0))
                        .rounded(px(8.0))
                        .bg(if theme.is_dark {
                            rgba(0x00000030)
                        } else {
                            rgba(0x0000000a)
                        })
                        .border_1()
                        .border_color(theme.card_border)
                        .child(
                            div()
                                .flex()
                                .flex_row()
                                .items_center()
                                .gap(px(6.0))
                                .child(
                                    div()
                                        .text_color(theme.text_secondary)
                                        .text_size(px(9.5))
                                        .child(if is_device_otp_view { "OTP:" } else { "CODE:" }),
                                )
                                .child(
                                    div()
                                        .text_color(theme.accent_blue)
                                        .font_weight(FontWeight::BOLD)
                                        .text_size(px(14.5))
                                        .child(code_str),
                                ),
                        )
                        .child(
                            div()
                                .flex()
                                .flex_row()
                                .items_center()
                                .gap(px(4.0))
                                .when(has_code, |row| {
                                    row.child(
                                        div()
                                            .id("btn-copy-code")
                                            .flex()
                                            .flex_row()
                                            .items_center()
                                            .gap(px(3.0))
                                            .px(px(6.0))
                                            .py(px(2.0))
                                            .rounded_full()
                                            .bg(copy_bg)
                                            .border_1()
                                            .border_color(copy_border)
                                            .cursor_pointer()
                                            .hover(move |s| s.bg(copy_border))
                                            .on_click(move |_, _, cx| {
                                                cx.write_to_clipboard(ClipboardItem::new_string(
                                                    code_for_copy.clone(),
                                                ));
                                            })
                                            .child(svg_icon(ICON_COPY, 9.0, theme.accent_blue))
                                            .child(
                                                div()
                                                    .text_color(theme.accent_blue)
                                                    .text_size(px(9.0))
                                                    .font_weight(FontWeight::MEDIUM)
                                                    .child("Copy"),
                                            ),
                                    )
                                })
                                .child(
                                    div()
                                        .id("btn-new-code")
                                        .flex()
                                        .flex_row()
                                        .items_center()
                                        .gap(px(3.0))
                                        .px(px(6.0))
                                        .py(px(2.0))
                                        .rounded_full()
                                        .bg(new_code_bg)
                                        .border_1()
                                        .border_color(new_code_border)
                                        .cursor_pointer()
                                        .hover(move |s| s.bg(new_code_border))
                                        .on_click(cx.listener(move |_, _, _, _| {
                                            if let Some(ref tx) = tx_regen {
                                                if let Some(ref dev_id) = regen_id {
                                                    let _ = tx.send(Command::GenerateDeviceOtp(
                                                        dev_id.clone(),
                                                        regen_name.clone(),
                                                    ));
                                                } else {
                                                    let _ = tx.send(Command::Pair);
                                                }
                                            }
                                        }))
                                        .child(svg_icon(ICON_REFRESH, 9.0, new_code_text))
                                        .child(
                                            div()
                                                .text_color(new_code_text)
                                                .text_size(px(9.0))
                                                .font_weight(FontWeight::MEDIUM)
                                                .child("New"),
                                        ),
                                ),
                        ),
                ),
        )
        .child(
            div()
                .id("btn-done-qr")
                .flex()
                .items_center()
                .justify_center()
                .px(px(14.0))
                .py(px(4.0))
                .rounded_full()
                .bg(theme.card_bg)
                .border_1()
                .border_color(theme.card_border)
                .cursor_pointer()
                .hover(|s| s.bg(theme.tile_hover))
                .on_click(cx.listener(|this, _, _, cx| {
                    this.show_qr_code = false;
                    this.state.show_qr_code = false;
                    this.selected_otp_device_id = None;
                    cx.notify();
                }))
                .child(
                    div()
                        .text_color(theme.text_secondary)
                        .text_size(px(10.0))
                        .font_weight(FontWeight::MEDIUM)
                        .child("Done · Back to Devices"),
                ),
        )
}
