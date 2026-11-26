"""Tier 1: Feature 11 — Rust Desktop Installer Packaging (R4).

Verifies Desktop Agent production build and installer packaging:
- Cargo workspace members and compiler lints
- Inno Setup script (installer/monitor.iss) parameters and modern wizard UI
- LZMA2 ultra64 solid compression configuration
- Installer legal asset bundling (EULA.txt, PRIVACY.txt, icon)
- Packaging automation script (scripts/package_windows.ps1) and SHA256 integrity
"""

from __future__ import annotations

import unittest
from pathlib import Path


class TestFeature11DesktopInstallerPackaging(unittest.TestCase):
    """Verifies Windows desktop executable compilation and Inno Setup installer packaging."""

    def setUp(self) -> None:
        self.project_root = Path(__file__).resolve().parents[3]
        self.cargo_path = self.project_root / "desktop" / "Cargo.toml"
        self.iss_path = self.project_root / "installer" / "monitor.iss"
        self.package_script_path = self.project_root / "scripts" / "package_windows.ps1"

    def test_01_cargo_workspace_members_and_compiler_lints(self) -> None:
        """Requirement R4 / Feature 27: Cargo workspace defines all 7 crates with strict lints."""
        self.assertTrue(self.cargo_path.exists())
        content = self.cargo_path.read_text(encoding="utf-8")

        expected_crates = [
            "monitor-core",
            "monitor-storage",
            "monitor-telemetry",
            "monitor-git",
            "monitor-transport",
            "monitor-agent",
            "monitor-tray",
        ]
        for crate in expected_crates:
            self.assertIn(f'crates/{crate}', content)

        # Lints safety
        self.assertIn('unsafe_code = "deny"', content)
        self.assertIn('warnings = "deny"', content)

    def test_02_inno_setup_script_properties(self) -> None:
        """Requirement R4 / Feature 27: Inno Setup 6 script specifies metadata and publisher."""
        self.assertTrue(self.iss_path.exists())
        content = self.iss_path.read_text(encoding="utf-8")

        self.assertIn('#define MyAppName "Monitor"', content)
        brand_toml = self.project_root / "config" / "brand.toml"
        expected_version = "1.0.4"
        if brand_toml.exists():
            try:
                import tomllib
            except ModuleNotFoundError:
                import tomli as tomllib  # type: ignore
            with open(brand_toml, "rb") as f:
                data = tomllib.load(f)
            expected_version = data["brand"]["version"]
        self.assertIn(f'#define MyAppVersion "{expected_version}"', content)
        self.assertIn('#define MyAppPublisher "Morning Star Web"', content)
        self.assertIn('AppId={{8C41E92A-7B0D-4F3A-9E6B-21A0D8F3E512}', content)
        self.assertIn('OutputBaseFilename=Monitor_Setup_v{#MyAppVersion}', content)

    def test_03_inno_setup_compression_and_architecture(self) -> None:
        """Requirement R4 / Feature 27: Inno Setup enables lzma2/ultra64 solid compression."""
        content = self.iss_path.read_text(encoding="utf-8")
        self.assertIn("Compression=lzma2/ultra64", content)
        self.assertIn("SolidCompression=yes", content)
        self.assertIn("WizardStyle=modern", content)
        self.assertIn("PrivilegesRequired=lowest", content)

    def test_04_installer_legal_and_branding_assets(self) -> None:
        """Requirement R4: Installer references required icon and banner images."""
        installer_dir = self.project_root / "installer"
        self.assertTrue((installer_dir / "monitor.ico").exists(), "monitor.ico must exist")
        self.assertTrue((installer_dir / "wizard_large.bmp").exists(), "wizard_large.bmp must exist")
        self.assertTrue((installer_dir / "wizard_small.bmp").exists(), "wizard_small.bmp must exist")
        self.assertTrue((installer_dir / "EULA.txt").exists(), "EULA.txt must exist")
        self.assertTrue((installer_dir / "PRIVACY.txt").exists(), "PRIVACY.txt must exist")

    def test_05_windows_packaging_powershell_script(self) -> None:
        """Requirement R4: scripts/package_windows.ps1 packages Monitor.exe with SHA256 checksum."""
        self.assertTrue(self.package_script_path.exists())
        content = self.package_script_path.read_text(encoding="utf-8")

        self.assertIn("cargo build --release", content)
        self.assertIn("Monitor.exe", content)
        self.assertIn("Get-FileHash", content)
        self.assertIn("SHA256", content)


if __name__ == "__main__":
    unittest.main()
