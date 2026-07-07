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

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `c0b730d` | (see git log) |

### Testing

- [OK] (Add test results)

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
