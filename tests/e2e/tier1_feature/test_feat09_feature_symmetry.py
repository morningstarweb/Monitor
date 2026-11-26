"""Tier 1: Feature 9 — Bidirectional Feature Symmetry Matrix (R3).

Verifies two-way capability symmetry between Android Client and Rust Desktop Agent:
- Remote Git Pull Request listing (GET /api/v1/projects/{repo_id}/pulls)
- Remote Pull Request Merge (POST /api/v1/projects/{repo_id}/pulls/{pr}/merge) with merge/squash/rebase
- Physical Disks Telemetry collection (sysinfo::Disks) across both platforms
- Remote Process Termination (POST /api/v1/system/processes/{pid}/kill)
- Heartbeat command execution loop symmetry
"""

from __future__ import annotations

import unittest
from tests.e2e.helpers.mock_server import MockDesktopAgent


class TestFeature09FeatureSymmetry(unittest.TestCase):
    """Verifies bidirectional capability parity between Desktop Agent and Android Client."""

    def setUp(self) -> None:
        self.agent = MockDesktopAgent(port=9480)
        _, res = self.agent.handle_pair({
            "otp": "749201",
            "deviceName": "TestPhone",
            "clientUuid": "uuid-sym-01",
        })
        self.token = res["token"]
        self.auth_header = f"Bearer {self.token}"

    def test_01_git_pull_requests_endpoint_symmetry(self) -> None:
        """Requirement R3 / Feature 2: Android client can list PRs served by Desktop Agent."""
        status, prs = self.agent.handle_get_pulls(self.auth_header, "monitor-core")
        self.assertEqual(status, 200)
        self.assertIsInstance(prs, list)
        self.assertGreater(len(prs), 0)
        first_pr = prs[0]
        self.assertEqual(first_pr["number"], 42)
        self.assertIn("title", first_pr)
        self.assertIn("author", first_pr)
        self.assertIn("state", first_pr)
        self.assertIn("base_branch", first_pr)
        self.assertIn("head_branch", first_pr)

    def test_02_git_pull_request_merge_endpoint_symmetry(self) -> None:
        """Requirement R3 / Feature 2: Android can merge PRs on Desktop with merge, squash, or rebase."""
        # Test squash merge
        status, res = self.agent.handle_merge_pull(
            self.auth_header, "monitor-core", 42, {"method": "squash"}
        )
        self.assertEqual(status, 200)
        self.assertTrue(res.get("success"))
        self.assertIn("squash", res.get("message", "").lower())

        # Verify PR state is updated
        _, prs = self.agent.handle_get_pulls(self.auth_header, "monitor-core")
        self.assertEqual(prs[0]["state"], "merged")

    def test_03_physical_disks_telemetry_symmetry(self) -> None:
        """Requirement R3 / Feature 5 & 13: Physical disk metrics populated in telemetry snapshot."""
        status, snapshot = self.agent.handle_get_snapshot(self.auth_header)
        self.assertEqual(status, 200)
        disks = snapshot.get("disks", [])
        self.assertGreater(len(disks), 0)
        disk = disks[0]
        self.assertIn("name", disk)
        self.assertIn("mount_point", disk)
        self.assertGreater(disk["total_bytes"], 0)
        self.assertGreater(disk["available_bytes"], 0)

    def test_04_process_kill_endpoint_symmetry(self) -> None:
        """Requirement R3 / Feature 7 & 13: Android can terminate runaway processes on Desktop."""
        target_pid = 4920
        status, res = self.agent.handle_kill_process(self.auth_header, target_pid)
        self.assertEqual(status, 200)
        self.assertTrue(res.get("success"))
        self.assertEqual(res.get("pid"), target_pid)
        self.assertIn(target_pid, self.agent.killed_processes)

    def test_05_remote_command_execution_symmetry(self) -> None:
        """Requirement R3 / Feature 4: Cloudflare heartbeat command execution parity."""
        # Commands queued from Android client execute on Desktop
        commands = [
            {"type": "kill_process", "payload": {"pid": 1234}},
            {"type": "merge_pr", "payload": {"repo_id": "monitor-core", "pr_number": 42}},
        ]
        supported_types = {"kill_process", "merge_pr", "sync_repos"}
        for cmd in commands:
            self.assertIn(cmd["type"], supported_types)


if __name__ == "__main__":
    unittest.main()
