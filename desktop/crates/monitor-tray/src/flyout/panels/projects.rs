use super::super::icons::{ICON_EXTERNAL_LINK, ICON_FOLDER, ICON_PLUS, ICON_TRASH, svg_icon};
use super::super::state::FlyoutState;
use super::super::theme::FlyoutTheme;
use super::header::render_panel_header;
use crate::client::Command;
use gpui_kit::*;
use std::sync::mpsc::Sender;

pub fn render_projects_panel<V: 'static, FBack>(
    state: &FlyoutState,
    command_sender: &Option<Sender<Command>>,
    cx: &mut Context<V>,
    on_back: FBack,
) -> impl IntoElement
where
    FBack: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    let theme = FlyoutTheme::for_mode(state.is_dark_mode);
    let tx_add = command_sender.clone();
    let btn_add_bg = if theme.is_dark {
        rgba(0x38bdf818)
    } else {
        rgba(0x0284c712)
    };
    let btn_add_border = if theme.is_dark {
        rgba(0x38bdf844)
    } else {
        rgba(0x0284c730)
    };
    let btn_add_text = if theme.is_dark {
        rgba(0x38bdf8ff)
    } else {
        rgba(0x0284c7ff)
    };

    div()
        .flex()
        .flex_col()
        .gap(px(8.0))
        .w_full()
        .h_full()
        // Header
        .child(render_panel_header("Git Projects", state.is_dark_mode, on_back, None))
        // Scrollable Body
        .child(
            div()
                .id("projects-scroll-container")
                .flex()
                .flex_col()
                .gap(px(8.0))
                .w_full()
                .flex_1()
                .overflow_y_scroll()
                // 1. Add Project Button
                .child(
                    div()
                        .id("btn-add-project")
                        .flex()
                        .flex_row()
                        .items_center()
                        .justify_center()
                        .gap(px(6.0))
                        .w_full()
                        .h(px(32.0))
                        .rounded(px(10.0))
                        .bg(btn_add_bg)
                        .border_1()
                        .border_color(btn_add_border)
                        .cursor_pointer()
                        .hover(move |s| s.bg(btn_add_border))
                        .on_click(cx.listener(move |_, _, _, _| {
                            if let Some(ref tx) = tx_add {
                                let tx_clone = tx.clone();
                                std::thread::Builder::new()
                                    .name("monitor-folder-picker".to_string())
                                    .spawn(move || {
                                        if let Some(folder) = rfd::FileDialog::new()
                                            .set_title("Choose a Git project or folder to monitor")
                                            .pick_folder()
                                        {
                                            let _ = tx_clone.send(Command::RegisterPath(folder));
                                        }
                                    })
                                    .ok();
                            }
                        }))
                        .child(svg_icon(ICON_PLUS, 12.0, btn_add_text))
                        .child(
                            div()
                                .text_color(btn_add_text)
                                .font_weight(FontWeight::MEDIUM)
                                .text_size(px(11.0))
                                .child("Add Project Folder…")
                        )
                )
                // 2. Projects List
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .gap(px(4.0))
                        .w_full()
                        .child(
                            div()
                                .text_color(theme.text_secondary)
                                .text_size(px(10.0))
                                .px(px(2.0))
                                .child(format!("MONITORED PROJECTS ({})", state.projects.len()))
                        )
                        .child(
                            if state.projects.is_empty() {
                                div()
                                    .p(px(10.0))
                                    .rounded(px(10.0))
                                    .bg(theme.card_bg)
                                    .border_1()
                                    .border_color(theme.card_border)
                                    .child(
                                        div()
                                            .text_color(theme.text_secondary)
                                            .text_size(px(10.5))
                                            .child("No Git projects registered yet. Click 'Add Project Folder' above to add a local repo.")
                                    )
                                    .into_any_element()
                            } else {
                                div()
                                    .flex()
                                    .flex_col()
                                    .gap(px(5.0))
                                    .children(state.projects.iter().enumerate().map(|(idx, proj)| {
                                        let proj_id = proj.repo_id.clone();
                                        let tx_remove = command_sender.clone();
                                        let branch = proj.current_branch.clone();
                                        let status_label = if proj.is_clean {
                                            "Clean".to_string()
                                        } else {
                                            format!("{}+ modified", proj.modified_count)
                                        };
                                        div()
                                            .flex()
                                            .flex_row()
                                            .items_center()
                                            .justify_between()
                                            .px(px(10.0))
                                            .py(px(7.0))
                                            .rounded(px(10.0))
                                            .bg(theme.card_bg)
                                            .border_1()
                                            .border_color(theme.card_border)
                                            .hover(|s| s.bg(theme.tile_hover))
                                            .child(
                                                div()
                                                    .flex()
                                                    .flex_row()
                                                    .items_center()
                                                    .gap(px(8.0))
                                                    .child(svg_icon(ICON_FOLDER, 14.0, rgba(0xfacc15ee)))
                                                    .child(
                                                        div()
                                                            .flex()
                                                            .flex_col()
                                                            .child(
                                                                div()
                                                                    .text_color(theme.text_primary)
                                                                    .font_weight(FontWeight::MEDIUM)
                                                                    .text_size(px(11.0))
                                                                    .child(proj.display_name.clone())
                                                            )
                                                            .child(
                                                                div()
                                                                    .flex()
                                                                    .flex_row()
                                                                    .items_center()
                                                                    .gap(px(6.0))
                                                                    .child(
                                                                        div()
                                                                            .text_color(theme.text_secondary)
                                                                            .text_size(px(9.0))
                                                                            .child(format!("Branch: {branch}"))
                                                                    )
                                                                    .child(
                                                                        div()
                                                                            .text_color(if proj.is_clean { rgba(0x22c55ecc) } else { rgba(0xf59e0bcc) })
                                                                            .text_size(px(9.0))
                                                                            .child(format!("({status_label})"))
                                                                    )
                                                            )
                                                    )
                                            )
                                            // Action Buttons
                                            .child(
                                                div()
                                                    .flex()
                                                    .flex_row()
                                                    .items_center()
                                                    .gap(px(4.0))
                                                    // 1. Open in Explorer Button
                                                    .child({
                                                        let path_clone = proj.path.clone();
                                                        div()
                                                            .id(ElementId::named_usize("btn-explore", idx))
                                                            .flex()
                                                            .items_center()
                                                            .justify_center()
                                                            .w(px(22.0))
                                                            .h(px(22.0))
                                                            .rounded(px(6.0))
                                                            .bg(theme.card_bg)
                                                            .border_1()
                                                            .border_color(theme.card_border)
                                                            .cursor_pointer()
                                                            .hover(|s| s.bg(theme.tile_hover))
                                                            .on_click(move |_, _, _| {
                                                                let _ = std::process::Command::new("explorer").arg(&path_clone).spawn();
                                                            })
                                                            .child(svg_icon(ICON_EXTERNAL_LINK, 10.0, theme.text_secondary))
                                                    })
                                                    // 2. Remove Project Button
                                                    .child(
                                                        div()
                                                            .id(ElementId::named_usize("btn-remove", idx))
                                                            .flex()
                                                            .items_center()
                                                            .justify_center()
                                                            .w(px(22.0))
                                                            .h(px(22.0))
                                                            .rounded(px(6.0))
                                                            .bg(theme.quit_bg)
                                                            .border_1()
                                                            .border_color(theme.quit_border)
                                                            .cursor_pointer()
                                                            .hover(|s| s.bg(theme.quit_hover))
                                                            .on_click(cx.listener(move |_, _, _, _| {
                                                                if let Some(ref tx) = tx_remove {
                                                                    let _ = tx.send(Command::RemoveProject(proj_id.to_string()));
                                                                }
                                                            }))
                                                            .child(svg_icon(ICON_TRASH, 10.0, theme.quit_text))
                                                    )
                                            )
                                    }))
                                    .into_any_element()
                            }
                        )
                )
        )
}
