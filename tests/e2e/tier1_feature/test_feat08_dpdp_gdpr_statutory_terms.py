"""Tier 1: Feature 8 — DPDP Act 2023 / GDPR Statutory Terms (R2, R3).

Verifies legal terms, privacy policies, and statutory compliance:
- Studio entity identification: Morning Star Web, Kolkata, India
- India Digital Personal Data Protection (DPDP) Act 2023 compliance
- EU / UK General Data Protection Regulation (GDPR) Article 5 principles
- Zero-surveillance and local-first data processing guarantee
- Designated grievance redressal officer contact (contact@morningstarweb.space)
"""

from __future__ import annotations

import unittest
from pathlib import Path
from tests.e2e.helpers.validators import validate_statutory_legal_terms


class TestFeature08DpdpGdprStatutoryTerms(unittest.TestCase):
    """Verifies presence and accuracy of statutory legal disclosures across documentation and app packages."""

    def setUp(self) -> None:
        self.project_root = Path(__file__).resolve().parents[3]
        self.eula_path = self.project_root / "installer" / "EULA.txt"
        self.privacy_path = self.project_root / "installer" / "PRIVACY.txt"

    def test_01_studio_entity_and_kolkata_jurisdiction(self) -> None:
        """Requirement R3 / Feature 21: Entity name and Kolkata jurisdiction must be specified."""
        self.assertTrue(self.eula_path.exists(), "installer/EULA.txt must exist")
        content = self.eula_path.read_text(encoding="utf-8")

        self.assertIn("Morning Star Web", content)
        self.assertIn("Kolkata", content)
        self.assertIn("West Bengal", content)
        self.assertIn("India", content)

    def test_02_dpdp_act_2023_statutory_notices(self) -> None:
        """Requirement R3 / Feature 21: DPDP Act 2023 compliance explicitly documented."""
        content = self.eula_path.read_text(encoding="utf-8")
        is_valid, errors = validate_statutory_legal_terms(content)
        self.assertTrue(is_valid, f"Statutory terms missing: {errors}")
        self.assertIn("DPDP Act 2023", content)

    def test_03_gdpr_article_5_data_minimization(self) -> None:
        """Requirement R3 / Feature 21: GDPR compliance and data minimization affirmed."""
        content = self.privacy_path.read_text(encoding="utf-8")
        self.assertIn("GDPR", content)
        self.assertTrue("zero surveillance" in content.lower() or "zero-surveillance" in content.lower())

    def test_04_zero_server_upload_architecture_guarantee(self) -> None:
        """Requirement R1 / R3: Explicit guarantee that private code is never uploaded to remote servers."""
        content = self.eula_path.read_text(encoding="utf-8")
        self.assertIn("Local-First Processing", content)
        self.assertIn("Your Source Code", content)
        # Verify statement of zero claims/ownership over user code
        self.assertIn("asserts zero ownership", content.lower())

    def test_05_grievance_officer_contact_information(self) -> None:
        """Requirement R3 / Feature 21: Designated grievance officer email present in policies."""
        content = self.eula_path.read_text(encoding="utf-8")
        self.assertIn("contact@morningstarweb.space", content)
        self.assertIn("grievance redressal", content.lower())
        self.assertIn("grievance officer", content.lower())


if __name__ == "__main__":
    unittest.main()
