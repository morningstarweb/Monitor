mod devices;
mod paired_row;
mod qr_view;

use crate::client::Command;
use crate::flyout::MonitorFlyout;
use crate::flyout::icons::{ICON_QR, svg_icon};
use crate::flyout::panels::header::render_panel_header_centered;
use crate::flyout::state::FlyoutState;
use crate::flyout::theme::FlyoutTheme;
use gpui_kit::*;
use std::sync::mpsc::Sender;

pub(super) struct MobilePalette {
    pub(super) copy_bg: Rgba,
    pub(super) copy_border: Rgba,
    pub(super) new_code_bg: Rgba,
    pub(super) new_code_border: Rgba,
    pub(super) new_code_text: Rgba,
}

impl MobilePalette {
    fn for_theme(theme: &FlyoutTheme) -> Self {
        if theme.is_dark {
            Self {
                copy_bg: rgba(0x38bdf822),
                copy_border: rgba(0x38bdf855),
                new_code_bg: rgba(0xa855f722),
                new_code_border: rgba(0xa855f755),
                new_code_text: rgba(0xc084fcff),
            }
        } else {
            Self {
                copy_bg: rgba(0x0284c715),
                copy_border: rgba(0x0284c735),
                new_code_bg: rgba(0x7c3aed15),
                new_code_border: rgba(0x7c3aed35),
                new_code_text: rgba(0x7c3aedff),
            }
        }
    }
}

pub fn render_mobile_panel<FBack>(
    state: &FlyoutState,
    is_showing_qr: bool,
    selected_otp_device_id: Option<&str>,
    command_sender: &Option<Sender<Command>>,
    cx: &mut Context<MonitorFlyout>,
    on_back: FBack,
) -> impl IntoElement
where
    FBack: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    let theme = FlyoutTheme::for_mode(state.is_dark_mode);
    let palette = MobilePalette::for_theme(&theme);
    let selected_device = selected_otp_device_id
        .and_then(|target_id| state.devices.iter().find(|d| d.device_id == target_id));
    let is_device_otp_view = selected_otp_device_id.is_some();

    let qr_toggle_btn = render_qr_toggle_button(
        &theme,
        is_showing_qr,
        is_device_otp_view,
        command_sender.clone(),
        cx,
    );

    let header_title: &'static str = if is_device_otp_view {
        "Device Verification OTP"
    } else {
        "Mobile & Devices"
    };

    div()
        .flex()
        .flex_col()
        .gap(px(8.0))
        .w_full()
        .h_full()
        .child(render_panel_header_centered(
            header_title,
            state.is_dark_mode,
            on_back,
            Some(qr_toggle_btn.into_any_element()),
        ))
        .child(if is_device_otp_view || is_showing_qr {
            qr_view::render_qr_otp_view(
                state,
                &theme,
                &palette,
                selected_otp_device_id,
                selected_device,
                command_sender,
                cx,
            )
            .into_any_element()
        } else {
            devices::render_devices_list_view(state, &theme, &palette, command_sender, cx)
                .into_any_element()
        })
}

fn render_qr_toggle_button(
    theme: &FlyoutTheme,
    is_showing_qr: bool,
    is_device_otp_view: bool,
    tx_quick_qr: Option<Sender<Command>>,
    cx: &mut Context<MonitorFlyout>,
) -> impl IntoElement {
    let active = is_showing_qr && !is_device_otp_view;
    let active_bg = if active {
        if theme.is_dark {
            rgba(0x38bdf830)
        } else {
            rgba(0x0284c720)
        }
    } else {
        theme.card_bg
    };
    let fg = if active {
        theme.accent_blue
    } else {
        theme.text_secondary
    };

    div()
        .id("btn-toggle-qr-header")
        .flex()
        .flex_row()
        .items_center()
        .gap(px(3.5))
        .px(px(8.0))
        .py(px(3.5))
        .rounded_full()
        .bg(active_bg)
        .border_1()
        .border_color(if active {
            theme.accent_blue
        } else {
            theme.card_border
        })
        .cursor_pointer()
        .hover(|s| s.bg(theme.tile_hover))
        .on_click(cx.listener(move |this, _, _, cx| {
            this.selected_otp_device_id = None;
            this.show_qr_code = !this.show_qr_code;
            this.state.show_qr_code = this.show_qr_code;
            if this.show_qr_code
                && this.state.pairing_code.is_none()
                && let Some(ref tx) = tx_quick_qr
            {
                let _ = tx.send(Command::Pair);
            }
            cx.notify();
        }))
        .child(svg_icon(ICON_QR, 11.0, fg))
        .child(
            div()
                .text_color(fg)
                .font_weight(FontWeight::MEDIUM)
                .text_size(px(10.0))
                .child(if active { "Devices" } else { "Quick QR" }),
        )
}
