# Android Baseline Profiles

> Contract for Android Baseline Profile generation and release integration in the Kotlin/Compose client.

## 1. Scope / Trigger

- Trigger: any change to Baseline Profile generation, Macrobenchmark dependencies, ProfileInstaller, Gradle Managed Devices, or GitHub Actions steps that run profile generation.
- App scope: `app/build.gradle.kts`, `app/src/**`, and generated profile source sets under `app/src/**/baselineProfiles/`.
- Producer scope: `baselineprofile/**`.
- CI scope: `.github/workflows/build.yml` and any workflow that executes Android Gradle build/test/profile tasks.
- Local policy: do not run Gradle build, lint, test, benchmark, or profile generation commands locally when repository policy requires GitHub Actions.

## 2. Signatures

```kotlin
// settings.gradle.kts
include(":app")
include(":baselineprofile")

// build.gradle.kts
id("androidx.baselineprofile") version "<androidx-benchmark-version>" apply false
id("com.android.test") version "<agp-version>" apply false

// app/build.gradle.kts
id("androidx.baselineprofile")
implementation("androidx.profileinstaller:profileinstaller:<version>")
baselineProfile(project(":baselineprofile"))

baselineProfile {
    automaticGenerationDuringBuild = false
    mergeIntoMain = true
    saveInSrc = true
}
```

```kotlin
// baselineprofile/build.gradle.kts
plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    targetProjectPath = ":app"
    testOptions.managedDevices.devices.create<ManagedVirtualDevice>("<deviceName>")
}

baselineProfile {
    managedDevices = listOf("<deviceName>")
    useConnectedDevices = false
}
```

```yaml
# .github/workflows/build.yml
run: gradle :app:generateBaselineProfile --no-daemon --warning-mode all
```

## 3. Contracts

- App consumer contract: the app module owns ProfileInstaller and the `baselineProfile(project(":baselineprofile"))` dependency.
- Producer contract: `baselineprofile` is a standalone `com.android.test` module targeting `:app`; profile journeys live under `baselineprofile/src/main/java`.
- Package contract: generator tests must collect profiles for the real application id, currently `com.chloemlla.projectlumen`, not the app namespace.
- Startup contract: startup profile generation should launch the app and cover the first high-value screen path. If onboarding appears on clean install, the journey may dismiss it before exercising the home surface.
- Managed-device boot contract: Application/Activity startup paths used by the generator must not hard-crash on AOSP emulators. Vendor-only APIs (for example hestub `HapticPlayer`) must be optional/reflective, and crash-SDK install/load plus integrity enforcement should not process-kill cold start during profile generation.
- CI contract: profile generation runs in GitHub Actions with Android emulator acceleration before release assembly. With `mergeIntoMain = true` on current AndroidX Benchmark/ProfileInstaller releases, the app workflow invokes the consumer aggregate task `:app:generateBaselineProfile`; local machines are limited to static inspection.
- Version contract: keep `androidx.baselineprofile`, `androidx.benchmark:benchmark-macro-junit4`, and `androidx.profileinstaller` on compatible AndroidX Benchmark/ProfileInstaller releases.

## 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Baseline Profile generator module missing | `:app:generateBaselineProfile` cannot resolve the producer dependency; add `include(":baselineprofile")` and `baselineProfile(project(":baselineprofile"))`. |
| Workflow calls `:app:generateReleaseBaselineProfile` while `mergeIntoMain = true` | AGP/AndroidX may not register the release-specific consumer task; call `:app:generateBaselineProfile` instead. |
| Generator uses namespace instead of application id | The profile task targets the wrong package; use `com.chloemlla.projectlumen`. |
| GitHub runner lacks KVM access | Managed device startup is slow or fails; workflow must enable KVM before profile generation. |
| Profile generation is attempted locally | Stop and move execution to GitHub Actions per repository policy. |
| Onboarding appears on clean install | Generator should dismiss it or profile the intended first-run journey deliberately. |
| Target package launches then dies immediately | Check ABI packaging (universal/x86_64), optional vendor API class-loading, and Application startup exceptions. |
| AndroidX Benchmark versions drift | Align plugin, macrobenchmark, and ProfileInstaller versions before CI execution. |

## 5. Good/Base/Bad Cases

- Good: a dedicated `baselineprofile` module runs `BaselineProfileRule` against a Gradle Managed Device in GitHub Actions, then release assembly packages the generated profile.
- Base: the generator launches the app, dismisses first-run onboarding when present, and exercises a short home-screen interaction without locale-fragile navigation.
- Bad: putting Macrobenchmark tests inside `app/src/androidTest`, hand-writing a large stale `baseline-prof.txt`, or making local profile generation part of the developer workflow.

## 6. Tests Required

- GitHub workflow: run `gradle :app:generateBaselineProfile --no-daemon --warning-mode all` before release APK assembly.
- GitHub workflow: run the existing release build, unit test, and lint steps after profile generation.
- Static review: verify the generator target package matches `projectLumenApplicationId`.
- Static review: verify no generated profile or benchmark output directories are committed unless intentionally saved as source profiles.

## 7. Wrong vs Correct

#### Wrong

```kotlin
// Runs against the namespace, not the installed app id.
baselineProfileRule.collect(packageName = "com.projectlumen.app") {
    startActivityAndWait()
}
```

#### Correct

```kotlin
baselineProfileRule.collect(
    packageName = "com.chloemlla.projectlumen",
    includeInStartupProfile = true,
) {
    startActivityAndWait()
}
```

#### Wrong

```text
gradle :app:generateReleaseBaselineProfile
```

#### Correct

```yaml
- name: Generate release baseline profile
  run: gradle :app:generateBaselineProfile --no-daemon --warning-mode all
```
