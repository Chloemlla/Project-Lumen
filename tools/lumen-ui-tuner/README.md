# Project Lumen UI Tuner

React 19 editor for Project Lumen UI tokens.

## What it edits

- `design/lumen-ui-tokens.json`
- Android top bar sizing, title offsets, title typography, page width, page padding, and section spacing.
- Preview-only sample text and preview colors.

## Editor behavior

- Click a preview element to inspect its token path and Android Compose mapping.
- Drag the primary or secondary top bar title horizontally to update title-start tokens.
- Edit preview text inline, then save or export JSON.
- In Chromium-based browsers, Open/Save can write the selected JSON file through the File System Access API.

## Dependency flow

Do not install dependencies locally when following repository rules.

The `Lumen UI Tuner Verification` workflow installs npm dependencies remotely, builds the tuner, generates `package-lock.json`, commits lockfile changes back to the branch, and uploads the built editor as a workflow artifact.
