# Android First-Run Gates and Per-Build Update Notes

> Contracts for launch-time full-screen gates: open-source notice, immersive product tutorial (onboarding), and **per-build "What's new / 本次更新说明"** pages keyed by GitHub commit hash + build time.

---

## Scenario: Launch Gate Stack (OSS → Tutorial → Build Notes)

### 1. Scope / Trigger

- Trigger: any change to first-install UX, post-update first-open UX, About reopen entries for legal/credits/notes, or build identity fields used to decide "new build".
- Applies to:
  - `app/src/main/java/com/projectlumen/app/app/ProjectLumenApp.kt` (gate composition order)
  - `ProjectLumenViewModel` (`refreshOnboardingState` / gate visibility)
  - `ProjectLumenOpenSourceNoticeScreen` + `ProjectLumenOssNoticeState`
  - `ProjectLumenOnboardingScreen` + `ProjectLumenOnboardingState`
  - New: `ProjectLumenBuildUpdateNotesScreen` + build-notes state
  - `SecureCredentialStore` install profile keys
  - `BuildConfig` / `BuildMetadata` (`COMMIT_HASH`, `SHORT_HASH`, `BUILD_TIME_UTC_MILLIS`)
  - `.github/workflows/build.yml` notes generation step
  - EN/ZH string resources for chrome copy

### 2. Signatures

```kotlin
// Existing identity (do not invent parallel fields)
data class BuildMetadata(
    val versionName: String,
    val versionCode: Int,
    val buildTimeUtcMillis: Long,
    val commitHash: String,
    val shortHash: String,
) {
    companion object {
        fun current(): BuildMetadata // from BuildConfig
    }
}

// Install profile (extend; keep existing keys stable)
// SecureCredentialStore.installProfile():
//   onboardingCompletedAt: Long
//   ossNoticeCompletedAt: Long
//   lastAcknowledgedCommitHash: String   // NEW
//   lastAcknowledgedBuildTimeUtcMillis: Long // NEW

fun SecureCredentialStore.markOssNoticeCompleted(nowMillis: Long = System.currentTimeMillis())
fun SecureCredentialStore.markOnboardingCompleted(nowMillis: Long = System.currentTimeMillis())
fun SecureCredentialStore.markBuildUpdateNotesAcknowledged(
    commitHash: String,
    buildTimeUtcMillis: Long,
)

// UI surfaces
@Composable fun ProjectLumenOpenSourceNoticeScreen(onContinue: () -> Unit, onDismiss: (() -> Unit)? = null, ...)
@Composable fun ProjectLumenOnboardingScreen(state: ProjectLumenOnboardingState, onFinished: () -> Unit, ...)
@Composable fun ProjectLumenBuildUpdateNotesScreen(
    notes: BuildUpdateNotes,
    onContinue: () -> Unit,
    onDismiss: (() -> Unit)? = null, // reopen mode
)

data class BuildUpdateNotes(
    val versionName: String,
    val versionCode: Int,
    val commitHash: String,
    val shortHash: String,
    val buildTimeUtcMillis: Long,
    val title: String,
    val bodyMarkdownOrPlain: String,
    val highlights: List<String>,
)

data class ProjectLumenBuildUpdateNotesState(
    val visible: Boolean = false,
    val reopenMode: Boolean = false,
    val notes: BuildUpdateNotes? = null,
)
```

### 3. Contracts

#### 3.1 Gate priority (highest wins; only one full-screen gate at a time)

| Priority | Gate | When auto-shown | Completion key | Reopen |
|----------|------|-----------------|----------------|--------|
| 1 | Open-source notice | First install only: `ossNoticeCompletedAt <= 0` and not grandfathered existing user | `ossNoticeCompletedAt` | About → reopen OSS |
| 2 | Immersive product tutorial (onboarding) | First install: `onboardingCompletedAt <= 0` after OSS completed or not required | `onboardingCompletedAt` | Do **not** force re-show on upgrade |
| 3 | Per-build update notes | Current build identity ≠ last acknowledged pair | `lastAcknowledgedCommitHash` + `lastAcknowledgedBuildTimeUtcMillis` | About → "What's new in this build" |

**Composition rule in `ProjectLumenApp`:**

1. If OSS first-run visible → only OSS screen (blocks home).
2. Else if onboarding visible → only onboarding.
3. Else if build-update-notes visible (non-reopen) → only notes page.
4. Else normal app chrome; reopen modes overlay as today for OSS (and notes).

Do **not** stack multiple first-run full screens simultaneously.

#### 3.2 Build identity (new-build detection)

- A **build** is uniquely identified for first-open notes by the pair:
  - `BuildConfig.COMMIT_HASH` (full SHA preferred for equality)
  - `BuildConfig.BUILD_TIME_UTC_MILLIS`
- Display uses `VERSION_NAME`, `VERSION_CODE`, `SHORT_HASH`, formatted build time.
- **Why not versionName alone**: CI can produce multiple artifacts for the same `application.version` with different commits/times (`VERSION_CODE` often `GITHUB_RUN_NUMBER`).
- **Why not commit alone**: rebuilds of the same commit at different times are still distinct installable builds; users should see notes again if the binary is a new build artifact (same commit, new build time) only when product policy wants it — **locked policy**: both commit **and** build time must match to suppress re-show. Changing either re-arms the notes page.

Equality:

```text
needsBuildNotes =
  lastAcknowledgedCommitHash != current.commitHash
  OR lastAcknowledgedBuildTimeUtcMillis != current.buildTimeUtcMillis
```

Empty/missing last-acknowledged fields ⇒ treat as not acknowledged (show once).

#### 3.3 Fresh install vs upgrade paths

| User state | OSS | Tutorial | Build notes |
|------------|-----|----------|-------------|
| Brand-new install (no onboarding, no OSS) | Show first | Show after OSS complete | Show after tutorial complete (this build) |
| Existing user who finished onboarding before OSS feature | Grandfather: auto-complete OSS without re-show (existing behavior) | Already done | Show if build identity new |
| Upgrade install (OSS + onboarding already completed) | Skip | Skip | Show if build identity new |
| Same build reopened | Skip | Skip | Skip (already acknowledged) |

#### 3.4 Update notes content pipeline

**Runtime must not depend on network** for already-installed build notes.

1. **CI (required for release/CI builds)** — in `.github/workflows/build.yml` after commit/env is known:
   - Read `PROJECT_LUMEN_COMMIT_HASH` / `PROJECT_LUMEN_SHORT_HASH` / `PROJECT_LUMEN_BUILD_TIME_UTC_MILLIS` / version.
   - Generate `app/src/main/assets/build-update-notes.json` (UTF-8) with shape:

```json
{
  "versionName": "1.2.3",
  "versionCode": 42,
  "commitHash": "<full sha>",
  "shortHash": "<8 hex>",
  "buildTimeUtcMillis": 0,
  "title": "<git subject>",
  "body": "<git body or conventional summary>",
  "highlights": ["..."]
}
```

   - Preferred generators:
     - `git show -s --format=%s $COMMIT_HASH` → `title`
     - `git show -s --format=%b $COMMIT_HASH` → `body` (trim; empty OK)
     - Optional: `git log -n 1 --pretty=...` only for that single commit (not multi-commit changelog unless product later expands)
   - Escape JSON properly; never embed raw unescaped quotes into `buildConfigField` strings for long notes — prefer **assets JSON**.

2. **Runtime loader**:
   - Read asset once; parse; validate that `commitHash`/`buildTimeUtcMillis` match `BuildMetadata.current()` when present.
   - Mismatch or missing asset → **fallback notes**: title from versionName, body empty or localized "No detailed notes for this build", still show identity row (version, short hash, build time).

3. **About / remote UpdateDialog**:
   - `UpdateDialog` + `ReleaseInfo.body` remain the **pre-install remote release notes** channel.
   - Per-build first-open page is **post-install** identity notes. Do not replace one with the other.

#### 3.5 Persistence keys (`SecureCredentialStore`)

| Key constant | Type | Meaning |
|--------------|------|---------|
| `onboarding_completed_at` | Long | Immersive tutorial finished |
| `oss_notice_completed_at` | Long | OSS notice finished |
| `build_update_notes_ack_commit` | String | Last acknowledged full commit hash |
| `build_update_notes_ack_build_time` | Long | Last acknowledged `BUILD_TIME_UTC_MILLIS` |

- Writing acknowledgment must be atomic enough that process death mid-tap does not re-loop forever: write both fields before hiding UI.
- Reopen mode must **not** clear acknowledgment.

#### 3.6 UI chrome (build notes page)

- Full-screen `Scaffold` + scrollable content + sticky primary **Continue** (mirror OSS notice).
- Header: localized "What's new in this build" / "本次更新说明".
- Identity block always visible:
  - Version name + version code
  - Short hash (monospace); optional copy full hash
  - Build time formatted with device locale / ISO fallback
- Body: highlights as bullets when non-empty; then plain/markdown body.
- First-run: system back may finish activity (same policy as OSS first-run) **or** be disabled until Continue — **locked**: match OSS first-run (do not mark acknowledged on back).
- Reopen: `BackHandler` → dismiss without changing acknowledgment.

#### 3.7 i18n

- All chrome strings in `values/strings.xml` + `values-zh/strings.xml`.
- Commit title/body/highlights are content of the build; keep as produced (often English commit messages). Do not machine-translate in CI unless a future task adds it.
- Escape apostrophes in XML string resources.

#### 3.8 Relationship to existing OSS + onboarding code

- Keep `ProjectLumenOpenSourceNoticeScreen` as legal/credits gate; do not merge OSS text into update notes.
- Keep multi-page animated onboarding as the **immersive user tutorial**.
- ViewModel `completeOssNotice` already chains into onboarding when pending — extend chain: after onboarding complete, evaluate build-notes visibility; after OSS complete on upgrade path with onboarding done, evaluate build-notes.

### 4. Validation & Error Matrix

| Condition | Behavior |
|-----------|----------|
| `ossNoticeCompletedAt <= 0` and not grandfathered | Show OSS; block notes/tutorial until complete |
| Grandfathered existing user (onboarding done, OSS never completed) | Auto-mark OSS complete; do not auto-show OSS |
| `onboardingCompletedAt <= 0` after OSS settled | Show immersive tutorial |
| Acknowledged pair equals current build | Do not auto-show notes |
| Acknowledged missing or differs | Auto-show notes after higher gates clear |
| Asset missing / JSON parse error | Fallback identity-only notes; still require Continue once |
| Asset commit/time mismatch vs `BuildConfig` | Prefer `BuildConfig` identity for display; use asset text only if hash matches; else fallback body |
| Reopen from About while already acknowledged | Show notes; Continue/Dismiss closes; keys unchanged |
| Concurrent reopen OSS + notes | Only one overlay; last user action wins; do not show both |
| Local debug build with `COMMIT_HASH=unknown` | Still show once per `(unknown, buildTime)` pair |
| Process killed before ack write | Notes may show again (acceptable) |
| Attempt to use `versionName` alone for ack | **Forbidden** by this spec |

### 5. Good / Base / Bad Cases

- **Good**: User installs CI APK for commit `abc…` at T1 → finishes OSS + tutorial → sees 本次更新说明 with subject line from that commit → Continue → home. Reopen app → no notes. Install new APK commit `def…` → notes for `def…` once.
- **Base**: Developer rebuilds same commit with new `BUILD_TIME_UTC_MILLIS` → notes re-arm (new binary artifact).
- **Good**: Existing upgraded user with onboarding done → only notes page on first open of new build.
- **Bad**: Showing remote GitHub latest release body as "this installed build notes" without matching install identity.
- **Bad**: Auto-showing product onboarding again on every upgrade.
- **Bad**: Embedding multi-kilobyte notes via unescaped `buildConfigField` String.
- **Bad**: Writing gates into a "super file" mega-Compose module without state/store separation (follow existing App/ViewModel/Screen split).

### 6. Tests Required

- **GitHub Actions only** for build/resource merge (no local Gradle per repo policy).
- Unit-style (when present in CI):
  - Gate resolver pure function: inputs `(ossAt, onboardingAt, lastAckCommit, lastAckTime, currentMeta, grandfatherFlags)` → expected visible gate enum.
  - Asset parser: valid JSON, empty body, mismatch hash, corrupt JSON → fallback.
- Manual QA:
  - Fresh install path order: OSS → tutorial → notes → home.
  - Upgrade path: notes only.
  - About reopen OSS + notes.
  - Second cold start same APK: no auto notes.

### 7. Wrong vs Correct

#### Wrong — acknowledge by version name only

```kotlin
if (prefs.lastVersionName != BuildConfig.VERSION_NAME) {
    showNotes = true
}
```

#### Correct — acknowledge by commit + build time

```kotlin
val current = BuildMetadata.current()
val needsNotes =
    installProfile.lastAcknowledgedCommitHash != current.commitHash ||
        installProfile.lastAcknowledgedBuildTimeUtcMillis != current.buildTimeUtcMillis
```

#### Wrong — show notes under OSS first-run

```kotlin
if (ossVisible || notesVisible) {
    // both composed → stacked gates
}
```

#### Correct — exclusive priority

```kotlin
when {
    ossNoticeState.visible && !ossNoticeState.reopenMode -> OpenSourceNotice(...)
    onboardingState.visible -> Onboarding(...)
    buildNotesState.visible && !buildNotesState.reopenMode -> BuildUpdateNotes(...)
    else -> MainChrome(...)
}
// reopen overlays after main chrome, one at a time
```

#### Wrong — CI dumps full git log for entire history into the APK every build

```bash
git log --oneline > app/src/main/assets/build-update-notes.txt
```

#### Correct — single-commit notes for this artifact

```bash
TITLE=$(git show -s --format=%s "$COMMIT_HASH")
BODY=$(git show -s --format=%b "$COMMIT_HASH")
# write JSON with version/commit/buildTime/title/body/highlights
```

---

## Scenario: CI Embedding of Commit Notes

### 1. Scope / Trigger

- Trigger: changing how release artifacts describe themselves to the client, or `build.yml` version/metadata steps.

### 2. Contracts

- Env already set by workflow: `PROJECT_LUMEN_VERSION_NAME`, `PROJECT_LUMEN_VERSION_CODE`, `PROJECT_LUMEN_BUILD_TIME_UTC_MILLIS`, `PROJECT_LUMEN_COMMIT_HASH`, `PROJECT_LUMEN_SHORT_HASH`.
- Notes file path (locked): `app/src/main/assets/build-update-notes.json`.
- File is **generated in CI workspace before Gradle assemble**; do not commit machine-local notes with secrets.
- Gradle must package `assets/`; no extra plugin required.
- If `git show` fails (shallow clone edge): write fallback JSON with title = versionName and empty body; build must not fail solely due to missing commit message.

### 3. Validation

- Missing `assets/` directory → create it in the workflow step.
- Non-UTF8 commit messages → force UTF-8 JSON encoding; replace illegal surrogates.

---

## Design Decisions

### Design Decision: Full-screen notes vs UpdateDialog

**Context**: Users need to understand *this installed binary*, not only *available remote updates*.

**Options**:
1. Reuse `UpdateDialog` with local text — couples install UX to download machine.
2. Dedicated full-screen first-open page (chosen).
3. Notification only — easy to miss.

**Decision**: Dedicated page aligned with OSS notice; keep `UpdateDialog` for remote updates.

### Design Decision: Commit + build time pair

**Context**: Same semantic version can ship multiple CI artifacts.

**Decision**: Pair equality on full commit hash + `BUILD_TIME_UTC_MILLIS` for acknowledgment.

### Design Decision: Assets JSON vs BuildConfig string

**Context**: Commit bodies can include quotes/newlines.

**Decision**: Assets JSON written by CI; runtime parser with fallback. Avoid long `buildConfigField` strings for notes body.

---

## Implementation Checklist (for code sessions)

- [ ] Extend `SecureCredentialStore` + install profile with ack commit/time
- [ ] Add `BuildUpdateNotes` model + asset loader
- [ ] Add `ProjectLumenBuildUpdateNotesState` + ViewModel complete/reopen/dismiss
- [ ] Compose screen + EN/ZH strings
- [ ] Wire exclusive gate order in `ProjectLumenApp`
- [ ] About entry to reopen notes
- [ ] CI step to write `build-update-notes.json`
- [ ] Gate unit tests in workflow-bound test sources when available

---

## Related Specs & Code

- Frontend index: [index.md](./index.md)
- Compose surfaces: [android-compose-surfaces.md](./android-compose-surfaces.md)
- Existing code:
  - `app/.../core/update/UpdateModels.kt` (`BuildMetadata`)
  - `app/.../core/security/SecureCredentialStore.kt`
  - `app/.../app/ProjectLumenOpenSourceNoticeScreen.kt`
  - `app/.../app/ProjectLumenOnboardingScreen.kt`
  - `.github/workflows/build.yml` (version/commit/time env)
- Sibling task history: `.trellis/tasks/07-17-first-run-oss-notice-credits`

---

**Language**: documentation and code identifiers in **English**; user-facing ZH strings live in `values-zh`. Conversational replies may be Chinese.