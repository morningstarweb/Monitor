# Production Release, Packaging & Dual-Repo Distribution Guide

Official build, binary packaging, signing, and CI/CD procedures for **Monitor (v1.0.0)**.

---

## 1. Dual-Repository Release Model

| Repository | Purpose & Contents | Privacy / Security Rule |
| --- | --- | --- |
| **`Monitor-Source-Code`** *(Master)* | Full monorepo (`desktop/`, `android/`, `cloud/`, `installer/`, release tooling, real endpoints, internal ops docs). | **Strictly private.** All development and official binary/APK builds happen here via `release.bat`. |
| **`Monitor`** *(Public Client Export)* | Sanitized Telegram-style client mirror (`desktop/`, `android/`, public docs, assets) exported via `export_public.bat` (`scripts/export_public_repo.py`). | **Zero backend/secrets.** `cloud/`, keystores, `google_*.json`, and prod broker URLs are removed/masked and verified by the pre-flight leak guard. |

Official pre-built signed installers (`Monitor_Setup_v{version}.exe`) and Android APKs built from `Monitor-Source-Code` are distributed via GitHub Releases so end users connect automatically to production infrastructure without needing to compile or configure secrets.

---

## 2. Native Glass Windows Installer Packaging (`monitor-installer`)

Monitor distributes Windows builds via the custom Rust + GPUI Kit **Native Glass Windows Installer** (`monitor-installer`) bundling a Solid Zstd level-19 (`MONPAK01`) archive packed by `pack-payload`, alongside standalone `monitor-uninstaller`. Legacy Inno Setup definitions remain in `installer/monitor.iss` and `desktop/installer.iss`.

### 2.1 One-Click Multi-Platform Release (`release.bat`)

```powershell
# Synchronizes version & versionCode across brand.toml, Cargo, Gradle, .rc manifests,
# builds Monitor.exe + Monitor_Setup_v1.0.0.exe + monitor-v1.0.0.exe, and copies to Downloads.
powershell -ExecutionPolicy Bypass -File scripts/one_click_release.ps1 -DesktopOnly -Version "1.0.0" -VersionCode 10000
```
*(Omit `-DesktopOnly` when Android release keystore environment variables are configured to also sign and verify `app-release.apk`.)*

### 2.2 Step-by-Step Windows Packaging

```powershell
# 1. Build Monitor desktop executable and installer tools (requires valid payload fixture)
powershell -ExecutionPolicy Bypass -File scripts/package_windows.ps1
cargo build --release --locked --manifest-path desktop/Cargo.toml -p monitor-installer --bin monitor-uninstaller --bin pack-payload

# 2. Fetch and SHA-256 verify pinned MinGit 2.56.1.windows.1 into installer/tools/git/
powershell -ExecutionPolicy Bypass -File scripts/prepare_mingit.ps1

# 3. Pack Solid Zstd level-19 payload (creates payload.zip + payload.expected.sha256)
.\desktop\target\release\pack-payload.exe . 19

# 4. Build strict release installer (MONITOR_RELEASE_BUILD=1 rejects dev fixture)
$env:MONITOR_RELEASE_BUILD = "1"
cargo build --release --locked --manifest-path desktop/Cargo.toml -p monitor-installer --bin monitor-installer
```
*(Restore `desktop/crates/monitor-installer/payload.zip` and `payload.expected.sha256` with `git checkout -- ...` after local packaging to keep git clean; `one_click_release.ps1` automates this automatically.)*

### 2.3 Bundled Runtime Components
- **MinGit Portable (`v2.56.1.windows.1`):** Verified by SHA-256 via `scripts/prepare_mingit.ps1` and bundled inside `<app>\tools\git\` (GPL-2.0 source offer documented in [THIRD_PARTY_NOTICES.txt](../THIRD_PARTY_NOTICES.txt)).
- **MSVC CRT DLLs:** Runtime libraries (`vcruntime140*.dll`, `msvcp140*.dll`) staged in `installer/redist/`.
- **Install Paths & Artifacts:** Staged to `Monitor_App_Package/Monitor_Setup_v1.0.0.exe` and `bin/Monitor.exe`; installs per-user under `%LOCALAPPDATA%\Programs\Monitor`.

---

## 3. Android Release & Debug Packaging (`android/`)

```powershell
Push-Location android

# Validation debug build + unit tests + lint
.\gradlew.bat testDebugUnitTest testReleaseUnitTest lintDebug lintRelease assembleDebug --console=plain

# Signed release APK + AAB (requires RELEASE_* env vars; fails closed if missing)
$env:REQUIRE_RELEASE_SIGNING = "true"
.\gradlew.bat clean assembleRelease bundleRelease
Pop-Location

# Verify 16 KB ELF alignment + full RELRO
powershell -ExecutionPolicy Bypass -File android/verify_16kb.ps1 -ApkPath android\app\build\outputs\apk\release\app-release.apk

# Verify signing certificate SHA-256 fingerprint
powershell -ExecutionPolicy Bypass -File scripts/verify_android_release.ps1
```

### Required Release Signing Environment Variables
- `RELEASE_KEYSTORE_PATH`: Path to production `.keystore` / `.jks`.
- `RELEASE_STORE_PASSWORD`: Keystore password.
- `RELEASE_KEY_ALIAS`: Signing key alias.
- `RELEASE_KEY_PASSWORD`: Signing key password.
- `RELEASE_SIGNER_CERT_SHA256`: Expected SHA-256 signer fingerprint (for `verify_android_release.ps1` / CI).

---

## 4. Automated CI/CD Workflows (`.github/workflows/`)

- **`verify.yml`:** Runs on all pushes, PRs, and manual dispatch (`contents: read` only, zero secrets/deploy). Executes hash-pinned `gitleaks` worktree + history scan, Rust fmt/check/clippy/test, Cloud `npm run check:all` *(stripped automatically in public export)*, Python E2E + adversarial suites, and offline CycloneDX-ish SBOM generation.
- **`ci.yml`:** Runs on `main` pushes and PRs covering `secret-scan`, `rust-checks`, `cloud-checks`, `android-checks` (debug/release tests & lint), and `e2e-checks` (including scalability stress tests).
- **`build-artifacts.yml`:** Runs on version tags (`v*`) or manual dispatch to assemble Windows `Monitor_Setup_v{version}.exe` and signed Android APK/AAB artifacts *(excluded from the public export)*.
