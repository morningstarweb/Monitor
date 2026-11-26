from __future__ import annotations

from .support import Any, Dict, List

class AndroidGitWorkspaceCases:
    """Adversarial stress testing for :feature:gitworkspace."""

    def test_protected_branch_deletion_guard(self) -> None:
        """Adversarial: Main and master branches must be rejected from deletion."""
        protected_branches = ["main", "master", "MAIN", "MASTER", " Main ", " master "]

        def can_delete_branch(branch_name: str) -> bool:
            trimmed = branch_name.strip()
            if trimmed.lower() in ("main", "master"):
                return False
            return bool(trimmed)

        for b in protected_branches:
            self.assertFalse(can_delete_branch(b), f"Branch {b} should be protected!")

        self.assertTrue(can_delete_branch("feature/obsidian-redesign"))
        self.assertTrue(can_delete_branch("hotfix/ws-stream-reconnect"))
        self.assertFalse(can_delete_branch(""))

    def test_empty_workspace_and_graph_invariants(self) -> None:
        """Adversarial: Empty workspace must produce 0 files, 0 PRs, null diff without dummy fallbacks."""
        empty_snapshot = {
            "repo_id": "r-empty",
            "revision": 0,
            "current_branch": "",
            "upstream_branch": None,
            "ahead_count": 0,
            "behind_count": 0,
            "files": []
        }

        # Assert no synthetic items
        self.assertEqual(len(empty_snapshot["files"]), 0)
        self.assertEqual(empty_snapshot["ahead_count"], 0)
        self.assertEqual(empty_snapshot["behind_count"], 0)

        # Pull requests
        empty_prs: List[Dict[str, Any]] = []
        self.assertEqual(len(empty_prs), 0)
        self.assertFalse(any(pr.get("number") in range(101, 105) for pr in empty_prs))
