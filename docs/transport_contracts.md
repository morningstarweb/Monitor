# Transport Contracts and State Machines (Protocol v2)

Reference contract specification aligned with `desktop/crates/monitor-core/src/protocol_v2.rs`, [networking.md](networking.md), and [protocol-v2.schema.json](protocol-v2.schema.json).

---

## 1. Transport Paths & Profile Availability

| Path | Transport | `local_only` | `official_free` (`strict_free`) | `self_hosted` |
| --- | --- | --- | --- | --- |
| **LAN v2** | HTTP/WS on `:9480` (`LanTransportV2`) | Yes | Yes | Yes |
| **Direct ICE** | WebRTC DataChannel via STUN (`webrtc-rs` / `stream-webrtc-android`) | Yes | Yes (primary remote path) | Yes |
| **TURN Relay** | WebRTC via operator / Cloudflare TURN | Not used | **Disabled (`403 SERVICE_DISABLED`)** | Optional, operator-owned; budget-gated |
| **WSS Relay** | `HostRoom` Durable Object / compatible WS relay | Not used | Yes (signaling & fallback) | Yes |

Selection priority order: **LAN -> Direct ICE -> TURN (if enabled) -> WSS**. `DualPathTransportManager` races transports cleanly and maintains WSS signaling for ICE establishment and network handover recovery.

---

## 2. Common Envelope Contract (`EnvelopeV2`)

Every Protocol v2 frame conforms to `docs/protocol-v2.schema.json`:

| Field | Type | Contract |
| --- | --- | --- |
| `protocol_version` | u32 | Constant `2`; mismatches are rejected. |
| `session_id` | UUID | Logical session identifier across paths. |
| `message_id` | UUID | Per-message unique trace identifier. |
| `epoch` | u64 | Monotonically increments per reconnect/network handover; stale-epoch frames are ignored. |
| `stream` | enum | `control`, `telemetry`, or `bulk`. |
| `type` | enum | 28 defined types (`HELLO`, `HELLO_ACK`, `CAPABILITIES`, `PING`, `PONG`, `SESSION_CLOSE`, `ERROR`, `CREDIT`, `SNAPSHOT`, `DELTA`, `RESYNC_REQUEST`, `WATCHDOG_ALERT`, `PROJECTS_REQUEST`, `PROJECTS_RESULT`, `GIT_GRAPH_REQUEST`, `GIT_GRAPH_RESULT`, `GIT_DIFF_REQUEST`, `GIT_DIFF_CHUNK`, `GIT_DIFF_END`, `COMMAND_REQUEST`, `COMMAND_ACCEPTED`, `COMMAND_REJECTED`, `COMMAND_PROGRESS`, `COMMAND_RESULT`, `COMMAND_STATUS_REQUEST`, `COMMAND_STATUS_RESULT`, `PATH_PREPARE`, `PATH_COMMIT`). Runtime RPC dispatches via `COMMAND_REQUEST` with `command_type` strings. |
| `seq` / `ack` | u64 | Stream send sequence and cumulative acknowledgment counter. |
| `deadline_ms` | u64 | Expiration boundary preventing stale queued execution. |
| `command_id` | UUID? | Stable idempotency key for command execution & correlation (`V2RequestCorrelator`). |
| `payload` | JSON? | Body subject to 16 KiB frame limit before chunking. |

---

## 3. Path State Machines

### 3.1 LAN v2 Path
```
DISCONNECTED --local pair/OTP--> PAIRED --TOFU pubkey + Noise IK--> OPEN
OPEN --revocation / 401--> CLOSED (denied before any privilege escalation)
OPEN --network change--> EVICT_POOL -> COOLDOWN (8s HTTP / reconnect)
```
Revocation (`revoked_at_utc_ms`) is permanent across restarts (`lan_v2_tests.rs::test_revoked_lan_v2_viewer_cannot_reconnect`).

### 3.2 Direct ICE / WebRTC DataChannel
```
IDLE --signaling ready--> GATHERING (STUN) --candidate pair--> CHECKING --open--> OPEN
OPEN --network lost (`DesktopConnectionObserver`)--> TEARDOWN (`promoteFallbackPath`) -> RE-RACE
```
`WebRtcDataChannelClient` caps write buffering at 256 KiB on Android (`512 KiB` on Rust host) and drops dead peer handles immediately on connectivity transitions (`120ms` debounce).

### 3.3 WSS Relay (`HostRoom`)
```
IDLE --60s single-use ticket--> WS_UPGRADE --Noise IK (`PROLOGUE_CLOUD`)--> RELAYING <-> HIBERNATED
```
`HostRoom` caps frames at `16,400` bytes (`16 KiB + 16B` AEAD tag) and concurrent viewers at 5 per host.

### 3.4 TURN Relay
```
strict_free: POST /api/v2/webrtc/turn-credentials -> 403 SERVICE_DISABLED (fail-closed)
self_hosted: analytics <=15m old & <600 GB & QuotaReservationDO lease N<=5 -> temporary ICE creds
```

---

## 4. Chunking & Fragmentation Contract

1. Maximum unfragmented plaintext frame: **`16,384` bytes (`MAX_PLAINTEXT_FRAME_SIZE`)**.
2. Larger envelopes split (`split_v2_envelope`) into **8 KiB (`CHUNK_BYTES = 8192`)** base64 chunks wrapped in `{ "_frame_chunk": true, "chunk_id", "index", "count", "data" }`.
3. Receiver (`EnvelopeChunkAssembler` on Android; matching assembler on Rust) enforces **max 512 chunks**, **4 MiB** max assembled payload, and **30s** assembly TTL. Oversized or expired assemblies fail with explicit error.

---

## 5. Idempotency & Replay Invariants

- Desktop `command_ledger` persists `Sha256(operation + args)` (`canonical_params_hash`) in SQLite WAL and converts interrupted `EXECUTING` tasks to `UNKNOWN_AFTER_CRASH` on startup.
- Cloud `device_commands` enforces tenant-namespaced `UNIQUE` `idempotency_key` (`0003_idempotency.sql`).
- Noise IK receive counters advance **only after successful ChaCha20-Poly1305 AEAD verification**.
- Control commands, alerts, and revocations are never silently dropped for backpressure; telemetry snapshots coalesce latest-first (`TelemetryBus` 1 MiB budget).

---

## 6. Verification References

- Wire Schema: [docs/protocol-v2.schema.json](protocol-v2.schema.json)
- Frame Limits, Handshake & Tamper Tests: `desktop/crates/monitor-transport/src/session_crypto/tests/{frame_limits,handshake_roundtrip,tampered_ciphertext}.rs`
- LAN v2 & Revocation Tests: `desktop/crates/monitor-transport/tests/lan_v2_tests.rs`
- Android Chunk & Request Correlation Tests: `android/core/network/src/test/java/app/monitor/core/network/`
