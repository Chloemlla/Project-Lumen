Repository Guidelines

Do not write to a super file!!!! Do not write to a super file!!!! Do not write to a super file!!!!
All actual build and test commands must be executed within the GitHub workflow; running them on your local machine is prohibited—local device performance is insufficient.

modify the code.

Regarding the garbled text issue you mentioned, it has been confirmed that it is not caused by file corruption. The file can be read correctly in PowerShell using the following method:
powershell
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
Get-Content -Encoding UTF8 file-path
Each time you complete the addition or modification of a feature according to my requirements, a commit message should be automatically generated and submitted and pushed after you finish modifying the code. When submitting a GPG key, you can temporarily omit the signature.
# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Working agreements (from `AGENTS.md` — read these first)

- **Do not run builds or tests locally.** Local hardware is too weak; all real build/test/lint runs happen in GitHub Actions. Push a commit and let CI verify. The commands below are documented so you know what CI runs and can reason about failures — not so you run them on this machine.
- **Commit and push after each completed change.** When you finish adding or modifying a feature, auto-generate a commit message and commit + push. GPG signing may be temporarily skipped (the key isn't required for these commits). Work happens directly on `main`.
- **"Garbled" Chinese text is NOT file corruption.** Files are UTF-8. In PowerShell read them with:
  ```powershell
  [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
  $OutputEncoding = [System.Text.Encoding]::UTF8
  Get-Content -Encoding UTF8 <file-path>
  ```
- `AGENTS.md` repeats "Do not write to a super file" — do not create large catch-all/aggregate files; keep changes scoped to the right module.

## Repository layout

Two deployable components live in one repo:

| Path | Component | Stack |
|---|---|---|
| `app/` | Android client | Kotlin, Jetpack Compose Material 3, Room, Java 21 |

Supporting: `design/lumen-ui-tokens.json` (UI tokens, mounted into the app as assets), `tools/lumen-ui-tuner/` (standalone Vite tool for tuning those tokens), `docs/` (planning/research, mostly Chinese).

## Commands (these run in CI — see workflows in `.github/workflows/`)

Android (`build.yml`, `release.yml`) — CI uses system `gradle` 9.5.1 on Java 21 (Zulu); there is no `gradlew` wrapper:
```bash
gradle assembleRelease --no-daemon --warning-mode all   # release APK (ABI splits + universal)
gradle testDebugUnitTest --no-daemon --warning-mode all --stacktrace  # JVM unit tests
gradle lintDebug --no-daemon --warning-mode all --stacktrace          # Android lint
# README shorthand for a full check: gradle test lint assembleDebug --no-daemon
```

## Android architecture (`app/`, package `com.projectlumen.app`)

- **Single Activity + Compose.** `MainActivity` hosts the whole UI; screens live in `app/app/ProjectLumen*Screens.kt` / `*FeatureEntry.kt`.
- **Manual dependency injection (no Hilt/Dagger).** `ProjectLumenApplication` constructs services and wiring; `ProjectLumenViewModel` receives them via its constructor, including **lambda callbacks** (`startTimerService`, `startLightMonitoring`, etc.) so the ViewModel can command Android services without depending on `Context`.
- **State flows one direction.** `ProjectLumenRepositories` aggregates the Room-backed repositories → `ProjectLumenStateStore` `combine`s their `Flow`s into a single `ProjectLumenUiState` via `stateIn` → the ViewModel exposes it. When touching UI state, thread data through the store rather than reading DAOs from Compose.
- **Persistence is layered:** Room (`core/database`, DAOs + entities, schemas exported to `app/schemas` via KAPT) for durable state/stats; DataStore preferences (`EyeCarePreferencesDataStore`); Tencent MMKV; and `security-crypto` (`SecureCredentialStore`) for install/device identity and secrets.
- **Background work.** Foreground services in `core/services`, `core/proximity`, `core/light`, `core/overlay`, `core/debug` — `TimerForegroundService`, `ProximityDetectionService` (camera + ML Kit face detection/mesh), `LightMonitorService`, `EyeProtectionOverlayService`, `DeveloperDebugOverlayService`. Timing uses **AlarmManager exact alarms** (`AlarmReceiver`, rescheduled by `BootReceiver`) reconciled by **WorkManager** workers (`TimerReconciliationWorker`, `ShizukuResilienceWorker`). Notifications go through `NotificationChannels` / `NotificationService` / `NotificationIds`.
- **Networking** (`core/api`): OkHttp built by `SecureOkHttpFactory` with optional certificate pinning (`CertificatePinPolicy`) and HMAC **request signing**. `ProjectLumenApiClient` talks to the Rust backend; a separate translation client targets the TTS host.
- **Shizuku** (`core/shizuku`, `dev.rikka.shizuku`) provides elevated shell operations (e.g. per-app network control) without root.
- **Native security layer** (`app/src/main/cpp/lumen_security.cpp`, built by CMake/NDK). Compiles the request-signing secret, release cert SHA-256, and expected package name into a `.so` for integrity/attestation checks; built for 16 KB page alignment. Note: the CI step that sets up the Android native toolchain is currently commented out in `build.yml` — the `externalNativeBuild` config in `app/build.gradle.kts` still references it.
- **Open API for third-party apps.** `ILumenOpenApi.aidl` + `LumenOpenService` (bound service) plus exported `openapi/*Activity` classes expose eye-fatigue level, screen time, and focus/rest control. These are gated by custom permissions `com.project.lumen.permission.ACCESS_LUMEN_CORE` (dangerous) and `TRIGGER_LUMEN_CONTROL` (signature).
- SDK: app `minSdk 29` (CRooot Android 10 floor), `compileSdk`/`targetSdk 37`, Java 21 toolchain. `lumen-crash-core` remains independently usable from API 26. ABI splits (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`) + universal APK. The bundled JetBrains Mono subset font is validated to stay < 20 KB at `preBuild`.

## Build-time configuration (Android)

`app/build.gradle.kts` reads config from env vars first, then Gradle properties, then falls back to a default — and injects it into `BuildConfig` / native defines. Key inputs (set as GitHub Actions secrets in `build.yml`): `PROJECT_LUMEN_API_BASE_URL`, `PROJECT_LUMEN_TRANSLATION_API_BASE_URL`, `PROJECT_LUMEN_TELEMETRY_ACCESS_TOKEN`, `PROJECT_LUMEN_REQUEST_SIGNING_SECRET`, `PROJECT_LUMEN_RELEASE_CERT_SHA256`, and the certificate-pinning pairs (`*_CERTIFICATE_PINNING_ENABLED` + `*_CERTIFICATE_PINS` — the build **fails** if pinning is enabled without pins). `versionName` comes from `app/application.version` (or `PROJECT_LUMEN_VERSION_NAME`); `versionCode` is derived from it or the CI run number.

## Trellis workflow (optional, agent-facing)

`.trellis/` (with `.agents/`, `.codex/`) is a spec-driven task framework: coding guidelines live under `.trellis/spec/<package>/<layer>/`, and `.trellis/workflow.md` documents a plan → execute → finish loop driven by `.trellis/scripts/task.py`. It is injected via hooks when active. The day-to-day practice in this repo (see git history) is the direct "modify → commit → push" flow described in `AGENTS.md`; only follow the full Trellis task lifecycle when explicitly asked.
