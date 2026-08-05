# Project Lumen — Supporting Tools, Video Assets & Resources Audit

**Date:** 2026-08-05
**Auditor:** tame-legacy-codebase review agent
**Scope:** `tools/lumen-ui-tuner/`, `remotion/`, `resources/`, `design/`, `baselineprofile/` (+ directly relevant CI workflows and consumers)

---

## Executive Summary

A full static audit of the supporting tooling was performed. Every file in scope was read, plus the CI workflows that build/verify these areas and the in-repo consumers (`app/build.gradle.kts`, `ProjectLumenUiTokens.kt`, `ProjectLumenSharedComponents.kt`).

The tooling is generally well-built: the Remotion animation is cleanly data-driven across 25 typed scenes, the baseline-profile generator is unusually robust (retry loops, logcat capture, process liveness checks), and the UI tuner is a well-componentized single-purpose editor with a CI verification workflow (including a real Android screenshot contract test). The main problems are: (1) a CI workflow that tries to render a **20:50-minute 4K video on every push** and will likely exhaust GitHub Actions limits; (2) a **dead `resources/` tree** (including a 1.1 MB unused icon); (3) a **Vite dev-server fs-allow widening combined with 0.0.0.0 binding**; (4) **unguarded JSON parsing / File System Access API calls** in the tuner; and (5) several **stale docs / dead-code / duplicate-source-of-truth** maintainability issues.

### Dimension Scores (1–10, 10 = best)

| Dimension    | Score | Key drivers |
|--------------|:-----:|-------------|
| Security     | **6** | Tuner dev-server file-exposure (`fs.allow` + `--host 0.0.0.0`); otherwise clean (no network calls in Remotion, local-only file I/O in tuner) |
| Stability    | **6** | Unguarded `JSON.parse`, unhandled File System Access API rejections; baseline profile has strong retry/error handling |
| Performance  | **5** | 4K 20-min render per push; unused 1.1 MB tracked icon; ~14 MB of tracked repo snapshots |
| Testing      | **5** | No tuner unit tests/lint; Remotion has a `validate` step (tsc + composition check); baseline profile is itself a test harness |
| Maintainability | **5** | Duplicated token defaults, dead constants, stale guide, orphaned resources, several code smells |
| Design       | **7** | Good componentization and data-driven architecture; but tuner color controls cannot affect the real app (dead-end) |
| Release      | **6** | Baseline-profile CI integration is solid and fail-closed; Remotion lacks a lockfile and has a risky render job |

---

## Code Size Baseline

| Area | Files (source) | Lines (approx.) | Notes |
|------|:---:|:---:|-------|
| `tools/lumen-ui-tuner/` | 8 JS/JSX | 902 JS/JSX + 549 CSS + 41 config/HTML | React 19 + Vite 5, `package-lock.json` committed (109 pkgs) |
| `remotion/android-product-animation/` | 17 TS/TSX/CSS | ~2,621 | **No `package-lock.json` committed** |
| `resources/` | 7 PNG | — | **Entire tree unreferenced**; 1,241,573 bytes total |
| `design/lumen-ui-tokens.json` | 1 JSON | 47 | Source of truth, consumed by app + tuner |
| `baselineprofile/` | 2 (Gradle + Kotlin) | 231 | Benchmark-macro module, CI-generated |
| **Total** | **35** | **≈ 5,650** | |

---

## Findings Table

| ID | Severity | File | Line | Description | Category |
|----|----------|------|------|-------------|----------|
| TOOLS-001 | Medium | `tools/lumen-ui-tuner/vite.config.js` + `package.json` | 12–14 / 7,9 | `server.fs.allow` widened to whole repo root; dev & preview servers bind `0.0.0.0`. Any LAN client can read arbitrary files under the repo root via Vite `/@fs/` (including the untracked `project_lumen-release.jks` sitting in the working tree). | Security |
| TOOLS-002 | Medium | `tools/lumen-ui-tuner/src/main.jsx` | 60–63 | `JSON.parse(text)` in `loadTokenText` is unguarded; opening a malformed JSON file throws and, with no error boundary, tears down the whole editor UI. | Stability |
| TOOLS-003 | Low | `tools/lumen-ui-tuner/src/main.jsx` | 35–49, 65–89 | `showOpenFilePicker`/`showSaveFilePicker`/`createWritable` are not wrapped in try/catch: user cancellation raises `AbortError`, and a stale/closed file handle makes Save fail with an unhandled rejection. | Stability |
| TOOLS-004 | Medium | `tools/lumen-ui-tuner/src/defaultTokens.js` | 1–47 | Defaults duplicate `design/lumen-ui-tokens.json` field-for-field — two sources of truth. Drift between them changes tuner fallback behavior vs. shipped app tokens. | Maintainability |
| TOOLS-005 | Low | `tools/lumen-ui-tuner/src/components/PhonePreview.jsx` vs `ElementInspector.jsx` | 286–293 / 42–51 | The element-key → token-path mapping for editable preview text is implemented twice (`readPreviewText` and the `textPath` table). | Maintainability |
| TOOLS-006 | Low | `tools/lumen-ui-tuner/package.json` | 10 | No unit tests and no lint config; `validate` is just `vite build`. | Testing |
| TOOLS-007 | Medium | `tools/lumen-ui-tuner/src/tokenSchema.js` (81–98), `src/components/TokenControl.jsx`, `design/lumen-ui-tokens.json` (15–16), `app/.../ProjectLumenSharedComponents.kt` (274–279) | — | Tuner color controls only write `preview.*` tokens; exported JSON keeps `topBar.primaryColor`/`onPrimaryColor` null, and the app's `LumenTopBar` never reads those color tokens at all (it uses `MaterialTheme.colorScheme.surface/onSurface`). Color tuning in the tuner is a dead end and the preview is not faithful to the real app. | Design |
| TOOLS-008 | Info | `tools/lumen-ui-tuner/package.json`, `package-lock.json` | 12–20 | All deps resolve from `registry.npmjs.org` with correct integrity hashes; versions current within their majors, but Vite 5.x is EOL (6/7 current) and ranges are caret-floating. | Release |
| REMO-001 | High | `.github/workflows/remotion-android-product-animation.yml` + `remotion/.../package.json` | 33–42 / 12 | Renders a **20:50, 37,500-frame** video at 1080p and **4K (3840×2160) on every push** touching `remotion/**`. Software x264 4K encoding of 20 min at CRF 18 will likely approach or exceed the default 6 h job timeout; no `timeout-minutes`, no `cancel-in-progress`, two parallel heavy jobs. Recommend moving 4K to `workflow_dispatch`/schedule. | Performance / Release |
| REMO-002 | Medium | `remotion/android-product-animation/` | package.json | **No `package-lock.json` is committed** (`.gitignore` only lists `node_modules/`, `out/`, `dist/`). CI does plain `npm install` with `node-version: latest`, so renders are non-reproducible and can silently change between runs. | Release / Stability |
| REMO-003 | Low | `remotion/android-product-animation/src/data/androidDemoState.ts` | 29, 68 | `minimumDurationInSeconds = 1200` is dead code with a misleading name (the actual duration is 1250 s); `totalDurationInSeconds` hardcodes `/30` while `videoFps` lives in `Root.tsx`. | Maintainability |
| REMO-004 | Low | `remotion/android-product-animation/src/ProductAnimation.tsx` | 53–71 | Magic timing constants (`sceneFrame - 112`, `/ 58`, `/ 110`, `/ 120`, `chapter * 17`) drive intro/exit choreography with no named constants; fragile to scene-duration edits. | Maintainability |
| REMO-005 | Low | `remotion/android-product-animation/src/components/PhoneFrame.tsx` | 38, 75–114 | `scene.signals.find(() => true)` idiom (should be `[0]`); trend labels (`metricTrendLabel`) and row details are rendered into the DOM but hidden via `display: none` (`phone.css` 299–301, 320–329). Per-scene progress values are also duplicated across signals/rows/spotlight (e.g. “75%” appears 4×), so the guide’s own “keep numbers consistent” rule is easy to break. | Maintainability |
| REMO-006 | Info | `docs/REMOTION_ANDROID_PRODUCT_ANIMATION_GUIDE.md` §3 | 47 | Guide recommends a **90–120 s** video; implementation is **20:50 (25 scenes × 50 s)**. Intentional per scene 1’s spotlight copy, but the guide was never updated. | Design |
| RES-001 | Medium | `resources/` (entire tree) | — | The whole `resources/` directory is **unreferenced dead weight**: `resources/icon.png` (1024×1024 RGB, 1,141,686 B, no alpha) and 6 density PNGs. The app ships launcher icons from `app/src/main/res/mipmap-*`; no build file or code references `resources/`. `resources/icon.png` is also a poor icon source (no alpha channel). | Maintainability / Performance |
| DES-001 | Low | `design/lumen-ui-tokens.json` | 1–47 | File is the app/tuner contract but bundles tuner-only fields (`preview.*`, `previewText.*`, `sample*Title`) into the shipped APK asset (app only consumes `topBar` + `page`). No JSON schema validation in the tuner; `schemaVersion` is never checked anywhere. | Maintainability |
| DOC-001 | Medium | `docs/REMOTION_ANDROID_PRODUCT_ANIMATION_GUIDE.md` §12 | 568–569 | References `ProjectLumenProductDemoScreen.kt` and `Destination.PRODUCT_DEMO`, both **deleted** in the latest commit (“删除产品演示页面”). Guide is now stale/misleading. | Maintainability |
| BP-001 | Info | `baselineprofile/build.gradle.kts` | 27–40 | Managed-device DSL uses `managedDevices { allDevices { create<ManagedVirtualDevice>(...) } }` — non-standard shape; CI runs it, so it presumably compiles under AGP 8.13/9, but it should be re-verified on AGP upgrades. | Release |
| BP-002 | Info | `baselineprofile/build.gradle.kts` | 18–19 | `suppressErrors = EMULATOR,LOW-BATTERY` is reasonable for baseline-profile collection but would mask real issues if this module were ever reused for perf benchmarks. | Release |
| BP-003 | Info | `.github/workflows/build.yml` | 163–183 | Baseline profile generation requires release signing secrets and fails closed when absent — intentional and consistent with repo policy; just a note that profile regen is gated on keystore availability. | Release |
| HYG-001 | Info | repo root | — | `Project-Lumen.zip` (596 KB) and `Project-Lumen-flutter-2026.6.28.7z` (13.9 MB) are **tracked** repo snapshots; `janus-project-icon.png` (115 KB) and the dead `resources/icon.png` add more. ≈15.7 MB of tracked binaries for tooling/snapshots. | Maintainability |
| HYG-002 | Info | repo root (untracked) | — | `project_lumen-release.jks` exists in the working tree. It is gitignored (`*.jks`) and untracked today, but it must never be committed or swept into a published zip/artifact. | Security |

---

## Detailed Findings

### 1. Security

**TOOLS-001 — Vite dev server exposes the whole repo (Medium).**
`tools/lumen-ui-tuner/vite.config.js` sets `server.fs.allow: [repositoryRoot]`, which is *required* so the editor can `import sharedTokens from "../../../design/lumen-ui-tokens.json"`. However, the `dev` and `preview` scripts bind `--host 0.0.0.0` (`package.json` lines 7, 9). The combination means any client that can reach the dev server can fetch arbitrary files under the repo root through Vite’s `/@fs/` endpoint. On a developer’s machine this includes the untracked `project_lumen-release.jks`. Mitigation: restrict `host` to `127.0.0.1` by default (keep `0.0.0.0` behind an explicit flag), and/or narrow `fs.allow` to just `design/` plus the tuner dir.

**HYG-002 — Release keystore present in working tree (Info).**
`project_lumen-release.jks` (2,774 B) sits in the repo root. It is covered by `.gitignore` (`*.jks`) and `git ls-files` confirms it is untracked — but it must not leak into `Project-Lumen.zip` or CI artifacts. Verify the committed zip does not contain it.

### 2. Stability

**TOOLS-002 — Unguarded `JSON.parse` (Medium).**
`loadTokenText` (main.jsx:60–63) parses whatever file the user opens. A malformed or truncated JSON throws a `SyntaxError` inside an async handler with no `try/catch`, and the app has no error boundary, so the entire editor unmounts (blank page). Wrap in `try/catch` and surface a status message.

**TOOLS-003 — File System Access API rejections unhandled (Low).**
`openTokenFile`/`saveTokenFile` (main.jsx:35–89) call `showOpenFilePicker`, `showSaveFilePicker`, and `handle.createWritable()` without catch. Cancelling the picker raises `AbortError`; a file closed/replaced after opening makes `createWritable` reject. Both produce unhandled promise rejections and a confusing no-op.

### 3. Performance

**REMO-001 — 4K render on every push (High).**
`androidDemoState.ts` computes `totalDurationInFrames = 25 × 1500 = 37,500` frames. `remotion-android-product-animation.yml` runs a matrix of two jobs on every push touching `remotion/**`:
- 1080p: `--crf=18 --scale=1` → 1920×1080 × 37,500 frames
- 4K: `--crf=18 --scale=2` → 3840×2160 × 37,500 frames (software x264)

A 20-minute 4K software encode on a standard GitHub Actions runner will realistically take hours and can exceed the default 360-minute job timeout, and there is no `timeout-minutes` or `cancel-in-progress: true` to bound cost. Recommendation: keep 1080p on push (or on a manual trigger), make the 4K leg `workflow_dispatch`/schedule-only, and add `timeout-minutes` (e.g. 300) per job.

**RES-001 — Orphaned 1.1 MB icon (Medium).**
`resources/icon.png` is a 1024×1024, 8-bit **RGB (no alpha)** PNG of 1,141,686 bytes, and `resources/android/icon/` holds 6 unused density PNGs (99,887 bytes total). Nothing references `resources/`. Beyond cleanliness, the icon is unusable as an Android launcher source because it lacks an alpha channel. The app’s real icons live in `app/src/main/res/mipmap-*`.

### 4. Testing

- **Tuner:** no unit tests, no ESLint, `validate: vite build` only (TOOLS-006). Positive: the `lumen-ui-tuner.yml` workflow runs a real Android emulator screenshot test (`LumenTopBarScreenshotTest`) that encodes the title-alignment contract — good integration coverage of the actual deliverable.
- **Remotion:** `validate` runs `tsc --noEmit && remotion compositions src/index.ts` — a real correctness gate. Good.
- **Baseline profile:** the generator is itself a test harness with robust cold-start retry (3 attempts), `pidof`/`ps` process liveness checks, logcat capture on failure, and crash-report/onboarding dismissal. This is the best-tested artifact in scope.

### 5. Maintainability

- **TOOLS-004:** `defaultTokens.js` duplicates `design/lumen-ui-tokens.json` exactly. Keep one source of truth (e.g. always import the JSON, or generate defaults from it).
- **DOC-001:** guide §12 cites the deleted product-demo screen/route. Update or remove the “已落地实现路径” section.
- **REMO-003/004/005:** dead `minimumDurationInSeconds`, hardcoded fps, magic choreography constants, `.find(() => true)`, hidden DOM elements, and duplicated per-scene numeric literals (the guide itself warns “统计图要和首页数字一致” — enforce via a shared helper or a consistency test).
- **DES-001:** the token file mixes app tokens with tuner-only preview data, and `schemaVersion` is never validated. Consider splitting tuner preview fields out or adding a `schemaVersion` check in `fromJson`.

### 6. Design

- **TOOLS-007:** the tuner’s color story is disconnected from the app. The “Preview colors” group edits only `preview.*`; the exported JSON still carries `topBar.primaryColor: null`; and the app’s `LumenTopBar` ignores those tokens (colors come from `MaterialTheme.colorScheme`). Result: a designer can tune a top-bar color in the tuner that will never appear in the app, and the preview is not a faithful representation of the real screen. Either wire `topBar.primaryColor`/`onPrimaryColor` through `LumenTopBar` (via `topAppBarColors(containerColor = ...)`), or label these controls clearly as preview-only.
- The Remotion architecture (typed scene definitions + pure presentational components) is a genuinely good design; scenes read as deterministic data with no runtime randomness.

### 7. Release

- **BP-003:** baseline profile generation is integrated into `build.yml` (line 183, `gradle :app:generateBaselineProfile`), gated on release signing, and the app module config (`automaticGenerationDuringBuild = false`, `mergeIntoMain = true`, `saveInSrc = true`) is consistent with the `androidx.baselineprofile` 1.4.1 plugin.
- **BP-001:** the `managedDevices { allDevices { create(...) } }` DSL shape is unusual; it evidently compiles under AGP 8.13.2 but should be sanity-checked on AGP upgrades.
- **REMO-002:** commit a lockfile for the Remotion package to make renders reproducible.

---

## Recommended Priority Actions

1. **REMO-001** — Gate the 4K render behind `workflow_dispatch` (or schedule) and add `timeout-minutes`; stop rendering 20 minutes of 4K on every push. *(High impact)*
2. **RES-001** — Delete the `resources/` tree (or move the intended icon source to `app/src/main/res/mipmap-*` with alpha). *(Trivial, removes 1.24 MB)*
3. **TOOLS-001** — Bind tuner dev/preview to localhost by default; narrow `fs.allow` to `design/`. *(Security)*
4. **TOOLS-002/003** — Wrap JSON import and File System Access calls in `try/catch`; add a minimal error boundary. *(Stability)*
5. **TOOLS-007** — Either wire top-bar color tokens through the app or label them preview-only in the tuner.
6. **DOC-001 / REMO-002 / TOOLS-004** — Update the stale guide section, commit the Remotion lockfile, and de-duplicate tuner defaults.
