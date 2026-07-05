# Project Lumen Android Remotion 产品动画调研文档

本文档只服务于 Android 客户端产品展示动画。范围限定为 `app/` 原生 Android Kotlin 应用，不包含 Rust 后端、React 管理台、VitePress 文档站或 UI token 调试工具的展示。

目标是帮助编写 Remotion 视频：明确哪些 Android 功能值得入镜、源码位置在哪里、画面应该怎样抽象、动画顺序如何安排，以及哪些状态可以用假数据复现。

## 1. Android 产品定位

Project Lumen Android 端是一个本地优先的护眼与专注应用。核心叙事不是“计时器”，而是：

- 让用户在长时间看屏幕时被温和打断。
- 用番茄钟承接专注工作流。
- 用近距离、眨眼、环境光和全屏遮罩扩展为主动护眼系统。
- 用统计、目标、模板、声音、备份、远端账户和 Shizuku 高级能力形成完整产品。
- 用开放 API、遥测、崩溃报告和开发者诊断支撑工程可信度。

Remotion 动画应该优先表达“手机本地实时状态 -> 护眼事件触发 -> 休息/专注闭环 -> 数据反馈 -> 高级保护”的产品闭环。

## 2. Android 总入口和导航位置

| 展示点 | 用户看到的功能 | 主要源码位置 | 动画价值 |
| --- | --- | --- | --- |
| 应用启动 | 首次引导、主题、崩溃页优先级、WebView、更新弹窗、主导航 | `app/src/main/java/com/projectlumen/app/app/ProjectLumenApp.kt` | 适合做开场手机入屏、底部导航展开、应用状态加载 |
| 主 Activity | ViewModel 创建、外部 Intent 处理、开放入口回跳 | `app/src/main/java/com/projectlumen/app/MainActivity.kt` | 适合表现外部应用触发 Lumen |
| 应用级对象 | 数据库、服务、监控、崩溃、Shizuku 恢复 | `app/src/main/java/com/projectlumen/app/ProjectLumenApplication.kt` | 适合表现后台能力网络 |
| UI 状态容器 | 设置、运行态、统计、模板、权益、计划 | `app/src/main/java/com/projectlumen/app/app/ProjectLumenUiState.kt` | 适合 Remotion 构造统一 mock 数据 |
| ViewModel | 所有用户操作的动作面 | `app/src/main/java/com/projectlumen/app/app/ProjectLumenViewModel.kt` | 适合整理按钮点击到功能变化的镜头链路 |

底部主导航在 `Destination` 中定义：

- `HOME`：首页。
- `BREAK`：护眼休息。
- `POMODORO`：番茄钟。
- `STATS`：统计。
- `SETTINGS`：设置。

二级页面：

- `TRANSLATION`：翻译。
- `TEMPLATES`：模板。
- `ABOUT`：关于与更新。
- `DEVELOPER`：开发者调试。
- `WEB`：内置 WebView。

## 3. Remotion 推荐视频结构

建议做一个 90 到 120 秒的横屏产品片，内部用竖屏手机壳承载 Android UI。画面节奏如下：

| 时间 | 镜头主题 | 画面内容 | 对应源码 |
| --- | --- | --- | --- |
| 0-8s | 产品开场 | 手机亮屏，Project Lumen 图标进入，底部五栏导航浮现 | `ProjectLumenApp.kt` |
| 8-20s | 首次引导与推荐配置 | Onboarding 页卡片滑动，设备指纹、推荐护眼配置、完成按钮 | `ProjectLumenOnboardingScreen.kt`, `ProjectLumenRecommendedSetupFeedback.kt` |
| 20-34s | 首页总览 | 当前状态卡、今日统计、目标进度、智能建议卡、快捷操作 | `ProjectLumenMainScreens.kt`, `ProjectLumenStatsAndTimerCards.kt`, `ProjectLumenHomeConvenienceCard.kt` |
| 34-48s | 护眼提醒闭环 | 工作计时 -> 预提醒 -> 开始休息 -> 倒计时模板 -> 返回工作 | `ReminderEngine.kt`, `BreakScreen`, `TimerCard` |
| 48-58s | 番茄钟 | Focus 圆环、短休/长休切换、完成番茄数累加 | `PomodoroEngine.kt`, `PomodoroScreen` |
| 58-72s | 主动护眼感知 | 摄像头采样、人脸框、近距离警告、眨眼/干眼、低光 lux、遮罩 | `core/proximity`, `core/light`, `core/overlay` |
| 72-84s | 统计和导出 | 7/30 天切换、趋势卡、习惯建议、CSV/PNG/PDF 导出按钮 | `StatisticsScreen`, `ProjectLumenStatisticsCards.kt`, `ExportService.kt` |
| 84-96s | 个性化模板 | 模板列表、Pro 锁、颜色/图片/倒计时样式编辑 | `ProjectLumenTemplateScreens.kt`, `DefaultTipTemplates.kt` |
| 96-108s | 设置与隐私权限 | 权限中心、提醒/监测/声音/外观设置折叠卡 | `ProjectLumenSettingsScreen.kt`, `ProjectLumenSettingsPrivacyCenter.kt` |
| 108-120s | 高级能力收束 | 云账户、Shizuku 原生护眼、开发者调试、开放 API 图层收束成产品标语 | `ProjectLumenRemoteCloudCard.kt`, `ProjectLumenShizukuSettingsSection.kt`, `ProjectLumenDeveloperDebugScreen.kt`, `openapi/` |

## 4. Remotion 画面组件建议

建议在 Remotion 中不要直接复刻 Compose，而是抽象成稳定的可控组件：

| Remotion 组件 | 用途 | 对应 Android 结构 |
| --- | --- | --- |
| `PhoneFrame` | 竖屏设备外壳、状态栏、导航栏 | Android 主屏幕容器 |
| `LumenTopBar` | 标题栏、返回按钮、页面标题 | `ProjectLumenSharedComponents.kt` |
| `BottomNav` | Home / Break / Pomodoro / Stats / Settings 五栏 | `Destination` |
| `MetricCard` | 今日统计、目标进度、状态卡 | `TodayStatsCard`, `GoalProgressCard`, `StateCard` |
| `TimerRing` | 护眼倒计时、番茄钟倒计时 | `TimerCard`, `AnimatedTimerText` |
| `SettingsAccordion` | 设置页折叠区 | `SettingsSection` |
| `SensorOverlay` | 人脸框、眼睛点、距离比例、lux 波形 | `FaceDistanceAnalyzer`, `LightMonitorService` |
| `TemplateCard` | 模板预览和编辑 | `TemplatePreviewCard`, `TemplatesScreen` |
| `DataFlowRibbon` | 本地 Room、MMKV、安全凭据、通知、服务的流动线条 | `ProjectLumenRepositories`, `AppDatabase`, `SecureCredentialStore` |

Remotion 数据建议集中放在一个 `androidDemoState.ts`，字段对应 `ProjectLumenUiState`：

```ts
export const androidDemoState = {
  settings: {
    reminderEnabled: true,
    pomodoroEnabled: true,
    statsEnabled: true,
    proximityMonitoringEnabled: true,
    blinkMonitoringEnabled: true,
    ambientLightMonitoringEnabled: true,
    globalOverlayEnabled: true,
    shizukuAdvancedModeEnabled: true,
  },
  runtime: {
    activeEngine: "REMINDER",
    reminderPhase: "WORKING",
    remainingSeconds: 420,
    progress: 0.42,
  },
  today: {
    workingMinutes: 96,
    restMinutes: 12,
    completedBreaks: 6,
    proximityWarnings: 2,
    blinkWarnings: 1,
    lowLightWarnings: 1,
  },
};
```

## 5. Android 功能地图和镜头用法

### 5.1 首次引导

源码：

- `app/src/main/java/com/projectlumen/app/app/ProjectLumenOnboardingScreen.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenOnboardingState.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenRecommendedSetupFeedback.kt`

入镜重点：

- 多页 onboarding 卡片。
- 设备指纹短码。
- 新安装检测。
- “应用推荐护眼配置”的按钮反馈。
- 推荐配置影响提醒、统计、通知、保活、预提醒、近距离、眨眼、低光、自动亮度、全屏遮罩和目标。

动画建议：

- 用横向卡片滑动表现 5 个引导页。
- 设备指纹用等宽字符逐位点亮。
- 点击推荐配置后，右侧出现一组保护能力开关同时变绿。

### 5.2 首页总览

源码：

- `app/src/main/java/com/projectlumen/app/app/ProjectLumenMainScreens.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenStatsAndTimerCards.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenHomeConvenienceCard.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenEyeCareInsights.kt`

入镜重点：

- 当前状态卡显示运行态。
- 今日护眼统计。
- 每日目标进度。
- 便捷操作：开始休息、开始番茄钟、暂停 1 小时。
- 引导卡检查权限、距离校准、开始提醒、导出报告。
- 护眼洞察卡把使用数据转成建议。

动画建议：

- 让 `StateCard` 在工作态、休息态、番茄钟态之间 morph。
- 目标进度条从 0 增长到完成比例。
- 快捷按钮点击后底部导航自动切到对应页面。

### 5.3 护眼提醒

源码：

- `app/src/main/java/com/projectlumen/app/core/runtime/ReminderEngine.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenRuntimeFeatureEntry.kt`
- `app/src/main/java/com/projectlumen/app/core/services/TimerForegroundService.kt`
- `app/src/main/java/com/projectlumen/app/core/services/NotificationService.kt`
- `app/src/main/java/com/projectlumen/app/core/services/AlarmReceiver.kt`
- `app/src/main/java/com/projectlumen/app/core/services/ReminderActionReceiver.kt`

功能阶段：

- `IDLE`：未运行。
- `WORKING`：工作计时中。
- `PRE_ALERT`：休息前预提醒。
- `AWAITING_ACTION`：等待用户开始或跳过。
- `RESTING`：休息倒计时。
- `PAUSED`：暂停或静默。

入镜重点：

- “工作 20 分钟 -> 预提醒 -> 休息 20 秒 -> 完成后回到工作”的闭环。
- 通知操作：开始休息、跳过、停止。
- 勿扰时段、禁用跳过、全屏遮罩是差异化卖点。

动画建议：

- 用一条时间轴表示 ReminderEngine 的状态转换。
- 手机内显示倒计时圆环，手机外显示通知气泡。
- 休息开始时屏幕背景切换到当前模板色。

### 5.4 番茄钟

源码：

- `app/src/main/java/com/projectlumen/app/core/runtime/PomodoroEngine.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenRuntimeFeatureEntry.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenMainScreens.kt`

入镜重点：

- 专注阶段。
- 短休。
- 第 4 个周期后的长休。
- 与护眼提醒共用运行状态，同一时间只允许一个主计时引擎。
- 完成番茄数和专注时长进入统计。

动画建议：

- 用 4 段小圆点表示番茄周期。
- Focus 段为高亮，Short Break 为柔和绿色，Long Break 为更长的波形展开。
- 当番茄钟启动时，护眼提醒按钮淡出或显示“其他计时运行中”。

### 5.5 统计、目标和导出

源码：

- `app/src/main/java/com/projectlumen/app/app/ProjectLumenMainScreens.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenStatisticsCards.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenStatsAndTimerCards.kt`
- `app/src/main/java/com/projectlumen/app/core/services/ExportService.kt`

入镜重点：

- 今日工作、休息、跳过、完成休息。
- 近距离、干眼、低光警告。
- 番茄统计：完成番茄数、专注次数、休息时间。
- 7 天、30 天、本月切换。
- 高级统计、习惯建议、趋势卡。
- CSV、统计图片、月度 PDF 导出。

动画建议：

- 把统计页面作为“行为反馈”段落，不宜太早出现。
- 使用同一组 mock 数据生成柱状图、趋势线和导出文件卡。
- 导出按钮点击后飞出 `CSV`、`PNG`、`PDF` 三个文件标签。

### 5.6 近距离、眨眼和低光保护

源码：

- `app/src/main/java/com/projectlumen/app/core/proximity/ProximityDetectionWorker.kt`
- `app/src/main/java/com/projectlumen/app/core/proximity/ProximityDetectionService.kt`
- `app/src/main/java/com/projectlumen/app/core/proximity/ProximityCameraSampler.kt`
- `app/src/main/java/com/projectlumen/app/core/proximity/FaceDistanceAnalyzer.kt`
- `app/src/main/java/com/projectlumen/app/core/proximity/ProximityTriggerGate.kt`
- `app/src/main/java/com/projectlumen/app/core/light/LightMonitorService.kt`
- `app/src/main/java/com/projectlumen/app/core/overlay/EyeProtectionOverlayService.kt`

入镜重点：

- 前置摄像头采样。
- 人脸框、眼睛点、脸宽比例、校准基线。
- 距离倍率超过阈值后触发通知、Toast 或全屏遮罩。
- 左右眼睁眼概率用于眨眼/干眼风险。
- lux 低于阈值触发低光提示。
- 自动亮度按 lux 映射。

动画建议：

- 不使用真实人脸素材时，用抽象头像轮廓和检测框。
- 距离风险用 `120% -> 165% -> 220%` 的数字增长表现。
- lux 用小型波形，从 `120 lux` 降到 `8 lux` 后触发低光卡片。
- 全屏遮罩可作为一次强视觉转场：手机屏幕从普通 UI 覆盖为休息提示。

### 5.7 模板与个性化

源码：

- `app/src/main/java/com/projectlumen/app/app/ProjectLumenTemplateScreens.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenTemplatesFeatureEntry.kt`
- `app/src/main/java/com/projectlumen/app/app/DefaultTipTemplates.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenUiFormatters.kt`

入镜重点：

- 模板预览。
- 免费与 Pro 模板区分。
- 自定义图片、标题、副标题。
- 倒计时样式：圆环、进度条、纯数字。
- 背景和主色影响休息界面，也会影响主题外观。

动画建议：

- 做 3 张模板卡横向轮播。
- 点击模板后，休息页面背景、主色和倒计时样式同步变化。
- Pro 锁只短暂出现，避免抢主线。

### 5.8 设置、权限和隐私中心

源码：

- `app/src/main/java/com/projectlumen/app/app/ProjectLumenSettingsScreen.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenSettingsPrivacyCenter.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenSettingsPrivacyModel.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenPermissionState.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenPermissionGates.kt`

关键设置组：

- 通用：语言、主题、动态取色、自动深色、统计、翻译入口、自动更新。
- 目标：休息次数、最大连续工作、每日番茄、周活跃天数。
- 数据：本地备份导出/导入、云账户卡。
- 通知和后台：通知权限、精确闹钟、前台服务保活。
- 近距离：摄像头权限、校准、检查间隔、采样秒数、阈值、冷却。
- 眨眼和环境：未眨眼阈值、低光阈值、自动亮度。
- 全屏遮罩：遮罩开关、休息时长、严格距离阈值。
- 声音：音效、震动、各类音量、自定义音频。
- 提醒：间隔、休息时长、询问、禁用跳过、勿扰、预提醒。
- 番茄钟：专注、短休、长休。
- 开发者：默认隐藏，从关于页连续点击解锁。

动画建议：

- 设置页不要完整滚一遍，选 3 个折叠区做代表：权限中心、主动护眼、声音/外观。
- 权限中心可以做成 readiness score 或待办清单。
- 开关和 slider 是 Remotion 中最容易表达“产品可配置性”的控件。

### 5.9 数据、备份、远端账户和权益

源码：

- `app/src/main/java/com/projectlumen/app/app/ProjectLumenRemoteCloudCard.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenRemoteFeatureEntry.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenBackupFeatureEntry.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenEntitlementFeatureEntry.kt`
- `app/src/main/java/com/projectlumen/app/core/api/ProjectLumenApiClient.kt`
- `app/src/main/java/com/projectlumen/app/core/security/SecureCredentialStore.kt`

已封装能力：

- API 健康检查。
- 邮箱登录、验证码、刷新会话。
- 当前用户和权益。
- Google 购买校验。
- 同步变更拉取和推送。
- 云备份上传和恢复。
- 功能开关、远端配置模板和策略。

动画建议：

- Android 视频中只用“云账户卡”做轻量展示，不展开后端。
- 画面可以表现本地数据加密保存，随后上传云备份。
- 权益可用 `FREE -> PRO` 的徽章切换表现模板解锁。

### 5.10 Shizuku 高级能力和 App 网络控制

源码：

- `app/src/main/java/com/projectlumen/app/app/ProjectLumenShizukuSettingsSection.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenAppNetworkControlFeatureEntry.kt`
- `app/src/main/java/com/projectlumen/app/core/shizuku/ShizukuCapabilityManager.kt`
- `app/src/main/java/com/projectlumen/app/core/shizuku/ShizukuNetworkControlModels.kt`
- `app/src/main/java/com/projectlumen/app/core/services/ShizukuResilienceWorker.kt`

入镜重点：

- Shizuku 授权状态。
- 前台应用上下文。
- 上下文感知采样、服务恢复、屏幕关闭、低电量、省电、勿扰、温度、摄像头隐私守护。
- 原生护眼：色温、系统亮度、Extra Dim。
- App 网络控制：限制应用网络、恢复网络策略。
- 诊断遥测上传、崩溃报告上传、应用清单上传。

动画建议：

- 这部分放在后段，作为“高级用户/系统级保护”能力。
- 用系统权限盾牌、前台 App 卡、亮度滑杆和 Extra Dim 叠层表达即可。
- App 网络控制可做成应用列表中某个 App 被加上网络阻断标记。

### 5.11 开发者调试和崩溃报告

源码：

- `app/src/main/java/com/projectlumen/app/app/ProjectLumenDeveloperDebugScreen.kt`
- `app/src/main/java/com/projectlumen/app/core/debug/DeveloperDebugOverlayService.kt`
- `app/src/main/java/com/projectlumen/app/core/debug/DeveloperDebugFrameStore.kt`
- `app/src/main/java/com/projectlumen/app/core/crash/CrashReport.kt`
- `app/src/main/java/com/projectlumen/app/core/crash/CrashReportStore.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenCrashReportScreen.kt`

入镜重点：

- 摄像头调试预览。
- AI 推理耗时、摄像头延迟、脸宽、脸宽比例。
- 原始传感器：lux、pitch、roll、yaw、加速度。
- 前台服务运行时长、最近重启、任务移除。
- API 安全诊断。
- 崩溃报告页面和复制报告。

动画建议：

- 用半透明 HUD 覆盖在手机屏幕上。
- 不要让开发者页抢占用户价值主线，建议 5-7 秒快速展示。
- 崩溃报告适合表达“发生问题也可恢复并上报”。

### 5.12 自动更新、关于页和 WebView

源码：

- `app/src/main/java/com/projectlumen/app/app/ProjectLumenAboutAndDialogs.kt`
- `app/src/main/java/com/projectlumen/app/core/update/UpdateChecker.kt`
- `app/src/main/java/com/projectlumen/app/core/update/UpdateInstaller.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenWebViewScreen.kt`

入镜重点：

- 关于页展示版本。
- 检查 GitHub Release。
- 匹配 ABI、要求 SHA256。
- 下载进度、校验、安装授权。
- 内置 WebView 可打开项目页面、复制 URL、外部浏览器打开。

动画建议：

- 作为结尾“可持续更新”的小镜头。
- 下载进度条从 0 到 100，随后 SHA256 check 变为完成。

### 5.13 开放 API 和外部触发

源码：

- `app/src/main/aidl/com/project/lumen/open/ILumenOpenApi.aidl`
- `app/src/main/java/com/projectlumen/app/openapi/LumenOpenService.kt`
- `app/src/main/java/com/projectlumen/app/openapi/LumenOpenRuntimeController.kt`
- `app/src/main/java/com/projectlumen/app/openapi/LumenOpenContracts.kt`
- `app/src/main/java/com/projectlumen/app/openapi/ExternalActivities.kt`

开放能力：

- 获取眼疲劳等级。
- 获取连续屏幕时间。
- 判断是否正在休息。
- 启动外部专注会话。
- 停止外部专注会话。
- 触发眼部放松。
- Intent 打开仪表盘、触发休息、打开视觉监控。

动画建议：

- 用另一个 App 的小卡片发出 Intent 箭头，Project Lumen 跳到 Stats 或 Break。
- 权限名和签名校验只做技术字幕，不要占太长时间。

## 6. 本地数据和后台服务的可视化

源码：

- 数据库：`app/src/main/java/com/projectlumen/app/core/database/AppDatabase.kt`
- 设置：`AppSettingsEntity`
- 运行态：`RuntimeStateEntity`
- 护眼统计：`DailyEyeStatsEntity`
- 番茄统计：`DailyPomodoroStatsEntity`
- 模板：`TipTemplateEntity`
- 目标：`DailyGoalEntity`
- 权益：`EntitlementEntity`
- 功能开关：`FeatureFlagEntity`
- 提醒计划：`ReminderPlanEntity`
- App 网络策略：`AppNetworkControlEntity`
- MMKV：`core/mmkv/ProjectLumenMmkv.kt`
- 安全凭据：`core/security/SecureCredentialStore.kt`

动画中可以把数据层简化为三块：

- `Room`：长期设置、统计、模板、目标、权益和计划。
- `MMKV / SecureCredentialStore`：运行态缓存、功能开关、会话、安全凭据、设备安装 ID。
- `Android Services`：前台服务、通知、闹钟、摄像头采样、光照传感器、悬浮窗。

建议可视化：

- 手机下方出现三层数据底座。
- 用户点击开始提醒后，箭头从 UI 到 ViewModel，再到 RuntimeEngine、Room、NotificationService。
- 传感器事件从摄像头/lux 进入统计，再回到首页洞察卡。

## 7. 推荐 Remotion 分镜脚本

### 7.1 90 秒版本

1. `0-6s`：Project Lumen 图标和手机主屏出现。
2. `6-14s`：首次引导卡片滑动，推荐配置一键应用。
3. `14-28s`：首页状态、今日统计、目标进度、快捷操作。
4. `28-43s`：护眼提醒状态机，从工作到休息再回到工作。
5. `43-53s`：番茄钟 Focus 到 Break。
6. `53-66s`：近距离、人脸检测、眨眼、低光、遮罩。
7. `66-76s`：统计页趋势和导出。
8. `76-84s`：模板个性化。
9. `84-90s`：Shizuku、云备份、开放 API 图标收束，落到产品 slogan。

### 7.2 120 秒版本

在 90 秒版本基础上增加：

- `设置与权限中心` 8 秒。
- `远端账户/云备份/权益` 8 秒。
- `开发者调试/崩溃报告/自动更新` 8 秒。

## 8. 推荐旁白文案

可直接作为 Remotion 字幕草稿：

1. `Project Lumen turns screen time into a guided eye-care rhythm.`
2. `Start with recommended protection: reminders, statistics, monitoring, brightness, and overlays.`
3. `The home screen keeps today, the next break, and your goals in one place.`
4. `When work runs too long, Lumen moves from pre-alert to rest, then returns you to focus.`
5. `Pomodoro mode shares the same runtime, so focus and eye-care never fight each other.`
6. `Camera, blink, and ambient-light signals add context-aware protection.`
7. `Every session becomes trends, goals, and exportable reports.`
8. `Templates make breaks feel personal, not generic.`
9. `Advanced controls add Shizuku, cloud backup, diagnostics, and external API triggers.`
10. `Project Lumen is local-first eye care for Android.`

如果做中文版本：

1. `Project Lumen 把屏幕使用时间变成可感知、可调节的护眼节奏。`
2. `一键推荐配置，开启提醒、统计、监测、亮度和全屏休息。`
3. `首页汇总今天、下一次休息和目标进度。`
4. `当连续工作过久，Lumen 从预提醒进入休息，再回到专注。`
5. `番茄钟与护眼提醒共享运行态，避免多个计时器互相冲突。`
6. `摄像头、眨眼和环境光让保护更主动。`
7. `每次使用都会沉淀为趋势、目标和可导出的报告。`
8. `模板让休息界面拥有自己的颜色、图片和倒计时风格。`
9. `Shizuku、云备份、诊断和开放 API 支撑高级场景。`
10. `Project Lumen，是 Android 上本地优先的护眼系统。`

## 9. Remotion 实现注意事项

- 不要在视频里展示后端管理台或 Rust API 页面，本片只讲 Android。
- 不要直接截图源码界面作为唯一素材；建议用 Remotion 组件重建核心卡片，这样状态可控。
- 传感器和人脸检测画面使用抽象图形，避免真实隐私素材。
- 所有数据使用演示 mock，不需要连接真实 API。
- 统计图要和首页数字一致，避免前后数值冲突。
- Shizuku 和开放 API 属于高级能力，放在后段点到为止。
- 视频第一屏必须让观众看到手机和护眼核心，不要从工程架构开始。

## 10. 素材来源

可直接复用：

- 应用图标：`janus-project-icon.png`
- Android launcher 图标：`app/src/main/res/mipmap-*/ic_launcher.png`
- 文档站图标：`docs/public/lumen-icon.png`
- UI 色彩参考：`design/lumen-ui-tokens.json`
- 中文/英文文案：`app/src/main/res/values/strings.xml`, `app/src/main/res/values-zh/strings.xml`

建议新建 Remotion 素材：

- 抽象人脸检测 SVG 或 Canvas 图层。
- lux 波形。
- 手机外壳。
- 通知气泡。
- 数据流线条。
- 文件导出标签。
- Shizuku 权限盾牌和 Extra Dim 屏幕叠层。

## 11. 最小可交付动画范围

如果只做 MVP 视频，建议保留以下 6 个场景：

1. Android 主屏和五栏导航。
2. 首页状态、目标、推荐配置。
3. 护眼提醒倒计时和休息模板。
4. 近距离/眨眼/低光主动保护。
5. 统计趋势和导出。
6. 模板、Shizuku、云备份、开放 API 的快速能力墙。

这样可以在较短工期内表达 Project Lumen 的 Android 产品完整性，同时避免把视频做成工程清单。

## 12. 已落地实现路径

本动画按“原生 Android 实机演示界面 + 独立 Remotion 视频层”落地：

- Android 实机演示界面：`app/src/main/java/com/projectlumen/app/app/ProjectLumenProductDemoScreen.kt`。
- Android 演示入口：设置页中的“产品演示”，路由位于 `ProjectLumenApp.kt` 的 `Destination.PRODUCT_DEMO`。
- Remotion 动画包：`remotion/android-product-animation/`。
- Remotion 中文分镜数据：`remotion/android-product-animation/src/data/androidDemoState.ts`。
- Remotion 渲染入口：`remotion/android-product-animation/src/index.ts`，Composition ID 为 `LumenAndroidProductAnimation`。
- GitHub Actions 渲染工作流：`.github/workflows/remotion-android-product-animation.yml`。

React Native 不作为本次方案的实现路径。现有客户端是 Kotlin/Compose，并且包含前台服务、Shizuku、摄像头、光照传感器、悬浮窗、通知和本地存储等 Android 原生能力。为了 Remotion 动画把客户端抽成 React Native 会变成移动端重写任务；Remotion 本身也主要渲染 React DOM/Web 画面，不能直接复用原生 Android 行为。因此本任务只共享演示状态、产品文案和视觉 token，不共享移动端组件实现。
