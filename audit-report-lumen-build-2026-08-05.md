# Project Lumen — CI/CD, Build Configuration, Scripts & Documentation Audit

- **Date:** 2026-08-05
- **Scope:** `.github/workflows/`, root & module Gradle build files, `Dockerfile`/`.dockerignore`, `scripts/`, `docs/`, and supporting config (`AGENTS.md`, `README.md`, `.gitignore`)
- **Review type:** Static, "tame-legacy-codebase" audit
- **Repository state:** clean `main` @ `17a4546`

---

## 1. Executive Summary

The repository has a genuinely capable CI system: Android release builds with real keystore signing, baseline-profile generation, APK signature inspection, Rust backend checks, admin-dashboard builds, CodeQL, a multi-stage Docker build with GHCR caching, and a documented release manifest flow. There is real depth here. However, the automation carries several **critical-to-high security and release-integrity problems**, most notably a hardcoded credential pair for a plain-HTTP Maven repository, a workflow that **auto-creates "latest" GitHub Releases on every push to every branch**, and a production deployment job that **downloads and executes an unpinned script from another repository with the SSH key and admin tokens in its environment**.

The Gradle and Rust builds are reasonably pinned and cached. The weakest areas are **release integrity** (releases fire before tests/lint, tagged releases skip baseline-profile generation, feature-branch pushes become the "latest" release the app auto-updates from) and **reproducibility** (three of four JS projects have no committed lockfile; the Docker base images float; `npm install` is never `--frozen-lockfile`).

### Scores (1–10)

| Dimension | Score | Rationale |
|---|---|---|
| **Security** | **4** | Hardcoded credentials over HTTP; un-pinned remote deploy script with SSH/admin secrets; known default request-signing secret compiled into the client; no CA certificates in the final Docker image |
| **Stability** | **5** | NDK version mismatch between workflows and `gradle.properties`; missing lockfiles; fork-PR builds always fail (secrets); no concurrency groups; CodeQL 30-min timeout likely too short |
| **Performance** | **6** | Gradle build cache + Docker GHA cache present; but Docker dependency layers not cached, NDK re-downloaded every run, npm installs uncached |
| **Testing** | **6** | Unit tests + lint + Rust tests + CodeQL all wired; but tests/lint run *after* the release is created, lint is debug-variant only |
| **Maintainability** | **5** | ~200-line signing/release blocks duplicated verbatim across two workflows; 52 KB copied script from another repo; README/doc claims that don't match CI behavior |
| **Design** | **5** | Multi-module Gradle layout and multi-stage Docker are sound; but release-on-every-push design, dead `pages` permissions, and disabled composite action undermine it |
| **Release** | **4** | Release created before quality gates; every branch push becomes `make_latest`; tagged releases lack baseline profiles; versionCode not monotonic across workflows |

**Overall: 5/10** — functional and carefully instrumented, but with several must-fix security and release-integrity defects before this should be considered production-safe.

---

## 2. Code Size Baseline

| Area | Files | Lines |
|---|---|---|
| Total tracked files | 520 | — |
| `app/` Kotlin + Java | 220 | ~39,775 |
| `backend/` Rust (`src`) | — | ~8,049 |
| `.github/workflows/` (9 workflows) | 9 | 2,047 |
| `scripts/` (9 files) | 9 | 2,657 (incl. 1,742-line `fix-dependabot-alerts.js`) |
| `docs/` | 55 | ~9,700 |
| Gradle build files (`*.gradle.kts` + `gradle.properties`) | 6 | 145 |

---

## 3. Findings Table

| ID | Severity | File | Line | Description | Category |
|---|---|---|---|---|---|
| S-01 | **Critical** | `settings.gradle.kts` | 33–40 | Hardcoded Maven credentials (`developer` / `developer!@#`) for a repository served over plain HTTP | Security |
| S-02 | **High** | `settings.gradle.kts` | 34–35 | `isAllowInsecureProtocol = true` + `http://nexus.itgsa.com:5566` for dependency resolution → MITM / supply-chain injection | Security |
| S-03 | **High** | `.github/workflows/build-artifacts.yml` | 17, 130–157 | Deploy job downloads and executes `deploy_image.js` from a **mutable `main` ref** of an external repo, with SSH `PRIVATE_KEY`, `ADMIN_PASSWORD`, and `LUMEN_OUTEMAIL_API_KEY` in env | Security |
| S-04 | **High** | `.github/workflows/build-artifacts.yml` | 96–157 | Production deployment runs on **every push to `main`** with no environment protection or approval gate | Security / Design |
| S-05 | **High** | `.github/workflows/dependabot-maintenance.yml` | 37, 59–80, 181–231 | Auto-push to `main` with a personal PAT and force-merge of Dependabot PRs bypasses review/branch protection | Security / Release |
| S-06 | **Medium** | `app/build.gradle.kts` + `backend/src/config.rs` | app:119; backend:66 | Well-known default request-signing secret `project-lumen-local-request-signing-key` compiled into every client build and used as backend default; backend only warns, does not fail closed | Security |
| S-07 | **Medium** | `app/build.gradle.kts` | 74, 81 | Production API URLs used as silent fallbacks; debug/CI builds (e.g., CodeQL `assembleDebug`) point at production with no pinning | Security |
| S-08 | **Medium** | `Dockerfile` | 20–36 | Final image has **no `ca-certificates`** but backend uses `reqwest` with `rustls-tls` (system roots) → outbound HTTPS (outemail) will fail in the container | Security / Stability |
| S-09 | **Medium** | `app/build.gradle.kts` | 151 | `TELEMETRY_ACCESS_TOKEN` baked into `BuildConfig` of every build (extractable from APK) | Security |
| S-10 | **Low** | `.github/workflows/build.yml` / `release.yml` | build:99–105; release:121–127 | Logs keystore alias value and base64 length (minor secret metadata) | Security |
| S-11 | **Low** | `app/build.gradle.kts` | 171–179 | Signing secret passed as plain CMake `-D` argument on the build command line | Security |
| S-12 | **Low** | repo root (working dir) | — | `project_lumen-release.jks` release keystore sits in the repo working directory (gitignored, not tracked, but live signing material kept with the checkout) | Security |
| ST-01 | **High** | `.github/workflows/build.yml` | 3–10, 357–368 | Workflow triggers on **every push to every branch** with no path filter, and auto-creates a GitHub Release per push (see R-01) | Stability / Release |
| ST-02 | **High** | `.github/workflows/build.yml` | 85–120 | `Write signing config` runs on PRs too; on fork PRs secrets are empty → the step `exit 1`, so **all fork PRs fail the build** | Stability |
| ST-03 | **Medium** | `.github/workflows/build.yml` + `gradle.properties` | build:41/45; release:38/42; codeql:65/69; `gradle.properties`:7 | Workflows install `ndk;30.0.15729638` but `projectLumenNdkVersion=28.2.13676358`; AGP must auto-download the right NDK every run. The composite action that reads the correct version (`.github/actions/setup-android-native-toolchain/action.yml`) is commented out in all workflows | Stability |
| ST-04 | **Medium** | `.github/workflows/release.yml` | whole | Tagged releases never run `generateBaselineProfile` (build.yml does) → released APKs lack the baseline profile nightly builds get | Stability / Performance |
| ST-05 | **Medium** | `backend/admin`, `docs`, `remotion/android-product-animation` | — | No committed `package-lock.json` for the admin dashboard, docs, or Remotion project → non-reproducible `npm install` | Stability / Security |
| ST-06 | **Medium** | `.github/workflows/codeql.yml` | 22, 104–106 | `timeout-minutes: 30` with a full `gradle assembleDebug` manual build is likely too tight | Stability |
| ST-07 | **Medium** | `.github/workflows/build.yml` / `release.yml` | build:412–421; release:338–342 | Release-sync `curl` retry/timeout flags exist only in `build.yml`; release.yml has neither | Stability |
| ST-08 | **Low** | `.github/workflows/build.yml` | 32–46 | SDK licenses + NDK accepted/installed on every run (no caching of SDK/NDK components) | Stability / Performance |
| ST-09 | **Low** | all workflows | — | Third-party actions pinned only to mutable tags (`@v4/@v5/@v6/@v2`), never to commit SHAs | Stability / Security |
| ST-10 | **Low** | `.github/workflows/lumen-crash-sdk-release.yml` | — | No Android SDK license/NDK step; relies on runner defaults while also running `lintDebug` + `assembleDebug` for the sample | Stability |
| P-01 | **Medium** | `Dockerfile` | 6, 16–17 | Single `RUN cargo build` and single `RUN npm install && npm run build` → any source change invalidates the whole dependency layer | Performance |
| P-02 | **Medium** | `.github/workflows/build.yml` etc. | 41–45 | NDK 30 installed then NDK 28 auto-downloaded → wasted bandwidth/time every build | Performance |
| P-03 | **Low** | `build-artifacts.yml`, `lumen-ui-tuner.yml`, etc. | — | npm installs uncached (no `actions/cache`); only Gradle uses a cache | Performance |
| P-04 | **Info** | `gradle.properties` | 2–3; `build-artifacts.yml` 89–90 | `org.gradle.caching=true` and Docker `type=gha` cache with `mode=max` are good | Performance |
| T-01 | **High** | `.github/workflows/build.yml` | 357 vs 423/433 | GitHub Release is created **before** unit tests and lint run; failing tests/lint do not block the release | Testing / Release |
| T-02 | **Low** | `.github/workflows/build.yml` / `release.yml` | build:441; release:93 | Only `lintDebug` is run; release variant lint is not checked | Testing |
| T-03 | **Low** | `.github/workflows/build.yml` | — | No instrumented/UI tests in the main build (only the path-gated tuner workflow runs connected tests) | Testing |
| T-04 | **Info** | `.github/workflows/codeql.yml` | — | CodeQL for java-kotlin / rust / actions is configured and scheduled | Testing |
| M-01 | **High** | `.github/workflows/build.yml` vs `release.yml` | both | ~200 lines of "Read app version", "Write signing config", "Prepare release assets", inline Python manifest generator, and backend-sync blocks are duplicated verbatim; should be composite actions or scripts | Maintainability |
| M-02 | **Medium** | `scripts/fix-dependabot-alerts.js` | whole | 1,742-line / 52 KB script copied from another project ("happy-tts-…"); assumes pnpm and a `rust-services/` directory that don't exist here | Maintainability |
| M-03 | **Medium** | `README.md` | 17, 39 | Claims app minSdk 26 (actual: 29 in `app/build.gradle.kts:159`) and that the docs workflow "publishes" the site (CI only uploads artifacts) | Maintainability |
| M-04 | **Medium** | `docs/homepage-guide.md` | 53 | States the workflow "publishes to GitHub Pages" on default-branch push — no `actions/deploy-pages` exists anywhere | Maintainability |
| M-05 | **Medium** | `.github/workflows/vitepress-docs.yml` | 18–21 | Grants `pages: write` + `id-token: write` but never deploys; dead/over-scoped permissions | Maintainability / Design |
| M-06 | **Low** | `Install-CodexTrellis.ps1` | 29 | Default `SourceRoot` is a hardcoded machine-specific path (`F:\Repositories\GitHub\jans\Janus`); also writes `trust_level = "trusted"` into the user's Codex config | Maintainability |
| M-07 | **Low** | all workflows | — | Node version inconsistent across workflows: `"latest"` (3×), `"24"` (2×), `"22"` (1×) | Maintainability |
| M-08 | **Low** | `.github/workflows/dependabot-maintenance.yml` | 35 | Uses `actions/checkout@v4` while everything else uses `@v5` | Maintainability |
| M-09 | **Info** | `docs/todo1`, `docs/todo2`, `docs/todo-7.3` | — | Extension-less planning files committed under `docs/`; `todo1` contains adversarial anti-debugging/anti-tamper "how-to" content (policy/review risk) | Maintainability |
| D-01 | **High** | `.github/workflows/build.yml` | 12–14, 357–368 | `contents: write` at workflow level + auto-release on every branch push is an over-broad trust boundary and pollutes the release channel | Design / Release |
| D-02 | **Medium** | `.github/workflows/build-artifacts.yml` | 57 | GHCR login uses `username: ${{ github.actor }}` (should be the package owner) | Design |
| D-03 | **Medium** | `build.yml`, `codeql.yml`, `lumen-ui-tuner.yml` | build:21; codeql:31; tuner:45 | Redundant `GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}` env on every job | Design |
| D-04 | **Medium** | `gradle.properties` | 5 | `android.suppressUnsupportedCompileSdk=37.0` hides unsupported-SDK warnings | Design |
| D-05 | **Low** | `settings.gradle.kts` | 9–26 | Composite-build opt-in for CRooot is clean and well documented | Design (positive) |
| R-01 | **High** | `.github/workflows/build.yml` | 357–368 | Every push to **any branch** creates a GitHub Release (`draft:false`, `make_latest:true`, `prerelease:false`) with tag `v{version}-{short_sha}` → feature-branch builds become the app's auto-update source (`releases/latest`) | Release |
| R-02 | **High** | `.github/workflows/build.yml` | 279–285 | Asset picker falls back to `app-release-unsigned.apk`; if signing silently failed, an unsigned APK would be published as a release | Release |
| R-03 | **Medium** | `.github/workflows/build.yml` / `release.yml` | build:200; release:181 | `PROJECT_LUMEN_RELEASE_CERT_SHA256` is derived from the actual keystore in build.yml but read from a secret in release.yml → inconsistent integrity enforcement | Release |
| R-04 | **Medium** | `build.yml` / `release.yml` | build:61; release:64 | `versionCode = GITHUB_RUN_NUMBER` is not monotonic across the two workflows' independent run-number sequences, and is unrelated to the version name → potential versionCode collisions/regressions | Release |
| R-05 | **Low** | `build.yml` / `release.yml` | build:359; release:296 | `softprops/action-gh-release@v2` is an unverified third-party action; consider pinning to a commit SHA | Release / Security |
| R-06 | **Low** | `release.yml` | 299 | Prerelease detection only via tag-name substring (`alpha`/`beta`/`rc`) | Release |

---

## 4. Detailed Findings

### 4.1 Security

**S-01 (Critical) — Hardcoded Maven credentials over HTTP.** `settings.gradle.kts:33-40`:
```kotlin
maven {
    isAllowInsecureProtocol = true
    url = uri("http://nexus.itgsa.com:5566/repository/release/")
    credentials {
        username = "developer"
        password = "developer!@#"
    }
}
```
Every Gradle build in the repo (local and CI) resolves dependencies from a third-party Nexus server over plaintext HTTP using a fixed, publicly-visible credential pair. Anyone who can read the repo can authenticate to that Nexus and, because the transport is unencrypted, a MITM can substitute arbitrary artifacts (JAR/AAR) that Gradle will execute. Fix: remove the repo and credentials, or move both to an authenticated, HTTPS-hosted repository provisioned from CI secrets.

**S-02 (High) — Insecure dependency transport.** Same block. Combined with `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, this repo is a single misconfigured server away from a supply-chain compromise. `isAllowInsecureProtocol = true` should never be used for third-party repositories.

**S-03 (High) — Un-pinned remote deploy script executed with production secrets.** `build-artifacts.yml:17` sets `DEPLOY_SCRIPT_REF: main`, and lines 130–136 download `https://raw.githubusercontent.com/Chloemlla/Happy-TTS/main/scripts/deploy_image.js` into the runner, then line 157 executes it with `SERVER_ADDRESS`, `USERNAME`, `PRIVATE_KEY` (SSH key), `CONTAINER_NAMES`, `ADMIN_PASSWORD`, `LUMEN_OUTEMAIL_API_KEY`, `LUMEN_REQUEST_SIGNING_SECRET` in the environment. This is both a supply-chain risk (the script's content is controlled by whatever is on `main` of that other repo at runtime) and a credential-exposure risk. The ref should be pinned to a commit SHA and the script should be vendored into this repo and reviewed.

**S-04 (High) — Production deployment on every main push.** The `deploy-image` job runs for any non-PR event (`if: github.event_name != 'pull_request'`), i.e., every push to `main` and every manual dispatch. There is no `environment:` block, no approval, and no confirmation step. A bad merge auto-deploys to production.

**S-05 (High) — PAT-based auto-push and force-merge.** `dependabot-maintenance.yml` checks out with `secrets.USER_PAT`, and the `fix-alerts` job commits and pushes directly to `main` (lines 69–80). The `manage-prs` job can `force-merge` Dependabot PRs via the GitHub API after a single loose check-name match (lines 181–231). A personal access token bypasses branch-protection rules that the automatic `GITHUB_TOKEN` would respect.

**S-06 (Medium) — Known default request-signing secret.** `app/build.gradle.kts:119` and `backend/src/config.rs:66` both default to `project-lumen-local-request-signing-key`. The client compiles this value into `BuildConfig` and the native `.so` whenever CI doesn't inject the real secret (local builds, CodeQL's `assembleDebug`, any future misconfiguration). The backend only logs a startup warning (`server.rs:152-154`) and continues. If request signing is ever enabled with defaults, request forgery is trivial. Recommendation: fail closed in the backend when the default secret is used with `LUMEN_REQUIRE_REQUEST_SIGNING=true`.

**S-07 (Medium) — Production endpoints as build fallbacks.** `app/build.gradle.kts:74,81` fall back to `https://eye.chloemlla.com/api` and `https://tts.chloemlla.com`. CI debug builds (e.g., `codeql.yml`'s `assembleDebug`) therefore target production. A debug build with the S-06 default secret and no pinning is a credible path to abusing production endpoints if signing is ever enabled with the default.

**S-08 (Medium) — Container missing CA certificates.** The backend uses `reqwest` with `rustls-tls` (system root store — see `backend/Cargo.toml:13`), and `outemail.rs` makes outbound HTTPS calls. The final stage `debian:bookworm-slim` (`Dockerfile:20-36`) never installs `ca-certificates`. TLS verification of outbound HTTPS will fail at runtime in the deployed container. Add `RUN apt-get update && apt-get install -y ca-certificates && rm -rf /var/lib/apt/lists/*`.

**S-09 (Medium) — Telemetry token embedded in every APK.** `app/build.gradle.kts:151` writes the telemetry access token into `BuildConfig.TELEMETRY_ACCESS_TOKEN`. Any bearer token shipped inside an APK is extractable; treat it as a public value and rotate, or gate telemetry through an authenticated device handshake instead.

**S-10 (Low) — Logs key alias and keystore length.** `build.yml:104` prints `KEY_ALIAS value: …` and `build.yml:101` prints `KEYSTORE_BASE64 length`. Not a direct leak, but it discloses secret metadata in logs. Delete or redact.

**S-11 (Low) — Signing secret on the CMake command line.** `app/build.gradle.kts:175` passes `-DLUMEN_REQUEST_SIGNING_SECRET=…` as a CMake argument. Gradle/CMake can echo these in diagnostics or build scans. Prefer writing the value into a generated header via a task that masks the value.

**S-12 (Low) — Live keystore beside the checkout.** `project_lumen-release.jks` exists in the repo working directory. It is correctly ignored (`*.jks` in `.gitignore`, and listed in `.dockerignore`), so it is not a committed-secret incident — but live release signing material should not live inside a source checkout at all. Move it to a secrets manager / out of the tree.

### 4.2 Stability

**ST-01/ST-02 — Trigger surface and fork-PR breakage.** `build.yml` runs on `push: branches: "**"` with no path filter, so every commit on any branch triggers the full release pipeline. The `Write signing config` step (line 85) has no `if:` guard and hard-fails when the four signing secrets are absent; on fork PRs GitHub does not provide secrets, so every fork PR fails the pipeline before any test runs. Gate signing behind `github.event_name != 'pull_request'` or `github.event.pull_request.head.repo.full_name == github.repository`.

**ST-03 — NDK version mismatch.** `gradle.properties:7` requests NDK `28.2.13676358`; the three workflows' inline license step installs `ndk;30.0.15729638`. AGP must then auto-download the requested NDK at build time (slow, and a failure point in restricted networks). The purpose-built composite action `.github/actions/setup-android-native-toolchain/action.yml` reads the correct versions from `gradle.properties` but is commented out in every workflow. Re-enable it and delete the inline NDK install.

**ST-04 — Tagged releases skip baseline-profile generation.** `build.yml` runs `:app:generateBaselineProfile` before `assembleRelease`; `release.yml` does not, and no baseline profile is committed to the repo. The artifact that users actually get from tagged releases is therefore built without the startup-optimized profile. Run the same baseline step (or commit the generated `baselineProfiles/` output) in `release.yml`.

**ST-05 — Missing npm lockfiles.** `backend/admin/package-lock.json`, `docs/package-lock.json`, and `remotion/android-product-animation/package-lock.json` are absent from the repo; the Dockerfile and CI run bare `npm install`. Combine with unpinned ranges (`^19.1.0`, `^5.4.11`) → non-reproducible and supply-chain-exposed installs. Commit lockfiles and use `npm ci` / `--frozen-lockfile`.

**ST-06 — CodeQL timeout.** `codeql.yml:22` sets `timeout-minutes: 30` for a manual `java-kotlin` build that runs `gradle assembleDebug` from cold-ish cache. That's a plausible timeout failure. Raise to 60 for the Java job.

**ST-07 — Inconsistent retry behavior.** The backend-sync `curl` in `build.yml` uses `--connect-timeout 10 --max-time 60 --retry 4 --retry-all-errors --retry-delay 5`; the identical step in `release.yml` has no timeout or retry. Extract the step to a shared script/action so both behave identically.

**ST-08–ST-10 — Minor.** SDK/NDK components are re-installed on every run; third-party actions use floating tags; `lumen-crash-sdk-release.yml` omits the SDK-license/NDK step the other workflows have.

### 4.3 Performance

- **P-01** — The Dockerfile's `cargo build` and `npm install && npm run build` are single `RUN` layers, so the dependency compilation layer is invalidated by any source change. Split `cargo fetch`/dependency build and `npm ci` into their own layers before copying sources.
- **P-02** — Re-enabling the native-toolchain composite action also fixes the wasted NDK 30 install + NDK 28 auto-download per build.
- **P-03** — npm installs are uncached across all workflows (no `actions/cache` for `~/.npm` / `node_modules`).
- **P-04 (positive)** — Gradle build cache is enabled and the Docker image uses `type=gha,mode=max` scoped caching.

### 4.4 Testing & Quality Gates

The most serious testing defect is **ordering**: in `build.yml`, the `Automatic release` step (line 357) runs *before* `Run state machine unit tests` (line 423) and `Run Android lint` (line 433). A red unit test or a lint failure does not prevent a signed release from being published. Quality gates must precede artifact publication. `release.yml` at least runs tests and lint before assembling, but it also publishes before... no — release.yml runs tests/lint first (lines 77–93) and then assembles/signs (line 166+), which is the correct order; only `build.yml` has the inversion. Additionally, only `lintDebug` runs — there is no `lintRelease` gate, and the connected/instrumented UI test suite is gated behind narrow path filters in the tuner workflow.

### 4.5 Maintainability & Documentation

- **M-01** — The signing-config, version-reading, asset-prep, manifest-generation, and backend-sync steps are near-verbatim copies across `build.yml` and `release.yml`. Drift has already occurred (different `curl` retry flags, different cert-SHA derivation). Extract into composite actions under `.github/actions/` or into `scripts/`.
- **M-02** — `scripts/fix-dependabot-alerts.js` is a 1,742-line import from another project; it drives pnpm (not used anywhere in this repo) and expects a `rust-services/` directory. It is likely only partially functional here and is a maintenance liability.
- **M-03/M-04/M-05** — Documentation/behavior mismatch: `README.md` says minSdk 26 (real: 29) and says the docs workflow "publishes" the site; `docs/homepage-guide.md:53` claims the docs deploy to GitHub Pages on default-branch push; but `vitepress-docs.yml` only uploads an artifact, and no `actions/deploy-pages` exists anywhere. Either implement the Pages deployment (the `pages: write` / `id-token: write` permissions already granted imply it was intended) or fix the docs.
- **M-06** — `Install-CodexTrellis.ps1` defaults `SourceRoot` to an absolute machine-specific path and (with `-ConfigureUserConfig`) writes `trust_level = "trusted"` into the user's `~/.codex/config.toml` — an auto-trust foot-gun.
- **M-09** — `docs/todo1` contains adversarial anti-debugging/anti-hook guidance (multi-process `ptrace`, breakpoint wiping, OLLVM obfuscation, Frida detection). Besides being unprofessional for a docs site, such techniques can conflict with store policy and are a review liability. It also sits in `docs/` as a non-Markdown extension-less file.
- Version drift: Node runtimes differ across workflows (`"latest"`, `24`, `22`); `actions/checkout` is `@v4` in one workflow and `@v5` elsewhere.

### 4.6 Design

- **D-01** — `contents: write` at the workflow level, combined with the auto-release step, gives every commit (even on feature branches) the ability to publish a "latest" release. Reduce permissions to `contents: read` where releases aren't created, or gate releases to a protected branch.
- **D-02** — GHCR login with `username: ${{ github.actor }}` is fragile for org-owned or bot-driven pushes; prefer the package owner.
- **D-03** — `GITHUB_TOKEN` env is redundant (available to the runner regardless) and inflates the surface.
- **D-04** — `android.suppressUnsupportedCompileSdk=37.0` masks an unsupported-compile-SDK warning rather than addressing it.

### 4.7 Release

- **R-01 (the headline release defect)** — `build.yml` `on: push: branches: "**"` + the `Automatic release` step mean every push to any branch creates a GitHub Release with `make_latest: true` and `prerelease: false`. The client's update checker reads `releases/latest` (see `docs/SYSTEM_UPDATE_STRATEGY_IMPLEMENTATION.md`), so a feature-branch or WIP build can become the "latest" update users are offered. Releases should only be produced from `main` or from explicit tags, with `make_latest` reserved for true stable releases.
- **R-02** — The aggregate-APK picker includes `app-release-unsigned.apk` as a fallback; if signing ever silently fails (e.g., keystore not applied), an unsigned APK could be published. Fail the step unless a signed APK is found.
- **R-03** — `build.yml` derives the release-cert SHA-256 from the decoded keystore; `release.yml` reads it from a secret. If the secret is stale or missing, the two workflows produce builds with different integrity enforcement.
- **R-04** — `versionCode = GITHUB_RUN_NUMBER` is unique per workflow run but not monotonic across workflows (build.yml vs release.yml have independent counters) and can repeat for unrelated version names. Derive `versionCode` from the version name (the repo already has `projectLumenVersionCodeFromName`) or from a monotonically increasing release source.
- **R-05/R-06** — Pin `softprops/action-gh-release` to a SHA; consider an explicit prerelease flag rather than tag-substring matching.

---

## 5. Top Recommended Fixes (priority order)

1. **Remove the hardcoded credentials + HTTP Maven repo** from `settings.gradle.kts` (S-01/S-02) — critical.
2. **Pin and vendor the deployment script** in `build-artifacts.yml`; add an `environment:` gate with manual approval (S-03/S-04).
3. **Stop auto-releasing on every branch push**; release only from `main`/tags and move quality gates (tests/lint) before release creation (R-01, T-01).
4. **Run `generateBaselineProfile` in `release.yml`** and re-enable the native-toolchain composite action (ST-03/ST-04).
5. **Add `ca-certificates` to the Docker final image** and commit npm lockfiles with `npm ci`/`--frozen-lockfile` (S-08, ST-05, P-01).
6. **Fail closed on default secrets** in the backend and remove the client-side default signing key / production URL fallbacks (S-06, S-07).
7. **Extract the duplicated ~200-line signing/release blocks** into composite actions or shared scripts (M-01).

---

*Generated as a static review artifact; no builds were run locally per repository policy (all builds/tests run in GitHub Actions).*
