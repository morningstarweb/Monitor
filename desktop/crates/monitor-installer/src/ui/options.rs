use crate::icons::{ICON_CHECK, ICON_WARNING, svg_icon};
use crate::theme::InstallerTheme;
use gpui_kit::{prelude::FluentBuilder, *};
use std::path::Path;

#[derive(Debug, Clone, Copy)]
pub struct InstallOptionFlags {
    pub desktop_shortcut: bool,
    pub start_with_windows: bool,
    pub clean_install: bool,
    pub is_upgrade: bool,
}

pub struct OptionCallbacks<FBack, FCancel, FInstall, FDesktop, FStartup, FClean> {
    pub on_back: FBack,
    pub on_cancel: FCancel,
    pub on_install: FInstall,
    pub on_toggle_desktop: FDesktop,
    pub on_toggle_startup: FStartup,
    pub on_toggle_clean: FClean,
}

impl<FBack, FCancel, FInstall, FDesktop, FStartup, FClean> std::fmt::Debug
    for OptionCallbacks<FBack, FCancel, FInstall, FDesktop, FStartup, FClean>
{
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("OptionCallbacks").finish_non_exhaustive()
    }
}

pub fn render_options_page<FBack, FCancel, FInstall, FDesktop, FStartup, FClean>(
    install_dir: &Path,
    flags: InstallOptionFlags,
    theme: &InstallerTheme,
    callbacks: OptionCallbacks<FBack, FCancel, FInstall, FDesktop, FStartup, FClean>,
) -> impl IntoElement
where
    FBack: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FCancel: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FInstall: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FDesktop: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FStartup: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FClean: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    let OptionCallbacks {
        on_back,
        on_cancel,
        on_install,
        on_toggle_desktop,
        on_toggle_startup,
        on_toggle_clean,
    } = callbacks;
    let desktop_shortcut = flags.desktop_shortcut;
    let start_with_windows = flags.start_with_windows;
    let clean_install = flags.clean_install;
    let primary_label = if flags.is_upgrade {
        "Upgrade"
    } else {
        "Install"
    };

    div()
        .flex()
        .flex_col()
        .justify_between()
        .w_full()
        .h_full()
        // Top Content
        .child(
            div()
                .flex()
                .flex_col()
                .gap(px(14.0))
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .gap(px(4.0))
                        .child(
                            div()
                                .text_color(theme.text_primary)
                                .font_weight(FontWeight::BOLD)
                                .text_size(px(23.0))
                                .child("Choose setup options"),
                        )
                        .child(
                            div()
                                .text_color(theme.text_secondary)
                                .text_size(px(12.0))
                                .child("Select how Monitor integrates with Windows."),
                        ),
                )
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .gap(px(10.0))
                        .child(render_option_row(
                            "row-desktop-shortcut",
                            desktop_shortcut,
                            "Create a desktop shortcut",
                            "Quick access from your desktop",
                            theme,
                            on_toggle_desktop,
                        ))
                        .child(render_option_row(
                            "row-startup-windows",
                            start_with_windows,
                            "Start Monitor when Windows starts",
                            "Keep the Windows host available in the system tray",
                            theme,
                            on_toggle_startup,
                        ))
                        .child(render_option_row(
                            "row-clean-install",
                            clean_install,
                            "Perform a fresh clean installation",
                            "Deletes saved device pairings, session tokens, and local database",
                            theme,
                            on_toggle_clean,
                        )),
                )
                .when(clean_install, |el| {
                    el.child(
                        div()
                            .flex()
                            .flex_row()
                            .items_center()
                            .gap(px(8.0))
                            .px(px(10.0))
                            .py(px(7.0))
                            .rounded(px(7.0))
                            .bg(theme.warning_bg)
                            .border_1()
                            .border_color(theme.warning_border)
                            .child(svg_icon(ICON_WARNING, 13.0, theme.warning_fg))
                            .child(
                                div()
                                    .flex_1()
                                    .text_color(theme.warning_fg)
                                    .text_size(px(10.5))
                                    .whitespace_normal()
                                    .child("Caution: Clean install resets %LOCALAPPDATA%\\Monitor; mobile devices will need to re-pair."),
                            ),
                    )
                })
                .child(
                    div()
                        .px(px(10.0))
                        .py(px(7.0))
                        .rounded(px(7.0))
                        .bg(theme.input_bg)
                        .border_1()
                        .border_color(theme.card_border)
                        .child(
                            div()
                                .text_color(theme.text_muted)
                                .text_size(px(10.5))
                                .overflow_hidden()
                                .child(format!(
                                    "Target: {}  •  Start Menu shortcut included",
                                    install_dir.display()
                                )),
                        ),
                ),
        )
        // Bottom Action Bar
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
                        .flex()
                        .flex_row()
                        .items_center()
                        .gap(px(10.0))
                        .child(secondary_btn("btn-back", "Back", theme, on_back))
                        .child(secondary_btn("btn-cancel", "Cancel", theme, on_cancel))
                        .child(
                            div()
                                .id("btn-install")
                                .flex()
                                .items_center()
                                .justify_center()
                                .px(px(22.0))
                                .py(px(7.0))
                                .rounded(px(7.0))
                                .bg(theme.btn_primary_bg)
                                .cursor_pointer()
                                .hover(|s| s.bg(theme.btn_primary_hover))
                                .on_click(on_install)
                                .child(
                                    div()
                                        .text_color(theme.btn_primary_text)
                                        .font_weight(FontWeight::BOLD)
                                        .text_size(px(12.0))
                                        .child(primary_label),
                                ),
                        ),
                ),
        )
}

fn secondary_btn(
    id: &'static str,
    label: &'static str,
    theme: &InstallerTheme,
    on_click: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
) -> impl IntoElement {
    div()
        .id(id)
        .flex()
        .items_center()
        .justify_center()
        .px(px(16.0))
        .py(px(7.0))
        .rounded(px(7.0))
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

fn render_option_row<FToggle>(
    id: &'static str,
    checked: bool,
    title: &'static str,
    desc: &'static str,
    theme: &InstallerTheme,
    on_toggle: FToggle,
) -> impl IntoElement
where
    FToggle: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    div()
        .id(id)
        .flex()
        .flex_row()
        .items_start()
        .gap(px(10.0))
        .p(px(9.0))
        .rounded(px(8.0))
        .bg(theme.card_bg)
        .border_1()
        .border_color(if checked {
            theme.card_border
        } else {
            theme.input_border
        })
        .cursor_pointer()
        .hover(|s| s.bg(rgba(0xffffff08)))
        .on_click(on_toggle)
        .child(
            div()
                .flex()
                .items_center()
                .justify_center()
                .w(px(16.0))
                .h(px(16.0))
                .mt(px(2.0))
                .rounded(px(4.0))
                .bg(if checked {
                    theme.accent_purple
                } else {
                    rgba(0x00000000)
                })
                .border_1()
                .border_color(if checked {
                    theme.accent_purple
                } else {
                    theme.step_track
                })
                .when(checked, |s| {
                    s.child(svg_icon(ICON_CHECK, 9.5, rgba(0xffffffff)))
                }),
        )
        .child(
            div()
                .flex()
                .flex_col()
                .gap(px(1.0))
                .child(
                    div()
                        .text_color(theme.text_primary)
                        .font_weight(FontWeight::SEMIBOLD)
                        .text_size(px(12.5))
                        .child(title),
                )
                .child(
                    div()
                        .text_color(theme.text_secondary)
                        .text_size(px(10.5))
                        .child(desc),
                ),
        )
}
