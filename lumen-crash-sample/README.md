# Lumen Crash SDK Sample

Minimal host for the short integration path:

1. `LumenCrash.installSafely(this) { ... }` in `SampleApplication`
2. Enable the startup watchdog and call `LumenCrash.markStartupComplete()` after the first frame
3. `LumenCrashGate { ... }` in `MainActivity`
4. Force an uncaught crash, relaunch, then use the crash UI

Build in CI / workflow only:

```bash
gradle :lumen-crash-sample:assembleDebug --no-daemon
gradle :lumen-crash-sample:assembleRelease --no-daemon
```

Release minify is enabled in the sample and uses package-level keep rules plus
`res/raw/keep.xml` for SDK strings.
