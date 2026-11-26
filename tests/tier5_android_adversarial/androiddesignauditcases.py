from __future__ import annotations

from .support import ANDROID_ROOT

class AndroidDesignAuditCases:
    """Exhaustive source code audit of the entire Android client codebase."""

    def test_zero_mock_files_and_symbols_in_android_main(self) -> None:
        """Adversarial: Asserts zero mock classes, files, or bypass tokens exist in src/main."""
        forbidden_terms = [
            "HomeMockSimulation",
            "GitWorkspaceMockSimulation",
            "generateMockVirtualChangedFiles",
            "dev-bypass-mock-token",
            "mockSimulation",
            "MockTelemetryProvider",
        ]

        main_kt_files = list(ANDROID_ROOT.glob("**/src/main/**/*.kt"))
        self.assertTrue(len(main_kt_files) >= 40, f"Expected >= 40 Kotlin files, found {len(main_kt_files)}")

        violations = []
        for file_path in main_kt_files:
            try:
                content = file_path.read_text(encoding="utf-8")
            except Exception as e:
                violations.append(f"Cannot read {file_path}: {e}")
                continue

            for term in forbidden_terms:
                if term in content:
                    violations.append(f"Found forbidden mock term '{term}' in {file_path}")

        self.assertEqual(violations, [], f"Mock violations found in production Android source: {violations}")

    def test_dark_obsidian_design_tokens_consistency(self) -> None:
        """Adversarial: Validate Dark Obsidian color tokens and glass transparency."""
        color_file = ANDROID_ROOT / "core" / "designsystem" / "src" / "main" / "java" / "app" / "monitor" / "core" / "designsystem" / "theme" / "Color.kt"
        self.assertTrue(color_file.exists(), f"Color.kt not found at {color_file}")

        content = color_file.read_text(encoding="utf-8")

        # Must define Dark Obsidian base colors
        self.assertIn("0xFF060A08", content)
        self.assertIn("0xFF0D1611", content)
        self.assertIn("0xFF00FF87", content) # Neon Emerald
        self.assertIn("0xFF00E5FF", content) # Cyan accent
        self.assertIn("DarkMonitorColors", content)
        self.assertIn("LightMonitorColors", content)

    def test_empty_state_strings_are_authentic_and_informative(self) -> None:
        """Adversarial: Empty states in Home, Git, and System screens provide informative messages."""
        home_screen = ANDROID_ROOT / "feature" / "dashboard" / "src" / "main" / "java" / "app" / "monitor" / "feature" / "dashboard" / "HomeScreen.kt"
        git_screen = ANDROID_ROOT / "feature" / "gitworkspace" / "src" / "main" / "java" / "app" / "monitor" / "feature" / "gitworkspace" / "ProjectWorkspaceScreen.kt"
        system_screen = ANDROID_ROOT / "feature" / "dashboard" / "src" / "main" / "java" / "app" / "monitor" / "feature" / "dashboard" / "sections" / "SystemProcessesTab.kt"

        self.assertIn("No projects synced yet", home_screen.read_text(encoding="utf-8"))
        git_screen_content = git_screen.read_text(encoding="utf-8") + (git_screen.parent / "components" / "WorkspaceTreeCards.kt").read_text(encoding="utf-8")
        self.assertIn("Working tree clean. No uncommitted changes.", git_screen_content)
        self.assertIn("No active instances reported", system_screen.read_text(encoding="utf-8"))
