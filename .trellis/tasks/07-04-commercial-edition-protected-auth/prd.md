# Commercial Edition Protected Auth

## Goal

Create a `commercial-edition` branch implementation that completes the TODO1 remote integration landing with Commercial Edition entitlement checks and C++ native client protection. Cloud sync and cloud backup must require authenticated Plus-or-higher entitlement on both client UX and backend API boundaries, and remote client requests must depend on the native `lumen_security` bridge in release builds.

## What I already know

- User requested Commercial Edition git branch implementation for `docs/todo1` protected auth.
- `docs/TODO1_REMOTE_INTEGRATION_LANDING.md` says account, sync, entitlements, backups, telemetry, security, remote config, and release APIs are already connected.
- `docs/NEXT_STEPS_FEATURE_RECOMMENDATIONS.md` states cloud sync should be Plus-only and must require account and privacy/commercial authorization.
- Android already has `PlanTier` and `PremiumFeature.CLOUD_SYNC`.
- `LocalEntitlementChecker` already maps `CLOUD_SYNC` to `PlanTier.PLUS`.
- Backend `/sync/*` and `/backups/*` currently require a bearer user session but do not enforce Plus entitlement.
- Android already loads `lumen_security` through `NativeSecurityBridge`; `AppIntegrityGuard` enforces it on startup and request signing reads the native signing secret.

## Assumptions

- "Commercial Edition" means a separate git branch named `commercial-edition` based on current `main`.
- Plus or Team entitlement unlocks cloud sync and cloud backup; Free and Pro remain blocked.
- Email login, `/me`, entitlement refresh, purchase verification, feature flag fetch, and health checks remain available so users can sign in and restore/obtain entitlements.
- Actual build/test commands must run only in GitHub Actions per repository policy.

## Requirements

- Create and work on branch `commercial-edition`.
- Client remote cloud card must show the Commercial Edition / Plus requirement and disable sync, upload backup, and restore backup until `CLOUD_SYNC` is allowed.
- Client remote operations must enforce `CLOUD_SYNC` in the ViewModel/feature-entry layer, not only in UI.
- Backend `/api/v1/sync/changes`, `/api/v1/sync/push`, `/api/v1/backups`, and `/api/v1/backups/latest` must require authenticated Plus-or-higher active entitlement.
- Backend forbidden responses should use existing `ApiError::forbidden_reason` style with stable reason code.
- Update TODO1/commercial documentation to record protected auth behavior.
- TODO1 client protection must use C++ native code: release request signing fails closed if the native bridge is unavailable, and device registration reports native protection state.

## Acceptance Criteria

- [ ] Branch `commercial-edition` exists and is pushed.
- [ ] Free/Pro local tier cannot trigger client cloud sync/backup operations.
- [ ] Plus/Team local tier can trigger client cloud sync/backup operations when signed in.
- [ ] Backend sync and backup endpoints return 403 for authenticated users without active Plus/Team entitlement.
- [ ] Backend sync and backup endpoints continue to work for active Plus/Team users.
- [ ] Docs mention Commercial Edition protected auth and Plus-gated cloud features.
- [ ] Docs mention C++ native client protection for TODO1 remote API requests.

## Definition of Done

- Source changes committed and pushed.
- Static diff/whitespace checks completed locally.
- Build, lint, type-check, and tests are not run locally; GitHub Actions owns actual verification.
- Spec/docs updated if new auth contracts are introduced.

## Out of Scope

- Real Google Play Billing production verification.
- New purchase UI or full Paywall screen.
- Team/Enterprise organization management.
- Local build/test execution.

## Technical Notes

- Likely client files: `ProjectLumenRemoteFeatureEntry.kt`, `ProjectLumenRemoteCloudCard.kt`, `ProjectLumenSettingsScreen.kt`, native security bridge/signing files, strings.
- Likely backend files: `auth_context.rs`, `routes/sync.rs`, `routes/backups.rs`, `store/entitlements.rs`, backend security specs/docs.
- Existing entitlement rank exists privately in backend store modules; may need a small reusable helper.
