#!/usr/bin/env bash
# Fast Automated Android Deployment Script for Monitor (OxygenOS / Modern Android)
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_DIR="$(dirname "$SCRIPT_DIR")"
APK_PATH="$WORKSPACE_DIR/android/app/build/outputs/apk/debug/app-debug.apk"

echo "========================================================"
echo " Monitor Android Fast Deploy (OxygenOS Optimized)      "
echo "========================================================"

SKIP_BUILD=false
if [ "$1" == "--skip-build" ]; then
    SKIP_BUILD=true
fi

if [ "$SKIP_BUILD" = false ]; then
    echo -e "\n[1/4] Building Android debug APK..."
    cd "$WORKSPACE_DIR/android"
    ./gradlew assembleDebug
fi

if [ ! -f "$APK_PATH" ]; then
    echo "Error: APK not found at $APK_PATH"
    exit 1
fi

echo -e "\n[2/4] Checking connected ADB devices..."
adb devices

echo -e "\n[3/4] Configuring device to bypass verification prompts..."
adb shell settings put global verifier_verify_adb_installs 0 || true
adb shell settings put global package_verifier_enable 0 || true

echo -e "\n[4/4] Installing APK and launching Monitor..."
adb install -r -d -t -g "$APK_PATH"
adb shell am start -n app.monitor/.MainActivity

echo -e "\n[SUCCESS] Monitor is running on your phone!"
