# Development Workflow and Guidelines

Setup, local runbooks, verification commands, and coding conventions for **Monitor (v1.0.0)**.

---

## 1. Toolchain Requirements

| Subsystem | Requirement | Verified Versions | Notes |
| --- | --- | --- | --- |
| **Desktop Host** | Rust Stable (`x86_64-pc-windows-msvc`) | Rust `1.85+` (verified `1.100`), Edition 2024 | Requires VS C++ Build Tools (x64). Static CRT (`+crt-static`) set in `desktop/.cargo/config.toml`. |
| **Android Client** | JDK 21 + Android SDK 35 | Gradle `8.13`, AGP `8.8.2`, Kotlin `2.1.0` | `minSdk = 30`, `compileSdk = 35`, `targetSdk = 35`. Use Android Studio JBR or JDK 21 (`JAVA_HOME`). |
| **Cloud Broker** | Node.js LTS + npm | Node.js `22`, Wrangler `4.158+` | Workerd local integration + D1 migrations (private `cloud/` directory). |
| **Python Tooling** | Python 3.11+ | Python `3.11+` (`tomllib` stdlib) | Zero external dependencies for `sync_brand.py`, `generate_sbom.py`, `export_public_repo.py`, E2E suites. |

---

## 2. Fast-Start Development & Verification Commands

### 2.1 Live Dev Inspector & Launchers
- **`npm run dev`** (`scripts/dev.mjs`): Probes port `9480`, launches desktop host via Cargo if stopped, prints LAN/Tailscale IPs, Noise key, and opens `http://localhost:9480/dev` live inspector (`--no-open` / `--check-only` supported).
- **`scripts/dev.ps1`**: Terminates conflicting host instances and launches `cargo run -p monitor-tray --bin monitor` (`-Release` supported).
- **`scripts/dev_installer.ps1`**: Launches `monitor-installer` GUI (`-Uninstall`, `-Release`).
- **`scripts/deploy_android.ps1`**: Builds debug APK and installs via ADB onto paired Android hardware (`-SkipBuild` supported).

### 2.2 Desktop Host Verification (`desktop/`)

```powershell
Push-Location desktop
cargo fmt --all -- --check
cargo check --workspace --all-targets
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace --exclude monitor-tray --offline
cargo check -p monitor-tray --all-targets --offline
cargo build --release --locked
Pop-Location
```
*(Note: `--exclude monitor-tray` avoids MSVC 4GB PDB `LNK1140` during unoptimized test linking when running full test binaries.)*

### 2.3 Android Companion Verification (`android/`)

```powershell
Push-Location android
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

.\gradlew.bat testDebugUnitTest testReleaseUnitTest lintDebug lintRelease assembleDebug
Pop-Location
```
To verify 16 KB ELF page alignment & full RELRO across `.so` libraries:
```powershell
powershell -ExecutionPolicy Bypass -File android/verify_16kb.ps1 -ApkPath android\app\build\outputs\apk\debug\app-debug.apk
```

### 2.4 Cloudflare Broker (`cloud/`: Private Master Repository)

```powershell
Push-Location cloud
npm ci
npm run check:all      # tsc + eslint + biome + prettier + node --test (80+ tests)
npm run test:workerd   # Real Workerd + D1 + SQLite DO integration suite
npm run dev            # Local Wrangler worker runtime
Pop-Location
```

### 2.5 Python Contract, Brand Sync, Secret & Export Checks

```powershell
# Verify config/brand.toml matches generated BrandConfig.{rs,kt}
python scripts/sync_brand.py --check

# Run Tiers 1-4 E2E Contract Suite & Statutory/Symmetry Adversarial Suites
python tests/e2e/runner.py
python tests/test_empirical_m3_adversarial.py

# Consolidated offline gate runner (logs exact outputs to %TEMP%\monitor-verify-gates)
powershell -ExecutionPolicy Bypass -File scripts/verify_gates.ps1

# Fail-closed gitleaks audit (requires gitleaks >= 8.28)
powershell -ExecutionPolicy Bypass -File scripts/scan_secrets.ps1

# Export sanitized Telegram-style public repository copy + Pre-Flight Leak Guard
export_public.bat
```

---

## 3. Code Quality & Architecture Conventions

### 3.1 Rust Standards (`desktop/`)
- **Zero Panic Policy (`desktop/Cargo.toml`):** `unwrap_used`, `expect_used`, `panic`, `todo`, `unimplemented`, `dbg_macro`, `print_stdout`, `print_stderr` are set to `"deny"` workspace-wide; handle errors cleanly via `Result`.
- **Async Discipline:** Offload synchronous Git/hashing/IO tasks with `tokio::task::spawn_blocking`.
- **Bounded Frames:** Respect `MAX_PLAINTEXT_FRAME_SIZE` (16 KiB) and split larger responses using `split_v2_envelope`.

### 3.2 Kotlin Standards (`android/`)
- **UDF State:** Expose read-only `StateFlow<UiState>` from ViewModels; isolate async lifetimes to structured scopes cleared in `onCleared()` / `close()`.
- **Settings Modularity:** `tests/test_empirical_m3_adversarial.py` strictly enforces `<= 200` lines across Settings composables and `SettingsViewModel.kt`.
- **General File Length Guideline:** `scripts/check_loc.py` flags files exceeding 200 nonblank, noncomment SLOC as a review heuristic.
