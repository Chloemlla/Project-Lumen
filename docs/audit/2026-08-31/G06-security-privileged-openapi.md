# G06 安全 / 完整性门禁 / Shizuku 提权 / 特权设备控制 / 对外开放 API / 原生层 审查报告

- 审查文件数：20（19 个 Kotlin/AIDL + 1 个 C++），总行数：约 3683 行
  （另为确认调用关系读了 `AndroidManifest.xml`、`app/build.gradle.kts`、`CMakeLists.txt`、`ProjectLumenApplication.kt`、`MainActivity.kt`、`ShizukuResilienceWorker.kt`、`RuntimeStateEntity.kt`、`ProjectLumenMmkv.kt` 的相关片段，缺陷只报在本组文件上）
- 结论摘要：**命令注入这一类经典漏洞本组基本不存在**——所有拼进 shell 的包名都过了 `ANDROID_PACKAGE_NAME_REGEX` 全匹配，UID 是 `Int`，其余全是编译期字面量，`ShizukuShellUserService` 只对本进程可见。真正的风险集中在三处完全不同的地方：（1）`PrivilegedDeviceControlCoordinator` 允许**后端下发的策略把"本地距离监测"开关当作"上传摄像头原图"的用户同意**，`requiresExplicitConsent` 分支写成了恒等式，还回传 `userConsentGranted = true`，这是本组最严重的隐私/合规问题；（2）`DeviceSecurityGate` 的 fail-closed 语义过宽——**一次扫描超时、SELinux 非 enforcing、TEE 判定不一致都会让设备终身 BLOCKED**，且全程无重试，正常用户（非 enforcing 的第三方 ROM、无硬件证明的低端机、模拟器）会直接失去所有前台服务与业务后端能力；（3）`ShizukuCapabilityManager` 是典型上帝类，`queryState()` 在 binder 掉线时**清空原生护眼的缓存态**，导致用户关闭护眼时程序误判"本来就没开"而直接返回成功，系统夜灯/降亮度/Extra Dim（还开了 `persist_across_reboots`）永久残留在设备上。另外原生层的 adb 检测判据（`/proc/net/unix` 里包含 `adb`）要么因 SELinux 恒为死代码，要么会把所有开了 USB 调试的用户挡在门外；`is_traced_via_ptrace()` 的 `PTRACE_TRACEME` 用法写反，永远返回 false 却每次都 `fork()`。日志扫描结果干净：本组 20 处 `Log.*` 无一打印 token、密钥、UID 映射表。

## 缺陷清单

### [G06-01] 后端策略即可把"本地护眼距离监测"变成摄像头原图持续上传，同意判定是恒等式
- 严重度：P0
- 类别：G 安全（隐私/合规）
- 位置：`app/src/main/java/com/projectlumen/app/core/devicecontrol/PrivilegedDeviceControlCoordinator.kt:192-199`、`:267-302`、`:304-346`、`:348-398`、`:209-230`
- 现状：
  ```kotlin
  private suspend fun hasLocalUserCameraConsent(policy: SilentVisionPolicy): Boolean {
      val featureOn = settings.proximityMonitoringEnabled || settings.blinkMonitoringEnabled
      if (!featureOn) return false
      if (!policy.requiresExplicitConsent) return true
      // In-app feature toggles are the explicit consent surface for analyzer upload.
      return featureOn          // ← 与上一行 return true 等价，分支毫无作用
  }
  ```
  `startCaptureLoop` 在 `policy.frameUploadEnabled || policy.surfaceAnalysisUploadEnabled` 时循环抓帧，`uploadFrame` / `uploadSurfaceFrame` 把 **JPEG 原图 base64 后整帧上传**；`ensureSilentVisionSession` 向后端回传 `userConsentGranted = true`。这两个 `*UploadEnabled` 与 `maxFps` 全部来自 `refreshPolicy()` 拉取的**远端策略**（`loadCachedPolicy` 默认 false，但后端一次下发即被 `persistPolicy` 落库长期生效）。
- 触发场景：用户只在设置里打开了"距离监测/眨眼监测"（其语义是本地判断坐姿距离）。后端把 `policy_privileged_silent_vision.enabled` 与 `frameUploadEnabled` 置 true 后，应用即以最高 5 fps 持续上传人脸原图，UI 无任何提示、无独立开关、无停止入口；`maxSessionMinutes`（解析了但代码里从未使用）也不会终止会话，只有用户关掉距离监测或后端主动 `continueStream=false` 才停。
- 影响：把本地功能开关冒充成生物特征数据上传的知情同意，用户不可见、不可撤销、无时限；对外声明的 "explicit non-goals: 无用户不可知的静默采集" 与实际行为矛盾。生物特征（人脸图像）跨境/上云在国内属敏感个人信息，需单独告知同意。
- 修复方案：
  1. `hasLocalUserCameraConsent` 改为读取**独立的上传同意项**（新增 `settings.remoteFrameUploadConsentGrantedAt` 之类字段，由专门的弹窗写入），`requiresExplicitConsent = true` 时必须该字段非 0 才返回 true；不要复用 `proximityMonitoringEnabled`。
  2. `ensureSilentVisionSession` 的 `userConsentGranted` 传真实同意状态，不要写死 `true`。
  3. 在 `startCaptureLoop` 里落实 `policy.maxSessionMinutes`：记录 `sessionStartedAt`，超时 `break` 并把 `sessionIdRef` 置空。
  4. 会话运行期间必须有用户可见通道（常驻通知或状态条）与"立即停止"入口。
- 风险/注意：改动会让后端已经下发过 `frameUploadEnabled` 的设备在用户补授权前停止上传，属预期行为；`persistPolicy` 已落库的旧策略需要一次迁移或按新同意项重新判定。

### [G06-02] 设备安全门禁 fail-closed 过宽且不可恢复：一次扫描超时/SELinux 非 enforcing/TEE 不一致 = 该设备终身不可用
- 严重度：P0
- 类别：G 安全 + E 韧性
- 位置：`app/src/main/java/com/projectlumen/app/core/security/DeviceSecurityGate.kt:53-69`、`:98-107`、`:69`（`isServiceAllowed`）；消费方 `ProjectLumenApplication.kt:342/377/390/399/412`、`core/api/ProjectLumenApiClient.kt:315`
- 现状：
  ```kotlin
  fun startStartupScan(scope: CoroutineScope) {
      if (!scanStarted.compareAndSet(false, true)) return   // 一辈子只扫一次
      ...
      _state.value = if (nativeIntegrityOk && isSafe(result)) State.ALLOWED else State.BLOCKED
  }
  fun isServiceAllowed(): Boolean = _state.value == State.ALLOWED
  private fun isSafe(a: SecurityAssessment) = a.completed && !a.rooted &&
      a.hardwareIntegrityOk != false && a.selinuxEnforcing != false && a.teeAttestationOk != false
  ```
  `_state` 初值 `UNKNOWN`，`isServiceAllowed()` 只认 `ALLOWED`。`fullScan()` 的超时上限是 `DeviceSecurityScanner.DEFAULT_SCAN_TIMEOUT_MS = 60_000`。
- 触发场景：
  1. **启动窗口期**：从 `Application.onCreate` 到 fullScan 结束（最长 60 s），`isServiceAllowed()` 为 false，用户此刻点"开始"→ `startTimerService()` 只打一行 `Log.w` 静默返回，计时器不启动、光线/距离监测不启动，且没有任何界面提示。
  2. **扫描超时/异常一次**：`completed = false` → `BLOCKED`，`scanStarted` 已 CAS 成 true，进程存活期内不再重扫；且没有任何重试或用户手动重扫入口。
  3. **正常用户误杀**：SELinux permissive 的第三方 ROM/模拟器（`selinuxEnforcing == false`）、缺硬件密钥证明的低端机或 TEE 判定 `!= CONSISTENT`（`teeAttestationOk == false`）、CRooot 硬件项 `overallOk == false` —— 三者任一即 `BLOCKED`：所有前台服务（计时、光线、距离、悬浮窗）+ 除 5 个 reporting capability 之外的全部后端调用（`requireBackendAllowed` 抛 `BackendCommunicationBlockedException`）全部失效。
- 影响：应用对这类设备端到端不可用，且症状是"点按钮没反应"而非明确报错，用户无法自查、无法恢复（重启进程后还会再扫一次，但判据不变仍然 BLOCKED）。这是把"风控信号"直接当成"功能开关"的典型误杀。
- 修复方案：
  1. 拆分严重度：只有 `rooted == true` 才 `BLOCKED`；`selinuxEnforcing == false` / `teeAttestationOk == false` / `hardwareIntegrityOk == false` 降级为新增的 `DEGRADED` 状态——本地护眼/计时等纯本地功能照常放行，只对高影响后端能力（`requireBackendAllowed`）拒绝。
  2. `completed == false`（超时/异常）**不得等于不安全**：保持 `UNKNOWN` 并允许本地服务运行，同时提供 `fun rescan(scope)` 重置 `scanStarted` 后重扫（带退避）。
  3. `isServiceAllowed()` 在 `UNKNOWN` 期间对本地服务返回 true（或让 `startStartupScan` 先跑 `quickScan()` 快速给出初值，再跑 fullScan 修正），避免启动窗口静默吞掉用户操作。
  4. 被拒绝时要有用户可见原因（现在只有 `Log.w`），至少在设置页显示当前 `state` 与判据。
- 风险/注意：放宽后 `ProjectLumenApplication` 各 `isServiceAllowed()` 调用点语义变化，需要同时改这些调用点区分"本地功能"与"高影响能力"；`backendEvidence()` 上报字段不变，后端仍可自行拒绝高影响操作。

### [G06-03] Shizuku binder 掉线会清空原生护眼缓存态 → 用户关闭护眼时误报成功，系统夜灯/低亮度/Extra Dim 永久残留
- 严重度：P0
- 类别：A 架构（缓存无失效路径）+ F 持久化一致性
- 位置：`app/src/main/java/com/projectlumen/app/core/shizuku/ShizukuCapabilityManager.kt:342-351`（`queryState` 的 `!binderAvailable` 早返回）、`:235-258`（关闭分支）、`:459-466`（`clearNativeDisplayAdjustments`）、`:490-503`（`setExtraDim` 写 `persist_across_reboots`）、`:386-391`（写 `screen_brightness_mode 0`）；触发方 `core/services/ShizukuResilienceWorker.kt:28`（每 15 min 调 `isReady()`）
- 现状：
  ```kotlin
  private fun queryState(error: String = ""): ShizukuCapabilityState {
      val binderAvailable = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
      if (!binderAvailable) {
          return ShizukuCapabilityState(binderAvailable = false, ...)  // ← 全默认值，nativeEyeProtectionApplied 归 false
      }
      ...  // binder 在时才 copy 回 _state.value 的 native* 字段
  }
  ```
  而关闭护眼的分支是：
  ```kotlin
  if (!shouldEnable) {
      if (!_state.value.nativeEyeProtectionApplied) { _state.value = ...; return@withContext true }  // 直接"成功"
  ```
- 触发场景：护眼已生效（夜灯开、亮度被压低、Extra Dim 开且 `reduce_bright_colors_persist_across_reboots=1`）→ 用户重启手机或 Shizuku 进程被杀（Shizuku 重启后需手动激活，极常见）→ `ShizukuResilienceWorker` 每 15 分钟调用 `isReady()`，`queryState()` 因 `pingBinder()` 为 false 返回全默认状态，`nativeEyeProtectionApplied` 被抹成 false → 用户在设置里关闭原生护眼 → 走上面的早返回，**返回 true 且完全不执行任何 shell 命令**。
- 影响：设备屏幕永久偏黄 + 亮度被锁在手动低值 + Extra Dim 常开（还跨重启保持），而应用界面显示"已关闭"。用户只能自己去系统设置里逐项关，且不会想到是本应用造成的。即使 Shizuku 恢复也不会自愈。
- 修复方案：
  1. `queryState()` 的 `!binderAvailable` 分支改成 `_state.value.copy(binderAvailable = false, permissionGranted = false, permissionRequestable = false, lastCheckedAt = ..., lastError = error)`，即只降级连通性字段，保留 `native*` 已应用态（这些是"我们对设备做过什么"的记录，与 Shizuku 是否在线无关）。
  2. 已应用态必须落到持久化（DataStore/MMKV），进程重启后仍知道"设备上还有我们留下的调整"，否则杀进程同样丢。
  3. 关闭分支在 `!currentState.ready` 且**记录显示曾经应用过**时，应报"需要 Shizuku 才能撤销"（现在这条路径存在，但被第 1 点的缓存清空绕过了）。
  4. `clearNativeDisplayAdjustments()` 还漏了两项还原：`settings put system screen_brightness_mode 1`（`applyNativeEyeProtectionTarget:391` 强行改成 0 后从未恢复）和 `settings put secure reduce_bright_colors_persist_across_reboots 0`；同时应记录并还原进入前的原始亮度值（当前无处保存）。
- 风险/注意：第 1 点会让 `state.nativeEyeProtectionApplied` 在 Shizuku 离线时仍为 true，UI 若用它来渲染开关，需确认展示逻辑（应显示"已应用，但当前无法修改"）。

### [G06-04] OpenAPI 的控制类方法落在 dangerous 权限下，signature 权限只保护了一个方法；调用方签名白名单默认为空
- 严重度：P1
- 类别：G 安全（越权）
- 位置：`app/src/main/java/com/projectlumen/app/openapi/LumenOpenService.kt:39-53`、`:83-103`；`app/src/main/AndroidManifest.xml:5-14`、`:73-108`、`:188-195`
- 现状：
  ```kotlin
  override fun startFocusSession(tag: String?, durationMs: Long) {
      val callerPackage = requireCaller(LumenOpenContracts.PERMISSION_ACCESS_CORE)   // dangerous
  override fun stopFocusSession() {
      val callerPackage = requireCaller(LumenOpenContracts.PERMISSION_ACCESS_CORE)   // dangerous
  override fun triggerEyeRelaxation() {
      val callerPackage = requireCaller(LumenOpenContracts.PERMISSION_TRIGGER_CONTROL) // signature
  ```
  `ACCESS_LUMEN_CORE` 是 `protectionLevel="dangerous"`（用户可授予任意应用），`TRIGGER_LUMEN_CONTROL` 才是 signature。`verifyTrustedSignatureIfConfigured` 在 `BuildConfig.OPEN_API_TRUSTED_SIGNATURE_SHA256` 为空时**直接 return 放行**，而该值默认就是空串（`app/build.gradle.kts:155` 从环境变量取，未配置即空）。
- 触发场景：任意第三方应用声明 `uses-permission ACCESS_LUMEN_CORE`，诱导用户在权限弹窗点允许（文案是本应用自定义的 label/description，用户很难判断影响），随后即可绑定 `LumenOpenService` 反复调用 `startFocusSession` / `stopFocusSession` 控制用户的专注计时，并读取 `getEyeFatigueLevel()`（健康类推断数据）与 `getContinuousScreenTime()`。
- 影响：读数据与写控制共用同一档权限，"控制"能力实际下沉到了用户一次点击就能给出去的 dangerous 权限；且没有任何调用方白名单兜底（默认空）。第三方可任意起停用户的专注会话，配合 [G06-05] 还能反复触发遥测上传。
- 修复方案：
  1. `startFocusSession` / `stopFocusSession` 改为 `requireCaller(LumenOpenContracts.PERMISSION_TRIGGER_CONTROL)`，与 `triggerEyeRelaxation` 对齐：**只读走 ACCESS_CORE，任何改变运行状态的走 signature**。若确实要给非同签名应用开放控制，应改成"每调用方一次性用户确认 + 可撤销"的模型，而不是靠 dangerous 权限。
  2. `verifyTrustedSignatureIfConfigured` 空配置时的 fail-open 需要显式决策：至少在 release 构建里当 `OPEN_API_TRUSTED_SIGNATURE_SHA256` 为空时拒绝所有非同签名调用方（`checkSignatures(...) == SIGNATURE_MATCH` 之外一律拒绝），并在 CI 里配置该 secret。
  3. `packageSignatures` 在单签名者时用 `signingCertificateHistory`（含轮换前的历史证书）→ 已经把密钥轮换走的调用方仍会通过；判定白名单时应改用 `apkContentsSigners`。
- 风险/注意：把控制方法提到 signature 级会让现有非同签名接入方失效——这是 API 契约变更，需同步 `docs/` 里的开放 API 说明与版本号。

### [G06-05] 每次 OpenAPI 调用都在 binder 线程 `runBlocking` 里同步做一次强制遥测网络上传，且异常会打崩本应用进程
- 严重度：P1
- 类别：E 韧性 + A 架构
- 位置：`app/src/main/java/com/projectlumen/app/openapi/LumenOpenService.kt:24-58`；`app/src/main/java/com/projectlumen/app/openapi/LumenOpenRuntimeController.kt:85`、`:99`、`:137`、`:144`、`:212-220`
- 现状：
  ```kotlin
  override fun getEyeFatigueLevel(): Int {
      requireCaller(...); return runBlocking(Dispatchers.IO) { controller.getEyeFatigueLevel() }
  }
  // controller 侧，每个写操作末尾：
  private suspend fun uploadOpenApiTelemetry(sourceApp: String?) {
      app.telemetry.uploadCurrentSnapshot(force = true, sourceApp = ...)   // force = true，同步等网络
  }
  ```
- 触发场景：第三方应用（同步 AIDL 调用）调 `startFocusSession`：本进程 binder 线程在 `runBlocking` 里依次做 Room 读写 → 通知重排 → `startTimerService()` → **一次强制遥测 HTTP 上传**，弱网下要等到 OkHttp 超时才返回。调用方主线程若直接调用（AIDL 同步方法的常见写法）会 ANR；同时占满本应用 binder 线程池（默认 16）会让后续调用排队。
- 影响：① 接入方 ANR / 卡顿；② 第三方可通过反复调用无限触发强制遥测上传（耗用户流量、污染后端指标）；③ **更严重**：`runBlocking` 里抛出的非 binder 白名单异常（`SQLiteException`、`ForegroundServiceStartNotAllowedException`、`IllegalStateException` 等）会在 binder 线程逃逸，`Binder.execTransact` 只能透传少数几种异常类型，其余会终结本应用进程——即第三方调用能远程打崩 Project Lumen。
- 修复方案：
  1. 把 `uploadOpenApiTelemetry` 从同步链路里摘出来：改成 `app.applicationScope.launch { app.telemetry.uploadCurrentSnapshot(force = false, ...) }`（fire-and-forget + 不强制），或直接只记录一条本地事件由既有周期上报带走。
  2. 每个 stub 方法用 `runCatching` 包住 controller 调用，只把 `SecurityException` / `IllegalArgumentException` 透传给调用方，其他异常转成安全的默认返回值或 `IllegalStateException`，绝不让任意异常穿过 binder 边界。
  3. `runBlocking` 加超时（`withTimeout(2_000)`），避免 binder 线程被长任务占死；读方法（`getEyeFatigueLevel` 等）本来就只读 Room，超时后返回上次值即可。
- 风险/注意：`triggerEyeRelaxation` 的返回值当前被 AIDL 丢弃（`void`），改成异步遥测不影响调用方语义。

### [G06-06] 外部调用直接构造全新 `RuntimeStateEntity` 覆盖单行运行态，抹掉进行中的会话与所有传感器运行标记
- 严重度：P1
- 类别：F 持久化一致性
- 位置：`app/src/main/java/com/projectlumen/app/openapi/LumenOpenRuntimeController.kt:58-86`（`startFocusSession`）、`:162-175`（`newExternalRestState`）
- 现状：
  ```kotlin
  val nextRuntime = RuntimeStateEntity(
      activeEngine = ActiveEngine.POMODORO.name, pomodoroPhase = FOCUS, pomodoroCycleIndex = 1, ...
  )   // 其余 30 余个字段全部落回 data class 默认值
  runtimeRepository.upsert(nextRuntime)
  ```
  `RuntimeStateEntity` 是 `@PrimaryKey val id: Int = 1` 的单行表，`upsert` 整行替换。`startFocusSession` 在覆盖前**没有对进行中的会话结算统计**（对比 `stopFocusSession` 有 `applyPomodoroDelta`，`triggerEyeRelaxation` 有 `applyEyeDelta`）。
- 触发场景：用户正在跑护眼提醒（REMINDER/WORKING）或已跑到第 4 个番茄周期，第三方应用调用 `startFocusSession`。
- 影响：① 该次提醒会话已累积的用眼时长永久丢失（未结算就被覆盖）；② `pomodoroCycleIndex` 被重置为 1，用户的周期计数回退；③ `proximityMonitoringActive` / `proximityTooClose` / `ambientTooDark` / `proximityLastWarningAt` / `blinkLastWarningAt` 等被清零 → 监测服务与 DB 状态不一致，告警去抖时间戳丢失会立刻重复弹一次提醒；④ `isManuallyPaused` / `suspendedUntil` 被清空 → 外部应用可以解除用户设置的暂停/免打扰。
- 修复方案：`startFocusSession` 改成"先结算再 `copy`"：
  1. 读到 `runtime` 后，若 `activeEngine == REMINDER` 用 `reminderEngine` 的停止/结算路径拿到 `eyeStatsDelta` 并 `statisticsRepository.applyEyeDelta(...)`；若 `== POMODORO` 走 `pomodoroEngine.stop(...)` + `applyPomodoroDelta(...)`（与 `stopFocusSession:88-100` 一致）。
  2. 写入改为 `runtime.copy(activeEngine = POMODORO, pomodoroPhase = FOCUS, pomodoroPhaseStartedAt = now, pomodoroPhaseEndAt = ..., pomodoroCycleIndex = runtime.pomodoroCycleIndex.coerceAtLeast(1), lastStatsTickAt = now, updatedAt = now)`，保留传感器与暂停字段。
  3. `newExternalRestState` 同理改为基于当前 `runtime` 的 `copy`。
- 风险/注意：`triggerEyeRelaxation` 已有 `reminderEngine.startBreak` 分支，改动时注意不要重复结算同一段时间（`lastStatsTickAt` 是去重依据）。

### [G06-07] 冷启动在主线程把整套 /proc 扫描 + `fork()` 跑两遍
- 严重度：P1
- 类别：D 生命周期 + E 韧性
- 位置：`app/src/main/java/com/projectlumen/app/core/security/AppIntegrityGuard.kt:31-68`（注释自称 "fast, synchronous"）、`app/src/main/java/com/projectlumen/app/core/security/DeviceSecurityGate.kt:42-50`（init 里再调一次）；原生实现 `app/src/main/cpp/lumen_security.cpp:155-179`、`:208-228`；调用方 `ProjectLumenApplication.kt:152`（第一次）+ `:157`（`deviceSecurityGate` by lazy 触发第二次）
- 现状：`enforce()` 一次调用会做 `has_hooking_artifacts()`：读 `/proc/self/maps`（上限 128 KB）、`/proc/self/cmdline`、`/proc/net/unix`（上限 128 KB）、遍历 `/proc/self/task/*/comm`（每线程一次 `ifstream`）、遍历 `/proc/self/fd/*` 做 `readlink`；随后 `isDebuggerAttachedNativeOrNull()` 再做一次 `has_tracer_pid()` + `is_traced_via_ptrace()`（**在 Android 应用进程里 `fork()` + `waitpid` 阻塞**）。`ProjectLumenApplication.onCreate` 第 152 行调一次，第 157 行 `deviceSecurityGate` 首次取值又在其 `init` 里调一次，两次都在主线程、都没有结果缓存（只缓存了签名 hash）。
- 触发场景：所有 release 构建的每次冷启动（`BuildConfig.APP_INTEGRITY_ENFORCEMENT_ENABLED` 为真时）。线程多、fd 多的机型（大量 ML Kit/相机线程）遍历成本随线程数线性增长。
- 影响：冷启动主线程被同步 I/O 阻塞两轮，首帧延迟；`fork()` 在多线程 ART 进程里属高风险操作（继承 fd 与信号处理，若进程内有 `SIGCHLD` 回收者会与 `waitpid(child)` 抢子进程），且同样跑两次。
- 修复方案：
  1. `AppIntegrityGuard` 增加 `@Volatile private var cachedVerdict: Boolean?`，`enforce()` 命中缓存直接返回；`DeviceSecurityGate.init` 复用同一结果，删掉重复调用（或者让 `ProjectLumenApplication:152` 不再单独调用，只保留 gate 里那一次）。
  2. `enforce()` 里只保留极廉价的检查（`Debug.isDebuggerConnected()`、包名+签名比对），把 `has_hooking_artifacts()` / ptrace 探测挪到 `startStartupScan` 的后台协程里，与 CRooot 扫描合并。
  3. 原生侧 `read_text_file` 改为按 `limit` 流式读取（当前是 `buffer << file.rdbuf()` 读全量再 `resize`）。
- 风险/注意：缓存 verdict 后"运行中才注入的 hook"不会被再次发现——这属于本来就已有的局限（`enforce` 只在启动跑一次），不构成回归。

### [G06-08] adb 检测判据是"`/proc/net/unix` 里出现字符串 adb"：要么恒为死代码，要么把所有开了 USB 调试的用户挡在启动门外
- 严重度：P1
- 类别：G 安全（误杀 / 判据无效）
- 位置：`app/src/main/cpp/lumen_security.cpp:260-272`（`isAdbOverNetworkDetected`）、`:174-178`（`has_hooking_artifacts` 同样扫全局 `/proc/net/unix`）、消费点 `app/src/main/java/com/projectlumen/app/core/security/AppIntegrityGuard.kt:61`
- 现状：
  ```cpp
  static constexpr std::array<const char *, 1> adb_needle = {"adb"};
  if (scan_text_file_for_artifacts("/proc/net/unix", adb_needle)) return JNI_TRUE;
  ```
  `AppIntegrityGuard.enforce` 把它计入 `failureReasons` → 抛 `SecurityException`。`/proc/net/unix` 是**系统级**文件（列出全机 unix socket 路径，如 `/dev/socket/adbd`），与"adb 是否监听 5555 端口"没有关系；同一函数里 `/proc/net/tcp`+`/proc/net/tcp6` 的 `0A`/`15B3` 判定才是真正的"adb over network"，而 Android 10+ 起 `untrusted_app` 读 `/proc/net/*` 普遍被 SELinux 拒绝，`read_text_file` 会拿到空串。
- 触发场景：二者必有其一——(a) SELinux 允许读：任何开着 USB 调试（adbd 在跑）的设备，甚至只是用过 scrcpy/无线调试的普通用户，冷启动即 `SecurityException`；(b) SELinux 拒绝读：三个判据全部恒为 false，这个检测是死代码，而 `/proc/net/unix` 那条 needle 扫描在 `has_hooking_artifacts()` 里也一并失效。
- 影响：(a) 情况下 release 应用对开发者/测试者/无线调试用户直接不可启动（且 `DeviceSecurityGate` 会把它变成永久 BLOCKED，见 [G06-02]）；(b) 情况下安全能力是虚假的，`nativeProtectionSummary` 仍显示 `adbOverNetwork=clean` 误导排查。
- 修复方案：
  1. 删掉对 `/proc/net/unix` 的 `"adb"` 匹配（两处：`isAdbOverNetworkDetected` 与 `has_hooking_artifacts` 的 needle 列表里 `/proc/net/unix` 这一路），"adb over network" 只保留 `/proc/net/tcp{,6}` 中 `state == 0A && port == 15B3` 的判定。
  2. 读不到文件（返回空串）时不能等价于 "clean"：让 `scan_proc_net_tcp_for_adb_port` 区分"文件不可读"与"确认无监听"，`isAdbOverNetworkDetected` 在不可读时返回 unknown（Kotlin 侧已有 `Boolean?` 通道），`AppIntegrityGuard` 只在明确为 true 时计入失败。
  3. `has_hooking_artifacts()` 的 needle 里 `"gmain"`、`"gadget"`、`"adb"` 这类过短/过泛的词应仅用于**本进程**范围的 `/proc/self/*`，不要用于系统级文件。
- 风险/注意：修完后"adb over network"实际几乎总是 unknown，需要接受这一检测在现代 Android 上基本不可用，不要用其他更激进的探测（如连本机 5555）替代。

### [G06-09] `SecureCredentialStore` 的加密存储初始化失败没有兜底：轻则静默丢会话与设备身份，重则在协程里未捕获抛出崩溃
- 严重度：P1
- 类别：E 韧性 + G 安全
- 位置：`app/src/main/java/com/projectlumen/app/core/security/SecureCredentialStore.kt:37-53`（`masterKey`/`secureMetadata` 裸 `by lazy`）、`:208-251`（`migrateLegacyCredentialsIfNeeded` 第 220 行访问 `secureMetadata` 未包裹）、`:61-123`（`save`/`load`/`clear`/游标方法均未包裹）、`:258-271`（`mmkvCryptKey` 回退临时密钥）
- 现状：
  ```kotlin
  private val secureMetadata by lazy { EncryptedSharedPreferences.create(...) }   // 抛出即向调用方逃逸
  fun load(): StoredAuthSession? { migrateLegacyCredentialsIfNeeded(); ... }      // 无 runCatching
  private fun mmkvCryptKey(): String = runCatching { ... }.getOrElse {
      Log.e(TAG, "mmkvCryptKey failed; using ephemeral key", error)
      UUID.randomUUID().toString() + UUID.randomUUID().toString()                 // ← 每次进程启动都是新密钥
  }
  ```
  `installProfile()` 与 `deviceInstallationId()` 有 `runCatching` 兜底，但 `save/load/clear/remoteSyncCursor/saveRemoteSyncCursor/remoteConfigCursor/saveRemoteConfigCursor/markOnboardingCompleted/markOssNoticeCompleted/resetOnboardingCompletion/markBuildUpdateNotesAcknowledged` **全部没有**。Kotlin `by lazy` 不缓存初始化异常，每次访问都会重新抛。
- 触发场景：Android Keystore 主密钥失效（系统升级后 keystore 迁移失败、恢复出厂/换机后 `allowBackup=false` 但残留 prefs 文件、设备锁屏凭据变更、`androidx.security` 已知的 `AEADBadTagException`/`InvalidProtocolBufferException`），或 `ProjectLumenMmkv.checkInitialized()` 因 MMKV 初始化失败而 `throw`（`ProjectLumenMmkv.kt:40-46`）。
- 影响：
  1. **崩溃路径（本组内即可确证）**：`PrivilegedDeviceControlCoordinator.refreshPolicy:168` 的 `app.secureCredentials.load()` 在 `scope.launch{}`（`CoroutineScope(SupervisorJob() + Dispatchers.IO)`，无 `CoroutineExceptionHandler`）里裸调，异常直达线程默认处理器 → 进程崩溃；`:206`、`:245`、`:311`、`:355`、`:410` 同样裸调。
  2. **静默数据丢失路径**：`mmkvCryptKey` 走到 ephemeral 分支后，`encryptedMmkv` 用一把每次启动都变的密钥打开同一个文件 → 旧数据全部解不出来，用户被静默登出、`deviceInstallationId` 每次启动都变（后端看成新设备），且日志只有一行 `Log.e`。
- 修复方案：
  1. 给 `secureMetadata` 加显式恢复：`runCatching { EncryptedSharedPreferences.create(...) }.getOrElse { appContext.deleteSharedPreferences(STORE_NAME); EncryptedSharedPreferences.create(...) }`（失效后删文件重建是官方推荐的唯一出路），仍失败则退化到一个明确的 in-memory 实现并把状态暴露给上层（新增 `val degraded: Boolean`）。
  2. `save/load/clear` 等公开方法统一 `runCatching` 兜底，返回 null / 静默失败而不是抛给随机调用方；`migrateLegacyCredentialsIfNeeded` 内部对 `secureMetadata` 的访问单独 `runCatching`，迁移失败也要把 `KEY_MMKV_MIGRATION_COMPLETE` 置位避免每次启动重试。
  3. `mmkvCryptKey` 的 ephemeral 分支要么明确清空 MMKV 文件（承认数据丢失并触发重新登录），要么直接抛出让上层进入"存储不可用"降级态——现在这种"悄悄换密钥继续跑"是最差选项。
- 风险/注意：删除并重建 `EncryptedSharedPreferences` 会丢 `KEY_MMKV_CRYPT_KEY`，等价于清空 MMKV 里的会话与设备指纹——必须同时清 MMKV，否则会留下"有文件但解不开"的僵尸状态。

### [G06-10] 特权显示写入无互斥：自动亮度与原生护眼平滑过渡互相打架，`_state` 是无锁 read-modify-write
- 严重度：P1
- 类别：B 并发 + A 架构（同一事实多个真相源）
- 位置：`app/src/main/java/com/projectlumen/app/core/shizuku/ShizukuCapabilityManager.kt:287-334`（`applySystemBrightness`）、`:232-285`（`applyNativeEyeProtection`）、`:386-419`（`applyNativeEyeProtectionTarget` 的 10 步 5 秒循环）；并发调用方 `core/light/LightMonitorService.kt:154`、`core/lifecycle/AppLifecycleCoordinator.kt:72`、`core/services/ShizukuResilienceWorker.kt:37`、`app/ProjectLumenSettingsFeatureEntry.kt:217`
- 现状：整个类没有任何 `Mutex`。`applyNativeEyeProtectionTarget` 会在 5 秒内分 10 帧连续 `settings put system screen_brightness`；同期 `LightMonitorService` 可以随环境光变化调用 `applySystemBrightness`。两者最后都做 `_state.value = queryState(...).copy(... _state.value.xxx ...)`——先读后写、非原子。
- 触发场景：用户同时开了"环境光自动亮度"与"Shizuku 原生护眼"（两个独立开关，可同时开）；或 `ShizukuResilienceWorker`（每 15 min）与 `LightMonitorService` 撞上；或用户在设置页拖动色温滑条（`smooth = true`）时环境光发生变化。
- 影响：① 屏幕亮度在两个写者之间来回跳变，肉眼可见闪烁，最终值取决于竞态；② `_state` 丢更新——`nativeBrightnessPercent` 可能记成另一路写入的值，进而污染 `readCurrentNativeEyeProtectionTarget` 的"起点"，下一次平滑过渡从错误亮度开始；③ `lastError` 被后写者清空，用户看不到真实失败原因。
- 修复方案：
  1. 给 `ShizukuCapabilityManager` 加 `private val commandMutex = Mutex()`，把 `applyNativeEyeProtection` / `applySystemBrightness` / `restrictAppNetwork` / `restoreAppNetwork` / `collectDeviceDiagnostics` / `listNetworkControllableApps` 的函数体整体包进 `commandMutex.withLock { }`（它们都已在 `withContext(Dispatchers.IO)` 里，加锁不阻塞主线程）。
  2. `_state` 的所有更新改用 `_state.update { it.copy(...) }`（`MutableStateFlow.update` 自带 CAS 循环），不要 `_state.value = queryState().copy(...)` 这种先读后写。
  3. 亮度只允许一个真相源：护眼激活期间让 `LightMonitorService` 的自动亮度让位（读 `state.nativeEyeProtectionApplied` 直接跳过），或反之——需要产品上明确优先级。
- 风险/注意：`applyNativeEyeProtectionTarget` 会持锁 5 秒（`SMOOTH_TRANSITION_MILLIS`），其他特权操作会排队；如不可接受，可只对"写显示设置"这一类加锁，网络管控用另一把锁。

### [G06-11] 视觉会话的 job 启停没有互斥，可能并存两条抓帧循环；`ProximityCameraSampler` 在取消时不被释放
- 严重度：P1
- 类别：B 并发 + C 资源
- 位置：`app/src/main/java/com/projectlumen/app/core/devicecontrol/PrivilegedDeviceControlCoordinator.kt:60-61`（`var visionJob` / `var heartbeatJob`）、`:65-93`、`:181-230`、`:267-302`
- 现状：`visionJob` / `heartbeatJob` 是普通 `var`，被 `start()`、`onBackendAvailable()`、`onForceStopRecovered()`、`onBackendUnavailable()`、`refreshPolicy()`（内部调 `onBackendUnavailable`）从多个 `Dispatchers.IO` 协程读写。`mutex` 只保护 `refreshPolicy` 的策略拉取，`maybeStartVisionSession` / `ensureSilentVisionSession` / `startCaptureLoop` **完全不在锁内**。`startCaptureLoop` 里 `val sampler = ProximityCameraSampler(app)` 是局部变量，循环退出/被 `cancel()` 时没有 `finally` 做任何释放。
- 触发场景：`onBackendAvailable()` 与 `onForceStopRecovered()` 几乎同时到达（后端恢复 + Android 15 force-stop 恢复），两条协程各自跑完 `ensureSilentVisionSession()` → 各自 `startCaptureLoop`；`visionJob = scope.launch{}` 的赋值竞态会让先启动的那条 job 失去引用，`visionJob?.cancel()` 再也取消不到它。
- 影响：两条循环同时抢摄像头（`exclusiveAccess` 语义直接失效）→ 抓帧大量失败、耗电与流量翻倍；失控的那条 job 只能随进程结束；`sampler`（内部持 `FaceDistanceAnalyzer`/ML Kit detector）在 job 取消后不被关闭，属于 ML Kit 检测器泄漏。
- 修复方案：
  1. 让 `maybeStartVisionSession()` / `ensureSilentVisionSession()` / `onBackendUnavailable()` 全部在同一把 `mutex` 内执行（注意 `refreshPolicy` 已持锁时调用 `onBackendUnavailable` 会自锁——把 `onBackendUnavailable` 拆成 `suspend fun stopSessionLocked()` 供锁内调用，公开方法负责加锁）。
  2. `visionJob` / `heartbeatJob` 改为 `AtomicReference<Job?>`，用 `getAndSet(newJob)?.cancel()` 保证旧 job 一定被取消。
  3. `startCaptureLoop` 内把 sampler 用 `try { ... } finally { sampler.close() }` 包住（`ProximityCameraSampler` 目前没有 close，需要为它补一个释放 `plainAnalyzer`/`topologyAnalyzer` 的方法——这是 G04 组的文件，需跨组协调）。
- 风险/注意：加锁后 `onBackendUnavailable()` 变成 `suspend`，调用方（`observeBackendAvailability`）需要改成在协程里调用。

### [G06-12] HMAC 签名密钥是 so 里的明文常量，且缺省值是仓库里公开的字符串；同一密钥缺失时两条链路的失败语义相反
- 严重度：P1
- 类别：G 安全
- 位置：`app/src/main/cpp/lumen_security.cpp:19-21`、`:232-238`；`app/src/main/java/com/projectlumen/app/core/security/ProjectLumenRequestSigner.kt:56-69`、`:88`；`app/src/main/java/com/projectlumen/app/core/security/AppIntegrityGuard.kt:49-54`
- 现状：
  ```cpp
  #ifndef LUMEN_REQUEST_SIGNING_SECRET
  #define LUMEN_REQUEST_SIGNING_SECRET "project-lumen-local-request-signing-key"
  #endif
  ...
  return env->NewStringUTF(LUMEN_REQUEST_SIGNING_SECRET);   // 直接把常量返回给 Java 层
  ```
  Kotlin 侧同一个字符串还硬编码了一份 `FALLBACK_REQUEST_SIGNING_SECRET`（`ProjectLumenRequestSigner.kt:88`）。`app/build.gradle.kts:113-118` 在环境变量与 gradle property 都缺失时会落到默认值。
- 触发场景：① 任何拿到 APK 的人 `unzip` 后对 `liblumen_security.so` 跑一次 `strings` 即可取到密钥（编译期宏就是一个普通 `.rodata` 字符串，无任何拆分/异或/白盒），随后可离线伪造任意 `X-Lumen-Signature`；② CI 未配置 `PROJECT_LUMEN_REQUEST_SIGNING_SECRET` 时，发布包用的就是本仓库明文可见的默认串。
- 影响：请求签名只能视为"防重放 + 防随手改包"的门槛，**不能当作调用方身份认证**；若后端把签名通过当成"来自正版客户端"的凭据（例如放宽限流、信任 `securityEvidence`），该信任是无效的。默认密钥场景下门槛为零。
- 修复方案：
  1. 后端侧：明确签名只用于完整性/防重放，任何授权决策必须依赖 `accessToken`（服务端签发、可吊销）。
  2. 构建侧：`app/build.gradle.kts` 在 release 构建且 `PROJECT_LUMEN_REQUEST_SIGNING_SECRET` 为空时**直接 fail the build**（现有 `*_CERTIFICATE_PINNING_ENABLED` 已有类似硬失败先例），删掉 cpp 与 Kotlin 里的明文默认值。
  3. 顺带统一失败语义：`AppIntegrityGuard.enforce:49-54` 在 so 不可用时 soft-fail 放行，而 `ProjectLumenRequestSigner.signingKey():64` 在 release 下 `error(...)` 抛异常——即"删掉 so"能绕过完整性门禁但会让全部网络请求抛 `IllegalStateException`（且该异常会在 OkHttp 拦截器里抛出）。两处应统一为"so 不可用 = 进入明确的降级态并告知用户"，而不是一个放行一个炸。
- 风险/注意：把 so 的明文密钥换成拆分/派生只是提高门槛，报告不应把它当作保密手段；真正的修复是后端不把签名当身份。

### [G06-13] release 证书指纹比较不做归一化，配错格式（冒号/小写）或启用 Play App Signing 会让全量用户冷启动被拦
- 严重度：P2（需确认发布渠道）
- 类别：G 安全（误杀）
- 位置：`app/src/main/cpp/lumen_security.cpp:248-253`；`app/src/main/java/com/projectlumen/app/core/security/AppIntegrityGuard.kt:122-147`（产出**大写**无分隔 hex）、`app/build.gradle.kts:154`（`APP_INTEGRITY_ENFORCEMENT_ENABLED = 指纹非空`）
- 现状：
  ```cpp
  if (!expected_cert.empty() && actual_cert != expected_cert) return JNI_FALSE;   // 逐字节比较
  ```
  `signingCertificateSha256` 用 `HEX_CHARS = "0123456789ABCDEF"` 生成大写、无冒号的 64 字符串；`LUMEN_RELEASE_CERT_SHA256` 直接取 CI secret 原文。`keytool -list` 与多数文档输出的是 `AB:CD:EF:...` 形式，`apksigner verify --print-certs` 输出的是小写无冒号。
- 触发场景：CI secret 里填了带冒号或小写的指纹 → `actual != expected` → `enforce()` 抛 `SecurityException` → 经 `DeviceSecurityGate` 变成永久 `BLOCKED`（[G06-02]）。若将来上架 Google Play 并启用 App Signing，`apkContentsSigners` 返回的是 Play 重签后的证书，与上传证书指纹必然不同，同样全量误杀。
- 影响：一个 secret 格式错误即导致所有正常用户"应用装上了但什么功能都不能用"，且日志只有 `Integrity check failed: native`，排查方向容易被误导到 hook 检测上。
- 修复方案：
  1. 原生侧比较前归一化两侧：去掉 `:`、空格，统一 `lower_ascii()`（`lower_ascii` 已存在，直接复用），即 `if (!expected.empty() && normalize(actual) != normalize(expected)) return JNI_FALSE;`。
  2. `app/build.gradle.kts` 在注入 `-DLUMEN_RELEASE_CERT_SHA256` 前做同样的归一化，并校验长度为 64、全为 hex，不合法直接 fail the build。
  3. 若计划走 Play App Signing，需支持多个可信指纹（逗号分隔，参考 `OPEN_API_TRUSTED_SIGNATURE_SHA256` 的处理方式）。
- 风险/注意：归一化后现有已发布版本的判定结果不变（当前 CI secret 若格式正确），属向后兼容改动。

### [G06-14] `is_traced_via_ptrace()` 的 TRACEME 用法写反，永远返回 false，但每次调用都 `fork()`
- 严重度：P2
- 类别：G 安全（判据无效）
- 位置：`app/src/main/cpp/lumen_security.cpp:208-228`，消费点 `:274-285`
- 现状：
  ```cpp
  if (child == 0) {
      if (ptrace(PTRACE_TRACEME, 0, 0, 0) == -1) _exit(1);   // 子进程把"自己"交给父进程跟踪
      raise(SIGSTOP); _exit(0);
  }
  if (WIFEXITED(status)) return WEXITSTATUS(status) == 1;    // 只有 TRACEME 失败才报"被跟踪"
  ```
  子进程刚 `fork()` 出来必然处于未被跟踪状态，`PTRACE_TRACEME` 一定成功——即使父进程正在被 Frida/gdb 跟踪也一样。经典写法是子进程对 `getppid()` 做 `PTRACE_ATTACH`，失败（`EPERM`）才说明父进程已被别人跟踪。
- 触发场景：每次 `isDebuggerAttachedNative()` 调用（冷启动两次，见 [G06-07]；`nativeProtectionSummary()` 每次打开诊断页再一次）。
- 影响：这条反调试判据恒为 false（真正起作用的只有前面的 `has_tracer_pid()`），但仍然为它付出一次 `fork()` + 两次 `waitpid` 的代价；`nativeProtectionSummary` 报告的 `nativeDebugger=clean` 有一半是假的。注释写着 "fail-open for safety" 而代码并没有对应逻辑。
- 修复方案：改成 `child` 里 `ptrace(PTRACE_ATTACH, getppid(), 0, 0)`，成功则 `PTRACE_DETACH` 后 `_exit(0)`、失败 `_exit(1)`，父进程按 `WEXITSTATUS == 1` 判定被跟踪（注意父进程不能同时 `waitpid` 干扰，且 attach 到父进程会短暂 STOP 父进程——若不接受这一副作用，直接**删掉这个探测**，只保留 `has_tracer_pid()`）。
- 风险/注意：`PTRACE_ATTACH` 到父进程会让父进程被短暂暂停，在主线程调用时有卡顿风险；建议直接删除该探测，收益不足以抵消 `fork()` 成本。

### [G06-15] MMKV 加密密钥实际只有前 16 字符生效，有效熵约 52 bit（另一半 UUID 完全被丢弃）
- 严重度：P2（需确认 MMKV 截断行为）
- 类别：G 安全
- 位置：`app/src/main/java/com/projectlumen/app/core/security/SecureCredentialStore.kt:51-53`、`:258-271`；`core/mmkv/ProjectLumenMmkv.kt:35-38`
- 现状：`mmkvCryptKey()` 生成 `UUID.randomUUID().toString() + UUID.randomUUID().toString()`（72 字符）交给 `MMKV.mmkvWithID(id, SINGLE_PROCESS_MODE, cryptKey)`。MMKV 的 `AESCrypt` 只取前 16 字节（AES-128），其余静默丢弃。UUID 字符串前 16 字符是 `xxxxxxxx-xxxx-4x`：其中 2 个是固定 `-`、1 个是固定版本位 `4`，只剩 13 个 hex 字符 ≈ 52 bit 随机性。
- 触发场景：任何拿到应用私有目录（root / 备份提取 / 取证）的人对 MMKV 文件做离线爆破时，实际面对的是 52 bit 而不是预期的 128 bit。
- 影响：至于攻击者还需要突破 `EncryptedSharedPreferences`（Keystore 保护）才能直接读到密钥，所以这层加密的意义本就是"文件被单独拷走时的额外防线"——而这条防线比设计意图弱了约 76 bit。
- 修复方案：`mmkvCryptKey()` 改为 `ByteArray(16).also { SecureRandom().nextBytes(it) }` 后用固定 16 字符的可打印字母表（如 Base64 字母表逐字节取模）编码成 **16 个 ASCII 字符**（≈96 bit），避免多字节字符导致 UTF-8 展开后仍被截断。注意这是一次性迁移：换密钥等于旧文件读不出来，需要与 [G06-09] 的"存储不可用降级"一起设计（保留旧 key、只对新安装用新算法，或接受一次登出）。
- 风险/注意：不要为了"更长更安全"而继续加长字符串——问题在截断，不在长度不够。另外 `encryptedMmkvWithId` 用的是 `SINGLE_PROCESS_MODE`，而 `ProjectLumenMmkv.mmkvWithId` 用 `MULTI_PROCESS_MODE`；本应用存在辅助进程（`AppIntegrityGuard.kt:36-42` 的注释说明辅助进程会跑同一个 `Application`），若辅助进程也触达 `SecureCredentialStore`，SINGLE_PROCESS 下可能读到陈旧数据甚至损坏文件 —— **需确认**辅助进程是否访问该 store，若会，应统一成 `MULTI_PROCESS_MODE`。

### [G06-16] `sourceApp` 归因可被调用方用 Intent extra 伪造，且导出 Activity 可被反复驱动触发强制遥测上传
- 严重度：P2
- 类别：G 安全
- 位置：`app/src/main/java/com/projectlumen/app/openapi/LumenOpenContracts.kt:48-50`、`:73-83`；消费点 `MainActivity.kt:152-169`（`callingPackage` 作为 `platformCallerPackage`）、`LumenOpenRuntimeController.kt:141-145`、`:212-220`
- 现状：
  ```kotlin
  val rawCaller = platformCallerPackage
      ?: intent.getStringExtra(LumenOpenContracts.EXTRA_CALLER_PACKAGE)
      ?: intent.getStringExtra(LumenOpenContracts.EXTRA_SOURCE_APP)
  ```
  `Activity.callingPackage` **只在调用方用 `startActivityForResult` 启动时才非空**，普通 `startActivity` 一律为 null → 归因完全落到调用方自己填的 extra 上，只做了字符集与长度校验（`SOURCE_APP_PATTERN`）。
- 触发场景：某应用（持有 dangerous 的 `ACCESS_LUMEN_CORE`）以 `startActivity` 发送 `ACTION_VIEW_DASHBOARD`，并把 `EXTRA_CALLER_PACKAGE` 填成竞品或系统包名；每次启动都会走 `recordOpenLaunch` → `uploadOpenApiTelemetry(force = true)` 一次网络上传。
- 影响：后端遥测里的 `sourceApp` 归因不可信（可栽赃给任意包名）；同时给出一个"外部可无限触发强制上传"的放大面（配合 [G06-05]）。签名级的 `ACTION_TRIGGER_REST` 路径还会把这个伪造包名交给 `MainActivity.scheduleReturnToCaller:173-186` 去 `getLaunchIntentForPackage` 启动——虽然受 signature 权限保护，但语义上仍是"用不可信输入决定启动哪个应用"。
- 修复方案：
  1. `parseLaunchRequest` 删掉 `EXTRA_CALLER_PACKAGE` / `EXTRA_SOURCE_APP` 两级回退；归因只用平台可信来源：`Activity.callingPackage`（非空时）或 `Activity.getReferrer()?.host`（`android-app://` 形式，由系统填充，调用方无法伪造）。
  2. 拿不到可信归因时用 `SOURCE_APP_EXTERNAL` 常量，不要用调用方给的字符串。
  3. `recordOpenLaunch` 走非强制遥测（见 [G06-05] 修复 1），去掉外部可控的上传放大。
- 风险/注意：`getReferrer()` 需要在 Activity 上下文里取，`parseLaunchRequest` 目前只收 `platformCallerPackage: String?`，签名不变即可（由 `MainActivity` 传 `callingPackage ?: referrer?.host`）；改后需同步更新对外开放 API 文档中关于 `EXTRA_CALLER_PACKAGE` 的说明。

### [G06-17] 每次设备安全扫描都反射生成最多 120 KB 诊断报告并长期驻留内存 / 打进 logcat
- 严重度：P2
- 类别：C 资源 + G 安全（日志）
- 位置：`app/src/main/java/com/projectlumen/app/core/security/CroootReportFormatter.kt:22-32`、`:45-59`、`:91-116`；生产者 `DeviceSecurityScanner.kt:160`（`summary = CroootReportFormatter.format(result)`）；消费者 `AppIntegrityGuard.kt:103`、`:117`（`Log.w(TAG, "... ${assessment.summary}")`）
- 现状：`distill()` 对**每次**扫描都无条件跑一遍全量反射遍历（`MAX_REPORT_LENGTH = 120_000`），结果字符串塞进 `SecurityAssessment.summary`，而 `DeviceSecurityGate._assessment` 是 `MutableStateFlow` → 连同 `rawResult` 一起在进程生命周期内常驻。`AppIntegrityGuard.isIntegrityConfirmed` 会把整个 summary 打到 logcat。
- 触发场景：每次冷启动都会跑一次 `fullScan()`；用户在诊断页手动扫描会再跑。
- 影响：① 约 240 KB（Java `String` 双字节）+ `rawResult` 常驻堆内存，只为一个开发者诊断页；② 全量设备安全画像（root 判据命中路径、SELinux 状态、TEE 细节等）被写进 logcat，会随任何 bugreport 一起流出；③ release 构建经 R8 混淆后 `javaClass.simpleName` 与 `field.name` 全是混淆名，这份报告在真正需要它的发布版里基本不可读。
- 修复方案：
  1. `SecurityAssessment.summary` 改为惰性：把 `rawResult` 留着，`summary` 变成 `val summary: String by lazy { CroootReportFormatter.format(rawResult) }` 或干脆改成 `fun formatReport(): String`，只在诊断页真正展示时才生成。
  2. `AppIntegrityGuard:103/117` 不要打整份 summary，改成只打结构化字段（`rooted/suspicious/selinuxEnforcing/teeAttestationOk`）。
  3. `appendValue` 的 `Map`/`Iterable` 分支没有 `depth >= MAX_DEPTH` 检查、也不走 `visited` 环检测（只有 `appendObject` 有）——自引用集合会一路递归到 `MAX_REPORT_LENGTH` 才停，存在 `StackOverflowError` 隐患；把深度与环检测提到 `appendValue` 开头统一做。
- 风险/注意：改成惰性后 `SecurityAssessment` 不再是纯 data 值对象（持有 `rawResult` 引用），若有地方对它做 `equals`/序列化需要复核。

### [G06-18] `loadCachedPolicy` 写入了 `endpointPrefix` 却从不读回，离线冷启动会静默丢失后端下发的路径前缀
- 严重度：P2
- 类别：F 持久化一致性
- 位置：`app/src/main/java/com/projectlumen/app/core/devicecontrol/PrivilegedDeviceControlCoordinator.kt:430-461`（读，无 `endpointPrefix`）对比 `:463-499`（写，`put("endpointPrefix", ...)` 两处）
- 现状：`persistPolicy` 把 `silentVision.endpointPrefix` 与 `lifecycleLock.endpointPrefix` 落进 `payloadJson`，`loadCachedPolicy` 构造 `SilentVisionPolicy(...)` / `LifecycleLockPolicy(...)` 时**没有这两个字段**，于是回退到 data class 默认值 `"/v1/device-control"`（`core/api/ProjectLumenDeviceControlModels.kt:13/26`）。
- 触发场景：后端曾下发过非默认 `endpointPrefix`（`core/api/ProjectLumenDeviceControlJson.kt` 会解析它），随后用户在无网状态下冷启动 → `refreshPolicy` 拉取失败 → 用缓存策略 → 前缀悄悄变回默认值。
- 影响：离线/弱网时特权设备控制相关请求打到与在线时不同的路径前缀，表现为"有网时正常、断网重连后接口 404"，且没有任何日志线索。属于典型"写了不读"的双真相源不一致。
- 修复方案：`loadCachedPolicy` 两处分别补 `endpointPrefix = silent?.optString("endpointPrefix", "/v1/device-control") ?: "/v1/device-control"` 与 `life?.optString(...)`，与 `ProjectLumenDeviceControlJson.kt:19/30` 的解析保持一致。
- 风险/注意：若后端下发的前缀曾经是错误值，补上读回会让错误值"复活"；建议同时对前缀做白名单校验（必须以 `/` 开头、不含 `..` 与协议头），避免后端可控字符串直接拼进 URL。

### [G06-19] Shizuku shell 调用没有超时与错误分类，绑定等待用不可取消的 `Object.wait(5s)` 且可能阻塞主线程回调
- 严重度：P2
- 类别：E 韧性 + B 并发
- 位置：`app/src/main/java/com/projectlumen/app/core/shizuku/ShizukuCapabilityManager.kt:510-543`（`executeShellCommand`）、`:545-573`（`shellServiceBinder`）、`:36-50`（`onServiceConnected` 在主线程取同一把锁）、`:287-334`（错误文案）
- 现状：`binder.transact(..., flags = 0)` 是同步双向调用，本地无超时（只有远端 `ShizukuShellUserService.COMMAND_TIMEOUT_MILLIS = 10_000` 兜底，且它 `join(1000)` 后就放弃读流）；`shellServiceBinder()` 在 `synchronized(shellServiceLock)` 里 `shellServiceLock.wait(remaining)` 最长 5 秒，`wait` 不响应协程取消。`ShellCommandResult.success` 只看 `exitCode == 0`，`applySystemBrightness` 的错误文案只能给出 "System brightness command failed."，无法区分"Shizuku 权限被撤销"、"命令不存在（OEM 裁剪）"、"参数被拒"。
- 触发场景：Shizuku 服务卡住（server 端 `pm`/`cmd` 挂住）→ 远端 10 秒后才 destroy 进程；期间协程无法取消，`WorkManager` 的 `ShizukuResilienceWorker` 会一直占着 worker 线程。另一路：某个 IO 线程正在锁内执行阻塞的 `Shizuku.bindUserService`（跨进程调用）时，`onServiceConnected` 在**主线程**进入 `synchronized(shellServiceLock)` 会被阻塞。
- 影响：偶发的"点了没反应且等很久"；错误提示文案与真实原因不符（brief 关心的"用户看到的提示是错的"确实存在——最常见的 `cmd appops set <pkg> android:internet` 在原生 AOSP 上根本没有 INTERNET 这个 appop，必然失败，用户看到的却是笼统的 "Delegated network guard is not supported on this device."，这一条文案倒是准确的，但亮度/夜灯路径的文案不准确）。
- 修复方案：
  1. `executeShellCommand` 外层套 `withTimeoutOrNull(SHELL_COMMAND_TIMEOUT_MILLIS)`（需把它改成 `suspend`），超时返回 `exitCode = 124`；`shellServiceBinder()` 的等待改成挂起友好的实现（`CompletableDeferred<IBinder>` 由 `onServiceConnected` 完成，调用方 `withTimeoutOrNull(5_000) { deferred.await() }`），彻底去掉 `Object.wait` 与主线程锁竞争。
  2. 增加错误分类：解析 `error` 输出中的 `Permission denied` / `SecurityException` → "Shizuku 授权已失效，请重新授权"；`Unknown command` / `Bad argument` / `unknown appop` → "此设备不支持该命令"；其余保留原文（`ShellCommandResult.error` 已带 stderr，只是没被用来分类）。
  3. `latestRestrictBackgroundDenylist()` 的 `UID_TOKEN_REGEX = \b\d{3,}\b` 会把 `cmd netpolicy` 输出里任何 3 位以上数字（版本号、时间戳）当成 UID，导致列表里的应用被误标成"已限制"；应改为按行解析 `uid=` / `UID` 字段。
- 风险/注意：把 `executeShellCommand` 改成 `suspend` 会波及 `readIntSetting` / `setNightDisplay` / `setExtraDim` / `setSystemBrightness` / `clearNativeDisplayAdjustments` / `latestInstalledApps` 等 8 个私有函数的签名，它们的调用点都已在 `withContext(Dispatchers.IO)` 内，改动是机械的但要一次改全（kapt 只报第一个错）。

### [G06-20] 未注册 Shizuku binder 存活监听，也从不注销权限回调，UI 会长时间显示陈旧的"可用"状态
- 严重度：P2
- 类别：A 架构（缓存失效路径缺失）
- 位置：`app/src/main/java/com/projectlumen/app/core/shizuku/ShizukuCapabilityManager.kt:51-60`
- 现状：只注册了 `Shizuku.addRequestPermissionResultListener`，没有 `addBinderReceivedListener` / `addBinderDeadListener`，也没有任何 `removeRequestPermissionResultListener` / `unbindUserService`。`state` 这个 `StateFlow` 只在有人主动调用 `refreshState()` / `isReady()` / 各 `apply*` 时才更新。
- 触发场景：用户在 Shizuku 应用里撤销授权，或 Shizuku 服务退出（重启手机后未激活）。此时 Project Lumen 界面仍显示上一次查询到的 `binderAvailable = true, permissionGranted = true`，直到用户下拉刷新或 `ShizukuResilienceWorker`（最长 15 分钟后）触发一次查询。
- 影响：设置页显示"Shizuku 已就绪"但所有特权操作都失败；用户拿到的是自相矛盾的提示。反向也成立：Shizuku 刚被激活时界面不会自动变成可用，用户以为没生效。
- 修复方案：在 `init` 里追加 `Shizuku.addBinderReceivedListenerSticky { refreshState() }` 与 `Shizuku.addBinderDeadListener { shellServiceBinder = null; refreshState() }`（均用 `runCatching` 包住，与现有风格一致）；`binderDead` 时还要把 `shellServiceBinder` 置空，避免下一次 `executeShellCommand` 拿到死 binder 走异常路径。
- 风险/注意：`ShizukuCapabilityManager` 是 `ProjectLumenApplication` 级单例、与进程同生命周期，不注销监听不构成泄漏；但若将来改成按需创建，必须补 `remove*Listener`。

## 已核查但无问题的点

- **Shizuku shell 命令注入：本组未发现可利用注入点。** 所有拼接进 shell 的可变量只有两类：（a）包名——`applyAppNetworkPolicy:751` 在执行前用 `ANDROID_PACKAGE_NAME_REGEX = [A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+` 做**全匹配**（`Regex.matches`），空格、`;`、`|`、`$`、反引号一律无法通过，`setDelegatedNetworkGuard:811-812` 的 `cmd appops set $packageName ...` 因此安全；（b）UID 与亮度/色温/百分比——都是 `Int` 且经过 `coerceIn`。`readIntSetting` 的 namespace/key、`USER_APP_LIST_COMMAND` / `SYSTEM_APP_LIST_COMMAND` 全是编译期常量。唯一残留的小瑕疵是 `sanitizePackageToken` 的 `take(160)` 在正则校验**之前**执行，理论上超长包名被截断后仍可能匹配正则而指向另一个包（Android 包名上限 255）——不构成注入，仅可能作用在错误的包上，可在修 [G06-19] 时顺手改成"超长直接拒绝"。
- **`ShizukuShellUserService` 不是对外攻击面。** 它是通过 `Shizuku.bindUserService` 由本应用私有绑定的 `Binder`，句柄不暴露给任何第三方，`onTransact` 里 `data.enforceInterface(DESCRIPTOR)` 也在（虽无 UID 校验，但拿不到 binder 就无法调用）。超时（10 s）+ `process.destroy()` + 独立 stdout/stderr 读取线程的实现是正确的，避免了管道填满导致的死锁。
- **`ProjectLumenRequestSigner` 的规范串构造没有分隔符歧义。** `sortedMapOf` 的 6 个 key 固定，值分别是 hex 摘要、大写 method、hex nonce、`url.encodedPath`、`url.encodedQuery`、秒级 timestamp，均不含 `\n` 与 `=`；timestamp + nonce 齐备（防重放由服务端负责）；客户端只做签名不做比较，因此不需要恒时比较（`MessageDigest.isEqual` 的要求在服务端）。唯一可改进项：签名未覆盖 host/port，只覆盖 path+query（若后端有多个域名共用同一密钥，同一签名可跨域名重放）——影响很小，未单独列为缺陷。
- **`SecureShareIntents` 的 URI 全部来自 `FileProvider`。** 4 个调用点（`ExportService.kt:26/46/61`、`DataBackupService.kt:43`、`UpdateInstaller.kt:78`）都是 `FileProvider.getUriForFile(context, "${packageName}.fileprovider", file)`，manifest 里 provider 是 `exported="false" grantUriPermissions="true"`，不存在 `file://` 导致 `FileUriExposedException` 的路径；`ClipData` + `FLAG_GRANT_READ_URI_PERMISSION` 同时加在 inner intent 与 chooser 上（Android 14+ 起 chooser 必须自己带 flag，这里是对的），授权随目标 Activity 结束自动回收，无 `grantUriPermission` 手工授予残留。
- **`LumenOpenService.requireCaller` 的身份校验骨架是正确的**：先 `Binder.getCallingUid()` 短路同 UID，再 `checkCallingPermission`（在 binder 事务内，能拿到真实 pid/uid，不存在"用自己权限自查"的错误），再 `getPackagesForUid` 且空列表直接拒绝，`packageSignatures` 异常时返回空列表 → 走 fail-closed 拒绝。问题只在权限**分级**（[G06-04]）与默认空白名单，不在校验实现。
- **`AppIntegrityGuard` 的构建门禁开关与进程过滤是对的**：`BuildConfig.DEBUG` 与 `APP_INTEGRITY_ENFORCEMENT_ENABLED`（= release 指纹是否配置）双重短路，debug/CI 构建不会被拦；`Application.getProcessName() != packageName` 时跳过，避免 CRooot 的 `:zygisk_fd_detector` 探针进程自我误报——这两处**不要在修复阶段动**。`ProjectLumenApplication.kt:152` 用 `runCatching` 包住 `enforce()`，`DeviceSecurityGate.init` 也 `runCatching` 转成布尔，因此完整性失败不会直接 process-kill（真正的后果在 [G06-02] 的 BLOCKED 语义上）。
- **原生层没有内存安全问题**：全程 `std::string` / `std::ifstream`，无 `strcpy`/`sprintf`/`strcat`；`read_symlink_target` 用 `PATH_MAX` 栈缓冲 + `sizeof(target) - 1` + 显式 `'\0'`，返回的是**值语义** `std::string` 而非栈指针；`jstring_to_string` 对 `GetStringUTFChars` 的 null 返回与 `ReleaseStringUTFChars` 配对正确；`opendir`/`closedir` 成对；`read_text_file` 有 `limit` 截断。JNI 函数名与 Kotlin 侧 `external fun` 一一对应（`requestSigningSecret` / `isNativeEnvironmentAllowed` / `isAdbOverNetworkDetected` / `isDebuggerAttachedNative`），签名第二参数是 `jobject`——与 Kotlin `object` 的**实例**方法（非 `@JvmStatic`）匹配，不存在 `UnsatisfiedLinkError`；`NativeSecurityBridge` 的 `System.loadLibrary` 用 `runCatching` 包住并以 `isAvailable` 对外表达，四个 `*OrNull` 包装都先判 `isAvailable` 再 `runCatching`，缺 ABI 时不会启动即崩。
- **`SecureCredentialStore.clear()` 的 key 列表是完备的**：会话七项 + 两个远端游标全清；`device_installation_id` / `first_seen_at` / onboarding 与构建确认标记**故意保留**（设备级身份而非用户级身份，注销后重登不应重新走引导），不是漏删。`deviceInstallationId` 的指纹在 `Build.*` 变化（系统升级）后仍复用已存值（`isDeviceFingerprint` + `DEVICE_FINGERPRINT_VERSION` 双重判定），不会因 OTA 变成新设备。
- **`DeviceSecurityGate.backendEvidence()` 上报的字段是有界的**：只有 `status/verified/completed/rooted/suspicious/hardwareIntegrityOk/selinuxEnforcing/teeAttestationOk/observedAt/scannerVersion`，**没有**把 `summary`（含设备指纹级细节）或 `rawResult` 传给后端——注释里"raw CRooot object never leaves the process"与实现一致，这一点不要在修复时"顺手补全字段"。
- **`DeviceSecurityScanner` 的并发与超时骨架正确**：`Mutex` 保证同时只有一次扫描，`withTimeout` + 单独 catch `TimeoutCancellationException`、重新抛出 `CancellationException`、最后兜 `Throwable` 的三段式是标准写法（注意 catch 顺序：`TimeoutCancellationException` 在 `CancellationException` 之前，正确）。
- **本组日志无敏感信息泄露**：20 处 `Log.*` 全部只打状态与异常对象，没有 token、access/refresh token、MMKV 密钥、签名密钥、UID↔包名映射表。唯一需要收敛的是 `AppIntegrityGuard:103/117` 打印整份 CRooot summary（已列为 [G06-17]）。
- **manifest 的组件暴露面与代码一致**：三个 `openapi/*Activity` 与 `LumenOpenService` 均 `exported="true"` 但都带 `android:permission`；其余 12 个 service/receiver 全是 `exported="false"`；`application` 上有 `android:intentMatchingFlags="enforceIntentFilter"`（Android 16 起对未声明 intent-filter 的隐式投递做拦截），`usesCleartextTraffic="false"`、`allowBackup="false"`、`installLocation="internalOnly"` 都是正确的收紧配置。`ShizukuProvider` 的 `permission="android.permission.INTERACT_ACROSS_USERS_FULL"` 是 Shizuku 官方要求的写法（普通应用拿不到该权限，等价于只允许系统/Shizuku 访问）。

