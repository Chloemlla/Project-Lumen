# Lumen Crash SDK

Reusable Android crash collection + adaptive Compose crash report UI, extracted from Project Lumen.

Module path: `:lumen-crash`  
Package: `com.chloemlla.lumen.crash`  
minSdk: 26 · compileSdk: 37 · Java/Kotlin: 17

## Features

- Uncaught exception capture with previous-handler chaining
- Multi-path atomic local persistence (`filesDir` / `noBackupFilesDir` / `cacheDir`)
- Breadcrumbs ring buffer (max 40 events, sanitized)
- Adaptive Material3 crash report screen (`WindowSizeClass`)
- Copy report ID / copy full report / share text / share file (file share needs host `FileProvider`)
- Host-configurable app metadata and product strings
- Upload stays in host app via `onCrashSaved`
- **Non-removable author attribution**: ChloeMlla + https://github.com/Chloemlla/
- Strict author integrity checks (fail-closed)

## Module layout

```text
lumen-crash/
  build.gradle.kts
  consumer-rules.pro
  README.md
  src/main/
    AndroidManifest.xml
    java/com/chloemlla/lumen/crash/
      LumenCrash.kt                 # public install / record / load / clear API
      LumenCrashConfig.kt           # host config + CrashAppInfo
      CrashReport.kt                # report model, JSON, clipboard export
      CrashReportStore.kt           # multi-path atomic persistence
      CrashBreadcrumbs.kt           # in-memory ring buffer
      CrashAuthorAttribution.kt     # non-overridable author constants
      AuthorIntegrity.kt            # fail-closed integrity verification
      ui/LumenCrashReportScreen.kt  # adaptive Compose UI
    res/values/strings.xml          # EN defaults
    res/values-zh/strings.xml       # ZH defaults
  src/test/.../AuthorIntegrityTest.kt
```

## Install

```kotlin
// settings.gradle.kts
include(":lumen-crash")

// app/build.gradle.kts
implementation(project(":lumen-crash"))
```

The library is Compose-first and exposes Compose Material3 + window-size-class as `api` dependencies. Host apps that already use Compose usually need no extra dependency wiring beyond the module dependency.

## Minimal integration (3 host touchpoints)

### 1) Install early in `Application`

Prefer `attachBaseContext` so the uncaught handler is active as early as possible.

```kotlin
class MyApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        LumenCrash.install(
            this,
            LumenCrashConfig(
                appDisplayName = "My App",
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                commitHash = BuildConfig.SHORT_HASH,
                fileProviderAuthority = "${packageName}.fileprovider",
                shareSubject = "Crash report",
                reportTitle = null,   // null => library string resource
                reportMessage = null, // null => library string resource
                onCrashSaved = { report -> /* optional upload */ },
                killProcessWhenNoPreviousHandler = true,
            ),
        )
    }
}
```

### 2) Gate app content with the pending report

```kotlin
setContent {
    val report = LumenCrash.loadPendingReport()
    if (report != null) {
        LumenCrashReportScreen(
            report = report,
            onContinue = {
                LumenCrash.clearPendingReport()
                // recreate() or continue into normal app content
            },
            clearStoredReportOnContinue = true,
        )
    } else {
        App()
    }
}
```

### 3) Record breadcrumbs / manual crashes

```kotlin
LumenCrash.recordBreadcrumb("MainActivity.onCreate")
LumenCrash.record(throwable) // also persists + invokes onCrashSaved
```

## Public API

| API | Purpose |
|---|---|
| `LumenCrash.install(application, config)` | One-time install: config, store, uncaught handler |
| `LumenCrash.isInstalled()` | Whether install completed |
| `LumenCrash.configOrNull()` | Current host config, or `null` |
| `LumenCrash.store()` | `CrashReportStore` (throws if not installed) |
| `LumenCrash.recordBreadcrumb(event)` | Append sanitized breadcrumb |
| `LumenCrash.record(throwable)` | Build + persist report, invoke `onCrashSaved` |
| `LumenCrash.loadPendingReport()` | In-memory startup report, else disk load |
| `LumenCrash.clearPendingReport()` | Clear store + startup report |
| `LumenCrash.clearStartupCrashReport()` | Clear in-memory report only |
| `LumenCrash.startupCrashReport` | Last captured in-memory report (read-only) |
| `LumenCrashReportScreen(...)` | Adaptive crash UI |
| `CrashReport.toClipboardText()` | Full export text (author-verified) |
| `CrashReport.toJson()` / `crashReportFromJson(...)` | Persistence format helpers |

### `LumenCrashConfig`

| Field | Required | Notes |
|---|---|---|
| `appDisplayName` | yes | Shown in system info / report |
| `versionName` | yes | Host app version name |
| `versionCode` | yes | Host app version code |
| `commitHash` | no | Default `"unknown"` |
| `fileProviderAuthority` | no | Enables share-as-file; `null` => text-only share |
| `shareSubject` | no | Share intent subject; falls back to library string |
| `reportTitle` | no | UI title override; `null` => EN/ZH library string |
| `reportMessage` | no | UI message override; `null` => EN/ZH library string |
| `onCrashSaved` | no | Host upload/telemetry hook after successful save |
| `killProcessWhenNoPreviousHandler` | no | Default `true`; kill process if no previous handler |

Author fields are **not** part of config and cannot be overridden.

### `LumenCrashReportScreen`

```kotlin
@Composable
fun LumenCrashReportScreen(
    report: CrashReport,
    onContinue: (() -> Unit)? = null,
    clearStoredReportOnContinue: Boolean = true,
    onClearStoredReport: (() -> Unit)? = null,
)
```

- Opens only after author integrity verification; failure shows a blocked screen.
- Title/message come from `LumenCrashConfig` overrides when non-blank, else library resources.
- Primary actions: copy report ID, copy full report, share, clear & continue.
- `onClearStoredReport` lets the host inject extra work (for example schedule upload then clear). When null, the screen calls `LumenCrash.clearPendingReport()`.

## Crash capture behavior

1. `install()` stores config, creates `CrashReportStore`, installs a default uncaught exception handler, and records an install breadcrumb.
2. On uncaught exception:
   - Build `CrashReport` (or fallback report if construction fails)
   - Keep it in `startupCrashReport`
   - Persist via a fresh `CrashReportStore(applicationContext)`
   - Invoke `onCrashSaved` when present
   - Chain to the previous handler when one exists
   - Otherwise optionally kill the process (`killProcessWhenNoPreviousHandler`)
3. `record(throwable)` performs the same report build/save/hook path for handled failures.
4. Next process start: host calls `loadPendingReport()` and shows `LumenCrashReportScreen` before normal UI.

If install has not run yet and an uncaught exception still reaches the SDK handler path, report construction falls back to package-name / `"unknown"` app metadata.

## Persistence

`CrashReportStore` writes `crash_report.json` atomically to all of:

1. `context.filesDir`
2. `context.noBackupFilesDir`
3. `context.cacheDir`

Save succeeds if **any** path succeeds. Load returns the first readable valid report. Clear deletes every existing copy.

JSON includes: report id, timestamps, exception/root cause, thread/process, system info, stack, recent events, and forced author fields.

## Breadcrumbs

- API: `LumenCrash.recordBreadcrumb(event)` or `CrashBreadcrumbs.record(event)`
- Capacity: 40 events (ring buffer)
- Each event truncated to 180 chars after sanitization
- Format: `HH:mm:ss.SSS  <event>`
- Snapshot is embedded into new `CrashReport.recentEvents`
- UI shows the last 12 events

Sanitization redacts local user-home paths plus `content://` / `file://` URIs. The same rules are applied to stack/root-cause text when building reports.

## Adaptive UI

`LumenCrashReportScreen` uses `calculateWindowSizeClass` when an `Activity` is available, with width/height fallbacks from `BoxWithConstraints`.

| Layout signal | Behavior |
|---|---|
| Compact width (`< 600.dp` or Compact class) | Full-width content, 16.dp horizontal padding, vertical action stack |
| Medium width | Content max 720.dp, 20.dp padding |
| Expanded width (`>= 840.dp` or Expanded class) | Content max 960.dp, wider metadata pills, horizontal actions when height is not compact |
| Compact height (`< 560.dp` or Compact class) | Tighter vertical padding/spacing; lower stack max heights so primary actions stay reachable |

Stack preview defaults to 18 collapsed lines; users can expand/collapse. Author footer card is always rendered when integrity passes.

## File share setup

Text share works without host setup. File share requires:

1. Host `FileProvider` authority passed as `fileProviderAuthority`
2. Provider paths that allow cache-dir file exposure

Example host provider:

```xml
<!-- AndroidManifest.xml -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

```xml
<!-- res/xml/file_paths.xml -->
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="cache" path="." />
</paths>
```

SDK share-as-file writes a UTF-8 `.txt` under cache and grants URI read permission to the target app. If authority is missing, the UI shows the library "file share unavailable" string and still allows text share.

## Host product copy

Library defaults ship in:

- `src/main/res/values/strings.xml` (EN)
- `src/main/res/values-zh/strings.xml` (ZH)

Override product-facing title/message/subject through config, not by forking author strings:

```kotlin
LumenCrashConfig(
    appDisplayName = getString(R.string.app_name),
    versionName = BuildConfig.VERSION_NAME,
    versionCode = BuildConfig.VERSION_CODE,
    commitHash = BuildConfig.SHORT_HASH,
    fileProviderAuthority = "${packageName}.fileprovider",
    shareSubject = getString(R.string.crash_report_share_subject),
    reportTitle = getString(R.string.crash_report_title),
    reportMessage = getString(R.string.crash_report_message),
    onCrashSaved = { report -> scheduleUpload(report) },
)
```

Upload is intentionally **out of scope** for the SDK. Project Lumen uses `onCrashSaved` / continue-time hooks to schedule telemetry upload while keeping network policy in the app.

## Author protection

Author constants live in `CrashAuthorAttribution`:

- Name: `ChloeMlla`
- URL: `https://github.com/Chloemlla/`
- Handle: `chloemlla`
- Fingerprint: SHA-256 of `AUTHOR_NAME|AUTHOR_URL` as lowercase hex
- Footer label: `Crash SDK by ChloeMlla · https://github.com/Chloemlla/`

Forced into:

- Report model (`authorName` / `authorUrl` / `authorFingerprint`)
- JSON persistence
- Clipboard / share payload footer
- Crash UI author footer (cannot be hidden via config)

`AuthorIntegrity.verifyOrThrow(...)` runs on install, report construction, load/export paths, and UI open. Mismatch throws `SecurityException` (or UI blocked state). Consumer ProGuard rules keep attribution constants/integrity entry points for multi-point checks.

> Open-source forks can still edit source; this protects against accidental/runtime stripping and raises the bar for silent removal. Absolute anti-fork protection is out of scope.

## ProGuard / R8

Release minify is off inside the library by default. Host minify should keep consumer rules from `consumer-rules.pro`:

```proguard
-keep class com.chloemlla.lumen.crash.CrashAuthorAttribution {
    public static final java.lang.String *;
}
-keep class com.chloemlla.lumen.crash.AuthorIntegrity {
    public static *** verifyOrThrow();
    public static *** fingerprintHex();
}
```

## Testing

Unit coverage currently focuses on author integrity and export attribution:

- `AuthorIntegrityTest.fingerprintMatchesConstant`
- `AuthorIntegrityTest.verifyOrThrowSucceeds`
- `AuthorIntegrityTest.clipboardTextIncludesAuthorAttribution`

Build/test execution for this repo is validated through GitHub workflow rather than local full builds.

## Project Lumen host notes

In this monorepo, `:app` already depends on `:lumen-crash` and:

- installs from `ProjectLumenApplication.installLumenCrashSdk()`
- gates startup UI in `MainActivity` with `LumenCrashReportScreen`
- can also present an in-session report from `ProjectLumenApp`
- schedules crash report upload from host hooks (`onCrashSaved` / clear callbacks)
- reuses the existing host FileProvider authority `${applicationId}.fileprovider`

Old app-local crash core sources were removed after extraction; do not reintroduce app-local duplicates under `core/crash` or `ProjectLumenCrashReportScreen`.

## Out of scope

- Server-side crash backend
- Non-Android platforms
- Crashlytics replacement
- Split core/UI dual artifacts
- Independent sample app (MVP uses this README + host app)
- Absolute protection against source-level fork edits
