# G04 感知与悬浮层 审查报告（B 版）

> 本报告为 G04 组第二份独立审查（B 版），与 G04-sensing-overlay.md 由不同 agent 独立产出，可用于交叉验证。

- 审查文件数：16，总行数：3330
  - `core/proximity/` 8 个：`ProximityCameraSampler.kt`(671)、`ProximityDetectionService.kt`(509)、`FaceDistanceAnalyzer.kt`(197)、`ProximityDetectionWorker.kt`(88)、`ProximityEventReceiver.kt`(98)、`ProximityTriggerGate.kt`(94)、`FaceDistanceSample.kt`(53)、`ProximityCameraForegroundEligibility.kt`(32)
  - `core/light/LightMonitorService.kt`(207)
  - `core/overlay/EyeProtectionOverlayService.kt`(192)
  - `core/debug/` 3 个：`DeveloperDebugOverlayService.kt`(353)、`DeveloperDebugFrameStore.kt`(136)、`MemoryHealthMonitor.kt`(74)
  - `core/insights/` 3 个：`AndroidDeviceInsightDataSource.kt`(308)、`DeviceInsightAnalyzer.kt`(209)、`DeviceInsightModels.kt`(109)
- 缺陷数：18（P0 2 / P1 11 / P2 5）
- 结论摘要：这一组的**数据结构与算法层是健康的**（NV21 转换、区间合并、前后台事件配对、帧存储上限、隐私面无设备指纹），问题几乎全部集中在**与 Android 框架和硬件打交道的边界**上：相机资源在异常/取消路径上不释放、ML Kit 检测器从头到尾没有 `close()`、悬浮窗 `addView` 裸调用、慢系统调用留在主线程、以及两处越层直写 DAO 的无锁累加。最严重的是 `ProximityCameraSampler` 的 surface 分析管线**在任何机型上都必定失败**（HandlerThread 上没有 EGL 上下文却调 `updateTexImage()`），失败还被 `runCatching` + `getOrNull()` 三层吞掉，表现为"功能静默不可用 + 前摄被每 500ms 反复开关"；其次是每轮采样都新建 `ProximityCameraSampler`、进而新建两个从不关闭的 ML Kit 检测器，属确定性的原生资源泄漏。架构上还有一处系统性隐患：整个运行时状态是一条 MMKV JSON 大对象，本组三个服务高频做"整体读→改→整体写"，会把别的模块（计时/提醒）刚写入的字段整片回滚。

## 缺陷清单

### [G04B-01] Surface 分析管线必定失败：HandlerThread 上没有 EGL 上下文，`updateTexImage()` 恒抛异常

- 严重度：P0
- 类别：A 架构 / E 韧性
- 位置：`app/src/main/java/com/projectlumen/app/core/proximity/ProximityCameraSampler.kt:282-285`（`SurfaceTexture(texName = 0)`）、`:320-342`（回调里 `updateTexImage()`）、`:126-159`（`captureSurfaceAnalysisFrame` 的 `withTimeoutOrNull` 不捕获该异常）
- 现状：
  ```kotlin
  val surfaceTexture = SurfaceTexture(/* texName = */ 0).apply { setDefaultBufferSize(...) }
  ...
  reader.setOnImageAvailableListener({ availableReader ->
      val result = runCatching<SurfaceCapturedFrame?> {
          surfaceTexture.updateTexImage()      // ← 该线程从未创建过 EGL 上下文
  ```
  `SurfaceTexture(int texName)` 是 attached 模式，`updateTexImage()` 要求调用线程当前持有拥有该纹理的 EGL 上下文。AOSP `GLConsumer::checkAndUpdateEglStateLocked()` 在 `eglGetCurrentDisplay() == EGL_NO_DISPLAY` 时返回 `INVALID_OPERATION`，JNI 层随即抛 `IllegalStateException("Unable to update texture contents")`。而 `HandlerThread("ProjectLumenSurfaceAnalysisCamera")` 全程没有任何 EGL 初始化。
- 触发场景：只要 `SilentVisionPolicy.surfaceAnalysisUploadEnabled` 为真就 100% 触发，与机型无关。异常被 `runCatching` 收成 `Result.failure` → `complete()` → `resumeWithException` → 逃出 `captureSurfacePipelineFrame`（`withTimeoutOrNull` 只吞 `TimeoutCancellationException`）→ 逃出 `captureSurfaceAnalysisFrame` → 最终被 `PrivilegedDeviceControlCoordinator.kt:283` 的 `runCatching{}.getOrNull()` 吞成 null。
- 影响：整条"surface 拓扑分析"端到端失效且完全静默（无日志、无遥测、无降级标记）；`PrivilegedDeviceControlCoordinator.startCaptureLoop` 以 `max(1000/maxFps, 500)` 毫秒无限重试，每次完整走一遍"开相机 → 配双 Surface 会话 → 收帧 → 抛异常 → 关相机"，等于持续开关前摄：重度耗电、相机指示灯/隐私图标反复闪烁，并与 `ProximityDetectionService` 抢占同一前置摄像头。
- 修复方案：这条管线的 `SurfaceTexture` 只为"让 preview Surface 作为生产者保持活跃"，并不需要真的消费纹理。最小修复是**删掉 `surfaceTexture.updateTexImage()` 这一行及其上方注释** —— JPEG 数据本就来自 `image.toJpegBytes()`，与 SurfaceTexture 无关，`bufferTransformMillis` 计时逻辑不变。若确实要保留消费纹理的语义，必须为该线程建立 EGL 上下文（`eglCreateContext` + `eglMakeCurrent` + `glGenTextures`，并把真实纹理名传给 `SurfaceTexture(texName)`），成本远大于收益。
- 风险/注意：删除后本管线与 `captureFaceAnalysisFrame` 的差异仅剩"多挂一个 preview Surface 目标"，两者高度重复，建议评估合并；该功能由后端下发策略驱动，修复前应确认产品是否仍需要这条管线。

### [G04B-02] ML Kit 检测器从头到尾没有 `close()`，且每轮采样都新建一个 sampler → 确定性原生资源泄漏

- 严重度：P0
- 类别：C 资源
- 位置：`app/src/main/java/com/projectlumen/app/core/proximity/FaceDistanceAnalyzer.kt:20-36`（`detector` / `meshDetector` 创建，全文无 `close()` 方法）；`ProximityCameraSampler.kt:38-42`（两个 `by lazy` 检测器，类本身也无 `close()`）；调用点 `ProximityDetectionService.kt:150`、`:260` 与 `PrivilegedDeviceControlCoordinator.kt:267-302`
- 现状：
  ```kotlin
  private val detector = FaceDetection.getClient(...)
  private val meshDetector = if (includeTopology) FaceMeshDetection.getClient(...) else null
  ```
  ```kotlin
  private val plainAnalyzer by lazy { FaceDistanceAnalyzer(includeTopology = false) }
  private val topologyAnalyzer by lazy { FaceDistanceAnalyzer(includeTopology = true) }
  ```
  `FaceDetector` / `FaceMeshDetector` 都实现 `Closeable`，官方要求使用完毕必须 `close()` 以释放原生检测器与已加载模型。本组既没有 `FaceDistanceAnalyzer.close()`，`ProximityCameraSampler` 也没有释放入口；而 `ProximityDetectionService.runDetection` 每轮是 `ProximityCameraSampler(this).captureFaceDistanceSamples(...)`（`:150`）—— **每轮新建一个 sampler**，用完即弃。
- 触发场景：每次周期采样（默认按分钟级、开发者模式最短 10 秒）都会新建 1~2 个检测器；`PrivilegedDeviceControlCoordinator.startCaptureLoop` 更是每 ≥500ms 一轮。开启眨眼/拓扑分析时用的是 `face-mesh-detection`（模型更大），泄漏更重。
- 影响：原生内存与文件描述符持续增长，GC 回收不掉（原生侧由检测器持有），长时间运行后 OOM / 被系统低内存杀掉；`onTrimMemory` 也救不回来。属"跑得越久越差"的典型泄漏，测试短时使用完全看不出来。
- 修复方案：①`FaceDistanceAnalyzer` 实现 `Closeable`：`override fun close() { runCatching { detector.close() }; runCatching { meshDetector?.close() } }`；②`ProximityCameraSampler` 增加 `fun close()` 关闭两个 lazy 检测器（用 `lazy` 的 `isInitialized()` 判断，避免为了关闭反而初始化）；③把 `ProximityCameraSampler` 的生命周期上提 —— `ProximityDetectionService` 持有单个实例、在 `onDestroy` 关闭，或调用处用 `use {}`；`PrivilegedDeviceControlCoordinator.startCaptureLoop` 改为循环外创建一次、循环结束 `close()`。
- 风险/注意：检测器 `close()` 后不能再 `process()`（会抛 `IllegalStateException`），所以复用实例时必须保证"关闭"发生在最后一轮之后；配合 [G04B-09] 的可取消 `await()` 一起改，否则 `close()` 会让在途任务以 canceled 结束而当前实现永不 resume。

### [G04B-03] Camera2 资源在"取消 / 同步抛异常"路径上不释放，surface 管线的 `finally` 是空实现

- 严重度：P1
- 类别：C 资源
- 位置：`app/src/main/java/com/projectlumen/app/core/proximity/ProximityCameraSampler.kt:394-396`（空 `finally`，对比 preview 版 `:265-268`）、`:175-182` 与 `:291-299`（`release()` 定义）
- 现状：
  ```kotlin
      } finally {
          // release handled in complete path; keep finally no-op when suspended.
      }
  ```
- 触发场景：①`suspendCancellableCoroutine` 代码块**同步抛出非取消异常**时（例如 `setOnImageAvailableListener` / `createCaptureSession` 直接抛 `IllegalStateException`），`invokeOnCancellation` 不会被调用（它只对取消生效），空 `finally` 也不做任何事；②取消与 `complete()` 竞态时 `finished` CAS 只有一边赢，另一边的 `HandlerThread` 不被 `quitSafely()`。
- 影响：`HandlerThread` + `ImageReader`（YUV_420_888 最高 960×960 双缓冲 ≈ 2.7MB）+ `Surface` + `SurfaceTexture` 全部泄漏。这条路径在 `startCaptureLoop` 里每 ≥500ms 循环一次，叠加 [G04B-01] 的必然异常，泄漏累积极快 —— 线程数会持续上涨直到 `OutOfMemoryError: pthread_create failed`。
- 修复方案：补成与 preview 版一致的幂等兜底，且每步单独包裹：
  ```kotlin
  } finally {
      runCatching { reader.close() }
      runCatching { previewSurface.release() }
      runCatching { surfaceTexture.release() }
      runCatching { thread.quitSafely() }
  }
  ```
  同时删掉那条与实现不符的注释；`release()` 已有 `finished` CAS 保护，重复调用安全。
- 风险/注意：必须逐个 `runCatching`，不能合成一个大的 —— 否则前一步抛异常会跳过后面的清理；`Surface.release()` 与相机线程上的操作并发时确实可能抛异常。

### [G04B-04] 两处越层直写 `DailyEyeStatsDao`，绕过 `StatisticsRepository` 且是无锁 read-modify-write

- 严重度：P1
- 类别：A 架构 / F 持久化
- 位置：`app/src/main/java/com/projectlumen/app/core/proximity/ProximityDetectionService.kt:302-312`（被 `:182`、`:190` 调用）、`app/src/main/java/com/projectlumen/app/core/light/LightMonitorService.kt:183-189`（被 `:127` 调用）
- 现状：
  ```kotlin
  private suspend fun incrementEyeStats(app, nowMillis, transform) {
      if (app.settingsRepository().get()?.statsEnabled == false) return
      val dao = app.database.dailyEyeStatsDao()
      val current = dao.get(date) ?: DailyEyeStatsEntity(statDate = date)
      dao.upsert(transform(current).copy(updatedAt = nowMillis))
  }
  ```
  Service 直接拿 `app.database` 拿 DAO，完全绕过 `core/repositories/StatisticsRepository`（`rg "dailyEyeStatsDao\(\)" app/src/main` 已确认写者不止一处）。同一实体存在至少 3 个写者：`StatisticsRepository`、本服务、`LightMonitorService`。
- 触发场景：`runDetection` 单轮内就会连续调两次 `incrementEyeStats`（`:182` 距离统计、`:190` 干眼统计）；暗光监测每次低光事件写一次；计时模块经 `StatisticsRepository` 写同一行。两者时间上重叠即丢更新（`get()` 与 `upsert()` 之间没有任何互斥，Room 也不会替你加）。
- 影响：①统计数字偷偷变小（用户看到"今天提醒 3 次"实际发生 5 次），且是永久性的数据丢失；②分层被击穿 —— 以后给统计加口径变更（例如去重、按小时聚合）必须同时改三处，漏一处就产生不一致。
- 修复方案：①两处 `incrementEyeStats` / `incrementLowLightStats` 改为调用 `StatisticsRepository` 的对应方法（Service 从 `ProjectLumenApplication` 取仓库，不再触碰 `app.database`）；②**同时**给 `StatisticsRepository` 加 `Mutex`，或给 `DailyEyeStatsDao` 增加原子 SQL 累加（`@Query("UPDATE daily_eye_stats SET proximity_warning_count = proximity_warning_count + :delta ... ")`，行不存在时先 `INSERT OR IGNORE`）—— 只做①不解决丢更新。
- 风险/注意：`StatisticsRepository` 目前也没有锁（见"跨组遗留"），必须一次性把三个写者统一到带锁/原子累加的接口上，否则残留写者继续丢更新；`statsEnabled == false` 的短路语义要在 Repository 侧保留，别在迁移中丢掉。

### [G04B-05] 运行时状态是单条 MMKV JSON 大对象，三个服务并发整体 read-modify-write → 跨模块状态被整片回滚

- 严重度：P1
- 类别：B 并发 / F 持久化
- 位置：`app/src/main/java/com/projectlumen/app/core/debug/DeveloperDebugOverlayService.kt:246-263`（每 1 秒）、`app/src/main/java/com/projectlumen/app/core/light/LightMonitorService.kt:129-138`（每 2 秒）、`app/src/main/java/com/projectlumen/app/core/proximity/ProximityDetectionService.kt:138-140`、`:213-232`、`:277-287`、`:292-299`、`:317-325`（单轮最多 5 次）
- 现状：
  ```kotlin
  runtimeRepository.get()?.let {
      runtimeRepository.upsert(it.copy(ambientLastLux = lastLux, sensorPitchDegrees = lastPitch, ...))
  }
  ```
  已核实 `RuntimeRepository`（`core/repositories/RuntimeRepository.kt:17-35`）背后是 `RuntimeStateMmkvStore`：`get()` 反序列化整条 JSON，`upsert()` 序列化**全部 40+ 字段**整体覆写 `state_json`，且 `upsert` 无任何锁。所以这不是"某一列被覆盖"，而是**整个运行时状态被一秒前的旧快照整片回滚**。
- 触发场景：调试面板开启时每秒读写一次全字段；若这一秒内 `AlarmReceiver` / `TimerForegroundService` 写入了 `reminderPhase=BREAK`、`breakEndAt`、`nextReminderAt`，会被回滚成一秒前的值。非开发者场景同样成立：暗光监测每 2 秒、近距离检测每轮 5 次都整片覆写。
- 影响：休息/番茄计时状态被静默回滚 —— 用户可见为"休息倒计时回跳或不结束""提醒不再触发""番茄阶段错乱"；`proximityLastWarningAt` 被回滚会让通知在冷却期内重复弹出。极难复现定位。
- 修复方案：①交仓库组：给 `RuntimeRepository` 增加 `suspend fun update(transform: (RuntimeStateEntity) -> RuntimeStateEntity)`，内部用 `Mutex` 把"读→变换→写"包住；②本组把上述 6 处 `get()?.let { upsert(it.copy(...)) }` 全改为 `update { it.copy(...) }`；③`DeveloperDebugOverlayService.writeSensorRuntime` 的传感器读数根本不该进持久化运行时状态，改走内存 `StateFlow`。
- 风险/注意：`TimerForegroundService`、`AlarmReceiver` 等别组文件有同类写法，必须全仓库一次性统一，否则残留写者仍会回滚；`update {}` 闭包内不要做挂起/耗时操作（持锁期间）。

### [G04B-06] `MemoryHealthMonitor.capture` 在主线程执行 `Debug.getMemoryInfo`，含冷启动路径与 `onTrimMemory`

- 严重度：P1
- 类别：B 并发（主线程阻塞）/ D 生命周期
- 位置：`app/src/main/java/com/projectlumen/app/core/debug/MemoryHealthMonitor.kt:28-63`（全部是普通同步函数，无 dispatcher）；调用点 `ProjectLumenApplication.kt:149`（`onCreate` 内）、`:175`（`onTrimMemory`）、`DeveloperDebugOverlayService.kt:121`（`onTrimMemory`）、`:321-326`（`tickOverlay` 主 Handler，每 5 秒）
- 现状：
  ```kotlin
  private fun capture(context: Context, nowMillis: Long, trimLevel: Int?): MemoryHealthSnapshot {
      val processMemory = Debug.MemoryInfo()
      Debug.getMemoryInfo(processMemory)        // 慢调用：遍历 /proc/self/smaps
      context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(systemMemory)
      ... processMemory.statKb("summary.java-heap")   // getMemoryStat ×4
  ```
  四个调用点全在主线程，`Application.onCreate` 那处只被 `runCatching` 包住，没有切线程。
- 触发场景：①每次冷启动必然触发一次，`Debug.getMemoryInfo` 在中低端机典型 20~150ms，直接加在首帧之前；②系统下发 `onTrimMemory` 时触发 —— 恰是要求应用尽快让出资源的时刻却做一次慢 IO；③调试面板开启时每 5 秒一次主线程慢调用。
- 影响：冷启动变慢（启动指标 / Baseline Profile 劣化）；内存紧张时主线程卡顿叠加，易被判 ANR 或加速被杀；调试面板开启期间 UI 周期性掉帧。
- 修复方案：新增 `suspend fun sampleAsync(...) = withContext(Dispatchers.IO) { capture(...) }` 与 `recordTrimAsync`，把同步版降为 `internal`（仅单测用）；`DeveloperDebugOverlayService` 用它已有的 `scope`（`Dispatchers.IO`，`:38`）launch；`ProjectLumenApplication:149/:175` 改到应用级 IO scope（属别组文件，需派单）。另把 `_snapshot.value = snapshot` 改成 `_snapshot.update { ... }` 保证并发原子。
- 风险/注意：`sample()` 现有返回值，改挂起后需核对调用点；已确认 `ProjectLumenViewModel:196` 只消费 `MemoryHealthMonitor.snapshot` 这个 `StateFlow`，不依赖返回值。

### [G04B-07] `WindowManager.addView` 未捕获 `BadTokenException`，悬浮窗权限被撤销即进程崩溃

- 严重度：P1
- 类别：D 生命周期
- 位置：`app/src/main/java/com/projectlumen/app/core/overlay/EyeProtectionOverlayService.kt:110`、`app/src/main/java/com/projectlumen/app/core/debug/DeveloperDebugOverlayService.kt:204`
- 现状：
  ```kotlin
  windowManager.addView(view, params)      // 裸调用，无 try/catch
  overlayView = view
  ```
  两处的 `Settings.canDrawOverlays()` 检查分别在 `onStartCommand:37` 与 `tickOverlay:164`，与 `addView` 之间存在时间窗；对应的 `removeView`（`:170-176` / `:221-227`）都有 `runCatching`，唯独 `addView` 没有。
- 触发场景：①用户在休息倒计时（最长 300 秒）进行中撤销"显示在其他应用上层"权限；②国内 ROM（MIUI / HarmonyOS / ColorOS）的"后台弹出界面"是独立于 `canDrawOverlays` 的二次开关，关闭时 `canDrawOverlays` 返回 true 而 `addView` 仍被拒 —— 这是最常见的真实路径；③`EyeProtectionOverlayService.show()` 有 5 个调用点（`AlarmReceiver:74`、`TimerForegroundService:254`、`ReminderActionReceiver:74`、`LumenOpenRuntimeController:179`、本服务 `:197`/`:205`），触发频繁。
- 影响：整个进程崩溃（未捕获异常发生在 `onStartCommand` 主线程），用户在休息提醒弹出瞬间闪退、计时状态丢失；国产 ROM 上可能必现。
- 修复方案：两处改为"失败即优雅退出"：`val added = runCatching { windowManager.addView(view, params) }.isSuccess; if (!added) { stopSelf(); return }`，`overlayView` 仍只在成功后赋值。`EyeProtectionOverlayService` 失败时应退化为普通通知提醒（避免"到点了什么都没发生"）；`ensureOverlay` 失败时要把 `previewImage` 复位为 null。
- 风险/注意：不要把 `overlayView = view` 提前到 `addView` 之前，否则 `removeOverlay` 会对未添加的 view 调 `removeView` 抛 `IllegalArgumentException`。

### [G04B-08] `ProximityDetectionService` 无重入保护，多次 `onStartCommand` 会并发抢占同一前置摄像头

- 严重度：P1
- 类别：B 并发
- 位置：`app/src/main/java/com/projectlumen/app/core/proximity/ProximityDetectionService.kt:42-65`
- 现状：
  ```kotlin
  scope.launch {
      runCatching { runDetection(app, calibrate) }...
      stopSelf(startId)
  }
  return START_STICKY
  ```
  每次 `onStartCommand` 无条件再 launch 一个 `runDetection`，没有 `AtomicBoolean` / `Mutex` / 会话判重；而 `runDetection` 内部还会先后开两次相机（`:150` 采样、`:260` 上传帧）。
- 触发场景：`ProximityDetectionWorker` 注册了两个互不相干的唯一工作 —— `project-lumen-proximity-sample`（`:49`）与 `project-lumen-proximity-calibration`（`:50`）。用户点"校准"时若周期采样刚好到点，两个 worker 各自 `ProximityDetectionService.start()`，服务已存在 → 第二次 `onStartCommand` → 两个 `runDetection` 并发。
- 影响：Camera2 对同一 cameraId 的第二次 `openCamera` 会踢掉先前客户端（先前设备收到 `onDisconnected`），于是两轮采样都拿不到有效帧：校准把基线写成无效值（`:155` 只要任一维度 > 0 就落库），周期采样记成"未检测到人脸"。用户可见为"校准失败 / 校准后判定完全不准"，偶发难复现。
- 修复方案：加进程内互斥 —— `private val detectionRunning = AtomicBoolean(false)`，`onStartCommand` 里 `if (!detectionRunning.compareAndSet(false, true)) { stopSelf(startId); return START_NOT_STICKY }`，协程 `finally` 里复位。更稳妥是用伴生对象的 `Mutex` 串行化（而非丢弃请求），并让 `calibrate` 请求优先等待而不是被丢掉。另建议 `START_STICKY` 改 `START_NOT_STICKY`：这是一次性采样服务，粘性重启只会在后台被 eligibility 立刻挡掉，白费一次进程唤醒。
- 风险/注意：`stopSelf(startId)` 依赖 startId 配对，每条早退路径必须恰好 `stopSelf` 一次（`startCameraForeground:120-122` 与 `onStartCommand:48` 现在重复调用，无害但重构时应收敛为一处）。

### [G04B-09] ML Kit `Task.await()` 用不可取消的 `suspendCoroutine` 且漏掉 canceled 终态 → 协程可能永久挂起

- 严重度：P1
- 类别：E 韧性 / B 并发
- 位置：`app/src/main/java/com/projectlumen/app/core/proximity/FaceDistanceAnalyzer.kt:157-162`（调用点 `:44`、`:105`）
- 现状：
  ```kotlin
  private suspend fun <T> Task<T>.await(): T = suspendCoroutine { continuation ->
      addOnSuccessListener { continuation.resume(it) }
      addOnFailureListener { continuation.resumeWithException(it) }
  }
  ```
  ①GMS `Task` 被取消时既不回调 success 也不回调 failure（需 `addOnCanceledListener` / `addOnCompleteListener` 才能观察），此时 continuation 永不 resume；②用的是 `suspendCoroutine` 而非 `suspendCancellableCoroutine`，外层取消也叫不醒它；③`analyze()` 整体没有超时保护 —— `withTimeoutOrNull` 只包住了 `capturePreviewFrame`（`:76`、`:100`、`:134`），推理阶段在超时之外。
- 触发场景：检测器已 `close()` 后仍有在途任务（正是 [G04B-02] 修复后必然出现的情形）、Play 服务模块推理途中被更新或杀掉，任务会以 canceled 结束；更常见的是 `meshDetector?.process(image)`（`:105`，`face-mesh-detection:16.0.0-beta3`）在模型下载/初始化异常时长时间不回调。
- 影响：`runDetection` 协程永不结束 → `stopSelf(startId)`（`:62`）永不执行 → **前台服务与相机通知常驻**、`proximityMonitoringActive` 卡在 true（UI 一直显示"正在检测"）、相机资源被持有；叠加下一轮采样还会来抢相机。
- 修复方案：改为覆盖三种终态的可取消实现：
  ```kotlin
  private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
      addOnCompleteListener { t ->
          when {
              t.isCanceled -> c.cancel()
              t.exception != null -> c.resumeWithException(t.exception!!)
              else -> c.resume(t.result)
          }
      }
  }
  ```
  并在 `ProximityCameraSampler` 的三处调用点把 `analyzer.analyze(...)` 也包进 `withTimeoutOrNull(2_500L)`（超时视为本帧无人脸）。
- 风险/注意：`c.cancel()` 会让 `analyze()` 抛 `CancellationException`，需与 [G04B-15] 一起改，避免被当成真实故障上报。

### [G04B-10] 眨眼采样率约 0.5Hz，远低于眨眼时长 → `blinkCount` 恒为 0，干眼提醒系统性误报

- 严重度：P1
- 类别：A 架构（算法与采样不匹配）
- 位置：`app/src/main/java/com/projectlumen/app/core/proximity/ProximityCameraSampler.kt:44-65`（`sampleIntervalMillis = 900L`，单帧预算 ≤1500ms）；`ProximityDetectionService.kt:142-149`（`captureSeconds` 上限 15）、`:399-409`（`countBlinkTransitions`）、`:412-416`（`blinkRatePerMinute`）
- 现状：
  ```kotlin
  val captureBudgetMillis = (deadline - System.currentTimeMillis()).coerceAtMost(1_500L)
  if (captureBudgetMillis < 750L) break
  captureFaceDistance(maxDurationMillis = captureBudgetMillis, ...)?.let(samples::add)
  val remaining = deadline - System.currentTimeMillis()
  if (remaining > sampleIntervalMillis) delay(sampleIntervalMillis.coerceAtLeast(300L))
  ```
  即"单帧最多 1.5 秒 + 固定 0.9 秒间隔"，实测节奏约 1.7~2.4 秒一帧；15 秒窗口最多约 6~8 帧。而 `countBlinkTransitions` 是在这串 `eyeOpenProbability` 上数"高→低→高"跳变。
- 触发场景：人类一次眨眼闭眼时长约 100~400ms。以 2 秒一帧采样，命中闭眼瞬间的概率约 5%~20%，且"高→低→高"需要连续三帧配合，实际几乎不可能成立。因此 `blinkCount` 绝大多数轮次为 0 → `blinked = false` → `dryForMillis` 持续增长 → 一旦超过 `blinkNoBlinkThresholdSeconds` 且过了冷却期就报干眼。
- 影响：用户正常眨眼却被反复提醒"你很久没有眨眼了"，还会叠加全屏休息悬浮窗（`:204-211`），并把 `eyeDryWarningCount` 累加成虚高统计 —— 这是功能层面的端到端不可信，比崩溃更伤信任。
- 修复方案：眨眼检测不能沿用"逐帧开关相机"的采样方式，必须改成**一次会话内连续取帧**：在 `ProximityCameraSampler` 增加"打开相机一次 → `setRepeatingRequest` 持续出帧 → 对每帧跑 `detector`（≥10fps）→ 收集 `eyeOpenProbability` 序列 → 关闭"的专用入口，眨眼判定只用这条路径。若短期无法改造，退而求其次：把干眼判定从"数眨眼次数"改为"统计闭眼帧占比 / 平均 `eyeOpenProbability` 低于阈值的持续时长"（对低采样率鲁棒），并在 `blinkMonitoringEnabled` 的设置项上明确标注为实验特性。
- 风险/注意：连续取帧会显著提高功耗与发热，需要限制单轮时长（建议 ≤10 秒）与频率；`evaluateBlinkState` 的单测期望值需同步更新；改判定口径会影响 `blinkLastBlinkAt` / `averageBlinksPerMinute` 两个已持久化字段的语义。

### [G04B-11] Worker 自续链在"相机前台不可用"时直接断掉，周期检测永久停止

- 严重度：P1
- 类别：E 韧性 / D 生命周期
- 位置：`app/src/main/java/com/projectlumen/app/core/proximity/ProximityDetectionWorker.kt:24-29`（对比 `:30-38` 的正确写法）
- 现状：
  ```kotlin
  if ((calibrate || monitoringEnabled) &&
      !ProximityCameraForegroundEligibility.canStartCameraForegroundService(applicationContext)
  ) {
      return Result.success()      // ← 没有 enqueueNext，链条到此为止
  }
  ```
  周期采样完全依赖"每次 `doWork` 末尾再 `enqueueNext` 一次"的自续链（`:42-44`）。而 Shizuku 延后分支（`:30-38`）就记得先 `enqueueNext` 再返回 —— 这条分支忘了。
- 触发场景：`canStartCameraForegroundService` 要求相机权限 + 进程处于前台。**用户把应用切到后台**（最常见）或临时撤销相机权限时，下一次 worker 醒来必然走进这个分支 → 链条断裂 → 之后再也没有周期采样。恢复只能靠 `ProximityEventReceiver` 收到 `ACTION_USER_PRESENT`（解锁）才 `enqueueNext(delaySeconds = 0)` 重新起链（`ProximityEventReceiver.kt:45`），且该重启还有 60 秒最小间隔与 TriggerGate 两道门。
- 影响：近距离/眨眼监测在后台静默停摆，用户以为一直在保护，实际只在"应用前台可见 + 刚解锁后"才生效；设置界面仍显示已开启。属功能端到端失效级别，只是不崩。
- 修复方案：该分支改为"先续链再返回"：`enqueueNext(applicationContext, delaySeconds = minOf(120, settings?.proximityIntervalSeconds() ?: 120)); return Result.success()`。更彻底的做法是把"是否续链"收口成 `doWork` 的 `finally`：只要 `monitoringEnabled && !calibrate` 就一定安排下一次，各早退分支只决定"这一轮做不做事"。
- 风险/注意：`enqueueNext` 用 `ExistingWorkPolicy.REPLACE` + 唯一名 `project-lumen-proximity-sample`，重复安排不会产生并行链，安全；但要避免 `delaySeconds = 0` 的忙等（后台不可用时应退避到 ≥60 秒），否则前台不可用期间会变成高频空转唤醒。

### [G04B-12] 光感自动亮度把系统亮度模式永久改成手动，且无平滑 / 滞回

- 严重度：P1
- 类别：A 架构 / E 韧性
- 位置：`app/src/main/java/com/projectlumen/app/core/light/LightMonitorService.kt:145-167`（配合 `:98-105` 的 2 秒节流）
- 现状：
  ```kotlin
  val ratio = (lux.coerceIn(0f, 500f) / 500f)
  val percent = (min + (max - min) * ratio).roundToInt().coerceIn(1, 100)
  ...
  Settings.System.putInt(contentResolver, SCREEN_BRIGHTNESS_MODE, SCREEN_BRIGHTNESS_MODE_MANUAL)
  Settings.System.putInt(contentResolver, SCREEN_BRIGHTNESS, brightness)
  ```
  亮度是瞬时 lux 的线性映射，无滑动平均、无滞回、无最小变更步长，节流仅 2 秒；`onDestroy:87-94` 只注销传感器，**从不恢复** `SCREEN_BRIGHTNESS_MODE`。
- 触发场景：开启 `autoBrightnessEnabled` 且授予 `WRITE_SETTINGS` 后，走动、手掌短暂遮挡光感、屏幕反光变化都会让 lux 在几十~几百跳动，于是每 2 秒亮度跳一档。关闭本功能或卸载应用后系统亮度模式仍停在"手动"。
- 影响：①亮度每 2 秒可见跳变，护眼功能反而造成视觉不适；②用户的系统"自动亮度"被静默永久关闭且无任何提示，需用户自己去系统设置里恢复 —— 对系统全局设置的不可逆副作用。
- 修复方案：①加指数滑动平均 + 滞回（`abs(percent - lastApplied) >= 5` 才写入），节流放宽到 5~10 秒；②首次改写前读出原 `SCREEN_BRIGHTNESS_MODE` 存入 `EyeCarePreferencesDataStore`，在 `onDestroy` 与用户关闭开关时恢复（恢复前比对"当前值是否仍是我们写入的 MANUAL"，避免覆盖用户新选择）；③lux→亮度曲线改对数（`ln(1+lux)/ln(1+500)`），贴合人眼感知。
- 风险/注意：`extraDimPercentForAutoBrightness`（`:169-181`）的 Shizuku 额外调光与本改造耦合，需一起复核；优先走 Shizuku 路径（`:151-160` 已存在）可完全避免改系统全局设置。

### [G04B-13] 缺少 ML Kit `ComponentRegistrar` 的 R8 keep 规则，minify release 下 `getClient()` 可能 NPE

- 严重度：P1（需确认：本仓库 release APK 未实测；同机 Synapse-Client 项目在同类 R8 配置下已实测复现，若复现应升 P0）
- 类别：H 编译与结构 / D 生命周期
- 位置：`app/src/main/java/com/projectlumen/app/core/proximity/FaceDistanceAnalyzer.kt:20`、`:32`；`app/proguard-rules.pro`（121 行，`rg -i "mlkit|ComponentRegistrar"` 零命中）
- 现状：`app/build.gradle.kts:198` `isMinifyEnabled = true`；`gradle.properties` 未设 `android.enableR8.fullMode=false`（AGP 默认开启 full mode）；proguard 还启用了 `-allowaccessmodification`、`-overloadaggressively`、`-repackageclasses`、`-optimizationpasses 5`（`:115-121`）。依赖 `com.google.mlkit:face-detection:16.1.7` / `face-mesh-detection:16.0.0-beta3`。ML Kit 通过 manifest 的 `MlKitComponentDiscoveryService` meta-data 声明各 `ComponentRegistrar`，运行时反射 `getDeclaredConstructor().newInstance()`；其 consumer 规则只 keep 类名而未 keep 无参构造，full mode 下未被静态引用的默认构造会被移除 → 组件发现失败 → `getClient()` 抛异常。
- 触发场景：仅 minify 的 release APK 触发（CI 的 `testDebugUnitTest` / `lintDebug` 都不会暴露），用户第一次开启近距离 / 眨眼监测即触发。
- 影响：release 包里近距离与眨眼检测整条功能不可用（`runDetection` 抛异常 → `recordHandledFailure` → 静默无提醒），debug 包却完全正常，属极易漏测的发布事故。
- 修复方案：在 `app/proguard-rules.pro` 末尾"anti-decompilation"段之前追加：
  ```
  # ML Kit 反射实例化 ComponentRegistrar，R8 full mode 会移除未被静态引用的无参构造。
  -keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
  -keep class com.google.mlkit.common.internal.** { <init>(); }
  -keepclassmembers class * extends com.google.mlkit.common.sdkinternal.** { <init>(...); }
  -dontwarn com.google.mlkit.**
  ```
  验证：推送后在 GitHub Actions 产出的 release APK 上实际开启一次近距离监测（本机禁止构建）。
- 风险/注意：`proguard-rules.pro` 是共享文件，多组可能同时改，需指定唯一修改者避免冲突；通配 keep 只保留构造函数，体积影响可忽略。

### [G04B-14] 休息悬浮窗每次 `show()` 新增一条倒计时 Handler 链且不清旧链；且窗口可获焦点、无退出途径

- 严重度：P2
- 类别：C 资源 / D 生命周期
- 位置：`app/src/main/java/com/projectlumen/app/core/overlay/EyeProtectionOverlayService.kt:70-114`、`:103-127`、`:156-168`
- 现状：
  ```kotlin
  private fun showOverlay(...) {
      removeOverlay()          // 只移除 view，没有 handler.removeCallbacks(countdownTicker)
      ...
      tickCountdown()          // 又起一条 250ms 自续链
  }
  private fun tickCountdown() { ...; handler.postDelayed(countdownTicker, 250L) }
  ```
  `handler.removeCallbacksAndMessages(null)` 只出现在 `onDestroy:63`。另外 `WindowManager.LayoutParams`（`:103-109`）未设 `FLAG_NOT_FOCUSABLE`，视图 `isFocusable = true`（`:83`）且不处理按键，`forceImmersive`（`:135-144`）隐藏状态栏与导航栏，`durationSeconds` 上限 300（`:57`）。
- 触发场景：`show()` 有 5 个调用点，`ProximityDetectionService.runDetection` 单轮内就可能连调两次（`:196-203` 距离提醒、`:204-211` 眨眼提醒）；服务存活期间每来一次 `show()` 就多一条链。
- 影响：①主线程 250ms 唤醒次数随 `show()` 次数线性增长，属持续无谓唤醒与耗电；②全屏可获焦点窗口抢走前台应用输入焦点，用户正在输入的内容被打断、返回键投递给该窗口但无人处理等于失效，最长 5 分钟除息屏或强杀外无法退出（正在通话或导航时会被完全遮挡）。
- 修复方案：①`showOverlay` 开头加 `handler.removeCallbacks(countdownTicker)`（`countdownTicker` 是单一 Runnable 实例，`:29`，可精确移除）；把 `:196-211` 的两次 `show()` 合并成一次（否则第二次会立刻替换掉第一条提醒，用户看不到距离提醒）；②给窗口加 `FLAG_NOT_FOCUSABLE`（保留 `isClickable = true` 的 view 消费触摸即可继续阻挡下层操作），并提供"跳过本次休息"按钮或前台通知上的"结束休息"操作；建议 `showOverlay` 前检测通话状态，通话中降级为普通通知。
- 风险/注意：不要把 `countdownTicker` 改成方法引用 `::tickCountdown`（每次求值都是新对象，`removeCallbacks` 会失效）——`DeveloperDebugOverlayService:174` 的 `::tickOverlay` 就是反例，那里只能靠 `removeCallbacksAndMessages(null)` 兜底。

### [G04B-15] 调试面板服务在设置关闭后仍每 750ms 空转；`runCatching` 吞掉 `CancellationException`

- 严重度：P2
- 类别：E 韧性 / B 并发
- 位置：`app/src/main/java/com/projectlumen/app/core/debug/DeveloperDebugOverlayService.kt:155-176`；`app/src/main/java/com/projectlumen/app/core/proximity/ProximityDetectionService.kt:56-63`
- 现状：
  ```kotlin
  private fun tickOverlay() {
      overlayTicking = true
      ...
      if (overlayTicking) handler.postDelayed(::tickOverlay, 750L)   // overlayTicking 仅在 onDestroy 置 false
  }
  ```
  ```kotlin
  runCatching { runDetection(app, calibrate) }
      .onFailure { app.recordHandledFailure(it); clearActiveState(app) }   // CancellationException 也走这里
  ```
  设置关闭时 `tickOverlay` 只 `removeOverlay()`，从不 `stopSelf()`；`if (overlayTicking)` 恒真。
- 触发场景：①用户关掉调试悬浮窗但不关开发者模式（只有 `ProjectLumenApplication:404` 才会 `stop`），服务永久留驻；②任何采样中途停止服务的操作（用户关闭监测、系统回收前台服务、`stopService`）都会让取消异常被当成业务故障上报。
- 影响：①前台服务通知常驻且不会自行消失，每 750ms 一次设置读取 + 主线程 post、每 5 秒一次主线程 `Debug.getMemoryInfo`、每 1 秒一次运行时状态整体写入，而界面上什么都没显示 —— 持续唤醒、明显耗电；②故障遥测被噪声污染，掩盖真实崩溃趋势。另有一处侥幸未爆的隐患：`clearActiveState` 在已取消的协程里仍能成功，仅因 `RuntimeRepository` 现为纯 MMKV 实现、`get()`/`upsert()` 标了 `suspend` 但实际不挂起（不检查取消）；一旦其回到 Room 或加入真正的挂起点，取消路径会在此抛出，`proximityMonitoringActive` 将永久停在 true。
- 修复方案：①`tickOverlay` 的 else 分支改为 `removeOverlay(); overlayTicking = false; stopSelf(); return`，节流放宽到 2 秒；②`onFailure` 里先 `if (throwable is CancellationException) throw throwable` 再上报，状态复位改用应用级 scope 或 `NonCancellable`。
- 风险/注意：`stopSelf()` 后需确认设置由"关"变"开"能重新拉起服务（`ProjectLumenApplication:400` 已覆盖），否则会变成"关一次再也开不起来"；`throw throwable` 后要核对 `CoroutineExceptionHandler`（`:34-40`）不会重复上报。

### [G04B-16] `ProximityEventReceiver` 静态注册 `ACTION_CONFIGURATION_CHANGED` 是死代码；解锁事件用 `REPLACE` 重置周期链

- 严重度：P2
- 类别：D 生命周期 / A 架构
- 位置：`app/src/main/java/com/projectlumen/app/core/proximity/ProximityEventReceiver.kt:55-58`、`:45`；配合 `ProximityDetectionWorker.kt:53-63`
- 现状：
  ```kotlin
  private val triggerActions = setOf(
      Intent.ACTION_USER_PRESENT,
      Intent.ACTION_CONFIGURATION_CHANGED,
  )
  ```
  `ACTION_CONFIGURATION_CHANGED` 是 `FLAG_RECEIVER_REGISTERED_ONLY` 广播，**只投递给运行时注册的接收器**，manifest 静态声明永远收不到（已在 `AndroidManifest.xml:138-186` 确认该接收器是静态声明）。因此这一半触发源是死代码。
- 触发场景：①横竖屏切换/折叠屏展开时本应触发的"重新采样"从来没生效过；②`ACTION_USER_PRESENT` 分支走 `enqueueNext(app, delaySeconds = 0)`，而 `enqueueNext` 用的是唯一名 + `ExistingWorkPolicy.REPLACE` —— 会**取消掉正在等待的周期链**并立刻跑一轮，之后周期从此刻重新计时。频繁解锁屏幕（一天几十次）就等于把周期采样节奏完全打乱成"每次解锁一次"。
- 影响：①用户预期的"转屏后重新检测"功能不存在（无报错、无日志，纯静默失效）；②采样节奏被解锁事件主导，`MIN_EVENT_TRIGGER_INTERVAL_MS = 60_000L` 只限制了 1 分钟，重度用户实际采样频率远高于设置值 → 耗电与相机指示灯频繁触发，与设置项显示的间隔不符。
- 修复方案：①要么删掉 `ACTION_CONFIGURATION_CHANGED`（推荐：转屏重采的收益很低），要么在某个长生命周期组件里 `registerReceiver` 运行时注册；②`ProximityEventReceiver` 不要复用 `enqueueNext`（它是周期链的续链函数），改为 `enqueueUniqueWork("project-lumen-proximity-event", ExistingWorkPolicy.KEEP, 立即执行的一次性请求)`，与周期链彼此独立，解锁事件就不会重置周期。
- 风险/注意：新增第三个唯一工作名后，`cancel(context)`（`ProximityDetectionWorker.kt:76-78`）只取消了 sample 链，需要同步取消 event 与 calibration 两个工作，否则"关闭监测"后仍会残留一次采样。

### [G04B-17] `recordServiceStop` 在 `onDestroy` 里新建游离 `CoroutineScope(Dispatchers.IO)`，写入可能永不发生

- 严重度：P2
- 类别：B 并发 / D 生命周期
- 位置：`app/src/main/java/com/projectlumen/app/core/debug/DeveloperDebugOverlayService.kt:301-310`（`onDestroy:88` 调用，`scope.cancel()` 在 `:94`）
- 现状：
  ```kotlin
  private fun recordServiceStop() {
      ...
      CoroutineScope(Dispatchers.IO).launch { ... }   // 每次新建，无人持有、无人取消
  ```
  这里显然是为了绕开紧随其后的 `scope.cancel()`（`:94`），但代价是启动了一个不受任何生命周期约束的 scope。
- 触发场景：`onDestroy` 常常发生在进程即将被系统回收时（低内存杀进程、用户滑掉任务卡）。游离协程还没被调度，进程就没了。
- 影响：运行时状态里的"服务已停止"标记写不进去 → UI 与遥测长期显示调试服务仍在运行；如果 `onDestroy` 频繁发生（服务反复起停），每次都新建一个 scope，属于隐蔽的对象与线程调度浪费。
- 修复方案：改用应用级长生命周期 scope（`ProjectLumenApplication` 已有的 IO scope），并把写入包进 `withContext(NonCancellable)`；服务自身的 `scope` 保持只负责服务期内的工作。若这条状态确实重要，更稳的做法是在服务启动时就用"心跳时间戳 + 超时视为已停止"表达，而不是依赖 `onDestroy` 一定能写成功。
- 风险/注意：同一模式在 `ProximityDetectionService` 的 `clearActiveState` 路径上也存在（见 [G04B-15]），修复时一并统一到应用级 scope，避免两种写法并存。

### [G04B-18] `ProximityTriggerGate` 只在开发者模式下生效，普通用户完全没有静止/防抖门禁

- 严重度：P2
- 类别：A 架构
- 位置：`app/src/main/java/com/projectlumen/app/core/proximity/ProximityTriggerGate.kt:16-26`（`:17` 直接 `return true`）
- 现状：
  ```kotlin
  suspend fun canRun(settings: AppSettingsEntity): Boolean {
      if (!settings.developerModeEnabled) return true
      if (!settings.developerStillnessTriggerEnabled && !settings.developerShakeSuppressionEnabled) return true
  ```
  这个门禁的作用是"设备在剧烈运动时跳过本轮采样"（走路、乘车时人脸距离数据没有意义），但对非开发者用户直接放行。另外它是**运动门禁而非距离门禁**：距离判定侧（`ProximityDetectionService.isTooClose:342`）只有冷却时间（`proximityAlertCooldownSeconds`），没有滞回区间。
- 触发场景：①普通用户在走路 / 乘车时被采样，`faceWidthPercent` 因抖动虚高 → 误报"离屏幕太近"；②距离在阈值附近抖动（例如比例在阈值上下 1% 徘徊）时，每过一次冷却期就报一次，用户体验为"提醒反复弹"。
- 影响：误报导致的通知与全屏悬浮窗打扰，是护眼类应用最容易被卸载的原因；开发者模式下的两个开关名字（`developerStillnessTriggerEnabled` / `developerShakeSuppressionEnabled`）说明作者已经想到了这个问题，只是没有对普通用户开放。
- 修复方案：①把静止门禁提为默认行为：`if (!settings.developerModeEnabled) return` 改为按一个新的普通设置项（默认开启）判断，开发者开关只用于"强制关闭门禁以便调试"；②`isTooClose` 增加滞回：进入告警用 `proximityThresholdPercent`，解除告警要低于 `threshold - 5`，并在运行时状态里记录当前是否处于"过近"态（`RuntimeStateEntity` 已有 `proximityLastWarningAt`，可再加一个布尔），避免边界抖动反复报警。
- 风险/注意：`sampleMotion` 会额外注册加速度计 + 陀螺仪 650ms，对普通用户全量开启会增加少量功耗（`SENSOR_DELAY_GAME` 偏高，可降到 `SENSOR_DELAY_UI`）；`STILL_GYRO_THRESHOLD = 0.18f` 的阈值只在开发者场景验证过，全量前建议先灰度。

## 已核查但无问题的点

1. **`ImageReader` 帧释放是正确的**（`ProximityCameraSampler.kt:201-219`、`:320-342`）：`image.close()` 在独立 `runCatching` 里、位于结果构造之后，无论 `toJpegBytes()` 是否抛异常都会执行，不存在"漏 close 导致背压耗尽"。`acquireLatestImage()` 对已关闭 reader 返回 null 而非抛异常（AOSP `acquireNextSurfaceImage` 在 `!mIsReaderValid` 时返回 `ACQUIRE_NO_BUFS`），所以 `quitSafely()` 后的残留消息不会崩。
2. **`Bitmap` 生命周期正确**（`:78-89`、`:102-118`、`:136-158`）：三处解码的 bitmap 都在 `finally { bitmap.recycle() }` 释放；`DeveloperDebugFrameStore.publish` 通过 `Bitmap.createBitmap` + `canvas.drawBitmap` 生成**独立缩略图副本**（`DeveloperDebugFrameStore.kt:74-84`），所以调用方随后 recycle 原图不会让调试面板画到已回收 bitmap。
3. **调试帧存储内存有上限**（`DeveloperDebugFrameStore.kt:21`、`:51-72`）：`AtomicReference` 只留最新一帧，缩略图宽度上限 240px（≈170KB）。旧缩略图不 `recycle()` 是正确选择（可能仍被 `ImageView` 引用，API 26+ 像素数据由 GC 经 NativeAllocationRegistry 回收）。`ProximityDetectionService.onTrimMemory:99-104`、调试服务 `:120-126`（`TRIM_MEMORY_RUNNING_CRITICAL`）与 `onDestroy:93` 都会清空，符合预期。
4. **`LightMonitorService` 传感器注册/注销严格配对**（`:67-77` / `:87-94`），采样率为 `SENSOR_DELAY_NORMAL` 而非 `SENSOR_DELAY_FASTEST`，另有 2 秒业务节流（`:101`）；**无光感设备优雅降级**（`:62-66` 直接 `stopSelf` + `START_NOT_STICKY`）。`sensorRegistered` / `lastHandledAt` 虽是普通 `var`，但读写全在主线程（`registerListener` 未传 Handler 即主 Looper），无需 `@Volatile`。
5. **`ProximityTriggerGate` 的传感器与协程配对正确**（`:55-74`）：`finish()` 先 `unregisterListener` 再判 `continuation.isActive` 才 resume，`invokeOnCancellation` 也注销；外层 `withTimeoutOrNull(900L)` > 内层 `postDelayed(650L)`，超时窗口留有余量；`sampleMotion` 内的 `var` 全在主 Looper 上读写。**没有**"取消后 continuation 二次 resume"的问题。
6. **隐私面无设备指纹级读取**：`AndroidDeviceInsightDataSource` 全文没有 IMEI / 序列号 / MAC / Android ID；权限缺失是**降级**而非抛异常（`hasUsageStatsAccess()` false → `USAGE_ACCESS_REQUIRED`，服务为 null → `RESTRICTED`，`collectUsage` 抛异常 → `RESTRICTED` + `failureReason`）。已确认这些使用洞察**不上传后端**（`rg "topApps|DeviceUsageSummary|foregroundMillis"` 在 `core/telemetry` 与 `core/api` 零命中），仅供本地 UI（`ProjectLumenDeviceInsightsCard`）展示，应用名截断 80 字符。
7. **人脸帧上传是双开关且默认关闭**：`uploadFaceAnalysisFrameIfEnabled:254-265` 同时要求 `diagnosticTelemetryUploadEnabled` 与 `diagnosticFaceAnalysisUploadEnabled`，`AppSettingsEntity:100`/`:103` 默认均 `false`，还要过 `backendConnectivity.decision(FACE_ANALYSIS).executable`；`proximityMonitoringEnabled` / `blinkMonitoringEnabled` / `developerDebugOverlayEnabled` 默认也都是 `false`。**与主报告在此条上结论不同**：我认为当前默认态与门禁是合规的，真正的问题在上传内容缺少用户可见的"已上传 N 张人脸帧"审计入口（属产品透明度，不构成缺陷）。
8. **洞察数据源的耗时调用都在 IO 上**：`collect()` 整体 `withContext(ioDispatcher)`（`:31`）；`registerReceiver(null, filter)` 读粘性电量广播在 `targetSdk 34+` 无需导出标志（null receiver 豁免）且被 `runCatching` 包裹；刷新频率合理（只在应用进入前台时一次，`AppLifecycleCoordinator:40`），不是每次重组。
9. **`DeviceInsightAnalyzer` 是纯函数且边界完备**：`mergeIntervals` 先排序再合并；`lateNightOverlapMillis` 从 `startDate-1` 到 `endDate` 逐日求交（24 小时窗口最多 3 次迭代）；`recommendations` 最后 `distinctBy(kind)` 去重。`queryForegroundTimeline` 用 `activeComponents` 按 Activity 粒度配对前后台事件、`SCREEN_NON_INTERACTIVE` 时关闭全部未闭合区间、循环结束用 `periodEnd` 兜底闭合，逻辑正确。
10. **`FaceDistanceSample.capturedAtMillis` 默认值可用**（`FaceDistanceSample.kt:24`）：默认 `System.currentTimeMillis()` 在 `analyze()` 构造样本时求值，`.copy(cameraLatencyMillis = ...)` 会保留它，因此 `evaluateBlinkState` 的 `samples.first().capturedAtMillis` 与 `blinkRatePerMinute` 不会退化成 0 —— 干眼误报的真实原因是采样率（[G04B-10]），不是时间戳缺省。
11. **前台服务提升失败处理正确**：四个服务的 `promote` 返回 false 时都 `stopSelf` + `START_NOT_STICKY`，避免"`startForegroundService` 后 5 秒未 `startForeground`"的 `RemoteServiceException`；相机服务类型与 manifest 的 `foregroundServiceType="camera"` 一致，其余三个 `specialUse` 服务都按 API 34 要求声明了 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`。
12. **`toNv21` 的缓冲区计算正确**（`ProximityCameraSampler.kt:591-634`）：逐行按 `rowStride` 定位、`pixelStride != 1` 时逐像素取值；数组尺寸 `ySize + chromaWidth*chromaHeight*2` 与最大写入下标 `ySize + chromaHeight*width - 1` 恰好吻合，无越界。
13. **花括号 / 圆括号平衡、无未使用 import**：16 个文件逐个统计 `{`/`}`、`(`/`)` 完全配对（历史上 `FaceDistanceAnalyzer` 缺右括号导致 kapt 报错的问题已不存在）；逐个核对 import 无未使用项（`java.nio.Buffer` 用于 `yCursor` 类型标注，`android.graphics.SurfaceTexture` 包名正确）。
14. **`ProximityCameraForegroundEligibility`（32 行）设计合理**：把"相机权限 + 进程前台"收成一处，并提供纯参数版重载（`:22-31`）供 JVM 单测注入，不依赖 `Context`；本组无静态/顶层 `Handler(Looper.getMainLooper())` 初始化，不会造成纯 JVM 单测的 `ExceptionInInitializerError`。
15. **`ProximityEventReceiver` 的 MMKV 迁移是正确的**（`:66-96`）：双重检查 + `Mutex` 保护迁移、`DataStore` 读取用 `catch { IOException → emptyPreferences() }` 降级、迁移完成标记幂等；`goAsync()` + `finally { pendingResult.finish() }` 配对正确。

## 跨组遗留（供汇总阶段派单）

- `ForegroundServiceController.start`（`core/services/ForegroundServiceController.kt:73`）在前台服务启动被拒后用 `SystemClock.sleep(2_000L)` **阻塞调用线程**重试。本组 `EyeProtectionOverlayService.show()` 的调用点里，`LumenOpenRuntimeController:179` 与 `AlarmReceiver:74` 可能在主线程 → 主线程被硬阻塞 2 秒（接近 ANR 阈值的一半）。应改为 `Handler.postDelayed` / 协程 `delay` 的非阻塞重试（审查纲要 D 类明确要求）。
- `StatisticsRepository`（`core/repositories/StatisticsRepository.kt:48-68`）**自身也没有锁**，其 `updateEyeStats` 与本组 [G04B-04] 的两处越层写法是同一份无锁 read-modify-write，因此"改走 Repository"只解决分层不解决丢更新，必须同时加 `Mutex` 或改原子 SQL 累加。
- `RuntimeRepository`（`core/repositories/RuntimeRepository.kt:28-30`）需要新增带锁的 `update {}` API，是 [G04B-05] 的根因；全仓库所有 `get()?.let { upsert(it.copy(...)) }` 写法都要一次性迁移。
- `PrivilegedDeviceControlCoordinator.startCaptureLoop`（`core/devicecontrol/PrivilegedDeviceControlCoordinator.kt:267-302`）需配合 [G04B-01]（surface 管线）、[G04B-02]（sampler / 检测器关闭）、[G04B-03]（finally 兜底）一起改：把 sampler 提到循环外创建一次并在结束时 `close()`，并给"连续失败 N 次"加熔断退避，避免当前的无条件 500ms 重试。
- `app/proguard-rules.pro` 的 ML Kit keep 规则（[G04B-13]）是共享文件改动，需在汇总阶段指定唯一修改者。

