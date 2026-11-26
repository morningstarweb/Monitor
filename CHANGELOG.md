# Changelog

All notable changes to the **Monitor** project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-11-26

### Added
- **Universal Same-Gmail + Per-Device OTP/QR Verification Gate (`T3 Admin`):**
  - Replaced legacy `ACCESS REQUESTS` / `[Accept]` approvals in both Windows Desktop Tray and Android with a per-device verification flow.
  - Signing into Google on Android keeps the app locked at the **"Verify This Device"** gate (`Scan QR Code` or `6-Digit OTP`) until verified via LAN HTTP or remote Protocol v2 E2EE (`auth.verify_otp` in `T0Monitoring`).
  - Successful OTP/QR verification grants **`T3` (`Admin`)** immediately; Desktop Tray `Mobile & Devices` provides a clickable per-device **`T3 Admin` / `T2 Op`** tier toggle (`POST /local/devices/{id}/tier`) alongside fail-closed `Permissions` toggles (`allow_remote_terminal`, `allow_checkpoint_rollback`, etc.).
  - Blocked unverified (`T0`) and revoked devices from bypassing verification during cloud-to-LAN token upgrades.
- **Universal Watchdog & Automated Shadow Checkpoint Engine:**
  - Upgraded `monitor-agent` (`pipeline.rs`) to scan all registered Git repositories directly using lightweight working-tree content/mtime fingerprinting (`CheckpointEngine::working_status_fingerprint`), creating shadow Git checkpoints automatically for **Antigravity, Claude, Codex, Cursor, external AI agents, or manual edits** without spamming duplicate idle snapshots.
  - Enhanced `find_repo_path` (`detector.rs`) to resolve Windows `file_<drive>_<repo>` `--workspace_id` arguments, forward slashes, URL-encoded URIs, and `\\?\` verbatim prefixes; normalized repository path matching in `monitor-storage` (`list_checkpoints`).
  - Added a **`+ Save`** manual snapshot button and inline permission feedback inside Android `CheckpointsBottomSheet`.
- **Native Windows Installer Hardening (`monitor-installer`):**
  - Added GPU-independent Win32 GDI fallback window (auto-triggered on GPUI window failure and manually selectable via `--fallback-ui` / `--gdi`) plus `--help` usage dialog and non-zero exit codes on fatal failures.
  - Added crash-recovery `.monitor-install-tx.dat` transaction journal, bounded transient AV lock retries during Solid Zstd extraction, symlink/reparse checks, protected OS directory / long-path (`> 170` chars) write-probe validation, and strict allowlist manifest-less uninstallation.
- **Modular Rust Codebase & Pre-Release Compliance (`v1.0.0`):**
  - Refactored large single-file Rust modules across `monitor-installer`, `monitor-transport`, `monitor-tray`, `monitor-git`, `monitor-agent`, and `monitor-storage` into focused folder submodules (`<= 400` lines per `.rs` file).
  - Updated Android `versionCode` to `20100` (`versionName = "1.0.0"`) to prevent `INSTALL_FAILED_VERSION_DOWNGRADE` over pre-release builds, added actionable non-GMS/self-hoster guidance on Google Sign-In Status `10`, and aligned license/legal notices with `PolyForm-Noncommercial-1.0.0`.
- **Android Companion Status Notifications:**
  - Added high-priority `CHANNEL_COMPANION` notifications in `AlertNotificationManager` & `MainActivity` prompting PC linking before verification and tracking live `Connected` / `Offline` / `Connecting` companion status afterward.

### Fixed
- **Wi-Fi <-> 5G / WebRTC Network Handover Recovery:**
  - Fixed `DesktopConnectionObserver` `lastValidated` tracking so `onCapabilitiesChanged(VALIDATED)` reliably triggers `reconnect()`.
  - Updated `DualPathTransportManager`, `CloudRelayTransportV2`, and `LanTransportV2` to evict stale OkHttp connection pools (`evictMonitorHttpPool`), tear down dead WebRTC peers, call `promoteFallbackPath()`, preserve `networkEpoch` isolation, and dynamically resume LAN transport when upgrading back from 5G to Wi-Fi.
  - Updated `DashboardViewModel.loadDashboardData()` so successful live snapshots immediately clear stale `Offline` state.
- **UI & Settings Refinements:**
  - Removed editable `Broker Endpoint URL`, raw sync-frequency controls, and cryptographic/D1 jargon from Android Settings (`CloudflareBrokerSyncPanel`, `DeviceStationPanel`, `AccountProfilePanel`, `TelemetryPreferencesPanel`).
  - Fixed Home `ConnectedDeviceCard` (`PC Offline` / `Check >` no longer redirects to Login), empty projects card alignment, `Project Terminal` single-line layout, and `System` tab badges (`Processes` / `Logs`) & `ProcessesTabHeaderBanner` clipping.

---

## [2.0.2] - 2026-11-25

### Changed
- Replaced Inno Setup packaging with the **Native Glass Windows Installer** (`monitor-installer`, GPUI Kit) featuring Solid Zstd level-19 compression (`pack-payload`), slim `monitor-uninstaller`, and unified release automation via `scripts/one_click_release.ps1`.
- Completed Android terminal real-time output streaming polish, onboarding/designsystem refinements, and automated `sync_brand.py` alignment.

---

## [2.0.1] - 2026-11-23

### Security & Architecture
- Hardened Cloudflare OAuth PKCE loopback validation, D1 schema migration (`0006`), `strict_free` TURN fail-closed policy, Android Keystore `SecureStorage` (AES-256-GCM direct without deprecated `EncryptedSharedPreferences`), and DPAPI host secret sealing.
- Added GPUI Kit desktop control center flyout in `monitor-tray` with `Cloudflare`, `Mobile & Devices`, `Permissions`, and `Projects` management panels.

---

## [1.0.7] - 2026-11-21

### Added
- **Visual Git DAG Studio & Bézier Curve Layout:**
  - Replaced angular diagonal connectors with smooth, high-fidelity cubic Bézier curves inspired by VS Code Git Graph.
  - Dynamic per-row active lane indenting: commit messages sit snugly against the active branch lane without wasted visual gaps on linear branches.
  - Full commit file history inspection via `query_commit_files` (`git show -m --first-parent --name-status` and `--numstat`), detailing modified, added, and deleted files with per-file insertion/deletion line counts.
  - Dedicated line-by-line Diff Inspector bottom sheet featuring syntax-highlighted chunks, addition/deletion line numbers, and fast category filtering (Modified, Added, Deleted).
- **WebRTC Protocol v2 Full Parity:**
  - Added `git.commit_files` to `OperationTier::T1ReadRepository` in `monitor-core`, granting full permission to authenticated viewers.
  - Registered `git.commit_files` in `is_v2_operation` dispatch and `execute_v2_command` in `monitor-transport`.
  - Optimized `v2RequestIfCloud` to route directly over active WebRTC DataChannels, eliminating 3-second connect timeouts from attempting dead local LAN HTTP requests while remote.
- **Instant Network Handover & Seamless Reconnection:**
  - Registered dynamic `ConnectivityManager.NetworkCallback` in Android (`onAvailable`, `onLost`, `onCapabilitiesChanged`).
  - Added `forceReconnect()` to `DualPathTransportManager` and `V2TransportCoordinator` to immediately tear down dead Ktor sockets and stale `PeerConnection` instances upon Wi-Fi / Cellular transitions.
  - Reduced network switch recovery from 30-45 seconds down to 1-2 seconds.
  - Removed redundant 1500ms LAN wait in `DualPathRpc` when WebRTC DataChannel is already open.
- **Documentation Overhaul:**
  - Comprehensive rewrite of `README.md`, `docs/README.md`, `docs/architecture.md`, `docs/networking.md`, `docs/security.md`, `docs/development.md`, and `docs/release.md`.

---

## [1.0.4] - 2026-11-16

### Added
- **Multi-Transport Failover Hierarchy:**
  - Direct LAN V2 (`ConnectionPath.LAN_DIRECT` over port 9480) with line-rate (~1ms) telemetry and Git RPC.
  - WebRTC P2P DataChannel (`ConnectionPath.WEBRTC_P2P`) with background pre-warming while on LAN.
  - Cloudflare Realtime TURN Relay (`ConnectionPath.WEBRTC_TURN`) via `monitor-turn` for symmetric NAT traversal.
  - Cloudflare Edge HostRoom WSS Relay (`ConnectionPath.CLOUD_RELAY_WSS`) always-on fallback pipe.
- **Full Data Parity across all transports:** Git Workspace, Graph, Diff, Branches, Log, Staging, Commit, Push, Pull, PRs, and Remote Terminal now route seamlessly across LAN, WebRTC, and Cloud Relay using Noise IK E2EE.
- **Persistent Authentication:** App stays logged in across restarts and background kills for both Google Cloud Identity and LAN paired tokens.
- **Viewer Trust Tier Hardening:** Automatically grants T3 (Owner full access) to authenticated viewers for Git and Terminal commands.

---

## [1.0.0] - 2026-11-11

### Added
- **Windows System Tray App (`monitor-tray`):**
  - High-performance Rust-based tray using `tray-icon` and `winit` event loops with native Win32 `muda` menus.
  - Explorer.exe restart recovery via GUID-based tray icon restoration (`0x82df6cf3...`).
  - Single-instance enforcement via TCP socket binding on loopback port `9489`.
  - Background asynchronous I/O thread (`monitor-tray-io`) preventing UI freezing.
  - Submenu dynamic update optimization with signature caching.
  - Standalone bundled Git runtime fallback support.
- **Android Companion App (`android/`):**
  - "Dark Obsidian Glass" design system conforming to strict design tokens.
  - Real-time CPU, RAM, disk, battery, and network telemetry charts.
  - Remote Git workspace management: status, visual DAG graph, branch creation/checkout/deletion, stage/unstage, discard changes, and commits.
  - Biometric confirmation gating for destructive operations via AndroidX `BiometricPrompt`.
  - Seamless dual-mode connectivity: Direct LAN WebSocket (port `9480`) and Cloudflare Cloud Broker.
  - Google Sign-In with OAuth 2.0 OIDC ID token validation.
- **Cloudflare Edge Broker (`cloud/`):**
  - Edge serverless broker built on Cloudflare Workers and Cloudflare D1 Database.
  - 256-bit cryptographically secure session management.
  - Constant-time token verification protecting against timing attacks.
  - Fully parameterized SQL queries ensuring SQL injection immunity.
  - WebSocket relay bridge for real-time telemetry streaming across carrier-grade NATs.
- **Windows Packaging (`installer/`):**
  - Inno Setup 6 installer with per-user installation (`PrivilegesRequired=lowest`).
  - Native bundled MSVC CRT redistributable runtime DLLs and portable MinGit.
