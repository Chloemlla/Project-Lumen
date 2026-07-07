# Android Foreground Service Contracts

## Scenario: Camera Foreground Service Startup

### 1. Scope / Trigger

- Trigger: Android client code starts `ProximityDetectionService`, or any future foreground service using `FOREGROUND_SERVICE_TYPE_CAMERA`.
- Applies when changing onboarding defaults, monitoring toggles, proximity/blink scheduling, calibration, or service startup wrappers.
- Prevents first-launch crashes from Android foreground service type validation when camera permission or foreground-start eligibility is missing.

### 2. Signatures

- `ProximityDetectionWorker.doWork(): Result`
- `ProximityCameraForegroundEligibility.canStartCameraForegroundService(context: Context): Boolean`
- `ProximityDetectionService.start(context: Context, calibrate: Boolean)`
- `ProximityDetectionService.onStartCommand(intent: Intent?, flags: Int, startId: Int): Int`

### 3. Contracts

- New-user onboarding recommended setup must not auto-enable camera-backed monitoring (`proximityMonitoringEnabled` or `blinkMonitoringEnabled`) before the user explicitly grants camera permission and opts into those features.
- `ProximityCameraForegroundEligibility` is the single source of truth for camera foreground-service startup gates: it requires `Manifest.permission.CAMERA`, and on Android 12+ requires the process lifecycle to be at least `Lifecycle.State.STARTED`.
- `ProximityDetectionWorker` checks `ProximityCameraForegroundEligibility.canStartCameraForegroundService(...)` before calling `ProximityDetectionService.start(...)`.
- `ProximityDetectionService.start(...)` checks the same eligibility before calling `ContextCompat.startForegroundService(...)`.
- `ProximityDetectionService.onStartCommand(...)` checks the same eligibility before calling `ServiceCompat.startForeground(...)`.
- Broadcast or resilience paths that enqueue proximity sampling must also use `ProximityCameraForegroundEligibility` before scheduling immediate camera sampling.
- `ServiceCompat.startForeground(...)` and `ContextCompat.startForegroundService(...)` must be wrapped so Android `ActiveServices.validateForegroundServiceType` failures are recorded and the service stops instead of crashing the main process.

### 4. Validation & Error Matrix

- Camera permission missing before worker launch -> return `Result.success()` and do not start the service.
- Camera permission missing inside service -> call `stopSelf(startId)` and return `START_NOT_STICKY`.
- Android 12+ process is not foreground-started before worker/service launch -> return `Result.success()` or skip service creation; do not record a crash report for this expected platform gate.
- Android 12+ process falls out of foreground-started state inside service before `startForeground` -> call `stopSelf(startId)` and return `START_NOT_STICKY`.
- Android rejects `FOREGROUND_SERVICE_TYPE_CAMERA` at `startForeground` -> record crash, stop service, return `START_NOT_STICKY`.
- Android rejects `startForegroundService` before service creation -> record crash through `ProjectLumenApplication.recordCrash(...)`.
- User manually enables camera-backed monitoring after permission is granted -> service may start normally.

### 5. Good/Base/Bad Cases

- Good: a new user finishes onboarding with recommended setup and lands on home without any camera foreground service startup.
- Good: proximity monitoring is enabled after camera permission exists; the worker starts the service and the service enters foreground before sampling.
- Good: a queued proximity worker fires after the app has gone to the background on Android 12+; it exits successfully without starting `ProximityDetectionService`.
- Base: existing users with monitoring enabled but camera permission revoked do not crash; sampling is skipped until permission returns.
- Bad: enabling proximity/blink monitoring in onboarding defaults, then immediately scheduling `ProximityDetectionService`.
- Bad: calling `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_CAMERA)` before checking `Manifest.permission.CAMERA`.
- Bad: using only `ContextCompat.checkSelfPermission(..., CAMERA)` as the startup guard on Android 12+, because camera foreground services also need foreground-start eligibility.

### 6. Tests Required

- Unit or instrumentation test: onboarding recommended setup leaves `proximityMonitoringEnabled` and `blinkMonitoringEnabled` false.
- Unit test: `ProximityCameraForegroundEligibility` permits pre-Android-12 granted CAMERA background starts, blocks Android 12+ background starts, and blocks missing CAMERA on all SDK versions.
- Worker test: missing camera permission prevents `ProximityDetectionService.start(...)` from being called.
- Worker or helper test: Android 12+ background-only process state prevents `ProximityDetectionService.start(...)` from being called.
- Service test: missing camera permission returns `START_NOT_STICKY` and stops the service before foreground promotion.
- Service test: Android 12+ background-only process state returns `START_NOT_STICKY` and stops before foreground promotion.
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
