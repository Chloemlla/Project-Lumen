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

Three deployable components live in one repo:

| Path | Component | Stack |
|---|---|---|
| `app/` | Android client | Kotlin, Jetpack Compose Material 3, Room, Java 21 |
| `backend/` | API service | Rust (axum + tokio + MongoDB) |
| `backend/admin/` | Admin dashboard | React 19 + TypeScript + Vite, served by the Rust binary at `/admin` |

Supporting: `design/lumen-ui-tokens.json` (UI tokens, mounted into the app as assets), `tools/lumen-ui-tuner/` (standalone Vite tool for tuning those tokens), `docs/` (planning/research, mostly Chinese), `Dockerfile` (multi-stage: Rust backend + Node admin → Debian runtime).

## Commands (these run in CI — see workflows in `.github/workflows/`)

Android (`build.yml`, `release.yml`) — CI uses system `gradle` 9.5.1 on Java 21 (Zulu); there is no `gradlew` wrapper:
```bash
gradle assembleRelease --no-daemon --warning-mode all   # release APK (ABI splits + universal)
gradle testDebugUnitTest --no-daemon --warning-mode all  # JVM unit tests
gradle lintDebug --no-daemon --warning-mode all          # Android lint
# README shorthand for a full check: gradle test lint assembleDebug --no-daemon
```

Rust backend (`build.yml`, runs from repo root):
```bash
cargo fmt   --manifest-path backend/Cargo.toml --all -- --check
cargo test  --manifest-path backend/Cargo.toml --all-targets
cargo build --manifest-path backend/Cargo.toml --release
```

Admin dashboard (`backend/admin/`):
```bash
npm install
npm run build      # tsc -b && vite build → dist/ (the Rust service serves this)
npm run dev        # local Vite dev server
```

## Android architecture (`app/`, package `com.projectlumen.app`)

- **Single Activity + Compose.** `MainActivity` hosts the whole UI; screens live in `app/app/ProjectLumen*Screens.kt` / `*FeatureEntry.kt`.
- **Manual dependency injection (no Hilt/Dagger).** `ProjectLumenApplication` constructs services and wiring; `ProjectLumenViewModel` receives them via its constructor, including **lambda callbacks** (`startTimerService`, `startLightMonitoring`, etc.) so the ViewModel can command Android services without depending on `Context`.
- **State flows one direction.** `ProjectLumenRepositories` aggregates the Room-backed repositories → `ProjectLumenStateStore` `combine`s their `Flow`s into a single `ProjectLumenUiState` via `stateIn` → the ViewModel exposes it. When touching UI state, thread data through the store rather than reading DAOs from Compose.
- **Persistence is layered:** Room (`core/database`, DAOs + entities, schemas exported to `app/schemas` via KSP) for durable state/stats; DataStore preferences (`EyeCarePreferencesDataStore`); Tencent MMKV; and `security-crypto` (`SecureCredentialStore`) for install/device identity and secrets.
- **Background work.** Foreground services in `core/services`, `core/proximity`, `core/light`, `core/overlay`, `core/debug` — `TimerForegroundService`, `ProximityDetectionService` (camera + ML Kit face detection/mesh), `LightMonitorService`, `EyeProtectionOverlayService`, `DeveloperDebugOverlayService`. Timing uses **AlarmManager exact alarms** (`AlarmReceiver`, rescheduled by `BootReceiver`) reconciled by **WorkManager** workers (`TimerReconciliationWorker`, `ShizukuResilienceWorker`). Notifications go through `NotificationChannels` / `NotificationService` / `NotificationIds`.
- **Networking** (`core/api`): OkHttp built by `SecureOkHttpFactory` with optional certificate pinning (`CertificatePinPolicy`) and HMAC **request signing**. `ProjectLumenApiClient` talks to the Rust backend; a separate translation client targets the TTS host.
- **Shizuku** (`core/shizuku`, `dev.rikka.shizuku`) provides elevated shell operations (e.g. per-app network control) without root.
- **Native security layer** (`app/src/main/cpp/lumen_security.cpp`, built by CMake/NDK). Compiles the request-signing secret, release cert SHA-256, and expected package name into a `.so` for integrity/attestation checks; built for 16 KB page alignment. Note: the CI step that sets up the Android native toolchain is currently commented out in `build.yml` — the `externalNativeBuild` config in `app/build.gradle.kts` still references it.
- **Open API for third-party apps.** `ILumenOpenApi.aidl` + `LumenOpenService` (bound service) plus exported `openapi/*Activity` classes expose eye-fatigue level, screen time, and focus/rest control. These are gated by custom permissions `com.project.lumen.permission.ACCESS_LUMEN_CORE` (dangerous) and `TRIGGER_LUMEN_CONTROL` (signature).
- SDK: `minSdk 26`, `compileSdk`/`targetSdk 37`, Java 21 toolchain. ABI splits (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`) + universal APK. The bundled JetBrains Mono subset font is validated to stay < 20 KB at `preBuild`.

## Backend architecture (`backend/`)

- **axum** router assembled in `src/api.rs`. Routes (`src/routes/*.rs`) are nested under a configurable prefix (default `/api`): a `/v1` group (also mirrored at root for legacy clients) behind optional `security::enforce_api_security` request-signature verification (`LUMEN_REQUIRE_REQUEST_SIGNING=true`) and `audit::audit_request`, plus `/admin` and public `platform` routes. The same binary serves the admin SPA from `/admin` and `/assets`.
- **Layering:** `routes/` (HTTP) → `store/` (MongoDB collections) → `models/` (serde types). `state.rs` holds `AppState` (Mongo client + config); `config.rs` reads all `LUMEN_*` env vars; `server.rs` wires CORS/tracing and binds the listener.
- **Purchases fail closed** (`LUMEN_ACCEPT_UNVERIFIED_PURCHASES=false`) until real Play verification is wired in. Email login codes are sent via Happy-TTS **outemail** (`outemail.rs`); when no API key is set, `/api/v1/auth/email/start` returns a dev code.
- See `backend/README.md` for the full env var list, endpoint catalog, and the 20 admin-dashboard modules / MongoDB collections.

## Cross-component contract

When backend request signing is explicitly enabled with `LUMEN_REQUIRE_REQUEST_SIGNING=true`, the Android build's `PROJECT_LUMEN_REQUEST_SIGNING_SECRET` **must equal** the backend's `LUMEN_REQUEST_SIGNING_SECRET`, or signed client requests get HTTP 403. Backend request-signature verification is disabled by default, including production deployments.

## Build-time configuration (Android)

`app/build.gradle.kts` reads config from env vars first, then Gradle properties, then falls back to a default — and injects it into `BuildConfig` / native defines. Key inputs (set as GitHub Actions secrets in `build.yml`): `PROJECT_LUMEN_API_BASE_URL`, `PROJECT_LUMEN_TRANSLATION_API_BASE_URL`, `PROJECT_LUMEN_TELEMETRY_ACCESS_TOKEN`, `PROJECT_LUMEN_REQUEST_SIGNING_SECRET`, `PROJECT_LUMEN_RELEASE_CERT_SHA256`, and the certificate-pinning pairs (`*_CERTIFICATE_PINNING_ENABLED` + `*_CERTIFICATE_PINS` — the build **fails** if pinning is enabled without pins). `versionName` comes from `app/application.version` (or `PROJECT_LUMEN_VERSION_NAME`); `versionCode` is derived from it or the CI run number.

## Trellis workflow (optional, agent-facing)

`.trellis/` (with `.agents/`, `.codex/`) is a spec-driven task framework: coding guidelines live under `.trellis/spec/<package>/<layer>/`, and `.trellis/workflow.md` documents a plan → execute → finish loop driven by `.trellis/scripts/task.py`. It is injected via hooks when active. The day-to-day practice in this repo (see git history) is the direct "modify → commit → push" flow described in `AGENTS.md`; only follow the full Trellis task lifecycle when explicitly asked.
