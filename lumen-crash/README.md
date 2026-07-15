# Lumen Crash SDK

Reusable Android crash collection + adaptive Compose crash report UI, extracted from Project Lumen.

## Features

- Uncaught exception capture with previous-handler chaining
- Multi-path atomic local persistence (`filesDir` / `noBackupFilesDir` / `cacheDir`)
- Breadcrumbs ring buffer
- Adaptive Material3 crash report screen (`WindowSizeClass`)
- Copy / share text / share file (when host supplies `FileProvider` authority)
- Host-configurable app metadata and product strings
- **Non-removable author attribution**: ChloeMlla + https://github.com/Chloemlla/
- Strict author integrity checks (fail-closed)

## Install

```kotlin
// settings.gradle.kts
include(":lumen-crash")

// app/build.gradle.kts
implementation(project(":lumen-crash"))
```

## Usage

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
                onCrashSaved = { report -> /* optional upload */ },
            ),
        )
    }
}

// Activity gate
setContent {
    val report = LumenCrash.loadPendingReport()
    if (report != null) {
        LumenCrashReportScreen(
            report = report,
            onContinue = {
                LumenCrash.clearPendingReport()
                // recreate / continue
            },
        )
    } else {
        App()
    }
}

// Manual capture
LumenCrash.record(throwable)
LumenCrash.recordBreadcrumb("MainActivity.onCreate")
```

## Author protection

Author fields are compile-time constants and are forced into:

- Crash report model (`authorName` / `authorUrl` / `authorFingerprint`)
- Clipboard / share payload footer
- Crash UI footer (cannot be hidden via config)

`LumenCrash.install`, report construction, UI open, and export paths call integrity verification. Tampering fails closed.

> Open-source forks can still edit source; this protects against accidental/runtime stripping and raises the bar for silent removal.
