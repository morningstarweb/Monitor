# Self-Hosting and Deployment Profiles: Monitor

Monitor supports three operational deployment profiles across local networks, Morning Star Web infrastructure, and independent self-hosted deployments.

## 1. Deployment Profiles

| Profile | Description | Cloud Dependency | Quota Policy | TURN Media Relay |
| :--- | :--- | :--- | :--- | :--- |
| **`local_only`** | Desktop Host + Android Companion on the same LAN / Tailscale network. No Google login required. | None | Not applicable | Not used |
| **`official_free`** | Morning Star Web managed Cloudflare Workers **Free** deployment (`monitor-cloud-broker`). | Cloudflare Workers + D1 + Durable Objects | `STRICT_FREE_POLICY` (`profile: "strict_free"`) | **Disabled by default** (`403 SERVICE_DISABLED`) |
| **`self_hosted`** | Third-party or enterprise operator deploying their own compatible broker & OAuth apps. | Operator-owned Cloudflare or compatible WebSocket relay | Custom operator quota policy | Optional, operator-owned (requires explicit egress monitoring) |

> **Security Invariant:** Across every profile, the broker acts as a **zero-knowledge relay** handling only ciphertext envelopes encrypted end-to-end with `Noise_IK_25519_ChaChaPoly_SHA256`.

---

## 2. `local_only` (Zero Cloud Setup)

1. Run the Windows desktop host (`monitor.exe` / `monitor-tray`). It listens on `0.0.0.0:9480` (configurable via `MONITOR_PORT`), restricted by `private_peer_only` middleware to loopback, RFC 1918 LAN, and Tailscale CGNAT (`100.64.0.0/10`) addresses.
2. Open the system tray flyout -> **Mobile & Devices** -> generate a QR code or 6-digit OTP.
3. On Android, choose **Scan QR Code** or enter the IP/port and 6-digit OTP manually to pair as `T3 Admin`.
4. All telemetry, Git studio views, checkpoints, and terminal runs flow directly over LAN (`LanTransportV2`) or direct WebRTC DataChannel.

---

## 3. `self_hosted` (Implementing a Compatible Broker & Rebranded Clients)

> [!NOTE]
> The public `morningstarweb/Monitor` source repository contains **100% of the Windows and Android clients**, installers, and Protocol v2 specifications, while Morning Star Web's proprietary Cloudflare Worker backend (`cloud/`) remains private. If you want zero third-party infrastructure, use **`local_only`** above (works out of the box over LAN or Tailscale with zero cloud setup). To operate your own **`self_hosted`** relay, build a backend compatible with Monitor's wire contracts and rebrand clients per [TRADEMARK.md](TRADEMARK.md):

1. **Brand Configuration (`config/brand.toml`):**
   - Copy `config/brand.example.toml` to `config/brand.toml`.
   - Customize `name`, `slug`, `application_id`, `namespace`, and colors (`min_sdk = 30`, `compile_sdk = 35`).
   - Run `python scripts/sync_brand.py` to regenerate `BrandConfig.kt` and `brand_config.rs`.
2. **Protocol v2 Broker Specification:**
   - Implement endpoints and hibernated WebSocket rendezvous conforming to [`docs/protocol-v2.schema.json`](docs/protocol-v2.schema.json), [`docs/transport_contracts.md`](docs/transport_contracts.md), and [`docs/networking.md`](docs/networking.md) (see `config/cloudflare.example.jsonc` for recommended Cloudflare Workers, D1 schema tables, `HostRoom` / `RateLimiterDO` / `QuotaReservationDO`, and TTL cron design).
   - Store OAuth secrets out-of-band (`wrangler secret put`); never embed secrets in config files or Git.
3. **Configure Client Endpoints & Google OAuth:**
   - Override the desktop broker URL via `MONITOR_BROKER_URL` or update desktop/Android client configuration (`desktop/crates/monitor-transport/src/cloud.rs` and `android/core/network/` + `android/feature/onboarding/`).
   - Register your own Android (`app.monitor` / custom package + release keystore SHA-1) and Desktop/Web OAuth 2.0 Client IDs in Google Cloud Console.
4. **Optional TURN Relay Warning:**
   - Managed WebRTC TURN egress incurs bandwidth billing beyond free allowances. Only enable TURN admission if you deploy an operator-owned TURN server with strict egress budgets.
