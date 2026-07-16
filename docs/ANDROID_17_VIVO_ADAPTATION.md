# Android 17 / Vivo Adaptation Notes

Source: [vivo Android 17 开发者适配文档](https://dev.vivo.com.cn/documentCenter/doc/1010)

Related: [Android 16 / Vivo notes](./ANDROID_16_VIVO_ADAPTATION.md)

Fetched package: `docs/vivo-adaptation/1010-android-17/` (script: `scripts/fetch_vivo_adaptation_doc.py`)

Project Lumen currently ships with `compileSdk/targetSdk = 37` and `minSdk = 26`. This note tracks high-priority items from the Vivo Android 17 guide after the 2026-07-16 refresh.

## High priority for this app

### 1. Background audio hardening (all apps / high)
Android 17 requires foreground-visible state or a `mediaPlayback` FGS with While-In-Use capability for sustained audio output.

**Lumen decision**
- Keep reminder cues as short notification sonification only.
- `AudioService` uses `AudioAttributes.USAGE_NOTIFICATION` / `CONTENT_TYPE_SONIFICATION`.
- Custom cue playback is transient `MediaPlayer` with completion/error release.
- Do not introduce continuous background media playback without a `mediaPlayback` FGS.
- Notification channels intentionally avoid embedding long custom channel sounds.

### 2. Explicit URI grants for share/send (all apps / medium, prep for Android 18)
Do not rely on implicit URI grants for `ACTION_SEND` / `ACTION_SEND_MULTIPLE` / stream URIs.

**Lumen decision**
- Centralize stream sharing in `SecureShareIntents`.
- Always attach `ClipData` + `FLAG_GRANT_READ_URI_PERMISSION` for FileProvider shares and APK install views.
- Covered call sites: backup export, CSV/PDF/image export, update APK install intent.
- Plain-text shares (`EXTRA_TEXT` only) do not need URI grants.
- `lumen-crash` file-share path also attaches `ClipData` + grant flags.

### 3. Large-screen adaptation (targetSdk 37 / high)
Orientation / resize locks may be ignored on large screens; opt-out properties available on API 36 are ignored at targetSdk 37+.

**Lumen decision**
- Keep `resizeableActivity="true"`.
- `configChanges` includes orientation/screenSize/screenLayout/smallestScreenSize/keyboardHidden/uiMode on primary activities.
- No forced portrait orientation.
- No reliance on `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`.

### 4. cleartext / network security (targetSdk 37)
`usesCleartextTraffic` is on a deprecation path; prefer `networkSecurityConfig`.

**Lumen decision**
- `minSdk = 26` (>= 24), so Manifest no longer declares `usesCleartextTraffic`.
- Domain policy lives only in `res/xml/network_security_config.xml`.
- Base config and production hosts force `cleartextTrafficPermitted="false"`.
- Production hosts remain HTTPS-only (`eye.chloemlla.com`, `tts.chloemlla.com`).

### 5. Local network permission (targetSdk 37)
`ACCESS_LOCAL_NETWORK` becomes required for LAN/mDNS access when targetSdk >= 37.

**Lumen decision**
- App API traffic is public HTTPS, not LAN discovery / mDNS / SSDP.
- No `ACCESS_LOCAL_NETWORK` declaration unless a future feature needs LAN/mDNS.

### 6. Certificate Transparency default (targetSdk 37)
CT is default-enforced for targetSdk 37+ HTTPS connections.

**Lumen decision**
- Production endpoints use public CA certificates expected to carry SCTs.
- No private/self-signed production hosts that would require CT opt-out.
- Keep CT enabled (do not disable in network security config).

### 7. Dynamic native library loading (targetSdk 37)
`System.load` / external writable `.so` loading must be read-only.

**Lumen decision**
- Native bridge uses packaged `System.loadLibrary("lumen_security")` only.
- No runtime-downloaded native plugins / custom DCL path.

### 8. Loopback host protection (targetSdk 37)
Cross-app localhost communication needs `USE_LOOPBACK_INTERFACE`.

**Lumen decision**
- No cross-app localhost HTTP/socket server or client.
- No `USE_LOOPBACK_INTERFACE` declaration required.

## Already aligned

- `targetSdk/compileSdk = 37`
- Edge-to-edge enabled
- Foreground services declare typed FGS categories for timer/camera/overlay work
- Secure credentials use `MasterKey` + encrypted stores (low key volume, no key-spam pattern)
- Parcel obtain/recycle in Shizuku IPC is try/finally and not re-read after recycle
- No `Process.setThreadPriority` out-of-range usage found
- No SMS inbox OTP scraping / `READ_SMS` OTP path

## Out of scope / not currently relevant

| Doc item | Decision |
|---|---|
| NPU permission (`FEATURE_NEURAL_PROCESSING_UNIT`) | Not used |
| Trackpad pointer-capture absolute mode | No pointer-capture drawing surface |
| MessageQueue private-field reflection | No `mMessages` reflection / Looper hacks |
| SMS OTP delayed access | No SMS OTP auto-fill feature |
| Android developer verification (ADI) | Process/policy item for store/signing identity; not a code path |
| Media3 MediaSessionService background music | Not a product requirement today |
| Parcel custom Parcelable mismatch | No app-owned Parcelable IPC types found |
| static final reflection mutation / hotfix frameworks | No Tinker/Sophix-style final-field hacks |

## Verification checklist

- Reminder tones remain short notification sonification, not silent background media progress.
- Backup / export / APK install shares open with explicit FileProvider URI grants.
- Large phone / foldable / tablet layouts remain usable after rotation / multi-window.
- HTTPS API hosts continue to connect under default CT enforcement.
- No LAN-only feature is introduced without `ACCESS_LOCAL_NETWORK` + runtime UX.

## Refresh log

- 2026-07-16: Re-fetched doc 1010 via `scripts/fetch_vivo_adaptation_doc.py 1010 --scan-repo`, revalidated high-priority items, removed Manifest `usesCleartextTraffic` in favor of `network_security_config.xml` only.
