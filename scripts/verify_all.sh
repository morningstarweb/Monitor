#!/usr/bin/env bash
# scripts/verify_all.sh — Master CI Quality Gate
# Enforces gates G01 through G21 per Monitor PRD v4.0.0 Section 11

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "=== MONITOR MASTER VERIFICATION SUITE ==="

# Gate G01: Workspace Integrity & Drift
echo "[G01] Checking Workspace Integrity & Git Drift..."
git diff --check || { echo "FAIL: Git whitespace or conflict markers detected!"; exit 1; }
python3 scripts/sync_brand.py --check || { echo "FAIL: Brand configurations out of date!"; exit 1; }

# Gate G02: Rust Formatting
if command -v cargo >/dev/null 2>&1; then
    pushd "$ROOT_DIR/desktop" >/dev/null
    echo "[G02] Checking Rust Formatting..."
    cargo fmt --all -- --check || { echo "FAIL: Rust formatting check failed!"; exit 1; }

    # Gate G03: Rust Compile Check
    echo "[G03] Running Rust Workspace Check..."
    cargo check --workspace --all-targets || { echo "FAIL: Cargo check failed!"; exit 1; }

    # Gate G04: Clippy Static Analysis
    echo "[G04] Running Clippy..."
    cargo clippy --workspace --all-targets --all-features -- -D warnings || { echo "FAIL: Clippy warnings detected!"; exit 1; }

    # Gate G05: Rust Test Suite
    echo "[G05] Running Rust Tests..."
    cargo test --workspace --all-targets || { echo "FAIL: Rust tests failed!"; exit 1; }
    popd >/dev/null
else
    echo "FAIL: Cargo is required to verify the desktop application."
    exit 1
fi

echo "[Android] Building APK, running unit tests and lint..."
(cd "$ROOT_DIR/android" && bash ./gradlew assembleDebug testDebugUnitTest lintDebug --console=plain)

# Gate G15: Python Linting
if command -v ruff >/dev/null 2>&1; then
    echo "[G15] Checking Python code..."
    ruff check scripts/ || { echo "FAIL: Python lint failed!"; exit 1; }
    ruff format --check scripts/ || { echo "FAIL: Python format check failed!"; exit 1; }
fi

# Gate G16: Maintainability LOC check
echo "[G16] Checking source lines of code guideline..."
python3 scripts/check_loc.py

echo "=== ALL VERIFICATION CHECKS PASSED ==="

