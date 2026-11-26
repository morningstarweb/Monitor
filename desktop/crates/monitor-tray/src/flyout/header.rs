use super::icons::{ICON_BELL, ICON_GEAR, svg_icon};
use super::state::FlyoutState;
use super::theme::FlyoutTheme;
use gpui_kit::{prelude::FluentBuilder, *};
use monitor_core::brand_config::{APP_NAME, APP_VERSION};

static APP_ICON_PATH: std::sync::OnceLock<std::path::PathBuf> = std::sync::OnceLock::new();

fn get_app_icon_path() -> std::path::PathBuf {
    APP_ICON_PATH
        .get_or_init(|| {
            let temp_path = std::env::temp_dir().join("monitor_brand_icon.png");
            let bytes = include_bytes!("brand_icon_64.png");
            if !temp_path.exists()
                || std::fs::metadata(&temp_path)
                    .map(|m| m.len() != bytes.len() as u64)
                    .unwrap_or(true)
            {
                let _ = std::fs::write(&temp_path, bytes);
            }
            temp_path
        })
        .clone()
}

pub fn render_header<FBell, FGear>(
    state: &FlyoutState,
    on_bell: FBell,
    on_gear: FGear,
) -> impl IntoElement
where
    FBell: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FGear: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    let theme = FlyoutTheme::for_mode(state.is_dark_mode);

    div()
        .flex()
        .flex_row()
        .items_center()
        .justify_between()
        .w_full()
        .px(px(2.0))
        .child(
            div()
                .flex()
                .flex_row()
                .items_center()
                .gap(px(10.0))
                // Official Monitor App Squircle Button
                .child(
                    div()
                        .flex()
                        .items_center()
                        .justify_center()
                        .w(px(34.0))
                        .h(px(34.0))
                        .rounded(px(9.0))
                        .border_1()
                        .border_color(if theme.is_dark {
                            rgba(0x38457488)
                        } else {
                            rgba(0x00000015)
                        })
                        .shadow_sm()
                        .overflow_hidden()
                        .child(img(get_app_icon_path()).size_full()),
                )
                // App Title & Online Status
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .gap(px(1.5))
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
                                        .text_size(px(13.5))
                                        .child(APP_NAME),
                                )
                                .child(
                                    div()
                                        .px(px(5.0))
                                        .py(px(1.0))
                                        .rounded_full()
                                        .bg(if theme.is_dark {
                                            rgba(0xffffff15)
                                        } else {
                                            rgba(0x0000000a)
                                        })
                                        .text_color(theme.text_secondary)
                                        .text_size(px(10.0))
                                        .child(format!("v{APP_VERSION}")),
                                ),
                        )
                        .child(
                            div()
                                .flex()
                                .flex_row()
                                .items_center()
                                .gap(px(5.0))
                                .child(
                                    div()
                                        .w(px(5.5))
                                        .h(px(5.5))
                                        .rounded_full()
                                        .bg(rgba(0x22c55eff)),
                                )
                                .child(
                                    div()
                                        .text_color(theme.text_secondary)
                                        .text_size(px(10.0))
                                        .child(state.status_text.clone()),
                                ),
                        ),
                ),
        )
        // Header Icon Actions (Bell & Settings)
        .child(
            div()
                .flex()
                .flex_row()
                .items_center()
                .gap(px(6.0))
                .child(
                    div()
                        .id("btn-bell")
                        .relative()
                        .flex()
                        .items_center()
                        .justify_center()
                        .w(px(29.0))
                        .h(px(29.0))
                        .rounded_full()
                        .bg(theme.card_bg)
                        .border_1()
                        .border_color(theme.card_border)
                        .cursor_pointer()
                        .hover(|s| s.bg(theme.tile_hover))
                        .on_click(on_bell)
                        .child(svg_icon(ICON_BELL, 14.0, theme.icon_primary))
                        .when(
                            !state.alerts.is_empty() || !state.active_agent_sessions.is_empty(),
                            |s| {
                                s.child(
                                    div()
                                        .absolute()
                                        .top(px(4.0))
                                        .right(px(4.0))
                                        .w(px(6.0))
                                        .h(px(6.0))
                                        .rounded_full()
                                        .bg(
                                            if state.alerts.iter().any(|a| a.severity == "critical")
                                            {
                                                rgba(0xef4444ff)
                                            } else {
                                                rgba(0x38bdf8ff)
                                            },
                                        ),
                                )
                            },
                        ),
                )
                .child(
                    div()
                        .id("btn-gear")
                        .flex()
                        .items_center()
                        .justify_center()
                        .w(px(29.0))
                        .h(px(29.0))
                        .rounded_full()
                        .bg(theme.card_bg)
                        .border_1()
                        .border_color(theme.card_border)
                        .cursor_pointer()
                        .hover(|s| s.bg(theme.tile_hover))
                        .on_click(on_gear)
                        .child(svg_icon(ICON_GEAR, 14.0, theme.icon_primary)),
                ),
        )
}
