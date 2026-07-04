# Android Native Libraries

> Contracts for Android native `.so` libraries and APK packaging.

---

## Scenario: 16 KB Page-Size Support

### 1. Scope / Trigger

- Trigger: any change to `app/src/main/cpp/**`, `externalNativeBuild`, `packaging.jniLibs`, NDK/CMake versions, or dependencies that ship native `.so` files.
- Scope: Project Lumen Android APKs must stay compatible with Android devices that use 16 KB memory pages.

### 2. Signatures

```kotlin
android {
    ndkVersion = providers.gradleProperty("projectLumenNdkVersion").get()

    externalNativeBuild {
        cmake {
            version = providers.gradleProperty("projectLumenCmakeVersion").get()
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}
```

```cmake
target_link_options(lumen_security PRIVATE "-Wl,-z,max-page-size=16384")
```

```bash
python3 scripts/verify_android_16kb_alignment.py app/build/outputs/apk/release/*.apk
```

### 3. Contracts

- `gradle.properties` owns `projectLumenNdkVersion` and `projectLumenCmakeVersion`; do not hard-code those versions separately in workflows.
- GitHub Actions must install the NDK/CMake versions from `gradle.properties` before any Android Gradle build that compiles native code.
- Native libraries must be packaged uncompressed with 16 KB ZIP data offsets.
- Every `PT_LOAD` segment in every APK `.so` must have an alignment that is at least 16 KB and divisible by 16 KB.
- Third-party AARs with native libraries are covered by the APK-level verification script because they are not relinked by the app's CMake build.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| APK contains compressed `lib/**/*.so` | 16 KB alignment verification fails. |
| APK native library ZIP data offset is not 16 KB aligned | 16 KB alignment verification fails. |
| Any ELF `PT_LOAD` alignment is below 16 KB or not divisible by 16 KB | 16 KB alignment verification fails. |
| New native dependency is not 16 KB ready | Release workflow fails before publishing assets. |
| Workflow installs a different NDK/CMake than Gradle requests | Fix workflow to read `gradle.properties`; do not add a second version constant. |

### 5. Good/Base/Bad Cases

- Good: CMake-built libraries use the project NDK and explicit 16 KB linker alignment, APK release workflows run the verification script before publishing.
- Base: pure Kotlin/Java changes do not touch native packaging and require no native alignment updates.
- Bad: adding a prebuilt `.so` or native AAR without the APK-level alignment check, or re-enabling `useLegacyPackaging`.

### 6. Tests Required

- GitHub workflow: after `gradle assembleRelease`, run `scripts/verify_android_16kb_alignment.py` against all release APK outputs.
- Manual review: when adding/updating native AAR dependencies, confirm the workflow passes the APK-level check rather than assuming Gradle/NDK relinks the dependency.

### 7. Wrong vs Correct

#### Wrong

```yaml
- name: Install Android native toolchain
  run: sdkmanager "ndk;28.2.13676358" "cmake;3.22.1"
```

#### Correct

```yaml
- name: Install Android native toolchain
  run: |
    NDK_VERSION="$(sed -n 's/^projectLumenNdkVersion=//p' gradle.properties | tail -n 1)"
    CMAKE_VERSION="$(sed -n 's/^projectLumenCmakeVersion=//p' gradle.properties | tail -n 1)"
    yes | sdkmanager "ndk;${NDK_VERSION}" "cmake;${CMAKE_VERSION}"
```
