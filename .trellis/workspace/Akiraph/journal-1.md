# Journal - Akiraph (Part 1)

> AI development session journal
> Started: 2026-07-04

---



## Session 1: Fix Android backend certificate pin resilience

**Date**: 2026-07-04
**Task**: Fix Android backend certificate pin resilience
**Branch**: `main`

### Summary

Removed stale default backend certificate pins, gated optional pinning behind explicit workflow build flags, kept HTTPS system trust as the default communication path, added pin policy tests, and documented the deployment contract.

### Main Changes

- Added an AndroidX Baseline Profile consumer to `app` with ProfileInstaller.
- Added a dedicated `baselineprofile` producer module with a startup/home `BaselineProfileRule` journey.
- Wired GitHub Actions to generate the release baseline profile before release APK assembly.
- Added `.trellis/spec/frontend/android-baseline-profiles.md` for the workflow-only generation contract.

### Git Commits

| Hash | Message |
|------|---------|
| `acf82f6` | (see git log) |

### Testing

- [OK] Static diff and whitespace checks passed.
- [OK] Local Gradle build/test/profile generation intentionally not run; repository policy requires GitHub Actions for those commands.
- [WARN] GitHub Actions status could not be queried locally because `gh` returned HTTP 401.

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 2: Remotion Android product animation

**Date**: 2026-07-05
**Task**: Remotion Android product animation
**Branch**: `main`

### Summary

Added a native Android product demo route, a Remotion Chinese Android product animation package, CI rendering workflow, and the frontend spec contract for the animation package.

### Main Changes

- Changed `backend/src/config.rs` so `LUMEN_REQUIRE_REQUEST_SIGNING` defaults to `false`.
- Changed backend deployment workflow to read `LUMEN_REQUIRE_REQUEST_SIGNING` from repository variables and fall back to `false`.
- Updated backend README, CLAUDE.md, and backend deployment security spec to document explicit opt-in request-signature verification.

### Git Commits

| Hash | Message |
|------|---------|
| `c0b730d` | (see git log) |

### Testing

- [OK] Static review only; local build, lint, type-check, and tests were not run because repository policy requires actual verification through GitHub workflows.

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 3: Android baseline profiles

**Date**: 2026-07-07
**Task**: Android baseline profiles
**Branch**: `main`

### Summary

Added AndroidX Baseline Profile consumer and generator module, wired GitHub Actions profile generation, and documented the workflow-only convention.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `4c4e2e8` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 4: Default disable backend request signing

**Date**: 2026-07-08
**Task**: Default disable backend request signing
**Branch**: `main`

### Summary

Defaulted backend request-signature verification off, made deployment opt-in via LUMEN_REQUIRE_REQUEST_SIGNING, and updated docs/specs.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `7867765` | (see git log) |
| `1fd376d` | (see git log) |
| `d92e41b` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 5: Seal UI/UX MVP close-out

**Date**: 2026-07-16
**Task**: Seal UI/UX MVP close-out
**Branch**: `main`

### Summary

Closed Seal surface/settings chrome MVP: accepted dock compact top bar, captured Android Compose surface spec, softened leftover recommended-setup borders, archived task.

### Main Changes

* Softened recommended-setup feedback card and color swatches onto shared soft-surface tokens
* Added `.trellis/spec/frontend/android-compose-surfaces.md` and linked it from the frontend index
* Finalized PRD acceptance criteria with destination-aware top-bar decision
* Archived `07-16-seal-ui-ux-optimize`

### Git Commits

| Hash | Message |
|------|---------|
| `6e50414` | (see git log) |
| `ab2a645` | archive seal-ui-ux-optimize |

### Testing

- [OK] Static review only; local full build/test skipped per AGENTS.md (GitHub workflow owns verification)

### Status

[OK] **Completed**

### Next Steps

- None - task complete
