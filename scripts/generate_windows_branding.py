#!/usr/bin/env python3
"""
scripts/generate_windows_branding.py

Generates high-precision Windows icon (.ico) and Inno Setup branding graphics
directly from the official Monitor app icon (assets/brand/monitor-app-icon.png).
Preserves 100% original geometry, brand colors, and anti-aliased subpixel rendering.
"""

import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

def main():
    root = Path(__file__).resolve().parent.parent
    app_icon_png = root / "assets" / "brand" / "monitor-app-icon.png"

    print(f"1. Loading official app icon from {app_icon_png}...")
    base_icon = Image.open(app_icon_png).convert("RGBA")

    # 2. Build multi-resolution true-color Windows .ICO
    sizes = [16, 24, 32, 48, 64, 128, 256]
    images = []
    for s in sizes:
        resized = base_icon.resize((s, s), Image.Resampling.LANCZOS)
        images.append(resized)

    ico_targets = [
        root / "desktop" / "crates" / "monitor-tray" / "src" / "monitor.ico",
        root / "Monitor_App_Package" / "monitor.ico",
        root / "installer" / "monitor.ico",
    ]

    print("2. Packaging 32-bit multi-resolution Windows .ico (16px to 256px)...")
    for target in ico_targets:
        target.parent.mkdir(parents=True, exist_ok=True)
        # Pillow save as ICO: largest image is base, remaining passed in append_images
        images[-1].save(
            str(target),
            format="ICO",
            sizes=[(s, s) for s in sizes],
            append_images=images[:-1]
        )
        print(f"   -> Wrote {target} ({target.stat().st_size} bytes)")

    # 3. Generate Inno Setup WizardSmallImageFile (64x64 or 55x55, 32-bit BMP)
    # The small image sits at the top right of inner wizard pages.
    wizard_small = base_icon.resize((55, 55), Image.Resampling.LANCZOS)
    wizard_small_path = root / "installer" / "wizard_small.bmp"
    # Convert to RGB with #151A2E background or white/system background
    # Modern Inno Setup header is default system window color (usually white/off-white in light, dark in dark).
    # Using transparent-compatible 32-bit BMP or clean white/obsidian background:
    small_bg = Image.new("RGBA", (55, 55), (0, 0, 0, 0))
    small_bg.paste(wizard_small, (0, 0), wizard_small)
    small_bg.save(str(wizard_small_path), format="BMP")
    print(f"3. Generated Inno Setup WizardSmallImageFile: {wizard_small_path}")

    # 4. Generate Inno Setup WizardImageFile (164x314 modern banner for Welcome/Finished pages)
    # We create a high-DPI 328x628 image (Inno Setup 6 automatically scales down) with:
    # - Deep dark obsidian glass background (#0B0D1A -> #13172E)
    # - Ambient fluid glow aura (#6366F1 / #00D2FF)
    # - Centered squircle app logo
    # - Clean hairline accent border
    banner_w, banner_h = 328, 628
    banner = Image.new("RGBA", (banner_w, banner_h), (11, 13, 26, 255))
    draw = ImageDraw.Draw(banner)

    # Gradient background
    for y in range(banner_h):
        t = y / float(banner_h)
        # #0B0D1A to #161B33 to #0B0D1A
        if t < 0.5:
            ratio = t * 2.0
            r = int(11 * (1 - ratio) + 22 * ratio)
            g = int(13 * (1 - ratio) + 27 * ratio)
            b = int(26 * (1 - ratio) + 51 * ratio)
        else:
            ratio = (t - 0.5) * 2.0
            r = int(22 * (1 - ratio) + 11 * ratio)
            g = int(27 * (1 - ratio) + 13 * ratio)
            b = int(51 * (1 - ratio) + 26 * ratio)
        draw.line([(0, y), (banner_w, y)], fill=(r, g, b, 255))

    # Ambient radial aura glow centered on the app logo
    aura = Image.new("RGBA", (banner_w, banner_h), (0, 0, 0, 0))
    aura_draw = ImageDraw.Draw(aura)
    cx, cy = banner_w // 2, banner_h // 2
    for rad in range(180, 0, -4):
        alpha = int((1.0 - (rad / 180.0)) * 75)
        aura_draw.ellipse([cx - rad, cy - rad, cx + rad, cy + rad], fill=(99, 102, 241, alpha))
    for rad in range(110, 0, -3):
        alpha = int((1.0 - (rad / 110.0)) * 95)
        aura_draw.ellipse([cx - rad, cy - rad, cx + rad, cy + rad], fill=(0, 210, 255, alpha))
    aura = aura.filter(ImageFilter.GaussianBlur(16))
    banner.alpha_composite(aura)

    # Place ONLY the official app icon (centered, prominent, clean)
    logo_size = 210
    scaled_logo = base_icon.resize((logo_size, logo_size), Image.Resampling.LANCZOS)
    banner.paste(scaled_logo, (cx - logo_size // 2, cy - logo_size // 2), scaled_logo)

    # Subtle hairline right border (Windows 11 glass boundary)
    draw_final = ImageDraw.Draw(banner)
    draw_final.line([(banner_w - 1, 0), (banner_w - 1, banner_h)], fill=(48, 56, 88, 255), width=1)

    wizard_large_path = root / "installer" / "wizard_large.bmp"
    # Convert banner to RGB 24-bit for Inno Setup compatibility
    banner.convert("RGB").save(str(wizard_large_path), format="BMP")
    print(f"4. Generated Inno Setup WizardImageFile: {wizard_large_path} ({banner_w}x{banner_h})")
    print("ALL BRANDING ASSETS GENERATED SUCCESSFULLY!")
    return 0

if __name__ == "__main__":
    sys.exit(main())
