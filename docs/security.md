# Security and Cryptographic Architecture

**Monitor** enforces a defense-in-depth security model combining zero-knowledge edge relaying, hardware-backed local storage, four-tier operation authorization, and sandboxed Git/terminal execution.

---

## 1. Zero-Knowledge Edge Broker Design

```
[Android Companion] <--- Noise IK E2EE (Ciphertext Only) ---> [Windows Host]
         \                                                        /
          \--- [Cloudflare Worker & HostRoom Durable Object] ----/
```

1. **Zero Plaintext Exposure:** Hardware vitals, process lists, Git graphs/diffs, file contents, and terminal outputs are encrypted end-to-end before leaving the host or mobile client. Cloudflare D1 drops all historical telemetry columns (`0001_zero_storage_telemetry.sql`) and returns presence metadata only (`snapshot: null`).
2. **Hashed Session Tokens & Single-Use Tickets:** Cloud sessions are 64-hex bearer tokens stored as **SHA-256 digests** in D1 (`sessions`), compared via constant-time helpers (`timingSafeEqual`). Rendezvous tickets expire in **60 seconds** and are atomically consumed once (`UPDATE ... WHERE used_at IS NULL`).
3. **Origin & Route Isolation:** Strict CORS allowlisting (no wildcards), isolate + `RateLimiterDO` SHA-256-hashed rate limiting, and `private_peer_only` / `native_client_only` Axum middleware isolating Windows host loopback controls (`Origin` / `Sec-Fetch-Site` rejected).

---

## 2. Cryptographic Specifications (`Noise_IK_25519_ChaChaPoly_SHA256`)

| Component | Primitive | Implementation Details |
| --- | --- | --- |
| **Key Exchange** | Curve25519 (`X25519`) | `snow 0.9.6` on Windows desktop; constant-shape pure Kotlin RFC 7748 Montgomery ladder (`X25519.kt`) with low-order point clamping/rejection on Android API 30+. |
| **Symmetric Cipher** | `ChaCha20-Poly1305` | 256-bit keys + 128-bit Poly1305 authentication tag (`chacha20poly1305` on Rust; `NoiseSymmetricState` / `SecureSessionV2` on Android). |
| **Prologue Domain Separation** | Transcript Binding | `MONITOR_V3_2_LAN_V2` for direct LAN v2 sockets; `MONITOR_V3_2_CLOUD_V2` for Cloud `HostRoom` WSS relays. |
| **WebRTC DataChannel Keys** | Derived AEAD Session Keys | Independent `data_channel_send_key` / `data_channel_recv_key` transmitted inside Noise Handshake Msg 2, isolating SCTP DataChannel traffic (`12-byte` LE monotonic counter nonce). |
| **Replay Protection** | Non-Advancing Nonce Gate | Receive nonces advance **strictly after** valid AEAD authentication succeeds; tampered or replayed ciphertext never corrupts cipher counters. |

---

## 3. Four-Tier Operation Authorization (`T0`-`T3`) & RBAC

Every local HTTP and Protocol v2 command passes through `desktop/crates/monitor-transport/src/authorization.rs` (`authorize_operation`) and `monitor-core/src/protocol_v2.rs` (`classify_operation`):

```rust
pub fn classify_operation(operation: &str) -> OperationTier {
    match operation {
        "telemetry.poll" | "telemetry.subscribe" | "process.list" | "auth.verify_otp" => {
            OperationTier::T0Monitoring
        }
        "git.status"
        | "git.graph"
        | "git.diff"
        | "git.commit_files"
        | "git.branches"
        | "git.workspace"
        | "git.pull_requests"
        | "git.identity.get"
        | "projects.list"
        | "permissions.get"
        | "watchdog.sessions"
        | "watchdog.alerts"
        | "watchdog.checkpoints"
        | "terminal.setting.get" => OperationTier::T1ReadRepository,
        "git.stage"
        | "git.unstage"
        | "git.discard_unstaged"
        | "git.create_branch"
        | "git.switch_branch"
        | "git.fetch"
        | "git.pull"
        | "git.push"
        | "git.merge_branch"
        | "git.merge_all_branches"
        | "git.identity.set"
        | "watchdog.ack"
        | "watchdog.checkpoint_create"
        | "terminal.setting.set"
        | "watchdog.snooze"
        | "watchdog.checkpoint" => OperationTier::T2StateMutation,
        // Includes: process.kill, git.commit, git.delete_branch, git.hard_reset,
        // git.merge_pr / merge_pr, watchdog.kill, watchdog.checkpoint_rollback,
        // terminal.start, terminal.stop
        _ => OperationTier::T3Dangerous,
    }
}
```

### Verification, Permissions & Revocation Invariants
1. **Per-Device Verification Gate:** Google Sign-In alone leaves new Android devices capped at `T0` on cloud and unverified on onboarding until verified via per-device 6-digit OTP or QR (`POST /api/v1/auth/pair` or remote Protocol v2 `auth.verify_otp`). Desktop Tray **Mobile & Devices** supports toggling verified devices between `T3 Admin` and `T2 Operator` (`POST /local/devices/{id}/tier`).
2. **Fail-Closed Desktop Permission Flags:** Six local settings default to `'false'` in `002_fail_closed_permission_defaults` (`allow_local_network`, `allow_git_operations`, `allow_cloud_operations`, `allow_checkpoint_rollback`, `allow_delete_operations`, `allow_remote_terminal`).
3. **Permanent Revocation Tombstones (`revoked_at_utc_ms`):** Enforced across local revocation memory maps, SQLite `viewer_trust`/`devices`, `HostRoom` `revocation_tx` termination broadcasts, and `authorize_operation`. Standard reconnects/enrollments never clear `revoked_at_utc_ms`.

---

## 4. Client Hardware Keystores & Biometrics

1. **Android `SecureStorage.kt` (`filesDir/monitor_secure_store_v1.bin`):**
   Stores tokens, Noise private keys, verification flags, and TOFU host pins inside an atomic `"MONS"` v1 envelope encrypted with a non-exportable 256-bit **`AndroidKeyStore` (`AES/GCM/NoPadding`)** master key (`app.monitor.secure.master.v1`), synced via `fd.sync()` + atomic rename. Throws `SecureStorageException` on integrity errors rather than clearing state.
2. **Android App Lock & Biometrics (`AppLockGate` / `DestructiveActionGate`):**
   Fail-closed process-memory unlock gate (relocks across background transitions when configured) and hardware biometric prompt (`BIOMETRIC_STRONG | BIOMETRIC_WEAK | DEVICE_CREDENTIAL`) protecting checkpoint rollbacks, agent session kills, branch deletes/merges, and PR merges.
3. **Play Integrity Note:** Runtime hardware attestation via Google Play Integrity API is **not** integrated; root/tamper detection relies on OS Keystore isolation.
4. **Desktop DPAPI (`key_storage/`):**
   Host X25519 Noise private keys are sealed via Win32 `CryptProtectData` (`dpapi2:<hex>`). *(Engineering note: the local `cloud_account` OAuth metadata JSON inside desktop SQLite `settings` is currently stored plaintext at rest.)*

---

## 5. Host Containment & OAuth PKCE Hygiene

- **Git Path & Argument Confinement (`path_guard.rs` & `executor/`):** Canonical path prefix checking blocks `../` traversals, `.git`/`git~1` injection, Windows reserved filenames, and symlinks leaving registered repos. Git invokes with `CREATE_NO_WINDOW`, `core.hooksPath=/dev/null`, `protocol.allow=never` (whitelist `http`, `https`, `ssh`, `git`, `file=user`; `ext=never`), and 120s timeout. Protected branches (`main`, `master`) block remote deletion.
- **Win32 Job Object Terminal Confinement (`terminal_handlers/`):** Child shells (`cmd.exe` / `pwsh.exe` / `powershell.exe`) run inside a Job Object (`JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE`), capped at 32 sessions, 60s execution, and 128 KiB output.
- **RFC 8252 OAuth PKCE (`cloud/oauth/` & `google_exchange.ts`):** Desktop sign-in uses S256 PKCE loopback callbacks validated strictly on `127.0.0.1` / `[::1]` / `localhost` (`/oauth/callback`), avoiding embedded desktop client secrets.
- **Idempotent Execution Ledger (`command_ledger`):** Desktop SQLite WAL records `Sha256(operation + args)` digests and reconciles crashes cleanly (`EXECUTING` -> `UNKNOWN_AFTER_CRASH`) without re-executing destructive operations.
