# G01 应用装配 / 状态管线 / 运行时引擎 审查报告

- 审查文件数：11，总行数：2677
- 结论摘要：这一组的骨架方向是对的（单向状态流、引擎纯函数化、lambda 注入确实避免了 Activity 泄漏），但**装配顺序与运行时驱动这两处存在系统性缺陷**。最严重的是 `DeviceSecurityGate` 的启动扫描是异步的、而 `isServiceAllowed()` 是同步查询且初值为 `UNKNOWN`，导致冷启动阶段所有前台服务/传感监测启动请求被静默拒绝且没有任何重试通道——保活、光照监测、距离监测在每次冷启动后实际不生效（P0）。其次是"一个 1 Hz 墙钟 + 一个上帝状态对象"这条主驱动链：`nowMillis` 被塞进 `ProjectLumenUiState`，使整棵 Compose 树每秒重组；时钟循环 `while(true)` 无异常隔离，任何一次 tick 抛异常就永久停摆；所有时长计算基于 `System.currentTimeMillis()` 且无跳变上限。再者，`ReminderEngine` 的 `AWAITING_ACTION` 是一个真正的自锁态（引擎无该分支、闹钟对过期时间不排程），用户忽略一次提醒后提醒功能会永久停止。服务命令 lambda 全是 `() -> Unit`，失败一律静默丢弃，用户与 ViewModel 都拿不到任何反馈。

## 缺陷清单

### [G01-01] 设备安全门禁未就绪即被同步查询，冷启动期间所有服务启动被静默拒绝且永不重试
- 严重度：P0
- 类别：A 架构 / E 韧性
- 位置：`app/src/main/java/com/projectlumen/app/ProjectLumenApplication.kt:157`、`:342`、`:377`、`:390`、`:399`、`:412`（配套证据：`core/security/DeviceSecurityGate.kt:33`、`:53-66`、`:69`）
- 现状：
  ```kotlin
  // ProjectLumenApplication.onCreate
  deviceSecurityGate.startStartupScan(applicationScope)   // 异步，Dispatchers.Default
  // ...
  fun startTimerService() {
      if (!deviceSecurityGate.isServiceAllowed()) {        // == State.ALLOWED
          Log.w(TAG, "Timer service refused by device security gate"); return
      }
      TimerReconciliationWorker.enqueue(this)              // 连兜底对账也一起跳过
      ForegroundServiceController.start(...)
  }
  ```
  `DeviceSecurityGate._state` 初值是 `State.UNKNOWN`，`isServiceAllowed()` 只在 `ALLOWED` 时为真。扫描 `scanner.fullScan()` 打开了 `includeHardware = true` + `includeDuckFeatures = true`（含 TEE 证明），耗时不可控。全仓库没有任何地方 `collect` `deviceSecurityGate.state`，门禁翻成 `ALLOWED` 之后不会补做任何事情。
- 触发场景：每次冷启动。`ProjectLumenViewModel.init` 的启动协程（`settingsEntry.applyStartupMonitoring` → `startLightMonitoring` / `scheduleProximityMonitoring` / `startDeveloperDebugService` / `startShizukuResilience`，以及 `runtimeEntry.refreshActiveNotifications` → `startTimerService`）与 `AppLifecycleCoordinator.onStart`（`AppLifecycleCoordinator.kt:63-85` 同样的五个启动调用）都在扫描完成之前跑完。验证方法：logcat 比对 `ProjectLumenApp: Timer service refused by device security gate` 与 `DeviceSecurityGate: Startup device security state=ALLOWED` 两条日志的时间戳先后。
- 影响：冷启动后前台计时服务不启动（保活失效、`TimerReconciliationWorker` 对账网也没排），环境光监测与距离/眨眼监测完全不工作，开发者调试悬浮窗与 Shizuku 韧性 worker 也不启动。用户必须切后台再回前台、或随便改一项设置，才会让这些服务真正起来。全过程只有一条 `Log.w`，UI 无任何提示。
- 修复方案：把"门禁未决"和"门禁拒绝"区分开。在 `DeviceSecurityGate` 增加 `suspend fun awaitDecision(): State`（`state.first { it == ALLOWED || it == BLOCKED }`），并在 `ProjectLumenApplication` 把五个 `start*` 系列函数改为挂起等待或"待决即入队、扫描落地后统一冲刷"：新增一个 `pendingServiceStarts: MutableSet<ServiceKind>`，`isServiceAllowed()` 为 `UNKNOWN` 时记录意图并 `return`，同时在 `onCreate` 里 `applicationScope.launch { deviceSecurityGate.state.first { it != UNKNOWN && it != SCANNING }; flushPendingServiceStarts() }`。注意 `TimerReconciliationWorker.enqueue(this)` 应移到门禁检查之前——它只是 WorkManager 对账，不涉及前台服务限制，本来就该无条件排。
- 风险/注意：冲刷时进程可能已经进入后台，`ForegroundServiceController.start` 会因 `process_not_foreground` 再次拒绝（这是正确行为，不要为此放宽）。不要改成"UNKNOWN 视为放行"来图省事——那会让 `ForegroundServiceController.kt:49` 的同一门禁在 root 设备上短暂失效。

### [G01-02] 服务命令 lambda 全是 `() -> Unit`，启动失败静默丢弃，UI 与状态机继续假装服务在跑
- 严重度：P1
- 类别：A 架构 / E 韧性
- 位置：`app/src/main/java/com/projectlumen/app/ProjectLumenApplication.kt:341-418`、`app/src/main/java/com/projectlumen/app/MainActivity.kt:119-131`
- 现状：
  ```kotlin
  fun startTimerService() { ...; ForegroundServiceController.start(context = this, intent = ...) }  // 返回值 Boolean 被丢弃
  fun startLightMonitoring() { if (!deviceSecurityGate.isServiceAllowed()) return; LightMonitorService.start(this) }
  ```
  `ForegroundServiceController.start` 明确返回 `Boolean`（拒绝原因还分 `device_security_blocked` / `process_not_foreground` / 系统抛异常三种），但 `startTimerService` 丢弃返回值；注入给 ViewModel 的签名也是 `startTimerService: () -> Unit`，从类型上就断掉了反馈通道。失败只走 `ForegroundServiceFailureReporter.recordForegroundServiceFailure` → `recordHandledFailure`（写崩溃报告文件），不进 `ProjectLumenUiState`。
- 触发场景：Android 12+ 后台启动前台服务被拒；门禁为 `UNKNOWN`/`BLOCKED`；用户在系统设置里关掉了通知或电池优化把服务掐掉。
- 影响：`RuntimeStateEntity.activeEngine` 仍是 `REMINDER`/`POMODORO`，主页倒计时照常走，但真实的前台服务不存在——应用被系统回收后不再提醒，用户看到的"运行中"是假的，且没有任何可操作的错误提示。
- 修复方案：把这批 lambda 的类型从 `() -> Unit` 改成返回结果（如 `() -> ServiceCommandResult`，含 `Started / RefusedNotForeground / RefusedBySecurityGate / Failed(throwable)`），`ProjectLumenApplication` 里如实返回 `ForegroundServiceController.start` 的结果；`ProjectLumenRuntimeFeatureEntry.refreshActiveNotifications` 收到非 `Started` 时写入一个新的 UI 字段（例如 `ProjectLumenUiState.keepAliveDegraded: Boolean`），由首页横幅提示"保活未生效"。
- 风险/注意：改签名要同步 `MainActivity.kt:119-131` 的全部方法引用、`ProjectLumenViewModel` 构造参数、`ProjectLumenRuntimeFeatureEntry` 与 `ProjectLumenSettingsFeatureEntry` 的构造参数（两处都持有 `stopTimerService` 等命名参数）。若想小步走，可先只改 `startTimerService` 一条链路。

### [G01-03] `nowMillis` 塞进上帝状态对象，导致整棵 Compose 树每秒重组一次
- 严重度：P1
- 类别：A 架构 / D 生命周期
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenUiState.kt:24`、`app/src/main/java/com/projectlumen/app/app/ProjectLumenStateStore.kt:91-97`、`app/src/main/java/com/projectlumen/app/app/ProjectLumenApp.kt:141`
- 现状：
  ```kotlin
  val uiState = combine(dataState, now, crashReport) { state, nowMillis, report ->
      state.copy(nowMillis = nowMillis, crashReport = report)
  }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), ProjectLumenUiState())
  ```
  `now` 由 `ProjectLumenRuntimeFeatureEntry.startClock` 每 1000 ms 写一次，因此 `uiState` 每秒必然产出一个新的 `ProjectLumenUiState` 实例。`ProjectLumenApp.kt:141` 在最顶层 `collectAsStateWithLifecycle()` 读它，并把整个 `uiState` 当参数传给 `HomeScreen`/`BreakScreen`/`PomodoroScreen`/`StatisticsScreen`/`SettingsScreen`/`TemplatesScreen`/`DeveloperDebugScreen`。
- 触发场景：应用在前台的每一秒，无论是否有计时在跑（`startClock` 不看 `activeEngine`）。
- 影响：`Scaffold`、`NavigationBar`（含每个 item 的 `animateFloatAsState` 与 `AnimatedContent`）、`NavHost` 与当前屏幕整棵子树每秒重新执行一次组合，持续占用 CPU、在中低端机上表现为常态掉帧与耗电偏高。
- 修复方案：把时间从状态对象里拆出来。`ProjectLumenStateStore` 去掉 `combine(..., now, ...)`，`uiState` 只由 `dataState + crashReport` 合成；另外暴露 `val nowMillis: StateFlow<Long> = now`，由 `ProjectLumenViewModel` 透出。需要倒计时的叶子组件（`TimerCard`、`ProjectLumenMainScreens.kt:590` 的进度计算、`ProjectLumenUiFormatters.remainingSeconds` 的调用点）改为各自 `collectAsStateWithLifecycle()` 或接收 `nowProvider: () -> Long` 并在内部 `derivedStateOf`。同时给 `startClock` 加"仅在 `activeEngine != IDLE` 时以 1 Hz 走，空闲时降频或挂起"。
- 风险/注意：`ProjectLumenUiState.nowMillis` 的读取点分散在多个屏幕文件（`ProjectLumenMainScreens.kt`、`ProjectLumenStatsAndTimerCards.kt`、`ProjectLumenApp.kt:149-159` 的 `autoDarkActive`），属于别组文件，必须一次性全部改完否则编译不过。`ProjectLumenApp` 里那个 `remember(uiState.nowMillis / 60_000L, ...)` 的 auto-dark 判定只需分钟级精度，改成读 `nowMillis` StateFlow 后要保留分钟级 key，别退化成每秒重算。

### [G01-04] ViewModel 构造函数在主线程做 Keystore / EncryptedSharedPreferences 同步 IO
- 严重度：P1
- 类别：B 并发 / D 生命周期
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenViewModel.kt:80-95`、`:96-102`，构造点在 `app/src/main/java/com/projectlumen/app/MainActivity.kt:100-138`
- 现状：
  ```kotlin
  private val installProfile = runCatching { secureCredentials.installProfile() } ...
  private val deviceFingerprint = runCatching { secureCredentials.deviceInstallationId() } ...
  private val firstOpenGateEntry = ProjectLumenFirstOpenGateEntry(...)   // init 里同步 resolve
  ```
  这三个属性初始化器在 `MainActivity.onCreate` 的主线程上执行。`installProfile()` → `encryptedMmkv` (by lazy) → `mmkvCryptKey()` → `secureMetadata`，即 `MasterKey.Builder(...).build()` + `EncryptedSharedPreferences.create(...)`，首启还会走 `secureMetadata.edit().putString(...).commit()`（同步落盘）。`deviceInstallationId()` 首启还会 `Settings.Secure.getString` + 写两个 MMKV 键。
- 触发场景：每次冷启动都会走 Keystore 取/建 AES256_GCM 主密钥（涉及 keystore binder + TEE），首次安装还多一次同步 `commit()`。
- 影响：主线程阻塞叠加在冷启动关键路径上（`Application.onCreate` 已有 G01-13 的开销），低端机与首次安装场景下首帧明显延后，极端情况可能触发 ANR。
- 修复方案：`ProjectLumenViewModel` 里把这两个值改为可空的 `MutableStateFlow`，在 `init` 已有的 `reportingScope.launch { ... }`（`:224`）内先读出来再喂给 `ProjectLumenFirstOpenGateEntry`；或把 `ProjectLumenFirstOpenGateEntry` 的构造参数从 `initialInstallProfile: DeviceInstallProfile` 改为 `suspend () -> DeviceInstallProfile`，由它自己在协程里首次 `refresh` 时拉取。`ProjectLumenFirstOpenGateEntry.init { applyAutomaticGate() }` 需要相应改为"数据到齐后再 apply"。
- 风险/注意：首开门禁（开源声明 / 引导 / 更新说明）的显示时序会从"首帧即定"变成"稍后一帧才定"，要确认 `ProjectLumenAutomaticFirstOpenGateHost` 在 `installProfile` 未就绪时不会先闪一下主界面——建议给 `ProjectLumenOssNoticeState` 增加"未决"初值，未决时先渲染空白 Surface。

### [G01-05] 1 Hz 时钟循环无异常隔离、无退出条件、后台不停；`runCatching` 包住 `launch` 是假的错误处理
- 严重度：P1
- 类别：B 并发 / E 韧性
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenViewModel.kt:78`、`:242-243`（循环体在 `app/app/ProjectLumenRuntimeFeatureEntry.kt:35-44`，修复需同时改动）
- 现状：
  ```kotlin
  runCatching { runtimeEntry.startClock(now) }.onFailure { Log.e(TAG, "startClock failed", it) }
  // startClock:
  scope.launch { while (true) { now.value = System.currentTimeMillis(); advanceDuePhases(current); delay(1_000) } }
  ```
  `startClock` 只做一次 `scope.launch` 就返回，`runCatching` 永远捕获不到循环内部的异常——这层保护是纯装饰。循环体内 `advanceDuePhases` 会做 `settingsRepository.get()`（Room 查询）、`runtimeRepository.get()`（MMKV，初始化失败时抛 `IllegalStateException`）、`statisticsRepository.applyEyeDelta`（Room 写）、`notifications.syncRuntimeAlarms`（AlarmManager）、`audio.playReminderTone`。任一处抛异常，异常冒泡出 `while(true)`，协程终止，交给 `crashReportingHandler`，此后**时钟永不重启**。
- 触发场景：(1) MMKV 初始化失败（见 G01-14）→ 第一次 tick 就死；(2) `SQLiteFullException` / 数据库被锁 / 磁盘满；(3) MediaPlayer/SoundPool 在某些 OEM 上抛异常；(4) 无异常时的另一面：应用切后台后循环照常以 1 Hz 执行一次 SQLite 查询，直到 Activity 的 ViewModel 被清除。
- 影响：时钟一死，`uiState.nowMillis` 冻结、倒计时不动、相位不再推进（只剩 AlarmManager 兜底），且同时弹出全屏崩溃报告页（见 G01-11）；正常情况下则是后台常驻 1 Hz 磁盘查询，白耗电。
- 修复方案：`ProjectLumenRuntimeFeatureEntry.startClock` 的循环体包一层 `try { advanceDuePhases(current) } catch (c: CancellationException) { throw c } catch (t: Throwable) { onTickFailure(t) }`，保证单次 tick 失败不终止循环；连续失败达到阈值再上报一次。同时把驱动条件收紧：`while (isActive)` 而非 `while (true)`，且仅当 `runtimeRepository.get()?.activeEngine != IDLE` 时按 1 Hz 走，空闲时用更长间隔（如 10 s）只更新 `now`。删掉 `ProjectLumenViewModel.kt:242` 那层无意义的 `runCatching`，改为直接调用。
- 风险/注意：降频后 `uiState.nowMillis` 的更新频率变化会影响所有依赖它做秒级显示的地方（与 G01-03 的修复应一起做）。`CancellationException` 必须原样抛出，否则 ViewModel 清除时协程无法结束。

### [G01-06] `AWAITING_ACTION` 是自锁状态：引擎无该分支、闹钟不对过期时间排程，用户忽略一次提醒后提醒功能永久停止
- 严重度：P1
- 类别：A 架构
- 位置：`app/src/main/java/com/projectlumen/app/core/runtime/ReminderEngine.kt:102-141`（`advance` 的 `when` 无 `AWAITING_ACTION` 分支，落到 `else -> null`）、`:253-258`（进入该相位处）
- 现状：`settings.askBeforeBreak` 为真时，休息到点后 `dueReminderTransition` 把相位置为 `AWAITING_ACTION` 并把 `lastStatsTickAt = nowMillis`。此后 `advance()` 对 `AWAITING_ACTION` 返回 `null`——引擎不会自己离开这个状态。而 `NotificationService.syncRuntimeAlarms:132` 对 `AWAITING_ACTION` 调 `scheduleReminder(state.nextPreAlertAt, state.nextReminderAt)`，这两个时间点此刻都已过去，`scheduleReminder:87/90` 的 `> System.currentTimeMillis()` 判断使**一个闹钟都不排**。
- 触发场景：开启"休息前询问"（`askBeforeBreak`）后，用户没看到/忽略了那条通知，然后把应用切到后台。`AppLifecycleCoordinator.onStop` 在非保活模式下还会 `cancelAllScheduled()` + `stopTimerService()`，此时唤醒源为零。
- 影响：应用永久停在"等待操作"，不再有任何提醒（核心功能端到端失效），直到用户主动进入应用点"开始休息"或"跳过"。另外这段等待时间全部堆在 `lastStatsTickAt` 上，用户最终动作时一次性计入"用眼时长"，可能是几小时。
- 修复方案：在 `ReminderEngine.advance` 的 `when` 里补 `AWAITING_ACTION` 分支：超过一个可配置的等待上限（建议复用 `settings.preAlertSeconds` 或新增 `awaitingActionTimeoutSeconds`，默认 5 分钟）后，视作"用户未响应"，走与 `askBeforeBreak = false` 相同的路径直接进入 `RESTING`（并按 `elapsedWorkingSeconds` 正常记账）；同时在进入 `AWAITING_ACTION` 时把 `nextReminderAt` 推到 `nowMillis + timeout`，使 `syncRuntimeAlarms` 能排出下一个闹钟。
- 风险/注意：`nextReminderAt` 同时被 `ProjectLumenMainScreens.kt:590` 用来画 `AWAITING_ACTION` 的进度条、被 `TimerForegroundService.kt:195` 与 `AlarmReceiver.kt:45` 用作判定，改语义要同步核对这三处。超时自动进入休息是行为变化，需要产品确认（也可以只做"重复提醒"不自动进入休息）。

### [G01-07] `skipBreak` 把已经休息掉的时间记成用眼时长，且完全不记休息时长
- 严重度：P1
- 类别：F 持久化一致性
- 位置：`app/src/main/java/com/projectlumen/app/core/runtime/ReminderEngine.kt:66-80`（配合 `:288-300`）
- 现状：
  ```kotlin
  fun skipBreak(settings, state, nowMillis) = RuntimeTransition(
      nextRuntime = newWorkingState(settings, nowMillis),
      eyeStatsDelta = EyeStatsDelta(
          workingSeconds = elapsedWorkingSeconds(state, nowMillis),        // max(reminderStartedAt, lastStatsTickAt)
          skipCount = 1,
          maxContinuousWorkSeconds = continuousWorkingSeconds(state, nowMillis), // nowMillis - reminderStartedAt
      ),
  )
  ```
  进入 `RESTING` 时 `lastStatsTickAt` 被设为休息开始时刻（`:52`、`:257`、`:264`）。因此在 `RESTING` 相位调用 `skipBreak` 时，`elapsedWorkingSeconds` = `now - 休息开始` = **已休息的秒数**，被记为 `workingSeconds`；`restSeconds` 为 0；`maxContinuousWorkSeconds` 则把整段休息一并算进"连续用眼"。
- 触发场景：`RESTING` 明确在可跳过相位集合内（`ProjectLumenMainScreens.kt:207-211` 的 `BreakSkippablePhases` 含 `RESTING`），休息界面会显示"跳过休息"按钮；通知栏的跳过动作（`core/services/ReminderActionReceiver.kt:44`）走同一个引擎方法。用户休息了 15 秒后点跳过即触发。
- 影响：统计数据被污染——已休息时长计入用眼时长、休息时长丢失、最长连续用眼被虚高。这些字段直接驱动首页健康评估、日目标进度与遥测上报。
- 修复方案：`ReminderEngine.skipBreak` 按来源相位分流：若 `state.reminderPhase == ReminderPhase.RESTING.name`，`eyeStatsDelta` 应为 `EyeStatsDelta(restSeconds = elapsedRestSeconds(state, nowMillis), skipCount = 1)`（不记 `workingSeconds`，也不用 `continuousWorkingSeconds`）；仅当来源相位是 `WORKING`/`PRE_ALERT`/`AWAITING_ACTION` 时才按现在的逻辑记 `workingSeconds` 与 `maxContinuousWorkSeconds`。
- 风险/注意：`ReminderActionReceiver.kt:44` 与前台服务共用该方法，修完两条路径行为一致，无需分别改。若已有单测覆盖 `skipBreak`，期望值需要同步更新。

### [G01-08] 所有时长计算基于 `System.currentTimeMillis()` 且无单次跳变上限，改系统时间会写入巨量用眼秒数
- 严重度：P1
- 类别：F 持久化一致性 / A 架构
- 位置：`app/src/main/java/com/projectlumen/app/core/runtime/ReminderEngine.kt:288-300`、`app/src/main/java/com/projectlumen/app/core/runtime/PomodoroEngine.kt:61`、`:82`（时间源在 `ProjectLumenRuntimeFeatureEntry.kt:38`；`coerceElapsedSecondsSince` 定义在 `core/time/DateKeys.kt:13-16`）
- 现状：
  ```kotlin
  fun Long.coerceElapsedSecondsSince(startMillis: Long): Long {
      if (this <= 0L || startMillis <= 0L || this <= startMillis) return 0L
      return (this - startMillis) / 1000L   // 只有下界保护，没有上界
  }
  ```
  时钟以 1 Hz 推进，因此单次 tick 的合理增量最多是数秒；但 `elapsedWorkingSeconds` / `continuousWorkingSeconds` 直接把两个墙钟时间戳相减，没有任何跳变检测。`PomodoroEngine` 的 `totalFocusSeconds` / `totalBreakSeconds` 同样如此。
- 触发场景：用户手动把系统时间往前调（调时区不影响 epoch，但手动改日期会）；运营商 NITZ / NTP 校时产生大跳变；设备长时间断电后 RTC 复位再同步。
- 影响：一次 tick 就把跳变量（可达数天）当作用眼时长写进当天的 `DailyEyeStatsEntity`，统计与日目标永久污染（无回滚路径）；同时 `nowMillis >= state.nextReminderAt` 立即成立，提醒/休息被瞬间连环触发。
- 修复方案：在引擎入口做跳变钳制。给 `coerceElapsedSecondsSince` 增加上限参数（如 `maxSeconds`），`ReminderEngine.elapsedWorkingSeconds` / `elapsedRestSeconds` / `continuousWorkingSeconds` 与 `PomodoroEngine` 的两处时长按"相位计划时长 + 少量宽容"取 `coerceAtMost`；更彻底的做法是在 `RuntimeStateEntity` 增加一个 `elapsedRealtimeAnchor`（`SystemClock.elapsedRealtime()`）字段，时长增量用 realtime 差值算，墙钟只用于闹钟与日期归档。
- 风险/注意：**不要把整套时间基准换成 `elapsedRealtime`**——`nextReminderAt` / `breakEndAt` / `pomodoroPhaseEndAt` 会被 `NotificationService.schedule` 传给 `AlarmManager.RTC_WAKEUP`（`NotificationService.kt:411`），必须保持墙钟。改动会影响 `TimerForegroundService.kt:178-211` 同一套计算，需同步（属别组文件，建议同一次修复统一处理）。

### [G01-09] 前后台切换与时钟对同一 `RuntimeStateEntity` 做无锁 get→copy→upsert，丢更新会把相位回滚
- 严重度：P1
- 类别：B 并发
- 位置：`app/src/main/java/com/projectlumen/app/core/lifecycle/AppLifecycleCoordinator.kt:39-86`（onStart）、`:91-120`（onStop）
- 现状：
  ```kotlin
  override fun onStop(owner: LifecycleOwner) {
      app.backendConnectivity.onBackground()
      scope.launch {                                   // Dispatchers.IO，无互斥
          val runtime = runtimeRepository.getOrDefault()          // 快照
          ...
          runtimeRepository.upsert(runtime.copy(lastBackgroundAt = nowMillis, updatedAt = nowMillis))  // 整体覆盖
  ```
  `RuntimeRepository` 的实际存储是进程内单例 `RuntimeStateMmkvStore`（`core/repositories/RuntimeRepository.kt:37-104`），`upsert` 直接整块覆盖 JSON，没有任何 `Mutex`。同一时刻的写者至少有：本协调器 onStart/onStop、ViewModel 的 1 Hz 时钟（`advanceDuePhases` → `applyTransition` → `upsert`）、`ProjectLumenSettingsFeatureEntry`、以及前台/传感服务。
- 触发场景：应用切后台的瞬间恰好有一次相位转移落地。onStop 的快照取在转移之前、写入在转移之后，就会把新相位覆盖回旧相位。锁屏/亮屏反复切换会放大这个窗口，且每次 `onStart` / `onStop` 都无条件新开一个协程，没有幂等保护或去抖。
- 影响：刚进入的 `RESTING` 被覆盖回 `WORKING`（而 onStop 紧接着 `cancelAllScheduled()`，这次休息被整段吞掉）；反向的丢更新会让 `lastBackgroundAt` 不落盘，导致下次 `resumeAfterBackgroundPause` 的暂停补偿算错。
- 修复方案：把"读-改-写"收敛成一个原子操作。在 `RuntimeRepository` 增加 `suspend fun mutate(transform: (RuntimeStateEntity) -> RuntimeStateEntity): RuntimeStateEntity`，内部用 `Mutex`（挂在 `RuntimeStateMmkvStore` 这个单例上）包住 `get` + `transform` + `upsert`；`AppLifecycleCoordinator.onStart/onStop` 改为 `runtimeRepository.mutate { it.copy(lastBackgroundAt = ..., updatedAt = ...) }`，`onStart` 的 `resumeAfterBackgroundPause` 也放进同一个 `mutate`。另外给协调器加 `@Volatile private var foregroundJob: Job?`，`onStart` 时先取消上一个未完成的 job，保证连续切换不叠加。
- 风险/注意：`RuntimeRepository.mutate` 属于别组文件，但本组是主要调用方，修复需一起提交。加锁后要确认 `ProjectLumenRuntimeFeatureEntry.applyTransition` 不会在持锁期间再调用会重入 `runtimeRepository` 的代码（`refreshActiveNotifications` 只读参数，安全）。

### [G01-10] 设置的乐观预览没有失效与回滚机制，写库失败后 UI 与引擎永久分歧且无提示
- 严重度：P1
- 类别：A 架构 / F 持久化一致性
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenViewModel.kt:483-490`、`app/src/main/java/com/projectlumen/app/app/ProjectLumenStateStore.kt:22`、`:99-101`、`:111-118`
- 现状：
  ```kotlin
  private fun resolveSettings(persisted: AppSettingsEntity?, preview: AppSettingsEntity?): AppSettingsEntity? {
      ...
      return if (preview.updatedAt > persisted.updatedAt) preview else persisted
  }
  ```
  `settingsPreview` 是一个永不清空的 `MutableStateFlow`。正常路径下写库用的是同一个 `nowMillis`，落库后 `persisted.updatedAt == preview.updatedAt`，预览自然失效——这一点设计是对的。但一旦 `ProjectLumenSettingsFeatureEntry.updateSettings`（`:47-73`）的协程抛异常（`settingsRepository.update` 里的 `dao.upsert` 失败），`persisted.updatedAt` 永远追不上，预览会**永久**胜出。
- 触发场景：`SQLiteFullException`（磁盘满）、数据库文件损坏/被锁、DataStore 写失败。另一个次生场景：`uiState.isReady == false` 时用户就能点开关，此时预览是基于默认 `AppSettingsEntity()` 计算的（`previewSettings` 的基线取自 `stateStore.uiState.value.settings`，而写库的基线取自 `settingsRepository.getOrDefault()`），两者基线不同，在数据库往返期间界面会闪一下"其它设置全变默认值"。
- 影响：开关看起来已经打开，但 `ProjectLumenRuntimeFeatureEntry` / `AppLifecycleCoordinator` 读的是数据库真值，引擎仍按旧设置运行。用户会遇到"我明明开了提醒但它不提醒"，且没有任何错误提示——这就是典型的同一事实两个真相源。
- 修复方案：给预览加确定的生命周期。`ProjectLumenStateStore.previewSettings` 接受一个令牌（如 `updatedAt`），新增 `fun clearPreview(updatedAt: Long)`；`ProjectLumenSettingsFeatureEntry.updateSettings` 与各 `set*Enabled` 在 `try/finally` 里，无论成功失败都回调清除对应预览，失败时额外把错误写进 UI 状态（可复用 G01-02 引入的降级字段）。次生场景则在 `isReady == false` 时禁用设置类交互，或在 `previewSettings` 里遇到 `!isReady` 时直接跳过预览。
- 风险/注意：清除预览后，数据库往返期间开关会短暂回弹到旧值；若要避免回弹，就必须保证清除发生在 Room 新值到达之后（可以用"落库成功后清除"而不是 `finally` 清除，只在失败分支立即清除）。

### [G01-11] 任何未捕获的协程异常都会弹出全屏崩溃报告页，把已恢复的非致命失败当成崩溃展示
- 严重度：P1
- 类别：A 架构 / E 韧性
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenViewModel.kt:103-106`、`app/src/main/java/com/projectlumen/app/app/ProjectLumenStateStore.kt:23`、`:103-105`、消费点 `app/src/main/java/com/projectlumen/app/app/ProjectLumenApp.kt:191-195`、`:308-324`
- 现状：
  ```kotlin
  private val crashReportingHandler = CoroutineExceptionHandler { _, throwable ->
      crashStateStore?.recordCrash(throwable) ?: recordCrashReport(throwable)
  }
  private val reportingScope = CoroutineScope(viewModelScope.coroutineContext + crashReportingHandler)
  ```
  `recordCrash` 把结果写进 `crashReport` StateFlow → `uiState.crashReport` → `ProjectLumenApp.kt:191` 的 `LaunchedEffect` 把它设为 `activeCrashReport` → `:308` 直接 `return@ProjectLumenTheme` 渲染 `LumenCrashReportScreen`，整个应用界面被崩溃页取代。`ProjectLumenStateStore` 的每个 `Flow.catch` 分支（`:25`、`:39`、`:43`、`:47`、`:51`、`:68`、`:72`、`:76`）也都调 `recordCrash`。
- 触发场景：`reportingScope` 里任何一个 `launch` 抛出未捕获异常即可，例如 `refreshShizukuState`（`:350-357`）里 Shizuku binder 掉线抛异常、`startDeviceSecurityScan` 之外的任意扩展路径、或某一路 Room Flow 抛一次瞬时异常。应用本身完全没崩，进程还活着。
- 影响：用户在正常使用中被推到一个"应用已崩溃，请上报"的全屏页面，需要点"继续"才能回到界面；同时 `onClearStoredReport` 会把这份报告上传到后端，制造噪声告警。
- 修复方案：区分"致命崩溃"和"已处理失败"。`ProjectLumenStateStore` 的 `Flow.catch` 与 `crashReportingHandler` 应改为调用一个新的 `recordHandledFailure`（内部走 `LumenCrash.recordNonFatal`，即 `ProjectLumenApplication.recordHandledFailure`，它明确不占用 pending-report 槽位），只写入一个轻量的 `ProjectLumenUiState.transientErrorMessage` 供 Snackbar 展示；`uiState.crashReport` 仅保留给启动崩溃（`MainActivity` 传入的 `initialStartupReport`）与开发者页面的手动预览（`ProjectLumenApp.kt:552`）使用。
- 风险/注意：`ProjectLumenViewModel` 构造参数 `recordCrashReport: (Throwable) -> CrashReport?` 目前从 `MainActivity.kt:134` 绑定的是 `app::recordCrash`（会占用 pending 槽位，导致**下次冷启动**也弹崩溃页），改动时要把非致命路径改绑到 `app::recordHandledFailure`。开发者页面的"预览崩溃报告"依赖 `activeCrashReportClearsStore = false` 这条路径，不要一起删掉。

### [G01-12] 设置尚未就绪就用默认值执行全局副作用：`setApplicationLocales` 被多调一次，首帧主题也用默认值
- 严重度：P1
- 类别：D 生命周期与框架约束
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenApp.kt:279-281`、`app/src/main/java/com/projectlumen/app/MainActivity.kt:54`
- 现状：
  ```kotlin
  LaunchedEffect(uiState.settings.languageCode) { LocaleController.apply(uiState.settings.languageCode) }
  ```
  `stateIn` 的 `initialValue = ProjectLumenUiState()`，其 `settings.languageCode` 是默认值 `"system"`（`AppSettingsEntity.kt:14`）。因此首帧必然先执行 `LocaleController.apply("system")` → `AppCompatDelegate.setApplicationLocales(空列表)`，随后 Room 值到达再执行一次真实语言。`MainActivity.kt:54` 同理：外层先用 `ProjectLumenTheme(themeMode = AppThemeMode.SYSTEM, useDynamicColors = false)` 包一层，内层 `ProjectLumenApp` 再按真实设置渲染一次主题。
- 触发场景：用户把语言设为非"跟随系统"（zh/en）的每一次冷启动。
- 影响：应用语言在冷启动时被先清空再设置；API 33+ 上 `setApplicationLocales` 会经由框架 `LocaleManager` 触发 Activity 重建（需实机确认重建次数，机制上至少多做一次无谓的全局 locale 切换），表现为首屏闪烁、冷启动变慢，并且 `MainActivity.onCreate` 的崩溃门禁逻辑会重跑一遍。深色模式用户还会先看到一帧浅色主题。
- 修复方案：把全局副作用推迟到数据就绪之后。`LaunchedEffect` 的条件改为 `LaunchedEffect(uiState.isReady, uiState.settings.languageCode) { if (uiState.isReady) LocaleController.apply(...) }`；并在 `LocaleController.apply` 里先比对 `AppCompatDelegate.getApplicationLocales()`，与目标一致时直接返回（幂等）。主题方面，`MainActivity.kt:54` 的外层 `ProjectLumenTheme` 只服务于 `LumenCrashGate`，可保留，但 `ProjectLumenApp` 在 `!uiState.isReady` 时应先渲染一个用最终主题色的占位 Surface，避免浅→深跳变。
- 风险/注意：给 `LocaleController.apply` 加幂等判断时注意 `LocaleListCompat.getEmptyLocaleList()` 与 `getApplicationLocales()` 返回空列表的相等性比较；`isReady` 依赖 `settings`+`runtime`+`dailyGoal` 三者齐备（`ProjectLumenStateStore.kt:62`、`:87`），若这条链上有任一路长期为 null，语言就永远不会被应用——所以幂等判断必须保留兜底。

### [G01-13] `Application.onCreate` 在主线程串行执行内存采样、进程退出原因查询、MMKV 初始化与两次原生完整性校验
- 严重度：P1
- 类别：B 并发（主线程阻塞）
- 位置：`app/src/main/java/com/projectlumen/app/ProjectLumenApplication.kt:147`、`:148`、`:149`、`:152`、`:174-177`
- 现状：
  ```kotlin
  runCatching { recordRecentProcessExitReason() }     // getHistoricalProcessExitReasons：跨进程 binder
  initializeMmkvOrRecordCrash()                      // MMKV.initialize：加载 .so + 建目录
  runCatching { MemoryHealthMonitor.sample(this) }    // Debug.getMemoryInfo：遍历 /proc/self/smaps
  runCatching { AppIntegrityGuard.enforce(this) }     // 原生校验
  deviceSecurityGate.startStartupScan(applicationScope)   // 该 lazy 的 init 里又 enforce 了一次
  ```
  `Debug.getMemoryInfo` 是官方标注的昂贵调用（数十至上百毫秒）。`DeviceSecurityGate.init`（`core/security/DeviceSecurityGate.kt:41-50`）里再次调用 `AppIntegrityGuard.enforce(appContext)`，而这个 lazy 恰好在 `:157` 被首次解引用——于是完整性校验在同一次 `onCreate` 的主线程上跑了两遍（证书 SHA-256 有缓存，但原生环境扫描没有）。`onTrimMemory`（`:174`）也是主线程回调，同样直接做 `Debug.getMemoryInfo`。
- 触发场景：每次冷启动；`onTrimMemory` 则在系统内存压力时高频触发。
- 影响：冷启动首帧被推迟（叠加 G01-04 后更明显）；内存吃紧时 `onTrimMemory` 的高频昂贵采样会加剧卡顿，正是最需要流畅的时候。
- 修复方案：把非阻塞必需的项挪到 `applicationScope`（已是 `Dispatchers.IO`）。`recordRecentProcessExitReason()` 与 `MemoryHealthMonitor.sample(this)` 改为 `applicationScope.launch { ... }`；`onTrimMemory` 里也改为 `applicationScope.launch { MemoryHealthMonitor.recordTrim(...) }`（先 `super.onTrimMemory(level)`）。`AppIntegrityGuard.enforce` 的重复调用去掉一处：既然 `DeviceSecurityGate.init` 已经做了并把结果存进 `nativeIntegrityOk`，`onCreate:152` 那次可以删掉，改为在需要时读 `deviceSecurityGate` 的判定结果。MMKV 初始化必须保留在主线程同步执行（下游依赖它）。
- 风险/注意：`MemoryHealthMonitor.sample` 异步化后，`ProjectLumenViewModel.memoryHealth` 在启动瞬间会是零值快照，开发者页面需容忍。删掉 `onCreate:152` 的 `enforce` 会改变"完整性失败"被记录的时机与来源（原来会 `recordHandledFailure`），要确认发布构建的 fail-closed 行为不变——`DeviceSecurityGate` 内部是 `getOrElse { false }` 后置为 `BLOCKED`，语义等价但不再写崩溃记录，必要时在 `DeviceSecurityGate` 里补一次上报。

### [G01-14] MMKV 初始化失败被静默吞掉，之后所有运行时状态读写都抛异常，无降级也无提示
- 严重度：P1
- 类别：E 韧性
- 位置：`app/src/main/java/com/projectlumen/app/ProjectLumenApplication.kt:148`、`:207-211`
- 现状：
  ```kotlin
  private fun initializeMmkvOrRecordCrash() {
      runCatching { ProjectLumenMmkv.initialize(this) }
          .onSuccess { CrashBreadcrumbs.record("MMKV initialized") }
          .onFailure(::recordHandledFailure)      // 记一笔就继续启动
  }
  ```
  `ProjectLumenMmkv.checkInitialized()` 在失败后对**每一次**访问都抛 `IllegalStateException`。而 `RuntimeStateMmkvStore.mmkv` 是 `by lazy`，`RuntimeRepository` 的全部读写都经它；`SecureCredentialStore.encryptedMmkv`、`MmkvBackendConnectivityPersistence`、`EyeCarePreferencesDataStore` 也一样。启动流程照常继续，没有任何"存储不可用"的状态位。
- 触发场景：`MMKV.initialize` 失败的真实原因包括：`libmmkv.so` 与设备 ABI 不匹配（本项目做 ABI 分包，装错包时可能发生）、`filesDir` 不可写、多进程环境下目录权限异常。
- 影响：计时/相位状态完全无法持久化——`ProjectLumenStateStore` 的 `runtime` 流被 `catch` 成 null，`isReady` 永远为 false，界面停在初始状态；1 Hz 时钟第一次 tick 就抛异常并永久死亡（见 G01-05）；同时弹出全屏崩溃页（见 G01-11）。用户看到的是一个"打开就没反应"的应用，日志之外没有任何解释。
- 修复方案：把存储可用性变成显式状态。`ProjectLumenApplication` 增加 `@Volatile var storageAvailable: Boolean`，`initializeMmkvOrRecordCrash` 失败时置 false 并透传给 UI（`ProjectLumenUiState` 增加 `storageUnavailable: Boolean`），由一个专门的错误页说明"本地存储不可用，请重新安装/检查存储空间"，同时跳过时钟与所有服务启动，避免每秒抛一次异常。若希望更进一步，可在 `ProjectLumenMmkv` 里提供一个内存兜底实现（仅本次进程有效）让应用降级可用。
- 风险/注意：`storageUnavailable` 的判定必须早于 `ProjectLumenViewModel` 构造（`SecureCredentialStore.installProfile` 也会踩到 MMKV），因此这个标记要放在 `ProjectLumenApplication` 而不是状态存储里。不要把 `ProjectLumenMmkv.initialize` 的抛出改成静默返回——那会把问题从"启动即报错"退化成"到处随机报错"。

### [G01-15] ViewModel 通过静态 `applicationContext()` 反向依赖 Application，架空了 lambda 注入的设计目的
- 严重度：P2
- 类别：A 架构（可测试性）
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenViewModel.kt:179-183`、`:203`、`app/src/main/java/com/projectlumen/app/ProjectLumenApplication.kt:420-427`
- 现状：
  ```kotlin
  securityEvidence = { (ProjectLumenApplication.applicationContext() as? ProjectLumenApplication)?.deviceSecurityGate?.backendEvidence() }
  // startDeviceSecurityScan()
  val context = ProjectLumenApplication.applicationContext() ?: return
  ```
  整个 ViewModel 的 20 多个构造参数都在避免依赖 `Context`，但这两处直接走静态可空单例把 `Context` 拿回来了，`startDeviceSecurityScan` 还在 ViewModel 里 `new DeviceSecurityScanner(context)`。
- 触发场景：纯 JVM 单测里 `applicationContext()` 返回 null，`startDeviceSecurityScan()` 直接静默 `return`——这条分支永远测不到，且行为与真机不一致（真机会跑扫描）。
- 影响：可测试性缺口；`DeviceSecurityGate`/`DeviceSecurityScanner` 存在两条获取路径（构造注入 vs 静态查找），后续重构容易改漏一条。
- 修复方案：把这两个能力也做成构造参数：新增 `securityEvidenceProvider: () -> JSONObject?` 与 `runDeviceSecurityScan: suspend () -> DeviceSecurityScanner.SecurityAssessment`，由 `MainActivity.createProjectLumenViewModel` 从 `app.deviceSecurityGate` / `app` 绑定，ViewModel 内不再引用 `ProjectLumenApplication`。
- 风险/注意：`ProjectLumenApplication.applicationContext()` 还被其它文件使用吗？经检索仅本文件两处引用（其余组件都是 `context.applicationContext as? ProjectLumenApplication`），因此这两处改完后该静态成员可一并删除——删除前请确认没有新增调用方。

### [G01-16] Compose 层直接强转 Application 取出 apiClient / backendConnectivity 自行组装 UpdateChecker，绕过 ViewModel
- 严重度：P2
- 类别：A 架构（分层被击穿）
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenApp.kt:166-179`、`:316-320`
- 现状：
  ```kotlin
  val application = baseContext.applicationContext as ProjectLumenApplication
  val updateChecker = remember(application) {
      UpdateChecker(context = application, apiClient = application.apiClient, backendGate = application.backendConnectivity)
  }
  ```
  更新检查、下载、安装授权的全部状态（`UpdateDialogState`、`downloadProgressBytes`…）都活在 Composable 里，网络调用由 `coroutineScope.launch { withContext(Dispatchers.IO) { ... } }` 直接发起，完全绕过 ViewModel 与状态存储。
- 触发场景：任何配置变更（旋屏、字体缩放、深浅色切换）都会丢掉 `updateDialogState` 与下载进度（它们是 `remember` 而非 `rememberSaveable`），下载中转屏即前功尽弃；`as ProjectLumenApplication` 是非空强转，在 Compose 预览或被其它 Application 托管时会抛 `ClassCastException`。
- 影响：更新流程不可测试、不随生命周期存活；与"UI 只读 StateStore"的分层约定冲突，是本组唯一的分层缺口。
- 修复方案：把更新检查搬进 ViewModel。新增 `ProjectLumenUpdateFeatureEntry`（持有 `UpdateChecker`/`UpdateInstaller`，由 `MainActivity` 注入），暴露 `StateFlow<UpdateDialogState>` 与 `checkForUpdate(manual)` / `download(candidate, asset)` / `install(file)`；`ProjectLumenApp` 只 `collectAsStateWithLifecycle` 并渲染。
- 风险/注意：`UpdateInstaller` 需要 Activity 级 `Context` 来启动安装器 Intent，搬进 ViewModel 时安装动作要保留在 Composable 侧（只把"检查/下载/状态"下沉），否则会泄漏 Activity。

### [G01-17] 组合期在主线程做磁盘读取（`app.crashReports.load()`）
- 严重度：P2
- 类别：B 并发（主线程阻塞）
- 位置：`app/src/main/java/com/projectlumen/app/MainActivity.kt:77-79`（另见 `ProjectLumenApplication.kt:83-93`）
- 现状：
  ```kotlin
  ProjectLumenApp(
      viewModel = viewModel,
      crashReport = runCatching { if (LumenCrash.isInstalled()) app.crashReports.load() else null }.getOrNull(),
      ...)
  ```
  这行在 `setContent` 的 Composable lambda 里，每次该 lambda 重组（例如 `openLaunchRequest.value` 变化，被同一 lambda 读取）都会重新读一次崩溃报告文件；`crashReports` 的 getter 本身还会在 SDK 未安装时尝试重新 `installLumenCrashSdk()` 并可能 `new CrashReportStore(this)`。
- 触发场景：外部应用通过 Open API 触发 `onNewIntent` → `openLaunchRequest.value` 变化 → 该 lambda 重组 → 主线程再读一次文件。冷启动时至少读一次。
- 影响：主线程文件 IO，帧时间抖动；`crashReports` getter 的副作用（可能重装 SDK）出现在组合期，属于不该在组合里做的事。
- 修复方案：在 `onCreate` 里把它取出来存成局部 `val initialStoredReport = ...`（与已有的 `initialStartupReport` 同一批），把该值传给 `ProjectLumenApp`；组合内不再调用。
- 风险/注意：`ProjectLumenApp` 内部对 `crashReport` 参数用了 `remember(crashReport) { mutableStateOf(...) }`，改成固定值后这两个 `remember` 的 key 不再变化，行为与预期一致（启动崩溃只需展示一次）。

### [G01-18] `ProjectLumenUiState` 直接以 Room 实体为字段类型，UI 与数据库 schema 硬绑定
- 严重度：P2
- 类别：A 架构（抽象缺失）
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenUiState.kt:14-27`
- 现状：11 个字段里有 8 个是 `*Entity`（`AppSettingsEntity`、`RuntimeStateEntity`、`DailyEyeStatsEntity`…），屏幕代码到处直接读 `uiState.settings.xxx` / `uiState.runtime.reminderPhase`（相位还是 `String` 而不是枚举，比较时到处写 `== ReminderPhase.RESTING.name`）。
- 触发场景：任何一次 Room 字段重命名/迁移都会波及所有屏幕文件；为 UI 写单测需要构造 40 多字段的 `RuntimeStateEntity`。
- 影响：可维护性与可测试性成本；相位用字符串比较绕过了编译器的穷尽性检查（`when` 分支漏写不会报错，只会静默走 `else`）。
- 修复方案：不必一次性重构。优先把 `runtime` 从实体改为一个面向 UI 的 `RuntimeSnapshot`（用 `ReminderPhase` / `PomodoroPhase` / `ActiveEngine` 枚举而不是 `String`，只保留 UI 真正用到的十几个字段），转换放在 `ProjectLumenStateStore` 的 combine 里。这样 `when (phase)` 能获得穷尽性检查。
- 风险/注意：改动面覆盖 `ProjectLumenMainScreens.kt`、`ProjectLumenStatsAndTimerCards.kt`、`ProjectLumenUiFormatters.kt` 等多个别组文件，建议单独排一次重构，不要和其它修复混在一个提交里。

### [G01-19] `SettingsRepository` / `RuntimeRepository` 被三处各自 new，且 `SettingsRepository.update` 没有任何锁
- 严重度：P2
- 类别：A 架构 / B 并发
- 位置：`app/src/main/java/com/projectlumen/app/ProjectLumenApplication.kt:355-361`、`:367-369`、`app/src/main/java/com/projectlumen/app/core/lifecycle/AppLifecycleCoordinator.kt:33-34`、`app/src/main/java/com/projectlumen/app/app/ProjectLumenRepositories.kt:23-29`
- 现状：`ProjectLumenApplication.settingsRepository()` / `runtimeRepository()` 是工厂方法，**每次调用都新建一个实例**（`AlarmReceiver`、`BootReceiver`、各 Worker 也都各建一个）；`AppLifecycleCoordinator` 在构造期各持有一个；`ProjectLumenRepositories` 又各建一个。`RuntimeRepository` 因为背后是进程内单例 `RuntimeStateMmkvStore` 所以数据一致（但仍无写锁，见 G01-09）；而 `SettingsRepository.update`（`core/repositories/SettingsRepository.kt:68-77`）是裸的 `getOrDefault()` → `transform` → `dao.upsert()`，没有 `Mutex`，也不在同一个事务里。
- 触发场景：`ProjectLumenSettingsFeatureEntry` 的多个 `set*Enabled` 都是独立 `scope.launch`，用户快速连点两个开关（或一个开关触发的 `updateSettings` 与 `AppLifecycleCoordinator` 的 `ensureDefault` 并发）就会出现丢更新——后写的那次基于更早的快照，把前一次的改动覆盖回去。
- 影响：设置项偶发"点了没生效"；由于 `AppSettingsEntity` 字段极多且整行 upsert，一次丢更新可能回退多个字段。
- 修复方案：给 `SettingsRepository` 加一个进程级 `Mutex`（因为存在多实例，锁必须挂在伴生对象/单例上而非实例字段），`update` 全程持锁；同时把 `ProjectLumenApplication.settingsRepository()` / `runtimeRepository()` 改成 `by lazy` 的单例属性，消除"每次调用新建实例"的误导性。
- 风险/注意：改成 `by lazy` 属性后要检查所有调用点（`AlarmReceiver.kt:31-32`、`BootReceiver`、`ReminderActionReceiver`、`TimerReconciliationWorker`、`ShizukuResilienceWorker`、`EyeCareTelemetryReporter.kt:132` 里还自己 `new RuntimeRepository(...)`）——它们都在别组，但改法是纯替换，无行为变化。

### [G01-20] 每次进入前台都无条件发一次设备注册请求，无节流无退避
- 严重度：P2
- 类别：E 韧性
- 位置：`app/src/main/java/com/projectlumen/app/core/lifecycle/AppLifecycleCoordinator.kt:50`、`:123-143`、`:145-166`
- 现状：`onStart` 里 `registerDeviceAsset(settings)` 只判断 `BackendCapability.DEVICE_REGISTRATION` 是否可执行，没有"上次注册时间"节流；失败也只是 `recordHandledFailure`，没有退避，下次前台再原样重试。`accessTokenForDeviceRegistration` 还可能顺带发一次 `refreshSession`。
- 触发场景：锁屏/亮屏、切到其它应用再切回来，每一次都发一次注册请求（`ProcessLifecycleOwner` 的 ON_START）。频繁切换应用的用户一天可能触发几十上百次。
- 影响：无谓的流量与后端写压力；在弱网下每次前台都会有一次可能超时的请求排在 `onStart` 协程里，拖慢后续的 `runtimeRepository` 恢复逻辑（它们在同一个协程里顺序执行）。
- 修复方案：把 `registerDeviceAsset` 加节流——用 MMKV 记录 `lastDeviceRegistrationAt`，间隔小于 6~24 小时且上次成功则跳过；失败时记录失败次数做指数退避。另外把它从 `onStart` 的主协程里挪出去单独 `scope.launch`，避免阻塞前台恢复路径。
- 风险/注意：如果后端依赖这个请求做"活跃设备"统计，加节流会改变统计口径，需要与后端确认间隔。

### [G01-21] 两个未使用的 import
- 严重度：P2
- 类别：H 编译与结构
- 位置：`app/src/main/java/com/projectlumen/app/app/ProjectLumenApp.kt:10`（`androidx.compose.animation.animateContentSize`）、`:50`（`androidx.compose.material3.ExperimentalMaterial3Api`，文件顶部的 `@file:OptIn` 用的是全限定名，短名从未出现）
- 现状：两个 import 在文件里没有任何短名引用。
- 触发场景：Kotlin 编译器会报 unused import 警告；当前 `app/build.gradle.kts` 未开启 `allWarningsAsErrors`，所以不会卡 CI。
- 影响：仅噪声；若后续开启警告即错误会变成构建失败。
- 修复方案：删除这两行 import。
- 风险/注意：无。

## 已核查但无问题的点

- **lambda 注入没有泄漏 Activity**：`MainActivity.kt:119-134` 注入的全部是 `app::xxx` 方法引用与 `app.telemetry` 闭包，捕获的是 `Application`；`BuildUpdateNotesLoader(app)` 也只持 `applicationContext`。ViewModel 存活于配置变更期间不会持有 Activity。这条设计是正确的，修复 G01-02 时请保持"只捕获 Application"。
- **`combine` 的重载与类型正确**：`baseDataState`（5 路）、`dataState`（5 路）、`uiState`（3 路）都在 kotlinx.coroutines 提供的 vararg 具体重载范围内（≤5），lambda 形参类型与顺序与上游一一对应，无隐式 `Array<Any?>` 退化。
- **`Flow.catch` 覆盖到了所有可能抛异常的上游**：`ProjectLumenStateStore.kt:80` 的 `repositories.deviceInsights.observe()` 没有 `.catch` 但它返回 `MutableStateFlow.asStateFlow()`（`DeviceInsightsRepository.kt:18`），且 `refresh()` 内部已 `runCatching`，StateFlow 本身不会抛——这里**不是**缺陷，不要为了对称而加 `catch`。
- **`SharingStarted.WhileSubscribed(5_000)` + `initialValue` 的选择合理**：切后台 5 秒后停掉 Room 订阅、回前台立即复用最后值，`isReady` 用 `settings/runtime/dailyGoal` 三者齐备来判定，避免了用默认值渲染真实数据。
- **`reportingScope` 的生命周期正确**：它复用 `viewModelScope.coroutineContext`（SupervisorJob），ViewModel 清除时所有子协程（含 1 Hz 时钟、`stateIn`）随之取消，不存在泄漏；不需要额外 `onCleared`。
- **本组没有静态/顶层 `Handler(Looper.getMainLooper())`**：11 个文件里检索无 `Looper` 使用，不存在纯 JVM 单测类加载即 `ExceptionInInitializerError` 的写法。
- **`ProjectLumenApp` 的 `remember` key 覆盖了 lambda 实际读到的字段**：`activeThemeTemplate`（key = `useDynamicColors` + `templates` + `activeTipTemplateId`）与 `activeTemplate` 实际读取的字段一致；`autoDarkActive` 的 key 用 `nowMillis / 60_000L` 是有意的分钟级精度，不是漏 key。
- **`PomodoroEngine` 的相位环与周期计数是穷尽的**：`FOCUS → SHORT_BREAK ×3 → LONG_BREAK → FOCUS`，`pomodoroCycleIndex` 在长休息后归 1，`advance` 的 `pomodoroPhaseEndAt <= 0L` 守卫挡住了 IDLE 态，没有不可达分支。滑块下界（`ProjectLumenSettingsScreen.kt:762-766` 为 5/3 分钟起）也排除了 0 时长导致的空转循环。
- **`AppLifecycleCoordinator` 无需注销监听器**：它注册在 `ProcessLifecycleOwner`（`ProjectLumenApplication.kt:162`）上，与进程同生命周期；其 `CoroutineScope` 同理不需要 `cancel()`。真正的问题是并发写（G01-09），不是注销。
- **`ProjectLumenApplication.recordCrash` / `recordHandledFailure` 对 `BackendCommunicationBlockedException` 的特判**：被后端门禁挡下的请求只留面包屑、不占 pending-report 槽位，这是正确的降噪设计，修 G01-11 时不要破坏它。

