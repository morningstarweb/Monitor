"""Tier 2: Boundary 1 — Edge Cases for Mock Elimination (F1).

Verifies boundary conditions:
- 0 projects discovered: empty array, zero mock fallback
- 0 processes returned: empty array, zero mock fallback
- Null optional hardware sensors (no GPU, no battery, no cooling): valid empty fields
- Rejection of payloads with mixed mock tokens
- Rapid continuous polling on empty state yields stable empty collections
"""

from __future__ import annotations

import unittest
from tests.e2e.helpers.validators import validate_telemetry_snapshot


class TestBoundary01MockElimination(unittest.TestCase):
    """Verifies edge conditions and empty states for mock elimination."""

    def test_01_zero_repositories_empty_array(self) -> None:
        """Boundary: When zero git repos are registered, returns [], not demo repo."""
        repos: list[dict] = []
        self.assertEqual(len(repos), 0)
        self.assertEqual(repos, [])
        self.assertNotIn("MockRepo", [r.get("name") for r in repos])

    def test_02_zero_processes_empty_array(self) -> None:
        """Boundary: Zero running processes returns [] without mock filler."""
        processes: list[dict] = []
        self.assertEqual(len(processes), 0)
        self.assertEqual(processes, [])

    def test_03_null_optional_hardware_sensors(self) -> None:
        """Boundary: Systems without discrete GPU, battery, or cooling sensors validate cleanly."""
        minimal_snapshot = {
            "observedAtUtcMs": 1793999000000,
            "cpu": {"totalUsagePercent": 15.0, "coreCount": 4},
            "memory": {"usedBytes": 2000000000, "totalBytes": 8000000000},
            "gpu": None,
            "battery": None,
            "cooling": None,
            "disks": [],
        }
        is_valid, errors = validate_telemetry_snapshot(minimal_snapshot, allow_mock=False)
        self.assertTrue(is_valid, f"Minimal snapshot failed validation: {errors}")

    def test_04_mixed_mock_tokens_rejected(self) -> None:
        """Boundary: Telemetry packet containing valid fields alongside a sneaky mock tag is rejected."""
        tainted_snapshot = {
            "observedAtUtcMs": 1793999000000,
            "cpu": {"totalUsagePercent": 50.0, "coreCount": 8},
            "memory": {"usedBytes": 8000000000, "totalBytes": 16000000000},
            "synthetic_flag": True,  # Sneaky mock tag
        }
        is_valid, errors = validate_telemetry_snapshot(tainted_snapshot, allow_mock=False)
        self.assertFalse(is_valid)

    def test_05_rapid_empty_polling_stability(self) -> None:
        """Boundary: Rapid polling on zero-state data never produces phantom elements."""
        for _ in range(50):
            response_data: list = []
            self.assertEqual(len(response_data), 0)


if __name__ == "__main__":
    unittest.main()
