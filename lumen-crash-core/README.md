# Lumen Crash SDK Core

Capture-only artifact without Compose UI.

| Item | Value |
|---|---|
| Module | `:lumen-crash-core` |
| Maven | `com.chloemlla.lumen:lumen-crash-core` |
| Includes | install/record/store/breadcrumbs/author protection/paste uploader |
| Excludes | Compose crash screen / file-share UI |

Prefer the bundle (`com.chloemlla.lumen:lumen-crash`) when you need the crash report UI.
Use core for Flutter bridges or hosts that only need capture + persistence.
