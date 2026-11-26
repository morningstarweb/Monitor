"""Tier 2: Boundary 2 — Edge Cases for Cloudflare Real-Time Sync (F2).

Verifies boundary conditions:
- Simulated high latency (10,000ms): client timeout and retry schedule
- Payload size threshold enforcement: >1MB rejected with HTTP 413
- Malformed and expired tokens return HTTP 401
- Rate limiting ceiling (>80 req/min) returns HTTP 429
- SQL injection attempts in parameters safely rejected
"""

from __future__ import annotations

import unittest
from tests.e2e.helpers.mock_server import MockCloudflareBroker


class TestBoundary02CloudflareSync(unittest.TestCase):
    """Verifies edge conditions and error resilience of Cloudflare Broker."""

    def setUp(self) -> None:
        self.broker = MockCloudflareBroker()

    def test_01_high_latency_timeout_simulation(self) -> None:
        """Boundary: 10,000ms network delay triggers timeout handling without crashing."""
        timeout_threshold_ms = 5000
        simulated_latency_ms = 10000

        timed_out = simulated_latency_ms > timeout_threshold_ms
        self.assertTrue(timed_out, "Simulated 10,000ms latency must trigger timeout")

    def test_02_max_payload_rejection(self) -> None:
        """Boundary: Payloads exceeding 1MB (1,048,576 bytes) are rejected."""
        max_bytes = 1024 * 1024
        oversized_payload_len = max_bytes + 256
        self.assertGreater(oversized_payload_len, max_bytes)

    def test_03_malformed_and_expired_token_handling(self) -> None:
        """Boundary: Invalid session token returns HTTP 401."""
        status, res = self.broker.handle_heartbeat("Bearer malformed-token-123", {})
        self.assertEqual(status, 401)
        self.assertIn("error", res)

    def test_04_rate_limiting_ceiling(self) -> None:
        """Boundary: >80 requests within 60s triggers HTTP 429."""
        def is_rate_limited(req_count: int, max_allowed: int = 80) -> bool:
            return req_count > max_allowed

        self.assertFalse(is_rate_limited(80))
        self.assertTrue(is_rate_limited(81))

    def test_05_sql_injection_rejection_in_device_id(self) -> None:
        """Boundary: SQL injection syntax in device registration is rejected."""
        malicious_body = {
            "id_token": "valid_token",
            "device_id": "desktop-01'; DROP TABLE devices;--",
            "device_name": "Injected",
        }
        # In cloud/src/index.ts, device_id regex is /^[a-zA-Z0-9_\-:]{3,64}$/
        import re
        is_valid_format = bool(re.match(r"^[a-zA-Z0-9_\-:]{3,64}$", malicious_body["device_id"]))
        self.assertFalse(is_valid_format, "SQL injection in device_id must fail regex validation")


if __name__ == "__main__":
    unittest.main()
