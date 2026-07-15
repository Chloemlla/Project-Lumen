#!/usr/bin/env bash
set -euo pipefail

# Connected instrumentation smoke used by .github/workflows/lumen-ui-tuner.yml.
# Kept as a standalone script so android-emulator-runner does not split multiline
# shell control flow into one-shot `sh -c` lines.

adb wait-for-device

for attempt in 1 2 3 4 5; do
  if adb shell cmd package list packages >/dev/null 2>&1; then
    break
  fi
  if [ "$attempt" = "5" ]; then
    echo "Android package manager did not become ready before connected tests." >&2
    exit 1
  fi
  sleep 10
done

run_connected_test() {
  gradle connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.projectlumen.app.app.LumenTopBarScreenshotTest \
    --no-daemon \
    --warning-mode all
}

for attempt in 1 2 3; do
  log_file="connected-debug-android-test-attempt-${attempt}.log"
  set +e
  run_connected_test >"$log_file" 2>&1
  status=$?
  set -e
  cat "$log_file"

  if [ "$status" -eq 0 ]; then
    break
  fi

  if ! grep -Eq "Failed to install split APK|Broken pipe|DdmlibAndroidDeviceController|EmulatorConsole" "$log_file"; then
    exit "$status"
  fi

  if [ "$attempt" = "3" ]; then
    exit "$status"
  fi

  echo "Retrying connected Android test after emulator install failure (attempt ${attempt})."
  adb wait-for-device || true
  adb uninstall com.chloemlla.projectlumen.test >/dev/null 2>&1 || true
  adb uninstall com.chloemlla.projectlumen >/dev/null 2>&1 || true
  sleep 15
done

mkdir -p ui-screenshot-artifacts
adb pull /sdcard/Android/data/com.chloemlla.projectlumen/files/screenshots ui-screenshot-artifacts || true
