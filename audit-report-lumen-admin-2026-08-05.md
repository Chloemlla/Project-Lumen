# Static Audit Report — Project Lumen Admin Dashboard

- **Date:** 2026-08-05
- **Scope:** `backend/admin/` — React 19 + TypeScript + Vite dashboard served by the Rust API at `/admin`
- **Auditor:** code review agent (static analysis)
- **Method:** Read every source file under `backend/admin/src/`, plus `index.html`, `package.json`, all `tsconfig*.json`, `vite.config.ts`, README, and the relevant backend (`server.rs`, `api.rs`, `admin_context.rs`, `store/admin_auth.rs`, `routes/admin.rs`, `routes/audit.rs`, `models/admin.rs`), CI (`build.yml`, `codeql.yml`, `dependabot-maintenance.yml`) and `Dockerfile` for cross-cutting security/release context.

---

## 1. Code Size Baseline

| Artifact | Files | Lines |
|---|---|---|
| TypeScript / TSX (`src/`) | 19 | ~3,272 |
| CSS (`src/styles.css`) | 1 | 858 |
| Config (`package.json`, `tsconfig*.json`, `vite.config.ts`, `index.html`) | 6 | ~100 |
| **Total** | 26 | **~4,230** |

Dependencies: runtime = `react`, `react-dom`, `lucide-react`; dev = `typescript`, `vite`, `@vitejs/plugin-react`, `@types/*`. No test, lint, or formatting tooling is installed.

---

## 2. Executive Summary

The dashboard is well-structured at the model layer: JSON decoding is defensive (`jsonAccess.ts`), state flows one direction through `AdminDashboardApp`, TypeScript is strict with `noUncheckedIndexedAccess`, and there is no `dangerouslySetInnerHTML`/`eval` anywhere (no direct XSS sink). React escapes user/backend data by default and the admin token is kept in memory only (never in `localStorage`/cookies) — good baseline hygiene.

The weaknesses are concentrated in (a) **release/dependency hygiene** (no lockfile, EOL Vite, no JS SAST), (b) **zero automated testing**, (c) **back-end CORS/headers posture** that the SPA depends on, and (d) **engineering robustness** (no error boundary, no fetch timeout, a double-refresh race, and uncontrolled forms whose DOM values can overwrite newer server policy with stale data).

### Scores (1–10)

| Dimension | Score | One-line rationale |
|---|---|---|
| Security | **5 / 10** | No XSS sink and in-memory tokens are good; but permissive backend CORS, no CSP, EOL Vite, no lockfile, and the client-side HTTPS gate is cosmetic. |
| Stability | **6 / 10** | Very defensive data mapping; missing error boundary, fetch timeout, refresh race, and stale-form overwrite risk. |
| Performance | **7 / 10** | Fine at admin scale; no memoization causes full-tree re-renders on every keystroke/30 s tick. |
| Testing | **1 / 10** | Zero tests, no test framework, CI only runs `npm run build`. |
| Maintainability | **6 / 10** | Clean layering and strict types; DOM-coupled action payload builder, dead exports, hardcoded constants, duplicated literals. |
| Design | **7 / 10** | Sound React patterns, pure model functions; 483-line mega-component and uncontrolled forms drag it down. |
| Release | **4 / 10** | No committed lockfile, EOL Vite, Docker double-copy, no lint/test/SAST gate in CI. |
| **Overall** | **5 / 10** | Solid small dashboard with systemic gaps in testing and dependency/release hygiene. |

---

## 3. Findings Table

| ID | Severity | File | Line | Description | Category |
|---|---|---|---|---|---|
| F-01 | High | `backend/src/server.rs` | 44–49 | Backend CORS is `allow_origin(Any).allow_methods(Any).allow_headers(Any)`; any web origin can call admin login/actions and read responses. | Security |
| F-02 | High | `backend/admin/` (repo) | `package.json` | No committed `package-lock.json`; CI (`build.yml:470`) and Docker (`Dockerfile:16`) run `npm install` — non-reproducible, unpinned transitive deps. `.gitignore` does **not** ignore lockfiles, so this is an omission. | Release |
| F-03 | High | `backend/admin/package.json` | 23 | No test files, no test runner, no `test` script; CI runs only `npm run build`. All pure business logic (model/), parsing, and payload building is untested. | Testing |
| F-04 | Medium | `backend/src/server.rs` | 43–50 | No security headers on the server that serves the admin SPA: no CSP, no `X-Frame-Options`, no `X-Content-Type-Options`, no `Referrer-Policy`. No XSS mitigation / no clickjacking protection for an admin console. | Security |
| F-05 | Medium | `backend/admin/package.json` | 23 | `vite ^5.4.11` is end-of-life (no security backports). Upgrade to Vite 7 (and current `@vitejs/plugin-react`). | Release |
| F-06 | Medium | `backend/admin/src/api/adminApi.ts` | 21–37 | `requestJson` has no timeout / `AbortController`. A hung request leaves buttons disabled and loading spinners stuck indefinitely. | Stability |
| F-07 | Medium | `backend/admin/src/AdminDashboardApp.tsx` | 156–175 | Concurrent 401s each trigger `refreshAdminSession`. Backend refresh consumes the refresh token (`find_one_and_delete` in `store/admin_auth.rs`), so the second concurrent refresh fails → `clearSession()` → spurious logout. Needs a single-flight refresh. | Stability |
| F-08 | Medium | `backend/admin/src/components/modules/ContentModules.tsx`, `ReleaseModules.tsx`, `UserModules.tsx` | 36–76, 123–244, 74–138 | All form inputs use `defaultValue` (uncontrolled). After `fetchDashboard()` refreshes data, inputs keep stale values; `buildActionPayload` reads the DOM, so an admin can silently re-save stale policy over newer server state. | Stability |
| F-09 | Medium | `backend/admin/src/AdminDashboardApp.tsx` | 283–347 | No React error boundary anywhere. Any render throw in a module (current code is mostly safe, but no protection against regressions) blanks the entire dashboard. | Stability |
| F-10 | Medium | `backend/admin/src/AdminDashboardApp.tsx` | 314–322, 357–365 | `runtimeState` is rebuilt on every render and passed to all `ModuleCard`s; no `React.memo`/memoization. Every search keystroke and the 30 s `now` tick re-render all visible modules and their tables. | Performance |
| F-11 | Medium | `backend/admin/src/model/actionPayloads.ts` | 174–184 | `readField()` reaches into the DOM by `document.getElementById`. Fragile (id collisions, refactors), untestable, and the root cause of the uncontrolled-form pattern. Prefer controlled form state / context. | Maintainability |
| F-12 | Low | `backend/admin/src/api/adminApi.ts` | 39–49 | `probeHealth` sends the admin `Authorization: Bearer` token to the **public** `/api/health` route. The token is exposed to an unauthenticated endpoint (logs/proxies) and `audit_request` records the operator identity on health probes. Probe health without a token. | Security |
| F-13 | Low | `backend/admin/src/AdminDashboardApp.tsx` | 96–102, 481–483 | Server error bodies are sliced (`text.slice(0, 180)` in `adminApi.ts:71`) and rendered into toast DOM. If the backend ever echoes sensitive data in errors it surfaces on screen. Also leaks internal error messages to anyone viewing. | Security |
| F-14 | Low | `backend/admin/src/model/dashboardModel.ts` | 71–73 | The "Sensitive actions blocked until HTTPS" gate (`isSecureAdminOrigin`) is client-side UX only. The backend `/admin/actions` accepts a valid bearer token from any origin; the banner overstates the protection. | Security |
| F-15 | Low | `backend/admin/src/AdminDashboardApp.tsx` | 167–171 | After a failed refresh, `apiJson` still retries with `sessionRef.current.token` which is now `""` — a redundant unauthenticated request that 401s again. Bail out when the token is empty. | Stability |
| F-16 | Low | `backend/admin/src/AdminDashboardApp.tsx` | 116–128 | `clearSession()` wipes the entire loaded dashboard back to fallback placeholders on refresh failure, even though previously loaded data is still valid. Keep last-known-good data. | Stability |
| F-17 | Low | `backend/admin/src/model/dashboardModel.ts` | 59 | `cloneJson` is exported but never used (dead code). | Maintainability |
| F-18 | Low | `backend/admin/src/types.ts` | 323–331 | `RuntimeState.token`, `RuntimeState.tokenExpiresAt`, `RuntimeState.now` are never consumed by `buildActionPayload` (only `range` is). The admin token is threaded into a broadly-passed state object unnecessarily. | Maintainability |
| F-19 | Low | `backend/admin/src/AdminDashboardApp.tsx` | 40–44 | `ApiJsonOptions.skipRefresh` is never passed `true` by any caller — dead option. | Maintainability |
| F-20 | Low | `backend/admin/src/AdminDashboardApp.tsx` | 265 | Pasted-token expiry hardcoded to `Date.now() + 50*60*1000` (50 min) while backend default TTL is 3600 s (`config.rs:43`). `SessionChip` shows "expired" ~10 min early for pasted tokens. | Maintainability |
| F-21 | Low | `backend/admin/src/components/MiniChart.tsx` | 11, 36 | `series.join(",")` builds a potentially large string every render and `getComputedStyle` runs on every draw. Minor. | Performance |
| F-22 | Low | `backend/admin/Dockerfile` | 32–33 | `dist` is copied twice: to `/app/backend/admin/dist` and to `/app/backend/admin`. The second copy is redundant/confusing given `LUMEN_ADMIN_STATIC_DIR=/app/backend/admin/dist`. | Release |
| F-23 | Low | `backend/admin/package.json` | 1–25 | No `engines` field; Node version only pinned implicitly by CI/Docker (`node:24-bookworm`). Minor reproducibility gap. | Release |
| F-24 | Low | `.github/workflows/codeql.yml` | 24–31 | CodeQL matrix covers `java-kotlin`, `rust`, `actions` — **no JavaScript/TypeScript** analysis; no `npm audit`/OSV scanning for the admin. | Release |
| F-25 | Info | `backend/admin/src/AdminDashboardApp.tsx` | 214–219 | `StrictMode` double-invokes the mount effect in dev → two health probes. Harmless but noisy. | Stability |
| F-26 | Info | `backend/admin/src/AdminDashboardApp.tsx` | 50–51, 357–365 | `environment` select is cosmetic — never sent to the backend; `range` is only used by `copy-sync-report`. Operators may believe these filter live data; they don't. | Maintainability |
| F-27 | Info | `backend/admin/src/model/dashboardModel.ts` | 75–295 | `mapDashboard` and `fallbackDashboardData` (`data/adminSections.ts`) must be kept in sync with the `DashboardData` type; TS enforces shape but not defaults. Acceptable, worth a comment. | Maintainability |
| F-28 | Info | `backend/admin/src/components/common.tsx` | 45–48 | Good a11y overall (aria-labels, `role` attributes, reduced-motion support). One nit: `aria-live="polite"` on the whole `module-grid` re-announces every filter keystroke to screen readers. | Design |
| F-29 | Info | `backend/admin/src/model/dashboardModel.ts` | 424–427 | `safeColor` accepts 3/4/5/6/7/8-digit hex; CSS only supports 3/4/6/8. `#abcde` (5) would produce an invalid CSS color (browser falls back silently). | Maintainability |
| F-30 | Info | `backend/admin/src/AdminDashboardApp.tsx` | 96–102 | Toast ids use `Date.now() + Math.random()`; keys are collision-safe in practice. Fine. | Design |
| F-31 | Info | `backend/admin/src/AdminDashboardApp.tsx` | 278–281 | `refreshAll` runs health + dashboard sequentially; no issue, just a design note. | Design |

> Backend-context items F-01, F-04 are technically outside `backend/admin/src` but are the security posture the dashboard relies on (they were in scope as cross-component contract).

---

## 4. Detailed Findings

### 4.1 Security

**F-01 — Permissive CORS (High, backend-context).**
`server.rs` applies `CorsLayer::new().allow_origin(Any).allow_methods(Any).allow_headers(Any)` to the whole app. Since auth is header-based (in-memory bearer token), a malicious origin cannot *steal* the token directly, but it can:
- call `POST /api/admin/auth/login` and read responses (no preflight friction → trivial credential-stuffing / brute-force, no rate limit was found on the route);
- call any admin endpoint with a token it obtained any other way;
- make this a single layer of defense (the token) between an attacker and a 1-hour admin session.

Recommendation: allowlist the exact admin dashboard origin(s) in CORS, and add rate limiting / lockout on `/admin/auth/login`.

**F-04 — Missing security headers (Medium).**
The same server serves the SPA from `/admin` with no CSP, `X-Frame-Options`, or `X-Content-Type-Options`. For an operations console that holds admin tokens in memory, an XSS anywhere (e.g., a future regression) would run with no CSP mitigation, and the page is clickjackable. Add:
`Content-Security-Policy: default-src 'self'; frame-ancestors 'none'`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`.

**F-12 — Token sent to public health endpoint (Low).**
`probeHealth` (`adminApi.ts:41`) passes the admin token to `GET /api/health`, which is unauthenticated (`api.rs:60`). The token never needs to reach that route; if the environment logs `Authorization` at a proxy/load-balancer, the admin token is on a public path. Audit middleware also records the operator's username for health probes. Fix: `probeHealth()` with no token.

**F-13 — Server error text rendered into toasts (Low).**
`parseJsonPayload` slices raw response text into the `AdminHttpError.message`, which `AdminDashboardApp` surfaces in a toast. This is fine today (React escapes text), but it displays raw backend error bodies on screen and could echo sensitive data if the backend ever reflects request content in errors.

**F-14 — Client-side HTTPS gate is cosmetic (Low).**
`isSecureAdminOrigin()` gates sensitive buttons, but the backend accepts a valid bearer token from any origin. The banner "Sensitive actions blocked until HTTPS is enabled" is accurate as UX but should not be presented as a security boundary. Real enforcement must happen server-side (origin allowlist → F-01).

**Positive notes (no finding needed):** no `dangerouslySetInnerHTML`/`eval`/`innerHTML`/`document.cookie` anywhere; `ColorValue` validates color format with `safeColor` before applying as inline style; tokens live only in React state and are cleared on logout; `clearLegacyStoredTokens()` scrubs any historical localStorage tokens on boot; password field is cleared after login.

### 4.2 Stability

**F-07 — Refresh race (Medium).** The 401-retry path (`AdminDashboardApp.tsx:156–175`) calls `refreshAdminSession()` per failing request. The backend refresh handler does `find_one_and_delete({ refreshToken })` (`store/admin_auth.rs:35`), so the second concurrent refresh gets 401 → `clearSession()`. Realistic trigger: token expires while two module actions or a refresh + action overlap. Fix: module-level single-flight promise shared by all callers.

**F-08 — Stale uncontrolled forms can overwrite newer server policy (Medium).** Template editor, allowlist, silent-vision, lifecycle-lock, and plan forms all use `defaultValue`. After `fetchDashboard()` the `data` prop changes but inputs don't. `buildActionPayload` reads the DOM, so clicking "Save vision policy" after a refresh can push the *previous* values back over the *current* server policy. Convert to controlled inputs (or re-key the inputs by the data revision).

**F-06 — No fetch timeout (Medium).** A stalled connection leaves `loading.action`, `loading.dashboard`, and buttons in their busy state indefinitely. Add `AbortSignal.timeout()` and surface a "request timed out" toast.

**F-09 — No error boundary (Medium).** `main.tsx` renders `<AdminDashboardApp/>` with no boundary. The current mapping code is defensive (every array read has a fallback; `.slice`/`.map` calls operate on strings guaranteed by `readString` defaults), so no crash was found statically — but a single future render exception takes down the whole dashboard. Wrap the `module-grid` in an error boundary with a "Reset view" button.

**F-15 / F-16 — Retry with empty token; wiping good data on refresh failure (Low).** Both are consequences of `refreshAdminSession` calling `clearSession()` on failure. After that, the retry re-fires with `token: ""` (wasted 401) and the dashboard resets to placeholders despite valid cached data. Prefer keeping last-known-good data and distinguishing "logged out" from "refresh hiccup".

### 4.3 Performance

**F-10 — No memoization (Medium).** `AdminDashboardApp` re-renders on every state change (query keystrokes, 30 s `now` tick, toasts) and passes a fresh `runtimeState` object to all visible `ModuleCard`s. With no `React.memo`, each keystroke re-renders every visible module and its table. Cheap fixes: `useMemo` the `runtimeState`, `useCallback` the `onAction`, and wrap `ModuleCard` (and `DataTable` bodies) in `React.memo`.

**F-21 — MiniChart overhead (Low).** `series.join(",")` stringifies the whole series each render to feed the effect deps; `getComputedStyle` runs on every draw. Trivial at current sizes.

**Positive note:** `moduleActions` is a module-level constant (not recreated), `statusCounts` is memoized, and lucide-react tree-shakes, so the bundle should be modest.

### 4.4 Testing

**F-03 — Zero coverage (High).** There are no `*.test.*`/`*.spec.*` files, no `vitest`/`jest`/Testing Library, no `test` script, and CI (`build.yml:467–471`) runs only `npm install && npm run build`. The highest-value logic is pure and easily unit-tested:
- `jsonAccess.ts` (all `read*` helpers),
- `dashboardModel.ts` (`mapDashboard`, `parseAdminSession`, `normalizeStatus`, `formatTime`, `safeColor`, `buildDashboardSummary`),
- `actionPayloads.ts` (`buildActionPayload`, `buildTemplatePayload`, `buildAllowlistPayload`),
- `adminApi.ts` (`requestJson` status/JSON/error handling, ideally with a mocked `fetch`).

Recommendation: add `vitest` + `@testing-library/react`, a `test` script, and wire `npm test` into `build.yml`.

**F-02 — No lint/format gate (Low).** No ESLint/Prettier/Biome config or script; CI has no JS lint step, and CodeQL excludes JavaScript/TypeScript (F-24). Style consistency is manual.

### 4.5 Maintainability

- **F-11 — DOM-coupled action payloads (Medium).** `buildActionPayload` reads form values from `document.getElementById(...)`. This ties payload construction to the DOM, makes it untestable, risks id collisions, and is the root cause of the uncontrolled-forms problem (F-08). Pass form state in via props/context instead.
- **F-17/F-18/F-19 — Dead code.** `cloneJson` (unused export), `RuntimeState.token/tokenExpiresAt/now` (unused fields — and `token` unnecessarily threads the admin credential through every module card), and `ApiJsonOptions.skipRefresh` (never used). Removing the token from `RuntimeState` is also a small hygiene win.
- **F-20 — Hardcoded token TTL mismatch.** `50 * 60 * 1000` hardcoded vs backend default `3600` s. Read `expiresAt` from the `/admin/me` response if the backend can return it, or make the constant shared.
- **F-26 — Cosmetic controls.** `environment` is never sent to the backend; `range` only affects the sync report. Either wire them into the request or mark them as display-only to avoid operator confusion.
- **F-27/F-29 — Drift risks.** `fallbackDashboardData` + `mapDashboard` must stay in sync with `DashboardData`; `safeColor` regex accepts hex lengths CSS does not. Small tidy-ups.

### 4.6 Design

- **D-01 — Uncontrolled forms + DOM reads** (see F-08/F-11): the single biggest design smell. Controlled inputs (or a lightweight form context) would make the action path testable and eliminate the stale-value hazard.
- **D-02 — 483-line `AdminDashboardApp`.** Auth/session, health, dashboard fetch, action dispatch, toast management, and download/clipboard helpers all live in one component. Extract `useAdminSession`, `useDashboardData`, `useToasts`, `useAdminActions` hooks.
- **D-03 — Strengths.** Clean layering (thin components over pure `model/` functions), strict TS (`noUncheckedIndexedAccess`), `sessionRef` keeps the session synchronous across callbacks, toasts are capped (max 3), `aria-live` regions are used, and `prefers-reduced-motion` is respected. `DashboardData` flows one way from store → map → state, matching the Android side's philosophy.

### 4.7 Release

- **F-02 — No lockfile (High).** `backend/admin` has no committed `package-lock.json`; CI and Docker use `npm install`, so every build resolves fresh `^`-range versions (including transitive deps). `.gitignore` does not exclude lockfiles, so committing one is the fix. (The repo root and `tools/lumen-ui-tuner` use pnpm/npm with lockfiles, so this dashboard is the outlier.)
- **F-05 — EOL Vite (Medium).** `vite ^5.4.11` is past end-of-life; no security backports. Upgrade to Vite 7 and verify `@vitejs/plugin-react` compatibility.
- **F-22 — Docker double-copy (Low).** `Dockerfile:32–33` copies `dist` to both `/app/backend/admin/dist` and `/app/backend/admin`; the env var points at the former. The second copy is redundant/confusing.
- **F-23 — No `engines` (Low).** Pin `node` in `package.json` to match CI/Docker (`node:24`).
- **F-24 — No JS SAST (Low).** Add `javascript-typescript` to the CodeQL matrix, and/or a `npm audit --omit=dev` step.

---

## 5. Prioritized Recommendations

1. **Commit a `package-lock.json`** for `backend/admin`; switch CI/Docker to `npm ci`. (F-02, R-01)
2. **Upgrade Vite 5 → 7** and current plugin. (F-05)
3. **Add vitest + Testing Library**, unit-test `model/`, `actionPayloads`, `jsonAccess`, `adminApi`; add `test` + `lint` scripts to CI. (F-03, F-24)
4. **Harden backend admin transport**: explicit CORS origin allowlist, rate limit `/admin/auth/login`, add CSP + `frame-ancestors 'none'` headers on the admin-serving routes. (F-01, F-04)
5. **Stop sending the admin token to `/api/health`.** (F-12)
6. **Make token refresh single-flight** and keep last-known-good dashboard data. (F-07, F-16)
7. **Convert module forms to controlled state** so payload building reads React state, not the DOM. (F-08, F-11)
8. **Add `AbortSignal.timeout()`** to `requestJson`. (F-06)
9. **Add a React error boundary** around the module grid. (F-09)
10. **Memoize `runtimeState` and wrap `ModuleCard` in `React.memo`.** (F-10)
11. Remove dead code (`cloneJson`, unused `RuntimeState` fields, `skipRefresh`) and stop threading the admin token through `RuntimeState`. (F-17/18/19)

---

## 6. Notes / Non-Issues

- Backend response contract was verified: `models/admin.rs` serializes with `#[serde(rename_all = "camelCase")]`, so frontend reads of `accessToken`/`refreshToken`/`expiresAt`/`generatedAt`/etc. match. No field-name mismatch found.
- `parseJsonPayload` correctly handles empty bodies (returns `{}`) and non-JSON error responses.
- `backupDownloadName` sanitizes the file id; `downloadJsonFile` uses a blob URL (no `data:`-URL injection).
- `MiniChart` null-checks the 2D context and handles the empty-series case.
- No `gradlew`/Android concerns in this scope; the dashboard is fully client-rendered with a `<noscript>` fallback message.
- The Dockerfile also builds the admin with `npm install` (non-reproducible) — same fix as F-02.
