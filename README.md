# Project Lumen

Project Lumen is a native Android app for healthier screen work. It helps users schedule eye breaks, run Pomodoro focus sessions, keep local usage statistics, and share those statistics as CSV.

The current repository contains the Kotlin/Android implementation of Project Lumen, built with Jetpack Compose and Room.

## Features

- Eye-break reminders with configurable work intervals and rest duration.
- Optional pre-alerts before a break is due.
- Break flow with start, skip, countdown, and completion tracking.
- Pomodoro timer with focus, short break, and long break phases.
- Daily local statistics for work time, rest time, skipped breaks, completed breaks, focus sessions, and completed Pomodoro cycles.
- CSV statistics sharing through Android's native share sheet.
- Light, dark, and system theme modes.
- English and Chinese localization.
- Reminder template data model with seeded built-in templates.
- Local notification channels and alarm receiver plumbing for reminder flows.

## Tech Stack

- Kotlin
- Android Gradle Plugin
- Jetpack Compose
- Material 3
- AndroidX Navigation Compose
- AndroidX Lifecycle and ViewModel
- Room database with KSP
- Android local notifications, alarms, and FileProvider sharing

## Repository Layout

```text
.
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/projectlumen/app/
│       │   ├── MainActivity.kt
│       │   ├── ProjectLumenApplication.kt
│       │   ├── app/                 # Compose app shell, screens, and ViewModel
│       │   ├── core/database/       # Room database, DAOs, and entities
│       │   ├── core/enums/          # Runtime state enums
│       │   ├── core/i18n/           # Locale handling
│       │   ├── core/services/       # Notifications, alarms, audio, and export
│       │   └── ui/theme/            # Compose theme definitions
│       └── res/                     # Strings, styles, icons, XML config
├── .github/workflows/
│   ├── build.yml                    # CI build, tests, lint, debug APK artifact
│   ├── release.yml                  # Release APK workflow
│   └── codeql.yml                   # GitHub Actions CodeQL analysis
├── docs.md                          # Android migration and design notes
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Architecture

Project Lumen keeps timer state in local persistent storage instead of relying only on in-memory countdowns. The main runtime state is stored in Room so the app can recover after process death, backgrounding, or device sleep.

Core state is coordinated by `ProjectLumenViewModel`, which observes:

- `AppSettingsEntity` for user settings.
- `RuntimeStateEntity` for the active reminder or Pomodoro phase.
- `DailyEyeStatsEntity` for eye-break statistics.
- `DailyPomodoroStatsEntity` for Pomodoro statistics.
- `TipTemplateEntity` for reminder template metadata.

The Compose UI reads a single `ProjectLumenUiState` and renders the main sections:

- Home
- Break
- Pomodoro
- Statistics
- Settings

## Data Model

The app uses a Room database named `project_lumen_mobile.db`.

Important tables include:

- `app_settings`: language, theme, reminder, pre-alert, notification, sound, and Pomodoro settings.
- `runtime_state`: active timer engine, reminder phase, Pomodoro phase, scheduled timestamps, pause state, and recovery metadata.
- `daily_eye_stats`: per-day work, rest, skip, pre-alert, and completed-break counters.
- `daily_pomodoro_stats`: per-day Pomodoro focus, break, restart, and completion counters.
- `tip_templates`: built-in and future custom reminder template metadata.

## CI and Verification

Builds and tests for this repository are intended to run in GitHub Actions, not on local machines.

The `Build Project Lumen Android` workflow runs Android verification on pushes, pull requests, and manual dispatches. It performs the Gradle test, lint, and debug APK build steps in CI, then uploads the debug APK as a workflow artifact.

The release workflow builds an unsigned release APK for tags beginning with `v` and for manual dispatches.

## Development Notes

- Follow the repository instructions in `AGENTS.md`: do not run local build or test commands, and do not run installation commands.
- Keep timer behavior based on persisted timestamps. Runtime memory and notifications should not be treated as the source of truth.
- Prefer scoped changes that fit the existing Compose, ViewModel, Room, and service structure.
- When reading UTF-8 files in PowerShell, configure UTF-8 output first if text appears garbled.

## Documentation

See `docs.md` for the original Android migration and implementation design notes, including the reminder state machine, Pomodoro state machine, persistence strategy, and feature roadmap.
