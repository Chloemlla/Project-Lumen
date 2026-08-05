# Lumen Crash SDK — Static Code Audit

**Date:** 2026-08-05
**Scope:** `lumen-crash/`, `lumen-crash-core/`, `lumen-crash-sample/` (all Kotlin sources, build configs, ProGuard rules, manifests, resources, unit tests, and the `lumen-crash-sdk-release.yml` workflow)
**Auditor:** tame-legacy-codebase code review agent

---

## 1. Executive summary

Three related Gradle modules implement a crash-collection SDK for Android:
- `lumen-crash-core` (1299 lines of main Kotlin) — capture, persistence, breadcrumbs, watchdog, paste upload, author-integrity protection. Independently usable from API 26.
- `lumen-crash` (1532 lines) — the Compose crash-report UI bundle that depends on core via `api(project(":lumen-crash-core"))`.
- `lumen-crash-sample` (87 lines) — minify-enabled sample host.

The codebase is well-structured, cleanly layered, and unusually well-documented (the main README is a 1772-line operator manual that captures real field failures — R8 stripping, ClashMeta install-order — as hardened lessons). The author-integrity design (fail-closed attribution, multi-point SHA-256 checks) is coherent, if unusual and slightly paranoid. Unit tests cover persistence round-trips, JSON migration defaults, integrity, and paste-URL parsing.

The most serious issues are in crash-time robustness: the uncaught-exception handler's fallback path is not itself fail-safe (a single OOM or a stripped integrity constant can abort the handler before persistence or before chaining to the previous handler), and the paste-upload UI leaks a non-daemon executor thread per upload. Secondary concerns: freeze/ANR detection is silently disabled when the startup watchdog is enabled but the host never calls `markStartupComplete()`; crash reports are stored in app-specific *external* storage (world-visible to any app with storage permission and to the user via MTP); the paste connection follows redirects that can downgrade HTTPS to plaintext HTTP; and `material-icons-extended` is exposed as an `api` dependency that forces a multi-megabyte icon set on every host.

### Scores (1–10, 10 = best)

| Dimension | Score | One-line rationale |
|---|---|---|
| Security | 6.0 | No secrets, HTTPS-only paste, good sanitization patterns; but external-storage privacy, redirect-downgrade risk, and public-paste-host default leak surface |
| Stability | 5.5 | Crash-path fallback not fail-safe; executor thread leak; watchdog startup coupling disables freeze detection; main-thread I/O at crash and at gate composition |
| Performance | 6.5 | Regex recompilation per call, redundant SHA-256 churn, leaked threads, multi-megabyte icon dependency, crash-time file I/O on the crashing thread |
| Testing | 6.5 | Solid core unit tests (store, JSON, integrity, URL parsing); zero tests for watchdog, breadcrumbs, sanitization, thread dump, handler logic, or the HTTP path |
| Maintainability | 7.0 | Clean layering and naming; duplicated sanitize logic, dual callbacks, stale README test list, hardcoded magic numbers |
| Design | 7.0 | Sensible consumer API and failure semantics; forced Compose-BOM coupling, global mutable proxy hook, dual unsafe/safe install API are the main dents |
| Release | 7.5 | Professional CI/release pipeline (versioning, GitHub Packages, consumer rules, checksums); no lint on the two libraries, duplicate test execution, release sample not fully exercised |

**Overall: 6.6 / 10** — a solid, genuinely usable crash library that would benefit most from hardening the crash path itself (fail-safe fallback), fixing the thread leak, and re-reviewing the storage-location and redirect choices.

---

## 2. Code size baseline

| Unit | Files | Kotlin LOC |
|---|---|---|
| `lumen-crash` (UI bundle, main source) | 3 | 1532 |
| `lumen-crash-core` (main source) | 13 | 1299 |
| `lumen-crash-core` (unit tests) | 5 | 240 |
| `lumen-crash-sample` | 2 | 87 |
| **Kotlin total** | 23 | **3158** |

Build/config artifacts: 3 × `build.gradle.kts` (167 + 128 + 62 = 357 LOC), 7 × ProGuard/consumer rules (≈131 LOC), 3 × AndroidManifest, 1 × FileProvider paths XML, 1 × resource keep XML, 1 × `sdk.version` (0.1.0), EN/ZH string resources (47 + 47 strings). Documentation: `lumen-crash/README.md` (1772), `README.zh-CN.md` (1772), `lumen-crash-core/README.md` (46), `lumen-crash-sample/README.md` (18).

---

## 3. Findings table

Severity legend: **Critical** = process-kill / data-loss / exploitable; **High** = likely user-visible failure or leak; **Medium** = conditional failure or notable risk; **Low** = quality/robustness; **Info** = observation.

| ID | Severity | File | Line | Description | Category |
|---|---|---|---|---|---|
| STA-01 | High | `lumen-crash-core/.../LumenCrash.kt` | 149–150 | Crash-handler fallback (`fromThrowableFallback`) not wrapped in `runCatching`; a throw (OOM, or `SecurityException` when R8 strips integrity constants) aborts the handler before save and before chaining to the previous handler → report lost, chained handler never runs | Stability |
| STA-02 | Medium | `lumen-crash/.../ui/LumenCrashReportScreen.kt` | 1392–1394 | `Executors.newSingleThreadExecutor()` created per paste upload, never shut down; non-daemon worker threads accumulate per upload | Stability / Performance |
| SEC-01 | Medium | `lumen-crash-core/.../CrashReportStore.kt` | 140–153 | Reports persisted in app-specific **external** storage (`getExternalFilesDir`/`externalCacheDir`); readable by any app with storage permission and via MTP — breadcrumbs/system info may be sensitive | Security |
| SEC-02 | Medium | `lumen-crash-core/.../CrashReportPasteUploader.kt` | 64 | `instanceFollowRedirects = true`; a misconfigured/compromised paste host can 3xx to `http://` and receive the crash-report body in cleartext | Security |
| STA-03 | Medium | `lumen-crash-core/.../LumenCrashWatchdog.kt` | 70–88 | `startupHangWatchdogEnabled = true` disables freeze/ANR detection until `markStartupComplete()`; a host that enables startup hang but never completes startup permanently loses freeze/ANR reports | Stability |
| PER-03 | Medium | `lumen-crash/build.gradle.kts` | 77 | `androidx.compose.material:material-icons-extended` published as `api` — multi-megabyte icon artifact forced on every host, even capture-only / Flutter hosts | Performance / Release |
| STA-05 | Low | `lumen-crash/.../ui/LumenCrashGate.kt` | 19 | `initialReport = loadPendingReportSafely()` is a default parameter evaluated during first composition → file I/O on the main thread (strict-mode / jank risk at startup) | Stability / Performance |
| STA-04 | Low | `lumen-crash-core/.../LumenCrash.kt` | 141–164 | Uncaught handler does stack-trace building, sanitize, SHA-256, JSON, and multi-file writes on the crashing thread; chained handler runs only after persistence — a second failure inside the handler loses everything | Stability |
| SEC-03 | Low | `lumen-crash-core/.../CrashReportPasteUploader.kt` | 21, 25; `LumenCrashReportScreen.kt` 425 | Default paste host is a **public anonymous** LogPaste (`paste.gentoo.zip`); upload is user-triggered but enabled by default, and breadcrumbs/system info can contain sensitive strings | Security |
| SEC-04 | Low | `lumen-crash-core/.../CrashReportPasteUploader.kt` | 29–30 | `shouldSkipManualProxy` is a global mutable `@Volatile var` on a public object — any code in the process can flip it; also a static-state API smell | Security / Design |
| SEC-05 | Info | `lumen-crash-core/.../CrashReport.kt` / `CrashBreadcrumbs.kt` | 230–237 / 34–41 | Sanitization redacts only a small pattern set; Windows-path redaction is partial (`C:\Users\John\AppData` → `C:\Users\[user-home]\AppData`) and host-recorded breadcrumbs may embed arbitrary sensitive data | Security |
| STA-06 | Low | `lumen-crash-core/.../LumenCrashWatchdog.kt` + `CrashBreadcrumbs.kt` | 99–103 / 26–27 | If the main thread froze while holding the `@Synchronized` breadcrumbs lock, the watchdog blocks on `snapshot()` and `emit`'s `runCatching` silently drops the report | Stability |
| STA-07 | Low | `lumen-crash-core/.../LumenCrash.kt` | 56–74 | `installSafely` swallows install failures with no logging; hosts cannot diagnose why install failed (e.g., integrity mismatch) beyond a `Boolean` | Stability / Maintainability |
| STA-08 | Low | `lumen-crash-core/.../CrashReport.kt` | 250–258 | `crashReportFromJson` uses strict `getLong`/`getString` on required fields; a legacy/corrupt report missing one key is discarded entirely (→ `null`) instead of degrading gracefully | Stability |
| PER-01 | Low | `lumen-crash-core/.../CrashBreadcrumbs.kt` + `CrashReport.kt` | 34–41 / 230–237 | 4–5 `Regex` objects recompiled on every `sanitize` call (every breadcrumb record and every report build) — should be cached constants | Performance |
| PER-02 | Low | `lumen-crash-core/.../AuthorIntegrity.kt` | 19–49 | `verifyOrThrow` performs 2 × `MessageDigest.getInstance` + 2 × SHA-256 per call, and runs on install/record/load/export/UI open and again inside `toJson`/`fromJson` — redundant digest churn on the crash path | Performance |
| TES-01 | Medium | `lumen-crash-core/src/test` | — | No tests for watchdog timing/state machine, `CrashBreadcrumbs` ring buffer + sanitization, `CrashThreadDump` cap, `LumenCrash` install/handler logic, or config-builder metadata resolution | Testing |
| TES-02 | Low | `lumen-crash-core/src/test/.../CrashReportPasteUploaderTest.kt` | 7–49 | Paste-uploader tests cover only URL normalization/parsing; the HTTP path (redirects, proxy, error status, body reading) is untested | Testing |
| TES-03 | Low | `lumen-crash-core/src/test` | — | No unit tests for `sanitize` redaction patterns — the core privacy feature has zero assertions | Testing |
| TES-04 | Info | `lumen-crash/build.gradle.kts` + `README.md` | 84; "Testing" section | `lumen-crash` declares `testImplementation(junit)` but has no tests; README "Testing" section is stale (lists only 3 tests that actually live in `lumen-crash-core`) | Testing / Maintainability |
| MAI-01 | Low | `lumen-crash-core` | CrashBreadcrumbs.kt 34–41 vs CrashReport.kt 230–237 | Sanitization regex set duplicated verbatim in two files | Maintainability |
| MAI-02 | Low | `lumen-crash-core/.../LumenCrash.kt` | 206–212 | `onCrashSaved` (legacy) and `onReportSaved` both invoked for every persisted report — ambiguous API; hosts setting both get double notifications (and `onAnrDetected` fires a third time for watchdog kinds) | Maintainability / Design |
| MAI-03 | Low | `lumen-crash-core` | multiple | Hardcoded magic numbers (180-char breadcrumbs, 40 events, 12-char report ID, 64 KB dump cap, 4 KB floor, 100 ms interval floor) repeated across config defaults and watchdog coercions | Maintainability |
| MAI-05 | Low | `lumen-crash-core/.../LumenCrash.kt` | 151–156 | Dead defensive branch for `config == null` inside the handler — handler is installed only after `installedConfig.set(...)`, so config can never be null when the handler runs | Maintainability |
| DES-01 | Low | `lumen-crash-core/.../LumenCrash.kt` | 22–74 | Dual install API: throwing `install(...)` and swallowing `installSafely(...)`; README further depends on `runCatching` fallbacks for convenience methods that may be absent from older published AARs — fragile versioning story | Design |
| DES-02 | Low | `lumen-crash/build.gradle.kts` | 71–82 | Bundle publishes unversioned Compose `api` deps; every host (even capture-only) must import a Compose BOM or resolution fails with empty-version coordinates | Design / Release |
| DES-03 | Low | `lumen-crash-core/.../CrashReportPasteUploader.kt` | 29–30, 102–108 | Mutable global hook + any-HTTPS base-URL config; paste endpoint is host-controlled but the API surface is global/static | Design |
| REL-01 | Low | `.github/workflows/lumen-crash-sdk-release.yml` | 142 | CI runs `:lumen-crash-sample:lintDebug` but no `lint` for `:lumen-crash` / `:lumen-crash-core`; library lint findings go unnoticed | Release |
| REL-02 | Low | `.github/workflows/lumen-crash-sdk-release.yml` | 142, 146 | `:lumen-crash-core:test` executed twice (test step and assemble step) | Release |
| REL-03 | Info | `.github/workflows/lumen-crash-sdk-release.yml` | 146 | The minify-enabled sample is only `assembleDebug` + `compileReleaseKotlin` in CI; the R8 path (release AAR) is not actually assembled in the release workflow | Release |
| REL-04 | Low | `lumen-crash/build.gradle.kts` + `lumen-crash-core/build.gradle.kts` | 135–141 / 116–122 | Publishing credentials read at configuration time with empty-string fallbacks; a misconfigured CI would attempt authenticated publishes with empty creds rather than failing fast | Release |
| DES-04 | Info | `lumen-crash-core` + `lumen-crash` | — | No `@JvmOverloads`/`@JvmStatic` on most public helpers — minor friction for Java hosts | Design |

---

## 4. Detailed findings

### 4.1 Stability — crash path robustness

**STA-01 (High) — crash-handler fallback is not fail-safe.**
`LumenCrash.kt:149–150`:
```kotlin
val report = runCatching { CrashReport.fromThrowable(throwable, appInfo) }
    .getOrElse { CrashReport.fromThrowableFallback(throwable, it, appInfo) }
```
`fromThrowableFallback` (CrashReport.kt:89–118) itself calls `AuthorIntegrity.verifyOrThrow("from-throwable-fallback")` and `throwable.stackTraceToString()`. If either throws — a `SecurityException` when R8 stripped an integrity constant (the exact scenario documented in the README "white-screen" field lesson), or an `OutOfMemoryError` while materializing the stack string — the exception escapes `getOrElse`, the handler returns without saving the report and without invoking `previousHandler.uncaughtException(...)`, and the app dies with no record. Both branches of the `getOrElse` should be individually wrapped in `runCatching` (or the handler body itself should be a single `runCatching`). Note `record()` at line 95–96 has the identical structure.

**STA-02 (Medium) — executor thread leak per paste upload.**
`LumenCrashReportScreen.kt:1392–1394`:
```kotlin
runCatching {
    Executors.newSingleThreadExecutor().execute { ... }
}
```
`newSingleThreadExecutor()` keeps its single non-daemon core thread alive until `shutdown()`; there is no `shutdown()` anywhere in the file. Each "Upload and share link" tap creates a new executor/thread that never dies for the process lifetime. The `pasteUploadInFlight` flag prevents concurrency within one composition but not across recompositions/dialog cycles. Fix: a shared single-thread executor (or `Executors.newSingleThreadExecutor { Thread(...).apply { isDaemon = true } }`) and/or `shutdown()` after completion.

**STA-03 (Medium) — startup watchdog permanently disables freeze detection.**
`LumenCrashWatchdog.kt:70–88`: `startupPending` is `config.startupHangWatchdogEnabled && !startupComplete.get()`. When true, the ANR/freeze branch is skipped (`if (!startupPending && ...)`). If a host enables `startupHangWatchdogEnabled` but never calls `markStartupComplete()` — e.g., first frame never arrives, or the host forgets the call — `startupPending` remains true forever and **no freeze/ANR report is ever emitted again**, even after the app becomes responsive. The intended "startup report beats duplicate freeze" behavior is reasonable; the missing guard is that `markStartupComplete` should also be triggered (or the freeze branch allowed) after a startup-hang report is emitted once. At minimum this coupling should be documented loudly.

**STA-04 (Low) — heavy work on the crashing thread.** The uncaught handler builds the report (stack-trace strings, 4 regex sanitizations, 2 SHA-256 digests), serializes JSON, then writes to up to 3 external targets with temp-file+rename, all on the crashing thread, before chaining to the previous handler. For a main-thread crash the process may be killed by the system before persistence completes. Acceptable for a crash library, but a dedicated crash handler thread (like `Crashlytics`'s) would be more robust; at minimum the OOM/`Error` surface (STA-01) should be closed.

**STA-05 (Low) — gate does file I/O during composition.** `LumenCrashGate.kt:19` evaluates `LumenCrash.loadPendingReportSafely()` as a default parameter during the first composition on the main thread (disk read of up to several report files, plus JSON parse). Better on a background dispatcher with a loading state.

**STA-06 (Low) — watchdog can deadlock on the breadcrumbs lock.** `CrashBreadcrumbs.snapshot()` is `@Synchronized`. If the main thread froze in the middle of `record()` (while holding the lock), the watchdog thread blocks in `fromWatchdog → CrashBreadcrumbs.snapshot()`, and `emit`'s `runCatching` swallows the timeout with no report. The breadcrumb snapshot should be taken under a try-with-timeout or the lock should be held for minimal, non-blocking work.

**STA-07 (Low) — silent install failure.** `installSafely` returns `false` on any failure with no logging. A host debugging an integrity mismatch or a provider conflict gets no diagnostics. Consider a `@Volatile installError` field or `Log.w` in the catch.

**STA-08 (Low) — strict JSON read discards recoverable reports.** `crashReportFromJson` uses `json.getLong("crashedAtMillis")`, `getString("crashedAtText")`, `getString("exceptionType")`, `getString("rootCause")`, `getString("systemInfo")`, `getString("stackTrace")`. A report missing any one of these (schema drift, truncation) throws `JSONException`, which `readReport` converts to `null` — the report is silently lost. `optLong`/`optString` with defaults would preserve partial reports (matching the legacy-default philosophy already applied to `threadName`/`processName`/`kind`/`durationMillis`).

### 4.2 Security

**SEC-01 (Medium) — external-storage persistence.** `CrashReportStore.resolveExternalTargets` (140–153) writes `crash_report.json` under `getExternalFilesDir("lumen-crash")`, `getExternalFilesDir(null)/lumen-crash`, and `externalCacheDir/lumen-crash`. These are app-scoped but **not private**: any app holding `READ_EXTERNAL_STORAGE` (or a file manager / MTP session) can read them, and a full-device backup can carry them off. The report contains app metadata, device info, thread dumps, breadcrumbs (host-controlled, may include user data), and stack traces. The README frames this as a deliberate choice ("not kept only under internal private paths"), but from a security posture it is the single largest data-exposure surface of the SDK. Recommendation: keep the primary copy in `noBackupFilesDir` and use external storage only as a secondary mirror, or encrypt the payload.

**SEC-02 (Medium) — redirects can downgrade HTTPS.** `CrashReportPasteUploader.kt:64` sets `instanceFollowRedirects = true`. `HttpURLConnection` follows 3xx redirects to any scheme; if the (host-configurable) paste endpoint or its DNS is compromised, the crash-report POST body can be re-sent to an `http://` origin in cleartext. Since the body may contain breadcrumbs and diagnostics, consider disabling auto-redirects and following only `https://` redirects manually, or validating the final URL scheme.

**SEC-03 (Low) — public paste host by default.** The default `DEFAULT_BASE_URL = "https://paste.gentoo.zip"` is a public anonymous paste service; any user action that taps "Upload and share link" publishes the sanitized report (with system info + breadcrumbs) publicly. User-initiated, so acceptable, but should stay clearly surfaced in the UI copy and README (it is).

**SEC-04 (Low) — global mutable proxy hook.** `CrashReportPasteUploader.shouldSkipManualProxy: (() -> Boolean)?` is a `@Volatile var` on a public `object`. Any code in the host process (or a dependency) can overwrite it. Prefer constructor/config injection.

**SEC-05 (Info) — sanitization is a small denylist.** Only 4–5 patterns are redacted (Windows `/Users`/`/home` home paths, `content://`, `file://`). `C:\Users\John\AppData\...` is only partially redacted because the regex stops at the first backslash or whitespace. Breadcrumbs are entirely host-controlled — `recordBreadcrumb("token: abc123")` leaks verbatim. The privacy note string (`lumen_crash_report_privacy_note`) overstates what sanitization achieves; worth aligning the copy with reality.

### 4.3 Performance

- **PER-01 (Low)** — `Regex` objects are constructed inline on every `sanitize()` call (CrashBreadcrumbs.kt:34–41, CrashReport.kt:230–237). Each breadcrumb record pays 4 regex compilations. Cache as `private val` companion constants.
- **PER-02 (Low)** — `AuthorIntegrity.verifyOrThrow` (AuthorIntegrity.kt:19–49) calls `fingerprintHex()` (a SHA-256 digest) and then performs a *second* identical digest, plus two `MessageDigest.getInstance` calls. This runs on install, every `record()`, every JSON save/load, clipboard export, and UI open. The redundant digest is pure overhead on the crash path.
- **PER-03 (Medium)** — `material-icons-extended` as `api` (lumen-crash/build.gradle.kts:77) drags a very large icon library into every consumer's APK, even capture-only/Flutter hosts that never render the crash UI. The screen uses a handful of `Icons.Outlined.*` icons; a slim icon subset (or `material-icons-core` + only the needed vectors) would cut megabytes. Consider making it `implementation` (still shrinks into the bundle AAR, but it remains a transitive runtime dep for hosts rendering the UI).
- **PER-04 (Low)** — `CrashReportStore.saveLocked` writes the full JSON payload to every writable target (up to 3) sequentially, with a temp file each. Fine for a rare crash event, but worth noting the report ID SHA-256 + JSON + 3 writes all happen on the crashing thread.

### 4.4 Testing

Coverage present (all in `lumen-crash-core/src/test`):
- `CrashReportPersistenceTest` — JSON round-trip, legacy-field defaults, store save/load/clear/migrate (nice use of the `internal` test constructor and temp dirs).
- `CrashReportPasteUploaderTest` — base-URL normalization and shareable-URL parsing.
- `AuthorIntegrityTest` — fingerprint constant, verify success, clipboard attribution.
- `CrashReportKindTest` — wire-value stability.
- `LumenCrashDefaultsTest` — authority suffix.

**TES-01 (Medium) — critical untested logic:** the watchdog state machine (freeze/startup timing, `AtomicBoolean` transitions), `CrashBreadcrumbs` ring-buffer size/truncation/sanitization, `CrashThreadDump` 64 KB cap, `LumenCrash.install` handler chaining and the `killProcessWhenNoPreviousHandler` path, and `LumenCrashConfigBuilder` metadata resolution are all untested. **TES-03 (Low)** — the sanitizer (the privacy guarantee) has zero tests. **TES-02 (Low)** — the paste HTTP path has no mock tests (`HttpURLConnection` is not faked), so redirects, error status, and proxy behavior are unverified. Note the `org.json:json` test dependency is correctly added so local JVM tests can exercise the framework `org.json` classes.

### 4.5 Maintainability

- **MAI-01** — the sanitize regex block is duplicated verbatim (CrashBreadcrumbs.kt vs CrashReport.kt); extract to one shared internal object.
- **MAI-02** — `onCrashSaved` + `onReportSaved` + (for watchdog kinds) `onAnrDetected` all fire for one persisted report. The README calls `onCrashSaved` "legacy", but keeping both active invites double-upload in hosts that set both. Consider deprecating `onCrashSaved` or making it delegate to `onReportSaved`.
- **MAI-03** — magic numbers are scattered (180, 40, 12, 64*1024, 4096, 100, 5_000, 1_000, 15_000). Most are mirrored between `LumenCrashConfig` defaults and `LumenCrashWatchdog` coercions; a single constants object would prevent drift.
- **MAI-05** — the `config == null` branch in the handler is unreachable in practice; harmless but misleading.

### 4.6 Design

- **DES-01** — the API offers `install` (throws) and `installSafely` (Boolean) and the README instructs hosts to `runCatching` around `install` anyway because `installSafely` may not exist on older AARs. That tri-state compatibility story (safe method vs. runCatching fallback) is a genuine maintenance burden for consumers. Recommend committing to `installSafely`/`loadPendingReportSafely` as permanent API.
- **DES-02** — unversioned Compose `api` deps + BOM requirement means even a capture-only host must import a Compose BOM or resolution fails. Since the bundle is the "everything" artifact, this is defensible, but core-only hosts are shielded (core has no Compose deps) — good.
- **DES-03** — `CrashReportPasteUploader.shouldSkipManualProxy` global hook is the one piece of "process-global mutable state" in an otherwise DI-clean codebase.
- **Author-integrity design** — coherent and fail-closed; the per-operation SHA-256 overhead is the only cost. One note: the fingerprint constant (`94796096...`) and the expected digest are both derived from the same source constants, so the "multi-point" check is really one check evaluated several ways; an attacker who edits the source trivially defeats it (README acknowledges forks can edit source). Fine as an attribution-obfuscation measure.

### 4.7 Release

- Versioning is consistent: both modules read `lumen-crash/sdk.version` (0.1.0); CI derives `<version>-<shortSha>` on main pushes and exact versions on `lumen-crash-v*` tags; `group`/`version`/POM metadata are duplicated correctly across both modules.
- Consumer ProGuard rules are thorough (`consumer-rules.pro` in both AARs) with package-level keep as a safe default; the `host-proguard-template.pro` and `host-keep-resources.xml` templates are a thoughtful touch for hosts that strip consumer rules. The package-wide `-keep ... ** { *; }` is heavy-handed (defeats R8 for the SDK) but documented as a safe default.
- **REL-01/02** — CI runs lint only for the sample and runs core tests twice; add `:lumen-crash:lintDebug :lumen-crash-core:lintDebug` and drop the duplicate test invocation.
- **REL-03** — the minify path is only smoke-tested via `compileReleaseKotlin`; consider `:lumen-crash-sample:assembleRelease` in CI since the README's own field lessons are about release-minify failures.
- **REL-04** — empty credential fallbacks in the publishing block mean a misconfigured CI fails with confusing auth errors rather than a clear message; consider failing fast when publishing to GitHub Packages without credentials.

---

## 5. Strengths worth preserving

- Exceptional documentation: the README's two "field lesson" postmortems (R8 white-screen, ClashMeta install-order) are directly actionable and match the code.
- Clean crash-path layering: report model → store → callbacks; JSON `kind`/`durationMillis` wire values are stable with legacy fallbacks.
- Atomic multi-target persistence with temp-file+rename and legacy-private-copy migration is well designed.
- The watchdog runs off the main looper and emits bounded thread dumps (64 KB cap) — the right shape for freeze detection.
- `installSafely`/`loadPendingReportSafely`/`LumenCrashGate` give hosts a genuinely safe short path; `AuthorIntegrity` fail-closed behavior is coherent with the attribution goal.
- Internal test constructor on `CrashReportStore` enables clean local persistence tests without Android.
- Release automation (GitHub Packages + Releases + checksums + `sdk-manifest.json`/`lumen-crash-latest.json`) is professional.

---

## 6. Prioritized recommendations

1. **Close STA-01**: wrap the fallback report construction in its own `runCatching` (both in the uncaught handler and in `record()`). Highest-impact robustness fix.
2. **Fix STA-02**: reuse a single daemon executor for paste uploads or `shutdown()` after each.
3. **Reconsider SEC-01**: keep the primary report in `noBackupFilesDir`; treat external storage as a mirror or drop it.
4. **Harden SEC-02**: don't auto-follow redirects to non-HTTPS origins for the paste POST.
5. **Document/fix STA-03**: after a startup-hang report, re-enable freeze detection (or require `markStartupComplete` and log loudly when startup hangs).
6. **Trim PER-03**: move `material-icons-extended` off `api` or replace with a small icon subset.
7. **Cache PER-01/PER-02**: hoist the sanitize `Regex`es and avoid the duplicate SHA-256.
8. **Add TES-01/TES-03**: watchdog state-machine tests, breadcrumb ring-buffer tests, and sanitizer tests.
9. **CI (REL-01/02/03)**: lint both libraries, dedupe the test invocation, and assemble the minified sample release.
