# Improve UX and remove fake implementations

## Goal

Remove user-facing fake experiences and fake implementations from Project Lumen so ordinary users only see actions that either perform real work, clearly explain their prerequisite, or are hidden until the capability is available.

## What I Already Know

* User asked to optimize UX and remove fake experiences / fake implementations.
* The repository has an Android Compose app, a Rust backend with an admin dashboard, and a Vite-based UI token tuner.
* The current worktree was clean before task creation.
* Broad keyword search found many false positives:
  * `sample` is often legitimate camera / telemetry sampling.
  * `preview` is often legitimate backup import preview, crash report developer preview, or UI token preview.
  * `disabled` is often valid state handling.
* Likely user-facing risk areas are Android client cards and capability/action surfaces that show buttons before prerequisites are satisfied.
* Remote account, cloud sync, backup upload/restore, translation, update checks, template editing, statistics export, and crash report sharing have real code paths.
* Developer-only preview surfaces are intentionally scoped to developer/debug tooling and should not be treated as ordinary-user fake UX.

## Assumptions

* MVP should focus on ordinary Android app UX first, because that is what users see most directly.
* A feature should not be removed just because it has a prerequisite; it should be gated, hidden, or labelled with the prerequisite.
* Local build and test commands must not be run in this environment. Verification must be via code review/static inspection here and CI/GitHub workflow externally.

## Requirements

* Scope MVP to ordinary Android app user UX.
* Identify ordinary-user-facing controls that create a fake experience: no-op actions, unavailable feature buttons, placeholder/demo outcomes, or capability rows that imply availability before prerequisites are met.
* Replace fake controls with one of:
  * A real action wired to existing implementation.
  * A clear prerequisite path if the action is blocked by login, permissions, plan tier, or missing data.
  * Removal/hiding of the action when it cannot honestly work.
* Keep legitimate developer/debug previews and true sampling code intact.
* Keep changes narrowly scoped and consistent with existing Compose components and strings.
* Preserve bilingual string coverage for changed Android UI text.

## Acceptance Criteria

* [ ] Ordinary users do not see buttons that imply unavailable premium/family/AI/cloud capabilities are active without prerequisites.
* [ ] Action buttons in the affected cards either perform real work or navigate/configure an actual prerequisite.
* [ ] Empty or blocked states use explicit text instead of silent disabled controls.
* [ ] No developer-only preview or real camera/telemetry sampling code is removed by mistake.
* [ ] Changed strings exist in both default and zh resources.
* [ ] No local build/test command is run; CI/GitHub workflow is the required build/test path.

## Technical Approach

Inspect the Android Compose surfaces first, especially `ProjectLumenEyeCareInsights.kt`, `ProjectLumenMainScreens.kt`, `ProjectLumenSettingsScreen.kt`, and feature-entry classes behind their actions. Prefer reducing misleading affordances over adding new architecture. If a capability already has a real implementation path, wire the button to that path or change the label/status to a prerequisite action.

## Decision (ADR-lite)

**Context**: The user request is broad and could cover docs, admin UI, tools, backend responses, and Android client behavior.

**Decision**: Start with ordinary-user Android UX because it has the highest impact and the clearest fake-experience risk. Treat admin/dev/tool preview surfaces as out of scope unless they leak into ordinary-user flows. User confirmed this MVP scope.

**Consequences**: This keeps the task shippable and avoids deleting legitimate preview/sampling functionality. A later task can audit backend/admin/tooling if needed.

## Out of Scope

* Removing developer/debug preview tools that are clearly labelled and not exposed as production user features.
* Replacing real camera, telemetry, backup preview, or API diagnostics sampling code.
* Large redesign of the app navigation or visual system.
* Running local build/test commands.

## Open Questions

* None.

## Technical Notes

* Read `.trellis/spec/frontend/index.md` and `.trellis/spec/backend/index.md`.
* Initial searches used keywords including `mock`, `placeholder`, `fake`, `dummy`, `stub`, `todo`, `coming soon`, `演示`, `示例`, `未实现`, and `disabled`.
* Relevant Android files inspected:
  * `app/src/main/java/com/projectlumen/app/app/ProjectLumenApp.kt`
  * `app/src/main/java/com/projectlumen/app/app/ProjectLumenMainScreens.kt`
  * `app/src/main/java/com/projectlumen/app/app/ProjectLumenEyeCareInsights.kt`
  * `app/src/main/java/com/projectlumen/app/app/ProjectLumenViewModel.kt`
  * `app/src/main/java/com/projectlumen/app/app/ProjectLumenRemoteFeatureEntry.kt`
  * `app/src/main/java/com/projectlumen/app/app/ProjectLumenRemoteCloudCard.kt`
