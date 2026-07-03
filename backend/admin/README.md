# Admin Dashboard

Static admin dashboard for Project Lumen operations.

This frontend intentionally uses only:

- `index.html`
- `assets/styles.css`
- `assets/app.js`

There is no React, npm package, bundler, or frontend build step. The Rust API service serves it at `/admin`.

The UI is structured around four operation areas:

- Users and entitlements
- Crash reports and observability
- Core content and telemetry
- Release and security ops

The dashboard can probe `/api/health` directly and loads live data from `/api/admin/dashboard` after admin login.

Admin session flow:

- `POST /api/admin/auth/login`
- `POST /api/admin/auth/refresh`
- Bearer token for `/api/admin/dashboard` and `/api/admin/actions`

Sensitive actions are recorded through `/api/admin/actions`; supported actions also update MongoDB management collections for manual plan grants, Pro revocation, template dispatch, forced update policy, and security allowlist changes. A configured `LUMEN_ADMIN_AUTOMATION_TOKEN` is accepted by `/api/admin/actions` only for workflow automation.
