# Monitor Engineering Documentation

Authoritative technical documentation for **Monitor (v1.0.0)**. Source code, build manifests, and CI workflows are always ground truth.

---

## Core Engineering Docs (Public & Private)

| Document | Scope |
| --- | --- |
| [**Installation Guide**](INSTALLATION.md) | Step-by-step Windows setup (SmartScreen/Smart App Control, GDI `--fallback-ui`, `--silent`), SHA-256 verification, Android APK sideloading, and `T3 Admin` pairing. |
| [**Troubleshooting Guide**](TROUBLESHOOTING.md) | Fixes for firewall/port `9480`, Tailscale routing, Google Sign-In Status 10, non-GMS fallback, OEM battery Doze, and single-instance lock `9489`. |
| [**Build From Source**](BUILD.md) | Exact Rust Edition 2024, JDK 21, and Android SDK 35 prerequisites and reproducible local build steps. |
| [**Frequently Asked Questions**](FAQ.md) | Workstation Sentinel vs remote screen-capture clarification, PolyForm licensing, zero-telemetry scope, and offline/local-only behavior. |
| [**Roadmap**](ROADMAP.md) | Current v1.0.0 production baseline and planned v1.1+ enhancements. |
| [**Architecture & System Design**](architecture.md) | System topology, exact 8-crate Rust workspace, Android Clean Architecture modules, Edge Broker DOs/D1 schema, and SQLite WAL persistence. |
| [**Networking & Transport**](networking.md) | Quad-path failover (`LanTransportV2`, WebRTC P2P, strict-free/optional TURN, `HostRoom` WSS), `ConnectivityManager` handover recovery, and 8 KiB Protocol v2 chunking. |
| [**Security & Cryptographic Architecture**](security.md) | Zero-knowledge relay model, `Noise_IK_25519_ChaChaPoly_SHA256`, trust tiers (`T0`-`T3`), DPAPI / `AndroidKeyStore` storage, PKCE OAuth, and Job Object/MinGit confinement. |
| [**Development Workflow**](development.md) | Toolchains, Rust/Android/Cloud verification commands, `npm run dev` inspector, `verify_gates.ps1`, Zero Panic Policy, and modularity guidelines. |
| [**Release & Packaging**](release.md) | `scripts/one_click_release.ps1` pipeline, `monitor-installer` Solid Zstd packaging, MinGit bundling, Android signing + 16 KB check, and GitHub Actions CI. |
| [**Transport Contracts**](transport_contracts.md) | State machines, `EnvelopeV2` framing invariants, idempotency digest rules, and backpressure limits. |
| [**Protocol v2 JSON Schema**](protocol-v2.schema.json) | Transport-neutral wire envelope JSON Schema (`EnvelopeV2`, 28 `MessageType` variants). |

---

## Excluded Private Master Documents

Internal operational, Cloudflare financial-safety (`strict_free`), dual-repo governance, and archived incident records live only in the private master repository (`Monitor-Source-Code`). For public self-hosting guidance, see [Self-Hosting Guide](../SELF_HOSTING.md).
