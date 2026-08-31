# G11 lumen-crash 崩溃上报 SDK 审查报告

- 审查文件数：32（`lumen-crash-core` 17 源 + 7 测试、`lumen-crash` 3 源、`lumen-crash-sample` 2 源、3 份 `build.gradle.kts`、`lumen-crash-sdk-release.yml`、4 份 README、2 份 consumer/host ProGuard 规则、2 份 AndroidManifest、FileProvider paths、strings）
- 总行数：约 9040（其中 Kotlin 源码 2649 行、测试 487 行、文档 3903 行）
- 结论摘要：**崩溃捕获这一环大体健康**——`Thread.setDefaultUncaughtExceptionHandler` 正确保存并链式调用了前一个 handler（不会吞掉系统/Play Console 的崩溃），报告落盘是单槽位、写入原子化，因此不存在"磁盘打满"和"报告无限堆积"这两类经典事故。但 handler 里 `getOrElse { fromThrowableFallback(...) }` 这一支没有被兜住（G11-17），OOM 崩溃会让报告丢失**并且跳过系统 handler**，这是本组最该先修的一条。其余问题集中在捕获之后的三段路：① 上报通道——崩溃当次提交的上传跑在 daemon 线程上、进程随即被杀，几乎必然失败，而"下次启动补传"完全依赖宿主主动调用 `loadPendingReport()`，`lumen-crash-core` 的文档里根本没写这件事，纯捕获型宿主（README 明确推荐给 Flutter 桥接）会**一份报告都传不出去**；② 判定语义——`FREEZE`（可恢复的 5 秒主线程卡顿）与真实崩溃共用同一个待处理槽位，既会覆盖尚未展示的真实崩溃，又会在下次启动/一次旋转后用崩溃页拦住整个应用；`STARTUP_HANG` 虽然加了前台门禁，但仍会在"宿主已 onResume、只是没调 `markStartupComplete()`"时误报，而这个信号 watchdog 自己已经在收集却没有使用；③ 库的边界——`lumen-crash-core` 的清单**无条件把 `INTERNET` 权限合并进每个宿主**，且默认开启向作者后端 `https://tts.chloemlla.com` 的上报（opt-out），consumer ProGuard 规则用 `-keep class com.chloemlla.lumen.crash.** { *; }` 关掉了下游对整包的 R8，还往宿主注入了全局 `-keepattributes`。考虑到"推 main 自动发版、下游自动吃 latest"，以上每一条都会无感扩散到 CLens 等下游。
- 与旧审计的关系：仓库根目录已有一份 `audit-report-lumen-crash-2026-08-05.md`（32 条，4 周前）。核对后，其中 **STA-02（paste 上传的 executor 从不 shutdown）已修复**（`LumenCrashReportScreen.kt:1437-1439` 现在有 `shutdown()`）；而 STA-01 / SEC-01 / SEC-02 / SEC-04 / PER-03 / DES-02 / DES-03 / REL-02 / REL-03 至今未动，本报告分别对应 G11-17 / G11-09 / G11-18 / G11-16 / G11-12 / G11-12 / G11-16 / G11-13 / G11-13。G11-01～G11-06、G11-08、G11-11、G11-14 是旧审计未覆盖的新问题。
- 修复优先级建议：G11-17（崩溃从系统统计消失）→ G11-03 + G11-04（假崩溃页拦住用户、覆盖真实报告）→ G11-01（上报端到端失效）→ G11-05（下游合规）→ G11-02 / G11-06 / G11-07（性能与内存）→ 其余 P2。

## 缺陷清单

### [G11-01] 崩溃当次的上报几乎必然失败，而"下次启动补传"只有 UI 宿主才会触发；纯 core 宿主一份都传不出去
- 严重度：P1
- 类别：E 韧性
- 位置：`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/LumenCrash.kt:293-301`、`:315-349`、`:217-223`、`:168-178`、`:50-64`；`lumen-crash-core/README.md:121-153`（Watchdogs 一节是全文最后一节，无补传说明）
- 现状：
  ```kotlin
  Executors.newSingleThreadExecutor { r -> Thread(r, "lumen-crash-backend-upload").apply { isDaemon = true } }
  ...
  runCatching { executor().submit { /* DNS + TLS + POST，connectTimeout 15s */ } }
  ```
  崩溃 handler 里 `saveReport()` → `submitBackendUpload()` 只是把任务 `submit` 到一个 **daemon** 线程，随后 handler 立刻把控制权交给 `previousHandler`（系统 `KillApplicationHandler`），它通知 AMS 后 `Process.killProcess`。上传线程连不上就被连根拔掉，且 daemon 属性保证 JVM 不会等它。真正的补传只发生在 `loadPendingReport()` / `loadPendingReportSafely()` 里（`:168-178`），而 `install()` 自己**从不**读取并补传已落盘的报告。
- 触发场景：任何一次真实崩溃。对本仓库主 app 无害（`MainActivity.onCreate` 调了 `loadPendingReportSafely()`）；对 `lumen-crash-core` 的目标用户（README 第 15 行：「Use core for Flutter bridges or hosts that only need capture + persistence」）致命——它们没有 `LumenCrashGate`，文档也没让它们调 `loadPendingReport()`，报告永远躺在磁盘上直到被下一次崩溃覆盖。
- 影响：崩溃上报端到端失效。作者的崩溃看板收不到纯 core 宿主的任何数据，且所有宿主的"崩溃当次"上传都靠下次启动兜底，实际到达率取决于用户是否再打开一次应用。
- 修复方案：① 在 `LumenCrash.install()` 末尾（`:59` 之后、`collectPriorExitReport` 之前）加一次 `runCatching { store().load() }?.let { submitBackendUpload(it, config) }`，让补传不依赖宿主调用任何 UI 相关 API；② `executor()` 的线程改为 `isDaemon = false` 并在 `submitBackendUpload` 的崩溃路径上给一个短 join（例如 `future.get(1500, MILLISECONDS)` 包在 runCatching 里），把"崩溃当次"从 0% 提到可观的成功率——注意上限必须小于系统 ANR/杀进程窗口；③ 把 `CrashReportBackendUploader.upload` 的 `connectTimeoutMillis` 在崩溃路径上收到 3~5 秒（当前 15/30 秒对一个将死的进程毫无意义）；④ `lumen-crash-core/README.md` 与 `README.zh-CN.md` 补一节「捕获型宿主必须在启动时调用 `LumenCrash.loadPendingReport()`（或新的 `flushPendingReports()`）」。
- 风险/注意：改 daemon 属性会让宿主进程在正常退出时多等最多一个 timeout；务必配合 ③ 一起改，且只在崩溃路径 join、`recordNonFatal` 路径保持完全异步。附带同类问题：`recordNonFatal`（`:139-152`）刻意不落盘，因此**离线时非致命报告直接丢失、无任何重试**——如果这是有意的取舍，建议在 README 的 `recordNonFatal` 行里写明"离线即丢弃"。

### [G11-02] `install()` 在 `Application.onCreate` 主线程上做 binder 调用 + 最多 9 次文件 IO，每次冷启动都被拖慢
- 严重度：P1
- 类别：D 生命周期（主线程阻塞）
- 位置：`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/LumenCrash.kt:50-64`、`:259-268`；`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/PriorExitCrashCollector.kt:19-64`、`:66-81`；`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/CrashReportStore.kt:149-171`
- 现状：`install()` 是同步的，`synchronized(installLock)` 里依次执行：
  ```kotlin
  storeRef.set(CrashReportStore(application.applicationContext))
  installUncaughtExceptionHandler(application)
  restartWatchdog(application, config)
  collectPriorExitReport(application, config)   // ← 主线程 binder + 磁盘
  ```
  `collectPriorExitReport` 默认开启（`priorExitCaptureEnabled = true`），在 API 30+ 上做：`getHistoricalProcessExitReasons`（跨进程 binder，取 8 条）→ `traceInputStream` 最多读 **128 KB** → `SharedPreferences` 首次加载（同步读盘）→ `store().load()`（`resolveExternalTargets` 里 `mkdirs()` + 最多 3 次 `exists()`/`readText`，都在**外部存储**上）→ 命中则 `saveReport()` 再写 3 份文件（每份 `createTempFile` + `writeText` + `delete` + `renameTo`）。宿主的 `onCrashSaved` / `onReportSaved` 回调也在这条主线程路径上被同步调用（本仓库主 app 是 `scheduleCrashReportUpload`，即 WorkManager 入库）。
- 触发场景：Android 11+ 设备上**每一次冷启动**都会付 binder + 目录创建 + 至少 3 次外部存储 `exists()` 的代价；上一次是原生崩溃/ANR 退出时，额外付 128 KB trace 读取 + 3 次外部存储写入 + 宿主回调（WorkManager 建库）。外部存储在 Android 11+ 走 FUSE，单次写入的抖动可达数十毫秒。
- 影响：冷启动 TTID 变差（尤其低端机与刚崩溃过的那次启动）；讽刺的是它自己启用的 `startupHangWatchdog` 正是用来抓这种启动慢的。严重时 `Application.onCreate` 被拉长会直接触发系统的启动 ANR。
- 修复方案：把 `collectPriorExitReport(application, config)` 从 `install()` 的同步路径挪到后台：在 `LumenCrash` 里复用 `executor()`（G11-01 里那个单线程池）`submit { collectPriorExitReport(...) }`，并保留现有的"不覆盖真实待处理报告"判断（`:266`）；`PriorExitCrashCollector.collect()` 内部无需改动。同时把 `CrashReportStore.resolveExternalTargets` 里的 `mkdirs()`（`:156-159`）改为惰性——只在真正 `writeAtomically` 时建目录，避免 `load()` 也去创建三个目录。
- 风险/注意：异步化后 `install()` 返回时 `startupCrashReport` 可能还没填上 PRIOR_EXIT 报告，`MainActivity.onCreate` 里 `app.startupCrashReport` 就读不到它了（会退化成"下一次启动才显示原生崩溃"）。若要保持当次可见，让 `collectPriorExitReport` 在后台完成后通过 `config.onReportSaved` 通知宿主，而不是让主线程等它。

### [G11-03] 可恢复的 `FREEZE` 报告与真实崩溃共用唯一槽位：既覆盖未展示的真实崩溃，又用崩溃页拦住下一次启动和一次旋转
- 严重度：P1
- 类别：A 架构 / F 持久化
- 位置：`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/LumenCrash.kt:241-257`（`recordWatchdogReport` → `saveReport`）、`:270-288`（`saveReport` 无条件 `startupCrashReport = report` + `store().save`）；`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/CrashReportStore.kt:41-45`、`:145-147`（单文件 `crash_report.json`）；对照 `LumenCrash.kt:139-152`（`recordNonFatal` 刻意不占槽位）
- 现状：`CrashReportStore` 只保存**一份**报告（固定文件名 `crash_report.json`），`saveReport()` 对所有 kind 一视同仁：
  ```kotlin
  private fun saveReport(report: CrashReport, config: LumenCrashConfig) {
      startupCrashReport = report
      runCatching { store().save(report) }...
  ```
  而 `FREEZE` 的定义是"主线程连续两个检查周期没处理心跳"（默认 ~6 秒），**应用随后完全恢复了**——`LumenCrashWatchdog.kt:52-56` 的心跳还会把 `freezeReported` 复位以便再报。只有 `collectPriorExitReport`（`:265-266`）写了"不覆盖真实待处理报告"的保护，watchdog 路径没有。
- 触发场景：
  1. 覆盖真实崩溃：应用崩溃 → 报告落盘 → 用户重开 → 启动阶段主线程卡 6 秒（Room 迁移、MMKV 首次初始化、低端机 GC）→ `FREEZE` 覆盖 `crash_report.json` → 真实崩溃的堆栈永久丢失（且它可能还没上传成功，见 G11-01）。
  2. 崩溃页拦截：会话中出现一次 6 秒卡顿 → `FREEZE` 落盘并写入 `startupCrashReport` → 用户**旋转屏幕**，`MainActivity.onCreate` 读到 `app.startupCrashReport != null`（`app/src/main/java/com/projectlumen/app/MainActivity.kt:45-52`），于是 `initialViewModel = null`，整个应用变成崩溃页 + 空 Surface；不旋转的话下一次冷启动同样被拦。
- 影响：用户在应用**从未崩溃**的情况下看到"崩溃报告"页并丢失当前界面；真实崩溃报告被一次无害卡顿覆盖丢失。
- 修复方案：让 watchdog 报告走"非致命"语义——在 `LumenCrash.recordWatchdogReport`（`:241-257`）中区分：`STARTUP_HANG` 保留现有落盘+占槽行为（它确实代表一次用户可见的启动失败），`FREEZE` 改为只 `submitBackendUpload(report, config)` + 触发 `config.onAnrDetected`，**不写 `startupCrashReport`、不写 store**（即复用 `recordNonFatal` 的路径）。若一定要落盘留证，则给 `CrashReportStore` 增加第二个文件名（如 `watchdog_report.json`）与独立的 `loadWatchdog()`，并在 `saveLocked` 前加"目标槽位已有 kind==CRASH 的报告则不覆盖"的判断。
- 风险/注意：`config.onCrashSaved`（legacy 回调）当前会收到 FREEZE 报告，主 app 靠它 `scheduleCrashReportUpload`；改动后要确保 FREEZE 仍然经由 `onAnrDetected` 或直接 `submitBackendUpload` 上报，否则会从"误报太多"变成"卡顿完全不可见"。

### [G11-04] `STARTUP_HANG` 仍会误报：watchdog 自己已经在收集 `onActivityResumed`，却没有把它当作"已经画出第一帧"
- 严重度：P1
- 类别：D 生命周期 / E 韧性
- 位置：`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/LumenCrashWatchdog.kt:116-132`、`:179-186`、`:58-75`、`:43`
- 现状：启动挂起的判定是「超时 && `isUserVisibleLaunch()`」，而 `isUserVisibleLaunch()` 的第一个分支就是 `isForeground`（有 activity 处于 resumed）：
  ```kotlin
  if (startupPending && now - startedAtMillis >= startupTimeoutMillis && startupReported.compareAndSet(false, true)) {
      if (isUserVisibleLaunch()) emit(CrashReportKind.STARTUP_HANG, now - startedAtMillis)
  ```
  `startupComplete` **只**能由宿主调用 `markStartupComplete()` 置位（`:88-94`）。于是"有 activity 已经 resumed、界面早就画出来了、但宿主没调 `markStartupComplete()`"这条路径**满足全部上报条件**，`isForeground == true` 反而让门禁放行。
- 触发场景：宿主开了 `startupHangWatchdogEnabled` 但首帧回调没覆盖全部入口——多 Activity 宿主从深链接/通知/桌面小组件进入了另一个没写 `markStartupComplete()` 的 Activity；或首页是原生 View 而 `withFrameNanos` 挂在别的 Compose 屏；或宿主复制示例代码后把 `LaunchedEffect` 挪进了某个条件分支。15 秒后必然产生一份 `rootCause = "Application did not report its first rendered frame within 15xxx ms"` 的假报告。README（`lumen-crash/README.md:1543`）自己提到已集成进 Clash Meta for Android 这类多 Activity 宿主，正是高危形态。
- 影响：下游 App 的崩溃看板被假 `startup_hang` 刷满；配合 G11-03 的槽位语义，这份假报告还会在下一次启动时用崩溃页拦住用户。
- 修复方案：在 `activityLifecycleCallbacks.onActivityResumed`（`:59-61`）里补一句 `markStartupCompleteInternal()`——一个 activity 走到 `onResume` 就是"进程已经能画帧"的**客观**证据，比宿主自报可靠得多；`markStartupComplete()` 保留为可选的更精确信号。同时把 `startedAtMillis`（`:43`）与超时比较改用 `SystemClock.uptimeMillis()`：`elapsedRealtime` 含深睡时间，会把"进程被挂起 3 小时"算成 `durationMillis = 10800000` 的启动挂起时长，即使门禁挡住了上报，报告里的时长数字也是错的。
- 风险/注意：`onActivityResumed` 隐式完成后，"`Application.onCreate` 卡死到永远"这一真实场景仍能被抓到（那时没有任何 activity 能 resume，`isUserVisibleLaunch()` 走 `getMyMemoryState` 分支），能力不损失。改时钟会让 `startupHangTimeoutMillis` 的语义从"墙钟"变成"进程运行时长"，需在两份 README 的 Watchdogs 一节同步措辞。

### [G11-05] `lumen-crash-core` 的清单无条件给每个宿主合并 `INTERNET` 权限，并默认把崩溃报告 + 跨重装稳定设备 ID 上报到作者后端
- 严重度：P1
- 类别：G 安全 / 合规
- 位置：`lumen-crash-core/src/main/AndroidManifest.xml:4`（`<uses-permission android:name="android.permission.INTERNET" />`）；`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/LumenCrashConfig.kt:64-73`（`crashReportBackendEnabled = true`、`crashReportBackendBaseUrl = "https://tts.chloemlla.com"`）、`:28-30`（`pasteUploadEnabled = true`）；`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/LumenCrashDefaults.kt:14-17`；`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/CrashDeviceId.kt:26-72`
- 现状：core 模块（明确宣传为"capture-only，不含 UI"）的清单注释写着"Merged into hosts so paste upload works without extra host setup"，无条件声明 `INTERNET`。同时 `crashReportBackendEnabled` 默认 `true`，上报体（`CrashReportBackendUploader.buildRequestBody`）包含完整堆栈、`Build.FINGERPRINT`、机型、ABI、内存快照、最近 40 条面包屑、`packageName`、`versionCode`，以及 `CrashDeviceId.resolve()` 得出的 `SHA-256(SSAID | 宿主包名 | 稳定 Build 特征)` —— 一个**跨重装、跨清除数据仍然稳定**的设备标识（README 自己这么描述，`lumen-crash/README.md:1313-1317`）。宿主要关掉只有一个总开关，没有分类开关（不能"只上报堆栈、不上报设备 ID"），也没有"用户同意后才上报"的钩子。
- 触发场景：任何下游接入。一个自己**没有声明 `INTERNET`** 的离线 App 接入 core 后，权限被清单合并静默加上，SDK 随即开始向第三方域名 POST；开发者在 `AndroidManifest.xml` 里看不到这条权限，只有 merged manifest 里有。
- 影响：下游 App 的 Play Data Safety / GDPR 申报与实际行为不符（收集了设备标识并传给第三方服务器）；离线定位的 App 被动获得联网能力。这是 SDK 分发型代码最容易招致合规问题的一条。
- 修复方案：① 从 `lumen-crash-core/src/main/AndroidManifest.xml` 删除 `INTERNET` 声明（core 里没有任何 UI，paste 上传是 UI 触发的；后端上传本就应该由宿主明示授权），改为在两份 README 里写"启用后端上传/粘贴分享的宿主需自行声明 `android.permission.INTERNET`"，并在 `CrashReportBackendUploader.upload` 捕获 `SecurityException` 时返回 `REJECTED`（缺权限时不要无限重试）；② `LumenCrashConfig.crashReportBackendEnabled` 默认改为 `false`（`LumenCrashConfigBuilder.kt:37` 同步），主 app 在 `ProjectLumenApplication.installLumenCrashSdk()` 里显式置 `true`；③ 增加 `crashReportConsentProvider: (() -> Boolean)?`，`submitBackendUpload` 在 `:322` 的总开关后再问一次，供下游接同意弹窗；④ 若默认值必须保持 `true`，至少在 `lumen-crash/README.md` 与 core README 的"3 步接入"最前面加一段醒目的"默认会向 tts.chloemlla.com 上报，关闭方式"说明——目前这段只在正文第 1300 行左右出现。
- 风险/注意：改默认值是**行为破坏性变更**，且下游自动吃 latest（见 G11-14），CLens 等宿主会在无感升级后突然停止上报。必须与下游同一批次提交，并在 release notes 里点明。

### [G11-06] 上传执行器是无界队列、无客户端限流：服务循环里高频 `recordNonFatal` 会堆积上传任务并冲刷掉面包屑
- 严重度：P1
- 类别：E 韧性
- 位置：`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/LumenCrash.kt:293-301`（`Executors.newSingleThreadExecutor`）、`:315-349`、`:139-152`；`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/CrashBreadcrumbs.kt:15-29`；`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/CrashReportBackendUploader.kt:42-43`（15s/30s 超时）
- 现状：`Executors.newSingleThreadExecutor()` 背后是无界 `LinkedBlockingQueue`；`submitBackendUpload` 对每份报告 `submit` 一个任务，单个任务最长 45 秒（连接 15 + 读 30）。去重集合 `uploadedReportIds` 挡不住高频调用，因为 `reportId` 的种子里含 `crashedAtMillis`（`CrashReport.kt:252-261`），每次调用都是新 id。`uploadedReportIds` 本身也只增不减（RETRYABLE 才移除）。同时每次 `recordNonFatal` 都会写一条面包屑（`:144`），而面包屑环只有 **40 条**。
- 触发场景：本仓库现成的触发点——`app/src/main/java/com/projectlumen/app/core/proximity/ProximityDetectionService.kt:248` 与 `:251` 在每个检测周期的失败分支上调 `recordHandledFailure` → `recordNonFatal`。用户离网、遥测上传持续失败时，前台服务每个周期产生一份报告：任务入队速度 > 出队速度（每个任务都要等超时），队列与 `uploadedReportIds` 单调增长；`LightMonitorService.kt:141`、`:166` 同理。
- 影响：长时间运行的前台服务内存单调增长（每份报告含完整堆栈字符串，数 KB 级）；40 条面包屑全被 `Handled failure captured: ...` 占满，**下一次真实崩溃丢失全部有效上下文**——面包屑本来是这个 SDK 相对 Crashlytics 的主要卖点。
- 修复方案：① 把 `executor()` 换成有界队列 + 丢弃策略：`ThreadPoolExecutor(1, 1, 30, SECONDS, ArrayBlockingQueue(32), threadFactory, ThreadPoolExecutor.DiscardOldestPolicy())`；② 在 `submitBackendUpload` 里加客户端限流：`AtomicLong` 记录上次提交时间，`NON_FATAL` 类型每 N 秒最多提交一份（致命/watchdog 报告不限流）；③ 给 `recordNonFatal` 加同类节流或按 `exceptionType + 堆栈首行` 折叠计数（同一异常连续 N 次只上报一次并带 `occurrences` 字段）；④ `uploadedReportIds` 加上限（超过 256 条时清空最旧的，或直接换成固定大小的 `LinkedHashSet` + `synchronized`）；⑤ 面包屑侧：`recordNonFatal` 的自动面包屑改为可关闭，或对连续同类事件去重。
- 风险/注意：折叠/限流会让看板上非致命事件的绝对数量下降，需同步后端对 `occurrences` 的理解；`DiscardOldestPolicy` 会丢弃最旧的待上传报告——对**致命**报告不可接受，所以务必只让非致命走这条池，或给致命报告单独一条无界但必然只有 1~2 个任务的路径。

### [G11-07] `LumenCrashGate` 把 `loadPendingReportSafely()` 写成 Composable 默认参数：每次重组都在主线程读外部存储，且会中途弹出崩溃页
- 严重度：P1
- 类别：D 生命周期（Compose）
- 位置：`lumen-crash/src/main/java/com/chloemlla/lumen/crash/ui/LumenCrashGate.kt:19`、`:25-27`；调用方 `lumen-crash-sample/src/main/java/com/chloemlla/lumen/crash/sample/MainActivity.kt:33`（`LumenCrashGate { ... }`，即文档推荐的短接入路径）
- 现状：
  ```kotlin
  fun LumenCrashGate(
      initialReport: CrashReport? = LumenCrash.loadPendingReportSafely(),
      ...
      var pendingReport by remember(initialReport?.reportId) { mutableStateOf(initialReport) }
  ```
  Compose 的默认参数表达式在**函数体内**求值，因此 `LumenCrashGate` 每次重新执行（未被 skip）都会调用 `loadPendingReportSafely()` → `store().load()` → 在**组合线程（主线程）**上 `exists()`/`readText` 最多 3 个外部存储路径 + 6 个 legacy 路径，并顺带 `submitBackendUpload`。更麻烦的是 `remember` 的 key 就是这次求值的结果：一旦会话中途 `startupCrashReport` 被填上（G11-03 的 FREEZE / 后台补捕的 PRIOR_EXIT），key 从 `null` 变成新 `reportId`，`mutableStateOf` 被重新初始化，**整个宿主 UI 被崩溃页顶掉**。
- 触发场景：短接入路径（示例与两份 README 的推荐写法）下，任何一次 gate 重组：用户按下"清除并继续"后 `pendingReport = null` 触发的重组、父级重组导致 `content` lambda 失效时的重组。本仓库主 app 显式传了 `initialReport`（`app/.../MainActivity.kt:56`）因此不受影响——**这条只打下游与示例**。
- 影响：主线程磁盘 IO（外部存储，抖动可达数十毫秒，会掉帧）；配合 G11-03 出现"应用用着好好的突然整屏变成崩溃报告"。
- 修复方案：默认参数不要放有副作用的调用。改成：
  ```kotlin
  fun LumenCrashGate(initialReport: CrashReport? = null, ...) {
      val resolved = initialReport ?: rememberSaveable { LumenCrash.loadPendingReportSafely() }  // 或 produceState 走 IO 线程
      var pendingReport by remember(resolved?.reportId) { mutableStateOf(resolved) }
  ```
  更彻底的做法是 `produceState(initialValue = null) { withContext(Dispatchers.IO) { LumenCrash.loadPendingReportSafely() } }`，第一帧先渲染宿主内容、拿到报告后再切崩溃页（配合 G11-03 修完，FREEZE 不再进这个槽位，就不会有中途顶屏问题）。
- 风险/注意：`produceState` 方案会让崩溃页比现在晚一帧出现，宿主首帧可能闪一下正常界面；若不接受就用 `remember` 同步版本（仍然只读一次盘）。

### [G11-08] ingest 载荷没有 `schemaVersion` / SDK 版本，且 `REJECTED`（4xx）后本地报告永不清理，每次启动重复 POST 一次
- 严重度：P2
- 类别：A 架构 / E 韧性
- 位置：`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/CrashReportBackendUploader.kt:102-115`、`:124-136`；`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/CrashReport.kt:338-361`（`toJson` 即契约）；`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/LumenCrash.kt:342-346`
- 现状：上报体 = `report.toJson()` 再补 `deviceInstallationId` / `packageName` / `versionCode` 三个字段（`PRIOR_EXIT` 还把 `exitReason` 折进 `systemInfo`，因为后端没有这一列）。字段名散落在 `CrashReport.toJson()`（14 个 put）和 `buildRequestBody`（3 个 put + 1 个改写）两处，**没有任何版本字段**——后端无法判断这份载荷来自哪个 SDK 版本。`REJECTED` 时 `uploadedReportIds` 保留该 id（本进程内不再试），但磁盘上的报告不会被删除，只有宿主调 `clearPendingReport()` 才清；纯 core 宿主（G11-01）不会调。
- 触发场景：后端给 ingest 增加一个必填字段并对缺字段返回 `HTTP 400` → 存量安装（下游 App 里已经装好的旧 SDK）每次冷启动 `loadPendingReport()` 都会 POST 一次、拿一次 400、报告继续留在磁盘上，直到被下一次崩溃覆盖。不是死循环（一次启动只试一次），但是永久的无效流量 + 报告永久无法送达且**用户侧完全无感**。
- 影响：契约演进时旧版 SDK 的崩溃静默丢失；后端拿不到 SDK 版本，无法做兼容分支或统计受影响安装量。
- 修复方案：① 在 `buildRequestBody` 里补 `put("schemaVersion", 1)` 和 `put("sdkVersion", BuildConfig-或常量)`——core 关闭了 `buildConfig`，所以在 `LumenCrashDefaults` 加 `const val SDK_INGEST_SCHEMA_VERSION = 1` / `const val SDK_VERSION = "0.1.0"`（发布时由 `-PlumenCrashSdkVersion` 注入更好，见 G11-14）；② `REJECTED` 时主动清理：在 `submitBackendUpload` 的 `outcome == REJECTED` 分支里 `runCatching { store().clear() }`，但**必须**只在该报告仍是当前待处理报告、且它已经被展示过/或 kind != CRASH 时才清（否则会吞掉用户还没看到的崩溃）。保守做法是加一个持久化的 `attemptCount`，达到 3 次 `REJECTED` 后放弃并清理；③ 后端契约字段名集中到一个 `internal object CrashIngestFields`，避免 `toJson()` 既当本地存储格式又当线上契约（现在这两个职责是同一个函数，改本地格式就等于改线上契约）。
- 风险/注意：`toJson()` 同时用于本地持久化和上传，`crashReportFromJson` 要能读旧文件——加字段是安全的（`optXxx` 读取），删/改字段名不安全。②的清理逻辑要格外小心，宁可不清也不能删掉未展示的真实崩溃。

### [G11-09] 崩溃报告优先写 app 外部存储：在 SDK 声明支持的 API 26~28 上，任何持 `READ_EXTERNAL_STORAGE` 的应用都能读到
- 严重度：P2
- 类别：G 安全
- 位置：`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/CrashReportStore.kt:8-17`（类注释即声明此策略）、`:64-79`、`:149-162`；`lumen-crash-core/build.gradle.kts:32`（`minSdk = 26`）；`lumen-crash/README.md:1167-1180`、`:1729-1731`
- 现状：`save()` **优先**写 `getExternalFilesDir("lumen-crash")` / `getExternalFilesDir(null)/lumen-crash` / `externalCacheDir/lumen-crash` 三份，只有全部失败才退回 `filesDir` / `noBackupFilesDir` / `cacheDir`，且外部写入成功后会**主动删除**内部副本（`:78`、`:113-119`）。报告内容含完整堆栈、异常 message、40 条面包屑、`Build.FINGERPRINT`、机型。
- 触发场景：API 26~28（SDK 自己声明 `minSdk 26`，Android 9 及以下没有 scoped storage）上，`/sdcard/Android/data/<pkg>/files/lumen-crash/crash_report.json` 对任何声明了 `READ_EXTERNAL_STORAGE` 的第三方应用都是可读的。API 29+ 有 scoped storage 保护，但持 `MANAGE_EXTERNAL_STORAGE` 的文件管理器、以及 adb/备份路径仍可读。
- 影响：崩溃报告里的异常 message 常带业务上下文（失败的 URL、SQL、参数），面包屑内容完全由宿主决定，等于把一份诊断日志放在弱保护位置。对一个被第三方消费的 SDK，这是默认策略层面的选择错误。
- 修复方案：把优先级反过来——默认写 `context.noBackupFilesDir/lumen-crash/crash_report.json`（不进云备份、不随 `allowBackup` 外泄），外部存储降级为**可选**：给 `LumenCrashConfig` 加 `externalReportMirrorEnabled: Boolean = false`，只有显式开启（调试场景）才写外部。`loadLocked()` 的读取顺序保持"两边都读"以兼容存量文件，`clear()` 保持清两边。
- 风险/注意：README 有两处（`:1167-1180`、`:1729-1731`）把"写外部存储"当作特性描述（"不受清除应用数据影响"），改默认值需同步这两段；`CrashReportPersistenceTest` 的 `storeSavesLoadsClearsAndRemovesLegacyCopies` / `storeFallsBackToInternalStorageWhenExternalIsUnwritable` 断言了当前优先级，需一并改。

### [G11-10] `LumenCrashReportScreen.kt` 1497 行，是全仓库最大文件，直接违反"禁止超级文件"硬规
- 严重度：P2
- 类别：A 架构
- 位置：`lumen-crash/src/main/java/com/chloemlla/lumen/crash/ui/LumenCrashReportScreen.kt:1-1497`
- 现状：一个文件里塞了：主屏 `LumenCrashReportScreen`（102-501，本身 400 行）、布局 token 引擎（`CrashLayoutTokens` + `crashLayoutTokens`，503-649，纯计算 147 行）、10 个私有 Composable（`CrashIntegrityBlockedScreen`、`CrashAuthorFooterCard`、`CrashReportHero`、`CrashReportCard`、`CrashReportSectionHeader`、`CrashReportInfoTile`、`CrashReportMetadataPill`、`CrashReportEventRow`、`CrashReportActionPanel`、`CrashReportActionButton`）、2 个对话框、以及 8 个非 Composable 的分享/剪贴板/上传工具函数（1238-1497）。`AGENTS.md` 的第一行就是"不要写超级文件"。
- 触发场景：任何一次改动都要在 1500 行里定位；`CrashReportActionPanel` 的三套布局分支（956-1099）把同样的 4 个按钮抄了 3 遍（共 143 行），改一个按钮要同时改三处，已经是重复实现。
- 影响：可维护性；这是 SDK 里最容易被下游要求定制的部分。
- 修复方案：按职责拆成同包 5 个文件（纯移动 + 改可见性，无逻辑变更）：
  1. `LumenCrashReportScreen.kt` ← 只留 `LumenCrashReportScreen`（102-501）
  2. `CrashLayoutTokens.kt` ← `CrashLayoutTokens` / `rememberCrashLayoutTokens` / `crashLayoutTokens`（503-649）；这段是纯函数，顺手给它补单测（当前布局逻辑零覆盖）
  3. `CrashReportComponents.kt` ← `CrashReportCard` / `SectionHeader` / `InfoTile` / `MetadataPill` / `EventRow` / `Hero` / `AuthorFooterCard` / `CrashIntegrityBlockedScreen`
  4. `CrashReportActions.kt` ← `CrashReportActionPanel` + `CrashReportActionButton`，并把三套布局分支收敛成"一个按钮列表 + 三种排布"（`data class CrashAction(text, icon, onClick, primary)` + `when(layout)` 只决定 Row/Column 结构），消掉 143 行重复
  5. `CrashReportSharing.kt` ← `shareCrashReportText/File/Link`、`resolveCrashShareDirectory`、`resolveFileProviderAuthority`、`launchCrashReportShare`、`uploadCrashReportPasteLink`、`openHttpsUrl`、`copyTextToClipboard`、`findActivity`（1238-1497）
- 风险/注意：`consumer-rules.pro` 与 `host-proguard-template.pro` 里 `-keep class com.chloemlla.lumen.crash.ui.LumenCrashReportScreenKt { *; }` 是**按文件名**生成的合成类名，拆文件后这条规则失效（虽然被后面的整包 `-keep` 兜住，见 G11-11）；拆完记得同步这两份规则里的 `*Kt` 条目。
- 关于"崩溃 SDK 该不该捆绑一整套 Compose UI"：**这一点仓库已经做对了**——`lumen-crash-core` 是独立发布的 artifact（`com.chloemlla.lumen:lumen-crash-core`），完全不含 Compose，只想要上报能力的下游可以只依赖它，两份 README 也都指明了这个选择。真正的分层问题不在"是否拆分"，而在 `lumen-crash` 用 `api` 把 Compose 依赖面无差别推给下游（见 G11-12）。另外崩溃后进程里跑 Compose 的二次崩溃风险已有兜底：所有分享/上传/剪贴板入口都包了 `runCatching`（370、385、1239、1267、1323、1405、1471、1487），`AuthorIntegrity` 失败时降级到 `CrashIntegrityBlockedScreen`（115-118），这部分设计是稳的。

### [G11-11] consumer ProGuard 规则用整包 `-keep` 关掉了下游对 SDK 的 R8，并往宿主注入全局 `-keepattributes`
- 严重度：P2
- 类别：H 编译与结构 / A 架构
- 位置：`lumen-crash-core/consumer-rules.pro`（末 3 行 + 第 4 行）、`lumen-crash/consumer-rules.pro`（同）、`lumen-crash/host-proguard-template.pro`
- 现状：
  ```
  -keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, RuntimeVisibleAnnotations
  ...
  -keep class com.chloemlla.lumen.crash.** { *; }
  -keepclassmembers class com.chloemlla.lumen.crash.** { *; }
  -dontwarn com.chloemlla.lumen.crash.**
  ```
  consumer rules 会被合并进**每个下游 App 的 R8 配置**。`-keepattributes` 是全局指令，不限包名：它让下游整个 App（不只是 SDK）保留 `Signature` / `InnerClasses` / `EnclosingMethod` 与全部注解，直接增大 APK 并削弱混淆效果。整包 `-keep ... { *; }` 让 SDK 的每个类、每个成员都不被移除、不被重命名、不被优化——包括整套 Compose 崩溃 UI（`com.chloemlla.lumen.crash.ui.**` 也匹配这个通配符）。`-dontwarn` 还会掩盖真实的缺类告警。
- 触发场景：任何开启 `isMinifyEnabled = true` 的下游 release 构建。前面那些精确的 `-keep`（`CrashAuthorAttribution` 常量、`AuthorIntegrity` 入口）本来是对的且必要的——问题只在最后 3 行的"保险起见全留"。
- 影响：下游 release APK 体积无谓增大、SDK 代码完全不混淆、宿主自身的混淆强度被 `-keepattributes` 拉低；`-dontwarn` 让真正的 R8 缺类问题静默。
- 修复方案：删掉两份 consumer-rules 里的最后 3 行整包规则；`-keepattributes` 收窄为只保留必要的（此 SDK 不依赖反射读注解，实际只需要 `Signature`？核对后能全删则全删，Kotlin 元数据由 R8 自行处理）。保留并明确以下精确规则即可：`CrashAuthorAttribution` 的 `String` 常量与 `payload()`（`AuthorIntegrity` 要按字面值算 SHA-256，被改名不影响，但常量折叠/字符串优化会）、`AuthorIntegrity` 的三个入口、`LumenCrashFileProvider`（清单引用，AGP 自动 keep 但显式更稳）。改完必须在 `lumen-crash-sample` 上真跑 `assembleRelease` 验证（见 G11-13）。
- 风险/注意：这是**已知踩坑区**（同作者的 R8 删 `ComponentRegistrar` 无参构造事故）。收窄规则必须配合 sample 的 R8 产物实测，否则可能把 `AuthorIntegrity` 的 fail-closed 变成下游 release 直接 `SecurityException` 白屏。建议分两步：先只删 `-dontwarn` 与 `-keepattributes` 中确认无用的项，验证通过后再动整包 `-keep`。

### [G11-12] 发布元数据缺陷：Compose 依赖用 `api` 暴露但 BOM 只在 `implementation`，下游可能解析不到版本；`material-icons-extended` 被强推给下游；core 声明了未使用的 `core-ktx`
- 严重度：P2
- 类别：A 架构 / H 编译与结构（需确认）
- 位置：`lumen-crash/build.gradle.kts:71-85`；`lumen-crash-core/build.gradle.kts:65-69`
- 现状：
  ```kotlin
  val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
  implementation(composeBom)          // ← 平台约束只进 implementation
  api("androidx.compose.ui:ui")       // ← 无版本号，靠 BOM 约束
  api("androidx.compose.material3:material3")
  api("androidx.compose.material:material-icons-extended")
  api("androidx.compose.material3:material3-window-size-class")
  ```
  `implementation(platform(...))` 的依赖约束只进入 `runtimeElements`，不进入 `apiElements`；而这些无版本号的坐标是用 `api` 声明的，会进入发布的 `apiElements` / POM `compile` 作用域。下游按 Maven 坐标消费 `com.chloemlla.lumen:lumen-crash` 时，如果自己没有引入 compose-bom，Gradle 就拿到一组"无版本要求"的依赖。另外 `material-icons-extended` 是数千个图标类的重型依赖，而这个屏幕只用了 10 个图标（`Icons.Outlined.BugReport/ContentCopy/DeleteOutline/Devices/ExpandLess/ExpandMore/Info/Person/Share/WarningAmber`）。`lumen-crash-core` 声明了 `implementation("androidx.core:core-ktx:1.17.0")`，但**core 全部 17 个源文件没有任何 androidx 引用**（已用 `rg androidx lumen-crash-core/src/` 确认为零命中），`implementation` 在库发布时仍会进 POM 的 runtime 作用域，把 `core-ktx 1.17.0`（要求较高 compileSdk）强加给每个纯捕获型下游。
- 触发场景：一个非 Compose 的下游（README 明确面向的"Flutter 桥接"）消费 `lumen-crash-core`，会莫名拉进 `core-ktx 1.17.0` 并被其 `compileSdk` 约束卡住；一个 Compose 下游若自己没声明 compose-bom（只声明具体版本或用 version catalog）消费 `lumen-crash`，会遇到 compose 依赖版本解析失败或落到意外版本。本仓库主 app 与 CLens 都自带 compose-bom，所以一直没暴露——**需确认**：本次未运行任何构建，"下游必然解析失败"未实测，但 `api` + `implementation(platform)` 的组合确定会让约束缺席于 `apiElements`。
- 影响：下游接入体验/构建可靠性；`material-icons-extended` 让每个下游多编译一个巨型依赖（未混淆时方法数与 APK 体积明显增长，且被 G11-11 的整包 keep 放大）。
- 修复方案：① `implementation(composeBom)` 改为 `api(platform(composeBom))`，让约束随 `apiElements` 一起发布；② `api("androidx.compose.material:material-icons-extended")` 改为 `implementation("androidx.compose.material:material-icons-core")`，把用到的 10 个图标换成 `material-icons-core` 里的等价项，或直接内联为 SDK 自己的 `ImageVector`（最省，且彻底摆脱图标库版本耦合）；③ 删掉 `lumen-crash-core/build.gradle.kts:66` 的 `core-ktx`（确认无引用）；④ 复核 `api` 面：`ui` / `material3` / `material3-window-size-class` 出现在公开 Composable 签名里的才需要 `api`，其余降为 `implementation`。
- 风险/注意：②会改变图标外观（`material-icons-core` 的图标集较小，`Devices` / `WarningAmber` / `BugReport` 可能不在其中，需逐个核对，缺的就内联 vector）；③若 AGP 版本对齐依赖 `core-ktx` 的传递约束，删除后下游可能改变 androidx 解析结果——发布前用 sample 的依赖树核对。

### [G11-13] 关键路径零测试：watchdog 完全不可单测，CI 也从不真正跑 sample 的 R8（`assembleRelease` 缺席）
- 严重度：P2
- 类别：H 编译与结构 / D 生命周期
- 位置：7 份测试文件（`lumen-crash-core/src/test/java/com/chloemlla/lumen/crash/`）；`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/LumenCrashWatchdog.kt:36-37`、`:104-170`；`.github/workflows/lumen-crash-sdk-release.yml:141-149`
- 现状：现有 7 个测试全部只覆盖**纯函数**：JSON 往返与 legacy 兼容、store 的落盘/迁移/回退（用 internal 构造注入临时目录，写得很好）、`classifyResponse` / `normalizeBaseUrl` / `buildRequestBody`、`CrashDeviceId.derive`、`fromWireValue`、`fileProviderAuthority`、作者指纹。**完全没有覆盖**：uncaught handler 的链式调用、`saveReport` 的槽位语义、`uploadedReportIds` 的释放逻辑、启动挂起/卡顿判定的时间边界。而恰恰是 watchdog（历史上误报过、见提交 `fix(crash): only report a startup hang...`）**在结构上无法单测**：构造函数里 `Looper.getMainLooper().thread` 与 `Handler(Looper.getMainLooper())`（`:36-37`）在纯 JVM 下就 NPE，判定逻辑与 `SystemClock` / `Thread.sleep` / `Application` 死死绑在一起。CI 侧：`:lumen-crash-sample:lintDebug`、`assembleDebug`、`compileReleaseKotlin` 都跑了，但**没有 `:lumen-crash-sample:assembleRelease`**——而 sample 正是唯一开了 `isMinifyEnabled = true` + `isShrinkResources = true` 的模块（`lumen-crash-sample/build.gradle.kts:21-22`），也就是说 consumer-rules 与 R8 的实际效果从来没被 CI 验证过，sample README 里"Release minify is enabled in the sample"这句话在 CI 层面是空的。
- 触发场景：任何一次改 watchdog 判定条件（比如修 G11-04）都无法用测试证明不再误报，只能靠人肉推理；任何一次改 consumer-rules（比如修 G11-11）或让 SDK 引入反射/序列化，都要等下游 release 崩了才发现。
- 影响：这个 SDK 最容易出事的两块（时间判定、R8）恰好是零验证区。
- 修复方案：① 把 watchdog 的判定抽成可测纯函数，放进 `LumenCrashWatchdog` 的 `internal companion object` 或新文件 `WatchdogDecisions.kt`：`internal fun shouldReportStartupHang(nowMillis, startedAtMillis, timeoutMillis, startupComplete, userVisible): Boolean` 与 `internal fun freezeDecision(heartbeatAgeMillis, timeoutMillis, foreground, alreadyCandidate, alreadyReported): Decision`，`runLoop` 只负责取时间和调用；`mainHandler` 改 `by lazy`（当前虽是实例字段但仍在构造期求值，改 lazy 后配合注入的时钟可在 JVM 测试里构造）。再补 4 个测试：超时未到不报、超时+可见则报、超时+不可见则退休、卡顿需连续两次才报。② `.github/workflows/lumen-crash-sdk-release.yml:146` 的 assemble 步骤里把 `:lumen-crash-sample:compileReleaseKotlin` 换成 `:lumen-crash-sample:assembleRelease`（sample 无签名配置，release 会走 debug 签名或未签名产物，assemble 本身能过；顺便让 R8 与资源压缩真正执行）。③ `:lumen-crash-core:test` 在 141 行和 146 行各跑一次，去掉一处。
- 风险/注意：①是纯重构，但 watchdog 是 P0 区代码，改完要 CI 全绿再推；②可能第一次就把现有 consumer-rules/资源 keep 的问题炸出来（这正是目的），要预留修 R8 报错的时间。

### [G11-14] 发布纪律：推 main 即发版但没有任何 API 兼容性校验，版本号恒为 `0.1.0-<sha>`，破坏性变更会无感落到下游
- 严重度：P2
- 类别：A 架构（发布契约）
- 位置：`.github/workflows/lumen-crash-sdk-release.yml:111-123`、`:141-149`、`:21-23`；`lumen-crash/sdk.version`（内容始终是 `0.1.0`）；`lumen-crash/README.md:229-233`、`:456`、`:1680`（要求下游"必须自动解析最新 main release，不要固定版本"）
- 现状：main 上任何命中 `lumen-crash*/**` 路径的提交都会发一个版本 `${FILE_VERSION}-${SHORT_HASH}`（即永远是 `0.1.0-xxxxxxxx`）的 GitHub Release + GitHub Packages 包，而文档**要求**下游默认自动解析最新 release。流水线里跑了单测和 lint，但**没有二进制/源码兼容性校验**（无 binary-compatibility-validator、无 API dump 比对），SDK 也没有 `explicitApi()`；public 面很宽：`LumenCrashConfig` 是 public `data class`（其 `copy`/`componentN` 签名随字段增删而变，属二进制破坏）、`CrashReportStore`、`CrashReportPasteUploader`（还带一个 public `@Volatile var` 钩子）、`AuthorIntegrity`、`crashReportFromJson` / `CrashReport.toJson()` 顶层函数全是公开的。版本号里没有任何兼容性信号（永远是 0.1.0 的 patch 前缀）。另外 `concurrency` 用 `cancel-in-progress: false`，两次快速推送会并发发版，先提交的可能后完成，导致按 `published_at` 排序的"latest"解析到较旧的提交。
- 触发场景：给 `LumenCrashConfig` 中间插入一个字段、重命名一个 `LumenCrash` 方法、或把 `crashReportBackendEnabled` 默认值翻转（G11-05 的建议！）——推 main 后自动发版，CLens 下次构建自动吃到 latest，编译失败或行为静默改变，而 release notes 是自动生成的、看不出破坏性。
- 影响：下游构建随机被打断，或更糟：行为变了但编译通过（默认值翻转类）。
- 修复方案：① 引入 `org.jetbrains.kotlinx.binary-compatibility-validator`，对 `:lumen-crash-core` 和 `:lumen-crash` 生成 `api/*.api` 并把 `apiCheck` 加到流水线的测试步骤里（`:141`），签名变更必须显式 `apiDump` 提交，diff 就是破坏性变更的审计点；② 给两个模块开 `explicitApi()`，并把非契约类型（`CrashReportStore`、`AuthorIntegrity`、`CrashThreadDump` 已是 internal、`crashReportFromJson`）收窄为 `internal` 或加 `@RestrictTo`，把 public 面缩到 `LumenCrash` / `LumenCrashConfig(+Builder)` / `CrashReport` / `CrashReportKind` / `LumenCrashGate` / `LumenCrashReportScreen`；③ 破坏性变更时手动 bump `lumen-crash/sdk.version` 的 minor 位，让版本号携带信号（现在这个文件从来没变过）；④ `concurrency` 改 `cancel-in-progress: true`，避免并发发版把 latest 顺序搞乱。
- 风险/注意：②会改变已发布的 public 面，本身就是破坏性变更，应当在同一批里连带把 CLens 的引用改掉；发布物已经包含 sources jar（`withSourcesJar()`）与完整 POM（name/description/license/developer/scm 都有），这部分不需要动。

### [G11-15] 脱敏只覆盖本地路径与 URI，UI 的隐私文案却宣称"最近事件细节已脱敏"；paste 分享把整份报告发到第三方公共站点且自动写进剪贴板
- 严重度：P2
- 类别：G 安全（需确认具体泄露路径）
- 位置：`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/CrashReport.kt:16-20`、`:294-301`；`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/CrashBreadcrumbs.kt:8-12`、`:39-46`；`lumen-crash/src/main/res/values/strings.xml:21`、`:37`；`lumen-crash/src/main/java/com/chloemlla/lumen/crash/ui/LumenCrashReportScreen.kt:1416-1428`、`:445-477`；`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/CrashReportPasteUploader.kt:22`
- 现状：`sanitize()` 只替换 5 个正则：Windows/Linux/macOS 家目录、`content://`、`file://`。异常 message、堆栈、面包屑里的其余一切原样保留——没有对 `Bearer xxx`、`token=`/`key=`/`password=` 之类的查询参数或键值、邮箱、手机号做任何处理，也**没有给宿主任何脱敏钩子**（无 `reportScrubber: ((String) -> String)?`）。面包屑内容完全由宿主决定且只截断到 180 字符。而 UI 上的隐私说明写的是"Local paths, content URIs, and recent event details are sanitized before the report is shown or shared."——"recent event details are sanitized"这半句是不成立的（面包屑只走同一组路径/URI 正则）。paste 分享的说明只有"Uploads the report and copies a shareable HTTPS link."，没告诉用户目标是**第三方公共 pastebin**（`https://paste.gentoo.zip`，任何拿到链接的人都能读，且内容留在第三方服务器上）；上传成功后代码**先自动把链接写进剪贴板**（`:1420-1426`）才弹对话框，用户并没有点"复制"。
- 触发场景：宿主在面包屑里记录了含用户输入的事件（本仓库主 app 的面包屑目前是安全的固定文案，但这是宿主自由字段）；或异常 message 里带了 URL 查询串/SQL 片段。用户点"分享为链接"后，这些内容进入公共 pastebin。文案的问题则是**必然**触发的：只要打开崩溃页就能看到那句不成立的脱敏承诺。
- 影响：下游宿主基于"已脱敏"的承诺放心往面包屑写内容，实际可能把敏感串上传到作者后端 + 公共 pastebin；用户在不知道目标是公共站点的情况下产生一个永久可读的链接。
- 修复方案：① 改文案：`lumen_crash_report_privacy_note` 只保留能兑现的部分（"本地路径与 content/file URI 会被替换"），去掉"recent event details"；`lumen_crash_report_share_as_link_description` 明确写出"上传到第三方公共粘贴服务（<host>），任何拿到链接的人都能查看"，中英两份同步（`values/strings.xml:21,37` + `values-zh/strings.xml:20,36`）；② 给 `LumenCrashConfig` 加 `reportScrubber: ((String) -> String)? = null`，在 `CrashReport.sanitize` 之后、`CrashBreadcrumbs.record` 之内各调用一次，让宿主能补自己的规则；③ `sanitize()` 增加两条通用正则：URL 查询串中的 `(token|key|secret|password|auth|signature)=[^&\s]+` 与 `Bearer\s+[A-Za-z0-9._\-]+` 替换为 `[redacted]`；④ 去掉 paste 成功后的自动剪贴板写入（`:1420-1426`），对话框已有"复制"按钮，让用户显式选择。
- 风险/注意：③的正则要够窄，不要把堆栈里的正常标识符误伤；②的 scrubber 在崩溃线程上执行，宿主实现若抛异常必须被 `runCatching` 兜住（`fromThrowable` 已有 fallback 路径可复用）。

### [G11-16] 宿主回调的执行线程没有契约（崩溃线程 / watchdog 线程 / 主线程三种都可能），配置默认值存在两份真相源
- 严重度：P2
- 类别：A 架构 / B 并发
- 位置：`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/LumenCrash.kt:276-284`（`onCrashSaved` / `onReportSaved` / `onAnrDetected` 的调用点）；`LumenCrashConfig.kt:31-53`（KDoc 未说明线程）；`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/LumenCrashConfigBuilder.kt:17-40` vs `LumenCrashConfig.kt:9-91`；`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/CrashReportPasteUploader.kt:30-31`
- 现状：三个宿主回调都在 `saveReport()` 里同步调用，而 `saveReport` 的调用方有三条完全不同的线程：崩溃发生的**任意线程**（uncaught handler）、**watchdog 后台线程**（`emit` → `recordWatchdogReport`）、**主线程**（`install` → `collectPriorExitReport`）。KDoc 只说"Receives every report after at least one persistence target accepted it"，没有一个字提线程。主 app 的 `onCrashSaved = { scheduleCrashReportUpload(report) }` 因此会在这三种线程上被调用。另外 `LumenCrashConfigBuilder` 把 `LumenCrashConfig` 的默认值**几乎逐条抄了一遍**（24 个 `var` 里除三个必填元数据外全部重复：`pasteUploadEnabled = true`、`anrWatchdogTimeoutMillis = 5_000L`、`crashReportBackendEnabled = true` …），两处各自持有一份默认值——改一处另一处静默不同步，而两条 install 路径（`install(app, config)` 与 `install(app) { }`）分别命中不同的那份。`CrashReportPasteUploader.shouldSkipManualProxy` 是个 public `@Volatile var` 全局钩子，KDoc 里直接点名宿主类 `ClashPartnerCompat`——通用 SDK 里嵌了一个特定宿主的概念。
- 触发场景：默认值双源——把 `crashReportBackendEnabled` 默认改成 `false`（G11-05）时只改 `LumenCrashConfig` 会漏掉 builder，走短路径的示例与下游依然默认上报，且没有任何测试会失败；线程契约——一个下游在 `onCrashSaved` 里更新 UI 或做 Room 同步写，在主线程分支（PRIOR_EXIT）上触发 StrictMode/ANR，在 watchdog 分支上抛 `CalledFromWrongThreadException`（被 `runCatching` 吞掉，表现为回调静默失效）。
- 影响：下游写出线程不安全的回调而得不到任何提示；SDK 默认值的实际生效值取决于宿主用了哪条 install 路径。
- 修复方案：① 在 `LumenCrashConfig` 的三个回调字段 KDoc 上写明"可能在崩溃线程、watchdog 线程或主线程被调用，实现必须线程安全且不得阻塞"，两份 README 的回调表同步；② 让 builder 的字段直接以 `LumenCrashConfig` 的默认值为初值——把 `LumenCrashConfigBuilder` 的属性初值改成引用一个 `private val defaults = LumenCrashConfig(appDisplayName = "", versionName = "", versionCode = 0)` 的对应字段（例如 `var pasteUploadEnabled: Boolean = defaults.pasteUploadEnabled`），消掉第二份真相源；③ `shouldSkipManualProxy` 的 KDoc 去掉宿主类名，改成中立描述（"宿主可在此声明当前网络环境需要 `Proxy.NO_PROXY`"），或把它变成 `LumenCrashConfig` 的一个字段而不是全局可变状态（全局 `var` 在多进程/多次 install 场景下无法回收）。
- 风险/注意：②的写法要确保 `LumenCrashConfig` 的构造不产生副作用（当前是纯 data class，安全）；③若改成 config 字段属于 public API 变更，需配合 G11-14 的兼容性流程。

### [G11-17] 崩溃 handler 的 fallback 分支没有被 `runCatching` 包住：OOM 崩溃时报告丢失，且系统 handler 不再执行（Play Console 也看不到）
- 严重度：P1
- 类别：D 生命周期 / E 韧性
- 位置：`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/LumenCrash.kt:209-211`（handler 内）、`:126-127`（`record()` 内同构）
- 现状：
  ```kotlin
  val report = runCatching { CrashReport.fromThrowable(throwable, appInfo) }
      .getOrElse { CrashReport.fromThrowableFallback(throwable, it, appInfo) }
  if (config != null) { saveReport(report, config) } else { ... }
  val chained = previousHandler
  if (chained != null && ...) chained.uncaughtException(thread, throwable)
  ```
  `getOrElse` 的 lambda **本身不在任何 try 里**。`fromThrowableFallback`（`CrashReport.kt:106-137`）会调 `AuthorIntegrity.verifyOrThrow`（可抛 `SecurityException`）和 `throwable.stackTraceToString()`（要分配一大串字符串）。一旦它抛出，异常就穿出整个 handler：`saveReport` 不会执行（报告丢失），**`previousHandler.uncaughtException` 也不会执行**——系统的 `KillApplicationHandler` 被跳过，AMS 收不到崩溃通知，logcat 里只剩"handler 自己抛异常"，Play Console / 系统崩溃统计里这次崩溃**彻底消失**。
- 触发场景：最现实的一条是 **OOM 崩溃**：第一次 `fromThrowable` 因为 `OutOfMemoryError` 失败（`runCatching` 会捕获 `Throwable` 所以 OOM 走进 fallback），fallback 又去 `stackTraceToString()` 再分配一次 → 第二次 OOM → 逃逸。内存已经耗尽的进程里，"再构造一份报告"这个 fallback 策略必然重蹈覆辙。另一条是被 fork/篡改后 `verifyOrThrow` 抛 `SecurityException`。
- 影响：一整类崩溃（OOM）既拿不到自己的报告，又因为跳过系统 handler 而在 Play Console 里消失——比"没装这个 SDK"更糟。
- 修复方案：把整个 handler 体包成一层 `runCatching`，并保证链式调用在 `finally` 语义下执行：
  ```kotlin
  val handler = Thread.UncaughtExceptionHandler { thread, throwable ->
      runCatching { /* 现有的 报告构建 + saveReport 全部逻辑 */ }
      // 无论上面成败，都必须走到这里
      val chained = previousHandler
      if (chained != null && chained !== handlerRef.get()) chained.uncaughtException(thread, throwable)
      else if (config?.killProcessWhenNoPreviousHandler != false) { Process.killProcess(Process.myPid()); exitProcess(10) }
  }
  ```
  同时把 fallback 也各自兜住：`.getOrElse { runCatching { fromThrowableFallback(...) }.getOrNull() }`，拿不到报告就直接跳过持久化。`record()`（`:126-127`）同样处理。
- 风险/注意：这条与 2026-08-05 那份旧审计的 **STA-01（High）** 是同一处，四周过去仍未修复，且它是本组唯一"会让崩溃从系统统计里消失"的问题，建议**最高优先级**处理。改动只涉及 handler 的控制流，不改任何数据格式。

### [G11-18] 两个上传器都开着 `instanceFollowRedirects = true`：Android 的 `HttpURLConnection` 由 OkHttp 实现，默认会跟随 `https → http` 降级重定向
- 严重度：P2
- 类别：G 安全（需确认）
- 位置：`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/CrashReportPasteUploader.kt:66`、`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/CrashReportBackendUploader.kt:62`；响应读取 `CrashReportPasteUploader.kt:147-152`、`CrashReportBackendUploader.kt:146-153`
- 现状：两处都显式 `instanceFollowRedirects = true`，而 `normalizeBaseUrl` 的 HTTPS 校验只作用于**初始** URL。Android 的 `HttpURLConnection` 自 4.4 起由 OkHttp 实现，其 `followSslRedirects` 默认为 `true`，也就是说 `https://` 请求收到指向 `http://` 的 3xx 时**会跟随**（这与桌面 OpenJDK 拒绝跨协议重定向的行为不同）。重定向后的请求会重发请求体——也就是整份崩溃报告——到明文端点。另外两个 `readBody` 都把响应整体读进内存，没有大小上限，服务端可以返回任意大的响应体。
- 触发场景：`pasteUploadBaseUrl` / `crashReportBackendBaseUrl` 都是宿主可配置的（默认分别是第三方公共 pastebin 和作者后端）。这些域名的 DNS 被劫持、或 pastebin 被接管、或宿主误配了一个会 302 到 http 的反代时，崩溃报告以明文过网。需确认：本次未实测 Android 上的重定向行为，判断依据是 OkHttp 的 `followSslRedirects` 默认值。
- 影响：崩溃报告（含堆栈、面包屑、设备指纹、设备 ID）可能被明文传输。
- 修复方案：两处都改为 `instanceFollowRedirects = false`，并在 `responseCode in 300..399` 时读 `Location` 头、只在其为 `https://` 时手动重发一次（最多一跳），否则按 `REJECTED` 处理。`readBody` 增加上限（如 `reader.read(buf)` 累计到 64 KB 就停）——响应只用来读 `accepted` 布尔值和 paste id，不需要更多。
- 风险/注意：默认 paste 服务如果依赖一次 `http→https` 或路径规范化的重定向，关掉自动跟随会让"分享为链接"失效；实现手动一跳时要保留对 `https` 的校验（`resolveShareableUrl` 已有类似校验可复用）。

### [G11-19] watchdog 抓取面包屑时会无限期阻塞在 `@Synchronized` 锁上：主线程恰好卡在 `record()` 里时，卡顿报告丢失且 watchdog 永久停摆
- 严重度：P2
- 类别：B 并发
- 位置：`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/CrashBreadcrumbs.kt:18-32`；`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/LumenCrashWatchdog.kt:188-192`（`emit`）；`lumen-crash-core/src/main/java/com/chloemlla/lumen/crash/CrashReport.kt:181`（`recentEvents = CrashBreadcrumbs.snapshot()`）
- 现状：`record()` 与 `snapshot()` 共用同一把对象锁（`@Synchronized`）。`record()` 的临界区并不短：5 次正则替换 + `Instant.atZone().format()` + deque 操作。watchdog 的 `emit()` → `onReport` → `CrashReport.fromWatchdog` → `snapshot()` 会**无限期**等这把锁（没有超时）。`emit` 外层的 `runCatching` 对"阻塞"毫无作用。
- 触发场景：主线程在 `CrashBreadcrumbs.record()` 内部被卡住（宿主在主线程记面包屑，随后主线程被 IO/binder/锁竞争卡住，或者 OS 在这个窗口挂起了进程）→ watchdog 线程停在 `snapshot()` 上 → `runLoop` 再也回不去 → 心跳不再 post、卡顿/启动挂起报告全部不再产生，直到进程结束。窗口很窄但后果是"看门狗自己被同一场卡顿卡死"。
- 影响：最需要 watchdog 工作的场景（真实主线程卡死）里，watchdog 有一定概率静默失效。
- 修复方案：`CrashBreadcrumbs` 换成不需要长期持锁的读取：内部用 `ReentrantLock`，`snapshot()` 用 `tryLock(50, MILLISECONDS)`，拿不到就返回 `emptyList()`（报告少了面包屑，但报告本身还在）；或者把存储换成 `ConcurrentLinkedDeque` + `AtomicInteger` 计数，读侧完全无锁。顺带把 `record()` 里的 5 个正则从每次调用重新匹配改为已缓存的顶层 `val`（已经是顶层 `val`，无需改），并考虑把时间格式化移出临界区（`Instant.format` 在锁内是纯浪费）。
- 风险/注意：`tryLock` 失败返回空列表会让极少数报告没有面包屑，需在报告里留个标记（如追加一条 `"<breadcrumbs unavailable>"`）以免误判为"宿主没记面包屑"。

## 已核查但无问题的点

- **uncaught handler 的链式调用是正确的**（`LumenCrash.kt:196-228`）：`previousHandler` 在 install 时保存，handler 末尾 `chained.uncaughtException(thread, throwable)` 交还给系统 `KillApplicationHandler`，因此 logcat / Play Console / 其它 SDK 的崩溃处理**不会被吞掉**；`killProcessWhenNoPreviousHandler` 只在确实没有前驱 handler 时才自己杀进程。重复 install 有 `existing != null && previousHandler === existing` 的短路（`:198-199`），不会自我链接成环。修复阶段不要动这段结构。
- **handler 内部除 fallback 那一支外都有兜底**：`store().save` / 三个宿主回调 / 上传提交全部包在 `runCatching` 里（`:275-287`、`:327`）；唯一的缺口是 `getOrElse` 的 lambda 本身，见 G11-17。
- **paste 上传的 executor 已经会 shutdown**（`LumenCrashReportScreen.kt:1406`、`:1437-1439`）：旧审计 STA-02 的"每次分享泄漏一个非 daemon 线程"已修复，不要重复处理。每次点击新建一个 executor 略显浪费但 `pasteUploadInFlight`（`:131`、`:446`）已挡住并发点击，行为正确。
- **磁盘无上限打满的风险不存在**：`CrashReportStore` 是固定文件名单槽位（`crash_report.json`），不是追加式日志、不按时间累积；面包屑固定 40 条 × 180 字符（`CrashBreadcrumbs.kt:15`、`:20`）；线程 dump 硬上限 64 KB（`CrashThreadDump.kt:7`、`:42-45`）；`ApplicationExitInfo` trace 硬上限 128 KB（`PriorExitCrashCollector.kt:88`）。这几个上限都不要在修复时放开。
- **落盘是原子的**：`writeAtomically`（`CrashReportStore.kt:128-143`）走临时文件 + `renameTo`，`renameTo` 失败还有直写兜底，`finally` 里删临时文件；多目标写入只要有一个成功就算成功（`writeAny`），失败才抛 `IOException`。
- **API 26 兼容性逐个核对通过**：`java.time`（`Instant` / `ZoneId` / `DateTimeFormatter.ofPattern`）本身就是 API 26 引入，`minSdk 26` 下无需 `coreLibraryDesugaring`；`Application.getProcessName()` 有 `SDK_INT >= P` 守卫（`CrashReport.kt:237-241`）；`longVersionCode` 有 `>= 28` 守卫、`PackageInfoFlags.of` 有 `>= 33` 守卫（`LumenCrashConfigBuilder.kt:44-52`、`:100-105`）；`getHistoricalProcessExitReasons` / `ApplicationExitInfo` 双重守卫（`PriorExitCrashCollector.kt:20`、`:67`、`isSupported()` 在 `LumenCrash.kt:261` 先判断）；`ArrayDeque` 是 Kotlin stdlib 的实现而非 `java.util`。没有发现会在 API 26 上抛 `NoSuchMethodError` 的调用。
- **`PriorExitCrashCollector` 的"只报一次"标记与"不覆盖真实报告"保护是对的**（`:44-60`、`LumenCrash.kt:265-266`）：持久化 `last_processed_timestamp_millis`，且已有待处理报告时不写入。这是 G11-03 里 watchdog 路径应该照抄的范式。
- **`CrashDeviceId` 用 `commit()` 而非 `apply()`** 是有意为之且正确（`:60-62` 注释）：崩溃进程即将死亡，`apply()` 的异步刷盘会让下次启动为同一设备生成不同 ID。它跑在上传执行器线程上，不阻塞主线程。
- **初始 URL 的 HTTPS 强制是到位的**：`CrashReportBackendUploader.normalizeBaseUrl` 与 `CrashReportPasteUploader.normalizeBaseUrl` 都 `require(startsWith("https://"))`，`resolveShareableUrl` 还校验返回 id 的字符集与协议（`CrashReportPasteUploader.kt:112-135`），`openHttpsUrl` 二次校验协议（`LumenCrashReportScreen.kt:1472`）。配置层面无法配出明文端点——唯一的缺口是重定向跟随（见 G11-18）。
- **上传超时是齐全的**：连接 15 s / 读 30 s 且 `coerceAtLeast(1_000)`（`CrashReportBackendUploader.kt:42-43`、`:60-61`），`CrashUploadOutcome` 三态分类（4xx 不重试、429/5xx/200+accepted=false 才重试）与后端行为对齐并有 7 条测试覆盖。G11-01 建议的只是在崩溃路径上把超时收短，不是"缺超时"。
- **FileProvider 配置自洽**：`lumen_crash_file_paths.xml` 的 `cache-path lumen-crash-share/` 与 `resolveCrashShareDirectory` 优先写 `cacheDir/lumen-crash-share` 对得上，authority 用 `${applicationId}.lumen.crash.fileprovider` 避免与宿主自带 FileProvider 冲突，`exported=false` + `grantUriPermissions=true` + 通过 chooser 外层 Intent 传递 `FLAG_GRANT_READ_URI_PERMISSION` 和 `clipData`（`LumenCrashReportScreen.kt:1325-1334`）都是正确写法。仅一处小瑕疵不单列成缺陷：`resolveCrashShareDirectory` 最后回退到 `cacheDir` 根（`:1306`），该路径不在 SDK 自己的 paths 白名单内，用 SDK 默认 authority 时 `getUriForFile` 会抛异常并被兜底成"分享失败"toast，行为可接受但注释里"由宿主的 `<cache-path path="." />` 覆盖"的假设对第三方宿主不成立。
- **`lumen-crash-sample` 没有硬编码任何真实凭据或上报地址**（`SampleApplication.kt` 只设了 title/message 与 watchdog 开关），不存在"示例被当生产配置抄走导致 token 泄露"的问题。它的 `attachBaseContext` + `onCreate` 双重 `installSafely` 是合理的兜底顺序；唯一可改进处是两处都忽略了 `installSafely` 的 `Boolean` 返回值，示例因此没演示"install 失败时怎么办"。
- **`recordNonFatal` 不占用待处理报告槽位**的设计是对的（`LumenCrash.kt:139-152` 与 README `:1160` 一致），G11-03 建议 `FREEZE` 也走这条路正是照它的范式。
- **`CrashReportKind.fromWireValue` 对未知/缺失值回退 `CRASH`**（`CrashReportKind.kt:19-22`）并有测试，跨 SDK 版本读旧报告不会崩。
