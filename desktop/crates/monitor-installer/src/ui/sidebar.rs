use crate::icons::{ICON_MONITOR_LOGO, svg_icon};
use crate::theme::InstallerTheme;
use gpui_kit::*;

pub const VERTICAL_GRADIENT_BAR: &[u8] = br##"<svg viewBox="0 0 2 400" width="2" height="100%" preserveAspectRatio="none" fill="none" xmlns="http://www.w3.org/2000/svg">
  <line x1="1" y1="0" x2="1" y2="400" stroke="url(#bar_grad)" stroke-width="2"/>
  <defs>
    <linearGradient id="bar_grad" x1="0" y1="0" x2="0" y2="400" gradientUnits="userSpaceOnUse">
      <stop offset="0%" stop-color="#a855f7"/>
      <stop offset="50%" stop-color="#8b5cf6"/>
      <stop offset="100%" stop-color="#38bdf8"/>
    </linearGradient>
  </defs>
</svg>"##;

pub fn render_sidebar(theme: &InstallerTheme, mode_label: &str) -> impl IntoElement {
    div()
        .flex()
        .flex_row()
        .h_full()
        .child(
            div()
                .flex()
                .flex_col()
                .items_center()
                .justify_between()
                .w(px(148.0))
                .h_full()
                .py(px(32.0))
                .px(px(12.0))
                .bg(theme.sidebar_bg)
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .items_center()
                        .gap(px(10.0))
                        .child(svg_icon(ICON_MONITOR_LOGO, 54.0, theme.accent_purple))
                        .child(
                            div()
                                .text_color(theme.text_primary)
                                .font_weight(FontWeight::BOLD)
                                .text_size(px(17.0))
                                .child("Monitor"),
                        )
                        .child(
                            div()
                                .px(px(8.0))
                                .py(px(2.0))
                                .rounded_full()
                                .bg(theme.accent_purple_light)
                                .border_1()
                                .border_color(theme.card_border)
                                .text_color(theme.accent_cyan)
                                .font_weight(FontWeight::MEDIUM)
                                .text_size(px(10.0))
                                .child(concat!("v", env!("CARGO_PKG_VERSION"))),
                        ),
                )
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .items_center()
                        .gap(px(4.0))
                        .child(
                            div()
                                .text_color(theme.text_muted)
                                .text_size(px(10.0))
                                .child(mode_label.to_string()),
                        )
                        .child(
                            div()
                                .text_color(theme.text_muted)
                                .text_size(px(9.5))
                                .child("Morning Star Web"),
                        ),
                ),
        )
        .child(svg().data(VERTICAL_GRADIENT_BAR).w(px(2.0)).h_full())
}
