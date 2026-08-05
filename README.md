# Project Lumen

Project Lumen is an Android-first eye-care and focus project. Its native client combines configurable break reminders and Pomodoro sessions with local statistics, sensor-assisted protections, privacy controls, and optional connected services. This repository also contains the reusable crash-reporting SDK, documentation site, product animation, and release automation.

## Android client

The current Kotlin and Jetpack Compose client supports:

- Eye-break and Pomodoro state machines with quiet hours, notifications, exact alarms, foreground timing, reboot recovery, and actionable reminders.
- On-device goals, templates, runtime state, and trend statistics, plus CSV/PNG/PDF sharing and JSON backup/restore.
- Permission-gated proximity and blink monitoring with the front camera and ML Kit, ambient-light warnings, brightness assistance, and full-screen rest overlays.
- Optional Shizuku enhancements for system-aware eye-care controls and app network policy management.
- Material 3 UI, dynamic color, light/dark themes, system/Chinese/English language modes, onboarding, and a privacy/permission center.
- Local device-usage and power insights, verified per-ABI update downloads, and integrated Lumen Crash reporting.
- Optional translation, account, entitlement, cloud sync, cloud backup, telemetry, and controlled AIDL/Intent integrations.

The app currently has a minimum SDK of 26, targets SDK 37, and uses a Java/Kotlin 21 toolchain. See the [client capability inventory](docs/CLIENT_EXISTING_FEATURES.md) for the detailed implementation map.

## Repository map

| Path | Purpose |
| --- | --- |
| [`app/`](app/) | Native Android application: Compose surfaces, runtime engines, services, local repositories, security, updates, and the external app API. |
| [`lumen-crash-core/`](lumen-crash-core/), [`lumen-crash/`](lumen-crash/), [`lumen-crash-sample/`](lumen-crash-sample/) | Reusable Android crash capture, persistence, adaptive Compose report UI, and integration sample. |
| [`baselineprofile/`](baselineprofile/) | Managed-device Baseline Profile generator for the Android app. |
| [`docs/`](docs/) | VitePress product, engineering, adaptation, and research documentation. |
| [`remotion/android-product-animation/`](remotion/android-product-animation/) | React/Remotion source for the Android product animation. |

Within the Android client, Compose product surfaces live under `app/.../app`, runtime/data/security integrations under `app/.../core`, and the permission-protected external interface under `app/.../openapi`.

## Build and verification

Repository policy requires all actual builds and tests to run in GitHub Actions. Do **not** run Gradle, Cargo, npm build, lint, render, or test commands on the local workstation.

- [`build.yml`](.github/workflows/build.yml) builds the Android release artifacts and Baseline Profile, and runs Android unit tests and lint.
- [`release.yml`](.github/workflows/release.yml) verifies and publishes tagged Android APK releases with checksums and a release manifest.
- [`lumen-crash-sdk-release.yml`](.github/workflows/lumen-crash-sdk-release.yml) verifies and publishes the Lumen Crash AARs.
- [`vitepress-docs.yml`](.github/workflows/vitepress-docs.yml) builds and publishes the documentation site.
- [`remotion-android-product-animation.yml`](.github/workflows/remotion-android-product-animation.yml) validates and renders the product animation.
- [`codeql.yml`](.github/workflows/codeql.yml) performs repository security analysis.

Trigger the relevant workflow through a push, pull request, tag, or `workflow_dispatch`, then inspect its logs, reports, and uploaded artifacts. Installable Android artifacts are published through [GitHub Releases](https://github.com/Chloemlla/Project-Lumen/releases).

## Security and privacy

- Core settings, runtime state, goals, templates, and statistics are stored on-device with Room/MMKV-backed repositories. Account credentials are handled through encrypted credential storage.
- Camera, usage access, notifications, exact alarms, overlays, system brightness, and Shizuku capabilities are surfaced as feature-specific permission controls rather than assumed access.
- Connected features use HTTPS. The client also contains request signing, optional certificate pinning, app-integrity checks, release SHA-256 verification, and signature checks for external app callers.
- Translation, account, cloud, telemetry, and update features communicate with external services. Review their settings and data flow before enabling or changing them.
- Keep signing credentials, access tokens, API secrets, and production security material in GitHub Actions secrets or protected deployment configuration; do not add new secret values to source or documentation.

## Documentation

- [Documentation home](docs/index.md)
- [Android client capabilities](docs/CLIENT_EXISTING_FEATURES.md)
- [System update and release strategy](docs/SYSTEM_UPDATE_STRATEGY_IMPLEMENTATION.md)
- [Remote account, entitlement, sync, and backup landing notes](docs/TODO1_REMOTE_INTEGRATION_LANDING.md)
- [Eye-care insights landing notes](docs/TODO2_EYE_CARE_INSIGHTS_LANDING.md)
- [Vivo adaptation workflow](docs/VIVO_ADAPTATION_DOC_WORKFLOW.md)
- [Lumen Crash SDK guide](lumen-crash/README.md) · [中文](lumen-crash/README.zh-CN.md)

## Contributing

Before changing code or documentation, read [`AGENTS.md`](AGENTS.md) and the relevant indexes under [`.trellis/spec/`](.trellis/spec/). Keep changes focused, preserve module boundaries, document permission or data-flow changes, and let the matching GitHub workflow perform the required verification. Pull requests should explain the user-visible behavior, affected modules, and workflow evidence.

Project Lumen is licensed under the [Apache License 2.0](LICENSE).
