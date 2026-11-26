use super::MobilePalette;
use crate::client::{Command, Device};
use crate::flyout::MonitorFlyout;
use crate::flyout::icons::{ICON_SHIELD, ICON_SMARTPHONE, ICON_TRASH, ICON_WIFI, svg_icon};
use crate::flyout::theme::FlyoutTheme;
use gpui_kit::{prelude::FluentBuilder, *};
use std::sync::mpsc::Sender;

pub(super) fn render_paired_section(
    active_devices: Vec<(usize, &Device)>,
    theme: &FlyoutTheme,
    palette: &MobilePalette,
    command_sender: &Option<Sender<Command>>,
    cx: &mut Context<MonitorFlyout>,
) -> impl IntoElement {
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
                .child(format!("PAIRED PHONES ({})", active_devices.len())),
        )
        .child(if active_devices.is_empty() {
            div()
                .flex()
                .flex_col()
                .items_center()
                .justify_center()
                .p(px(18.0))
                .gap(px(5.0))
                .rounded(px(10.0))
                .bg(theme.card_bg)
                .border_1()
                .border_color(theme.card_border)
                .child(
                    div()
                        .text_color(theme.text_secondary)
                        .text_size(px(11.0))
                        .child("No verified phones yet."),
                )
                .child(
                    div()
                        .text_color(theme.accent_blue)
                        .text_size(px(9.5))
                        .child("Tap a registered phone above for OTP or [Quick QR]."),
                )
                .into_any_element()
        } else {
            let mut list = div().flex().flex_col().gap(px(4.0));
            for (idx, dev) in active_devices {
                list = list.child(render_paired_row(
                    idx,
                    dev,
                    theme,
                    palette,
                    command_sender,
                    cx,
                ));
            }
            list.into_any_element()
        })
}

fn render_paired_row(
    idx: usize,
    dev: &Device,
    theme: &FlyoutTheme,
    palette: &MobilePalette,
    command_sender: &Option<Sender<Command>>,
    cx: &mut Context<MonitorFlyout>,
) -> impl IntoElement {
    let dev_id = dev.device_id.clone();
    let dev_id_otp = dev.device_id.clone();
    let dev_name_otp = dev.display_name.clone();
    let dev_id_tier = dev.device_id.clone();
    let is_t3 = dev.trust_tier == "T3" || dev.role == "Admin";
    let next_tier = if is_t3 { "T2" } else { "T3" };
    let is_local = dev.is_local;
    let tx_revoke = command_sender.clone();
    let tx_tier = command_sender.clone();
    let tx_otp = command_sender.clone();
    let copy_bg = palette.copy_bg;
    let copy_border = palette.copy_border;

    let tier_text_color = if is_t3 {
        if theme.is_dark {
            rgba(0x4ade80ff)
        } else {
            rgba(0x16a34aff)
        }
    } else if theme.is_dark {
        rgba(0xfbbf24ff)
    } else {
        rgba(0xd97706ff)
    };

    div()
        .flex()
        .flex_row()
        .items_center()
        .justify_between()
        .px(px(8.0))
        .py(px(6.0))
        .rounded(px(10.0))
        .bg(theme.card_bg)
        .border_1()
        .border_color(theme.card_border)
        .hover(|s| s.bg(theme.tile_hover))
        .child(
            div()
                .flex()
                .flex_row()
                .items_center()
                .gap(px(7.0))
                .child(svg_icon(
                    if is_local { ICON_WIFI } else { ICON_SMARTPHONE },
                    13.0,
                    if is_local {
                        rgba(0x4ade80ff)
                    } else {
                        rgba(0xa855f7ff)
                    },
                ))
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
                                .text_size(px(8.0))
                                .child(if is_local {
                                    "Local Wi-Fi · Active"
                                } else {
                                    "Cloud / Remote"
                                }),
                        ),
                ),
        )
        .child(
            div()
                .flex()
                .flex_row()
                .items_center()
                .gap(px(4.0))
                .child(
                    div()
                        .id(ElementId::named_usize("btn-tier-toggle", idx))
                        .flex()
                        .flex_row()
                        .items_center()
                        .gap(px(2.5))
                        .px(px(6.0))
                        .py(px(2.0))
                        .rounded_full()
                        .bg(if is_t3 {
                            rgba(0x22c55e22)
                        } else {
                            rgba(0xf59e0b22)
                        })
                        .border_1()
                        .border_color(if is_t3 {
                            rgba(0x22c55e55)
                        } else {
                            rgba(0xf59e0b55)
                        })
                        .cursor_pointer()
                        .hover(|s| s.bg(rgba(0x22c55e38)))
                        .on_click(cx.listener(move |_, _, _, _| {
                            if let Some(ref tx) = tx_tier {
                                let _ = tx.send(Command::SetDeviceTier(
                                    dev_id_tier.clone(),
                                    next_tier.to_string(),
                                ));
                            }
                        }))
                        .child(svg_icon(
                            ICON_SHIELD,
                            8.0,
                            if is_t3 {
                                rgba(0x4ade80ff)
                            } else {
                                rgba(0xfbbf24ff)
                            },
                        ))
                        .child(
                            div()
                                .text_color(tier_text_color)
                                .text_size(px(8.0))
                                .font_weight(FontWeight::BOLD)
                                .child(if is_t3 { "T3 Admin" } else { "T2 Op" }),
                        ),
                )
                .child(
                    div()
                        .id(ElementId::named_usize("btn-dev-otp", idx))
                        .flex()
                        .items_center()
                        .px(px(5.0))
                        .py(px(2.0))
                        .rounded_full()
                        .bg(copy_bg)
                        .border_1()
                        .border_color(copy_border)
                        .cursor_pointer()
                        .hover(move |s| s.bg(copy_border))
                        .on_click(cx.listener(move |this, _, _, cx| {
                            if let Some(ref tx) = tx_otp {
                                let _ = tx.send(Command::GenerateDeviceOtp(
                                    dev_id_otp.clone(),
                                    dev_name_otp.clone(),
                                ));
                            }
                            this.show_qr_code = false;
                            this.selected_otp_device_id = Some(dev_id_otp.clone());
                            cx.notify();
                        }))
                        .child(
                            div()
                                .text_color(theme.accent_blue)
                                .text_size(px(8.0))
                                .font_weight(FontWeight::MEDIUM)
                                .child("OTP"),
                        ),
                )
                .when(!is_local, |row| {
                    row.child(
                        div()
                            .id(ElementId::named_usize("btn-revoke", idx))
                            .flex()
                            .flex_row()
                            .items_center()
                            .gap(px(2.0))
                            .px(px(5.0))
                            .py(px(2.0))
                            .rounded(px(6.0))
                            .bg(theme.quit_bg)
                            .border_1()
                            .border_color(theme.quit_border)
                            .cursor_pointer()
                            .hover(|s| s.bg(theme.quit_hover))
                            .on_click(cx.listener(move |_, _, _, _| {
                                if let Some(ref tx) = tx_revoke {
                                    let _ = tx.send(Command::RevokeDevice(dev_id.clone()));
                                }
                            }))
                            .child(svg_icon(ICON_TRASH, 8.5, theme.quit_text)),
                    )
                }),
        )
}
