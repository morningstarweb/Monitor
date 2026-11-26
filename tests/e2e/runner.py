"""Master E2E Test Runner for Monitor Platform.

Executes all 4 Tiers of opaque-box, requirement-driven tests:
- Tier 1: Feature Coverage (12 Features x >= 5 tests)
- Tier 2: Boundary & Corner Cases (12 Features x >= 5 tests)
- Tier 3: Cross-Feature Pairwise Combinations (>= 15 tests)
- Tier 4: Real-World Application Scenarios (>= 5 tests)

Returns exit code 0 on complete pass.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import unittest
from pathlib import Path
from typing import Any, Dict, List, Tuple


PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))


class TextResultCollector(unittest.TestResult):
    """Custom TestResult to gather structured execution statistics."""

    def __init__(self, stream=None, descriptions=None, verbosity=None):
        super().__init__(stream, descriptions, verbosity)
        self.successes: List[unittest.TestCase] = []
        self.test_records: List[Dict[str, Any]] = []

    def addSuccess(self, test: unittest.TestCase) -> None:
        super().addSuccess(test)
        self.successes.append(test)
        self.test_records.append({
            "test_id": test.id(),
            "status": "PASSED",
            "error": None,
        })

    def addFailure(self, test: unittest.TestCase, err) -> None:
        super().addFailure(test, err)
        self.test_records.append({
            "test_id": test.id(),
            "status": "FAILED",
            "error": self._exc_info_to_string(err, test),
        })

    def addError(self, test: unittest.TestCase, err) -> None:
        super().addError(test, err)
        self.test_records.append({
            "test_id": test.id(),
            "status": "ERROR",
            "error": self._exc_info_to_string(err, test),
        })


def run_tier(tier_dir: Path, verbosity: int = 1) -> Tuple[int, int, int, List[Dict[str, Any]], float]:
    """Runs tests in a specific tier directory and returns (passed, failed, errors, records, duration)."""
    start_time = time.perf_counter()
    loader = unittest.TestLoader()
    suite = loader.discover(start_dir=str(tier_dir), pattern="test_*.py", top_level_dir=str(PROJECT_ROOT))

    collector = TextResultCollector()
    suite.run(collector)
    duration = time.perf_counter() - start_time

    passed = len(collector.successes)
    failed = len(collector.failures)
    errors = len(collector.errors)

    return passed, failed, errors, collector.test_records, duration


def main() -> int:
    parser = argparse.ArgumentParser(description="Monitor E2E Test Suite Runner")
    parser.add_argument(
        "--tier",
        choices=["1", "2", "3", "4", "all"],
        default="all",
        help="Specific test tier to execute (default: all)",
    )
    parser.add_argument(
        "-v", "--verbose",
        action="store_true",
        help="Enable verbose output per test case",
    )
    parser.add_argument(
        "--report-json",
        type=str,
        default=None,
        help="Optional path to output detailed JSON test results",
    )
    args = parser.parse_args()

    e2e_root = Path(__file__).resolve().parent

    tier_map = {
        "1": ("Tier 1: Feature Coverage", e2e_root / "tier1_feature"),
        "2": ("Tier 2: Boundary & Corner Cases", e2e_root / "tier2_boundary"),
        "3": ("Tier 3: Pairwise Combinations", e2e_root / "tier3_pairwise"),
        "4": ("Tier 4: Real-World Scenarios", e2e_root / "tier4_scenarios"),
    }

    selected_tiers = tier_map.keys() if args.tier == "all" else [args.tier]

    print("=" * 80)
    print("   MONITOR PLATFORM — END-TO-END AUTOMATED VERIFICATION SUITE")
    print("   Strict Zero-Trust, Requirement-Driven Architecture (Tiers 1-4)")
    print("=" * 80)
    print()

    total_passed = 0
    total_failed = 0
    total_errors = 0
    total_duration = 0.0
    tier_summaries: Dict[str, Any] = {}
    all_records: List[Dict[str, Any]] = []

    for t_key in sorted(selected_tiers):
        t_name, t_dir = tier_map[t_key]
        if not t_dir.exists():
            print(f"[-] Directory for {t_name} not found: {t_dir}")
            continue

        print(f"[*] Executing {t_name}...")
        passed, failed, errors, records, duration = run_tier(t_dir, verbosity=2 if args.verbose else 1)

        total_passed += passed
        total_failed += failed
        total_errors += errors
        total_duration += duration
        all_records.extend(records)

        tier_summaries[t_name] = {
            "passed": passed,
            "failed": failed,
            "errors": errors,
            "total": passed + failed + errors,
            "duration_seconds": round(duration, 3),
        }

        status_flag = "[OK]" if (failed == 0 and errors == 0) else "[FAIL]"
        print(
            f"    -> {status_flag} {passed} passed, {failed} failed, {errors} errors "
            f"({duration:.3f}s)"
        )

        if args.verbose or failed > 0 or errors > 0:
            for rec in records:
                if rec["status"] != "PASSED" or args.verbose:
                    prefix = "PASS" if rec["status"] == "PASSED" else rec["status"]
                    print(f"       [{prefix}] {rec['test_id']}")
                    if rec["error"]:
                        for line in rec["error"].strip().splitlines()[-4:]:
                            print(f"              {line}")
        print()

    grand_total = total_passed + total_failed + total_errors
    print("=" * 80)
    print(f"TEST EXECUTION SUMMARY: {grand_total} tests executed in {total_duration:.3f}s")
    print(f"  Passed : {total_passed}")
    print(f"  Failed : {total_failed}")
    print(f"  Errors : {total_errors}")
    print("=" * 80)

    if total_failed == 0 and total_errors == 0:
        print("[SUCCESS] All test cases satisfied interface contracts and requirement specifications.")
        exit_code = 0
    else:
        print("[FAILURE] Some tests failed. Review output above.")
        exit_code = 1

    # Write JSON report if requested or default to tests/e2e/test_results.json
    report_file = args.report_json or str(e2e_root / "test_results.json")
    report_data = {
        "timestamp_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "summary": {
            "total": grand_total,
            "passed": total_passed,
            "failed": total_failed,
            "errors": total_errors,
            "duration_seconds": round(total_duration, 3),
            "status": "PASSED" if exit_code == 0 else "FAILED",
        },
        "tiers": tier_summaries,
        "results": all_records,
    }

    try:
        with open(report_file, "w", encoding="utf-8") as f:
            json.dump(report_data, f, indent=2)
        print(f"[*] Detailed test report generated at: {report_file}")
    except Exception as ex:
        print(f"[!] Warning: Could not write JSON report: {ex}")

    return exit_code


if __name__ == "__main__":
    sys.exit(main())
