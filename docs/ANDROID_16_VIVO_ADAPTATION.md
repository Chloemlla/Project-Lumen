# Android 16 / Vivo Adaptation Notes

Source: [vivo Android 16 开发者适配文档](https://dev.vivo.com.cn/documentCenter/doc/832)

Fetched package: `docs/vivo-adaptation/832-android-16/` (script: `scripts/fetch_vivo_adaptation_doc.py`)

Project Lumen currently ships with `compileSdk/targetSdk = 37` (above Android 16 / API 36). This note tracks Vivo Android 16 guidance that still applies after the 2026-07-16 refresh.

## High priority items for this app

### 1. Adaptive / large-screen layout (targetSdk 36+)
On displays >= 600dp, Android 16 can ignore orientation / resizability / aspect-ratio locks.

**Lumen response**
- No forced portrait orientation.
- `resizeableActivity="true"` on primary activities.
- `configChanges` includes orientation/screenSize/screenLayout/smallestScreenSize.
- UI pages keep responsive gutters / max-width on tablet / foldable widths.
- No `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` opt-out; we adapt instead of disabling.

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
- Timers/reminders use AlarmManager exact alarms + foreground service runtime.
- Background reconciliation/resilience uses WorkManager unique work (quota-aware), not fixed-rate executors.

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
Android 16 strengthens default protection against intent redirection attacks, and apps can opt into stricter matching with `intentMatchingFlags`.

**Lumen response**
- Application now sets `android:intentMatchingFlags="enforceIntentFilter"`.
- Notification / alarm PendingIntents use explicit component classes and package.
- Open API launch parsing only trusts action + sanitized string extras; no nested Intent extras.
- Open API binder path verifies caller permission and optional trusted signature digests.
- Share/install stream intents use explicit URI grants via `SecureShareIntents` (`ClipData` + `FLAG_GRANT_READ_URI_PERMISSION`).

### 8. Edge-to-edge enforcement
`windowOptOutEdgeToEdgeEnforcement` is ignored for targetSdk 36+.

**Lumen response**
- `MainActivity` uses `enableEdgeToEdge()`.
- No opt-out attribute present.

### 9. JobScheduler quota / abandoned jobs / setImportantWhileForeground
Background job quotas tighten; abandoned jobs and foreground-importance flags are no longer reliable escapes.

**Lumen response**
- App background work is WorkManager-based (`TimerReconciliationWorker`, `ShizukuResilienceWorker`, `ProximityDetectionWorker`).
- No direct `JobInfo#setImportantWhileForeground` usage.
- No manual `JobScheduler`/`jobFinished` lifecycle ownership.

### 10. ART / hidden API tooling
Avoid HiddenApiBypass and unsupported packers that rely on ART internals.

**Lumen response**
- No HiddenApiBypass dependency.
- No dpt-shell / commercial packer integration in this repository.

## Lower-priority / N/A for Lumen

| Doc item | Decision |
|---|---|
| Ordered broadcast global priority | N/A — no multi-process ordered broadcast coordination |
| Autofill dialog APIs | N/A — no Autofill provider / virtual view autofill |
| Keyboard ACTION_KEY_MISSING / encryption intents | N/A — no remote keyboard pairing feature |
| Accessibility expansion APIs | Optional future UX improvement only |
| Progress-centric notifications | Optional; current notifications remain standard styles |
| Keystore sharing APIs | N/A — no cross-app keystore sharing |

## Verification checklist

- Large phone / foldable / tablet: settings and home remain readable, no clipped fixed-portrait layouts.
- Predictive back: secondary pages animate back through `BackHandler` / NavController.
- Reminder alarms still schedule via explicit `AlarmReceiver` PendingIntents.
- Share backup / export / install APK still open with granted FileProvider URIs.
- Stricter intent matching does not break launcher / open-api / alarm receivers (explicit components + filters remain).

## Refresh log

- 2026-07-16: Re-fetched doc 832 via `scripts/fetch_vivo_adaptation_doc.py 832 --scan-repo`, revalidated high-priority items, added `intentMatchingFlags=enforceIntentFilter`.
