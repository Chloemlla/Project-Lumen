# Fix backend request signing 403s

## Goal

Fix production Android API calls returning HTTP 403 despite `signed=true` diagnostics by making the client request-signing secret source stable and aligned with the backend deployment secret.

## What I Already Know

* User reports `GET /api/v1/releases/check?...` returns HTTP 403 with `signed=true`, `integrity=false`, and `auth=false`.
* `/v1/releases/check` does not require bearer auth or Play Integrity, so this failure is in request signing, not auth or integrity.
* The backend rejects signed requests when the HMAC secret, canonical payload, timestamp, or nonce is invalid.
* A manually signed live request with the public local fallback secret still returns HTTP 403, so production backend is not using the fallback secret.
* Android currently reads the signing secret from `NativeSecurityBridge` and silently falls back to the public local default if the native bridge is unavailable.
* Android release builds already receive `PROJECT_LUMEN_REQUEST_SIGNING_SECRET`; backend deployment receives the matching secret as `LUMEN_REQUEST_SIGNING_SECRET`.
* The native `lumen_security` library uses C++ standard library types, so release APKs must package the `c++_shared` runtime when CMake builds that library with the shared STL.

## Requirements

* Android request signing must use the release build's `PROJECT_LUMEN_REQUEST_SIGNING_SECRET` consistently for all backend API requests.
* Release clients must not silently sign production API requests with the public local fallback secret when a configured signing secret is available through build config.
* Keep the existing HMAC canonical payload format compatible with the Rust backend.
* Add secret-free diagnostics that identify the signing key source/status without exposing the secret value.
* Preserve local development behavior: when no signing secret is configured, local/debug builds may use the documented fallback key.
* Package the Android C++ shared runtime required by `lumen_security`.
* Do not disable backend request signing or loosen server verification as the primary fix.

## Acceptance Criteria

* [ ] All `ProjectLumenApiClient` requests continue to attach `X-Lumen-Timestamp`, `X-Lumen-Nonce`, and `X-Lumen-Signature`.
* [ ] Request signing chooses the same configured build secret regardless of native bridge availability.
* [ ] If a release build cannot access any configured signing secret, diagnostics surface that state instead of silently looking healthy.
* [ ] Developer/debug API diagnostics show signing source/status without revealing raw secret material.
* [ ] Release APK packaging includes the native C++ shared runtime needed by `lumen_security`.
* [ ] GitHub Actions build/test/lint workflows pass after the change.

## Definition of Done

* Code changed and committed.
* Verification runs only in GitHub Actions, not locally.
* Any learned signing/deployment contract is reflected in `.trellis/spec/` if needed.
* Commit is pushed to `main`.

## Technical Approach

Use the existing build secret as the stable client-side source of truth. Add a `BuildConfig` field that carries the same request-signing secret already passed to native CMake. Update `ProjectLumenRequestSigner` to prefer the configured build secret, fall back to native, then use the local default only when no configured secret exists. Add a secret-free `keySource`/status for diagnostics.

## Decision (ADR-lite)

**Context**: The previous backend deployment fix made production use a non-default request-signing secret. Android can still silently sign with the default key if the native bridge is missing or returns an unusable value, producing `signed=true` diagnostics while every request is forbidden.

**Decision**: Make configured Android build secret the primary signing source and keep native as a secondary source. This favors correctness and diagnostics over relying on native obfuscation alone.

**Consequences**: The secret remains extractable from the client, as all client-embedded secrets are. The practical security boundary remains HMAC replay protection plus HTTPS/pinning/integrity, not secrecy of an APK-embedded value.

## Out of Scope

* Replacing request signing with a full server-issued device credential protocol.
* Disabling request signing in production.
* Making Play Integrity required for `/releases/check`.
* Changing backend API response shapes.

## Technical Notes

* Relevant files:
  * `app/build.gradle.kts`
  * `app/src/main/java/com/projectlumen/app/core/security/ProjectLumenRequestSigner.kt`
  * `app/src/main/java/com/projectlumen/app/core/security/NativeSecurityBridge.kt`
  * `app/src/main/java/com/projectlumen/app/core/api/ProjectLumenApiClient.kt`
  * `app/src/main/java/com/projectlumen/app/core/api/ProjectLumenApiDiagnostics.kt`
  * `backend/src/routes/security.rs`
  * `.github/workflows/build-artifacts.yml`
* Backend signing contract is documented in `.trellis/spec/backend/deployment-security.md`.
