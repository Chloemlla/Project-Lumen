# VitePress Docs Site

> Contracts for the `docs/` VitePress documentation site.

## Scenario: Docs Site Build And Publish Contract

### 1. Scope / Trigger

- Trigger: changing `docs/package.json`, `docs/.vitepress/**`, `docs/index.md`, `docs/public/**`, or `.github/workflows/vitepress-docs.yml`.
- Scope: the documentation site is owned by `docs/` and must not require changes to Android, Rust backend, or root Gradle configuration.
- Constraint: actual docs install/build/preview commands are run by GitHub Actions, not on the local workstation.

### 2. Signatures

Commands are declared in `docs/package.json`:

```json
{
  "scripts": {
    "docs:dev": "vitepress --host 0.0.0.0",
    "docs:build": "vitepress build",
    "docs:preview": "vitepress preview --host 0.0.0.0"
  }
}
```

The verification workflow is `.github/workflows/vitepress-docs.yml`.

### 3. Contracts

- `VITEPRESS_BASE` controls the published base path.
- GitHub Pages uses `VITEPRESS_BASE=/Project-Lumen/`.
- `docs/.vitepress/config.ts` must derive `base` and head asset URLs from the same base path.
- Static assets used by the docs site live under `docs/public/`.
- Site navigation and sidebar links are maintained in `docs/.vitepress/config.ts`.
- The homepage is `docs/index.md`; its standalone explanation is `docs/homepage-guide.md`.

### 4. Validation & Error Matrix

| Condition | Required outcome |
| --- | --- |
| `VITEPRESS_BASE` is unset | Config falls back to `/Project-Lumen/`. |
| GitHub workflow runs on docs changes | It installs dependencies in `docs/` and runs `npm run docs:build`. |
| Push targets the default branch | Workflow uploads and deploys the Pages artifact. |
| Pull request changes docs | Workflow builds and uploads the artifact but does not deploy Pages. |
| Local contributor wants to verify | Do not run install/build locally when repository instructions prohibit it; rely on workflow. |

### 5. Good/Base/Bad Cases

- Good: add a Markdown page, add it to `themeConfig.sidebar`, and let `.github/workflows/vitepress-docs.yml` build it.
- Base: change only copy in an existing Markdown page; no config change is required.
- Bad: hard-code `/Project-Lumen/` in one asset URL while using a different `base`, because Pages and preview paths can drift.

### 6. Tests Required

- GitHub Actions must run `npm install` and `npm run docs:build` from `docs/`.
- The build artifact path must be `docs/.vitepress/dist/**`.
- Pages deployment must only happen on push to the default branch.
- For homepage changes, assert the page still links to `homepage-guide`, product capabilities, and the roadmap.

### 7. Wrong vs Correct

#### Wrong

```ts
export default defineConfig({
  base: "/Project-Lumen/",
  head: [["link", { rel: "icon", href: "/favicon.png" }]],
});
```

The base path and asset path are not coupled, so the icon can break when the site is published under a repository path.

#### Correct

```ts
const basePath = process.env.VITEPRESS_BASE ?? "/Project-Lumen/";

export default defineConfig({
  base: basePath,
  head: [["link", { rel: "icon", href: `${basePath}lumen-icon.png` }]],
});
```

The same source of truth drives both routing and head assets.
