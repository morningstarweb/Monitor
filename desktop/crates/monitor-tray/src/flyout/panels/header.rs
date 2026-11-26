use super::super::icons::{ICON_CHEVRON_LEFT, svg_icon};
use super::super::theme::FlyoutTheme;
use gpui_kit::*;

pub fn render_panel_header<FBack>(
    title: &'static str,
    is_dark: bool,
    on_back: FBack,
    right_action: Option<AnyElement>,
) -> impl IntoElement
where
    FBack: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    render_panel_header_with_extra(title, is_dark, on_back, None, right_action)
}

pub fn render_panel_header_centered<FBack>(
    title: &'static str,
    is_dark: bool,
    on_back: FBack,
    right_action: Option<AnyElement>,
) -> impl IntoElement
where
    FBack: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    let theme = FlyoutTheme::for_mode(is_dark);

    div()
        .flex()
        .flex_row()
        .items_center()
        .justify_between()
        .w_full()
        .px(px(2.0))
        .pb(px(4.0))
        .border_b_1()
        .border_color(theme.card_border)
        .child(
            div()
                .flex()
                .items_center()
                .justify_start()
                .min_w(px(78.0))
                .child(
                    div()
                        .id("btn-back")
                        .flex()
                        .flex_row()
                        .items_center()
                        .gap(px(3.0))
                        .px(px(8.0))
                        .py(px(3.5))
                        .rounded_full()
                        .bg(theme.card_bg)
                        .border_1()
                        .border_color(theme.card_border)
                        .cursor_pointer()
                        .hover(|s| s.bg(theme.tile_hover))
                        .on_click(on_back)
                        .child(svg_icon(ICON_CHEVRON_LEFT, 11.0, theme.text_secondary))
                        .child(
                            div()
                                .text_color(theme.text_secondary)
                                .font_weight(FontWeight::MEDIUM)
                                .text_size(px(10.5))
                                .child("Back"),
                        ),
                ),
        )
        .child(
            div().flex().flex_1().items_center().justify_center().child(
                div()
                    .text_color(theme.text_primary)
                    .font_weight(FontWeight::BOLD)
                    .text_size(px(13.0))
                    .child(title),
            ),
        )
        .child(
            div()
                .flex()
                .items_center()
                .justify_end()
                .min_w(px(78.0))
                .children(right_action),
        )
}

pub fn render_panel_header_with_extra<FBack>(
    title: &'static str,
    is_dark: bool,
    on_back: FBack,
    next_to_back: Option<AnyElement>,
    right_action: Option<AnyElement>,
) -> impl IntoElement
where
    FBack: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    let theme = FlyoutTheme::for_mode(is_dark);

    div()
        .flex()
        .flex_row()
        .items_center()
        .justify_between()
        .w_full()
        .px(px(2.0))
        .pb(px(4.0))
        .border_b_1()
        .border_color(theme.card_border)
        .child(
            div()
                .flex()
                .flex_row()
                .items_center()
                .gap(px(6.0))
                // Back Button
                .child(
                    div()
                        .id("btn-back")
                        .flex()
                        .flex_row()
                        .items_center()
                        .gap(px(3.0))
                        .px(px(8.0))
                        .py(px(3.5))
                        .rounded_full()
                        .bg(theme.card_bg)
                        .border_1()
                        .border_color(theme.card_border)
                        .cursor_pointer()
                        .hover(|s| s.bg(theme.tile_hover))
                        .on_click(on_back)
                        .child(svg_icon(ICON_CHEVRON_LEFT, 11.0, theme.text_secondary))
                        .child(
                            div()
                                .text_color(theme.text_secondary)
                                .font_weight(FontWeight::MEDIUM)
                                .text_size(px(10.5))
                                .child("Back"),
                        ),
                )
                .children(next_to_back)
                // Panel Title
                .child(
                    div()
                        .text_color(theme.text_primary)
                        .font_weight(FontWeight::BOLD)
                        .text_size(px(13.0))
                        .child(title),
                ),
        )
        .children(right_action)
}
