# Android Launcher Shortcut Contracts

## Scenario: Static App Icon Shortcuts

### 1. Scope / Trigger

- Trigger: changing Android long-press launcher entries, app shortcut labels, or shortcut-to-screen routing.
- Applies to `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/shortcuts.xml`, shortcut string resources, and `MainActivity` launch request handling.

### 2. Signatures

- Manifest metadata: `android:name="android.app.shortcuts"` with `android:resource="@xml/shortcuts"` on `.MainActivity`.
- Static shortcuts: `<shortcut android:shortcutId="...">` containing an explicit `<intent android:targetPackage="com.projectlumen.app" android:targetClass="com.projectlumen.app.MainActivity">`.
- Existing launch actions:
  - `com.project.lumen.action.VIEW_DASHBOARD`
  - `com.project.lumen.action.TRIGGER_REST`
  - `com.project.lumen.action.START_VISUAL_MONITOR`

### 3. Contracts

- Shortcut XML lives under `app/src/main/res/xml/shortcuts.xml`.
- Shortcut labels must use resource strings in both `values/strings.xml` and `values-zh/strings.xml`.
- Shortcut intents must be explicit to `MainActivity`; do not add new launcher-visible activities for simple in-app destinations.
- Prefer existing `LumenOpenIntents.parseLaunchRequest(...)` actions when they already map to the target screen or command.
- Include `com.project.lumen.extra.SOURCE_APP=project_lumen` for app-owned shortcuts.

### 4. Validation & Error Matrix

- Missing `android.app.shortcuts` metadata -> launcher does not expose long-press entries.
- Missing localized label string -> Android resource merge fails in workflow build.
- Unknown shortcut action -> app opens but stays on the default Home route.
- Implicit shortcut intent -> launcher behavior can vary by resolver and package visibility.

### 5. Good/Base/Bad Cases

- Good: long-press app icon exposes stable entries for stats, rest, and visual monitor, each using explicit `MainActivity` intents.
- Base: adding another shortcut only adds one `<shortcut>` plus localized strings and maps to an existing launch action.
- Bad: duplicating navigation logic in a new Activity only to reach an existing Compose destination.
- Bad: hard-coding visible labels in `shortcuts.xml`.

### 6. Tests Required

- GitHub Actions Android build verifies manifest/resource merge.
- Manual launcher check on Android 8.0+ verifies long-press entries appear and route to the expected screen or command.
- If adding new shortcut actions, add a focused test around `LumenOpenIntents.parseLaunchRequest(...)`.

### 7. Wrong vs Correct

#### Wrong

```xml
<shortcut
    android:shortcutId="stats"
    android:shortcutShortLabel="Stats">
    <intent android:action="com.projectlumen.app.OPEN_STATS" />
</shortcut>
```

#### Correct

```xml
<shortcut
    android:shortcutId="dashboard"
    android:shortcutShortLabel="@string/shortcut_dashboard_short_label">
    <intent
        android:action="com.project.lumen.action.VIEW_DASHBOARD"
        android:targetClass="com.projectlumen.app.MainActivity"
        android:targetPackage="com.projectlumen.app" />
</shortcut>
```
