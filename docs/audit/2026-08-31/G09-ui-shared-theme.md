# G09 UI 层——共享组件 / 表单控件 / 主题与设计令牌 / WebView / 法务与开源声明 / 引导 / Toast / i18n 审查报告

- 审查文件数：32，总行数：9727
  （`app/app/` 下 19 个共 5600 行；`ui/theme/` 5 个共 535 行；`ui/svg/` 6 个共 3010 行；`core/toast/LumenToast.kt` 531 行；`core/i18n/LocaleController.kt` 51 行）
- 结论摘要：**WebView 这一块是全组最健康的部分**——JS 默认关闭（仅 DEBUG 开）、JS 桥仅 DEBUG 注入且只暴露版本号、URL 走硬编码仓库白名单、未覆写 `onReceivedSslError`、`onDispose` 里 `destroy()`，逐条对照 G 类清单基本无洞。真正的问题集中在**共享地基的"两份实现 / 两个真相源"**：`SettingsSection` 被复制成两个 ~100 行的重载且行为与持久化 key 都不一致（切语言或发新版就丢用户折叠状态）；`LumenToast` 把 12 个品牌色硬编码成十六进制字面量，与 `ui/theme/Color.kt` 平行维护且完全无视动态取色；开源声明与权限清单靠手写，已经漏掉了随 APK 分发的 JetBrains Mono 字体（OFL 要求附带许可）；设计令牌里 13 个 topBar 令牌有 8 个从未被消费。性能上有三处确定的浪费：70 条路径的机器生成矢量每次重组重建、引导页把 60fps 动画值读在顶层导致整屏每帧重组、以及主线程同步解码用户图片。最严重的单点是 `LumenToast.showOverlay` 漏摘前台视图，会把一条 toast 永久钉在界面顶部。

## 缺陷清单

### [G09-01] `LumenToast.showOverlay` 不摘除已挂在 Activity 上的前台 toast，视图永久滞留在界面顶部
- 严重度：P1
- 类别：C 资源
- 位置：`app/src/main/java/com/projectlumen/app/core/toast/LumenToast.kt:202-236`（第 210-211 行），对照 `:174-180`
- 现状：
  ```kotlin
  private fun showOverlay(...) {
      overlayView?.let { runCatching { windowManager.removeView(it) } }
      foregroundView = null            // ← 只置空引用，没有 removeView
  ```
  反向的 `showInActivity` 是正确的：它既 `root.removeView(foregroundView)` 又移除 `overlayView`（:174-180）。而 `show()` 在分发前先 `dismissRunnable?.let(mainHandler::removeCallbacks)`（:135）把上一条 toast 的自动消失取消掉了。于是 `foregroundView` 指向的 View 既失去了引用、又失去了定时移除，永远留在 `activity.window.decorView` 上。
- 触发场景：应用内弹出一条 toast（走 `showInActivity`）→ 2.6 秒内应用退到后台（`onActivityStopped` 把 `foreground` 置 false）→ 此时任何代码再弹一条 toast（例如 `TimerForegroundService` / `NotificationService` 的休息提醒到点），且用户已授予悬浮窗权限 → 走 `showOverlay` 分支。
- 影响：用户切回应用后，上一条 toast 卡在顶部遮住 TopAppBar，且**永不消失**（只有 Activity 重建才会消失）；后续每次重复这个时序都会再钉一条。
- 修复方案：在 `showOverlay` 里把置空改成先摘除，与 `showInActivity` 对称：
  ```kotlin
  foregroundView?.let { view -> runCatching { (view.parent as? ViewGroup)?.removeView(view) } }
  foregroundView = null
  ```
  同时建议 `scheduleDismiss` 在覆盖 `dismissRunnable` 前，先把上一条的移除动作立刻执行一次（而不是单纯 `removeCallbacks` 丢弃）。
- 风险/注意：`showInActivity` 与 `showOverlay` 共用 `dismissRunnable` 单槽，改动后要确认两条路径的 "移除旧的 → 添加新的 → 排新的 dismiss" 顺序仍一致；不要改成保留多条 toast（当前"同时只显示一条"是有意设计）。

### [G09-02] `SettingsSection` 复制成两个 ~100 行重载，`forceExpanded` 语义不一致，且折叠状态的持久化 key 一个随语言变、一个随发版变
- 严重度：P1
- 类别：A 架构 / F 持久化
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenSharedComponents.kt:424-527`（String 版）与 `:529-626`（`@StringRes` 版）；调用点 `ProjectLumenSettingsScreen.kt:816-822`、`:893-900`、`:1019`、`:1269`、`ProjectLumenSettingsPrivacyCenter.kt:68`
- 现状：两个重载是逐行复制的两份实现，差异全是无意的：
  ```kotlin
  // String 版：forceExpanded 时头部不可点，且隐藏箭头
  .then(if (forceExpanded) Modifier else Modifier.clickable { expanded = !expanded })
  if (!forceExpanded) { Icon(KeyboardArrowDown, ...) }
  // @StringRes 版：无论 forceExpanded 都可点、都画箭头
  .clickable(...) { expanded = !expanded }
  Icon(Icons.Outlined.KeyboardArrowDown, ...)
  ```
  而**所有 `forceExpanded = true` 的真实调用点用的全是 `@StringRes` 重载**。另外两版落盘 key 不同：`SettingsSectionExpansionStore.key(title) = "section_str_$title"`（本地化后的标题文本）vs `key(titleRes) = "section_$titleRes"`（资源 ID 整数）。
- 触发场景：
  1. 在设置页点"配置通知权限"，引导流程把 `section_notifications` 强制展开（`forceExpanded=true`）→ 用户手指碰到卡片头部 → 分区被折叠，权限引导要用的开关行全部隐藏，而 `LaunchedEffect(forceExpanded)` 是一次性的，不会再展开。
  2. 切换应用语言（中↔英）→ String 版分区的 key 从"提醒引擎"变成 "Reminder engine"，所有折叠状态回到默认值，并在 prefs 里留下双份垃圾键。
  3. 发布新版本时增删任何资源 → `R.string.*` 的整型 ID 重排 → `section_2131755xxx` 全部对不上，用户记住的分区展开/折叠状态在升级后静默重置。
- 影响：权限引导中途"自己合上"；换语言或升级后设置页折叠状态莫名重置。
- 修复方案：删掉 `@StringRes` 重载的函数体，改为转调 String 版：`SettingsSection(title = stringResource(titleRes), icon, initiallyExpanded, forceExpanded, headerAccessory, summary, content)`，让 `forceExpanded` 只有一份语义。同时把 `SettingsSectionExpansionStore` 的 key 改成调用方传入的**稳定标识**（新增 `id: String` 形参，各调用点传 `"notifications"`、`"reminder"` 这类常量），不要再用本地化文本或资源 ID。
- 风险/注意：改 key 会让所有现存用户的折叠状态失效一次（一次性、可接受）；`@StringRes` 重载有 20+ 调用点，若新增 `id` 形参需逐个调用点补齐（含命名参数调用）。两版的退出动画时长（160ms vs 120ms）会被统一，属预期变化。

### [G09-03] 机器生成的 `ImageVector` 每次重组重建整棵路径树（Coder 70 条路径），无 `remember` 缓存
- 严重度：P1
- 类别：D 生命周期与框架约束（Compose 重组）
- 位置：`app/src/main/java/com/projectlumen/app/ui/svg/drawablevectors/Coder.kt:18`（1451 行 / 70 条 `path`）、`VideoSteaming.kt:17`（31 条）、`VideoFiles.kt:17`（24 条）、`Download.kt:18`（19 条）；调用点 `ProjectLumenSharedComponents.kt:707-712`、`ProjectLumenAboutAndDialogs.kt:324`、`ProjectLumenOpenSourceNoticeScreen.kt:120`
- 现状：
  ```kotlin
  @Composable
  fun DynamicColorImageVectors.coder(): ImageVector {
      return Builder(name = "Coder", ...).apply { path(...) { moveTo(...) ... } /* ×70 */ }.build()
  }
  ```
  函数体内部读 `MaterialTheme.colorScheme.*` 与 `LocalFixedColorRoles.current`，但整个 `Builder → build()` 没有任何缓存。每次调用都返回**一个新的 `ImageVector` 实例**，于是 `Image(imageVector = ...)` 内部的 `rememberVectorPainter(image)` 也整体失效、重建 `VectorPainter` 并重新光栅化。
- 触发场景：任何让宿主 composable 重组的输入变化都会重跑一次——`EmptyStateMessage` 出现在会随 `uiState`（含每秒变化的 `nowMillis`）重组的列表页里；`AboutHeroCard` 的 `onVersionClick` 是每次组合新建的 lambda，父级 `AboutScreen` 一重组它就跟着重组；主题切换 / 深浅色切换 / 模板调色板变化会让这三处全部重建。
- 影响：单次重建 70 条路径 ≈ 数百个 `PathNode` 对象加一次全量重绘，在低端机上是可感知的掉帧与 GC 压力；"关于"页和开源声明页首屏尤其明显。
- 修复方案：给 4 个 builder 各加一层按颜色 key 的缓存，例如
  ```kotlin
  @Composable
  fun DynamicColorImageVectors.coder(): ImageVector {
      val scheme = MaterialTheme.colorScheme
      val fixed = LocalFixedColorRoles.current
      return remember(scheme, fixed) { Builder(...)...build() }
  }
  ```
  （key 必须覆盖函数体内读到的全部颜色角色，否则换主题不刷新。）更彻底的做法是把这 4 个纯装饰插画搬到 `res/drawable/*.xml` 由系统缓存，只保留需要跟随主题的填充色用 tint 处理。
- 风险/注意：`remember` 的 key 若只写 `Unit`，深色模式切换后插画会保留旧配色；`ui/svg/VectorPreviews.kt` 的 `@Preview` 也会走同一路径，改完需确认预览仍能渲染。

### [G09-04] 引导页把 60fps 无限动画的值读在顶层 composable，导致整屏每帧重组
- 严重度：P1
- 类别：D 生命周期与框架约束（Compose 重组）
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenOnboardingScreen.kt:77-85`（读值）、`:136-141`（透传）、`:218-225`（真正使用处）
- 现状：
  ```kotlin
  val iconPulse by rememberInfiniteTransition(...).animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(1400), Reverse))
  ...
  OnboardingPageCard(page = activePage, ..., iconScale = iconPulse)   // 以 Float 形参下传
  ```
  `by` 解构在 `ProjectLumenOnboardingScreen` 的重组域内读取了动画状态，于是该 composable 的整个函数体（Scaffold、外层 Column、进度条、`AnimatedContent`、`RecommendedEyeCareSetupActionPreview()`、底部按钮及其全部 `stringResource`）每帧都重组一次；而这个值最终只用于 `Modifier.graphicsLayer { scaleX = iconScale }`——本该只影响绘制阶段。
- 触发场景：首次安装打开应用，引导页停留期间持续发生（动画是 `infiniteRepeatable`，永不停）。
- 影响：引导页（用户对应用的第一印象）持续 60fps 全屏重组，低端机上翻页动画和按钮点击都会发涩；`RecommendedEyeCareSetupActionPreview()` 里两个 `StatusLine` 的 `smartWrapDisplayText` 也被每帧重算。
- 修复方案：把状态读取下推到绘制阶段。最小改动是把形参改成 lambda：`iconScale: () -> Float`，调用处传 `{ iconPulse }`，`graphicsLayer { scaleX = iconScale(); scaleY = iconScale() }`；或者把 `rememberInfiniteTransition` 整段移进 `OnboardingPageCard` 内部、仅在 `graphicsLayer` 的 lambda 里读取（`graphicsLayer` 的 block 在绘制阶段执行，读快照状态只触发重绘不触发重组）。
- 风险/注意：`OnboardingPageCard` 是 `private`，只有一个调用点，改签名无外部影响；注意 `AnimatedContent` 的 `transitionSpec` 读 `initialState/targetState.index`，与本改动无关不要动。

### [G09-05] 开源声明手写维护，已漏掉随 APK 分发的 JetBrains Mono 字体（OFL-1.1 要求随件附带许可）与 `crooot-sdk`
- 严重度：P1
- 类别：A 架构（真相源）/ G 安全合规
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenOpenSourceNoticeScreen.kt:302-410`（`rememberProjectLumenCredits()` 手写 15 条）；对照 `app/build.gradle.kts:306-342` 的真实依赖与 `app/src/main/res/font/jetbrains_mono_lumen_subset.ttf`
- 现状：致谢列表是硬编码的 `List<OssCredit>`，与 `build.gradle.kts` 的依赖块没有任何机械关联。逐条比对后缺失：
  1. **JetBrains Mono**（`res/font/jetbrains_mono_lumen_subset.ttf`，6.3 KB 子集，`ui/theme/Typography.kt:11-13` 全应用文本都在用）——SIL Open Font License 1.1 明确要求**随作品分发许可全文**，且不得只把字体名当商标使用。它是当前唯一真正被二次分发的第三方素材，却完全没出现在声明里。
  2. `com.chloemlla.crooot:crooot-sdk:0.1.0`（`build.gradle.kts:307`）——第三方坐标的二进制依赖，无条目。
  3. 其余依赖（`animation-graphics`、`ui-tooling-preview`、`profileinstaller`、`work-runtime-ktx`、`lifecycle-*`、`navigation-compose`）可归入现有的 "Jetpack Compose / Material 3" 与 "AndroidX …" 两条聚合条目，可接受。
- 触发场景：任何人查看"开源声明"页，或按 OFL 条款检查随 APK 分发的字体许可。
- 影响：法务缺口（分发 OFL 字体未附许可）；同时这份手写清单必然随依赖升级继续腐化——`Lumen Crash` 条目的 URL 甚至直接指向 Project-Lumen 仓库而非 SDK 自身。
- 修复方案：在 `rememberProjectLumenCredits()` 里补两条：JetBrains Mono（作者 JetBrains，许可 "SIL Open Font License 1.1"，url `https://github.com/JetBrains/JetBrainsMono`）与 crooot-sdk；并把这份清单的维护改成机械可校验——最省事的做法是在 CI 里加一条脚本，比对 `build.gradle.kts` 的 `implementation(...)` 坐标数与本文件条目数，不一致即失败（不引入新 Gradle 插件，符合"不写超级文件/不加重型依赖"的约束）。
- 风险/注意：新增 `OssCredit` 需要同时在 `values/` 与 `values-zh/` 补 `credit_*_desc` 字符串，漏一边会在另一语言下抛资源缺失；条目顺序会影响页面观感，建议插在 "Material Icons Extended" 之后。


### [G09-06] `LocaleController` 双机制并存：Android 13+ 系统"应用语言"设置会被应用自己存的值覆盖回去
- 严重度：P1
- 类别：D 生命周期与框架约束
- 位置：`app/src/main/java/com/projectlumen/app/core/i18n/LocaleController.kt:15-31`；调用点 `ProjectLumenApp.kt:168-170`（`wrap`）与 `:279-281`（`apply`）；`AndroidManifest.xml:52` 声明了 `android:localeConfig="@xml/locales_config"`
- 现状：同一件事有两套实现同时生效——
  ```kotlin
  fun apply(languageCode: String) { AppCompatDelegate.setApplicationLocales(localeListFor(normalize(languageCode))) }
  fun wrap(base: Context, languageCode: String): Context { ... base.createConfigurationContext(configuration) }
  ```
  `ProjectLumenApp` 既 `LaunchedEffect(uiState.settings.languageCode) { LocaleController.apply(...) }`，又 `remember { LocaleController.wrap(baseContext, uiState.settings.languageCode) }`。两者的真相源都是 **Room 里的 `settings.languageCode`**，而 `setApplicationLocales` 的真相源本应是系统的 per-app locale（`AppCompatDelegate.getApplicationLocales()`）。
- 触发场景：清单里声明了 `localeConfig`，所以系统"设置 → 应用 → Project Lumen → 语言"这个入口对用户是可见的。用户在系统设置里把应用语言改成英文 → 应用重建 → `LaunchedEffect(languageCode)` 拿数据库里的旧值（`zh`）再调一次 `setApplicationLocales("zh")`，同时 `wrap()` 也把资源强制回中文。
- 影响：系统级的应用语言选择被静默改回去，用户在系统设置里看到的语言和应用实际显示的语言不一致，且怎么改都会被弹回；这是 Android 13+ per-app language 的典型误用。
- 修复方案：把系统 API 定为唯一真相源：
  1. 启动时先读 `AppCompatDelegate.getApplicationLocales()`，非空则**反向同步**进 `settings.languageCode`，再决定是否调用 `apply`；
  2. 删除 `wrap()` 及 `ProjectLumenApp.kt:168-170` 的 `localizedContext`——`setApplicationLocales` 已经会给所有 Activity 的 `Context` 换好语言，`createConfigurationContext` 只是第二套覆盖；`ProjectLumenApp.kt:215/229/273` 那几处 `localizedContext.getString(...)` 直接换成 `baseContext.getString(...)`。
- 风险/注意：`wrap()` 目前还负责 `setLayoutDirection`，删除前确认没有 RTL 语言进 `locales_config.xml`（当前只有 zh/en，无影响）；`apply()` 外面裹着 `runCatching {}` 会吞掉失败，反向同步逻辑不要也裹进同一个 `runCatching`。

### [G09-07] 6 个文件复制粘贴同一份 ~195 行 import 块，`ProjectLumenAppConstants.kt` 284 行里 195 行是 import
- 严重度：P2
- 类别：H 编译与结构
- 位置：`ProjectLumenAppConstants.kt:3-195`、`ProjectLumenSharedComponents.kt:3-223`、`ProjectLumenAboutAndDialogs.kt:3-209`、`ProjectLumenUiFormatters.kt:3-199`、`ProjectLumenWebViewScreen.kt:3-194`、`ProjectLumenFormControls.kt:3-204`（同组其余 13 个文件的 import 都是干净的）
- 现状：这 6 个文件的 import 块逐行相同（从 `android.Manifest` 到 `kotlinx.coroutines.launch`），显然是拆分超级文件时把整块 import 复制进了每个碎片。以 `ProjectLumenAppConstants.kt` 为例，正文只用到 `WebView`、`StringRes`、`R`、`RoundedCornerShape`、`dp`、`Color`、`Composable`、`MaterialTheme`、`CardDefaults`、`BorderStroke`、`DateTimeFormatter` 共 11 个符号，却 import 了 193 个——`ImageView`、`AlarmManager`、`NotificationManager`、`PackageManager`、`NavHost`、`ProjectLumenApplication`、`UpdateInstaller`、`BackupImportSummary`、全部 20 多个 `compose.animation.*` 全是无用 import。
- 触发场景：改名或删除任何一个被顺带 import 的符号（例如把 `UpdateCandidate` 改名）→ 6 个文件同时编译失败，而其中 5 个根本没用到它；`rg` 查依赖关系时每个文件都"看起来"依赖 ViewModel、数据库实体、更新器和导航。
- 影响：编译期耦合被虚假放大，重构半径失真；`lintDebug`/IDE 的 unused-import 噪声掩盖真实问题；新人无法从 import 判断一个文件的真实职责。
- 修复方案：逐个文件按正文实际引用裁剪 import。可用的机械做法：对每个文件取出 `^import .*\.(\w+)$` 的末段符号名，在该文件 195 行之后的正文里 `rg -w` 搜一次，零命中即删除（注意扩展函数与操作符 import 需要人工确认：`getValue`/`setValue`/`toUri`/`getSystemService`/`toColorInt`/`collectAsStateWithLifecycle` 这类不会以符号名出现在正文里）。
- 风险/注意：`androidx.compose.runtime.getValue` / `setValue` 是 `by` 委托必需的，机械脚本会误判为未使用；`import android.graphics.Color as AndroidColor` 这类别名 import 也要按别名搜。删完必须靠 CI 编译验证，本机禁止构建。

### [G09-08] 模板调色板与自定义主题色在 API 29/30 上被完全忽略（`useDynamicColors` 默认 true，minSdk 29）
- 严重度：P2
- 类别：A 架构
- 位置：`app/src/main/java/com/projectlumen/app/ui/theme/Theme.kt:86-118`
- 现状：`useDynamicColors` 被当成三处判断的开关，但只有第一处考虑了 SDK 版本：
  ```kotlin
  val templatePaletteIsDark = remember(...) { if (useDynamicColors) null else templatePaletteDarkness(themePaletteJson) }
  val baseColorScheme = when {
      useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> { dynamic... }   // ← 有版本判断
      darkTheme -> DarkColors
      else -> LightColors
  }
  val colorScheme = remember(...) { if (useDynamicColors) baseColorScheme else baseColorScheme.applyTemplatePalette(...) }  // ← 没有
  ```
  在 API 29/30 上 `useDynamicColors=true` 时：动态取色拿不到（回落到静态 Lumen 配色），而 `applyTemplatePalette` 又因为 `useDynamicColors` 为真被整体跳过。
- 触发场景：Android 10/11 设备（`minSdk 29`，`useDynamicColors` 默认 `true`，见 `EyeCarePreferencesDataStore.kt:37`）上选一个带 `palette` 的提示模板，或设置自定义主题主色/背景色。
- 影响：这些用户看到的永远是内置 Lumen 配色，模板调色板和自定义主题色**静默无效**，而设置页的开关看起来是生效的；用户只有先关掉"使用壁纸取色"才能让模板配色生效，界面上没有任何提示。
- 修复方案：在 `ProjectLumenTheme` 顶部先算一次真实生效值，三处判断统一用它：
  ```kotlin
  val dynamicColorsActive = useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  ```
  替换 `:87`、`:96`、`:109` 三处的 `useDynamicColors`。相应地 `ProjectLumenUiFormatters.kt:323-326` 的 `templateAppearanceLocksThemeMode`（同样只看 `settings.useDynamicColors`）要用同一判据，否则浅色/深色开关的可用状态又会与实际渲染不一致。
- 风险/注意：改完之后 API<31 的用户会突然开始看到模板配色（这是修复目标，但属可见行为变化）；`ProjectLumenSettingsScreen.kt:1175-1190` 的开关在低版本上应改为置灰并说明"当前系统不支持壁纸取色"，否则用户仍会困惑。

### [G09-09] `LumenToast` 硬编码 12 个十六进制颜色，与 `ui/theme/Color.kt` 平行维护，且无视动态取色 / 模板调色板
- 严重度：P2
- 类别：A 架构（真相源）
- 位置：`app/src/main/java/com/projectlumen/app/core/toast/LumenToast.kt:53-73`（4 个 kind × 2 个 accent）、`:278-284`（surface / surfaceSoft / outline / body 共 6 个）、`:79`（`accentColor` 兼容 getter）
- 现状：
  ```kotlin
  INFO(accentColorLight = Color.parseColor("#126B66"), accentColorDark = Color.parseColor("#8ED6D1"), ...)
  val surface = if (darkTheme) Color.parseColor("#1A211E") else Color.parseColor("#FFFCFA")
  ```
  这些字面量与 `ui/theme/Color.kt` 一一对应（`LumenTeal=#126B66`、`LumenTealDark=#8ED6D1`、`LumenCoral=#B85C38`、`LumenIndigo=#525DAA`、`LumenSurfaceContainerDark=#1A211E`、`LumenSurface=#FFFCFA`、`LumenOutlineVariantDark=#404B47`），是同一套品牌色的第二份拷贝。另外
  ```kotlin
  /** Backward-compatible accent for rich-message callers. */
  val accentColor: Int get() = accentColorLight
  ```
  被 `core/services/NotificationService.kt:323` 与 `:363` 用于 `LumenToast.richMessage(...)` 的关键字着色——**恒取浅色变体**。
- 触发场景：① 改动 `Color.kt` 的品牌色（或启用 Material You / 模板调色板）→ toast 仍是旧配色，与全应用不一致；② 深色模式下弹出 `NotificationService` 构造的富文本 toast → 关键字用 `#B85C38` 画在 `#1A211E` 的深色卡片上，对比度约 3.3:1，低于正文文本 4.5:1 的可读性门槛（同一条 toast 的普通正文用的是 `#E7EFEC`，对比明显）。
- 影响：深色模式下被高亮的关键字反而比周围正文更难读；品牌色/主题变更时 toast 成为唯一不跟随的界面元素。
- 修复方案：toast 是 View 层组件读不到 `MaterialTheme`，正确做法是把这套色值下沉到资源：新建 `res/values/toast_colors.xml` + `res/values-night/toast_colors.xml`，用 `ContextCompat.getColor(context, R.color.lumen_toast_accent_info)` 取值，`Color.kt` 与 xml 保持同名同值（或反过来让 `Color.kt` 从资源取）。同时把 `accentColor` 兼容 getter 删掉，`richMessage` 改成接收 `kind: LumenToastKind` 并在 `createToastView` 内部按 `metrics.darkTheme` 解析 accent —— 着色时机才是唯一知道深浅色的时机。
- 风险/注意：`richMessage` 有 2 个外部调用点（`NotificationService.kt:320`、`:360`），改签名需同步；`richMessage` 返回的 `SpannableString` 也被用于系统通知（`showFallbackNotification` 走 `message.toString()` 会丢掉 span，属既有行为，不要在本次一起改）。


### [G09-10] `UriImagePreview` 在主线程同步解码用户任意图片，且每次重组重复解码
- 严重度：P2
- 类别：C 资源 / D 生命周期
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenSharedComponents.kt:829-845`
- 现状：
  ```kotlin
  factory = { context -> ImageView(context).apply { scaleType = CENTER_CROP; setImageURI(path.toUri()) } },
  update = { imageView -> imageView.setImageURI(path.toUri()) },
  ```
  `ImageView.setImageURI` 是**同步**解码（内部 `ContentResolver.openInputStream` + `BitmapFactory.decodeStream`），且不做任何降采样——按原图分辨率整张解进内存，最后只显示在 64dp 的方框里。`AndroidView` 的 `update` 块在**每次重组**都会执行，于是每次重组重新解码一次。
- 触发场景：用户在模板编辑里选一张相册照片（主摄 4000×3000 ≈ 48 MB ARGB_8888）→ 预览行随所在页面的任意状态变化反复重组（该页面挂在每秒变化的 `uiState` 上）。
- 影响：选图后主线程卡顿肉眼可见；超大图（全景 / 长截图）可能直接 OOM；重复解码持续制造大对象、频繁触发 GC。
- 修复方案：把解码搬离组合与主线程，并加下采样。最小改动是掐掉重复解码：
  ```kotlin
  update = { imageView -> if (imageView.tag != path) { imageView.tag = path; imageView.setImageURI(path.toUri()) } }
  ```
  更正确的做法是 `LaunchedEffect(path) { withContext(Dispatchers.IO) { /* inJustDecodeBounds → inSampleSize 目标 64dp */ } }` 解出缩略图后交给 `Image(bitmap)`，彻底去掉 `AndroidView`/`ImageView`。
- 风险/注意：该组件供模板背景图预览使用，改成 `Image(bitmap)` 要用 `ContentScale.Crop` 保持等价裁剪；`persistReadableUri`（`ProjectLumenUiFormatters.kt:404-408`）把 `takePersistableUriPermission` 的失败静默吞掉，若权限没拿到解码会抛 `SecurityException`——新实现要显式兜底成占位图而不是崩溃。

### [G09-11] 设计令牌每进一个屏幕就在组合期解析一次 asset；13 个 topBar 令牌里 8 个从未被消费
- 严重度：P2
- 类别：A 架构 / D 生命周期
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenUiTokens.kt:15-23`（`load`）、`:96-99`（`rememberLumenUiTokens`）；消费点 `ProjectLumenSharedComponents.kt:236`（`LumenTopBar`）与 `ProjectLumenMetricsAndLayout.kt:341`（`LumenPage`）
- 现状：
  ```kotlin
  @Composable
  internal fun rememberLumenUiTokens(context: Context): LumenUiTokens = remember(context) { LumenUiTokens.load(context) }
  ```
  `remember(context)` 的作用域是**每个调用点的每个组合实例**，不是进程级。`LumenTopBar` 与 `LumenPage` 各调用一次，于是每次导航到新页面都会在组合期（主线程）`assets.open("lumen-ui-tokens.json")` + `JSONObject` 解析两次。文件只有 1.2 KB，单次成本不大，但它落在每个页面的关键组合路径上、内容在运行期永不变化。
  更实质的问题是令牌契约失真：`LumenTopBarTokens` 定义并解析 13 个字段，全仓库只消费 4 个（`titleFontSizeSp`、`titleFontWeight`、`titleMaxLines`、`secondaryLeadingWidthDp`）。`containerStartPaddingDp`、`containerEndPaddingDp`、`contentTopGapDp`、`contentHeightDp`、`contentBottomGapDp`、`collapseThresholdDp`、`primaryColor`、`onPrimaryColor` 在生产代码里零引用（`primaryTitleStartDp` 只被 androidTest 截图测试读到）。
- 触发场景：设计者用 `tools/lumen-ui-tuner` 调整顶栏高度（`contentHeightDp`）或顶栏主色，在工具预览里看到变化，提交 `design/lumen-ui-tokens.json`（该目录整体挂进 assets，见 `app/build.gradle.kts:258-262`），装到手机上发现应用毫无变化。
- 影响：设计令牌管线名存实亡，2/3 的令牌是死数据；同时每次页面切换白做两次 asset 读取与 JSON 解析。
- 修复方案：① 提到进程级缓存：伴生对象加 `@Volatile private var cached: LumenUiTokens?`，`load` 命中即返回；或在 `ProjectLumenTheme` 解析一次后用 `staticCompositionLocalOf` 下发，`rememberLumenUiTokens` 改读该 CompositionLocal。② 对 8 个未消费令牌二选一：在 `LumenTopBar`/`LumenPage` 真正接上（顶栏高度、内边距、collapse 阈值都是 `TopAppBar` 支持的），或从 `LumenTopBarTokens`、`design/lumen-ui-tokens.json`、`tools/lumen-ui-tuner` 三处一并删掉——不要留"看得见改不动"的旋钮。
- 风险/注意：`design/lumen-ui-tokens.json` 当前值与 Kotlin 默认值**逐字段完全相同**，所以接上或删除在当前数据下都不改变观感；`tools/lumen-ui-tuner/src/defaultTokens.js` 是第三份拷贝，改字段要三处同步；删字段会连带改 `LumenTopBarScreenshotTest`。

### [G09-12] 推荐护眼配置存在两份平行清单（25 字段赋值 + 25 条相等比较），且快照每秒重算
- 严重度：P2
- 类别：A 架构（真相源）/ D 重组
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenRecommendedSetupFeedback.kt:106-134` 与 `:232-261`；目标值同样两份 `:136-143` 与 `:263-271`；未缓存的快照 `:72-76`
- 现状：推荐值先在 `recommendedEyeCareSettings` 里以 `current.copy(reminderEnabled = true, warnIntervalMinutes = 20, …)` 写 25 个字段，随后 `recommendedEyeCareSettingMatches` 又把同样 25 个字段逐条写成 `settings.reminderEnabled == recommended.reminderEnabled` 的比较列表，两份清单无任何机械关联。同时 `val snapshot = recommendedEyeCareSetupSnapshot(uiState = uiState, …)` 没有 `remember`，而 `uiState` 含每秒变化的 `nowMillis`（`ProjectLumenUiState.kt:24`）。
- 触发场景：① 给推荐配置新增字段只改了 `recommendedEyeCareSettings`、忘了改匹配列表 → 状态文案照旧显示"已完整应用推荐配置"，那个字段其实从未被检查，用户点"应用推荐配置"前后文案不变、无法自查。② 设置页停留时快照每秒重算：两次 40+ 字段实体 `copy`、29 次比较、4 个临时 List。
- 影响："推荐配置已应用"的结论可能与实际不符（静默错报）；设置页每秒产生一批可避免的临时对象。
- 修复方案：把推荐值收敛成单一清单，两个函数都从它派生。由于 `recommendedEyeCareSettings` 是 `current.copy(...)`，只有被推荐配置触碰的字段会不同，所以"全部已应用"等价于 `settings == recommendedEyeCareSettings(settings)`；配合一份显式字段读取器列表即可同时算出 matchCount，两处不再各写一遍。另把 `:72` 包成 `remember(uiState.settings, uiState.dailyGoal, permissionRequirements, shizukuReady) { … }`，把 `nowMillis` 排除在 key 之外。
- 风险/注意：`recommendedEyeCareSettings` 还被"应用推荐配置"的写入路径使用，重构不能改变它产出的字段值；`totalSettings` 出现在用户可见文案 `recommended_setup_status_custom`（"已匹配 x/y 项"），改动后数字应保持 25/4。

### [G09-13] `openUri` 对任意 scheme 发 `ACTION_VIEW`，而 URL 可以来自 GitHub Release 响应
- 严重度：P2
- 类别：G 安全
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenWebViewScreen.kt:416-420`（`openUri`）、`:203-219`（白名单外一律外跳）、`:370-377`（`shouldOverrideUrlLoading` 同样外跳）；污点来源 `ProjectLumenAboutAndDialogs.kt:585`
- 现状：
  ```kotlin
  internal fun openUri(context: Context, uri: Uri) {
      val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      runCatching { context.startActivity(intent) }.onFailure { Toast... }
  }
  ```
  白名单 `isProjectLumenRepoUrl` 要求 `scheme == "https"` 且前缀等于仓库地址，**不通过就调 `openUri` 外跳**——scheme 越"奇怪"越会走到外跳分支。URL 链路：`UpdateChecker` 从 GitHub API 取 `release.htmlUrl` → `ProjectLumenAboutAndDialogs.kt:585` `pendingReleaseUrl = release.htmlUrl.ifBlank { … }` → `viewModel.navigateWebPage(url)` → `ProjectLumenApp.kt:326` `WebViewScreen(url)` → 非 https → `openUri(任意 uri)`。
- 触发场景：Release 元数据被篡改（账号被盗、仓库转移、或 `api.github.com` 链路上的中间人——`SecureOkHttpFactory` 对该域名并未强制证书固定），把 `html_url` 换成 `myapp://…`、`content://…` 或某个第三方应用的导出组件深链，用户在"发现新版本"对话框点确认即触发。
- 影响：由远端数据决定本机 `ACTION_VIEW` 的目标，构成一个低当量 Intent 重定向面——可跳到任意已注册 scheme 的组件并携带攻击者控制的数据。不涉及本应用私有组件，故不评 P1。
- 修复方案：在 `openUri` 入口加 scheme 白名单，非 http/https 直接走失败提示：
  ```kotlin
  if (uri.scheme?.lowercase() !in setOf("http", "https")) { Toast.makeText(context, context.getString(R.string.webview_open_failed), Toast.LENGTH_SHORT).show(); return }
  ```
  并在 `ProjectLumenAboutAndDialogs.kt:585` 先校验 `release.htmlUrl` 必须以 `PROJECT_LUMEN_RELEASES_BASE_URL`（`ProjectLumenAppConstants.kt:200` 已有该常量）开头，不通过就退回该常量。
- 风险/注意：`openUri` 同时被顶栏"在浏览器打开"和 `shouldOverrideUrlLoading` 使用，加限制后页面内 `mailto:`/`tel:` 链接不再外跳（当前目标页是 GitHub 仓库页，影响可忽略，但属有意接受的行为变化）。

### [G09-14] 更新检查 / 下载对话框完全模态且无取消入口，Release 说明无长度上限
- 严重度：P2
- 类别：E 韧性
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenAboutAndDialogs.kt:543-553`（`Checking`）、`:597-624`（`Downloading`）、`:535`（`Text(release.body)`）
- 现状：
  ```kotlin
  UpdateDialogState.Checking -> AlertDialog(onDismissRequest = {}, ..., confirmButton = {})
  is UpdateDialogState.Downloading -> AlertDialog(onDismissRequest = {}, ..., confirmButton = {}, dismissButton = {})
  ```
  两个状态既不响应点外区域、也不响应返回键（`AlertDialog` 会消费返回），且没有任何按钮。唯一的退出方式是对应协程自己改状态。下载协程在 `ProjectLumenApp.kt:252-277`，`updateInstaller.downloadApk` 只有 `readTimeout`（`UpdateInstaller.kt:32-33`，按单次读计时），慢速但存活的连接可以让下载持续任意长时间。另外 `showReleaseInfo` 直接 `Text(release.body)` 渲染 GitHub 返回的发布说明，无字符上限，而 M3 `AlertDialog` 的正文区不可滚动。
- 触发场景：地铁/弱网/校园认证网络下点"检查更新"或"下载更新"；或某个版本的 release body 写了长篇 changelog。
- 影响：用户被一个无法关闭的模态对话框卡住，只能杀进程（下载几十 MB 的 APK 期间尤其明显）；长 changelog 被截断且无法滚动，用户看不到后半段也无法关闭。
- 修复方案：① `Checking` 加 `dismissButton = { OutlinedButton(onClick = onDismiss) { Text(cancel) } }` 并让 `onDismissRequest = onDismiss`；② `Downloading` 加一个"取消下载"按钮，回调里 `cancel()` 掉 `triggerUpdateDownload` 启动的 Job（需要在 `ProjectLumenApp.kt` 把该 `Job` 存进 `remember`，并在 `UpdateDialog` 新增 `onCancelDownload: () -> Unit`）；③ `showReleaseInfo` 里给 `Text(release.body)` 加 `maxLines`/`Modifier.verticalScroll(rememberScrollState())`，或把 body 截到（例如）4000 字并附"在浏览器查看完整说明"。
- 风险/注意：`UpdateDialog` 的形参已有 10 个，再加回调前建议先把它收成一个状态对象；取消下载后要确保 `downloadingUpdate`/`downloadProgressBytes` 被复位（现有 `finally` 语义靠的是协程正常返回，取消路径需另行处理）。

### [G09-15] 首开门禁的可变状态无同步，且三个"完成"入口直接调加密存储不做兜底
- 严重度：P2
- 类别：B 并发 / E 韧性
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenFirstOpenGateEntry.kt:17-20`（三个裸 `var`）、`:36-57`（`refresh`）、`:59-70`、`:83-89`、`:97-112`（三个 `complete*`）
- 现状：`installProfile` / `onboardingEligible` / `newInstallDetected` 是普通 `var`（无 `@Volatile`、无锁）。写入者有两类：`refresh()` 由 `ProjectLumenViewModel.kt:222-241` 的 `reportingScope.launch { … }` 在启动协程里调用；`completeOssNotice()` / `completeOnboarding()` / `completeBuildUpdateNotes()` 由 Compose 点击回调在主线程调用（`ProjectLumenFirstOpenGateHost.kt:14/20/29`）。两者都会接着调 `applyAutomaticGate()` 覆写三个 `MutableStateFlow`。
  另外错误处理不一致：`refresh()` 明确认为该存储会失败——`runCatching { secureCredentials.installProfile() }.getOrDefault(installProfile)`、`runCatching { markOssNoticeCompleted(...) }`；而三个 `complete*` 直接裸调 `secureCredentials.markOssNoticeCompleted / markOnboardingCompleted / markBuildUpdateNotesAcknowledged`。后者内部还有 `require(commitHash.isNotBlank())`、`require(buildTimeUtcMillis > 0L)`（`SecureCredentialStore.kt:176-177`）会抛 `IllegalArgumentException`。
- 触发场景：① 首开时 OSS 声明门禁由构造函数（`init { applyAutomaticGate() }`）立刻显示，用户在启动协程跑到 `refresh()` 之前就点了"继续"→ 主线程写 `installProfile`，随后 `refresh()` 在协程线程用磁盘旧值覆盖它并重新 `applyAutomaticGate()`，门禁可能重新弹出。② `EncryptedSharedPreferences`/MMKV 加密层在部分 OEM ROM 上（Keystore 失效、备份恢复后）会抛异常——`refresh()` 能扛住，点击回调不能，异常从 Compose 点击回调抛出即崩溃；而门禁在下次启动仍未通过，形成"点一次崩一次"的永久卡死。
- 影响：首开引导可能重复出现；极端情况下用户永远进不了应用主界面。
- 修复方案：① 三个 `var` 改为在同一把锁内读改写（`private val lock = Any()` + `synchronized(lock) { … }` 包住 `refresh()` 与三个 `complete*` 的状态更新段），或把整个 `Entry` 的状态改由单一 `MutableStateFlow<GateModel>` + `update {}` 承载。② 三个 `complete*` 里的持久化调用统一包 `runCatching`，并且**只有持久化成功才更新内存 `installProfile` 并放行门禁**；失败时保留门禁并向用户提示（否则内存放行、磁盘未写，下次启动又弹一遍）。
- 风险/注意：`markBuildUpdateNotesAcknowledged` 的 `require` 在当前构建里不会触发（`app/build.gradle.kts:60-67` 给 `COMMIT_HASH`/`SHORT_HASH` 兜了 `"unknown"`、`BUILD_TIME_UTC_MILLIS` 兜了 `System.currentTimeMillis()`），所以这一条的现实触发面主要是加密存储异常，不要据此改动 Gradle 兜底逻辑；`refresh()` 里"老用户免 OSS 门禁"的补写逻辑与 `applyAutomaticGate()` 的 `grandfatherOssNotice = installProfile.onboardingCompletedAt > 0L` 是同一规则的两处实现，加锁重构时顺手收成一处。

### [G09-16] 隐私同意的真相源就是"引导流程完成时间"，而引导页有一个直接记同意的"跳过"按钮
- 严重度：P2
- 类别：A 架构（真相源）
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenOnboardingScreen.kt:112`（跳过）、`:169-175`（完成）；`ProjectLumenFirstOpenGateEntry.kt:83-89`（`completeOnboarding` → `markOnboardingCompleted`）、`:91-95`（`withdrawPrivacyConsent` → `resetOnboardingCompletion`）；入口 `ProjectLumenLegalCenterScreen.kt:126-128`、`:185-209`
- 现状：`legal_center_withdraw_privacy_consent`（撤回隐私政策同意）最终调用的是 `firstOpenGateEntry.withdrawPrivacyConsent()`，它把 `onboardingCompletedAt` 清零——也就是说**"引导完成时间"被当作隐私同意记录**。而引导页本身是一个 5 页的信息轮播（`onboarding_page_welcome/protection/permissions/cloud/best_practice`），既没有隐私政策正文或链接，也没有独立的同意勾选，顶部却有一个"跳过"按钮直接 `onComplete(false)` → 写入 `markOnboardingCompleted`。
  另外 `withdrawPrivacyConsent()` 是唯一一个不调 `applyAutomaticGate()` 的状态变更方法，所以撤回后本次会话不会重新弹门禁。
- 触发场景：用户首开点"跳过"；或在法务中心点"撤回隐私政策同意"后继续使用当前会话。
- 影响：合规链条不成立——"用户在信息轮播上点了跳过"被记录为"用户已同意隐私政策"，且撤回同意后本次会话仍以已同意状态继续运行（要到下次启动才重新门禁）。
- 修复方案：把两件事拆成两个记录：在 `SecureCredentialStore` 增加独立的 `privacyConsentAt`（与 `onboardingCompletedAt` 并列），`withdrawPrivacyConsent` 只清前者；门禁 `ProjectLumenFirstOpenGateResolver` 用 `privacyConsentAt <= 0` 判定是否必须展示同意页。同意页需要有明确动作（政策链接 + "同意并继续"），"跳过"只跳过功能介绍、不写同意。`withdrawPrivacyConsent()` 末尾补上 `applyAutomaticGate()`，让撤回立即生效。
- 风险/注意：新增字段涉及 `SecureCredentialStore` 与 `DeviceInstallProfile`（G06 组文件），需与该组协调；对老用户要做一次迁移（`onboardingCompletedAt > 0` 视为 `privacyConsentAt = onboardingCompletedAt`），否则升级后所有存量用户会被重新拦在同意页。

### [G09-17] `LumenTypography` 只定义 9/15 个 M3 文本样式，而未定义的 4 个样式正被用在最显眼的标题上
- 严重度：P2
- 类别：A 架构（设计系统一致性）
- 位置：`app/src/main/java/com/projectlumen/app/ui/theme/Typography.kt:15-67`（缺陷本体）；未定义样式的使用点：`headlineLarge` → `ProjectLumenBuildUpdateNotesScreen.kt:92`；`headlineSmall` → `ProjectLumenOpenSourceNoticeScreen.kt:114`、`ProjectLumenOnboardingScreen.kt:229`、`ProjectLumenBuildUpdateNotesScreen.kt:196`（以下两处属其他组文件，仅作证据）：`displayMedium` → `ProjectLumenStatsAndTimerCards.kt:421`、`labelSmall` → `ProjectLumenDeveloperDebugScreen.kt:476`
- 现状：`LumenTypography` 逐个覆写了 `headlineMedium / titleLarge / titleMedium / titleSmall / bodyLarge / bodyMedium / bodySmall / labelLarge / labelMedium` 共 9 个样式，全部指定 `fontFamily = LumenMonoFontFamily`（JetBrains Mono 子集）。剩下的 `displayLarge/Medium/Small`、`headlineLarge/Small`、`labelSmall` 保留 Material 默认值——即默认字体（Roboto）。而全仓库统计（`rg -o "typography\.\w+"`）显示 `headlineSmall` 3 次、`headlineLarge` 1 次、`displayMedium` 1 次、`labelSmall` 1 次都在实际使用。
- 触发场景：打开"开源声明"页、首开引导页、构建更新说明页——它们的一级标题走 `headlineSmall`/`headlineLarge`。
- 影响：应用里字号最大、最显眼的几个标题用的是系统默认字体，而同一屏其余文字是 JetBrains Mono，字形与字宽明显不同；这不是设计意图（其余 9 个样式都被显式统一过）。
- 修复方案：修改只需落在 `Typography.kt` 一个文件：补齐 `headlineLarge`、`headlineSmall`、`displayMedium`、`labelSmall` 四个样式，沿用 `fontFamily = LumenMonoFontFamily` 与既有字号阶梯（建议 headlineLarge 32sp/40sp、headlineSmall 22sp/28sp、displayMedium 40sp/48sp、labelSmall 11sp/14sp），不需要改任何调用点。
- 风险/注意：JetBrains Mono 子集只有 6.3 KB（`app/build.gradle.kts:273-282` 强制 <20 KB），是纯拉丁子集，不含 CJK；补齐样式后中文标题同样依赖系统字体回退——这是既有行为（9 个已定义样式早已如此），本次改动不会引入新差异，但如果后续要给标题换字体，需要先在真机上确认中文回退效果。

### [G09-18] 法务"应用权限"清单手写维护，与 `AndroidManifest.xml` 已经不一致
- 严重度：P2
- 类别：A 架构（真相源）
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenLegalCenterScreen.kt:329-347`（`legalPermissions` 手写 17 条）；对照 `app/src/main/AndroidManifest.xml:16-38` 与 `app/src/main/res/values/app_permissions_strings.xml`
- 现状：清单是一份硬编码 `List<PermissionEntry>`。逐条比对清单声明与页面展示：
  - Manifest 声明但页面**未说明**：`POST_PROMOTED_NOTIFICATIONS`（Android 16 提升通知）、`FOREGROUND_SERVICE_SPECIAL_USE`、`FOREGROUND_SERVICE_CAMERA`（页面只笼统写了 `FOREGROUND_SERVICE`）。
  - 字符串资源里留着 `legal_permission_vibrate_name` / `_desc`（`app_permissions_strings.xml:33-34`，中英文两份），但 `VIBRATE` 既不在 Manifest 里、也不在 `legalPermissions` 里——纯残留。
- 触发场景：用户或应用商店审核（国内商店对"权限清单/个人信息收集清单"完整性有要求）逐条核对声明权限与说明。
- 影响：合规页面与实际声明不符：有 3 项已声明权限没有用途说明，另有 1 项说明对应的权限根本没声明。
- 修复方案：把三条缺失项补进 `legalPermissions` 并新增对应 `legal_permission_*` 字符串（中英各一份）；删除 `legal_permission_vibrate_*` 两条残留字符串。为防止再次漂移，在 CI 里加一条脚本：解析 `AndroidManifest.xml` 的 `uses-permission` 名称集合，与 `app_permissions_strings.xml` 里 `legal_permission_*_name` 的字符串值集合做双向 diff，不一致即失败（自定义权限 `ACCESS_LUMEN_CORE`/`TRIGGER_LUMEN_CONTROL` 走 `<permission>` 声明，需在脚本里一并纳入）。
- 风险/注意：`legalPermissions` 的顺序即页面展示顺序，建议把新增的前台服务子权限紧跟在 `FOREGROUND_SERVICE` 之后；改字符串资源要同时改 `values/` 与 `values-zh/`，漏一边会在另一语言下抛资源缺失。

### [G09-19] 翻译页：5000 字上限三处硬编码且校验分支不可达；每次进屏新建一个 OkHttpClient
- 严重度：P2
- 类别：E 韧性 / H 结构
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenTranslationScreen.kt:79`（客户端构造）、`:112`、`:160`、`:166`（三处 5000）
- 现状：
  ```kotlin
  val api = remember(context) { ProjectLumenTranslationApiClient(context.applicationContext) }
  ...
  onValueChange = { sourceText = it.take(5000); ... }          // 输入框已截断到 5000
  trimmedText.length > 5000 -> { errorMessage = textTooLongMessage; return }   // 永不成立
  ```
  ① 因为输入时已 `take(5000)`、而 `trim()` 只会变短，`translate()` 里的"文本过长"分支**不可达**，`translation_error_text_too_long` 是死文案；真正的服务端上限若小于 5000，客户端没有任何拦截。② `ProjectLumenTranslationApiClient` 的构造参数默认值会调 `SecureOkHttpFactory.create(...)`（`ProjectLumenTranslationApiClient.kt:33-37`），即**每次进入翻译页都新建一个 OkHttpClient**，各自带独立的 `ConnectionPool` 与 `Dispatcher` 线程池，且没有任何 `shutdown`/`evictAll`。③ 5000 这个上限散落三处，与 `ProjectLumenAppConstants.kt` 的常量集中约定相悖。
- 触发场景：反复进出翻译页（每次都新建一个客户端，空闲连接默认保留 5 分钟）；或后端把上限调成 3000 而客户端仍放行 5000 字请求。
- 影响：连接池与线程随访问次数累积（可回收但完全没必要）；上限漂移时用户只能收到服务端报错而非本地提示；死分支误导后续维护者以为已有长度保护。
- 修复方案：① 把客户端提到进程级——`ProjectLumenApplication` 已经在做手写依赖注入，把 `translationApiClient` 挂在 Application 上，页面改成 `remember { application.translationApiClient }`（与 `ProjectLumenApp.kt:172-178` 复用 `application.apiClient` 的做法一致）。② 把 `5000` 抽成 `ProjectLumenAppConstants.kt` 里的 `TRANSLATION_MAX_INPUT_CHARS`，三处引用同一常量；③ 删掉不可达的 `> 5000` 分支（连同 `translation_error_text_too_long` 字符串），或改成不在输入时截断、而在提交时校验（二选一，别两套都留）。
- 风险/注意：`SecureOkHttpFactory.create` 在 baseUrl 非 https 时会 `throw IllegalArgumentException`；目前它在 `remember{}` 里执行，即抛在组合期会整树崩溃。挪到 Application 后异常会前移到启动阶段，需要在那里裹 `runCatching` 并给出降级（服务不可用），不要让启动直接崩。

### [G09-20] `LumenToast` 的静态初始化包含 `Handler(Looper.getMainLooper())` 与 8 次 `Color.parseColor`，纯 JVM 单测一加载即 `ExceptionInInitializerError`
- 严重度：P2
- 类别：D 生命周期与框架约束
- 位置：`app/src/main/java/com/projectlumen/app/core/toast/LumenToast.kt:90`（`private val mainHandler = Handler(Looper.getMainLooper())`）、`:51-74`（枚举常量里 8 次 `Color.parseColor`）
- 现状：两处都在类初始化阶段调用 Android 框架 API。`app/build.gradle.kts` 未设置 `testOptions.unitTests.isReturnDefaultValues`，默认下未桩 android.jar 方法会直接抛异常：`Looper.getMainLooper()` 抛 "not mocked"（若开了 returnDefaultValues 则返回 null 并让 `Handler(null)` 抛 NPE），`Color.parseColor` 同样抛 "not mocked"。二者都发生在 `<clinit>`，表现为 `ExceptionInInitializerError`。
- 触发场景：给 `core/services/NotificationService` 的文案构造加一个 JVM 单测（该目录已有 `ForegroundServiceControllerTest` 等纯 JVM 测试）。`NotificationService.kt:317-327` 里 `LumenToast.richMessage(..., color = LumenToastKind.WARNING.accentColor)` 会同时触发 `LumenToastKind` 与 `LumenToast` 的类初始化，测试立刻失败，且报错指向 `<clinit>` 而非真实原因，排查成本高。
- 影响：把"任何触及 toast 的代码"整体挡在纯 JVM 单测之外；这是 brief 的 D 类明确点名的模式。
- 修复方案：① `mainHandler` 改成 `by lazy { Handler(Looper.getMainLooper()) }`（只有真正弹 toast 时才初始化）。② 枚举里的 `Color.parseColor("#RRGGBB")` 改成编译期常量 `0xFF126B66.toInt()` 之类的字面量，彻底不碰框架 API（与 G09-09 建议的"色值下沉到 xml 资源"合并做更好：改成 `@ColorRes` 常量后类初始化不再有任何框架调用）。
- 风险/注意：`by lazy` 默认是同步的（`LazyThreadSafetyMode.SYNCHRONIZED`），首次弹 toast 时有一次极小的加锁开销，可接受；`accentColor(darkTheme)` 与 `accentColor` getter 的返回值必须与现有 6 个色值逐位一致，否则会悄悄改变通知的 `setColor`。

## 已核查但无问题的点

- **WebView 安全整体过关**（`ProjectLumenWebViewScreen.kt`）：`settings.javaScriptEnabled = BuildConfig.DEBUG`（release 关闭）；`addJavascriptInterface` 只在 `BuildConfig.DEBUG` 下注入，且 `ProjectLumenWebViewJsApi` 只暴露包名/应用名/版本号/渠道/debuggable，无敏感数据、无反射入口，方法均带 `@JavascriptInterface`；**未覆写 `onReceivedSslError`**（默认行为是 cancel，全仓库 `rg` 无任何 `handler.proceed()`）；`DisposableEffect` 的 `onDispose` 里 `destroy()`；`AndroidView` 外层套 `key(url)` 保证换 URL 时重建。`allowFileAccess` / `allowFileAccessFromFileURLs` / `allowUniversalAccessFromFileURLs` 虽未显式关闭，但 `targetSdk 37`（≥30）下 `allowFileAccess` 默认已为 false，另两项自 API 16 起默认 false——**不要为了"看起来更安全"去补这三行以外的东西，但补上也无害**。
- **URL 白名单方向正确**：`isProjectLumenRepoUrl` 强制 `https` + 精确前缀（`==` / `/` / `?` / `#` 四种边界），`https://github.com/Chloemlla/Project-Lumen@evil.com/` 这类 userinfo 混淆无法通过；非白名单 URL 一律外跳浏览器而不是在 WebView 内加载。仅 scheme 校验需按 G09-13 收紧。
- **时间格式化没有 `SimpleDateFormat` 问题**：`ProjectLumenUiFormatters.kt` 里没有任何 `SimpleDateFormat`/`DecimalFormat` 构造；`crashDetailsTimeFormatter`、`updateDialogTimeFormatter`（`ProjectLumenAppConstants.kt:208-209`）是顶层 `DateTimeFormatter`——不可变、线程安全、只构造一次，是正确写法。`ProjectLumenBuildUpdateNotesScreen.kt:110-122` 的本地化时间也正确地 `remember(buildTimeUtcMillis, locale.toLanguageTag())` 并带双层 `runCatching` 兜底。
- **`isAutoDarkActive`（`ProjectLumenUiFormatters.kt:241-253`）时区与跨午夜逻辑正确**：用 `ZoneId.getOffset(Instant)` 取当日实际偏移（含夏令时），`floorDiv`/`mod` 处理负值，`start == end` 视为关闭、`start > end` 走跨午夜分支。不要改。
- **设计令牌有硬编码兜底**：`LumenUiTokens.load` 整体 `runCatching{}.getOrElse { LumenUiTokens() }`，每个字段又各自 `optFloat(name, fallback)`，`titleFontWeight`/`titleMaxLines` 还做了 `coerceIn`。asset 缺失或字段损坏不会白屏或崩溃。（`page` 那组没有 `coerceIn`，但值来自随包 asset 而非用户输入，暂不视为缺陷。）
- **模板调色板与主题模式的优先级是明确且有意的**：`Theme.kt:86-93` 让模板调色板决定明暗（`templatePaletteIsDark ?: themeMode`），`ProjectLumenUiFormatters.kt:318-326` 的注释明确写了"只在显示上抑制、绝不回写用户偏好"，`ProjectLumenSettingsScreen.kt:573-580` 用窄 key `remember` 计算开关可用性。**这套"不覆盖用户存储偏好"的做法是正确的，修 G09-08 时不要顺手改成回写 `settings.themeMode`。**
- **`SettingsSectionGroupController` 的双单调 token 设计正确**：`expandToken`/`collapseToken` 递增 + 各分区 `LaunchedEffect(token)` 各响应一次，既能全展开/全折叠又保留各分区自己的局部状态；`collapseAll` 还正确跳过了 `forceExpanded` 的分区。
- **`ProjectLumenFirstOpenGateResolver` 是纯函数、可单测、职责清晰**：输入全部由 `ProjectLumenFirstOpenGateInput` 显式给出，无 `Context`、无静态依赖，三个门禁的优先级（OSS → 引导 → 构建说明）一目了然。`Resolver`/`Host`/`Entry` 的三段拆分是合理的（判定 / 渲染 / 状态与持久化），**不是过度拆分**。
- **法务长文案走的是资源不是 Kotlin**：`LegalDocumentScreen` 只接 `@StringRes bodyRes` 并按 `\n` 分段渲染，正文全在 `res/values*/legal_*_strings.xml`，没有"Kotlin 里也存一份"的双真相源问题。
- **翻译页的协程作用域与并发点击**：`rememberCoroutineScope()` 随页面销毁取消；翻译按钮 `enabled = !translating && trimmedText.isNotBlank() && serviceEnabled`，刷新按钮 `enabled = !loadingConfig`，正常操作下不会并发重复请求。
- **`Onboarding`/`OssNotice`/`BuildUpdateNotes` 的静态列表都已 `remember`**：`rememberOnboardingPages()`（5 页）与 `rememberProjectLumenCredits()`（15 条）都是 `remember {}`，不会每次重组重建。
- **花括号与圆括号平衡**：本组 32 个文件逐个统计 `{`/`}` 与 `(`/`)` 全部配对（例如 `ProjectLumenSharedComponents.kt` 97/97、343/343；`LumenToast.kt` 69/69、260/260），无 H 类语法结构风险；`when` 分支在 `EmptyStateIllustration`（4 个）、`UpdateDialogState`（7 个）等处均穷尽。


