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
  - [Resolve latest main auto release](#2-choose--resolve-a-version)
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
  - [Required third-party minify exemption](#required-third-party-minify-exemption)
  - [Field lesson: white-screen / instant exit on release cold start](#field-lesson-white-screen--instant-exit-on-release-cold-start)
- [Integration pitfalls](#integration-pitfalls)
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
- **Non-removable author attribution**: Chloemlla + https://github.com/Chloemlla/
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

For consumers, **require** auto-resolving the latest Project Lumen `main` release via the `lumen-crash-v*` tag prefix (see section 2). Do not hard-pin `com.chloemlla.lumen:lumen-crash` for normal integration.

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

### 2) Choose / resolve a version

Default consumer guidance is: **always automatically resolve the latest Project Lumen `main` auto release** for `lumen-crash`. Do **not** hard-pin a fixed version string in source control for normal external integration.

| Scenario | Version form | Required / recommended source |
|---|---|---|
| Default external integration (**required**) | latest `0.1.0-<shortSha>` | Auto-resolve newest `lumen-crash-v*` main release at configure/CI time |
| Local monorepo development | project module | `implementation(project(":lumen-crash"))` |
| Exceptional temporary freeze only | pure `X.Y.Z` | Allowed only for short-lived debugging; not the default docs path |

**Requirement:** host apps should depend on `com.chloemlla.lumen:lumen-crash:<auto-resolved-latest>`, where `<auto-resolved-latest>` comes from the resolver in section 2.1 on every CI/configure run. Do not leave a hand-maintained hard-coded version as the long-term integration path.

#### 2.1 Automatically resolve the latest main auto release

Main-branch auto releases are created by `.github/workflows/lumen-crash-sdk-release.yml` with:

- version form: `<sdk.version>-<shortSha>` (example: `0.1.0-1a2b3c4d`)
- tag form: `lumen-crash-v<version>` (example: `lumen-crash-v0.1.0-1a2b3c4d`)

Use the GitHub Releases API filtered by the `lumen-crash-v` tag prefix. Do **not** use `/releases/latest` alone, because this repository may also publish non-SDK releases.

bash / Git Bash:

```bash
# Requires: curl + jq
# Optional: export GH_TOKEN=... if API auth / rate-limit requires it

OWNER_REPO="Chloemlla/Project-Lumen"
API="https://api.github.com/repos/${OWNER_REPO}/releases?per_page=100"
AUTH_HEADER=()
if [ -n "${GH_TOKEN:-${GITHUB_TOKEN:-}}" ]; then
  AUTH_HEADER=(-H "Authorization: Bearer ${GH_TOKEN:-$GITHUB_TOKEN}")
fi

VERSION="$(
  curl -fsSL "${AUTH_HEADER[@]}" \
    -H "Accept: application/vnd.github+json" \
    "$API" \
  | jq -r '
      [.[]
        | select(.draft == false)
        | select(.tag_name | startswith("lumen-crash-v"))
      ]
      | sort_by(.published_at // .created_at)
      | reverse
      | .[0].tag_name
      | sub("^lumen-crash-v"; "")
    '
)"

if [ -z "$VERSION" ] || [ "$VERSION" = "null" ]; then
  echo "No lumen-crash release found" >&2
  exit 1
fi

echo "Resolved latest lumen-crash main auto release: ${VERSION}"
echo "implementation(\"com.chloemlla.lumen:lumen-crash:${VERSION}\")"
echo "Release page: https://github.com/${OWNER_REPO}/releases/tag/lumen-crash-v${VERSION}"
```

PowerShell:

```powershell
# Optional: $env:GH_TOKEN = "..."
$ownerRepo = "Chloemlla/Project-Lumen"
$headers = @{
  Accept = "application/vnd.github+json"
  "User-Agent" = "lumen-crash-version-resolver"
}
$token = $env:GH_TOKEN
if (-not $token) { $token = $env:GITHUB_TOKEN }
if ($token) { $headers.Authorization = "Bearer $token" }

$releases = Invoke-RestMethod `
  -Uri "https://api.github.com/repos/$ownerRepo/releases?per_page=100" `
  -Headers $headers

$latest = $releases |
  Where-Object { -not $_.draft -and $_.tag_name -like "lumen-crash-v*" } |
  Sort-Object { if ($_.published_at) { [datetime]$_.published_at } else { [datetime]$_.created_at } } -Descending |
  Select-Object -First 1

if (-not $latest) {
  throw "No lumen-crash release found"
}

$version = $latest.tag_name -replace '^lumen-crash-v', ''
Write-Host "Resolved latest lumen-crash main auto release: $version"
Write-Host "implementation(\"com.chloemlla.lumen:lumen-crash:$version\")"
Write-Host "Release page: https://github.com/$ownerRepo/releases/tag/lumen-crash-v$version"
```

GitHub CLI one-liner:

```bash
gh release list --repo Chloemlla/Project-Lumen --limit 100   | awk '/^lumen-crash-v/ { print $1; exit }'   | sed 's/^lumen-crash-v//'
```

After resolution, inject the auto-resolved latest version into Gradle. Prefer an env/property injection so the version is **not hard-pinned** in source.

Recommended CI / local pattern:

```bash
VERSION="$(...resolve command from above...)"
echo "LUMEN_CRASH_VERSION=${VERSION}" >> "$GITHUB_ENV"
# or for local Gradle:
# echo "lumenCrashVersion=${VERSION}" >> gradle.properties
```

```kotlin
// app/build.gradle.kts
val lumenCrashVersion =
    providers.gradleProperty("lumenCrashVersion")
        .orElse(providers.environmentVariable("LUMEN_CRASH_VERSION"))
        .orNull
        ?: error("Resolve latest lumen-crash first (see README section 2.1)")

dependencies {
    implementation("com.chloemlla.lumen:lumen-crash:$lumenCrashVersion")
}
```

Do **not** commit a permanent hard-coded line such as `implementation("com.chloemlla.lumen:lumen-crash:0.1.0-1a2b3c4d")` as the normal integration path. The version must come from the latest-main auto resolver.

#### 2.2 Other version sources

These are inspection aids for the same auto-latest policy, not reasons to hard-pin forever:

- GitHub Release title / tag (`lumen-crash-v0.1.0-1a2b3c4d` => `0.1.0-1a2b3c4d`)
- release asset `sdk-manifest.json` field `version`
- GitHub Packages package version list

Pure stable tags such as `lumen-crash-v0.1.0` are only for exceptional temporary freezes. Default external integration remains: auto-resolve the newest `main` `lumen-crash-v*` release every time.

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

1. Automatically resolve the latest Project Lumen `main` auto release for `lumen-crash` (see [section 2](#2-choose--resolve-a-version)). Do **not** hard-pin a fixed version.
2. Create a GitHub token with `read:packages`.
3. Put credentials in `~/.gradle/gradle.properties` (do **not** commit them).
4. Add the GitHub Packages Maven repository in `settings.gradle.kts`.
5. Depend on `com.chloemlla.lumen:lumen-crash:$lumenCrashVersion` where `$lumenCrashVersion` is the auto-resolved latest value.
6. Sync Gradle and wire `LumenCrash.install(...)` + pending-report UI.

| Field | Value |
|---|---|
| Group ID | `com.chloemlla.lumen` |
| Artifact ID | `lumen-crash` |
| Default version source | **Auto-latest** `main` release (`0.1.0-<shortSha>`) |
| Version policy | Always auto-resolve; do not hard-pin in normal integration |
| Example resolved version (ephemeral) | `0.1.0-1a2b3c4d` |
| Full coordinates form | `com.chloemlla.lumen:lumen-crash:<auto-resolved-latest>` |
| Maven repository | `https://maven.pkg.github.com/Chloemlla/Project-Lumen` |
| Packages page | `https://github.com/Chloemlla/Project-Lumen/packages` |
| Auto release tag pattern | `https://github.com/Chloemlla/Project-Lumen/releases/tag/lumen-crash-v0.1.0-<shortSha>` |

Gradle dependency form after auto-resolve:

```kotlin
implementation("com.chloemlla.lumen:lumen-crash:$lumenCrashVersion")
// $lumenCrashVersion comes from section 2.1 / env / gradle.properties, not a hard-coded pin
```

#### 5.2 Find / resolve the published version

Required path: auto-resolve the newest `lumen-crash-v*` release published from Project Lumen `main` (section 2.1) on each CI/configure run, then inject that value into Gradle. Do **not** hard-pin it as a permanent source-controlled constant.

| Source | What to use |
|---|---|
| **Required:** latest main auto release | run the resolver in section 2.1; form `0.1.0-<shortSha>` |
| GitHub Release tag | `lumen-crash-v0.1.0-1a2b3c4d` => dependency version `0.1.0-1a2b3c4d` |
| Release asset `sdk-manifest.json` | field `version` and `maven.coordinates` |
| GitHub Packages package version list | package version string such as `0.1.0-1a2b3c4d` |

For external integration, always track the latest main auto release (`0.1.0-<shortSha>`). Do not document or keep a permanent hard-pinned stable freeze as the default dependency line.

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
val lumenCrashVersion =
    providers.gradleProperty("lumenCrashVersion")
        .orElse(providers.environmentVariable("LUMEN_CRASH_VERSION"))
        .orNull
        ?: error("Resolve latest lumen-crash first (see README section 2.1)")

dependencies {
    // Required: auto-latest main release from section 2.1 / 5.2
    // Do not hard-pin com.chloemlla.lumen:lumen-crash:X.Y.Z-<sha> in source control.
    implementation("com.chloemlla.lumen:lumen-crash:$lumenCrashVersion")
}
```

Groovy:

```groovy
def lumenCrashVersion = findProperty("lumenCrashVersion") ?: System.getenv("LUMEN_CRASH_VERSION")
if (!lumenCrashVersion) {
    throw new GradleException("Resolve latest lumen-crash first (see README section 2.1)")
}

dependencies {
    implementation "com.chloemlla.lumen:lumen-crash:$lumenCrashVersion"
}
```

`$lumenCrashVersion` must come from the latest-main auto resolver. Do **not** hard-pin a fixed release string for normal integration.

#### 5.7 Sync, resolve, and verify

1. Sync the Gradle project in Android Studio, or run:

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
```

2. Confirm the tree includes a resolved latest coordinate, for example:

```text
com.chloemlla.lumen:lumen-crash:<auto-resolved-latest>
# example snapshot of one resolve run: com.chloemlla.lumen:lumen-crash:0.1.0-1a2b3c4d
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

1. Auto-resolve the latest Project Lumen `main` auto release for `lumen-crash` (`lumen-crash-vX.Y.Z-<shortSha>`) on every CI/configure run
2. Consume via **GitHub Packages Maven coordinates** as `com.chloemlla.lumen:lumen-crash:<auto-resolved-latest>`
3. Keep credentials outside VCS
4. Keep re-resolving the newest main auto release; do **not** hard-pin a fixed version for normal integration
5. Avoid long-lived hard-pinned freezes in source control
6. Verify `sdk-manifest.json` / checksums when validating a resolved version
7. Wire `install` + pending-report UI gate before shipping

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

`LumenCrashReportScreen` uses `calculateWindowSizeClass` when an `Activity` is available, with width/height fallbacks from `BoxWithConstraints` and `LocalConfiguration`. The screen also applies `WindowInsets.safeDrawing` so phone status/navigation bars and display cutouts do not cover content under edge-to-edge hosts.

| Layout signal | Behavior |
|---|---|
| Compact width (`< 600.dp` or Compact class / phone) | Full-width content, denser 12–16.dp padding, smaller hero type, denser info tiles / pills / buttons, stack header wraps above expand control, vertical action stack |
| Medium width | Content max 720.dp, 20.dp padding, two-column secondary actions when height is not compact |
| Expanded width (`>= 840.dp` or Expanded class) | Content max 960.dp, wider metadata pills, horizontal secondary actions when height is not compact |
| Compact height (`< 560.dp` or Compact class) | Tighter vertical padding/spacing; lower stack max heights and denser controls so primary actions stay reachable on short phones |

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

SDK share-as-file writes a UTF-8 `.txt` under internal cache first (`cacheDir/lumen-crash-share`, fallback external cache), attaches it through `FileProvider`, and propagates temporary URI read grants through the system chooser. If authority is missing, the UI shows the library "file share unavailable" string and still allows text share.

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

- Name: `Chloemlla`
- URL: `https://github.com/Chloemlla/`
- Handle: `chloemlla`
- Fingerprint: SHA-256 of `AUTHOR_NAME|AUTHOR_URL` as lowercase hex
- Footer label: `Crash SDK by Chloemlla · https://github.com/Chloemlla/`

Forced into:

- Report model (`authorName` / `authorUrl` / `authorFingerprint`)
- JSON persistence
- Clipboard / share payload footer
- Crash UI author footer (cannot be hidden via config)

`AuthorIntegrity.verifyOrThrow(...)` runs on install, report construction, load/export paths, and UI open. Mismatch throws `SecurityException` (or UI blocked state). Consumer ProGuard rules keep attribution constants/integrity entry points for multi-point checks.

> Open-source forks can still edit source; this protects against accidental/runtime stripping and raises the bar for silent removal. Absolute anti-fork protection is out of scope.

## ProGuard / R8

### Required third-party minify exemption

If the host app enables ProGuard / R8, **`com.chloemlla.lumen.crash.**` must be treated as a minify exemption surface**.

Do **not**:

- obfuscate / rename Lumen Crash public API classes
- shrink away author attribution constants
- strip integrity entry points used at runtime

If you do, install and crash UI can fail-closed (`SecurityException` or blocked crash screen), and copy/share may lose author attribution.

### Automatic exemption path (preferred)

Release minify is off inside the library by default. The AAR ships `consumer-rules.pro`, and Android Gradle Plugin merges those consumer rules into the **host app** minify config automatically when you depend on:

```kotlin
implementation("com.chloemlla.lumen:lumen-crash:0.1.0")
```

For a normal Maven / GitHub Packages dependency, this automatic consumer-rules merge is the preferred exemption path. You usually **do not** need to copy rules by hand.

### Explicit host exemption (recommended backup)

If your host:

- enables `isMinifyEnabled = true`
- strips consumer rules
- uses a custom shrinker pipeline
- or wants an explicit app-module backup

add this **required exemption block** to host `app/proguard-rules.pro`:

```proguard
############################################################
# Lumen Crash SDK minify exemption
# Artifact: com.chloemlla.lumen:lumen-crash
# Put this in the host app proguard-rules.pro
############################################################

# Keep annotations / signatures used by integrity + public API.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keepattributes SourceFile, LineNumberTable

# Required: author attribution constants must keep source values/names.
-keep class com.chloemlla.lumen.crash.CrashAuthorAttribution {
    public static final java.lang.String *;
    public static *** payload();
}
-keepclassmembers class com.chloemlla.lumen.crash.CrashAuthorAttribution {
    public static final java.lang.String *;
}

# Required: integrity entry points used on install / report / UI open.
-keep class com.chloemlla.lumen.crash.AuthorIntegrity {
    public static *** verifyOrThrow(...);
    public static *** fingerprintHex();
    public static *** verifiedAuthorBlock();
}
-keep class com.chloemlla.lumen.crash.AuthorBlock { *; }

# Required backup: keep public SDK API used by host integration
-keep class com.chloemlla.lumen.crash.LumenCrash { *; }
-keep class com.chloemlla.lumen.crash.LumenCrashConfig { *; }
-keep class com.chloemlla.lumen.crash.CrashReport { *; }
-keep class com.chloemlla.lumen.crash.CrashAppInfo { *; }
-keep class com.chloemlla.lumen.crash.CrashReportStore { *; }
-keep class com.chloemlla.lumen.crash.CrashBreadcrumbs { *; }
-keep class com.chloemlla.lumen.crash.ui.LumenCrashReportScreenKt { *; }

# Package-level exemption (safe default for third-party hosts)
-keep class com.chloemlla.lumen.crash.** { *; }
-keepclassmembers class com.chloemlla.lumen.crash.** { *; }
-keepnames class com.chloemlla.lumen.crash.**
-dontwarn com.chloemlla.lumen.crash.**
```

Host release minify example:

```kotlin
// app/build.gradle.kts
android {
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro", // must include the Lumen Crash exemption block above
            )
        }
    }
}
```

### Compose / Material3 note

This SDK is Compose-first and publishes Material3 / window-size-class as `api` dependencies. In host release builds, do not add broad rules that strip Compose runtime classes used by the crash UI.

### Why the exemption is required

- `CrashAuthorAttribution` constants are read by multi-point author integrity checks.
- `AuthorIntegrity.verifyOrThrow()` / `fingerprintHex()` run on install, report build/load/export, and UI open.
- Host integration calls `LumenCrash`, `LumenCrashConfig`, `CrashReport`, and `LumenCrashReportScreen`.
- If those symbols are renamed or removed, install/UI can fail-closed with `SecurityException` or a blocked crash screen.

### Verify after enabling minify

1. Build a minified release APK/AAB that depends on `lumen-crash`.
2. Cold-start the release build once before any crash test.
3. Force a test crash or open the crash report preview path.
4. Confirm:
   - app does **not** white-screen / immediately process-die on cold start
   - `LumenCrash.install(...)` still succeeds
   - pending report UI opens
   - copy / share still include author attribution

### Field lesson: white-screen / instant exit on release cold start

Seen in a real external host (Seal) after enabling:

- `isMinifyEnabled = true`
- `isShrinkResources = true`
- early `LumenCrash.install(...)` in `Application.attachBaseContext`
- startup gate via `LumenCrash.loadPendingReport()`

**What happened**

1. R8 renamed/stripped author constants or integrity entry points.
2. `AuthorIntegrity.verifyOrThrow(...)` failed closed with `SecurityException`.
3. That exception escaped host `Application` / `MainActivity` startup.
4. User-visible symptom: **white screen then instant exit**, with little useful UI.

**Root-cause checklist**

| Check | Why it matters |
|---|---|
| Host minify on, but no explicit Lumen Crash keep backup | consumer-rules may be stripped or ignored by custom pipelines |
| `CrashAuthorAttribution` constants / `payload()` not kept | fingerprint / author-name checks fail closed |
| `AuthorIntegrity.verifyOrThrow(...)` stripped or renamed | install / load / UI open all fail closed |
| `isShrinkResources = true` without keeping `@string/lumen_crash_*` | crash UI labels can disappear after resource shrink |
| Host calls install / loadPendingReport without `runCatching` | one integrity failure becomes a process-killing startup crash |

**Hardening that fixed the host**

1. Put the explicit keep block above into host `app/proguard-rules.pro` even if consumer-rules should already merge.
2. Keep the package-level exemption:
   - `-keep class com.chloemlla.lumen.crash.** { *; }`
   - `-keepclassmembers class com.chloemlla.lumen.crash.** { *; }`
   - `-keepnames class com.chloemlla.lumen.crash.**`
3. If resource shrink is on, keep SDK strings:

```xml
<!-- host app/src/main/res/raw/keep.xml -->
<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:tools="http://schemas.android.com/tools"
    tools:keep="@string/lumen_crash_*,@plurals/lumen_crash_*" />
```

4. Treat crash-sdk startup as non-fatal for the host process:

```kotlin
class MyApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        runCatching {
            LumenCrash.install(this, /* config */)
            LumenCrash.recordBreadcrumb("Application.attachBaseContext")
        }.onFailure { it.printStackTrace() }
    }
}

// MainActivity
val pending = runCatching { LumenCrash.loadPendingReport() }
    .onFailure { it.printStackTrace() }
    .getOrNull()
```

Integrity is still fail-closed inside the SDK. Host hardening only prevents one failed integrity check from taking down the whole process before normal UI can start.

**Release smoke matrix**

| Step | Expected |
|---|---|
| Cold start minified release | No white-screen, reaches normal UI or crash-report UI |
| Force crash + relaunch | Pending `LumenCrashReportScreen` opens |
| Copy / share | Author footer still present |
| Continue / clear | Next cold start does not reopen the same report |

## Integration pitfalls

High-frequency host integration mistakes. Use this as a pre-ship checklist; details live in the sections linked below.

### Dependency and version

- Do **not** use `/releases/latest` alone. This repository may publish non-SDK releases. Only accept tags with the `lumen-crash-v*` prefix.
- **Required:** auto-resolve the newest `main` `lumen-crash-v*` release and inject it as `com.chloemlla.lumen:lumen-crash:<auto-resolved-latest>`. Do **not** hard-pin a fixed version string for normal external integration.
- Maven itself has no magical floating `latest` coordinate; the approved way to stay current is the section 2.1 resolver + property/env injection on every CI/configure run.
- GitHub Packages needs authenticated reads (`read:packages`). Org SSO tokens must be SSO-authorized.
- Keep credentials outside VCS (`~/.gradle/gradle.properties` or CI secrets). Never commit tokens.
- `Could not find` / `401` / `403` almost always means one of: missing Packages repo URL, stale/wrong version string, missing/invalid token. See [Troubleshooting](#9-troubleshooting).

### Host environment

- Host `minSdk` must be `>= 26`. Kotlin / JVM 17 is recommended.
- The SDK is Compose-first. Hosts must enable `buildFeatures.compose = true`, or dependency resolution can succeed while crash UI symbols/runtime still fail.
- Material3 and window-size-class are published as `api` dependencies; hosts that already use Compose Material3 usually need no extra Compose wiring.
- Do not add broad release minify rules that strip Compose runtime classes used by the crash UI.

### Required host touchpoints

Missing any of these three is a broken integration:

1. **Install early** in `Application.attachBaseContext` so the uncaught handler is active before late startup work. See [Minimal integration](#minimal-integration-3-host-touchpoints).
2. **Gate startup UI** with `LumenCrash.loadPendingReport()` -> `LumenCrashReportScreen` before normal app content.
3. **Record breadcrumbs / handled failures** on important paths via `recordBreadcrumb` / `record(throwable)`.

Also:

- Prefer `LumenCrash.record(throwable)` over hand-building reports. Direct `CrashReport.fromThrowable(...)` requires a full `CrashAppInfo`.
- `LumenCrash.store()` throws if install has not run.
- On continue, clear storage (`clearPendingReport()` or the screen clear path). Otherwise the next cold start still blocks on the same report.
- Host startup calls (`install`, `loadPendingReport`, breadcrumbs) should be wrapped in `runCatching`. Integrity remains fail-closed inside the SDK, but the host process should still reach a recoverable UI path.

### File share and product copy

- Text share works without host setup. File share requires a host `FileProvider` plus `LumenCrashConfig.fileProviderAuthority`. Without authority, the UI keeps text share and shows the library "file share unavailable" string.
- Provider paths must expose cache (required) and preferably external-cache for the SDK share writer fallback. See [File share setup](#file-share-setup).
- Override only product-facing strings through config (`reportTitle`, `reportMessage`, `shareSubject`). Do not re-copy the full shared UI string set into the host app.
- Author attribution is not configurable and cannot be hidden. Attempts to strip it fail closed.

### Persistence assumptions

- Reports are stored under app-specific **external** directories (`getExternalFilesDir` / external cache), not internal private storage as the primary path.
- Save succeeds if **any** external path succeeds. Legacy private paths exist only for migration.
- Do not assume "clear app data" semantics map 1:1 to internal `filesDir` only. Clear via `LumenCrash.clearPendingReport()` or wipe the external store locations too.

### ProGuard / R8

- When host minify is enabled, treat `com.chloemlla.lumen.crash.**` as a third-party exemption surface. See [ProGuard / R8](#proguard--r8).
- Prefer automatic AAR `consumer-rules.pro` merge. If the host strips consumer rules or uses a custom shrinker, copy the explicit keep block into host `proguard-rules.pro`.
- Keep at least: `CrashAuthorAttribution` (including `payload()`), `AuthorIntegrity`, public API classes, package-level keep/keepnames/dontwarn for `com.chloemlla.lumen.crash.**`.
- If `isShrinkResources = true`, also keep `@string/lumen_crash_*` and `@plurals/lumen_crash_*` via host `res/raw/keep.xml`.
- Obfuscating/stripping author constants or integrity entry points causes install/`SecurityException`, blocked crash UI, missing author footers, or **white-screen/instant exit on cold start**.
- Wrap host `install` / `loadPendingReport` / breadcrumb calls in `runCatching` so one integrity failure cannot kill process startup.
- After enabling minify, verify: cold start survives, install succeeds, pending UI opens, copy/share still include author attribution.

### Author integrity is fail-closed

- Integrity checks run on install, report build/load/export, and UI open.
- Mismatch is not a soft warning: it throws or shows a blocked screen.
- This raises the bar against silent attribution removal; absolute anti-fork protection is intentionally out of scope.

### Responsibility boundary

- Upload/telemetry stays in the host (`onCrashSaved` / continue-time hooks). The SDK does not ship a crash backend.
- Do not reintroduce host-local crash core clones (`core/crash`, custom crash screens that replace `LumenCrashReportScreen`) after extracting to this module.

### Safe production path

1. Auto-resolve the latest main auto-release version (`lumen-crash-vX.Y.Z-<shortSha>`) and inject it; do **not** hard-pin.
2. Consume via GitHub Packages with out-of-repo credentials.
3. Install in `attachBaseContext` inside `runCatching`.
4. Gate startup with pending-report UI; load pending report inside `runCatching`.
5. Configure FileProvider if file share is required.
6. Verify release minify keeps author integrity + public API, and keep crash strings if resource shrink is on.
7. Cold-start a minified release once, then smoke-test crash capture, pending UI, copy, and share before shipping.

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
