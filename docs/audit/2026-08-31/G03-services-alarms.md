# G03 前台服务 / 精确闹钟 / 通知 / 备份导出 审查报告

- 审查文件数：16，总行数：3141
  （`core/services/` 14 个文件 3046 行 + `core/time/` 2 个文件 95 行；为核实调用方另读了 `AndroidManifest.xml`、`ForegroundServiceArchitectureTest.kt`、`ProjectLumenApplication.startTimerService`、`ProjectLumenViewModel`/`*FeatureEntry` 的调度器、`SettingsRepository`、`RuntimeRepository`、`SecureShareIntents`，缺陷只报在本组文件上。）
- 结论摘要：这一组的**平台约束覆盖度明显高于平均水平**——`ForegroundServiceController` 是真正的唯一提权入口（有源码文本测试兜底）、`PendingIntent` 全部带 `FLAG_IMMUTABLE`、精确闹钟权限被撤销时有 `setAndAllowWhileIdle` 降级、四个 receiver 全部 `goAsync()`、`POST_NOTIFICATIONS` 未授权时 `SecurityException` 全部被捕获。但**可靠性骨架有两处结构性缺口**：一是 `AlarmReceiver` 与 `TimerForegroundService` 是两个平权的引擎驱动者，同一到点时刻会各自 `advance()` 一次，统计双计、提示音双响；二是重启与 WorkManager 对账两条恢复路径都只重排闹钟、从不调用 `advance()`，因此关机/被杀期间到点的那次休息会被永久丢弃。最严重的单点是 `ForegroundServiceController.start` 里的 `SystemClock.sleep(2_000)`——它有确凿的主线程调用链（设置页开关 → `viewModelScope`(Main) → `LightMonitorService.start`），一次平台拒绝就是 2 秒 UI 冻结。另有三处主线程重 IO（PDF/位图/备份 JSON）和一处每秒 tick 丢弃亚秒余量导致工作时长系统性少算。

## 缺陷清单

### [G03-01] `ForegroundServiceController.start` 用 `SystemClock.sleep(2_000)` 做重试，会冻结主线程
- 严重度：P1
- 类别：D 生命周期
- 位置：`app/src/main/java/com/projectlumen/app/core/services/ForegroundServiceController.kt:73`
  确凿的主线程调用链：`app/app/ProjectLumenViewModel.kt:106`（`reportingScope` = `viewModelScope`，即 `Dispatchers.Main.immediate`）→ `app/app/ProjectLumenSettingsFeatureEntry.kt:146-149` `scope.launch { applyLightMonitoringSettings(...) }` → 同文件 `:190-192` → `ProjectLumenApplication.startLightMonitoring()` → `core/light/LightMonitorService.kt:195-201`。同型链还有 `applyDeveloperDebugSettings`（`:198-200`）→ `DeveloperDebugOverlayService.kt:333/345`。
- 现状：
  ```kotlin
  if (isForegroundServiceStartNotAllowed(exception)) {
      Log.i(TAG, "... retrying ${serviceName} in ${RETRY_DELAY_MILLIS}ms")
      SystemClock.sleep(RETRY_DELAY_MILLIS)   // 2_000L，阻塞调用线程
      try { ContextCompat.startForegroundService(context, intent); return true }
  ```
  文件头注释明确说这是为 Android 16 `mAllowStartForeground` 冷启动窗口设计的重试，但实现方式是同步睡眠，且 `start()` 没有任何"必须在后台线程调用"的约束。
- 触发场景：用户在设置页打开"环境光监测"或"开发者调试浮层"的瞬间，进程刚从冷启动进入前台、平台还未把 `mAllowStartForeground` 置真（Android 12+ 抛 `ForegroundServiceStartNotAllowedException`，或 Android 12 之前的 `IllegalStateException("startForegroundService() not allowed")`），`isForegroundServiceStartNotAllowed` 命中 → 主线程 `sleep` 2 秒。
- 影响：点开关后界面卡死 2 秒（掉帧、输入无响应）；若同一次操作里两个服务先后被拒（环境光 + 调试浮层同时开启），累计 4 秒，已进入 ANR 观察区间；`ProjectLumenViewModel.reportIfThrows` 是同步 `runCatching`，不会把它挪出主线程。
- 修复方案：把重试改为非阻塞。在 `ForegroundServiceController.kt` 内新增一个惰性主线程 Handler（`private val retryHandler by lazy { Handler(Looper.getMainLooper()) }`，**必须 `by lazy`**，否则纯 JVM 单测加载该 object 会 `ExceptionInInitializerError`），把 `SystemClock.sleep` 段替换为 `retryHandler.postDelayed({ runCatching { ContextCompat.startForegroundService(context, intent) }.onFailure { handleFailure(...) } }, RETRY_DELAY_MILLIS)`，并让 `start()` 在这条路径上先返回 `true`（"已排入重试"）或改成返回一个三态枚举。重试代码**必须留在本文件内**：`ForegroundServiceArchitectureTest.androidXForegroundServiceCallsStayInsideSharedController`（`app/src/test/.../ForegroundServiceArchitectureTest.kt:22-42`）断言除 `ForegroundServiceController.kt` 外任何 `.kt` 都不得出现 `ContextCompat.startForegroundService(` / `ServiceCompat.startForeground(` 字面量。
- 风险/注意：`start()` 的返回值语义会变（当前"重试成功=true"，改后是"已受理"）。现有调用方（`ProjectLumenApplication.kt:349`、`LightMonitorService.kt:196`、`EyeProtectionOverlayService.kt:189`、`ProximityDetectionService.kt:500`、`DeveloperDebugOverlayService.kt:333/345`）目前**全部忽略返回值**，所以改动是安全的；但 `ForegroundServiceControllerTest` 里若有对 `start` 返回值的断言需同步调整。

### [G03-02] `AlarmReceiver` 与 `TimerForegroundService` 双引擎驱动者、无幂等保护：统计双计、提示音双响
- 严重度：P1
- 类别：B 并发（兼 F 持久化）
- 位置：`core/services/AlarmReceiver.kt:31-34`、`:90-124`（`reconcileRuntime` → `advance` → `applyTransition`）；`core/services/TimerForegroundService.kt:110-139`、`:225-249`（tick 循环里的 `advanceDuePhases` → `applyTransition`）；`ReminderActionReceiver.kt:34-47` 同型
- 现状：两处都是"读快照 → `ReminderEngine/PomodoroEngine.advance()` → 写 delta + upsert"，且各自跑在独立的 `CoroutineScope(Dispatchers.IO)` 上：
  ```kotlin
  // AlarmReceiver
  val runtime = app.runtimeRepository().getOrDefault()
  val transition = ReminderEngine().advance(settings, runtime, nowMillis) ?: ...
  statisticsRepository.applyEyeDelta(...); app.runtimeRepository().upsert(transition.nextRuntime)
  ```
  `RuntimeRepository`（`core/repositories/RuntimeRepository.kt:29-31`）的 `upsert` 没有任何锁（唯一的 `Mutex` 只保护 MMKV 迁移），`StatisticsRepository` 也无锁。没有任何"谁是权威"的约定，也没有基于 `updatedAt`/阶段序号的 CAS。
- 触发场景：**正常路径就会撞**——闹钟被安排在 `nextReminderAt`，而前台服务的 tick 循环每秒判断 `now >= nextReminderAt` 也会在同一秒内 `advance()`。两者读到同一份未推进的快照，各自算出同一个 transition 并各自落库。
- 影响：每次休息到点，`DailyEyeStats` 的 `completedBreakCount` / `preAlertCount` / `workingSeconds` 可能被加两次（统计页数字虚高、导出与月报同样失真）；`AudioEvent.ReminderTone` 触发两次 → 提示音重复响；`EyeProtectionOverlayService.show` 也可能被调用两次。
- 修复方案：确立单一权威。推荐：在 `AlarmReceiver.onReceive` 里先判断计时前台服务是否存活（例如 `ProjectLumenApplication` 增加一个 `@Volatile var timerLoopRunning`，由 `TimerForegroundService.onStartCommand`/`onDestroy` 维护），存活则 receiver 只负责"发通知 + 拉起服务"，`reconcileRuntime` 的 `advance()` 直接跳过；服务不在时才由 receiver 推进。若不想引入进程内标志，则给推进操作加幂等键：在 `RuntimeStateEntity` 上用 `updatedAt`（或新增 `phaseSequence`）做条件更新，`applyTransition` 前先比对读到的 `updatedAt` 是否仍是库里的值，不一致就放弃本次 transition（写 `RuntimeRepository` 的条件 upsert 属 G02 组，需协同）。
- 风险/注意：改成"服务存活时 receiver 不推进"后，必须保证服务被系统杀掉后标志会被清（`onDestroy` 未必被调用——建议标志存 MMKV 并带 `elapsedRealtime` 心跳，或用 `ActivityManager.getRunningServices` 之外的可靠信号）；否则会退化成两条路径都不推进，比双计更严重。

### [G03-03] `BootReceiver` 接受 `LOCKED_BOOT_COMPLETED` 且组件是 `directBootAware`，但 `restoreScheduledWork` 读的是 CE 加密的 Room
- 严重度：P1
- 类别：D 生命周期
- 位置：`core/services/BootReceiver.kt:34-39`（`isRecoveryAction` 把 `ACTION_LOCKED_BOOT_COMPLETED` 当恢复点）、`:41-44`（随即 `settingsRepository.get()` / `runtimeRepository().get()`）；配套声明见 `app/src/main/AndroidManifest.xml:126-133`（`android:directBootAware="true"` + `<action android:name="android.intent.action.LOCKED_BOOT_COMPLETED" />`）
- 现状：
  ```kotlin
  fun isRecoveryAction(action: String?): Boolean =
      action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_LOCKED_BOOT_COMPLETED || ...
  suspend fun restoreScheduledWork(app: ProjectLumenApplication) {
      val settings = app.settingsRepository().get()   // Room（CE 存储），用户解锁前不可读
  ```
  全仓库 `rg isUserUnlocked|UserManager` 无任何命中，即没有任何直接启动模式的守卫。
- 触发场景：设了锁屏密码的设备（FBE 全量加密，Android 10+ 默认）开机后、用户第一次解锁前，系统就把 `LOCKED_BOOT_COMPLETED` 投给这个 `directBootAware` 的 receiver。此时 CE 目录未解密，Room 打不开数据库文件（`SQLiteException: unable to open database file`），MMKV/DataStore 同理。
- 影响：每次开机都会向崩溃遥测灌一条"已处理失败"（`runCatching{}.onFailure{ recordHandledFailure }` 吞掉异常，所以不崩，但噪声掩盖真实故障）；同时进程会在直接启动模式下被提前创建，`AppDatabase` / MMKV 单例可能在不可用状态下完成初始化并被后续复用；本次 `LOCKED_BOOT_COMPLETED` 的恢复工作全部无效（真正的恢复要等随后的 `BOOT_COMPLETED`）。
- 修复方案：在 `BootReceiver.onReceive` 里最先判断存储可用性——`if (Build.VERSION.SDK_INT >= 24 && !context.getSystemService(UserManager::class.java).isUserUnlocked) return`；或把 `isRecoveryAction` 中的 `ACTION_LOCKED_BOOT_COMPLETED` 移除并同步删掉 manifest 里那条 action 与 `directBootAware="true"`（本 receiver 的工作全部依赖 CE 数据，没有直接启动的价值）。注意 `isRecoveryAction` 是 `internal`/公开给单测的纯函数，改它要同步改对应测试。
- 风险/注意：保留 `directBootAware` 时若只加解锁判断，Android 15 强停恢复语义（文件头注释提到的那条）不受影响；若删掉 `LOCKED_BOOT_COMPLETED`，需确认没有别的模块依赖"解锁前也能收到一次"。

### [G03-04] 重启与 WorkManager 对账两条恢复路径都不调用 `advance()`，逾期阶段永不推进
- 严重度：P1
- 类别：E 韧性
- 位置：`core/services/BootReceiver.kt:60-73`、`core/services/TimerReconciliationWorker.kt:23-27`
- 现状：两条路径只做"重排闹钟 + 拉起服务"，从不推进引擎：
  ```kotlin
  // TimerReconciliationWorker
  if (settings.keepAliveEnabled && runtime.activeEngine != ActiveEngine.IDLE.name) {
      app.startTimerService()
      app.notifications.syncRuntimeAlarms(settings, runtime)   // runtime 里的时间点已全部过期
      enqueue(applicationContext)
  }
  ```
  而 `NotificationService.scheduleReminder/scheduleBreakDone`（`NotificationService.kt:86-99`）对过期时间点是**静默丢弃**（`if (preAlertAt > System.currentTimeMillis())`）。对比 `AlarmReceiver.reconcileRuntime` 是会先 `ReminderEngine().advance(...)` 的——恢复路径缺了这一步。
- 触发场景：(a) 计时进行中关机 10 分钟，期间 `nextReminderAt` 到点，开机后 `restoreScheduledWork` 用过期状态重排 → 一个闹钟都没排上；(b) 进程被系统/厂商清理，15 分钟后 `TimerReconciliationWorker` 醒来，同样只重排过期时间点。此外 (b) 里的 `app.startTimerService()` 在 Android 12+ 属于后台启动前台服务，普通（非加急）WorkManager 任务不在豁免名单内，会被平台拒绝（`ForegroundServiceController` 会把它记为"预期拒绝"），所以对账根本拉不起服务。
- 影响：关机/被杀期间到点的那次休息被永久丢弃——不提醒、不弹强制休息浮层、不计统计；且因为闹钟一个都没排上，此后**再也不会**有任何唤醒源，计时器直到用户手动打开 App 才复活（`keepAliveEnabled=false` 且 `notificationEnabled=false` 但开了强制浮层的组合下，`BootReceiver` 连 `startTimerService` 都不会调，完全静默死亡）。
- 修复方案：把 `AlarmReceiver` 的对账逻辑抽成可复用的挂起函数（例如在 `AlarmReceiver.Companion` 暴露 `suspend fun reconcileNow(app, notifications, nowMillis)`，内部就是现有 `reconcileRuntime` 的 `advance` + `applyTransition` + `syncRuntimeAlarms`），然后：`BootReceiver.restoreScheduledWork` 在读到非 IDLE 的 runtime 后先调用它，再走现有的 `syncRuntimeAlarms`/`startTimerService`；`TimerReconciliationWorker.doWork` 同样先调用它。另外 `TimerReconciliationWorker` 在 FGS 启动被拒时应有兜底：直接 `notifications.showOngoingStatus(runtime)` + 依赖闹钟继续走，或把请求改为 `setExpedited(...)` 的加急工作。顺带修 `BootReceiver.kt:61` 与 `:66` 在 `keepAliveEnabled && notificationEnabled` 同时为真时**重复调用 `startTimerService()`** 两次（多一次 `startForegroundService` 与一次 WorkManager REPLACE，无功能收益）。
- 风险/注意：恢复时补推进会一次性写入一段较大的 `workingSeconds` delta，需确认 `ReminderEngine.advance` 对"逾期很久"的输入不会算出离谱数值（`coerceElapsedSecondsSince` 只做非负截断，不设上限）；建议同时给恢复路径的 delta 加一个合理上限（如不超过 `warnIntervalMinutes`）。

### [G03-05] `ExportService` 三个导出入口在主线程做 PDF 渲染、位图压缩与文件写入
- 严重度：P1
- 类别：B 并发
- 位置：`core/services/ExportService.kt:23-34`（`shareCsv`）、`:36-54`（`shareMonthlyPdf`）、`:56-69`（`shareStatsImage`）
  调用链：`app/app/ProjectLumenMainScreens.kt:685/690/695` Compose `onClick` → `ProjectLumenViewModel.kt:455-462` `reportIfThrows { ... }`（`:504-506` 就是同步 `runCatching`，无协程）→ `ProjectLumenSharingFeatureEntry.kt:9-25`（无协程、无 `withContext`）→ 本文件。
- 现状：三个函数都是普通（非 `suspend`）函数，直接 `File(...).writeText(...)` / `FileOutputStream(...).use { ... }`，`shareStatsImage` 还要先 `createBitmap(1200, 900)`（ARGB_8888 约 4.3 MB）画 7~14 根柱子再 `compress(PNG, 100, output)`，`shareMonthlyPdf` 要构造 `PdfDocument` 画满一页 A4。
- 触发场景：用户在统计页点"导出 CSV / 导出图片 / 导出月报"。数据越多（`monthlyEyeStats.takeLast(22)`）耗时越长，低端机上 PNG 无损压缩 100 质量的 1200×900 位图通常 300 ms~1 s+。
- 影响：点击后界面明显卡顿甚至 ANR（主线程磁盘写入同时会触发 StrictMode 的 `DiskWriteViolation`）；导出失败时异常被 `reportIfThrows` 静默记录，用户看不到任何反馈。
- 修复方案：把三个函数改成 `suspend fun` 并在内部 `withContext(Dispatchers.IO) { 生成文件 }`，只把最后的 `SecureShareIntents.shareStream(...)`（`startActivity`）留在主线程；相应地 `ProjectLumenSharingFeatureEntry` 的三个方法改为 `scope.launch { ... }`（该类已持有 `stateProvider`，需要额外注入 `CoroutineScope`，与 `ProjectLumenBackupFeatureEntry.kt:14-19` 的既有写法一致）。`buildStatsBitmap` 生成的位图在 `compress` 之后应 `recycle()`（放在 `try/finally` 里），避免峰值多占 4 MB。
- 风险/注意：改成挂起后连点导出会并发写同一个固定缓存文件名（`project_lumen_stats.png` 等），建议顺带在 Entry 层加"导出中"状态位或把文件名加时间戳。签名改动会波及 `ProjectLumenViewModel:455-462` 与 Compose 的 `onClick` 引用（`viewModel::shareStatistics` 形态不变，无需改 UI）。

### [G03-06] `DataBackupService.shareBackup` 在主线程序列化并写入备份 JSON
- 严重度：P1
- 类别：B 并发
- 位置：`core/services/DataBackupService.kt:40-51`
  调用链：`ProjectLumenViewModel.kt:464` → `app/app/ProjectLumenBackupFeatureEntry.kt:23-27` `scope.launch { backup.shareBackup() }`，而 `scope` 是 `reportingScope`（`ProjectLumenViewModel.kt:106`，`Dispatchers.Main.immediate`）。同文件的 `previewBackupImport`/`importBackup`（`:29-50`）都套了 `withContext(Dispatchers.IO)`，只有 `shareBackup` 漏了。
- 现状：
  ```kotlin
  suspend fun shareBackup() {
      val file = File(context.cacheDir, "project_lumen_backup.json")
      file.writeText(exportBackupJson().toString(2), Charsets.UTF_8)   // 主线程
  ```
  `exportBackupJson()` 会把全部模板、全部 `dailyEyeStats`/`dailyPomodoroStats`、权益、特性开关、提醒计划序列化成缩进 JSON（`toString(2)`）。DAO 是 suspend 的所以不会触发 Room 的主线程断言，但 JSON 组装与 `writeText` 实打实跑在主线程。
- 触发场景：设置页点"导出备份"（`ProjectLumenSettingsScreen.kt:1223`）。使用一年后 `dailyEyeStats` 有数百行、模板与计划若干，`toString(2)` 是纯字符串拼接。
- 影响：导出瞬间界面卡顿，StrictMode 主线程磁盘写入告警；数据量大时可达 ANR。
- 修复方案：`shareBackup()` 内把生成部分包进 `withContext(Dispatchers.IO) { ... }`，返回 `uri` 后再在原调度器上调 `SecureShareIntents.shareStream`。`suspend` 函数不应假设调用方的调度器，同理建议给 `readBackupJson`（`:113-119`，`contentResolver.openInputStream` 也是阻塞 IO）也加 `withContext(Dispatchers.IO)`，这样即便将来有调用方忘了包 IO 也安全。
- 风险/注意：无签名变化，调用方无需改动。

### [G03-07] `importBackupJson` 既非事务也非幂等：中途失败留下半导入库，重试则统计翻倍
- 严重度：P1
- 类别：F 持久化一致性
- 位置：`core/services/DataBackupService.kt:73-84`（八个 import 串行、无事务）、`:308-333`（`importEyeStats` 累加）、`:335-355`（`importPomodoroStats` 累加）、异常点 `:586` 与 `:601`（`getString("statDate")` 缺字段即抛 `JSONException`）
- 现状：
  ```kotlin
  suspend fun importBackupJson(json: JSONObject): BackupImportSummary {
      importSettings(...); importDailyGoal(...); importTemplates(...)
      importEyeStats(...); importPomodoroStats(...); importEntitlements(...) ...
  ```
  且 eye/pomodoro 统计是"与现有值相加"：`workingSeconds = current.workingSeconds + imported.workingSeconds`。
- 触发场景：(a) 备份文件被截断或某个 stats 条目缺 `statDate`（`getString` 而非 `optString`）→ 异常从 `importEyeStats` 抛出，此时 settings/dailyGoal/templates 已经落库，权益/特性开关/提醒计划尚未；(b) 用户看到导入失败后**再点一次导入**，或误把同一个备份导入两次 → 已成功写入的那部分统计被再加一遍。
- 影响：库处于设置已改、数据未齐的中间态（用户设置被覆盖但统计不全）；重试后工作/休息时长、跳过次数、番茄数全部翻倍且不可逆（没有撤销入口）。
- 修复方案：(1) 把整个 `importBackupJson` 放进一次 Room 事务——`database.withTransaction { ... }`（`androidx.room:room-ktx` 的 `RoomDatabase.withTransaction`），注意 `importSettings` 里的 `eyeCarePreferences?.saveFromSettings` 是 DataStore 写入、不能进事务，应挪到事务成功之后；(2) 逐条解析改成容错：`toEyeStats`/`toPomodoroStats` 的 `getString("statDate")` 改为 `optString("statDate")` 并在为空时 `continue`（`importEyeStats` 内已有 `?: continue` 的骨架，只差把抛异常改成返回 null）；(3) 幂等化：统计合并从"相加"改为"按 `statDate` 取较大值"或"以导入值覆盖"，并在 JSON 里记录 `exportedAt`+来源，重复导入同一 `exportedAt` 时直接跳过。
- 风险/注意：把"相加"改成"覆盖/取大"会改变既有语义（跨设备合并两台设备的统计时会少算），需要与产品确认；若必须保留相加，则至少要靠 `exportedAt` 去重，避免同一备份被计两次。

### [G03-08] 每秒 tick 丢弃亚秒余量，工作/休息时长被系统性少算
- 严重度：P1
- 类别：F 持久化一致性
- 位置：`core/services/TimerForegroundService.kt:196-208`（WORKING/PRE_ALERT/AWAITING_ACTION 分支）、`:211-219`（RESTING 分支）、`:176-190`（安静时段分支同型）；辅助函数 `core/time/DateKeys.kt:13-16`
- 现状：
  ```kotlin
  val seconds = nowMillis.coerceElapsedSecondsSince(max(state.reminderStartedAt, state.lastStatsTickAt))
  if (seconds > 0L) { statisticsRepository.updateEyeStats(...) { it.copy(workingSeconds = it.workingSeconds + seconds, ...) } }
  state.copy(lastStatsTickAt = nowMillis, ...)   // 无论是否记账，游标都推到 nowMillis
  ```
  `coerceElapsedSecondsSince` 是整秒下取整（`(this - startMillis) / 1000L`），而 `lastStatsTickAt` 被无条件推进到 `nowMillis`，两者之间最多 999 ms 的余量被丢弃。
- 触发场景：`tickingFlow`（`:141-152`）按 `elapsedRealtime` 对齐每 1000 ms 发射，但记账用的是 `System.currentTimeMillis()`，且 `delay` 的实际唤醒有抖动。相邻两次 tick 的差值只要落到 999 ms，这一秒就整秒丢失；设备负载高、进程被降频或 Doze 短暂拉伸时抖动更明显。
- 影响：统计页/导出/月报里的"工作时长""休息时长"低于真实值（抖动分布决定幅度，坏情况下可达十几个百分点），且随使用时长累积，用户会看到"明明用了一小时只记了 50 分钟"。
- 修复方案：记账后让游标只前进已入账的整秒，而不是跳到 `nowMillis`。把两处（以及安静时段那处）改成先算 `val base = max(state.reminderStartedAt, state.lastStatsTickAt)`、`val seconds = nowMillis.coerceElapsedSecondsSince(base)`，然后 `state.copy(lastStatsTickAt = if (seconds > 0L) base + seconds * 1000L else state.lastStatsTickAt, updatedAt = nowMillis)`；`seconds == 0` 时不写库（省掉一次 upsert）。
- 风险/注意：`lastStatsTickAt` 还参与 `shiftReminderRuntime`/`shiftPomodoroRuntime`（`:336-367`，息屏补偿时会被重置为 `nowMillis`），改动后要确认息屏恢复不会因为游标落后而把息屏那段时间也记成工作时长——`adjustForScreenState` 会先整体位移时间轴，需要一起验证；`AlarmReceiver`/`ReminderActionReceiver` 走 `ReminderEngine` 的 delta 路径不受影响。

### [G03-09] `loopStarted` 与 `scope.cancel()` 组合不可恢复：服务实例复用后永不再 tick
- 严重度：P1
- 类别：D 生命周期
- 位置：`core/services/TimerForegroundService.kt:54`、`:97-100`、`:114-118`
- 现状：
  ```kotlin
  @Volatile private var loopStarted = false                    // 只置真，永不复位
  ...
  if (!loopStarted) { loopStarted = true; scope.launch { runTimerLoop() } }
  ...
  if (!settings.keepAliveEnabled || runtime.activeEngine == ActiveEngine.IDLE.name) {
      stopSelf(); scope.cancel(); return@collect               // scope 一旦取消就永久失效
  }
  ```
  `scope` 是服务字段（`:49-53`，`SupervisorJob`），被 `cancel()` 后其上的 `launch` 全部变成空操作。
- 触发场景：`stopSelf()` 只是"请求停止"，`onDestroy` 要等主线程消息队列轮到。若在这个窗口内又来一次 `startForegroundService`（`startTimerService()` 的调用点很多：`AlarmReceiver.kt:82`、`ReminderActionReceiver.kt:96`、`ExactAlarmPermissionReceiver.kt:28`、`TimerReconciliationWorker.kt:24`、`AppLifecycleCoordinator.kt:83`、`ProjectLumenRuntimeFeatureEntry.kt:191`），Android 会把 `onStartCommand` 投给**同一个尚未销毁的实例**：`promote` 成功、返回 `START_STICKY`，但 `loopStarted` 仍为 `true` 且 `scope` 已死。
- 影响：前台服务活着、常驻通知在，但计时永不推进——用户看到进度条冻结、休息永不到点，且因为服务"在跑"，对账 worker 也不会做别的事；只能靠杀进程恢复。
- 修复方案：不要在循环里取消服务自己的 scope。把 `:114-118` 改为只 `stopSelf()` 并 `return@collect`（让 `onDestroy`（`:104-108`）里的 `scope.cancel()` 成为唯一取消点），同时在 `onDestroy` 里把 `loopStarted = false`；或者更稳妥：不复用 `loopStarted` 布尔，改成保存 `Job`（`private var loopJob: Job? = null`），`onStartCommand` 里 `if (loopJob?.isActive != true) loopJob = scope.launch { runTimerLoop() }`，并把服务级 scope 换成 `lifecycleScope`（本类已是 `LifecycleService`，`lifecycleScope` 会随 `onDestroy` 自动取消，比手工 `cancel()` 更难写错）。
- 风险/注意：换成 `lifecycleScope` 会把默认调度器变成 `Dispatchers.Main.immediate`，`runTimerLoop` 里全是 Room/统计的挂起调用与 `NotificationService` 调用，必须显式 `launch(Dispatchers.IO)`，否则会把数据库工作拖回主线程（Room suspend DAO 自己会切线程，但 `notifications.*` 的构建逻辑不会）。另外现有的 `CoroutineExceptionHandler`（`:50-52`）要一并迁移。

### [G03-10] 导入设置时先写 Room 再写 DataStore，进程死在中间会让旧偏好永久覆盖导入结果
- 严重度：P2
- 类别：F 持久化一致性
- 位置：`core/services/DataBackupService.kt:283-284`
- 现状：
  ```kotlin
  database.appSettingsDao().upsert(imported)
  eyeCarePreferences?.saveFromSettings(imported)
  ```
  而读取侧是 **DataStore 覆盖 Room**：`core/repositories/SettingsRepository.kt:23-27` 的 `get()` 返回 `settings.withEyeCarePreferences(persistedPreferences)`。
- 触发场景：两次挂起调用之间进程被杀（低内存、用户强停、导入在后台被回收）。Room 已是导入后的值，DataStore 还是旧值。
- 影响：DataStore 覆盖的那批字段（护眼偏好）会静默回退到导入前的值，且此后每次读取都以旧值为准——用户以为导入成功，实际部分设置没生效，且再导入一次也可能踩同一个窗口。
- 修复方案：交换顺序，先 `eyeCarePreferences?.saveFromSettings(imported)` 再 `database.appSettingsDao().upsert(imported)`（与本仓库"偏好存储先于 Room"的既有纪律一致）；若按 [G03-07] 引入事务，则 DataStore 写入放在事务提交之后、Room 写入之前不可行，届时应改为"先 DataStore，再事务写 Room"。
- 风险/注意：`SettingsRepository.update`（`SettingsRepository.kt:74-75`）存在同样的顺序问题，但那是 G02 组的文件，需协同修，不要在本组顺手改。

### [G03-11] `showUpdateAvailable` 一次发两条几乎相同的更新通知，且通知 id 用魔法偏移绕过 `NotificationIds`
- 严重度：P2
- 类别：A 架构
- 位置：`core/services/NotificationService.kt:210-236`
- 现状：
  ```kotlin
  show(id = NotificationIds.POMODORO + 1000, channel = STATUS, title = about_update_status, message = about_update_found(tagName), ...)
  notificationManager.notify(NotificationIds.POMODORO + 1001, NotificationCompat.Builder(...)
      .setContentTitle(context.getString(R.string.about_update_status))
      .setContentText("$tagName $releaseName") ... )
  ```
  两条通知标题完全一样，正文一个是"发现新版本 {tag}"、一个是"{tag} {releaseName}"。同类魔法偏移还有 `:461` 的 `openAppPendingIntent(id + 100)`、`:467/472` 的 `NotificationIds.BREAK_DUE + 10/11`——`NotificationIds`（`core/constants/NotificationIds.kt`）里并没有这些 id。
- 触发场景：自动更新检查发现新版本时（`autoUpdateCheckEnabled`）。
- 影响：用户通知栏出现两条重复的"发现更新"，只能分别划掉；`POMODORO + 1000/1001 = 3001/3002` 是脱离常量表的裸数字，将来新增通知类型极易撞号（撞号即互相覆盖，表现为"通知莫名消失"）。
- 修复方案：删掉 `:220-235` 这段第二次 `notify`（`show(...)` 已经带 `openAppPendingIntent` 与 `setAutoCancel`，功能完全覆盖），若需要展示 `releaseName` 就把它并入第一条的 message；同时把 `POMODORO + 1000` 提为 `NotificationIds.UPDATE_AVAILABLE`，`BREAK_DUE + 10/11` 提为 `NotificationIds.START_BREAK_ACTION` / `SKIP_BREAK_ACTION`，`id + 100` 的全屏 requestCode 改为独立常量段。
- 风险/注意：`NotificationIds` 是跨模块常量（`core/proximity`、`core/light`、`core/overlay`、`core/debug` 都在用），新增常量不影响既有值；只要不改动已有数字就没有兼容问题。

### [G03-12] `NotificationService` 895 行上帝类：把 AlarmManager 调度策略、渠道、内容构建、Toast(UI) 和 Live Update 去重全揽在一起
- 严重度：P2
- 类别：A 架构
- 位置：`core/services/NotificationService.kt:41-84`（渠道）、`:86-142`（**AlarmManager 调度策略**：`syncRuntimeAlarms` 按 `activeEngine`/`reminderPhase`/安静时段决定排哪些闹钟）、`:317-376`（`showProximityWarning`/`showEyeDryWarning`/`showLowLightWarning` 里直接 `context.showLumenToast(...)`，即在通知类里做 UI）、`:238-315`（五个前台服务的通知构建）、`:550-850`（Live Update 内容与签名去重）
- 现状：一个类同时是"通知门面""闹钟调度器""Toast 展示器""Live Update 渲染器"。`syncRuntimeAlarms` 是真正的运行时调度策略（它决定强制休息能不能在后台触发），却住在名为 Notification 的类里；`TimerForegroundService.refreshRuntimeNotifications`（`TimerForegroundService.kt:269-275`）的注释也不得不解释"这里必须调 syncRuntimeAlarms，因为浮层依赖它"。
- 触发场景：不是运行期缺陷，而是改动风险——任何人调整"通知开关"逻辑（`:112-122` 三段互相纠缠的 `notificationEnabled` / `globalOverlayEnabled` 分支）都可能顺手改掉强制休息的唤醒源，而这一点只靠注释保护，没有测试。
- 影响：可维护性与可测试性差（类硬依赖 `Context`，无法在纯 JVM 单测里验证调度决策）；"通知关了但浮层仍要生效"这条业务规则分散在 `NotificationService.syncRuntimeAlarms`、`AlarmReceiver.kt:69-80`、`TimerForegroundService.kt:251-260` 三处，属于同一事实的多个真相源。
- 修复方案：把 `:86-142`（`scheduleReminder`/`scheduleBreakDone`/`syncRuntimeAlarms`/`cancelAllScheduled`/`schedule`/`scheduledPendingIntent`/`existingPendingIntent`/`canScheduleExactAlarms`）整体抽到新文件 `core/services/RuntimeAlarmScheduler.kt`，让它只依赖 `AlarmManager` + 一个纯函数式的"该排哪些闹钟"决策（决策函数可单测）；`NotificationService` 改为持有它并转发，或让调用方直接依赖新类。三处 `showLumenToast` 从 `NotificationService` 移到各自的调用方（`ProximityDetectionService` / `LightMonitorService`），通知类不再触碰 UI。
- 风险/注意：`syncRuntimeAlarms` 的调用点分散在 `TimerForegroundService`、`AlarmReceiver`、`ReminderActionReceiver`、`BootReceiver`、`ExactAlarmPermissionReceiver`、`ShizukuResilienceWorker`、`TimerReconciliationWorker`、`ProjectLumenRuntimeFeatureEntry` 共 8 处，签名迁移必须一次改全；建议先保留 `NotificationService` 的同名转发方法以缩小 diff。这属于大范围重构，优先级应低于本报告的 P1 项。

### [G03-13] 三个 `NotificationService` 实例并存，Live Update 去重签名状态分裂
- 严重度：P2
- 类别：A 架构
- 位置：`core/services/TimerForegroundService.kt:70`（`notifications = NotificationService(this)`）、`core/services/AlarmReceiver.kt:30`（每次收广播 `NotificationService(context.applicationContext)`）、对照 `ProjectLumenApplication.kt:64`（`val notifications: NotificationService by lazy { ... }`，`ReminderActionReceiver.kt:51/97`、`BootReceiver.kt:64` 用的是这一份）
- 现状：去重状态 `lastPublishedLiveUpdateSignature`（`NotificationService.kt:34`）是**实例字段**，三份实例各有一份；`ongoingContentIntent`/`ongoingStopIntent`/`notificationManager` 也各建一份。
- 触发场景：常态。前台服务用自己那份判重，闹钟广播每次新建一份（签名恒为 null，必然重发），Application 那份又是第三套。
- 影响：`NotificationIds.FOREGROUND_TIMER` 这条常驻通知会被多余地重复 `notify`（每次 `notify` 都是一次 binder + 通知栏刷新，Live Update 芯片可能出现闪动）；"同一事实多个真相源"，将来若在去重逻辑里加更多状态（如节流窗口）会直接失效。
- 修复方案：让 `TimerForegroundService.onCreate` 与 `AlarmReceiver.onReceive` 都改用 `app.notifications`（`TimerForegroundService` 已在 `:69` 拿到 `app`；`AlarmReceiver` 已在 `:27` 拿到 `app`），删掉两处 `NotificationService(...)` 构造。
- 风险/注意：`app.notifications` 用的是 Application `Context`，而 `NotificationService` 内部有 `context.getString(...)`——若 App 内语言切换依赖 Activity 级 `Context` 的 `Configuration`，共用 Application 实例可能让通知文案不跟随应用内语言设置。修复前需确认 `LocaleController` 是否用 `AppCompatDelegate.setApplicationLocales`（那样 Application `Context` 也会跟随，无风险）。

### [G03-14] 所有精确闹钟都用 `setExactAndAllowWhileIdle`，Doze 下相邻闹钟会被节流推迟
- 严重度：P2（需确认产品是否接受）
- 类别：E 韧性
- 位置：`core/services/NotificationService.kt:405-418`
- 现状：
  ```kotlin
  if (canScheduleExactAlarms()) {
      try { alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent); return }
      catch (_: SecurityException) { }
  }
  alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
  ```
  `syncRuntimeAlarms` 在 WORKING 阶段会同时排 `PRE_ALERT` 与 `BREAK_DUE` 两个闹钟（`:132`），两者只相差 `preAlertSeconds`（默认几十秒）。
- 触发场景：设备进入 Doze（灭屏静置）后进入工作阶段。`setExactAndAllowWhileIdle` 每个应用在 Doze 下大约每 9~10 分钟只放行一次，第一个闹钟消耗掉配额后，紧随其后的第二个会被推迟到下一个窗口。
- 影响：预警响了但"该休息了"迟到数分钟，或反之；强制休息浮层同样迟到——对一款"到点必须休息"的产品是端到端体验受损。
- 修复方案：对时间敏感的两个闹钟改用 `AlarmManager.setAlarmClock(AlarmClockInfo(triggerAtMillis, showIntent), pendingIntent)`——它完全不受 Doze 节流限制（代价是系统会在状态栏显示闹钟图标，且需要 `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`，前者已在 manifest 声明）。或者退一步：只给 `BREAK_DUE`（硬约束）用 `setAlarmClock`，`PRE_ALERT` 保持现状。
- 风险/注意：`setAlarmClock` 会让系统显示"下一个闹钟"图标并可能出现在锁屏，是可见的行为变化，需要产品确认；另外 `cancelAllScheduled`（`:398-403`）靠 `FLAG_NO_CREATE` 取回 PendingIntent 再取消，换 API 后取消逻辑不变（仍按 requestCode+Intent 匹配），无需改动。

### [G03-15] Android 14+ 全屏提醒会被静默降级：从不检查 `canUseFullScreenIntent()`
- 严重度：P2
- 类别：D 生命周期
- 位置：`core/services/NotificationService.kt:442-462`（`show(..., fullScreen = true)` → `setFullScreenIntent`），调用方 `:165-175`（`showReminderDue`）、`:317-337`（`showProximityWarning`）、`:339-355`（`showEyeDryWarning`）
- 现状：manifest 声明了 `USE_FULL_SCREEN_INTENT`（`AndroidManifest.xml:22`），代码直接 `builder.setFullScreenIntent(openAppPendingIntent(id + 100), true)`，全仓库 `rg canUseFullScreenIntent` 无命中。
- 触发场景：`targetSdk 37`，在 Android 14+ 设备上全新安装。该权限自 Android 14 起对非"闹钟/通话"类应用**默认不授予**，`NotificationManager.canUseFullScreenIntent()` 返回 false，系统把全屏意图静默降级为普通抬头通知。
- 影响：灭屏/锁屏时"该休息了"不再全屏唤起，只有一条通知，用户很可能错过——而这正是产品的核心提醒场景。（强制休息浮层走的是 `EyeProtectionOverlayService`，不受影响，所以不是完全失效。）
- 修复方案：在 `show()` 里把 `if (fullScreen)` 改为 `if (fullScreen && canUseFullScreenIntents())`，新增私有方法：SDK < 34 返回 true，否则 `context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()`；不可用时退化为 `setPriority(PRIORITY_HIGH)` + `CATEGORY_ALARM`（现状已具备）。同时在设置页/引导页提供跳转 `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`（`Intent(..., Uri.parse("package:$packageName"))`）让用户手动授予（UI 部分属别组，本组只负责让 `NotificationService` 暴露 `canUseFullScreenIntents()` 供 UI 查询）。
- 风险/注意：仅新增判断，不改变已授权设备的行为；`Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` 是 API 34 常量，引用处需 SDK 判断。

### [G03-16] 备份 JSON 明文包含 `purchaseToken` 与全量健康统计，经系统分享面板交给任意应用
- 严重度：P2
- 类别：G 安全
- 位置：`core/services/DataBackupService.kt:532-542`（`EntitlementEntity.toJson` 写出 `purchaseToken`）、`:40-51`（`shareBackup` 明文落缓存并 `ACTION_SEND`）
- 现状：备份 JSON 未加密，内含 `purchaseToken`、`rawPayloadJson`、逐日健康统计（工作/休息时长、久坐、干眼与低光告警次数），通过 `SecureShareIntents.shareStream` 交给用户在 chooser 里选中的任意应用。
- 触发场景：用户点"导出备份"并选择一个第三方应用（云盘、聊天、笔记）。
- 影响：购买凭据外泄可能被用于权益重放/伪造激活；逐日健康数据属敏感个人信息，落到第三方应用的沙箱后不可回收。文件本身写在 `context.cacheDir`（内部存储，`file_paths.xml` 只暴露 `cache-path`，URI 授权是逐次的），所以**不存在被任意应用直接读取**的问题——风险仅在用户主动分享这一步。
- 修复方案：从备份中剔除 `purchaseToken` 与 `rawPayloadJson`（导入侧 `toEntitlement()` 的 `optString("purchaseToken", "")` 已能容忍字段缺失，权益本应由服务端校验重建，不需要随备份走）；若必须保留，则用 `SecureCredentialStore` 派生的密钥对备份体加密，并在分享前的确认弹窗里明示"备份包含健康数据"。
- 风险/注意：剔除字段后，"导出备份 → 换机导入"将不再自动恢复付费权益，需要确认是否有服务端恢复通道（`ProjectLumenRemoteFeatureEntry` 有远端备份路径，可能依赖这些字段，改前需与该组核对）。

## 已核查但无问题的点

- **`ForegroundServiceController` 确实是唯一提权入口**：`ContextCompat.startForegroundService` / `ServiceCompat.startForeground` 全仓库只出现在该文件（`rg` 确认），并由 `ForegroundServiceArchitectureTest`（源码文本型断言）双向保护——既要求 5 个前台服务源文件出现 `ForegroundServiceController.promote(` 字面量，也禁止其他文件出现 AndroidX 的两个调用。**任何重构都必须保留这两个字面量**，否则单测直接红。
- **`startForegroundService` → `startForeground` 的 5 秒窗口是安全的**：`TimerForegroundService.onStartCommand`（`:81-102`）同步调用 `promote`，失败即 `stopSelf(startId)` + `START_NOT_STICKY`，这是官方推荐的规避 `ForegroundServiceDidNotStartInTimeException` 的写法。
- **Android 14+ 服务类型与权限自洽**：`TimerForegroundService` 用 `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`，manifest 有 `foregroundServiceType="specialUse"` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` 说明 + `FOREGROUND_SERVICE_SPECIAL_USE` 权限（`AndroidManifest.xml:24,148-154`）；SDK < 34 传 0 也正确。
- **精确闹钟权限被撤销不会崩**：`canScheduleExactAlarms()`（`NotificationService.kt:160-163`）先判断，`setExactAndAllowWhileIdle` 外还套了 `catch (_: SecurityException)` 处理"检查与调用之间权限变化"的竞态，最终降级到 `setAndAllowWhileIdle`；`ExactAlarmPermissionReceiver` 还在权限状态变化时重排。这条降级链完整，不要在修 [G03-14] 时破坏它。
- **`PendingIntent` flag 正确**：所有 `getBroadcast`/`getActivity` 都带 `FLAG_IMMUTABLE`（`:425,434,538,857`），满足 Android 12+ 强制要求；`existingPendingIntent` 用 `FLAG_NO_CREATE` 探测存在性再取消，语义正确。
- **通知 id 与闹钟 requestCode 无实质冲突**：`NotificationIds` 的 14 个常量互不相同；`openAppPendingIntent` 的几个偏移（如 `PROXIMITY_WARNING+100 = 9202` 与 `LOW_LIGHT_FOREGROUND` 的 `9202`）虽然 requestCode 撞号，但它们的目标 Intent 完全相同（都是 `openAppIntent()`），复用同一个 PendingIntent 不改变行为。（可读性问题已并入 [G03-11]。）
- **四个 BroadcastReceiver 都正确使用 `goAsync()`**：`AlarmReceiver:25`、`ReminderActionReceiver:22`、`BootReceiver:22`、`ExactAlarmPermissionReceiver:19`，且都在协程末尾 `pendingResult.finish()`、都用 `runCatching{}.onFailure{ recordHandledFailure }` 兜住异常，不会因为异常而漏掉 `finish()`。
- **`POST_NOTIFICATIONS` 未授权时不会崩**：`canPostNotifications()`（`:527-531`）前置判断 + 三处 `notify` 都包了 `catch (_: SecurityException)`。（代价是静默失败、用户无感知，但这属于产品取舍，不作为缺陷上报。）
- **导出文件位置安全**：CSV/PNG/PDF/备份都写在 `context.cacheDir`（内部存储），`res/xml/file_paths.xml` 只声明 `cache-path` 与 `external-cache-path`，实际使用的是前者；`SecureShareIntents` 同时设置 `ClipData` 与 `FLAG_GRANT_READ_URI_PERMISSION`，是 Android 14+ 的正确写法，没有把健康数据写到外部可读目录。
- **`ExportService` 的流与 `PdfDocument` 关闭正确**：`FileOutputStream(...).use { }`（`:38,58`）+ `document.close()` 放在 `finally`（`:42-44`）。
- **`QuietHours` 的时间边界正确**：`isActive`（`:16-27`）用 `start < end` / 否则 `current >= start || current < end` 正确处理跨午夜；`localMinuteOfDay`（`:67-73`）按目标时刻取该时刻的时区偏移，DST 切换与非整小时时区都成立；`activeBoundary`（`:51-65`）对"当前在午夜前"与"午夜后"分别取 `date` / `date.minusDays(1)`，与 `recordIncrementalEyeStats` 里 `activeStartMillis(...).coerceAtMost(nowMillis)` 的用法一致。
- **`AuraAudioService` 无需释放 MediaPlayer / 音频焦点**：它不播放音频，只向外部应用 `com.chloemlla.aura` 发显式广播（`setPackage` + 固定 action），失败被 `runCatching` 吞掉；manifest 声明了 `QUERY_ALL_PACKAGES`（`:36`），所以 Android 11+ 的包可见性过滤不会让 `isAuraInstalled` 误判为未安装。
- **`TimerReconciliationWorker` / `ShizukuResilienceWorker` 的自续期链是合理的看门狗**：`enqueueUniqueWork(..., REPLACE)` 让每次 `startTimerService()` 都把 15 分钟对账窗口往后推，等价于"心跳复位"；`ShizukuResilienceWorker` 在开关关闭时会 `cancel` 自己（`:24-27`），有退出条件，不是失控循环。
- **`TimerForegroundService` 的息屏 receiver 与 scope 生命周期**：`registerReceiver` 在 API 33+ 带 `RECEIVER_NOT_EXPORTED`（`:284-289`），`onDestroy` 先 `unregisterScreenReceiver()`（幂等、包了 `runCatching`）再 `scope.cancel()`；`loopStarted` 有 `@Volatile`。（可恢复性问题另见 [G03-09]。）
- **`tickingFlow` 用单调时钟做节拍**：`:141-152` 以 `SystemClock.elapsedRealtime()` 累加 `nextTickAt` 并在落后超过 1 秒时重新对齐，不受用户改系统时间影响；只有记账用的是 wall clock（见 [G03-08]）。
