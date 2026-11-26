use crate::icons::{ICON_CHECK, svg_icon};
use crate::state::LegalDocument;
use crate::theme::InstallerTheme;
use gpui_kit::{component::scroll::ScrollableElement, prelude::FluentBuilder, *};

const FULL_EULA_TEXT: &str = include_str!("../../../../../installer/EULA.txt");
const FULL_PRIVACY_TEXT: &str = include_str!("../../../../../installer/PRIVACY.txt");

#[allow(clippy::too_many_arguments)]
pub fn render_license_page<FBack, FCancel, FNext, FToggleAgree, FTabEula, FTabPrivacy>(
    eula_agreed: bool,
    active_doc: LegalDocument,
    is_upgrade: bool,
    theme: &InstallerTheme,
    on_back: FBack,
    on_cancel: FCancel,
    on_next: FNext,
    on_toggle_agree: FToggleAgree,
    on_tab_eula: FTabEula,
    on_tab_privacy: FTabPrivacy,
) -> impl IntoElement
where
    FBack: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FCancel: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FNext: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FToggleAgree: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FTabEula: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FTabPrivacy: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    let doc_text = match active_doc {
        LegalDocument::Eula => FULL_EULA_TEXT,
        LegalDocument::Privacy => FULL_PRIVACY_TEXT,
    };
    let doc_lines: Vec<String> = doc_text.lines().map(ToOwned::to_owned).collect();

    div()
        .flex()
        .flex_col()
        .justify_between()
        .w_full()
        .h_full()
        // Top Section: Header, Document Tabs, Scrollable Card, and Clean Acceptance Checkbox
        .child(
            div()
                .flex()
                .flex_col()
                .w_full()
                .gap(px(9.0))
                .child(
                    div()
                        .flex()
                        .flex_row()
                        .items_end()
                        .justify_between()
                        .child(
                            div()
                                .flex()
                                .flex_col()
                                .gap(px(2.0))
                                .child(
                                    div()
                                        .text_color(theme.text_primary)
                                        .font_weight(FontWeight::BOLD)
                                        .text_size(px(22.0))
                                        .child("Review Terms & Privacy"),
                                )
                                .child(
                                    div()
                                        .text_color(theme.text_secondary)
                                        .text_size(px(11.5))
                                        .child("Source-available license (PolyForm Noncommercial 1.0.0) & zero-surveillance policy."),
                                ),
                        )
                        // Segmented document switcher
                        .child(
                            div()
                                .flex()
                                .flex_row()
                                .gap(px(4.0))
                                .p(px(3.0))
                                .rounded(px(7.0))
                                .bg(theme.input_bg)
                                .border_1()
                                .border_color(theme.card_border)
                                .child(doc_tab_btn(
                                    "tab-eula",
                                    "License (EULA)",
                                    active_doc == LegalDocument::Eula,
                                    theme,
                                    on_tab_eula,
                                ))
                                .child(doc_tab_btn(
                                    "tab-privacy",
                                    "Privacy",
                                    active_doc == LegalDocument::Privacy,
                                    theme,
                                    on_tab_privacy,
                                )),
                        ),
                )
                // Real Scrollable Legal Document Container (with interactive GPUI scrollbar)
                .child(
                    div()
                        .w_full()
                        .h(px(210.0))
                        .rounded(px(8.0))
                        .bg(theme.input_bg)
                        .border_1()
                        .border_color(theme.input_border)
                        .overflow_hidden()
                        .child(
                            div()
                                .id("license-scroll-area")
                                .w_full()
                                .h_full()
                                .overflow_y_scrollbar()
                                .child(
                                    div()
                                        .flex()
                                        .flex_col()
                                        .w_full()
                                        .p(px(12.0))
                                        .pr(px(16.0))
                                        .gap(px(3.0))
                                        .children(doc_lines.into_iter().enumerate().map(
                                            |(ix, line)| {
                                                let trimmed = line.trim();
                                                if trimmed.is_empty() {
                                                    div().id(("doc-gap", ix)).h(px(4.0)).into_any_element()
                                                } else if trimmed.starts_with("===")
                                                    || trimmed.starts_with("---")
                                                {
                                                    div()
                                                        .id(("doc-sep", ix))
                                                        .w_full()
                                                        .h(px(1.0))
                                                        .my(px(2.0))
                                                        .bg(theme.card_border)
                                                        .into_any_element()
                                                } else {
                                                    let is_heading = trimmed
                                                        .chars()
                                                        .next()
                                                        .is_some_and(|c| c.is_ascii_digit())
                                                        && trimmed.contains(". ");
                                                    div()
                                                        .id(("doc-line", ix))
                                                        .w_full()
                                                        .text_color(if is_heading {
                                                            theme.text_primary
                                                        } else {
                                                            theme.text_secondary
                                                        })
                                                        .font_weight(if is_heading {
                                                            FontWeight::BOLD
                                                        } else {
                                                            FontWeight::NORMAL
                                                        })
                                                        .text_size(px(10.5))
                                                        .whitespace_normal()
                                                        .line_height(px(15.0))
                                                        .child(line)
                                                        .into_any_element()
                                                }
                                            },
                                        )),
                                ),
                        ),
                )
                // Single crisp acceptance checkbox (replaces dual Agree/Disagree radio trap)
                .child(
                    div()
                        .id("checkbox-accept-license")
                        .flex()
                        .flex_row()
                        .items_center()
                        .gap(px(10.0))
                        .p(px(10.0))
                        .rounded(px(8.0))
                        .bg(theme.card_bg)
                        .border_1()
                        .border_color(if eula_agreed {
                            theme.accent_purple
                        } else {
                            theme.card_border
                        })
                        .cursor_pointer()
                        .hover(|s| s.bg(rgba(0xffffff08)))
                        .on_click(on_toggle_agree)
                        .child(
                            div()
                                .flex()
                                .items_center()
                                .justify_center()
                                .w(px(17.0))
                                .h(px(17.0))
                                .rounded(px(4.0))
                                .bg(if eula_agreed {
                                    theme.accent_purple
                                } else {
                                    rgba(0x00000000)
                                })
                                .border_1()
                                .border_color(if eula_agreed {
                                    theme.accent_purple
                                } else {
                                    theme.step_track
                                })
                                .when(eula_agreed, |el| {
                                    el.child(svg_icon(ICON_CHECK, 10.0, rgba(0xffffffff)))
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
                                        .text_size(px(12.0))
                                        .child("I accept the License Agreement and Privacy Notice"),
                                )
                                .child(
                                    div()
                                        .text_color(theme.text_secondary)
                                        .text_size(px(10.5))
                                        .child(if is_upgrade {
                                            "Previously accepted for Monitor. Click to toggle."
                                        } else {
                                            "Required to proceed with Monitor installation."
                                        }),
                                ),
                        ),
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
                .pt(px(10.0))
                .border_t_1()
                .border_color(theme.card_border)
                .child(
                    div()
                        .text_color(if eula_agreed {
                            theme.text_muted
                        } else {
                            theme.warning_fg
                        })
                        .text_size(px(10.5))
                        .child(if eula_agreed {
                            "Scroll inside box to read complete terms."
                        } else {
                            "Check the acceptance box above to continue."
                        }),
                )
                .child(
                    div()
                        .flex()
                        .flex_row()
                        .items_center()
                        .gap(px(10.0))
                        .child(action_btn("btn-back", "Back", theme, on_back))
                        .child(action_btn("btn-cancel", "Cancel", theme, on_cancel))
                        .child(next_btn(eula_agreed, theme, on_next)),
                ),
        )
}

fn doc_tab_btn(
    id: &'static str,
    label: &'static str,
    active: bool,
    theme: &InstallerTheme,
    on_click: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
) -> impl IntoElement {
    div()
        .id(id)
        .px(px(10.0))
        .py(px(4.0))
        .rounded(px(5.0))
        .bg(if active {
            theme.accent_purple
        } else {
            rgba(0x00000000)
        })
        .cursor_pointer()
        .hover(|s| if active { s } else { s.bg(rgba(0xffffff0c)) })
        .on_click(on_click)
        .child(
            div()
                .text_color(if active {
                    theme.btn_primary_text
                } else {
                    theme.text_secondary
                })
                .font_weight(if active {
                    FontWeight::SEMIBOLD
                } else {
                    FontWeight::NORMAL
                })
                .text_size(px(10.5))
                .child(label),
        )
}

fn action_btn(
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

fn next_btn(
    eula_agreed: bool,
    theme: &InstallerTheme,
    on_next: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
) -> impl IntoElement {
    let btn = div()
        .id("btn-next")
        .flex()
        .items_center()
        .justify_center()
        .px(px(22.0))
        .py(px(7.0))
        .rounded(px(7.0))
        .bg(if eula_agreed {
            theme.btn_primary_bg
        } else {
            theme.step_pending
        })
        .opacity(if eula_agreed { 1.0 } else { 0.45 });

    if eula_agreed {
        btn.cursor_pointer()
            .hover(|h| h.bg(theme.btn_primary_hover))
            .on_click(on_next)
            .child(
                div()
                    .text_color(theme.btn_primary_text)
                    .font_weight(FontWeight::BOLD)
                    .text_size(px(12.0))
                    .child("Next"),
            )
    } else {
        btn.child(
            div()
                .text_color(theme.text_muted)
                .font_weight(FontWeight::BOLD)
                .text_size(px(12.0))
                .child("Next"),
        )
    }
}
