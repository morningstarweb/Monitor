use super::{MobilePalette, paired_row};
use crate::client::{Command, Device};
use crate::flyout::MonitorFlyout;
use crate::flyout::icons::{ICON_QR, ICON_SMARTPHONE, svg_icon};
use crate::flyout::state::FlyoutState;
use crate::flyout::theme::FlyoutTheme;
use gpui_kit::*;
use std::sync::mpsc::Sender;

/// Devices List View (ZERO "Accept" buttons anywhere!)
/// 1. AWAITING VERIFICATION (Registered via Gmail / initial handshake; click opens unique OTP + QR)
/// 2. PAIRED PHONES (Verified T3 Admin / T2 Operator; toggle T3/T2, view OTP, or Logout)
/// 3. PREVIOUS DEVICES (Revoked; click re-issues unique per-device OTP for secure re-verification)
pub(super) fn render_devices_list_view(
    state: &FlyoutState,
    theme: &FlyoutTheme,
    palette: &MobilePalette,
    command_sender: &Option<Sender<Command>>,
    cx: &mut Context<MonitorFlyout>,
) -> impl IntoElement {
    let awaiting_devices: Vec<_> = state
        .devices
        .iter()
        .enumerate()
        .filter(|(_, d)| d.is_pending && !d.is_revoked)
        .collect();
    let active_devices: Vec<_> = state
        .devices
        .iter()
        .enumerate()
        .filter(|(_, d)| !d.is_pending && !d.is_revoked)
        .collect();
    let previous_devices: Vec<_> = state
        .devices
        .iter()
        .enumerate()
        .filter(|(_, d)| d.is_revoked)
        .collect();

    let mut container = div()
        .id("mobile-scroll-container")
        .flex()
        .flex_col()
        .gap(px(10.0))
        .w_full()
        .flex_1()
        .overflow_y_scroll();

    if !awaiting_devices.is_empty() {
        container = container.child(render_awaiting_section(
            awaiting_devices,
            theme,
            palette,
            command_sender,
            cx,
        ));
    }

    container = container.child(paired_row::render_paired_section(
        active_devices,
        theme,
        palette,
        command_sender,
        cx,
    ));

    if !previous_devices.is_empty() {
        container = container.child(render_previous_section(
            previous_devices,
            theme,
            palette,
            command_sender,
            cx,
        ));
    }

    container
}

fn render_awaiting_section(
    awaiting_devices: Vec<(usize, &Device)>,
    theme: &FlyoutTheme,
    palette: &MobilePalette,
    command_sender: &Option<Sender<Command>>,
    cx: &mut Context<MonitorFlyout>,
) -> impl IntoElement {
    let copy_bg = palette.copy_bg;
    let copy_border = palette.copy_border;

    div()
        .flex()
        .flex_col()
        .gap(px(4.0))
        .w_full()
        .child(
            div()
                .text_color(rgba(0x38bdf8ff))
                .text_size(px(10.0))
                .font_weight(FontWeight::BOLD)
                .px(px(2.0))
                .child(format!(
                    "REGISTERED · VERIFY OTP ({})",
                    awaiting_devices.len()
                )),
        )
        .children(awaiting_devices.into_iter().map(|(idx, dev)| {
            let dev_id = dev.device_id.clone();
            let dev_name = dev.display_name.clone();
            let has_live_otp = dev.active_otp.is_some();
            let tx_otp = command_sender.clone();
            div()
                .id(ElementId::named_usize("row-unverified-dev", idx))
                .flex()
                .flex_row()
                .items_center()
                .justify_between()
                .px(px(8.0))
                .py(px(6.0))
                .rounded(px(10.0))
                .bg(if theme.is_dark {
                    rgba(0x38bdf812)
                } else {
                    rgba(0x0284c70a)
                })
                .border_1()
                .border_color(if theme.is_dark {
                    rgba(0x38bdf840)
                } else {
                    rgba(0x0284c730)
                })
                .cursor_pointer()
                .hover(|s| s.bg(theme.tile_hover))
                .on_click(cx.listener(move |this, _, _, cx| {
                    if let Some(ref tx) = tx_otp
                        && !has_live_otp
                    {
                        let _ =
                            tx.send(Command::GenerateDeviceOtp(dev_id.clone(), dev_name.clone()));
                    }
                    this.show_qr_code = false;
                    this.selected_otp_device_id = Some(dev_id.clone());
                    cx.notify();
                }))
                .child(
                    div()
                        .flex()
                        .flex_row()
                        .items_center()
                        .gap(px(8.0))
                        .child(svg_icon(ICON_SMARTPHONE, 13.0, theme.accent_blue))
                        .child(
                            div()
                                .flex()
                                .flex_col()
                                .child(
                                    div()
                                        .text_color(theme.text_primary)
                                        .font_weight(FontWeight::MEDIUM)
                                        .text_size(px(10.5))
                                        .child(dev.display_name.clone()),
                                )
                                .child(
                                    div()
                                        .text_color(theme.text_secondary)
                                        .text_size(px(8.5))
                                        .child(if let Some(ref code) = dev.active_otp {
                                            format!("Active OTP: {code}")
                                        } else {
                                            "Tap to generate unique OTP".to_string()
                                        }),
                                ),
                        ),
                )
                .child(
                    div()
                        .flex()
                        .flex_row()
                        .items_center()
                        .gap(px(3.0))
                        .px(px(8.0))
                        .py(px(3.0))
                        .rounded_full()
                        .bg(copy_bg)
                        .border_1()
                        .border_color(copy_border)
                        .child(svg_icon(ICON_QR, 8.5, theme.accent_blue))
                        .child(
                            div()
                                .text_color(theme.accent_blue)
                                .text_size(px(9.0))
                                .font_weight(FontWeight::SEMIBOLD)
                                .child(if has_live_otp {
                                    "View OTP ›"
                                } else {
                                    "OTP ›"
                                }),
                        ),
                )
        }))
}

fn render_previous_section(
    previous_devices: Vec<(usize, &Device)>,
    theme: &FlyoutTheme,
    palette: &MobilePalette,
    command_sender: &Option<Sender<Command>>,
    cx: &mut Context<MonitorFlyout>,
) -> impl IntoElement {
    let copy_bg = palette.copy_bg;
    let copy_border = palette.copy_border;

    div()
        .flex()
        .flex_col()
        .gap(px(4.0))
        .w_full()
        .child(
            div()
                .text_color(theme.text_secondary)
                .text_size(px(10.0))
                .px(px(2.0))
                .child(format!("PREVIOUS DEVICES ({})", previous_devices.len())),
        )
        .children(previous_devices.into_iter().map(|(idx, dev)| {
            let dev_id = dev.device_id.clone();
            let dev_name = dev.display_name.clone();
            let tx_otp = command_sender.clone();
            div()
                .flex()
                .flex_row()
                .items_center()
                .justify_between()
                .px(px(8.0))
                .py(px(5.0))
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
                        .child(svg_icon(ICON_SMARTPHONE, 12.0, theme.text_secondary))
                        .child(
                            div()
                                .flex()
                                .flex_col()
                                .child(
                                    div()
                                        .text_color(theme.text_secondary)
                                        .font_weight(FontWeight::MEDIUM)
                                        .text_size(px(10.0))
                                        .child(dev.display_name.clone()),
                                )
                                .child(
                                    div()
                                        .text_color(theme.text_secondary)
                                        .text_size(px(8.0))
                                        .child("Revoked · Needs fresh OTP"),
                                ),
                        ),
                )
                .child(
                    div()
                        .id(ElementId::named_usize("btn-reverify-prev", idx))
                        .flex()
                        .flex_row()
                        .items_center()
                        .gap(px(3.0))
                        .px(px(7.0))
                        .py(px(2.5))
                        .rounded_full()
                        .bg(copy_bg)
                        .border_1()
                        .border_color(copy_border)
                        .cursor_pointer()
                        .hover(move |s| s.bg(copy_border))
                        .on_click(cx.listener(move |this, _, _, cx| {
                            if let Some(ref tx) = tx_otp {
                                let _ = tx.send(Command::GenerateDeviceOtp(
                                    dev_id.clone(),
                                    dev_name.clone(),
                                ));
                            }
                            this.show_qr_code = false;
                            this.selected_otp_device_id = Some(dev_id.clone());
                            cx.notify();
                        }))
                        .child(svg_icon(ICON_QR, 8.5, theme.accent_blue))
                        .child(
                            div()
                                .text_color(theme.accent_blue)
                                .text_size(px(8.5))
                                .font_weight(FontWeight::MEDIUM)
                                .child("OTP ›"),
                        ),
                )
        }))
}
