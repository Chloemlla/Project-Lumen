# Project Lumen — Rust Backend Static Audit Report

**Date:** 2026-08-05
**Scope:** `F:\Repositories\GitHub\Project-Lumen\backend\src\` (all 51 `.rs` files) + `Cargo.toml`, `Dockerfile`, `.github/workflows/build.yml`, `backend/README.md`
**Method:** Manual static review (no builds/tests run locally per repo policy; CI runs `cargo fmt --check` and `cargo test --all-targets` only).

---

## Code size baseline

| Metric | Value |
|---|---|
| Rust source files | 51 |
| Total lines | 8,049 |
| Test functions | 7 (`auth_context.rs`: 3, `routes/security.rs`: 2, `store/entitlements.rs`: 2) |
| dev-dependencies | none (`Cargo.toml` has no `[dev-dependencies]`) |
| MongoDB collections | 23 |
| `unwrap()` / `expect()` / `panic!` in non-test code | 0 (only inside `#[cfg(test)]`) |
| TTL indexes | 1 of ~35 (`api_nonces`) |

---

## Executive summary and scores

| Dimension | Score | One-line rationale |
|---|---|---|
| Security | **5 / 10** | Solid HMAC+nonce request-signing design, but purchase verification is a stub, the device-security gate is client-spoofable, admin login is unthrottled, and auth can fall back to a known dev code. |
| Stability | **6 / 10** | No panics in production paths and careful error mapping, but 20+ collections grow unbounded, no graceful shutdown, no Mongo timeouts/retry. |
| Performance | **5 / 10** | Audit middleware adds 2 DB ops to every request, sync `changes_since` returns unbounded payloads, per-upload `count_documents`. |
| Testing | **3 / 10** | Only 7 unit tests, no integration tests, no Mongo mocking, no dev-dependencies; the security middleware itself is untested. |
| Maintainability | **6 / 10** | Clean store/routes/models layering, but duplicated helpers, dead code, README/config drift. |
| Design | **6 / 10** | Good single-direction layering and fail-closed defaults, but the purchase pipeline is incomplete and the device-security gate provides false assurance. |
| Release | **5 / 10** | Multi-stage Dockerfile is sane; CI runs neither clippy nor a release build, no graceful shutdown, README env docs drift from code. |

**Overall: 5 / 10** — a well-structured and thoughtfully-fail-closed codebase whose largest risks are (1) the unverifiable purchase/device-security trust model, (2) unbounded MongoDB growth, and (3) missing rate limiting on auth/admin/upload paths.

---

## Findings

| ID | Sev | File | Line | Description | Category |
|---|---|---|---|---|---|
| LUMEN-B1 | **High** | `src/store/entitlements.rs` | 57–113 | Google Play purchase "verification" never calls the Play Developer API. With `LUMEN_ACCEPT_UNVERIFIED_PURCHASES=false` (default/prod) every purchase is stored with `status="pending"` and **nothing ever promotes it to `active`** — the purchase feature cannot work. With it `true`, any authenticated user self-grants `PLUS`/`TEAM` by POSTing a fake `purchaseToken` (tier derived from `product_id` string only). | Security |
| LUMEN-B2 | **High** | `src/auth_context.rs` | 11–70 | Device-security gate (`require_device_security`) trusts client-supplied evidence. `store/devices.rs:88–109` always stamps `"verified": false` on stored evidence, but `require_device_security` **never checks that flag** — it only checks client-controlled booleans (`status=="clean"`, `completed`, `rooted`, `observedAt` freshness). Any authenticated user can self-attest a "clean" device and pass every `require_device_security` gate. | Security |
| LUMEN-B3 | **High** | `src/store/admin_auth.rs` | 16–26 | No rate limiting on `POST /admin/auth/login`. The admin password is a single static secret (`LUMEN_ADMIN_PASSWORD`, default `change-me`) and comparison is a plain `!=` string compare (no constant-time). Unthrottled remote brute-force is possible; the only guard is `server.rs` logging a warning when the default is in use. | Security |
| LUMEN-B4 | **Medium** | `src/routes/session.rs` | 23–28 | When `LUMEN_OUTEMAIL_API_KEY` is unset (or outemail unreachable), `start_email_login` returns the static dev code (`LUMEN_DEV_LOGIN_CODE`, default `000000`) in the response for **any** email — a full account-takeover hole in any deployment that forgets to configure outemail. README documents this as dev behavior, but nothing hard-gates it out of production. | Security |
| LUMEN-B5 | **Medium** | `src/routes/audit.rs` | 10–33 | `audit_request` middleware wraps the whole `/api` tree (including `/health`, `/openapi.json`, public flag routes) and inserts one `admin_access_audit` row per request. Every authenticated request also triggers an extra token→user lookup. 2 extra DB ops per request; collection has no TTL index (see B10). | Performance |
| LUMEN-B6 | **Medium** | `src/routes/audit.rs` | 49–63 | `request_ip` trusts the client-supplied `x-forwarded-for` header verbatim (first hop). Unless the reverse proxy strips/overwrites it, audit IPs are trivially spoofable and the audit log is not forensically reliable. | Security |
| LUMEN-B7 | **Medium** | `src/routes/session.rs` | 19–51 | `start_email_login` is unthrottled. Combined with (a) `login_requests` having only a **plain** (non-TTL) index on `expiresAt` (B10), and (b) never-verified rows never being deleted, an attacker can spam it to (i) grow the collection unboundedly and (ii) email-bomb any victim when outemail is configured. | Security |
| LUMEN-B8 | **Medium** | `src/store/sync.rs` | 55–90 | `changes_since` returns **every** change since the cursor with no page limit and sync_changes are never pruned. A rarely-syncing device gets a growing unbounded response, and the collection grows forever (no TTL). | Performance |
| LUMEN-B9 | **Medium** | `src/store/telemetry.rs` | 99–122 | Telemetry rate limit is a `count_documents` before insert (TOCTOU race allows a few extra inserts) and is the **only** rate limit in the API. Vision-frame uploads (`store/privileged_control.rs`) have no per-session/per-user limit despite 2 MB frames, and face-analysis frames (`store/face_analysis.rs`) have none either. | Security |
| LUMEN-B10 | **Medium** | `src/store/mod.rs` | 136–371 | Only `api_nonces` gets a TTL index. Every append-heavy collection — `sessions`, `login_requests`, `admin_sessions`, `sync_changes`, `telemetry_uploads`, `face_analysis_frames`, `vision_stream_sessions`, `vision_stream_frames`, `lifecycle_events`, `backups`, `admin_access_audit`, `admin_actions`, `admin_crash_reports`, `admin_api_metrics`, `admin_sync_metrics`, `admin_telemetry` — grows without bound. `admin_access_audit` is the worst (one row per request, B5). | Stability |
| LUMEN-B11 | **Medium** | `src/store/mod.rs` | 63–134 | MongoDB client uses default timeouts (server-selection ~30 s) and no retry. If Mongo is unreachable at runtime, every request blocks up to ~30 s and piling-up requests exhaust the runtime; there is no fail-fast or circuit-breaker. | Stability |
| LUMEN-B12 | **Medium** | `src/server.rs` | 91 | `axum::serve(listener, app)` has no `with_graceful_shutdown`. On SIGTERM the process is killed abruptly; in-flight requests are dropped and uncommitted Mongo writes can be lost. `tokio`'s `signal` feature is enabled but unused. | Stability |
| LUMEN-B13 | **Medium** | `src/store/admin_actions.rs` | 177–179 | `force_update` defaults to `true` when an admin omits it. A malformed "force-update" action can force every client to update. | Stability |
| LUMEN-B14 | **Medium** | `src/store/entitlements.rs` | 12–55 | `list_entitlements` / `user_has_tier_at_least` load **all** of a user's entitlement rows with no `limit`; and each `verify_google_purchase` call inserts a new row, so `pending` spam rows accumulate (see B1). | Performance |
| LUMEN-B15 | **Low** | `src/server.rs` | 44–50 | `CorsLayer` is `allow_origin(Any).allow_methods(Any).allow_headers(Any)` — fully open CORS. Mitigated because tokens are bearer-only and the dashboard keeps them in memory, but any web origin can send authenticated requests if a token leaks. | Security |
| LUMEN-B16 | **Low** | `src/routes/face_analysis.rs` | 58 | `MAX_FRAME_BASE64_LENGTH = 2_800_000` (≈2.8 MB) but axum's default body limit is 2 MB and no `DefaultBodyLimit` is set. Base64 payloads near the allowed max are rejected with 413 before the code's own check runs — the limit is effectively ~1.9 MB, and the code/DB schema advertise the larger value. | Stability |
| LUMEN-B17 | **Low** | `src/routes/platform.rs` | 415–420 | `rollout_bucket` uses `std::collections::hash_map::DefaultHasher`, whose algorithm is not guaranteed stable across Rust releases. Upgrading the toolchain can silently re-bucket users into different rollout cohorts. | Stability |
| LUMEN-B18 | **Low** | `src/config.rs` | 56 | `LUMEN_ACCESS_TOKEN_TTL_SECONDS` is clamped with `.min(7_200)` (max 2 h) while `backend/README.md:34` documents `604800` (7 days). Documentation and code disagree; operators following the README get a silently different TTL. | Maintainability |
| LUMEN-B19 | **Low** | `src/outemail.rs` | 71 | `response.text()` reads the entire outemail provider body with no size cap — a misbehaving/hijacked outemail endpoint can balloon memory. | Performance |
| LUMEN-B20 | **Low** | `.github/workflows/build.yml` | 455–497 | Backend CI runs only `cargo fmt --check` and `cargo test --all-targets`. No `cargo clippy`, no `cargo build --release` (the CLAUDE.md claims a release build runs in CI; it only happens in the Docker build), so release-only warnings/errors are caught only at image build. | Release |
| LUMEN-B21 | **Low** | `Dockerfile` | 20–29 | Runtime image is `debian:bookworm-slim` with no explicit `ca-certificates` install; outbound TLS (outemail `rustls`, remote TLS MongoDB) may fail on images lacking the bundle. Also `FROM rust:1-bookworm` is a floating tag (not version-pinned), reducing reproducibility. | Release |
| LUMEN-B22 | **Low** | `src/routes/security.rs` | 46–69 | Signing header values (timestamp/nonce/signature) are not length-limited before being parsed/stored; a client can send up-to-HTTP-limit nonce values that are persisted (until TTL). Minor DoS surface. | Security |
| LUMEN-B23 | **Low** | `src/store/auth.rs` | 253–255 | `is_duplicate_key` detects duplicate-key via `error.to_string().contains("E11000")` string matching — brittle across driver versions; the driver exposes typed error kinds (`error.kind`) that should be used. | Maintainability |
| LUMEN-B24 | **Low** | `src/store/privileged_control.rs` | 285–328 / 330–429 | Vision heartbeat and frame upload do read-modify-write (`find_one` → `replace_one`) on the session doc; concurrent frame uploads can lose `frames_uploaded`/`frames_captured` updates (lost update race). | Stability |
| LUMEN-B25 | **Low** | `src/store/admin_actions.rs` | 26–40 | Admin action is applied **before** the audit row is inserted, with no transaction/rollback. If the audit insert fails, an irreversible action (e.g. grant/revoke/force-update) was performed without a log record. | Stability |
| LUMEN-B26 | **Low** | `src/store/backups.rs` / `src/store/sync.rs` | 16–24 / 32–53 | Backup and sync-push payloads have no explicit size/shape cap beyond axum's 2 MB body limit; `BackupRecord.backup` and `SyncChange.payload` are arbitrary `Value`s stored verbatim (no schema validation). | Security |
| LUMEN-B27 | **Low** | `src/error.rs` | 9–30 | `BadRequest`/`Conflict` echo caller-supplied strings verbatim to clients (acceptable), but `Internal` correctly masks DB errors. MongoDB errors are logged in full at `error` level via `database_error` (`store/mod.rs:374–377`) — ensure Mongo server config never embeds credentials in query errors. | Security |
| LUMEN-B28 | **Info** | `src/models/privileged_control.rs` | 170–177 | `SurfaceVisionFrameUploadResponse` is defined but never used (surface frames return `VisionFrameUploadResponse`) — dead code. | Maintainability |
| LUMEN-B29 | **Info** | `src/auth_context.rs` / `src/admin_context.rs` | 92–103 / 28–39 | Identical `bearer_token` helper duplicated; `EmptyFallback` trait duplicated in `platform.rs` (441–453) and `admin_actions.rs` (522–534); `now_millis`/`elapsed_ms` duplicated in 5 files. Consolidate into shared helpers. | Maintainability |
| LUMEN-B30 | **Info** | `src/store/auth.rs` | 257–266 | `normalize_email` accepts any string containing `@` ≤ 254 chars (e.g. `a@b`) — no domain/TLD validation; weak deliverability and mild abuse surface. | Security |
| LUMEN-B31 | **Info** | `src/store/admin_audit.rs` | 21 | `geo` is hardcoded to `"unknown"` and never populated — vestigial field. | Maintainability |
| LUMEN-B32 | **Info** | `src/routes/platform.rs` | 29–83 | `/openapi.json` and `/releases/check` are public and enumerate the full API surface, security schemes, and release metadata (APK URLs, SHA-256, rollout). Intentional per README, but should be acknowledged as public info exposure. | Security |
| LUMEN-B33 | **Info** | `src/store/mod.rs` | 430–446 | `create_index` runs serially at startup for ~35 indexes each boot; on a large existing collection this can slow startup (index build takes time). Prefer `create_index` with `commit_quorum` or bake indexes into deployment. | Performance |
| LUMEN-B34 | **Info** | `src/routes/security.rs` | 114–129 | Timestamp skew check uses `(now - timestamp).abs()`; reachable `i64` inputs cannot overflow to a small value, so no bypass, but using `abs_diff()` would be more robust/clear. | Maintainability |
| LUMEN-B35 | **Info** | `backend/README.md` | 34 | README env block omits several supported vars and lists `LUMEN_ACCESS_TOKEN_TTL_SECONDS=604800` which code clamps to 7200 (see B18). | Maintainability |

---

## Detailed findings (context)

### Security

**B1 — Purchase verification is a stub with a fail-closed trap (High).**
`store/entitlements.rs:verify_google_purchase` never contacts the Google Play Developer API (no client ID, no service account, no `androidpublisher` call anywhere in the tree). When `LUMEN_ACCEPT_UNVERIFIED_PURCHASES=false` (the README-blessed production default), every submitted purchase is written with `status: "pending"` and `tier: <derived from product_id>`; the entitlement response returns `tier: "FREE"`. `resolve_active_tier` (line 116) only counts `status == "active"`, and **no code path ever flips `pending` → `active`**, so the purchase cannot ever take effect. When the operator flips the flag to `true` to make purchases work, the same endpoint stamps `status: "active"` and returns the derived tier — any authenticated user can self-grant `PLUS`/`TEAM` with a fabricated token. Net effect: the feature is either broken or a privilege-escalation hole; there is no middle path.

**B2 — Device-security gate is client-controlled (High).**
`auth_context.rs:require_device_security` checks `evidence.status == "clean"`, `completed`, `rooted==false`, `suspicious==false`, `hardwareIntegrityOk != false`, `teeAttestationOk != false`, and `observedAt` freshness — all fields a client sends via `POST /devices/register`. The server's own sanitizer (`store/devices.rs:sanitize_security_evidence`) stamps `"verified": false`, but the gate never checks `verified`. There is no server-side attestation, no signature verification against the Android release key, and no integration with the app's native security layer. Every gate (`sync/changes`, `sync/push`, `backups`, `purchases/google/verify`, `device-control/*`) is trivially satisfiable by any authenticated user.

**B3 — Admin login brute-force (High).**
`store/admin_auth.rs:create_admin_session` compares `username != config.admin_username || password != config.admin_password`. No attempt throttling, lockout, or IP-based limit exists anywhere for this route. The default password `change-me` is only warned about in logs. The comparison is not constant-time (minor, but combined with no rate limit the practical risk is brute force). Admin session tokens are stored in Mongo with a plain (non-TTL) index (B10).

**B4 — Dev login code as production auth bypass (Medium).**
`routes/session.rs:start_email` returns `dev_code` whenever `state.outemail` is `None`. `OutEmailClient::from_config` returns `None` unless both `LUMEN_OUTEMAIL_BASE_URL` and `LUMEN_OUTEMAIL_API_KEY` are set. Any deployment missing the API key returns the static code (default `000000`) in the response body for any email address, which can then be exchanged for tokens at `/v1/auth/email/verify`. This is documented dev behavior but there is no guard tying it to an explicit non-production mode.

**B5/B6 — Audit middleware cost and spoofable IP (Medium).**
`routes/audit.rs` runs for every `/api` request (and legacy `/v1`). It performs a token→user lookup for every authenticated request (an extra `sessions.find` + `users.find`) and then one `admin_access_audit` insert per request. Health-checks hitting `/api/health` also write audit rows. `request_ip` takes the first entry of `x-forwarded-for` directly from client headers — spoofable unless the trusted proxy overwrites it. Consider sampling, a TTL on the collection, and a trusted-proxy header policy.

**B7 — Login-start spam / email bombing (Medium).**
`start_email_login` creates a `login_requests` row with no rate limit. Rows are deleted only by `verify_email_login`'s `find_one_and_delete`; abandoned requests persist forever (index is non-TTL). An attacker can flood the collection and, when outemail is configured, mail-bomb any target address.

**B9/B26 — Rate limits and payload caps are inconsistent (Medium).**
The only rate limit in the API is telemetry's 60/hour/user `count_documents`. Vision frames (2 MB base64), face-analysis frames (2.8 MB base64), sync pushes (arbitrary change count), and backups (arbitrary `Value`) are all unlimited per user/session.

### Stability

**B10 — Unbounded collection growth (Medium).**
Of ~35 indexes created in `ensure_indexes`, only `api_nonces` uses `expire_after`. `login_requests.expiresAt`, `sessions.expiresAt`, `admin_sessions.expiresAt`, plus every `receivedAt`/`sampledAt`/`at`/`uploadedAt`/`cursor` field across the append-heavy collections are plain indexes. Mongo TTL monitor will never purge them. `admin_access_audit` is the fastest-growing (one row/request). This is the single largest long-term stability risk.

**B12 — No graceful shutdown (Medium).**
`tokio` lists `signal` in features and `main.rs` is a `#[tokio::main]`, but `axum::serve` is called without `with_graceful_shutdown`. Container orchestrators will SIGTERM the process mid-flight.

**B13 — `force_update` defaults true (Medium).**
`store/admin_actions.rs` line 178: `force_update: payload.get("forceUpdate").and_then(Value::as_bool).unwrap_or(true)`. Omitting the field in a release-sync action forces an update on all clients.

**B16 — Body limit vs advertised frame max mismatch (Low).**
`MAX_FRAME_BASE64_LENGTH` (2.8 MB) cannot be reached because axum's default body limit is 2 MB and no `DefaultBodyLimit` layer exists. The face/vision frame upload of a payload near the code limit fails with 413. Either raise the body limit or lower the constant so the code matches reality.

### Performance

- **B5** — per-request audit insert + token lookups.
- **B8** — `changes_since` unbounded; consider a `limit` with pagination or a cap on retained changes per user.
- **B14** — full entitlement scans per user on every `/entitlements` and every `require_plus_entitlement` call (the latter runs on every sync/backup request). A user with many `pending` rows (B1 spam) degrades these calls. Consider a `$group`/`$max` or maintaining a denormalized active-tier field on the user.
- **B33** — 35 serial `create_index` calls at every startup; consider idempotent creation only when missing, or run them out-of-band.

### Testing

Only 7 tests exist: 3 in `auth_context.rs` (pure logic on the Plus guard — two of which are *source-text string assertions* on `include_str!`), 2 in `routes/security.rs` (pure `is_release_check_path`), 2 in `store/entitlements.rs` (pure tier resolution). There is no test for:
- HMAC signature generation/verification, timestamp skew, nonce replay;
- any route handler (no axum `Router` integration tests, no `tower::ServiceExt` oneshot);
- `store` logic against a real/mocked Mongo (no `mockall`, no `mongodb-memory-server`);
- auth flows, session refresh, purchase recording, sync cursors, admin actions.

`Cargo.toml` has no `[dev-dependencies]`; the two test files that need tokio rely on the main `tokio` dependency. Coverage is effectively confined to pure functions.

### Maintainability

- Code is consistently formatted, typed, and layered (routes → store → models); error mapping is centralized; production code contains zero `unwrap`/`expect`.
- Duplication: `bearer_token` (auth_context.rs + admin_context.rs), `EmptyFallback` (platform.rs + admin_actions.rs), `now_millis`/`elapsed_ms` in 5 files, `tier_rank` (two incompatible definitions: `store/entitlements.rs:155` ranks PRO=1, PLUS=2, TEAM=3; `store/admin_dashboard.rs:530` ranks PRO=2, PLUS=3, TEAM=4, DEVELOPER=5 — inconsistent semantics between the two).
- Dead code: `SurfaceVisionFrameUploadResponse` (B28), `AdminAccessAuditRecord.geo` (B31).
- Config/README drift: `LUMEN_ACCESS_TOKEN_TTL_SECONDS` (B18); README omits several env vars.

### Design

- The store handles are `pub(crate)` and all DB access goes through `AppStore` methods — good encapsulation.
- `models::*` wildcard re-exports are convenient but make unused-import churn easy.
- `api.rs` uses `#[path = "..."]` module declarations to keep route modules in `routes/`; works but is an unusual pattern to maintainers.
- The two most consequential design gaps are the purchase pipeline (B1) and the spoofable device-security attestation (B2); both are trust-model issues rather than coding slips.

### Release

- `Dockerfile` is multi-stage and functional; admin `dist` is copied to both `/app/backend/admin/dist` and `/app/backend/admin` (the second copy is redundant).
- CI backend job (build.yml:455–497) runs fmt + tests only; no clippy and no release build. The `cargo build --release` step documented in `CLAUDE.md` is not present in the workflow — it only occurs in the Docker build.
- `rust:1-bookworm` base tag is floating; `debian:bookworm-slim` runtime does not explicitly install `ca-certificates` (required for rustls-based outbound TLS: outemail, TLS MongoDB).
- Default env values (`change-me`, `000000`, `project-lumen-local-request-signing-key`) are safe only for local dev; startup warnings exist but nothing fails the process if a default secret is in use.

---

## Priority recommendations

1. **High — Purchases:** implement real Play Developer API verification (or remove the "verified" framing); add a worker that re-checks `pending` entitlements; require a server-issued `purchaseToken`/order-id idempotency check so the same purchase cannot be re-submitted to mint rows or tiers.
2. **High — Device security:** stop trusting client evidence; verify a server-computed attestation (HMAC over evidence using the app's compiled secret — the Android `lumen_security` layer already holds such a key), or downgrade these gates to informational checks.
3. **High — Admin auth:** add per-IP + per-account rate limiting/lockout on `/admin/auth/login` and `/v1/auth/email/*`; use constant-time comparison for admin password.
4. **Medium — Storage:** convert intended-TTL indexes to real TTL indexes (`expire_after`) on `sessions`, `login_requests`, `admin_sessions`, and add retention policies (TTL or capped) to `admin_access_audit`, `telemetry_uploads`, `sync_changes`, `backups`, `face_analysis_frames`, `vision_stream_*`, `lifecycle_events`.
5. **Medium — Runtime:** add `with_graceful_shutdown`, Mongo timeouts/retry, and a `DefaultBodyLimit` (raise for frame uploads and lower the frame constant to match).
6. **Medium — Login flow:** gate the dev-code path behind an explicit dev-mode flag so production can never serve static codes.
7. **Low — Quality gates:** add `cargo clippy -- -D warnings` and a release build to CI; add `[dev-dependencies]` + integration tests for the HMAC middleware and store logic against a mock/in-memory Mongo.
