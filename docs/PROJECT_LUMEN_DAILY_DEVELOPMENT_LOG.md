# Project Lumen 每日开发日志

本文根据仓库 commit 历史整理，用于回顾 Project Lumen 的阶段演进、每日开发重点和后续推进依据。日志按日期归纳，不逐条展开全部提交；每个日期列出代表性 commit、主要完成内容、风险和后续方向。

## 2026-04-06

### 开发重点

项目初始搭建，建立移动端 v1 基础结构和早期构建配置。

### 完成内容

- 创建 Project Lumen 初始仓库和移动端基础。
- 多次提交 scaffold，逐步形成早期移动端项目结构。
- 引入 core library desugaring 相关配置，为较新 Java API 在 Android 上运行做兼容准备。

### 关键提交

- `fcfefcb Initial commit`
- `4c418d1 feat: scaffold Project-Lumen mobile v1 foundation`
- `7ba0c25 feat: scaffold Project-Lumen mobile v1 foundation`
- `5cae830 feat: scaffold Project-Lumen mobile v1 foundation`
- `5342173 feat: scaffold Project-Lumen mobile v1 foundation`
- `0f047ca coreLibraryDesugaring`
- `d3a410a coreLibraryDesugaring`

### 遗留问题

- 初始阶段结构仍偏探索，后续需要明确主平台和核心功能边界。
- 构建、发布、测试和数据模型还未形成稳定闭环。

### 后续方向

- 明确产品主线。
- 建立运行设置、计时和提醒模型。
- 补齐 CI 和发布验证。

## 2026-06-20

### 开发重点

扩展桌面支持和运行设置，开始将项目能力从移动端基础扩展到多端与运行时控制。

### 完成内容

- 增加 Windows desktop support。
- 接入运行设置和 timer wiring。
- 建立 CodeQL workflow。
- 优化启动视觉和数据库初始化异步处理。
- 处理 FFI 相关导入冲突。

### 关键提交

- `603f9f5 feat: add Windows desktop support`
- `710c798 feat: add Windows desktop support`
- `d574914 feat: wire runtime settings and timers`
- `ad925af Create codeql.yml`
- `a800593 Update launch visuals and make DB init async`
- `70eeecb fix: hide ffi extensions in deferred import`

### 遗留问题

- 多端方向带来复杂度，后续需要判断是否继续维护桌面和移动多套路径。
- CodeQL 已建立，但构建方式和语言矩阵还需随着项目结构调整。

### 后续方向

- 梳理项目结构。
- 决定 Android 原生化或多端延续策略。

## 2026-06-26

### 开发重点

Flutter/多端阶段继续做 UI、启动、崩溃和桌面发布修补，为后续重构前留下过渡能力。

### 完成内容

- 优化首页、番茄钟和模板 UI。
- 增加 Windows installer build 和 release automation。
- 调整 splash screen、启动主题、数据库初始化、路由恢复和崩溃处理。
- 引入 Google Fonts。
- 修复样式、bootstrap 错误处理和 SQLite FFI 初始化。
- 增加扩展诊断日志。

### 关键提交

- `9c4ffc7 Refine UI layout and button actions...`
- `513d3b8 Refactor dependency overrides...`
- `0ae6e84 Add Windows installer build and release automation...`
- `91884f7 Implement route validation...`
- `c56a1dd Fix styles and enhance bootstrap error handling...`
- `467592e Initialize SQLite FFI factory...`
- `2c1dc74 Implement and initialize logging...`
- `d5669f6 Improve Android crash handling...`

### 遗留问题

- 多端维护成本继续增加。
- 桌面构建和 Flutter 结构与后续 Android 原生主线存在路线冲突。

### 后续方向

- 清理旧结构，转向更可控的 Android 原生实现。
- 重新建立 Android 客户端的数据、UI 和后台能力。

## 2026-06-28

### 开发重点

项目发生关键转向：剥离旧多端结构，重建 Android 原生客户端主线，并补齐提醒、通知、模板、后台服务、CI 和 release 基础。

### 完成内容

- 大规模清理旧文件和多端遗留结构。
- 重组项目文件和配置。
- 更新 Gradle、Kotlin、Room、AppCompat、Compose 动画和 Material 组件依赖。
- 建立 Android 原生 README 和项目说明。
- 新增 tip templates、Room DAO、数据库基础。
- 增加权限、通知、音频、导出和后台 timer service。
- 增加系统色背景、模板应用、active template UI。
- 增强通知状态、通知调度、PendingIntent flags。
- 增加动画、卡片、图标和 UI polish。
- 建立 GitHub Actions CI、CodeQL、release APK packaging、签名配置和自动发布。
- 加入 update check：BuildConfig 版本元数据、GitHub latest release 检测、通知提醒。
- 启用 release code shrinking 和资源优化，并新增 ProGuard 基础规则。
- 增加崩溃报告存储和展示。

### 关键提交

- `6be58de Removed a large number of files...`
- `1ba08b1 Refactor: Simplify and organize project files...`
- `93721da Add tip templates feature...`
- `5222b41 Improve Android permissions and notification handling...`
- `ec84179 Implement background timer service...`
- `84ade8b Update CI workflow to read app version...`
- `40c01f4 Implement secure signing pipeline...`
- `47338e5 Update build configuration to include short Git SHA...`
- `92dcdb8 Update version comparison logic...`
- `6e9cdff Enable code shrinking and resource optimization...`
- `3662798 Update Proguard rules...`
- `ca232fe Update app to handle uncaught exceptions...`

### 风险与处理

- 风险：大规模重构可能带来功能缺口。
- 处理：通过 CI、README、workflow、Room 和通知服务逐步建立新基线。
- 风险：release 开启 R8 后可能出现运行时崩溃。
- 处理：先加入基础 keep 规则，后续继续细化。

### 后续方向

- 完善应用内更新下载和安装。
- 增加网络状态、安装授权、下载进度和校验。
- 继续补齐数据导出、备份、后台稳定性和 UI。

## 2026-06-29

### 开发重点

围绕应用内更新、版本策略、安装授权、WebView、UI polish 和构建兼容进行集中推进。

### 完成内容

- 改进 APK 选择逻辑，优先匹配 ABI 和资源命名。
- 改进 workflow APK 聚合、上传和 release asset 处理。
- 增加 update notification intent。
- 增加安装 APK 权限、APK 下载、安装逻辑、未知来源授权提示。
- 使用 `Intent.ACTION_VIEW` 调用系统安装器。
- 增加下载进度 UI。
- 增加 network state 权限和网络感知 HTTP connection。
- 增加应用内 WebView 打开外部链接。
- 优化版本文件读取、version code、release dialog、外部链接确认。
- 增加语言切换重构、UI 卡片样式和颜色自定义。

### 关键提交

- `2ec38ac Remove unused String extension method...`
- `db3e30f Make Gradlew executable in CI workflow`
- `e2a299e Update Gradle to version 9.5.1...`
- `2bc5cf6 Update Gradle version to 8.11.1...`
- `38423a3 Implement update notification with intent...`
- `ef841fe Add permission for installing packages...`
- `7be486d Add support for conditional APK installation...`
- `a2c0dca Update installer to use VIEW intent...`
- `feed5d4 Update download progress handling...`
- `269ac45 Fix: Add network-aware HTTP connection...`
- `aa04bee Implement WebView screen...`
- `8c854a5 Add color customization...`

### 风险与处理

- 风险：内置 APK 更新涉及未知来源安装权限，可能影响 Play 版上架。
- 处理：当前先服务 GitHub 版，后续通过 flavor 区分 GitHub 和 Play 更新策略。
- 风险：Release version/tag/checksum 解析复杂。
- 处理：补充版本解析逻辑和 checksum 绑定策略。

### 后续方向

- 完善 release 复现文档。
- 区分渠道更新策略。
- 强化 update 失败反馈和安全校验。

## 2026-06-30

### 开发重点

后端、Docker/GHCR、遥测、备份同步、Shizuku 高级能力、相机检测和开发调试能力快速成型。

### 完成内容

- 增加 Java 21、Gradle lint 配置和 `.gitignore` 后端 target 忽略。
- 增加后端 API routes、MongoDB index 创建、backup handling。
- 增加 Dockerfile 和 GHCR 镜像 workflow。
- 增加 telemetry upload endpoint 和 typed JSON payload。
- 增加 face mesh detection、camera frame handling 和调试可视化。
- 改进 proximity check interval 和 worker 稳定性。
- 增加忽略电池优化权限。
- 增加 system alert 和 overlay 权限。
- 增加 Shizuku provider dependency。
- 实现 Shizuku 高级系统级护眼和进程控制。
- 开发者调试 UI 显示 Shizuku 状态和上下文。
- 增加 crash report share format。
- 解决 Android API deprecation、lint、rustfmt 和 workflow 问题。

### 关键提交

- `1b71962 Update build configuration to support Java 21...`
- `a8391c4 Fix: Add new API routes...`
- `2a2ae18 Add Dockerfile for multi-stage Rust build...`
- `06d767e Add telemetry upload endpoint...`
- `60e98f3 Refactor telemetry upload handler...`
- `c29d285 Add face mesh detection support...`
- `ddac0e7 Update proximity check interval...`
- `55c6bf5 Update build workflows to include Android lint...`
- `87c0021 Implement Shizuku integration...`
- `447eda6 Refine developer debug UI...`
- `917f846 Add crash report share format options`
- `2b4c765 Resolve reported lint issues`

### 风险与处理

- 风险：Shizuku 和系统级能力在不同设备上兼容性不一致。
- 处理：增加状态展示、刷新和后续 guard 修复。
- 风险：相机、face mesh、overlay、battery optimization 权限较敏感。
- 处理：需要后续权限与隐私中心统一说明。
- 风险：后端能力增加后需要更多测试和生产配置保护。
- 处理：workflow 加入 Rust verification，后续补限流和验签。

### 后续方向

- 完善 Shizuku guard。
- 推进商业化路线文档。
- 加强安全通信、证书绑定和 API 防护。

## 2026-07-01

### 开发重点

集中推进 UI/UX、Shizuku 修复、沉浸式护眼 toast、翻译入口、商业化路线、安全通信、NDK 自防卫、更新策略文档、模板、R8 规则和启动崩溃修复。

### 完成内容

- 改进应用 UI/UX、设置开关即时更新、主题色和模板色应用。
- 修复 timer 页面和通知状态同步。
- 增加沉浸式 eye care toast UI，并补权限 fallback。
- 修复 Shizuku guard 状态、camera privacy API、sensor privacy API 等兼容问题。
- 增加翻译入口和客户端，修复剪贴板 API。
- 更新商业化路线图。
- 增加安全通信需求落地：HTTPS、证书 pin、请求签名、防重放、Play Integrity、SecureCredentialStore。
- 增加 NDK `lumen_security`，用于签名密钥读取、包名/证书校验、反调试和 hooking artifact 检测。
- 更新 Android Gradle plugin，暴露 Gradle deprecation details，修复 backend rustfmt。
- 增加彩色模板和 JSON layout 配置。
- 增加系统更新策略复现指南。
- 增加 Janus 项目图标并调整。
- 恢复启动崩溃报告路径。
- 细化 R8 keep 规则，从全包 keep 转为运行时边界保留。

### 关键提交

- `2a57aac Improve app UI and UX polish`
- `f87290d feat: add immersive eye care toast UI`
- `2655827 Fix Shizuku system guard status lookup`
- `a6c54f9 Fix immediate settings switch updates`
- `06840d3 Apply templates to app theme colors`
- `2cbf9ea Fix timer pages and notification state sync`
- `f24e892 docs: update commercialization roadmap`
- `58f4cd6 feat: add translation entry and client`
- `4ca4269 Add overlay-based toast notification system...`
- `623f25b docs: add update strategy implementation guide`
- `78b57aa Add new colorful templates...`
- `dc4788c fix: restore startup crash reporting`
- `39fe659 build: refine R8 keep rules`
- `1e57d77 build: balance R8 shrinking safety`

### 风险与处理

- 风险：安全和 R8 改动容易影响 release 运行。
- 处理：保留 Android 组件、Room、WorkManager、JNI、WebView JS bridge 和持久化枚举等边界；普通代码交给 R8 优化。
- 风险：商业化路线已丰富，但真实支付验签和账号 UI 尚未完整闭环。
- 处理：后续优先推进账号、权益同步、Paywall 和服务端 Google Play 验签。
- 风险：权限面继续扩大。
- 处理：后续必须补权限与隐私中心，避免只依赖系统弹窗。

### 后续方向

- 等待 GitHub workflow 验证 release 和 Rust 后端。
- 完成账号 UI 和 session 存储。
- 建立服务端权益同步。
- 扩展 Paywall 到所有 `PremiumFeature`。
- 完成云备份和同步最小闭环。
- 设计 GitHub/Play/国内/企业 flavor。

## 汇总观察

### 开发节奏

提交历史显示，项目在 2026-06-28 至 2026-07-01 进入高强度迭代期。主要工作从 Android 原生化、发布链路和后台服务，快速扩展到后端、Admin、Shizuku、安全通信、更新策略、商业化和 R8 治理。

### 技术主线

当前主线已经明确为：

- Android 原生 Kotlin 客户端。
- Rust + MongoDB 后端。
- GitHub Actions 作为唯一正式构建和验证入口。
- GitHub Release 作为 GitHub 版发布源。
- 本地优先，云端作为增强服务。

### 当前最需要控制的风险

- 功能增长快于验证覆盖。
- 权限和隐私说明尚未完全产品化。
- 商业化模型已有架构，但支付验签和账号体验还未闭环。
- R8 和安全自防卫需要持续通过 release 构建验证。
- 多渠道策略还未通过 flavor 分离。

### 推荐下一步

1. 固化当前 release 基线。
2. 优先完成账号 UI、session 存储和权益同步。
3. 扩展 Paywall 到所有 Pro/Plus 能力。
4. 补齐权限与隐私中心。
5. 实现云备份和同步最小闭环。
6. 将 GitHub 版和 Play 版更新/权限行为拆分。
