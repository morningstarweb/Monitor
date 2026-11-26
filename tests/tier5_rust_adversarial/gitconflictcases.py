from __future__ import annotations

from .support import List, Path, shutil, subprocess, tempfile
from .process_simulator import RustWhiteBoxProcessSimulator

class GitConflictCases:
    """Empirical verification of Git rebase/squash merge conflict recovery under dirty working trees."""

    def setUp(self):
        self.temp_dir = tempfile.mkdtemp(prefix="monitor_tier5_git_")
        self.repo_dir = Path(self.temp_dir) / "repo"
        self.repo_dir.mkdir(parents=True, exist_ok=True)

        self._run_git(["init", "-b", "main"])
        self._run_git(["config", "user.name", "Tier5 Tester"])
        self._run_git(["config", "user.email", "tester@morningstar.internal"])

        self.main_file = self.repo_dir / "app.txt"
        self.main_file.write_text("initial main line 1\ninitial main line 2\n", encoding="utf-8")
        self._run_git(["add", "app.txt"])
        self._run_git(["commit", "-m", "Initial commit"])

    def tearDown(self):
        shutil.rmtree(self.temp_dir, ignore_errors=True)

    def _run_git(self, args: List[str]) -> subprocess.CompletedProcess:
        return subprocess.run(
            ["git"] + args,
            cwd=str(self.repo_dir),
            capture_output=True,
            text=True,
            check=True,
        )

    def _run_git_allow_fail(self, args: List[str]) -> subprocess.CompletedProcess:
        return subprocess.run(
            ["git"] + args,
            cwd=str(self.repo_dir),
            capture_output=True,
            text=True,
            check=False,
        )

    def test_pr_argument_validation(self):
        """PR number 0 and unsupported merge methods must be rejected before any git execution."""
        sim = RustWhiteBoxProcessSimulator()

        # PR 0 rejected
        cmd_bad_pr = {
            "id": "c_bad_pr",
            "type": "merge_pr",
            "payload": {"repo_id": "r1", "pr_number": 0, "method": "merge"},
        }
        res1 = sim.execute_cloud_pending_command(cmd_bad_pr)
        self.assertEqual(res1["status"], "failed")
        self.assertIn("missing 'pr_number'", res1["result"].lower())

        # Unsupported methods rejected
        for bad_m in ["cherry-pick", "fast-forward", "", "merge; rm -rf /"]:
            cmd_bad_m = {
                "id": f"c_bad_{bad_m}",
                "type": "merge_pr",
                "payload": {"repo_id": "r1", "pr_number": 10, "method": bad_m},
            }
            res = sim.execute_cloud_pending_command(cmd_bad_m)
            self.assertEqual(res["status"], "failed")
            self.assertIn("invalid merge method", res["result"].lower())

        # Valid case-insensitive methods accepted
        for good_m in ["MERGE", "Squash", "REBASE", "merge", "squash", "rebase"]:
            cmd_good = {
                "id": f"c_good_{good_m}",
                "type": "merge_pr",
                "payload": {"repo_id": "r1", "pr_number": 10, "method": good_m},
            }
            res = sim.execute_cloud_pending_command(cmd_good)
            self.assertEqual(res["status"], "completed")

    def test_rebase_conflict_abort_recovery_under_dirty_working_tree(self):
        """When rebase conflicts under a dirty tree, repository returns to clean state without lock leaks."""
        # Create divergent feature branch
        self._run_git(["checkout", "-b", "feat-rebase-dirty"])
        self.main_file.write_text("feature branch edit\nline 2\n", encoding="utf-8")
        self._run_git(["commit", "-am", "Feature commit"])

        # Create divergent main commit
        self._run_git(["checkout", "main"])
        self.main_file.write_text("main branch conflicting edit\nline 2\n", encoding="utf-8")
        self._run_git(["commit", "-am", "Main commit"])

        # Leave an uncommitted dirty staged file and untracked file
        dirty_file = self.repo_dir / "dirty_staged.txt"
        dirty_file.write_text("uncommitted dirty staged line\n", encoding="utf-8")
        self._run_git(["add", "dirty_staged.txt"])

        scratch_file = self.repo_dir / "scratch.tmp"
        scratch_file.write_text("scratch work\n", encoding="utf-8")

        # Attempt git rebase -> will conflict / fail
        rebase_out = self._run_git_allow_fail(["rebase", "feat-rebase-dirty"])
        self.assertNotEqual(rebase_out.returncode, 0, "Rebase must fail with conflict")

        # Execute Monitor's strategy-aware abort:
        # 1. git rebase --abort (or --quit)
        # 2. git reset --merge
        # 3. Defense-in-depth: git reset --hard HEAD && git clean -fd
        self._run_git_allow_fail(["rebase", "--abort"])
        self._run_git_allow_fail(["rebase", "--quit"])
        self._run_git_allow_fail(["reset", "--merge"])
        self._run_git_allow_fail(["reset", "--hard", "HEAD"])
        self._run_git_allow_fail(["clean", "-fd"])

        # Assert no dangling locks or rebase state
        self.assertFalse((self.repo_dir / ".git" / "rebase-merge").exists(), "rebase-merge must not exist")
        self.assertFalse((self.repo_dir / ".git" / "rebase-apply").exists(), "rebase-apply must not exist")
        self.assertFalse((self.repo_dir / ".git" / "MERGE_HEAD").exists(), "MERGE_HEAD must not exist")
        self.assertFalse((self.repo_dir / ".git" / "index.lock").exists(), "index.lock must not exist")

        # Assert working tree is completely clean
        status_proc = self._run_git(["status", "--porcelain"])
        self.assertEqual(status_proc.stdout.strip(), "", "Working tree must be clean after abort")

        # Subsequent git operation works
        subsequent = self._run_git_allow_fail(["commit", "--allow-empty", "-m", "Post abort commit"])
        self.assertEqual(subsequent.returncode, 0, "Subsequent commit must succeed cleanly")

    def test_squash_conflict_clean_state_restoration(self):
        """Squash conflict must abort cleanly without leaving staged uncommitted changes."""
        self._run_git(["checkout", "-b", "feat-squash-conflict"])
        self.main_file.write_text("squash conflict line\n", encoding="utf-8")
        self._run_git(["commit", "-am", "Squash commit"])

        self._run_git(["checkout", "main"])
        self.main_file.write_text("main conflicting line\n", encoding="utf-8")
        self._run_git(["commit", "-am", "Main commit"])

        squash_attempt = self._run_git_allow_fail(["merge", "--squash", "feat-squash-conflict"])
        self.assertNotEqual(squash_attempt.returncode, 0, "Squash merge must fail on conflict")

        # Monitor squash abort: reset --merge, fallback reset --hard HEAD
        self._run_git_allow_fail(["reset", "--merge"])
        self._run_git_allow_fail(["reset", "--hard", "HEAD"])
        self._run_git_allow_fail(["clean", "-fd"])

        status_proc = self._run_git(["status", "--porcelain"])
        self.assertEqual(status_proc.stdout.strip(), "", "Status after squash abort must be empty")
        self.assertIn("main conflicting line", self.main_file.read_text(encoding="utf-8"))

    def test_binary_conflict_clean_state_restoration(self):
        """Binary file conflict across branches must abort and preserve original binary content."""
        bin_file = self.repo_dir / "firmware.bin"
        base_bytes = bytes([i % 256 for i in range(256)])
        bin_file.write_bytes(base_bytes)
        self._run_git(["add", "firmware.bin"])
        self._run_git(["commit", "-m", "Add binary file"])

        # Feature modifies binary
        self._run_git(["checkout", "-b", "feat-bin"])
        feat_bytes = bytes([(i + 128) % 256 for i in range(256)])
        bin_file.write_bytes(feat_bytes)
        self._run_git(["commit", "-am", "Feature binary edit"])

        # Main modifies binary differently
        self._run_git(["checkout", "main"])
        main_bytes = bytes([(i + 64) % 256 for i in range(256)])
        bin_file.write_bytes(main_bytes)
        self._run_git(["commit", "-am", "Main binary edit"])

        merge_res = self._run_git_allow_fail(["merge", "feat-bin"])
        self.assertNotEqual(merge_res.returncode, 0, "Binary merge must fail with conflict")

        # Abort
        self._run_git_allow_fail(["merge", "--abort"])
        self._run_git_allow_fail(["reset", "--hard", "HEAD"])

        self.assertEqual(bin_file.read_bytes(), main_bytes, "Binary content must equal main HEAD")
