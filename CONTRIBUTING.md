# Contributing to Monitor

Contributions are welcome for the Windows host, Android companion, and shared tooling.

## Before Opening a Pull Request

1. Read the [Architecture & System Design](docs/architecture.md), [Networking & Transport](docs/networking.md), and [Security & Cryptographic Architecture](docs/security.md) guides.
2. Start from a clean checkout (`git status --short --branch`). Note that `desktop/crates/monitor-installer/payload.zip` is a hash-verified development fixture (`payload.expected.sha256`); production builds replace it dynamically during release packaging.
3. Keep changes scoped to the owning Rust crate, Android module, or Cloudflare Worker route where practical.
4. Respect the desktop **Zero Panic Policy** (`unwrap()`, `expect()`, `panic!()`, `todo!()`, `dbg!()`, `print_stdout`/`stderr` denied workspace-wide via `desktop/Cargo.toml`) and Android UDF (`StateFlow`) architecture.
5. Protocol v2 wire changes require aligning both Rust (`monitor-core/src/protocol_v2.rs`), Kotlin (`ProtocolV2.kt`), and `docs/protocol-v2.schema.json`.
6. Update documentation when behavior, routes, or supported configuration changes.

## Code Organization

- **`desktop/crates/`:** 8-crate Rust workspace (`monitor-core`, `monitor-storage`, `monitor-telemetry`, `monitor-git`, `monitor-transport`, `monitor-agent`, `monitor-tray`, `monitor-installer`).
- **`android/`:** Clean Architecture Android project (`:app`, `:core:{common,model,network,designsystem}`, `:feature:{dashboard,gitworkspace,onboarding}`).
- **`cloud/` (Private Master Monorepo):** Cloudflare Worker routes (`cloud/src/routes/`), Durable Objects (`HostRoom`, `RateLimiterDO`, `QuotaReservationDO`), and D1 migrations (`0001..0006`).
- **`scripts/check_loc.py`:** Reports source files above 200 nonblank, noncomment lines for modularity review (note: Android Settings contract suites also verify Settings screen/ViewModel modularity).

## Secret & Artifact Hygiene

- Never commit real credentials, Cloudflare account IDs, Google `client_secret` files, release keystores, `.apk`/`.exe` installers, or machine-specific paths.
- Forks must configure their own Cloudflare Worker broker URL and Google OAuth client IDs (see [SELF_HOSTING.md](SELF_HOSTING.md) and [TRADEMARK.md](TRADEMARK.md)).
- Never attach binaries, installers, APKs, AABs, or keystore files to pull requests.

## Verification Gates

Run the relevant local checks listed in [docs/development.md](docs/development.md):

- **Rust:** `cargo fmt --all -- --check`, `cargo clippy --workspace --all-targets -- -D warnings`, `cargo test --workspace --all-targets` (inside `desktop/`).
- **Android:** `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug` (inside `android/`).
- **Cloud (when `cloud/` is present):** `npm ci && npm run check:all` and `npm run test:workerd` (inside `cloud/`).
- **Python Contracts / E2E:** `python tests/e2e/runner.py` and `python tests/test_empirical_m3_adversarial.py`.
- **Consolidated Windows Gate:** `powershell -ExecutionPolicy Bypass -File scripts/verify_gates.ps1`.
- **Secret Scan:** `powershell -ExecutionPolicy Bypass -File scripts/scan_secrets.ps1` (requires `gitleaks >= 8.28`).

GitHub Actions workflows (`.github/workflows/ci.yml` and `.github/workflows/verify.yml`) execute automated `gitleaks` secret scans, Rust checks, Android unit/lint checks, Python contract suites, Cloud checks (in the private monorepo), and SBOM generation.

- **Modular Codebase (`<= 400` lines/file):** Keep Rust modules under 400 lines (prefer focused submodules in folders) and preserve Android Settings file limits (`<= 200` lines).
- **Licensing & Contributor Grant:** Monitor is source-available under [PolyForm Noncommercial 1.0.0](LICENSE) (not OSI open source). By submitting a pull request or patch, you confirm that you have the right to submit the contribution and license it under PolyForm Noncommercial 1.0.0 while granting Morning Star Web non-exclusive commercial and distribution rights for official releases. Follow the [Code of Conduct](CODE_OF_CONDUCT.md) and submit focused, well-described commits.
