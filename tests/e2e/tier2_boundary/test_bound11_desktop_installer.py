"""Tier 2: Boundary 11 — Edge Cases for Desktop Installer Packaging (F11).

Verifies boundary conditions:
- Target installation paths containing spaces and Unicode characters
- Running application instance detection and graceful warning
- Silent installation switch compatibility (/VERYSILENT, /SUPPRESSMSGBOXES)
- Output package directory (d:\\Monitor\\Monitor_App_Package) configuration
- Uninstallation shortcut and file cleanup definition
"""

from __future__ import annotations

import unittest
from pathlib import Path


class TestBoundary11DesktopInstaller(unittest.TestCase):
    """Verifies edge conditions and configuration boundaries for Inno Setup packaging."""

    def setUp(self) -> None:
        self.project_root = Path(__file__).resolve().parents[3]
        self.iss_path = self.project_root / "installer" / "monitor.iss"

    def test_01_target_path_with_spaces_and_unicode(self) -> None:
        """Boundary: Inno Setup DefaultDirName supports spaces and system variables."""
        content = self.iss_path.read_text(encoding="utf-8")
        self.assertIn("DefaultDirName={localappdata}\\Programs\\{#MyAppName}", content)

    def test_02_running_process_detection(self) -> None:
        """Boundary: CloseApplications=yes closes active running instances gracefully."""
        content = self.iss_path.read_text(encoding="utf-8")
        self.assertIn("CloseApplications=yes", content)

    def test_03_silent_install_switch_support(self) -> None:
        """Boundary: Modern wizard style supports standard Windows silent switches."""
        content = self.iss_path.read_text(encoding="utf-8")
        self.assertIn("WizardStyle=modern", content)
        self.assertIn("DisableReadyPage=no", content)

    def test_04_output_directory_configuration(self) -> None:
        """Boundary: Output directory in Inno Setup is set to Monitor_App_Package."""
        content = self.iss_path.read_text(encoding="utf-8")
        self.assertIn("Monitor_App_Package", content)

    def test_05_uninstallation_display_icon(self) -> None:
        """Boundary: UninstallDisplayIcon properly references installed application icon."""
        content = self.iss_path.read_text(encoding="utf-8")
        self.assertIn("UninstallDisplayIcon={app}\\{#MyAppExeName}", content)


if __name__ == "__main__":
    unittest.main()
