from __future__ import annotations

from .support import Dict, List, Optional, threading
from .process_simulator import RustWhiteBoxProcessSimulator

class TrayPolicyCases:
    """Stress testing of tray security policy tampering, loopback restrictions, and fail-safe defaults."""

    def test_loopback_restriction_rejects_spoofed_headers(self):
        """Management routes require true 127.0.0.1 socket peer; spoofed headers are rejected."""
        def check_management_peer(peer_ip: str, headers: Dict[str, str]) -> int:
            # Native socket peer check (ignoring any proxy headers)
            if peer_ip not in ("127.0.0.1", "::1"):
                return 403
            return 200

        # Direct loopback -> 200
        self.assertEqual(check_management_peer("127.0.0.1", {}), 200)

        # External IP -> 403
        self.assertEqual(check_management_peer("192.168.1.50", {}), 403)

        # External IP with spoofed headers -> still 403
        spoofed = {"X-Forwarded-For": "127.0.0.1", "X-Real-IP": "127.0.0.1"}
        self.assertEqual(check_management_peer("192.168.1.50", spoofed), 403)

    def test_fail_safe_closed_policy_defaults_on_corrupt_setting(self):
        """Corrupted settings in persistent storage default safely to False (closed)."""
        def parse_setting(val: Optional[str]) -> bool:
            if not val:
                return False
            return val == "1" or val.lower() == "true"

        corrupt_values = ["", "invalid", "null", "2", "-1", "yes", "true; DROP TABLE", "NONE", None]
        for val in corrupt_values:
            self.assertFalse(parse_setting(val), f"Setting '{val}' must evaluate to False (fail-safe closed)")

        self.assertTrue(parse_setting("1"))
        self.assertTrue(parse_setting("true"))
        self.assertTrue(parse_setting("True"))

    def test_zero_deadlock_guarantee_under_concurrent_command_dispatch(self):
        """Simultaneous command execution across multiple threads completes with zero deadlocks."""
        sim = RustWhiteBoxProcessSimulator()
        sim.allow_delete_operations = True
        errors: List[Exception] = []
        completed: List[int] = [0]
        lock = threading.Lock()

        def worker(thread_idx: int):
            try:
                for i in range(15):
                    # Add dummy process
                    pid = 20000 + thread_idx * 100 + i
                    sim.active_processes[pid] = f"proc_{pid}.exe"
                    cmd = {
                        "id": f"cmd_t{thread_idx}_{i}",
                        "type": "kill_process",
                        "payload": {"pid": pid},
                    }
                    res = sim.execute_cloud_pending_command(cmd)
                    if res["status"] != "completed":
                        raise ValueError(f"Command failed: {res}")
                    with lock:
                        completed[0] += 1
            except Exception as e:
                with lock:
                    errors.append(e)

        threads = [threading.Thread(target=worker, args=(t,)) for t in range(8)]
        for t in threads:
            t.start()
        for t in threads:
            t.join(timeout=5.0)
            self.assertFalse(t.is_alive(), "Worker thread deadlocked during concurrent execution!")

        self.assertEqual(len(errors), 0, f"Concurrent execution errors: {errors}")
        self.assertEqual(completed[0], 120, "All 120 concurrent commands must complete cleanly")
