use super::super::icons::{
    ICON_CLOUD, ICON_FOLDER, ICON_SHIELD, ICON_SMARTPHONE, ICON_TERMINAL, ICON_TRASH, svg_icon,
};
use super::super::state::FlyoutState;
use super::super::theme::FlyoutTheme;
use super::header::render_panel_header;
use crate::client::Command;
use crate::flyout::MonitorFlyout;
use gpui_kit::{prelude::FluentBuilder, *};
use std::sync::mpsc::Sender;

pub fn render_permissions_panel<FBack>(
    state: &FlyoutState,
    command_sender: &Option<Sender<Command>>,
    cx: &mut Context<MonitorFlyout>,
    on_back: FBack,
) -> impl IntoElement
where
    FBack: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    let theme = FlyoutTheme::for_mode(state.is_dark_mode);
    let tx_cloud = command_sender.clone();
    let tx_local = command_sender.clone();
    let tx_git = command_sender.clone();
    let tx_term = command_sender.clone();
    let tx_chk = command_sender.clone();
    let tx_del = command_sender.clone();

    div()
        .flex()
        .flex_col()
        .gap(px(8.0))
        .w_full()
        .h_full()
        // Header
        .child(render_panel_header(
            "Access Permissions",
            state.is_dark_mode,
            on_back,
            None,
        ))
        // Scrollable Body
        .child(
            div()
                .id("permissions-scroll-container")
                .flex()
                .flex_col()
                .gap(px(8.0))
                .w_full()
                .flex_1()
                .overflow_y_scroll()
                .child(
                    div()
                        .text_color(theme.text_secondary)
                        .text_size(px(10.0))
                        .px(px(2.0))
                        .child("Control what mobile devices are permitted to do on this PC:"),
                )
                // Permissions Rows Container
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .w_full()
                        .rounded(px(12.0))
                        .bg(theme.card_bg)
                        .border_1()
                        .border_color(theme.card_border)
                        .overflow_hidden()
                        // 1. Cloud Relay Sync
                        .child(render_perm_row(
                            PermRowSpec {
                                id: "perm-cloud-row",
                                icon_data: ICON_CLOUD,
                                title: "Cloud Relay Sync",
                                desc: "Allow access through cloud relay",
                                is_active: state.cloud_access_allowed,
                                is_top: true,
                                is_bottom: false,
                            },
                            &theme,
                            cx.listener(move |this, _, _, cx| {
                                this.state.cloud_access_allowed = !this.state.cloud_access_allowed;
                                cx.notify();
                                if let Some(ref tx) = tx_cloud {
                                    let _ = tx.send(Command::ToggleCloudAccess);
                                }
                            }),
                        ))
                        // 2. Local Wi-Fi
                        .child(render_perm_row(
                            PermRowSpec {
                                id: "perm-local-row",
                                icon_data: ICON_SMARTPHONE,
                                title: "Local Wi-Fi Access",
                                desc: "Allow phones on same Wi-Fi network",
                                is_active: state.local_access_allowed,
                                is_top: false,
                                is_bottom: false,
                            },
                            &theme,
                            cx.listener(move |this, _, _, cx| {
                                this.state.local_access_allowed = !this.state.local_access_allowed;
                                cx.notify();
                                if let Some(ref tx) = tx_local {
                                    let _ = tx.send(Command::ToggleLocalAccess);
                                }
                            }),
                        ))
                        // 3. Git Operations
                        .child(render_perm_row(
                            PermRowSpec {
                                id: "perm-git-row",
                                icon_data: ICON_FOLDER,
                                title: "Git Actions & Commits",
                                desc: "Allow staging, commits & branches",
                                is_active: state.git_access_allowed,
                                is_top: false,
                                is_bottom: false,
                            },
                            &theme,
                            cx.listener(move |this, _, _, cx| {
                                this.state.git_access_allowed = !this.state.git_access_allowed;
                                cx.notify();
                                if let Some(ref tx) = tx_git {
                                    let _ = tx.send(Command::ToggleGitAccess);
                                }
                            }),
                        ))
                        // 4. Remote Terminal
                        .child(render_perm_row(
                            PermRowSpec {
                                id: "perm-term-row",
                                icon_data: ICON_TERMINAL,
                                title: "Remote Terminal",
                                desc: "Allow T3 Admin shell commands",
                                is_active: state.terminal_access_allowed,
                                is_top: false,
                                is_bottom: false,
                            },
                            &theme,
                            cx.listener(move |this, _, _, cx| {
                                this.state.terminal_access_allowed =
                                    !this.state.terminal_access_allowed;
                                cx.notify();
                                if let Some(ref tx) = tx_term {
                                    let _ = tx.send(Command::ToggleTerminalAccess);
                                }
                            }),
                        ))
                        // 5. Automated Safety Rollbacks
                        .child(render_perm_row(
                            PermRowSpec {
                                id: "perm-chk-row",
                                icon_data: ICON_SHIELD,
                                title: "Automated Safety Rollbacks",
                                desc: "Allow checkpoint & rollback actions",
                                is_active: state.checkpoint_access_allowed,
                                is_top: false,
                                is_bottom: false,
                            },
                            &theme,
                            cx.listener(move |this, _, _, cx| {
                                this.state.checkpoint_access_allowed =
                                    !this.state.checkpoint_access_allowed;
                                cx.notify();
                                if let Some(ref tx) = tx_chk {
                                    let _ = tx.send(Command::ToggleCheckpointAccess);
                                }
                            }),
                        ))
                        // 6. File Deletions & Process Control
                        .child(render_perm_row(
                            PermRowSpec {
                                id: "perm-del-row",
                                icon_data: ICON_TRASH,
                                title: "File Deletions & Process Kill",
                                desc: "Allow file deletes & process kill (T3)",
                                is_active: state.delete_access_allowed,
                                is_top: false,
                                is_bottom: true,
                            },
                            &theme,
                            cx.listener(move |this, _, _, cx| {
                                this.state.delete_access_allowed =
                                    !this.state.delete_access_allowed;
                                cx.notify();
                                if let Some(ref tx) = tx_del {
                                    let _ = tx.send(Command::ToggleDeleteAccess);
                                }
                            }),
                        )),
                ),
        )
}

struct PermRowSpec {
    id: &'static str,
    icon_data: &'static [u8],
    title: &'static str,
    desc: &'static str,
    is_active: bool,
    is_top: bool,
    is_bottom: bool,
}

fn render_perm_row<F>(spec: PermRowSpec, theme: &FlyoutTheme, on_toggle: F) -> impl IntoElement
where
    F: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    let PermRowSpec {
        id,
        icon_data,
        title,
        desc,
        is_active,
        is_top,
        is_bottom,
    } = spec;
    div()
        .id(id)
        .flex()
        .flex_row()
        .items_center()
        .justify_between()
        .gap(px(8.0))
        .w_full()
        .px(px(10.0))
        .py(px(8.0))
        .when(is_top, |s| s.rounded_t(px(11.0)))
        .when(is_bottom, |s| s.rounded_b(px(11.0)))
        .when(!is_bottom, |s| {
            s.border_b_1().border_color(theme.card_border)
        })
        .cursor_pointer()
        .hover(|s| s.bg(rgba(0x00000006)))
        .on_click(on_toggle)
        .child(
            div()
                .flex()
                .flex_row()
                .flex_1()
                .min_w_0()
                .items_center()
                .gap(px(9.0))
                .child(svg_icon(
                    icon_data,
                    14.0,
                    if is_active {
                        theme.accent_blue
                    } else {
                        theme.text_secondary
                    },
                ))
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .flex_1()
                        .min_w_0()
                        .child(
                            div()
                                .text_color(theme.text_primary)
                                .font_weight(FontWeight::MEDIUM)
                                .text_size(px(11.0))
                                .child(title),
                        )
                        .child(
                            div()
                                .text_color(theme.text_secondary)
                                .text_size(px(9.0))
                                .child(desc),
                        ),
                ),
        )
        // Apple-Style Pill Toggle Switch
        .child(
            div()
                .flex()
                .flex_shrink_0()
                .items_center()
                .w(px(32.0))
                .h(px(18.0))
                .p(px(2.0))
                .rounded_full()
                .bg(if is_active {
                    theme.toggle_active_bg
                } else {
                    theme.toggle_inactive_bg
                })
                .border_1()
                .border_color(if is_active {
                    theme.toggle_active_border
                } else {
                    theme.toggle_inactive_border
                })
                .child(
                    div()
                        .w(px(12.0))
                        .h(px(12.0))
                        .rounded_full()
                        .bg(rgba(0xffffffff))
                        .shadow_sm()
                        .when(is_active, |s| s.ml(px(14.0))),
                ),
        )
}
