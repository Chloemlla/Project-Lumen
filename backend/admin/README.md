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

The dashboard can probe `/api/health` directly. Other admin actions are wired as UI-ready controls and copy/export flows so dedicated admin API endpoints can be connected without replacing the frontend shell.
