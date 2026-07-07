# Backend Deployment Security

> Concrete deployment contract for backend request signing and client security env wiring.

## Scenario: Request Signing Secret Parity

### 1. Scope / Trigger

- Trigger: backend deployment and Android release builds both participate in the same HMAC request-signing contract.
- Applies when changing backend deployment workflows, Android release build envs, request-signing middleware, or production API secrets.

### 2. Signatures

- Backend env: `LUMEN_REQUEST_SIGNING_SECRET`.
- Backend env: `LUMEN_ALLOW_PUBLIC_RELEASE_CHECK`.
- Android release build env: `PROJECT_LUMEN_REQUEST_SIGNING_SECRET`.
- Backend middleware reads `X-Lumen-Timestamp`, `X-Lumen-Nonce`, and `X-Lumen-Signature`.
- Android client signs the canonical request payload and sends the same three headers on API requests.
- Public release check API: `GET /api/v1/releases/check` and legacy `GET /v1/releases/check`.
- Android native signing bridge is built by CMake with `ANDROID_STL=c++_shared` and requires `libc++_shared.so` in the APK.

### 3. Contracts

- `LUMEN_REQUEST_SIGNING_SECRET` and `PROJECT_LUMEN_REQUEST_SIGNING_SECRET` must carry the same secret value for the same production API environment.
- The local default `project-lumen-local-request-signing-key` is allowed only for local development or deliberately matched non-production testing.
- Backend deployment workflows must pass the GitHub Actions secret `PROJECT_LUMEN_REQUEST_SIGNING_SECRET` into the running container as `LUMEN_REQUEST_SIGNING_SECRET`.
- `LUMEN_ALLOW_PUBLIC_RELEASE_CHECK=true` makes `GET /api/v1/releases/check` and legacy `GET /v1/releases/check` public update-discovery endpoints so older or mismatched APKs can still discover a corrective release.
- `LUMEN_ALLOW_PUBLIC_RELEASE_CHECK=false` puts release check behind the same HMAC requirement as other protected `/v1` routes.
- All other protected `/v1` routes must continue to fail closed when `LUMEN_REQUIRE_REQUEST_SIGNING=true`.
- Android release builds that use the native signing bridge must pass `-DANDROID_STL=c++_shared` and package/pick `**/libc++_shared.so` so `System.loadLibrary("lumen_security")` can resolve the C++ runtime.
- If `LUMEN_REQUIRE_REQUEST_SIGNING=true`, unsigned requests or signatures generated with a different secret must fail closed.
- `LUMEN_REQUIRE_PLAY_INTEGRITY` is independent from request signing; enabling it requires clients to be able to attach `X-Lumen-Integrity`.

### 4. Validation & Error Matrix

- Missing `X-Lumen-Timestamp`, `X-Lumen-Nonce`, or `X-Lumen-Signature` while signing is required -> HTTP 403.
- `LUMEN_ALLOW_PUBLIC_RELEASE_CHECK=true` and `GET /api/v1/releases/check` or `GET /v1/releases/check` without signing headers -> normal release response, not HTTP 403.
- `LUMEN_ALLOW_PUBLIC_RELEASE_CHECK=true` and `GET /api/v1/releases/check` or `GET /v1/releases/check` with stale/invalid signing headers -> normal release response, not `REQUEST_SIGNATURE_INVALID`.
- `LUMEN_ALLOW_PUBLIC_RELEASE_CHECK=false` and unsigned release check while signing is required -> HTTP 403.
- Timestamp outside `LUMEN_REQUEST_TIMESTAMP_SKEW_SECONDS` -> HTTP 403.
- Empty backend signing secret while signing is required -> HTTP 403.
- Android/backend signing secret mismatch -> HTTP 403 on otherwise valid signed requests.
- Reused nonce within the accepted timestamp window -> HTTP 403.
- Missing or too-short `X-Lumen-Integrity` while Play Integrity is required for that path -> HTTP 403.
- Missing `libc++_shared.so` in an APK that needs the shared STL -> native signing bridge fails to load; request signing may fall back to the wrong key source and production API requests return HTTP 403.

### 5. Good/Base/Bad Cases

- Good: Android release and backend deployment both receive the same production request-signing secret from GitHub Actions secrets.
- Good: `LUMEN_ALLOW_PUBLIC_RELEASE_CHECK=true` makes release-check requests public while account, sync, telemetry, purchase, and backup routes still require valid request signatures.
- Good: `LUMEN_ALLOW_PUBLIC_RELEASE_CHECK=false` restores strict HMAC protection for release check without disabling HMAC on the rest of `/v1`.
- Good: Android APKs that include `lumen_security` also include `libc++_shared.so` for every packaged ABI when the native library is built with the shared STL.
- Base: local backend and local/debug client both use the documented development fallback secret.
- Bad: The update-discovery endpoint sits only behind request signing, so a released APK with a bad compiled secret cannot learn about the fixed release.
- Bad: Android release is built with `PROJECT_LUMEN_REQUEST_SIGNING_SECRET`, but backend deployment omits `LUMEN_REQUEST_SIGNING_SECRET`; login starts fail with HTTP 403 even when diagnostics report `signed=true`.
- Bad: `lumen_security` uses C++ standard library code, but the APK omits `libc++_shared.so`; the client can report `signed=true` while signing with the fallback key after the native bridge fails.

### 6. Tests Required

- GitHub workflow review: backend deployment env includes `LUMEN_REQUEST_SIGNING_SECRET: ${{ secrets.PROJECT_LUMEN_REQUEST_SIGNING_SECRET }}`.
- GitHub workflow review: backend deployment env passes `LUMEN_ALLOW_PUBLIC_RELEASE_CHECK: ${{ vars.LUMEN_ALLOW_PUBLIC_RELEASE_CHECK }}` so production can explicitly close the public release-check bypass.
- Android packaging review: `app/build.gradle.kts` includes `-DANDROID_STL=c++_shared` and a `jniLibs.pickFirsts` entry for `**/libc++_shared.so`.
- Backend route/security tests: with `LUMEN_ALLOW_PUBLIC_RELEASE_CHECK=true`, release check succeeds without request-signing headers for both configured API prefix and legacy `/v1` path.
- Backend route/security tests: with `LUMEN_ALLOW_PUBLIC_RELEASE_CHECK=false`, unsigned release check returns HTTP 403 when request signing is required.
- Backend route/security tests: a request signed with the configured secret succeeds, and the same request signed with a different secret returns HTTP 403.
- Android signing tests: canonical payload construction remains aligned with backend canonicalization for method, path, query, body hash, timestamp, and nonce.

### 7. Wrong vs Correct

#### Wrong

```yaml
env:
  IMAGE_URL: ${{ needs.build-image.outputs.deploy_image }}
```

```rust
let v1 = Router::new()
    .merge(platform::router())
    .layer(middleware::from_fn_with_state(state.clone(), security::enforce_api_security));
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

```rust
if state.config.allow_public_release_check && is_release_check_path(api_prefix, method, path) {
    return Ok(next.run(request).await);
}
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

## Scenario: Android Certificate Pinning Activation

### 1. Scope / Trigger

- Trigger: Android release builds optionally harden HTTPS with OkHttp certificate pinning.
- Applies when changing Android build envs, GitHub release workflows, API base URLs, `SecureOkHttpFactory`, or certificate pin secrets.

### 2. Signatures

- Backend API base env: `PROJECT_LUMEN_API_BASE_URL`.
- Translation API base env: `PROJECT_LUMEN_TRANSLATION_API_BASE_URL`.
- Backend pinning enable env: `PROJECT_LUMEN_API_CERTIFICATE_PINNING_ENABLED`.
- Backend pins env: `PROJECT_LUMEN_API_CERTIFICATE_PINS`.
- Translation pinning enable env: `PROJECT_LUMEN_TRANSLATION_CERTIFICATE_PINNING_ENABLED`.
- Translation pins env: `PROJECT_LUMEN_TRANSLATION_CERTIFICATE_PINS`.
- Android `BuildConfig.API_CERTIFICATE_PINS` and `BuildConfig.TRANSLATION_CERTIFICATE_PINS` are populated only when the matching `*_CERTIFICATE_PINNING_ENABLED` flag is truthy (`1`, `true`, `yes`, or `on`).

### 3. Contracts

- Android clients must reject non-HTTPS API and translation base URLs before building the OkHttp client.
- Android clients must use Android/OkHttp system trust for HTTPS even when no pins are configured.
- Certificate pinning is an optional hardening layer. A non-empty pins secret must not automatically enable pinning.
- If pinning is deliberately enabled, the pins list must include at least the current public key pin and one backup `sha256/` pin for the active host.
- Pins may be separated by comma, semicolon, or newline; missing `sha256/` prefixes are normalized before registration with OkHttp.
- `SecureOkHttpFactory.requireCertificatePins=true` is reserved for future builds that deliberately fail startup when the effective pins list is empty.

### 4. Validation & Error Matrix

- Non-HTTPS base URL -> client throws before sending a request.
- `*_CERTIFICATE_PINNING_ENABLED` unset or false -> effective pins are empty; OkHttp uses normal HTTPS trust.
- `*_CERTIFICATE_PINNING_ENABLED=true` with empty `*_CERTIFICATE_PINS` -> Android Gradle configuration fails before publishing an APK.
- `*_CERTIFICATE_PINNING_ENABLED=true` with valid current/backup pins -> OkHttp enforces pin matching.
- `*_CERTIFICATE_PINNING_ENABLED=true` with stale or wrong pins -> TLS fails before HTTP; backend never sees the request.
- `requireCertificatePins=true` with empty effective pins -> client throws before sending a request.
- A retained stale `*_CERTIFICATE_PINS` secret without the enable flag -> no pinning; communication stays on HTTPS system trust.

### 5. Good/Base/Bad Cases

- Good: CDN-managed certificate rotation leaves pinning disabled; HTTPS trust, request signing, and optional Play Integrity continue to protect requests without blocking all clients.
- Good: A pinned release sets `PROJECT_LUMEN_API_CERTIFICATE_PINNING_ENABLED=true` and provides current plus backup pins in `PROJECT_LUMEN_API_CERTIFICATE_PINS`.
- Base: Debug/local builds leave pinning disabled and still require HTTPS.
- Bad: A workflow compiles stale `PROJECT_LUMEN_API_CERTIFICATE_PINS` solely because the secret exists; all Android clients fail TLS before reaching the backend.
- Bad: A release enables pinning with only one current pin and no backup, making the next certificate key rotation a client outage.

### 6. Tests Required

- Android unit tests: `CertificatePinPolicy.parse` normalizes prefixes, accepts comma/semicolon/newline separators, drops blanks, and deduplicates pins.
- Android client tests: `SecureOkHttpFactory` rejects HTTP URLs and applies no `CertificatePinner` when effective pins are empty.
- Android Gradle checks: enabling API or translation pinning without matching pins fails configuration.
- GitHub workflow review: build and release workflows pass both `*_CERTIFICATE_PINNING_ENABLED` and `*_CERTIFICATE_PINS` secrets, with pinning disabled unless the enable secret is truthy.
- Release checklist: if pinning is enabled, validate current and backup pins against the production host before publishing.

### 7. Wrong vs Correct

#### Wrong

```kotlin
buildConfigField("String", "API_CERTIFICATE_PINS", "\"$projectLumenApiCertificatePins\"")
```

```yaml
PROJECT_LUMEN_API_CERTIFICATE_PINS: ${{ secrets.PROJECT_LUMEN_API_CERTIFICATE_PINS }}
```

#### Correct

```kotlin
val projectLumenEffectiveApiCertificatePins =
    if (projectLumenApiCertificatePinningEnabled) projectLumenApiCertificatePins else ""

buildConfigField("String", "API_CERTIFICATE_PINS", "\"${projectLumenBuildConfigString(projectLumenEffectiveApiCertificatePins)}\"")
```

```yaml
PROJECT_LUMEN_API_CERTIFICATE_PINNING_ENABLED: ${{ secrets.PROJECT_LUMEN_API_CERTIFICATE_PINNING_ENABLED }}
PROJECT_LUMEN_API_CERTIFICATE_PINS: ${{ secrets.PROJECT_LUMEN_API_CERTIFICATE_PINS }}
```

The correct path keeps certificate pinning available for deliberate hardened releases without letting stale pins break every client/backend request.
