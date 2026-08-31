# G05 网络层 / API 契约 / 遥测 / 应用内更新 / Clash 伙伴集成 审查报告

- 审查文件数：27，总行数：4239
  - `core/api/` 19 个（2486 行）、`core/network/ClashPartnerCompat.kt`（420 行）、`core/telemetry/EyeCareTelemetryReporter.kt`（574 行）、`core/update/` 6 个（759 行）
- 结论摘要：这一组的**架构骨架是健康的**——能力门禁（`BackendCapabilityGate`）确实在签名与网络之前执行、连接状态只有一个真相源（`StateFlow` + MMKV 镜像）、退避表有界、JSON 解析基本全部走 `opt*` + 默认值、全仓库没有任何 `HostnameVerifier{true}` / trust-all `TrustManager` / `Log.*`。**真正的风险集中在两处**：一是**应用内更新的信任链**——下载的 APK 只校验服务端自己给的 SHA-256，既不校验签名证书一致性、也不限制下载域名，服务端 JSON 说什么就装什么（P0）；二是**E 类韧性的系统性缺失**——所有 OkHttp 客户端都没有 `callTimeout`，而 `execute()` 是阻塞调用不随协程取消而中断，慢流/黑洞连接会长期占用 `Dispatchers.IO` 线程；同时证书固定的"是否启用"只存在于构建期，pins 漏配会**静默降级为无固定**且运行期无法察觉。另有两条主线程阻塞（遥测快照的磁盘/相机 IO、Clash 伙伴状态的跨进程 binder）会直接影响冷启动手感。

## 缺陷清单

### [G05-01] 应用内更新不校验 APK 签名与下载域名，信任链完全等于"服务端 JSON 说什么就装什么"
- 严重度：P0
- 类别：G 安全
- 位置：`app/src/main/java/com/projectlumen/app/core/update/UpdateInstaller.kt:21-64`（`downloadApk`）、`:77-80`（`installApk`）、`:110-124`（`openHttpConnection`）；数据来源 `app/src/main/java/com/projectlumen/app/core/update/UpdateChecker.kt:128-146`（`fullApkUrl` / `fullApkSha256` 均取自后端响应）
- 现状：
  ```kotlin
  val expectedSha256 = asset.sha256?.trim()?.lowercase()          // 来自后端 JSON
  val connection = openHttpConnection(asset.downloadUrl)          // 主机名不受限，任意 https 域
  ...
  if (!actualSha256.equals(expectedSha256, ignoreCase = true)) { targetFile.delete(); throw ... }
  targetFile                                                     // 校验通过即交给安装器
  ```
  `openHttpConnection` 只检查 `protocol == "https"`；SHA-256 与 URL 来自**同一个**后端响应，因此哈希校验只能证明"下载完整"，不能证明"这个 APK 是我们发布的"。`installApk` 直接把文件交给 `ACTION_VIEW`，全程没有 `PackageManager.getPackageArchiveInfo(..., GET_SIGNING_CERTIFICATES)` 之类的签名一致性校验，也没有校验 APK 内的 `packageName`。
- 触发场景：后端被攻破、后端配置被误改、或（在 pins 漏配的构建里，见 [G05-03]）中间人改写 `/v1/releases/check` 响应，把 `fullApkUrl` 指向攻击者主机并给出对应的 SHA-256。GitHub 兜底路径同理：`UpdateChecker.kt:225-275` 会把 release 正文或任何名字含 `checksum`/`sha256` 的附件里的 64 位 hex 当权威哈希。
- 影响：用户在"发现新版本"弹窗里点更新 → 应用下载任意 APK 并拉起系统安装器。若该 APK 用我们的包名，系统会因签名不符拒绝；但攻击者只要换一个包名，就得到一次由本应用背书、用户高度信任的任意应用安装引导（可携带任意权限申请）。
- 修复方案：在 `UpdateInstaller.downloadApk` 校验 SHA-256 之后、返回文件之前，追加一段签名与包名校验，任一不符即 `targetFile.delete()` 并抛 `IOException`：
  1. `context.packageManager.getPackageArchiveInfo(targetFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)`，取 `signingInfo.apkContentsSigners` 的 SHA-256 摘要集合；
  2. 与当前应用自身的签名摘要（`packageManager.getPackageInfo(context.packageName, GET_SIGNING_CERTIFICATES)`）求交集，为空则拒绝；
  3. 同时要求归档包的 `packageName == context.packageName`、`longVersionCode > BuildConfig.VERSION_CODE`。
  另在 `openHttpConnection`（`UpdateInstaller.kt:110`、`UpdateChecker.kt:289`）加一个主机白名单常量（发布域 + `github.com` / `objects.githubusercontent.com`），非白名单主机直接 `throw IOException`。
- 风险/注意：`app/src/main/cpp/lumen_security.cpp` 已经把 release 证书 SHA-256 编进原生层（`BuildConfig.APP_INTEGRITY_ENFORCEMENT_ENABLED`），可以复用同一份指纹做比对，但要注意 debug 构建用的是调试签名——校验必须以"当前进程自身的签名"为基准而不是硬编码 release 指纹，否则 debug 里自更新会全部失败。主机白名单需要和 `PROJECT_LUMEN_API_BASE_URL` 可被 CI 覆盖这一事实对齐（建议白名单从 `ProjectLumenApiConfig.baseUrl` 的 host 派生 + 常量补充）。

### [G05-02] 所有 OkHttp 客户端都没有 `callTimeout`，且 `execute()` 不随协程取消中断——慢流会长期占住 `Dispatchers.IO` 线程
- 严重度：P1
- 类别：E 韧性
- 位置：`app/src/main/java/com/projectlumen/app/core/api/SecureOkHttpFactory.kt:50-53`；受影响调用点 `core/api/ProjectLumenApiClient.kt:360`、`core/api/ProjectLumenTranslationApiClient.kt:96`；放大点 `core/api/BackendConnectivityController.kt:111`
- 现状：
  ```kotlin
  .connectTimeout(ProjectLumenApiConfig.REQUEST_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)  // 6s
  .readTimeout(...)   // 6s，每次 read 重新计时
  .writeTimeout(...)  // 6s
  .build()            // 没有 callTimeout
  ```
  `readTimeout` 是**单次读**的超时：服务端每 5 秒吐 1 个字节，这个请求就可以永远不超时。同时 `ProjectLumenApiClient.request` 用的是同步 `httpClient.newCall(request).execute()`，它是阻塞调用——`BackendConnectivityController.performProbe` 外面套的 `withTimeout(7_000)` 只能让协程抛 `TimeoutCancellationException` 返回，**底层 socket 读取不会被打断**，那个 IO 线程要等 OkHttp 自己的超时才能释放；探测立即安排下一次重试（`:126-128` 间隔 500ms，再叠 `BackendRetryPolicy` 5/30/120/300s）。
- 触发场景：弱网、运营商劫持页、企业/校园强制门户（captive portal）、后端进程僵死但 TCP 还活着——都会产生"连上了但只滴水"的连接。移动端很常见。
- 影响：`Dispatchers.IO` 默认上限 64 线程；被挂住的请求会持续累积（探测 + 遥测 + 同步 + 翻译），最终 Room 的 suspend DAO、DataStore、文件读写全部排队饿死，用户看到的是"应用整体卡住/一直转圈"，而不是一次干净的失败。
- 修复方案：
  1. `SecureOkHttpFactory.create` 追加 `.callTimeout(...)`，取一个明显大于单次读超时的整体上限（建议 `REQUEST_TIMEOUT_MILLIS * 3`，即 18s；APK/大 body 不走这个客户端，所以不会误杀）；
  2. 把 `ProjectLumenApiClient.request` 与 `ProjectLumenTranslationApiClient.request` 的 `execute()` 换成可取消形式：`suspendCancellableCoroutine { cont -> val call = httpClient.newCall(request); cont.invokeOnCancellation { call.cancel() }; call.enqueue(...) }`，或最小改动版——保留 `execute()` 但在 `withContext(Dispatchers.IO)` 内用 `currentCoroutineContext().job.invokeOnCompletion { call.cancel() }` 绑定取消。
- 风险/注意：`callTimeout` 会同时作用于健康探测；`PROBE_TIMEOUT_MILLIS = 7_000L` 比它小，所以探测行为不变。改成 `enqueue` 后异常类型会从 `IOException` 变成通过 `cont.resumeWithException` 传出的同一个 `IOException`，`BackendConnectivityController.errorCode`（`:197-205`）的分类逻辑无需改；但 `ProjectLumenApiDiagnostics.record` 的 `durationMillis` 计算依赖 `startedAtElapsed`，改写时不要把 `recordTrace` 挪出 `use{}` 之外。

### [G05-03] 证书固定"是否启用"只存在于构建期，pins 漏配会静默降级为无固定且运行期无从察觉
- 严重度：P1
- 类别：G 安全
- 位置：`app/src/main/java/com/projectlumen/app/core/api/SecureOkHttpFactory.kt:23`、`:31-33`、`:36-42`；`core/api/ProjectLumenApiClient.kt:21-24`（未传 `requireCertificatePins`）；`core/api/ProjectLumenTranslationApiClient.kt:33-37`（显式传 `false`）；构建期约定 `app/build.gradle.kts:134-143`、`:152-153`
- 现状：`build.gradle.kts` 只把 pins 字符串塞进 `BuildConfig.API_CERTIFICATE_PINS`，"启用"这个布尔量**没有**进 `BuildConfig`（`:134-137` 里 disabled 时直接把 pins 置空）。运行期：
  ```kotlin
  val pins = CertificatePinPolicy.parse(certificatePins)
  if (requireCertificatePins && pins.isEmpty()) { throw IllegalArgumentException(...) }  // 生产从不为 true
  if (pins.isNotEmpty()) { certificatePinner(...) }                                     // 空则完全不固定
  ```
  `requireCertificatePins` 在整个 `src/main` 里**只有默认值 `false` 和翻译客户端的显式 `false`**，唯一传 `true` 的地方是 `app/src/test/.../SecureOkHttpFactoryTest.kt:41`。也就是说这个"必须有 pins"的闸门是死代码。
- 触发场景：CI secret 名字打错、secret 未在 `release.yml` 的环境里生效、或有人临时把 `PROJECT_LUMEN_API_CERTIFICATE_PINNING_ENABLED` 设成 false 后忘了改回来。构建**不会失败**（`require` 只在 enabled=true 且 pins 空时触发），APK 正常发布，只是从此对后端与翻译主机零固定。
- 影响：发布版在企业根证书/用户安装根证书/中间人代理下可被完整解密与改写。结合 [G05-01]，改写 `/v1/releases/check` 就能投递任意 APK；改写 `/v1/config/*` 能下发任意策略（见 [G05-09]）。而且团队没有任何运行期信号能发现"这一版其实没固定"。
- 修复方案：
  1. `app/build.gradle.kts` 增加 `buildConfigField("boolean", "CERTIFICATE_PINNING_REQUIRED", ...)`（值取 `projectLumenApiCertificatePinningEnabled`，翻译侧同理），供运行期读取；
  2. `ProjectLumenApiClient` 的默认构造参数改为 `requireCertificatePins = BuildConfig.CERTIFICATE_PINNING_REQUIRED`，翻译客户端同理；
  3. 更强的一档（建议）：在 `SecureOkHttpFactory.create` 里加 `if (!BuildConfig.DEBUG && pins.isEmpty()) 记录一条 CrashBreadcrumbs / 非致命上报`，这样即使选择不硬失败，也能在遥测里看到"这台设备上的这一版没有固定"。
- 风险/注意：**不要**在没有逃生舱的情况下直接让 release 硬失败——pin 轮换时旧 pin 过期会让所有老版本彻底连不上后端、只能发版补救。推荐做法是同时配置"当前证书 + 备用 CA/下一张证书"两枚 pin（`CertificatePinPolicy.parse` 已支持逗号/分号/换行分隔多枚），并把硬失败限定为"构建期 + 启动期一次性诊断"，而不是每次请求抛异常。改动会让 `SecureOkHttpFactoryTest` 的三个用例仍然通过（它们直接传参，不读 BuildConfig）。

### [G05-04] 遥测快照在主线程做磁盘 / 相机服务 / 包管理器 IO，冷启动与每次计时状态切换都会触发
- 严重度：P1
- 类别：B 并发（主线程阻塞）
- 位置：`app/src/main/java/com/projectlumen/app/core/telemetry/EyeCareTelemetryReporter.kt:120-179`（`uploadCurrentSnapshotUnchecked` 全程没有 `withContext`）、`:341-355`（`frontCameraResolution`）、`:374-382`（`CrashReportStore(context).load()`）、`:510-527`（`shizuku?.collectDeviceDiagnostics`）
- 现状：调用链是 `ProjectLumenViewModel.init` → `reportingScope.launch{...uploadTelemetrySnapshot()}`，而 `reportingScope = CoroutineScope(viewModelScope.coroutineContext + handler)`（`app/ProjectLumenViewModel.kt:106`、`:236`）——即 **`Dispatchers.Main.immediate`**。`ProjectLumenRuntimeFeatureEntry.applyTransition`（`:212-223`）每次计时状态切换也在同一个 scope 里调它。快照构造过程中在主线程执行的阻塞工作有：
  ```kotlin
  frontCameraResolution()                       // CameraManager.getCameraCharacteristics()，同步 binder
  CrashReportStore(context).load()              // 读 app 外部私有目录里的崩溃报告文件
  shizuku?.collectDeviceDiagnostics(...)         // 内部自己切了 IO，安全
  upload.toJson()                                // 整个快照的 JSON 序列化
  ```
  只有真正的 HTTP 部分（`apiClient.uploadTelemetry` → `withContext(Dispatchers.IO)`）离开了主线程。Room 的 suspend DAO 自己会换线程，所以数据库读取不是问题。
- 触发场景：每次冷启动（ViewModel `init` 里就有一次），以及每次番茄钟/提醒引擎状态跃迁。`CrashReportStore.load()` 走的是 `getExternalFilesDir` 系列路径，首次访问外部存储卷较慢；`getCameraCharacteristics` 要遍历 `cameraIdList` 并跨进程取 `SCALER_STREAM_CONFIGURATION_MAP`。
- 影响：冷启动首帧后一次主线程停顿（实测量级为数十毫秒到数百毫秒，取决于外部存储与相机服务状态），番茄钟切换时掉帧；开了 StrictMode 会直接报 `DiskReadViolation`。
- 修复方案：
  1. 把 `uploadCurrentSnapshotUnchecked` 与 `uploadCrashReportUnchecked` 的函数体整体包进 `withContext(Dispatchers.IO) { ... }`（两个函数已经是 `suspend`，调用点无需改）；
  2. `frontCameraResolution()` 的结果在进程内缓存（`by lazy` 或 `@Volatile var cached: String?`）——同一台设备上它是常量，没有必要每次上报都查一遍。
- 风险/注意：`toDeveloperDebug` / `toDeviceDiagnostics` 是 `AppSettingsEntity` / `RuntimeStateEntity` 的扩展函数，包进 `withContext` 后闭包捕获不变，行为一致。注意 `BackendCommunicationArchitectureTest:60-62` 会按源码文本断言本文件仍包含 `decision(BackendCapability.TELEMETRY)` 与 `decision(BackendCapability.FACE_ANALYSIS)`，重排代码时不要把这两个调用挪走或改写。

### [G05-05] `ClashPartnerCompat.refresh` 在主线程做跨应用 ContentProvider 同步 binder 调用（含 `Application.onCreate`）
- 严重度：P1
- 类别：D 生命周期与框架约束
- 位置：`app/src/main/java/com/projectlumen/app/core/network/ClashPartnerCompat.kt:188-193`（`start`）、`:199-222`（`refresh`）、`:253-282`（`buildStatus`）、`:345-374`（`queryPartnerStatus`）；主线程调用点 `app/ProjectLumenApplication.kt:133`、`app/app/ProjectLumenSettingsScreen.kt:707-724`
- 现状：
  ```kotlin
  for ((pkg, uri) in partnerStatusUris) {                       // 3 个候选包
      val bundle = runCatching { resolver.call(uri, METHOD_PARTNER_STATUS, null, null) }.getOrNull() ?: continue
  ```
  `ContentResolver.call` 是**同步跨进程调用**，如果 Clash 进程没在运行，它会触发对方进程冷启动并一直阻塞到对方返回。`buildStatus` 之前还有 `pm.getApplicationInfo` ×3（`:333-343`）、`cm.allNetworks` 遍历（`:410-419`）、以及 `prefs()` 首次访问的 SharedPreferences 磁盘加载（`:248-251`）。三个主线程入口：`Application.onCreate` 第一行区（`:133`，且没有包在 `runCatching` 里）、设置页 `DisposableEffect` 里的 `ClashPartnerCompat.refresh(clashContext)`、开关回调里的 `setAutoAdaptEnabled` → `refresh`。
- 触发场景：设备上装了 Clash Meta 但进程未运行——这正是最常见的状态（用户没开 VPN）。冷启动时我们主动把对方进程叫起来并等它答复。
- 影响：冷启动可见的启动延迟（对方进程冷启动 + Application 创建 + provider 查询串行），极端情况（对方进程被系统冻结/正在被 LMK 回收）会命中 `Application.onCreate` 的 ANR 窗口；进设置页与拨动开关时同样有一次主线程停顿。
- 修复方案：
  1. 把 `refresh` 拆成"读状态"和"发布状态"两半：`ClashPartnerCompat.start(context)` 里先用缓存/默认值发布一次，随后在自有 `CoroutineScope(Dispatchers.IO)`（或 `HandlerThread`）里跑 `buildStatus` + `applyVpnProcessBinding`，完成后仍通过 `mainHandler.post` 通知 listener；
  2. 对外新增 `refreshAsync()` 供 UI 调用，`ProjectLumenSettingsScreen` 的 `DisposableEffect` 与开关回调改用它（该文件属别组，需在修复阶段同步改调用点）；
  3. `shouldSkipManualProxy()` 保持读缓存的 `status`，不要在里面触发查询——它被 `SecureOkHttpFactory.create`、`UpdateChecker.openHttpConnection`、`UpdateInstaller.openHttpConnection` 在热路径上频繁调用。
- 风险/注意：`start()` 之前 `shouldSkipManualProxy()` 必须继续返回 `false`（`SecureOkHttpFactoryTest:23` 直接断言了这一点，纯 JVM 单测下不能触发任何 Android 类初始化）。异步化后 `refresh` 不再能保证"返回时 `status` 已更新"，`ProjectLumenApplication:136-140` 给 `CrashReportPasteUploader.shouldSkipManualProxy` 装的 lambda 是延迟求值的，不受影响。

### [G05-06] Clash 伙伴状态不校验 provider 归属包与签名，且未知 `accessTier` fail-open 成 `Full`
- 严重度：P1
- 类别：G 安全
- 位置：`app/src/main/java/com/projectlumen/app/core/network/ClashPartnerCompat.kt:345-374`（`queryPartnerStatus`）、`:25-31`（`parseClashAccess`）、`:253-282`（`buildStatus` 用它决定 `clashVpnRunning`）
- 现状：两个问题叠在一起。
  ```kotlin
  .authority("$pkg.status")                       // 只按 authority 字符串找，从不问这个 authority 属于哪个包
  ...
  else -> if (values.isEmpty()) ClashAccess.Unavailable else ClashAccess.Full
  ```
  第一，只要有应用占住 `com.github.metacubex.clash.status` 这个 authority，`resolver.call` 就会打到它身上；代码从未用 `packageManager.resolveContentProvider(authority, 0)` 反查归属包，也没做签名校验。注释里 `signer_unverified` 说的是**Clash 校验我们**，反方向没有任何校验。
  第二，`queryPartnerStatus` 构造的 `values` map 用的是固定字面量 key + `getBoolean(..., false)` 默认值，因此**永远非空**，`parseClashAccess` 的 `values.isEmpty() -> Unavailable` 分支在生产代码里不可达（只有 `ClashPartnerCompatTest:20` 直接传 `emptyMap()` 才走到）。结果是：任何返回了 bundle 的 provider，只要 `accessTier` 不是 `denied`/`basic`/`full`（缺字段、拼写变化、未来新增的第四档），都被判为 **`Full`**——fail-open。
- 触发场景：(a) 恶意应用先于 Clash 安装并声明同名 authority（Android 拒绝重复 authority，所以先装者胜，此时真 Clash 装不上、`clashInstalled` 为 false，但 `queryPartnerStatus` 不看 `clashInstalled`，照样采信）；(b) 更现实的一档：CMFA 未来引入新的 tier 字符串（例如 `restricted`），我们会把受限响应当成完整授权，然后用它返回的 `vpnRunning` 决定是否把**整个进程**绑到 VPN。
- 影响：伪造/受限的 provider 可以让 `clashVpnRunning=true`，进而让 `applyVpnProcessBinding`（`:382-402`）把进程绑到设备上任意一条 VPN 网络（`findVpnNetwork` 只按 `TRANSPORT_VPN` 挑，不看归属），同时 `shouldSkipManualProxy()` 变 true、所有 HTTP 栈强制 `Proxy.NO_PROXY`。UI 上还会显示"VPN 已连接 · 流量自动经 Clash"这类误导文案。
- 修复方案：
  1. `queryPartnerStatus` 在 `resolver.call` **之前**加归属校验：`context.packageManager.resolveContentProvider("$pkg.status", 0)?.packageName` 必须 `== pkg`，否则 `continue`；进一步用 `PackageManager.checkSignatures` 或 `getPackageInfo(pkg, GET_SIGNING_CERTIFICATES)` 比对已登记的 CMFA 签名摘要（可复用 `BuildConfig.OPEN_API_TRUSTED_SIGNATURE_SHA256` 的同类做法，新增一个 `PROJECT_LUMEN_CLASH_TRUSTED_SIGNATURE_SHA256`，未配置时退回仅校验归属包）；
  2. `parseClashAccess` 改成 fail-closed：把 `else` 分支从 `Full` 改为——`accessTier` 字段存在但不认识 → `ClashAccess.Denied`（附 `deniedReason = "unknown_tier"`），`accessTier` 完全缺失（apiVersion < 3 的老 CMFA）→ 才按 `Full`。这需要 `values` 里区分"键不存在"与"值为 null"，建议把 `queryPartnerStatus` 里的 `"accessTier" to bundle.getString("accessTier")` 改成只在 `bundle.containsKey("accessTier")` 时放入。
- 风险/注意：`ClashPartnerCompatTest:11-21` 会受影响——`legacyRepliesWithoutAccessTierCountAsFullOnlyWhenNonEmpty` 依赖 `mapOf("vpnRunning" to true) → Full`（这一条仍成立）与 `emptyMap() → Unavailable`（仍成立）；新增"未知 tier → Denied"是新行为，需要补一条用例。改 fail-closed 后，如果 CMFA 的签名摘要没配对，会退化成"读不到 Clash 状态"并走 `clashInstalled && vpnActive` 启发式——这是安全的降级，但要确认 `statusLabel` 的文案不会误导用户（`describeDeniedReason` 已有 `signer_unverified` 文案可复用）。

### [G05-07] 同一份崩溃报告会被快照遥测反复上报（每次上报都读，但从不清理）
- 严重度：P1
- 类别：E 韧性（上报非幂等）
- 位置：`app/src/main/java/com/projectlumen/app/core/telemetry/EyeCareTelemetryReporter.kt:374-402`（`toDeveloperDebug`，`:377` 的 `CrashReportStore(context).load()`）；对照专用路径 `app/ProjectLumenApplication.kt:296-313`、`:331-339`
- 现状：专用崩溃上报路径是幂等的——`scheduleCrashReportUpload` 上报成功（`result?.accepted == true`）后调用 `clearUploadedCrashReport` 把文件删掉。但**普通快照上报也会捎带崩溃日志**：
  ```kotlin
  val crashLogs = if (settings.diagnosticCrashReportUploadEnabled) {
      CrashReportStore(context).load()?.let { report -> listOf(report.toCrashLogTelemetry()) }.orEmpty()
  }
  ```
  这条路径既不检查是否已经上报过，也不在成功后清理，且没有任何 `reportId` 去重字段进入 `CrashLogTelemetry`（`core/api/ProjectLumenTelemetryModels.kt:138-143` 只有 `crashedAt`/`exceptionType`/`rootCause`/`stackTraceLines`）。
- 触发场景：发生一次崩溃后，只要专用上报还没成功（后端不可达、`accepted != true`、或崩后用户很快又开始用应用），此后**每一次**快照上报都会带上同一份崩溃日志——非强制路径每 60s 一次（`MIN_UPLOAD_INTERVAL_MILLIS`），强制路径（每次计时状态跃迁、近距离/干眼告警）不受节流限制。
- 影响：后端崩溃统计被同一次崩溃刷成几十上百条，崩溃率/影响设备数全部失真；对用户是无谓的流量与电量。
- 修复方案：两个方向选一，建议都做：
  1. 最小修复：`toDeveloperDebug` 里**不再**附带崩溃日志（崩溃有专用幂等通道 `uploadCrashReport`），把 `crashLogs` 固定为 `emptyList()`，只保留 `sensorDisturbance` 与 `apiTraces`；
  2. 若产品上确实希望快照携带崩溃，则给 `CrashLogTelemetry` 增加 `reportId`（`CrashReport.reportId` 已存在，`clearUploadedCrashReport` 就是按它比对的），并在 `EyeCareTelemetryReporter` 里记住"最近已随快照上报过的 reportId"（进程内 `@Volatile var` 即可）跳过重复。
- 风险/注意：方案 1 后 `DeveloperDebugTelemetry` 可能整体变 `null`（`:396` 的三项皆空判断），后端要能接受 `developerDebug: null`——`ProjectLumenTelemetryJson.kt:15` 用的是 `putNullable`，契约上已经允许 null。不要顺手删掉 `uploadCrashReportUnchecked` 里的 `crashLogs`，那条才是正路。

### [G05-08] 服务端下发的设备管控数值没有客户端上限钳制
- 严重度：P1
- 类别：E 韧性 / G 安全
- 位置：`app/src/main/java/com/projectlumen/app/core/api/ProjectLumenDeviceControlJson.kt:9-31`（`toDeviceControlPolicy`）、`:57-70`（会话启动返回的 policy）、`:144-156`（生命周期事件返回的 policy）
- 现状：
  ```kotlin
  maxFps = silent.optInt("maxFps", 2),
  maxSessionMinutes = silent.optInt("maxSessionMinutes", 120),
  ...
  restartDelayMs = life.optLong("restartDelayMs", 0L),
  maxRestartBurst = life.optInt("maxRestartBurst", 3),
  ```
  默认值都很保守，但只要字段存在就原样采信，没有任何 `coerceIn`。这几个值直接决定相机采样频率、静默视觉会话时长、以及前台服务自愈重启的节奏。
- 触发场景：后端配置写错一个零（`maxFps: 20` 而不是 `2`）、后端被攻破、或（在 pins 漏配的构建里）中间人改写 `/v1/device-control/policy` 响应。
- 影响：`maxFps` 放大 → 前置摄像头高频采样 + ML Kit 推理，电量与机身温度失控；`maxSessionMinutes` 放大 → 静默视觉会话永不过期；`restartDelayMs = 0` + `maxRestartBurst` 巨大 → 前台服务自愈变成重启风暴。全都是用户可见的"手机烫、耗电快、应用反复重启"。
- 修复方案：在这三处解析点（建议抽一个 `private fun JSONObject.toSilentVisionPolicy()` 与 `toLifecycleLockPolicy()`，顺带消掉现在的三份复制粘贴）对每个数值字段加钳制：`maxFps` → `coerceIn(1, 5)`，`maxSessionMinutes` → `coerceIn(1, 240)`，`restartDelayMs` → `coerceIn(1_000L, 600_000L)`，`maxRestartBurst` → `coerceIn(0, 5)`。上限常量放在 `ProjectLumenDeviceControlModels.kt` 里作为 companion，和 data class 默认值放在一起便于对照。
- 风险/注意：钳制上限要和 `core/devicecontrol/` 组消费这些策略的实现对齐（那是别组的文件，本条只改解析侧）；若消费侧已经自己钳制过，这里仍应保留——防御要在信任边界上做一次。

### [G05-09] 遥测上报失败后没有退避，失败时每个采样 tick 都会重试
- 严重度：P2
- 类别：E 韧性
- 位置：`app/src/main/java/com/projectlumen/app/core/telemetry/EyeCareTelemetryReporter.kt:129`、`:177-178`、`:189`、`:211-212`
- 现状：节流依赖 `lastUploadAt`，而 `lastUploadAt.set(nowMillis)` 只写在 `apiClient.uploadTelemetry` **成功返回之后**（`.also { lastUploadAt.set(nowMillis) }`）。失败时 `lastUploadAt` 保持旧值，于是下一个 tick 的 `nowMillis - lastUploadAt.get() < MIN_UPLOAD_INTERVAL_MILLIS` 判断照旧为假，立刻再发一次。
- 触发场景：后端 5xx、限流 429、或网络处于"能连但很慢"的状态——此时连接性探测未必把状态打成 `UNREACHABLE`（探测只要 `/health` 通就算 REACHABLE），能力门禁不会拦。`ProximityDetectionService` 的检测循环与计时引擎跃迁都会持续触发。
- 影响：失败被放大成密集重试，叠加 [G05-02] 的无 `callTimeout`，每次重试还可能占住一个 IO 线程数秒；对用户是耗电，对后端是失败请求风暴。
- 修复方案：把 `lastUploadAt` 的语义从"上次成功时间"改为"上次尝试时间"——在 `apiClient.uploadTelemetry` 调用**之前**就 `lastUploadAt.set(nowMillis)`；再加一个 `consecutiveFailures` 计数，失败时按 `BackendRetryPolicy.delayMillis`（`core/api/BackendCommunicationPolicy.kt:81-88`，已有的 5/30/120/300s 表）拉长下一次允许上报的时间点，成功时归零。
- 风险/注意：`force = true` 的路径（`LightMonitorService:140`、告警时的近距离检测、`uploadDiagnosticsNow`）目前完全绕过节流，用户手动点"立即上报诊断"必须仍然能立刻发出——退避只应作用于 `force = false` 的自动上报。

### [G05-10] `runCatching` 吞掉 `CancellationException`，破坏结构化并发
- 严重度：P2
- 类别：E 韧性 / B 并发
- 位置：`app/src/main/java/com/projectlumen/app/core/telemetry/EyeCareTelemetryReporter.kt:86-93`、`:101-107`、`:114-117`；`app/src/main/java/com/projectlumen/app/core/update/UpdateChecker.kt:32`
- 现状：`runCatching { <suspend 调用> }.getOrNull()` 捕获的是 `Throwable`，包括协程取消用的 `CancellationException`。
  ```kotlin
  suspend fun uploadCurrentSnapshot(...): RemoteTelemetryUploadResult? {
      return runCatching { uploadCurrentSnapshotUnchecked(...) }.getOrNull()
  }
  ```
  被取消时这里不会把取消继续往外抛，而是安静地返回 `null`，调用者（`ProjectLumenViewModel` 的 `reportingScope`、`ProximityDetectionService` 的服务作用域）会当成"上报失败"继续执行后续逻辑。
- 触发场景：ViewModel 被清理（用户退出界面）、前台服务 `onDestroy`、或 `withTimeout` 触发时，正在进行的上报被取消。
- 影响：取消语义丢失——协程作用域已经在关闭，代码却继续跑完剩下的步骤（再写一次数据库、再发一次通知）；在 `UpdateChecker.checkForUpdate` 里表现为"后端清单查询被取消"被误判成"后端没有可用清单"，于是继续走 GitHub 兜底路径又发一次网络请求。
- 修复方案：这四处改成显式重抛取消：
  ```kotlin
  } catch (cancellation: CancellationException) {
      throw cancellation
  } catch (error: Throwable) {
      return null
  }
  ```
  或保留 `runCatching` 但在 `getOrElse { if (it is CancellationException) throw it else null }`。`UpdateChecker.kt:32` 同理。
- 风险/注意：`ProjectLumenApiClient.request`（`:384-389`）的 `catch (error: Throwable)` 是**先记 trace 再原样重抛**，本身没有吞异常，不要误改。`ProjectLumenApplication:304` 与 `ProjectLumenViewModel:236` 外层的 `runCatching` 同样吞取消，但那是别组文件，属同一类问题。

### [G05-11] 响应体没有大小上限，且诊断预览会把整个 body 再完整解析一遍
- 严重度：P2
- 类别：E 韧性
- 位置：`app/src/main/java/com/projectlumen/app/core/api/ProjectLumenApiClient.kt:399-401`（`response.body?.string()`）、`core/api/ProjectLumenTranslationApiClient.kt:107-109`、`core/update/UpdateChecker.kt:97`、`:251`（`bufferedReader().readText()`）；放大点 `core/api/ProjectLumenApiDiagnostics.kt:76-95`
- 现状：所有响应都用 `string()` / `readText()` 一次性读进 `String`，没有 `Content-Length` 上限检查、没有流式解析。随后 `recordTrace` 会把请求体与响应体都交给 `previewBody`：
  ```kotlin
  private fun previewBody(text: String?): String {
      val redacted = redactJson(normalized) ?: redactPlainText(normalized)   // 先整体解析 + 整体复制
      return if (redacted.length <= MAX_PREVIEW_CHARS) redacted else redacted.take(1200) + "...[truncated]"
  }
  ```
  截断发生在**解析与脱敏之后**，所以一个 5 MB 的 body 会先被 `JSONObject(...)` 解析成对象树、再 `redactObject` 复制一整棵新树、再 `toString()` 成第二个 5 MB 字符串，最后只留 1200 字符。
- 触发场景：`fetchLatestBackup` / `uploadBackup` 的备份 JSON（重度用户可达 MB 级）、`v1/sync/changes` 的大分页、静默视觉的 base64 帧上传（`RemoteCameraFramePayload.dataBase64`，`dataBase64` 不在敏感 key 名单里所以会被整段复制）、以及异常/恶意的超大响应。
- 影响：单次调用瞬时内存占用是 body 的 3~4 倍，低端机（`minSdk 29` 的入门机）在 `uploadBackup` 或帧上传时可能直接 OOM；即使不 OOM 也是明显的 GC 抖动。
- 修复方案：
  1. `previewBody` 先截断再脱敏：`val head = normalized.take(MAX_PREVIEW_CHARS * 2)`，超长时**跳过** `redactJson`（截断后的 JSON 本来就解析不了）直接走 `redactPlainText(head)`，再 `take(MAX_PREVIEW_CHARS)`；
  2. `ProjectLumenApiClient.readResponseText` 加上限：先看 `response.body?.contentLength()`，超过阈值（建议 4 MB）直接 `throw IOException`；未知长度时用 `response.peekBody(MAX_BODY_BYTES)` 或 `source().readString` 配合计数；
  3. `UpdateChecker.fetchTextAsset`（checksum 文件）与 `fetchLatestGitHubRelease` 同样加一个小上限（checksum 文件 64 KB 足够）。
- 风险/注意：`MAX_PREVIEW_CHARS = 1_200` 与 `MAX_TRACES = 30` 已经把诊断面板的**常驻**内存限住了，本条只影响瞬时峰值；改 `previewBody` 时要保证 `redactPlainText` 的两条正则（`ProjectLumenApiDiagnostics.kt:152-155`）仍能命中被截断文本里的 token 片段，否则会把半截 token 留在预览里。

### [G05-12] `UpdateChecker.checkForUpdate` 是 `suspend` 却在内部做阻塞 IO，靠调用点自己记得切线程
- 严重度：P2
- 类别：A 架构与设计
- 位置：`app/src/main/java/com/projectlumen/app/core/update/UpdateChecker.kt:30-59`（`checkForUpdate`）、`:84-119`（`fetchLatestGitHubRelease`，非 suspend 的阻塞函数）、`:239-258`（`fetchTextAsset`）；唯一调用点 `app/app/ProjectLumenApp.kt:202-204`
- 现状：`checkForUpdate` 是 `suspend`，但 GitHub 兜底分支直接调用同步的 `HttpsURLConnection`，函数内部没有任何 `withContext(Dispatchers.IO)`。目前之所以没炸，是因为唯一的调用点自己包了 `withContext(Dispatchers.IO) { runCatching { updateChecker.checkForUpdate() } }`。
- 触发场景：任何人新增第二个调用点（比如从 `WorkManager` worker、或从 ViewModel 里直接 `viewModelScope.launch { checkForUpdate() }`）而忘记包 `withContext`——`suspend` 签名会让人合理地以为它自己处理了线程。此时主线程执行 `HttpsURLConnection.responseCode` 会抛 `NetworkOnMainThreadException`。
- 影响：新增调用点即崩溃，且崩溃点看起来与调用者无关，排查成本高。另外阻塞 IO 不响应协程取消，退出界面后请求仍在跑。
- 修复方案：把 `checkForUpdate` 的函数体包进 `withContext(Dispatchers.IO) { ... }`（`apiClient.checkRemoteRelease` 内部已经自己切了 IO，嵌套 `withContext` 无害），并把调用点 `ProjectLumenApp.kt:202` 的外层 `withContext(Dispatchers.IO)` 保留或去掉都可以。
- 风险/注意：`BackendCommunicationArchitectureTest:47-48` 按源码文本断言本文件包含 `backendGate.decision(BackendCapability.RELEASE_DISCOVERY)`，包 `withContext` 时不要改动这一行的写法。文件里 `UpdateChecker.kt` 在 HEAD 处的括号计数不平衡是正则字面量导致的已知现象，不要试图"修正"。

### [G05-13] APK 下载：进度回调每 8 KB 触发一次；中断留下半个文件；安装后不清理缓存
- 严重度：P2
- 类别：C 资源管理 / E 韧性
- 位置：`app/src/main/java/com/projectlumen/app/core/update/UpdateInstaller.kt:42-59`（下载循环）、`:29`（`targetFile`）；消费侧 `app/app/ProjectLumenApp.kt:258-265`
- 现状：
  ```kotlin
  val buffer = ByteArray(DEFAULT_BUFFER_SIZE)          // 8 KB
  while (true) { ...; onProgress?.invoke(downloadedBytes, totalBytes) }   // 每 8 KB 回调一次
  ```
  调用侧的回调体是 `downloadProgressBytes = downloadedBytes`（Compose `mutableLongStateOf`），也就是**每 8 KB 写一次 Compose 快照状态**。一个 60 MB 的 universal APK ≈ 7700 次写。此外：下载中途抛 `IOException`（断网、磁盘满）时 `targetFile` 不会被删（只有 SHA 不匹配才 `delete()`），残留半个 APK 留在 `cacheDir`；成功安装后也没有任何地方删除这个文件。下载前没有检查可用空间（`cacheDir.usableSpace` vs `asset.sizeBytes`，而 `sizeBytes` 后端已经给了）。
- 触发场景：任何一次真实的应用内更新下载。
- 影响：下载进度条期间持续的重组风暴（对话框 UI 抖动/掉帧、下载速度也被拖慢）；`cacheDir` 里长期躺着一到多个几十 MB 的 APK（系统清理缓存前不会释放）；磁盘将满时下载失败信息不明确。
- 修复方案：
  1. 在 `downloadApk` 内部对回调做节流：记录 `lastReportedBytes` / `lastReportedAt`，只在 `downloadedBytes - lastReportedBytes >= 256 * 1024 || now - lastReportedAt >= 200ms` 时才 `onProgress?.invoke`，循环结束后补发一次最终值；
  2. 把下载体包进 `try { ... } catch (t: Throwable) { targetFile.delete(); throw t }`，保证任何失败都不留残件；
  3. 下载前检查 `asset.sizeBytes?.let { require(context.cacheDir.usableSpace > it * 2) }`，不足时抛带明确文案的 `IOException`；
  4. 安装成功后清理：可在 `installApk` 之外新增 `fun clearDownloadedApks()` 删除 `cacheDir` 下匹配 `*.apk` 的文件，由 UI 在 `UpdateDialogState.Hidden` 时调用（注意不能在 `installApk` 之后立刻删——系统安装器还要通过 FileProvider 读它）。
- 风险/注意：调用侧 `ProjectLumenApp.kt:260-263` 的 lambda 在 IO 线程里写 Compose 状态，节流后写入次数下降但线程语义不变；不要顺手把回调改成在主线程分发（那样会把 IO 线程和主线程绑在一起反而更差）。

### [G05-14] 非法证书 pin 会让 `apiClient` 每次访问都抛异常（`normalize` 无条件拼前缀）
- 严重度：P2
- 类别：G 安全 / E 韧性
- 位置：`app/src/main/java/com/projectlumen/app/core/api/CertificatePinPolicy.kt:12-15`；触发点 `core/api/SecureOkHttpFactory.kt:36-42`；构造点 `app/ProjectLumenApplication.kt:77-82`（`by lazy`）
- 现状：
  ```kotlin
  private fun normalize(pin: String): String {
      if (pin.isBlank()) return ""
      return if (pin.startsWith(PIN_PREFIX)) pin else "$PIN_PREFIX$pin"   // 什么内容都拼
  }
  ```
  没有校验 pin 是否是合法的 44 字符 base64 SHA-256。一个手误的 secret（多一个空格外的字符、粘成了十六进制、粘成了整段 PEM）会变成 `sha256/<垃圾>`，`CertificatePinner.Builder().add()` 在解析 base64 时抛 `IllegalArgumentException`——这个异常发生在 `ProjectLumenApiClient` 的默认构造参数里，也就是 `apiClient` 这个 `by lazy` 的初始化块里。Kotlin 的 `lazy` 不缓存失败，因此**每次访问 `app.apiClient` 都会重新抛一次**。
- 触发场景：CI secret 配错格式。构建期的 `require` 只检查"非空"，不检查格式，所以这类错误能一路发布出去。
- 影响：健康探测路径被 `runCatching` 兜住会降级成"后端不可达"（全部后端功能隐藏），但任何在主线程直接访问 `app.apiClient` 的路径会直接崩溃；用户看到的是"云功能全部消失"或启动即崩，且现场日志只有一行 base64 解析错误。
- 修复方案：
  1. `CertificatePinPolicy.parse` 里过滤掉非法 pin：正则 `^(sha256/)?[A-Za-z0-9+/]{43}=$` 不匹配的直接丢弃（而不是拼前缀后交给 OkHttp 抛异常），并考虑返回一个"被丢弃了几条"的信息供诊断；
  2. `app/build.gradle.kts:138-143` 的 `require` 增加格式校验，把这类错误挡在 CI 里（构建期失败远好过发布后失败）。
- 风险/注意：过滤后如果所有 pin 都非法，就退化成"零固定"——这正是 [G05-03] 要求能被察觉的情形，两条应一起修：过滤 + 运行期可见性（诊断/面包屑），而不是静默通过。

### [G05-15] `BackendConnectivityController.retryJob` 跨线程读写且无 `@Volatile`/锁
- 严重度：P2
- 类别：B 并发
- 位置：`app/src/main/java/com/projectlumen/app/core/api/BackendConnectivityController.kt:34`（声明）、`:56-66`（`onForeground`/`onBackground`，主线程）、`:134-135`、`:164`、`:168-175`（探测协程，`Dispatchers.IO`）
- 现状：`private var retryJob: Job? = null` 没有 `@Volatile`，也不在 `probeLock` 保护内（`activeProbe` 是被正确同步的，`retryJob` 不是）。写它的线程有两类：`AppLifecycleCoordinator` 通过 `ProcessLifecycleOwner` 在主线程调用 `onForeground`/`onBackground`；`markReachable`/`markUnreachable`/`scheduleRecoveryRetry` 在 `applicationScope`（`Dispatchers.IO`）里跑。
- 触发场景：用户在后端探测失败的瞬间切到后台（或从后台切回前台）——`onBackground()` 的 `retryJob?.cancel()` 可能读到的是过期引用，刚被 IO 线程赋值的新 job 逃过取消；反向也可能出现两个 retryJob 并存。
- 影响：目前后果有限——重试 job 内部还有 `if (foreground.get()) refresh(force = true)` 兜底（`:173`），所以漏取消的 job 醒来后会发现不在前台而空转退出。真实损失是最长 300 秒的僵尸协程与一次不该发生的重复探测。
- 修复方案：给 `retryJob` 加 `@Volatile`（最低成本），或把它一并纳入 `probeLock`：`private fun swapRetryJob(next: Job?) { synchronized(probeLock) { retryJob?.cancel(); retryJob = next } }`，`onForeground`/`onBackground`/`markReachable`/`scheduleRecoveryRetry` 统一走这个入口。
- 风险/注意：`BackendConnectivityControllerTest` 的 `concurrentRefreshesShareOneProbe` 依赖 `activeProbe` 现有的同步语义，不要把 `probeLock` 的临界区扩大到包含 `await()`（会死锁）。

### [G05-16] `ProjectLumenApiClient` 已是覆盖 11 个能力域的上帝类，`SilentVisionPolicy`/`LifecycleLockPolicy` 解析三份复制
- 严重度：P2
- 类别：A 架构与设计
- 位置：`app/src/main/java/com/projectlumen/app/core/api/ProjectLumenApiClient.kt:30-304`（23 个端点方法）；`core/api/ProjectLumenDeviceControlJson.kt:9-20` / `:57-70` / `:144-156`（同一份 `SilentVisionPolicy` 解析写了两遍、`LifecycleLockPolicy` 写了两遍）
- 现状：单个类里塞了认证、设备注册、权益/内购、远程配置、发布发现、云同步、云备份、遥测、人脸分析、设备管控——`BackendCapability` 枚举里全部 11 个能力共 23 个方法；`ProjectLumenDeviceControlJson.kt` 里三处逐字段重复的 policy 解析（每处约 10 行、默认值必须手工保持一致）。
- 触发场景：任何一次契约演进——例如给 `SilentVisionPolicy` 加一个字段，就必须记得同时改三处；漏改一处的表现是"某条路径下新字段永远是默认值"，而这类 bug 没有编译错误、也没有测试覆盖。[G05-08] 的钳制修复同样会遇到"要改三份"的问题。
- 影响：可维护性；以及上面这类"改漏一处"的静默行为不一致。
- 修复方案：
  1. 先做低风险的一步：在 `ProjectLumenDeviceControlJson.kt` 里抽出 `private fun JSONObject.toSilentVisionPolicy()` 与 `private fun JSONObject.toLifecycleLockPolicy()`，三处调用改为复用（顺带把 [G05-08] 的钳制只写一遍）；
  2. `ProjectLumenApiClient` 的拆分留到后续：把 `request()` 抽成 `ProjectLumenApiTransport`，然后按域拆 `AuthApi` / `SyncApi` / `TelemetryApi` / `DeviceControlApi` 等门面，共用同一个 transport 与 `backendGate`。
- 风险/注意：**拆分 `ProjectLumenApiClient` 会直接打破两个现有测试**——`BackendCommunicationArchitectureTest:10-30` 用源码文本断言"`= request(` 之后的第一个参数必须是 `capability = BackendCapability.`"且"`backendGate.requireExecutable` 出现在签名与 `httpClient.newCall(request).execute()` 之前"，`:33-41` 断言"全仓库只有 `ProjectLumenApplication.kt` 会构造 `ProjectLumenApiClient(`"。如果本轮只做第 1 步（policy 解析去重），这两个测试不受影响；要动第 2 步必须同步改测试。

### [G05-17] `ProjectLumenTranslationApiClient` 的 `context` 参数从未使用，却强迫调用方持有 `Context`
- 严重度：P2
- 类别：A 架构与设计
- 位置：`app/src/main/java/com/projectlumen/app/core/api/ProjectLumenTranslationApiClient.kt:31`
- 现状：`class ProjectLumenTranslationApiClient(context: Context, ...)` —— `context` 既不是 `val`，构造体与 `init` 里也没有任何引用，纯粹是个未使用参数（Kotlin 会给出未使用告警）。
- 触发场景：想给翻译客户端写纯 JVM 单测时，必须凭空造一个 `Context`（Robolectric 或 mock），而这个类实际上只需要 `baseUrl` 与 `OkHttpClient`。
- 影响：可测试性无谓变差；也会让读者以为这个类持有 Android 依赖。
- 修复方案：删掉 `context` 参数，同步修掉唯一调用点 `app/src/main/java/com/projectlumen/app/app/ProjectLumenTranslationScreen.kt:79`（`val api = remember(context) { ProjectLumenTranslationApiClient(context.applicationContext) }` → `remember { ProjectLumenTranslationApiClient() }`）。
- 风险/注意：`BackendCommunicationArchitectureTest:56-58` 断言 `ProjectLumenTranslationScreen.kt` **包含** `ProjectLumenTranslationApiClient` 且**不包含** `BackendCapability`——改构造参数不影响这两条，但修改调用点时**不要**顺手给翻译加能力门禁（见"已核查"部分，那是有意的设计）。

### [G05-18] `UpdateChecker` 里有死代码：未使用的 `queryEncode` 与未使用的 `JSONArray` import
- 严重度：P2
- 类别：H 编译与结构
- 位置：`app/src/main/java/com/projectlumen/app/core/update/UpdateChecker.kt:199-201`（`queryEncode` 无任何调用方）、`:14`（`import org.json.JSONArray`，文件里只用到 `optJSONArray` 的推断类型，没有显式引用该类型名）、`:18`（`import java.net.URLEncoder` 仅被 `queryEncode` 使用）
- 现状：查询串编码已经由 `ProjectLumenApiClient.queryEncode`（`:395-397`）负责，`UpdateChecker` 自己那一份是历史残留；`JSONArray` 与 `URLEncoder` 两个 import 随之悬空。
- 触发场景：CI 的 `gradle lintDebug --warning-mode all` 会打出未使用警告；后续若引入 ktlint/detekt 或把警告升级为错误就会直接卡住构建。
- 影响：仅噪音与潜在的构建卡点，没有运行期后果。
- 修复方案：删除 `queryEncode`（`:199-201`）以及 `:14` 和 `:18` 两行 import。
- 风险/注意：删 `URLEncoder` 前确认 `deviceRolloutKey()` 等后续改动不需要它——目前 rolloutKey 是通过 `apiClient.checkRemoteRelease` 的参数传的，编码由 `ProjectLumenApiClient` 完成。

## 已核查但无问题的点

以下是我逐行确认**正确**的关键设计，修复阶段请勿"顺手改掉"：

**安全（G）**
- 全仓库没有任何 `HostnameVerifier { _, _ -> true }`、trust-all `TrustManager`、`sslSocketFactory` 覆写，也没有 `HttpLoggingInterceptor`（`rg` 全仓库确认，唯一的裸 `OkHttpClient()` 在 `ProjectLumenApiClientGateTest.kt:38`，测试代码，合理）。
- 本组四个目录里**没有任何** `Log.d/v/i/w/e` 或 `println`（`rg` 确认），不存在日志泄漏 token / 安装 ID / 健康数据的问题。
- HMAC 签名覆盖完整且无拼接注入：`ProjectLumenRequestSigner.canonicalPayload` 用 `sortedMapOf` 固定顺序，签 `method + encodedPath + encodedQuery + bodySha256 + timestamp + nonce`，以 `key=value` 换行拼接；`encodedPath`/`encodedQuery` 是百分号编码的（不含换行），`bodySha256` 与 `timestamp`/`nonce` 是 hex/数字，因此不存在 `a|bc` 与 `ab|c` 撞串的分隔符注入。密钥来自原生层 `NativeSecurityBridge.requestSigningSecretOrNull()`，仅 `BuildConfig.DEBUG` 下才允许本地兜底常量，release 缺失即 `error(...)`。
- `SecureOkHttpFactory.create` 强制 `https`（`:26-28`），`UpdateChecker.openHttpConnection`（`:289-303`）与 `UpdateInstaller.openHttpConnection`（`:110-124`）同样强制 `https`。
- APK 下载**确实**校验 SHA-256（`UpdateInstaller.kt:55-59`），不匹配即删文件并抛异常；`selectBestAsset`（`UpdateChecker.kt:353-357`）会过滤掉没有 sha256 的 APK 资产。缺的只是签名一致性校验（[G05-01]）。
- `FileProvider` 权限最小化：authority `${applicationId}.fileprovider` 且 `exported="false"`（`AndroidManifest.xml:199-205`），`file_paths.xml` 只暴露 `cache-path` / `external-cache-path`，`SecureShareIntents.viewApk` 只加 `FLAG_GRANT_READ_URI_PERMISSION`（无 WRITE）。
- `ProjectLumenApiDiagnostics` 的脱敏是真实有效的：key 名含 `token`/`authorization`/`password`/`secret`/`code`/`email`/`rawpayload`/`backup` 的一律替换为 `[redacted]`，另有两条正则兜住 `Bearer xxx` 与常见 token 字段；trace 上限 30 条、预览上限 1200 字符，常驻内存有界。

**韧性（E）与熔断**
- **熔断是真的**，不只是记状态：`BackendCommunicationPolicy.resolve` 在 `UNREACHABLE` 时对所有非 `HEALTH_PROBE` 能力返回 `executable = false`，`requireExecutable` 抛 `BackendCommunicationBlockedException`，请求在**签名与网络之前**就被拦下（`ProjectLumenApiClient.kt:314-315`，顺序有测试守护）。UI 侧靠 `visible = false` 直接隐藏后端功能而不是无限 loading。
- 退避表有界且合理：`BackendRetryPolicy` 5s → 30s → 120s → 300s 后封顶；探测本身 `PROBE_ATTEMPTS = 2` + 500ms 间隔 + 60s 最小探测间隔（`MIN_PROBE_INTERVAL_MILLIS`）；后台时 `scheduleRecoveryRetry` 直接不排（`:169`）。
- `CHECKING` 状态下有 5 分钟"最近可达"宽限（`RECENT_REACHABLE_TTL_MILLIS`），避免每次探测期间功能闪断。
- 并发探测去重正确：`activeProbe` 的读写全部在 `synchronized(probeLock)` 内，`invokeOnCompletion` 里也做了 identity 检查后再清空；有测试 `concurrentRefreshesShareOneProbe` 守护。
- **连接状态只有一个真相源**：`BackendConnectivityController._state`（`StateFlow`）是唯一权威，MMKV（`MmkvBackendConnectivityPersistence`）只是它的持久化镜像且只存"稳定态"（`stableStatus` 把 `CHECKING`/`UNKNOWN` 归一为 `UNKNOWN`，不会把瞬态写进磁盘）；`ProjectLumenViewModel.backendConnectivityState`（`:191`）与 `ProjectLumenApplication:317` 都是直接 `map` 这一个 flow，没有第二份状态。
- **遥测没有无界离线队列**：设计上是 fire-and-forget 快照（失败即丢弃），因此不存在事件堆积导致 OOM / 磁盘打满的问题。缺的是失败退避（[G05-09]）与崩溃日志去重（[G05-07]），不要为了"可靠上报"引入一个无上限的本地队列。
- 遥测的字段级上限齐备：`MAX_CRASH_STACK_LINES = 32`、`MAX_CRASH_LINE_LENGTH = 320`、`MAX_API_TRACE_COUNT = 12`、`MAX_CONFIGURATION_ITEMS = 24` 等，单次上报体积可控。

**JSON 契约（E）**
- 手写解析基本全部走 `opt*` + 显式默认值，缺字段/类型不符不会抛异常（`ProjectLumenApiJson.kt`、`ProjectLumenDeviceControlJson.kt`、`ProjectLumenFaceAnalysisJson.kt`、`ProjectLumenTelemetryJson.kt` 逐行确认）；`toObjectList`/`toStringList`（`ProjectLumenApiJson.kt:206-222`）对 null 数组与非对象元素都做了跳过。
- 顶层 JSON 非法时 `String.toJsonObject()` 会转成语义清晰的 `IOException("...invalid JSON.")`，不会把 `JSONException` 泄给上层；空 body 返回空 `JSONObject`。
- 少数 `getJSONObject`（`toAuthSession` 的 `"user"`、`toRemoteBackup` 的 `"metadata"`、`fetchMe` 的 `"user"`）会在字段缺失时抛 `JSONException` —— 我核对了这几条路径的调用方都在 `runCatching` 内，不会崩溃，因此不单列为缺陷；但若将来有新调用方直连，建议改成 `optJSONObject(...) ?: JSONObject()`。
- 这一组**没有** `kotlinx.serialization` 与手写序列化并存的双套机制——全部是 `org.json` 手写，风格统一（代价是 [G05-16] 的重复）。

**版本比较与更新判定**
- 版本比较是数值比较而非字符串比较：`parseVersionDescriptor` 把 `1.10.0` 解析成 `SemanticVersion(1, 10, 0)`，`compareValuesBy(major, minor, patch)` 因此 `1.10.0 > 1.9.0` 判定**正确**。
- 后端清单路径有防降级：`remoteRelease.versionCode <= currentBuild.versionCode` 直接 `NoUpdate`（`UpdateChecker.kt:69`）。
- `isExactVersionMatch` 用 short hash 精确排除"同版本号但同一次构建"的误报；`isSdkRelease` 排除 `lumen-crash*` / `sdk-*` 这类 SDK tag，避免把 SDK 发布当成 App 更新。
- `BuildUpdateNotesParser` 强制打包说明的 `commitHash` / `buildTimeUtcMillis` 与当前构建**完全一致**才采用，否则回落到 fallback —— 这一条防的是"旧说明配新包"，是对的。
- `BuildUpdateNotesLoader` 的双重检查缓存发布顺序正确（先写 `cachedBuild` 再写 `cachedNotes`，两者都是 `@Volatile`，读端先读 `cachedNotes`），没有可见性问题。
- `ClashPartnerCompat` 里 `mainHandler` 与 `partnerStatusUris` 都是 `by lazy`——这是为了纯 JVM 单测不触发 `Handler(Looper.getMainLooper())` / `android.net.Uri` 的类加载崩溃，注释已写明，**不要**改成 eager 初始化。

**有意的设计（勿"修正"）**
- **翻译客户端故意不接 `BackendCapabilityGate`**：`BackendCommunicationArchitectureTest:55-58`（`recoveryPathsAndBackgroundCollectorsRemainExplicitlySeparated`）显式断言 `ProjectLumenTranslationScreen.kt` 不含 `BackendCapability`——翻译被定义为独立于主后端健康度的恢复路径。给它加熔断会让测试失败，也违背设计意图。
- `AllowAllBackendCapabilityGate` 作为默认参数只服务于测试与独立构造场景；生产装配（`ProjectLumenApplication.kt:79`、`:102`）一律传 `backendConnectivity`，且有测试断言全仓库只有 `ProjectLumenApplication` 构造 `ProjectLumenApiClient`。
- `ProjectLumenApiConfig.normalizeApiBaseUrl` 把裸主机根（`https://tts.chloemlla.com`）纠正为带 `/api/lumen` 前缀的完整 base URL，是为了兼容 CI 里可能只配主机的情形，逻辑正确。
- `LifecycleLockPolicy.antiUninstallIntent` 注释明确"仅策略元数据，客户端从不阻止卸载或隐藏控件"——这是合规上的重要约束，任何改动都不要让它真的去干预卸载。
