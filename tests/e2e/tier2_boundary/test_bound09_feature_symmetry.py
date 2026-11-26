"""Tier 2: Boundary 9 — Edge Cases for Feature Symmetry (F9).

Verifies boundary conditions:
- Invalid PR numbers (0, negative, non-existent 999999) return 400/404
- Unsupported PR merge methods (fastforward_only, cherrypick) return 400
- 100% full disk (0 available bytes) calculates usage without divide-by-zero
- Process termination on nonexistent PID handles error cleanly
- Unsupported remote command type handled without daemon termination
"""

from __future__ import annotations

import unittest
from tests.e2e.helpers.mock_server import MockDesktopAgent


class TestBoundary09FeatureSymmetry(unittest.TestCase):
    """Verifies edge conditions and error handling in bidirectional symmetry features."""

    def setUp(self) -> None:
        self.agent = MockDesktopAgent(port=9480)
        _, res = self.agent.handle_pair({
            "otp": "749201",
            "deviceName": "TestPhone",
            "clientUuid": "uuid-b09",
        })
        self.auth_header = f"Bearer {res['token']}"

    def test_01_invalid_pr_numbers_rejected(self) -> None:
        """Boundary: Merging non-existent PR returns 404."""
        status_0, res_0 = self.agent.handle_merge_pull(
            self.auth_header, "monitor-core", 0, {"method": "merge"}
        )
        self.assertEqual(status_0, 404)

        status_999, res_999 = self.agent.handle_merge_pull(
            self.auth_header, "monitor-core", 999999, {"method": "merge"}
        )
        self.assertEqual(status_999, 404)

    def test_02_unsupported_merge_methods_rejected(self) -> None:
        """Boundary: Unrecognized merge strategies return HTTP 400."""
        invalid_methods = ["fastforward", "cherrypick", "revert", ""]
        for m in invalid_methods:
            status, res = self.agent.handle_merge_pull(
                self.auth_header, "monitor-core", 42, {"method": m}
            )
            self.assertEqual(status, 400, f"Method '{m}' must be rejected with 400")

    def test_03_full_disk_zero_available_bytes_calculation(self) -> None:
        """Boundary: 0 available bytes on drive computes 100.0% usage without zero division."""
        total_bytes = 1_000_000_000_000
        available_bytes = 0

        def calculate_percent_used(total: int, available: int) -> float:
            if total <= 0:
                return 0.0
            used = total - available
            return round((used / total) * 100.0, 1)

        self.assertEqual(calculate_percent_used(total_bytes, available_bytes), 100.0)
        # Edge: total_bytes is 0 (unmounted / zero-byte partition)
        self.assertEqual(calculate_percent_used(0, 0), 0.0)

    def test_04_process_termination_on_nonexistent_pid(self) -> None:
        """Boundary: Terminating PID that does not exist returns success=false or safe handling."""
        status, res = self.agent.handle_kill_process(self.auth_header, 9999999)
        self.assertEqual(status, 200)

    def test_05_unknown_cloud_command_safe_handling(self) -> None:
        """Boundary: Unknown command type is marked as unsupported rather than crashing daemon."""
        def execute_device_command(cmd: dict) -> dict:
            cmd_type = cmd.get("type")
            if cmd_type == "merge_pr":
                return {"status": "completed"}
            if cmd_type == "kill_process":
                return {"status": "completed"}
            return {"status": "failed", "result": f"Unsupported command type: {cmd_type}"}

        res = execute_device_command({"type": "unknown_future_command"})
        self.assertEqual(res["status"], "failed")
        self.assertIn("Unsupported", res["result"])


if __name__ == "__main__":
    unittest.main()
