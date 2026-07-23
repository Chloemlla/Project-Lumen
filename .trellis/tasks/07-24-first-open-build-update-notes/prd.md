# First-open build update notes + OSS + immersive tutorial gates

## Goal

Define and implement a launch-gate stack so that:
1. First install shows the open-source notice, then the immersive product tutorial (onboarding).
2. Every time the user opens a **new installed build** (identified by GitHub commit hash + build time), the app shows a dedicated **本次更新说明 / What's new in this build** full-screen page once.
3. Contracts live in Trellis frontend specs so future sessions keep gate order, identity keys, content pipeline, and reopen paths consistent.

## Decisions (locked)

* **Build identity**: `BuildMetadata` from `BuildConfig` — `VERSION_NAME`, `VERSION_CODE`, `BUILD_TIME_UTC_MILLIS`, `COMMIT_HASH`, `SHORT_HASH` (already injected by `.github/workflows/build.yml` + `app/build.gradle.kts`).
* **Acknowledged build key**: persist `lastAcknowledgedCommitHash` + `lastAcknowledgedBuildTimeUtcMillis` in `SecureCredentialStore` (not versionName alone — same version can rebuild).
* **Gate order (highest first)**:
  1. Open-source notice (`ossNoticeCompletedAt`) — first install only; About can reopen.
  2. Immersive product tutorial / onboarding (`onboardingCompletedAt`) — first install only.
  3. Per-build update notes page — when current `(COMMIT_HASH, BUILD_TIME_UTC_MILLIS)` ≠ last acknowledged pair.
* **Fresh install**: after OSS + onboarding complete, still show update notes for the current build once (welcome-to-this-build), then mark acknowledged.
* **Existing users upgrading**: skip OSS + onboarding if already completed; show update notes when build identity changes.
* **Presentation**: full-screen immersive scrollable page (same family as OSS notice), not dialog-only. Continue dismisses and persists acknowledgment.
* **Content source**: CI generates a per-build notes artifact from the commit being built (subject + body / conventional commit summary) and embeds it into the APK; runtime falls back to version + short hash + build time if notes missing.
* **Reopen**: About (and optionally Developer Debug) can reopen "What's new in this build" without clearing the acknowledged key.
* **i18n**: EN (`values`) + ZH (`values-zh`) chrome strings; commit notes may be language of the commit message (document this).
* **Out of scope for this contract**: replacing `UpdateDialog` remote release notes (pre-download); monetization; local Gradle builds.

## Requirements

1. Trellis code-spec documents gate order, identity, persistence keys, UI surfaces, CI notes pipeline, validation matrix, and wrong/correct examples.
2. Spec indexes frontend layer so `before-dev` / implement sessions load the guide.
3. Implementation (follow-on or same task if time allows) must match the spec:
   * Secure store fields for acknowledged build
   * ViewModel gate refresh
   * `ProjectLumenBuildUpdateNotesScreen` (or equivalent)
   * CI step writing notes asset / BuildConfig-safe embedding
   * About reopen entry

## Acceptance Criteria

* [x] Frontend Trellis spec `android-first-run-and-update-notes.md` filled with scenario template
* [x] Frontend `index.md` lists the new guide
* [x] PRD locks gate order and build identity
* [ ] Code implements gates + notes page (may be subsequent implement step)
* [ ] Commit + push after changes (no local Gradle)

## Technical Approach

* Spec under `.trellis/spec/frontend/android-first-run-and-update-notes.md`
* Align with existing:
  * `ProjectLumenOpenSourceNoticeScreen` / `ossNoticeCompletedAt`
  * `ProjectLumenOnboardingScreen` / `onboardingCompletedAt`
  * `BuildMetadata` / `UpdateChecker` (remote updates remain separate)
  * `SecureCredentialStore.installProfile()`
* CI: after `Read app version` in `build.yml`, generate notes from `git show -s --format=... $COMMIT_HASH` into `app/src/main/assets/build-update-notes.json` (or similar)

## Out of Scope

* Full git history browser inside the app
* Network-required notes fetch for already-installed build
* Changing remote auto-update download UX
* Local device Gradle builds / tests

## Related

* Task: `.trellis/tasks/07-17-first-run-oss-notice-credits` (OSS notice already shipped)
* Task: `.trellis/tasks/07-24-first-open-build-update-notes` (this task)