# Android 16 / Vivo Adaptation Notes

Source: [vivo Android 16 开发者适配文档](https://dev.vivo.com.cn/documentCenter/doc/832)

Project Lumen currently ships with `compileSdk/targetSdk = 37` (above Android 16 / API 36). This note tracks Vivo Android 16 guidance that still applies.

## High priority items for this app

### 1. Adaptive / large-screen layout (targetSdk 36+)
On displays >= 600dp, Android 16 can ignore orientation / resizability / aspect-ratio locks.

**Lumen response**
- No forced portrait orientation.
- `resizeableActivity="true"` on primary activities.
- `configChanges` includes orientation/screenSize/screenLayout/smallestScreenSize.
- `LumenPage` centers content with max width and wider gutters on tablet / foldable widths.

### 2. Predictive back migration (targetSdk 16+)
Predictive back system animations are enabled by default; `onBackPressed` / raw KEYCODE_BACK paths are obsolete.

**Lumen response**
- Application sets `android:enableOnBackInvokedCallback="true"`.
- Navigation interception uses Compose `BackHandler` (Activity `OnBackPressedDispatcher`), not `onBackPressed()`.
- Secondary destinations and WebView dismiss use `BackHandler`.

### 3. scheduleAtFixedRate backlog behavior (targetSdk 36+)
When app returns to a valid lifecycle, Android 16 only runs one backlog fixed-rate task instead of flushing the pile.

**Lumen response**
- No `ScheduledExecutorService.scheduleAtFixedRate` usage found.
- Timers/reminders use AlarmManager exact alarms + foreground service runtime, which is the preferred model.

### 4. elegantTextHeight deprecated
Android 16 ignores forcing non-elegant fonts.

**Lumen response**
- Compose typography already defines explicit `lineHeight` values.
- No `elegantTextHeight=false` dependency.

### 5. Health / body sensors permission migration
Heart-rate and related APIs move from `BODY_SENSORS` to `android.permission.health.*`.

**Lumen response**
- App does not use heart-rate / BODY_SENSORS APIs.
- No health-permission migration required.

### 6. Local network permission preparation
LAN/mDNS access will require nearby/local-network runtime permission in newer Android versions.

**Lumen response**
- Network usage is public HTTPS, not LAN discovery.
- No `NEARBY_WIFI_DEVICES` / `ACCESS_LOCAL_NETWORK` declaration unless a future feature needs LAN.

### 7. Intent redirection hardening
Android 16 strengthens default protection against intent redirection attacks.

**Lumen response**
- Notification / alarm PendingIntents use explicit component classes and package.
- Open API launch parsing only trusts action + sanitized string extras; no nested Intent extras.
- Open API binder path verifies caller permission and optional trusted signature digests.
- Share/install stream intents use explicit URI grants via `SecureShareIntents`.

### 8. ART / hidden API tooling
Avoid HiddenApiBypass and unsupported packers that rely on ART internals.

**Lumen response**
- No HiddenApiBypass dependency.
- No dpt-shell / commercial packer integration in this repository.

## Verification checklist

- Large phone / foldable / tablet: settings and home remain readable, no clipped fixed-portrait layouts.
- Predictive back: secondary pages animate back through `BackHandler` / NavController.
- Reminder alarms still schedule via explicit `AlarmReceiver` PendingIntents.
- Share backup / export / install APK still open with granted FileProvider URIs.
