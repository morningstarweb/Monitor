use gpui_kit::*;

/// Monitor Brand Vector Logo
pub const ICON_MONITOR_LOGO: &[u8] = br##"<svg viewBox="0 0 48 48" width="48" height="48" fill="none" xmlns="http://www.w3.org/2000/svg">
  <rect x="5" y="7" width="38" height="26" rx="4.5" stroke="url(#paint_grad)" stroke-width="2.6"/>
  <line x1="16" y1="41" x2="32" y2="41" stroke="url(#paint_grad)" stroke-width="2.6" stroke-linecap="round"/>
  <line x1="24" y1="33" x2="24" y2="41" stroke="url(#paint_grad)" stroke-width="2.6" stroke-linecap="round"/>
  <defs>
    <linearGradient id="paint_grad" x1="5" y1="7" x2="43" y2="41" gradientUnits="userSpaceOnUse">
      <stop offset="0%" stop-color="#a855f7"/>
      <stop offset="100%" stop-color="#38bdf8"/>
    </linearGradient>
  </defs>
</svg>"##;

/// Live System Insights (Bar Chart Icon)
pub const ICON_INSIGHTS: &[u8] = br##"<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
  <rect x="3" y="12" width="3.5" height="8" rx="1"/>
  <rect x="8.5" y="8" width="3.5" height="12" rx="1"/>
  <rect x="14" y="4" width="3.5" height="16" rx="1"/>
  <rect x="19.5" y="10" width="3.5" height="10" rx="1"/>
</svg>"##;

/// Git Workspace Control (Branch Fork Icon)
pub const ICON_GIT_BRANCH: &[u8] = br##"<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
  <line x1="6" y1="3" x2="6" y2="15"/>
  <circle cx="18" cy="6" r="3"/>
  <circle cx="6" cy="18" r="3"/>
  <path d="M18 9a9 9 0 0 1-9 9"/>
</svg>"##;

/// Secure Device Pairing (Shield Lock Icon)
pub const ICON_SECURITY: &[u8] = br##"<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
  <rect x="9.5" y="11" width="5" height="4" rx="1"/>
  <path d="M10.5 11V9.5a1.5 1.5 0 0 1 3 0V11"/>
</svg>"##;

/// Folder Location Icon
pub const ICON_FOLDER: &[u8] = br##"<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
  <path d="M4 20h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.93a2 2 0 0 1-1.66-.9l-.82-1.2A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13c0 1.1.9 2 2 2Z"/>
</svg>"##;

/// Checkmark Icon
pub const ICON_CHECK: &[u8] = br##"<svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="#ffffff" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
  <polyline points="20 6 9 17 4 12"/>
</svg>"##;

/// Progress Indicator Icon
pub const ICON_SPINNER: &[u8] = br##"<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
  <line x1="12" y1="2" x2="12" y2="6"/>
  <line x1="12" y1="18" x2="12" y2="22"/>
  <line x1="4.93" y1="4.93" x2="7.76" y2="7.76"/>
  <line x1="16.24" y1="16.24" x2="19.07" y2="19.07"/>
  <line x1="2" y1="12" x2="6" y2="12"/>
  <line x1="18" y1="12" x2="22" y2="12"/>
  <line x1="4.93" y1="19.07" x2="7.76" y2="16.24"/>
  <line x1="16.24" y1="7.76" x2="19.07" y2="4.93"/>
</svg>"##;

/// Window Control: Minimize
pub const ICON_MINIMIZE: &[u8] = br##"<svg viewBox="0 0 16 16" width="11" height="11" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><line x1="3" y1="9" x2="13" y2="9"/></svg>"##;

/// Window Control: Close
pub const ICON_CLOSE: &[u8] = br##"<svg viewBox="0 0 16 16" width="11" height="11" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><line x1="4" y1="4" x2="12" y2="12"/><line x1="12" y1="4" x2="4" y2="12"/></svg>"##;

/// Trash / Remove Icon for Uninstaller
pub const ICON_TRASH: &[u8] = br##"<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
  <polyline points="3 6 5 6 21 6"/>
  <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
  <line x1="10" y1="11" x2="10" y2="17"/>
  <line x1="14" y1="11" x2="14" y2="17"/>
</svg>"##;

/// Alert / Warning Triangle Icon
pub const ICON_WARNING: &[u8] = br##"<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
  <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
  <line x1="12" y1="9" x2="12" y2="13"/>
  <line x1="12" y1="17" x2="12.01" y2="17"/>
</svg>"##;

/// Lightning / Quick Upgrade Icon
pub const ICON_UPGRADE: &[u8] = br##"<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
  <path d="M12 19V5"/>
  <path d="m5 12 7-7 7 7"/>
</svg>"##;

pub fn svg_icon(data: &'static [u8], size_px: f32, color: Rgba) -> impl IntoElement {
    svg()
        .data(data)
        .w(px(size_px))
        .h(px(size_px))
        .text_color(color)
}
