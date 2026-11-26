use super::icons::{ICON_CHIP, ICON_PLAY, ICON_WINDOWS, svg_icon};
use super::state::FlyoutState;
use super::theme::FlyoutTheme;
use gpui_kit::{prelude::FluentBuilder, *};

pub fn render_device_card(state: &FlyoutState) -> impl IntoElement {
    let theme = FlyoutTheme::for_mode(state.is_dark_mode);
    let ram_pct = if state.memory_total_gb > 0.0 {
        ((state.memory_used_gb / state.memory_total_gb) * 100.0).round() as u32
    } else {
        42
    };

    div()
        .flex()
        .flex_row()
        .items_center()
        .justify_between()
        .w_full()
        .px(px(10.0))
        .py(px(7.0))
        .rounded(px(14.0))
        .bg(theme.card_bg)
        .border_1()
        .border_color(theme.card_border)
        .shadow_sm()
        .child(
            div()
                .flex()
                .flex_row()
                .items_center()
                .gap(px(10.0))
                // 3D Metallic Laptop Frame with Real System Wallpaper Display
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .items_center()
                        .justify_center()
                        .w(px(52.0))
                        .h(px(35.0))
                        // Laptop Lid & Bezel
                        .child(
                            div()
                                .relative()
                                .w(px(48.0))
                                .h(px(29.0))
                                .rounded(px(3.5))
                                .bg(rgba(0x0f172aff))
                                .border_1()
                                .border_color(rgba(0x94a3b8cc))
                                .overflow_hidden()
                                .shadow_sm()
                                // Camera Dot
                                .child(
                                    div()
                                        .absolute()
                                        .top(px(1.0))
                                        .left(px(23.0))
                                        .w(px(2.0))
                                        .h(px(2.0))
                                        .rounded_full()
                                        .bg(rgba(0x64748bff)),
                                )
                                // Screen Area: Displays the Real Windows Desktop Wallpaper!
                                .child(div().w_full().h_full().overflow_hidden().child(
                                    if let Some(ref p) = state.wallpaper_path {
                                        img(p.clone()).w_full().h_full()
                                    } else {
                                        img(r"C:\WINDOWS\web\wallpaper\Windows\img0.jpg")
                                            .w_full()
                                            .h_full()
                                    },
                                )),
                        )
                        // Metallic Keyboard Deck Base
                        .child(
                            div()
                                .w(px(54.0))
                                .h(px(3.5))
                                .rounded_b(px(2.5))
                                .bg(rgba(0xcbd5e1ff))
                                .border_b_1()
                                .border_color(rgba(0x94a3b8ff))
                                .shadow_sm(),
                        ),
                )
                // Device Details: Hostname + OS + CPU/RAM Chip
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .gap(px(1.5))
                        .child(
                            div()
                                .text_color(theme.text_primary)
                                .font_weight(FontWeight::BOLD)
                                .text_size(px(12.5))
                                .child(state.hostname.clone()),
                        )
                        .child(
                            div()
                                .flex()
                                .flex_row()
                                .items_center()
                                .gap(px(5.0))
                                .child(svg_icon(ICON_WINDOWS, 10.5, rgba(0x0078d4ff)))
                                .child(
                                    div()
                                        .text_color(theme.text_secondary)
                                        .text_size(px(10.0))
                                        .child({
                                            if state.os_name.contains("Windows 11") {
                                                "Windows 11".to_string()
                                            } else if state.os_name.contains("Windows 10") {
                                                "Windows 10".to_string()
                                            } else if state.os_name.contains("Windows Server") {
                                                "Windows Server".to_string()
                                            } else if !state.os_name.is_empty() {
                                                state
                                                    .os_name
                                                    .trim_start_matches("Microsoft ")
                                                    .split(" (")
                                                    .next()
                                                    .unwrap_or("Windows")
                                                    .to_string()
                                            } else {
                                                "Windows".to_string()
                                            }
                                        }),
                                ),
                        )
                        .child(
                            div()
                                .flex()
                                .flex_row()
                                .items_center()
                                .gap(px(4.0))
                                .child(svg_icon(ICON_CHIP, 10.0, theme.text_secondary))
                                .child(
                                    div()
                                        .text_color(theme.text_secondary)
                                        .text_size(px(9.5))
                                        .child(format!(
                                            "CPU {:.0}% • RAM {}%",
                                            state.cpu_percent, ram_pct
                                        )),
                                ),
                        ),
                ),
        )
        // Right Status Pill (shows active AI Agent if detected, else "▶ Running")
        .child({
            let (pill_text, pill_bg, pill_border, pill_color) =
                if let Some(agent) = state.active_agent_sessions.first() {
                    (
                        format!("🤖 {}", agent.profile_name),
                        rgba(0xa855f720),
                        rgba(0xa855f755),
                        rgba(0xa855f7ff),
                    )
                } else {
                    (
                        "Running".to_string(),
                        theme.pill_running_bg,
                        theme.pill_running_border,
                        theme.pill_running_text,
                    )
                };
            div()
                .flex()
                .flex_row()
                .items_center()
                .gap(px(4.0))
                .px(px(8.0))
                .py(px(3.5))
                .rounded_full()
                .bg(pill_bg)
                .border_1()
                .border_color(pill_border)
                .when(state.active_agent_sessions.is_empty(), |s| {
                    s.child(svg_icon(ICON_PLAY, 7.5, pill_color))
                })
                .child(
                    div()
                        .text_color(pill_color)
                        .font_weight(FontWeight::BOLD)
                        .text_size(px(9.5))
                        .child(pill_text),
                )
        })
}
