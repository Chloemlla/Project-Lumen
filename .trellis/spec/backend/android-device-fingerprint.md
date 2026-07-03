# Android Device Fingerprint Contract

## Scenario: Android Device Fingerprint Reporting

### 1. Scope / Trigger

- Trigger: Android client device identity now crosses secure local storage, app settings, authenticated device registration, telemetry upload, backend storage, and admin dashboard display.
- Applies when changing `SecureCredentialStore.deviceInstallationId()`, `/v1/devices/register`, telemetry `deviceProfile`, or admin device asset rendering.

### 2. Signatures

- Android `SecureCredentialStore.deviceInstallationId(): String` returns one 64-character lowercase SHA-256 hex fingerprint.
- `POST /api/v1/devices/register` accepts `deviceInstallationId` and `deviceFingerprint`.
- Telemetry `deviceProfile` includes `deviceFingerprint`, `deviceName`, `primaryAbi`, and `buildFingerprint`.
- Admin dashboard `AdminDeviceAsset` returns `deviceFingerprint` for registered and telemetry-derived device rows.

### 3. Contracts

- Fingerprint material is deterministic for the same device profile: package name, Android ID, brand, manufacturer, model, device, product, Android release, SDK int, primary ABI, and `Build.FINGERPRINT`.
- The fingerprint generation path must not include a random install secret, UUID, timestamp, user email, access token, or previous settings seed.
- Once generated with the current algorithm version, the encrypted credential store returns the stored value so normal OS updates do not churn identity while app data remains intact.
- Registration payload fields:
  - `deviceInstallationId`: 64-character fingerprint used by existing sync/auth/device fields.
  - `deviceFingerprint`: same 64-character fingerprint, explicit for device asset reporting.
- Telemetry keeps the fingerprint in both top-level `deviceInstallationId` and `deviceProfile.deviceFingerprint`.

### 4. Validation & Error Matrix

- Missing `deviceInstallationId` on registration -> HTTP 400.
- Invalid or missing `deviceFingerprint` on registration -> store an empty explicit fingerprint only for legacy compatibility; new Android clients must send a valid 64-character value.
- Invalid telemetry `deviceProfile.deviceFingerprint` -> sanitize to empty before storing debug payload.
- Device IDs longer than backend limits -> HTTP 400 for top-level ID fields; truncate non-identity profile text fields only.

### 5. Good/Base/Bad Cases

- Good: a vivo V2426A on Android 16 / SDK 36 with primary ABI `arm64-v8a` and the same Build fingerprint gets the same 64-character fingerprint on every app start.
- Good: clearing only in-memory state does not change the fingerprint because it is persisted in encrypted MMKV.
- Base: old clients without `deviceFingerprint` can still register, but admin rows show the explicit fingerprint as not reported.
- Bad: including `UUID.randomUUID()`, install time, or a generated secret in fingerprint material; this makes one physical device produce multiple identities.

### 6. Tests Required

- Unit test: deterministic fingerprint helper returns identical lowercase 64-character SHA-256 hex for the same device profile.
- Unit test: changing model, SDK, ABI, Android ID, or Build fingerprint changes the generated value.
- Repository/API test: device registration stores `deviceFingerprint` and echoes it in `DeviceRegistrationResponse`.
- Telemetry test: invalid `deviceProfile.deviceFingerprint` is cleared, while valid 64-character hex survives sanitization.
- Admin test: device asset rows prefer explicit registered/telemetry fingerprint and shorten it only in the web dashboard display.

### 7. Wrong vs Correct

#### Wrong

```kotlin
val fingerprint = sha256Hex(UUID.randomUUID().toString() + Build.FINGERPRINT)
```

#### Correct

```kotlin
val material = listOf(
    packageName,
    androidId,
    Build.MANUFACTURER,
    Build.MODEL,
    Build.VERSION.SDK_INT.toString(),
    Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
    Build.FINGERPRINT,
).joinToString("|") { it.trim().lowercase() }
val fingerprint = sha256Hex(material)
```

The correct path makes the identifier deterministic for one device profile while still producing a 64-character opaque value for backend correlation.
