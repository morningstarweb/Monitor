from __future__ import annotations

from .support import hashlib, json, time
from .cloudflare_simulator import CloudflareWorkerSimulator

class CloudflareEdgeCases:
    """Stress testing of Cloudflare edge sync, heartbeat storms, rate limiting, and 1MB ceiling."""

    def setUp(self):
        self.worker = CloudflareWorkerSimulator()
        self.token = self.worker.register_user_session(
            user_id="user_prod_1",
            email="developer@morningstarweb.space",
            device_id="win_prod_1",
            role="desktop",
        )

    def test_payload_size_boundary_1mb_enforcement(self):
        """Payloads <= 1MB are accepted; payloads > 1MB are rejected with 413 Payload Too Large."""
        # 1. Normal payload (~1KB) -> 200 OK
        normal_data = json.dumps({"lan_ip": "192.168.1.10", "snapshot": {"cpu": 25.0}}).encode("utf-8")
        s1, b1 = self.worker.handle_heartbeat(self.token, normal_data)
        self.assertEqual(s1, 200)
        self.assertTrue(b1.get("success", False))

        # 2. Exactly 1,048,576 bytes (1MB) -> 200 OK
        padding = "x" * (1024 * 1024 - 100)
        exact_1mb = json.dumps({"lan_ip": "192.168.1.10", "pad": padding}).encode("utf-8")[: 1024 * 1024]
        # Re-encode valid json at ~1MB
        pad_size = 1024 * 1024 - 60
        valid_1mb = json.dumps({"pad": "y" * pad_size}).encode("utf-8")
        self.assertLessEqual(len(valid_1mb), 1024 * 1024)
        s2, b2 = self.worker.handle_heartbeat(self.token, valid_1mb)
        self.assertEqual(s2, 200)

        # 3. Oversized payload (> 1MB) -> 413
        oversized = b"{" + b'"pad": "' + b"z" * (1024 * 1024 + 1000) + b'"}'
        self.assertGreater(len(oversized), 1024 * 1024)
        s3, b3 = self.worker.handle_heartbeat(self.token, oversized)
        self.assertEqual(s3, 413)
        self.assertIn("too large", b3.get("error", "").lower())

    def test_rapid_heartbeat_storm_burst(self):
        """Burst of 50 rapid heartbeats executes cleanly, maintaining state consistency."""
        for i in range(50):
            payload = json.dumps({
                "lan_ip": "192.168.1.10",
                "snapshot": {"sample_seq": i, "cpu": float(i % 100)},
                "command_results": [{"id": f"cmd_{i}", "status": "completed"}],
            }).encode("utf-8")
            status, body = self.worker.handle_heartbeat(self.token, payload)
            self.assertEqual(status, 200, f"Heartbeat #{i} failed with status {status}")
            self.assertTrue(body.get("success", False))

        self.assertEqual(self.worker.devices["win_prod_1"]["last_snapshot"]["sample_seq"], 49)

    def test_rate_limiting_ceiling_80_req_min(self):
        """IP exceeding 80 requests per minute triggers 429 Too Many Requests."""
        test_ip = "198.51.100.99"
        small_payload = json.dumps({"lan_ip": "192.168.1.1"}).encode("utf-8")

        for i in range(80):
            status, _ = self.worker.handle_heartbeat(self.token, small_payload, client_ip=test_ip)
            self.assertEqual(status, 200, f"Request {i+1} should pass rate limit")

        # 81st request must trigger 429
        status_blocked, body_blocked = self.worker.handle_heartbeat(self.token, small_payload, client_ip=test_ip)
        self.assertEqual(status_blocked, 429, "81st request within 60s must be rate limited")
        self.assertIn("too many requests", body_blocked.get("error", "").lower())

    def test_cross_tenant_isolation(self):
        """User A session cannot view or access devices registered to User B."""
        token_a = self.token
        token_b = self.worker.register_user_session(
            user_id="user_prod_2",
            email="other@example.com",
            device_id="win_prod_2",
            role="desktop",
        )

        # User A checks devices -> only win_prod_1
        s_a, b_a = self.worker.list_user_devices(token_a)
        self.assertEqual(s_a, 200)
        ids_a = [d["id"] for d in b_a.get("devices", [])]
        self.assertIn("win_prod_1", ids_a)
        self.assertNotIn("win_prod_2", ids_a, "Cross-tenant leak: User A saw User B device!")

        # User B checks devices -> only win_prod_2
        s_b, b_b = self.worker.list_user_devices(token_b)
        self.assertEqual(s_b, 200)
        ids_b = [d["id"] for d in b_b.get("devices", [])]
        self.assertIn("win_prod_2", ids_b)
        self.assertNotIn("win_prod_1", ids_b, "Cross-tenant leak: User B saw User A device!")

    def test_session_token_256bit_and_expiration(self):
        """Session token must be 256 bits (64 hex characters) and expire after 30 days."""
        self.assertEqual(len(self.token), 64)
        self.assertTrue(all(c in "0123456789abcdef" for c in self.token))

        # Test expired token rejection
        expired_token = self.worker.register_user_session(
            user_id="user_exp",
            email="exp@example.com",
            device_id="win_exp",
        )
        # Manually backdate expiry
        hashed_exp = hashlib.sha256(expired_token.encode("utf-8")).hexdigest()
        self.worker.sessions[hashed_exp]["expires_at"] = int(time.time()) - 100

        status, body = self.worker.handle_heartbeat(expired_token, b"{}")
        self.assertEqual(status, 401, "Expired token must be rejected with 401")
