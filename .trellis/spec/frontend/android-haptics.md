# Android Haptics

## Scenario: Vendor HE Haptic Playback

### 1. Scope / Trigger

- Trigger: Android client code adds or changes reminder vibration, tactile feedback, HE waveform generation, or vendor haptic playback.
- Source scope: `app/src/main/java/com/projectlumen/app/core/haptics/**`, `AudioService`, and any UI/open API path that emits `AudioEvent.ReminderTone(vibrate = true)`.
- Dependency scope: vendor `android.os.DynamicEffect` / `android.os.HapticPlayer` APIs come from `compileOnly("gsai.sdk:hestub:1.0.0")`. Never package hestub as `implementation`; stub `android.os.*` classes can crash AOSP / baseline managed devices on cold start.
- Repository scope: dependency resolution includes `http://nexus.itgsa.com:5566/repository/release/` with insecure protocol explicitly allowed.

### 2. Signatures

- `HapticHeEffect.transient(effect: HapticTransientEffect): String`
- `HapticHeEffect.continuous(effect: HapticContinuousEffect): String`
- `HapticHeEffect.reminderNudge(): String`
- `HapticPlaybackService.isAvailable(): Boolean`
- `HapticPlaybackService.playHeJson(json: String, options: HapticPlaybackOptions): Boolean`
- `HapticPlaybackService.playTransient(effect: HapticTransientEffect, options: HapticPlaybackOptions): Boolean`
- `HapticPlaybackService.playContinuous(effect: HapticContinuousEffect, options: HapticPlaybackOptions): Boolean`
- `HapticPlaybackService.updateInterval(intervalMillis: Int): Boolean`
- `HapticPlaybackService.updateAmplitude(amplitude: Int): Boolean`
- `HapticPlaybackService.updateFrequency(frequency: Int): Boolean`
- `HapticPlaybackService.updateParameter(intervalMillis: Int, amplitude: Int, frequency: Int): Boolean`
- `HapticPlaybackService.stop(): Boolean`

### 3. Contracts

- HE JSON must follow the vendor format: `Metadata` plus a `Pattern` array of `Event` objects.
- Transient events use `Type = "transient"`, non-negative `RelativeTime`, and `Parameters.Intensity/Frequency` clamped to `[0, 100]`.
- Continuous events use `Type = "continuous"`, positive `Duration`, and a `Curve` whose first and last points have `Intensity = 0`.
- Playback options clamp `intervalMillis` to `[0, 1000]` and `amplitude` to `[1, 255]`; `loop = 1` means no repeat and `loop = -1` means infinite repeat.
- `HapticPlaybackService` must access `android.os.DynamicEffect` and `android.os.HapticPlayer` only through reflection / runtime class lookup (hestub is compile-time), so stock AOSP emulators and baseline-profile managed devices never fail class loading.
- Every vendor player call must fail closed; unsupported devices fall back to platform vibration.
- `AudioService` should try `HapticPlaybackService.playReminderNudge()` first when `vibrate = true`, then fall back to platform `VibrationEffect` if the vendor player is unavailable or playback fails.
- The vendor Maven repository uses the credentials provided by the haptic SDK integration guide.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| `android.os.HapticPlayer` or `android.os.DynamicEffect` is absent at runtime | Player calls fail closed; callers fall back to platform vibration. |
| Vendor `HapticPlayer.isAvailable()` returns false | Playback methods return false without throwing. |
| Optional update APIs are absent | `updateInterval`, `updateAmplitude`, `updateFrequency`, or `updateParameter` returns false. |
| HE intensity/frequency inputs exceed vendor ranges | HE JSON clamps values before playback. |
| Reminder vibration requested on unsupported device | `AudioService` uses `VibrationEffect.createOneShot(...)`. |

### 5. Good/Base/Bad Cases

- Good: reminders on supported high-voltage linear motor devices play a short HE nudge and can still update interval/amplitude/frequency through `HapticPlaybackService`.
- Base: ordinary Android devices without vendor haptic APIs retain the previous one-shot vibration behavior.
- Bad: hard-importing `android.os.HapticPlayer` / `DynamicEffect` so the class fails to load on AOSP emulators.
- Bad: calling vendor `start(...)` without availability checks and exception handling.
- Bad: removing the platform `VibrationEffect` fallback and making reminders silent on unsupported devices.

### 6. Tests Required

- Unit test: transient HE generation clamps `RelativeTime`, `Intensity`, and `Frequency`.
- Unit test: continuous HE generation inserts required zero-intensity start/end curve points.
- GitHub workflow: `gradle testDebugUnitTest --no-daemon --warning-mode all` covers the pure HE generator tests.
- Manual review: verify `AudioService.vibrate()` keeps the fallback `VibrationEffect` path after attempting vendor haptics.

### 7. Wrong vs Correct

#### Wrong

```kotlin
// Hard dependency crashes class loading on AOSP / baseline managed devices.
import android.os.HapticPlayer
import android.os.DynamicEffect
HapticPlayer(DynamicEffect.create(json)).start(1)
```

#### Correct

```kotlin
// Reflective vendor access fails closed when framework classes are absent.
if (!haptics.playReminderNudge()) {
    vibrator.vibrate(VibrationEffect.createOneShot(90L, VibrationEffect.DEFAULT_AMPLITUDE))
}
```
