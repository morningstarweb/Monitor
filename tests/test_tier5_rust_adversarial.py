"""Tier 5 adversarial verification suite for the Rust desktop and cloud broker."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

TEST_ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = TEST_ROOT.parent
for import_root in (TEST_ROOT, PROJECT_ROOT):
    if str(import_root) not in sys.path:
        sys.path.insert(0, str(import_root))

from tier5_rust_adversarial.cloudflareedgecases import CloudflareEdgeCases
from tier5_rust_adversarial.cloudflare_simulator import CloudflareWorkerSimulator
from tier5_rust_adversarial.gitconflictcases import GitConflictCases
from tier5_rust_adversarial.process_simulator import RustWhiteBoxProcessSimulator
from tier5_rust_adversarial.processkillsecuritycases import ProcessKillSecurityCases
from tier5_rust_adversarial.traypolicycases import TrayPolicyCases

PROJECT_ROOT = TEST_ROOT.parent


class TestTier5ProcessKillSecurityBoundaries(ProcessKillSecurityCases, unittest.TestCase):
    pass


class TestTier5GitDirtyAbortAdversarial(GitConflictCases, unittest.TestCase):
    pass


class TestTier5CloudflareEdgeSyncAdversarial(CloudflareEdgeCases, unittest.TestCase):
    pass


class TestTier5DesktopTrayPolicyTamperingAndHardening(TrayPolicyCases, unittest.TestCase):
    pass


__all__ = ["PROJECT_ROOT", "RustWhiteBoxProcessSimulator", "CloudflareWorkerSimulator", "TestTier5ProcessKillSecurityBoundaries", "TestTier5GitDirtyAbortAdversarial", "TestTier5CloudflareEdgeSyncAdversarial", "TestTier5DesktopTrayPolicyTamperingAndHardening", "run_tier5_adversarial_suite"]

def run_tier5_adversarial_suite(verbosity: int = 2) -> bool:
    """Entry point for standalone execution."""
    print("=" * 80)
    print(" MONITOR TIER 5 INDEPENDENT ADVERSARIAL STRESS SUITE")
    print(" White-Box Hardening: Rust Desktop Daemon & Cloudflare Edge Sync")
    print("=" * 80)

    loader = unittest.TestLoader()
    suite = loader.loadTestsFromModule(sys.modules[__name__])
    runner = unittest.TextTestRunner(verbosity=verbosity)
    result = runner.run(suite)

    print("=" * 80)
    print(f"TIER 5 EXECUTION SUMMARY: {result.testsRun} tests executed")
    print(f"  Passed : {result.testsRun - len(result.failures) - len(result.errors)}")
    print(f"  Failed : {len(result.failures)}")
    print(f"  Errors : {len(result.errors)}")
    print("=" * 80)

    return result.wasSuccessful()


if __name__ == "__main__":
    success = run_tier5_adversarial_suite()
    sys.exit(0 if success else 1)
