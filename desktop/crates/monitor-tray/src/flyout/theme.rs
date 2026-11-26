use gpui_kit::*;

/// Complete visual styling palette adapting between Authentic Apple Glass and Dark Glass
#[derive(Clone, Copy, Debug)]
pub struct FlyoutTheme {
    pub is_dark: bool,
    // Outer Flyout Container
    pub glass_bg: Rgba,
    pub glass_border: Rgba,

    // Cards & Panels
    pub card_bg: Rgba,
    pub card_border: Rgba,

    // Tiles (1x4 Action Grid)
    pub tile_bg: Rgba,
    pub tile_border: Rgba,
    pub tile_hover: Rgba,
    pub tile_badge_bg: Rgba,
    pub tile_badge_text: Rgba,

    // Typography
    pub text_primary: Rgba,
    pub text_secondary: Rgba,

    // Accents
    pub accent_blue: Rgba,

    // Icons
    pub icon_primary: Rgba,
    pub icon_pulse: Rgba,

    // Apple/Windows Toggle Switch
    pub toggle_active_bg: Rgba,
    pub toggle_active_border: Rgba,
    pub toggle_inactive_bg: Rgba,
    pub toggle_inactive_border: Rgba,

    // Status Pill
    pub pill_running_bg: Rgba,
    pub pill_running_border: Rgba,
    pub pill_running_text: Rgba,

    // Quit Button
    pub quit_bg: Rgba,
    pub quit_border: Rgba,
    pub quit_text: Rgba,
    pub quit_hover: Rgba,
}

impl FlyoutTheme {
    pub fn for_mode(is_dark: bool) -> Self {
        if is_dark {
            Self {
                is_dark: true,
                glass_bg: rgba(0x0e1122d4),
                glass_border: rgba(0x38457466),

                // Solid elevated dark surfaces (no washed-out transparency)
                card_bg: rgba(0x1a2038ff),
                card_border: rgba(0x2d3758cc),

                tile_bg: rgba(0x1a2038ff),
                tile_border: rgba(0x2d3758cc),
                tile_hover: rgba(0x252e50ff),
                tile_badge_bg: rgba(0x8b5cf6ee),
                tile_badge_text: rgba(0xffffffff),

                text_primary: rgba(0xffffffff),
                text_secondary: rgba(0x94a3b8dd),
                accent_blue: rgba(0x38bdf8ff),

                icon_primary: rgba(0xffffffff),
                icon_pulse: rgba(0xa855f7ff),

                toggle_active_bg: rgba(0x8b5cf6ff),
                toggle_active_border: rgba(0xa78bfaaa),
                toggle_inactive_bg: rgba(0x334155aa),
                toggle_inactive_border: rgba(0x47556988),

                pill_running_bg: rgba(0x22c55e20),
                pill_running_border: rgba(0x22c55e55),
                pill_running_text: rgba(0x4ade80ff),

                quit_bg: rgba(0x2a1420ff),
                quit_border: rgba(0xef444455),
                quit_text: rgba(0xf87171ff),
                quit_hover: rgba(0x38192aff),
            }
        } else {
            // Luminous Frosted Whitish Background with Pure White Buttons & Cards
            Self {
                is_dark: false,
                glass_bg: rgba(0xf1f5f9f2),
                glass_border: rgba(0x00000012),

                // Pure White Solid Buttons & Cards
                card_bg: rgba(0xffffffff),
                card_border: rgba(0xe2e8f0dd),

                tile_bg: rgba(0xffffffff),
                tile_border: rgba(0xe2e8f0dd),
                tile_hover: rgba(0xf1f5f9ff),
                tile_badge_bg: rgba(0xbae6fdff),
                tile_badge_text: rgba(0x0284c7ff),

                // Deep Solid Black Typography (User requested pure black in white mode)
                text_primary: rgba(0x000000ff),
                text_secondary: rgba(0x18181bee),
                accent_blue: rgba(0x0284c7ff),

                // Deep Solid Black Icons
                icon_primary: rgba(0x000000ff),
                icon_pulse: rgba(0x000000ff),

                toggle_active_bg: rgba(0x0284c7ff),
                toggle_active_border: rgba(0x38bdf8aa),
                toggle_inactive_bg: rgba(0xcbd5e1aa),
                toggle_inactive_border: rgba(0x94a3b888),

                pill_running_bg: rgba(0xdcfce7dd),
                pill_running_border: rgba(0x86efac88),
                pill_running_text: rgba(0x15803dff),

                quit_bg: rgba(0xfff1f2ff),
                quit_border: rgba(0xfecacaee),
                quit_text: rgba(0xdc2626ff),
                quit_hover: rgba(0xfee2e2ff),
            }
        }
    }
}
