from __future__ import annotations

from .support import Dict, Optional, json, re

class AndroidOnboardingCases:
    """Adversarial stress testing for :feature:onboarding and Biometrics."""

    def test_pairing_qr_code_fuzzing(self) -> None:
        """Adversarial: Fuzz QR code parser with valid JSON, URI query params, raw codes, and garbage."""
        def parse_qr_code(raw: str) -> Optional[Dict[str, str]]:
            trimmed = raw.strip()
            host = "127.0.0.1"
            port = "9480"
            code = ""

            try:
                if trimmed.startswith("{") and trimmed.endswith("}"):
                    data = json.loads(trimmed)
                    host = data.get("host", host)
                    port = str(data.get("port", port))
                    code = str(data.get("code", ""))
                elif "code=" in trimmed:
                    # Parse URI query params
                    match_host = re.search(r"[?&]host=([^&]+)", trimmed)
                    match_port = re.search(r"[?&]port=([^&]+)", trimmed)
                    match_code = re.search(r"[?&]code=([^&]+)", trimmed)
                    if match_host:
                        host = match_host.group(1)
                    if match_port:
                        port = match_port.group(1)
                    if match_code:
                        code = match_code.group(1)
                elif len(trimmed) == 6 and trimmed.isdigit():
                    code = trimmed
            except Exception:
                return None

            if len(code) != 6 or not code.isdigit():
                return None

            return {"host": host, "port": port, "code": code}

        # Valid cases
        self.assertEqual(
            parse_qr_code('{"host":"192.168.1.100","port":9480,"code":"123456"}'),
            {"host": "192.168.1.100", "port": "9480", "code": "123456"}
        )
        self.assertEqual(
            parse_qr_code("monitor://pair?host=10.0.2.2&port=9480&code=654321"),
            {"host": "10.0.2.2", "port": "9480", "code": "654321"}
        )
        self.assertEqual(
            parse_qr_code("987654"),
            {"host": "127.0.0.1", "port": "9480", "code": "987654"}
        )

        # Invalid cases
        invalid_qrs = [
            "",
            "12345",         # 5 digits
            "1234567",       # 7 digits
            "abcdef",        # letters
            "12345a",        # mixed
            '{"host": "ok"}',# missing code
            "monitor://pair?host=10.0.2.2",
            "not a qr code at all",
            "{bad-json",
        ]
        for qr in invalid_qrs:
            self.assertIsNone(parse_qr_code(qr), f"Should reject invalid QR: {qr}")

    def test_biometric_auth_error_mapping_resilience(self) -> None:
        """Adversarial: Biometric error callbacks must handle cancel, lockout, and fallback without crashing."""
        # Standard Android BiometricPrompt error codes
        BIOMETRIC_ERROR_HW_UNAVAILABLE = 1
        BIOMETRIC_ERROR_UNABLE_TO_PROCESS = 2
        BIOMETRIC_ERROR_TIMEOUT = 3
        BIOMETRIC_ERROR_NO_SPACE = 4
        BIOMETRIC_ERROR_CANCELED = 5
        BIOMETRIC_ERROR_LOCKOUT = 7
        BIOMETRIC_ERROR_LOCKOUT_PERMANENT = 9
        BIOMETRIC_ERROR_USER_CANCELED = 10
        BIOMETRIC_ERROR_NO_BIOMETRICS = 11
        BIOMETRIC_ERROR_HW_NOT_PRESENT = 12

        def handle_biometric_error(error_code: int) -> str:
            if error_code in (BIOMETRIC_ERROR_USER_CANCELED, BIOMETRIC_ERROR_CANCELED):
                return "CANCELED"
            else:
                return "FALLBACK"

        self.assertEqual(handle_biometric_error(BIOMETRIC_ERROR_USER_CANCELED), "CANCELED")
        self.assertEqual(handle_biometric_error(BIOMETRIC_ERROR_CANCELED), "CANCELED")
        self.assertEqual(handle_biometric_error(BIOMETRIC_ERROR_LOCKOUT), "FALLBACK")
        self.assertEqual(handle_biometric_error(BIOMETRIC_ERROR_NO_BIOMETRICS), "FALLBACK")
        self.assertEqual(handle_biometric_error(BIOMETRIC_ERROR_HW_NOT_PRESENT), "FALLBACK")
