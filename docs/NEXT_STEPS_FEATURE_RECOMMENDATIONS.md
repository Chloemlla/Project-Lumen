# Project Lumen 商业化发布下一步功能与技术路线图

本文档针对当前 `Project-Lumen` 仓库状态重写，用于规划下一阶段商业化发布、功能分层、技术落点、后端生产化、发布渠道和验证目标。

> 仓库执行约束：实际构建、测试、Lint、安装和依赖安装命令只能在 GitHub Actions 工作流中执行；本地只做代码和文档修改。本文所有验证项默认进入 GitHub workflow，不要求本地执行。

## 1. 当前项目状态

### 1.1 产品形态

Project Lumen 当前已经不是单纯的本地倒计时工具，而是一个以“用眼休息、专注计时、护眼检测、数据统计、个性化模板、商业权益、后端同步”为核心的 Android + Rust 服务端产品。

当前 Android 端：

- 原生 Kotlin Android 应用。
- Jetpack Compose Material 3 UI。
- Room 数据库，当前 `AppDatabase` 版本为 `13`，`exportSchema = true`。
- Java 21 / Kotlin 2.1.20 / Android Gradle Plugin 8.12.0。
- `compileSdk = 37`、`targetSdk = 37`、`minSdk = 26`。
- 当前应用版本来源为 `app/application.version`，现值 `1.0.1`。
- 通过 `BuildConfig.API_BASE_URL` 默认连接 `http://eye.chloemlla.com/api`。

当前后端：

- Rust API 服务，`backend/` 下使用 Axum 风格路由拆分。
- MongoDB 存储用户、会话、权益、同步变更、备份、Admin 数据和遥测数据。
- 默认 API 前缀 `/api`。
- 静态 Admin Dashboard 默认托管在 `/admin`。
- Dockerfile 与 GHCR 镜像 workflow 已存在。

当前 CI/CD：

- `.github/workflows/build.yml` 覆盖 Android 单元测试、Android Lint、Release APK 构建、Release assets、SHA256 checksum、自动 GitHub Release，以及 Rust 后端格式检查和测试。
- `.github/workflows/release.yml` 覆盖 tag 或手动触发的 Android Release APK 构建和 GitHub Release。
- `.github/workflows/build-artifacts.yml` 覆盖 GHCR 镜像构建与可选远程部署。
- `.github/workflows/codeql.yml` 覆盖 Java/Kotlin 与 GitHub Actions CodeQL。

## 2. 已添加功能归档

本节作为商业化路线图的基础盘点，后续规划不再重复把这些内容当作从零实现项。

### 2.1 免费核心体验已具备

- 用眼提醒：工作计时、预提醒、正式提醒、等待操作、休息、跳过、暂停、恢复、停止。
- 番茄钟：专注、短休息、长休息、轮次推进、停止和统计。
- 通知系统：通知渠道、前台服务通知、闹钟触发、通知动作、Android 13+ 通知权限引导。
- 后台可靠性：`AlarmManager`、前台服务、开机恢复、运行态持久化、恢复协调。
- 基础统计：用眼统计、番茄统计、休息完成、跳过、最大连续工作、近 7/30 天/月度筛选。
- 目标体系：每日休息次数、最大连续工作时长、每日番茄数、每周活跃天数。
- 静默时段：`PAUSE_TIMER`、`SILENT_NOTIFICATIONS`、`RECORD_ONLY`，支持跨午夜。
- 模板系统：内置模板、系统色模板、背景色、图片路径、标题、副标题、跳过按钮、倒计时样式。
- 数据导出：CSV、统计图片、PDF 月报。
- 本地 JSON 备份/恢复：设置、目标、模板、统计、权益、feature flags、提醒计划。
- 崩溃报告：本地保存、启动提示、复制、分享、基础脱敏。
- 更新检测：GitHub Release 检测、ABI APK 选择、下载进度、SHA256 校验、未知来源安装授权跳转。
- 国际化：系统语言、中文、英文。

### 2.2 护眼增强能力已具备

- 近距离检测：前置相机采样、ML Kit 人脸检测、人脸宽度/眼距基准、近距离阈值、冷却时间。
- 眨眼提醒：基于眼睛打开概率和无眨眼时间阈值的干眼风险提醒。
- 低光照提醒：环境光检测、低亮度阈值、低光统计。
- 自动亮度：最低/最高亮度百分比配置。
- 强提醒遮罩：全屏休息遮罩、严格距离触发、休息时长配置。
- Shizuku 高级模式：上下文感知采样、服务恢复、息屏/低电量/省电/勿扰/温控/相机隐私保护条件。
- 开发调试：本地调试遮罩、实时预览、低内存模拟、传感器和前台服务诊断字段。

### 2.3 商业化基础已具备

- 本地权益层：
  - `PlanTier`: `FREE`、`PRO`、`PLUS`、`TEAM`。
  - `PremiumFeature`: `PRO_TEMPLATES`、`ADVANCED_STATISTICS`、`LOCAL_BACKUP`、`MULTIPLE_REMINDER_PLANS`、`ADVANCED_EXPORT`、`CLOUD_SYNC`。
  - `EntitlementEntity` 与 `entitlements` 表。
  - `LocalEntitlementChecker` 本地 gating。
  - `recordManualProEntitlement()` 手动 Pro 授权入口，适合作为开发、内测或企业人工授权工具，不应作为公开商业授权主路径。
- Pro 模板基础：
  - 内置免费模板 6 个。
  - 内置 Pro 模板 10 个。
  - Pro 模板选择已在客户端做本地权益拦截。
- 提醒计划基础：
  - `ReminderPlanEntity` 和 `reminder_plans` 表已存在。
  - 已具备多计划商业化的模型基础。
- Feature flags 基础：
  - `FeatureFlagEntity` 与 `feature_flags` 表已存在。
  - 可承接远端开关、灰度、渠道能力差异和付费能力开关。

### 2.4 后端与 Admin 基础已具备

当前 API 已包含：

```text
GET  /api/health
POST /api/v1/auth/email/start
POST /api/v1/auth/email/verify
GET  /api/v1/me
GET  /api/v1/entitlements
POST /api/v1/purchases/google/verify
GET  /api/v1/sync/changes?since=cursor
POST /api/v1/sync/push
POST /api/v1/backups
GET  /api/v1/backups/latest
POST /api/v1/telemetry
POST /api/v1/face-analysis/frames
POST /api/admin/auth/login
POST /api/admin/auth/refresh
GET  /api/admin/me
GET  /api/admin/dashboard
POST /api/admin/actions
```

Admin Dashboard 已具备的管理方向：

- 用户、设备、访问审计、权益变更、Google purchase audit、云备份。
- 崩溃聚合、脱敏堆栈、版本/设备影响、API health、sync throughput。
- 模板 CMS、视觉模板参数、音频/触觉矩阵、i18n 分发、匿名宏观遥测。
- OTA 完整性登记、灰度策略、Rust route topology、HTTP allowlist、安全会话。

## 3. 商业化定位

### 3.1 产品定位

Project Lumen 建议定位为：

> 本地优先、低干扰、可长期使用的用眼健康与专注辅助工具。

商业化不应建立在“限制基础休息提醒”上。基础提醒、基础番茄钟、本地基础统计和核心护眼提醒应保持可长期免费使用，付费点集中在：

- 更强的个性化。
- 更长期的数据洞察。
- 更可靠的多设备能力。
- 更完整的备份、同步和报告能力。
- 团队/企业管理能力。

### 3.2 商业化版本包

#### Free

目标：建立信任和留存，保证核心护眼价值不被付费墙破坏。

建议包含：

- 基础用眼提醒。
- 基础番茄钟。
- 通知、前台服务、开机恢复。
- 静默时段基础配置。
- 今日目标基础展示。
- 最近 7 天或最近 30 天基础统计。
- 免费模板 6 个。
- CSV 导出。
- 本地崩溃报告。
- GitHub 版更新检测。
- 近距离、低光、眨眼提醒作为可选护眼能力，但上传遥测和高级报告必须单独授权。

#### Pro 一次性买断

目标：适合个人用户，维护成本可控，不强迫订阅。

建议包含：

- Pro 模板 10+ 个。
- 自定义模板高级编辑。
- 多提醒计划。
- 高级统计：月报、趋势、完成率、跳过率、最大连续工作分析、番茄趋势。
- 高级导出：统计图片主题、PDF 月报、完整 JSON 备份/恢复。
- 高级声音和震动配置。
- 本地完整备份/恢复。
- 工作模式：工作日、周末、阅读、编程、游戏/观影静默。
- 更细的护眼阈值配置和报告展示。

#### Plus 订阅

目标：只承接存在持续服务成本的能力。

建议包含：

- 云同步。
- 云备份。
- 多设备同步。
- 云端权益恢复。
- 跨设备统计聚合。
- 远端模板库。
- AI/规则混合个性化建议。
- Web 仪表盘个人版。

#### Team / Enterprise

目标：后续 B2B 扩展，不作为下一次个人商业化发布的阻断项。

建议包含：

- 组织成员管理。
- 团队匿名聚合统计。
- 管理员默认提醒策略。
- 企业模板分发。
- 团队月报。
- 企业授权码或私有化部署。
- Admin Dashboard 中的组织、权益、审计、发布和模板管理。

## 4. 下一次商业化发布目标

建议把下一次商业化发布定义为 `1.1 商业化候选版`，目标不是一次性完成 Team/Enterprise，而是完成“个人 Pro/Plus 可真实售卖”的闭环。

### 4.1 发布目标

- 用户可以在客户端看到清晰的 Free/Pro/Plus 功能差异。
- Pro 功能不再依赖手动本地授权。
- 支付后可由服务端真实校验并下发权益。
- 权益可离线缓存，但退款、撤销、过期能被服务端纠正。
- 云同步入口只对 Plus 开放，且必须有账号和隐私同意。
- GitHub 版、Play 版、国内/企业版的更新和支付策略边界清楚。
- 所有验证通过 GitHub Actions 执行。

### 4.2 非目标

- 不在 `1.1` 阶段强行上线 Team/Enterprise。
- 不把基础用眼提醒或基础番茄钟放入付费墙。
- 不默认上传原始摄像头帧。
- 不做医疗诊断、治疗、疾病预防等承诺。
- 不使用本地手动 Pro 作为公开版正式授权方案。

## 5. P0 发布阻断项

### 5.1 真实支付与服务端验签

当前状态：

- 客户端已有本地权益模型和 Pro gating。
- 后端已有 `POST /api/v1/purchases/google/verify`。
- 后端当前在 `LUMEN_ACCEPT_UNVERIFIED_PURCHASES=false` 时会写入 `pending`，不会真正下发 Pro/Plus 权益。
- `LUMEN_ACCEPT_UNVERIFIED_PURCHASES=true` 只适合开发或临时验证，不适合生产。

商业化发布必须新增：

- Android Google Play Billing 接入：
  - 商品 ID：
    - `lumen_pro_lifetime`
    - `lumen_plus_monthly`
    - `lumen_plus_yearly`
  - 购买入口：
    - Pro 模板。
    - 高级统计。
    - 多提醒计划。
    - 本地备份/高级导出。
    - 云同步。
  - 恢复购买入口：
    - 设置页权益卡片。
    - 关于页。
    - 购买失败后的重试路径。
  - 购买完成后提交：
    - `productId`
    - `purchaseToken`
    - `deviceInstallationId`
    - 当前登录用户 access token。
- 后端 Google Play Developer API 验签：
  - 使用服务账号凭据，不把凭据放入客户端。
  - 针对一次性商品校验 purchase state、consumption/acknowledgement、orderId。
  - 针对订阅校验 expiry time、cancel reason、payment state、linked purchase token。
  - 明确退款、撤销、过期、宽限期状态。
  - `accept_unverified_purchases` 在生产必须为 `false`。
- MongoDB 约束：
  - `entitlements.source + entitlements.purchaseToken` 唯一索引。
  - `entitlements.userId + entitlements.productId + entitlements.status` 查询索引。
  - `purchaseToken` 不能被另一个用户重复绑定。
- 权益状态：
  - `active`
  - `pending`
  - `expired`
  - `grace_period`
  - `revoked`
  - `refunded`
- 客户端缓存策略：
  - 支付成功后可以临时解锁 `pending` 能力，但必须在服务端确认后写入 `active`。
  - 网络失败时保留上次 `active` 权益一个有限宽限期。
  - 服务端明确返回 `revoked/refunded/expired` 时降级。

建议后端返回结构保持与现有 `PurchaseVerifyResponse` 兼容，但补充可解释字段：

```json
{
  "status": "active",
  "tier": "PRO",
  "verifiedAt": 1780000000000,
  "entitlement": {},
  "reason": "",
  "expiresAt": 0
}
```

### 5.2 账号与会话落地

当前状态：

- 后端已有邮箱登录 start/verify API。
- Android `ProjectLumenApiClient` 已有 `startEmailLogin()`、`verifyEmailLogin()`、`fetchMe()`。
- 客户端缺少完整账号 UI、session 持久化和登录态驱动的同步/权益流程。
- 后端仍需接入真实邮件发送。

必须新增：

- 客户端账号页：
  - 邮箱输入。
  - 验证码输入。
  - 登录状态。
  - 退出登录。
  - 恢复权益。
  - 云同步开关。
- 客户端 session 存储：
  - 保存 `accessToken`、`expiresAt`、`userId`、`email`。
  - 不把 token 写入 JSON 备份。
  - access token 过期时重新登录或后续补 refresh token。
- 后端邮件：
  - `LUMEN_DEV_LOGIN_CODE` 只用于非生产。
  - 生产使用 SMTP、Resend、SES 或等价邮件服务。
  - 登录验证码限流：同邮箱、同 IP、同设备分别限流。
  - 验证码只存 hash，不存明文。
- 安全策略：
  - 登录请求 TTL 保持短周期。
  - access token TTL 保持可控。
  - Admin token 与用户 token 分离。

### 5.3 完整权益同步

当前状态：

- 后端 `GET /api/v1/entitlements` 已存在。
- 客户端 `fetchEntitlements()` 已存在。
- 本地 `EntitlementRepository.observeTier()` 当前主要依赖 `settings.planTier`。

必须新增：

- `RemoteEntitlementSyncService`：
  - 登录后拉取权益。
  - 购买后拉取权益。
  - App 启动时如果距离 `lastEntitlementSyncAt` 超过 24 小时则拉取。
  - 手动“恢复购买”时强制拉取。
- 本地合并规则：
  - 以服务端 `status` 为准。
  - 永久权益 `expiresAt = 0`。
  - 订阅权益用 `expiresAt` 判断本地有效性，但不可只信客户端时间。
  - `settings.planTier` 由本地 entitlement 列表计算，不应手写为长期真相来源。
- 权益降级：
  - Pro/Plus 功能入口保留，但显示升级或恢复购买。
  - 已创建的 Pro 内容不删除，只限制新增、编辑或启用高级能力。

### 5.4 付费墙与 feature gating 全覆盖

当前状态：

- Pro 模板选择已 gating。
- `PremiumFeature` 已覆盖主要付费能力。
- 备份、高级统计、多提醒计划、高级导出、云同步还需要统一入口 gating。

必须新增：

- 统一 `PaywallState`：
  - `feature: PremiumFeature`
  - `requiredTier: PlanTier`
  - `sourceScreen`
  - `returnAction`
  - `messageKey`
- 统一入口方法：

```kotlin
fun requireFeature(feature: PremiumFeature, onAllowed: () -> Unit)
```

- UI 触发点：
  - 选择 Pro 模板 -> `PRO_TEMPLATES`
  - 查看月报/高级趋势 -> `ADVANCED_STATISTICS`
  - 导出 PDF/主题图片 -> `ADVANCED_EXPORT`
  - 导出/导入完整 JSON 备份 -> `LOCAL_BACKUP`
  - 创建第 2 个及以上提醒计划 -> `MULTIPLE_REMINDER_PLANS`
  - 开启云同步/云备份 -> `CLOUD_SYNC`
- 体验原则：
  - 不打断正在运行的计时。
  - 不用强制全屏拦截基础操作。
  - 购买失败后返回原页面。
  - 免费用户可以看见高级功能入口和示例，但不能误以为已开启。

### 5.5 云同步最小闭环

当前状态：

- 后端已有 `/sync/changes` 和 `/sync/push`。
- 数据模型 `RemoteSyncChange` 已存在。
- 本地表已具备 `updatedAt`、部分 `remoteId`、`deletedAt`。
- 客户端还没有完整同步引擎和 UI。

Plus 发布最低要求：

- 同步集合：
  - `app_settings`
  - `daily_goals`
  - `tip_templates`
  - `reminder_plans`
  - `daily_eye_stats`
  - `daily_pomodoro_stats`
- 不同步：
  - 崩溃报告，除非用户主动提交。
  - 原始摄像头帧。
  - 本地文件路径。
  - 系统权限状态。
  - APK 下载缓存。
- 本地新增：
  - 每个可同步集合维护 `remoteId`、`updatedAt`、`deletedAt`。
  - 单独保存 `syncCursor`、`lastSyncAt`、`lastSyncError`。
  - 同步失败不影响本地提醒计时。
- 冲突规则：
  - 设置：字段级或 `updatedAt` 后写 wins。
  - 模板：`remoteId` 匹配，`deletedAt` 软删除。
  - 提醒计划：`remoteId` 匹配，保留本地禁用状态。
  - 统计：按日增量合并，不直接覆盖。
  - 权益：只从服务端下发，不接受客户端 push。
- 后端补充：
  - `userId + cursor` 索引。
  - `userId + collection + remoteId` 幂等约束。
  - 单次 push changes 数量限制。
  - 单用户每日同步写入限额。

### 5.6 云备份最小闭环

当前状态：

- 本地 JSON 备份/恢复已存在。
- 后端 `/backups` 和 `/backups/latest` 已存在。
- 客户端 `uploadBackup()` 和 `fetchLatestBackup()` 已存在。

Plus 发布最低要求：

- 客户端 UI：
  - 手动上传云备份。
  - 拉取最新云备份。
  - 展示备份时间、设备 ID、schemaVersion。
  - 恢复前复用现有导入摘要。
- 后端：
  - 单用户备份数量上限。
  - 单个备份大小上限。
  - 备份按 `userId + uploadedAt` 查询。
  - 可选保留最近 N 份。
- 隐私：
  - 不上传 access token。
  - 不上传本地文件真实路径。
  - 模板图片如需云端保存，应走对象存储，不塞入 JSON。

### 5.7 生产后端安全

商业发布前必须完成：

- HTTPS：
  - `eye.chloemlla.com` 生产 API 必须使用 HTTPS。
  - Android 生产配置移除对生产域的明文 HTTP 依赖。
  - GitHub 版如仍需要 HTTP 调试，应通过 debug/flavor 区分。
- 配置：
  - `LUMEN_ADMIN_PASSWORD` 不使用默认值。
  - `LUMEN_DEV_LOGIN_CODE` 生产禁用。
  - `LUMEN_ACCEPT_UNVERIFIED_PURCHASES=false`。
  - MongoDB URI 不写入仓库。
- Admin：
  - Admin Dashboard 生产只通过 HTTPS 访问。
  - Admin 操作保留审计。
  - 敏感操作需要二次确认。
  - 后续增加 MFA 或一次性操作码。
- API 防护：
  - 请求体大小限制。
  - 登录、购买校验、同步、遥测限流。
  - CORS allowlist。
  - 统一 request id。
  - 不在错误响应中泄露内部异常。
- MongoDB：
  - 定期备份。
  - 必要索引。
  - TTL 索引用于登录请求、过期 session、短期遥测样本。

### 5.8 隐私、权限与合规

当前权限面较大，商业发布必须把权限解释和用户同意做成产品能力，而不是只写在上架说明里。

需要覆盖的权限：

- `POST_NOTIFICATIONS`：提醒和前台计时通知。
- `SCHEDULE_EXACT_ALARM`：准时触发休息提醒。
- `CAMERA`：近距离检测和眨眼提醒。
- `SYSTEM_ALERT_WINDOW`：强提醒遮罩。
- `WRITE_SETTINGS`：自动亮度。
- `USE_FULL_SCREEN_INTENT`：高优先级休息提醒。
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` / `FOREGROUND_SERVICE_CAMERA`：后台计时、相机采样、光照监测。
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`：降低后台被杀概率。
- `RECEIVE_BOOT_COMPLETED`：开机恢复提醒。
- `INTERNET` / `ACCESS_NETWORK_STATE`：更新、账号、权益、同步、遥测。
- `REQUEST_INSTALL_PACKAGES`：GitHub 版 APK 更新安装。
- `VIBRATE`：触觉反馈。
- `PACKAGE_USAGE_STATS`：如用于上下文判断，必须明确用户授权路径和用途。

必须新增或完善：

- 首次启动权限说明页。
- 设置页“权限与隐私”子页面。
- 遥测单独开关，默认关闭或明确 opt-in。
- 原始摄像头帧上传默认关闭，只允许开发/研究模式或明确同意。
- 隐私政策：
  - 本地保存哪些数据。
  - 上传哪些聚合数据。
  - 不上传哪些数据。
  - 如何删除账号和云端数据。
  - 如何导出本地数据。
  - 支付 SDK 与第三方服务说明。
- 医疗边界：
  - 避免“治疗”“预防疾病”“诊断”等词。
  - 使用“习惯建议”“用眼休息提醒”“减少长时间连续用眼”等表达。

### 5.9 发布渠道策略

必须明确不同渠道的行为：

- GitHub 版：
  - 保留 GitHub Release 更新检测。
  - 保留 APK 下载和 `REQUEST_INSTALL_PACKAGES`。
  - 支付可使用外部授权码或后续自建支付，但不能只靠本地手写权益。
- Google Play 版：
  - 禁用内置 APK 下载更新。
  - 使用 Play 更新入口或商店更新。
  - 使用 Google Play Billing。
  - 根据 Play 政策处理 `REQUEST_INSTALL_PACKAGES` 和高敏权限。
- 国内渠道版：
  - 按渠道要求接入支付、更新和隐私弹窗。
  - 明确渠道包 API base URL 和更新策略。
- 企业版：
  - 可保留内置更新。
  - 可使用企业授权码或私有后端。
  - 可关闭面向个人的 Google Play Billing。

建议新增 product flavor：

```text
github
play
china
enterprise
```

每个 flavor 控制：

- `API_BASE_URL`
- 更新策略。
- 支付策略。
- 是否声明 `REQUEST_INSTALL_PACKAGES`。
- 是否显示 Shizuku/高级调试入口。
- 隐私政策 URL。

## 6. P1 商业价值增强功能

P1 不应阻塞首个商业闭环，但能显著提高 Pro/Plus 付费理由。

### 6.1 商业化首页与权益卡片

新增目标：

- 首页或设置页清楚展示当前版本：Free / Pro / Plus / Team。
- 展示权益有效期、上次同步时间、恢复购买入口。
- 展示“已解锁能力”而不是只展示购买按钮。

技术要点：

- `ProjectLumenUiState` 增加可派生的 `effectiveTier` 或由 UI formatter 计算。
- 权益卡片读取本地 `entitlements` 和 `settings.lastEntitlementSyncAt`。
- 购买入口调用统一 `PaywallState`。
- 网络失败时展示可恢复错误，不阻断本地基础功能。

### 6.2 多提醒计划真正产品化

当前已有 `ReminderPlanEntity`，下一步要把模型变成完整功能。

功能目标：

- 免费版 1 个计划。
- Pro 版无限或更高数量上限。
- 每个计划包含提醒间隔、休息时长、静默时段、启用状态、排序。
- 可快速切换计划。
- 可按工作日/周末/时间段自动启用。

技术要点：

- DAO 增加 active plan 查询和排序更新。
- `RuntimeRepository` 应用计划时重算运行态下一次触发时间。
- `ReminderEngine` 输入应接收当前计划，而不是只读全局 settings。
- 同步时用 `remoteId + updatedAt + deletedAt` 合并。
- 删除计划使用软删除，避免多设备恢复后复活。

### 6.3 远端模板 CMS

当前 Admin Dashboard 已有模板 CMS 方向，客户端已有 `remoteId` 和 `layoutJson`。

功能目标：

- 后端维护模板目录。
- 客户端按语言、版本、渠道拉取模板。
- Pro 模板可远端更新。
- 模板图片走对象存储或固定 CDN。

技术要点：

- 后端新增或完善：

```text
GET /api/v1/templates?locale=zh-CN&channel=play&since=cursor
```

- 模板字段：
  - `remoteId`
  - `name`
  - `tier`
  - `backgroundType`
  - `backgroundValue`
  - `primaryColor`
  - `titleText`
  - `subtitleText`
  - `layoutJson`
  - `assetUrl`
  - `updatedAt`
  - `deletedAt`
- 客户端：
  - 拉取后写入 `tip_templates`。
  - 用户自定义模板不被远端覆盖。
  - Pro 模板选择继续走 `PRO_TEMPLATES` gating。

### 6.4 高级统计与报告

当前已有统计图、PDF 月报和习惯建议基础。

功能目标：

- Free 展示基础统计。
- Pro 展示长期趋势、月报、完成率、跳过率、番茄趋势、最大连续工作分析。
- Plus 可聚合多设备。

技术要点：

- 指标口径固定：
  - 休息完成率 = `completedBreakCount / (completedBreakCount + skipCount)`。
  - 跳过率 = `skipCount / (completedBreakCount + skipCount)`。
  - 最大连续工作 = `maxContinuousWorkSeconds` 最大值。
  - 用眼总时长 = `workingSeconds` 求和。
  - 休息总时长 = `restSeconds` 求和。
- UI gating：
  - 免费用户可见最近 7/30 天基础指标。
  - 月报、PDF、高级趋势走 `ADVANCED_STATISTICS` 或 `ADVANCED_EXPORT`。
- 导出：
  - PDF 继续用 Android `PdfDocument`。
  - PNG 继续用 `Canvas`。
  - 所有文件通过 `FileProvider` 分享，不暴露真实路径。

### 6.5 AI/规则建议

当前本地已有基于统计的习惯建议方向，遥测模型也已存在。

功能目标：

- Free：本地规则建议。
- Pro：更多本地规则和月报结论。
- Plus：云端聚合后的个性化建议。

技术要点：

- 第一阶段不要依赖大模型，先使用规则引擎：
  - 跳过率高于阈值 -> 建议缩短休息时长或调整提醒间隔。
  - 连续工作过长 -> 建议开启强提醒或降低间隔。
  - 低光提醒频繁 -> 建议开启环境光提醒。
  - 近距离提醒频繁 -> 建议重新校准距离。
- 服务端 AI 如后续接入：
  - 只上传聚合统计。
  - 默认不上传原始帧。
  - 输出文案明确为习惯建议。
  - Admin 可审计 prompt version 和建议版本。

### 6.6 远端发布与灰度控制

功能目标：

- Admin 可登记发布版本、SHA256、渠道、灰度比例。
- 客户端按渠道获取更新策略。
- GitHub Release 更新继续保留，但商业版需要更清晰的渠道行为。

技术要点：

- 后端 `admin_releases` 集合：
  - `versionName`
  - `versionCode`
  - `channel`
  - `assetUrl`
  - `sha256`
  - `rolloutPercent`
  - `minSupportedVersionCode`
  - `forceUpdate`
  - `publishedAt`
- 客户端：
  - GitHub flavor 继续读 GitHub Release。
  - 其他 flavor 可读后端发布策略或跳转商店。
  - 校验 SHA256 后再安装。

## 7. P2 后续扩展

### 7.1 Team / Enterprise

功能目标：

- 企业授权。
- 团队匿名聚合报表。
- 默认策略下发。
- 组织模板。
- 管理员审计。

技术要点：

- 新增后端实体：
  - `organizations`
  - `organization_members`
  - `organization_policies`
  - `team_entitlements`
  - `team_reports`
- 客户端：
  - 登录后识别 `TEAM` tier。
  - 拉取组织默认策略。
  - 用户可选择是否上报聚合统计。
- 隐私：
  - 默认只给管理员看聚合数据。
  - 不展示个人具体工作内容。
  - 不上传具体使用 App 列表，除非企业版单独授权且清楚说明。

### 7.2 国内商业化

功能目标：

- 国内支付渠道。
- 国内更新渠道。
- 国内隐私合规弹窗和 SDK 列表。

技术要点：

- 独立 `china` flavor。
- 独立隐私政策 URL。
- 渠道支付 token 仍必须服务端验签。
- 不把“渠道返回成功”直接等同于本地永久 Pro。

### 7.3 企业私有化

功能目标：

- 企业独立 API base URL。
- 私有 MongoDB。
- 私有 Admin Dashboard。
- 企业授权码。

技术要点：

- `enterprise` flavor 允许配置：
  - `PROJECT_LUMEN_API_BASE_URL`
  - 企业 logo 或标题。
  - 是否禁用公共更新。
  - 是否禁用个人支付。
- 后端支持 license import/export。

## 8. 数据与接口技术设计

### 8.1 客户端本地表职责

- `app_settings`：
  - 本地设置、当前计划、权限相关开关、当前缓存 tier。
  - 不应长期作为权益真相来源。
- `entitlements`：
  - 本地权益缓存。
  - 由服务端同步或本地开发授权写入。
- `feature_flags`：
  - 本地和远端功能开关缓存。
- `reminder_plans`：
  - 多提醒计划。
  - Pro 功能核心表。
- `tip_templates`：
  - 内置模板、自定义模板、远端模板。
  - `isPremium`、`remoteId`、`deletedAt` 已适合商业化。
- `daily_eye_stats` / `daily_pomodoro_stats`：
  - 按日聚合统计。
  - 同步时按日期和设备合并。
- `runtime_state`：
  - 当前计时真相来源。
  - 不建议直接同步。

### 8.2 服务端集合职责

生产建议集合：

```text
users
login_requests
sessions
entitlements
sync_changes
backups
telemetry_uploads
face_analysis_frames
counters
admin_sessions
admin_actions
admin_access_audit
admin_crash_reports
admin_api_metrics
admin_sync_metrics
admin_templates
admin_telemetry
admin_releases
admin_security_allowlist
```

商业化发布必须重点补齐的索引：

```text
users.email unique
sessions.accessTokenHash unique / ttl
login_requests.expiresAt ttl
entitlements.source + entitlements.purchaseToken unique
entitlements.userId + entitlements.status
sync_changes.userId + sync_changes.cursor
sync_changes.userId + sync_changes.change.collection + sync_changes.change.remoteId
backups.userId + backups.uploadedAt
admin_actions.createdAt
telemetry_uploads.receivedAt ttl
face_analysis_frames.receivedAt ttl
```

### 8.3 客户端新增服务建议

建议新增或补齐以下客户端组件：

```text
AuthSessionRepository
RemoteEntitlementSyncService
PurchaseRepository
SyncRepository
CloudBackupRepository
PaywallCoordinator
RemoteFeatureFlagRepository
RemoteTemplateRepository
CommercialReleaseConfig
```

职责边界：

- `AuthSessionRepository`：只管登录态和 token，不做权益判断。
- `PurchaseRepository`：只管购买、恢复和提交服务端验签。
- `RemoteEntitlementSyncService`：把服务端权益写入本地 `entitlements`，并计算当前 tier。
- `PaywallCoordinator`：只判断入口是否允许和应展示哪个付费墙。
- `SyncRepository`：只负责 changes push/pull 和 cursor。
- `CloudBackupRepository`：复用本地 `DataBackupService` 生成 JSON，再上传后端。
- `RemoteFeatureFlagRepository`：合并后端 flag 和本地 flag。
- `RemoteTemplateRepository`：拉取远端模板并写入 `tip_templates`。

### 8.4 后端新增服务建议

建议新增或补齐：

```text
GooglePlayVerifier
EntitlementResolver
EmailDeliveryService
RateLimiter
SyncCompactor
BackupRetentionService
AdminReleaseService
TemplateCatalogService
AuditLogger
```

职责边界：

- `GooglePlayVerifier`：封装 Google API，不暴露给路由。
- `EntitlementResolver`：统一从 entitlement records 计算 `FREE/PRO/PLUS/TEAM`。
- `EmailDeliveryService`：生产发送验证码，开发可保留 dev delivery。
- `RateLimiter`：登录、购买、同步、遥测分别限流。
- `SyncCompactor`：后续可压缩同一 remoteId 的历史 changes。
- `BackupRetentionService`：控制云备份数量和大小。
- `AuditLogger`：Admin 和敏感用户操作统一审计。

## 9. 质量与验证计划

所有命令必须在 GitHub Actions 中执行，本地不执行。

### 9.1 Android 验证

现有 workflow 已执行：

```text
gradle testDebugUnitTest --no-daemon
gradle lintDebug --no-daemon
gradle assembleRelease --no-daemon
```

商业化新增后建议增加：

- Billing 相关单元测试：
  - 商品 ID 到 tier 映射。
  - pending/active/expired/revoked 状态处理。
  - restore purchases 合并规则。
- Paywall 测试：
  - Free 无法使用 Pro 模板。
  - Pro 可用高级统计、本地备份、多计划和高级导出。
  - Plus 可用云同步。
- 同步测试：
  - 设置后写 wins。
  - 统计按日合并。
  - 模板软删除。
  - cursor 推进。
- Room migration 测试：
  - 版本 13 到下一版本。
  - 权益和计划表迁移。
  - release 构建不走 destructive migration。
- 权限回归测试：
  - 未授权相机时近距离功能不崩溃。
  - 未授权悬浮窗时强提醒遮罩有引导。
  - 未授权精确闹钟时 UI 展示延迟风险。

### 9.2 Rust 后端验证

现有 workflow 已执行：

```text
cargo fmt --manifest-path backend/Cargo.toml --all -- --check
cargo test --manifest-path backend/Cargo.toml --all-targets
```

商业化新增后建议增加：

- Google purchase verifier mock 测试。
- Entitlement resolver 测试。
- Token hash 和 session TTL 测试。
- Sync push idempotency 测试。
- Backup size/retention 测试。
- Admin audit 测试。
- Rate limit 测试。

### 9.3 发布产物验证

必须保留：

- Universal APK。
- ABI split APK。
- `checksums.txt`。
- GitHub Release 生成 release notes。

商业化新增：

- 每个 flavor 单独构建产物。
- Play 版不包含内置 APK 安装入口。
- GitHub 版保留 SHA256 校验更新。
- Release note 中标明隐私、权限、支付和同步变化。

## 10. 推荐实施顺序

### Phase 1：商业化基础闭环

目标：Pro/Plus 能真实售卖，权益可信。

任务：

1. 接入账号 UI 和 session 存储。
2. 接入真实邮件发送和登录限流。
3. 接入 Google Play Billing 客户端。
4. 后端实现 Google Play Developer API 验签。
5. 本地 entitlement 由服务端快照计算 tier。
6. 统一 Paywall 和 `PremiumFeature` gating。
7. 禁止 release 版暴露手动 Pro 入口。

验收：

- 用户登录后能购买 Pro。
- 后端验签后返回 `active/PRO`。
- 客户端重启后仍能使用 Pro。
- 退款/撤销后服务端可降级。
- Free 用户不能绕过 Pro 模板和高级入口。

### Phase 2：Plus 云能力闭环

目标：Plus 订阅有持续服务价值。

任务：

1. 实现同步 cursor 本地存储。
2. 实现 settings/templates/goals/plans/stats 的 push/pull。
3. 实现云备份上传和恢复 UI。
4. 处理同步冲突和离线失败。
5. Admin Dashboard 展示同步和备份指标。

验收：

- 两台设备登录同一账号后可同步设置和模板。
- 按日统计不会互相覆盖。
- 云备份恢复前有摘要。
- 网络失败不影响本地提醒。

### Phase 3：渠道与合规发布

目标：能面向公开用户发布。

任务：

1. 增加 flavor：`github`、`play`、`china`、`enterprise`。
2. 生产 API 全面切 HTTPS。
3. 编写隐私政策、用户协议、权限说明。
4. 设置页加入权限与隐私中心。
5. Play 版禁用内置 APK 更新安装。
6. GitHub 版保留 Release 更新。

验收：

- 不同 flavor 权限和更新行为正确。
- 隐私与权限说明覆盖所有敏感能力。
- 生产配置不使用 dev code 或 unverified purchases。

### Phase 4：商业价值增强

目标：提高转化和留存。

任务：

1. 多提醒计划完整 UI。
2. 远端模板 CMS。
3. 高级统计和 Pro 月报优化。
4. 本地规则建议增强。
5. 远端 feature flags 和灰度。

验收：

- Pro 付费点清晰且不伤害基础体验。
- Plus 用户能看到同步和备份价值。
- Admin 能观察版本、同步、权益和模板状态。

## 11. 商业化发布检查清单

发布前必须确认：

- [ ] 生产 API 使用 HTTPS。
- [ ] 生产 `LUMEN_ACCEPT_UNVERIFIED_PURCHASES=false`。
- [ ] 生产禁用固定 dev login code。
- [ ] Google Play Billing 客户端完成。
- [ ] Google Play Developer API 服务端验签完成。
- [ ] Entitlement token 去重和撤销逻辑完成。
- [ ] Paywall 覆盖所有 `PremiumFeature`。
- [ ] release 版隐藏或禁用手动 Pro 授权入口。
- [ ] 账号 UI、登录态和退出登录完成。
- [ ] 恢复购买完成。
- [ ] 隐私政策和用户协议完成。
- [ ] 权限说明页完成。
- [ ] 遥测和原始帧上传默认受用户同意控制。
- [ ] 云同步不会影响本地计时。
- [ ] 云备份恢复前展示摘要。
- [ ] GitHub Actions 通过 Android 和 Rust 验证。
- [ ] Release assets 生成 SHA256。
- [ ] Play/GitHub/国内/企业渠道行为区分完成。

## 12. 最优先的下一步

最推荐先做以下 5 件事：

1. 把账号 UI、session 存储、权益同步接上现有 API。
2. 把 `PremiumFeature` gating 从模板扩展到备份、高级统计、多提醒计划、高级导出和云同步。
3. 实现后端真实 Google Play 验签，保持生产 `accept_unverified_purchases=false`。
4. 增加商业化 flavor 和更新策略差异，避免 Play 版继续内置 APK 安装。
5. 补齐隐私、权限、遥测同意和生产 HTTPS。

完成这 5 件事后，Project Lumen 才具备对外销售 Pro/Plus 的基本可信闭环；Team/Enterprise、远端模板 CMS 和 AI 建议可以随后作为增长功能继续推进。
