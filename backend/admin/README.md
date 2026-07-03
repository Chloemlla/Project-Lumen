# Admin Dashboard

React 19 + TypeScript admin dashboard for Project Lumen operations.

Source lives in:

- `index.html`
- `src/**/*.tsx`
- `src/styles.css`

The GitHub workflow and Docker build run the Vite build and emit `dist/`. The Rust API service serves the built `dist/` directory at `/admin`.

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

Admin access and refresh tokens are held in memory for the current tab. The dashboard remembers only the last username in `localStorage`.
