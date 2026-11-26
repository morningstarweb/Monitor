use super::super::icons::{ICON_DOCUMENT, ICON_REFRESH, ICON_WINDOWS, svg_icon};
use super::super::state::FlyoutState;
use super::super::theme::FlyoutTheme;
use super::header::render_panel_header;
use crate::client::Command;
use gpui_kit::{prelude::FluentBuilder, *};
use monitor_core::brand_config::{APP_NAME, APP_VERSION};
use std::sync::mpsc::Sender;

pub fn render_settings_panel<V: 'static, FBack>(
    state: &FlyoutState,
    command_sender: &Option<Sender<Command>>,
    cx: &mut Context<V>,
    on_back: FBack,
) -> impl IntoElement
where
    FBack: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    let theme = FlyoutTheme::for_mode(state.is_dark_mode);
    let tx_restart = command_sender.clone();
    let autostart_val = state.autostart_enabled;

    div()
        .flex()
        .flex_col()
        .gap(px(8.0))
        .w_full()
        .h_full()
        // Header
        .child(render_panel_header(
            "Settings",
            state.is_dark_mode,
            on_back,
            None,
        ))
        // Scrollable Body
        .child(
            div()
                .id("settings-scroll-container")
                .flex()
                .flex_col()
                .gap(px(8.0))
                .w_full()
                .flex_1()
                .overflow_y_scroll()
                // 1. Settings Actions Card
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .w_full()
                        .rounded(px(12.0))
                        .bg(theme.card_bg)
                        .border_1()
                        .border_color(theme.card_border)
                        .overflow_hidden()
                        // Start with Windows
                        .child(
                            div()
                                .flex()
                                .flex_row()
                                .items_center()
                                .justify_between()
                                .px(px(10.0))
                                .py(px(8.0))
                                .rounded_t(px(11.0))
                                .border_b_1()
                                .border_color(theme.card_border)
                                .child(
                                    div()
                                        .flex()
                                        .flex_row()
                                        .items_center()
                                        .gap(px(8.0))
                                        .child(svg_icon(ICON_WINDOWS, 12.0, rgba(0x0078d4ff)))
                                        .child(
                                            div()
                                                .text_color(theme.text_primary)
                                                .font_weight(FontWeight::MEDIUM)
                                                .text_size(px(11.0))
                                                .child("Start with Windows"),
                                        ),
                                )
                                .child(
                                    div()
                                        .id("toggle-autostart-settings")
                                        .flex()
                                        .items_center()
                                        .w(px(32.0))
                                        .h(px(18.0))
                                        .p(px(2.0))
                                        .rounded_full()
                                        .bg(if autostart_val {
                                            theme.toggle_active_bg
                                        } else {
                                            theme.toggle_inactive_bg
                                        })
                                        .border_1()
                                        .border_color(if autostart_val {
                                            theme.toggle_active_border
                                        } else {
                                            theme.toggle_inactive_border
                                        })
                                        .cursor_pointer()
                                        .on_click(cx.listener(move |_, _, _, _| {
                                            let new_val = !autostart_val;
                                            let _ = monitor_tray::set_auto_start(new_val);
                                        }))
                                        .child(
                                            div()
                                                .w(px(12.0))
                                                .h(px(12.0))
                                                .rounded_full()
                                                .bg(rgba(0xffffffff))
                                                .shadow_sm()
                                                .when(autostart_val, |s| s.ml(px(14.0))),
                                        ),
                                ),
                        )
                        // Restart Service
                        .child(
                            div()
                                .id("btn-restart-service")
                                .flex()
                                .flex_row()
                                .items_center()
                                .justify_between()
                                .px(px(10.0))
                                .py(px(8.0))
                                .border_b_1()
                                .border_color(theme.card_border)
                                .cursor_pointer()
                                .hover(|s| s.bg(theme.tile_hover))
                                .on_click(cx.listener(move |_, _, _, _| {
                                    if let Some(ref tx) = tx_restart {
                                        let _ = tx.send(Command::Refresh);
                                    }
                                }))
                                .child(
                                    div()
                                        .flex()
                                        .flex_row()
                                        .items_center()
                                        .gap(px(8.0))
                                        .child(svg_icon(ICON_REFRESH, 12.0, theme.icon_primary))
                                        .child(
                                            div()
                                                .text_color(theme.text_primary)
                                                .font_weight(FontWeight::MEDIUM)
                                                .text_size(px(11.0))
                                                .child("Restart Background Service"),
                                        ),
                                ),
                        )
                        // Open Logs Folder
                        .child(
                            div()
                                .id("btn-open-logs")
                                .flex()
                                .flex_row()
                                .items_center()
                                .justify_between()
                                .px(px(10.0))
                                .py(px(8.0))
                                .rounded_b(px(11.0))
                                .cursor_pointer()
                                .hover(|s| s.bg(theme.tile_hover))
                                .on_click(cx.listener(|_, _, _, _| {
                                    let _ = std::process::Command::new("explorer.exe")
                                        .arg(monitor_agent::data_directory())
                                        .spawn();
                                }))
                                .child(
                                    div()
                                        .flex()
                                        .flex_row()
                                        .items_center()
                                        .gap(px(8.0))
                                        .child(svg_icon(ICON_DOCUMENT, 12.0, theme.icon_primary))
                                        .child(
                                            div()
                                                .text_color(theme.text_primary)
                                                .font_weight(FontWeight::MEDIUM)
                                                .text_size(px(11.0))
                                                .child("Open Logs Folder"),
                                        ),
                                ),
                        ),
                )
                // 2. About Card
                .child(
                    div()
                        .p(px(8.0))
                        .rounded(px(10.0))
                        .bg(theme.card_bg)
                        .border_1()
                        .border_color(theme.card_border)
                        .child(
                            div()
                                .text_color(theme.text_secondary)
                                .text_size(px(9.5))
                                .child(format!("{APP_NAME} Desktop v{APP_VERSION} · Native GPUI")),
                        ),
                ),
        )
}
