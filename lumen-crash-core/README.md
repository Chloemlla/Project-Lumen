# Lumen Crash SDK Core

Capture-only artifact without Compose UI.

| Item | Value |
|---|---|
| Module | `:lumen-crash-core` |
| Maven | `com.chloemlla.lumen:lumen-crash-core` |
| Includes | install/record/store/breadcrumbs/ANR + startup watchdog/author protection/paste uploader |
| Excludes | Compose crash screen / file-share UI |

Prefer the bundle (`com.chloemlla.lumen:lumen-crash`) when you need the crash report UI.
Use core for Flutter bridges or hosts that only need capture + persistence.

## Watchdogs

`LumenCrash` keeps the existing uncaught-exception path and adds a background main-looper
watchdog. It persists a `CrashReportKind.FREEZE` report when the main thread stops processing
heartbeats for the configured timeout. This runs off the main looper, so it can still capture a
thread dump while the UI is blocked.

```kotlin
LumenCrash.install(this) {
    anrWatchdogEnabled = true
    anrWatchdogTimeoutMillis = 5_000L
    anrWatchdogCheckIntervalMillis = 1_000L
    onAnrDetected = { report -> /* enqueue telemetry from a worker */ }
}
```

For startup paths that can wait forever before rendering their first frame, opt into the startup
watchdog and call `markStartupComplete()` from the host's first-frame callback:

```kotlin
LumenCrash.install(this) {
    startupHangWatchdogEnabled = true
    startupHangTimeoutMillis = 15_000L
}

// Call after the first usable frame, not from Application.onCreate().
LumenCrash.markStartupComplete()
```

Startup reports use `CrashReportKind.STARTUP_HANG`; main-looper reports use
`CrashReportKind.FREEZE`. Existing JSON without `kind` or `durationMillis` continues to load as
an ordinary crash report.
