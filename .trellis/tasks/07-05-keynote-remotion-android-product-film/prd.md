# Keynote-Length Remotion Android Product Film

## Goal

Upgrade the existing Project Lumen Android Remotion animation into a high-polish, Apple-keynote-inspired Chinese product film that runs at least 20 minutes and covers every feature area documented in `docs/REMOTION_ANDROID_PRODUCT_ANIMATION_GUIDE.md`.

## What I Already Know

* User requested the most visually polished, smooth Remotion animation possible.
* Required runtime: not less than 20 minutes.
* Required style: premium keynote/product-launch presentation inspired by Apple event pacing, without using Apple assets or branding.
* Required scope: include all feature areas from `docs/REMOTION_ANDROID_PRODUCT_ANIMATION_GUIDE.md`.
* Current Remotion package exists at `remotion/android-product-animation/`.
* Composition ID is `LumenAndroidProductAnimation`.
* Build, type-check, render, and tests must run in GitHub Actions, not locally.

## Requirements

* Expand `LumenAndroidProductAnimation` duration to at least 20 minutes.
* Cover all Android guide areas:
  * product positioning, app launch, main navigation, onboarding, recommended setup
  * home dashboard, reminder loop, Pomodoro runtime, active sensing
  * statistics, goals, exports, templates, settings, permissions, privacy
  * sound, appearance, backup/import, remote account, entitlements
  * Shizuku advanced protection, app network controls, diagnostics
  * developer debug, crash reports, automatic update, WebView, Open API
  * local data, MMKV/secure credentials, Room, Android services, data flow
* Make the animation more cinematic and premium: large typography, chapter pacing, refined phone mockups, product surfaces, data ribbons, signal panels, charts, capability walls, and closing summary.
* Keep the Remotion implementation deterministic and data-driven.
* Use the Android launcher round icon already copied to `public/lumen-icon.png`.
* Do not introduce real backend calls, real sensor capture, user data, or external media dependency.
* Keep React Native out of scope.

## Acceptance Criteria

* [ ] `totalDurationInFrames / fps >= 1200` seconds.
* [ ] The data model includes chapters for all guide feature areas.
* [ ] Remotion visuals include phone UI, chapter text, feature rails, metrics, sensing, data flow, and capability summaries.
* [ ] Chinese visible copy and narration text are present throughout.
* [ ] Render workflow remains GitHub Actions based.
* [ ] Local verification is limited to static inspection.

## Technical Approach

Use a data-driven long-form chapter system. Each chapter owns timing, visual theme, phone state, signals, capabilities, narrative bullets, and flow nodes. Components render reusable premium keynote surfaces based on the active chapter instead of hard-coding one-off timelines.

## Decision (ADR-lite)

**Context**: A 20+ minute product film must cover many feature areas without becoming a fragile hand-coded sequence.

**Decision**: Use a deterministic chapter registry and reusable cinematic Remotion components.

**Consequences**: The video can cover the full guide in a maintainable way. The tradeoff is that the production behaves like a polished generated keynote rather than a fully bespoke animation for every second.

## Out of Scope

* Local Remotion render/build/test.
* React Native migration.
* Backend admin dashboard, Rust API UI, docs site, or UI tuner product presentation.
* Real user data, real camera images, real API calls, or external asset fetching.

## Technical Notes

* Relevant spec: `.trellis/spec/frontend/directory-structure.md` §2.2.
* Relevant package: `remotion/android-product-animation/`.
* Current static asset contract: `remotion/android-product-animation/public/lumen-icon.png`.
