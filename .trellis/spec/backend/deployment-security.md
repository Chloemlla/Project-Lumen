# Backend Deployment Security

> Concrete deployment contract for backend request signing and client security env wiring.

## Scenario: Request Signing Secret Parity

### 1. Scope / Trigger

- Trigger: backend deployment and Android release builds both participate in the same HMAC request-signing contract.
- Applies when changing backend deployment workflows, Android release build envs, request-signing middleware, or production API secrets.

### 2. Signatures

- Backend env: `LUMEN_REQUEST_SIGNING_SECRET`.
- Android release build env: `PROJECT_LUMEN_REQUEST_SIGNING_SECRET`.
- Backend middleware reads `X-Lumen-Timestamp`, `X-Lumen-Nonce`, and `X-Lumen-Signature`.
- Android client signs the canonical request payload and sends the same three headers on API requests.
- Android native signing bridge is built by CMake with `ANDROID_STL=c++_shared` and requires `libc++_shared.so` in the APK.

### 3. Contracts

- `LUMEN_REQUEST_SIGNING_SECRET` and `PROJECT_LUMEN_REQUEST_SIGNING_SECRET` must carry the same secret value for the same production API environment.
- The local default `project-lumen-local-request-signing-key` is allowed only for local development or deliberately matched non-production testing.
- Backend deployment workflows must pass the GitHub Actions secret `PROJECT_LUMEN_REQUEST_SIGNING_SECRET` into the running container as `LUMEN_REQUEST_SIGNING_SECRET`.
- Android release builds that use the native signing bridge must pass `-DANDROID_STL=c++_shared` and package/pick `**/libc++_shared.so` so `System.loadLibrary("lumen_security")` can resolve the C++ runtime.
- If `LUMEN_REQUIRE_REQUEST_SIGNING=true`, unsigned requests or signatures generated with a different secret must fail closed.
- `LUMEN_REQUIRE_PLAY_INTEGRITY` is independent from request signing; enabling it requires clients to be able to attach `X-Lumen-Integrity`.

### 4. Validation & Error Matrix

- Missing `X-Lumen-Timestamp`, `X-Lumen-Nonce`, or `X-Lumen-Signature` while signing is required -> HTTP 403.
- Timestamp outside `LUMEN_REQUEST_TIMESTAMP_SKEW_SECONDS` -> HTTP 403.
- Empty backend signing secret while signing is required -> HTTP 403.
- Android/backend signing secret mismatch -> HTTP 403 on otherwise valid signed requests.
- Reused nonce within the accepted timestamp window -> HTTP 403.
- Missing or too-short `X-Lumen-Integrity` while Play Integrity is required for that path -> HTTP 403.
- Missing `libc++_shared.so` in an APK that needs the shared STL -> native signing bridge fails to load; request signing may fall back to the wrong key source and production API requests return HTTP 403.

### 5. Good/Base/Bad Cases

- Good: Android release and backend deployment both receive the same production request-signing secret from GitHub Actions secrets.
- Good: Android APKs that include `lumen_security` also include `libc++_shared.so` for every packaged ABI when the native library is built with the shared STL.
- Base: local backend and local/debug client both use the documented development fallback secret.
- Bad: Android release is built with `PROJECT_LUMEN_REQUEST_SIGNING_SECRET`, but backend deployment omits `LUMEN_REQUEST_SIGNING_SECRET`; login starts fail with HTTP 403 even when diagnostics report `signed=true`.
- Bad: `lumen_security` uses C++ standard library code, but the APK omits `libc++_shared.so`; the client can report `signed=true` while signing with the fallback key after the native bridge fails.

### 6. Tests Required

- GitHub workflow review: backend deployment env includes `LUMEN_REQUEST_SIGNING_SECRET: ${{ secrets.PROJECT_LUMEN_REQUEST_SIGNING_SECRET }}`.
- Android packaging review: `app/build.gradle.kts` includes `-DANDROID_STL=c++_shared` and a `jniLibs.pickFirsts` entry for `**/libc++_shared.so`.
- Backend route/security tests: a request signed with the configured secret succeeds, and the same request signed with a different secret returns HTTP 403.
- Android signing tests: canonical payload construction remains aligned with backend canonicalization for method, path, query, body hash, timestamp, and nonce.

### 7. Wrong vs Correct

#### Wrong

```yaml
env:
  IMAGE_URL: ${{ needs.build-image.outputs.deploy_image }}
```

```kotlin
externalNativeBuild {
    cmake {
        arguments += listOf("-DLUMEN_REQUEST_SIGNING_SECRET=...")
    }
}
```

#### Correct

```yaml
env:
  IMAGE_URL: ${{ needs.build-image.outputs.deploy_image }}
  LUMEN_REQUEST_SIGNING_SECRET: ${{ secrets.PROJECT_LUMEN_REQUEST_SIGNING_SECRET }}
```

```kotlin
externalNativeBuild {
    cmake {
        arguments += listOf(
            "-DANDROID_STL=c++_shared",
            "-DLUMEN_REQUEST_SIGNING_SECRET=...",
        )
    }
}

packaging {
    jniLibs {
        pickFirsts += "**/libc++_shared.so"
    }
}
```

The correct path keeps production request signatures verifiable by the backend without weakening the security middleware.
