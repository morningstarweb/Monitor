"""Validation functions and constraint checks for Monitor E2E testing."""

from __future__ import annotations

from typing import Any, Dict, List, Tuple


def validate_telemetry_snapshot(data: Dict[str, Any], allow_mock: bool = False) -> Tuple[bool, List[str]]:
    """Validates that a telemetry snapshot conforms to the system contract and contains zero mock data."""
    errors: List[str] = []

    if not isinstance(data, dict):
        return False, ["Snapshot must be a dictionary"]

    # Required top-level fields
    for req in ("observedAtUtcMs", "cpu", "memory"):
        if req not in data:
            errors.append(f"Missing required field '{req}'")

    if "observedAtUtcMs" in data:
        ts = data["observedAtUtcMs"]
        if not isinstance(ts, int) or ts <= 0:
            errors.append("observedAtUtcMs must be a positive integer timestamp")

    # CPU validation
    if "cpu" in data:
        cpu = data["cpu"]
        if not isinstance(cpu, dict):
            errors.append("cpu must be a dictionary")
        else:
            if "totalUsagePercent" not in cpu or not isinstance(cpu["totalUsagePercent"], (int, float)):
                errors.append("cpu.totalUsagePercent must be numeric")
            elif not (0.0 <= cpu["totalUsagePercent"] <= 100.0):
                errors.append("cpu.totalUsagePercent out of range [0, 100]")
            if "coreCount" not in cpu or not isinstance(cpu["coreCount"], int) or cpu["coreCount"] <= 0:
                errors.append("cpu.coreCount must be a positive integer")

    # Memory validation
    if "memory" in data:
        mem = data["memory"]
        if not isinstance(mem, dict):
            errors.append("memory must be a dictionary")
        else:
            used = mem.get("usedBytes")
            total = mem.get("totalBytes")
            if not isinstance(used, int) or not isinstance(total, int) or used < 0 or total <= 0:
                errors.append("memory usedBytes and totalBytes must be valid byte counts")
            elif used > total:
                errors.append("usedBytes cannot exceed totalBytes")

    # Cooling hardening validation (Feature 6): Must be None or physical sensor, NOT synthetic RPM math
    if "cooling" in data and data["cooling"] is not None:
        cooling = data["cooling"]
        if not isinstance(cooling, dict):
            errors.append("cooling must be None or a dict of physical sensor readings")
        else:
            # Synthetic cooling typically invented synthetic RPM values
            if cooling.get("isSynthetic", False):
                errors.append("Synthetic cooling fan RPM is disallowed by Feature 6")

    # Mock elimination checks (Feature 1)
    if not allow_mock:
        raw_str = str(data).lower()
        for forbidden in ("mock", "dummy", "fake", "dev-bypass-mock-token", "synthetic"):
            if forbidden in raw_str:
                errors.append(f"Forbidden mock marker '{forbidden}' detected in telemetry payload")

    return len(errors) == 0, errors
def validate_dark_obsidian_tokens(theme_config: Dict[str, Any]) -> Tuple[bool, List[str]]:
    """Validates Dark Obsidian Glass design tokens per R2 specifications."""
    errors: List[str] = []

    # Obsidian base colors: #0B0D1A or #05070E or dark navy/black glass variants
    base_color = theme_config.get("backgroundColor", "").upper()
    valid_bases = {"#0B0D1A", "#05070E", "#0A0C16", "#0E101F"}
    if base_color and base_color not in valid_bases:
        errors.append(f"Background color '{base_color}' violates dark obsidian base palette")

    # Border alpha must be strictly between 0.15 and 0.25 (silver/white frosted glass border)
    border_alpha = theme_config.get("borderAlpha")
    if border_alpha is not None:
        if not isinstance(border_alpha, (int, float)) or not (0.15 <= border_alpha <= 0.25):
            errors.append(f"Border alpha {border_alpha} outside required 0.15–0.25 alpha range")

    # Squircle corner radius between 20dp and 28dp
    corner_radius = theme_config.get("cornerRadiusDp")
    if corner_radius is not None:
        if not isinstance(corner_radius, (int, float)) or not (20 <= corner_radius <= 28):
            errors.append(f"Corner radius {corner_radius}dp outside required 20–28dp range")

    return len(errors) == 0, errors


def validate_statutory_legal_terms(text: str) -> Tuple[bool, List[str]]:
    """Validates presence of all required statutory legal terms per R2, R3, DPDP Act 2023 and GDPR."""
    errors: List[str] = []

    required_phrases = [
        "Morning Star Web",
        "Kolkata",
        "India",
        "contact@morningstarweb.space",
        "DPDP Act 2023",
        "GDPR",
    ]

    for phrase in required_phrases:
        if phrase.lower() not in text.lower():
            errors.append(f"Missing required statutory legal phrase: '{phrase}'")

    return len(errors) == 0, errors

