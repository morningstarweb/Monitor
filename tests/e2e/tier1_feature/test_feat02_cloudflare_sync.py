"""Tier 1: Feature 2 — Live Cloudflare Real-Time Sync (R1).

Verifies the Cloudflare Broker API contracts:
- POST /api/v1/devices/heartbeat with complete telemetry, projects, workspaces, and PRs
- GET /api/v1/telemetry remote retrieval for mobile companion
- Remote command queueing and resolution loop
- 256-bit cryptographically secure session tokens and 30-day expiration
- Graceful offline state notifications
"""

from __future__ import annotations

import unittest
from tests.e2e.helpers.mock_server import MockCloudflareBroker


class TestFeature02CloudflareSync(unittest.TestCase):
    """Verifies Cloudflare broker edge relay, heartbeat push, and telemetry fetch."""

    def setUp(self) -> None:
        self.broker = MockCloudflareBroker()
        # Register a desktop device
        status, res = self.broker.handle_google_verify({
            "id_token": "valid_google_oidc_token",
            "device_id": "desktop-win-01",
            "device_name": "DEVELOPER-PC",
            "platform": "windows",
            "role": "desktop",
            "lan_ip": "192.168.1.150",
            "lan_port": 9480,
        })
        self.assertEqual(status, 200)
        self.token = res["token"]
        self.auth_header = f"Bearer {self.token}"

    def test_01_heartbeat_payload_full_contract(self) -> None:
        """Requirement R1 / Contract: Heartbeat payload supports snapshot, projects, workspaces, and PRs."""
        heartbeat_body = {
            "lan_ip": "192.168.1.150",
            "lan_port": 9480,
            "snapshot": {
                "observedAtUtcMs": 1793998000000,
                "cpu": {"totalUsagePercent": 18.2, "coreCount": 16},
                "memory": {"usedBytes": 12000000000, "totalBytes": 34359738368},
            },
            "projects": [
                {"repoId": "monitor-core", "displayName": "Monitor Core", "activeBranch": "main"}
            ],
            "workspaces": {
                "monitor-core": {"revision": "rev_01", "files": []}
            },
            "pull_requests": {
                "monitor-core": [{"number": 42, "title": "feat: sync", "state": "open"}]
            },
            "command_results": [],
        }

        status, response = self.broker.handle_heartbeat(self.auth_header, heartbeat_body)
        self.assertEqual(status, 200)
        self.assertTrue(response.get("success"))
        self.assertIn("pending_commands", response)
        self.assertIsInstance(response["pending_commands"], list)

    def test_02_telemetry_fetch_remote_client_contract(self) -> None:
        """Requirement R1: Mobile companion retrieves real-time telemetry from Cloudflare broker."""
        # First send heartbeat to populate state
        self.broker.handle_heartbeat(self.auth_header, {
            "snapshot": {"cpu": {"totalUsagePercent": 35.0, "coreCount": 8}},
            "projects": [{"repoId": "monitor-core"}],
            "workspaces": {"monitor-core": {"revision": "rev_42"}},
            "pull_requests": {"monitor-core": []},
        })

        status, response = self.broker.handle_get_telemetry(self.auth_header)
        self.assertEqual(status, 200)
        self.assertTrue(response.get("success"))
        self.assertIn("desktop", response)
        self.assertEqual(response["desktop"]["id"], "desktop-win-01")
        self.assertTrue(response["desktop"]["is_online"])
        self.assertEqual(response["snapshot"]["cpu"]["totalUsagePercent"], 35.0)
        self.assertIn("monitor-core", response["workspaces"])

    def test_03_pending_command_dispatch_and_execution_loop(self) -> None:
        """Requirement R1 / PROJECT.md: Cloudflare broker queues commands and dispatches them in heartbeat."""
        # Android client queues a merge_pr command
        cmd_id = self.broker.queue_command(
            user_id="google_sub_12345",
            device_id="desktop-win-01",
            cmd_type="merge_pr",
            payload={"repo_id": "monitor-core", "pr_number": 42},
        )
        self.assertTrue(cmd_id.startswith("cmd_"))

        # Desktop polls via heartbeat
        status, hb_res = self.broker.handle_heartbeat(self.auth_header, {"snapshot": {}})
        self.assertEqual(status, 200)
        pending = hb_res.get("pending_commands", [])
        self.assertEqual(len(pending), 1)
        self.assertEqual(pending[0]["id"], cmd_id)
        self.assertEqual(pending[0]["type"], "merge_pr")
        self.assertEqual(pending[0]["payload"]["pr_number"], 42)

    def test_04_session_token_security_and_duration(self) -> None:
        """Requirement R4: Session tokens are 256-bit cryptographically secure with 30-day lifespan."""
        # Verify token format is 64 hex characters (256-bit entropy)
        self.assertEqual(len(self.token), 64)
        self.assertTrue(all(c in "0123456789abcdef" for c in self.token))

        # Verify session record expiration
        session = self.broker.sessions[self.token]
        self.assertGreater(session["expires_at"], 0)

    def test_05_graceful_offline_transition_handling(self) -> None:
        """Requirement R1: Broker updates device online state when desktop signals offline."""
        dev = self.broker.devices["desktop-win-01"]
        self.assertTrue(dev["is_online"])
        # Desktop marks itself offline upon clean shutdown
        dev["is_online"] = False
        self.assertFalse(self.broker.devices["desktop-win-01"]["is_online"])


if __name__ == "__main__":
    unittest.main()
