# Build From Source Guide

Instructions for building Monitor's Windows desktop binaries (`Monitor.exe`, `Monitor_Setup_v1.0.0.exe`) and Android APK (`app-debug.apk` / signed `app-release.apk`) cleanly from source.

---

## 1. Prerequisites

| Component | Version / Requirement |
| --- | --- |
| **OS (Desktop Build)** | Windows 10/11 x64 (`x86_64-pc-windows-msvc`), Visual Studio Build Tools with C++ & Windows SDK |
| **Rust Toolchain** | Stable Rust supporting **Edition 2024** + `rustfmt` + `clippy` (`desktop/Cargo.lock` pinned) |
| **Java / JDK** | **JDK 21** (e.g. Eclipse Temurin / Adoptium `21`) |
| **Android SDK** | SDK Platform **API 35**, Build-Tools `35.0.0`, Gradle wrapper `8.13` (`android/gradlew.bat`) |
| **Python** | Python `3.11+` (standard library only, for `scripts/sync_brand.py`, `scripts/generate_sbom.py`, tests) |

---

## 2. Synchronize Brand & Configuration Constants

Before compiling desktop or Android:

```powershell
python scripts/sync_brand.py --check
```

Note: Public exports (`D:\Monitor`) replace production endpoints and OAuth Client IDs with neutral placeholders (`https://your-monitor-broker.workers.dev` and `YOUR_GOOGLE_*_CLIENT_ID`). Configure `config/brand.toml` and `config/google_*.json` (or `MONITOR_CLOUD_URL` / local LAN mode) if distributing custom builds. See [`SELF_HOSTING.md`](../SELF_HOSTING.md).

---

## 3. Build Windows Desktop & Native Glass Installer

### Run Quality & Test Gates
```powershell
Push-Location desktop
cargo fmt --all -- --check
cargo check --workspace --all-targets
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace --exclude monitor-tray --offline
cargo test -p monitor-tray --offline
Pop-Location
```

### Build `Monitor.exe` & `Monitor_Setup_v1.0.0.exe`
```powershell
# 1. Build desktop binary + uninstaller + Solid Zstd packer
Push-Location desktop
cargo build --release --locked -p monitor-tray --bin monitor
cargo build --release --locked -p monitor-installer --bin monitor-uninstaller --bin pack-payload
Pop-Location

New-Item -ItemType Directory -Path bin -Force | Out-Null
Copy-Item desktop\target\release\monitor.exe bin\Monitor.exe -Force

# 2. Fetch and SHA-256 verify bundled MinGit runtime (Windows)
powershell -ExecutionPolicy Bypass -File scripts\prepare_mingit.ps1

# 3. Pack Solid Zstd level-19 payload & compile strict release installer
.\desktop\target\release\pack-payload.exe (Get-Location).Path 19
$env:MONITOR_RELEASE_BUILD = '1'
cargo build --release --locked --manifest-path desktop\Cargo.toml -p monitor-installer --bin monitor-installer
```

Installer executable output: `desktop\target\release\monitor-installer.exe`.

---

## 4. Build Android Companion APK

### Debug APK (Testable on Emulator / Local Devices Immediately)
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.13.10002-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Push-Location android
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
Pop-Location
```
Output: `android\app\build\outputs\apk\debug\app-debug.apk`.

### Signed Production Release APK & AAB
Production release builds (`REQUIRE_RELEASE_SIGNING=true`) intentionally fail closed unless keystore environment variables are supplied:

```powershell
$env:REQUIRE_RELEASE_SIGNING = "true"
$env:RELEASE_KEYSTORE_PATH = "C:\path\to\your-release.jks"
$env:RELEASE_STORE_PASSWORD = "<store-password>"
$env:RELEASE_KEY_ALIAS = "<key-alias>"
$env:RELEASE_KEY_PASSWORD = "<key-password>"

Push-Location android
.\gradlew.bat testReleaseUnitTest lintRelease assembleRelease bundleRelease
Pop-Location

# Verify 16 KB page-size alignment, full RELRO, zipalign -P 16, and signature
powershell -ExecutionPolicy Bypass -File android\verify_16kb.ps1 `
  -Apk android\app\build\outputs\apk\release\app-release.apk
```
