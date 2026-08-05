# Static Audit Report — Project Lumen Android App (`app/`)

**Audit date:** 2026-08-05
**Scope:** All Kotlin/Java sources under `app/src/main`, `app/src/test`, `app/src/androidTest`, plus `AndroidManifest.xml`, `build.gradle.kts`, `proguard-rules.pro`, `src/main/cpp/lumen_security.cpp`, `src/main/aidl/**`, and `src/main/res/xml/*`.
**Method:** Full read-through of every source file (200 main + 19 unit test + 1 androidTest = 220 files), targeted pattern greps, and build/manifest configuration review.

---

## 1. Code-size baseline

| Metric | Value |
|---|---|
| Total Kotlin/Java files | 220 (200 main, 19 test, 1 androidTest) |
| Physical lines (all sources) | 39,775 |
| Physical lines (main) | 37,991 |
| Physical lines (unit tests) | 1,712 |
| Physical lines (androidTest) | 72 |
| Native code (C++) | 204 lines (`lumen_security.cpp`) |
| AIDL | 11 lines (`ILumenOpenApi.aidl`) |
| Resource XML | 19 files |
| Room schema exports | **absent** (see S-16) |

Estimated non-blank/non-comment source lines: ~34–36k.

---

## 2. Executive summary with scores (1–10)

| Dimension | Score | One-line justification |
|---|---|---|
| **Security** | **5.0** | Solid foundations (encrypted credential store, pinning hooks, signing, integrity guard) undermined by a hardcoded fallback signing secret, a permission bypass on the exported Open API, backend-controlled camera uploads, and plaintext purchase tokens. |
| **Stability** | **6.5** | Heavy `runCatching` and a resilient FGS controller, but `runBlocking` on Binder threads, non-transactional read-modify-write stats, a 2 s main-thread sleep retry, and `goAsync()` receivers doing heavy work are real crash/ANR risks. |
| **Performance** | **6.0** | One-second DB-write loops in three services, unbounded `observeAll()` stats lists, synchronous PDF/CSV/bitmap export on the caller thread, static retained bitmaps. |
| **Testing** | **4.5** | Good pure-logic tests (engines, policies, parsers, gates) but zero coverage for DAOs, migrations, repositories, ViewModel/StateStore, AIDL/OpenAPI, the security layer, or any instrumentation of Room. |
| **Maintainability** | **5.0** | Monolithic `AppSettingsEntity` (~100 columns), a 1,313-line settings screen, large hand-rolled JSON serialization duplicated in 4+ files, dead/incomplete code, magic numbers. |
| **Design** | **7.0** | Layering (routes→store→repo→stateStore→ViewModel), manual DI, and single-direction state flow are consistently well executed; main defects are dual sources of truth (Room + MMKV) and a weak consent model for backend camera sessions. |
| **Release** | **6.0** | R8 + shrink, ABI splits, proguard rules for reflection-heavy code are right; but the build silently ships weak defaults (signing secret, empty cert fingerprint), can produce unsigned release APKs, and the Room schema history is not committed. |

**Overall:** a substantially engineered, security-conscious legacy codebase whose biggest risks cluster in the *trust boundaries*: remote/backend-controlled capabilities, exported Open API surface, and default-on/fallback build-time secrets.

---

## 3. Findings

Legend: **Critical / High / Medium / Low / Info**. File paths are relative to `app/`.

| ID | Severity | File | Line(s) | Description | Category |
|---|---|---|---|---|---|
| S-01 | **Critical** | `build.gradle.kts` / `src/main/cpp/lumen_security.cpp` / `core/security/ProjectLumenRequestSigner.kt` | 113–119 / 14–16 / 71 | The HMAC request-signing secret has a hardcoded fallback `project-lumen-local-request-signing-key` in Gradle, the native `.so`, and the Kotlin signer. Any release built without `PROJECT_LUMEN_REQUEST_SIGNING_SECRET` silently signs requests with a publicly-known constant, defeating the entire request-signing control. Build should `require()` the secret for release (or fail closed when signing is enabled). | Security |
| S-02 | **High** | `MainActivity.kt` / `openapi/LumenOpenContracts.kt` / `openapi/ExternalActivities.kt` / `AndroidManifest.xml` | 153–171 / 37–65 / 1–9 / 72–107 | **Open API permission bypass.** `MainActivity` is `exported` with no permission and processes `ACTION_TRIGGER_REST` / `ACTION_VIEW_DASHBOARD` / `ACTION_START_VISUAL_MONITOR` in `handleOpenIntent`. The gated activities `RestOverlayActivity` (signature permission) / `DashboardActivity` / `VisualMonitorActivity` (dangerous permission) all subclass `MainActivity`, so any app can launch `MainActivity` directly with the same action and trigger rest/overlay/telemetry without holding `TRIGGER_LUMEN_CONTROL`. The manifest-level permission on the subclasses is effectively bypassable. | Security |
| S-03 | **High** | `openapi/LumenOpenService.kt` / `AndroidManifest.xml` | 63–103 / 187–194 | Exported AIDL service gated only by `ACCESS_LUMEN_CORE` (a **dangerous** runtime-grantable permission); `OPEN_API_TRUSTED_SIGNATURE_SHA256` defaults to empty, so signature verification is silently disabled. Any app the user grants that permission can read eye-fatigue/screen-time and start/stop focus sessions. Recommend `signature`-level gating and/or a default-on trusted-signature requirement. | Security |
| S-04 | **High** | `core/devicecontrol/PrivilegedDeviceControlCoordinator.kt` | 181–210, 267–398 | Backend-controlled **silent vision** session: a remote `DeviceControlPolicy` can enable camera-frame uploads; the only local "consent" is the proximity/blink toggle (`hasLocalUserCameraConsent`, lines 192–199). Camera frames (JPEG + face mesh topology) are base64-uploaded to the backend on a server-triggered schedule. Consent is indirect and the policy can be changed remotely via `v1/device-control/policy`. Needs a prominent explicit in-app disclosure + kill switch independent of the monitoring toggle. | Security |
| S-05 | **High** | `core/database/entities/EntitlementEntity.kt` / `core/services/DataBackupService.kt` | 7–18 / 531–541, 357–363 | Purchase tokens and raw Play payloads are stored **plaintext** in Room and exported in plaintext backup JSON that is shared via the system share sheet. Play purchase tokens are sensitive (usable for purchase/refund checks). Store them in `SecureCredentialStore`; redact from backup exports. | Security |
| S-06 | **Medium** | `core/services/DataBackupService.kt` | 68–84, 129–379, 609–620 | Backup import accepts an attacker-crafted JSON that directly sets `planTier=PRO`, `entitlementExpiresAt`, and arbitrary entitlements/templates with no integrity check. A user (or a malicious file picker result) can self-grant premium and poison synced data. At minimum require a checksum/HMAC over the exported payload. | Security |
| S-07 | **Medium** | `AndroidManifest.xml` | 46 | `android:allowBackup="true"` with no `android:dataExtractionRules` / `android:fullBackupContent`. On modern Android this includes the Room DB (entitlements, purchase tokens, settings) and MMKV files in cloud/ADB backups. Add a rules file that excludes sensitive stores, or disable backup. | Security |
| S-08 | **Medium** | `AndroidManifest.xml` | 33–38 | Declares `PACKAGE_USAGE_STATS` (with `tools:ignore`) and `QUERY_ALL_PACKAGES`. `QUERY_ALL_PACKAGES` is a restricted, policy-scrutinized permission; combined with Shizuku app-inventory upload it broadens the data-collection surface. Justify or narrow it. | Security |
| S-09 | **Medium** | `core/security/AppIntegrityGuard.kt` / `build.gradle.kts` | 31 / 120–126, 154 | `APP_INTEGRITY_ENFORCEMENT_ENABLED` is derived from the release-cert SHA-256 being non-blank, which defaults to empty. A release built without the env var ships with **integrity enforcement silently disabled**. If the native bridge fails to load, `enforce()` also soft-fails (line 40–44). Fail closed for release. | Security |
| S-10 | **Low** | `core/security/ProjectLumenRequestSigner.kt` / `openapi/LumenOpenService.kt` | 51–58 / 26–57 | `runBlocking(Dispatchers.IO)` inside AIDL `Binder` methods (LumenOpenService) and `error()` on missing native secret in the signer. Binder threads are a limited pool; blocking them on DB+network work can cause ANRs and cross-client head-of-line blocking. Use `CoroutineScope` + `async` with timeout and return an error code. | Stability |
| S-11 | **Medium** | `core/repositories/StatisticsRepository.kt` | 21–68 | `updateEyeStats` / `updatePomodoroStats` and the `apply*Delta` helpers do read-then-write (`get()` + `upsert()`) **without** a Room `@Transaction`. Concurrent writers (TimerForegroundService 1 s tick, AlarmReceiver, OpenAPI controller, LightMonitorService) can lose increments or overwrite each other. Wrap the read-modify-write in a `@Transaction` (or a DAO-level atomic update query). | Stability |
| S-12 | **Medium** | `core/services/ForegroundServiceController.kt` | 64–96 | On a platform `ForegroundServiceStartNotAllowedException`, `start()` retries after `SystemClock.sleep(2_000)` on the **caller thread**. `startTimerService()` is invoked from main-dispatcher coroutines (ViewModel/`refreshActiveNotifications`), so this can block the UI thread for 2 s. Move the retry onto a background dispatcher and surface the refusal asynchronously. | Performance |
| S-13 | **Medium** | `core/services/TimerForegroundService.kt` | 109–151 | The timer loop emits once per second and, per tick, reads settings+runtime from DB and frequently writes stats + runtime + re-publishes the ongoing notification. This is sustained DB/notification churn whenever keep-alive is on. Consider a coarse tick (5–10 s) with sleep-based adjustment, or only persist deltas periodically. | Performance |
| S-14 | **Medium** | `core/services/AlarmReceiver.kt` / `core/services/ReminderActionReceiver.kt` / `core/services/BootReceiver.kt` | 23–88 / 20–59 / 20–31 | `goAsync()` receivers spawn a plain `CoroutineScope(Dispatchers.IO)` and do multiple DB reads, engine transitions, notification/alarm writes, and overlay FGS starts. Broadcast receivers have a ~10 s budget on modern Android; heavy work here risks `BroadcastReceiverTimeoutError`. Prefer `WorkManager` for the non-urgent portions. | Stability |
| S-15 | **Medium** | `core/services/AudioService.kt` | 41–47 | `ToneGenerator` is created per beep and never `release()`d — a native resource leak on every reminder cue. Reuse a single instance or release it. | Stability |
| S-16 | **Medium** | `app/schemas/` (missing) | — | `@Database(exportSchema = true)` + `room.schemaLocation` are configured, but no schema JSON is committed (directory absent). This disables Room's migration validation and the schema history needed for safe migrations. Commit the exported schemas. | Maintainability |
| S-17 | **Medium** | `core/runtime/PomodoroEngine.kt` | 60–63 | `completedTomatoCount` is incremented **only when a long break is reached** (cycle ≥ 4). A "tomato" conventionally completes after every focus session; as written, `completedTomatoCount` advances once per 4 sessions while `completedFocusSessions` advances every session. Likely a logic bug (verify intended semantics). | Stability |
| S-18 | **Medium** | `core/shizuku/ShizukuCapabilityManager.kt` | 689–691 | `latestCameraPrivacyEnabled()` is a stub that always returns `false`, so the `shizukuCameraPrivacyGuardEnabled` setting never actually defers sampling while another app uses the camera. Dead/incomplete feature with a misleading setting. | Maintainability |
| S-19 | **Medium** | `core/database/daos/DailyEyeStatsDao.kt` / `DailyPomodoroStatsDao.kt` / `AppNetworkControlsDao.kt` | 11–18 / 11–18 / 11–24 | `observeAll()`/`getAll()` are unbounded (one row/day forever, plus one row per controlled app) and are collected wholesale into `ProjectLumenUiState` and the telemetry snapshot. Add pruning/retention and page or cap the flows. | Performance |
| S-20 | **Medium** | `core/repositories/RuntimeRepository.kt` / `FeatureFlagRepository.kt` / `core/preferences/EyeCarePreferencesDataStore.kt` | 37–203 / 27–110 / 154–281 | Three stores use a **dual source of truth** (Room + MMKV): data is read from MMKV, written to MMKV, and Room only receives a one-time migration. The Room `runtime_state` / `feature_flags` tables and their 30+ column migrations become vestigial; `RuntimeRepository` also hand-serializes ~45 fields to JSON, which will drift from the entity. Pick one store per aggregate. | Maintainability |
| S-21 | **Medium** | `core/services/ExportService.kt` / `app/ProjectLumenSharingFeatureEntry.kt` | 23–69 / 9–25 | CSV/PDF/bitmap generation + file I/O run synchronously on the caller thread; the ViewModel invokes them from Compose click handlers (main thread). For long stat histories this janks the UI. Wrap in `withContext(Dispatchers.IO)`. | Performance |
| S-22 | **Medium** | `app/ProjectLumenDeveloperDebugScreen.kt` / `app/ProjectLumenAboutAndDialogs.kt` | 216–240 (About unlock) / Developer screen | Developer mode is unlocked by tapping the version 7 times in **release builds** too, exposing API traces (redacted body previews), memory stats, a backend force-enable toggle, an app-inventory/upload debug panel, and a purchase-token verification field. Gate the developer destination behind `BuildConfig.DEBUG` (or at least a much stronger unlock) and strip sensitive controls from release. | Security |
| S-23 | **Medium** | `core/telemetry/EyeCareTelemetryReporter.kt` / `app/ProjectLumenRuntimeFeatureEntry.kt` | 80–178, 214–223 / 217–223 | `uploadCurrentSnapshot(force=true)` is fired on every runtime transition, OpenAPI action, proximity/blink warning, and light warning — i.e., frequent network+DB work whenever stats or diagnostics are enabled. Coalesce snapshots (e.g., a debounced single-flight uploader). | Performance |
| S-24 | **Low** | `core/security/SecureCredentialStore.kt` | 236–249 | If persisting the MMKV crypt key to `EncryptedSharedPreferences` fails, an ephemeral random key is used; any previously saved credentials become permanently unrecoverable (silent data loss). Consider failing loudly or migrating key material. | Stability |
| S-25 | **Low** | `core/api/ProjectLumenApiDiagnostics.kt` / `core/api/ProjectLumenApiClient.kt` | 42–74 / 334–357 | Up to 30 API traces with request/response body **previews** (redacted but not removed) are held in memory and rendered on the developer screen. Redaction keys are heuristic; ensure no bearer tokens or purchase payloads can leak through non-JSON bodies. | Security |
| S-26 | **Low** | `core/update/UpdateChecker.kt` | 84–119, 225–258 | Falls back to the public GitHub API for release discovery; unauthenticated rate limits (60/hr) will start failing, and the `PUBLISHED_AT` fallback can surface a "newer" release based on publish time even when version tags match. Prefer the backend manifest and treat GitHub as a last resort with graceful degradation. | Stability |
| S-27 | **Low** | `core/network/ClashPartnerCompat.kt` | 314–340 | Binds the **entire process** to the detected Clash Meta VPN network (auto-on by default when Clash is installed), routing all app traffic including auth/telemetry through the user's Clash tunnel. This is a user-visible feature but is a privacy-relevant default; ensure the toggle is prominent and off-by-default for new installs. | Security |
| S-28 | **Low** | `core/update/UpdateInstaller.kt` | 77–80 | APK install uses an implicit `ACTION_VIEW` package-installer intent (with explicit FileProvider URI). On some OEMs this requires the user to pick the installer; consider the `PackageInstaller` API for a more reliable install path. | Stability |
| S-29 | **Low** | `core/proximity/FaceDistanceAnalyzer.kt` | 141–146 | `Task<T>.await()` uses `suspendCoroutine` with no cancellation/timeout handling; an ML Kit task that never completes leaks the coroutine. Add `suspendCancellableCoroutine` + `invokeOnCancellation`. | Stability |
| S-30 | **Low** | `core/proximity/ProximityCameraSampler.kt` | 258–262, 387–390 | In `capturePreviewFrame`, `reader.close()` / `thread.quitSafely()` run again in `finally` after `release()` already closed them (unwrapped in `runCatching`); in `captureSurfacePipelineFrame` the `finally` is a no-op while `reader`/`previewSurface`/`surfaceTexture` were created before the `try` — an exception before `complete()` leaks camera resources. Make resource release single-path and exception-safe. | Stability |
| S-31 | **Low** | `core/services/NotificationService.kt` | 205–231 | `showUpdateAvailable` uses magic notification IDs `POMODORO + 1000/1001` and mixes two notifications for one event. Centralize the IDs (they already live in `NotificationIds`). | Maintainability |
| S-32 | **Low** | `app/ProjectLumenUiState.kt` / `app/ProjectLumenStateStore.kt` | 14–27 / 90–96 | `ProjectLumenUiState` carries full `eyeStats`/`pomodoroStats`/`templates`/`reminderPlans` lists into Compose on every `nowMillis` tick (1 s), causing the whole state tree to be rebuilt/re-evaluated each second. Derive "now-dependent" values (countdown text) closer to the leaf and keep the store at a coarser cadence. | Performance |
| S-33 | **Low** | `app/ProjectLumenAppNetworkControlState.kt` / `core/shizuku/ShizukuCapabilityManager.kt` | 18–26 / 204–230 | Network restriction for a package is applied by UID blacklist + `cmd appops`; on restore the "not blacklisted" detection is string matching on shell output, which is OEM-fragile and can misreport state. Consider re-querying the denylist instead of parsing error text. | Stability |
| S-34 | **Low** | `core/services/TimerReconciliationWorker.kt` / `core/services/ShizukuResilienceWorker.kt` | 17–28 / 17–61 | Workers re-enqueue themselves every 15 min indefinitely. `TimerReconciliationWorker` only re-enqueues while keep-alive + active engine (good), but `ShizukuResilienceWorker` re-enqueues unconditionally while the setting is on, including at night. Add a bounded schedule or idle-gate. | Performance |
| S-35 | **Low** | `core/debug/DeveloperDebugFrameStore.kt` / `core/debug/DeveloperDebugOverlayService.kt` | 21–45 / 204–212 | A debug bitmap is retained in a static `AtomicReference` and re-drawn every 750 ms when the overlay runs. Cleared on trim, but still a memory-retention footgun if the overlay is left on. | Performance |
| S-36 | **Info** | `core/billing/LocalEntitlementChecker.kt` / `app/ProjectLumenEntitlementFeatureEntry.kt` | 1–20 / 15–36 | Premium gating is purely client-side (`planTier` string in settings) and `recordManualProEntitlement()` — a "manual PRO" grant with no expiry — is reachable from the ViewModel (currently no UI caller, i.e. dead code, but it is a latent backdoor). Remove it or gate it behind `BuildConfig.DEBUG`. | Security |
| S-37 | **Info** | `core/database/entities/AppSettingsEntity.kt` | 10–115 | Single-row entity with ~100 columns; every new feature adds `ALTER TABLE` migrations. This "super table" makes merges, defaults, and migrations error-prone. Split settings into cohesive aggregates (timer, proximity, shizuku, diagnostics). | Maintainability |
| S-38 | **Info** | `app/ProjectLumenSettingsScreen.kt` / `ProjectLumenWebViewScreen.kt` / `ProjectLumenAppConstants.kt` / `ProjectLumenPermissionGates.kt` | 1–196 (duplicated import block) | The same ~120-line Compose import block is copy-pasted across several screen files; `ProjectLumenSettingsScreen.kt` is 1,313 lines. Extract shared imports via file-level aliases or split the screen into feature composables. | Maintainability |
| S-39 | **Info** | `build.gradle.kts` | 183–192 | Release signing is only configured when 4 Gradle properties are present; otherwise release builds are produced **unsigned**. CI sets them, but a local `gradle assembleRelease` yields an uninstallable APK with no warning. Enforce signing in the release build type. | Release |
| S-40 | **Info** | `build.gradle.kts` / `core/api/ProjectLumenApiConfig.kt` | 68–95, 113–119 / 6–8 | Build-time `PROJECT_LUMEN_API_BASE_URL` and the translation base URL have production defaults baked in; an unbranded fork would silently point at `chloemlla.com`. Acceptable for a single-product repo, but document that the defaults are production endpoints. | Release |
| S-41 | **Info** | `build.gradle.kts` | 319 | `androidx.security:security-crypto:1.1.0-alpha06` is an alpha in production. Watch for the stable release (the alpha API is deprecated/in-flux). | Maintainability |
| S-42 | **Info** | `core/services/NotificationService.kt` / `core/toast/LumenToast.kt` | 460–462 / 202–236 | Full-screen intents and overlay toasts rely on `USE_FULL_SCREEN_INTENT` and `SYSTEM_ALERT_WINDOW` (both declared). Behavior differs per OEM and Android 14+ tightened full-screen-intent grants; the fallback paths are fine but untestable on emulators. | Stability |
| S-43 | **Info** | `app/MainActivity` → `openapi/ExternalActivities.kt` | 1–9 | `DashboardActivity`/`RestOverlayActivity`/`VisualMonitorActivity` are empty `MainActivity` subclasses whose only purpose is carrying manifest permission metadata (see S-02 for the bypass). Reconsider the design — e.g., a single entry activity that self-checks the required permission at runtime. | Design |
| S-44 | **Info** | `core/insights/AndroidDeviceInsightDataSource.kt` | 31–79 | Usage-stats collection is consent-gated on `PACKAGE_USAGE_STATS` (declared, user-granted). Reasonable, but the data (top apps by time) is presented as "insights" — confirm it is not uploaded with diagnostics unless explicitly enabled. | Security |
| S-45 | **Info** | `core/devicecontrol/PrivilegedDeviceControlCoordinator.kt` | 430–498 | Remote device-control policy is cached into `feature_flags` and used to *enable* privileged behavior locally. The feature-flag store is itself editable via backup import (S-06), so local policy injection is possible. Treat remote-policy data as untrusted and HMAC/verify it. | Security |
| S-46 | **Info** | `app/ProjectLumenRemoteFeatureEntry.kt` | 319–325 | `localSecurityConfig()` reports `"pinning=configured"` unconditionally even when certificate pinning is off. Misleading device-registration telemetry; derive it from `ProjectLumenApiConfig.apiCertificatePins`. | Security |
| S-47 | **Info** | `core/api/ProjectLumenApiClient.kt` / `core/api/ProjectLumenApiDiagnostics.kt` | 417–421 / 76–131 | JSON parsing uses `org.json` with exceptions wrapped; response text is read fully into memory. For large telemetry bodies this is fine, but no streaming for the (rare) large sync payloads. | Performance |
| S-48 | **Info** | `core/runtime/PomodoroEngine.kt` | 50–70 | `isLongBreak = pomodoroCycleIndex >= 4` triggers a long break after exactly 4 focus sessions, which is standard; the tomato-count bug in S-17 is the only real defect here. | — |

---

## 4. Dimension deep-dives

### 4.1 Security
Strengths:
- `SecureCredentialStore` uses AES256-GCM `EncryptedSharedPreferences` + encrypted MMKV; tokens never in plaintext there.
- `SecureOkHttpFactory` enforces HTTPS, supports per-host SHA-256 pinning, and fails the build when pinning is enabled without pins.
- `AppIntegrityGuard` + native `lumen_security.cpp` (tracer PID, env, `/proc/self/maps` hook scans) is a credible anti-tamper layer.
- WebView JS interface and JavaScript are **debug-only**; the update pipeline verifies APK SHA-256.
- Request signing is canonical and nonce/timestamped; backend gates (`DeviceSecurityGate`, `BackendConnectivityController`) fail closed.

Weaknesses cluster at trust boundaries:
1. **Bypassable Open API** (S-02, S-03): exported MainActivity + dangerous permission + optional signature verification.
2. **Fallback secrets/defaults** (S-01, S-09): release silently ships weak config when CI env vars are absent.
3. **Remote-controlled camera session** (S-04): consent model too thin.
4. **Plaintext purchase tokens + backup import** (S-05, S-06, S-45).
5. **Debug surface in release** (S-22) and easter-egg developer unlock.

### 4.2 Stability
- The codebase is defensively written (`runCatching` everywhere, FGS failures treated as non-crash). The main crash/ANR vectors are: Binder-thread blocking (S-10), non-transactional stats writes (S-11), broadcast-receiver overwork (S-14), main-thread sleeps (S-12), and resource leaks (S-15, S-30).
- `TimerForegroundService`, `AlarmReceiver`, and `ReminderActionReceiver` each re-create engines/repositories per invocation — consistent but wasteful; also each uses a fresh `CoroutineScope` (receivers) that is never cancelled if `goAsync` times out.
- `RuntimeRepository` and `FeatureFlagRepository` reading from MMKV while Room holds the same schema creates drift risk (S-20).

### 4.3 Performance
- Three services write `runtime_state` at ~1 Hz (TimerForegroundService tick, DeveloperDebugOverlay sensor writer, ProximityDetectionService on capture). Each write is an MMKV encode + `MutableStateFlow` emit that fans out to `ProjectLumenUiState` (S-32).
- Unbounded DAO flows (S-19), synchronous exports (S-21), and per-transition telemetry uploads (S-23).

### 4.4 Testing
Covered: `ReminderEngineTest`, `PomodoroEngineTest`, `BackendConnectivityControllerTest`, `BackendCommunicationPolicyTest`, `CertificatePinPolicyTest`, `SecureOkHttpFactoryTest`, `ProjectLumenApiClientGateTest`, `ShizukuNetworkRestrictionStateTest`, `ProximityCameraForegroundEligibilityTest`, `ForegroundServiceController/StartEligibility/ArchitectureTest`, `DeviceInsightAnalyzerTest`, `HapticHeEffectTest`, `BuildUpdateNotesParserTest`, `BackendFeatureVisibilityTest`, `FirstOpenGateResolverTest`, `AppNetworkControlStateTest` (+ 1 androidTest screenshot).

Gaps (all significant for a "tame the legacy" effort):
- No Room DAO/instrumentation tests; no migration tests (1→18).
- No repository tests (esp. `StatisticsRepository` read-modify-write concurrency, S-11).
- No ViewModel/StateStore tests; no AIDL/OpenAPI binding tests.
- No security-layer tests (`NativeSecurityBridge`, `AppIntegrityGuard`, `ProjectLumenRequestSigner`, `SecureCredentialStore`).
- No service/lifecycle integration tests (timer loop, alarms, boot recovery).
- The 19 unit tests target ~1.7k lines; main is ~38k — an 8:1 test-to-code ratio for the *pure* parts only, near-zero for Android-coupled code.

### 4.5 Maintainability
- Wide `AppSettingsEntity` + 17 migrations (S-37).
- Duplicated hand-rolled JSON: `RuntimeRepository.toJson/fromJson`, `DataBackupService.toJson/importX`, `EyeCarePreferencesDataStore.readFromMmkv/writeToMmkv/withEyeCarePreferences` each re-list ~40–100 fields (S-20, S-37).
- Duplicated Compose import blocks across 4+ files (S-38).
- Dead/incomplete code: `recordManualProEntitlement` (S-36), `LocalEntitlementChecker` interface not wired anywhere, `latestCameraPrivacyEnabled` stub (S-18), vestigial Room `runtime_state` columns.
- No TODO/FIXME markers anywhere; some magic numbers (notification IDs, progress constants, thresholds are fine; `POMODORO+1000` is not).

### 4.6 Design
Strengths: clear layering, manual DI with constructor-injected lambda callbacks, single `ProjectLumenUiState` via `combine`+`stateIn`, FeatureEntry pattern for UI-facing actions, `ForegroundServiceController` centralizing FGS failure policy.

Concerns: dual persistence stores (S-20), Open API activity-as-subclass design (S-43), remote-policy-as-feature-flag design (S-45), and the "silent vision" consent model (S-04).

### 4.7 Release
- R8 minify + resource shrink on; proguard keeps AIDL/OpenAPI/Room/native/JavascriptInterface classes.
- ABI splits (4 ABIs + universal) with a documented exception for baseline-profile tasks.
- `versionCode` derived from `application.version` (1.0.1 → 10001); 1.0.1 is the current version.
- Missing: committed Room schemas (S-16), enforced release signing (S-39), fail-closed integrity/signing defaults (S-01, S-09), `dataExtractionRules` for backup (S-07).

---

## 5. Top recommendations (quick wins)

1. **S-01 / S-09 / S-39:** Make release builds `require()` the request-signing secret, release-cert SHA-256, and signing config — never fall back to weak defaults.
2. **S-02 / S-03:** Close the Open API bypass: make `MainActivity`'s open-intent handling verify the caller holds `TRIGGER_LUMEN_CONTROL`/`ACCESS_LUMEN_CORE` (and a trusted signature) exactly like `LumenOpenService.requireCaller`.
3. **S-11:** Wrap stats read-modify-write in `@Transaction`/atomic DAO updates.
4. **S-16:** Commit `app/schemas/` and add a migration test.
5. **S-04 / S-05 / S-06:** Harden the remote-policy + camera-upload consent model; move purchase tokens to encrypted storage; HMAC/checksum backups.
6. **S-22:** Gate the developer screen behind `BuildConfig.DEBUG`.
