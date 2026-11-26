use super::icons::{
    ICON_CHEVRON_RIGHT, ICON_DOCUMENT, ICON_REFRESH, ICON_SHIELD, ICON_WINDOWS, svg_icon,
};
use super::state::FlyoutState;
use super::theme::FlyoutTheme;
use gpui_kit::{prelude::FluentBuilder, *};

pub fn render_actions<FPerm, FAuto, FLogs, FRestart>(
    state: &FlyoutState,
    on_permissions: FPerm,
    on_autostart: FAuto,
    on_logs: FLogs,
    on_restart: FRestart,
) -> impl IntoElement
where
    FPerm: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FAuto: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FLogs: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FRestart: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    let theme = FlyoutTheme::for_mode(state.is_dark_mode);
    let autostart_val = state.autostart_enabled;

    div()
        .flex()
        .flex_col()
        .w_full()
        .rounded(px(14.0))
        .bg(theme.card_bg)
        .border_1()
        .border_color(theme.card_border)
        .shadow_sm()
        .overflow_hidden()
        // Row 1: Start with Windows
        .child(
            div()
                .id("row-autostart")
                .flex()
                .flex_row()
                .items_center()
                .justify_between()
                .px(px(12.0))
                .py(px(6.5))
                .rounded_t(px(13.0))
                .border_b_1()
                .border_color(theme.card_border)
                .cursor_pointer()
                .hover(|s| s.bg(theme.tile_hover))
                .on_click(on_autostart)
                .child(
                    div()
                        .flex()
                        .flex_row()
                        .items_center()
                        .gap(px(9.0))
                        .child(svg_icon(ICON_WINDOWS, 13.0, rgba(0x0078d4ff)))
                        .child(
                            div()
                                .text_color(theme.text_primary)
                                .font_weight(FontWeight::MEDIUM)
                                .text_size(px(11.5))
                                .child("Start with Windows"),
                        ),
                )
                // Windows 11 / Apple Pill Toggle Switch
                .child(
                    div()
                        .flex()
                        .items_center()
                        .w(px(34.0))
                        .h(px(19.0))
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
                        .child(
                            div()
                                .w(px(13.0))
                                .h(px(13.0))
                                .rounded_full()
                                .bg(rgba(0xffffffff))
                                .shadow_sm()
                                .when(autostart_val, |s| s.ml(px(15.0))),
                        ),
                ),
        )
        // Row 2: Permissions (Navigates to Permissions Sub-Panel)
        .child(
            div()
                .id("row-permissions")
                .flex()
                .flex_row()
                .items_center()
                .justify_between()
                .px(px(12.0))
                .py(px(6.5))
                .border_b_1()
                .border_color(theme.card_border)
                .cursor_pointer()
                .hover(|s| s.bg(theme.tile_hover))
                .on_click(on_permissions)
                .child(
                    div()
                        .flex()
                        .flex_row()
                        .items_center()
                        .gap(px(9.0))
                        .child(svg_icon(ICON_SHIELD, 14.0, theme.icon_primary))
                        .child(
                            div()
                                .text_color(theme.text_primary)
                                .font_weight(FontWeight::MEDIUM)
                                .text_size(px(11.5))
                                .child("Permissions"),
                        ),
                )
                .child(svg_icon(ICON_CHEVRON_RIGHT, 11.5, theme.text_secondary)),
        )
        // Row 3: Open Logs Folder
        .child(
            div()
                .id("row-logs")
                .flex()
                .flex_row()
                .items_center()
                .justify_between()
                .px(px(12.0))
                .py(px(6.5))
                .border_b_1()
                .border_color(theme.card_border)
                .cursor_pointer()
                .hover(|s| s.bg(theme.tile_hover))
                .on_click(on_logs)
                .child(
                    div()
                        .flex()
                        .flex_row()
                        .items_center()
                        .gap(px(9.0))
                        .child(svg_icon(ICON_DOCUMENT, 14.0, theme.icon_primary))
                        .child(
                            div()
                                .text_color(theme.text_primary)
                                .font_weight(FontWeight::MEDIUM)
                                .text_size(px(11.5))
                                .child("Open Logs Folder"),
                        ),
                )
                .child(svg_icon(ICON_CHEVRON_RIGHT, 11.5, theme.text_secondary)),
        )
        // Row 4: Restart Service
        .child(
            div()
                .id("row-restart")
                .flex()
                .flex_row()
                .items_center()
                .justify_between()
                .px(px(12.0))
                .py(px(6.5))
                .rounded_b(px(13.0))
                .cursor_pointer()
                .hover(|s| s.bg(theme.tile_hover))
                .on_click(on_restart)
                .child(
                    div()
                        .flex()
                        .flex_row()
                        .items_center()
                        .gap(px(9.0))
                        .child(svg_icon(ICON_REFRESH, 14.0, theme.icon_primary))
                        .child(
                            div()
                                .text_color(theme.text_primary)
                                .font_weight(FontWeight::MEDIUM)
                                .text_size(px(11.5))
                                .child("Restart Service"),
                        ),
                )
                .child(svg_icon(ICON_CHEVRON_RIGHT, 11.5, theme.text_secondary)),
        )
}
