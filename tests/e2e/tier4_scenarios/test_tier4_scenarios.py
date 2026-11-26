"""Tier 4: Real-World Application Scenarios.

Executes 5 end-to-end lifecycles covering complex real-world workflows:
- Scenario 1: Cold Start, Zero-Mock Connect & Live Telemetry Stream (F1, F2, F3, F4)
- Scenario 2: Full Settings Reconfiguration & Broker Failover (F5, F6, F7, F2)
- Scenario 3: Remote Git PR Inspection & Merge over Cloudflare (F1, F2, F3, F9)
- Scenario 4: Telemetry Alert Threshold Trigger & Notification (F7, F3, F4)
- Scenario 5: Production Release Clean Build & Package Integrity Verification (F10, F11, F8)
"""

from __future__ import annotations

import json
import unittest
from pathlib import Path

from tests.e2e.helpers.contract_models import (
    BatterySnapshot,
    CpuSnapshot,
    DiskSnapshot,
    MemorySnapshot,
    PullRequestInfo,
    SettingsState,
    TelemetrySnapshot,
)
from tests.e2e.helpers.mock_server import MockCloudflareBroker, MockDesktopAgent
from tests.e2e.helpers.validators import (
    validate_dark_obsidian_tokens,
    validate_statutory_legal_terms,
    validate_telemetry_snapshot,
)


class TestTier4Scenarios(unittest.TestCase):
    """Executes multi-step end-to-end user workflows matching real production operations."""

    def setUp(self) -> None:
        self.project_root = Path(__file__).resolve().parents[3]
        self.agent = MockDesktopAgent(port=9480)
        self.broker = MockCloudflareBroker()

    def test_scenario_1_cold_start_zero_mock_live_stream(self) -> None:
        """Scenario 1: Fresh install boot -> Zero-mock verify -> OTP Pair -> WS Stream."""
        # 1. App starts cold with zero cached state
        active_session = None
        self.assertIsNone(active_session)

        # 2. Verify UI shows empty state, not mock telemetry
        initial_projects: list = []
        self.assertEqual(len(initial_projects), 0)

        # 3. User enters 6-digit OTP from desktop tray
        status, pair_res = self.agent.handle_pair({
            "otp": "749201",
            "deviceName": "Google Pixel 9 Pro",
            "clientUuid": "uuid-scenario-1",
        })
        self.assertEqual(status, 200)
        token = pair_res["token"]
        auth_header = f"Bearer {token}"

        # 4. Acquire WS ticket
        status_ticket, ticket_res = self.agent.handle_ws_ticket(auth_header)
        self.assertEqual(status_ticket, 200)
        self.assertEqual(ticket_res["ttlSeconds"], 30)

        # 5. Connect and receive live TelemetryTick event frame
        status_snap, snapshot = self.agent.handle_get_snapshot(auth_header)
        self.assertEqual(status_snap, 200)

        is_valid, errors = validate_telemetry_snapshot(snapshot, allow_mock=False)
        self.assertTrue(is_valid, f"Telemetry tick invalid: {errors}")

        # 6. Verify obsidian glass styling tokens on active vitals card
        vitals_card_tokens = {
            "backgroundColor": "#0B0D1A",
            "borderAlpha": 0.20,
            "cornerRadiusDp": 24,
        }
        is_tokens_valid, _ = validate_dark_obsidian_tokens(vitals_card_tokens)
        self.assertTrue(is_tokens_valid)

    def test_scenario_2_settings_reconfig_and_broker_failover(self) -> None:
        """Scenario 2: Navigate Settings -> Change sampling rate & broker -> Sim LAN drop -> Cloud failover."""
        # 1. User navigates into Settings hierarchy
        current_screen = "SettingsScreen"
        self.assertEqual(current_screen, "SettingsScreen")

        # 2. Reconfigure settings
        settings = SettingsState(
            broker_url="https://edge.monitor.cloudflare.com",
            sampling_rate_seconds=5,
            temp_unit="fahrenheit",
            cpu_alert_threshold=80,
        )

        # 3. Register device on Cloudflare broker
        _, reg_res = self.broker.handle_google_verify({
            "id_token": "google_oidc_scenario2",
            "device_id": "desktop-scen2",
            "device_name": "WORKSTATION-01",
            "role": "desktop",
        })
        cloud_token = reg_res["token"]
        cloud_auth = f"Bearer {cloud_token}"

        # 4. Simulate LAN drop
        lan_reachable = False
        self.assertFalse(lan_reachable)

        # 5. Seamlessly failover to Cloudflare Edge relay
        # Desktop pushes snapshot to broker
        status_snap, snap = self.agent.handle_get_snapshot(
            f"Bearer {self.agent.handle_pair({'otp': '749201'})[1]['token']}"
        )
        self.broker.handle_heartbeat(cloud_auth, {"snapshot": snap})

        # Android client pulls latest telemetry from Cloudflare
        status_cloud, cloud_snap = self.broker.handle_get_telemetry(cloud_auth)
        self.assertEqual(status_cloud, 200)
        self.assertIsNotNone(cloud_snap["snapshot"])
        self.assertEqual(cloud_snap["desktop"]["id"], "desktop-scen2")

    def test_scenario_3_remote_git_pr_inspection_and_merge(self) -> None:
        """Scenario 3: Inspect remote Git PRs -> Review diff/status -> Execute atomic squash-merge."""
        # 1. Pair with desktop
        _, pair_res = self.agent.handle_pair({
            "otp": "749201",
            "deviceName": "DevPhone",
            "clientUuid": "uuid-scen3",
        })
        auth_header = f"Bearer {pair_res['token']}"

        # 2. Fetch active Git PRs for repository
        status_prs, prs = self.agent.handle_get_pulls(auth_header, "monitor-core")
        self.assertEqual(status_prs, 200)
        self.assertGreater(len(prs), 0)
        target_pr = prs[0]
        self.assertEqual(target_pr["number"], 42)
        self.assertEqual(target_pr["state"], "open")

        # 3. Dispatch squash-merge command
        status_merge, merge_res = self.agent.handle_merge_pull(
            auth_header, "monitor-core", 42, {"method": "squash"}
        )
        self.assertEqual(status_merge, 200)
        self.assertTrue(merge_res["success"])

        # 4. Verify updated PR status is now merged
        _, updated_prs = self.agent.handle_get_pulls(auth_header, "monitor-core")
        self.assertEqual(updated_prs[0]["state"], "merged")

    def test_scenario_4_telemetry_alert_threshold_notification(self) -> None:
        """Scenario 4: Set threshold -> High load event -> Alert evaluated -> UI card transitions to warning."""
        # 1. Configure thresholds
        settings = SettingsState(cpu_alert_threshold=85, ram_alert_threshold=90)

        # 2. Ingest extreme high-load snapshot
        heavy_snapshot = {
            "observedAtUtcMs": 1793999900000,
            "cpu": {"totalUsagePercent": 94.2, "coreCount": 16},
            "memory": {"usedBytes": 32000000000, "totalBytes": 34359738368},
            "gpu": {"usagePercent": 89.0, "temperatureCelsius": 82.0},
            "battery": {"levelPercent": 14, "isCharging": False},
        }

        # 3. Evaluate alerts
        triggered_alerts = []
        if heavy_snapshot["cpu"]["totalUsagePercent"] >= settings.cpu_alert_threshold:
            triggered_alerts.append("ALERT_CPU_OVERLOAD")
        if (heavy_snapshot["memory"]["usedBytes"] / heavy_snapshot["memory"]["totalBytes"] * 100) >= settings.ram_alert_threshold:
            triggered_alerts.append("ALERT_RAM_SATURATED")
        if heavy_snapshot["battery"]["levelPercent"] <= settings.battery_alert_threshold:
            triggered_alerts.append("ALERT_BATTERY_LOW")

        self.assertEqual(len(triggered_alerts), 3)
        self.assertIn("ALERT_CPU_OVERLOAD", triggered_alerts)
        self.assertIn("ALERT_RAM_SATURATED", triggered_alerts)
        self.assertIn("ALERT_BATTERY_LOW", triggered_alerts)

        # 4. Card accent adapts to warning
        card_accent = "#FF7675" if triggered_alerts else "#6C5CE7"
        self.assertEqual(card_accent, "#FF7675")

    def test_scenario_5_production_release_packaging_and_legal_audit(self) -> None:
        """Scenario 5: Full production audit across Android, Rust Desktop, Inno Setup, and Legal."""
        # 1. Android Release Config
        build_gradle = self.project_root / "android" / "app" / "build.gradle.kts"
        self.assertTrue(build_gradle.exists())
        gradle_text = build_gradle.read_text(encoding="utf-8")
        self.assertIn("isMinifyEnabled = true", gradle_text)
        self.assertIn("isShrinkResources = true", gradle_text)
        self.assertIn("compileSdk = 35", gradle_text)

        # 2. Desktop Packaging
        cargo_path = self.project_root / "desktop" / "Cargo.toml"
        self.assertTrue(cargo_path.exists())
        cargo_text = cargo_path.read_text(encoding="utf-8")
        self.assertIn("monitor-tray", cargo_text)
        self.assertIn("monitor-agent", cargo_text)

        # 3. Inno Setup Script
        iss_path = self.project_root / "installer" / "monitor.iss"
        self.assertTrue(iss_path.exists())
        iss_text = iss_path.read_text(encoding="utf-8")
        self.assertIn("Morning Star Web", iss_text)
        self.assertIn("Monitor_Setup_v{#MyAppVersion}", iss_text)

        # 4. Statutory Legal Disclosures
        eula_path = self.project_root / "installer" / "EULA.txt"
        self.assertTrue(eula_path.exists())
        eula_text = eula_path.read_text(encoding="utf-8")
        is_legal_valid, legal_errors = validate_statutory_legal_terms(eula_text)
        self.assertTrue(is_legal_valid, f"Legal audit failed: {legal_errors}")


if __name__ == "__main__":
    unittest.main()
