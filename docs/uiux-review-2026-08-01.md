# Project-Lumen UI/UX Review Report




---

# Project-Lumen UI/UX Responsive Review — 2026-08-01

## Overview
Automated scan of 34 Kotlin Jetpack Compose UI files for responsive layout issues. Found 8 issues total.

## Critical Issues (Overflow Risks / Hardcoded Sizes)

### app/src/main/java/com/projectlumen/app/app/ProjectLumenSettingsPrivacyCenter.kt:304
- **Element**: `PermissionControlTileItem`
- **Issue**: Hardcoded min width on grid tiles: Modifier.widthIn(min = 158.dp) inside a FlowRow (PermissionControlTileGrid -> LumenFlowRow). On a 360dp screen the section content width is ~312dp (360 - 12dp LumenPage gutters - 12dp SettingsSection card padding), so two tiles (158+158+8 spacing = 324dp) no longer fit and the intended 2-column permission grid silently collapses to a single column; the min width also prevents tiles from flexing, so the grid is 2-up on a 412dp device but 1-up on a 360dp device.
- **Why**: On small screens (360-412dp) the fixed 158dp minimum is larger than half the available row width, so FlowRow wraps each tile onto its own line. The tile never adapts to available space, producing a broken/sparse single-column grid instead of the designed two-column layout, and a large unused gutter on the left of each tile.
- **Fix**: Remove widthIn(min = 158.dp) and let the tiles share the row width: either wrap pairs of tiles in Rows where each tile uses Modifier.weight(1f), or use a LazyVerticalGrid with GridCells.Adaptive(minSize = 150.dp) / a chunked-row layout so tiles flex to whatever width is actually available on 360dp screens.


### app/src/main/java/com/projectlumen/app/app/ProjectLumenStatisticsCards.kt:227
- **Element**: `TrendCard per-day Row (Text + Box bar + Text)`
- **Issue**: Anti-pattern #6: Row containing two unweighted Text children (the 5-char date and the minutesLabel value) that have no maxLines/overflow, next to a weight(1f) trend bar, in a Row with no horizontalScroll.
- **Why**: The two Text widgets are measured at their intrinsic width before the weighted bar gets the remainder, and nothing constrains them to one line. Under a larger system font scale or a longer localized minutes_short format the labels widen, squeeze the weight(1f) progress bar toward zero width (the trend bar visually disappears), and, since the Row has no horizontalScroll, the label text can crowd/clip at the row edges on a 360dp screen instead of the bar keeping a stable width.
- **Fix**: Add maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false to both the date and minutes Texts (or give them fixed compact widths), and optionally wrap the Row in Modifier.horizontalScroll(rememberScrollState()) so the bar keeps a stable share of the width and long labels cannot push content out of bounds.


### app/src/main/java/com/projectlumen/app/app/ProjectLumenStatsAndTimerCards.kt:369
- **Element**: `Box (circular countdown in TimerCard, COUNTDOWN_STYLE_CIRCLE branch)`
- **Issue**: Hardcoded fixed size Modifier.size(210.dp) on the circular countdown container instead of adapting to available width (anti-pattern #1 hardcoded dp size, #8 fixed dimension that doesn't adapt).
- **Why**: The 210dp circle has a fixed footprint that ignores the card's real width. On a 360dp phone the available card content is only ~296dp (360 - 24dp page gutter - 40dp card padding), so the ring currently fits but with no margin to spare. The moment the available width drops below ~250-296dp (narrower/split-screen layouts, large font scaling, tighter host containers) the fixed Box clips against the card, and the centered displayMedium timer text inside it is ellipsized/overlapped instead of scaling down. Because the dimension is hardcoded, the element can never shrink to match its container.
- **Fix**: Replace Modifier.size(210.dp) with Modifier.fillMaxWidth().aspectRatio(1f) capped by .sizeIn(max = 210.dp) so the circle grows/shrinks with the card width instead of being a fixed 210dp.


### app/src/main/java/com/projectlumen/app/app/ProjectLumenProductDemoScreen.kt:154
- **Element**: `Box / CircularProgressIndicator in ProductDemoPhoneCard`
- **Issue**: Hardcoded fixed size Modifier.size(184.dp) on the phone-mock progress ring (also line 159 on the CircularProgressIndicator) instead of a container-relative dimension (anti-pattern #1 hardcoded dp size, #8 fixed dimension that doesn't adapt).
- **Why**: The 184dp ring is a fixed dimension inside a fillMaxWidth card whose content width at 360dp is only ~300dp (360 - 24dp page gutter - 36dp card padding). It fits today but cannot shrink when the container narrows (smaller devices, split-screen, accessibility font scale), and the inner Column (displaySmall '12:40' plus the 'Rest protection active' label) is measured against the fixed 184dp box, so longer or scaled text overflows/gets clipped inside the ring rather than the ring adapting to the phone card width.
- **Fix**: Use Modifier.fillMaxWidth().aspectRatio(1f) with .sizeIn(max = 184.dp) (or derive the ring size from the container via BoxWithConstraints) so the demo ring scales responsively with the card.


## Moderate Issues

### app/src/main/java/com/projectlumen/app/app/ProjectLumenSettingsScreen.kt:578
- **Element**: `SettingsSectionToolbar`
- **Issue**: Row of two fixed-intrinsic-width TextButtons (SettingsSectionToolbar, defined in ProjectLumenSharedComponents.kt:522) rendered at the top of the settings page: Row(fillMaxWidth) { TextButton('Expand all'); TextButton('Collapse all') } with no Modifier.weight, no horizontal scroll, and unconstrained label Text. The buttons cannot shrink, so with longer localized labels (e.g. German/Portuguese) or a large system font scale their combined intrinsic width can exceed the ~336dp content width on a 360dp screen.
- **Why**: Row children with no weight are measured at their intrinsic size; if the sum exceeds the row's max width, the Row overflows horizontally and the right-hand TextButton is drawn off-screen/clipped (the LumenPage column is not horizontally scrollable), hiding the 'Collapse all' control on small screens at high font scale or in long locales.
- **Fix**: Give each TextButton Modifier.weight(1f) so the two buttons split the available width evenly, and constrain the label Text with maxLines = 1 and overflow = TextOverflow.Ellipsis so long labels truncate instead of overflowing.


### app/src/main/java/com/projectlumen/app/app/ProjectLumenAboutAndDialogs.kt:466
- **Element**: `UpdateDialog.showReleaseInfo -> AlertDialog text slot / Text(release.body)`
- **Issue**: The UpdateDialog's AlertDialog `text` slot (showReleaseInfo, used at lines 511/537/566) is an unbounded Column containing `Text(release.body)` (GitHub release notes) with no maxLines/ellipsis and no vertical scroll. Material3's basic AlertDialog (BOM 2024.12.01 / m3 1.3.1) does not scroll its text slot, so on a 360-412dp phone the dialog's content column grows beyond the available screen height and the trailing confirm/dismiss buttons (and the LinearProgressIndicator in the Downloading state) are clipped off-screen / unreachable.
- **Why**: Release bodies are multi-paragraph markdown that easily reach hundreds of lines. The dialog window clamps to the screen minus insets, but the unbounded AlertDialog text column cannot scroll, so the content overflows vertically and the action buttons at the bottom get cut off — worst on small phones where the height budget is smallest.
- **Fix**: Bound and scroll the dialog body: wrap the text content in `Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()))`, or cap `release.body` with `maxLines` + `overflow = TextOverflow.Ellipsis` (plus an expandable/collapsible 'read more'), so the dialog never exceeds the small-screen viewport and the buttons stay visible.


### app/src/main/java/com/projectlumen/app/app/ProjectLumenSharedComponents.kt:522
- **Element**: `SettingsSectionToolbar (Row of two TextButtons)`
- **Issue**: Anti-pattern #2/#3: Row with multiple fixed-content children and no horizontalScroll and no weights. SettingsSectionToolbar is an end-aligned Row (Arrangement.spacedBy(8.dp, Alignment.End)) containing two TextButtons whose intrinsic widths are fully determined by their icon + label content. The buttons cannot shrink.
- **Why**: On a 360-412dp phone the row's available width is only ~336dp after LumenPage's 12dp gutters. Each TextButton is ~120dp in English ('Expand all'/'Collapse all'), which fits, but the M3 strings are translatable (e.g. Turkish 'Tümünü genişlet' ~17 chars) or grow under font scaling (≥1.4x); the two intrinsic widths then sum past 336dp and, because the Row is end-aligned with no horizontalScroll, the start button clips off-screen with no way to reach it.
- **Fix**: Add Modifier.horizontalScroll(rememberScrollState()) to the Row so the buttons stay reachable, or convert the two buttons into weighted children (Modifier.weight(1f) each) so they share and shrink to the available width; both are robust to long translations and large font scales.


### app/src/main/java/com/projectlumen/app/app/ProjectLumenOnboardingScreen.kt:152
- **Element**: `Row (Back/Next button bar) with the primary Button 'Apply setup' (maxLines=2, TextOverflow.Ellipsis)`
- **Issue**: Bottom navigation Row contains two side-by-side weight(1f) buttons (Back OutlinedButton line 158, primary Button line 167) whose content is a fixed 18dp leading icon + 8dp Spacer + text inside Material 3 default button padding (~24dp per side). On a 360dp screen the scrollable Column leaves only 312dp of content width, so the two buttons plus 12dp spacing give each button ~150dp; after padding (48dp) and icon+spacer (26dp) only ~76dp remains for the label. The final-page CTA is 'Apply setup' (~94dp at bodyLarge, per res/values/strings.xml line 728), which cannot fit on one line, so the primary CTA wraps to 2 lines and ellipsizes in longer locales.
- **Why**: The weighted buttons cannot hard-overflow the Row (weight forces them to fit), but the fixed icon+Spacer+default 24dp button padding eat all the flexible budget, leaving the label with ~76dp on a 360dp screen. 'Apply setup' needs ~94dp at one line, so it wraps onto two lines and, for longer translated labels, truncates with an ellipsis — a cramped, inconsistent CTA on narrow phones (and worse on 360dp devices than on 412dp).
- **Fix**: Detect narrow widths with BoxWithConstraints and stack the buttons in a Column when available width is under ~400dp, or remove the leading icon from the primary CTA, or reduce the Button/OutlinedButton contentPadding (e.g. horizontal = 16.dp) so the label has real room; optionally drop the fixed 8dp Spacer and use a weighted icon/Row so the text gets the remaining space.


## Files Scanned
- app/src/main/java/com/projectlumen/app/app/ProjectLumenShizukuSettingsSection.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenSettingsScreen.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenApp.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenBackendConnectivityDeveloperControls.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenDeveloperDebugScreen.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenEyeCareInsights.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenSettingsPrivacyCenter.kt
- app/src/main/java/com/projectlumen/app/MainActivity.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenBuildUpdateNotesScreen.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenFirstOpenGateHost.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenAboutAndDialogs.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenDeveloperShizukuNetworkControls.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenRemoteCloudCard.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenMainScreens.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenDeviceInsightsCard.kt
- app/src/main/java/com/projectlumen/app/ui/theme/Theme.kt
- app/src/main/java/com/projectlumen/app/ui/svg/VectorPreviews.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenStatisticsCards.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenSharedComponents.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenOpenSourceNoticeScreen.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenSettingsAnchors.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenMetricsAndLayout.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenRecommendedSetupFeedback.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenFormControls.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenAppConstants.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenStatsAndTimerCards.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenTemplateScreens.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenUiFormatters.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenPermissionGates.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenWebViewScreen.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenHomeConvenienceCard.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenProductDemoScreen.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenOnboardingScreen.kt
- app/src/main/java/com/projectlumen/app/app/ProjectLumenTranslationScreen.kt
