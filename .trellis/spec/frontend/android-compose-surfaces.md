# Android Compose Surfaces

> Soft Material 3 surface language for Project Lumen Android Compose screens.
> Status: filled from the Seal UI/UX base + settings chrome work.

---

## 1. Scope / Trigger

Use this spec when changing:

- theme shapes / card defaults
- settings preference rows / section chrome
- top app bar hierarchy
- shared Compose surface components under `app/.../app/ProjectLumen*.kt`

Do **not** use this for web (`apps/web`) radius tokens; web still follows `component-guidelines.md`.

---

## 2. Shape and surface contracts

### Theme shapes

`ui/theme/Shape.kt` must expose:

| Token | Radius | Typical use |
|-------|--------|-------------|
| `extraSmall` | 8dp | tiny chips / dense tiles |
| `small` | 12dp | icon chips |
| `medium` | 16dp | medium containers |
| `large` | 20dp | cards / preference rows |
| `extraLarge` | 28dp | large sheets / hero shells |

`ProjectLumenTheme` must pass `shapes = LumenShapes`.

### Shared card defaults

| API | Contract |
|-----|----------|
| `LumenCardShape` | 20dp |
| `LumenPreferenceShape` | 20dp |
| `LumenIconChipShape` | 12dp |
| `lumenCardColors()` | `surfaceContainerLow` / `onSurface` |
| `lumenCardElevation()` | default 0dp |
| `lumenCardBorder()` | `null` |

Preference rows (`SwitchRow`, `NumberSlider`, `FileSettingRow`, etc.) should use:

1. soft container (`surfaceContainerLow`)
2. leading icon chip (`primaryContainer` chip + `LumenIconChipShape`)
3. no outline border by default

---

## 3. Top-bar hierarchy

| Destination type | App bar | Scroll behavior | Color |
|------------------|---------|-----------------|-------|
| Dock / bottom-nav (`showInBottomNav = true`) | compact `TopAppBar` | pinned | `surface` / scrolled `surfaceContainer` |
| Secondary page | `LargeTopAppBar` | `exitUntilCollapsed` | same surface hierarchy |

Rules:

- Never paint the primary app chrome as a solid primary-colored bar.
- Keep `LocalLumenPageScrollState` for settings anchor scrolling.
- Scaffold must wire `nestedScroll(topBarScrollBehavior.nestedScrollConnection)`.

Why dock stays compact: expanded LargeTopAppBar on primary tabs leaves a sparse empty band before first content.

---

## 4. Validation & error matrix

| Condition | Result |
|-----------|--------|
| Shared card/section reintroduces default hard border | Fail surface review |
| Shared card restores default elevation > 0 | Fail surface review |
| Dock page uses expanded LargeTopAppBar | Fail chrome review unless explicit product decision |
| Secondary page loses nestedScroll/collapse wiring | Fail chrome review |
| Settings behavior (section expand, anchors, permission force-expand) changes while restyling | Fail functional review |

---

## 5. Good / Base / Bad cases

### Good

```kotlin
Card(
    shape = LumenCardShape,
    colors = lumenCardColors(),
    elevation = lumenCardElevation(),
    border = lumenCardBorder(),
) { ... }

val useExpandedTopBar = !currentDestination.showInBottomNav
```

### Base

Settings dock page uses compact surface top bar + soft preference sections; secondary About/Developer pages may expand/collapse.

### Bad

```kotlin
// Don't reintroduce double boxing
Card(
    shape = RoundedCornerShape(8.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
) {
    Row(Modifier.border(1.dp, outlineVariant, RoundedCornerShape(8.dp))) { ... }
}
```

---

## 6. Tests required

- Static review of shared surface defaults in `ProjectLumenAppConstants` / shared components.
- Screenshot or UI-token checks for top-bar modes when chrome tokens change.
- No local full Gradle build/test; GitHub workflow owns compile verification.

Assertion points:

1. `LumenShapes` remains `8/12/16/20/28`.
2. Shared card border default is null and elevation default is 0.
3. Dock destinations keep compact top bar; secondary destinations keep LargeTopAppBar collapse path.

---

## 7. Wrong vs Correct

### Wrong

- Force every page, including Settings, onto LargeTopAppBar because Seal settings pages do that.
- Soften only one settings row while leaving `SettingsSection` / `ActionCard` / feedback cards with hard borders.

### Correct

- Soften shared primitives first so most screens inherit the surface language.
- Keep dock chrome compact and secondary chrome collapsible.
- Treat onboarding / product-demo / developer-only bordered tiles as follow-ups unless they sit on the settings critical path.

---

## Design Decision: Dock compact vs secondary LargeTopAppBar

**Context**: Seal-like LargeTopAppBar looks correct on secondary pages, but on bottom-nav primary destinations it wastes vertical space before content.

**Options considered**:
1. Global LargeTopAppBar everywhere
2. Compact top bar everywhere
3. Destination-aware hierarchy (chosen)

**Decision**: Destination-aware hierarchy. Shared surfaces always soft; top-bar expansion depends on `Destination.showInBottomNav`.

**Related files**:
- `app/src/main/java/com/projectlumen/app/ui/theme/Shape.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenAppConstants.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenSharedComponents.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenFormControls.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenApp.kt`
- `design/lumen-ui-tokens.json`
