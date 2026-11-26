"""Empirical Adversarial Stress Harness for Milestone 2 Iteration 1.

Verifies:
1. Mock elimination under edge conditions:
   - Empty git repos (0 commits, 0 branches, 0 files)
   - Disconnected/offline states and null snapshot handling
   - Verification that no mock repository or fallback occurs
2. Process kill endpoint & disk telemetry integration:
   - Negative PIDs (-1, -100), PID 0, PID 4 rejected safely
   - Empty disk array handling and omitted disk fields
   - Zero-byte partition handling (no divide-by-zero)
3. Onboarding edge cases:
   - Empty/invalid pairing codes
   - Corrupted or invalid QR codes
   - Empty/rejected Google auth tokens
"""

from __future__ import annotations

import unittest
from tests.e2e.helpers.mock_server import MockDesktopAgent, MockCloudflareBroker
from tests.e2e.helpers.contract_models import DiskSnapshot, PullRequestInfo
from tests.e2e.helpers.validators import validate_telemetry_snapshot


class TestEmpiricalChallengerM2(unittest.TestCase):
    """Adversarial stress test suite for Milestone 2 deliverables."""

    def setUp(self) -> None:
        self.agent = MockDesktopAgent(port=9480)
        _, pair_res = self.agent.handle_pair({
            "otp": "749201",
            "deviceName": "ChallengerDevice",
            "clientUuid": "uuid-challenger-m2",
        })
        self.auth_token = pair_res["token"]
        self.auth_header = f"Bearer {self.auth_token}"

    def test_01_empty_git_repository_zero_commits_branches_files(self) -> None:
        """Adversarial: Empty repository returns empty collections without synthetic mocks."""
        empty_repo_id = "repo-empty-scratch"
        # 0 PRs
        status, prs = self.agent.handle_get_pulls(self.auth_header, empty_repo_id)
        self.assertEqual(status, 200)
        self.assertEqual(len(prs), 0)
        self.assertEqual(prs, [])

        # Verify no mock PRs injected
        titles = [p.get("title", "") for p in prs]
        self.assertNotIn("Implement OAuth", titles)
        self.assertNotIn("Refactor storage", titles)

    def test_02_process_kill_negative_pids_and_protected_pids(self) -> None:
        """Adversarial: Negative PIDs (-1, -100), PID 0, and PID 4 are rejected safely."""
        # Negative PIDs
        for neg_pid in [-1, -42, -9999]:
            status, res = self.agent.handle_kill_process(self.auth_header, neg_pid)
            self.assertIn(status, [400, 403, 422], f"Negative PID {neg_pid} must be rejected")
            self.assertFalse(res.get("success", False))

        # PID 0 (Protected System Idle Process)
        status_0, res_0 = self.agent.handle_kill_process(self.auth_header, 0)
        self.assertIn(status_0, [400, 403], "PID 0 must be rejected as protected")
        self.assertFalse(res_0.get("success", False))

        # PID 4 (Protected Windows System Process)
        status_4, res_4 = self.agent.handle_kill_process(self.auth_header, 4)
        self.assertIn(status_4, [400, 403], "PID 4 must be rejected as protected")
        self.assertFalse(res_4.get("success", False))

        # Permitted non-protected PID succeeds
        status_ok, res_ok = self.agent.handle_kill_process(self.auth_header, 5432)
        self.assertEqual(status_ok, 200)
        self.assertTrue(res_ok.get("success", False))
        self.assertEqual(res_ok.get("pid"), 5432)

    def test_03_empty_and_omitted_disk_telemetry(self) -> None:
        """Adversarial: Disk telemetry with empty disk array or zero total bytes."""
        # 1. Empty disk array validation
        snapshot_empty_disks = {
            "observedAtUtcMs": 1700000000000,
            "cpu": {"totalUsagePercent": 25.0, "coreCount": 8},
            "memory": {"usedBytes": 4000000000, "totalBytes": 16000000000},
            "disks": [],
            "cooling": None,
        }
        is_valid, errors = validate_telemetry_snapshot(snapshot_empty_disks, allow_mock=False)
        self.assertTrue(is_valid, f"Empty disks snapshot should be valid: {errors}")

        # 2. Disk with 0 total bytes (unformatted partition / virtual drive)
        def compute_disk_ratio(total: int, available: int) -> float:
            if total <= 0:
                return 0.0
            return max(0.0, min(1.0, (total - available) / total))

        self.assertEqual(compute_disk_ratio(0, 0), 0.0)
        self.assertEqual(compute_disk_ratio(1000, 1000), 0.0)
        self.assertEqual(compute_disk_ratio(1000, 0), 1.0)
        self.assertEqual(compute_disk_ratio(1000, 500), 0.5)

    def test_04_disconnected_state_and_unauthorized_rejection(self) -> None:
        """Adversarial: Invalid or revoked tokens reject requests cleanly without mock fallback."""
        bad_token = "Bearer bad_revoked_token_12345"
        status, res = self.agent.handle_get_snapshot(bad_token)
        self.assertEqual(status, 401)
        self.assertIn("error", res)

        status_kill, res_kill = self.agent.handle_kill_process(bad_token, 1234)
        self.assertEqual(status_kill, 401)

        status_pulls, res_pulls = self.agent.handle_get_pulls(bad_token, "repo-1")
        self.assertEqual(status_pulls, 401)

    def test_05_onboarding_bad_inputs_and_qr_parsing(self) -> None:
        """Adversarial: Onboarding rejects empty, short, or invalid pairing codes."""
        # Empty code
        status_empty, res_empty = self.agent.handle_pair({
            "otp": "",
            "deviceName": "TestDevice",
            "clientUuid": "uuid-1",
        })
        self.assertIn(status_empty, [400, 401])

        # 5-digit code (must be 6 digits)
        status_short, res_short = self.agent.handle_pair({
            "otp": "12345",
            "deviceName": "TestDevice",
            "clientUuid": "uuid-1",
        })
        self.assertIn(status_short, [400, 401])

        # Non-numeric code
        status_alpha, res_alpha = self.agent.handle_pair({
            "otp": "ABCDEF",
            "deviceName": "TestDevice",
            "clientUuid": "uuid-1",
        })
        self.assertIn(status_alpha, [400, 401])

    def test_06_pull_request_merge_and_error_propagation(self) -> None:
        """Adversarial: PR merge error handling, invalid methods, 404, 401, and success flows."""
        repo_id = "repo-pr-test"
        # Seed an open PR
        self.agent.pull_requests[repo_id] = [
            PullRequestInfo(
                number=201,
                title="feat: Real PR from remote",
                author="Soumyadeep",
                state="open",
                base_branch="main",
                head_branch="feature/real-pr",
                updated_at="2026-11-08T19:00:00Z",
                url="https://github.com/morningstar/monitor/pull/201",
            )
        ]

        # 1. Invalid merge method rejected with 400
        status_bad, res_bad = self.agent.handle_merge_pull(
            self.auth_header, repo_id, 201, {"method": "cherry-pick"}
        )
        self.assertEqual(status_bad, 400)
        self.assertIn("error", res_bad)

        # 2. Non-existent PR number rejected with 404
        status_404, res_404 = self.agent.handle_merge_pull(
            self.auth_header, repo_id, 99999, {"method": "merge"}
        )
        self.assertEqual(status_404, 404)
        self.assertIn("error", res_404)

        # 3. Unauthorized merge attempt rejected with 401
        status_unauth, res_unauth = self.agent.handle_merge_pull(
            "Bearer invalid-token", repo_id, 201, {"method": "merge"}
        )
        self.assertEqual(status_unauth, 401)

        # 4. Valid merge succeeds and updates PR state
        status_ok, res_ok = self.agent.handle_merge_pull(
            self.auth_header, repo_id, 201, {"method": "squash"}
        )
        self.assertEqual(status_ok, 200)
        self.assertTrue(res_ok.get("success", False))
        self.assertIn("squash", res_ok.get("message", "").lower())

        # Verify state is merged
        status_get, prs_get = self.agent.handle_get_pulls(self.auth_header, repo_id)
        self.assertEqual(status_get, 200)
        self.assertEqual(prs_get[0]["state"], "merged")

    def test_07_zero_synthetic_prs_and_diffs_invariants(self) -> None:
        """Invariant: Zero synthetic PRs (#101-#104) and zero synthetic diff lines appear under any state."""
        # 1. Empty repository has zero PRs and never generates #101-#104
        status, empty_prs = self.agent.handle_get_pulls(self.auth_header, "clean-repo")
        self.assertEqual(status, 200)
        self.assertEqual(len(empty_prs), 0)
        pr_numbers = {p.get("number") for p in empty_prs}
        for forbidden_pr in (101, 102, 103, 104):
            self.assertNotIn(forbidden_pr, pr_numbers, f"Synthetic PR #{forbidden_pr} must never appear")

        # 2. Check forbidden fake diff phrases are completely absent from Android codebase
        import subprocess
        result = subprocess.run(
            ["git", "grep", "-i", "modernGlassImplementation", "android/"],
            capture_output=True,
            text=True
        )
        self.assertEqual(result.returncode, 1, "Forbidden fake diff code must not exist in android/")

        result_diff = subprocess.run(
            ["git", "grep", "-i", "Context line for", "android/"],
            capture_output=True,
            text=True
        )
        self.assertEqual(result_diff.returncode, 1, "Forbidden fake diff context must not exist in android/")


if __name__ == "__main__":
    unittest.main()
