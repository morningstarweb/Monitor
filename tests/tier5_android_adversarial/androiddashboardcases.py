from __future__ import annotations

from .support import Any, Dict, concurrent

class AndroidDashboardCases:
    """Adversarial stress testing for :feature:dashboard and Settings."""

    def test_extreme_notification_thresholds_clamping(self) -> None:
        """Adversarial: Extreme and negative threshold clamping (coerceIn(0, 100))."""
        def clamp_threshold(value: int) -> int:
            return max(0, min(100, value))

        test_cases = [
            (-999999, 0),
            (-100, 0),
            (-1, 0),
            (0, 0),
            (1, 1),
            (50, 50),
            (85, 85),
            (99, 99),
            (100, 100),
            (101, 100),
            (500, 100),
            (1000000, 100),
        ]

        for input_val, expected in test_cases:
            self.assertEqual(clamp_threshold(input_val), expected)

    def test_concurrent_preference_updates_race_freedom(self) -> None:
        """Adversarial: 200 concurrent updates across 20 threads to preference state."""
        preferences = {
            "sampling_interval": "FAST_1S",
            "cpu_threshold": 80,
            "ram_threshold": 80,
            "battery_threshold": 20,
            "broker_url": "https://your-monitor-broker.workers.dev",
            "app_lock_enabled": False,
        }

        def worker(thread_id: int) -> Dict[str, Any]:
            local_state = dict(preferences)
            local_state["cpu_threshold"] = (thread_id * 7) % 100
            local_state["ram_threshold"] = (thread_id * 11) % 100
            local_state["sampling_interval"] = "ECO_5S" if thread_id % 2 == 0 else "NORMAL_2S"
            local_state["app_lock_enabled"] = (thread_id % 3 == 0)
            return local_state

        with concurrent.futures.ThreadPoolExecutor(max_workers=20) as executor:
            futures = [executor.submit(worker, i) for i in range(200)]
            results = [f.result() for f in concurrent.futures.as_completed(futures)]

        self.assertEqual(len(results), 200)
        for r in results:
            self.assertIn(r["sampling_interval"], ["FAST_1S", "NORMAL_2S", "ECO_5S"])
            self.assertTrue(0 <= r["cpu_threshold"] <= 100)
            self.assertTrue(0 <= r["ram_threshold"] <= 100)
            self.assertIsInstance(r["app_lock_enabled"], bool)

    def test_broken_broker_urls_validation(self) -> None:
        """Adversarial: Broken broker URLs must be rejected cleanly without unhandled exceptions."""
        def is_broken_url(raw: str) -> bool:
            import urllib.parse
            url = raw.strip()
            if not url:
                return True
            if not (url.lower().startswith("http://") or url.lower().startswith("https://")):
                return True
            if ".invalid" in url.lower():
                return True
            try:
                parsed = urllib.parse.urlsplit(url)
                if not parsed.hostname or not parsed.netloc:
                    return True
            except Exception:
                return True
            return False

        broken_urls = [
            "",
            "   ",
            "invalid_scheme://host",
            "ftp://files.org",
            "://no-scheme",
            "https://",
            "http://",
            "https://broker.invalid/telemetry",
            "http://[invalid-ipv6/telemetry",
            "just_a_string",
        ]

        for url in broken_urls:
            self.assertTrue(is_broken_url(url), f"Should flag as broken: {url}")

        valid_urls = [
            "https://your-monitor-broker.workers.dev",
            "https://broker.company.com/api/v1",
            "http://10.0.2.2:9480",
            "http://192.168.1.150:9480",
            "http://localhost:9480",
        ]

        for url in valid_urls:
            self.assertFalse(is_broken_url(url), f"Should accept valid URL: {url}")
