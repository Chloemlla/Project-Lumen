# Android Backend Connectivity Gating

## Scenario: Main-backend connectivity capability gate

### 1. Scope / Trigger

Apply this contract whenever Android code adds or changes communication with the Project Lumen main backend, exposes a backend-dependent Compose control, or schedules backend work in an application/service coordinator.

The gate covers account sessions, device registration, entitlements/purchases, remote configuration, cloud sync, cloud backup, telemetry, face analysis, privileged device control, and main-backend release discovery. It does not cover the translation client or GitHub Releases fallback because those are independent recovery paths.

### 2. Signatures

The shared production boundary is:

```kotlin
fun interface BackendCapabilityGate {
    fun decision(capability: BackendCapability): BackendCapabilityDecision
    fun requireExecutable(capability: BackendCapability)
}

class BackendConnectivityController internal constructor(
    scope: CoroutineScope,
    persistence: BackendConnectivityPersistence,
    healthProbe: suspend () -> ApiHealth,
    nowMillis: () -> Long = System::currentTimeMillis,
    delayMillis: suspend (Long) -> Unit = { delay(it) },
    probeTimeoutMillis: Long = 7_000L,
) : BackendCapabilityGate

class ProjectLumenApiClient(
    baseUrl: String = ProjectLumenApiConfig.baseUrl,
    httpClient: OkHttpClient = SecureOkHttpFactory.create(...),
    backendGate: BackendCapabilityGate = AllowAllBackendCapabilityGate,
)
```

Production must construct exactly one `ProjectLumenApiClient` in `ProjectLumenApplication` and inject the same `BackendConnectivityController` into the API client, telemetry reporter, update checker, lifecycle work, services, and UI state.

### 3. Contracts

#### State machine

`BackendHealthStatus` is `UNKNOWN`, `CHECKING`, `REACHABLE`, or `UNREACHABLE`.

- `HEALTH_PROBE` is always visible and executable.
- `REACHABLE` allows every main-backend capability.
- `UNKNOWN` and `UNREACHABLE` hide and block every non-health capability.
- `CHECKING` remains allowed only when the last confirmed result was reachable within `RECENT_REACHABLE_TTL_MILLIS` (5 minutes).
- `developerForceEnabled=true` allows and reveals non-health capabilities but must not rewrite the measured health status.

#### Probe and retry

- A health refresh is single-flight.
- Each refresh performs at most 2 attempts, each bounded by 7 seconds, with 500 ms between attempts.
- Only `status == "ok"` is a successful health response.
- Foreground recovery retries use 5 s, 30 s, 2 min, then 5 min capped backoff.
- Background transition cancels scheduled recovery retries; the next foreground transition refreshes again.

#### Persistence

Use the dedicated MMKV ID `backend_connectivity`. Persist only stable health metadata, timestamps, failure count, stable error code, and the developer override. Never clear credentials, sync/config cursors, cached remote data, diagnostic preferences, or local backups when connectivity fails.

#### Final request boundary

Every main-backend API method passes an explicit `BackendCapability` to `ProjectLumenApiClient.request`. `backendGate.requireExecutable(capability)` must run before URL/body construction, request signing, diagnostics payload creation, and `OkHttpClient.newCall`.

Request signing remains independent: connectivity gating must not change `ProjectLumenRequestSigner`, the native secret source, certificate validation, or signature headers.

#### UI and background behavior

- Ordinary Settings hides the remote account/cloud card, cloud growth capability and action, diagnostics quick tile/row, and Shizuku diagnostic upload group when effective access is blocked.
- The growth denominator is 4 while cloud is hidden and 5 while cloud is visible.
- Local backup/import, local reports, eye-care features, local Shizuku controls, translation, About, developer entry, and update UI remain available.
- The update checker skips only main-backend release discovery and still tries GitHub Releases.
- Developer options always show measured status, effective status, timestamps, error code, refresh, and the persisted force-enable switch.
- Telemetry, crash upload, face capture/upload, device registration, session refresh, and privileged device-control loops must preflight before expensive collection or capture.

### 4. Validation & Error Matrix

| Condition | Decision / error |
|---|---|
| Health capability in any state | executable; reason `health_probe` |
| Non-health capability while unknown | hidden/blocked; `BackendCommunicationBlockedException("backend_unknown")` |
| Non-health capability while checking without fresh success | hidden/blocked; `backend_checking` |
| Non-health capability while unreachable | hidden/blocked; `backend_unreachable` |
| Developer override while actual health is unreachable | executable and forced; actual state stays `UNREACHABLE` |
| Probe timeout | persist stable error code `timeout` |
| HTTP health failure | persist `http_<status>` |
| I/O health failure | persist `io` |
| MMKV load/save failure | fall back safely; cold start must not crash |
| Expected gate rejection reaches crash reporter | suppress as an expected local control-flow outcome |

### 5. Good / Base / Bad Cases

- Good: the backend becomes unreachable while Settings is open; remote and diagnostic controls disappear, background upload loops stop, local functionality remains, and a successful foreground probe restores the controls.
- Base: cold start has no confirmed health; ordinary backend features stay hidden until the bounded health probe succeeds.
- Bad: a service collects a camera frame, Shizuku inventory, database snapshot, or signing payload before checking the capability.
- Bad: connectivity failure clears the stored account, cached remote configuration, local data, or opt-in preferences.
- Bad: the force-enable switch changes the displayed actual health to reachable.

### 6. Tests Required

- Policy unit tests assert all states, the 5-minute checking TTL, force semantics, health bypass, and retry backoff.
- Controller unit tests assert persisted initialization, failure/success recovery, stable error codes, override persistence, and single-flight refresh.
- API-boundary tests assert blocked work stops before HTTP execution and representative endpoints map to the intended capability.
- Architecture tests assert one production main-backend client, shared gate injection, final-boundary ordering, GitHub/translation exclusions, and background preflights.
- Settings model tests assert diagnostics are absent while blocked, local controls remain, and the cloud denominator changes from 5 to 4.
- CI must run Android build/lint/test workflows; repository policy forbids local Gradle execution.

### 7. Wrong vs Correct

#### Wrong

```kotlin
val upload = collectCameraAndDevicePayload()
apiClient.uploadFaceAnalysisFrame(token, upload)
```

This spends privacy-sensitive and expensive work before the final API gate rejects the request.

#### Correct

```kotlin
if (!backendGate.decision(BackendCapability.FACE_ANALYSIS).executable) return
val upload = collectCameraAndDevicePayload()
apiClient.uploadFaceAnalysisFrame(token, upload)
```

The API client still repeats `requireExecutable` as the non-bypassable final boundary.

## Design Decision: Preserve independent recovery paths

Translation and GitHub Releases are intentionally outside `BackendCapability`. They can help users recover or update when the main backend is down. Do not reuse the main-backend gate for these clients unless a future product requirement explicitly changes that availability contract.
