# Frequently Asked Questions (FAQ)

### Is Monitor a remote desktop screen-sharing tool like RDP, VNC, or RustDesk?
No. Monitor does **not** stream video frames or mirror your monitor. It is a **Remote Development & Workstation Sentinel**:
- Live hardware telemetry (per-core CPU, RAM/swap, storage capacities, top processes, battery status)
- Full 32-lane interactive Git DAG studio, syntax diff inspector, branch management, and GitHub PR merging
- Bounded remote command execution inside Win32 Job Objects
- Autonomous background watchdog for AI coding agents with shadow Git safety checkpoints

### Is Monitor open source?
Monitor is **Source-Available** under the [**PolyForm Noncommercial License 1.0.0**](../LICENSE). Individuals, students, researchers, and non-profits can inspect, compile, modify, and self-host the client code free of charge for noncommercial purposes. Commercial usage, SaaS hosting, or resale requires a commercial license from **Morning Star Web** (`contact@morningstarweb.space`).

### Does Monitor upload my source code or telemetry to the cloud?
No. Git repositories, diffs, terminal output, and hardware metrics are executed and stored locally on your machine. When using the optional remote Cloudflare broker, all session payloads travel inside **`Noise_IK_25519_ChaChaPoly_SHA256`** end-to-end encrypted envelopes, the relay never sees plaintext telemetry, files, or commands.

### Why didn't Monitor detect GPU temperatures or network bandwidth?
Monitor intentionally reports only hardware counters verified on Windows without kernel drivers (`sysinfo` + Win32 `GetSystemPowerStatus`): CPU, RAM/swap, mounted disk space, running processes, and battery level. Synthetic GPU, fan RPM, or unverified metrics are never fabricated.

### Does Monitor use mDNS or broadcast traffic on my network?
No. Monitor uses local subnet detection (`127.0.0.0/8`, RFC1918 LAN), Tailscale CGNAT (`100.64.0.0/10`), QR codes, and Cloudflare rendezvous presence, no UDP 5353 mDNS multicasting.

### Can I use Monitor completely offline without a Google account?
Yes. Select **Use Local Wi-Fi / Tailscale / Manual QR** on Android. Your PC and phone exchange Noise IK keys directly over local Wi-Fi or Tailscale with zero internet dependency.

### Why is `cloud/` excluded from the public repository?
The public `morningstarweb/Monitor` repository contains the complete client source code (`desktop/`, `android/`, installers, tests, docs) under PolyForm Noncommercial 1.0.0. Proprietary managed broker code and production secrets stay private in Morning Star Web's internal monorepo. See [`SELF_HOSTING.md`](../SELF_HOSTING.md) for deployment modes.
