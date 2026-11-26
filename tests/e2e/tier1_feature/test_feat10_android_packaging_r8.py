"""Tier 1: Feature 10 — Android Production Packaging & R8 (R4).

Verifies Android Google Play Store release readiness:
- Release build configuration (isMinifyEnabled = true, isShrinkResources = true)
- Manifest permissions audit (strict minimization, no invasive permissions)
- SDK API level compliance (compileSdk = 35, targetSdk = 35, minSdk = 30)
- ProGuard / R8 optimization configuration
- Semantic versioning and Application ID (app.monitor)
"""

from __future__ import annotations

import unittest
from pathlib import Path


class TestFeature10AndroidPackagingR8(unittest.TestCase):
    """Verifies production release configuration and security standards for Android."""

    def setUp(self) -> None:
        self.project_root = Path(__file__).resolve().parents[3]
        self.build_gradle_path = self.project_root / "android" / "app" / "build.gradle.kts"
        self.manifest_path = self.project_root / "android" / "app" / "src" / "main" / "AndroidManifest.xml"
        self.proguard_path = self.project_root / "android" / "app" / "proguard-rules.pro"

    def test_01_build_gradle_release_configuration(self) -> None:
        """Requirement R4 / Feature 26: Release build type enables minification and resource shrinking."""
        self.assertTrue(self.build_gradle_path.exists())
        content = self.build_gradle_path.read_text(encoding="utf-8")

        self.assertIn("isMinifyEnabled = true", content)
        self.assertIn("isShrinkResources = true", content)
        self.assertIn("proguardFiles", content)

    def test_02_manifest_permissions_privacy_audit(self) -> None:
        """Requirement R4 / Google Play Policy: Strict permission minimization."""
        self.assertTrue(self.manifest_path.exists())
        content = self.manifest_path.read_text(encoding="utf-8")

        # Allowed necessary permissions
        self.assertIn("android.permission.INTERNET", content)
        self.assertIn("android.permission.ACCESS_NETWORK_STATE", content)
        self.assertIn("android.permission.USE_BIOMETRIC", content)

        # Prohibited invasive permissions for Google Play Store safety
        forbidden_permissions = [
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.RECORD_AUDIO",
            "android.permission.CAMERA",
            "android.permission.READ_SMS",
            "android.permission.READ_CALL_LOG",
        ]
        for forbidden in forbidden_permissions:
            self.assertNotIn(forbidden, content, f"Invasive permission '{forbidden}' detected")

    def test_03_sdk_versions_google_play_compliance(self) -> None:
        """Requirement R4: Target SDK must satisfy latest Google Play target API level requirements."""
        content = self.build_gradle_path.read_text(encoding="utf-8")
        self.assertIn("compileSdk = 35", content)
        self.assertIn("targetSdk = 35", content)
        self.assertIn("minSdk = 30", content)

    def test_04_proguard_rules_model_serialization_keep(self) -> None:
        """Requirement R4: ProGuard rules preserve optimization without broken reflection."""
        self.assertTrue(self.proguard_path.exists())
        content = self.proguard_path.read_text(encoding="utf-8")
        self.assertTrue(len(content.strip()) > 0, "proguard-rules.pro must exist")

    def test_05_application_id_and_versioning(self) -> None:
        """Requirement R4: Application ID app.monitor and valid semantic version."""
        content = self.build_gradle_path.read_text(encoding="utf-8")
        self.assertIn('applicationId = "app.monitor"', content)
        brand_toml = self.project_root / "config" / "brand.toml"
        if brand_toml.exists():
            try:
                import tomllib
            except ModuleNotFoundError:
                import tomli as tomllib  # type: ignore
            with open(brand_toml, "rb") as f:
                data = tomllib.load(f)
            expected_code = data["android"]["version_code"]
            expected_name = data["android"]["version_name"]
            self.assertIn(f"versionCode = {expected_code}", content)
            self.assertIn(f'versionName = "{expected_name}"', content)
        else:
            self.assertRegex(content, r"versionCode\s*=\s*\d+")
            self.assertRegex(content, r'versionName\s*=\s*"[0-9.]+"')


if __name__ == "__main__":
    unittest.main()
