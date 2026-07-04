# Shizuku App Network Controls

## Goal

Add a developer-advanced Android tool on the default `main` branch that uses the existing Shizuku shell bridge to disable network access for installed system and user applications, persists the packages Project Lumen disabled, and lets the user restore network access later.

## What I already know

- The user wants this advanced feature on the default branch, which is `main`.
- The app already has a developer advanced debug screen.
- The app already integrates Shizuku through `ShizukuCapabilityManager` and `ShizukuShellUserService`.
- Shizuku shell commands are already used for native display/brightness operations.
- Existing Shizuku app inventory only lists third-party user apps for diagnostics; this feature must include system and user apps.
- The app uses Room (`AppDatabase` v17) and repositories for persistent local state.
- Actual build/test commands must run only in GitHub Actions per repository policy.

## Assumptions

- "Disable networking" means using Android network policy shell commands available through Shizuku shell privileges, not a VPN firewall and not root iptables.
- Project Lumen should persist only app packages it attempted to disable, plus command status metadata, so it can provide a reversible management list.
- The user can manually restore packages from the developer advanced page.
- If the device/ROM does not support the stronger policy, the UI should surface the error instead of pretending the app is blocked.

## Requirements

- Add the feature to the default `main` branch.
- Add a Developer advanced page section for Shizuku app network controls.
- List installed user apps and system apps with package name, UID when available, and app type.
- Let the user apply Android netpolicy network restriction for an app through Shizuku.
- Also attempt a Shizuku AppOps INTERNET guard to reduce cross-process / system-service delegated networking when the device ROM exposes that operation.
- Persist disabled package records locally for management and recovery.
- Let the user restore network access for persisted disabled apps.
- Refresh state from Shizuku and local persistence without requiring a local build/test run.
- Keep Shizuku unavailable / unauthorized states explicit and non-crashing.

## Acceptance Criteria

- [ ] Developer advanced page shows a Shizuku app network controls section.
- [ ] The section can refresh installed user and system apps.
- [ ] Selecting a listed app attempts to apply Android netpolicy restriction through Shizuku.
- [ ] Restriction attempts also try the delegated-network AppOps guard and persist whether it was supported.
- [ ] Successful or failed attempts are persisted with package name, UID, type, timestamp, and last error/status.
- [ ] Persisted disabled records appear in a management list.
- [ ] A disabled record can be restored, attempting to re-enable the app network policy and updating local state.
- [ ] Shizuku unavailable or unauthorized state shows actionable status and does not crash.
- [ ] Source changes are committed and pushed to `main`.

## Definition of Done

- Source changes committed and pushed to `origin/main`.
- Static diff/whitespace checks completed locally.
- Build, lint, type-check, and tests are not run locally; GitHub Actions owns actual verification.
- Specs/docs updated if a new Android/Shizuku contract is introduced.

## Out of Scope

- VPN-based firewall implementation.
- Root-only iptables/nftables management.
- Automatic background re-application after reboot.
- Non-developer settings surface.
- Backend/cloud sync of network-control state.

## Technical Notes

- Likely UI file: `app/src/main/java/com/projectlumen/app/app/ProjectLumenDeveloperDebugScreen.kt`.
- Likely Shizuku file: `app/src/main/java/com/projectlumen/app/core/shizuku/ShizukuCapabilityManager.kt`.
- Likely state files: Room entity/DAO/repository plus `ProjectLumenViewModel`.
- Need research: exact Android `cmd netpolicy` syntax and fallback behavior across Android versions.

## Research References

- [`research/android-netpolicy-shizuku.md`](research/android-netpolicy-shizuku.md) — AOSP shell interface supports UID-level `restrict-background-blacklist` add/remove via `cmd netpolicy`; AppOps INTERNET is treated as a best-effort delegated-network guard because it is not guaranteed across Android versions/ROMs.

## Decision

Use the existing Shizuku shell bridge to apply and remove `cmd netpolicy add/remove restrict-background-blacklist <uid>`, attempt `cmd appops set <package> android:internet|INTERNET ignore/allow` as a delegated-network guard, persist each Project Lumen initiated package record in Room, and surface command failures in the developer page.
