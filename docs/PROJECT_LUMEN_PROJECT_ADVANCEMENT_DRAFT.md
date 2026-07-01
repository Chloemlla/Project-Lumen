# Project Lumen 项目推进草稿

## 1. 文档目的

本文用于整理 Project Lumen 当前研发基线、阶段目标、推进节奏、任务拆分、验收口径和风险控制。内容参考仓库提交历史、现有 Android 客户端、Rust 后端、Admin Dashboard、GitHub Actions workflow 和 `docs/` 中已有路线文档。

本文是推进草稿，不是最终排期承诺。后续每次阶段推进应以 GitHub issue、pull request、workflow 结果和 release 产物为准。

## 2. 当前项目基线

### 2.1 产品基线

Project Lumen 当前已经从早期 Flutter/多端探索，转为以 Android 原生 Kotlin 为主线的用眼健康与专注辅助应用。核心能力包括：

- 用眼休息提醒。
- 番茄钟。
- 前台服务和通知。
- 开机恢复、闹钟触发和后台计时恢复。
- Room 本地设置、运行态和统计。
- 模板、主题色、动态色和本地个性化。
- CSV、统计图片、PDF、本地 JSON 备份恢复。
- 本地崩溃报告。
- GitHub Release 更新检测、APK 下载、SHA-256 校验和系统安装器交接。
- 近距离、眨眼、低光照、自动亮度、强提醒遮罩和开发调试遮罩。
- Shizuku 高级系统上下文能力。
- 翻译入口和后端 API 客户端基础。
- 安全通信、请求签名、Play Integrity、NDK 安全桥、R8 细粒度 keep 规则。

### 2.2 后端基线

Rust 后端当前提供：

- `/api/health`
- 邮箱登录 start/verify/refresh。
- `/api/v1/me`
- `/api/v1/entitlements`
- `/api/v1/purchases/google/verify`
- `/api/v1/sync/changes`
- `/api/v1/sync/push`
- `/api/v1/backups`
- `/api/v1/backups/latest`
- `/api/v1/telemetry`
- `/api/v1/face-analysis/frames`
- `/api/admin/*`

MongoDB 集合已覆盖用户、登录请求、会话、API nonce、权益、同步变更、备份、遥测、人脸分析帧、Admin 会话、Admin 动作、审计、模板、发布和 allowlist。

### 2.3 工程基线

当前工程基线包括：

- Android Gradle Plugin、Kotlin、Java 21 和 Gradle workflow 配置。
- GitHub Actions Android 单元测试、Lint、release APK 构建、release assets、SHA-256 checksum。
- Rust 后端 `cargo fmt` 和 `cargo test` workflow。
- GHCR 镜像构建和可选部署 workflow。
- CodeQL workflow。
- Release APK 支持 ABI split 和通用包。
- 本地禁止执行实际构建、测试、安装和依赖安装命令，验证应交给 GitHub workflow。

## 3. 提交历史反映的阶段演进

### 3.1 初始与多端探索阶段

代表提交：

- `fcfefcb Initial commit`
- `4c418d1`、`7ba0c25`、`5cae830`、`5342173 feat: scaffold Project-Lumen mobile v1 foundation`
- `710c798 feat: add Windows desktop support`
- `d574914 feat: wire runtime settings and timers`

该阶段完成项目雏形、运行设置、计时能力和多端方向探索。后续提交显示项目逐渐从多端 Flutter/桌面方向收敛。

### 3.2 原生 Android 重构阶段

代表提交：

- `6be58de Removed a large number of files...`
- `1ba08b1 Refactor: Simplify and organize project files...`
- `93721da Add tip templates feature...`
- `5222b41 Improve Android permissions and notification handling...`
- `ec84179 Implement background timer service...`

该阶段剥离大量旧结构，重心转向 Android 原生实现，补齐 Room、模板、权限、通知、音频、导出和前台服务。

### 3.3 发布与更新链路阶段

代表提交：

- `84ade8b Update CI workflow to read app version...`
- `47338e5 Update build configuration to include short Git SHA...`
- `92dcdb8 Update version comparison logic...`
- `ef841fe Add permission for installing packages and implement APK download...`
- `7be486d Add support for conditional APK installation...`
- `feed5d4 Update download progress handling...`
- `623f25b docs: add update strategy implementation guide`

该阶段完成 GitHub Release 更新策略、版本元数据、APK 下载校验、安装授权、发布资产命名和复现文档。

### 3.4 护眼增强与调试能力阶段

代表提交：

- `c29d285 Add face mesh detection support...`
- `ddac0e7 Update proximity check interval...`
- `87c0021 Implement Shizuku integration...`
- `edb8d5c Add Shizuku system guard controls`
- `f87290d feat: add immersive eye care toast UI`
- `4b7625d fix: modernize overlay immersive system bars`

该阶段将项目从定时提醒扩展到情境感知护眼：前置相机、face mesh、环境系统状态、遮罩、toast 和开发调试。

### 3.5 后端与商业化基础阶段

代表提交：

- `a8391c4 Fix: Add new API routes...`
- `06d767e Add telemetry upload endpoint...`
- `2a2ae18 Add Dockerfile for multi-stage Rust build...`
- `f24e892 docs: update commercialization roadmap`
- `58f4cd6 feat: add translation entry and client`
- `4ca4269 Add overlay-based toast notification system...`，同时落地安全通信和 NDK 安全桥。

该阶段建立 Rust 后端、MongoDB、遥测、备份、同步、商业化路线、安全通信和 API 客户端基础。

### 3.6 安全、体积和稳定性治理阶段

代表提交：

- `6e9cdff Enable code shrinking and resource optimization...`
- `3662798 Update Proguard rules...`
- `dc4788c fix: restore startup crash reporting`
- `39fe659 build: refine R8 keep rules`
- `1e57d77 build: balance R8 shrinking safety`

该阶段关注 release 体积、R8 混淆、JNI 边界、崩溃报告、证书签名和运行时完整性，目标是在减小体积的同时避免运行时崩溃和功能缺失。

## 4. 推进原则

### 4.1 本地核心不依赖云端

用眼提醒、番茄钟、本地统计、通知、模板和基础护眼能力应保持离线可用。云同步、云备份、权益恢复和远端模板是增强能力，不应成为基础提醒可用性的前置条件。

### 4.2 权限必须可解释

相机、悬浮窗、精确闹钟、通知、前台服务、忽略电池优化、写系统设置、安装 APK 和使用情况访问都必须有清晰解释。权限缺失时功能降级，不应崩溃。

### 4.3 商业化不能破坏基础体验

Free 版本应保留基础提醒、番茄钟、本地基础统计和核心护眼价值。Pro/Plus 应提供高级模板、多计划、高级统计、完整备份、云同步、云备份和远端服务价值。

### 4.4 安全和可维护性优先于短期堆功能

新增后端接口、支付、同步和遥测时，应同步考虑鉴权、限流、审计、隐私、错误处理和 workflow 验证。

### 4.5 验证交给 GitHub workflow

受仓库约束，实际构建、测试、Lint、安装和依赖安装不在本地执行。每个推进阶段的验收应以 GitHub Actions 结果为准。

## 5. 下一阶段总体目标

建议下一阶段目标定义为：

> 完成 Project Lumen 从“功能密集原型”到“可公开测试的 Android + 后端闭环产品”的过渡。

关键结果：

- 基础提醒稳定。
- 权限与隐私说明完整。
- 账号、权益、备份、同步具备最小闭环。
- Pro/Plus 付费点明确但不破坏免费基础体验。
- Release 包体积持续可控。
- GitHub workflow 能验证 Android 与 Rust 两端。
- Admin Dashboard 能支撑基本运营观测。

## 6. 阶段拆分

### Phase 0：稳定性与发布基线冻结

目标：确认当前主线可以作为后续功能叠加的稳定基线。

任务：

- 固化 R8 规则边界：Android 组件、WorkManager、Room、JNI、WebView JS bridge、持久化枚举。
- 梳理启动崩溃、崩溃报告保存、崩溃报告展示和分享路径。
- 确认 release workflow 输出 APK、ABI 包和 checksum。
- 确认更新检测复现文档与代码一致。
- 检查权限缺失时的降级路径。

验收：

- GitHub Actions Android 单元测试和 Lint 通过。
- Rust `cargo fmt` 和 `cargo test` 通过。
- Release assets 包含 APK 和 `checksums.txt`。
- 启动崩溃报告不因 R8/JNI 规则失效。

### Phase 1：账号与权益闭环

目标：建立用户身份、服务端权益和本地权限判断的基础。

任务：

- 完善账号 UI：邮箱输入、验证码输入、登录状态、退出登录。
- 使用 `SecureCredentialStore` 管理 session。
- 登录后拉取 `/api/v1/me` 和 `/api/v1/entitlements`。
- 将本地 `settings.planTier` 从“手写真相”降级为缓存或展示字段。
- 建立 `RemoteEntitlementSyncService`。
- 梳理手动 Pro 授权入口，仅保留开发/内测用途。

验收：

- 未登录时基础功能可用。
- 登录后可恢复服务端权益。
- 本地重启后 session 可恢复。
- token 不进入本地 JSON 备份。
- 服务端返回降级状态时本地功能正确降级。

### Phase 2：商业化入口和付费墙

目标：使 Pro/Plus 功能入口统一、可解释、可回退。

任务：

- 建立统一 `PaywallState`。
- 建立 `requireFeature(feature, onAllowed)` 风格入口。
- 覆盖 Pro 模板、高级统计、高级导出、本地完整备份、多提醒计划和云同步。
- 设置页展示当前 tier、权益状态、同步时间和恢复购买入口。
- 后端 Google purchase verify 保持 fail-closed，生产禁用未验证购买。

验收：

- Free 用户可看到高级能力入口，但不会误以为已开启。
- Pro 用户可使用 Pro 模板、高级统计、多计划、本地完整备份和高级导出。
- Plus 用户可进入云同步和云备份。
- 购买失败或网络失败不影响基础计时。

### Phase 3：同步与云备份最小闭环

目标：为 Plus 提供持续服务价值。

任务：

- 本地保存 sync cursor、lastSyncAt、lastSyncError。
- 同步 settings、daily goals、tip templates、reminder plans、daily stats。
- 明确不推送 runtime_state、token、原始摄像头帧和本地文件真实路径。
- 实现云备份上传、最新备份查询和恢复前摘要。
- 后端补充同步幂等、单次变更数量限制和备份大小限制。

验收：

- 同一账号两台设备可同步设置和模板。
- 统计按日合并，不互相覆盖。
- 同步失败不影响本地提醒。
- 云备份恢复前展示摘要。
- 权益只从服务端下发，不接受客户端 push。

### Phase 4：隐私权限中心与合规材料

目标：使敏感权限和数据使用对用户可解释。

任务：

- 首次启动权限说明页。
- 设置页“权限与隐私”中心。
- 遥测独立开关。
- 原始帧上传默认关闭。
- 隐私政策、用户协议、第三方服务清单。
- 医疗边界文案：只描述提醒和习惯建议，不描述诊断或治疗。

验收：

- 每个敏感权限都有用途说明和关闭路径。
- 用户不授权相机时，近距离/眨眼能力不可用但应用不崩溃。
- 用户不授权悬浮窗时，强提醒遮罩降级为通知或普通提示。
- 用户可理解哪些数据只在本地、哪些可能上传。

### Phase 5：渠道 flavor 和公开测试

目标：区分 GitHub、Play、国内和企业渠道。

任务：

- 新增 `github`、`play`、`china`、`enterprise` flavor 设计。
- GitHub 版保留内置 APK 更新。
- Play 版禁用 APK 下载安装，使用商店更新。
- 国内版预留渠道支付、更新和隐私弹窗策略。
- 企业版预留私有 API base URL 和授权策略。

验收：

- 不同 flavor 的权限、更新入口、支付入口和 API 地址可分开控制。
- Play 版不声明不必要的安装 APK 权限。
- GitHub 版继续使用 SHA-256 校验更新。

## 7. 任务优先级

### P0

- 启动稳定性和崩溃报告。
- R8 规则和 release 包体积治理。
- GitHub Actions 验证链路。
- 权限缺失降级。
- 账号和 session 安全存储。

### P1

- 服务端权益同步。
- Paywall 和功能 gating。
- 云备份最小闭环。
- 同步 cursor 和冲突规则。
- 隐私权限中心。

### P2

- 多提醒计划完整产品化。
- 远端模板 CMS。
- 高级统计和月报。
- 远端发布策略和灰度。
- Admin Dashboard 指标完善。

### P3

- Team/Enterprise。
- 国内商业化渠道。
- 企业私有化部署。
- AI/规则混合建议。

## 8. 每日推进节奏建议

每天开发记录建议包含：

- 日期。
- 今日目标。
- 完成内容。
- 关键提交。
- 影响模块。
- 验证状态。
- 遗留问题。
- 明日计划。

每次提交建议保持：

- 一个提交只解决一个主题。
- 文档、代码、资源、workflow 修改尽量拆分。
- 涉及 release、安全、R8、数据库迁移时，提交信息明确说明风险边界。
- 不把本地生成物和无关文件混进功能提交。

## 9. 验收矩阵

| 模块 | 验收重点 | 验证位置 |
| --- | --- | --- |
| Android 计时 | 状态机、后台恢复、通知动作 | GitHub Actions 单元测试、人工设备验证 |
| Room | 迁移、实体、DAO、备份恢复 | GitHub Actions 单元测试 |
| 护眼检测 | 权限、采样、降级、遮罩 | 人工设备验证 |
| 更新 | Release 检测、SHA-256、安装授权 | GitHub Release 流程和人工验证 |
| 后端 API | 鉴权、权益、同步、备份、遥测 | Rust tests、API 验证 |
| Admin | 登录、审计、仪表盘数据 | 后端测试和人工验证 |
| 安全 | HTTPS、签名、防重放、NDK、R8 | workflow、代码审查、设备验证 |
| 商业化 | tier、付费墙、恢复购买 | 单元测试和沙盒支付验证 |

## 10. 风险登记

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| Android 后台限制 | 计时丢失、提醒延迟 | 前台服务、闹钟、WorkManager、开机恢复、权限说明 |
| R8 误裁剪 | release 崩溃或功能缺失 | 细粒度 keep、workflow release 验证、崩溃报告 |
| 权限敏感 | 上架受阻、用户不信任 | 权限中心、隐私政策、默认关闭高敏上传 |
| 未验证购买 | 商业化不可控 | 生产 fail-closed、服务端验签、权益撤销 |
| 同步冲突 | 数据覆盖或丢失 | cursor、remoteId、updatedAt、deletedAt、集合级规则 |
| API 滥用 | 成本和安全风险 | 限流、签名、防重放、审计 |
| 医疗表述 | 合规风险 | 使用提醒、辅助、习惯建议等表述 |

## 11. 近期建议执行清单

1. 冻结当前 release 基线并等待 GitHub workflow 结果。
2. 完成账号页和 session 持久化 UI。
3. 接上服务端权益同步，并把本地 tier 改为派生结果。
4. 扩展 Paywall 到所有 `PremiumFeature`。
5. 补齐权限与隐私中心。
6. 实现云备份 UI 最小闭环。
7. 设计同步 cursor 和冲突规则。
8. 规划 flavor 分层，先区分 GitHub 和 Play 行为。

## 12. 当前推进结论

Project Lumen 当前处于“功能快速成型后进入稳定化和商业化闭环”的阶段。提交历史显示，项目在 2026-06-28 至 2026-07-01 期间完成了大量 Android 原生化、发布链路、后端、安全和护眼增强能力。下一阶段不宜继续无边界堆功能，应优先把账号、权益、隐私、同步、备份、验证和渠道边界打通，形成可公开测试的稳定版本。
