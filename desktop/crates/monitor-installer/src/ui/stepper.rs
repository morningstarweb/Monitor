use crate::icons::{ICON_CHECK, svg_icon};
use crate::state::InstallerStep;
use crate::theme::InstallerTheme;
use gpui_kit::{prelude::FluentBuilder, *};

use std::rc::Rc;

const STEP_LABELS: [&str; 6] = [
    "Welcome", "License", "Location", "Options", "Install", "Finish",
];

pub fn render_stepper<F>(
    current_step: InstallerStep,
    highest_step: InstallerStep,
    locked: bool,
    theme: &InstallerTheme,
    on_select_step: F,
) -> impl IntoElement
where
    F: Fn(&InstallerStep, &mut Window, &mut App) + 'static,
{
    let current_idx = current_step.index();
    let total = InstallerStep::total_steps();
    let on_select_step = Rc::new(on_select_step);

    div()
        .flex()
        .flex_row()
        .items_center()
        .w_full()
        .py(px(4.0))
        .children((0..total).map(|idx| {
            let is_completed = idx < current_idx;
            let is_active = idx == current_idx;
            let is_last = idx == total - 1;
            let target_step = match idx {
                0 => InstallerStep::Welcome,
                1 => InstallerStep::License,
                2 => InstallerStep::Location,
                3 => InstallerStep::Options,
                4 => InstallerStep::Installing,
                _ => InstallerStep::Finished,
            };
            let clickable = !locked
                && target_step <= highest_step
                && target_step <= InstallerStep::Options
                && target_step != current_step;

            let on_click_step = Rc::clone(&on_select_step);
            let label = STEP_LABELS[idx];

            div()
                .flex()
                .flex_row()
                .items_center()
                .when(!is_last, gpui_kit::Styled::flex_1)
                .child(
                    div()
                        .id(("stepper-node", idx))
                        .flex()
                        .flex_row()
                        .items_center()
                        .gap(px(5.0))
                        .when(clickable, |el| {
                            el.cursor_pointer().on_click(move |_, window, cx| {
                                on_click_step(&target_step, window, cx)
                            })
                        })
                        .child(
                            div()
                                .flex()
                                .items_center()
                                .justify_center()
                                .w(px(16.0))
                                .h(px(16.0))
                                .rounded_full()
                                .bg(if is_completed || is_active {
                                    theme.step_completed
                                } else {
                                    theme.step_pending
                                })
                                .border_1()
                                .border_color(if is_completed || is_active {
                                    theme.step_completed
                                } else {
                                    theme.step_track
                                })
                                .child(if is_completed {
                                    div()
                                        .child(svg_icon(ICON_CHECK, 8.5, rgba(0xffffffff)))
                                        .into_any_element()
                                } else if is_active {
                                    div()
                                        .w(px(5.5))
                                        .h(px(5.5))
                                        .rounded_full()
                                        .bg(rgba(0xffffffff))
                                        .into_any_element()
                                } else {
                                    div().into_any_element()
                                }),
                        )
                        .when(is_active, |el| {
                            el.child(
                                div()
                                    .text_color(theme.text_primary)
                                    .font_weight(FontWeight::SEMIBOLD)
                                    .text_size(px(10.5))
                                    .child(label),
                            )
                        }),
                )
                .when(!is_last, |s| {
                    s.child(div().flex_1().h(px(2.0)).mx(px(6.0)).bg(if is_completed {
                        theme.step_completed
                    } else {
                        theme.step_track
                    }))
                })
        }))
}
