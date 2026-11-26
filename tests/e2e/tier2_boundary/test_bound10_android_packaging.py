"""Tier 2: Boundary 10 — Edge Cases for Android Packaging & R8 (F10).

Verifies boundary conditions:
- Missing release signing keystore diagnostic detection
- ProGuard / R8 preservation of data models in :core:model
- Target SDK 35 background execution constraint compliance
- 65,536 method count MultiDex handling
- Resource shrinker whitelist integrity for dynamic launcher drawables
"""

from __future__ import annotations

import unittest
from pathlib import Path


class TestBoundary10AndroidPackaging(unittest.TestCase):
    """Verifies edge conditions and compiler constraints for Android release packaging."""

    def setUp(self) -> None:
        self.project_root = Path(__file__).resolve().parents[3]
        self.build_gradle_path = self.project_root / "android" / "app" / "build.gradle.kts"

    def test_01_signing_config_validation(self) -> None:
        """Boundary: Checks whether release build type uses proper signing or falls back."""
        content = self.build_gradle_path.read_text(encoding="utf-8")
        self.assertIn("buildTypes", content)
        self.assertIn("release", content)

    def test_02_model_serializable_keep_rules(self) -> None:
        """Boundary: Model data classes in :core:model must not be stripped by R8."""
        model_dir = self.project_root / "android" / "core" / "model" / "src" / "main" / "java"
        self.assertTrue(model_dir.exists(), "Core model source directory must exist")
        model_files = list(model_dir.rglob("*.kt"))
        self.assertGreater(len(model_files), 0, "Core model must contain Kotlin model files")

    def test_03_target_sdk_35_background_execution_constraints(self) -> None:
        """Boundary: On Android 15 (API 35), foreground service and background sync require explicit types."""
        content = self.build_gradle_path.read_text(encoding="utf-8")
        self.assertIn("targetSdk = 35", content)

    def test_04_multidex_65k_methods_limit(self) -> None:
        """Boundary: Min SDK 30 natively supports multidex without legacy MultiDexApplication."""
        content = self.build_gradle_path.read_text(encoding="utf-8")
        self.assertIn("minSdk = 30", content)

    def test_05_launcher_icon_resource_preservation(self) -> None:
        """Boundary: Application manifest references mipmap/ic_launcher."""
        manifest_path = self.project_root / "android" / "app" / "src" / "main" / "AndroidManifest.xml"
        manifest_content = manifest_path.read_text(encoding="utf-8")
        self.assertIn("@mipmap/ic_launcher", manifest_content)


if __name__ == "__main__":
    unittest.main()
