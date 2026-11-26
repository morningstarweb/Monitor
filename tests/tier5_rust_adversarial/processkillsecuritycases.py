from __future__ import annotations

from .process_simulator import RustWhiteBoxProcessSimulator

class ProcessKillSecurityCases:
    """Stress testing of process termination security boundaries and RBAC enforcement."""

    def setUp(self):
        self.sim = RustWhiteBoxProcessSimulator(current_pid=9182)

    def test_pid_0_kernel_idle_process_rejected(self):
        """PID 0 (System Idle Process) must be rejected with 400 Bad Request."""
        self.sim.allow_delete_operations = True
        status, body = self.sim.handle_http_kill_process("operator", 0)
        self.assertEqual(status, 400)
        self.assertIn("protected system process", body.get("message", "").lower())

    def test_pid_4_windows_system_process_rejected(self):
        """PID 4 (Windows NT System process) must be rejected with 400 Bad Request."""
        self.sim.allow_delete_operations = True
        status, body = self.sim.handle_http_kill_process("operator", 4)
        self.assertEqual(status, 400)
        self.assertIn("protected system process", body.get("message", "").lower())

    def test_self_pid_suicide_prevention(self):
        """Attempting to terminate the Desktop daemon's own PID must be rejected with 400 Bad Request."""
        self.sim.allow_delete_operations = True
        status, body = self.sim.handle_http_kill_process("operator", 9182)
        self.assertEqual(status, 400)
        self.assertIn("protected system process", body.get("message", "").lower())

    def test_negative_pids_rejected_at_route_parser(self):
        """Negative PIDs (-1, -4, -99999) cannot parse as u32 and are rejected with 400 Bad Request."""
        self.sim.allow_delete_operations = True
        for neg_pid in [-1, -4, -100, -99999]:
            status, body = self.sim.handle_http_kill_process("operator", neg_pid)
            self.assertEqual(status, 400, f"Negative PID {neg_pid} must return HTTP 400")
            self.assertIn("invalid u32", body.get("message", "").lower())

    def test_malformed_and_overflow_pids_rejected(self):
        """Non-numeric and overflow PIDs (e.g. '0x10', 'NaN', '99999999999999') must be rejected with 400."""
        self.sim.allow_delete_operations = True
        malformed = ["0x10", "NaN", "null", "abc", "1.5", "99999999999999999999", "1; DROP TABLE"]
        for bad in malformed:
            status, body = self.sim.handle_http_kill_process("operator", bad)
            self.assertEqual(status, 400, f"Malformed PID '{bad}' must return HTTP 400")

    def test_nonexistent_high_pids_return_404(self):
        """Non-existent high PIDs (e.g. 4294967295 / u32::MAX, 999999999) must return 404 Not Found."""
        self.sim.allow_delete_operations = True
        for high_pid in [999999999, 4294967295]:
            status, body = self.sim.handle_http_kill_process("operator", high_pid)
            self.assertEqual(status, 404, f"Non-existent PID {high_pid} must return HTTP 404")
            self.assertIn("not found", body.get("message", "").lower())

    def test_viewer_role_bypass_prevented_strictly(self):
        """Viewer role must receive 403 Forbidden on ANY kill request, preceding PID checks."""
        self.sim.allow_delete_operations = True

        # Viewer attempting normal PID
        s1, b1 = self.sim.handle_http_kill_process("viewer", 1000)
        self.assertEqual(s1, 403)
        self.assertIn("viewer role cannot terminate", b1.get("message", "").lower())

        # Viewer attempting protected PID 0 (RBAC must precede PID validation)
        s2, b2 = self.sim.handle_http_kill_process("viewer", 0)
        self.assertEqual(s2, 403, "RBAC must precede protected PID check")
        self.assertIn("viewer role cannot terminate", b2.get("message", "").lower())

        # Viewer attempting negative PID
        s3, b3 = self.sim.handle_http_kill_process("viewer", -1)
        self.assertEqual(s3, 403, "Viewer role must always return 403")

    def test_desktop_tray_policy_enforcement(self):
        """When allow_delete_operations is false, Operator receives 403 Forbidden."""
        self.sim.allow_delete_operations = False
        status, body = self.sim.handle_http_kill_process("operator", 1000)
        self.assertEqual(status, 403)
        self.assertIn("blocked by desktop tray policy", body.get("message", "").lower())

        # Enable policy -> succeeds
        self.sim.allow_delete_operations = True
        status_ok, body_ok = self.sim.handle_http_kill_process("operator", 1000)
        self.assertEqual(status_ok, 200)
        self.assertTrue(body_ok.get("success", False))

    def test_cloud_pending_command_kill_process_payload_boundaries(self):
        """Cloud pending command kill_process must enforce schema, policy, and protected PID checks."""
        # 1. Missing PID
        res1 = self.sim.execute_cloud_pending_command({"id": "c1", "type": "kill_process", "payload": {}})
        self.assertEqual(res1["status"], "failed")
        self.assertIn("missing 'pid'", res1["result"].lower())

        # 2. Protected PID 0
        self.sim.allow_delete_operations = True
        res2 = self.sim.execute_cloud_pending_command({"id": "c2", "type": "kill_process", "payload": {"pid": 0}})
        self.assertEqual(res2["status"], "failed")
        self.assertIn("protected system process", res2["result"].lower())

        # 3. Protected PID 4
        res3 = self.sim.execute_cloud_pending_command({"id": "c3", "type": "kill_process", "payload": {"pid": 4}})
        self.assertEqual(res3["status"], "failed")
        self.assertIn("protected system process", res3["result"].lower())

        # 4. Self PID
        res4 = self.sim.execute_cloud_pending_command({"id": "c4", "type": "kill_process", "payload": {"pid": 9182}})
        self.assertEqual(res4["status"], "failed")
        self.assertIn("protected system process", res4["result"].lower())

        # 5. Tray policy disabled
        self.sim.allow_delete_operations = False
        res5 = self.sim.execute_cloud_pending_command({"id": "c5", "type": "kill_process", "payload": {"pid": 1024}})
        self.assertEqual(res5["status"], "failed")
        self.assertIn("disabled by desktop security policy", res5["result"].lower())
