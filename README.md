# Project Lumen

Project Lumen is an Android-first eye-care and focus project. Its native client combines configurable break reminders and Pomodoro sessions with local statistics, sensor-assisted protections, privacy controls, and optional connected services. This repository also contains the reusable crash-reporting SDK, UI tokens and tuning tool, documentation site, product animation, and release automation.

## Android client

The current Kotlin and Jetpack Compose client (v1.0.1, `com.chloemlla.projectlumen`) supports:

- **Eye-break and Pomodoro** state machines with quiet hours, notifications, exact alarms, foreground timing, reboot recovery, and actionable reminders.
- **On-device goals, templates, runtime state, and trend statistics**, plus CSV/PNG/PDF sharing and JSON backup/restore.
- **Permission-gated proximity and blink monitoring** with the front camera and ML Kit, ambient-light warnings, brightness assistance, and full-screen rest overlays.
- **Optional Shizuku enhancements** for system-aware eye-care controls and app network policy management.
- **Material 3 UI** with dynamic color, light/dark themes (a tip template that ships its own palette decides light or dark and pauses the theme-mode setting without discarding it), system/Chinese/English language modes, onboarding, and a privacy/permission center.
- **Local device-usage and power insights**, signature-verified, origin-restricted per-ABI update downloads, and integrated Lumen Crash reporting for both fatal crashes and handled failures.
- **Optional translation, account, entitlement, cloud sync, cloud backup, telemetry**, and controlled AIDL/Intent integrations.

The app targets SDK 37 with a minimum SDK of 29, uses a Java/Kotlin 21 toolchain, and follows a single-Activity Compose architecture with manual dependency injection. See the [client capability inventory](docs/CLIENT_EXISTING_FEATURES.md) for the detailed implementation map.

## Repository map

| Path | Purpose |
| --- | --- |
| [`app/`](app/) | Native Android application: Compose surfaces (`app/.../app`), runtime/data/security integrations (`app/.../core`), and the permission-protected external interface (`app/.../openapi`). |
| [`lumen-crash-core/`](lumen-crash-core/), [`lumen-crash/`](lumen-crash/), [`lumen-crash-sample/`](lumen-crash-sample/) | Reusable Android crash capture, persistence, adaptive Compose report UI, and integration sample. |
| [`baselineprofile/`](baselineprofile/) | Managed-device Baseline Profile generator for the Android app. |
| [`design/`](design/) | UI tokens (`lumen-ui-tokens.json`) mounted into the Android app as assets. |
| [`tools/lumen-ui-tuner/`](tools/lumen-ui-tuner/) | Standalone Vite tool for tuning UI tokens. |
| [`docs/`](docs/) | VitePress product, engineering, adaptation, and research documentation. |
| [`remotion/android-product-animation/`](remotion/android-product-animation/) | React/Remotion source for the Android product animation. |
| [`resources/`](resources/) | Shared resources including Android icon assets. |
| [`scripts/`](scripts/) | Utility scripts for build notes, crash SDK release synchronization, Dependabot alert fixes, and 16 KB page alignment verification. |

## Build and verification

Repository policy requires all actual builds and tests to run in GitHub Actions. Do **not** run Gradle, npm build, lint, render, or test commands on the local workstation.

- [`build.yml`](.github/workflows/build.yml) builds the Android release artifacts and Baseline Profile, and runs Android unit tests and lint. Release builds fail closed when the signing secret or release certificate SHA-256 is missing; unit tests and lint run before signing.
- [`release.yml`](.github/workflows/release.yml) verifies and publishes tagged Android APK releases with checksums and a release manifest.
- [`lumen-crash-sdk-release.yml`](.github/workflows/lumen-crash-sdk-release.yml) verifies and publishes the Lumen Crash AARs.
- [`lumen-ui-tuner.yml`](.github/workflows/lumen-ui-tuner.yml) builds and deploys the UI token tuner tool.
- [`vitepress-docs.yml`](.github/workflows/vitepress-docs.yml) builds and publishes the documentation site.
- [`remotion-android-product-animation.yml`](.github/workflows/remotion-android-product-animation.yml) validates and renders the product animation.
- [`codeql.yml`](.github/workflows/codeql.yml) performs repository security analysis.
- [`dependabot-maintenance.yml`](.github/workflows/dependabot-maintenance.yml) automates Dependabot alert triage and fix PRs.

Trigger the relevant workflow through a push, pull request, tag, or `workflow_dispatch`, then inspect its logs, reports, and uploaded artifacts. Installable Android artifacts are published through [GitHub Releases](https://github.com/Chloemlla/Project-Lumen/releases).

## Configuration and secret management

Build-time configuration and CI credentials are provisioned as GitHub Actions secrets (repository **Settings → Secrets and variables → Actions**). The workflows read them via `secrets.*` in [`build.yml`](.github/workflows/build.yml) and [`release.yml`](.github/workflows/release.yml). Version and build-identity keys are derived by CI and do **not** need to be stored.

| Secret | Consumed in | Purpose |
| --- | --- | --- |
| `KEYSTORE_BASE64` | `build.yml`, `release.yml` | Base64 of the Android signing `.jks` keystore |
| `KEYSTORE_PASSWORD` | `build.yml`, `release.yml` | Keystore password |
| `KEY_ALIAS` | `build.yml`, `release.yml` | Signing key alias |
| `KEY_PASSWORD` | `build.yml`, `release.yml` | Signing private-key password |
| `PROJECT_LUMEN_API_BASE_URL` | `build.yml`, `release.yml` | Client API base URL |
| `PROJECT_LUMEN_API_CERTIFICATE_PINNING_ENABLED` | `build.yml`, `release.yml` | Enable API certificate pinning |
| `PROJECT_LUMEN_API_CERTIFICATE_PINS` | `build.yml`, `release.yml` | API certificate pins |
| `PROJECT_LUMEN_TRANSLATION_API_BASE_URL` | `build.yml`, `release.yml` | Translation service base URL |
| `PROJECT_LUMEN_TRANSLATION_CERTIFICATE_PINNING_ENABLED` | `build.yml`, `release.yml` | Enable translation certificate pinning |
| `PROJECT_LUMEN_TRANSLATION_CERTIFICATE_PINS` | `build.yml`, `release.yml` | Translation certificate pins |
| `PROJECT_LUMEN_TELEMETRY_ACCESS_TOKEN` | `build.yml`, `release.yml` | Telemetry access token |
| `PROJECT_LUMEN_REQUEST_SIGNING_SECRET` | `build.yml`, `release.yml` | HMAC request-signing secret compiled into the native security layer |
| `PROJECT_LUMEN_RELEASE_CERT_SHA256` | `build.yml`, `release.yml` | Release certificate SHA-256 for integrity checks |
| `PROJECT_LUMEN_OPEN_API_TRUSTED_SIGNATURE_SHA256` | `build.yml`, `release.yml` | Trusted third-party signature for the exported Open API |
| `PROJECT_LUMEN_ADMIN_ACTIONS_URL` | `build.yml`, `release.yml` | Release-manifest sync admin action URL |
| `PROJECT_LUMEN_ADMIN_TOKEN` | `build.yml`, `release.yml` | Release-manifest sync bearer token |
| `USER_PAT` | `lumen-ui-tuner.yml`, `dependabot-maintenance.yml` | PAT for the UI-tuner deploy and Dependabot PR operations |

The 13 non-signing secrets above can also be stored in the **Project Lumen** section of the [Happy-TTS](https://github.com/Chloemlla/Happy-TTS) env-manager (admin → Env Manager) and pushed to this repo's Actions secrets in one click ("同步全部到 GitHub"). The four keystore signing secrets are intentionally managed out-of-band and not stored there. CI-derived keys that require no configuration: `PROJECT_LUMEN_VERSION_NAME`, `PROJECT_LUMEN_VERSION_CODE`, `PROJECT_LUMEN_BUILD_TIME_UTC_MILLIS`, `PROJECT_LUMEN_COMMIT_HASH`, `PROJECT_LUMEN_SHORT_HASH`.

Keep all secret values out of source and documentation; rotate them through the GitHub UI or the Happy-TTS env-manager.

`PROJECT_LUMEN_REQUEST_SIGNING_SECRET` and `PROJECT_LUMEN_RELEASE_CERT_SHA256` are hard requirements for release builds — a missing value fails the build instead of silently falling back to a known constant. Local diagnostics can opt out explicitly with `-PprojectLumenAllowInsecureRelease=true`.

## Security and privacy

- **Core settings, runtime state, goals, templates, and statistics** are stored on-device with Room/MMKV-backed repositories. Account credentials are handled through encrypted credential storage.
- **Camera, usage access, notifications, exact alarms, overlays, system brightness, and Shizuku** capabilities are surfaced as feature-specific permission controls rather than assumed access.
- **Connected features** use HTTPS. The client also contains request signing (a hard requirement for release builds), optional certificate pinning, app-integrity checks, release SHA-256 verification, and signature checks for external app callers. Crash reports are redacted before leaving the device and uploaded over HTTPS-only redirects; handled failures are reported without interrupting the UI.
- **Translation, account, cloud, telemetry, and update** features communicate with external services. Review their settings and data flow before enabling or changing them.
- **Keep signing credentials, access tokens, API secrets, and production security material** in GitHub Actions secrets or protected deployment configuration; do not add new secret values to source or documentation.

## Documentation

- [Documentation home](docs/index.md)
- [Android client capabilities](docs/CLIENT_EXISTING_FEATURES.md)
- [System update and release strategy](docs/SYSTEM_UPDATE_STRATEGY_IMPLEMENTATION.md)
- [Remote account, entitlement, sync, and backup landing notes](docs/TODO1_REMOTE_INTEGRATION_LANDING.md)
- [Eye-care insights landing notes](docs/TODO2_EYE_CARE_INSIGHTS_LANDING.md)
- [Vivo adaptation workflow](docs/VIVO_ADAPTATION_DOC_WORKFLOW.md) — covering Android 11 through 17
- [UI/UX review (August 2026)](docs/uiux-review-2026-08-01.md)
- [Product animation guide](docs/REMOTION_ANDROID_PRODUCT_ANIMATION_GUIDE.md)
- [Lumen Crash SDK guide](lumen-crash/README.md) · [中文](lumen-crash/README.zh-CN.md)

## August 2026 architecture and code audit

A parallel 11-track audit of the Android client, the crash SDK, and CI completed in August 2026 raised 229 findings. The itemized [audit checklist](docs/audit/2026-08-31/AUDIT-CHECKLIST.md) tracks 195 of 230 items as fixed or verified closed — including every P0 — and gives a per-item rationale for the remainder. User-visible outcomes include more consistent reminder and statistics accounting, a fail-closed signing and update trust chain, privacy-hardened crash reporting, and CI that verifies tests and lint before any release artifact is signed.

## Contributing

Before changing code or documentation, read [`AGENTS.md`](AGENTS.md). Keep changes focused, preserve module boundaries, document permission or data-flow changes, and let the matching GitHub workflow perform the required verification. Pull requests should explain the user-visible behavior, affected modules, and workflow evidence.

Project Lumen is licensed under the [Apache License 2.0](LICENSE).