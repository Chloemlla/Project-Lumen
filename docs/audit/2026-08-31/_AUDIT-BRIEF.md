# Project-Lumen 架构审查纲要（所有审查 agent 必读）

仓库：`F:\Repositories\GitHub\Project-Lumen`（Windows，git 分支 `main`，工作树干净）

## 项目背景

Android 护眼 / 专注类应用：Kotlin + Jetpack Compose Material 3 + Room + Java 21，`minSdk 29`、`compileSdk/targetSdk 37`。

- **单 Activity + Compose**：`MainActivity` 承载全部 UI，屏幕散落在 `app/app/ProjectLumen*Screens.kt` / `*FeatureEntry.kt`
- **手写依赖注入**（无 Hilt/Dagger）：`ProjectLumenApplication` 构造所有服务并注入 `ProjectLumenViewModel`；ViewModel 通过 lambda 回调（`startTimerService`、`startLightMonitoring` 等）命令 Android 服务，从而不依赖 `Context`
- **单向状态流**：`ProjectLumenRepositories`（聚合 Room 仓库）→ `ProjectLumenStateStore` 用 `combine` + `stateIn` 合成单一 `ProjectLumenUiState` → ViewModel 暴露
- **持久化分层**：Room（`core/database`，KAPT 导出 schema 到 `app/schemas`）、DataStore（`EyeCarePreferencesDataStore`）、腾讯 MMKV、`security-crypto`（`SecureCredentialStore` 存安装/设备标识与密钥）
- **后台**：前台服务 `TimerForegroundService` / `ProximityDetectionService`（相机 + ML Kit 人脸检测与网格）/ `LightMonitorService` / `EyeProtectionOverlayService` / `DeveloperDebugOverlayService`；计时用 **AlarmManager 精确闹钟**（`AlarmReceiver`，`BootReceiver` 重排）并由 **WorkManager** 对账（`TimerReconciliationWorker`、`ShizukuResilienceWorker`）
- **网络**：OkHttp 由 `SecureOkHttpFactory` 构建，可选证书固定（`CertificatePinPolicy`）+ HMAC 请求签名；`ProjectLumenApiClient` 对接 Rust 后端，翻译走独立客户端
- **Shizuku**（`dev.rikka.shizuku`）提供免 root 提权 shell（如按应用网络管控）
- **原生安全层** `app/src/main/cpp/lumen_security.cpp`：把请求签名密钥、release 证书 SHA-256、期望包名编译进 `.so` 做完整性/证明校验
- **对外开放 API**：`ILumenOpenApi.aidl` + `LumenOpenService`（绑定服务）+ 导出的 `openapi/*Activity`，由自定义权限 `ACCESS_LUMEN_CORE`（dangerous）/ `TRIGGER_LUMEN_CONTROL`（signature）门禁

## 硬性纪律（违反即任务失败）

1. **禁止运行任何构建 / 测试 / lint**：不得执行 `gradle`、`gradlew`、`npm`、`bun`、`kotlinc`、`cargo` 等。本机性能不足，一切构建只由 GitHub Actions 负责。
2. **禁止修改任何源码**。你是只读审查员。唯一允许写入的文件是分配给你的那份报告。
3. **禁止调用 `find`**（尤其 Git 自带的 `find.exe`）。找文件用 `fd`，搜内容用 `rg`，或直接用 Glob / Grep 工具。
4. 允许的只读命令仅限：`fd`、`rg`、`wc`、`git show`、`git log`、`git diff`。
5. **只准读分配给你的文件组**。可以为了理解调用关系去 `rg` 全仓库、读少量别组文件，但**缺陷只报在自己组的文件上**，别人组的问题交给别人。

## 审查维度（逐类核对，逐行读完自己组的文件）

- **A 架构与设计**：分层被击穿（UI 直读 DAO、Service 绕过 Repository）、职责边界模糊、上帝类 / 超级文件、循环依赖、重复实现、抽象缺失或过度、可测试性差（硬依赖 `Context` / 静态单例）、**同一事实存在多个真相源**
- **B 并发与线程安全**：read-modify-write 竞态（`dao.get()` → `copy` → `upsert` 不在同一把锁内）、共享可变 `var` 无 `@Volatile`/`Mutex`/`synchronized`、主线程阻塞调用（`Debug.getMemoryInfo`、`getHistoricalProcessExitReasons`、大文件/位图/PDF）、`CoroutineScope` 未随生命周期 `cancel()`、同一 Room 实体多写者
- **C 资源管理**：ML Kit `detector`/`meshDetector`、流、`Cursor`、`Bitmap`、`PdfDocument` 未关闭；关闭不在 `finally` / `use{}`；无上限的缓存与帧存储
- **D 生命周期与框架约束**：`startForegroundService` 冷启动抛 `ForegroundServiceStartNotAllowedException`（重试必须非阻塞，不能 `SystemClock.sleep`）；Activity 基类与主题匹配（`AppCompatActivity` 需要 AppCompat 主题）；**静态/顶层初始化里 `Handler(Looper.getMainLooper())` 会让纯 JVM 单测类加载 NPE → `ExceptionInInitializerError`**，应 `by lazy`；`LOCKED_BOOT_COMPLETED` 时 CE 加密的 Room 不可读；**Compose `remember`/`derivedStateOf` 的 key 必须覆盖 lambda 实际读到的全部字段**（"UI 不更新"头号成因）
- **E 韧性**（AI 生成代码系统性缺失，务必逐条追问）：每个外部调用是否设超时；重试是否指数退避、重试的操作是否幂等；依赖挂掉能否熔断 / 降级兜底；有无无界队列 / 无上限并发 / 无退出条件循环导致 OOM 或失控
- **F 持久化一致性**：并发累加丢更新（`sum += delta`）；**MMKV 必须先于 Room 写入**，否则崩溃后陈旧 MMKV 会永久覆盖新 Room；双真相源不一致；Room 迁移与导出 schema 是否同步
- **G 安全**：硬编码凭据 / 密钥 / salt；明文写敏感数据而非 `SecureCredentialStore`；组件 `exported` 与自定义权限、`queryIntentActivities` 劫持；WebView 的 `setJavaScriptEnabled`/`addJavascriptInterface`/https 校验；`Log.d/v` 打印 token / 安装 ID / PII；完整性门禁要 fail-closed 但**不能误杀模拟器与开发环境**；Shizuku shell 是否幂等、有无命令注入
- **H 编译与结构**：花括号 / 括号平衡（kapt 只报第一个语法错误）；未使用 import（会被 lint 卡）；改函数签名后所有调用点（含命名参数）是否同步；`when` 分支是否穷尽

## 报告格式（严格遵守）

```markdown
# G<N> <组名> 审查报告

- 审查文件数：N，总行数：N
- 结论摘要：一段话——这一组的架构健康度、最严重的问题是什么

## 缺陷清单

### [G<N>-01] 一句话标题
- 严重度：P0 / P1 / P2
- 类别：A 架构 / B 并发 / C 资源 / D 生命周期 / E 韧性 / F 持久化 / G 安全 / H 编译结构
- 位置：`相对路径:行号`（多处全列）
- 现状：关键代码片段（≤10 行）+ 当前行为说明
- 触发场景：什么条件下真会出问题
- 影响：用户可见的后果
- 修复方案：具体到"改哪个文件的哪个函数、怎么改"
- 风险/注意：修复可能波及的调用方或行为变化

## 已核查但无问题的点
- 列出你确认过是正确的关键设计（防止后续修复阶段误改）
```

严重度定义：

| 档 | 含义 |
|---|---|
| **P0** | 会崩溃 / 丢数据 / 安全漏洞 / 功能端到端失效 |
| **P1** | 明确的架构缺陷或真实 bug，但不致命 |
| **P2** | 可维护性、一致性、潜在隐患（需确认的也归这里并注明"需确认"） |

## 质量红线

- **只报有真实触发场景的问题**。凑数、纯风格偏好（缩进/命名喜好）、"建议加注释"一律不要写。
- 说不出真实触发场景的，不要写进清单。
- 不确定的写成 P2 并注明"需确认"，不要伪装成确定结论。
- 每条必须做到：另一个人只看你的报告就能动手修。
- 宁可 8 条扎实的，也不要 40 条注水的。
