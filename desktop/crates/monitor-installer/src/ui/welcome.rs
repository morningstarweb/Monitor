use crate::engine::ExistingInstall;
use crate::icons::{ICON_GIT_BRANCH, ICON_INSIGHTS, ICON_SECURITY, ICON_UPGRADE, svg_icon};
use crate::theme::InstallerTheme;
use gpui_kit::{prelude::FluentBuilder, *};

pub fn render_welcome_page<FCancel, FNext, FExpress>(
    existing: Option<&ExistingInstall>,
    theme: &InstallerTheme,
    on_cancel: FCancel,
    on_next: FNext,
    on_express_upgrade: FExpress,
) -> impl IntoElement
where
    FCancel: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FNext: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FExpress: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    let target_ver = env!("CARGO_PKG_VERSION");

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
                                .text_size(px(24.0))
                                .child(if existing.is_some() {
                                    "Update Monitor"
                                } else {
                                    "Welcome to Monitor"
                                }),
                        )
                        .child(
                            div()
                                .text_color(theme.accent_purple)
                                .font_weight(FontWeight::SEMIBOLD)
                                .text_size(px(13.5))
                                .child("Remote Development & System Monitor"),
                        )
                        .child(
                            div()
                                .text_color(theme.text_secondary)
                                .text_size(px(12.0))
                                .child(
                                    "Set up the Windows host to monitor your system and connect your Android device.",
                                ),
                        ),
                )
                .when_some(existing, |el, prev| {
                    let badge_text = match prev.version.as_deref() {
                        Some(v) if v == target_ver => format!(
                            "Installed v{v} detected at {} — ready to repair or reinstall.",
                            prev.install_dir.display()
                        ),
                        Some(v) => format!(
                            "Existing v{v} detected — ready for seamless upgrade to v{target_ver} (pairings preserved)."
                        ),
                        None => format!(
                            "Existing installation detected at {} — ready to update to v{target_ver}.",
                            prev.install_dir.display()
                        ),
                    };
                    el.child(
                        div()
                            .flex()
                            .flex_row()
                            .items_center()
                            .gap(px(8.0))
                            .px(px(12.0))
                            .py(px(8.0))
                            .rounded(px(8.0))
                            .bg(theme.success_bg)
                            .border_1()
                            .border_color(theme.success_border)
                            .child(svg_icon(ICON_UPGRADE, 14.0, theme.success_fg))
                            .child(
                                div()
                                    .flex_1()
                                    .text_color(theme.text_primary)
                                    .text_size(px(11.0))
                                    .whitespace_normal()
                                    .child(badge_text),
                            ),
                    )
                })
                // 3 Key Feature Rows
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .gap(px(10.0))
                        .pt(px(2.0))
                        .child(render_feature_row(
                            ICON_INSIGHTS,
                            "Live system insights",
                            "Real-time CPU, RAM, storage, process trees, and battery vitals.",
                            theme,
                        ))
                        .child(render_feature_row(
                            ICON_GIT_BRANCH,
                            "Git workspace control",
                            "Visual commit DAG topology, staged diffs, branch switching, and push/pull.",
                            theme,
                        ))
                        .child(render_feature_row(
                            ICON_SECURITY,
                            "Secure device pairing",
                            "Encrypted peer-to-peer pairing with Noise protocol over local Wi-Fi & Cloud.",
                            theme,
                        )),
                ),
        )
        // Bottom Action Bar
        .child(
            div()
                .flex()
                .flex_row()
                .items_center()
                .justify_between()
                .w_full()
                .pt(px(12.0))
                .border_t_1()
                .border_color(theme.card_border)
                .child(
                    div()
                        .text_color(theme.text_muted)
                        .text_size(px(10.5))
                        .child("No administrator access required."),
                )
                .child(
                    div()
                        .flex()
                        .flex_row()
                        .items_center()
                        .gap(px(8.0))
                        .child(
                            div()
                                .id("btn-cancel")
                                .flex()
                                .items_center()
                                .justify_center()
                                .px(px(14.0))
                                .py(px(7.0))
                                .rounded(px(7.0))
                                .bg(theme.btn_secondary_bg)
                                .border_1()
                                .border_color(theme.btn_secondary_border)
                                .cursor_pointer()
                                .hover(|s| s.bg(theme.btn_secondary_hover))
                                .on_click(on_cancel)
                                .child(
                                    div()
                                        .text_color(theme.btn_secondary_text)
                                        .font_weight(FontWeight::MEDIUM)
                                        .text_size(px(12.0))
                                        .child("Cancel"),
                                ),
                        )
                        .map(|el| {
                            if existing.is_some() {
                                el.child(
                                    div()
                                        .id("btn-customize")
                                        .flex()
                                        .items_center()
                                        .justify_center()
                                        .px(px(14.0))
                                        .py(px(7.0))
                                        .rounded(px(7.0))
                                        .bg(theme.btn_secondary_bg)
                                        .border_1()
                                        .border_color(theme.btn_secondary_border)
                                        .cursor_pointer()
                                        .hover(|s| s.bg(theme.btn_secondary_hover))
                                        .on_click(on_next)
                                        .child(
                                            div()
                                                .text_color(theme.btn_secondary_text)
                                                .font_weight(FontWeight::MEDIUM)
                                                .text_size(px(12.0))
                                                .child("Customize"),
                                        ),
                                )
                                .child(
                                    div()
                                        .id("btn-express-upgrade")
                                        .flex()
                                        .items_center()
                                        .justify_center()
                                        .px(px(18.0))
                                        .py(px(7.0))
                                        .rounded(px(7.0))
                                        .bg(theme.btn_primary_bg)
                                        .cursor_pointer()
                                        .hover(|s| s.bg(theme.btn_primary_hover))
                                        .on_click(on_express_upgrade)
                                        .child(
                                            div()
                                                .text_color(theme.btn_primary_text)
                                                .font_weight(FontWeight::BOLD)
                                                .text_size(px(12.0))
                                                .child("Quick Upgrade"),
                                        ),
                                )
                            } else {
                                el.child(
                                    div()
                                        .id("btn-get-started")
                                        .flex()
                                        .items_center()
                                        .justify_center()
                                        .px(px(20.0))
                                        .py(px(7.0))
                                        .rounded(px(7.0))
                                        .bg(theme.btn_primary_bg)
                                        .cursor_pointer()
                                        .hover(|s| s.bg(theme.btn_primary_hover))
                                        .on_click(on_next)
                                        .child(
                                            div()
                                                .text_color(theme.btn_primary_text)
                                                .font_weight(FontWeight::BOLD)
                                                .text_size(px(12.0))
                                                .child("Get started"),
                                        ),
                                )
                            }
                        }),
                ),
        )
}

fn render_feature_row(
    icon_data: &'static [u8],
    title: &'static str,
    desc: &'static str,
    theme: &InstallerTheme,
) -> impl IntoElement {
    div()
        .flex()
        .flex_row()
        .items_center()
        .gap(px(12.0))
        .child(
            div()
                .flex()
                .items_center()
                .justify_center()
                .w(px(34.0))
                .h(px(34.0))
                .rounded(px(8.0))
                .bg(theme.card_bg)
                .border_1()
                .border_color(theme.card_border)
                .child(svg_icon(icon_data, 17.0, theme.accent_purple)),
        )
        .child(
            div()
                .flex()
                .flex_col()
                .flex_1()
                .min_w(px(0.0))
                .gap(px(1.0))
                .child(
                    div()
                        .text_color(theme.text_primary)
                        .font_weight(FontWeight::SEMIBOLD)
                        .text_size(px(13.0))
                        .child(title),
                )
                .child(
                    div()
                        .text_color(theme.text_secondary)
                        .text_size(px(10.5))
                        .whitespace_normal()
                        .child(desc),
                ),
        )
}
