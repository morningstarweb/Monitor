use gpui_kit::*;
use qrcode::{Color, QrCode};

/// Renders a scannable, high-contrast QR Code completely using native GPUI elements.
/// Zero raster/SVG overhead, 100% pure mathematical layout.
pub fn render_qr_code(payload: &str, module_size_px: f32) -> Option<AnyElement> {
    let qr = QrCode::new(payload.as_bytes()).ok()?;
    let width = qr.width();
    let colors = qr.to_colors();

    Some(
        div()
            .flex()
            .flex_col()
            .items_center()
            .justify_center()
            .p(px(8.0))
            .rounded(px(10.0))
            .bg(rgba(0xffffffff))
            .shadow_md()
            .child(div().flex().flex_col().children((0..width).map(|y| {
                div().flex().flex_row().children((0..width).map(|x| {
                    let is_dark = colors[y * width + x] == Color::Dark;
                    div()
                        .w(px(module_size_px))
                        .h(px(module_size_px))
                        .bg(if is_dark {
                            rgba(0x0f172aff)
                        } else {
                            rgba(0xffffffff)
                        })
                }))
            })))
            .into_any_element(),
    )
}
