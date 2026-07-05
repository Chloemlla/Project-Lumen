# KOOM Memory Monitoring Research

## Source

* <https://github.com/KwaiAppTeam/KOOM>
* Raw README fetched on 2026-07-05.

## Relevant KOOM Concepts

KOOM describes itself as a high-performance Android online memory monitoring solution for OOM governance. Its documented modules focus on:

* Java heap leak monitoring, including child-process heap dumping to reduce app freeze time.
* Native heap leak monitoring with allocation stack reporting.
* Thread leak monitoring.
* Native library packaging considerations around `libc++_shared.so` versus static STL artifacts.

## Constraints In This Repository

* Project Lumen already has Android Kotlin, Compose, Room/MMKV runtime state, foreground services, crash breadcrumbs, and developer diagnostics.
* The app already has native code and explicitly manages `libc++_shared.so` packaging.
* Build and test commands must run only through GitHub workflows, so adding a native external dependency without CI feedback is high risk.
* Existing developer diagnostics already include low-memory simulation through `DeveloperDebugOverlayService`.

## Recommendation

For this task, implement a KOOM-inspired first step instead of adding the KOOM SDK directly:

* Add a lightweight in-process memory health monitor that samples Java heap, native heap, graphics PSS, total PSS, and system memory pressure through Android platform APIs.
* Record `onTrimMemory` pressure level and last sample time.
* Surface the data in the existing developer diagnostics screen.
* Hook low-memory simulation into the same monitor so developers can verify the UI path.

This gives the project immediate memory observability with a small blast radius. A future task can evaluate direct `koom-java-leak`, `koom-native-leak`, or `koom-thread-leak` integration once CI can validate native packaging and startup behavior.
