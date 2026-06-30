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
LUMEN_DEV_LOGIN_CODE=000000
LUMEN_LOGIN_TTL_SECONDS=600
LUMEN_ACCESS_TOKEN_TTL_SECONDS=604800
LUMEN_ACCEPT_UNVERIFIED_PURCHASES=false
```

`LUMEN_ACCEPT_UNVERIFIED_PURCHASES=false` is intentionally fail-closed for Google Play purchases until real platform verification credentials are wired in.

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
