# Project Lumen

Project Lumen is a native Android Kotlin app for eye-break reminders, pomodoro timing, local statistics, templates, notifications, and CSV sharing.

## Current implementation

- Android native Kotlin project
- Java 21 toolchain
- Jetpack Compose Material 3 UI
- Room-backed local state and statistics
- System/Chinese/English i18n resources
- AlarmManager notifications and a foreground timing service
- Built-in reminder templates
- CSV statistics sharing

## Build

Use the Gradle tasks configured in `.github/workflows/build.yml`:

```bash
gradle test lint assembleDebug --no-daemon
```

Release tags run `.github/workflows/release.yml` and produce an unsigned Android release APK unless signing is added later.
