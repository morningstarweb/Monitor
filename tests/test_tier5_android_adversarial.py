"""Tier 5 adversarial hardening tests for Android client modules."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

TESTS_DIR = Path(__file__).resolve().parent
if str(TESTS_DIR) not in sys.path:
    sys.path.insert(0, str(TESTS_DIR))

from tier5_android_adversarial.androiddashboardcases import AndroidDashboardCases
from tier5_android_adversarial.androiddesignauditcases import AndroidDesignAuditCases
from tier5_android_adversarial.androidgitworkspacecases import AndroidGitWorkspaceCases
from tier5_android_adversarial.androidnetworkcases import AndroidNetworkCases
from tier5_android_adversarial.androidonboardingcases import AndroidOnboardingCases
from tier5_android_adversarial.support import ANDROID_ROOT, PROJECT_ROOT


class TestTier5AndroidNetworkAdversarial(AndroidNetworkCases, unittest.TestCase):
    pass


class TestTier5AndroidDashboardAdversarial(AndroidDashboardCases, unittest.TestCase):
    pass


class TestTier5AndroidGitWorkspaceAdversarial(AndroidGitWorkspaceCases, unittest.TestCase):
    pass


class TestTier5AndroidOnboardingBiometricsAdversarial(AndroidOnboardingCases, unittest.TestCase):
    pass


class TestTier5AndroidZeroMockAndDarkObsidianAudit(AndroidDesignAuditCases, unittest.TestCase):
    pass


__all__ = ["PROJECT_ROOT", "ANDROID_ROOT", "TestTier5AndroidNetworkAdversarial", "TestTier5AndroidDashboardAdversarial", "TestTier5AndroidGitWorkspaceAdversarial", "TestTier5AndroidOnboardingBiometricsAdversarial", "TestTier5AndroidZeroMockAndDarkObsidianAudit"]

if __name__ == "__main__":
    unittest.main()
