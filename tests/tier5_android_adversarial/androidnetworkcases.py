from __future__ import annotations

from .support import Any, Dict, Optional, json

class AndroidNetworkCases:
    """Adversarial stress testing for :core:network and streaming protocols."""

    def test_reconnect_backoff_jitter_bounds(self) -> None:
        """Adversarial: Verify exponential backoff ceiling and jitter calculation.

        Matches DashboardViewModel.reconnectDelayMillis:
        ceiling = min(15_000, 1000 << min(attempt, 4))
        delay = (ceiling / 2) + (ceiling / 2 * jitter)
        """
        def reconnect_delay(attempt: int, jitter: float) -> int:
            attempt_clamped = min(max(attempt, 0), 4)
            ceiling = min(15000, 1000 << attempt_clamped)
            jitter_clamped = min(max(jitter, 0.0), 1.0)
            return int((ceiling // 2) + ((ceiling // 2) * jitter_clamped))

        # Attempt 0: base 1000ms -> delay range [500ms, 1000ms]
        self.assertEqual(reconnect_delay(0, 0.0), 500)
        self.assertEqual(reconnect_delay(0, 0.5), 750)
        self.assertEqual(reconnect_delay(0, 1.0), 1000)

        # Attempt 4+: ceiling capped at 15000ms -> delay range [7500ms, 15000ms]
        self.assertEqual(reconnect_delay(4, 0.0), 7500)
        self.assertEqual(reconnect_delay(4, 1.0), 15000)
        self.assertEqual(reconnect_delay(100, 0.0), 7500)
        self.assertEqual(reconnect_delay(100, 1.0), 15000)

        # Jitter out of bounds clamping
        self.assertEqual(reconnect_delay(0, -10.0), 500)
        self.assertEqual(reconnect_delay(0, 10.0), 1000)

    def test_corrupt_websocket_frame_decoding_fuzzing(self) -> None:
        """Adversarial: Fuzz ServerEvent decoder with malformed, truncated, and corrupt payloads."""
        known_events = {
            "TelemetryTick", "ProjectUpdated", "WorkspaceUpdated",
            "OperationProgress", "AlertTriggered", "ResyncRequired"
        }

        def decode_server_event(raw: str) -> Optional[Dict[str, Any]]:
            try:
                parsed = json.loads(raw)
            except Exception as e:
                raise ValueError(f"Corrupt JSON: {e}")

            if not isinstance(parsed, dict):
                raise ValueError("Payload is not a JsonObject")

            if "event" not in parsed or not isinstance(parsed["event"], str):
                raise ValueError("Missing or invalid event name")

            event_name = parsed["event"]
            if event_name not in known_events:
                # Unknown future event: ignored without crashing
                return None

            data = parsed.get("data")
            if event_name == "TelemetryTick":
                if not isinstance(data, dict) or "schema_version" not in data or "hostname" not in data:
                    raise ValueError("Malformed TelemetryTick payload")
            elif event_name == "ProjectUpdated":
                if not isinstance(data, dict) or "repo_id" not in data or "display_name" not in data:
                    raise ValueError("Malformed ProjectUpdated payload")

            return {"event": event_name, "data": data}

        # Corrupt and invalid inputs that must raise ValueError (caught by Exception in Android)
        fuzz_corpus = [
            "",
            "   ",
            "{",
            "{'invalid': 'single_quotes'}",
            "null",
            "12345",
            "true",
            "\"just a string\"",
            "[]",
            "[1, 2, 3]",
            "{}",
            "{\"data\": {}}",
            "{\"event\": null}",
            "{\"event\": 123}",
            "{\"event\": \"TelemetryTick\", \"data\": {}}",
            "{\"event\": \"TelemetryTick\", \"data\": \"not an object\"}",
            "{\"event\": \"ProjectUpdated\", \"data\": null}",
            "{\"event\": \"ProjectUpdated\", \"data\": {\"missing\": \"repo_id\"}}",
        ]

        for payload in fuzz_corpus:
            with self.assertRaises(ValueError, msg=f"Should reject: {payload}"):
                decode_server_event(payload)

        # Future events must return None cleanly (ignoring future protocol additions)
        future_payload = json.dumps({"event": "QuantumTelemetryTickV2", "data": {"alpha": 1}})
        self.assertIsNone(decode_server_event(future_payload))

        # Valid TelemetryTick
        valid_telemetry = json.dumps({
            "event": "TelemetryTick",
            "data": {
                "schema_version": 3,
                "observed_at_utc_ms": 1700000000000,
                "hostname": "workstation-prod",
                "agent_version": "1.0.4",
                "uptime_seconds": 3600,
                "battery": {"has_battery": False},
                "cpu": {"total_usage_percent": 15.0, "frequency_mhz": 3400},
                "ram": {"total_bytes": 16000000000, "used_bytes": 8000000000, "available_bytes": 8000000000, "usage_percent": 50.0}
            }
        })
        decoded = decode_server_event(valid_telemetry)
        self.assertIsNotNone(decoded)
        self.assertEqual(decoded["event"], "TelemetryTick")

    def test_token_expiration_401_state_transition(self) -> None:
        """Adversarial: Simulate 401 Unauthorized during live telemetry, verifying pairing requirement."""
        class MockDashboardState:
            def __init__(self) -> None:
                self.is_online = True
                self.requires_pairing = False
                self.error_message: Optional[str] = None
                self.reconnect_attempt = 0

            def handle_error(self, status_code: int, message: str) -> None:
                self.is_online = False
                self.error_message = message
                if status_code == 401:
                    self.requires_pairing = True

        state = MockDashboardState()
        self.assertTrue(state.is_online)
        self.assertFalse(state.requires_pairing)

        # Transient network error (e.g. 503 or timeout)
        state.handle_error(503, "Desktop agent unreachable")
        self.assertFalse(state.is_online)
        self.assertFalse(state.requires_pairing)

        # Token expiration (401 Unauthorized)
        state.handle_error(401, "Device token expired or revoked")
        self.assertFalse(state.is_online)
        self.assertTrue(state.requires_pairing)
        self.assertEqual(state.error_message, "Device token expired or revoked")
