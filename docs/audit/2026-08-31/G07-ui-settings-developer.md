# G07 UI 层——设置 / 隐私中心 / 开发者调试面板 / Shizuku 设置 / 权限门禁 审查报告

- 审查文件数：14，总行数：4622
- 结论摘要：这一组的**状态流向是干净的**（全部经 `viewModel` 回调写库，没有一处 UI 直读 DAO/Repository），`remember` 的 key 也比预期严谨得多——`activeTemplate` / `templateAppearanceLocksThemeMode` / `isFamilyEyeCareModeActive` 三处纯函数的 key 都完整覆盖了被读字段，不存在"UI 不刷新"的经典漏 key。真正的问题集中在三处：**（1）权限漏斗是个死胡同**——权限被永久拒绝后只弹一个 Toast，而"打开系统通知设置"按钮的显示条件写反了（`if (!notificationPermissionNeeded)`），于是"拒绝两次 → 通知/相机功能在应用内永远开不起来"；同时 `activePermissionSetupTarget` 在用户放弃授权后永不清空，导致对应权限行永久停在"完成后自动返回"的引导态、分区被 `forceExpanded` 钉住而"全部折叠"对它静默无效。**（2）`rememberPermissionRequirements()` 没有 `remember`**，而 `uiState.nowMillis` 每秒推一次新实例，于是设置页/开发者页打开期间每秒执行 7 次系统 binder 查询（AppOps × 2、AlarmManager、NotificationManager、Settings.canDrawOverlays、Settings.System.canWrite）。**（3）`ProjectLumenSettingsScreen.kt` 的 `SettingsScreen` 是一个 1070 行的单函数**（16 个设置分区 + 49 处 `updateSettings` + 5 个内联权限状态机函数全挤在一个 Composable 体里），直接违反仓库"禁止超级文件"的硬规。另外 `ProjectLumenSettingsScreen.kt` 与 `ProjectLumenPermissionGates.kt` 的 import 块是整块复制粘贴的：前者 198 条 import 里 125 条未使用，后者 246 行的文件里有 196 条 import、其中 182 条未使用（真正的代码只有 45 行）。开发者面板的发布风险经核查**可接受**：入口是 About 页版本号连点 7 次的隐藏门禁，后端地址是只读展示不可编辑，API 诊断日志在 `ProjectLumenApiDiagnostics` 层已做 JSON 键名脱敏，面板里没有明文 token / 密钥。

## 缺陷清单

### [G07-01] 权限被永久拒绝后应用内没有任何补救路径，"打开系统通知设置"按钮的显示条件还写反了
- 严重度：P1（接近 P0：通知与相机类功能在应用内彻底无法启用）
- 类别：D 生命周期与框架约束
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenPermissionGates.kt:204-220`、`:227-243`；`app/src/main/java/com/projectlumen/app/app/ProjectLumenSettingsScreen.kt:877-883`（条件写反）、`:833-850`、`:912-929`、`:1031-1043`
- 现状：
  ```kotlin
  // ProjectLumenPermissionGates.kt:204
  val permissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
      if (granted) action?.invoke()
      else Toast.makeText(context, ...notification_permission_denied_message..., LENGTH_LONG).show()
  }
  // ProjectLumenSettingsScreen.kt:877
  if (!notificationPermissionNeeded) {          // ← 反了
      OutlinedButton(onClick = { openAppNotificationSettings(context) }) { ... }
  }
  ```
  两个门禁全程没有调用 `shouldShowRequestPermissionRationale`，也没有在被拒后引导到系统设置。而唯一那个"打开系统通知设置"的按钮，只在**权限已经拿到**时才显示。
- 触发场景：Android 11+ 对同一权限连续拒绝两次后即进入"永久拒绝"，`launch()` 会立刻回调 `granted=false` 而不再弹系统对话框。用户此后每次点"启用通知"开关、或点 `NotificationRequirementCard` 的"允许通知"、或点隐私中心该行的"去处理"，都只会闪一个 Toast。相机同理（距离监测 / 眨眼监测），且相机路径连一个"打开系统设置"的入口都不存在。
- 影响：护眼类核心能力（提醒通知、距离监测、眨眼监测）在应用内变成永久不可开启，用户只能自己猜到要去"系统设置 → 应用 → 权限"里手动开。开关被点后 UI 也不给任何解释，看起来像"这个开关坏了"。
- 修复方案：
  1. `ProjectLumenPermissionGates.kt`：把两个 gate 的 denied 分支改为——先用 `(context as? Activity)?.shouldShowRequestPermissionRationale(perm)`；返回 `false` 且此前已请求过（用一个 `rememberSaveable` 的 `hasRequestedOnce` 标记区分"首次"与"永久拒绝"）时，不弹 Toast，而是弹一个说明对话框并提供跳转 `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`（`package:` uri）的按钮；跳转沿用 `ProjectLumenSystemSettingsIntents.kt` 里已有的 `startFirstAvailableSettingsActivity` 兜底写法（把它改成 `internal` 并新增 `openAppDetailsSettings(context)`）。
  2. `ProjectLumenSettingsScreen.kt:877` 的条件删掉取反或整体去掉守卫——"打开系统通知设置"按钮应当**始终**可见（权限已授予时它是入口，未授予时它是唯一出路）。
  3. 相机的 `NotificationRequirementCard`（`:912`、`:1031`）在永久拒绝态下把 `actionLabelRes` 换成 `R.string.open_system_settings`、`onClick` 换成跳应用详情页。
- 风险/注意：`shouldShowRequestPermissionRationale` 需要 `Activity` 而不是 `Context`，`LocalContext.current` 在本项目里是 `MainActivity`（单 Activity 架构），但仍要用 `as?` 并对 null 走"直接跳系统设置"的降级路径，不能强转。第 2 条改动会让已授予权限的用户也多看到一个按钮，属于预期变化。

### [G07-02] 用户放弃授权后 `activePermissionSetupTarget` 永不清空，权限行永久停在"引导中"、"全部折叠"对该分区静默失效
- 严重度：P2
- 类别：D 生命周期与框架约束
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenSettingsScreen.kt:283-290`（`scrollToPermissionTarget`）、`:309-315`、`:541-567`（唯一的自动清空点）、`:387`（另一条清空路径）、`:819`/`:900`/`:1019`/`:1269`/`:1290`（各分区 `forceExpanded`）；`app/src/main/java/com/projectlumen/app/app/ProjectLumenSettingsPrivacyCenter.kt:68`、`:459-465`（"完成后自动返回"提示）
- 现状：
  ```kotlin
  fun scrollToPermissionTarget(target: PermissionSetupTarget, returnAfterCompletion: Boolean) {
      activePermissionSetupTarget = if (returnAfterCompletion) target else null
      permissionReturnScrollPosition = scrollState.value
      permissionAnchorPositions[target]?.let { ... }   // USAGE_ACCESS 根本没有注册锚点
  }
  ```
  `activePermissionSetupTarget` 只有两条清空路径：`LaunchedEffect`（`:562-565`，条件是 `isPermissionTargetConfigured(target)` 变真）和 `setPermissionTargetEnabled(target, false)`（`:387`，要求该行有开关）。用户在系统设置页**点返回而不授权**时两条都不成立——没有任何"放弃"路径。
- 触发场景：在隐私与权限中心点"使用情况访问权限"行的"去处理" → 跳到系统 Usage Access 页 → 直接返回不授权。此时 `activePermissionSetupTarget = USAGE_ACCESS` 被永久钉住：它既没有注册锚点（`SettingsScrollAnchor(s)` 的注册清单只有 STATISTICS / NOTIFICATIONS / EXACT_ALARM / FULL_SCREEN / DISTANCE_CAMERA / BLINK_CAMERA / AMBIENT_LIGHT / BRIGHTNESS / OVERLAY / KEEP_ALIVE / DIAGNOSTICS / SHIZUKU），也没有开关可以把它置回（该行 `switchChecked = null`，`ProjectLumenSettingsPrivacyCenter.kt:124-134`）。EXACT_ALARM / FULL_SCREEN / OVERLAY / BRIGHTNESS / SHIZUKU 同理——只要用户跳去系统设置后改变主意。
- 影响：(a) 该权限行永久显示 `settings_permission_return_hint`（"完成后自动返回"）并保持 primaryContainer 高亮，用户以为还有流程没走完；(b) 隐私中心与目标所在分区的 `forceExpanded` 恒为 true，顶部工具栏的"全部折叠"对它们静默无效（`ProjectLumenSharedComponents.kt:556-558` 的 `collapseToken > 0 && !forceExpanded`）；(c) 该状态是 `rememberSaveable`，跨页面来回导航都不消失，只有进程被杀才复位；(d) 用户若在很久之后才在系统设置里补授该权限，效应会用当初记录的 `permissionReturnScrollPosition` 把页面滚到一个与当前无关的位置。**注意不是死锁**：本组所有 `forceExpanded` 调用点都走 `SettingsSection` 的 `titleRes` 重载（`ProjectLumenSharedComponents.kt:578-607`），该重载在 `forceExpanded` 时**保留**了 header 的 `clickable` 与折叠箭头（只有 `title: String` 重载会移除，本组无人使用），所以用户仍能手动折叠——但状态本身是错的。
- 修复方案：在 `ProjectLumenSettingsScreen.kt` 增加"回到前台时若目标仍未满足就放弃引导"的收尾：
  ```kotlin
  val lifecycleOwner = LocalLifecycleOwner.current
  var permissionSetupResumeToken by remember { mutableIntStateOf(0) }
  DisposableEffect(lifecycleOwner) {
      val observer = LifecycleEventObserver { _, event ->
          if (event == Lifecycle.Event.ON_RESUME) permissionSetupResumeToken += 1
      }
      lifecycleOwner.lifecycle.addObserver(observer)
      onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }
  LaunchedEffect(permissionSetupResumeToken) {
      val target = activePermissionSetupTarget ?: return@LaunchedEffect
      if (permissionSetupResumeToken > 0 && !isPermissionTargetConfigured(target)) {
          activePermissionSetupTarget = null   // 用户放弃了
      }
  }
  ```
  （`permissionReturnScrollPosition` 不要在这里回滚，避免用户刚返回就被强行滚走。）另外给 `PermissionSetupTarget.USAGE_ACCESS` 在隐私中心那一节外面补一个 `SettingsScrollAnchor`，否则"去处理"点下去页面完全没有视觉反馈。
- 风险/注意：`isPermissionTargetConfigured` 里读的 `permissionRequirements` 也是 ON_RESUME 刷新的，两个 observer 的回调顺序不保证；用 token 作为 `LaunchedEffect` key 时该效应会在下一次组合后才跑，届时 `permissionRequirements` 已是新值，是安全的。若图省事写在同一个 observer 里同步判断，会读到旧的权限快照，反而误清刚刚授权成功的目标。采纳 G07-05 的控制器化拆分后，这段收尾逻辑应放进 `SettingsPermissionSetupController`。

### [G07-03] `rememberPermissionRequirements()` 没有 `remember`，设置页/开发者页打开期间每秒执行 7 次系统 binder 查询
- 严重度：P1
- 类别：D 生命周期与框架约束（兼 B 主线程开销）
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenPermissionState.kt:42-45`、`:47-57`；调用点 `ProjectLumenSettingsScreen.kt:249`、`ProjectLumenDeveloperDebugScreen.kt:93`
- 现状：
  ```kotlin
  return refreshKey.let {
      context.permissionRequirements()      // 没有 remember，纯粹靠读 refreshKey 建立订阅
  }
  ```
  `permissionRequirements()` 一次会做 7 次跨进程查询：`ContextCompat.checkSelfPermission`×2、`AlarmManager.canScheduleExactAlarms()`、`NotificationManager.canUseFullScreenIntent()`、`Settings.canDrawOverlays()`、`Settings.System.canWrite()`、`AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS)`（`core/insights/AndroidDeviceInsightDataSource.kt:288`）。后 5 项都是真 binder / ContentProvider 调用。
- 触发场景：返回值非 Unit 的 Composable 不可跳过，宿主每重组一次它就整体重跑一次。而 `SettingsScreen` 读了 `uiState.nowMillis`（`:261` 传给 `mainBackendUiDecision`），`nowMillis` 由 `ProjectLumenRuntimeFeatureEntry.startClock` 的 `while(true){ …; delay(1_000) }` 每秒推一次新的 `ProjectLumenUiState`。于是只要设置页或开发者页在前台，就是**每秒 7 次主线程 IPC**；拖动任意滑杆时重组频率还会升到每帧一次，变成每帧 7 次 IPC。
- 影响：设置页滑杆拖动掉帧、待在设置页时持续耗电；`AppOpsManager` 与 `Settings.System.canWrite` 在部分国产 ROM 上有额外的权限管理层，单次耗时可达毫秒级，叠加后是可感知的卡顿。
- 修复方案：`ProjectLumenPermissionState.kt` 把 `return refreshKey.let { ... }` 改成
  ```kotlin
  return remember(refreshKey, context) { context.permissionRequirements() }
  ```
  语义完全不变（`refreshKey` 仍然在 ON_RESUME 递增来强制重查），但每个前台周期只查一次。
- 风险/注意：这样一来"权限在应用内被系统弹窗直接授予"后不会立刻刷新——但现在也不会（`permissionRequirements` 只在 ON_RESUME 变），而系统权限对话框关闭必然伴随 ON_RESUME，因此行为一致。若后续需要在不离开应用的情况下刷新，给 `rememberPermissionRequirements` 加一个显式的 `refresh()` 返回值即可，不要退回每帧重查。

### [G07-04] `pendingBackupImportUri` 用 `remember` 而其配对状态在 ViewModel 里，Activity 重建后备份导入对话框的"确认"变成死键
- 严重度：P2（需确认：触发面被 `configChanges` 收窄，见下）
- 类别：D 生命周期与框架约束
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenSettingsScreen.kt:272`、`:465-487`
- 现状：
  ```kotlin
  var pendingBackupImportUri by remember { mutableStateOf<Uri?>(null) }   // 组合作用域
  ...
  backupImportPreview?.let { summary ->                                   // ViewModel 作用域
      val targetUri = pendingBackupImportUri
      BackupImportDialog(..., onConfirm = { if (targetUri != null) { viewModel.importBackup(targetUri) ... } })
  }
  ```
  对话框的显示条件来自 ViewModel（`viewModel.backupImportPreview`），而它执行导入所需的 uri 存在组合里。两者生命周期不同，一旦只有前者存活，对话框会照常显示，但"确认"分支被 `targetUri != null` 静默吞掉。
- 触发场景：需要一次"销毁并重建 Activity、但保留 ViewModel"的配置变更。`AndroidManifest.xml:61` 已经声明了 `orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|uiMode`，所以旋转和深色模式切换不会触发；**未**覆盖的是 `locale` / `fontScale` / `density`——即用户在对话框打开期间改了系统字体大小、显示大小或系统语言。进程被杀的场景不受影响（ViewModel 一起没了，对话框不会显示）。
- 影响：用户看到"导入备份"确认框，点"确认"毫无反应且无任何提示，只有"取消"有效，且再次点击也不会恢复——必须重新走一次文件选择。
- 修复方案：`Uri` 是 `Parcelable`，把 `:272` 改成 `var pendingBackupImportUri by rememberSaveable { mutableStateOf<Uri?>(null) }` 即可。更稳的做法是把 uri 一起收进 ViewModel 的 `backupImportPreview` 状态里（`previewBackupImport(uri)` 已经拿到了 uri），让"预览"与"待导入 uri"成为同一个真相源；这需要改 `ProjectLumenBackupFeatureEntry`（属别组文件），故本组只做 `rememberSaveable` 的最小修复。
- 风险/注意：`rememberSaveable` 会把 uri 写进 Bundle，`content://` uri 不含敏感数据，但重建后该 uri 的读权限仍需有效——`ActivityResultContracts.OpenDocument()` 授予的是任务级读权限，同进程内的 Activity 重建不会失效，因此无需额外 `takePersistableUriPermission`。

### [G07-05] `SettingsScreen` 是一个 1070 行的单 Composable，违反仓库"禁止超级文件"硬规
- 严重度：P1
- 类别：A 架构与设计
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenSettingsScreen.kt:234-1303`（单个函数）；对照 `ProjectLumenDeveloperDebugScreen.kt:85-416`、`ProjectLumenShizukuSettingsSection.kt:48-93`
- 现状：`SettingsScreen` 一个函数里塞进了：24 个 `val`/`var` 状态声明（`:244-276`）、5 个内联的本地函数（`requestReminderTimingPermissions`、`scrollToPermissionTarget`、`isPermissionTargetConfigured`、`startPermissionSetup`、`setPermissionTargetEnabled`、`scrollToGrowthTarget`、`markGrowthApplyStarted`、`isGrowthTargetConfigured`，共约 170 行权限/成长引导状态机）、4 个 `LaunchedEffect`（其中两个的 key 列表各有 12/19 项）、3 个对话框、16 个 `SettingsSection`、49 处 `viewModel.updateSettings { current -> current.copy(...) }`。锚点用的 `SettingsScrollAnchors` 还需要手工嵌套并配对花括号（`:621-632`、`:886-895`、`:1008-1009`），缩进已经和实际嵌套层级脱节（`:582-583` 的 `CompositionLocalProvider { LumenPage {` 之后整块不缩进）。
- 触发场景：任何一次修改。新增一项设置要在 `PermissionSetupTarget`、`isPermissionTargetConfigured`、`startPermissionSetup`、`setPermissionTargetEnabled`、两个 `LaunchedEffect` 的 key 列表、锚点注册、UI 分区共 7 处同步改动——G07-06 就是这种"7 处改了 6 处"的实例。
- 影响：可维护性；同时是本组几条真实 bug 的共同根因（key 列表漏项、状态机没有收尾路径）。
- 修复方案：按"状态机下沉 + 分区拆文件"两步走，不要只做机械切分。
  1. 新建 `ProjectLumenSettingsPermissionSetupController.kt`：把 `PermissionSetupTarget` 的状态机（`activePermissionSetupTarget`、`permissionReturnScrollPosition`、`permissionAnchorPositions` 与 `isPermissionTargetConfigured` / `startPermissionSetup` / `setPermissionTargetEnabled` / `scrollToPermissionTarget`）收成一个 `@Stable class SettingsPermissionSetupController` + `rememberSettingsPermissionSetupController(...)`。判定函数改成接受显式参数（`settings`、`permissionRequirements`、`shizukuReady`、`backendFeaturesVisible`），key 列表由控制器内部用 `snapshotFlow` 或单个 data class 快照统一驱动，从根上消灭"漏 key"。
  2. 同样新建 `ProjectLumenSettingsGrowthController.kt` 承载 `GrowthConfigTarget` 那一套（`:431-453`、`:499-540`）。
  3. 分区按主题拆成 4 个文件，每个暴露一个 `internal fun XxxSettingsSections(...)`：`ProjectLumenSettingsGeneralSections.kt`（general/appearance/data/update）、`ProjectLumenSettingsTimingSections.kt`（reminder/pre_alert/pomodoro/quiet_hours/goals/notifications/keep_alive）、`ProjectLumenSettingsSensingSections.kt`（proximity/eye_protection/sound）、`ProjectLumenSettingsAccountSections.kt`（cloud/shizuku/developer 入口）。公共的锚点常量（`GeneralGrowthAnchors` 等 `:209-232`）移到 `ProjectLumenSettingsAnchors.kt`。
  4. `SettingsScreen` 本体收缩到 ~80 行：取状态、建两个 controller、按顺序调 4 个分区函数。
  `ProjectLumenDeveloperDebugScreen.kt`（806 行）**同病但轻**：最大函数 `DeveloperDebugScreen` 约 330 行，其余是私有小组件；建议只把 `SettingsSection(R.string.developer_section_api_security)` 那一段（`:264-367`，含购买校验表单）拆到 `ProjectLumenDeveloperApiSecuritySection.kt`。`ProjectLumenShizukuSettingsSection.kt`（620 行）**结构健康**，入口函数仅 46 行，其余已按 `ShizukuQuickActions` / `ShizukuDiagnosticUploadSettings` / `ShizukuNativeEyeProtectionSettings` / `ShizukuSamplingGuardSettings` 分解，不必动。
- 风险/注意：拆分时 `SettingsScrollAnchors` 依赖 `LumenPage` 的 `Column` 直接子级顺序来计算 `positionInRoot`，分区函数必须是 `ColumnScope.() -> Unit` 且**不能**再包一层 `Column`，否则 `Arrangement.spacedBy` 的间距和锚点位置都会变。`LocalLumenPageScrollState` / `LocalSettingsSectionGroup` 是 CompositionLocal，跨文件调用不受影响。

### [G07-06] 权限收尾 `LaunchedEffect` 的 key 漏了 `settings.shizukuNativeEyeProtectionEnabled`，靠 Shizuku 满足亮度权限时引导不会收尾
- 严重度：P2
- 类别：D 生命周期与框架约束
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenSettingsScreen.kt:541-567`（key 列表）对照 `:291-308`（`isPermissionTargetConfigured`，第 303-304 行的 BRIGHTNESS 分支）与 `:256`
- 现状：
  ```kotlin
  val shizukuNativeBrightnessEnabled = settings.shizukuAdvancedModeEnabled && settings.shizukuNativeEyeProtectionEnabled  // :256
  ...
  PermissionSetupTarget.BRIGHTNESS -> settings.autoBrightnessEnabled &&
      (!writeSettingsPermissionNeeded || shizukuNativeBrightnessEnabled)                                                  // :303
  ...
  LaunchedEffect(activePermissionSetupTarget, settings.statsEnabled, ..., settings.shizukuAdvancedModeEnabled, ...) {      // :541
      val target = activePermissionSetupTarget ?: return@LaunchedEffect
      if (isPermissionTargetConfigured(target)) { activePermissionSetupTarget = null; ...animateScrollTo(...) }
  }
  ```
  19 项 key 里有 `shizukuAdvancedModeEnabled`，但**没有** `shizukuNativeEyeProtectionEnabled`，而判定函数经由 `shizukuNativeBrightnessEnabled` 实际读了它。
- 触发场景：Shizuku 高级模式**已经**打开的用户，从隐私中心点"自动亮度"的"去处理"（`activePermissionSetupTarget = BRIGHTNESS`），然后不去授 WRITE_SETTINGS，而是在 Shizuku 分区打开"原生护眼"。此时 BRIGHTNESS 实质已满足，但 19 个 key 一个都没变（`shizukuAdvancedModeEnabled` 本来就是 true），`LaunchedEffect` 不重启，捕获的仍是旧的 `isPermissionTargetConfigured` 闭包。
- 影响：护眼分区保持 `forceExpanded`（折叠箭头消失，见 G07-02）、该行一直显示"完成后自动返回"提示、不会自动滚回原位置；要等用户碰巧改动了另外 19 项里的任意一项才自愈。
- 修复方案：在 `:552` 后补一行 `settings.shizukuNativeEyeProtectionEnabled,`。若采纳 G07-05 的第 1 步（控制器化），更彻底的做法是把 19 个 key 换成一个显式快照 data class：`LaunchedEffect(activePermissionSetupTarget, PermissionSetupSnapshot(settings, permissionRequirements, shizukuState.ready, backendFeaturesVisible))`，由 data class 的 `equals` 兜住所有字段，之后新增判定字段不必再回来改 key 列表。
- 风险/注意：`backendFeaturesVisible` 同样被 `isPermissionTargetConfigured` 的 DIAGNOSTICS 分支读到且不在 key 列表里，但 `:454-464` 有一个独立的 `LaunchedEffect(backendFeaturesVisible)` 专门清 DIAGNOSTICS，已被覆盖——补 key 时不要顺手删掉那个效应。

### [G07-07] `updateSettings` 的"改动前基线"是独立的一次读取，并发写设置时会漏掉距离监测重排与 Shizuku 护眼重下发
- 严重度：P2（需确认：竞态窗口等于一次 Room+DataStore 往返，见触发场景）
- 类别：B 并发与线程安全
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenSettingsFeatureEntry.kt:43-74`（尤其 `:48-49`、`:70`）
- 现状：
  ```kotlin
  scope.launch {
      val current = settingsRepository.getOrDefault()              // 第 1 次读
      val updated = settingsRepository.update(nowMillis, transform) // 内部再读一次、算、写
      val shouldRescheduleProximity = ... current.xxx != updated.xxx ...
      if (shouldRescheduleProximity) scheduleProximityMonitoring()
      if (current.hasShizukuNativeEyeProtectionChange(updated)) applyShizukuNativeEyeProtectionSettings(updated, true)
  }
  ```
  `current` 与 `update` 内部用于计算的那份 current 是两次独立读取，中间没有任何互斥（`SettingsRepository.update` 本身也是 `getOrDefault()` → `transform` → `dao.upsert`，无 `Mutex`）。
- 触发场景：`scope` 是 `reportingScope = CoroutineScope(viewModelScope.coroutineContext + handler)`（`ProjectLumenViewModel.kt:106`、`:125`），即 `Dispatchers.Main.immediate`，因此多个 `updateSettings` 不会真正并行，但会在 `getOrDefault()` / `update()` 内部的挂起点交错——竞态窗口约等于一次 Room + DataStore 往返（毫秒级）。可达路径：`startPermissionSetup` 的 DIAGNOSTICS 分支（`ProjectLumenSettingsScreen.kt:323-331`）在一次点击里先 `updateSettings` 再 `requestShizukuAuthorization`；Shizuku 快捷预设按钮（`ProjectLumenShizukuSettingsSection.kt:496-536`）与紧随其后的开关操作；以及冷启动时 `applyStartupMonitoring` 与用户首个开关操作重叠。注意 `NumberSlider` 只在 `onValueChangeFinished` 提交一次（`ProjectLumenFormControls.kt:297-300`），所以拖动滑杆**不会**产生写入风暴，这一点与直觉相反，也是本条降为 P2 的原因。
- 影响：两类真实后果——(a) `shouldRescheduleProximity` 为假，`AlarmManager` 精确闹钟没有按新的 `proximityCheckIntervalMinutes` 重排，距离监测仍按旧周期跑，用户改了间隔却"没生效"；(b) `hasShizukuNativeEyeProtectionChange` 为假，新的色温/亮度不会通过 Shizuku 下发，护眼滤镜停在旧参数。两者都表现为"改了设置没反应"，且下一次任意设置改动才会补上。
- 修复方案：本文件内即可闭环，不必改 repository——把基线从"额外读一次"改成"从写入实际使用的那一份里捕获"：
  ```kotlin
  fun updateSettings(transform: (AppSettingsEntity) -> AppSettingsEntity, nowMillis: Long = System.currentTimeMillis()) {
      scope.launch {
          var before: AppSettingsEntity? = null
          val updated = settingsRepository.update(nowMillis) { current -> before = current; transform(current) }
          val previous = before ?: updated
          ... 用 previous 代替 current 做全部 diff ...
      }
  }
  ```
  这样 diff 的基线与写入的基线严格一致，同时少一次 DataStore + Room 读。若要彻底串行化，还应给 `SettingsRepository.update` 加 `Mutex`（属 core/repositories，别组）。
- 风险/注意：`transform` 会被 `update` 调用恰好一次，用 lambda 捕获是安全的；但 `before` 必须声明成 `var ... : AppSettingsEntity?` 而不是用 `lateinit`（`AppSettingsEntity` 是 data class，`lateinit` 不允许）。`applyStartupMonitoring` 走的是另一条路径，不受影响。

### [G07-08] 关闭距离监测时对 runtime 表做无锁的 get→copy→upsert，会覆盖 `ProximityDetectionService` 的并发写入
- 严重度：P1
- 类别：F 持久化一致性（兼 B）
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenSettingsFeatureEntry.kt:110-132`（`:122-129`）
- 现状：
  ```kotlin
  val state = runtimeRepository.getOrDefault()
  runtimeRepository.upsert(
      state.copy(proximityMonitoringActive = false, proximityTooClose = false, updatedAt = System.currentTimeMillis()),
  )
  ```
  读、改、写三步之间没有任何锁，而 `upsert` 是整行覆盖（`RuntimeStateEntity` 单行、id 固定）。
- 触发场景：用户关闭"距离监测"开关的瞬间，`ProximityDetectionService` 很可能正在写同一行（它会更新 `proximityLastRatioPercent`、`proximityDebugInferenceMillis`、`blinkLast*` 等字段）。相机 + ML Kit 的一次推理耗时几十到几百毫秒，正好落在这个窗口里。同一行还有计时引擎（`ProjectLumenRuntimeFeatureEntry` 每秒 `advanceDuePhases`）在写。
- 影响：服务刚写入的检测结果被这次 `upsert` 用旧快照整行盖回，表现为调试面板/设置页上的比例、眼睛张开度、推理耗时读数回跳；反过来若服务后写，`proximityMonitoringActive = false` 会被服务的旧快照复活成 true，监测状态显示错误。都是丢更新。
- 修复方案：`RuntimeRepository.upsert` 是"整个实体覆盖写"（`core/repositories/RuntimeRepository.kt:28-30` → `RuntimeStateMmkvStore.upsert`，MMKV 里存的是整份 state JSON），所以不存在"只写自己那几列"的余地，必须把读-改-写收进一把锁。做法：给 `RuntimeRepository` 增加 `suspend fun update(transform: (RuntimeStateEntity) -> RuntimeStateEntity): RuntimeStateEntity`，内部用 `Mutex` 包住 `get()` → `transform` → `upsert`（该文件里已经有一把 `migrationLock` 可以作为写锁的样板），然后把 `setProximityMonitoringEnabled` 的 `:122-129` 改成
  ```kotlin
  runtimeRepository.update { it.copy(proximityMonitoringActive = false, proximityTooClose = false, updatedAt = nowMillis) }
  ```
  `RuntimeRepository` 属 core/repositories（别组），本组只负责把调用点换过去；修复阶段需与负责 repository 的组同批提交，否则本条无法单独落地。
- 风险/注意：`:127` 用的是 `System.currentTimeMillis()` 而不是形参 `nowMillis`，改造时统一成 `nowMillis` 以免同一次操作出现两个时间戳。加锁后所有 runtime 写入者（`ProximityDetectionService`、计时引擎、`AlarmReceiver`）都必须改走 `update`，只改一半反而会让人误以为已经安全——这一点要在修复计划里写明。`ProximityDetectionService` 自身的 read-modify-write 属 G04 组范围，本条只覆盖设置入口这一侧。

### [G07-09] 隐私中心把同一批权限渲染了两遍（磁贴网格 + 长条列表），两套定义各写一份；`shizukuNativeBrightness` 判定有三份副本
- 严重度：P2
- 类别：A 架构与设计（同一事实多个真相源）
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenSettingsPrivacyCenter.kt:104-112`（磁贴）与 `:113-262`（长条列表）；`app/src/main/java/com/projectlumen/app/app/ProjectLumenSettingsPrivacyModel.kt:41-131`（`privacyControlTiles`）；`ProjectLumenSettingsScreen.kt:256` 对照 `ProjectLumenSettingsPrivacyModel.kt:193-195`
- 现状：`SettingsPrivacyPermissionCenter` 展开后先渲染 `PermissionControlTileGrid`（10 个磁贴，数据来自 `privacyControlTiles`），紧接着渲染 13 个 `PrivacyPermissionRow`——其中 10 个的 icon / titleRes / detailRes / checked / ready 与磁贴**逐字重复**，只是把 `privacyControlTiles` 里的表驱动写法在 Composable 里手抄了一遍。`DIAGNOSTICS` 的可见性同样有两套机制：模型里 `.filterNot { !backendFeaturesVisible && tile.target == DIAGNOSTICS }`（`:128-130`），组合里 `if (backendFeaturesVisible) { PrivacyPermissionRow(...) }`（`:135-148`）。"Shizuku 原生亮度可替代 WRITE_SETTINGS"这条规则有三份实现：`usesShizukuNativeBrightness(settings)`（模型 `:193`）、`PrivacyPermissionRow` 的内联表达式（`:234`）、`SettingsScreen` 的局部变量 `shizukuNativeBrightnessEnabled`（`:256`）。
- 触发场景：任何一次"给某个权限改文案 / 改就绪判定 / 新增一个权限项"的改动。磁贴与长条已经出现了实质分歧——磁贴集合缺少 `USAGE_ACCESS` / `EXACT_ALARM` / `FULL_SCREEN` 三项，而 `firstMissingPermissionTarget`（`:60`）会返回它们并交给"一键处理下一项"按钮，于是按钮指向的目标在磁贴区里根本不存在。
- 影响：(a) 维护成本翻倍且已经开始漂移；(b) 用户在同一个展开区里连续看到同一批开关两次（先磁贴再长条），交互语义重复；(c) G07-06 那个漏 key 的 bug 正是"第三份 `shizukuNativeBrightness` 副本"造成的。
- 修复方案：以 `ProjectLumenSettingsPrivacyModel.kt` 为唯一真相源。把 `PermissionControlTile` 扩成完整描述（补 `sensitive: Boolean`、`switchChecked: Boolean?`、`featureEnabled: Boolean`），让 `privacyControlTiles` 返回全部 13 项且不再做 `filterNot`（可见性交给调用方一次判断）；`ProjectLumenSettingsPrivacyCenter.kt` 的 `:113-262` 整段改成 `tiles.forEach { PrivacyPermissionRow(tile = it, activeTarget = ..., ...) }`；同时删掉磁贴网格或删掉长条列表二选一（建议保留长条列表，因为只有它带"去处理/配置"按钮和"完成后自动返回"提示）。`ProjectLumenSettingsScreen.kt:256` 的局部变量改为调用 `usesShizukuNativeBrightness(settings)`。
- 风险/注意：`privacyControlTiles` 有单元测试依赖（`app/src/test/java/com/projectlumen/app/app/BackendFeatureVisibilityTest.kt:33`、`:48` 断言 DIAGNOSTICS 的可见性），若把 `filterNot` 移出模型层，这两个测试必须同步改成断言调用方过滤后的结果，否则 CI 会红。

### [G07-10] 就绪评分与"待处理项"两套口径不一致：关掉统计/保活/环境光后评分永远到不了 100%，却没有任何一行提示该修什么
- 严重度：P2
- 类别：A 架构与设计
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenSettingsPrivacyModel.kt:152-172`（`privacyReadinessScore`，12 项）对照 `:174-191`（`privacyActionNeededCount`，9 项）；展示侧 `ProjectLumenSettingsPrivacyCenter.kt:470-504`（`PrivacyReadinessBadge`）
- 现状：`privacyReadinessScore` 的 12 项 checks 里有 3 项是"功能是否开启"而非"权限是否满足"：`settings.statsEnabled`、`settings.keepAliveEnabled`、`settings.ambientLightMonitoringEnabled`。`privacyActionNeededCount` 的 9 项里没有它们。长条行的色调判定是 `actionNeeded = featureEnabled && !ready`（`ProjectLumenSettingsPrivacyCenter.kt:374`），功能关着时是 `Off` 而不是 `Attention`。
- 触发场景：用户主动关掉"启用统计"（隐私偏好，完全合理），其余权限全部满足。此时 `actionNeededCount = 0`、`readinessScore = 11/12 = 91`，徽章走 `else` 分支显示"91%"，进度条也停在 91%，但 13 行里没有任何一行是"需处理"。三项都关掉则是 75%。
- 影响：用户看到一个永远达不到 100% 的"配置完成度"，且无从知道差在哪——这会诱导用户去打开自己并不想要的统计/保活/环境光采集，与"隐私中心"的定位相反。
- 修复方案：`privacyReadinessScore` 的口径改成与行/磁贴一致——只统计"已开启的功能其权限是否满足"，即把这三项从 `!x` 改成与其他项相同的 `!settings.xEnabled || <权限满足>` 形式（对这三项而言权限恒满足，故直接删除这三行、分母变 9，与 `privacyActionNeededCount` 的 9 项一一对应）。改完后 `readinessScore == 100 ⟺ actionNeededCount == 0`，徽章的三个分支才自洽。若产品确实想鼓励开启这三项，应另做一个"推荐配置"指标，不要混进"权限就绪度"。
- 风险/注意：`privacyReadinessScore` 同时被 `MetricRow(R.string.eye_care_config_score, ...)`（`:81`）和 `LinearProgressIndicator`（`:82-88`）使用，分母从 12 变 9 会让现有用户的百分比数字跳变（多数会变高），属预期变化。需检查 `R.string.eye_care_config_score` 在别处（成长能力卡）是否也展示同一个数值，避免两处口径再次分叉。

### [G07-11] `ProjectLumenSettingsScreen.kt` 与 `ProjectLumenPermissionGates.kt` 的 import 块是整块复制的，其中 307 条未被使用
- 严重度：P2
- 类别：H 编译与结构
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenSettingsScreen.kt:3-200`（198 条 import，125 条未使用）；`app/src/main/java/com/projectlumen/app/app/ProjectLumenPermissionGates.kt:3-198`（196 条 import，182 条未使用；该文件 246 行里只有 45 行是代码）
- 现状：两个文件的 import 头几乎逐行相同，明显是从同一个更大的文件拆分时整块复制的。`ProjectLumenPermissionGates.kt` 只用到 `Manifest`、`Settings` 之外的 8 个符号，却 import 了 `WebView` / `JavascriptInterface` / `WebViewClient` / `AndroidView` / `NavHost` / `rememberNavController` / `AppSettingsEntity` / `TipTemplateEntity` / `HttpURLConnection` / `JSONObject` / `UpdateChecker` / `ProjectLumenTheme` 等一整套无关依赖。`ProjectLumenSettingsScreen.kt` 同样 import 了整套 WebView、导航、`java.net`、`java.time`、Room 实体与 update 模块。
- 触发场景：不会导致 CI 失败（`app/build.gradle.kts:254` 的 `lint { disable += "GradleDependency" }` 之外没有配置 ktlint/detekt，Android Lint 也没有未使用 import 的检查项，kotlinc 只报 warning）。真实代价在阅读与工具层面：`ProjectLumenPermissionGates.kt` 从 import 看像是一个依赖 WebView 与导航栈的重型 UI 文件，实际只是两个权限 gate；任何按 import 判断模块耦合的重构（包括本次审查的分组）都会被误导。
- 影响：可维护性 / 误导性耦合信号；也是"从超级文件里切出来的碎片没有收尾"的直接证据。
- 修复方案：两个文件都删到只剩实际使用的 import。`ProjectLumenPermissionGates.kt` 最终只需要：`android.Manifest`、`androidx.activity.compose.rememberLauncherForActivityResult`、`androidx.activity.result.contract.ActivityResultContracts`、`androidx.compose.runtime.{Composable, getValue, remember, mutableStateOf, setValue}`、`androidx.compose.ui.platform.LocalContext`、`android.widget.Toast`、`com.projectlumen.app.R`（若采纳 G07-01 还会新增 `Activity` 与跳转函数）。`ProjectLumenSettingsScreen.kt` 建议在 G07-05 拆分之后再清理，避免两次改同一片区域造成冲突。注意 `androidx.compose.runtime.getValue` / `setValue` 是 `by` 委托的隐式操作符，删不得（用文本搜索判断"未使用"时会误报这两个）。
- 风险/注意：删 import 时必须逐个确认没有被"同名但来自另一个包"的符号顶替；本组核对方式是对每个 import 的末段标识符在文件内做 `\b<name>\b` 计数，计数为 1（即只有 import 行自己）才判为未使用，`getValue`/`setValue` 已按上述规则排除。

### [G07-12] 开发者面板在 composition 期每秒做一次 `PowerManager` binder 查询，并对 ~40 个诊断行重跑字符串折行
- 严重度：P2
- 类别：D 生命周期与框架约束（组合期做 IO/系统查询）
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenDeveloperDebugScreen.kt:200-208`（`isIgnoringBatteryOptimizations(context)` 直接出现在 `DeveloperMetricRow` 的实参里）、`:773-776`；`:538`（`smartWrapDebugText(value)`）、`:583-612`；`:275-289`（`configuredPinCount` → `CertificatePinPolicy.parse`）
- 现状：
  ```kotlin
  DeveloperMetricRow(
      R.string.developer_battery_optimization,
      if (isIgnoringBatteryOptimizations(context)) { ... } else { ... },   // 每次重组都是一次 binder 调用
  )
  ```
  `smartWrapDebugText` 在每个 `DeveloperMetricRow` 的 `Text` 实参里被调用（`:538`），对超过 36 字符的值做 6 次 `String.replace` + `lineSequence` + `chunked`，全部在组合期、无缓存。
- 触发场景：`DeveloperDebugScreen` 读了 `uiState.nowMillis`（`:99`、`:110`、`:215`），而 `nowMillis` 每秒一跳（`ProjectLumenRuntimeFeatureEntry.startClock`，`delay(1_000)`），因此整屏每秒至少重组一次；`developer_section_system` 默认展开（`SettingsSection` 的 `initiallyExpanded` 默认 true），所以电池优化白名单查询每秒都会跑。面板上有约 40 个 `DeveloperMetricRow`，其中 URL / 错误信息 / API trace 行都超过 36 字符。
- 影响：开发者面板停留时持续的主线程开销与 GC 压力；这是"开发者自己用的页面"，影响面小，但同一个反模式（组合期系统查询）已经在 G07-03 里造成了真实卡顿，值得一并纠正。
- 修复方案：
  1. `:203` 改为 `val ignoringBatteryOptimizations = remember(permissionRequirements) { isIgnoringBatteryOptimizations(context) }`（复用已有的 ON_RESUME 刷新节奏；`permissionRequirements` 在采纳 G07-03 后会成为每个前台周期稳定的实例，正好作为 key），再把结果传给 `DeveloperMetricRow`。
  2. `smartWrapDebugText` 的调用移到 `DeveloperMetricRow` 内部的 `remember(value) { smartWrapDebugText(value) }`。
  3. `:275-289` 的两次 `configuredPinCount(...)` 包一层 `remember { }`（`ProjectLumenApiConfig` 的 pin 字符串是编译期常量，可以无 key）。
- 风险/注意：第 1 条依赖 G07-03 先落地（否则 `permissionRequirements` 每帧都是新实例，`remember` 的 key 等于没加）；若两条不同批修，先给它一个独立的 ON_RESUME token 作 key。

### [G07-13] Shizuku 状态文案与系统守卫文案在本组内各有两份完全重复的实现
- 严重度：P2
- 类别：A 架构与设计（重复实现）
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenDeveloperShizukuNetworkControls.kt:219-227`（`developerShizukuStatusLabel`）对照 `app/src/main/java/com/projectlumen/app/app/ProjectLumenShizukuSettingsSection.kt:581-589`（`shizukuStatusLabel`）——两个函数体逐字相同；`ProjectLumenDeveloperDebugScreen.kt:745-771`（`developerShizukuSystemGuardLabel`）对照 `ProjectLumenShizukuSettingsSection.kt:548-579`（`shizukuSystemGuardLabel` + `shizukuSystemGuardReasons`）——6 个守卫条件与阈值 `state.thermalStatus >= 2` 全部重复。
- 触发场景：新增一个 Shizuku 守卫开关（例如"充电中不采样"）时，只改一处就会让另一处静默漏报。`thermalStatus >= 2` 这个魔法阈值出现两次，调阈值同理。
- 影响：设置页的"系统守卫"状态与开发者面板的同名行可能给出不同结论，排障时误导人。
- 修复方案：把 `shizukuStatusLabel` 与 `shizukuSystemGuardReasons`/`shizukuSystemGuardLabel` 从 `ProjectLumenShizukuSettingsSection.kt` 提到一个共享文件（例如新建 `ProjectLumenShizukuStatusLabels.kt`，或放进已有的 `ProjectLumenSettingsPrivacyModel.kt` 之外的 UI 文案层），改为 `internal`；删掉 `ProjectLumenDeveloperShizukuNetworkControls.kt:219-227` 与 `ProjectLumenDeveloperDebugScreen.kt:745-771`，调用点改用共享实现。
- 风险/注意：`developerShizukuStatusLabel` 是 `internal` 且被 `ProjectLumenDeveloperDebugScreen.kt:218` 与 `ProjectLumenDeveloperShizukuNetworkControls.kt:58` 两处调用，删除时两个调用点都要改名；`shizukuStatusLabel` 目前是 `private`，提取时改 `internal` 会进入模块命名空间，需确认没有同名符号（已核对：`app/` 包内无其他 `shizukuStatusLabel`）。

### [G07-14] 网络管控记录卡固定 `take(12)`，同时生效的限制超过 12 条时多出来的在 UI 上无法恢复；限制按钮也没有二次确认
- 严重度：P2
- 类别：E 韧性
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenDeveloperShizukuNetworkControls.kt:88-91`、`:275-276`（`MAX_NETWORK_RECORD_CARDS = 12`）、`:140-150`（无确认的限制按钮）
- 现状：
  ```kotlin
  records.take(MAX_NETWORK_RECORD_CARDS).forEach { record -> DeveloperNetworkControlRecordCard(...) }
  ```
  搜索框（`:77-84`）只过滤 `networkApps`，对 `records` 完全不生效；"恢复网络"按钮只存在于被渲染出来的记录卡上，而 `ProjectLumenViewModel.restoreAppNetwork` 是全应用**唯一**的恢复入口（已核对：`core/shizuku/ShizukuCapabilityManager.restoreAppNetwork` 只有这一个调用者，没有任何 Worker 或启动时自动恢复）。
- 触发场景：用户在开发者面板里限制了 13 个及以上应用的网络。`AppNetworkControlsDao.observeAll()` 的排序是 `(networkRestricted OR delegatedGuardApplied) DESC, updatedAt DESC, packageName ASC`，所以生效中的记录排在前面——这缓解了大部分情况，但一旦生效条数本身超过 12，第 13 条起就没有卡片、也没有恢复按钮。
- 影响：被限制的应用永久失去网络，用户在应用内无法解除（只能靠 Shizuku/adb 手动 `cmd netpolicy` 还原，或找到该记录被别的记录挤出前 12 位之前的时机）。`:59-66` 的计数行会显示"生效 15"，与只有 12 张卡形成可见矛盾但无补救。另外限制按钮是全宽 error 色、点一下立即通过提权 shell 生效，误触成本高。
- 修复方案：
  1. 让搜索框同时过滤 `records`（`normalizedQuery` 对 `record.packageName` / `record.uid` 做同样匹配），这样超出 12 条时用户至少能搜到目标。
  2. 生效中的记录不截断：`records.filter { it.hasActiveNetworkRestriction } + records.filterNot { it.hasActiveNetworkRestriction }.take(MAX_NETWORK_RECORD_CARDS)`，即只对"已恢复的历史记录"限量。
  3. 截断时显示"仅展示前 N / 共 M 条"的提示，不要静默丢弃。
  4. `DeveloperNetworkAppCard` 的限制按钮加一个 `AlertDialog` 二次确认，系统应用（`app.appType == ShizukuNetworkAppTypes.SYSTEM`）额外提示风险。
- 风险/注意：第 2 条会让生效记录数很大时一次组合出很多卡片，而该区域在 `LumenPage` 的 `Column + verticalScroll` 里（非 Lazy），需要给"已恢复历史"保留上限以免无界增长；不要顺手把整段改成 `LazyColumn`——嵌套在 `verticalScroll` 里的 `LazyColumn` 会因无限高度约束崩溃。

### [G07-15] `restrictApp` 的"已限制"守卫在提权 shell 调用之前，双击可并发下发两次限制
- 严重度：P2
- 类别：B 并发与线程安全
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenAppNetworkControlFeatureEntry.kt:28-36`
- 现状：
  ```kotlin
  fun restrictApp(app: ShizukuNetworkApp) {
      scope.launch {
          if (repository.get(app.packageName)?.hasActiveNetworkRestriction == true) return@launch
          val nowMillis = System.currentTimeMillis()
          val result = shizuku.restrictAppNetwork(app)      // 秒级的 shell 调用
          repository.upsert(result.toRestrictedEntity(nowMillis))
          refreshAppCache()
      }
  }
  ```
  幂等守卫读的是"调用前"的库状态，而记录只在 shell 返回后才写入；`scope.launch` 之间没有 per-package 的互斥。UI 侧的 `enabled = record?.hasActiveNetworkRestriction != true`（`ProjectLumenDeveloperShizukuNetworkControls.kt:142`）也只能在记录写回后才变灰。
- 触发场景：在记录写回前（提权 shell 往返 + `listNetworkControllableApps()` 刷新，实测量级在数百毫秒到数秒）连点两次同一张卡的"限制网络"。两个协程都通过守卫，串行/并行下发两次 `restrictAppNetwork`，两次 `upsert` 后者覆盖前者。
- 影响：同一个应用被下发两次限制命令（若底层命令不幂等，可能留下重复的 uid policy 规则）；`nowMillis` 与 `lastError` 取自后完成的那一次，前一次的错误信息被吞。
- 修复方案：在 `ProjectLumenAppNetworkControlFeatureEntry` 内加一个 `private val inFlight = mutableSetOf<String>()` + `private val mutex = Mutex()`（或直接 `Map<String, Mutex>`），`restrictApp` / `restoreApp` 进入时以 `packageName` 为键抢占，抢不到直接 `return@launch`，`finally` 里释放；顺便把 `nowMillis` 挪到 shell 调用**之后**取，让 `restrictedAt` 反映真正生效的时刻。
- 风险/注意：`restoreApp`（`:38-53`）有同样的结构，同一把锁要覆盖两者，否则"限制中途点恢复"会互相穿越。`_networkApps` 的 `refreshAppCache()` 在并发下也会互相覆盖，但它是纯缓存刷新，最后一次赢即可，无需额外处理。

### [G07-16] 7 组 `NumberSlider` 的 `steps` 算错，导致 20% / 25% / 20 秒这类整数档位选不到
- 严重度：P2
- 类别：D 生命周期与框架约束（框架契约用错）
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenSettingsScreen.kt:1157`、`:1160`、`:1163`、`:1166`、`:1169`（5 个音量滑杆）、`:1109`（`overlay_rest_duration`）、`:1112`（`overlay_strict_distance`）
- 现状：Compose `Slider` 的 `steps` 语义是"起止之间的**中间**档位数"，可选档位共 `steps + 2` 个、间距 `(end - start) / (steps + 1)`。而 `NumberSlider` 提交的是 `sliderValue.roundToInt()`（`ProjectLumenFormControls.kt:298`）。
  - 音量：`0f..100f, steps = 20` → 间距 `100/21 ≈ 4.762`，实际可提交值是 `0,5,10,14,19,24,29,33,38,43,48,52,57,62,67,71,76,81,86,90,95,100`。想要 5% 粒度应当是 `steps = 19`。
  - `overlay_rest_duration`：`5f..120f, steps = 23` → 间距 4.79，可选值 `5,10,15,19,24,29,...`；5 秒粒度应为 `steps = 22`（同文件 `:737` 的 `warn_interval` 用 `5f..120f, steps = 22` 就是对的）。
  - `overlay_strict_distance`：`120f..250f, steps = 26` → 应为 `25`。
- 触发场景：用户想把提示音量设成 20%、或把悬浮窗休息时长设成 20 秒——滑杆会跳过这些值，标签显示 19% / 19 秒。
- 影响：用户可见的"选不中整数"与"标签显示 33%、71% 这种奇怪数字"；备份/云同步里也会存下这些非整档值。
- 修复方案：把上述 7 处的 `steps` 分别改为 `19`、`19`、`19`、`19`、`19`、`22`、`25`。核对公式：想要粒度 `g` 时 `steps = (end - start) / g - 1`。
- 风险/注意：只改 `steps` 不影响已存储的值（`sliderValue` 初值用 `value.toFloat().coerceIn(range)`，非档位值也能正常显示），但用户下一次拖动会被吸附到最近档位。同文件另有 4 处 `steps` 略偏（`:804` `1f..16f, steps=15` 应为 14；`:1058`、`:1080`、`:1085` 的 `1f..100f, steps=99` 应为 98），因为间距小于 1、四舍五入后每个整数仍可达，**不构成用户可见缺陷，不必改**——列在此处仅为避免修复时误判为漏改。

### [G07-17] `ProjectLumenSettingsFeatureEntry` 注入了从未使用的 `NotificationService`
- 严重度：P2
- 类别：H 编译与结构
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenSettingsFeatureEntry.kt:22`（`private val notifications: NotificationService`）；构造点 `ProjectLumenViewModel.kt:130`
- 现状：全文件内 `notifications` 只出现在构造参数声明这一处（已核对：`rg "notifications" ProjectLumenSettingsFeatureEntry.kt` 只命中第 22 行）。通知相关操作实际都走 `runtimeEntry.refreshActiveNotifications(...)`（`:99`、`:106`）。
- 触发场景：kotlinc 会给出 "Property is never used" 警告（CI 用 `--warning-mode all` 但不会因此失败）；真实代价是读代码的人会以为这个类直接操作通知，从而在这里而不是 `ProjectLumenRuntimeFeatureEntry` 里加通知逻辑。
- 影响：可维护性 + 一个多余的构造依赖，妨碍将来给这个类写不带 `NotificationService` 的单元测试。
- 修复方案：删掉 `ProjectLumenSettingsFeatureEntry.kt:22` 的参数，同步删掉 `ProjectLumenViewModel.kt:130` 的实参 `notifications = notifications,`。
- 风险/注意：`ProjectLumenViewModel` 里的 `notifications` 变量还被其他 FeatureEntry 使用，只能删这一处实参，不要连带删掉它的声明。

### [G07-18] 委托网络守卫的状态靠错误字符串前缀匹配推断，无关错误会被误报成"ROM 不支持"
- 严重度：P2
- 类别：A 架构与设计
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenAppNetworkControlState.kt:18-26`
- 现状：
  ```kotlin
  delegatedGuardApplied -> ACTIVE
  !delegatedGuardAttempted -> NOT_ATTEMPTED
  lastError.isBlank() ||
      (!lastError.contains(SHIZUKU_DELEGATED_GUARD_ERROR_PREFIX) && lastError.contains(SHIZUKU_UID_POLICY_ERROR_PREFIX)) -> CLEARED
  else -> UNSUPPORTED
  ```
  唯一区分 CLEARED / UNSUPPORTED 的依据是 `lastError` 里是否含有两个字符串前缀常量。
- 触发场景：提权 shell 因为与这两个前缀都无关的原因失败（Shizuku 服务中途断开、`cmd` 超时、ROM 返回其它 stderr），`lastError` 非空且两个前缀都不含 → 落到 `else` 分支，UI 显示"委托守卫不受支持"。
- 影响：开发者面板对同一个失败给出错误结论（"这台机器不支持"而不是"这次调用失败了"），排障时会往完全错误的方向查。
- 修复方案：状态应由 `ShizukuNetworkPolicyResult` 显式携带，而不是从错误文本反推。在 `core/shizuku` 侧给结果类加一个枚举字段（例如 `delegatedGuardOutcome: APPLIED / CLEARED / UNSUPPORTED / FAILED / NOT_ATTEMPTED`），`AppNetworkControlEntity` 增列持久化，`delegatedNetworkGuardDisplayStatus` 直接映射；过渡期至少给 `else` 分支再拆一个 `FAILED`（含未知错误）并新增对应文案，不要把未知错误归入 UNSUPPORTED。
- 风险/注意：涉及 `core/shizuku` 与 Room 加列（需要 `AppDatabase` 迁移 + 导出 schema），属跨组改动；若本轮只想小修，就只做"新增 FAILED 分支 + 新增字符串资源"，`ProjectLumenDeveloperShizukuNetworkControls.kt:262-271` 的 `when` 需同步补分支（否则 `when` 不穷尽会编译失败）。

### [G07-19] 滚动锚点在 layout 阶段每帧向 `mutableStateMapOf` 写入，无变更去重
- 严重度：P2
- 类别：E 韧性（性能）
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenSettingsAnchors.kt:44-58`
- 现状：
  ```kotlin
  .onGloballyPositioned { coordinates ->
      val position = (activeScrollState.value + coordinates.positionInRoot().y.roundToInt() - topOffsetPx).coerceAtLeast(0)
      targets.forEach { target -> anchorPositions[target] = position }
  }
  ```
  写入无条件执行，`anchorPositions` 是 `SnapshotStateMap`（`ProjectLumenSettingsScreen.kt:265-266`）。
- 触发场景：`onGloballyPositioned` 在每次布局后回调。设置页滚动、`AnimatedVisibility` 展开/收起、`animateContentSize` 动画期间都是每帧布局；设置页一共注册了 13 个权限锚点 + 3 个成长锚点，于是动画/滚动期间每帧产生十余次 snapshot map 写入（每次写入都要构造新的持久化 map 节点并登记快照记录）。
- 影响：滚动与展开动画期间的额外分配与 GC 压力，叠加在 G07-03 的每帧 IPC 之上。目前不会造成功能错误（这些 key 只在点击回调里被读，不在 composition/layout 阶段读，所以不存在"布局中写入被读取的状态"导致的无限布局循环）。
- 修复方案：加一行去重即可：
  ```kotlin
  targets.forEach { target -> if (anchorPositions[target] != position) anchorPositions[target] = position }
  ```
  `topOffsetPx` 也可以从每次重组计算改成 `remember(LocalDensity.current) { ... }`。
- 风险/注意：不要把 `anchorPositions` 换成普通 `MutableMap` —— 虽然当前没有 composition 期读取，但 `mutableStateMapOf` 保证了跨重组的可见性；改成普通 map 后若将来有人在 composition 里读它就会静默拿到过期值。

## 已核查但无问题的点

- **`remember` / `derivedStateOf` 的 key 完整性（重点核查项）**：`ProjectLumenSettingsScreen.kt:245` 的 `remember(uiState.templates, settings.activeTipTemplateId) { activeTemplate(uiState) }` 与 `:573-579` 的 `remember(settings.useDynamicColors, uiState.templates, settings.activeTipTemplateId) { templateAppearanceLocksThemeMode(uiState) }` **key 都是完整的**——逐行读了被调函数实现（`ProjectLumenUiFormatters.kt:314-316`、`:323-326`）确认它们只读 `uiState.templates`、`uiState.settings.activeTipTemplateId`、`uiState.settings.useDynamicColors` 以及活动模板的 `layoutJson`（后者随 `templates` 列表实例一起变）。`:1196-1198` 的 `remember(uiState.templates, proEnabled)` 同样完整。修复阶段不要"顺手"给这三处加 key。
- **成长引导的 `LaunchedEffect` key（`:521-540`）是完整的**：逐字比对了 `isFamilyEyeCareModeActive`（`ProjectLumenEyeCareInsights.kt:1135-1147`）读到的 10 个字段与 key 列表，全部覆盖；REPORTS / CLOUD / GUIDANCE 三个分支读到的 `statsEnabled` / `remoteState.signedIn` / `reminderEnabled` 也都在列。只有权限那一个效应漏了一项（见 G07-06），不要连带改动这一个。
- **分层没有被击穿**：14 个文件里没有任何一处 Composable 直读 DAO 或 Repository。唯一持有 Repository 的是 `ProjectLumenAppNetworkControlFeatureEntry`（非 Composable 的控制器类，与仓库既有 `*FeatureEntry` 约定一致）。所有设置写入都经 `viewModel.updateSettings` / 语义化的 `setXxxEnabled`。
- **权限判定的 SDK 版本分支正确**：`needsNotificationPermission` 用 `SDK_INT < TIRAMISU` 提前返回、`needsExactAlarmSettings` 用 `< S`、`needsFullScreenIntentSettings` 用 `< UPSIDE_DOWN_CAKE`（`ProjectLumenUiFormatters.kt:410-439`，别组文件），`minSdk 29` 下不会触碰高版本 API；`openExactAlarmSettings` / `openFullScreenIntentSettings` 也各自带同样的版本守卫。
- **`ProjectLumenSystemSettingsIntents.kt` 的 Intent 兜底是正确且完整的**：`startFirstAvailableSettingsActivity`（`:35-43`）对每个候选 Intent 先 `resolveActivity` 再 `runCatching { startActivity }`，全部失败才 Toast，不会抛 `ActivityNotFoundException`；使用情况访问和电池用量各准备了 3–4 个候选 action（含 `ACTION_APPLICATION_DETAILS_SETTINGS` 终兜底），vivo / 小米缺失某个 action 时能退化。`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 权限已在 `AndroidManifest.xml:26` 声明。**唯一的小瑕疵**（不单独立条）：`ProjectLumenDeveloperDebugScreen.kt:779-787` 的电池设置兜底链最后一环是 `openAppNotificationSettings(context)`，跳到的是通知设置而非应用详情页，属于跳错页面而非崩溃，建议在修 G07-01 时顺手换成新增的 `openAppDetailsSettings`。
- **开发者面板的发布风险可接受**：入口是 About 页版本号连点 7 次（`ProjectLumenAboutAndDialogs.kt:218-245`）后写 `developerModeEnabled`，属行业惯例的隐藏门禁；面板里**没有**修改后端地址的能力（`ProjectLumenDeveloperDebugScreen.kt:265` 与 `ProjectLumenBackendConnectivityDeveloperControls.kt` 全为只读展示，唯一可写的是 `developerForceEnabled` 开关，且旁边有 `backend_connectivity_force_enable_warning` 提示）；API 诊断卡展示的 `requestBodyPreview` / `responseBodyPreview` 已在 `core/api/ProjectLumenApiDiagnostics.kt:78-140` 做过 JSON 键名脱敏（`[redacted]`）与长度截断，`authorizationAttached` / `signed` 只暴露布尔值；`secureCredentialStatus`（`:629-641`）只显示"有/无 token"且被 `remember(context)` 缓存，不打印 token 本身。面板里没有发现任何硬编码凭据、设备指纹或 PII 输出。
- **`developerModeEnabled` 可被备份文件写入**（`core/services/DataBackupService.kt:251`、`:474`），即导入一份 `developerModeEnabled: true` 的备份等价于连点 7 次。因为它只解锁一个诊断面板、且面板内所有危险操作都另需 Shizuku 授权，判定为可接受，不单独立条；若将来面板新增真正的特权操作，需要重新评估。
- **Shizuku 危险操作的授权链是正确的**：`ShizukuAdvancedSettingsSection` 的每个开启动作都紧跟 `viewModel.requestShizukuAuthorization()`，三个快捷预设（`:496-536`）也都如此；`ShizukuNetworkControlsSection` 的刷新/限制按钮以 `enabled = shizukuState.ready` 门禁，未授权时只显示"授权"按钮。
- **`DisposableEffect` 全部成对清理**：`ProjectLumenSettingsScreen.kt:707-715`（Clash 监听器 add/remove）、`:1120-1128`（Aura 安装状态的 ON_RESUME 观察者）、`ProjectLumenPermissionState.kt:32-40`（权限刷新观察者）三处都在 `onDispose` 里精确移除，且 key 用的是 `context` / `lifecycleOwner` 而非常量。Aura 安装检测走 ON_RESUME 而不是每帧查 `PackageManager`，是正确做法。
- **`rememberSaveable` 的使用是正确的**：`activeGrowthConfigTarget` / `activePermissionSetupTarget` 存的是 Kotlin 枚举（实现 `Serializable`，在 `autoSaver` 的 `AcceptableClasses` 白名单内），`growthReturnScrollPosition` / `permissionReturnScrollPosition` 是 Int，`networkAppQuery` / `purchaseProductId` / `purchaseToken` 是 String，均可正常存取。唯一漏用的是 `pendingBackupImportUri`（见 G07-04）。
- **长列表用 `Column + verticalScroll` 是有意为之，不应改成 `LazyColumn`**：`LumenPage`（`ProjectLumenMetricsAndLayout.kt:340-380`）提供 `ScrollState`，而整套权限/成长引导依赖 `scrollState.animateScrollTo(像素位置)` + `onGloballyPositioned` 的绝对坐标（`ProjectLumenSettingsAnchors.kt`），换成 `LazyColumn` 就只能用 `animateScrollToItem`，锚点机制要整体重写。同时 `ProjectLumenSettingsScreen.kt` 的 16 处 `SettingsSection` 里有 9 处显式 `initiallyExpanded = false`（隐私与权限中心那一节在 `ProjectLumenSettingsPrivacyCenter.kt:67` 也默认折叠），`SettingsSection` 折叠时内容在 `AnimatedVisibility` 之外**不参与组合**（`titleRes` 重载见 `ProjectLumenSharedComponents.kt:614-623`），首屏实际组合的子项有限。因此不把它列为性能缺陷；真正的每帧开销来自 G07-03 / G07-12 / G07-19。
- **花括号与圆括号平衡**：14 个文件逐个统计 `{`/`}` 与 `(`/`)` 全部配对，且四个大文件与 `git show HEAD:<path>` 的计数完全一致（工作树干净，无未提交改动）。`when` 分支全部穷尽：`setPermissionTargetEnabled`（`:388-429`，13 个 target 全覆盖，`EXACT_ALARM`/`FULL_SCREEN`/`USAGE_ACCESS` 显式合并为 `Unit`）、`isPermissionTargetConfigured`、`networkGuardStatusLabel`（4 个 `DelegatedNetworkGuardDisplayStatus`）、`PrivacyPermissionTone` 的 4 处 `when`、`backendHealthStatusLabel`（4 个 `BackendHealthStatus`）均无 `else` 兜底且完整——这是好事，加枚举值时编译器会强制提醒，修复阶段不要给它们加 `else`。
- **`NumberSlider` / `SwitchRow` 的调用点签名全部匹配**：核对了 `ProjectLumenFormControls.kt:255-263` 的 8 个形参与本组全部 30 余处位置实参（`labelRes, icon, value, range, steps, valueLabel, [labelMaxLines], onValueChange` 尾随 lambda），无错位。






