# Seal vs Lumen UI pattern gap

## Seal strengths worth adopting

1. **Shape system**
   - `Shapes(extraSmall=8, small=12, medium=16, large=20, extraLarge=28)`
   - Components consistently pull `MaterialTheme.shapes.*` instead of hard-coded one-off radii.

2. **Soft preference surfaces**
   - Rows use `surfaceContainerLow` + large shape + light vertical padding.
   - Leading icon sits in a small primary-container chip.
   - Disabled state via opacity helper, not layout thrash.

3. **Chrome hierarchy**
   - Settings use `LargeTopAppBar` with exit-until-collapsed scroll behavior.
   - Content uses LazyColumn + scaffold insets, less custom math.

4. **Typography polish**
   - System Material typography + paragraph line break + content text direction.
   - Seal keeps readable density; Lumen forces mono everywhere which increases visual noise for long settings text.

5. **Button / control padding**
   - Icon+text buttons share content paddings and medium shapes.
   - Consistent labelLarge usage.

## Lumen current state

1. Shared card style: 8dp radius, elevation 2, outline border.
2. Settings rows: bordered surfaceVariant chips stacked inside bordered section cards → double boxing.
3. Custom collapsing primary-colored top bar driven by scroll state CompositionLocal.
4. Mono JetBrains font family for nearly every text role.
5. Page padding tokens exist (`12/12/12/18`, section gap 12) and are good reuse points.

## Feasible adoption map for Lumen

### Approach A — Foundation + shared primitives (Recommended MVP)

How:
- Add Material `Shapes` to `ProjectLumenTheme`.
- Soften `LumenCardShape` / card colors / elevation defaults toward Seal surface language.
- Restyle `SwitchRow` / settings rows / section chrome using Seal-like preference rows.
- Slightly retune page tokens and bottom-nav container polish.
- Keep Lumen top-bar structure initially, but move color from solid primary toward surface + onSurface hierarchy (or hybrid).

Pros:
- Highest visual impact / lowest product risk.
- Touches shared primitives so most screens improve automatically.
Cons:
- Secondary screens with hard-coded radii may still lag until follow-up.

### Approach B — Full chrome rewrite

How:
- Replace custom top bar with Material LargeTopAppBar collapse.
- Rewrite settings into Seal-like nested preference pages.
- Broad navigation motion polish.

Pros:
- Closest to Seal feel.
Cons:
- Much larger blast radius; more regression surface for dense Lumen settings.

### Approach C — Theme tokens only

How:
- Only change colors/shapes/typography tokens; leave component structure.

Pros:
- Smallest diff.
Cons:
- Nested bordered cards remain; UX improvement is weak.

## Recommendation

Ship **Approach A** first. It imports Seal's best surface/spacing language into Lumen's existing architecture.

## Outcome (2026-07-16)

MVP shipped on Approach A plus a destination-aware top-bar split:

* Shared soft surfaces landed: `LumenShapes` 8/12/16/20/28, zero-elevation cards, no default border, preference rows with icon chips.
* Dock destinations keep compact `TopAppBar` (Settings included) to avoid sparse expanded chrome.
* Secondary destinations keep `LargeTopAppBar` + `exitUntilCollapsedScrollBehavior`.
* Spec captured in `.trellis/spec/frontend/android-compose-surfaces.md`.
* Follow-ups intentionally deferred: onboarding / product-demo / developer-only hard borders and some page-local 8dp radii.
