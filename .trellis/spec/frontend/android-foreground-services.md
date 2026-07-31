# Android Foreground Service Contracts

## Scenario: Foreground Service Startup and Promotion

### 1. Scope / Trigger

- Trigger: Android client code creates or promotes any foreground service, including camera and `specialUse` services.
- Applies when changing onboarding defaults, monitoring toggles, proximity/blink scheduling, calibration, or service startup wrappers.
- Prevents first-launch crashes from Android foreground service type validation when camera permission or foreground-start eligibility is missing.

### 2. Signatures

- `ProximityDetectionWorker.doWork(): Result`
- `ForegroundServiceStartEligibility.canStartFromForegroundProcess(): Boolean`
- `ForegroundServiceController.start(context: Context, intent: Intent, eligibilityCheck: (() -> Boolean)?): Boolean`
- `ForegroundServiceController.promote(service: Service, ...): Boolean`
- `ProximityCameraForegroundEligibility.canStartCameraForegroundService(context: Context): Boolean`
- `ProximityDetectionService.start(context: Context, calibrate: Boolean)`
- `ProximityDetectionService.onStartCommand(intent: Intent?, flags: Int, startId: Int): Int`
- `LightMonitorService.start(context: Context)`

### 3. Contracts

- New-user onboarding recommended setup must not auto-enable camera-backed monitoring (`proximityMonitoringEnabled` or `blinkMonitoringEnabled`) before the user explicitly grants camera permission and opts into those features.
- `ForegroundServiceController` is the single creation/promotion failure boundary for every app foreground service; direct `ContextCompat.startForegroundService(...)` and `ServiceCompat.startForeground(...)` calls outside it are forbidden.
- Android 12+ `ForegroundServiceStartNotAllowedException` is expected platform control flow. It may produce a rate-limited breadcrumb, but must not call `ProjectLumenApplication.recordCrash(...)`, populate the startup crash gate, or upload crash telemetry.
- Unexpected manifest, declared-type, permission, notification, and service implementation failures are still recorded as crashes; the service stops and returns `START_NOT_STICKY` when foreground promotion fails.
- `ForegroundServiceStartEligibility` is the shared conservative visibility gate for services with no reliable background-start exemption. `LightMonitorService` and developer-debug starts use it immediately before the framework call.
- Timer and forced-rest overlay starts may originate from an exact alarm or direct user interaction exemption, so they may attempt through `ForegroundServiceController` without the conservative visibility gate; a platform refusal remains non-fatal.
- `ProximityCameraForegroundEligibility` is the single source of truth for camera foreground-service startup gates: it requires `Manifest.permission.CAMERA`, and on Android 12+ requires the process lifecycle to be at least `Lifecycle.State.STARTED`.
- `ProximityDetectionWorker` checks `ProximityCameraForegroundEligibility.canStartCameraForegroundService(...)` before calling `ProximityDetectionService.start(...)`.
- `ProximityDetectionService.start(...)` checks the same eligibility before calling `ContextCompat.startForegroundService(...)`.
- `ProximityDetectionService.onStartCommand(...)` checks the same eligibility before calling `ServiceCompat.startForeground(...)`.
- Broadcast or resilience paths that enqueue proximity sampling must also use `ProximityCameraForegroundEligibility` before scheduling immediate camera sampling.
- Camera eligibility is rechecked at promotion so a foreground-to-background race is treated as an expected deferral rather than a crash.

### 4. Validation & Error Matrix

- Camera permission missing before worker launch -> return `Result.success()` and do not start the service.
- Camera permission missing inside service -> call `stopSelf(startId)` and return `START_NOT_STICKY`.
- Android 12+ process is not foreground-started before worker/service launch -> return `Result.success()` or skip service creation; do not record a crash report for this expected platform gate.
- Android 12+ process falls out of foreground-started state inside service before `startForeground` -> call `stopSelf(startId)` and return `START_NOT_STICKY`.
- Android rejects creation or promotion because background start is not allowed -> rate-limited breadcrumb only; do not create a crash report.
- Android rejects a declared type, permission, notification, or manifest contract while the caller remains eligible -> record the unexpected failure, stop service, return `START_NOT_STICKY`.
- User manually enables camera-backed monitoring after permission is granted -> service may start normally.

### 5. Good/Base/Bad Cases

- Good: a new user finishes onboarding with recommended setup and lands on home without any camera foreground service startup.
- Good: proximity monitoring is enabled after camera permission exists; the worker starts the service and the service enters foreground before sampling.
- Good: a queued proximity worker fires after the app has gone to the background on Android 12+; it exits successfully without starting `ProximityDetectionService`.
- Good: boot or WorkManager restoration sees light monitoring enabled while the process is backgrounded; startup is deferred without producing a startup crash report.
- Good: two overlapping recovery paths receive the same expected refusal; breadcrumbs are rate-limited and no crash telemetry is uploaded.
- Base: existing users with monitoring enabled but camera permission revoked do not crash; sampling is skipped until permission returns.
- Bad: enabling proximity/blink monitoring in onboarding defaults, then immediately scheduling `ProximityDetectionService`.
- Bad: calling `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_CAMERA)` before checking `Manifest.permission.CAMERA`.
- Bad: using only `ContextCompat.checkSelfPermission(..., CAMERA)` as the startup guard on Android 12+, because camera foreground services also need foreground-start eligibility.
- Bad: catching `ForegroundServiceStartNotAllowedException` and passing it to `recordCrash`, because that turns handled scheduling control flow into a false startup crash.
- Bad: calling `ServiceCompat.startForeground(...)` directly inside a service without a stop-on-failure boundary.

### 6. Tests Required

- Unit or instrumentation test: onboarding recommended setup leaves `proximityMonitoringEnabled` and `blinkMonitoringEnabled` false.
- Unit test: `ProximityCameraForegroundEligibility` permits pre-Android-12 granted CAMERA background starts, blocks Android 12+ background starts, and blocks missing CAMERA on all SDK versions.
- Unit test: `ForegroundServiceStartEligibility` permits pre-Android-12 background attempts and blocks Android 12+ attempts unless the process is `STARTED`.
- Unit test: `ForegroundServiceController` classifies Android 12+ background-start refusal as expected while leaving unrelated `SecurityException` failures unexpected.
- Worker test: missing camera permission prevents `ProximityDetectionService.start(...)` from being called.
- Worker or helper test: Android 12+ background-only process state prevents `ProximityDetectionService.start(...)` from being called.
- Service test: missing camera permission returns `START_NOT_STICKY` and stops the service before foreground promotion.
- Service test: Android 12+ background-only process state returns `START_NOT_STICKY` and stops before foreground promotion.
- Regression test: expected background creation/promotion refusal does not invoke crash recording or populate the startup crash gate.
- Regression test: all five app foreground services use the shared creation and promotion boundary.
- Regression test in GitHub Actions: app build and Android tests cover the new-user onboarding completion path.

### 7. Wrong vs Correct

#### Wrong

```kotlin
fun completeOnboarding(applyRecommendedSetup: Boolean) {
    updateSettings {
        it.copy(
            proximityMonitoringEnabled = true,
            blinkMonitoringEnabled = true,
        )
    }
}

override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    ServiceCompat.startForeground(
        this,
        NotificationIds.PROXIMITY_FOREGROUND,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
    )
    if (!hasCameraPermission()) return START_NOT_STICKY
    return START_STICKY
}
```

#### Correct

```kotlin
fun completeOnboarding(applyRecommendedSetup: Boolean) {
    updateSettings {
        it.copy(
            reminderEnabled = true,
            ambientLightMonitoringEnabled = true,
        )
    }
}

override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (
        !ProximityCameraForegroundEligibility.canStartCameraForegroundService(this) ||
        !startCameraForeground(app, startId)
    ) {
        stopSelf(startId)
        return START_NOT_STICKY
    }
    return START_STICKY
}
```
