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

---

## Scenario: Native Integrity Bridge

### 1. Scope / Trigger

- Trigger: any change to `lumen_security.cpp`, `NativeSecurityBridge.kt`, or `AppIntegrityGuard.kt`.
- Scope: release builds with `APP_INTEGRITY_ENFORCEMENT_ENABLED=true` must reject obvious debugger, injection, and runtime hooking environments before protected API signing behavior is trusted.

### 2. Signatures

```kotlin
NativeSecurityBridge.isNativeEnvironmentAllowedOrNull(
    packageName = appContext.packageName,
    signingCertSha256 = signingCertificateSha256(appContext),
    debugAllowed = false,
)
```

```cpp
Java_com_projectlumen_app_core_security_NativeSecurityBridge_isNativeEnvironmentAllowed(
    JNIEnv *env,
    jobject,
    jstring package_name,
    jstring signing_cert_sha256,
    jboolean debug_allowed
)
```

### 3. Contracts

- Keep the JNI method signature stable unless Kotlin call sites are updated in the same change.
- Native checks must verify expected package and release certificate before trusting the environment.
- When `debug_allowed == JNI_FALSE`, native checks must reject a non-zero `TracerPid`, suspicious debug/injection environment variables, known hook library mappings, suspicious task names, suspicious file-descriptor targets, and known Frida/Xposed/Substrate socket artifacts.
- Debuggable local builds are bypassed by `AppIntegrityGuard`; do not make native checks block ordinary debug development paths unless the caller explicitly opts in.
- Release request signing must fail closed when `NativeSecurityBridge.requestSigningSecretOrNull()` is unavailable; only debug builds may use the documented local fallback signing secret.
- TODO1 remote device registration must include native bridge availability, native environment verdict, request-signing policy, and App Integrity state in `localSecurityConfig`.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Package name does not match `LUMEN_EXPECTED_PACKAGE` | Native bridge returns `JNI_FALSE`. |
| Release certificate is configured and does not match | Native bridge returns `JNI_FALSE`. |
| `TracerPid` is non-zero with debug disallowed | Native bridge returns `JNI_FALSE`. |
| Frida/Xposed/Substrate/Riru/Zygisk artifacts appear in maps, cmdline, task comm, fd symlinks, or Unix socket metadata | Native bridge returns `JNI_FALSE`. |
| Debug build calls `AppIntegrityGuard.enforce` | Guard returns before native enforcement. |
| Release API request cannot load `lumen_security` | Request signing throws before sending a fallback-signed request. |
| Remote device registration succeeds | `localSecurityConfig` reports native bridge/protection state for backend/admin inspection. |

### 5. Good/Base/Bad Cases

- Good: add detection paths as small native helpers and keep all checks side-effect free.
- Good: keep TODO1 remote signing and device registration tied to native bridge status.
- Base: Java-side `Debug.isDebuggerConnected()` remains a complementary signal, not a replacement for native checks.
- Bad: release builds silently falling back to `project-lumen-local-request-signing-key` when native loading fails.
- Bad: calling `ptrace` or killing the process from the native bridge; return a verdict and let `AppIntegrityGuard` own enforcement.

### 6. Tests Required

- GitHub workflow: Android build must still compile `lumen_security`.
- Manual review: check that new needles are lower-case and searched through the case-normalized helper.
- Manual review: check that release-only enforcement remains gated by `BuildConfig.DEBUG` and `APP_INTEGRITY_ENFORCEMENT_ENABLED`.
- Manual review: check release request signing has no non-native fallback path.

### 7. Wrong vs Correct

#### Wrong

```cpp
if (detected) abort();
```

#### Correct

```cpp
if (debug_allowed == JNI_FALSE && has_hooking_artifacts()) return JNI_FALSE;
```
