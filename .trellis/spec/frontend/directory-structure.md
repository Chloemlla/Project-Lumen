# Frontend Directory Structure

> How `apps/web/` is organized. Status: greenfield — feature-based convention to follow; ⏳ marks details to confirm with real code.

---

## 1. Stack (✅ decided, doc 05 §2 / D13)

**Vite + React + TypeScript**; **Tailwind + shadcn/ui + Radix**. Real-time via a WebSocket client. Shared types/schema come from `packages/shared` (never redeclared locally).

## 2. Layout (⏳ feature-based)

```
apps/web/src/
├─ app/             # app shell, router, providers (Query/WS/theme)
├─ features/        # one folder per Code-mode surface (doc 02 §2.2)
│  ├─ session/         # conversation + supervisor plan/decisions
│  ├─ activity-stream/ # real-time supervisor↔CLI event stream (signature surface)
│  ├─ plan/            # plan / task-decomposition view
│  ├─ diff/            # diff view + file tree
│  ├─ run-panel/       # test/build/verify results
│  ├─ best-of-n/       # candidate comparison + adjudication
│  ├─ repo-connect/    # GitHub OAuth / PAT / repo picker
│  └─ model-config/    # cc/cx provider + model config (cc-switch-style)
├─ components/      # shared presentational components (shadcn/ui wrappers)
├─ hooks/           # cross-feature reusable hooks
├─ lib/             # pure client utilities (api client, ws client, formatters)
└─ types/           # local-only UI types (cross-cutting types live in packages/shared)
```

## 2.1 Admin Dashboard Layout (`backend/admin/`)

The backend admin dashboard is a React 19 + TypeScript Vite app whose source is
served only after a workflow/Docker build emits static assets.

### 1. Scope / Trigger

- Trigger: any change to the admin dashboard UI, Vite config, Docker copy path,
  or `LUMEN_ADMIN_STATIC_DIR`.
- Source scope: `backend/admin/index.html`, `backend/admin/src/**/*.tsx`,
  `backend/admin/src/**/*.ts`, `backend/admin/src/styles.css`.
- Runtime scope: `backend/admin/dist/` is the build output and is ignored by git.

### 2. Signatures

```text
cd backend/admin
npm install
npm run build
```

These commands are executed by GitHub Actions and Docker builds, not local
developer machines when repository policy forbids local build/test execution.

### 3. Contracts

- Package contract: `backend/admin/package.json` owns React/Vite dependencies.
- Build contract: `npm run build` runs `tsc -b && vite build`.
- Runtime contract: Rust serves `LUMEN_ADMIN_STATIC_DIR`, whose default is
  `backend/admin/dist`.
- Docker contract: the image build must copy only the built `dist/` into the
  runtime image path used by `LUMEN_ADMIN_STATIC_DIR`, and keep the built files
  available at the legacy `/app/backend/admin` path because the deployment
  script inherits existing container environment variables.
- API contract: the dashboard calls `/api/health`, `/api/admin/auth/login`,
  `/api/admin/auth/refresh`, `/api/admin/me`, `/api/admin/dashboard`, and
  `/api/admin/actions`. Client-only utilities such as backup JSON download must
  not be routed through `/api/admin/actions`.
- `/api/admin/actions` server actions are exactly `change-plan`, `revoke-pro`,
  `push-template`, `force-update`, and `save-allowlist`; any other button action
  must be handled client-side as copy, download, token refresh, or health probe.

### 4. Validation & Error Matrix

- `dist/index.html` missing -> backend logs `admin dashboard index file does not exist`.
- `/api/admin/dashboard` returns `401` -> dashboard attempts one refresh-token
  flow when a refresh token is available, then clears session state on failure.
- Pasted admin token -> dashboard validates it with `/api/admin/me` before
  treating it as an active session token.
- Unsupported `/api/admin/actions` action -> backend returns `400`; the client
  must not POST unsupported local utility actions such as `download-backup`.
- Non-local HTTP origin -> sensitive admin action buttons stay disabled.
- Empty live data -> modules render empty states rather than static fake records.

### 5. Good/Base/Bad Cases

- Good: React component source is `.tsx`, runtime JSON is narrowed at the API
  boundary, and tokens stay in memory for the current tab.
- Base: `backend/admin/src/model/*` maps unknown API JSON into dashboard view
  models before components render it.
- Bad: committing hand-written runtime JS under `backend/admin/assets/`, serving
  TSX source directly, or storing admin access/refresh tokens in `localStorage`.

### 6. Tests Required

- GitHub workflow: `backend/admin` must run `npm install`, `npm run build`,
  and upload the `backend/admin/dist/**` artifact.
- Docker workflow: image build must run the same admin build, verify
  `dist/index.html` exists, and copy `dist/` to both current and legacy runtime
  paths.
- Cross-layer action check: every action sent through `/api/admin/actions` must
  match the backend `apply_admin_action` allowlist, and client-only actions must
  never rely on an unsupported backend action name.
- Manual review: verify sensitive actions are disabled on non-local HTTP and
  enabled on HTTPS/localhost.

### 7. Wrong vs Correct

#### Wrong

```text
LUMEN_ADMIN_STATIC_DIR=backend/admin
<script type="module" src="./assets/app.js"></script>
```

#### Correct

```text
LUMEN_ADMIN_STATIC_DIR=backend/admin/dist
<script type="module" src="/src/main.tsx"></script>
```

## 2.2 Remotion Android Product Animation (`remotion/android-product-animation/`)

The Android product animation is a React/TypeScript Remotion package that
renders a Chinese Android client product video. It is separate from the
Kotlin/Compose Android app; shared behavior is represented through demo state
and product copy, not shared mobile UI components.

### 1. Scope / Trigger

- Trigger: any change to `remotion/android-product-animation/**`,
  `.github/workflows/remotion-android-product-animation.yml`, or the Android
  product animation guide.
- Source scope: `remotion/android-product-animation/src/**/*.ts`,
  `remotion/android-product-animation/src/**/*.tsx`,
  `remotion/android-product-animation/src/**/*.css`, package config files, and
  assets under `remotion/android-product-animation/public/`.
- Runtime scope: rendered videos are emitted under
  `remotion/android-product-animation/out/` and must stay ignored by git.

### 2. Signatures

```text
cd remotion/android-product-animation
npm install
npm run validate
npm run render
```

These commands are executed by GitHub Actions, not locally when repository
policy forbids local build/test execution.

### 3. Contracts

- Package contract: `package.json` owns Remotion, React, TypeScript, and icon
  dependencies for this animation package only.
- Composition contract: `src/Root.tsx` exposes Composition ID
  `LumenAndroidProductAnimation`.
- Data contract: `src/data/androidDemoState.ts` owns Chinese scene copy,
  timing, phone-state metrics, sensing metrics, and capability labels.
- Asset contract: `public/lumen-icon.png` is the local Remotion static asset for
  the Project Lumen icon.
- Output contract: `npm run render` writes
  `out/lumen-android-product-animation.mp4`.
- CI contract: `.github/workflows/remotion-android-product-animation.yml`
  installs CJK fonts before validation/render so Chinese text renders on Ubuntu.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Missing `public/lumen-icon.png` | Remotion validation/render fails instead of silently omitting the brand asset. |
| Missing CJK fonts on CI | Workflow installs `fonts-noto-cjk` before rendering. |
| Scene timing gap | `getSceneAtFrame` falls back to the closing scene, but scene ranges should still be reviewed. |
| Render output missing | Workflow upload uses `if-no-files-found: error`. |
| Local verification needed | Use static inspection only; actual validate/render remains in GitHub Actions. |

### 5. Good/Base/Bad Cases

- Good: scene copy and timing live in `androidDemoState.ts`, while visual
  components stay split by responsibility (`PhoneFrame`, `ScenePanel`,
  `SensorOverlay`).
- Base: a new scene adds one `DemoScene` entry and reuses existing component
  surfaces.
- Bad: rewriting the Kotlin/Compose Android client in React Native only to feed
  Remotion, or making the Remotion package call real Android/backend services.
- Bad: committing rendered MP4 files or `node_modules/`.

### 6. Tests Required

- GitHub workflow: run `npm install`, `npm run validate`, and `npm run render`
  in `remotion/android-product-animation`.
- Artifact check: upload
  `remotion/android-product-animation/out/lumen-android-product-animation.mp4`
  with `if-no-files-found: error`.
- Manual review: inspect the rendered artifact for nonblank frames, Chinese text
  rendering, readable phone UI, and alignment with the Android product guide.

### 7. Wrong vs Correct

#### Wrong

```text
Move the Android client to React Native so Remotion can reuse mobile screens.
```

#### Correct

```text
Keep Kotlin/Compose for the Android app, add a native demo surface, and render a
separate Remotion React composition from deterministic demo state.
```

## 3. Module organization

- A feature owns its components, hooks, and local state; it imports shared UI from `components/` and shared types from `packages/shared`.
- Promote a component/hook to the top-level `components/`/`hooks/` only when a **second** feature needs it (avoid premature sharing → needless complexity).

## 4. Naming conventions

- Components: `PascalCase.tsx`. Hooks: `useCamelCase.ts`. Other files: `kebab-case.ts`.
- One component per file; co-locate its styles/tests beside it.

## 5. Common mistakes to avoid

- Redeclaring server DTOs locally instead of importing from `packages/shared` (causes front/back type drift).
- A "utils dumping ground" — keep `lib/` purpose-named.
