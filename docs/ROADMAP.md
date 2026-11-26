# Monitor Roadmap

## `v1.0.0` (Current Production Baseline)
- Native Rust Edition 2024 Windows host (`monitor-tray` GPUI Kit 332x400 glass flyout, 8 workspace crates)
- Native Glass Solid Zstd level-19 Windows installer (`monitor-installer`) with automatic Win32 GDI fallback (`--fallback-ui`) & clean transaction rollback
- Android Kotlin 2.1 Compose companion (`API 30-35`, 16 KB page-size verified, hardware `AndroidKeyStore` `"MONS"` sealing)
- `Noise_IK_25519_ChaChaPoly_SHA256` end-to-end encryption across LAN (`:9480`), WebRTC P2P (`SCTP DataChannel`), and Cloudflare `HostRoom` relay
- `T3 Admin` OTP / QR verification gate & fail-closed capability permissions

---

## Planned Near-Term Releases (`v1.1+`)
- **Signed Windows Distribution:** Acquire Azure/Microsoft Trusted Signing or EV Authenticode certificates to eliminate Defender SmartScreen prompts cleanly.
- **Google Play Store Listing:** Publish signed AAB track alongside direct verified APK downloads in `morningstarweb/Monitor` Releases.
- **Full Desktop Metadata Encryption:** Extend DPAPI protection in `monitor-storage` from `host_noise_private_key` to `cloud_account` local metadata JSON.
- **Additional Watchdog Rules & Custom Profiles UI:** Manage custom agent matchers and cooldown rules directly inside the Windows Glass Flyout.
- **Optional Cross-Platform Host Exploration:** Evaluate Linux daemon parity (`sysinfo` + `tokio` + Noise IK transport) cleanly decoupled from Windows-only tray APIs.
