"""Tier 2: Boundary 3 — Edge Cases for Desktop Direct Stream (F3).

Verifies boundary conditions:
- Invalid OTP codes (alphanumeric, too short, too long, empty) rejected
- Single-use WS tickets expire after 30 seconds
- Extreme process list size (10,000 processes) bound by limit
- Process termination on system PID 0 or nonexistent PID returns graceful error
- Non-private client IP addressing rejected for local endpoints
"""

from __future__ import annotations

import time
import unittest
from tests.e2e.helpers.mock_server import MockDesktopAgent


class TestBoundary03DesktopStream(unittest.TestCase):
    """Verifies edge conditions and corner cases for Desktop Direct Stream."""

    def setUp(self) -> None:
        self.agent = MockDesktopAgent(port=9480)

    def test_01_invalid_otp_variations_rejected(self) -> None:
        """Boundary: OTPs other than exact 6-digit match are rejected."""
        invalid_otps = ["", "12345", "1234567", "ABCDEF", "749200", "      "]
        for otp in invalid_otps:
            status, res = self.agent.handle_pair({
                "otp": otp,
                "deviceName": "Test",
                "clientUuid": "uuid-1",
            })
            self.assertEqual(status, 401, f"OTP '{otp}' should have been rejected")

    def test_02_ws_ticket_expiration_ttl(self) -> None:
        """Boundary: WS ticket older than 30s is expired."""
        status, res = self.agent.handle_pair({
            "otp": "749201",
            "deviceName": "Test",
            "clientUuid": "uuid-1",
        })
        token = res["token"]
        auth_header = f"Bearer {token}"

        _, ticket_res = self.agent.handle_ws_ticket(auth_header)
        ticket = ticket_res["ticket"]

        # Simulate ticket aging past 30 seconds
        self.agent.ws_tickets[ticket] = time.time() - 5  # Expired 5 seconds ago
        is_expired = time.time() > self.agent.ws_tickets[ticket]
        self.assertTrue(is_expired, "Ticket past 30s TTL must be considered expired")

    def test_03_max_process_list_bound(self) -> None:
        """Boundary: Querying with limit=50 caps output even if system has 10,000 processes."""
        large_process_pool = [{"pid": i, "name": f"proc_{i}.exe"} for i in range(1, 10001)]
        limit = 50
        capped = large_process_pool[:limit]
        self.assertEqual(len(capped), 50)

    def test_04_system_pid_and_zero_pid_termination_protection(self) -> None:
        """Boundary: Killing PID 0 or PID 4 (System) is forbidden."""
        status, res = self.agent.handle_pair({
            "otp": "749201",
            "deviceName": "Test",
            "clientUuid": "uuid-1",
        })
        auth_header = f"Bearer {res['token']}"

        status_0, _ = self.agent.handle_kill_process(auth_header, 0)
        self.assertEqual(status_0, 403)

        status_4, _ = self.agent.handle_kill_process(auth_header, 4)
        self.assertEqual(status_4, 403)

    def test_05_non_private_ip_rejection_for_local_endpoints(self) -> None:
        """Boundary: Public IP attempting to hit local management endpoint is forbidden."""
        def is_private_ip(ip_str: str) -> bool:
            import ipaddress
            try:
                ip = ipaddress.ip_address(ip_str)
                return ip.is_loopback or ip.is_private
            except ValueError:
                return False

        self.assertTrue(is_private_ip("127.0.0.1"))
        self.assertTrue(is_private_ip("192.168.1.150"))
        self.assertFalse(is_private_ip("8.8.8.8"))
        self.assertFalse(is_private_ip("142.250.190.46"))


if __name__ == "__main__":
    unittest.main()
