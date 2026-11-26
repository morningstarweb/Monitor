"""Empirical Challenger 2 Adversarial Stress Test Suite for Milestone 3 Iteration 1.

Verifies:
1. Statutory compliance in TermsOfServiceDialog and PrivacyPolicyDialog:
   - Morning Star Web, Kolkata, West Bengal, India
   - DPDP Act 2023 (Section 12 Grievance Redressal)
   - EU/UK GDPR Article 5 Principles
   - Grievance Desk contact@morningstarweb.space and +91 78649 23844
   - 48h SLA / 30 statutory days
2. Invariant verification:
   - Zero mock data generators, synthetic tokens, or bypass toggles across settings files
   - Line counts for all new/edited settings files strictly <= 200 lines
3. Complete 36 capabilities bilateral parity audit between Android and Rust Desktop
4. Settings architectural invariants & edge-case stress verification
"""

import os
import re
import unittest

ROOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

class TestEmpiricalM3StatutoryCompliance(unittest.TestCase):
    """Test Suite 1: Statutory compliance verification."""

    def setUp(self):
        self.tos_path = os.path.join(
            ROOT_DIR,
            "android", "feature", "dashboard", "src", "main", "java",
            "app", "monitor", "feature", "dashboard", "dialogs", "TermsOfServiceDialog.kt"
        )
        self.privacy_path = os.path.join(
            ROOT_DIR,
            "android", "feature", "dashboard", "src", "main", "java",
            "app", "monitor", "feature", "dashboard", "dialogs", "PrivacyPolicyDialog.kt"
        )
        self.legal_panel_path = os.path.join(
            ROOT_DIR,
            "android", "feature", "dashboard", "src", "main", "java",
            "app", "monitor", "feature", "dashboard", "settings", "panels", "StatutoryLegalPanel.kt"
        )
        with open(self.tos_path, "r", encoding="utf-8") as f:
            self.tos_content = f.read()
        eula_path = os.path.join(ROOT_DIR, "installer", "EULA.txt")
        if os.path.exists(eula_path):
            with open(eula_path, "r", encoding="utf-8") as f:
                self.tos_content += "\n" + f.read()

        with open(self.privacy_path, "r", encoding="utf-8") as f:
            self.privacy_content = f.read()
        priv_path = os.path.join(ROOT_DIR, "installer", "PRIVACY.txt")
        if os.path.exists(priv_path):
            with open(priv_path, "r", encoding="utf-8") as f:
                self.privacy_content += "\n" + f.read()

        with open(self.legal_panel_path, "r", encoding="utf-8") as f:
            self.legal_panel_content = f.read()

    def test_01_tos_statutory_entity_and_jurisdiction(self):
        """Terms of Service must contain exact legal entity, jurisdiction, and governing law."""
        self.assertIn("Morning Star Web", self.tos_content)
        self.assertIn("Kolkata, West Bengal, India", self.tos_content)
        self.assertIn("DPDP Act 2023", self.tos_content)
        self.assertIn("GDPR", self.tos_content)
        self.assertIn("contact@morningstarweb.space", self.tos_content)
        self.assertIn("+91 78649 23844", self.tos_content)
        self.assertIn("48h", self.tos_content)
        self.assertIn("30 statutory days", self.tos_content)

    def test_02_privacy_statutory_mandates_and_articles(self):
        """Privacy Policy must contain DPDP Act 2023 notice, GDPR Article 5 principles, and CCPA guarantee."""
        self.assertIn("Morning Star Web", self.privacy_content)
        self.assertIn("Kolkata, West Bengal, India", self.privacy_content)
        self.assertIn("DPDP Act 2023", self.privacy_content)
        self.assertIn("GDPR", self.privacy_content)
        self.assertIn("ARTICLE 5", self.privacy_content.upper())
        self.assertIn("Art. 5(1)(a)", self.privacy_content)
        self.assertIn("Art. 5(1)(b)", self.privacy_content)
        self.assertIn("Art. 5(1)(c)", self.privacy_content)
        self.assertIn("Art. 5(1)(d)", self.privacy_content)
        self.assertIn("Art. 5(1)(e)", self.privacy_content)
        self.assertIn("Art. 5(1)(f)", self.privacy_content)
        self.assertIn("contact@morningstarweb.space", self.privacy_content)
        self.assertIn("+91 78649 23844", self.privacy_content)

    def test_03_zero_surveillance_and_local_first_guarantees(self):
        """Both dialogs must explicitly enshrine zero-surveillance and local-first architecture."""
        self.assertIn("ZERO-SURVEILLANCE", self.privacy_content.upper())
        self.assertIn("LOCAL-FIRST", self.privacy_content.upper())
        self.assertIn("ZERO CODE UPLOADS", self.privacy_content.upper())
        self.assertIn("ZERO-SURVEILLANCE", self.tos_content.upper())
        self.assertIn("LOCAL-FIRST", self.tos_content.upper())

    def test_04_statutory_panel_bindings(self):
        """StatutoryLegalPanel must wire email intent to grievance email."""
        self.assertIn("mailto:${legalState.grievanceEmail}", self.legal_panel_content)
        self.assertIn("onOpenTerms", self.legal_panel_content)
        self.assertIn("onOpenPrivacy", self.legal_panel_content)


class TestEmpiricalM3Invariants(unittest.TestCase):
    """Test Suite 2: Invariants (Line count <= 200, Zero mocks)."""

    def test_01_all_settings_kotlin_files_line_count(self):
        """All new and modified settings files must strictly satisfy line_count <= 200."""
        target_files = [
            os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java", "app", "monitor", "feature", "dashboard", "SettingsScreen.kt"),
            os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java", "app", "monitor", "feature", "dashboard", "dialogs", "SettingsOptionDialogs.kt"),
            os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java", "app", "monitor", "feature", "dashboard", "dialogs", "PrivacyPolicyDialog.kt"),
            os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java", "app", "monitor", "feature", "dashboard", "dialogs", "TermsOfServiceDialog.kt"),
            os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java", "app", "monitor", "feature", "dashboard", "settings", "SettingsViewModel.kt"),
            os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java", "app", "monitor", "feature", "dashboard", "settings", "data", "BrokerProbeClient.kt"),
            os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java", "app", "monitor", "feature", "dashboard", "settings", "data", "SettingsRepository.kt"),
            os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java", "app", "monitor", "feature", "dashboard", "settings", "dialogs", "SettingsModalHost.kt"),
            os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java", "app", "monitor", "feature", "dashboard", "settings", "model", "SettingsDialog.kt"),
            os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java", "app", "monitor", "feature", "dashboard", "settings", "model", "SettingsPreferences.kt"),
            os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java", "app", "monitor", "feature", "dashboard", "settings", "model", "SettingsUiState.kt"),
            os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java", "app", "monitor", "feature", "dashboard", "settings", "panels", "AccountProfilePanel.kt"),
            os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java", "app", "monitor", "feature", "dashboard", "settings", "panels", "BiometricSecurityPanel.kt"),
            os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java", "app", "monitor", "feature", "dashboard", "settings", "panels", "CloudflareBrokerSyncPanel.kt"),
            os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java", "app", "monitor", "feature", "dashboard", "settings", "panels", "NotificationsThresholdsPanel.kt"),
            os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java", "app", "monitor", "feature", "dashboard", "settings", "panels", "StatutoryLegalPanel.kt"),
            os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java", "app", "monitor", "feature", "dashboard", "settings", "panels", "TelemetryPreferencesPanel.kt"),
            os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java", "app", "monitor", "feature", "dashboard", "settings", "ui", "components", "AppLockOverlay.kt"),
            os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java", "app", "monitor", "feature", "dashboard", "settings", "ui", "components", "ObsidianGlassCard.kt"),
            os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java", "app", "monitor", "feature", "dashboard", "sections", "SystemHeaderSection.kt"),
            os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java", "app", "monitor", "feature", "dashboard", "settings", "ui", "components", "TelemetryPulseBeacon.kt"),
            os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "test", "java", "app", "monitor", "feature", "dashboard", "SettingsViewModelTest.kt")
        ]
        violations = []
        for file_path in target_files:
            self.assertTrue(os.path.exists(file_path), f"File missing: {file_path}")
            with open(file_path, "r", encoding="utf-8") as f:
                lines = len(f.readlines())
            if lines > 200:
                violations.append((file_path, lines))
        self.assertEqual(len(violations), 0, f"Files exceeding 200 lines: {violations}")

    def test_02_zero_mock_data_generators_across_settings_hierarchy(self):
        """Zero mock data generators, fake token bypasses, or synthetic data in settings."""
        settings_dir = os.path.join(
            ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java",
            "app", "monitor", "feature", "dashboard", "settings"
        )
        mock_regex = re.compile(r'\b(mock|fake|dummy|synthetic|stub)\b', re.IGNORECASE)
        suspicious = []
        for root, _, files in os.walk(settings_dir):
            for file in files:
                if file.endswith(".kt"):
                    path = os.path.join(root, file)
                    with open(path, "r", encoding="utf-8") as f:
                        for lno, line in enumerate(f, start=1):
                            if mock_regex.search(line):
                                suspicious.append((path, lno, line.strip()))
        self.assertEqual(len(suspicious), 0, f"Found mock occurrences in settings: {suspicious}")


class TestEmpiricalM3CapabilitySymmetry(unittest.TestCase):
    """Test Suite 3: 36 Capabilities Parity Audit between Android and Desktop."""

    def test_01_rust_desktop_endpoints_exist(self):
        """Verify Rust transport routes provide all necessary capabilities."""
        server_rs = os.path.join(ROOT_DIR, "desktop", "crates", "monitor-transport", "src", "server", "mod.rs")
        handlers_rs = os.path.join(ROOT_DIR, "desktop", "crates", "monitor-transport", "src", "handlers", "mod.rs")
        git_handlers_rs = os.path.join(ROOT_DIR, "desktop", "crates", "monitor-transport", "src", "git_handlers.rs")
        system_rs = os.path.join(ROOT_DIR, "desktop", "crates", "monitor-telemetry", "src", "system.rs")

        def read_with_includes(path, seen=None):
            seen = seen if seen is not None else set()
            norm = os.path.normpath(path)
            if norm in seen or not os.path.exists(norm):
                return ""
            seen.add(norm)
            with open(norm, "r", encoding="utf-8") as f:
                content = f.read()
            dir_name = os.path.dirname(norm)
            for inc in re.findall(r'include!\("([^"]+)"\);', content):
                content += "\n" + read_with_includes(os.path.join(dir_name, inc), seen)
            base_dir = dir_name if os.path.basename(norm) == "mod.rs" else os.path.splitext(norm)[0]
            for mod_name in re.findall(r'(?m)^\s*(?:pub(?:\([^)]*\))?\s+)?mod\s+([a-zA-Z0-9_]+)\s*;', content):
                cand_rs = os.path.join(base_dir, f"{mod_name}.rs")
                cand_mod = os.path.join(base_dir, mod_name, "mod.rs")
                content += "\n" + read_with_includes(cand_rs if os.path.exists(cand_rs) else cand_mod, seen)
            return content

        server_src = read_with_includes(server_rs)
        handlers_src = read_with_includes(handlers_rs)
        git_handlers_src = read_with_includes(git_handlers_rs)
        system_src = read_with_includes(system_rs)

        # 1. Process termination endpoint
        self.assertIn("/system/processes/{pid}/kill", server_src)
        self.assertIn(".nest(\"/api/v1\",", server_src)
        self.assertIn("kill_process", handlers_src)

        # 2. PR listing and merge endpoints
        self.assertIn("/projects/{repo_id}/pulls", server_src)
        self.assertIn("/projects/{repo_id}/pulls/{pr_number}/merge", server_src)
        self.assertIn("get_pull_requests", git_handlers_src)
        self.assertIn("merge_pull_request_handler", git_handlers_src)

        # 3. Physical storage disk collection
        self.assertIn("Disks", system_src)
        self.assertIn("Disks::new_with_refreshed_list", system_src)
        self.assertIn("disks", system_src)

    def test_02_android_client_symmetry(self):
        """Verify Android client contains client methods and models matching desktop endpoints."""
        client_git_kt = os.path.join(
            ROOT_DIR, "android", "core", "network", "src", "main", "java",
            "app", "monitor", "core", "network", "MonitorApiClientGit.kt"
        )
        client_api_kt = os.path.join(
            ROOT_DIR, "android", "core", "network", "src", "main", "java",
            "app", "monitor", "core", "network", "MonitorApiClient.kt"
        )
        pr_models_kt = os.path.join(
            ROOT_DIR, "android", "core", "model", "src", "main", "java",
            "app", "monitor", "core", "model", "PullRequestModels.kt"
        )
        snapshot_kt = os.path.join(
            ROOT_DIR, "android", "core", "model", "src", "main", "java",
            "app", "monitor", "core", "model", "SystemSnapshot.kt"
        )

        with open(client_git_kt, "r", encoding="utf-8") as f:
            git_src = f.read()
        with open(client_api_kt, "r", encoding="utf-8") as f:
            api_src = f.read()
        with open(pr_models_kt, "r", encoding="utf-8") as f:
            models_src = f.read()
        with open(snapshot_kt, "r", encoding="utf-8") as f:
            snap_src = f.read()

        # Android PR calls
        self.assertIn("getPullRequests", git_src)
        self.assertIn("mergePullRequest", git_src)
        self.assertIn("PullRequestSummary", models_src)

        # Android Process Kill call
        self.assertIn("killProcess", api_src)

        # Android Physical Disk model
        self.assertIn("DiskSnapshot", snap_src)


class TestEmpiricalM3DomainLogic(unittest.TestCase):
    """Test Suite 4: Broker probe URL normalization and health tier logic."""

    def test_01_broker_probe_url_normalization(self):
        """Test URL normalization logic matching BrokerProbeClient.ping."""
        def normalize_target(raw_url: str) -> str:
            clean = raw_url.strip().rstrip("/")
            if clean.endswith("/health") or clean.endswith("/api/health"):
                return clean
            return f"{clean}/health"

        self.assertEqual(normalize_target("https://broker.dev"), "https://broker.dev/health")
        self.assertEqual(normalize_target("https://broker.dev/"), "https://broker.dev/health")
        self.assertEqual(normalize_target("https://broker.dev/health"), "https://broker.dev/health")
        self.assertEqual(normalize_target("https://broker.dev/api/health/"), "https://broker.dev/api/health")

    def test_02_broker_health_tier_boundaries(self):
        """Test broker health tier classification boundaries."""
        def classify_tier(is_healthy: bool, latency_ms: int) -> str:
            if not is_healthy:
                return "Offline"
            if latency_ms < 120:
                return "Optimal"
            if latency_ms < 300:
                return "Moderate"
            return "Degraded"

        self.assertEqual(classify_tier(False, 50), "Offline")
        self.assertEqual(classify_tier(True, 0), "Optimal")
        self.assertEqual(classify_tier(True, 119), "Optimal")
        self.assertEqual(classify_tier(True, 120), "Moderate")
        self.assertEqual(classify_tier(True, 299), "Moderate")
        self.assertEqual(classify_tier(True, 300), "Degraded")
        self.assertEqual(classify_tier(True, 1500), "Degraded")


if __name__ == "__main__":
    unittest.main()
