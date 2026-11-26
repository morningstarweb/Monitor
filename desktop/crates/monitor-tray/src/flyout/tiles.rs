use super::icons::{ICON_CHECK, ICON_CLOUD, ICON_LAYERS, ICON_PULSE, ICON_SMARTPHONE, svg_icon};
use super::state::FlyoutState;
use super::theme::FlyoutTheme;
use gpui_kit::{prelude::FluentBuilder, *};

struct TileSpec {
    id: &'static str,
    icon_data: &'static [u8],
    icon_sz: f32,
    icon_color: Rgba,
    title: &'static str,
    line2: Option<String>,
}

pub fn render_tiles<FMobile, FCloud, FProjects, FSysmon>(
    state: &FlyoutState,
    on_mobile: FMobile,
    on_cloud: FCloud,
    on_projects: FProjects,
    on_sysmon: FSysmon,
) -> impl IntoElement
where
    FMobile: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FCloud: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FProjects: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    FSysmon: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    let theme = FlyoutTheme::for_mode(state.is_dark_mode);

    // 4 Action Cards in ONE Single Horizontal Row (100% matching reference design)
    div()
        .flex()
        .flex_row()
        .gap(px(6.0))
        .w_full()
        // 1. Mobile Devices Tile
        .child(render_single_tile(
            TileSpec {
                id: "tile-mobile",
                icon_data: ICON_SMARTPHONE,
                icon_sz: 19.0,
                icon_color: theme.icon_primary,
                title: "Mobile",
                line2: Some("Devices".to_string()),
            },
            Some(render_counter_badge(
                &format!("{}", state.mobile_devices_count),
                theme.tile_badge_bg,
                theme.tile_badge_text,
            )),
            &theme,
            on_mobile,
        ))
        // 2. Cloud Tile
        .child(render_single_tile(
            TileSpec {
                id: "tile-cloud",
                icon_data: ICON_CLOUD,
                icon_sz: 20.0,
                icon_color: theme.icon_primary,
                title: "Cloud",
                line2: None,
            },
            Some(render_check_badge(state.is_cloud_synced)),
            &theme,
            on_cloud,
        ))
        // 3. Projects Tile (Stacked Layers matching reference)
        .child(render_single_tile(
            TileSpec {
                id: "tile-projects",
                icon_data: ICON_LAYERS,
                icon_sz: 19.0,
                icon_color: theme.icon_primary,
                title: "Projects",
                line2: None,
            },
            Some(render_counter_badge(
                &format!("{}", state.projects_count),
                theme.tile_badge_bg,
                theme.tile_badge_text,
            )),
            &theme,
            on_projects,
        ))
        // 4. System Monitor Tile
        .child(render_single_tile(
            TileSpec {
                id: "tile-sysmon",
                icon_data: ICON_PULSE,
                icon_sz: 20.0,
                icon_color: theme.icon_pulse,
                title: "System",
                line2: Some("Monitor".to_string()),
            },
            None,
            &theme,
            on_sysmon,
        ))
}

fn render_single_tile<F>(
    spec: TileSpec,
    badge: Option<AnyElement>,
    theme: &FlyoutTheme,
    on_click: F,
) -> impl IntoElement
where
    F: Fn(&ClickEvent, &mut Window, &mut App) + 'static,
{
    let TileSpec {
        id,
        icon_data,
        icon_sz,
        icon_color,
        title,
        line2,
    } = spec;
    div()
        .id(id)
        .flex()
        .flex_col()
        .items_center()
        .justify_center()
        .flex_1()
        .h(px(64.0))
        .rounded(px(14.0))
        .bg(theme.tile_bg)
        .border_1()
        .border_color(theme.tile_border)
        .shadow_sm()
        .cursor_pointer()
        .hover(|s| s.bg(theme.tile_hover))
        .on_click(on_click)
        .child(
            div()
                .relative()
                .flex()
                .items_center()
                .justify_center()
                .child(svg_icon(icon_data, icon_sz, icon_color))
                .children(badge),
        )
        .child(
            div()
                .flex()
                .flex_col()
                .items_center()
                .mt(px(3.0))
                .child(
                    div()
                        .text_color(theme.text_primary)
                        .font_weight(FontWeight::MEDIUM)
                        .text_size(px(10.0))
                        .child(title),
                )
                .when_some(line2, |this, l2| {
                    this.child(
                        div()
                            .text_color(theme.text_secondary)
                            .text_size(px(9.0))
                            .child(l2),
                    )
                }),
        )
}

fn render_counter_badge(text: &str, bg_color: Rgba, text_color: Rgba) -> AnyElement {
    div()
        .absolute()
        .top(px(-6.0))
        .right(px(-10.0))
        .flex()
        .items_center()
        .justify_center()
        .min_w(px(14.0))
        .h(px(14.0))
        .px(px(3.5))
        .rounded_full()
        .bg(bg_color)
        .text_color(text_color)
        .font_weight(FontWeight::BOLD)
        .text_size(px(8.5))
        .child(text.to_string())
        .into_any_element()
}

fn render_check_badge(is_synced: bool) -> AnyElement {
    let bg = if is_synced {
        rgba(0x22c55eee)
    } else {
        rgba(0x64748bee)
    };
    div()
        .absolute()
        .top(px(-4.0))
        .right(px(-8.0))
        .flex()
        .items_center()
        .justify_center()
        .w(px(13.0))
        .h(px(13.0))
        .rounded_full()
        .bg(bg)
        .child(svg_icon(ICON_CHECK, 7.5, rgba(0xffffffff)))
        .into_any_element()
}
