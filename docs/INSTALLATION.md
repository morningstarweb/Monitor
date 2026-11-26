# Monitor v1.0.0 — Installation Guide

Official release installers and APKs are published at [morningstarweb/Monitor-Releases](https://github.com/morningstarweb/Monitor-Releases/releases).

---

## 1. Verify Downloads Before Running (SHA-256)

Every release ships with a `CHECKSUMS.sha256` file. Verify integrity on Windows PowerShell before installing:

```powershell
Get-FileHash .\Monitor_Setup_v1.0.0.exe -Algorithm SHA256
Get-FileHash .\Monitor-Android-v1.0.0.apk -Algorithm SHA256
```

Compare the output hash against `CHECKSUMS.sha256`.

---

## 2. Windows 10 / 11 Host Installation

Monitor is a native Windows x64 tray service and background workstation sentinel (`Monitor.exe`) distributed with a Solid Zstd level-19 self-extracting installer (`Monitor_Setup_v1.0.0.exe`) containing bundled **MinGit 2.56.1** and required MSVC runtime libraries. **Administrator elevation is not required** — it installs cleanly per user under `%LOCALAPPDATA%\Programs\Monitor`.

### Handling Microsoft Defender SmartScreen & Smart App Control
Because `Monitor v1.0.0` is published without a paid Authenticode certificate under a zero-cost community distribution policy:

1. **Microsoft Defender SmartScreen ("Windows protected your PC"):**
   - Click **More info** -> verify the SHA-256 checksum matches the release notes -> click **Run anyway**.
2. **Windows 11 Smart App Control (Enforcement Mode):**
   - If Smart App Control blocks unsigned executables outright without a "Run anyway" button, verify the SHA-256 checksum first, then unblock the file properties or build locally from source via [`docs/BUILD.md`](BUILD.md).

### Interactive Install Modes & GPU Fallback
- **Standard Native Glass UI:** Double-click `Monitor_Setup_v1.0.0.exe`.
- **Automatic Graphics Fallback:** On VMs, Remote Desktop sessions, or GPUs without Direct3D 11 acceleration, if GPUI graphics initialization fails, Monitor Setup automatically falls back to a native Win32 GDI installer window instead of silently exiting.
- **Manual Classic Win32 GDI UI:**
  ```powershell
  .\Monitor_Setup_v1.0.0.exe --fallback-ui
  ```
- **Silent Automated Install / Custom Directory:**
  ```powershell
  .\Monitor_Setup_v1.0.0.exe --silent
  .\Monitor_Setup_v1.0.0.exe --silent --dir "C:\Tools\Monitor"
  .\Monitor_Setup_v1.0.0.exe --help
  ```

### Upgrading & Uninstalling
- Running a newer `Monitor_Setup_v*.exe` detects existing installs, terminates running Monitor/MinGit locks cleanly, preserves `%LOCALAPPDATA%\Monitor\monitor.db` pairings and revocations, and cleans stale files using transaction rollback protection.
- Uninstall via **Windows Settings -> Installed apps -> Monitor -> Uninstall**, or run `%LOCALAPPDATA%\Programs\Monitor\uninstall.exe` (`--clean` removes user data).

---

## 3. Android Companion Installation (`app.monitor`)

Requirements: **Android 11+ (`minSdk = 30`, `compileSdk/targetSdk = 35`)**, 16 KB page-size compatible.

1. Download the signed release `.apk` from [Monitor-Releases](https://github.com/morningstarweb/Monitor-Releases/releases) and verify its SHA-256 hash.
2. When tapped from Chrome or Files, Android prompts for **Install unknown apps** permission:
   - Tap **Settings** -> toggle **Allow from this source** ON -> navigate back and tap **Install**.
3. **Google Play Protect ("Unverified app"):**
   - Sideloaded APKs outside Play Store may display Play Protect's unverified prompt.
   - Monitor requests zero SMS, Contacts, Accessibility, or Location permissions. Tap **More details** -> **Install anyway** (or **Scan app** first if offered).
4. **Upgrading From Earlier Builds:**
   - `v1.0.0` sets `versionCode = 20100` (`versionName = "1.0.0"`), allowing clean upgrades over pre-release builds (`v2.0.1`–`v2.0.3`, `versionCode <= 20003`) while preserving hardware-backed `AndroidKeyStore` keys and host pairings — provided the same signing key is used.

---

## 4. First-Time Pairing (`T3 Admin` OTP / QR Gate)

Monitor uses a two-layer zero-trust authorization flow:
1. Click the **Monitor** icon in the Windows system tray -> open **Mobile & Devices**.
2. Choose **Quick QR** (for instant camera scan over local Wi-Fi / Tailscale) or sign in with the **same Google account** on both Windows and Android.
3. Unverified devices register at `T0` (read-only/restricted). Click the device in the Windows Tray to issue a **unique 6-digit OTP** (or scan the device-specific QR) and enter it on Android to promote the device to `T3 Admin`.
4. Grant remote capabilities explicitly inside Windows Tray -> **Permissions** (`Git`, `Terminal`, `Checkpoints`, `Delete / Kill`, `LAN`, `Cloud`). All destructive toggles default **OFF** (`fail-closed`).
