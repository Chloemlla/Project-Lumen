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

```yaml
- name: Set up Android native toolchain
  uses: ./.github/actions/setup-android-native-toolchain
```

```bash
python3 scripts/verify_android_16kb_alignment.py app/build/outputs/apk/release/*.apk
```

### 3. Contracts

- `gradle.properties` owns `projectLumenNdkVersion` and `projectLumenCmakeVersion`; do not hard-code those versions separately in workflows.
- GitHub Actions must install the NDK/CMake versions from `gradle.properties` before any Android Gradle build that compiles native code.
- GitHub Actions must use `.github/actions/setup-android-native-toolchain` for Android native toolchain setup so `sdkmanager` is available and all workflows share one install path.
- The shared setup action must judge installation by `sdkmanager`'s own exit status. The expected `yes` input helper may receive `SIGPIPE` after `sdkmanager` closes stdin successfully and must not turn a successful install into a workflow failure under the composite shell's `pipefail` mode.
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
| `sdkmanager` is not available after Android SDK setup | Native toolchain setup fails before any Gradle build with a clear `sdkmanager was not found` message. |
| `sdkmanager` succeeds and closes the `yes` input pipe | Native toolchain setup succeeds even if `yes` exits with `SIGPIPE`. |
| `sdkmanager` returns a non-zero status | Native toolchain setup reports and returns that exact status. |

### 5. Good/Base/Bad Cases

- Good: CMake-built libraries use the project NDK and explicit 16 KB linker alignment, APK release workflows run the verification script before publishing.
- Base: pure Kotlin/Java changes do not touch native packaging and require no native alignment updates.
- Bad: adding a prebuilt `.so` or native AAR without the APK-level alignment check, or re-enabling `useLegacyPackaging`.

### 6. Tests Required

- GitHub workflow: after `gradle assembleRelease`, run `scripts/verify_android_16kb_alignment.py` against all release APK outputs.
- GitHub workflow: before any Android Gradle build that compiles native code, use `.github/actions/setup-android-native-toolchain` so SDK setup and NDK/CMake installation stay shared.
- Manual review: the shared setup action captures `PIPESTATUS[1]` immediately after the `yes | sdkmanager` pipeline and fails only when that `sdkmanager` status is non-zero.
- Manual review: when adding/updating native AAR dependencies, confirm the workflow passes the APK-level check rather than assuming Gradle/NDK relinks the dependency.

### 7. Wrong vs Correct

#### Wrong

```yaml
- name: Install Android native toolchain
  run: sdkmanager "ndk;28.2.13676358" "cmake;3.22.1"
```

#### Correct

```yaml
- name: Set up Android native toolchain
  uses: ./.github/actions/setup-android-native-toolchain
```

Inside the shared action, do not let the input helper's expected pipe closure mask the installer result:

```bash
# Wrong: composite actions run with pipefail, so a successful sdkmanager can
# still fail the step when yes receives SIGPIPE.
yes | "$SDKMANAGER" "ndk;${NDK_VERSION}" "cmake;${CMAKE_VERSION}"
```

```bash
# Correct: temporarily suspend errexit, then propagate sdkmanager itself.
set +e
yes | "$SDKMANAGER" "ndk;${NDK_VERSION}" "cmake;${CMAKE_VERSION}"
SDKMANAGER_STATUS="${PIPESTATUS[1]}"
set -e
test "$SDKMANAGER_STATUS" -eq 0 || exit "$SDKMANAGER_STATUS"
```

---

## Scenario: Native Integrity Bridge

### 1. Scope / Trigger

- Trigger: any change to `lumen_security.cpp`, `NativeSecurityBridge.kt`, or `AppIntegrityGuard.kt`.
- Scope: release builds with `APP_INTEGRITY_ENFORCEMENT_ENABLED=true` must reject obvious debugger, injection, and runtime hooking environments before protected API signing behavior is trusted.

### 2. Signatures

```kotlin
NativeSecurityBridge.evaluateEnvironmentOrNull(
    packageName = appContext.packageName,
    signingCertSha256 = signingCertificateSha256(appContext),
    debugAllowed = false,
)

NativeSecurityBridge.signCanonicalPayloadOrNull(
    canonicalPayloadUtf8 = canonicalPayload.toByteArray(Charsets.UTF_8),
    debugAllowed = BuildConfig.DEBUG,
)
```

```cpp
Java_com_projectlumen_app_core_security_NativeSecurityBridge_evaluateEnvironment(
    JNIEnv *env,
    jobject,
    jstring package_name,
    jstring signing_cert_sha256,
    jboolean debug_allowed
)

Java_com_projectlumen_app_core_security_NativeSecurityBridge_signCanonicalPayload(
    JNIEnv *env,
    jobject,
    jbyteArray canonical_payload_utf8,
    jboolean debug_allowed,
    jintArray reason_mask_out
)
```

### 3. Contracts

- Keep the JNI method signature stable unless Kotlin call sites are updated in the same change.
- Native checks must verify expected package, `/proc/self/cmdline` process name, and normalized release certificate before trusting the environment.
- Stable native reason bits cover package mismatch, process mismatch, certificate missing/mismatch, tracer, suspicious environment, hook artifacts, unverified release identity, invalid signing secret, and internal failure. Kotlin must preserve unknown bits in diagnostics.
- When `debug_allowed == JNI_FALSE`, native checks must reject a non-zero `TracerPid`, suspicious debug/injection environment variables, known hook library mappings, suspicious task names, suspicious file-descriptor targets, and known Frida/Xposed/Substrate socket artifacts.
- `/proc`, task, fd, environment, and symlink inspection must use explicit byte/entry caps. Read or parse failures become the internal-failure reason in release checks rather than silently allowing signing.
- Debuggable local builds are bypassed by `AppIntegrityGuard`; do not make native checks block ordinary debug development paths unless the caller explicitly opts in.
- The request-signing secret and HMAC-SHA256 operation stay native. JNI returns only a lowercase signature and structured reason mask; it must never return the raw secret.
- A clean release evaluation establishes process-local verified identity. Every release signature requires that identity and repeats volatile debugger/environment/hook checks immediately before signing.
- Release request signing must fail closed when the bridge is unavailable, the reason mask is non-zero, or the signature is malformed. Only debug builds may use the documented local fallback signing secret.
- Native detection remains side-effect free: no `ptrace`, abort, process kill, or destructive anti-analysis behavior.
- TODO1 remote device registration must include native bridge availability, native environment verdict, request-signing policy, and App Integrity state in `localSecurityConfig`.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Package or process name does not match `LUMEN_EXPECTED_PACKAGE` | Verdict contains the stable package/process mismatch bit and release identity is not established. |
| Release certificate is missing, malformed, or mismatched | Verdict contains the certificate missing/mismatch bit and release identity is not established. |
| `TracerPid` is non-zero with debug disallowed | Verdict contains `tracer_detected`; release signing returns no signature. |
| Frida/Xposed/Substrate/Riru/Zygisk artifacts appear in maps, cmdline, task comm, fd symlinks, or Unix socket metadata | Verdict contains `hook_artifact_detected`; release signing returns no signature. |
| A bounded native scan cannot complete safely | Verdict contains `internal_failure`; release signing returns no signature. |
| Release signing is requested before a clean native evaluation | Signing returns no signature with `release_identity_not_verified`. |
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
- GitHub workflow: pure host C++ tests must cover RFC 4231 HMAC and the shared Project Lumen canonical request vector.
- Android unit tests: reason-bit mapping, unknown-bit diagnostics, canonical payload construction, and removal of the raw-secret JNI path.
- Manual review: check that new needles are lower-case and searched through the case-normalized helper.
- Manual review: check debug builds bypass enforcement, release builds always evaluate native identity for signing, and `APP_INTEGRITY_ENFORCEMENT_ENABLED` still owns startup exception/report policy.
- Manual review: check release request signing has no non-native fallback path.

### 7. Wrong vs Correct

#### Wrong

```cpp
if (detected) abort();
```

#### Correct

```cpp
if (debug_allowed == JNI_FALSE) reasons |= collect_volatile_reasons();
return reasons;
```
