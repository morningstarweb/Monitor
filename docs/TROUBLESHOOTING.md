# Monitor: Troubleshooting Guide

Solutions for common Windows setup, networking, pairing, Google OAuth, and Android background issues.

---

## 1. Windows Installer Launches and Exits or Shows Classic UI

- **Symptom:** Running `Monitor_Setup_v1.0.0.exe` inside a VM, Remote Desktop (RDP), or older GPU without Direct3D 11 triggers a Win32 fallback notice or opens the classic Win32 GDI setup wizard instead of the translucent glass wizard.
- **Cause:** `gpui-kit` requires Direct3D hardware/WARP initialization. When unavailable, `monitor-installer` falls back automatically to Win32 GDI.
- **Fix:** Complete installation through the fallback Win32 GDI window, force it explicitly with `--fallback-ui`, or run `.\Monitor_Setup_v1.0.0.exe --silent`.

---

## 2. Android Cannot Reach Windows Host Over Local Wi-Fi (LAN)

1. **Check the Windows Firewall Profile:**
   - Ensure your PC Wi-Fi network is set to **Private** (not Public) in Windows Settings, or allow inbound TCP on port **`9480`** on private networks for `Monitor.exe`.
2. **Confirm "Local Network (Wi-Fi)" Toggle is Enabled:**
   - Open Windows Tray -> **Permissions** -> ensure **Local Network (Wi-Fi)** is toggled **ON**.
3. **Verify Port `9480` Is Not Occupied:**
   ```powershell
   netstat -ano | findstr :9480
   ```
   Monitor enforces `private_peer_only`, refusing public non-RFC1918 clients (`127.0.0.0/8`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `100.64.0.0/10` Tailscale).
4. **No Multicast Needed:**
   - Monitor does **not** use mDNS (`5353/udp`). If router AP isolation blocks subnet peer-to-peer traffic, use **Tailscale** (`100.64.0.0/10`) or **Cloud Mode**.

---

## 3. Google Sign-In Errors (`Status 10` / `Developer Error` / Missing Devices)

### Android Status `10` (`DEVELOPER_ERROR`)
- **Cause:** The APK signature SHA-1 does not match the SHA-1 registered in the OAuth 2.0 Android client inside Google Cloud Console (common when compiling your own APK without registering your keystore's SHA-1).
- **Fix:**
  - If you downloaded the official APK from `morningstarweb/Monitor` Releases, check you have the latest build matching `CHECKSUMS.sha256`.
  - If building from source, register your keystore SHA-1 in your own Google Cloud OAuth project ([`SELF_HOSTING.md`](../SELF_HOSTING.md)) OR tap **Use Local Wi-Fi / Tailscale / Manual QR** on Android, Local LAN and Noise IK E2EE work 100% offline with zero Google Services!

### PC and Phone Signed In, But Devices Do Not Appear in Cloud Mode
- **Cause:** Desktop and Android are signed into **different** Google accounts, or Desktop Tray **Cloud Remote Relay** toggle is OFF.
- **Fix:** Verify the exact same Google email address is shown in both Desktop Tray -> **Cloud** and Android -> **Settings -> Account**, and enable **Cloud Remote Relay**.

---

## 4. "Permission Denied" / `T0` / `403` When Running Git, Terminal, or Rollback

Monitor defaults **all remote mutations OFF** (`fail-closed`):
1. Ensure your Android device completed the **6-digit OTP / QR verification** (`T3 Admin` or `T2 Op` badge visible in Desktop Tray -> **Mobile & Devices**; note **Terminal** requires `T3 Admin`).
2. Open Windows Tray -> **Permissions** and explicitly enable the capability needed (`Git Operations`, `Remote Terminal`, `Checkpoints & Rollback`, `Process Kill / Destructive`).

---

## 5. Android Session Drops When Screen Turns Off (Xiaomi / OPPO / Vivo / Samsung)

- **Cause:** Aggressive OEM battery optimization kills foreground services (`ActiveSessionService`).
- **Fix:** Open Android Settings -> **Apps -> Monitor -> Battery** -> select **Unrestricted / Don't optimize** (and enable **Autostart** on MIUI/HyperOS/ColorOS).

---

## 6. "Monitor Is Already Running" / Single-Instance Lock

- Monitor binds `127.0.0.1:9489` to enforce a single instance and prevent SQLite WAL conflicts.
- Check the Windows system tray overflow (`^`) icon or end stale `Monitor.exe` processes before launching manually.
