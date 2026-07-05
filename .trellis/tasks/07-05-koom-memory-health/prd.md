# KOOM-Inspired Memory Health Monitoring

## Goal

Enhance Project Lumen with a KOOM-inspired Android memory diagnostics surface that helps developers observe OOM risk without adding a native leak-monitoring SDK in this task.

## Requirements

* Add a lightweight memory health monitor using Android platform APIs.
* Capture Java heap, native heap, graphics memory, total PSS, available system memory, low-memory threshold, system low-memory flag, and last trim level.
* Refresh memory health while developer diagnostics are active.
* Update the monitor when the app receives `onTrimMemory`.
* Make the existing low-memory simulation update the same memory health state.
* Display the memory health snapshot in the existing Developer advanced debug screen.
* Keep the implementation local-first and avoid diagnostic upload behavior changes.

## Acceptance Criteria

* [ ] Developer diagnostics show memory pressure, heap/native/graphics/PSS values, system availability, threshold, last sample, and last trim level.
* [ ] Starting developer diagnostics samples memory periodically.
* [ ] `Application.onTrimMemory` records trim pressure in the memory snapshot.
* [ ] Simulate low memory updates the memory snapshot and existing low-memory timestamp.
* [ ] No new native dependency or Room migration is required for this MVP.

## Definition of Done

* Code follows existing Android/Kotlin patterns.
* Lint/typecheck/test validation is deferred to GitHub workflow per repository instruction.
* Documentation/research notes are recorded in this task directory.
* Changes are committed and pushed after implementation.

## Technical Approach

Create a small `core.debug.MemoryHealthMonitor` backed by `StateFlow`, sampled from `DeveloperDebugOverlayService` and trim callbacks. Expose that state through `ProjectLumenViewModel` and render it in `ProjectLumenDeveloperDebugScreen`.

## Decision (ADR-lite)

**Context**: KOOM includes Java/native/thread leak modules with native packaging implications. Project Lumen already has native code and strict workflow-only build validation.

**Decision**: Implement platform-API memory health monitoring now, defer direct KOOM SDK integration.

**Consequences**: The app gains useful OOM-risk visibility with low implementation risk. It does not yet produce KOOM heap/native leak reports.

## Out of Scope

* Direct dependency on `com.kuaishou.koom:*`.
* Heap dump generation.
* Native allocation stack tracing.
* Thread leak detection.
* Remote upload of memory diagnostics.

## Research References

* [`research/koom-memory-monitoring.md`](research/koom-memory-monitoring.md) - KOOM concepts and MVP mapping for this repository.

## Technical Notes

* Existing low-memory debug path: `app/src/main/java/com/projectlumen/app/core/debug/DeveloperDebugOverlayService.kt`.
* Existing developer UI: `app/src/main/java/com/projectlumen/app/app/ProjectLumenDeveloperDebugScreen.kt`.
* Existing application trim callback location: `app/src/main/java/com/projectlumen/app/ProjectLumenApplication.kt`.
* Existing runtime persistence remains unchanged; memory health is ephemeral process diagnostics.
