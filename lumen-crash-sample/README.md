# Lumen Crash SDK Sample

Minimal host for the short integration path:

1. `LumenCrash.installSafely(this) { ... }` in `SampleApplication`
2. `LumenCrashGate { ... }` in `MainActivity`
3. Force an uncaught crash, relaunch, then use the crash UI

Build in CI / workflow only:

```bash
gradle :lumen-crash-sample:assembleDebug --no-daemon
```
