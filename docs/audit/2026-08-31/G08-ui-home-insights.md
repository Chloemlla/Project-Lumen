# G08 UI 层——主屏 / 护眼洞察 / 统计卡片 / 提示模板 / 远程与运行时入口 审查报告

- 审查文件数：19，总行数：5858（`wc -l` 实测）
- 结论摘要：这一组的**状态流方向是干净的**——所有 Composable 都通过 `uiState` + lambda 回调工作，没有一处 UI 直读 DAO/Repository/系统服务；并且 brief 预判的头号风险「`remember` key 不完整」在本组 **20 处 `remember` 里一处都不存在**（逐个核对了被调函数实际读的字段，见文末）。真正的问题集中在三处：（1）**模板编辑器把 Room 的 `updatedAt` 当作 `remember` key**，导致每敲一个字符就被数据库回写值重置，中文输入法下会吞字；（2）**云同步把对端整份 settings 直接覆盖本地**且无冲突解决，多设备用户会静默丢配置，同时"立即同步"按钮没有 busy 门禁、`launchRemote` 没有重入保护，连点即并发全量导入/推送；（3）**`startClock` 是一个无退出条件的 1Hz 循环**，每秒一次 Room + DataStore 读，并且因为 `nowMillis` 被塞进 `ProjectLumenUiState`，整棵 UI 每秒重组一次——即使没有任何计时器在跑。另有洞察聚合逻辑被复制了三份且阈值已经漂移（同一页两张卡对同一数据给出矛盾结论）、两张卡片是从未被组合的死代码、以及 5 个文件各带约 150 行复制粘贴的无用 import。

## 缺陷清单

### [G08-01] 模板编辑器把 `template.updatedAt` 当 `remember` key，每敲一字就被数据库回写值覆盖（输入法吞字）
- 严重度：P1
- 类别：D 生命周期与框架约束（Compose 状态）
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenTemplateScreens.kt:317-318`（key），`:331-334`、`:341-344`、`:347-349`（每次 `onValueChange` 都落库）；写入实现 `app/src/main/java/com/projectlumen/app/app/ProjectLumenTemplatesFeatureEntry.kt:52-68`
- 现状：
  ```kotlin
  var titleText by remember(template.id, template.updatedAt) { mutableStateOf(template.titleText) }
  ...
  onValueChange = {
      titleText = it
      viewModel.updateTemplateContent(template, it, subtitleText, template.showSkipButton)
  }
  ```
  `updateTemplateContent` 会 `upsert(template.copy(titleText = ..., updatedAt = System.currentTimeMillis()))`。Room 的 Flow 因此重新发射一个 `updatedAt` 不同的实体 → `remember` 的 key 变了 → 本地 `titleText` 被**重新初始化为数据库里那一份**。
- 触发场景：连续快速输入（尤其中文输入法的组合输入）。时序：敲 "a"（本地=a，异步写 A）→ 敲 "b"（本地=ab，异步写 B）→ 写 A 的 Flow 发射到达（`titleText` 被重置为 "a"，光标跳到末尾、"b" 丢失）→ 写 B 到达再变回 "ab"。输入越快、Room/DataStore 越忙，回退窗口越长。
- 影响：编辑休息提示标题/副标题时字符被吞、光标跳动、中文候选词被打断；另外每个按键都产生一次 Room 写入（一段 20 字的副标题 = 20 次 upsert + 20 次全量 Flow 重发 → 全 UI 重组）。
- 修复方案：`ProjectLumenTemplateScreens.kt:317-318` 的 key 去掉 `template.updatedAt`，只保留 `template.id`（切换模板才重置）：`remember(template.id) { mutableStateOf(template.titleText) }`。落库改为不在每次 `onValueChange` 触发：在 `TemplateEditor` 内用 `LaunchedEffect(titleText, subtitleText) { delay(400); viewModel.updateTemplateContent(...) }` 做去抖，或改为失焦/离开页面时提交一次。`showSkipButton` 那一路（`:347-349`）保持立即提交即可（开关无输入法问题）。
- 风险/注意：去掉 `updatedAt` 后，若同一模板被**云配置同步**改写（`ProjectLumenRemoteFeatureEntry.kt:390-431` 会覆盖 `titleText`/`subtitleText`），编辑器不会跟随刷新。这是可接受的（正在编辑时被远端覆盖本来就不该抢走焦点），但如果要保留刷新能力，应改为「远端值变化且当前输入框未获焦点」才同步，不要退回用 `updatedAt` 做 key。

### [G08-02] 云同步把对端整份 settings 无条件覆盖本地，多设备用户静默丢配置
- 严重度：P1
- 类别：F 持久化一致性
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenRemoteFeatureEntry.kt:433-446`（`applyRemoteSyncChanges`）、`:448-467`（`syncChangesToBackupJson`）、`:492-516`（`backupJsonToSyncChanges`）、`:158-185`（`syncNow`）；落地实现 `core/services/DataBackupService.kt:129-...`（`importSettings`，属别组文件，仅作证据引用）
- 现状：
  ```kotlin
  // syncChangesToBackupJson
  "settings" -> backupJson.put("settings", change.payload)
  // applyRemoteSyncChanges
  backup.importBackupJson(backupJson)      // → importSettings(json.optJSONObject("settings"))
  ```
  `importSettings` 对 JSON 里出现的**每个字段**都执行 `json.optString(k, current.k)` 覆盖，没有任何 `updatedAt` 比较或字段级合并。而推送侧 `backupJsonToSyncChanges` 每次都把 `exportBackupJson()` 的**全量 settings 快照**当作一条 `operation="UPSERT"` 推上去，`remoteId` 硬编码为 `"local-settings"`（不含设备标识）。
- 触发场景：同一账号在两台设备上都点过"立即同步"。A 设备把自己的全量 settings 推上去；B 设备下一次 `syncNow` 先 `fetchSyncChanges` 拉到这条（`deviceInstallationId != 自己`，通过过滤）→ 整份覆盖 B 的本地设置 → 然后 B 又把覆盖后的结果推回去。B 自己刚调的提醒间隔、免打扰时段、音量、主题全部消失，且没有任何提示。
- 影响：多设备用户的本地设置被静默清掉（用户视角就是"设置自己变回去了"）。其中 `activeTipTemplateId` 也在导入范围内（`DataBackupService.kt:173`），而云配置模板在两台设备上是各自 Room 自增 id，跨设备 id 不对应，所以同步后休息页的模板可能静默换成另一个或回落到第一个。
- 修复方案：在 `ProjectLumenRemoteFeatureEntry.applyRemoteSyncChanges`（`:433`）里对 `settings` / `dailyGoal` 这两个"单例文档"类集合单独处理，不要走 `importBackupJson` 的全量覆盖：改为比较 `change.updatedAt` 与本地 `settings.updatedAt`，仅当远端更新且更晚才应用；或者更稳的做法是**默认不同步 settings**，只同步统计/模板/权益这些可追加合并的集合（把 `"settings"`、`"dailyGoal"` 从 `backupJsonToSyncChanges`（`:495-504`）的 collections 列表里去掉），把"跨设备同步设置"降级为显式的"上传/恢复云备份"（`uploadCloudBackup`/`restoreLatestCloudBackup` 已经是用户显式操作，语义清楚）。同时给 `remoteId` 加上设备维度（`"local-$collection-$deviceId"`），避免多设备在服务端互相踩同一行。
- 风险/注意：改动会改变服务端已有数据的语义（原来所有设备共用 `local-settings` 这一个 remoteId）。若服务端按 remoteId 做唯一约束，改 remoteId 会产生新行、旧行残留；需要与后端确认清理策略。另外 `syncNow` 的"拉→推→再拉"三段（`:167-179`）在去掉 settings 后仍然可用，不必改。

### [G08-03] 成长能力卡的"立即同步"没有 busy 门禁，`launchRemote` 也无重入保护：连点即并发全量同步
- 严重度：P1
- 类别：B 并发与线程安全
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenEyeCareInsights.kt:622-633`（按钮）；`app/src/main/java/com/projectlumen/app/app/ProjectLumenRemoteFeatureEntry.kt:259-284`（`launchRemote`）、`:251-257`（`signOut`）；对照正确写法 `app/src/main/java/com/projectlumen/app/app/ProjectLumenRemoteCloudCard.kt:187-195`（那里每个按钮都是 `enabled = !state.busy && cloudSyncAllowed`）
- 现状：
  ```kotlin
  // EyeCareGrowthCapabilityCard —— remoteState 已经传进来了，却没用它做 enabled
  OutlinedButton(onClick = if (cloudSyncReady) onSyncCloud else onConfigureCloud) { ... }
  // launchRemote：直接 scope.launch，不检查 _state.value.busy
  private fun launchRemote(...) { scope.launch { _state.value = _state.value.copy(busy = true, ...) ... } }
  ```
  `onSyncCloud = viewModel::syncRemoteNow`（`ProjectLumenSettingsScreen.kt:610`）→ `remoteEntry.syncNow()`。
- 触发场景：在「设置 → 护眼成长能力」里连点两次"立即同步"（该卡片既不禁用按钮也**不显示任何进度**，用户看不到第一次已经在跑，连点是自然行为）。两个 `syncNow` 协程并发：各自 `fetchSyncChanges` → `backup.importBackupJson`（并发写 Room 的 settings/统计/模板）→ `pushSyncChanges` 推两份全量 → 各自 `credentials.saveRemoteSyncCursor(nextCursor)` 互相覆盖。同理 `signOut`（`:251`）不取消在飞的请求：签出后仍在跑的 `refreshAccountWithAccessToken`（`:149-155`）会把 `signedInEmail`/`sessionAvailable=true` 写回去。
- 影响：重复推送（服务端多出一份重复变更）；游标回退导致下次同步把已应用过的远端变更再导入一遍；并发导入时 `_state.value = _state.value.copy(...)` 是无锁读改写，先失败的那个协程写入的 `errorMessage` 会被后成功的那个清空——**错误提示静默消失**。签出后 UI 仍显示"已登录"，但凭据已清，后续任何操作都报 "Sign in before using cloud features."
- 修复方案：两处都改。①`ProjectLumenEyeCareInsights.kt:622-633`：`OutlinedButton(enabled = !remoteState.busy, onClick = ...)`，并在 `cloudCapabilityVisible` 分支里加一行 `if (remoteState.busy) StatusLine(Icons.Outlined.Sync, ...)` 之类的进度提示。②`ProjectLumenRemoteFeatureEntry.launchRemote`（`:259`）开头加闸：`if (_state.value.busy) return`，并把所有 `_state.value = _state.value.copy(...)`（`:70`、`:86`、`:109`、`:149`、`:181`、`:197`、`:212`、`:244`、`:265`、`:271`、`:277`、`:291`、`:296`、`:304`）统一换成 `_state.update { it.copy(...) }`。③`signOut`（`:251`）改为先 `currentJob?.cancel()`（把 `launchRemote` 返回的 Job 存成字段）再清凭据。
- 风险/注意：`verifyEmailLogin`（`:98`）里 `val current = _state.value` 是在挂起点**之前**捕获、挂起点之后才 `current.copy(...)` 写回，改成 `update {}` 时要顺手修掉这个陈旧快照写回（否则 busy 闸门加上后仍有一处会丢中途更新）。加 `if (busy) return` 后，UI 上原本"连点两次会同步两次"的行为变成静默忽略第二次，最好配合 ② 的进度提示一起上，否则用户会以为按钮没反应。

### [G08-04] `startClock` 是无退出条件的 1Hz 循环：空闲时也每秒一次 Room+DataStore 读，并让整棵 UI 每秒重组
- 严重度：P1
- 类别：E 韧性 / A 架构
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenRuntimeFeatureEntry.kt:35-44`（`startClock`）、`:201-210`（`advanceDuePhases`）；启动点 `ProjectLumenViewModel.kt:242`；放大器 `ProjectLumenStateStore.kt:91-96`（`combine(dataState, now, crashReport)`）
- 现状：
  ```kotlin
  fun startClock(now: MutableStateFlow<Long>) {
      scope.launch {
          while (true) {                    // 没有任何退出/降频条件
              val current = System.currentTimeMillis()
              now.value = current
              advanceDuePhases(current)     // settingsRepository.get() + runtimeRepository.get()
              delay(1_000)
          }
      }
  }
  ```
  `advanceDuePhases` 每次都 `settingsRepository.get()`（一次 Room 查询 **+ 一次 DataStore `read()`**，见 `core/repositories/SettingsRepository.kt:23-27`）和 `runtimeRepository.get()`（MMKV），即使 `state.activeEngine == IDLE` 也要先读完两份再 `return`。
- 触发场景：常态。App 一进来 ViewModel 就启动这个循环，没有任何计时器在跑（`activeEngine=IDLE`）也一样跑；`uiState` 用的是 `SharingStarted.WhileSubscribed(5_000)`，UI 不再收集后 combine 会停，但**这个循环不会停**，Room/DataStore 读继续。
- 影响：①空闲时每小时 3600 次 Room 查询 + 3600 次 DataStore 读，纯耗电耗 CPU（对一个护眼后台类 App 是能被用户在电池统计里看见的量级）；②因为 `nowMillis` 是 `ProjectLumenUiState` 的字段，每秒产出一个新的 `uiState` 实例 → `HomeScreen`/`StatisticsScreen`/`SettingsScreen` 及其所有以 `uiState` 为参数的卡片**每秒全量重组一次**（聚合结果靠 `remember` 保住了不重算，但重组本身照跑；这也是 G08 附注里 `rememberPermissionRequirements` 每秒 7 次 binder 调用的直接放大器）。
- 修复方案：改 `startClock`（`:35`）——把"推进相位"和"给 UI 供时钟"拆开：`advanceDuePhases` 只在 `runtimeRepository.get()?.activeEngine != IDLE` 时才需要 1Hz；空闲时把 `delay` 提到 30s 或改为挂在 `runtimeRepository.observe()` 上（有活跃引擎才进 1Hz 循环，回到 IDLE 就退出循环等下一次 observe 事件）。另外 `advanceDuePhases`（`:201`）里 `settingsRepository.get()` 应改成读一次缓存/`observe()` 的最新值，而不是每秒重新查库+读 DataStore。
- 风险/注意：`nowMillis` 从 `uiState` 里摘出去（改为独立的 `StateFlow<Long>`，只让 `TimerCard`/`StateCard` 订阅）是消除每秒全量重组的根治手段，但那是 `ProjectLumenStateStore`（别组文件）的改动，需要跨组协调，本条只主张改 `startClock` 自己的频率与读放大。降频后要确认 `AlarmReceiver`/`TimerReconciliationWorker` 仍能兜住相位推进（本仓库本来就是"闹钟为准 + 循环对账"的双轨设计，降频不应影响正确性，但需回归一次跨越休息结束时刻的场景）。

### [G08-05] 选图目标 id 用 `remember` 而非 `rememberSaveable`：选图期间旋转/进程重建后所选图片被静默丢弃
- 严重度：P1
- 类别：D 生命周期与框架约束
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenTemplateScreens.kt:209-217`
- 现状：
  ```kotlin
  var imageTargetTemplateId by remember { mutableStateOf<Long?>(null) }
  val templateImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      val targetTemplate = uiState.templates.firstOrNull { it.id == imageTargetTemplateId }
      imageTargetTemplateId = null
      if (uri != null && targetTemplate != null) { ...; viewModel.updateTemplateImage(targetTemplate, uri.toString()) }
  }
  ```
- 触发场景：点"选择模板图片"→ 系统文件选择器（另一个 Activity）在前台期间本 Activity 被重建：横竖屏切换、开发者选项"不保留活动"、或系统在低内存下回收。回来时 `imageTargetTemplateId` 已回到 `null`，`targetTemplate` 解析为 `null`，`uri` 明明拿到了但整个分支被跳过。
- 影响：用户选完图片，界面毫无变化也毫无提示，图片没被应用；重试一次仍可能复现。同时已经通过 `persistReadableUri` 之前的路径被跳过，白拿了一个不会用的 URI。
- 修复方案：`ProjectLumenTemplateScreens.kt:209` 改成 `var imageTargetTemplateId by rememberSaveable { mutableStateOf<Long?>(null) }`（`rememberSaveable` 已在本文件 `:137` import，`Long?` 可直接被 Bundle 保存）。另在回调里补一条兜底：`uri != null && targetTemplate == null` 时给一次 Toast 提示"未能确定目标模板，请重试"，而不是静默返回。
- 风险/注意：无行为兼容风险。注意不要顺手把 `viewModel.updateTemplateImage` 挪到 `LaunchedEffect` 里——`ActivityResultCallback` 已经在主线程且只触发一次，直接调用是对的。

### [G08-06] 同一套 14 天洞察聚合被复制三份，阈值已经漂移：同一页两张卡对同一数据给出矛盾结论
- 严重度：P2
- 类别：A 架构与设计
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenEyeCareInsights.kt:782-900`（`eyeCareInsightSummary`，权威版）、`:693-765`（`applyPersonalizedEyeCareGuidance` 内重算一遍）；`app/src/main/java/com/projectlumen/app/app/ProjectLumenStatisticsCards.kt:257-274`（`HabitSuggestionCard` 又重算一遍）
- 现状：三处各自写了一遍 `take(14)` + `sumOf { completedBreakCount }` / `sumOf { skipCount }` / `skipRate` / `maxOfOrNull { maxContinuousWorkSeconds }` / `sumOf { lowLightWarningCount }` / `sumOf { eyeDryWarningCount }`，然后各自定阈值：
  ```kotlin
  // eyeCareInsightSummary（洞察卡/健康报告卡）
  if (skipRate >= 40) add(R.string.eye_care_reason_skipped_breaks)
  if (lowLightWarnings >= 2) add(R.string.eye_care_reason_low_light)
  // HabitSuggestionCard（习惯建议卡）
  if (skipRate > 50 && completionRate < 40) add(R.string.habit_suggestion_shorter_break)
  if (lowLightWarnings >= 3) add(R.string.habit_suggestion_room_light)
  ```
- 触发场景：统计页同时渲染 `EyeCareHealthReportCard`（`ProjectLumenMainScreens.kt:639`）和 `HabitSuggestionCard`（`:653`）。当 `skipRate=45` 或 `lowLightWarnings=2` 时，上面那张卡说"跳过休息偏多 / 环境光偏暗，建议改善"，下面那张卡同时说"节奏良好，保持当前习惯"（`habit_suggestion_keep_rhythm`，因为它的条件都没命中）。
- 影响：同屏自相矛盾的健康结论，用户无法判断该信哪张卡；后续任何一处调阈值都只改一半。另外 `rememberEyeCareInsightSummary` 在同一屏被独立调用多次（`:94`、`:193`、`:241`、`:384`、`:440`），每个调用点一份独立 `remember` 缓存，同一份 14 天聚合在一屏内算 2-3 遍。
- 修复方案：把 `eyeCareInsightSummary`（`ProjectLumenEyeCareInsights.kt:782-930`，含 `calculateRiskScore`）连同阈值常量下沉到 `core/insights`（该包已存在，`DeviceInsightAnalyzer` 就是同类角色），成为纯函数 + 一组命名常量（`SKIP_RATE_HIGH`、`WARNING_ALERT_COUNT` 等）。然后：①`HabitSuggestionCard`（`ProjectLumenStatisticsCards.kt:256`）改为接收 `EyeCareInsightSummary` 参数，用 `summary.skipRate`/`summary.lowLightWarnings` 和同一批常量判断，删掉自己那段聚合；②`applyPersonalizedEyeCareGuidance`（`ProjectLumenEyeCareInsights.kt:693`）改为 `val summary = eyeCareInsightSummary(uiState, ...)` 后直接用，删掉 `:697-710` 的重算；③在 `StatisticsScreen`/`HomeScreen` 各算一次 summary 往下传，替代 5 个卡片各自 `rememberEyeCareInsightSummary`。
- 风险/注意：统一阈值必然改变现有文案的出现时机（例如 `skipRate=45` 之后习惯卡也会开始提示缩短休息），这是修 bug 的预期行为，但要一次性确认取哪一套阈值为准（建议以 `eyeCareInsightSummary` 的 40/2 为准，习惯卡的 50/3 更宽松）。`applyPersonalizedEyeCareGuidance` 用的 `goalContinuousMinutes.coerceIn(15,120)` 与 summary 里未夹紧的 `uiState.dailyGoal.maxContinuousWorkMinutes` 不同，合并时别把这个夹紧丢掉。

### [G08-07] 配置分公式可以在"还有待办权限"时算到 100%，同一张卡自相矛盾
- 严重度：P2
- 类别：E 韧性（数据展示正确性）
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenEyeCareInsights.kt:849-850`（公式）；展示处 `:209-217`（首页洞察卡把配置分和"还有 N 项待完成"并排放）、`:456-464`（另一张卡的进度条）
- 现状：
  ```kotlin
  val configurationScore = (100 - missingSetupCount * 12 + activeProtectionCount * 3).coerceIn(0, 100)
  ```
  `activeProtectionCount` 最大 9（+27），`missingSetupCount` 每项只扣 12。
- 触发场景：用户点了"应用推荐设置"（9 项防护全开 → +27），但相机权限没给（`missingSetupCount = 1` → -12）：`100 - 12 + 27 = 115` → 夹到 **100**。
- 影响：首页洞察卡上「配置分 100%」和它下面一行「还有 1 项设置待完成」（`eye_care_setup_missing_count`，`:213-218`）同时出现，进度条也满格。用户据此认为配置已完成，不会去给相机权限，近距离/眨眼监测实际全程不工作。
- 修复方案：改 `ProjectLumenEyeCareInsights.kt:849` 的公式，让"有缺失项"与"满分"互斥。最小改法：`val configurationScore = (100 - missingSetupCount * 12 + activeProtectionCount * 3).coerceIn(0, if (missingSetupCount > 0) 99 - missingSetupCount * 4 else 100)`；更清晰的改法是把它拆成两个独立指标——完成度 `activeProtectionCount / 9` 与阻塞项 `missingSetupCount`，不要把加分和扣分混在一个百分数里。
- 风险/注意：`configurationScore` 同时用于 `:456-464` 的 `LinearProgressIndicator(progress = { score / 100f })`（那张卡目前是死代码，见 G08-08），改公式不影响别处；风险评分 `calculateRiskScore` 不读这个值，无连带。

### [G08-08] 权限透明度卡与系统背景选择器从未被组合：整块隐私说明 UI 出厂即不可达
- 严重度：P2
- 类别：A 架构 / H 结构
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenEyeCareInsights.kt:435-511`（`EyeCareSetupAndPrivacyCard`，77 行）；`app/src/main/java/com/projectlumen/app/app/ProjectLumenTemplateScreens.kt:374-403`（`SystemBackgroundPicker`，30 行）
- 现状：全仓库（含 `app/src/test`）搜索这两个 Composable 的调用点，结果只有它们自身的声明——无任何调用方。`EyeCareSetupAndPrivacyCard` 内含 7 行 `PermissionTransparencyLine`（逐条说明通知/精确闹钟/全屏意图/相机/悬浮窗/写设置/Shizuku 各自为什么需要、当前是否满足）和 `R.string.eye_care_privacy_boundary` 隐私边界声明；`PermissionTransparencyLine`（`:1047-1099`）因此也只被这张死卡引用。
- 触发场景：常态——用户在 App 里找不到这块内容。三个入口页（Home/Statistics/Settings）分别组合的是 `EyeCareGuidedSetupCard`、`EyeCareInsightsHomeCard`、`EyeCareHealthReportCard`、`EyeCareActionPlanCard`、`EyeCareGrowthCapabilityCard`，唯独漏了这张。
- 影响：本该面向用户的"每个敏感权限用来做什么"的透明度说明完全不可见——对一个申请相机（人脸/眨眼检测）、悬浮窗、写设置、Shizuku 提权的护眼 App，这是实打实缺失的合规/信任面。`SystemBackgroundPicker` 缺失则让 `ProjectLumenTemplatesFeatureEntry.updateTemplateSystemBackground`（`:28-39`）和 `ProjectLumenViewModel.kt:444` 的包装成为同样不可达的代码路径。
- 修复方案：二选一，别留中间态。①接上：在 `ProjectLumenSettingsScreen.kt` 的隐私/权限区块（`EyeCareActionPlanCard` 之前，约 `:594` 附近）插入 `EyeCareSetupAndPrivacyCard(uiState, permissionRequirements, shizukuState.ready)`——该处这三个参数都已在作用域内；`SystemBackgroundPicker` 接到 `TemplatesScreen` 的 `TemplateEditor` 之后（`ProjectLumenTemplateScreens.kt:306` 附近，仅当 `isActiveTemplate`）。②删除：连带删掉 `PermissionTransparencyLine`、`updateTemplateSystemBackground` 及其 ViewModel 包装。**建议选 ①**（隐私说明卡值得上线），`SystemBackgroundPicker` 可按产品意愿决定。
- 风险/注意：若选 ①，`EyeCareSetupAndPrivacyCard` 展示的 `configurationScore` 会带上 G08-07 的满分 bug，两条要一起修。接 `SystemBackgroundPicker` 后，被 `updateTemplateSystemBackground` 改成 `SYSTEM` 背景的**内置模板**（id 1-6）会在下次启动被 `seedDefaultTemplates`（`ProjectLumenTemplatesFeatureEntry.kt:93-107`）重置回硬编码色值——它只保留 `countdownStyle`，不保留 `backgroundType`/`backgroundValue`。所以选 ① 时必须同时把 `backgroundType`/`backgroundValue`/`primaryColor` 加入 seed 的保留字段，否则会引入"用户改了配色，重启就变回去"的新 bug。

### [G08-09] 备份导入/预览失败无任何用户反馈，异常被崩溃处理器静默吃掉
- 严重度：P2
- 类别：E 韧性
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenBackupFeatureEntry.kt:29-50`
- 现状：
  ```kotlin
  fun previewBackupImport(uri: Uri) {
      scope.launch { _importPreview.value = withContext(Dispatchers.IO) { backup.previewImport(uri) } }
  }
  fun importBackup(uri: Uri) {
      scope.launch { withContext(Dispatchers.IO) { backup.importBackup(uri) }; _importPreview.value = null; ... }
  }
  ```
  两个方法都没有 `runCatching`。`scope` 是 `ProjectLumenViewModel.kt:106` 的 `reportingScope`，其 `CoroutineExceptionHandler` 会把异常记成崩溃报告并**吞掉**（`ProjectLumenViewModel.kt:103-105`）。对照 `ProjectLumenRemoteFeatureEntry.launchRemote` 是有 `runCatching` + `errorMessage` 的。
- 触发场景：用户在系统选择器里挑了一个不是本 App 备份的 JSON/文本文件（或备份文件被截断、URI 权限已失效）。`readBackupJson` 抛 `JSONException`/`IOException`。
- 影响：预览路径——`_importPreview` 保持 `null`，确认弹窗永远不出现，用户点了"选择备份文件"之后界面毫无变化，无法判断是没选中还是文件不对；导入路径——`_importPreview` 不被清空、`applySettingsToActiveRuntime` 不执行，弹窗停在旧的预览摘要上，用户以为导入成功了。两种情况都只在崩溃上报里留痕，用户侧零提示。
- 修复方案：在 `ProjectLumenBackupFeatureEntry` 里加一个 `private val _importError = MutableStateFlow<String?>(null)` 并暴露出去（与 `importPreview` 同样经 `ProjectLumenViewModel` 转出）；`previewBackupImport`/`importBackup` 的 body 包 `runCatching { ... }.onFailure { _importError.value = it.message ?: it.javaClass.simpleName; _importPreview.value = null }`。`ProjectLumenSettingsScreen.kt:468-483` 的备份区块加一行 `StatusLine(Icons.Outlined.WarningAmber, error)` 展示并允许清除。
- 风险/注意：错误文案不要直接把异常 message 当成给用户看的主文案（可能含文件路径），建议前面加一条固定的 `R.string` 说明，异常信息作为副文本。改动会新增一个从 entry 到 ViewModel 到 Settings 屏的状态透传，注意 Settings 屏属别组文件，需协调。

### [G08-10] 首页/设置页的"导出报告"按钮恒可点，但统计关闭时静默什么都不做；无数据时导出空报告
- 严重度：P2
- 类别：E 韧性
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenSharingFeatureEntry.kt:21-25`（静默 return）；恒可点的入口 `app/src/main/java/com/projectlumen/app/app/ProjectLumenEyeCareInsights.kt:172-174`（首页引导卡）、`:407-409`（行动计划卡，被 Statistics 与 Settings 两处复用）；对照已经做对的地方 `app/src/main/java/com/projectlumen/app/app/ProjectLumenMainScreens.kt:625`、`:682-709`（`hasExportableStats` 控制显示导出按钮或空态）
- 现状：
  ```kotlin
  fun shareMonthlyReportPdf() {
      val state = stateProvider()
      if (!state.settings.statsEnabled) return      // 静默
      export.shareMonthlyPdf(state.eyeStats, state.pomodoroStats)
  }
  ```
  而 `EyeCareGuidedSetupCard`/`EyeCareActionPlanCard` 里 `OutlinedButton(onClick = onExportReport)` 没有 `enabled` 判断，也没有任何数据/开关状态检查。
- 触发场景：①用户在设置里关了"统计"（`statsEnabled=false`）后，在首页点"导出月报 PDF"——按钮亮着、点下去毫无反应、无 Toast 无弹窗。②全新安装、统计开着但还没有任何数据，点导出会走到 `export.shareMonthlyPdf(emptyList(), emptyList())`，分享出一份空报告。
- 影响：用户视角是"按钮坏了"，或者分享出一份空白 PDF 给别人。同一个 App 里统计页已经做了正确的空态处理，行为不一致。
- 修复方案：把 `ProjectLumenMainScreens.kt:617-625` 的 `hasStatsData`/`hasExportableStats` 判断提成一个复用函数（例如放到 `ProjectLumenUiFormatters.kt` 的 `internal fun hasExportableStats(uiState): Boolean`），然后给 `EyeCareGuidedSetupCard`（`ProjectLumenEyeCareInsights.kt:83`）和 `EyeCareActionPlanCard`（`:377`）增加 `exportEnabled: Boolean` 参数，`OutlinedButton(enabled = exportEnabled, onClick = onExportReport)`；三个调用点（`ProjectLumenMainScreens.kt:282`、`:655`、`ProjectLumenSettingsScreen.kt:594`）传入该判断结果。`ProjectLumenSharingFeatureEntry` 的三个 `return` 保留作为兜底。
- 风险/注意：改了这两个 Composable 的签名，三个调用点都用的是命名参数，必须同步（`ProjectLumenSettingsScreen.kt` 属别组文件，需协调）。不要改成"点了给 Toast"——本组其余卡片都用 `enabled` + 空态的模式，保持一致。

### [G08-11] 一键"应用推荐/家庭档案/个性化指导"直接覆写 20-30 项已持久化设置，无确认无撤销
- 严重度：P2
- 类别：F 持久化一致性（用户配置丢失）
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenEyeCareInsights.kt:649-691`（`applyFamilyEyeCareMode`，覆写 30 个字段 + 4 项目标）、`:693-765`（`applyPersonalizedEyeCareGuidance`）、`:644-647`（`applyRecommendedEyeCareSettings`）；按钮 `:160-162`、`:219-224`（首页主按钮，`fillMaxWidth` 的 Filled Button）、`:404-406`、`:634-639`（家庭档案/本地指导，均无 `enabled` 无确认）
- 现状：
  ```kotlin
  internal fun applyFamilyEyeCareMode(viewModel: ProjectLumenViewModel) {
      viewModel.updateSettings { current -> current.copy(
          reminderEnabled = true, warnIntervalMinutes = 15, restDurationSeconds = 30, ...
          quietHoursEnabled = true, quietStartMinute = 1260, quietEndMinute = 420,
          quietMode = QuietMode.PAUSE_TIMER.name, disableSkip = true, ... ) }   // 共 30 个字段
      viewModel.updateDailyGoal { current -> current.copy(restBreakGoal = 10, maxContinuousWorkMinutes = 30, ...) }
  }
  ```
  三个函数都是点击即落库，没有二次确认，也没有保存旧值以供撤销。
- 触发场景：用户已经按自己的作息调好了免打扰时段（比如 23:30-06:30）和提醒间隔，然后在首页点了那颗全宽主按钮"应用推荐设置"，或在设置页点了"应用家庭护眼档案"（该按钮就在成长能力卡的按钮排里，与"打开模板""导出报告"混排，很容易误触）。
- 影响：免打扰时段被改成 21:00-07:00、`disableSkip` 被强制打开（此后休息无法跳过）、提醒间隔被改成 15 分钟——全部立即生效且不可撤销，用户只能凭记忆一项项调回去。`applyFamilyEyeCareMode` 还会把 `autoBrightnessEnabled` 强制设为 false，静默关掉用户开着的自动亮度。
- 修复方案：给这三个动作加确认。最小改法：在 `ProjectLumenEyeCareInsights.kt` 里为"家庭档案"和"个性化指导"两个按钮包一层 `AlertDialog` 确认（列出将被改写的关键项：提醒间隔、休息时长、免打扰时段、是否允许跳过），Composable 内用 `var pendingApply by rememberSaveable { mutableStateOf<...>(null) }` 驱动。若要做撤销，可在 `applyFamilyEyeCareMode` 之前把 `uiState.settings`/`uiState.dailyGoal` 快照存进 ViewModel 的一个 `MutableStateFlow`，然后在 `RecommendedEyeCareSetupFeedback`（`ProjectLumenRecommendedSetupFeedback.kt:65`）里加一个"撤销"按钮。
- 风险/注意：`applyRecommendedEyeCareSettings`（首页主按钮）语义相对温和且是引导流程的核心动作，加确认会伤害引导体验——建议只给"家庭档案"和"个性化指导"这两个大范围覆写加确认，"应用推荐设置"改为提供撤销即可。三个函数被 4 个卡片共 6 处调用（`ProjectLumenMainScreens.kt:277`、`:287`、`:297`、`:659`，`ProjectLumenSettingsScreen.kt:598`、`:613`、`:617`），改签名要全量同步。

### [G08-12] `ProjectLumenEntitlementFeatureEntry` 整类不可达（本地自授 PRO，无服务端校验），`SecurityScanUiState` 是无人引用的第二真相源
- 严重度：P2
- 类别：A 架构 / G 安全（潜在）
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenEntitlementFeatureEntry.kt:10-37`；`app/src/main/java/com/projectlumen/app/app/ProjectLumenSecurityScanState.kt:10-13`
- 现状：
  ```kotlin
  fun recordManualProEntitlement(productId: String = "manual_pro") {
      scope.launch {
          entitlementRepository.upsert(EntitlementEntity(source = "manual_license", tier = PlanTier.PRO.name, status = "active", ...))
          settingsRepository.update(nowMillis) { it.copy(planTier = PlanTier.PRO.name, entitlementExpiresAt = 0L, ...) }
      }
  }
  ```
  全仓库搜索 `recordManualProEntitlement`：只有此定义与 `ProjectLumenViewModel.kt:469` 的透传包装，**没有任何 UI 调用点**。`SecurityScanUiState(scanState, scanner)` 同样零引用——ViewModel 实际用的是 `_securityScanState: MutableStateFlow<DeviceSecurityScanState>`（`ProjectLumenViewModel.kt:199-200`）。
- 触发场景：当前不可触发（死代码）。风险在于将来有人在设置页接一颗按钮：它把 `planTier` 直接写成 PRO、`entitlementExpiresAt = 0L`（永不过期），完全绕过 `verifyGooglePurchase`（`ProjectLumenRemoteFeatureEntry.kt:219-249`）那条经服务端校验的路径；而 `planTier` 正是付费模板（`ProjectLumenTemplateScreens.kt:223`）和云同步（`:529`、`ProjectLumenRemoteFeatureEntry.kt:312-317`）的唯一门禁。
- 影响：死代码本身无用户影响；`SecurityScanUiState` 把 `DeviceSecurityScanner`（一个持 `Context` 的扫描器）放进"UI state"形状里，是与 `securityScanState` 并列的第二真相源雏形，留着会误导后续实现。
- 修复方案：删除 `ProjectLumenSecurityScanState.kt` 整个文件，以及 `ProjectLumenEntitlementFeatureEntry.kt` 整个类 + `ProjectLumenViewModel.kt:163-167`（构造）与 `:469`（包装）。若产品确实需要"手动许可证"能力，不要保留现状的裸自授：应改为输入许可证码 → 走 `apiClient` 校验 → 由服务端返回的 entitlement 落库，并且**放在开发者模式屏内**（与 `verifyGooglePurchase` 现在的位置一致，`ProjectLumenDeveloperDebugScreen.kt:339`）。
- 风险/注意：`SecurityScanUiState` 是 `public`（没有 `internal`），删除属于公开 API 变更，但本模块是应用而非库，且零引用，无兼容风险。删 `ProjectLumenEntitlementFeatureEntry` 会连带删掉 ViewModel 对 `repositories.entitlements` 的一处引用，确认 `repositories.entitlements` 仍被 `remoteEntry` 使用（是的，`ProjectLumenRemoteFeatureEntry.kt:236`、`:329`），不要一起删掉。

### [G08-13] 5 个文件各带约 150 行复制粘贴的无用 import（WebView / HttpURLConnection / UpdateChecker 等）
- 严重度：P2
- 类别：H 编译与结构
- 位置（本组内，实测"import 总数 / 其中未在文件正文出现"）：
  - `app/src/main/java/com/projectlumen/app/app/ProjectLumenStatisticsCards.kt:3-198` —— 196 / 164
  - `app/src/main/java/com/projectlumen/app/app/ProjectLumenMetricsAndLayout.kt:3-200` —— 198 / 153
  - `app/src/main/java/com/projectlumen/app/app/ProjectLumenTemplateScreens.kt:3-202` —— 200 / 149
  - `app/src/main/java/com/projectlumen/app/app/ProjectLumenMainScreens.kt:3-199` —— 197 / 146
  - `app/src/main/java/com/projectlumen/app/app/ProjectLumenStatsAndTimerCards.kt:3-198` —— 196 / 143
- 现状：这 5 个文件（以及本组之外的 `ProjectLumenSettingsScreen.kt`、`ProjectLumenSharedComponents.kt` 等 8 个文件）共用**同一份逐字相同的 import 块**——显然是从一个超级文件拆分时整块复制的。于是 `ProjectLumenStatisticsCards.kt`（只画统计卡片）里 import 了 `android.webkit.WebView`、`JavascriptInterface`、`java.net.HttpURLConnection`、`UpdateChecker`、`ProjectLumenApplication`、`CrashReport`。
- 触发场景：不影响编译（Kotlin 编译器对无用 import 既不报错也不告警，`lint { disable += "GradleDependency" }` 之外也没有相关检查），但影响每一次人工/工具阅读：审安全的人看到 `ProjectLumenStatisticsCards.kt` import 了 WebView + HttpURLConnection，会认为这个文件在做网络与 WebView；文件的真实依赖面完全被淹没。
- 影响：可维护性与可审计性。另有一个实际隐患：这些文件同时 import 了 `android.provider.Settings` 与 `androidx.compose.material.icons.outlined.Settings`、`android.content.Context` 等同名/易混符号，后续在这些文件里写 `Settings.` 会解析到意料之外的那个。
- 修复方案：逐文件做一次 import 清理（IDE 的 Optimize Imports，或按上面统计的名单删除）。**必须逐文件做，不能跨文件套用同一份删除名单**——同一个 import 在有的文件里是用到的。
- 风险/注意：自动统计里有两类假阳性，删之前要确认：①`getValue` / `setValue`（`androidx.compose.runtime.getValue/setValue`）在正文里不出现名字，但 `var x by remember { ... }` 的属性委托**需要**它们，删掉会编译失败；②`ExperimentalLayoutApi` / `ExperimentalMaterial3Api` 这类只出现在 `@OptIn(...)` 注解里的符号，要确认该文件确实没有 `@OptIn`。建议先删 `android.*` / `java.net.*` / `org.json.*` / `core.update.*` / `webkit.*` 这些明显与文件职责无关的段落，再逐步收紧。

### [G08-14] `DeviceSecurityScanCard` 一半文案是硬编码英文，与同卡的本地化标签混排
- 严重度：P2
- 类别：E 韧性（i18n 正确性）
- 位置：`app/src/main/java/com/projectlumen/app/app/DeviceSecurityScanCard.kt:74`、`:83`、`:90`、`:101`、`:113`、`:124`、`:130`、`:136-139`、`:154-157`、`:172-175`
- 现状：同一张卡里标签走资源（`stringResource(R.string.developer_crooot_root_status)` 等 5 条），值和说明却是硬编码字面量：`"Device Security Scan"`、`"Tap \"Scan Now\" to check device security status..."`、`"Scanning device security… This may take up to 60 seconds."`、`"Scan failed: ${...}"`、`"Scan Now"/"Scan Again"`、`"ROOTED"/"Clean"`、`"Compromised"/"Not checked"`、`"Enforcing ✓"/"Permissive ⚠"`。
- 触发场景：中文用户在设置里打开开发者模式（`ProjectLumenSettingsScreen.kt:1294` 的 `settings.developerModeEnabled` 是用户可开的开关）→ 进入开发者调试屏（`ProjectLumenDeveloperDebugScreen.kt:370` 是这张卡唯一的调用点）。
- 影响：卡片呈现"中文标签 + 英文取值"的半汉化状态；`R.string.developer_crooot_*` 系列资源已经存在，说明这一屏本来是要本地化的，这里是遗漏而非有意为之。Android lint 的 `HardcodedText` 只查 XML，不会在 CI 里拦住。
- 修复方案：在 `app/src/main/res/values/strings.xml` 与 `values-zh*/strings.xml` 补 `developer_crooot_scan_title`、`developer_crooot_scan_hint`、`developer_crooot_scanning`、`developer_crooot_scan_failed`（带 `%1$s` 占位）、`developer_crooot_scan_now`、`developer_crooot_scan_again`，以及取值枚举 `..._rooted`/`_clean`/`_found`/`_none`/`_ok`/`_compromised`/`_not_checked`/`_enforcing`/`_permissive`/`_unknown`/`_verified`/`_failed`，然后把上述行改为 `stringResource(...)`。
- 风险/注意：`"Enforcing ✓"` / `"Permissive ⚠"` 里的符号是文案的一部分，迁移时别丢。这一屏是开发者面向的诊断界面，翻译用词应保留英文技术术语（ROOT / SELinux / TEE）不要意译。

## 已核查但无问题的点

**（1）`remember` key 完整性——本组 20 处全部正确，修复阶段请勿"顺手改"**
brief 点名这是本仓库"UI 不更新"的头号成因，我逐处读了被调函数的实现来核对，结论是本组不存在漏 key：
- `rememberEyeCareInsightSummary`（`ProjectLumenEyeCareInsights.kt:772-780`）key = `eyeStats, settings, dailyGoal, permissionRequirements, shizukuReady`；`eyeCareInsightSummary`（`:782-900`）实际只读 `uiState.eyeStats`、`uiState.settings.*`、`uiState.dailyGoal.maxContinuousWorkMinutes` 与后两个入参，完整。且 `PermissionRequirements` 是 `data class`（`ProjectLumenPermissionState.kt:16`），key 比较走值相等，不会每帧失效。
- `BreakScreen` 的 `remember(uiState.templates, uiState.settings.activeTipTemplateId) { activeTemplate(uiState) }`（`ProjectLumenMainScreens.kt:396-398`）——这正是 brief 描述的"整个 uiState 传进纯函数、只拿两个字段当 key"的形状，但 `activeTemplate`（`ProjectLumenUiFormatters.kt:314-316`）确实只读这两个字段，**是正确的**。
- `remember(template?.layoutJson) { templateCountdownStyle(template) }`（`ProjectLumenMainScreens.kt:399`）：`templateCountdownStyle`（`ProjectLumenUiFormatters.kt:301-312`）只读 `layoutJson`。
- `EyeCareGuidedSetupCard` 的 `steps`（`ProjectLumenEyeCareInsights.kt:99-141`）4 个 key 覆盖了 lambda 读到的全部字段（`settings` 的 5 个开关、`eyeStats`、`missingRuntimePermission`、`distanceCalibrated`）。
- `HabitSuggestionCard`（`ProjectLumenStatisticsCards.kt:257`）key = `eyeStats, dailyGoal.maxContinuousWorkMinutes`，而 lambda 只读 `dailyGoal` 的这一个字段。
- 其余 `:195`、`:242`、`:983`、`:998`、`ProjectLumenStatsAndTimerCards.kt:240`、`ProjectLumenStatisticsCards.kt:202/297/298/299/300/302/310`、`ProjectLumenMainScreens.kt:613/614/617`、`DeviceSecurityScanCard.kt:232` 均已核对完整。

**（2）分层没有被击穿**
本组没有任何 Composable 直接读 DAO / Repository / 系统服务，也没有直接起 Service——全部经 `uiState` + lambda 回调。`LocalContext` 的使用面只有三类且都正当：跳系统设置页（`openExactAlarmSettings`/`openOverlaySettings`/`openUsageAccessSettings` 等）、分享 Intent（`ProjectLumenHomeConvenienceCard.kt:137-150`，已用 `runCatching` 兜 `ActivityNotFoundException`）、剪贴板（`:99-108`，走 `LocalClipboard` 的挂起 API）。

**（3）组合期没有昂贵计算，也没有每帧构造格式化器**
所有聚合（`sumOf`/`maxOfOrNull`/`average`/`take`/`reversed`/`distinct`）都在 `remember` 里。`REMOTE_CLOUD_BACKUP_FORMATTER`（`ProjectLumenRemoteCloudCard.kt:221-222`）是顶层 `val`，不随重组重建；本组**没有**任何 `SimpleDateFormat`/`DateTimeFormatter` 在 Composable 体内构造。

**（4）除零 / NaN / 空集合路径都已兜住**
- `UsageAppRow` 的进度分母 `maximum` 取自 `topApps.maxOf(...).coerceAtLeast(1L)`（`ProjectLumenDeviceInsightsCard.kt:193`），且 `maxOf` 被 `topApps.isEmpty()` 判断保护（`:189`）——不会出 NaN，也不会 `maxOf` 抛空集合异常。
- `GoalProgressCard` 四条进度的分母都 `.coerceAtLeast(1)`（`ProjectLumenStatsAndTimerCards.kt:262/268/273/278`）。
- `TrendCard` 用 `recent.maxOf { max(it.workingSeconds, 1L) }` 且被 `recent.isEmpty()` 保护（`ProjectLumenStatisticsCards.kt:216/222`）。
- `averageContinuousMinutes` 先 `filter { > 0 }` 再 `.average().takeIf { !it.isNaN() }`（`ProjectLumenEyeCareInsights.kt:802-808`、`ProjectLumenStatisticsCards.kt:302-309`）。
- `completionRate`/`skipRate` 都判了 `totalBreakDecisions > 0`；`AdvancedStatsCard` 用 `.coerceAtLeast(1)`（`:301`）。
- 全组**没有** `!!`、没有 `first()`、没有 `list[0]`，取首元素一律 `firstOrNull()`。

**（5）loading / empty / error 三态是齐的**
`DeviceUsageAndPowerInsightsCard` 对 `DeviceUsageAvailability` 五个取值穷尽处理且无 `else`（`ProjectLumenDeviceInsightsCard.kt:124-197`），另有刷新中的 spinner（`:80-82`）和 `AGGREGATED_FALLBACK` 降级说明（`:186-188`）。我核了数据源：`availability == AVAILABLE` 蕴含 `usage != null`（`core/insights/AndroidDeviceInsightDataSource.kt:56-67`），所以 `state.usage?.let` 不会静默留白。`RemoteCloudAccountCard` 有 busy 进度条 + `errorMessage` + `lastOperation` 三行，且每颗按钮都 `enabled = !state.busy`（G08-03 的问题只在成长能力卡那一处）。`DeviceInsightsState.failureReason` 从未展示，但用户仍能看到 `device_insights_restricted` 的通用提示，属可诊断性缺口而非空白态，未单独立项。

**（6）`when` 穷尽性 / 花括号平衡**
`recommendationText`（`ProjectLumenDeviceInsightsCard.kt:370-399`）、`chargeStateLabel`、`appCategoryLabel`、`UsageAvailabilityContent`、`DeviceSecurityScanCard` 对 sealed interface 的 `when`（`:80-105`）都无 `else`，新增枚举项/子类会编译失败——这是想要的。`RecommendationLine`（`:355-365`）的图标 `when` 带 `else`，是有意的降级默认值。19 个文件的 `{}`/`()`/`[]` 净差全部为 0（实测），工作树除 `docs/audit/` 外干净，`git show HEAD:<file>` 与工作副本一致，拆分没引入括号失衡。

**（7）看着像 bug 但不是的三处，请勿改**
- `resumeReminder`（`ProjectLumenRuntimeFeatureEntry.kt:81-90`）用 `newWorkingState` 重开一整段工作计时、而非续上暂停时的剩余时间。这与引擎自身的一小时暂停自动恢复行为一致（`core/runtime/ReminderEngine.kt:104-105` 同样调 `newWorkingState`），是设计选择而非疏漏——只改一侧会引入不一致。
- `backupJsonToSyncChanges`（`ProjectLumenRemoteFeatureEntry.kt:492-516`）里 `payload ?: return@mapNotNull null` 对数组类集合永远不会触发（`JSONObject.put(k, null)` 会删键，所以 payload 恒非空）。我顺着追过"推空 `{}` → 对端导入一条空记录"这条路：**不可达**，因为 `DataBackupService.buildBackupJson`（`:98-111`）对每个集合都写入（可能为空的）JSONArray，`optJSONArray` 不会返回 null。这里不用花时间。
- `DefaultTipTemplates` 的硬编码英文 `name` 与固定 id 1-6 没问题：`templateDisplayName`/`templateSubtitle`（`ProjectLumenUiFormatters.kt:256-279`）把 id 1-6 映射到本地化资源，内置模板的英文 `name` 永远不会显示给用户。`seedDefaultTemplates`（`ProjectLumenTemplatesFeatureEntry.kt:86-113`）只改 `existing.isBuiltin` 的行，且用户可编辑字段（`titleText`/`subtitleText`/`showSkipButton`/`imagePath`）与 `countdownStyle`（经 `mergeTemplateLayout`）都被保留——**前提是 G08-08 不去接 `SystemBackgroundPicker`**，那条已在其风险栏说明。
- `stopReminderRuntime` 与 `stopPomodoroRuntime`（`ProjectLumenRuntimeFeatureEntry.kt:159-171`）逐字重复，但合并只是改名、无行为收益，为避免无谓改动未立项。

## 跨组线索（不计入本组缺陷，请路由给对应组）

- `app/src/main/java/com/projectlumen/app/app/ProjectLumenPermissionState.kt:42-45`：`return refreshKey.let { context.permissionRequirements() }` **没有包在 `remember(refreshKey)` 里**，所以每次重组都重新执行 7 项权限检查，其中 `hasUsageStatsAccess()`（AppOps）、`canScheduleExactAlarms`、`canUseFullScreenIntent`、`Settings.canDrawOverlays`、`Settings.System.canWrite` 都是 binder / provider 调用。叠加 G08-04 的每秒全量重组，等于主线程上每秒约 7 次 IPC，且首页与统计页各一份。修法很小：`return remember(refreshKey) { context.permissionRequirements() }`（`refreshKey` 已经在 `ON_RESUME` 时自增，语义不变）。该文件不在 G08 名单内。
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenStateStore.kt:91-96`：`nowMillis` 被 `combine` 进 `ProjectLumenUiState`，是 G08-04"整棵 UI 每秒重组"的结构性根因。彻底修复需把时钟从 `uiState` 里摘出成独立 `StateFlow<Long>`，只让倒计时相关卡片订阅。
