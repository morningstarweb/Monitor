# Trademark & Brand Policy — Monitor (Morning Star Web)

Copyright (c) 2026 Morning Star Web. All Rights Reserved.

## 1. Purpose
While the client source code in this repository is made publicly available for transparency, security auditing, and self-hosted compilation under the terms in `LICENSE`, the **trademarks, service marks, trade names, and brand assets** of **Morning Star Web** are **NOT** licensed under any open-source license.

This separation ensures that users can always verify whether an application, installer, APK, or cloud relay they are running is an authentic, untampered release from Morning Star Web.

## 2. Protected Trademarks & Brand Assets
The following names, identifiers, and visual assets are the proprietary trademarks and brand property of **Morning Star Web** (established under Common Law Trademark rights `™` and unfair-competition protections):

- **Brand & Product Names:** `"Monitor"`, `"Monitor Desktop"`, `"Monitor Mobile"`, `"Morning Star Web"`, `"MorningStar Web"`, and `"Monitor Cloud Broker"`.
- **Logos & Iconography:** All official icons, tray symbols, splash screens, and graphical artwork located in `assets/`, `installer/*.ico`, `installer/*.bmp`, and `android/**/res/mipmap*` / `drawable*`.
- **Official Identifiers & Infrastructure:**
  - Android Application ID (`app.monitor`)
  - Official Hosted Broker endpoints (`*.morningstarweb.space`, official production relay endpoints)
  - Official OAuth Client IDs and code-signing identities belonging to Morning Star Web.

## 3. Rules for Forks, Rebuilds & Redistribution
You are welcome to inspect, audit, modify, and compile this source code in accordance with `LICENSE`, **subject to the following strict branding and infrastructure rules**:

1. **Rebrand Modified or Self-Hosted Builds:**
   If you distribute a modified binary, installer, APK, or custom fork built from this source code, you **MUST**:
   - Change the application name, publisher name, logos, and icons (`config/brand.toml` and platform manifests) so users cannot confuse your release with an official Morning Star Web product.
   - Change the Android `applicationId` (e.g., to `com.yourdomain.monitor`) and generate your own signing key.
2. **No Unauthorized Cloud / Auth Reuse:**
   You **MUST NOT** point third-party or modified builds at official Morning Star Web relay servers, hosted APIs, or OAuth Client IDs. Self-hosted or forked builds must use your own broker infrastructure as documented in `SELF_HOSTING.md`.
3. **No Impersonation or Endorsement Claims:**
   You **MUST NOT** use "Monitor", "Morning Star Web", or any confusingly similar name or logo to imply official endorsement, affiliation, or certification by Morning Star Web.

## 4. Permitted Referential Use
You may accurately state in plain text that your independent software is *"based on Monitor source code by Morning Star Web"* or *"compatible with the Monitor v2 protocol"*, provided that your product name, branding, and visual identity remain clearly distinct.

## 5. Contact & Enforcement
Official releases are distributed exclusively through **Morning Star Web** (`https://morningstarweb.space`) and verified repositories under `https://github.com/morningstarweb` (`morningstarweb/Monitor` and `morningstarweb/Monitor-Releases`).

For brand permissions or trademark inquiries, contact: `contact@morningstarweb.space`.
