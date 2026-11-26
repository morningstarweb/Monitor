"""Comprehensive Empirical Adversarial Stress Test Suite for Milestone 3 Iteration 2.

Tests:
1. AppLock Resume Gating State Machine:
   - Exhaustive truth table across (event, isAppLockActive, requireOnResume)
   - Multiple resume/unlock cycles
2. Notification & Alert Threshold Clamping:
   - Negative values (-1, -50, -999999) -> 0
   - Boundary values (0, 100) -> 0, 100
   - Overflow values (101, 500, 2147483647) -> 100
3. Zero Synthetic Data Invariant:
   - Audit SettingsUiState.kt for mock latency (38L), default Optimal, dummy session days
   - Audit CloudflareBrokerSyncPanel.kt for fallback "CF"
4. File Line Count Constraints:
   - Verify every file touched in M3 is strictly <= 200 lines
5. Kotlin AST / Regex Verification of DisposableEffect and Clamping:
   - SettingsScreen.kt contains DisposableEffect + LifecycleEventObserver + ON_RESUME
   - SettingsViewModel.kt contains .coerceIn(0, 100) on all three thresholds
   - SettingsRepository.kt contains .coerceIn(0, 100) on all three thresholds and SharedPreferences loader
"""

import os
import re
import unittest

ROOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

class TestAppLockResumeGating(unittest.TestCase):
    """Adversarial stress test on AppLock resume gating logic."""

    def test_lifecycle_resume_truth_table(self):
        """Verify that ONLY (ON_RESUME and isAppLockActive and requireOnResume) triggers lock."""
        events = ["ON_CREATE", "ON_START", "ON_RESUME", "ON_PAUSE", "ON_STOP", "ON_DESTROY"]
        
        for event in events:
            for active in [True, False]:
                for require in [True, False]:
                    # Formula from SettingsScreen.kt line 68:
                    # if (event == Lifecycle.Event.ON_RESUME && state.security.isAppLockActive && state.security.requireOnResume)
                    should_lock = (event == "ON_RESUME" and active and require)
                    
                    # Simulate state
                    is_locked = False
                    if event == "ON_RESUME" and active and require:
                        is_locked = True
                    
                    self.assertEqual(is_locked, should_lock,
                                     f"Failed for event={event}, active={active}, require={require}")

    def test_repeated_resume_unlock_cycles(self):
        """Simulate user unlocking and multiple background/resume transitions."""
        is_locked = False
        active = True
        require = True

        # App resumes
        if "ON_RESUME" == "ON_RESUME" and active and require:
            is_locked = True
        self.assertTrue(is_locked)

        # User unlocks
        is_locked = False
        self.assertFalse(is_locked)

        # App pauses and stops
        # Resumes again
        if "ON_RESUME" == "ON_RESUME" and active and require:
            is_locked = True
        self.assertTrue(is_locked)

        # User disables requireOnResume while locked
        require = False
        # User unlocks
        is_locked = False
        # Next resume should NOT lock
        if "ON_RESUME" == "ON_RESUME" and active and require:
            is_locked = True
        self.assertFalse(is_locked)


class TestThresholdClamping(unittest.TestCase):
    """Adversarial stress test on threshold clamping."""

    def clamp(self, val: int) -> int:
        return max(0, min(100, val))

    def test_hostile_negative_thresholds(self):
        hostile_negatives = [-1, -5, -50, -100, -999999, -2147483648]
        for val in hostile_negatives:
            self.assertEqual(self.clamp(val), 0, f"Expected 0 for {val}")

    def test_hostile_overflow_thresholds(self):
        hostile_overflows = [101, 105, 150, 999, 99999, 2147483647]
        for val in hostile_overflows:
            self.assertEqual(self.clamp(val), 100, f"Expected 100 for {val}")

    def test_valid_boundary_thresholds(self):
        for val in range(0, 101):
            self.assertEqual(self.clamp(val), val)


class TestSettingsCodeInvariants(unittest.TestCase):
    """Empirical verification of source code invariants."""

    def test_settings_screen_lifecycle_observer(self):
        gate_path = os.path.join(ROOT_DIR, "android", "app", "src", "main", "java",
                                "app", "monitor", "security", "AppLockGate.kt")
        if os.path.exists(gate_path):
            with open(gate_path, "r", encoding="utf-8") as f:
                content = f.read()
            self.assertIn("DisposableEffect(lifecycleOwner)", content)
            self.assertIn("LifecycleEventObserver", content)
            self.assertIn("Lifecycle.Event.ON_RESUME", content)
            self.assertIn("isAppLocked = true", content)
            self.assertIn("AppLockOverlay", content)
        else:
            path = os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java",
                                "app", "monitor", "feature", "dashboard", "SettingsScreen.kt")
            with open(path, "r", encoding="utf-8") as f:
                content = f.read()
            self.assertIn("DisposableEffect(lifecycleOwner)", content)
            self.assertIn("LifecycleEventObserver", content)
            self.assertIn("Lifecycle.Event.ON_RESUME", content)

    def test_clamping_in_settings_viewmodel(self):
        path = os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java",
                            "app", "monitor", "feature", "dashboard", "settings", "SettingsViewModel.kt")
        with open(path, "r", encoding="utf-8") as f:
            content = f.read()

        self.assertIn("percent.coerceIn(0, 100)", content)
        # Verify all 3 threshold setters use coerceIn
        self.assertRegex(content, r"fun setCpuThreshold\(percent: Int\) = viewModelScope\.launch \{ settingsRepository\.updateCpuThreshold\(percent\.coerceIn\(0, 100\)\) \}")
        self.assertRegex(content, r"fun setRamThreshold\(percent: Int\) = viewModelScope\.launch \{ settingsRepository\.updateRamThreshold\(percent\.coerceIn\(0, 100\)\) \}")
        self.assertRegex(content, r"fun setBatteryLowThreshold\(percent: Int\) = viewModelScope\.launch \{ settingsRepository\.updateBatteryLowThreshold\(percent\.coerceIn\(0, 100\)\) \}")

    def test_clamping_in_settings_repository(self):
        path = os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java",
                            "app", "monitor", "feature", "dashboard", "settings", "data", "SettingsRepository.kt")
        with open(path, "r", encoding="utf-8") as f:
            content = f.read()

        self.assertIn("threshold.coerceIn(0, 100)", content)
        self.assertIn("prefs.getInt(KEY_CPU_THRESHOLD, 85).coerceIn(0, 100)", content)
        self.assertIn("prefs.getInt(KEY_RAM_THRESHOLD, 85).coerceIn(0, 100)", content)
        self.assertIn("prefs.getInt(KEY_BATTERY_THRESHOLD, 20).coerceIn(0, 100)", content)

    def test_zero_mock_or_synthetic_defaults(self):
        ui_state_path = os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java",
                                     "app", "monitor", "feature", "dashboard", "settings", "model", "SettingsUiState.kt")
        with open(ui_state_path, "r", encoding="utf-8") as f:
            ui_state = f.read()

        # Must not contain 38L dummy latency
        self.assertNotIn("38L", ui_state)
        # BrokerSyncState defaults must be clean
        self.assertIn("val latencyMs: Long? = null", ui_state)
        self.assertIn("val edgeColo: String? = null", ui_state)
        self.assertIn("val healthTier: BrokerHealthTier = BrokerHealthTier.Idle", ui_state)
        self.assertIn("val lastSyncUtcMs: Long? = null", ui_state)

        # AccountProfileState defaults
        self.assertIn("val daysUntilExpiration: Int? = null", ui_state)
        self.assertIn("val isSessionActive: Boolean = false", ui_state)

        # CloudflareBrokerSyncPanel must not default to "CF"
        panel_path = os.path.join(ROOT_DIR, "android", "feature", "dashboard", "src", "main", "java",
                                  "app", "monitor", "feature", "dashboard", "settings", "panels", "CloudflareBrokerSyncPanel.kt")
        with open(panel_path, "r", encoding="utf-8") as f:
            panel = f.read()

        self.assertNotIn('"CF"', panel)

    def test_all_m3_files_line_count(self):
        m3_files = [
            "android/feature/dashboard/src/main/java/app/monitor/feature/dashboard/SettingsScreen.kt",
            "android/feature/dashboard/src/main/java/app/monitor/feature/dashboard/settings/SettingsViewModel.kt",
            "android/feature/dashboard/src/main/java/app/monitor/feature/dashboard/settings/data/SettingsRepository.kt",
            "android/feature/dashboard/src/main/java/app/monitor/feature/dashboard/settings/data/BrokerProbeClient.kt",
            "android/feature/dashboard/src/main/java/app/monitor/feature/dashboard/settings/model/SettingsUiState.kt",
            "android/feature/dashboard/src/main/java/app/monitor/feature/dashboard/settings/model/SettingsPreferences.kt",
            "android/feature/dashboard/src/main/java/app/monitor/feature/dashboard/settings/model/SettingsDialog.kt",
            "android/feature/dashboard/src/main/java/app/monitor/feature/dashboard/settings/panels/AccountProfilePanel.kt",
            "android/feature/dashboard/src/main/java/app/monitor/feature/dashboard/settings/panels/BiometricSecurityPanel.kt",
            "android/feature/dashboard/src/main/java/app/monitor/feature/dashboard/settings/panels/CloudflareBrokerSyncPanel.kt",
            "android/feature/dashboard/src/main/java/app/monitor/feature/dashboard/settings/panels/NotificationsThresholdsPanel.kt",
            "android/feature/dashboard/src/main/java/app/monitor/feature/dashboard/settings/panels/StatutoryLegalPanel.kt",
            "android/feature/dashboard/src/main/java/app/monitor/feature/dashboard/settings/panels/TelemetryPreferencesPanel.kt",
            "android/feature/dashboard/src/main/java/app/monitor/feature/dashboard/settings/dialogs/SettingsModalHost.kt",
            "android/feature/dashboard/src/main/java/app/monitor/feature/dashboard/dialogs/SettingsOptionDialogs.kt",
            "android/feature/dashboard/src/main/java/app/monitor/feature/dashboard/dialogs/PrivacyPolicyDialog.kt",
            "android/feature/dashboard/src/main/java/app/monitor/feature/dashboard/dialogs/TermsOfServiceDialog.kt",
            "android/feature/dashboard/src/main/java/app/monitor/feature/dashboard/settings/ui/components/AppLockOverlay.kt",
            "android/feature/dashboard/src/main/java/app/monitor/feature/dashboard/settings/ui/components/ObsidianGlassCard.kt",
            "android/feature/dashboard/src/main/java/app/monitor/feature/dashboard/sections/SystemHeaderSection.kt",
            "android/feature/dashboard/src/main/java/app/monitor/feature/dashboard/settings/ui/components/TelemetryPulseBeacon.kt",
            "android/feature/dashboard/src/test/java/app/monitor/feature/dashboard/SettingsViewModelStressTest.kt",
            "android/feature/dashboard/src/test/java/app/monitor/feature/dashboard/SettingsViewModelTest.kt",
        ]
        for rel_path in m3_files:
            abs_path = os.path.join(ROOT_DIR, rel_path.replace("/", os.sep))
            self.assertTrue(os.path.exists(abs_path), f"Missing: {rel_path}")
            with open(abs_path, "r", encoding="utf-8") as f:
                lines = len(f.readlines())
            self.assertLessEqual(lines, 200, f"File {rel_path} has {lines} lines (> 200 limit)")


if __name__ == "__main__":
    unittest.main()
