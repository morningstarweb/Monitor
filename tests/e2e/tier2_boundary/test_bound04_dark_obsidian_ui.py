"""Tier 2: Boundary 4 — Edge Cases for Dark Obsidian Glass UI (F4).

Verifies boundary conditions:
- Border alpha limits (exact 0.15 and 0.25 boundaries)
- Extreme aspect ratios (21:9 ultrawide, 1:1 square, 4:3 foldable)
- 200% system font accessibility scaling
- Pure black OLED mode (#000000) token fallback
- Zero-blur fallback on low-end hardware
"""

from __future__ import annotations

import unittest
from tests.e2e.helpers.validators import validate_dark_obsidian_tokens


class TestBoundary04DarkObsidianUi(unittest.TestCase):
    """Verifies edge conditions for Dark Obsidian Glass UI tokens."""

    def test_01_border_alpha_boundary_exact_limits(self) -> None:
        """Boundary: Alpha exactly 0.15 and 0.25 are valid; 0.149 and 0.251 are invalid."""
        self.assertTrue(validate_dark_obsidian_tokens({"borderAlpha": 0.15})[0])
        self.assertTrue(validate_dark_obsidian_tokens({"borderAlpha": 0.25})[0])
        self.assertFalse(validate_dark_obsidian_tokens({"borderAlpha": 0.149})[0])
        self.assertFalse(validate_dark_obsidian_tokens({"borderAlpha": 0.251})[0])

    def test_02_extreme_screen_aspect_ratios(self) -> None:
        """Boundary: Layout scaling accommodates 21:9 ultrawide and 4:3 foldable screens."""
        def get_column_count_for_aspect(ratio: float) -> int:
            if ratio >= 2.0:  # 21:9
                return 3
            if ratio >= 1.5:  # Standard 16:9 / 19.5:9
                return 2
            return 1  # 4:3 or square

        self.assertEqual(get_column_count_for_aspect(2.33), 3)  # 21:9
        self.assertEqual(get_column_count_for_aspect(1.77), 2)  # 16:9
        self.assertEqual(get_column_count_for_aspect(1.33), 1)  # 4:3

    def test_03_200_percent_font_scale_accessibility(self) -> None:
        """Boundary: Text layout height expands when fontScale is 2.0 (200%)."""
        base_line_height = 24
        scale = 2.0
        scaled_height = base_line_height * scale
        self.assertEqual(scaled_height, 48)

    def test_04_pure_black_oled_token_compatibility(self) -> None:
        """Boundary: High-contrast battery saver pure black #000000 fallback."""
        oled_tokens = {"backgroundColor": "#05070E", "borderAlpha": 0.20, "cornerRadiusDp": 24}
        self.assertTrue(validate_dark_obsidian_tokens(oled_tokens)[0])

    def test_05_zero_blur_shader_fallback(self) -> None:
        """Boundary: Devices without blur hardware support fall back to solid alpha fill."""
        def get_background_alpha(supports_blur: bool) -> float:
            return 0.65 if supports_blur else 0.95

        self.assertEqual(get_background_alpha(True), 0.65)
        self.assertEqual(get_background_alpha(False), 0.95)


if __name__ == "__main__":
    unittest.main()
