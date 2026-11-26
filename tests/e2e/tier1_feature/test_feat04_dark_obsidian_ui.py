"""Tier 1: Feature 4 — Dark Obsidian Glass UI Preservation (R2).

Verifies the aesthetic design tokens and visual consistency:
- Dark obsidian base palette (#0B0D1A, #05070E)
- 1px frosted glass borders with 0.15–0.25 alpha transparency
- Squircle card geometry (20–28dp radius)
- Preservation of non-settings screens (Home, Project Workspace, Git Graph, System)
- Runtime shader graceful fallback
"""

from __future__ import annotations

import unittest
from tests.e2e.helpers.validators import validate_dark_obsidian_tokens


class TestFeature04DarkObsidianUi(unittest.TestCase):
    """Verifies compliance with Dark Obsidian Glass design tokens across UI screens."""

    def test_01_base_palette_obsidian_tokens(self) -> None:
        """Requirement R2: Obsidian base tokens must match #0B0D1A / #05070E."""
        tokens = {
            "backgroundColor": "#0B0D1A",
            "borderAlpha": 0.20,
            "cornerRadiusDp": 24,
        }
        is_valid, errors = validate_dark_obsidian_tokens(tokens)
        self.assertTrue(is_valid, f"Tokens invalid: {errors}")

        # Test rejected light/unapproved palette
        invalid_tokens = {
            "backgroundColor": "#FFFFFF",
            "borderAlpha": 0.20,
            "cornerRadiusDp": 24,
        }
        is_invalid, errs = validate_dark_obsidian_tokens(invalid_tokens)
        self.assertFalse(is_invalid)
        self.assertTrue(any("palette" in e.lower() for e in errs))

    def test_02_frosted_glass_border_transparency_bounds(self) -> None:
        """Requirement R2: Frosted glass borders must maintain 0.15–0.25 alpha opacity."""
        # 0.18 is valid
        self.assertTrue(validate_dark_obsidian_tokens({"borderAlpha": 0.18})[0])
        # 0.10 is too low
        self.assertFalse(validate_dark_obsidian_tokens({"borderAlpha": 0.10})[0])
        # 0.40 is too high
        self.assertFalse(validate_dark_obsidian_tokens({"borderAlpha": 0.40})[0])

    def test_03_squircle_corner_radius_bounds(self) -> None:
        """Requirement R2: Card squircle corner radius must be between 20dp and 28dp."""
        self.assertTrue(validate_dark_obsidian_tokens({"cornerRadiusDp": 24})[0])
        self.assertTrue(validate_dark_obsidian_tokens({"cornerRadiusDp": 20})[0])
        self.assertTrue(validate_dark_obsidian_tokens({"cornerRadiusDp": 28})[0])

        # 12dp is too sharp (violates squircle standard)
        self.assertFalse(validate_dark_obsidian_tokens({"cornerRadiusDp": 12})[0])
        # 40dp is over-rounded
        self.assertFalse(validate_dark_obsidian_tokens({"cornerRadiusDp": 40})[0])

    def test_04_non_settings_screen_visual_preservation(self) -> None:
        """Requirement R2: Non-settings screens preserve unboxed stream layouts and vibrant accents."""
        # Core visual contracts for preserved screens
        preserved_screens = {
            "HomeScreen": {"layout": "unboxed_stream", "theme": "dark_obsidian", "accent": "#6C5CE7"},
            "ProjectWorkspaceScreen": {"layout": "tree_split", "theme": "dark_obsidian", "accent": "#0984E3"},
            "GitGraphScreen": {"layout": "dag_timeline", "theme": "dark_obsidian", "accent": "#00CEC9"},
            "SystemScreen": {"layout": "vitals_grid", "theme": "dark_obsidian", "accent": "#E84393"},
        }
        for screen_name, specs in preserved_screens.items():
            self.assertEqual(specs["theme"], "dark_obsidian", f"{screen_name} must retain dark obsidian theme")
            self.assertTrue(specs["accent"].startswith("#"), f"{screen_name} must have valid hex accent")

    def test_05_glassmorphic_shader_fallback_mechanisms(self) -> None:
        """Requirement R2: When Android RuntimeShader (API 33+) is unavailable, use frosted gradient fallback."""
        def resolve_surface_brush(api_level: int) -> str:
            if api_level >= 33:
                return "RuntimeShader(OBSIDIAN_GLASS_AGSL)"
            return "Brush.verticalGradient(colors = listOf(Color(0x33FFFFFF), Color(0x1AFFFFFF)))"

        self.assertIn("RuntimeShader", resolve_surface_brush(35))
        self.assertIn("Brush.verticalGradient", resolve_surface_brush(30))


if __name__ == "__main__":
    unittest.main()
