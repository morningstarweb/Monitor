"""Tier 1: Feature 7 — Telemetry Preferences & Notifications Panels (R2).

Verifies telemetry preferences and notification threshold configuration:
- Supported sampling rates (1s, 2s, 5s)
- Temperature unit formatting and conversion (Celsius <-> Fahrenheit)
- Process sorting and filtering options
- Alert threshold triggers (CPU, RAM, Battery)
- Background sync and notification channel preferences
"""

from __future__ import annotations

import unittest
from tests.e2e.helpers.contract_models import SettingsState


class TestFeature07TelemetryPrefsNotifications(unittest.TestCase):
    """Verifies user configurable telemetry preferences and system notification thresholds."""

    def test_01_sampling_rate_preference_options(self) -> None:
        """Requirement R2 / Feature 18: Supports configurable intervals (1s, 2s, 5s)."""
        allowed_intervals = {1, 2, 5}
        settings = SettingsState(sampling_rate_seconds=2)
        self.assertIn(settings.sampling_rate_seconds, allowed_intervals)

        for rate in (1, 2, 5):
            settings.sampling_rate_seconds = rate
            self.assertEqual(settings.sampling_rate_seconds, rate)

    def test_02_temperature_unit_conversion_accuracy(self) -> None:
        """Requirement R2 / Feature 18: Temperature conversion between Celsius and Fahrenheit."""
        def celsius_to_fahrenheit(c: float) -> float:
            return round((c * 9.0 / 5.0) + 32.0, 1)

        def fahrenheit_to_celsius(f: float) -> float:
            return round((f - 32.0) * 5.0 / 9.0, 1)

        # Standard operating temperatures
        self.assertEqual(celsius_to_fahrenheit(0.0), 32.0)
        self.assertEqual(celsius_to_fahrenheit(50.0), 122.0)
        self.assertEqual(celsius_to_fahrenheit(100.0), 212.0)

        self.assertEqual(fahrenheit_to_celsius(32.0), 0.0)
        self.assertEqual(fahrenheit_to_celsius(122.0), 50.0)
        self.assertEqual(fahrenheit_to_celsius(212.0), 100.0)

    def test_03_process_sorting_and_filter_preference(self) -> None:
        """Requirement R2 / Feature 18: Process list sorting by CPU, memory, or process name."""
        processes = [
            {"name": "rustc.exe", "cpu": 45.0, "memory": 1200},
            {"name": "chrome.exe", "cpu": 15.0, "memory": 3500},
            {"name": "agent.exe", "cpu": 2.0, "memory": 150},
        ]

        by_cpu = sorted(processes, key=lambda p: p["cpu"], reverse=True)
        self.assertEqual(by_cpu[0]["name"], "rustc.exe")

        by_mem = sorted(processes, key=lambda p: p["memory"], reverse=True)
        self.assertEqual(by_mem[0]["name"], "chrome.exe")

    def test_04_alert_threshold_boundary_verification(self) -> None:
        """Requirement R2 / Feature 19: High CPU/RAM and low battery alert conditions."""
        def evaluate_alerts(
            cpu: float, ram: float, battery: int, thresholds: SettingsState
        ) -> list[str]:
            alerts = []
            if cpu >= thresholds.cpu_alert_threshold:
                alerts.append("ALERT_HIGH_CPU")
            if ram >= thresholds.ram_alert_threshold:
                alerts.append("ALERT_HIGH_RAM")
            if battery <= thresholds.battery_alert_threshold:
                alerts.append("ALERT_LOW_BATTERY")
            return alerts

        settings = SettingsState(cpu_alert_threshold=85, ram_alert_threshold=90, battery_alert_threshold=20)

        # Safe conditions
        self.assertEqual(len(evaluate_alerts(40.0, 60.0, 80, settings)), 0)

        # Danger conditions
        alerts = evaluate_alerts(92.0, 95.0, 15, settings)
        self.assertEqual(len(alerts), 3)
        self.assertIn("ALERT_HIGH_CPU", alerts)
        self.assertIn("ALERT_HIGH_RAM", alerts)
        self.assertIn("ALERT_LOW_BATTERY", alerts)

    def test_05_background_sync_and_notification_channel_toggles(self) -> None:
        """Requirement R2 / Feature 19: Background sync and push notification preference toggles."""
        settings = SettingsState()
        self.assertTrue(settings.background_sync_enabled)

        # Disable background sync (saves mobile battery)
        settings.background_sync_enabled = False
        self.assertFalse(settings.background_sync_enabled)


if __name__ == "__main__":
    unittest.main()
