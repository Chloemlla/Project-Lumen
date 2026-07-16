# Android 17 / Vivo Adaptation Notes

Source: [vivo Android 17 开发者适配文档](https://dev.vivo.com.cn/documentCenter/doc/1010)

Related: [Android 16 / Vivo notes](./ANDROID_16_VIVO_ADAPTATION.md)

Project Lumen currently ships with `compileSdk/targetSdk = 37`. This note tracks the high-priority items from the Vivo guide and the repo response.

## High priority for this app

### 1. Background audio hardening (all apps / high)
Android 17 requires foreground-visible state or a `mediaPlayback` FGS with While-In-Use capability for sustained audio output.

**Lumen decision**
- Keep reminder cues as short notification sonification only.
- `AudioService` uses `AudioAttributes.USAGE_NOTIFICATION` / `CONTENT_TYPE_SONIFICATION`.
- Do not introduce continuous background media playback without a `mediaPlayback` FGS.
- Notification channels intentionally avoid embedding long custom channel sounds.

### 2. Explicit URI grants for share/send (all apps / medium, prep for Android 18)
Do not rely on implicit URI grants for `ACTION_SEND` / `ACTION_SEND_MULTIPLE`.

**Lumen decision**
- Centralize stream sharing in `SecureShareIntents`.
- Always attach `ClipData` + `FLAG_GRANT_READ_URI_PERMISSION` for FileProvider shares and APK install views.
- Updated: backup export, CSV/PDF/image export, update APK install intent.

### 3. Large-screen adaptation (targetSdk 37 / high)
Orientation / resize locks may be ignored on large screens; preserve UI state across config changes.

**Lumen decision**
- Keep `resizeableActivity="true"`.
- Add `configChanges` for orientation/screenSize/screenLayout/smallestScreenSize/keyboardHidden/uiMode on primary activities.
- Existing settings section expansion persistence remains useful after process recreation.

### 4. cleartext / network security (targetSdk 37)
`usesCleartextTraffic` is on a deprecation path; prefer `networkSecurityConfig`.

**Lumen decision**
- Keep cleartext disabled globally.
- Domain policy lives in `res/xml/network_security_config.xml`.
- Production hosts remain HTTPS-only.

### 5. Local network permission (targetSdk 37)
`ACCESS_LOCAL_NETWORK` becomes required for LAN/mDNS access when targetSdk >= 37.

**Lumen decision**
- App API traffic is public HTTPS (`eye.chloemlla.com`, `tts.chloemlla.com`), not LAN discovery.
- No `ACCESS_LOCAL_NETWORK` declaration unless a future feature needs LAN/mDNS.

## Already aligned

- `targetSdk/compileSdk = 37`
- No forced portrait locks
- FileProvider for shared/exported files
- Edge-to-edge enabled
- Foreground services declare typed FGS categories for timer/camera/overlay work

## Out of scope / not currently relevant

- NPU permission (`FEATURE_NEURAL_PROCESSING_UNIT`) — not used
- Loopback host protection (`USE_LOOPBACK_INTERFACE`) — no localhost IPC server
- Parcel recycle hardening / FileUtils mode string calls — no direct usage found
- Media3 MediaSessionService background music — not a product requirement today
