use crate::icons::{ICON_CHECK, svg_icon};
use crate::theme::InstallerTheme;
use gpui_kit::*;
use std::path::Path;

pub(super) fn render_uninstall_complete<FC>(
    uninstall_dir: &Path,
    wipe_data: bool,
    theme: &InstallerTheme,
    on_close: FC,
) -> impl IntoElement
where
    FC: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    div()
        .flex()
        .flex_col()
        .justify_between()
        .w_full()
        .h_full()
        .child(
            div()
                .flex()
                .flex_col()
                .gap(px(16.0))
                .child(page_header(
                    "Uninstallation Complete",
                    "Monitor has been successfully removed from your computer.",
                    theme,
                ))
                .child(
                    glass_card(theme)
                        .child(
                            div()
                                .flex()
                                .flex_row()
                                .items_center()
                                .gap(px(10.0))
                                .child(
                                    div()
                                        .flex()
                                        .items_center()
                                        .justify_center()
                                        .w(px(28.0))
                                        .h(px(28.0))
                                        .rounded_full()
                                        .bg(theme.success_bg)
                                        .border_1()
                                        .border_color(theme.success_border)
                                        .child(svg_icon(ICON_CHECK, 13.0, theme.success_fg)),
                                )
                                .child(
                                    div()
                                        .text_color(theme.text_primary)
                                        .font_weight(FontWeight::SEMIBOLD)
                                        .text_size(px(14.0))
                                        .child("All Monitor host binaries and system entries removed."),
                                ),
                        )
                        .child(
                            div()
                                .text_color(theme.text_secondary)
                                .text_size(px(11.5))
                                .child(format!(
                                    "Removed installation directory: {}",
                                    uninstall_dir.display()
                                )),
                        )
                        .child(div().text_color(theme.text_secondary).text_size(px(11.5)).child(
                            if wipe_data {
                                "User configuration, logs, and device pairing credentials were wiped."
                            } else {
                                "Your device pairings and project list in %LOCALAPPDATA%\\Monitor were kept intact."
                            },
                        )),
                ),
        )
        .child(
            div()
                .flex()
                .flex_row()
                .items_center()
                .justify_end()
                .w_full()
                .pt(px(12.0))
                .border_t_1()
                .border_color(theme.card_border)
                .child(
                    div()
                        .id("btn-uninstall-close")
                        .flex()
                        .items_center()
                        .justify_center()
                        .w(px(110.0))
                        .h(px(34.0))
                        .rounded(px(6.0))
                        .bg(theme.btn_primary_bg)
                        .border_1()
                        .border_color(theme.accent_purple)
                        .cursor_pointer()
                        .hover(|s| s.bg(theme.btn_primary_hover))
                        .on_click(on_close)
                        .child(
                            div()
                                .text_color(theme.btn_primary_text)
                                .font_weight(FontWeight::SEMIBOLD)
                                .text_size(px(12.0))
                                .child("Finish"),
                        ),
                ),
        )
}

pub(super) fn page_header(
    title: &'static str,
    subtitle: &'static str,
    theme: &InstallerTheme,
) -> Div {
    div()
        .flex()
        .flex_col()
        .gap(px(4.0))
        .child(
            div()
                .text_color(theme.text_primary)
                .font_weight(FontWeight::BOLD)
                .text_size(px(23.0))
                .child(title),
        )
        .child(
            div()
                .text_color(theme.text_secondary)
                .text_size(px(12.0))
                .child(subtitle),
        )
}

pub(super) fn glass_card(theme: &InstallerTheme) -> Div {
    div()
        .flex()
        .flex_col()
        .gap(px(8.0))
        .p(px(14.0))
        .rounded(px(10.0))
        .bg(theme.card_bg)
        .border_1()
        .border_color(theme.card_border)
}

pub(super) fn bullet_row(text: &'static str, theme: &InstallerTheme) -> Div {
    bullet_row_owned(text.to_string(), theme)
}

pub(super) fn bullet_row_owned(text: String, theme: &InstallerTheme) -> Div {
    div()
        .flex()
        .flex_row()
        .items_start()
        .gap(px(8.0))
        .child(
            div()
                .mt(px(5.0))
                .w(px(5.0))
                .h(px(5.0))
                .rounded_full()
                .bg(theme.accent_purple),
        )
        .child(
            div()
                .flex_1()
                .text_color(theme.text_secondary)
                .text_size(px(11.5))
                .whitespace_normal()
                .child(text),
        )
}

pub(super) fn cancel_btn<F>(
    id: &'static str,
    label: &'static str,
    theme: &InstallerTheme,
    on_click: F,
) -> impl IntoElement
where
    F: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    div()
        .id(id)
        .flex()
        .items_center()
        .justify_center()
        .w(px(90.0))
        .h(px(34.0))
        .rounded(px(6.0))
        .bg(theme.btn_secondary_bg)
        .border_1()
        .border_color(theme.btn_secondary_border)
        .cursor_pointer()
        .hover(|s| s.bg(theme.btn_secondary_hover))
        .on_click(on_click)
        .child(
            div()
                .text_color(theme.btn_secondary_text)
                .font_weight(FontWeight::MEDIUM)
                .text_size(px(12.0))
                .child(label),
        )
}
