# Project Lumen API

Rust backend API service for Project Lumen client integration.

Default public base URL:

```text
https://eye.chloemlla.com/api
```

The service mounts all routes under `/api` by default.

## Environment

```text
LUMEN_BIND_ADDRESS=0.0.0.0:8080
LUMEN_API_PREFIX=/api
LUMEN_ADMIN_STATIC_DIR=backend/admin/dist
LUMEN_MONGODB_URI=mongodb://localhost:27017
LUMEN_MONGODB_DATABASE=project_lumen
LUMEN_ADMIN_USERNAME=admin
LUMEN_ADMIN_PASSWORD=change-me
LUMEN_ADMIN_AUTOMATION_TOKEN=
LUMEN_ADMIN_ACCESS_TOKEN_TTL_SECONDS=3600
LUMEN_ADMIN_REFRESH_TOKEN_TTL_SECONDS=604800
LUMEN_DEV_LOGIN_CODE=000000
LUMEN_LOGIN_TTL_SECONDS=600
LUMEN_OUTEMAIL_BASE_URL=https://tts.chloemlla.com
LUMEN_OUTEMAIL_API_KEY=
LUMEN_OUTEMAIL_FROM=noreply
LUMEN_OUTEMAIL_DISPLAY_NAME=Project Lumen
LUMEN_OUTEMAIL_DOMAIN=
LUMEN_OUTEMAIL_TIMEOUT_SECONDS=10
LUMEN_ACCESS_TOKEN_TTL_SECONDS=604800
LUMEN_REQUEST_SIGNING_SECRET=project-lumen-local-request-signing-key
LUMEN_REQUEST_TIMESTAMP_SKEW_SECONDS=300
LUMEN_REQUIRE_REQUEST_SIGNING=false
LUMEN_ALLOW_PUBLIC_RELEASE_CHECK=true
LUMEN_ACCEPT_UNVERIFIED_PURCHASES=false
```

`LUMEN_ACCEPT_UNVERIFIED_PURCHASES=false` is intentionally fail-closed for Google Play purchases until real platform verification credentials are wired in.

Request signing verification is disabled by default, including production deployments. Set `LUMEN_REQUIRE_REQUEST_SIGNING=true` explicitly to require the `X-Lumen-Timestamp`, `X-Lumen-Nonce`, and `X-Lumen-Signature` headers on protected `/v1` routes.

When request signing is enabled, `LUMEN_REQUEST_SIGNING_SECRET` must match the Android release build secret supplied as `PROJECT_LUMEN_REQUEST_SIGNING_SECRET`; otherwise signed client requests fail with HTTP 403. Keep the default value for local development only.

`LUMEN_ALLOW_PUBLIC_RELEASE_CHECK=true` lets `GET /api/v1/releases/check` and legacy `GET /v1/releases/check` bypass HMAC signing so already-released APKs can discover corrective updates. Set it to `false` only when the release-check endpoint should require the same request signature as other `/v1` routes. GitHub deployment reads this from the non-secret repository variable `LUMEN_ALLOW_PUBLIC_RELEASE_CHECK`.

Android builds always require HTTPS and Android/OkHttp system trust for backend communication. `PROJECT_LUMEN_API_CERTIFICATE_PINS` and `PROJECT_LUMEN_TRANSLATION_CERTIFICATE_PINS` are optional hardening inputs; they are compiled into the APK only when `PROJECT_LUMEN_API_CERTIFICATE_PINNING_ENABLED=true` or `PROJECT_LUMEN_TRANSLATION_CERTIFICATE_PINNING_ENABLED=true`, and the build fails if pinning is enabled without pins. Leave pinning disabled when certificates are managed by a public CA or CDN, and enable it only when the release process can provide both current and backup `sha256/` pins for the active host.

Email login sends verification codes through Happy-TTS outemail when both `LUMEN_OUTEMAIL_BASE_URL` and `LUMEN_OUTEMAIL_API_KEY` are configured. The API key is sent as a Bearer token to `POST /api/outemail/send`; when the key is empty, `/api/v1/auth/email/start` keeps returning `devCode` for development use.

## Admin Dashboard

The admin frontend is a React 19 + TypeScript dashboard served by the Rust service after the Vite build emits static assets:

```text
GET /admin
```

Source lives in `backend/admin/` and uses TSX components. The GitHub workflow and Docker image build run `npm install` and `npm run build` in `backend/admin/`, then the backend serves `backend/admin/dist` at `/admin`.

The dashboard includes the 20 operations modules needed for Project Lumen admin workflows:

- Users, devices, access audit, entitlement changes, Google purchase audit, and cloud backups.
- Crash aggregation, sanitized stack review, version/device impact, API health, and sync throughput.
- Template CMS, visual template parameters, audio/haptics matrix, i18n dispatch, and anonymous macro telemetry.
- OTA integrity registry, rollout policy, Rust route topology, HTTP allowlist review, and admin session security.

Sensitive action buttons are disabled when the dashboard is opened over non-local HTTP. Production admin access should be served through HTTPS only. Admin access and refresh tokens are kept in memory for the current tab rather than stored in `localStorage`.

`LUMEN_ADMIN_AUTOMATION_TOKEN` is optional, must be at least 32 characters, and is accepted only by `POST /api/admin/actions`.
Set it to the same value as the GitHub Actions secret `PROJECT_LUMEN_ADMIN_TOKEN` when release workflows need to sync `release-manifest.json` to MongoDB.

Admin API endpoints:

```text
POST /api/admin/auth/login
POST /api/admin/auth/refresh
GET  /api/admin/me
GET  /api/admin/dashboard
POST /api/admin/actions
```

MongoDB management collections:

```text
admin_sessions
admin_actions
admin_access_audit
admin_crash_reports
admin_api_metrics
admin_sync_metrics
admin_templates
admin_telemetry
admin_releases
admin_security_allowlist
```

The dashboard reads `/api/admin/dashboard` for live MongoDB-backed data after login. Static fallback data is only for the pre-login/pre-load UI; authenticated dashboard responses return real MongoDB records or empty sections.

## Endpoints

```text
GET  /api/health
GET  /api/openapi.json
POST /api/v1/auth/email/start
POST /api/v1/auth/email/verify
POST /api/v1/auth/session/refresh
GET  /api/v1/me
POST /api/v1/devices/register
GET  /api/v1/entitlements
GET  /api/v1/config/feature-flags
GET  /api/v1/config/sync?cursor=cursor&version=1&channel=stable
POST /api/v1/purchases/google/verify
GET  /api/v1/sync/changes?since=cursor
POST /api/v1/sync/push
POST /api/v1/backups
GET  /api/v1/backups/latest
POST /api/v1/telemetry
GET  /api/v1/telemetry/debug/latest
POST /api/v1/face-analysis/frames
GET  /api/v1/releases/check?currentVersionCode=code&abi=arm64-v8a&channel=stable
```

Authenticated endpoints use:

```text
Authorization: Bearer <accessToken>
```
