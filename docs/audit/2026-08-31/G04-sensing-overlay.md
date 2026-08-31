# G04 感知与悬浮层（相机距离/眨眼 · 光照 · 悬浮窗 · 调试悬浮层 · 洞察）审查报告

- 审查文件数：16，总行数：3330
  - `core/proximity/` 8 个 / 1742 行、`core/light/` 1 个 / 207 行、`core/overlay/` 1 个 / 192 行、`core/debug/` 3 个 / 563 行、`core/insights/` 3 个 / 626 行
- 缺陷计数：**P0 4 条**（G04-01、G04-02、G04-04、G04-13）、**P1 8 条**（G04-03、05、06、07、11、12、14、16）、**P2 8 条**（G04-08、09、10、15、17、18、19、20），合计 20 条
- 结论摘要：本组的健康度呈两极分化。`core/insights/` 质量明显高于其余目录——分层清晰、纯函数化、有单测、数据不出设备，只有两处口径瑕疵。真正的风险全部集中在**相机采集链路**：`core/proximity/` 里 ML Kit 检测器从创建到进程死亡**从不 `close()`**（每轮采样泄漏 1~3 个原生检测器），Camera2 的取消/超时路径存在一个"`CameraDevice` 永久泄漏"的竞态（Surface 变体连 `ImageReader` 与 `HandlerThread` 一起泄漏），两者叠加会让应用内存单调上涨并把前摄永久占死。更根本的问题是**架构层面的**：`ProximityCameraForegroundEligibility` 依赖的进程可见性门禁要求"App UI 可见"，而周期采样本就跑在后台，`ProximityDetectionWorker` 又在这条早退路径上不再续排，结果是**距离/眨眼监测实际只在用户盯着本应用界面时工作**；即便它跑起来，采样节奏（约 1 帧/2 秒）在物理上也无法观测到一次眨眼，使干眼告警变成恒定误报。悬浮层一侧最严重的是 `WindowManager.addView` 未做异常兜底（移除侧反而包了 `runCatching`），权限被撤销或 OEM 拒绝叠加窗时直接主线程崩溃。此外三个感知服务对同一条 MMKV runtime 状态各自做"整行读改写"，会互相覆盖告警冷却时间戳，导致重复弹通知与重复弹遮罩。

## 缺陷清单

### [G04-01] ML Kit 人脸/网格检测器从不 close，每轮采样泄漏 1~3 个原生检测器
- 严重度：P0
- 类别：C 资源
- 位置：`app/src/main/java/com/projectlumen/app/core/proximity/FaceDistanceAnalyzer.kt:19-39`（持有 `detector` / `meshDetector`，**整个类没有 `close()`**）、`app/src/main/java/com/projectlumen/app/core/proximity/ProximityCameraSampler.kt:38-42`（`by lazy` 创建，无释放）、`app/src/main/java/com/projectlumen/app/core/proximity/ProximityDetectionService.kt:150`、`:260`（每次 `runDetection` 都 `ProximityCameraSampler(this)` 新建实例）
- 现状：
  ```kotlin
  // FaceDistanceAnalyzer.kt:20-39 —— 两个 Closeable 成员，类里没有任何 close 入口
  private val detector = FaceDetection.getClient(...)
  private val meshDetector = if (includeTopology) FaceMeshDetection.getClient(...) else null
  // ProximityCameraSampler.kt:38-39
  private val plainAnalyzer by lazy { FaceDistanceAnalyzer(includeTopology = false) }
  private val topologyAnalyzer by lazy { FaceDistanceAnalyzer(includeTopology = true) }
  ```
  全仓库 `rg` 确认：`FaceDistanceAnalyzer` 只在 `ProximityCameraSampler` 内实例化，没有任何调用点调用 `.close()`；`ProximityCameraSampler` 也没有 `close()`/`use{}`。而 `ProximityDetectionService.runDetection` 每次执行都新建一个 sampler（第 150 行），若开启了人脸帧上传还会再新建第二个（第 260 行）。
- 触发场景：正常使用。周期采样由 `ProximityDetectionWorker` 按 `proximityCheckIntervalMinutes`（默认分钟级）反复拉起服务；开发者模式下 `developerTickIntervalSeconds` 最低 10 秒一轮。每轮至少泄漏 1 个 `FaceDetector`（开启调试帧/拓扑时额外泄漏 1 个带 contour 的 `FaceDetector` + 1 个 `FaceMeshDetector`）。
- 影响：ML Kit 检测器持有原生 TFLite 解释器与图像缓冲，单个数 MB 级。按 5 分钟一轮、一天 288 轮计算，进程内累积数百个未释放的原生检测器 → 应用内存持续单调上涨，最终 native OOM / `java.lang.OutOfMemoryError` 崩溃，或系统因内存压力反复杀掉前台服务导致护眼监测整体失效。这是本组最确定、最严重的资源缺陷。
- 修复方案：
  1. `FaceDistanceAnalyzer` 实现 `java.io.Closeable`，`close()` 里 `detector.close()` + `meshDetector?.close()`（各自包 `runCatching`）。
  2. `ProximityCameraSampler` 实现 `Closeable`，`close()` 里关闭两个 lazy analyzer（用 `isInitialized()` 判断，避免为了关闭反而创建）。
  3. `ProximityDetectionService.runDetection`（第 150 行）与 `uploadFaceAnalysisFrameIfEnabled`（第 260 行）改为 `ProximityCameraSampler(this).use { ... }`，或在 `runDetection` 开头建**一个** sampler、`try/finally` 中关闭，并把它作为参数传给 `uploadFaceAnalysisFrameIfEnabled`（顺带消除一轮里开两次相机的浪费）。
  4. 跨组关联：`core/devicecontrol/PrivilegedDeviceControlCoordinator.kt:271` 也在循环外新建 sampler 且从不关闭，同一修复需同步该调用点（缺陷归 devicecontrol 组，但接口签名变化会波及）。
- 风险/注意：改成单实例复用后，`analyzer(includeTopology)` 的两个 lazy 只会各创建一次，属于收益；但要确认 `detector.close()` 不会在仍有未完成 `process()` Task 时调用——当前代码是 `await()` 顺序执行，关闭点在所有 `analyze` 之后，安全。

### [G04-02] 相机超时/取消发生在 `openCamera` 与 `onOpened` 之间时，`CameraDevice` 永久泄漏（Surface 变体连 ImageReader/HandlerThread 一起泄漏）
- 严重度：P0
- 类别：C 资源 / B 并发
- 位置：`app/src/main/java/com/projectlumen/app/core/proximity/ProximityCameraSampler.kt:175-199`、`:225-247`、`:265-268`（`capturePreviewFrame`）；`:291-318`、`:348-376`、`:394-396`（`captureSurfacePipelineFrame`，`finally` 是空注释）
- 现状：
  ```kotlin
  var cameraDevice: CameraDevice? = null        // 非 @Volatile，跨线程读写
  fun release() { ...; runCatching { cameraDevice?.close() }; ...; thread.quitSafely() }
  fun complete(result: ...) {
      if (!finished.compareAndSet(false, true)) return   // 已 finish → 直接返回，不再 release
      release(); ...
  }
  continuation.invokeOnCancellation { if (finished.compareAndSet(false, true)) release() }
  // onOpened 里：cameraDevice = camera（由相机 HandlerThread 写）
  ```
  取消/超时发生在 `openCamera()` 之后、`onOpened()` 之前时：`invokeOnCancellation` 在协程线程把 `finished` 置真并 `release()`，此时 `cameraDevice` 仍为 `null`（且无 `@Volatile`，即使已赋值也不保证可见）；随后 `onOpened` 在 HandlerThread 上到达，赋值 `cameraDevice = camera`，后续 `createCaptureSessionCompat` 在已关闭的 `reader.surface` 上失败 → `complete(...)` 因 `finished` 已为真**立即 return**，`camera` 从此没有任何路径被 `close()`。`captureSurfacePipelineFrame` 更糟：外层 `finally` 只有一行注释（第 394-396 行），`reader` / `previewSurface` / `surfaceTexture` / `HandlerThread` 在这条路径上也全部泄漏。
- 触发场景：超时上限本来就很紧——`captureFaceDistance` 是 `maxDurationMillis.coerceIn(750L, 2_500L)`，而 `captureFaceDistanceSamples` 给出的预算是 `(deadline - now).coerceAtMost(1_500L)`，即常态 750~1500 ms。中低端机冷启动前摄 `openCamera` + 会话配置常需 0.8~2 s，因此**每轮采样的第一帧就有现实概率在 open 过程中超时**。`captureSurfacePipelineFrame` 被 silent-vision 循环以最高 2 Hz 反复调用（`PrivilegedDeviceControlCoordinator.kt:284`），泄漏会快速累积。
- 影响：前摄被本进程永久占用 → 本应用后续所有采样都拿到 `onError(ERROR_CAMERA_IN_USE)`，距离/眨眼监测端到端静默失效；同时**系统相机和其他 App 也打不开前摄**，用户只能强杀应用。Surface 变体还会每次泄漏一个 `HandlerThread` + `ImageReader`（YUV 640~960，2 buffer），线程数与原生内存双线增长直至崩溃。
- 修复方案：
  1. 把 `cameraDevice` / `captureSession` 换成 `AtomicReference<CameraDevice?>` / `AtomicReference<CameraCaptureSession?>`（或至少加 `@Volatile`，但 `var` 在 lambda 捕获下无法直接标注，用 `AtomicReference` 最省事）。
  2. `onOpened` 里先 `if (finished.get()) { camera.close(); return }`，再赋值；赋值用 `getAndSet`，若发现已 finish 立刻关闭新拿到的 device。
  3. `complete()` 的早退分支改为"仍然尝试释放本次新增资源"，即把 `release()` 做成幂等（`AtomicReference.getAndSet(null)` + `runCatching`），`complete` 无论 `finished` 与否都调用一次 `release()`。
  4. `captureSurfacePipelineFrame` 的外层 `finally`（第 394-396 行）补齐兜底释放：`reader.close()`、`previewSurface.release()`、`surfaceTexture.release()`、`thread.quitSafely()`（与 `capturePreviewFrame:265-268` 对齐），并同样补 `cameraDevice` 的兜底关闭——目前**两个函数的外层 finally 都没有关 CameraDevice**。
  5. 把超时下限从 750 ms 提到至少 2000 ms（或把 `captureFaceDistanceSamples` 的 `coerceAtMost(1_500L)` 放宽），减少"开相机途中被取消"的概率。
- 风险/注意：`release()` 变幂等后会出现"重复 close"，`CameraDevice.close()`/`ImageReader.close()` 幂等安全，但仍需保留 `runCatching`。放宽超时会拉长一轮采样时长，需同步确认 `captureFaceDistanceSamples` 的 `deadline`/`sampleIntervalMillis` 逻辑（第 52-63 行）不会变成死循环——当前 `break` 条件 `captureBudgetMillis < 750L` 若下限改动需一起改。

### [G04-03] 感知服务绕过 `StatisticsRepository` 直连 DAO 做 read-modify-write，并发下丢统计
- 严重度：P1
- 类别：B 并发 / A 架构 / F 持久化
- 位置：`app/src/main/java/com/projectlumen/app/core/proximity/ProximityDetectionService.kt:302-312`（并由 `:181-193` 在同一轮里连续调用两次）、`app/src/main/java/com/projectlumen/app/core/light/LightMonitorService.kt:183-189`（`incrementLowLightStats`，同一模式、同一实体）
- 现状：
  ```kotlin
  private suspend fun incrementEyeStats(app, nowMillis, transform: (DailyEyeStatsEntity) -> DailyEyeStatsEntity) {
      if (app.settingsRepository().get()?.statsEnabled == false) return
      val date = todayKey(nowMillis)
      val dao = app.database.dailyEyeStatsDao()          // ← 直接拿 DAO，绕过 StatisticsRepository
      val current = dao.get(date) ?: DailyEyeStatsEntity(statDate = date)
      dao.upsert(transform(current).copy(updatedAt = nowMillis))   // ← get→copy→upsert 无事务/无锁
  }
  ```
  写的实体是 `DailyEyeStatsEntity`（按 `statDate` 主键的当日统计），累加字段为 `proximityWarningCount`、`proximityCloseSeconds`、`eyeDryWarningCount`。`runDetection` 第 181-193 行在同一轮里对同一行连续做两次完整的 get→upsert（`tooClose` 一次、`blinkState.shouldWarn` 一次），中间没有事务边界。
  `LightMonitorService.incrementLowLightStats` 是同一份代码的复制：
  ```kotlin
  val dao = app.database.dailyEyeStatsDao()
  val current = dao.get(date) ?: DailyEyeStatsEntity(statDate = date)
  dao.upsert(current.copy(lowLightWarningCount = current.lowLightWarningCount + 1, updatedAt = nowMillis))
  ```
  两个服务跑在各自的 `Dispatchers.IO` scope 上，互不感知，且都会写同一行 `DailyEyeStatsEntity`。
- 触发场景：`DailyEyeStatsEntity` 是多写者实体——计时服务/休息统计（`core/repositories/StatisticsRepository`）、`LightMonitorService`（低光告警 +1）以及 `ProximityDetectionService`（距离/干眼 +1）都会累加当日行。`ProximityDetectionService` 的采样是分钟级、`LightMonitorService` 的低光告警冷却是 120 s，只要两个写者的 get→upsert 交叠，后一个 `upsert` 用的是过期快照，前一个的 `+1` 被整行覆盖丢弃。
- 影响：用户看到的"今日距离告警次数 / 近距离时长 / 干眼提醒次数"少于实际，且会出现别的写者刚写入的字段被回退（因为 `copy` 会把整行其它字段一起写回旧值）。统计口径不可信。
- 修复方案：把累加收敛到 `StatisticsRepository`——在其中新增形如 `suspend fun incrementDailyEyeStats(date: String, transform: (DailyEyeStatsEntity) -> DailyEyeStatsEntity)` 的方法，内部用 `@Transaction` 的 DAO 方法（或 Room `@Query("UPDATE daily_eye_stats SET proximity_warning_count = proximity_warning_count + :delta ... WHERE stat_date = :date")` 的原子 SQL 增量 + `INSERT OR IGNORE` 建行），并用一把仓库级 `Mutex` 串行化。然后把 `ProximityDetectionService.incrementEyeStats` 与 `LightMonitorService.incrementLowLightStats` 都改为委托 `app.statisticsRepository()`，删掉两处 `app.database.dailyEyeStatsDao()` 的直接引用；同时把 `ProximityDetectionService.kt:181-193` 的两次调用合并成一次 transform，减少一次往返。
- 风险/注意：`statsEnabled == false` 的短路语义要保留（当前是"设置为 false 才跳过，null 时仍写"）。若改成原子 SQL 增量，需要同步 Room 导出 schema 与迁移检查；跨组关联：`core/repositories/StatisticsRepository.kt` 的改动由数据组负责，本条只负责去掉服务侧的越层访问。

### [G04-04] 相机前台服务门禁要求「App UI 可见」，而周期采样恰好在后台跑：Worker 自续链被永久掐断，后台监测端到端失效
- 严重度：P0
- 类别：E 韧性 / A 架构 / D 生命周期
- 位置：`app/src/main/java/com/projectlumen/app/core/proximity/ProximityDetectionWorker.kt:17-46`（尤其 `:24-29` 的早退与 `:42-44` 的续排）、`app/src/main/java/com/projectlumen/app/core/proximity/ProximityCameraForegroundEligibility.kt:17-20`（门禁组合）
- 现状：
  ```kotlin
  // ProximityDetectionWorker.doWork()
  if ((calibrate || monitoringEnabled) &&
      !ProximityCameraForegroundEligibility.canStartCameraForegroundService(applicationContext)) {
      return Result.success()          // ← 直接返回，跳过下面的 enqueueNext
  }
  ...
  if (!calibrate && settings != null && monitoringEnabled && timeTriggerAllowed) {
      enqueueNext(applicationContext, delaySeconds = settings.proximityIntervalSeconds())
  }
  // ProximityCameraForegroundEligibility
  fun canStartCameraForegroundService(context: Context) =
      hasCameraPermission(context) && ForegroundServiceStartEligibility.canStartFromForegroundProcess()
  ```
  已核查 `ForegroundServiceStartEligibility.canStartFromForegroundProcess()`（`core/services/`，别组文件）的实现：SDK ≥ 31 时它要求 `ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(STARTED)`，即**必须有 Activity 处于可见状态**。而周期采样正是由 WorkManager 在后台唤醒执行的，此时 `ProcessLifecycleOwner` 几乎必然不是 STARTED。
  另外 `doWork()` 全程没有 `try/catch`：任何抛出（`settingsRepository().get()` 出错、`ProximityDetectionService.start` 抛异常等）都会让 Worker 变成 `Result.failure()`，同样不会续排（请求也没有配置 retry 策略）。
- 触发场景：用户开启距离/眨眼监测后把 App 切到后台（正常使用方式）。第一次 tick 落在后台 → 命中第 24-29 行早退 → `return Result.success()` 且**不再 `enqueueNext`** → 由 `enqueueUniqueWork(REPLACE)` 维持的自续链彻底断裂。此后只有 `ProximityEventReceiver` 收到 `USER_PRESENT` 才会重新 `enqueueNext(delaySeconds = 0)`，但解锁瞬间 App UI 通常也不可见，于是又立刻断一次。
- 影响：**距离/眨眼监测实际上只能在用户盯着本 App 界面时工作**；一旦切到别的应用（也就是真正需要护眼提醒的时候）就永久静默，设置页仍显示"已开启"，没有任何错误提示。这是功能端到端失效级别的缺陷。
- 修复方案：分两层修。
  1. **止损（必做）**：把"续排"提成 `doWork()` 的唯一出口——`try { ... } finally { if (!calibrate && monitoringEnabled && timeTriggerAllowed) enqueueNext(...) }`；第 24-29 行的早退改为"跳过本次启动服务但仍续排"。这样即便本次不可用，链条也能活到用户下次打开界面。
  2. **架构（根治）**：不要"每轮 start/stop 一个 camera 型前台服务"。Android 14+ 对 `FOREGROUND_SERVICE_TYPE_CAMERA` 的后台启动有硬限制，这个拓扑注定无法后台工作。应改为由一个常驻前台服务（可复用已常驻的 `TimerForegroundService`，或让 `ProximityDetectionService` 在开启监测期间保持运行）在**服务内部**按 `proximityCheckIntervalMinutes` 定时采样，只在服务首次启动时需要"UI 可见"这一次门禁；服务存活期间的后续采样不再触碰 `startForegroundService`。
- 风险/注意：方案 1 的 `finally` 续排会用 `ExistingWorkPolicy.REPLACE` 替换掉可能已由 `ProximityEventReceiver.enqueueNext(delaySeconds = 0)` 排入的即时任务（同一 unique name `project-lumen-proximity-sample`），需确认交互。方案 2 会显著改变前台通知数量与耗电特征，并需要重新评估 `ProximityDetectionService` 现在"跑完就 `stopSelf`"的假设（`onStartCommand` 第 56-63 行）以及 [G04-01]/[G04-02] 的资源释放时机（常驻后 sampler 应复用而非每轮新建）。

### [G04-05] 眨眼检测的采样率在物理上无法观测到一次眨眼，导致干眼告警恒定误报
- 严重度：P1
- 类别：A 架构 / E 韧性
- 位置：`app/src/main/java/com/projectlumen/app/core/proximity/ProximityCameraSampler.kt:44-65`（采样节奏）、`app/src/main/java/com/projectlumen/app/core/proximity/ProximityDetectionService.kt:357-410`（`evaluateBlinkState` / `countBlinkTransitions`）
- 现状：
  ```kotlin
  // Sampler：每次 captureFaceDistance 都完整开/关一次相机，随后固定 delay 900ms
  val captureBudgetMillis = (deadline - System.currentTimeMillis()).coerceAtMost(1_500L)
  if (captureBudgetMillis < 750L) break
  captureFaceDistance(...)?.let(samples::add)
  if (remaining > sampleIntervalMillis) delay(sampleIntervalMillis.coerceAtLeast(300L))
  // Service：只用这些离散样本的睁眼概率序列数闭→睁跃迁
  if (probability <= CLOSED_EYE_PROBABILITY) sawClosed = true      // 0.35f
  if (sawClosed && probability >= OPEN_EYE_PROBABILITY) blinkCount += 1   // 0.75f
  ```
  一次采集耗时 750~1500 ms，加 900 ms 间隔，实际帧间隔约 1.6~2.4 s；一轮最长 15 s（`blinkNoBlinkThresholdSeconds + 1`，上限 15）也只有 6~9 个离散帧。人的一次眨眼闭眼相位约 100~400 ms，落在采样瞬间的概率约 5%~20%，而 `countBlinkTransitions` 要求**同一轮内先出现 ≤0.35 的闭眼帧、再出现 ≥0.75 的睁眼帧**，两个条件同时命中的概率接近于零。
- 触发场景：开启眨眼监测后的每一轮。`blinkCount` 恒为 0 → `blinked=false` → `lastBlinkAt` 取 `previousLastBlinkAt`（第一次为 `samples.first().capturedAtMillis`）→ `dryForMillis` 随时间单调增长并必然超过 `blinkNoBlinkThresholdSeconds`，同时 `eyesOpen`（最后两帧睁眼）在正常使用时为真。
- 影响：用户每隔 `blinkAlertCooldownSeconds` 就收到一次"长时间未眨眼"通知 + 全屏护眼遮罩（`EyeProtectionOverlayService.show`，第 204-211 行），并且 `eyeDryWarningCount` 被持续累加，统计失真。这是"功能实现方式与目标不匹配"导致的系统性误报，用户体验上比崩溃更烦人。
- 修复方案：眨眼判定必须改成**连续帧流**而不是"反复开关相机取单帧"。具体：在 `ProximityCameraSampler` 增加一个 `captureBlinkWindow(durationMillis)`，一次 `openCamera` + 一个 `setRepeatingRequest` 会话内持续消费 `ImageReader` 帧（目标 ≥10 fps，只跑 `FaceDetector` 的 classification，不跑 contour/mesh），把睁眼概率序列一次性返回；`evaluateBlinkState` 的阈值逻辑可基本不变。若短期不想重构，退而求其次的**止损**方案是：`blinkCount == 0` 时不要把它当作"确实没眨眼"，而是返回"不确定"（`shouldWarn = false`），只在真的观测到过眨眼且随后长时间无跃迁时才告警——即把 `lastBlinkAt` 的兜底从 `samples.first().capturedAtMillis`（第 377 行）改成"无有效观测则不告警"。
- 风险/注意：连续帧方案会显著提高单轮耗电与相机占用时长，需要和 `proximityCaptureSeconds` / 前台服务通知文案一起评估；止损方案会让干眼提醒在重构前基本不再触发，属于行为可见变化，需产品确认。同时注意 `captureSeconds` 的计算（`ProximityDetectionService.kt:142-149`）依赖 `blinkNoBlinkThresholdSeconds + 1`，改采样方式后这段要一起调整。

### [G04-06] `onDestroy` 用一次性游离 `CoroutineScope` 写库，且服务 scope 已被 cancel，停止时间戳大概率丢失
- 严重度：P1
- 类别：B 并发 / D 生命周期
- 位置：`app/src/main/java/com/projectlumen/app/core/proximity/ProximityDetectionService.kt:67-78`、`app/src/main/java/com/projectlumen/app/core/debug/DeveloperDebugOverlayService.kt:301-315`（`recordServiceStop`，同一模式，由 `onDestroy` 第 88 行调用）
- 现状：
  ```kotlin
  override fun onDestroy() {
      val app = application as? ProjectLumenApplication
      if (app != null) {
          CoroutineScope(Dispatchers.IO).launch { recordForegroundServiceStop(app, System.currentTimeMillis()) }
          app.deviceControl.onServiceDestroyed(...)
      }
      scope.cancel()
      super.onDestroy()
  }
  ```
  这里刻意绕开了已有的 `scope`（因为紧接着要 `cancel()`），代价是创建了一个**无人持有、无人取消**的游离作用域；它的 `Job` 没有父级，异常也不会走 `recordHandledFailure`（服务 scope 的 `CoroutineExceptionHandler` 不覆盖它）。
- 触发场景：服务被系统回收或 `stopSelf` 后进入 `onDestroy`。此时进程往往紧接着被降优先级或杀掉，游离协程的 Room 写入没有任何完成保证；若写入抛异常（例如 `LOCKED_BOOT_COMPLETED` 阶段 CE 加密的 Room 不可读写），异常会被默认 handler 吞掉或直接崩溃在 IO 线程上。
- 影响：`foregroundServiceStoppedAt` 常态写不进去 → 依赖它做前台服务健康度/续期判断的逻辑（`foregroundServiceStartedAt` 已置位而 `StoppedAt` 仍为 0）会认为服务"仍在运行"，可能导致重复拉起或诊断面板显示错误状态。
- 修复方案：不要在 `onDestroy` 里现场 new scope。可选其一：
  (a) 把"记录停止时间"移到 `runDetection` 结束处（`onStartCommand` 第 56-63 行 `stopSelf` 之前）在服务自己的 `scope` 内完成；
  (b) 用应用级长生命周期作用域（`ProjectLumenApplication` 已有 scope 时复用它），并保留 `recordHandledFailure` 的异常处理；
  (c) 若必须在 `onDestroy` 落库，改用 WorkManager 一次性任务保证最终一致。
  另外 `scope.cancel()` 应该放在 `onDestroy` 最前面之外的位置权衡：当前 cancel 会打断正在进行的相机采集（配合 [G04-02] 的取消路径），修复 [G04-02] 时需确认取消能可靠释放相机。
- 风险/注意：方案 (a) 改变了"停止时间"的语义（变成"检测结束时间"而非"服务销毁时间"），使用该字段的诊断/洞察展示需同步核对（跨组关联：`core/insights` 与设置页诊断卡片会读这些字段）。

### [G04-07] `ProximityCameraSampler` 无本地同意门禁地对外暴露"原始人脸 JPEG"取帧接口
- 严重度：P1
- 类别：G 安全与隐私 / A 架构
- 位置：`app/src/main/java/com/projectlumen/app/core/proximity/ProximityCameraSampler.kt:92-119`（`captureFaceAnalysisFrame`）、`:126-159`（`captureSurfaceAnalysisFrame`）、`app/src/main/java/com/projectlumen/app/core/proximity/ProximityDetectionService.kt:254-264`（本组内唯一的开关点）
- 现状：
  ```kotlin
  // Sampler 里两个 public 方法直接返回原始 JPEG 字节，内部只检查 CAMERA 运行时权限
  suspend fun captureFaceAnalysisFrame(...): FaceAnalysisFrameCapture?   // frameBytes = 整帧 JPEG(quality 82)
  suspend fun captureSurfaceAnalysisFrame(...): SurfaceAnalysisFrameCapture?
  // Service 侧的门禁（本组唯一一处）
  if (!settings.diagnosticTelemetryUploadEnabled || !settings.diagnosticFaceAnalysisUploadEnabled) return
  ```
  从本组这一侧核查跨组线索的结果：把帧交给 `PrivilegedDeviceControlCoordinator` 的不是本组代码——该协调器**自己** `ProximityCameraSampler(app)`（`core/devicecontrol/PrivilegedDeviceControlCoordinator.kt:271`）并在 `while` 循环里以 `1000/maxFps.coerceIn(1,5)` 的节奏调用上面两个方法（`:284`、`:292`），上传走 `uploadSilentVisionFrame` / `uploadSurfaceAnalysisFrame`。也就是说本组没有"帧回调注册点"，但**本组提供了取帧能力且未在能力边界上设任何同意校验**；`captureSurfaceAnalysisFrame` 除了这条静默上传链路之外**没有任何其他调用方**（全仓库 `rg` 已确认），它是专门为该链路存在的。本组能真正关掉上传的开关只有 `ProximityDetectionService.uploadFaceAnalysisFrameIfEnabled` 里的两个 settings 布尔，且它们只约束本服务这一条路径，对 devicecontrol 那条循环完全无效。
- 触发场景：后端下发 `SilentVisionPolicy`（`frameUploadEnabled` / `surfaceAnalysisUploadEnabled`）后，只要 `hasLocalUserCameraConsent` 判定通过（该判定的失效由 devicecontrol 组负责），本组的取帧接口就会被以最高 5 fps 反复调用，把用户面部原图上传。
- 影响：用户面部原始图像离设备上传，且本组这一侧不存在任何独立的、用户可见的"关闭取帧"闸门；同时该路径复用了 [G04-02] 里泄漏最严重的 `captureSurfacePipelineFrame`，在隐私之外还会拖垮设备。
- 修复方案：在本组这一侧建立"能力闸门"，不把责任全部外推：
  1. 给 `ProximityCameraSampler` 的两个取帧方法加显式的能力参数（例如 `captureFaceAnalysisFrame(consent: FrameCaptureConsent)`），`FrameCaptureConsent` 只能由一个集中的、读取本地设置（`diagnosticFaceAnalysisUploadEnabled` 等）的工厂产出，让"绕过用户开关"在编译期不可表达。
  2. 或者更小改动：在 sampler 内部统一校验一处本地开关（注入一个 `suspend () -> Boolean` 的 `frameUploadAllowed`），任一取帧方法在返回原始字节前先校验，未授权直接返回 `null`。
  3. `captureSurfaceAnalysisFrame` 若仅为静默上传服务，建议连同其唯一调用方一起评估是否保留；至少改为 `internal` 并加 KDoc 明确"输出含原始人脸图像"。
- 风险/注意：加参数会改签名，`PrivilegedDeviceControlCoordinator.kt:284/292` 与 `ProximityDetectionService.kt:260` 三个调用点需同步（含命名参数）。真正的"同意判定失效"根因在 devicecontrol 组，本条只解决"取帧能力无门禁"这一侧，两处需一起修才闭环。

### [G04-08] `ProximityEventReceiver` 在 manifest 里监听 `CONFIGURATION_CHANGED`，该广播静态注册收不到
- 严重度：P2
- 类别：D 生命周期 / E 韧性
- 位置：`app/src/main/java/com/projectlumen/app/core/proximity/ProximityEventReceiver.kt:55-58`、清单侧 `app/src/main/AndroidManifest.xml:139-145`
- 现状：
  ```kotlin
  private val triggerActions = setOf(
      Intent.ACTION_USER_PRESENT,
      Intent.ACTION_CONFIGURATION_CHANGED,
  )
  ```
  清单中该 receiver 静态声明了 `android.intent.action.CONFIGURATION_CHANGED`。而该广播按平台约定**只能通过 `Context.registerReceiver` 动态注册接收**，manifest 声明的组件不会被投递。
- 触发场景：旋转屏幕、切换深色模式、改字号/语言等——预期会触发一次即时采样，实际永不触发。
- 影响：只有解锁（`USER_PRESENT`）这一条事件触发实际生效；"配置变化触发采样"这个设计意图完全失效，且代码上看不出来（读者会以为它在工作）。因为不影响主链路（时间触发仍走 Worker），归 P2。
- 修复方案：二选一——(a) 删掉 `ACTION_CONFIGURATION_CHANGED`（清单与 `triggerActions` 同时删），承认只按解锁触发；(b) 若确实要这个触发源，改为在 `MainActivity`/`Application` 里 `registerReceiver` 动态注册，并在对应生命周期 `unregisterReceiver`。倾向 (a)：动态注册需要一个长生命周期宿主，收益不抵复杂度。
- 风险/注意：删除后 `triggerActions` 只剩一个元素，`if (action !in triggerActions) return` 可保留不动；清单改动需确认没有别的组件依赖同一 intent-filter。

### [G04-09] 事件触发的 60 秒节流是非原子的 MMKV read-modify-write
- 严重度：P2（需确认并发实际概率）
- 类别：B 并发
- 位置：`app/src/main/java/com/projectlumen/app/core/proximity/ProximityEventReceiver.kt:66-74`
- 现状：
  ```kotlin
  val lastTriggerAt = store.decodeLong(KEY_LAST_TRIGGER_AT, 0L)
  if (now - lastTriggerAt < MIN_EVENT_TRIGGER_INTERVAL_MS) return false
  store.encode(KEY_LAST_TRIGGER_AT, now)     // ← 与上面的读之间无锁
  ```
  `onReceive` 每次都新建 `CoroutineScope(Dispatchers.IO)`（第 32 行）跑这段，多个广播可并发进入。
- 触发场景：短时间内连续投递两个 `USER_PRESENT`（快速锁屏解锁）时，两个协程可能都读到旧的 `lastTriggerAt` 并都返回 true。
- 影响：节流被击穿，连续 `enqueueNext(delaySeconds = 0)`；因为 unique work 是 `REPLACE`，后果只是多余的一次替换，不会真的双开相机，故仅 P2。
- 修复方案：把读改判写整体放进已有的 companion `Mutex`（把 `migrationLock` 改名为 `eventStateLock` 并复用），或直接用 MMKV 之外的 `AtomicLong` + `compareAndSet` 语义。
- 风险/注意：`migrationLock.withLock` 目前只包迁移，扩大临界区后要确认迁移里的 DataStore `first()` 不会长时间持锁（可先迁移、再单独加锁做节流判定）。

### [G04-10] `ProximityTriggerGate` 的 650 ms 定时回调在取消时未撤销，且传感器回调压在主线程
- 严重度：P2
- 类别：D 生命周期 / B 并发
- 位置：`app/src/main/java/com/projectlumen/app/core/proximity/ProximityTriggerGate.kt:66-75`
- 现状：
  ```kotlin
  val handler = Handler(Looper.getMainLooper())
  manager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME, handler)
  manager.registerListener(listener, gyroscope, SensorManager.SENSOR_DELAY_GAME, handler)
  handler.postDelayed(::finish, 650L)
  continuation.invokeOnCancellation { manager.unregisterListener(listener) }
  ```
  `invokeOnCancellation` 只注销了传感器，没有 `handler.removeCallbacks`；`::finish` 每次都是新的函数引用，即便想 remove 也得先持有该引用。`SENSOR_DELAY_GAME`（约 20 ms）两路传感器 650 ms 内会向**主线程**投递上百个回调。
- 触发场景：主线程繁忙导致 `withTimeoutOrNull(900L)` 先于 650 ms 回调完成时（例如首帧渲染、大列表滚动），协程被取消，遗留的 `finish` 仍会在主线程执行一次（只会重复 `unregisterListener` 并因 `continuation.isActive == false` 不 resume，无功能损害）。
- 影响：无功能性后果，但每次门禁判定都在主线程处理上百个传感器事件，会给 UI 增加可感知的抖动（该门禁仅在开发者模式启用，故 P2）。
- 修复方案：把 `::finish` 提取为 `val finishRunnable = Runnable { finish() }`，`invokeOnCancellation` 里 `handler.removeCallbacks(finishRunnable)` 一并清理；`Handler` 改用一条专用 `HandlerThread`（或复用 `SensorManager` 支持的后台 Handler）而不是主线程 Looper，避免主线程被传感器回调打满。
- 风险/注意：`Handler(Looper.getMainLooper())` 出现在函数体内（不是顶层/静态字段），不会引发纯 JVM 单测的类加载 NPE——这一点已核查，改动时不要顺手把它提成字段，否则会引入 brief 提到的 `ExceptionInInitializerError` 问题。

### [G04-11] 光照服务每 2 秒无条件重写整个 runtime 状态，驱动全量 UI 状态重发
- 严重度：P1
- 类别：F 持久化 / E 韧性
- 位置：`app/src/main/java/com/projectlumen/app/core/light/LightMonitorService.kt:98-105`（2 秒节流）、`:129-138`（无条件 upsert）
- 现状：
  ```kotlin
  override fun onSensorChanged(event: SensorEvent) {
      if (now - lastHandledAt < 2_000L) return
      lastHandledAt = now
      scope.launch { handleLux(lux, now) }
  }
  // handleLux 尾部：不比较新旧值，直接整行写回
  runtime?.let {
      runtimeRepository.upsert(it.copy(ambientLastLux = lux, ambientTooDark = tooDark, ..., updatedAt = nowMillis))
  }
  ```
  `ambientLastLux` 是一个随环境持续抖动的 Float，`updatedAt` 每次都变，因此**每 2 秒必然产生一次完整的 runtime 状态写**。已核查 `RuntimeRepository`（`core/repositories/RuntimeRepository.kt:28-30` → `RuntimeStateMmkvStore.upsert`）：写入落在 **MMKV**（整行 JSON 序列化 + `MutableStateFlow.value` 赋值），不是 Room，所以磁盘成本比 Room 事务低，但每次都要把 40 多个字段序列化成 JSON 并触发一次状态流发射。另外每次 `handleLux` 还要 `settingsRepository().get()`（第 111 行）+ 第 184 行再 `get()` 一次，一次回调最多读设置两次。
- 触发场景：开启环境光监测或自动亮度后，只要服务在跑就持续发生（`SENSOR_DELAY_NORMAL` 约 200 ms 一次事件，被节流到 2 s）。
- 影响：(1) 每 2 秒一次全字段 JSON 序列化 + MMKV mmap 写 + `StateFlow` 发射，纯浪费的 CPU 与耗电；(2) runtime 状态是 `ProjectLumenStateStore` `combine` 的输入源之一，每 2 秒一次变更会让整个 `ProjectLumenUiState` 重新发射，前台时导致 Compose 树每 2 秒重组一次（跨组关联：UI 组的 `remember`/`derivedStateOf` key 若不精确会放大成整页重绘）；(3) 放大了 [G04-16] 的丢更新窗口——写得越频繁，覆盖别的写者的概率越高。
- 修复方案：在 `handleLux` 里加变更门槛后再写——例如 `ambientLastLux` 变化小于阈值（如 `abs(lux - it.ambientLastLux) < max(2f, it.ambientLastLux * 0.15f)`）且 `tooDark` 未翻转且 `shouldWarn == false` 时直接跳过 `upsert`；同时把节流窗口从 2 s 提到 10~15 s（低光判定不需要 2 秒粒度），并把 `settings` 读取结果传给 `incrementLowLightStats` 复用，避免一次回调两次读设置。
- 风险/注意：诊断/洞察页若展示"实时照度"会变得不再逐秒刷新，需与 UI 组确认可接受（建议实时值只走内存态，不落 MMKV）。

### [G04-12] 自动亮度每 2 秒改写系统亮度并把系统切成手动模式，且从不恢复
- 严重度：P1
- 类别：D 生命周期 / E 韧性 / A 架构
- 位置：`app/src/main/java/com/projectlumen/app/core/light/LightMonitorService.kt:122-124`、`:145-167`
- 现状：
  ```kotlin
  val ratio = (lux.coerceIn(0f, 500f) / 500f)
  val percent = (min + (max - min) * ratio).roundToInt().coerceIn(1, 100)
  ...
  if (!Settings.System.canWrite(this)) return       // 静默失败，用户无任何反馈
  Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, SCREEN_BRIGHTNESS_MODE_MANUAL)
  Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
  ```
  没有任何平滑/滞回：`percent` 直接线性映射瞬时 lux，每 2 秒写一次全局设置。并且一旦执行过，系统亮度模式被永久置为 MANUAL——关闭本功能、停止服务、卸载重装都不会把 `SCREEN_BRIGHTNESS_MODE` 改回 `AUTOMATIC`。
- 触发场景：`autoBrightnessEnabled = true` 且已授予 `WRITE_SETTINGS`。用户在窗边走动、灯光闪动、手遮挡传感器都会让 lux 剧烈跳变。
- 影响：(1) 屏幕亮度每 2 秒可见跳变（闪烁），比不开这个功能体验更差；(2) 用户系统自带的自适应亮度被静默永久关闭，即使关掉本 App 的功能也不恢复，属于对系统设置的破坏性副作用；(3) 未授予 `WRITE_SETTINGS` 时第 161 行静默 `return`，用户以为功能生效但什么都没发生（Shizuku 路径同样在第 154 行返回 false 后落到这里）。
- 修复方案：
  1. 加滞回与平滑：只有当目标 `percent` 与"上次已应用值"相差超过阈值（建议 5~8 个百分点）时才写；对 lux 做指数滑动平均（`ema = ema*0.7 + lux*0.3`）后再映射；把最小写入间隔提到 10 s 以上。
  2. 在首次切 MANUAL 之前把原始 `SCREEN_BRIGHTNESS_MODE` 与 `SCREEN_BRIGHTNESS` 存入设置/`DataStore`，在 `onDestroy` 与"用户关闭自动亮度"时恢复（新增一个 `restoreSystemBrightness()`）。
  3. 权限缺失时不要静默：`!Settings.System.canWrite(this)` 时把状态写入 runtime 行（如 `autoBrightnessBlockedReason`），供设置页提示用户去授权（跨组关联：需要数据组加字段、UI 组加提示）。
- 风险/注意：恢复原始亮度需要在"用户手动改过亮度"的情况下做取舍，建议只恢复 `SCREEN_BRIGHTNESS_MODE`、不强行覆盖用户当前亮度值。滞回阈值调大后夜间从亮到暗的响应会变慢，需与产品确认。

### [G04-13] `WindowManager.addView` 未做异常保护，悬浮窗权限被撤销/被 OEM 拒绝时主线程崩溃
- 严重度：P0
- 类别：D 生命周期 / E 韧性
- 位置：`app/src/main/java/com/projectlumen/app/core/overlay/EyeProtectionOverlayService.kt:110`（对比 `:170-176` 的 `removeOverlay` 是包了 `runCatching` 的）
- 现状：
  ```kotlin
  val windowManager = getSystemService(WindowManager::class.java)
  ...
  windowManager.addView(view, params)      // ← 裸调用，无 try/catch
  overlayView = view
  // 而移除侧是保护过的：
  private fun removeOverlay() { ...; runCatching { getSystemService(WindowManager::class.java).removeView(view) } }
  ```
  `showOverlay` 由 `onStartCommand` 在**主线程**直接调用（第 58 行），`Settings.canDrawOverlays` 的检查在第 37 行、`show()` 的检查在第 184 行，两次检查与 `addView` 之间都有时间窗。
- 触发场景：(1) 用户在"应用可显示在其他应用上层"里撤销权限，恰好此时一个休息遮罩被触发 → `addView` 抛 `WindowManager$BadTokenException`；(2) 部分 OEM（MIUI/EMUI/ColorOS）系统里 `canDrawOverlays` 返回 true 但 `TYPE_APPLICATION_OVERLAY` 仍被后台弹窗策略拒绝，`addView` 抛 `BadTokenException`；(3) `getSystemService` 返回 null（极端裁剪 ROM）→ NPE。
- 影响：应用直接崩溃（未捕获异常发生在主线程的 `onStartCommand` 中），且崩溃时机是"用户正被提醒休息"，观感极差；崩溃还会带走同进程的计时前台服务，使计时状态错乱。
- 修复方案：把第 73 行与第 110 行改成
  ```kotlin
  val windowManager = getSystemService(WindowManager::class.java) ?: run { stopSelf(); return }
  val added = runCatching { windowManager.addView(view, params) }.isSuccess
  if (!added) { app.notifications.showBreakFallbackNotification(title, message); stopSelf(); return }
  overlayView = view
  ```
  即失败降级为普通通知（`NotificationService` 已有多种提醒通知可复用），并且只有 `addView` 成功后才赋值 `overlayView`（当前顺序已正确，但异常路径下 `countdownText` 仍被持有，需一并置 null）。同时 `showOverlay` 末尾的 `forceImmersive` / `tickCountdown` 只在添加成功后执行。
- 风险/注意：降级到通知需要新增/复用一个通知构建方法（跨组关联：`core/notifications`）。注意 `removeOverlay()` 在 `showOverlay` 开头已被调用，异常路径不要重复 `removeView` 一个从未添加的 view（`removeOverlay` 已有 `overlayView == null` 的短路，安全）。

### [G04-14] `show()` 丢弃启动结果且不传 eligibilityCheck，后台场景下休息遮罩静默不弹
- 严重度：P1
- 类别：E 韧性 / D 生命周期
- 位置：`app/src/main/java/com/projectlumen/app/core/overlay/EyeProtectionOverlayService.kt:183-190`
- 现状：
  ```kotlin
  fun show(context: Context, title: String, message: String, durationSeconds: Int) {
      if (!Settings.canDrawOverlays(context)) return          // 静默 return
      val intent = Intent(context, EyeProtectionOverlayService::class.java)...
      ForegroundServiceController.start(context, intent)      // ← 返回值 Boolean 被丢弃
  }
  ```
  对比 `ProximityDetectionService.start`（`ProximityDetectionService.kt:500-506`）会传 `eligibilityCheck`，这里既不传检查也不看结果。调用方有 5 处：`ProximityDetectionService.kt:197/205`、`TimerForegroundService.kt:254`、`AlarmReceiver.kt:74`、`ReminderActionReceiver.kt:74`、`LumenOpenRuntimeController.kt:179`，全部无法得知遮罩是否真的弹了。
- 触发场景：`ForegroundServiceController.start` 内部在 Android 12+ 捕获 `ForegroundServiceStartNotAllowedException` 后会 `SystemClock.sleep(2_000)` **阻塞当前线程**再重试一次，两次都失败就只记一条 breadcrumb 返回 false。`AlarmReceiver.onReceive` / `ReminderActionReceiver.onReceive` 运行在**主线程**，因此这条路径会造成 2 秒主线程阻塞（接近 BroadcastReceiver ANR 阈值），并且最终遮罩不显示。
- 影响：用户设定的休息强制遮罩在"屏幕关闭后计时到点""从通知点休息"等最需要它的场景下可能什么都不发生，且没有任何降级提醒；同时伴随一次 2 秒主线程卡顿。
- 修复方案：
  1. `show()` 改为返回 `Boolean`（`ForegroundServiceController.start` 的结果），调用方在 false 时降级为高优先级通知；至少在本文件内先把返回值透出。
  2. 传入 `eligibilityCheck = ForegroundServiceStartEligibility::canStartFromForegroundProcess`，让不可能成功的场景走"预期拒绝"分支，避免进入 `SystemClock.sleep` 重试路径。
  3. 更彻底的方向：遮罩本身只依赖 `SYSTEM_ALERT_WINDOW`，不必是前台服务。可以把窗口管理挪到一个由 `TimerForegroundService`（已常驻）持有的 `OverlayPresenter`，彻底摆脱"后台启动前台服务"限制。
- 风险/注意：跨组关联——`SystemClock.sleep` 在 `core/services/ForegroundServiceController.kt:73`，由服务组负责改成非阻塞重试；本组只负责"不进入该路径 + 失败可感知"。方案 3 会改变服务拓扑，需与计时服务的生命周期一起设计。

### [G04-15] 全屏遮罩最长 300 秒不可关闭、无逃生出口，且同一轮检测可被连续两次 `show` 覆盖
- 严重度：P2
- 类别：D 生命周期 / A 架构
- 位置：`app/src/main/java/com/projectlumen/app/core/overlay/EyeProtectionOverlayService.kt:57`、`:81-114`、`:116-127`；触发侧 `app/src/main/java/com/projectlumen/app/core/proximity/ProximityDetectionService.kt:196-211`
- 现状：
  ```kotlin
  val durationSeconds = intent?.getIntExtra(EXTRA_DURATION_SECONDS, 20)?.coerceIn(5, 300) ?: 20
  // MATCH_PARENT 全屏、isClickable=true、未设置 FLAG_NOT_FOCUSABLE/FLAG_NOT_TOUCHABLE，
  // 带 FLAG_KEEP_SCREEN_ON，且没有任何关闭按钮或 BACK 处理
  ```
  遮罩铺满屏幕并吞掉触摸，倒计时结束前唯一的退出方式是等待或强杀应用。另外 `ProximityDetectionService.runDetection` 在同一轮里可能先因 `shouldWarn` 弹距离遮罩（第 196-203 行）、紧接着又因 `blinkState.shouldWarn` 弹干眼遮罩（第 204-211 行）；第二次 `onStartCommand` 会 `removeOverlay()` 后重建并**重置倒计时**，用户只看得到后一条文案，而总时长翻倍。
- 触发场景：`overlayRestDurationSeconds` 设为较大值时；以及距离超标与"长时间未眨眼"同时判定为真（结合 [G04-05] 的误报，这个组合很容易同时成立）。
- 影响：用户在最长 5 分钟内无法接听电话、无法操作手机，只能等；连续两次 show 还会让实际遮挡时间叠加。属于体验与可用性风险（是否"强制"是产品选择，故 P2，但"无逃生出口 + 可叠加"应视为缺陷）。
- 修复方案：(1) 增加长按/双击 3 秒的紧急跳过（或前 3 秒内可取消），并在文案上说明；(2) 在 `showOverlay` 里对"已有遮罩正在显示"的情况改为**合并文案、不重置倒计时**（判断 `overlayView != null && removeAtMillis > now` 则只更新标题/正文并取 `max(remaining, newDuration)`）；(3) `ProximityDetectionService` 侧把两条告警合成一次 `show` 调用，避免背靠背两次启动服务。
- 风险/注意：加入紧急跳过会削弱强制休息的效果，需产品确认；合并逻辑要保证 `renderedRemainingSeconds` 与 `countdownText` 一起更新，否则倒计时文本会停住。

### [G04-16] 三个感知服务对同一条 runtime 状态并发做「整行读改写」，告警冷却时间戳会被互相覆盖
- 严重度：P1
- 类别：B 并发 / F 持久化
- 位置：
  - `app/src/main/java/com/projectlumen/app/core/debug/DeveloperDebugOverlayService.kt:246-263`（`writeSensorRuntime`，**每 1 秒**一次）、`:283-299`、`:301-315`、`:269-281`
  - `app/src/main/java/com/projectlumen/app/core/light/LightMonitorService.kt:129-138`（**每 2 秒**一次）
  - `app/src/main/java/com/projectlumen/app/core/proximity/ProximityDetectionService.kt:86-93`、`:138-140`、`:213-232`、`:277-287`、`:292-299`、`:317-325`
- 现状：三个服务都是 `runtimeRepository.get()` → `it.copy(只改自己那几个字段)` → `runtimeRepository.upsert(...)`，各自跑在**各自服务的 `Dispatchers.IO` scope** 上。已核查 `RuntimeRepository`（`core/repositories/RuntimeRepository.kt:17-35` 与其内部 `RuntimeStateMmkvStore:60-69`）：
  ```kotlin
  suspend fun get() = RuntimeStateMmkvStore.get(dao)          // 读 MMKV 整行 JSON，无锁
  suspend fun upsert(state) = RuntimeStateMmkvStore.upsert(dao, state.copy(id = 1))  // 写整行 JSON，无锁
  ```
  **仓库层没有任何 `Mutex`/事务，也没有 `update { }` 这样的原子改写入口**（对比 `SettingsRepository` 是有 `update {}` 的，见 `ProximityDetectionService.kt:157` 的用法）。整条状态是一个 40 多字段的 JSON 整体覆盖写，所以任意两个写者交叠时，后写者会把前写者刚改的字段**整体回退**。
- 触发场景：`DeveloperDebugOverlayService` 每 1 秒写一次传感器字段；`LightMonitorService` 每 2 秒写一次环境光字段；`ProximityDetectionService` 在每轮检测结束时写一次（含 `proximityLastWarningAt` / `blinkLastWarningAt` / `proximityCloseStartedAt`）。只要 proximity 的写落在另一个服务的 get 与 upsert 之间，它刚写的告警时间戳就被抹掉。开发者模式下（1 秒 + 750 ms 双轮询）碰撞概率显著提高。
- 影响：`proximityLastWarningAt` / `blinkLastWarningAt` / `ambientLastWarningAt` 是**告警冷却的唯一依据**（`ProximityDetectionService.kt:171-173`、`:381-383`、`LightMonitorService.kt:119-121`）。被回退后冷却判定失效 → 用户在冷却期内被**重复弹通知 + 重复弹全屏遮罩**（结合 [G04-15] 的不可关闭遮罩，体验伤害被放大）。同时 `proximityCloseStartedAt` 被回退会让"持续近距离时长"统计错乱。
- 修复方案：
  1. 在 `RuntimeRepository` 增加 `suspend fun update(transform: (RuntimeStateEntity) -> RuntimeStateEntity): RuntimeStateEntity`，内部用一把仓库级 `Mutex` 包住 `get`→`transform`→`upsert`（与 `SettingsRepository.update` 对齐）。
  2. 把本组全部 12 处 `get()?.let { upsert(it.copy(...)) }` 改成 `update { it.copy(...) }`；不要保留任何裸 `upsert` 的读改写。
  3. 注意 MMKV 是 `multiProcessMmkvWithId`（`RuntimeRepository.kt:43`），进程内 `Mutex` 不能覆盖跨进程写；若确实存在多进程写入者，需改用 MMKV 的进程间锁或把 runtime 状态收敛到单进程持有。
- 风险/注意：跨组关联——`RuntimeRepository` 属于数据/仓库组，新增 `update` 由该组落地；本组负责把调用点全部切换过去。另外已核查到一个需要该组确认的隐患：`RuntimeStateMmkvStore.upsert` **只写 MMKV、完全不写 Room**（`dao` 仅在一次性迁移中被读一次，见 `:82-84`），即 `RuntimeStateEntity` 的 Room 表在迁移后永久陈旧——这与 brief 里"MMKV 必须先于 Room"的约定不冲突（因为根本没写 Room），但属于"同一事实两个真相源"，缺陷归仓库组。

### [G04-17] 开发者调试悬浮层的双轮询（750 ms 拉设置 + 1 s 写状态）常驻运行，且 `addView` 同样无异常保护
- 严重度：P2
- 类别：E 韧性 / B 并发 / D 生命周期
- 位置：`app/src/main/java/com/projectlumen/app/core/debug/DeveloperDebugOverlayService.kt:155-176`（750 ms 轮询）、`:128-139`（1 s 写状态）、`:204`（裸 `addView`）、`:49-53` 与 `:246-263`（跨线程读的非 `@Volatile` 字段）
- 现状：
  ```kotlin
  private fun tickOverlay() {
      overlayTicking = true
      sampleMemoryHealth()
      scope.launch {
          val settings = (application as ProjectLumenApplication).settingsRepository().get()   // ← 每 750ms 一次
          handler.post { if (...) { ensureOverlay(); renderOverlay() } else removeOverlay() }
      }
      if (overlayTicking) handler.postDelayed(::tickOverlay, 750L)
  }
  ...
  getSystemService(WindowManager::class.java).addView(container, params)   // 第 204 行，无 try/catch
  private var lastLux = 0f   // 主线程（传感器回调）写，IO 线程（writeSensorRuntime）读，无 @Volatile
  ```
  服务是 `START_STICKY` 且自身从不 `stopSelf`，只能靠 `DeveloperDebugOverlayService.stop()` 外部停止。因此一旦启动，750 ms 的设置轮询 + 1 s 的状态写入会一直跑。`lastLux/lastPitch/lastRoll/lastYaw/lastAccelerationMagnitude` 由主线程的传感器回调写入，却在 `writeSensorRuntime` 的 IO 协程里读取，没有 `@Volatile`。
- 触发场景：开启开发者模式 + 调试悬浮层。轮询在悬浮层被关闭（`developerDebugOverlayEnabled = false`）后**仍然继续**，因为关闭分支只是 `removeOverlay()`，`overlayTicking` 仍为 true。
- 影响：(1) 开发者模式下持续耗电与 CPU 占用（每小时约 4800 次设置读 + 3600 次 MMKV 整行写），并显著放大 [G04-16] 的覆盖窗口；(2) 传感器字段可能读到陈旧值，调试悬浮层显示的姿态/照度数据不可信（调试工具给出错误读数比没有更糟）；(3) 第 204 行 `addView` 与 [G04-13] 同一模式，OEM 拒绝叠加窗时会崩溃（这里权限检查在同一主线程块内，撤销竞态窗口小，故仅 P2）。
- 修复方案：
  1. 把设置轮询改成订阅：用 `settingsRepository().observe()`（或 StateStore 已有的 Flow）驱动"显示/隐藏"，只在需要刷新缩略图时按帧到达刷新，删掉 750 ms 的 `postDelayed` 轮询；至少把间隔放宽到 2~3 s，并在 `developerDebugOverlayEnabled == false` 时把 `overlayTicking` 置 false 停掉轮询链。
  2. `lastLux` 等 5 个字段加 `@Volatile`（或改为 `AtomicReference<SensorSnapshot>` 一次性发布，避免读到半更新的组合值）。
  3. 第 204 行按 [G04-13] 同样方式包 `runCatching`，失败时不赋值 `overlayView`、并把 `previewImage` 置 null。
- 风险/注意：改成订阅后要确认服务在 `developerModeEnabled` 关闭时能自行 `stopSelf`（当前完全依赖外部 `stop()`）；`@Volatile` 只保证单字段可见性，若 UI 要求 5 个字段一致快照则必须用 `AtomicReference` 方案。

### [G04-18] `MemoryHealthMonitor.sample` 是重量级同步调用，却被主线程的悬浮层轮询链每 5 秒调用一次
- 严重度：P2
- 类别：B 并发（主线程阻塞）
- 位置：`app/src/main/java/com/projectlumen/app/core/debug/MemoryHealthMonitor.kt:40-45`（`Debug.getMemoryInfo`）；调用点 `app/src/main/java/com/projectlumen/app/core/debug/DeveloperDebugOverlayService.kt:321-326`（`sampleMemoryHealth`）、`:157`（在主线程的 `tickOverlay` 里）、`:81`（`onStartCommand` 主线程）、`:121`（`onTrimMemory` 主线程）
- 现状：
  ```kotlin
  private fun capture(context: Context, nowMillis: Long, trimLevel: Int?): MemoryHealthSnapshot {
      val processMemory = Debug.MemoryInfo()
      Debug.getMemoryInfo(processMemory)          // ← 需要遍历 /proc/self/smaps，几十~数百毫秒
      ...
  }
  // DeveloperDebugOverlayService.tickOverlay() 在主线程 Handler 链上直接调用
  private fun tickOverlay() { overlayTicking = true; sampleMemoryHealth(); ... }
  ```
  `MemoryHealthMonitor` 本身是同步 API，没有切线程也没有注明"必须在后台线程调用"；`sampleMemoryHealth` 的 5 秒节流（`MEMORY_HEALTH_SAMPLE_INTERVAL_MILLIS`）只限制频率，不改变执行线程。
- 触发场景：调试悬浮层服务运行期间（开发者模式），每 5 秒一次在主线程上做 smaps 遍历；低端机上 `Debug.getMemoryInfo` 耗时可达 100~300 ms。`ProjectLumenApplication:149` 也在调用（该调用点归组合/应用组）。
- 影响：开发者模式下 UI 每 5 秒卡顿一次；`onTrimMemory` 路径在系统内存紧张时又叠加一次主线程 smaps 遍历，恰好是最不该阻塞的时刻。
- 修复方案：把 `MemoryHealthMonitor.sample` / `recordTrim` 改成 `suspend`（内部 `withContext(Dispatchers.IO)`），或保留同步签名但在 KDoc 明确标注"必须在后台线程调用"并把 `DeveloperDebugOverlayService.sampleMemoryHealth` 改为 `scope.launch { ... }`（该服务已有 IO scope）。`onTrimMemory` 里同样改成投递到 IO scope（`recordTrim` 只是更新 `StateFlow`，异步无副作用）。
- 风险/注意：`recordTrim` 目前是同步返回 `MemoryHealthSnapshot`，改 `suspend` 会波及 `ProjectLumenApplication:175` 与 `:149`（跨组关联：应用组）。若只改调用方而不改签名，注意 `capture` 里 `_snapshot.value` 的读改写在并发下可能丢 `lastTrimLevel`（当前是单线程调用，改成并发后需要 `update {}`）。

### [G04-19] 洞察的聚合回退路径会把跨天的 `INTERVAL_DAILY` 桶重复相加，导致「高日曝光」建议误报
- 严重度：P2
- 类别：E 韧性 / A 架构
- 位置：`app/src/main/java/com/projectlumen/app/core/insights/AndroidDeviceInsightDataSource.kt:156-187`（`queryAggregateFallback`）、`:52`（`LOOKBACK_MILLIS` 固定 24 小时）
- 现状：
  ```kotlin
  val stats = usageStatsManager
      ?.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, periodStart, periodEnd)   // 24h 窗口通常跨越午夜
      .orEmpty()
      .filter { it.totalTimeInForeground > 0L }
  ...
  totalForegroundMillis = stats.sumOf { it.totalTimeInForeground }.coerceAtMost(periodDuration)
  ```
  `queryUsageStats(INTERVAL_DAILY, ...)` 返回**所有与查询区间相交的日桶**，每个桶携带的是**整日**前台时长。`periodStart = now - 24h` 几乎必然跨午夜，因此同一个包会拿到"昨天整日 + 今天整日"两个桶，`sumOf` 把它们直接相加（同一 `packageName` 的 `groupBy...sumOf` 也是相加），得到最多约 2 倍的虚高值，只被 `coerceAtMost(24h)` 削顶。
- 触发场景：`queryForegroundTimeline` 返回空 `intervals` 时进入这条回退（部分 OEM 会限制 `queryEvents` 的历史事件，或刚授予使用情况访问权限而事件流被裁剪）。此时 `quality = AGGREGATED_FALLBACK`。
- 影响：`DeviceInsightAnalyzer.recommendations` **完全不看 `quality`**（已核查 `DeviceInsightAnalyzer.kt:67-121`），所以虚高的 `totalForegroundMillis`（阈值仅 4 小时）会让"今日用眼时长过长"建议在回退路径上几乎必然误报；卡片上展示的总时长同样虚高。UI 侧已针对 `AGGREGATED_FALLBACK` 隐藏了"最长连续使用/夜间使用"两项（`ProjectLumenDeviceInsightsCard.kt:156-186`），但没有处理总时长与建议。
- 修复方案：在 `queryAggregateFallback` 里按包只取**与查询窗口重叠比例**折算，或更稳妥地改用 `queryAndAggregateUsageStats(periodStart, periodEnd)`（返回按包聚合后的单一 map，避免多桶重复），并把结果乘以窗口/桶时长的重叠系数；同时给 `DeviceInsightAnalyzer.recommendations` 增加 `quality` 参数，在 `AGGREGATED_FALLBACK` 时跳过依赖精确时长的建议（`HIGH_DAILY_EXPOSURE`、`DOMINANT_VISUAL_APP`）。
- 风险/注意：`DeviceInsightAnalyzer.recommendations` 有单测（`app/src/test/java/com/projectlumen/app/core/insights/DeviceInsightAnalyzerTest.kt:81-114`），加参数需同步更新测试的调用点（含命名参数）。改用 `queryAndAggregateUsageStats` 会丢失 per-bucket 信息，但本路径本来就不需要。

### [G04-20] 应用分类映射不全：相册/图片类应用永远落到 OTHER，`COMMUNICATION` 枚举永不产生
- 严重度：P2
- 类别：A 架构 / H 编译结构
- 位置：`app/src/main/java/com/projectlumen/app/core/insights/AndroidDeviceInsightDataSource.kt:207-216`（`toUsageCategory`）、`app/src/main/java/com/projectlumen/app/core/insights/DeviceInsightModels.kt:17-27`（枚举）、`app/src/main/java/com/projectlumen/app/core/insights/DeviceInsightAnalyzer.kt:203-208`（`VISUALLY_INTENSE_CATEGORIES`）
- 现状：
  ```kotlin
  private fun ApplicationInfo?.toUsageCategory(): AppUsageCategory = when (this?.category) {
      ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppUsageCategory.PRODUCTIVITY
      ApplicationInfo.CATEGORY_SOCIAL -> AppUsageCategory.SOCIAL
      ApplicationInfo.CATEGORY_VIDEO -> AppUsageCategory.VIDEO
      ApplicationInfo.CATEGORY_GAME -> AppUsageCategory.GAME
      ApplicationInfo.CATEGORY_NEWS -> AppUsageCategory.READING
      ApplicationInfo.CATEGORY_MAPS -> AppUsageCategory.NAVIGATION
      ApplicationInfo.CATEGORY_AUDIO -> AppUsageCategory.AUDIO
      else -> AppUsageCategory.OTHER
  }
  ```
  平台还存在 `ApplicationInfo.CATEGORY_IMAGE`（相册/看图/修图）与 `CATEGORY_ACCESSIBILITY`，这里未映射，全部落到 `OTHER`。而 `AppUsageCategory.COMMUNICATION` 在整个仓库里**没有任何产出点**（`ApplicationInfo` 没有对应常量），是一个永不出现的枚举值。
- 触发场景：用户当天用得最多的是相册/修图/漫画类应用（`CATEGORY_IMAGE`）时，它进入 `topApps` 但 `category = OTHER`，不在 `VISUALLY_INTENSE_CATEGORIES` 里。
- 影响：`DOMINANT_VISUAL_APP`（"某个高视觉负荷应用占用了 90 分钟以上"）这条建议对相册/看图类应用永远不触发——而这类应用恰恰是长时间盯屏的典型场景，护眼建议的覆盖面出现缺口。`COMMUNICATION` 则是死枚举，读代码时会误以为有通讯类识别能力。
- 修复方案：在 `toUsageCategory` 增加 `ApplicationInfo.CATEGORY_IMAGE -> AppUsageCategory.IMAGE`（新增枚举值）并把它加入 `VISUALLY_INTENSE_CATEGORIES`；删除永不产生的 `COMMUNICATION`，或改为按包名/`CATEGORY_SOCIAL` 之外的启发式真正产出它（倾向直接删除）。
- 风险/注意：`AppUsageCategory` 是 `public` 枚举且被 UI 层引用（`ProjectLumenDeviceInsightsCard.kt` 会按 category 取图标/文案），增删枚举值需要 UI 组同步补 `when` 分支（否则 Compose 侧的 `when` 不穷尽会编译失败）。这是跨组联动改动，建议与 UI 组一起做。

## 已核查但无问题的点

以下是逐行读过并确认**当前实现正确**的关键设计，修复阶段请勿"顺手改掉"：

- **调试帧不会用到已回收的 Bitmap**：`ProximityCameraSampler.captureFaceDistance:83-89` 先 `DeveloperDebugFrameStore.publish(bitmap, sample)` 再在 `finally` 里 `bitmap.recycle()`，看似危险，但 `DeveloperDebugFrameStore.createDebugThumbnail`（`DeveloperDebugFrameStore.kt:74-84`）内部 `Bitmap.createBitmap` + `Canvas.drawBitmap` 生成的是**独立副本**，原图回收不影响缩略图。不要为此加"延迟回收"。
- **调试帧存储是有界的**：`DeveloperDebugFrameStore` 用 `AtomicReference` 只保留**最新一帧**（240 px 宽缩略图，约 200 KB 级），不是列表；`ProximityDetectionService.onTrimMemory:99-104` 与 `DeveloperDebugOverlayService.onTrimMemory:120-126` 还会在临界内存时 `clear()`。brief 里担心的"无上限帧缓存"不存在。
- **ML Kit 模型是随包内置的**：依赖是 `com.google.mlkit:face-detection:16.1.7` 与 `com.google.mlkit:face-mesh-detection:16.0.0-beta3`（`app/build.gradle.kts:339-340`），不是 `play-services-mlkit-*` 的按需下载版本，因此**不存在"模型下载失败导致功能静默死掉"**这一降级路径需求。（但仍需注意 R8 对 ML Kit `ComponentRegistrar` 的裁剪问题，那属于构建/CI 组。）
- **`ImageProxy` 相关担忧不适用**：本项目**没有使用 CameraX**（无 `ImageAnalysis` / `ImageProxy` / `ProcessCameraProvider`），采集是手写的 Camera2 + `ImageReader`（`ProximityCameraSampler.capturePreviewFrame`）。`Image` 对象在 `setOnImageAvailableListener` 里每条路径都 `runCatching { image.close() }`（`:215-217`、`:340`），`acquireLatestImage()` 返回 null 时直接 return 也无需关闭。真正的泄漏在 `CameraDevice` 一侧，见 [G04-02]。
- **`Handler(Looper.getMainLooper())` 都在实例字段/函数体内，不在顶层或 `object` 静态初始化里**：`EyeProtectionOverlayService.kt:28`、`DeveloperDebugOverlayService.kt:39` 是 Service 实例字段，`ProximityTriggerGate.kt:66` 在函数体内。**不会**引发 brief 提到的纯 JVM 单测 `ExceptionInInitializerError`。修复时不要把它们提升为顶层/companion 字段。
- **悬浮窗 add/remove 严格配对**：`EyeProtectionOverlayService.showOverlay` 第 71 行先 `removeOverlay()` 再 `addView`，`removeOverlay` 用 `overlayView ?: return` + 先置 null 后移除（`:170-176`），`onDestroy` 也会调用；`DeveloperDebugOverlayService.ensureOverlay` 有 `overlayView != null` 短路（`:179`）。不存在重复 `addView` 或漏 `removeView`。
- **悬浮窗旋转/分屏无需额外处理**：两个悬浮窗都用 `WindowManager.LayoutParams`（`MATCH_PARENT` 全屏 / 固定 dp 小窗 + `Gravity.TOP or END`），由 WindowManager 在配置变化时自行重新布局，不依赖 Activity 的配置回调。
- **`foregroundServiceType` 与清单一致**：`ProximityDetectionService` 用 `FOREGROUND_SERVICE_TYPE_CAMERA` 对应清单 `android:foregroundServiceType="camera"`（`AndroidManifest.xml:157-159`）；`LightMonitorService` / `EyeProtectionOverlayService` / `DeveloperDebugOverlayService` 用 `SPECIAL_USE` 且清单里都带了 `<property>` 子标签（`:162-183`）。`startForeground` 统一走 `ForegroundServiceController.promote` 且在 `onStartCommand` 同步路径内调用，不会触发"超时未 startForeground"被杀。
- **`LightMonitorService` 的可变字段无需加锁**：`sensorRegistered`、`lastHandledAt` 只在主线程被触碰（`registerListener` 未传 Handler → 回调走主 Looper；`onStartCommand`/`onDestroy` 也是主线程），不存在跨线程可见性问题。（`DeveloperDebugOverlayService` 的传感器字段不同，见 [G04-17]。）
- **`DeviceInsightAnalyzer` 的区间合并与夜间重叠计算是正确的**：`mergeIntervals`（`:162-185`）先排序再按 `maxGapMillis` 合并，`totalForegroundMillis` 用 `maxGap = 0` 的并集（不会把多应用重叠时间重复计数），`longestContinuousSessionMillis` 用 2 分钟容差合并；`lateNightOverlapMillis`（`:187-200`）从"起始日期减一天"遍历到结束日期，正确覆盖了跨午夜的 22:00~06:00 窗口。该文件有单测覆盖，逻辑不要重写。
- **洞察数据不出设备**：`rg` 确认 `core/api` 与遥测请求模型里没有任何 usage/topApps/foregroundMillis 字段，`DeviceInsightsState` 只进 `DeviceInsightsRepository` 的内存 `StateFlow` 供 UI 使用。使用情况统计（属敏感数据）没有被上传。
- **`DeviceInsightsRepository.refresh` 已有 `Mutex`**：并发刷新会串行化，`refreshDeviceInsights` 由 `LaunchedEffect(permissionRequirements.usageAccess)` 触发，不会形成刷新风暴。
- **`ProximityEventReceiver` 的 `goAsync()` 与迁移锁**：`goAsync` + `finally { pendingResult.finish() }` 配对正确；MMKV 迁移用 companion `Mutex` + 双重检查（`:76-96`），不会重复迁移。
- **花括号平衡**：本组 16 个文件的 `{` / `}` 计数全部相等（脚本核对通过），没有 kapt 会卡住的语法失衡。
- **未发现未使用 import**：逐文件核对了 16 个文件的 import 与使用点，没有可被 lint 卡住的多余 import。
- **`ProximityDetectionService.TRIM_MEMORY_RUNNING_CRITICAL_LEVEL = 15` 数值正确**：与平台 `ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL` 一致，行为无误（仅是硬编码魔法数，替换成平台常量不改变语义）。



