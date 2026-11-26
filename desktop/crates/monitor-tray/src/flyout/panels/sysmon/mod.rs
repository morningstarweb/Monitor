mod details;
mod metrics;

use crate::client::Command;
use crate::flyout::panels::header::render_panel_header;
use crate::flyout::state::FlyoutState;
use crate::flyout::theme::FlyoutTheme;
use gpui_kit::{prelude::FluentBuilder, *};
use std::sync::mpsc::Sender;

pub fn render_sysmon_panel<V: 'static, FBack>(
    state: &FlyoutState,
    command_sender: &Option<Sender<Command>>,
    cx: &mut Context<V>,
    on_back: FBack,
) -> impl IntoElement
where
    FBack: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    let theme = FlyoutTheme::for_mode(state.is_dark_mode);
    let tx_poll = command_sender.clone();
    let track_bg = if theme.is_dark {
        rgba(0xffffff15)
    } else {
        rgba(0x00000010)
    };

    div()
        .flex()
        .flex_col()
        .gap(px(8.0))
        .w_full()
        .h_full()
        .child(render_panel_header(
            "System Monitor",
            state.is_dark_mode,
            on_back,
            None,
        ))
        .child(
            div()
                .id("sysmon-scroll-container")
                .flex()
                .flex_col()
                .gap(px(8.0))
                .w_full()
                .flex_1()
                .overflow_y_scroll()
                .child(metrics::render_cpu_card(state, &theme, track_bg))
                .child(metrics::render_ram_card(state, &theme, track_bg))
                .children(metrics::render_disk_cards(state, &theme, track_bg))
                .when(!state.top_processes.is_empty(), |this| {
                    this.child(details::render_top_processes_card(state, &theme))
                })
                .child(details::render_specs_card(state, &theme))
                .child(details::render_refresh_button(tx_poll, &theme, cx)),
        )
}
