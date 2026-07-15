# Lumen Crash SDK

Reusable Android crash collection + adaptive Compose crash report UI, extracted from Project Lumen.

[English](./README.md) | [中文](./README.zh-CN.md)

| Item | Value |
|---|---|
| Module | `:lumen-crash` |
| Package | `com.chloemlla.lumen.crash` |
| minSdk | 26 |
| compileSdk | 37 |
| Language level | Java / Kotlin 17 |

## Table of contents

- [Features](#features)
- [Module layout](#module-layout)
- [Install](#install)
- [Auto release](#auto-release)
- [Consume published SDK](#consume-published-sdk)
  - [What each release produces](#1-what-each-release-produces)
  - [GitHub Packages](#5-option-b-github-packages-recommended-for-external-apps)
  - [GitHub Packages Maven tutorial](#51-quick-start-github-packages-maven-package)
  - [Release assets / local Maven](#6-option-c-consume-github-release-assets-without-packages-auth)
  - [Troubleshooting](#9-troubleshooting)
- [Minimal integration](#minimal-integration-3-host-touchpoints)
- [Public API](#public-api)
- [Crash capture behavior](#crash-capture-behavior)
- [Persistence](#persistence)
- [Breadcrumbs](#breadcrumbs)
- [Adaptive UI](#adaptive-ui)
- [File share setup](#file-share-setup)
- [Host product copy](#host-product-copy)
- [Author protection](#author-protection)
- [ProGuard / R8](#proguard--r8)
- [Testing](#testing)
- [Project Lumen host notes](#project-lumen-host-notes)
- [Out of scope](#out-of-scope)

## Features

- Uncaught exception capture with previous-handler chaining
- Multi-path atomic persistence under app-specific **external** storage (`getExternalFilesDir` / `externalCacheDir`)
- Breadcrumbs ring buffer (max 40 events, sanitized)
- Adaptive Material3 crash report screen (`WindowSizeClass`)
- Copy report ID / copy full report / share text / share file (file share needs host `FileProvider`)
- Host-configurable app metadata and product strings
- Upload stays in the host app via `onCrashSaved`
- **Non-removable author attribution**: ChloeMlla + https://github.com/Chloemlla/
- Strict author integrity checks (fail-closed)

## Module layout

```text
lumen-crash/
  build.gradle.kts
  consumer-rules.pro
  sdk.version
  README.md
  README.zh-CN.md
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

## Auto release

The SDK is released automatically by GitHub Actions workflow:

- Workflow: `.github/workflows/lumen-crash-sdk-release.yml`
- Version source: `lumen-crash/sdk.version`
- Maven coordinates: `com.chloemlla.lumen:lumen-crash:<version>`

### Triggers

| Trigger | Version / tag behavior |
|---|---|
| Push to `main` that changes `lumen-crash/**` or the workflow file | Version = `<sdk.version>-<shortSha>`, tag = `lumen-crash-v<version>` |
| Push tag `lumen-crash-vX.Y.Z` | Version = `X.Y.Z`, release uses that exact tag |
| Manual `workflow_dispatch` | Optional version override; still publishes GitHub Release + Packages by default |

### Release pipeline

1. Resolve version metadata
2. Run `:lumen-crash:test`
3. Assemble release AAR (`:lumen-crash:assembleRelease`)
4. Publish Maven artifacts to a local repo for packaging
5. Collect AAR / POM / sources / checksums + `sdk-manifest.json`
6. Create GitHub Release under tag `lumen-crash-v...`
7. Publish the same Maven publication to GitHub Packages

### Manual stable tag example

```bash
# bump lumen-crash/sdk.version first when needed
git tag lumen-crash-v0.1.0
git push origin lumen-crash-v0.1.0
```

## Consume published SDK

This section is a practical tutorial for using the **release artifacts** produced by `.github/workflows/lumen-crash-sdk-release.yml`.

### 1) What each release produces

Every successful SDK release publishes:

1. A **GitHub Release** under tag `lumen-crash-v<version>`
2. The same Maven publication to **GitHub Packages**
3. Workflow artifacts named `lumen-crash-sdk-<version>`

Typical release assets:

| Asset | Example | Purpose |
|---|---|---|
| Release AAR | `lumen-crash-0.1.0.aar` | Binary Android library package |
| POM | `lumen-crash-0.1.0.pom` | Maven coordinates + dependency metadata |
| Gradle Module Metadata | `lumen-crash-0.1.0.module` | Variant-aware Gradle metadata |
| Sources JAR | `lumen-crash-0.1.0-sources.jar` | Source attachment for IDE navigation |
| Checksums | `checksums.txt` | SHA-256 for every asset |
| Manifest | `sdk-manifest.json` | Machine-readable release metadata |
| Notes | `release-notes.md` | Human-readable release summary |

Maven coordinates:

```text
com.chloemlla.lumen:lumen-crash:<version>
```

Repository URL:

```text
https://maven.pkg.github.com/Chloemlla/Project-Lumen
```

Release page pattern:

```text
https://github.com/Chloemlla/Project-Lumen/releases/tag/lumen-crash-v<version>
```

### 2) Choose a version

| Scenario | Version form | Recommended source |
|---|---|---|
| Stable consumer integration | `0.1.0` | Tag release `lumen-crash-v0.1.0` |
| Main-branch snapshot-like build | `0.1.0-<shortSha>` | Latest auto release from `main` |
| Local monorepo development | project module | `implementation(project(":lumen-crash"))` |

Read the chosen version from either:

- GitHub Release title / tag (`lumen-crash-v0.1.0` => `0.1.0`)
- `sdk-manifest.json` field `version`
- GitHub Packages package version list

### 3) Verify download integrity

Before wiring a manually downloaded AAR into CI or a production host app, verify checksums:

```bash
# Linux / macOS / Git Bash
sha256sum -c checksums.txt

# Or verify one file
sha256sum lumen-crash-0.1.0.aar
# compare with the line in checksums.txt
```

```powershell
# Windows PowerShell
Get-FileHash .\lumen-crash-0.1.0.aar -Algorithm SHA256
# compare with checksums.txt
```

Also open `sdk-manifest.json` and confirm:

- `groupId` = `com.chloemlla.lumen`
- `artifactId` = `lumen-crash`
- `version` matches the assets you downloaded
- `maven.coordinates` matches your Gradle dependency line

### 4) Option A: monorepo project module

Use this inside Project Lumen itself.

```kotlin
// settings.gradle.kts
include(":lumen-crash")

// app/build.gradle.kts
dependencies {
    implementation(project(":lumen-crash"))
}
```

Pros:

- No authentication
- Tracks source directly
- Best for local feature work

Cons:

- Not portable to external host apps

### 5) Option B: GitHub Packages (recommended for external apps)

This is the recommended way for **external Android apps** to consume the published Maven package.

#### 5.1 Quick start: GitHub Packages Maven package

Use this checklist when you only need the shortest path:

1. Confirm a published version exists on the Packages page or Release page.
2. Create a GitHub token with `read:packages`.
3. Put credentials in `~/.gradle/gradle.properties` (do **not** commit them).
4. Add the GitHub Packages Maven repository in `settings.gradle.kts`.
5. Depend on `com.chloemlla.lumen:lumen-crash:<version>`.
6. Sync Gradle and wire `LumenCrash.install(...)` + pending-report UI.

| Field | Value |
|---|---|
| Group ID | `com.chloemlla.lumen` |
| Artifact ID | `lumen-crash` |
| Example version | `0.1.0` |
| Full coordinates | `com.chloemlla.lumen:lumen-crash:0.1.0` |
| Maven repository | `https://maven.pkg.github.com/Chloemlla/Project-Lumen` |
| Packages page | `https://github.com/Chloemlla/Project-Lumen/packages` |
| Stable release pattern | `https://github.com/Chloemlla/Project-Lumen/releases/tag/lumen-crash-v0.1.0` |

Gradle dependency line:

```kotlin
implementation("com.chloemlla.lumen:lumen-crash:0.1.0")
```

#### 5.2 Find the published version

Pick one source and keep the version exact:

| Source | What to copy |
|---|---|
| GitHub Packages package version list | package version string such as `0.1.0` |
| GitHub Release tag | `lumen-crash-v0.1.0` => dependency version `0.1.0` |
| Release asset `sdk-manifest.json` | field `version` and `maven.coordinates` |
| main-branch auto release | form `0.1.0-<shortSha>` |

Stable consumer apps should prefer a pure semver tag (`0.1.0`). Use `0.1.0-<shortSha>` only when you intentionally track a main-branch build.

#### 5.3 Create a read token

GitHub Packages is authenticated even when the package is public in some account/org configurations. Create a token that can read packages from this repository:

| Runtime | Credential |
|---|---|
| Local machine | classic PAT or fine-grained token with `read:packages` |
| Same-repo GitHub Actions | `GITHUB_TOKEN` with `packages: read` |
| Other-repo / external CI | dedicated PAT/fine-grained token with `read:packages`, stored as a secret |

Token rules:

- Username is your GitHub username (or the identity that owns the token).
- Password / token value is the PAT or CI token, **not** your GitHub login password.
- If the account/org uses SAML SSO, authorize the token for SSO first.
- Never commit the token into git.

Classic PAT minimum scope:

```text
read:packages
```

If the package is private or your org requires broader package access, also ensure the token can read the owning repository.

#### 5.4 Store credentials outside the repo

Recommended local file: `~/.gradle/gradle.properties`

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_PAT_OR_TOKEN
```

Windows example path:

```text
C:\Users\<you>\.gradle\gradle.properties
```

macOS / Linux example path:

```text
~/.gradle/gradle.properties
```

Environment-variable alternative:

```bash
# bash / zsh / Git Bash
export GITHUB_ACTOR=YOUR_GITHUB_USERNAME
export GITHUB_TOKEN=YOUR_GITHUB_PAT_OR_TOKEN
```

```powershell
# Windows PowerShell
$env:GITHUB_ACTOR = "YOUR_GITHUB_USERNAME"
$env:GITHUB_TOKEN = "YOUR_GITHUB_PAT_OR_TOKEN"
```

Do **not** put real tokens into a committed project `gradle.properties`.

#### 5.5 Add the Maven repository once

In the consumer app `settings.gradle.kts`:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "GitHubPackagesProjectLumen"
            url = uri("https://maven.pkg.github.com/Chloemlla/Project-Lumen")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

Notes:

- Keep `google()` and `mavenCentral()` so transitive AndroidX / Compose dependencies still resolve.
- Put credentials on this repository block; do not hardcode secrets in source.
- If your project still uses root `build.gradle.kts` / `allprojects.repositories`, add the same Maven block there instead.

Groovy `settings.gradle` equivalent:

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "GitHubPackagesProjectLumen"
            url = uri("https://maven.pkg.github.com/Chloemlla/Project-Lumen")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

#### 5.6 Declare the dependency

In the host module, usually `app/build.gradle.kts`:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.chloemlla.lumen:lumen-crash:0.1.0")

    // main-branch auto-published build example:
    // implementation("com.chloemlla.lumen:lumen-crash:0.1.0-1a2b3c4d")
}
```

Groovy:

```groovy
dependencies {
    implementation "com.chloemlla.lumen:lumen-crash:0.1.0"
}
```

Replace `0.1.0` with the exact published version you selected in step 5.2.

#### 5.7 Sync, resolve, and verify

1. Sync the Gradle project in Android Studio, or run:

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
```

2. Confirm the tree includes:

```text
com.chloemlla.lumen:lumen-crash:0.1.0
```

3. Optional smoke checks:

```bash
# resolve only
./gradlew :app:compileDebugKotlin --dry-run

# full compile
./gradlew :app:compileDebugKotlin
```

If resolution fails, jump to [Troubleshooting](#9-troubleshooting).

#### 5.8 Host app requirements

Because the SDK is Compose-first and publishes Material3 / window-size-class as `api` dependencies:

- Host `minSdk` should be `>= 26`
- Host should enable Compose
- Kotlin / JVM 17 is recommended
- No extra Compose dependency wiring is usually required if the host already uses Compose Material3

Example host flags:

```kotlin
android {
    compileSdk = 35 // or newer

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
```

#### 5.9 Minimal code after the package resolves

After Gradle can download the package, wire these three host touchpoints.

Install early:

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
                onCrashSaved = { report ->
                    // optional host upload / telemetry schedule
                },
            ),
        )
    }
}
```

Gate startup UI on a pending report:

```kotlin
setContent {
    val report = LumenCrash.loadPendingReport()
    if (report != null) {
        LumenCrashReportScreen(
            report = report,
            onContinue = {
                LumenCrash.clearPendingReport()
                // recreate() or switch to normal app content
            },
        )
    } else {
        App()
    }
}
```

Optional handled failures / breadcrumbs:

```kotlin
LumenCrash.recordBreadcrumb("CheckoutScreen.submit")
runCatching { riskyWork() }
    .onFailure { LumenCrash.record(it) }
```

#### 5.10 Consumer CI example

Same repository / token that can read the package:

```yaml
- name: Build consumer app
  env:
    GITHUB_ACTOR: ${{ github.actor }}
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
  run: ./gradlew :app:assembleRelease --no-daemon
```

External repository that cannot read this package with the default token:

```yaml
- name: Build consumer app
  env:
    GITHUB_ACTOR: ${{ github.actor }}
    GITHUB_TOKEN: ${{ secrets.LUMEN_CRASH_READ_PACKAGES_TOKEN }}
  run: ./gradlew :app:assembleRelease --no-daemon
```

Store `LUMEN_CRASH_READ_PACKAGES_TOKEN` as a repository secret with `read:packages`.

#### 5.11 Common GitHub Packages mistakes

| Mistake | Result | Fix |
|---|---|---|
| Wrong repo URL | `Could not find ... lumen-crash` | Use `https://maven.pkg.github.com/Chloemlla/Project-Lumen` |
| Missing credentials block | `401 Unauthorized` | Add `credentials { ... }` and set `gpr.*` or env vars |
| Token lacks `read:packages` | `401` / `403` | Recreate token with package read permission |
| SSO not authorized | `403 Forbidden` | Authorize the token for org SSO |
| Version typo | package not found | Copy exact version from Packages/Release/`sdk-manifest.json` |
| Credentials committed | secret leak | Move secrets to `~/.gradle/gradle.properties` or CI secrets and rotate the token |
| Using bare AAR instead of Maven coordinates | missing transitive deps | Prefer `implementation("com.chloemlla.lumen:lumen-crash:<version>")` |

### 6) Option C: consume GitHub Release assets without Packages auth

Use this when you can download release files but do not want GitHub Packages credentials in every consumer.

#### 6.1 Download assets

From the release page `lumen-crash-v<version>`, download at least:

- `lumen-crash-<version>.aar`
- `lumen-crash-<version>.pom` (recommended)
- `checksums.txt`
- `sdk-manifest.json`

Verify checksums as shown above.

#### 6.2 Local Maven repository layout

Create a local Maven repo and place files in standard coordinates path:

```text
local-maven/
  com/
    chloemlla/
      lumen/
        lumen-crash/
          0.1.0/
            lumen-crash-0.1.0.aar
            lumen-crash-0.1.0.pom
            lumen-crash-0.1.0.module          # optional but recommended
            lumen-crash-0.1.0-sources.jar     # optional
```

Then point Gradle at that folder:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            name = "LumenCrashLocal"
            url = uri("${rootDir}/local-maven")
        }
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.chloemlla.lumen:lumen-crash:0.1.0")
}
```

#### 6.3 Direct AAR file dependency (quick smoke test only)

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(files("libs/lumen-crash-0.1.0.aar"))
}
```

Notes:

- This is fine for a quick compile smoke test
- Prefer Maven coordinates for real apps, because POM-driven transitive dependency metadata is preserved
- If you use a bare AAR, you may need to manually align Compose Material3 / activity-compose versions with the SDK

### 7) End-to-end host wiring after the dependency is resolved

Once Gradle can resolve `lumen-crash`, integrate these three touchpoints.

#### 7.1 Install early

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
                onCrashSaved = { report ->
                    // host upload / telemetry schedule
                },
            ),
        )
    }
}
```

#### 7.2 Gate UI on pending report

```kotlin
setContent {
    val report = LumenCrash.loadPendingReport()
    if (report != null) {
        LumenCrashReportScreen(
            report = report,
            onContinue = {
                LumenCrash.clearPendingReport()
                // recreate() or switch to normal app content
            },
        )
    } else {
        App()
    }
}
```

#### 7.3 Optional: breadcrumbs and handled failures

```kotlin
LumenCrash.recordBreadcrumb("CheckoutScreen.submit")
runCatching { riskyWork() }
    .onFailure { LumenCrash.record(it) }
```

#### 7.4 Optional: file share support

If you want "share as file" in the crash UI, configure host `FileProvider` and pass its authority through `LumenCrashConfig.fileProviderAuthority`. Text-only share works without this.

### 8) CI usage pattern

Example GitHub Actions snippet for a consumer repository:

```yaml
- name: Build consumer app
  env:
    GITHUB_ACTOR: ${{ github.actor }}
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
  run: |
    ./gradlew :app:assembleRelease --no-daemon
```

For private package access from another repository, use a PAT secret with `read:packages` instead of a default token that cannot read this package.

### 9) Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `Could not find com.chloemlla.lumen:lumen-crash:...` | Missing GitHub Packages repo or wrong version | Add repo URL and confirm exact version from release/manifest |
| `401 Unauthorized` / `403 Forbidden` | Token missing `read:packages` or SSO not authorized | Recreate/authorize token; verify `gpr.user` / `gpr.key` |
| Dependency resolves but Compose UI symbols missing | Host Compose not enabled | Enable `buildFeatures.compose = true` and use a Compose-capable host |
| File share action unavailable | No `fileProviderAuthority` | Configure host FileProvider and pass authority in config |
| Preview/manual `fromThrowable` fails compile | Missing `CrashAppInfo` | Pass app metadata, or prefer `LumenCrash.record(throwable)` |
| Checksum mismatch | Partial/corrupt download | Re-download assets and re-check `checksums.txt` |

### 10) Recommended production path

For external host apps:

1. Prefer a stable tag version (`lumen-crash-vX.Y.Z`)
2. Consume via **GitHub Packages Maven coordinates**
3. Keep credentials outside VCS
4. Verify `sdk-manifest.json` / checksums when promoting a version
5. Wire `install` + pending-report UI gate before shipping

For this monorepo:

1. Keep using `implementation(project(":lumen-crash"))`
2. Use published artifacts only when validating external consumer packaging

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
| `CrashReport.fromThrowable(throwable, appInfo)` | Build a report from a throwable (needs `CrashAppInfo`) |

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

### `CrashAppInfo`

Used by low-level report builders such as `CrashReport.fromThrowable(...)`.

| Field | Required | Notes |
|---|---|---|
| `appDisplayName` | yes | Product/app display name |
| `versionName` | yes | Version name |
| `versionCode` | yes | Version code |
| `commitHash` | yes | Commit / short hash string |

Normal host integration should prefer `LumenCrash.record(throwable)`, which derives `CrashAppInfo` from `LumenCrashConfig`. Direct `fromThrowable` callers (for example developer crash-page previews) must supply `CrashAppInfo` themselves.

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

`CrashReportStore` writes `crash_report.json` atomically under app-specific **external** directories, not app-private internal storage:

1. `context.getExternalFilesDir("lumen-crash")`
2. `context.getExternalFilesDir(null)/lumen-crash`
3. `context.externalCacheDir/lumen-crash`

Save succeeds if **any external path** succeeds. After a successful save, any legacy private copies under `filesDir` / `noBackupFilesDir` / `cacheDir` are deleted.

Load order:

1. External locations first
2. Legacy private locations only for migration; a readable legacy report is rewritten to external storage

Clear deletes every external and legacy private copy.

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
    <external-cache-path name="external_cache" path="." />
</paths>
```

SDK share-as-file writes a UTF-8 `.txt` under external cache (fallback: internal cache) and grants URI read permission to the target app. If authority is missing, the UI shows the library "file share unavailable" string and still allows text share.

## Host product copy

Shared crash UI labels, actions, share-option copy, and stack-hint text live only in the SDK:

- `src/main/res/values/strings.xml` (EN)
- `src/main/res/values-zh/strings.xml` (ZH)

Project Lumen keeps only the three product-facing overrides in the host app and injects them through config:

- `crash_report_title`
- `crash_report_message`
- `crash_report_share_subject`

Do not reintroduce host-side duplicates of the shared UI strings, and do not override author attribution.

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
- developer debug crash preview builds `CrashReport.fromThrowable(..., CrashAppInfo(...))` with app name + `BuildConfig` metadata

Old app-local crash core sources were removed after extraction; do not reintroduce app-local duplicates under `core/crash` or `ProjectLumenCrashReportScreen`.

## Out of scope

- Server-side crash backend
- Non-Android platforms
- Crashlytics replacement
- Split core/UI dual artifacts
- Independent sample app (MVP uses this README + host app)
- Absolute protection against source-level fork edits
