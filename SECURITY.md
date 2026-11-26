# Security Policy

## Reporting a Vulnerability

Please **do not** open a public GitHub issue for suspected security vulnerabilities.

Send a private report to **[contact@morningstarweb.space](mailto:contact@morningstarweb.space)** (or `security@morningstarweb.space`) including:

- Affected component (Windows Desktop Host, Android Companion, Cloudflare Worker Broker, or Installer/Uninstaller).
- Monitor version (`config/brand.toml`) and environment details (Windows / Android version, connection route).
- Impact assessment and minimal reproduction steps.

Do **not** include live account tokens, Cloudflare API tokens, Google OAuth secrets, signing keys, or private repository files in initial reports.

## Response & Coordinated Disclosure Targets

- **Acknowledgment:** Within 48 hours (2 business days) of receipt.
- **Initial Triage Update:** Within 7 days of receipt.
- **Coordinated Disclosure:** We coordinate remediation and security advisories privately with the reporter before public disclosure. Good-faith security research following this private-first process will not face legal threats.

## Scope

Security reviews cover:

- Authentication, Google OAuth OIDC / PKCE loopback validation, per-device OTP/QR `T3 Admin` promotion, and permanent revocation tombstones.
- End-to-end encryption (`Noise_IK_25519_ChaChaPoly_SHA256`), replay protection, DataChannel symmetric encryption, Windows DPAPI key sealing, and Android Keystore `SecureStorage`.
- Four-tier command authorization (`T0`-`T3`), fail-closed permission toggles, Win32 Job Object terminal confinement, and MinGit path/argument sandboxing.
- Cloudflare Worker route security, `HostRoom` / `RateLimiterDO` / `QuotaReservationDO` isolation, and Protocol v2 framing.

See [docs/security.md](docs/security.md) for full implementation details. Out of scope: third-party provider outages, physical access on an already-compromised workstation, and social engineering.

## Supported Versions

| Version | Status | Notes |
| --- | --- | --- |
| `1.0.x` | Active Maintenance (`1.0.0` current release) | Primary supported series across Windows Host, Android, and Broker. |
| `< 1.0` | Unsupported | Upgrade to the `1.0.x` line. |
