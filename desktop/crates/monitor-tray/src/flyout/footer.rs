use super::icons::{ICON_POWER, svg_icon};
use super::theme::FlyoutTheme;
use gpui_kit::*;

pub fn render_footer<V: 'static>(is_dark_mode: bool, cx: &mut Context<V>) -> impl IntoElement {
    let theme = FlyoutTheme::for_mode(is_dark_mode);

    div()
        .id("btn-quit")
        .flex()
        .flex_row()
        .items_center()
        .gap(px(8.0))
        .w_full()
        .h(px(35.0))
        .px(px(12.0))
        .rounded(px(12.0))
        .bg(theme.quit_bg)
        .border_1()
        .border_color(theme.quit_border)
        .shadow_sm()
        .cursor_pointer()
        .hover(|s| s.bg(theme.quit_hover))
        .on_click(cx.listener(|_, _, _, cx| {
            cx.quit();
        }))
        .child(svg_icon(ICON_POWER, 13.5, theme.quit_text))
        .child(
            div()
                .text_color(theme.quit_text)
                .font_weight(FontWeight::BOLD)
                .text_size(px(11.5))
                .child("Quit Monitor"),
        )
}
