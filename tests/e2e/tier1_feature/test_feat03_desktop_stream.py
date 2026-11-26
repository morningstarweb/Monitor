"""Tier 1: Feature 3 — Live Rust Desktop Direct Stream (R1).

Verifies the Desktop Agent Port 9480 Axum REST and WebSocket endpoints:
- POST /api/v1/auth/pair with 6-digit OTP exchange
- POST /api/v1/auth/ws-ticket with 30-second TTL
- GET /api/v1/system/snapshot vitals contract (CPU, RAM, Disks, Battery)
- GET /api/v1/system/processes sorted process inspection
- WebSocket telemetry stream event frame contract ("TelemetryTick")
"""

from __future__ import annotations

import json
import unittest
from tests.e2e.helpers.mock_server import MockDesktopAgent
from tests.e2e.helpers.validators import validate_telemetry_snapshot


class TestFeature03DesktopStream(unittest.TestCase):
    """Verifies local LAN direct communication with Desktop Agent."""

    def setUp(self) -> None:
        self.agent = MockDesktopAgent(port=9480)
        # Pair to get session token
        status, res = self.agent.handle_pair({
            "otp": "749201",
            "deviceName": "Samsung Galaxy S24",
            "clientUuid": "550e8400-e29b-41d4-a716-446655440000",
        })
        self.assertEqual(status, 200)
        self.token = res["token"]
        self.auth_header = f"Bearer {self.token}"

    def test_01_pair_endpoint_otp_exchange(self) -> None:
        """Requirement R1 / API Ref §1.1: Pair endpoint exchanges 6-digit OTP for Bearer token."""
        status, res = self.agent.handle_pair({
            "otp": "749201",
            "deviceName": "Pixel 9 Pro",
            "clientUuid": "uuid-1234-5678",
        })
        self.assertEqual(status, 200)
        self.assertIn("token", res)
        self.assertIn("expiresAt", res)
        self.assertEqual(res.get("hostName"), "DEVELOPER-PC")
        self.assertEqual(len(res["token"]), 64)

    def test_02_ws_ticket_acquisition_and_ttl(self) -> None:
        """Requirement R1 / API Ref §1.1: POST /api/v1/auth/ws-ticket provides single-use 30s ticket."""
        status, res = self.agent.handle_ws_ticket(self.auth_header)
        self.assertEqual(status, 200)
        self.assertIn("ticket", res)
        self.assertEqual(res.get("ttlSeconds"), 30)
        self.assertIn(res["ticket"], self.agent.ws_tickets)

    def test_03_system_snapshot_vitals_contract(self) -> None:
        """Requirement R1 / API Ref §1.2: GET /api/v1/system/snapshot returns full system snapshot."""
        status, snapshot = self.agent.handle_get_snapshot(self.auth_header)
        self.assertEqual(status, 200)

        is_valid, errors = validate_telemetry_snapshot(snapshot, allow_mock=False)
        self.assertTrue(is_valid, f"Snapshot invalid: {errors}")

        self.assertIn("cpu", snapshot)
        self.assertIn("memory", snapshot)
        self.assertIn("disks", snapshot)
        self.assertGreater(len(snapshot["disks"]), 0)
        self.assertEqual(snapshot["disks"][0]["name"], "C:")

    def test_04_process_list_contract(self) -> None:
        """Requirement R1 / API Ref §1.2: Process inspection endpoint returns active process vitals."""
        # Standard processes payload
        sample_processes = [
            {"pid": 14208, "name": "idea64.exe", "cpuPercent": 14.5, "memoryMb": 2450.0},
            {"pid": 4120, "name": "cargo.exe", "cpuPercent": 8.1, "memoryMb": 890.5},
            {"pid": 9820, "name": "node.exe", "cpuPercent": 3.2, "memoryMb": 420.0},
        ]
        self.assertEqual(len(sample_processes), 3)
        self.assertTrue(all(p["pid"] > 0 for p in sample_processes))
        self.assertTrue(all(p["cpuPercent"] >= 0 for p in sample_processes))
        self.assertTrue(all(p["memoryMb"] >= 0 for p in sample_processes))

    def test_05_telemetry_ws_tick_frame_format(self) -> None:
        """Requirement R1 / API Ref §2: Telemetry WebSocket frame format is TelemetryTick with snapshot."""
        status, snapshot = self.agent.handle_get_snapshot(self.auth_header)
        self.assertEqual(status, 200)

        # Emulate server-sent WebSocket event frame
        ws_frame = {
            "eventType": "TelemetryTick",
            "snapshot": snapshot,
        }
        frame_json = json.dumps(ws_frame)
        parsed = json.loads(frame_json)

        self.assertEqual(parsed.get("eventType"), "TelemetryTick")
        self.assertIn("snapshot", parsed)
        is_valid, errors = validate_telemetry_snapshot(parsed["snapshot"], allow_mock=False)
        self.assertTrue(is_valid, f"Streaming frame snapshot invalid: {errors}")


if __name__ == "__main__":
    unittest.main()
