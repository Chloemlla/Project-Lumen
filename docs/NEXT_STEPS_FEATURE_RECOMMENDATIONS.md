# Project Lumen 下一步功能与商业化实现建议

本文档基于当前仓库状态整理，面向后续产品规划、功能拆分、商业化落地和技术实现。当前项目已经是 Android 原生 Kotlin 应用，核心栈包括 Jetpack Compose、Room、AlarmManager、前台服务、本地通知、CSV/图片分享、模板配置、崩溃报告和 GitHub Release 更新检测。

> 仓库约束：实际构建、测试、Lint、安装相关命令只允许在 GitHub Actions 工作流中执行；本地只做代码与文档修改。

## 0. 2026-06-30 实施进度归档

本次归档以当前 Android 仓库可直接落地的客户端功能为边界；涉及 Google Play 后台、服务端、账号、云同步、团队后台、Web 仪表盘和真实支付校验的事项已保留客户端模型或接口，但不标记为完全完成。

### 0.1 已完成

- [x] `SettingsRepository`、`RuntimeRepository`、`StatisticsRepository`、`TipTemplateRepository`、`ReminderEngine`、`PomodoroEngine` 已接入主流程。
- [x] 核心状态机 JVM 测试已纳入 GitHub Actions，新增静默时段与最大连续工作统计用例。
- [x] Room 正式版不再使用破坏性迁移兜底；新增版本 9 迁移，包含权益、目标、静默时段、模板同步字段、feature flags、提醒计划、护眼增强和统计字段。
- [x] GitHub Actions 增加远端 `lintDebug` 和测试/lint 报告上传；本地仍不执行构建、测试、安装。
- [x] 启动、前台服务、通知动作和闹钟触发路径已接入静默时段判断。
- [x] 新增静默时段：`pause_timer`、`silent_notifications`、`record_only`，支持跨午夜。
- [x] 新增今日目标：每日休息次数、最大连续工作时长、每日番茄数、每周活跃天数，并在首页展示进度。
- [x] 统计页升级：最近 7 天、30 天、本月筛选，展示总工作/休息、休息完成率、跳过率、平均连续工作、番茄数。
- [x] 本地规则个性化建议：根据近 14 天跳过率、连续工作、低光照和眨眼提醒生成非医疗化习惯建议。
- [x] 模板系统增强：免费/Pro 模板分层，内置 Pro 模板扩展到 10 个以上，Pro 模板入口已做本地权益 gating。
- [x] 模板编辑增强：支持休息标题、副标题、跳过按钮显示和圆环/进度条/数字倒计时样式配置。
- [x] 音频与触觉增强：休息开始音、休息结束音、预提醒音、番茄开始/结束音量分别配置，支持震动反馈和自定义音频 URI 持久化。
- [x] 崩溃报告增强：复制、分享、基础路径/URI 脱敏。
- [x] 本地 JSON 备份/恢复：导出设置、目标、模板、统计、权益、feature flags、提醒计划；导入前展示摘要，导入时覆盖设置并合并统计。
- [x] 高级导出：支持 CSV、统计图片和本地 PDF 月报分享。
- [x] 本地权益模型：`planTier`、`entitlements`、`EntitlementChecker`、`PremiumFeature`、Pro 模板 gating。
- [x] 更新完整性校验：Release `checksums.txt` 生成、客户端 SHA256 解析与下载后比对已存在。
- [x] 版本号策略：workflow 使用 `GITHUB_RUN_NUMBER` 作为单调递增 `versionCode`。
- [x] 护眼增强能力已接入设置入口：近距离、眨眼、低光照、自动亮度、强提醒遮罩。

### 0.2 已建模但仍需外部系统

- [ ] Google Play Billing / 国内渠道支付：客户端已有权益表和 gating，真实购买、恢复购买、退款/撤销校验需要支付 SDK 与服务端或渠道后台。
- [ ] 服务端权益校验：客户端可缓存权益，但 token 校验、宽限期策略和撤销状态需要服务端 API。
- [ ] 账号系统、云同步、云备份、多设备同步：当前仅有本地 JSON 备份和可同步字段；服务端、鉴权和冲突合并 API 仍未实现。
- [ ] 服务端 AI 个性化建议：本地规则建议已完成；如后续接入服务端 AI，仍需服务端、鉴权、隐私策略和聚合统计 API。
- [ ] 团队版/企业版/Web 仪表盘：属于后续外部产品与后端范围，本仓库未实现。

## 1. 当前能力盘点

### 1.1 已有产品功能

- 用眼提醒：支持工作计时、预提醒、正式提醒、开始休息、跳过休息、暂停 1 小时、手动恢复、停止。
- 休息页：支持模板背景、主色、文案、倒计时展示、开始休息/跳过交互。
- 番茄钟：支持专注、短休息、长休息、轮次推进、开始/停止。
- 统计：支持用眼统计、番茄统计、近 7/30 天与月度筛选、关键指标、CSV 分享、统计图片分享。
- 设置：支持语言、主题、提醒间隔、休息时长、预提醒、静默时段、目标、声音、震动、自定义音频路径、通知开关、自动更新检查、备份恢复。
- 模板：支持内置模板、系统主题色模板、自定义图片路径、免费/Pro 分层。
- 通知：支持通知渠道、精确闹钟能力检测、Android 13+ 通知权限引导、前台服务常驻状态通知。
- 更新：支持 GitHub Release 最新版本检测、语义版本与发布时间双判定、ABI APK 选择、下载进度、未知来源安装授权跳转。
- 崩溃报告：本地保存崩溃信息，启动后展示崩溃详情页，支持复制、分享和基础脱敏。
- 数据备份：支持本地 JSON 备份导出/导入，导入前展示摘要，统计按日期合并。
- 护眼增强：支持近距离、眨眼、低光照、自动亮度和严格遮罩提醒。
- 国际化：已有中文、英文和系统语言逻辑。

### 1.2 已有技术结构

- `ProjectLumenViewModel` 当前承担主要状态机、设置写入、统计聚合、通知刷新和导出入口。
- `AppDatabase` 使用 Room，当前版本为 9，包含设置、运行态、用眼统计、番茄统计、模板、目标、权益、feature flags、提醒计划表。
- `RuntimeStateEntity` 是计时恢复真相来源，包含普通提醒和番茄钟阶段时间戳。
- `NotificationService` 负责闹钟调度、通知展示、通知动作和前台服务通知构建。
- `ExportService` 负责 CSV 与 Canvas 统计图分享。
- `UpdateChecker` 与 `UpdateInstaller` 负责更新检测和 APK 下载/安装。
- GitHub Actions 已有 `build.yml`、`release.yml`、`codeql.yml`。

## 2. 产品定位建议

Project Lumen 适合定位为“轻量但可靠的数字健康与专注辅助工具”，核心卖点不应只是倒计时，而是：

- 低干扰的用眼健康提醒。
- 可证明可靠的后台计时与恢复。
- 可视化习惯数据。
- 个人工作流可定制。
- 面向长期使用的无广告体验。

优先级上，先把“可靠计时 + 统计可信 + 设置完整 + 更新稳定”打磨成基础口碑，再接商业化。过早加入复杂账号、云同步或订阅墙，会增加维护成本并稀释核心体验。

## 3. 下一步功能路线图

### P0：稳定性与架构基础

目标：降低核心计时和数据统计的回归风险，为后续商业化能力铺底。

#### 3.1 拆分 ViewModel 职责

现状：`ProjectLumenViewModel` 已经承担大量职责，继续叠加功能会让状态机难以测试。

建议拆分：

- `SettingsRepository`
  - 封装 `AppSettingsDao`。
  - 暴露 `Flow<AppSettingsEntity>`。
  - 提供 `update(transform)`、`setLanguageCode()`、`setThemeMode()`。
- `RuntimeRepository`
  - 封装 `RuntimeStateDao`。
  - 提供状态机写入的单点入口。
- `ReminderEngine`
  - 负责普通提醒状态转移。
  - 输入：`settings`、`runtime`、`nowMillis`、用户动作。
  - 输出：`RuntimeTransition`，包含新状态、统计增量、通知计划、音频事件。
- `PomodoroEngine`
  - 负责番茄钟状态转移。
- `StatisticsRepository`
  - 统一统计聚合、日期键、导出数据读取。
- `NotificationScheduler`
  - 封装 `NotificationService` 中和调度有关的能力。

推荐数据结构：

```kotlin
data class RuntimeTransition(
    val nextRuntime: RuntimeStateEntity,
    val eyeStatsDelta: EyeStatsDelta = EyeStatsDelta(),
    val pomodoroStatsDelta: PomodoroStatsDelta = PomodoroStatsDelta(),
    val notificationPlan: NotificationPlan = NotificationPlan.None,
    val audioEvent: AudioEvent = AudioEvent.None,
)
```

收益：

- 状态机可写纯 Kotlin 单元测试。
- UI 只消费状态，不关心调度细节。
- 商业化功能可以通过权限/权益层包裹入口，而不侵入核心状态机。

#### 3.2 建立核心状态机测试矩阵

测试只在 GitHub Actions 中执行。建议新增 JVM 单元测试，覆盖：

- 普通提醒：
  - `WORKING -> PRE_ALERT`
  - `WORKING/PRE_ALERT -> AWAITING_ACTION`
  - `AWAITING_ACTION -> RESTING`
  - `AWAITING_ACTION -> WORKING` 跳过
  - `RESTING -> WORKING`
  - `PAUSED -> WORKING` 到期恢复
  - 关闭预提醒时不进入 `PRE_ALERT`
  - 修改间隔/休息时长后运行态重新对齐
- 番茄钟：
  - 第 1-3 轮 `FOCUS -> SHORT_BREAK`
  - 第 4 轮 `FOCUS -> LONG_BREAK`
  - 长休息结束后轮次重置
  - 中途停止统计 `restartCount`
- 统计：
  - 跨天场景。
  - 离线恢复补记时长。
  - `statsEnabled = false` 时不写统计。
- 更新检测：
  - 语义版本高低比较。
  - 相同版本但发布时间更新。
  - ABI 资产匹配和 universal 回退。

#### 3.3 Room 迁移策略收紧

当前 `AppDatabase` 使用 `fallbackToDestructiveMigration(dropAllTables = true)`。在正式商业化前应移除或仅限 debug 使用，否则升级可能清空用户数据。

建议：

- 保留显式 `Migration`。
- 新增 schema 导出：`exportSchema = true`。
- 在仓库中提交 Room schema JSON。
- 为每次新增字段写迁移。
- 对关键表新增 `createdAt`、`updatedAt`、`deletedAt`，为后续云同步做准备。

建议新增字段：

- `app_settings`
  - `planTier TEXT NOT NULL DEFAULT 'free'`
  - `entitlementExpiresAt INTEGER NOT NULL DEFAULT 0`
  - `lastEntitlementSyncAt INTEGER NOT NULL DEFAULT 0`
- `tip_templates`
  - `isPremium INTEGER NOT NULL DEFAULT 0`
  - `remoteId TEXT NOT NULL DEFAULT ''`
  - `deletedAt INTEGER NOT NULL DEFAULT 0`
- 新表 `feature_flags`
  - `key TEXT PRIMARY KEY`
  - `enabled INTEGER NOT NULL`
  - `payloadJson TEXT NOT NULL DEFAULT ''`
  - `updatedAt INTEGER NOT NULL`

#### 3.4 后台计时可靠性增强

当前使用 AlarmManager + 前台服务是正确方向。下一步建议：

- 启动时统一调用 `restoreFromClock()` 后再刷新通知。
- `BootReceiver` 中不要直接做复杂逻辑，只启动轻量恢复任务或打开前台服务。
- 对 `SCHEDULE_EXACT_ALARM` 不可用的设备，UI 明确显示“可能延迟”状态。
- 对厂商后台限制提供系统设置入口，例如电池优化白名单。
- 增加 `lastForegroundAt`、`lastBackgroundAt` 写入逻辑，辅助恢复诊断。

#### 3.5 崩溃报告可操作化

现状是本地崩溃详情页。建议补充：

- 一键复制崩溃报告。
- 一键分享崩溃报告到邮件/反馈渠道。
- 崩溃报告脱敏：
  - 不上传文件路径中的用户名。
  - 不上传自定义音频或图片 URI。
  - 不包含完整设备唯一标识。
- 后续如接入服务端，采用用户明确同意后上传。

## 4. P1：核心体验增强

### 4.1 自定义静默时段

已有 `useAutoDarkWindow` 字段，但更需要“提醒静默时段”。

新增设置字段：

- `quietHoursEnabled Boolean`
- `quietStartMinute Int`
- `quietEndMinute Int`
- `quietMode TEXT`：`pause_timer` / `silent_notifications` / `record_only`

行为：

- `pause_timer`：静默时段内不累计工作提醒，结束后重新开始一轮。
- `silent_notifications`：继续计时和统计，但不弹高优先级通知。
- `record_only`：只记录工作时长，不触发休息。

技术点：

- 在 `ReminderEngine` 计算下一次提醒时跳过静默区间。
- 跨午夜处理：`start > end` 表示跨天。
- UI 使用两个时间选择器，不使用自由文本输入。

### 4.2 今日目标与健康达成

新增目标：

- 每日休息次数目标。
- 每日最大连续工作时长目标。
- 每日番茄数目标。
- 每周专注天数目标。

新增表 `daily_goals`：

```sql
CREATE TABLE daily_goals (
  id INTEGER PRIMARY KEY,
  restBreakGoal INTEGER NOT NULL DEFAULT 8,
  maxContinuousWorkMinutes INTEGER NOT NULL DEFAULT 45,
  pomodoroGoal INTEGER NOT NULL DEFAULT 4,
  weeklyActiveDaysGoal INTEGER NOT NULL DEFAULT 5,
  updatedAt INTEGER NOT NULL
);
```

首页展示：

- 今日目标进度。
- 最大连续工作时长警告。
- 本周连续达成天数。

### 4.3 统计页升级

当前统计较基础。建议升级为：

- 日/周/月三个 Tab。
- 趋势图：
  - 工作分钟。
  - 休息分钟。
  - 跳过次数。
  - 番茄数。
- 指标卡：
  - 总工作时长。
  - 总休息时长。
  - 休息完成率。
  - 跳过率。
  - 平均连续工作时长。
- 可视化应优先使用 Compose Canvas 或引入轻量图表库。

数据口径：

- 休息完成率 = `completedBreakCount / (completedBreakCount + skipCount)`。
- 番茄完成率后续需要记录计划数，否则不要展示。
- “健康评分”不要做医学承诺，只做习惯完成度。

### 4.4 模板系统增强

现状已有模板和图片路径。下一步：

- 模板编辑：
  - 背景：系统色、纯色、图片。
  - 主色。
  - 标题文案。
  - 副标题文案。
  - 按钮显示。
  - 倒计时样式：圆环、进度条、数字。
- 模板导入/导出：
  - 使用 JSON。
  - 图片使用 SAF URI 或本地复制缓存。
- 内置模板分层：
  - 免费模板 3-5 个。
  - Pro 模板 10+ 个。

建议 `layoutJson` 结构：

```json
{
  "countdownStyle": "circle",
  "titleSizeSp": 28,
  "subtitleSizeSp": 16,
  "buttonStyle": "filled",
  "showIllustration": true,
  "safeAreaPaddingDp": 24
}
```

### 4.5 音频与触觉反馈

新增能力：

- 内置音效包。
- 自定义音频试听。
- 震动提醒开关。
- 分场景音量：
  - 预提醒。
  - 休息开始。
  - 休息结束。
  - 番茄开始/结束。

Android 技术点：

- 自定义音频保留 URI 权限：使用 `takePersistableUriPermission()`。
- 音频播放统一由 `AudioService` 管理，避免多个 `MediaPlayer` 泄漏。
- 震动使用 `VibratorManager`，Android 12+ 和低版本分支处理。

## 5. P2：专业版与商业化功能

商业化建议采用“免费可长期用 + Pro 提供效率和个性化增强”的模式。不要把基础用眼提醒、休息、番茄钟放入付费墙。

### 5.1 免费版功能

- 基础用眼提醒。
- 基础番茄钟。
- 最近 7 天统计。
- 3-5 个基础模板。
- CSV 导出。
- GitHub Release 更新检测。

### 5.2 Pro 一次性买断功能

适合个人工具，用户接受度高，维护成本低。

建议 Pro 功能：

- 无限模板与高级模板。
- 高级统计：月/年趋势、完成率、跳过率、连续达成。
- 统计图片高级主题。
- 自定义多个提醒计划。
- 多套工作模式：
  - 工作日模式。
  - 周末模式。
  - 阅读模式。
  - 编程模式。
  - 游戏/观影静默模式。
- 高级声音包与震动模式。
- 数据备份/恢复到本地文件。
- 主题包。

### 5.3 订阅功能

只有在加入服务端能力后才建议订阅。

可订阅功能：

- 云同步。
- 多设备同步。
- Web 仪表盘。
- 自动云备份。
- AI 个性化建议。
- 团队/家庭健康报表。

如果没有服务端持续成本，不建议强行做订阅。

### 5.4 团队版/企业版

适合后续扩展，不建议第一阶段实现。

功能：

- 团队健康挑战。
- 团队匿名统计。
- 管理员配置默认提醒策略。
- 公司统一模板。
- 导出团队月报。
- 企业私有化部署或组织授权码。

隐私边界：

- 默认只上传聚合数据。
- 不上传具体使用 App、具体工作内容、精确位置。
- 团队报表隐藏个人明细，除非用户明确选择加入。

## 6. 支付与权益系统技术方案

### 6.1 Android 支付通道

海外/Google Play：

- 使用 Google Play Billing Library。
- 产品类型：
  - `lumen_pro_lifetime`：一次性买断。
  - `lumen_plus_monthly`：月订阅。
  - `lumen_plus_yearly`：年订阅。

国内分发：

- 如果不走 Google Play，需要单独接入渠道支付或自建订单。
- 自建支付必须有服务端校验，不要只在客户端本地写入 Pro 状态。

### 6.2 本地权益模型

新增表 `entitlements`：

```sql
CREATE TABLE entitlements (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  source TEXT NOT NULL,
  productId TEXT NOT NULL,
  purchaseToken TEXT NOT NULL DEFAULT '',
  tier TEXT NOT NULL,
  status TEXT NOT NULL,
  purchasedAt INTEGER NOT NULL,
  expiresAt INTEGER NOT NULL DEFAULT 0,
  lastVerifiedAt INTEGER NOT NULL DEFAULT 0,
  rawPayloadJson TEXT NOT NULL DEFAULT ''
);
```

字段说明：

- `source`：`google_play` / `manual_license` / `server`。
- `tier`：`free` / `pro` / `plus` / `team`。
- `status`：`active` / `expired` / `grace_period` / `revoked` / `pending`。
- `expiresAt = 0` 表示永久授权。

应用内统一使用：

```kotlin
interface EntitlementChecker {
    fun observeTier(): Flow<PlanTier>
    fun canUse(feature: PremiumFeature): Boolean
}
```

### 6.3 权益校验策略

- 客户端可缓存权益，保证离线可用。
- 购买后立即本地解锁，但后台必须校验购买 token。
- 周期性校验：
  - App 启动。
  - 手动恢复购买。
  - 距上次校验超过 24 小时。
- 校验失败处理：
  - 网络失败：保留上次权益一段宽限期。
  - 明确撤销/退款：降级。

### 6.4 付费墙设计

触发点：

- 用户选择 Pro 模板。
- 用户进入高级统计。
- 用户创建超过免费数量的计划。
- 用户开启云同步。

原则：

- 不打断计时。
- 不用全屏强制弹窗。
- 清楚列出当前操作为什么需要 Pro。
- 购买失败后返回原页面。

## 7. 云同步与账号系统

云同步不是第一阶段必须项，但如果做订阅，必须规划。

### 7.1 账号方案

推荐分阶段：

- V1：无账号，本地优先。
- V2：邮箱验证码登录。
- V3：第三方登录，如 Google。

避免一开始实现密码体系，减少安全维护成本。

### 7.2 服务端建议

轻量方案：

- API：Ktor / Spring Boot / Node.js 任一稳定框架。
- 数据库：PostgreSQL。
- 对象存储：用于模板图片和备份文件。
- 鉴权：JWT + refresh token。
- 部署：Docker + GitHub Actions。

核心 API：

```text
POST /v1/auth/email/start
POST /v1/auth/email/verify
GET  /v1/me
GET  /v1/entitlements
POST /v1/purchases/google/verify
GET  /v1/sync/changes?since=cursor
POST /v1/sync/push
POST /v1/backups
GET  /v1/backups/latest
```

### 7.3 同步数据范围

建议同步：

- 设置。
- 模板。
- 目标。
- 统计聚合。
- 权益状态。

不建议同步：

- 崩溃报告，除非用户主动上传。
- 本地文件原路径。
- 系统通知权限状态。
- APK 更新缓存。

### 7.4 冲突处理

设置类：

- 使用 `updatedAt` 后写 wins。
- 对每个设置项可采用字段级合并。

统计类：

- 使用按日聚合增量，不用简单覆盖。
- 服务端保存 `deviceId + statDate` 明细，再聚合成用户统计。

模板类：

- `remoteId` 标识云端模板。
- `deletedAt` 支持软删除。
- 图片上传后保存远端 URL，本地保留缓存 URI。

### 7.5 本地设备标识

不要使用不可重置硬件 ID。生成随机 UUID 存本地：

```text
device_installation_id = UUID.randomUUID()
```

卸载后可重置，符合隐私预期。

## 8. AI 个性化建议

这适合作为 Plus 订阅功能，但要谨慎表述，避免医疗建议。

功能：

- 根据近 14 天跳过率建议调整提醒间隔。
- 根据休息完成率建议降低/提高休息时长。
- 根据番茄完成时间段建议推荐工作节奏。
- 生成周报总结。

本地优先方案：

- 先使用规则引擎，不接大模型。
- 规则示例：
  - 跳过率 > 50% 且休息完成率 < 40%，建议把休息时长从 20 秒降到 15 秒。
  - 连续 3 天工作时长高但休息少，建议开启强提醒。

后续服务端 AI：

- 上传聚合统计，不上传明细行为。
- 文案明确“习惯建议，不构成医疗建议”。

## 9. 数据备份与导入导出

### 9.1 本地备份

Pro 功能，优先级高于云同步。

导出格式：

```json
{
  "schemaVersion": 1,
  "exportedAt": 1780000000000,
  "settings": {},
  "templates": [],
  "dailyEyeStats": [],
  "dailyPomodoroStats": []
}
```

技术点：

- 使用 SAF 选择保存位置。
- 导入前展示摘要：
  - 统计天数。
  - 模板数量。
  - 设置是否覆盖。
- 导入模式：
  - 合并统计。
  - 覆盖设置。
  - 跳过已有模板或重命名。

### 9.2 导出格式增强

现有 CSV 可保留。新增：

- JSON 完整备份。
- PNG 周报。
- PDF 月报，适合团队版或高级统计。

## 10. 更新与发布体系增强

当前 GitHub Release 更新检测已可用。下一步建议：

### 10.1 APK 完整性校验

Release body 或独立 `checksums.txt` 提供 SHA256。

客户端下载后：

- 计算 APK SHA256。
- 与 Release 中声明值比对。
- 不匹配则阻止安装并提示。

### 10.2 渠道更新策略

- GitHub 版：使用现有 Release 检查。
- Google Play 版：不建议内置 APK 下载，改用 Play 更新入口。
- 国内渠道版：按渠道规则处理更新。

可通过 build flavor 区分：

- `github`
- `play`
- `fdroid`
- `enterprise`

### 10.3 版本号策略

当前 workflow 的 `VERSION_CODE=1` 固定。正式发布前应改为单调递增：

- 使用 GitHub run number。
- 或从 tag 生成。
- 或维护 `version.properties`。

示例：

```text
versionName = 1.4.0
versionCode = 10400
```

## 11. 隐私、合规与风控

### 11.1 隐私政策必须覆盖

- 本地保存哪些数据。
- 是否上传统计。
- 是否上传崩溃。
- 是否使用支付 SDK。
- 是否使用更新检查网络请求。
- 如何删除数据。
- 如何导出数据。

### 11.2 权限解释

当前 Manifest 包含：

- `POST_NOTIFICATIONS`
- `SCHEDULE_EXACT_ALARM`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_SPECIAL_USE`
- `RECEIVE_BOOT_COMPLETED`
- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `REQUEST_INSTALL_PACKAGES`

建议在设置页提供权限说明页：

- 通知权限：用于提醒和前台计时。
- 精确闹钟：用于更准时的休息提醒。
- 开机启动：用于恢复提醒计划。
- 网络：用于检查更新，未来用于云同步/支付校验。
- 安装包权限：仅 GitHub 版用于安装更新 APK。

### 11.3 医疗合规边界

避免使用“治疗”“预防疾病”“医学诊断”等表述。

推荐表述：

- “帮助建立休息习惯”
- “减少长时间连续用眼”
- “提醒你定期休息”

## 12. UI/UX 下一步优化

### 12.1 首页

建议信息层级：

- 当前状态：工作中/预提醒/等待操作/休息中/番茄中。
- 下一次关键时间。
- 主操作按钮：开始/暂停/恢复/停止。
- 今日摘要。
- 权限状态提醒，只在缺失时显示。

### 12.2 设置页

当前设置项越来越多，建议拆分为子页面：

- 通用。
- 提醒。
- 番茄钟。
- 通知与权限。
- 声音与触觉。
- 外观与模板。
- 数据与导出。
- 关于与更新。

### 12.3 高级统计页

建议新增筛选：

- 最近 7 天。
- 最近 30 天。
- 本月。
- 自定义范围。

避免堆叠过多卡片，优先以图表和少量关键指标呈现。

## 13. 技术债清单

优先处理：

- `ProjectLumenViewModel` 过大，应拆分状态机和仓储。
- `ProjectLumenApp.kt` 单文件 UI 过大，应拆分 screen/component。
- 移除正式版 destructive migration。
- 版本号 `versionCode` 固定问题。
- 自定义 URI 权限持久化。
- 更新 APK 缺少 SHA256 校验。
- 通知动作与状态恢复需要更多测试。

中期处理：

- 引入依赖注入，Hilt 或手写轻量容器均可。
- 引入 feature package：
  - `feature_home`
  - `feature_reminder`
  - `feature_pomodoro`
  - `feature_statistics`
  - `feature_settings`
  - `feature_templates`
  - `feature_update`
  - `feature_billing`
- 为导出、更新、音频、通知增加接口，方便测试替身。

## 14. 推荐里程碑

### Milestone 1：稳定版 1.0

- 状态机拆分。
- 核心测试矩阵。
- 移除正式 destructive migration。
- 设置页拆分。
- 权限说明页。
- 更新完整性校验。
- 修正 `versionCode` 策略。

### Milestone 2：体验版 1.1

- 静默时段。
- 今日目标。
- 统计页升级。
- 模板编辑增强。
- 音频试听与震动提醒。
- 本地备份/恢复。

### Milestone 3：Pro 版 1.2

- 本地权益模型。
- Google Play Billing 或目标分发渠道支付。
- Pro 模板。
- 高级统计。
- 多提醒计划。
- 高级导出。

### Milestone 4：同步版 2.0

- 账号系统。
- 服务端权益校验。
- 云同步。
- 云备份。
- Plus 订阅。
- Web 仪表盘。

### Milestone 5：团队版 2.x

- 团队组织。
- 匿名聚合报表。
- 管理员默认策略。
- 企业授权。

## 15. GitHub Actions 验证建议

由于本地禁止运行构建和测试，所有验证应进入工作流。

建议在 `build.yml` 中逐步加入：

- 单元测试。
- Android Lint。
- Room migration 测试。
- Release APK 构建。
- APK SHA256 生成。
- 上传测试报告。

建议命令在工作流中执行：

```bash
gradle test lint assembleDebug --no-daemon
gradle assembleRelease --no-daemon
```

CodeQL 已存在，建议保留。

## 16. 最推荐的下一步执行顺序

1. 拆分状态机与 Repository，先不改 UI 行为。
2. 在 GitHub Actions 增加状态机单元测试。
3. 移除正式版破坏性迁移风险。
4. 修正发布版本号和 APK SHA256 校验。
5. 做静默时段、今日目标、统计升级。
6. 做本地备份/恢复。
7. 做 Pro 权益模型和付费墙。
8. 再接支付。
9. 最后考虑账号、云同步和订阅。

这个顺序能保证商业化建立在稳定核心之上，同时不会破坏免费用户的基础体验。
