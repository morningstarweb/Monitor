use gpui_kit::*;

/// Visual Styling Theme matching the Monitor Installer Specification (100% Pure Deep Dark)
#[derive(Clone, Copy, Debug)]
pub struct InstallerTheme {
    // Window Frame & Backgrounds
    pub window_bg: Rgba,
    pub titlebar_bg: Rgba,
    pub titlebar_border: Rgba,
    pub sidebar_bg: Rgba,
    pub content_bg: Rgba,

    // Cards & Panels
    pub card_bg: Rgba,
    pub card_border: Rgba,
    pub input_bg: Rgba,
    pub input_border: Rgba,

    // Typography
    pub text_primary: Rgba,
    pub text_secondary: Rgba,
    pub text_muted: Rgba,

    // Vibrant Brand Accents
    pub accent_purple: Rgba,
    pub accent_purple_light: Rgba,
    pub accent_cyan: Rgba,

    // Semantic Status Colors
    pub success_fg: Rgba,
    pub success_bg: Rgba,
    pub success_border: Rgba,
    pub warning_fg: Rgba,
    pub warning_bg: Rgba,
    pub warning_border: Rgba,
    pub danger_fg: Rgba,
    pub danger_bg: Rgba,
    pub danger_border: Rgba,

    // Stepper Indicators
    pub step_track: Rgba,
    pub step_completed: Rgba,
    pub step_pending: Rgba,

    // Progress Bar
    pub progress_track: Rgba,

    // Buttons
    pub btn_primary_bg: Rgba,
    pub btn_primary_hover: Rgba,
    pub btn_primary_text: Rgba,
    pub btn_secondary_bg: Rgba,
    pub btn_secondary_border: Rgba,
    pub btn_secondary_hover: Rgba,
    pub btn_secondary_text: Rgba,
}

impl Default for InstallerTheme {
    fn default() -> Self {
        Self {
            window_bg: rgba(0x0b0f19ff),
            titlebar_bg: rgba(0x0e1322ff),
            titlebar_border: rgba(0x1e293bcc),
            sidebar_bg: rgba(0x0b0f19ff),
            content_bg: rgba(0x0d1222ff),

            card_bg: rgba(0x131a2eff),
            card_border: rgba(0x232f4ecc),
            input_bg: rgba(0x101728ff),
            input_border: rgba(0x283659cc),

            text_primary: rgba(0xffffffff),
            text_secondary: rgba(0x94a3b8ee),
            text_muted: rgba(0x64748bcc),

            accent_purple: rgba(0x8b5cf6ff),
            accent_purple_light: rgba(0xa855f724),
            accent_cyan: rgba(0x38bdf8ff),

            success_fg: rgba(0x4ade80ff),
            success_bg: rgba(0x22c55e1c),
            success_border: rgba(0x22c55e55),

            warning_fg: rgba(0xfbbf24ff),
            warning_bg: rgba(0xf59e0b1a),
            warning_border: rgba(0xf59e0b55),

            danger_fg: rgba(0xf87171ff),
            danger_bg: rgba(0xef44441c),
            danger_border: rgba(0xef444466),

            step_track: rgba(0x334155aa),
            step_completed: rgba(0x8b5cf6ff),
            step_pending: rgba(0x1e293bcc),

            progress_track: rgba(0x1e293bcc),

            btn_primary_bg: rgba(0x8b5cf6ff),
            btn_primary_hover: rgba(0x7c3aedff),
            btn_primary_text: rgba(0xffffffff),

            btn_secondary_bg: rgba(0x1a233cbb),
            btn_secondary_border: rgba(0x334155cc),
            btn_secondary_hover: rgba(0x253154ff),
            btn_secondary_text: rgba(0xffffffff),
        }
    }
}
