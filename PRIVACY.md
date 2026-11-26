# Privacy and Data Handling: Monitor

**Organization:** Morning Star Web (Kolkata, West Bengal, India)  
**Grievance & Privacy Contact:** `contact@morningstarweb.space` | Phone: `+91 78649 23844`  
**Regulatory Alignment:** India DPDP Act 2023 (Section 12 Grievance Redressal), EU GDPR (Article 5 Principles), UK GDPR.

---

## 1. Data Collected and Storage Boundaries

| Data | Produced By | Storage / Transmission | Broker Visibility |
| --- | --- | --- | --- |
| CPU, RAM, disk capacity/free space, battery status, running process snapshot | Desktop host (`monitor-telemetry`) | E2EE (`Noise_IK_25519_ChaChaPoly_SHA256`) directly to paired Android viewer | Ciphertext envelopes only; never stored in cloud D1 |
| Git DAG topology, diffs, commit files, staged/unstaged changes | Bundled MinGit via desktop host (`monitor-git`) | Executed locally; streamed E2EE to paired viewer | Ciphertext envelopes only; source code is never uploaded to cloud |
| Terminal commands & streamed output | Desktop host (`terminal_handlers/`) | Executed locally in Win32 Job Object; streamed E2EE | Ciphertext envelopes only |
| Watchdog alerts & shadow Git checkpoints | Desktop host (`monitor-agent`) | Local SQLite WAL & Git refs (`refs/monitor/checkpoints/...`); E2EE notifications | Ciphertext envelopes only (optional `broker_alerts` metadata bridge if cloud alerts used) |
| Pairings, trust tiers (`T0`-`T3`), revocation tombstones, command ledger | Desktop host (`monitor-storage`) | Local SQLite (`%LOCALAPPDATA%\Monitor\monitor.db`) | Not stored on broker |
| Client tokens, Noise static private keys, host pins | Android (`SecureStorage.kt`) / Desktop (`key_storage/`) | Hardware-backed `AndroidKeyStore` AES-256-GCM (`monitor_secure_store_v1.bin`) / Windows DPAPI (`dpapi2:`) | Private keys never leave devices |
| Google identity metadata, device registry, short-lived tickets, sessions (SHA-256 hashed) | Cloud broker (optional out-of-home sync) | Cloudflare D1 relational database | Control-plane metadata only |
| Rate-limiter & quota lease counters | Cloud broker | Cloudflare Durable Objects (`RateLimiterDO`, `QuotaReservationDO`) with SHA-256 hashed actor keys | Operational control-plane only |

---

## 2. Zero-Surveillance & Local-First Guarantees

- **Zero Telemetry / Source Uploads:** Source files, commit histories, terminal outputs, and hardware snapshots are never uploaded to third-party analytics services or stored in Cloudflare D1 (`cloud/migrations/0001_zero_storage_telemetry.sql`).
- **No Ad SDKs or Commercial Trackers:** Verified free of advertising identifiers, behavioral tracking SDKs, or keystroke/screen surveillance.
- **No Crash-Upload Pipeline:** Monitor does not transmit background diagnostic dumps or crash reports.

---

## 3. Log Redaction & Local Data Retention

- **Log Redaction:** Sensitive URLs and token prefixes (`ghp_`, `github_pat_`, `glpat-`, `Bearer `) are redacted by `desktop/crates/monitor-git/src/operations/tests/redaction.rs` (`operations/mod.rs`). Local secret audits run via `scripts/scan_secrets.ps1` with `--redact`.
- **Unpair & Erasure:**
  - Unpairing/revoking a device immediately denies future access across LAN HTTP/WS and Cloud `HostRoom` relays (`revoked_at_utc_ms`).
  - Logging out on Android clears stored cloud session tokens and resets verification state via `TokenStorage.clearAccountData()`.
  - Uninstalling on Windows via `monitor-uninstaller` cleans installed binaries and optionally wipes `%LOCALAPPDATA%\Monitor` (`--clean` / `--wipe-data`).
- **Backups:** Maintainer SQLite/D1 operational backup procedures preserve revocation tombstones (`revoked_at_utc_ms IS NOT NULL`) so deleted devices cannot regain access.
