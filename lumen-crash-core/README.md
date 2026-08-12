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

## Recommended integration (first)

Consume `lumen-crash-core` from a **locally staged Maven layout** instead of pointing Gradle
straight at GitHub Packages.

GitHub Packages requires a GitHub token on **every** download — even for public packages. A host
that resolves directly from `maven.pkg.github.com/Chloemlla/Project-Lumen` needs `gpr.user` /
`gpr.key` (a PAT with `read:packages`) for local builds and only works out of the box in GitHub
Actions because the injected `GITHUB_TOKEN` is used implicitly. Staging the release assets
locally removes that auth requirement for third-party apps entirely.

### 1. Resolve the latest release into a local Maven dir

A host-side resolve script queries the GitHub API for the newest non-draft `lumen-crash-v*`
release, then downloads the AAR/POM assets into `android/local-maven`. The API is reachable
anonymously, but passing `GITHUB_TOKEN` (auto-injected in Actions, or `gh auth token` locally)
avoids the anonymous 60 requests/hour rate limit:

```bash
# scripts/resolve-lumen-crash.sh (outline)
OWNER_REPO="Chloemlla/Project-Lumen"
RELEASES="$(curl -fsSL \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer ${GITHUB_TOKEN:-}" \
  "https://api.github.com/repos/${OWNER_REPO}/releases?per_page=100")"
VERSION="$(printf '%s' "$RELEASES" | python3 -c '
import json, sys
releases = json.load(sys.stdin)
cands = [r for r in releases if not r.get("draft")
         and str(r.get("tag_name", "")).startswith("lumen-crash-v")]
cands.sort(key=lambda r: r.get("published_at") or r.get("created_at") or "")
print(cands[-1]["tag_name"].removeprefix("lumen-crash-v"))'
)"
# Stage lumen-crash-core-${VERSION}.aar / .pom into
# android/local-maven/com/chloemlla/lumen/lumen-crash-core/${VERSION}/
```

Expose the resolved version to Gradle via a property or an environment variable:

```properties
# android/gradle.properties
lumenCrashVersion=0.1.0
```

### 2. Register the local repo; add GitHub Packages only as a credentialed fallback

In `android/settings.gradle`, resolve `lumen-crash-core` from the staged directory and register
GitHub Packages **only when credentials exist** — empty credentials make Gradle return HTTP 401
and abort resolution even when `local-maven` already contains the AAR:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
        // Preferred: release assets staged by scripts/resolve-lumen-crash.* so CI does
        // not need cross-repo GitHub Packages auth for lumen-crash-core.
        maven {
            name = "LumenCrashLocal"
            url = uri("${settingsDir}/local-maven")
        }
        def gprUser = providers.gradleProperty("gpr.user").orNull
            ?: System.getenv("GITHUB_ACTOR")
        def gprKey = providers.gradleProperty("gpr.key").orNull
            ?: System.getenv("GITHUB_TOKEN")
        // Only register GitHub Packages when credentials exist. Empty credentials
        // return 401 and abort resolution even if local-maven already has the AAR.
        if (gprUser && gprKey) {
            maven {
                name = "GitHubPackagesProjectLumen"
                url = uri("https://maven.pkg.github.com/Chloemlla/Project-Lumen")
                credentials {
                    username = gprUser
                    password = gprKey
                }
            }
        }
    }
}
```

### 3. Declare the dependency (capture-only)

```kotlin
val lumenCrashVersion =
    providers.gradleProperty("lumenCrashVersion")
        .orElse(providers.environmentVariable("LUMEN_CRASH_VERSION"))
        .orElse("0.1.0")
        .get()

implementation("com.chloemlla.lumen:lumen-crash-core:$lumenCrashVersion")
```

### Why this order

- Local staging keeps third-party hosts buildable with **no GitHub Packages credentials** for the
  dependency itself — only the GitHub API call to discover the latest version is required, which
  CI satisfies with its injected `GITHUB_TOKEN`.
- GitHub Packages remains a valid fallback when credentials are present; it lets a host resolve
  directly without the staging step, at the cost of needing `read:packages` auth everywhere.
- Runtime integration (watchdog configuration, `markStartupComplete()`) is documented in
  [Watchdogs](#watchdogs).

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
