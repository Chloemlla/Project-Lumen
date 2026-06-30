# Project Lumen API

Rust backend API service for Project Lumen client integration.

Default public base URL:

```text
http://eye.chloemlla.com/api
```

The service mounts all routes under `/api` by default.

## Environment

```text
LUMEN_BIND_ADDRESS=0.0.0.0:8080
LUMEN_API_PREFIX=/api
LUMEN_ADMIN_STATIC_DIR=backend/admin
LUMEN_MONGODB_URI=mongodb://localhost:27017
LUMEN_MONGODB_DATABASE=project_lumen
LUMEN_ADMIN_USERNAME=admin
LUMEN_ADMIN_PASSWORD=change-me
LUMEN_ADMIN_ACCESS_TOKEN_TTL_SECONDS=3600
LUMEN_ADMIN_REFRESH_TOKEN_TTL_SECONDS=604800
LUMEN_DEV_LOGIN_CODE=000000
LUMEN_LOGIN_TTL_SECONDS=600
LUMEN_ACCESS_TOKEN_TTL_SECONDS=604800
LUMEN_ACCEPT_UNVERIFIED_PURCHASES=false
```

`LUMEN_ACCEPT_UNVERIFIED_PURCHASES=false` is intentionally fail-closed for Google Play purchases until real platform verification credentials are wired in.

## Admin Dashboard

The admin frontend is a zero-build static dashboard served by the Rust service:

```text
GET /admin
```

It lives in `backend/admin/` and uses plain HTML, CSS, and JavaScript. It does not require React, npm, a bundler, or any frontend install step.

The dashboard includes the 20 operations modules needed for Project Lumen admin workflows:

- Users, devices, access audit, entitlement changes, Google purchase audit, and cloud backups.
- Crash aggregation, sanitized stack review, version/device impact, API health, and sync throughput.
- Template CMS, visual template parameters, audio/haptics matrix, i18n dispatch, and anonymous macro telemetry.
- OTA integrity registry, rollout policy, Rust route topology, HTTP allowlist review, and admin session security.

Sensitive action buttons are disabled when the dashboard is opened over non-local HTTP. Production admin access should be served through HTTPS only.

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

The dashboard reads `/api/admin/dashboard` for live MongoDB-backed data. The static fallback data only keeps the UI usable before login or before management collections receive samples.

## Endpoints

```text
GET  /api/health
POST /api/v1/auth/email/start
POST /api/v1/auth/email/verify
GET  /api/v1/me
GET  /api/v1/entitlements
POST /api/v1/purchases/google/verify
GET  /api/v1/sync/changes?since=cursor
POST /api/v1/sync/push
POST /api/v1/backups
GET  /api/v1/backups/latest
```

Authenticated endpoints use:

```text
Authorization: Bearer <accessToken>
```
