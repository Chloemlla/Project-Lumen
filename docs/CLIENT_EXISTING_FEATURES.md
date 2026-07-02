# Project Lumen 客户端现有功能清单

本文档基于当前 `app/` Android 客户端源码整理，范围包括主界面、后台服务、本地数据、开放接口和已封装但未完全暴露为主界面的底层能力。

## 1. 客户端总体形态

- 客户端是原生 Android Kotlin 应用。
- UI 使用 Jetpack Compose Material 3。
- 本地数据使用 Room 数据库。
- 支持系统、中文、英文三种语言策略。
- 主体入口为 `MainActivity`，应用级对象为 `ProjectLumenApplication`。
- 主导航包含：首页、护眼休息、番茄钟、统计、设置。
- 二级入口包含：翻译、模板、关于、开发者调试、内置 WebView。

主要源码位置：

- `app/src/main/java/com/projectlumen/app/MainActivity.kt`
- `app/src/main/java/com/projectlumen/app/ProjectLumenApplication.kt`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenApp.kt`

## 2. 首页

首页用于汇总当前运行状态和常用操作。

现有能力：

- 显示当前计时状态。
- 显示下一次提醒倒计时。
- 显示今日护眼统计摘要。
- 显示今日目标进度。
- 在翻译入口启用时显示翻译入口卡片。
- 根据当前运行状态动态显示快捷操作：
  - 启动护眼提醒。
  - 暂停护眼提醒。
  - 暂停 1 小时。
  - 恢复提醒。
  - 停止当前计时。

相关源码：

- `ProjectLumenMainScreens.kt`
- `ProjectLumenStatsAndTimerCards.kt`

## 3. 护眼提醒

护眼提醒是客户端核心功能之一，包含工作、预提醒、等待操作、休息、暂停等阶段。

现有能力：

- 启动护眼提醒。
- 暂停护眼提醒。
- 暂停护眼提醒 1 小时。
- 恢复提醒。
- 停止所有计时。
- 到点后进入休息流程。
- 支持先询问用户再休息。
- 支持直接开始休息。
- 支持跳过休息。
- 可禁用跳过休息。
- 休息结束后自动进入下一轮工作提醒。
- 支持休息开始音效。
- 支持休息结束音效。
- 支持预提醒音效。
- 支持震动。
- 支持全屏休息遮罩。
- 支持统计工作时长、休息时长、跳过次数、完成休息次数、预提醒次数、最大连续工作时长。

可配置项：

- 是否启用护眼提醒。
- 工作提醒间隔。
- 休息时长。
- 是否休息前询问。
- 是否禁用跳过。
- 是否启用预提醒。
- 预提醒提前秒数。
- 勿扰时段。
- 勿扰模式。
- 通知开关。
- 后台保活开关。

核心源码：

- `core/runtime/ReminderEngine.kt`
- `app/ProjectLumenRuntimeFeatureEntry.kt`
- `core/services/TimerForegroundService.kt`
- `core/services/NotificationService.kt`

## 4. 番茄钟

番茄钟与护眼提醒共用运行状态，同一时间只允许一个主计时引擎运行。

现有能力：

- 启动番茄钟。
- 停止番茄钟。
- 专注阶段结束后进入短休或长休。
- 默认第 4 个专注周期后进入长休。
- 休息结束后自动进入下一轮专注。
- 支持工作开始音效。
- 支持工作结束音效。
- 支持番茄钟通知。
- 支持后台前台服务保活。
- 记录完成番茄数。
- 记录完成专注次数。
- 记录总专注时长。
- 记录总休息时长。
- 记录中途停止次数。

可配置项：

- 是否启用番茄钟。
- 专注时长。
- 短休时长。
- 长休时长。
- 工作开始音效开关和自定义音频。
- 工作结束音效开关和自定义音频。
- 对应音量。

核心源码：

- `core/runtime/PomodoroEngine.kt`
- `app/ProjectLumenRuntimeFeatureEntry.kt`

## 5. 统计与趋势

统计页面集中展示护眼和番茄钟数据。

现有能力：

- 今日统计：
  - 工作时长。
  - 休息时长。
  - 跳过次数。
  - 完成休息次数。
  - 近距离警告次数。
  - 近距离持续时间。
  - 干眼警告次数。
  - 低光警告次数。
- 番茄统计：
  - 完成番茄数。
  - 专注次数。
  - 休息时间。
- 目标进度：
  - 每日休息目标。
  - 最大连续工作目标。
  - 每日番茄目标。
  - 每周活跃天数目标。
- 统计窗口：
  - 近 7 天。
  - 近 30 天。
  - 本月。
- 高级统计卡片。
- 习惯建议卡片。
- 趋势卡片。
- 无统计数据时显示空状态。

导出能力：

- 导出 CSV。
- 分享统计图片 PNG。
- 导出本月 PDF 报告。

相关源码：

- `app/ProjectLumenMainScreens.kt`
- `app/ProjectLumenStatisticsCards.kt`
- `core/services/ExportService.kt`

## 6. 通知、闹钟与后台计时

客户端使用通知、AlarmManager 和前台服务保证提醒与计时体验。

现有能力：

- 创建通知渠道：
  - 护眼提醒。
  - 番茄钟。
  - 状态通知。
  - 近距离/视觉监测。
- 使用 AlarmManager 安排：
  - 预提醒。
  - 休息到期。
  - 休息结束。
  - 番茄钟阶段结束。
- 支持精确闹钟权限检测。
- 支持通知权限检测。
- 支持全屏提醒通知。
- 通知内操作：
  - 开始休息。
  - 跳过休息。
  - 停止计时。
- 前台服务保持计时运行。
- 屏幕关闭时暂停计时推进，屏幕重新点亮后平移计时目标，避免把熄屏时间误计为使用时间。
- 前台服务被系统移除或重启时记录诊断时间。
- 开机广播恢复相关运行能力。

相关源码：

- `core/services/NotificationService.kt`
- `core/services/TimerForegroundService.kt`
- `core/services/AlarmReceiver.kt`
- `core/services/ReminderActionReceiver.kt`
- `core/services/BootReceiver.kt`
- `core/services/TimerReconciliationWorker.kt`

## 7. 近距离检测

客户端支持基于前置摄像头和 ML Kit 的近距离检测。

现有能力：

- 使用前置摄像头采样人脸。
- 使用 ML Kit Face Detection 获取人脸框、眼睛、睁眼概率等信息。
- 可选使用 Face Mesh/轮廓拓扑数据用于调试或上报。
- 支持距离校准：
  - 眼距像素基线。
  - 脸宽百分比基线。
- 支持基于基线倍率判断是否过近。
- 没有基线时可按脸宽阈值判断。
- 记录最近人脸时间。
- 记录近距离比例。
- 记录近距离开始时间。
- 记录近距离累计秒数。
- 冷却时间内避免重复警告。
- 触发近距离通知。
- 触发本地 Toast 提示。
- 在超过严格阈值时触发全屏护眼遮罩。
- 支持 WorkManager 周期性调度采样。
- 支持用户解锁、配置变化等事件触发采样。

可配置项：

- 是否启用近距离监测。
- 校准基线。
- 距离倍率阈值。
- 检查间隔。
- 单次采样秒数。
- 脸宽阈值。
- 警告冷却时间。
- 全屏遮罩严格距离阈值。

相关源码：

- `core/proximity/ProximityDetectionWorker.kt`
- `core/proximity/ProximityDetectionService.kt`
- `core/proximity/ProximityCameraSampler.kt`
- `core/proximity/FaceDistanceAnalyzer.kt`
- `core/proximity/ProximityTriggerGate.kt`

## 8. 眨眼与干眼风险监测

眨眼监测复用摄像头采样结果。

现有能力：

- 读取左右眼睁眼概率。
- 识别闭眼到睁眼的眨眼转换。
- 估算每分钟眨眼次数。
- 记录最近眨眼时间。
- 记录最近睁眼概率。
- 长时间未眨眼且眼睛持续睁开时触发干眼警告。
- 触发干眼通知和 Toast。
- 触发全屏眨眼休息遮罩。
- 统计干眼警告次数。

可配置项：

- 是否启用眨眼监测。
- 未眨眼阈值秒数。
- 警告冷却时间。

相关源码：

- `core/proximity/ProximityDetectionService.kt`
- `core/proximity/FaceDistanceAnalyzer.kt`

## 9. 环境光与亮度保护

客户端支持环境光监测和自动亮度调节。

现有能力：

- 使用光照传感器读取 lux。
- 记录当前 lux。
- 判断环境是否过暗。
- 低光环境触发通知和 Toast。
- 统计低光警告次数。
- 支持自动亮度调节。
- 自动亮度按 lux 在最小/最大亮度百分比之间映射。
- 普通模式使用 `Settings.System.SCREEN_BRIGHTNESS`。
- Shizuku 原生护眼开启时优先使用 Shizuku 设置系统亮度。

可配置项：

- 是否启用环境光监测。
- 低光 lux 阈值。
- 是否启用自动亮度。
- 自动亮度最小值。
- 自动亮度最大值。

相关源码：

- `core/light/LightMonitorService.kt`

## 10. 全屏护眼遮罩

客户端支持系统级悬浮窗护眼遮罩。

现有能力：

- 显示全屏遮罩。
- 显示标题、提示文案和倒计时。
- 隐藏系统状态栏和导航栏。
- 到时自动关闭。
- 可用于：
  - 护眼休息。
  - 外部 API 触发休息。
  - 近距离严格警告。
  - 干眼警告。

前置权限：

- `SYSTEM_ALERT_WINDOW` 悬浮窗权限。

相关源码：

- `core/overlay/EyeProtectionOverlayService.kt`

## 11. 模板与个性化外观

客户端内置休息模板，并支持一定范围内自定义。

现有能力：

- 显示当前模板预览。
- 列出所有模板。
- 选择模板。
- 区分免费模板和 PRO 模板。
- PRO 未启用时锁定高级模板。
- 为模板选择自定义图片。
- 清除自定义图片。
- 编辑模板标题。
- 编辑模板副标题。
- 设置模板是否显示跳过按钮。
- 设置倒计时样式：
  - 圆环。
  - 进度条。
  - 纯数字。
- 动态切换模板背景和主色。
- 使用模板外观时会强制浅色模式并关闭自动深色窗口。

相关源码：

- `app/ProjectLumenTemplateScreens.kt`
- `app/ProjectLumenTemplatesFeatureEntry.kt`
- `app/DefaultTipTemplates.kt`

## 12. 主题、语言与基础设置

现有能力：

- 语言选择：
  - 跟随系统。
  - 中文。
  - 英文。
- 主题选择：
  - 跟随系统。
  - 浅色。
  - 深色。
- Android 12+ 动态壁纸取色。
- 自动深色窗口：
  - 设置开始时间。
  - 设置结束时间。
- 统计开关。
- 翻译入口开关。
- 自动更新检查开关。
- 关于页入口。
- 模板页入口。

相关源码：

- `app/ProjectLumenSettingsScreen.kt`
- `core/i18n/LocaleController.kt`
- `ui/theme/Theme.kt`

## 13. 每日目标

现有能力：

- 设置每日休息次数目标。
- 设置最大连续工作分钟目标。
- 设置每日番茄目标。
- 设置每周活跃天数目标。
- 首页展示目标进度。

相关源码：

- `app/ProjectLumenSettingsScreen.kt`
- `app/ProjectLumenStatsAndTimerCards.kt`
- `core/database/entities/DailyGoalEntity.kt`

## 14. 声音与震动

现有能力：

- 全局声音开关。
- 震动开关。
- 预提醒声音开关。
- 休息开始声音开关。
- 番茄钟工作开始声音开关。
- 番茄钟工作结束声音开关。
- 设置各类音量：
  - 预提醒音量。
  - 休息开始音量。
  - 休息结束音量。
  - 番茄钟开始音量。
  - 番茄钟结束音量。
- 选择自定义音频文件：
  - 休息结束音频。
  - 休息开始音频。
  - 番茄钟工作开始音频。
  - 番茄钟工作结束音频。
- 清除自定义音频。

相关源码：

- `app/ProjectLumenSettingsScreen.kt`
- `core/services/AudioService.kt`

## 15. 数据导出、分享与备份

现有能力：

- 分享统计 CSV。
- 分享统计 PNG 图片。
- 分享本月 PDF 报告。
- 导出完整 JSON 备份。
- 导入 JSON 备份。
- 导入前预览备份摘要：
  - schema 版本。
  - 模板数量。
  - 护眼统计天数。
  - 番茄钟统计天数。
  - 权益数量。
  - 提醒计划数量。
- 导入时恢复或合并：
  - 设置。
  - 每日目标。
  - 模板。
  - 护眼统计。
  - 番茄钟统计。
  - 权益。
  - 功能开关。
  - 提醒计划。

相关源码：

- `app/ProjectLumenSharingFeatureEntry.kt`
- `app/ProjectLumenBackupFeatureEntry.kt`
- `core/services/ExportService.kt`
- `core/services/DataBackupService.kt`

## 16. 翻译

翻译是二级入口，可从首页卡片进入，也可在设置中隐藏入口。

现有能力：

- 输入待翻译文本。
- 文本最长 5000 字符。
- 源语言：
  - 自动。
  - 中文。
  - 英文。
  - 日文。
  - 韩文。
- 目标语言：
  - 中文。
  - 英文。
  - 日文。
  - 韩文。
- 拉取翻译服务配置。
- 服务不可用时显示状态。
- 执行翻译。
- 显示翻译结果。
- 显示候选译文。
- 复制翻译结果。
- 手动刷新翻译服务状态。

默认翻译服务：

- `https://tts.chloemlla.com`

相关源码：

- `app/ProjectLumenTranslationScreen.kt`
- `core/api/ProjectLumenTranslationApiClient.kt`
- `core/api/ProjectLumenApiConfig.kt`

## 17. 关于页与内置 WebView

现有能力：

- 显示应用版本。
- 显示应用说明。
- 打开源码链接。
- 打开最新 Release 链接。
- 打开外部链接前显示确认弹窗。
- 使用内置 WebView 展示 Project Lumen 相关页面。
- WebView 支持复制 URL。
- WebView 支持打开外部浏览器。
- 关于页连续点击版本或底部品牌 7 次可解锁开发者模式。

相关源码：

- `app/ProjectLumenAboutAndDialogs.kt`
- `app/ProjectLumenWebViewScreen.kt`

## 18. 自动更新

现有能力：

- 启动后可自动检查更新。
- 设置页可手动检查更新。
- 通过 GitHub Release API 获取最新版本。
- 通过语义版本或发布时间判断是否有更新。
- 根据设备 ABI 选择最合适的 APK 资产。
- 只接受带 SHA256 校验值的 APK。
- 下载 APK 时显示进度。
- 下载后校验 SHA256。
- 校验失败会删除文件并报错。
- 校验通过后调用系统安装器。
- 未授权安装未知来源应用时引导到系统授权页。
- 没有可直接下载资产时打开 Release 页面。

默认 Release API：

- `https://api.github.com/repos/Chloemlla/Project-Lumen/releases/latest`

相关源码：

- `core/update/UpdateChecker.kt`
- `core/update/UpdateInstaller.kt`
- `app/ProjectLumenAboutAndDialogs.kt`

## 19. Shizuku 高级模式

Shizuku 高级模式用于增强系统状态感知、采样守护、服务恢复和原生护眼能力。

现有能力：

- 显示 Shizuku 状态。
- 请求 Shizuku 授权。
- 刷新 Shizuku 状态。
- 显示服务版本、UID、最近检查时间。
- 显示前台应用上下文。
- 判断是否应延后摄像头采样。
- 快捷预设：
  - 启用核心能力。
  - 启用智能守护。
  - 启用舒适护眼。
  - 关闭原生护眼。
- 采样守护：
  - 上下文感知采样。
  - 服务恢复。
  - 屏幕关闭守护。
  - 低电量守护。
  - 省电模式守护。
  - 勿扰模式守护。
  - 温度守护。
  - 摄像头隐私守护。
- 原生护眼：
  - 色温。
  - 系统亮度。
  - Extra Dim。
  - Extra Dim 强度。
- 诊断上传：
  - 诊断遥测上传开关。
  - 崩溃报告上传开关。
  - 应用清单上传开关。
  - 立即上传诊断。

相关源码：

- `app/ProjectLumenShizukuSettingsSection.kt`
- `core/shizuku/ShizukuCapabilityManager.kt`
- `core/services/ShizukuResilienceWorker.kt`

## 20. 开发者调试

开发者模式默认隐藏，可在关于页连续点击解锁。

现有能力：

- 开启/关闭开发者模式。
- 开启/关闭实时调试覆盖层。
- 开启/关闭摄像头调试预览。
- 显示调试指标：
  - AI 推理耗时。
  - 摄像头延迟。
  - 人脸宽度。
  - 人脸比例。
- 调试触发器配置：
  - Tick 间隔。
  - 时间触发。
  - 解锁触发。
  - 静止触发。
  - 抖动抑制。
- 系统状态：
  - 电池优化状态。
  - 前台服务运行时长。
  - 前台服务最近重启时间。
  - 最近任务移除时间。
  - Shizuku 状态。
  - Shizuku 前台上下文。
  - Shizuku 系统守护状态。
- 支持打开电池优化设置。
- 支持模拟低内存。
- API 安全诊断：
  - API Base URL。
  - 明文流量状态。
  - API 证书 Pin 状态。
  - 翻译服务 Base URL。
  - 翻译证书 Pin 状态。
  - 请求签名状态。
  - Play Integrity 状态。
  - 安全凭据状态。
  - WebView Bridge 状态。
- 原始传感器显示：
  - 当前 lux。
  - lux 曲线。
  - pitch。
  - roll。
  - yaw。
  - 加速度。
- 崩溃页预览。

相关源码：

- `app/ProjectLumenDeveloperDebugScreen.kt`
- `core/debug/DeveloperDebugOverlayService.kt`
- `core/debug/DeveloperDebugFrameStore.kt`

## 21. 崩溃报告

现有能力：

- 应用启动时安装全局未捕获异常处理器。
- 崩溃时保存本地崩溃报告。
- 下次启动时优先展示崩溃报告页面。
- 用户继续后可清除已存报告。
- 开发者页可生成模拟崩溃报告预览。
- 诊断上传开启时可将崩溃报告作为遥测的一部分上传。

相关源码：

- `ProjectLumenApplication.kt`
- `core/crash/CrashReport.kt`
- `core/crash/CrashReportStore.kt`
- `app/ProjectLumenCrashReportScreen.kt`

## 22. 开放 API

客户端通过 AIDL 和 Intent 向外部应用提供受控访问能力。

AIDL 方法：

- `getEyeFatigueLevel()`：返回 0 到 100 的眼疲劳等级。
- `getContinuousScreenTime()`：返回连续屏幕使用时间。
- `isRestingNow()`：判断当前是否正在休息。
- `startFocusSession(tag, durationMs)`：启动外部专注会话。
- `stopFocusSession()`：停止外部专注会话。
- `triggerEyeRelaxation()`：触发眼部放松。

Intent 能力：

- 打开仪表盘。
- 触发休息。
- 打开视觉监控/开发者相关页面。

权限与安全：

- `com.project.lumen.permission.ACCESS_LUMEN_CORE`
- `com.project.lumen.permission.TRIGGER_LUMEN_CONTROL`
- 可配置可信调用方签名 SHA256。
- 同签名应用可通过检查。

相关源码：

- `app/src/main/aidl/com/project/lumen/open/ILumenOpenApi.aidl`
- `openapi/LumenOpenService.kt`
- `openapi/LumenOpenRuntimeController.kt`
- `openapi/LumenOpenContracts.kt`
- `openapi/ExternalActivities.kt`

## 23. 远端 API 客户端能力

客户端已封装 Project Lumen 远端 API，但当前主界面未看到完整账号中心或同步管理入口。

已封装能力：

- 健康检查。
- 邮箱登录开始。
- 邮箱验证码登录验证。
- 刷新会话。
- 获取当前用户信息。
- 拉取权益。
- 校验 Google 购买。
- 拉取同步变更。
- 推送同步变更。
- 上传护眼遥测。
- 上传人脸分析帧。
- 上传云备份。
- 拉取最新云备份。

安全相关：

- 请求签名。
- 可选证书 Pin。
- 登录、购买、遥测等敏感接口可携带 Play Integrity Token。
- Access Token 使用安全凭据存储读取。

默认 API：

- `https://eye.chloemlla.com/api`

相关源码：

- `core/api/ProjectLumenApiClient.kt`
- `core/api/ProjectLumenApiConfig.kt`
- `core/security/ProjectLumenRequestSigner.kt`
- `core/security/ProjectLumenIntegrityProvider.kt`
- `core/security/SecureCredentialStore.kt`

## 24. 遥测

客户端支持上传护眼、环境、设备、调试相关遥测。

现有能力：

- 上传当前护眼健康快照。
- 上传距离违规信息。
- 上传眨眼指标。
- 上传休息遵守率。
- 上传环境上下文：
  - lux 等级。
  - 姿态状态。
  - 场景状态。
- 上传设备信息：
  - 厂商。
  - 型号。
  - Android 版本。
  - SDK。
  - 前摄分辨率。
  - 应用版本。
- 上传校准锚点。
- 上传 AI 性能：
  - 人脸检测耗时。
  - 摄像头延迟。
  - 后台服务被杀计数。
- 开发者诊断开启后上传：
  - 传感器扰动。
  - 崩溃日志。
  - Shizuku 设备诊断。
  - 可选用户应用清单。

上传条件：

- 需要 Access Token。
- 未强制上传时有最小上传间隔。
- 统计或诊断遥测开关必须满足对应条件。

相关源码：

- `core/telemetry/EyeCareTelemetryReporter.kt`
- `core/api/ProjectLumenTelemetryModels.kt`
- `core/api/ProjectLumenTelemetryJson.kt`

## 25. 本地数据库

客户端本地数据库当前版本为 16。

表结构覆盖：

- `app_settings`：应用设置。
- `runtime_state`：当前运行状态。
- `daily_eye_stats`：每日护眼统计。
- `daily_pomodoro_stats`：每日番茄钟统计。
- `tip_templates`：提示模板。
- `daily_goals`：每日目标。
- `feature_flags`：功能开关。
- `entitlements`：权益。
- `reminder_plans`：提醒计划。

数据库迁移覆盖：

- 自动更新开关。
- 音频和自定义文件。
- 后台保活和近距离检测。
- 商业化与个性化字段。
- 眨眼、环境光、全屏遮罩。
- 开发者调试。
- Shizuku 高级模式。
- 动态外观。
- 翻译入口。
- Shizuku 原生护眼。
- 诊断遥测授权。

相关源码：

- `core/database/AppDatabase.kt`
- `core/database/entities/`
- `core/database/daos/`

## 26. 权限使用

客户端声明和使用的主要权限包括：

- 通知权限。
- 精确闹钟权限。
- 摄像头权限。
- 悬浮窗权限。
- 写系统设置权限。
- 全屏 Intent 权限。
- 前台服务权限。
- 摄像头前台服务权限。
- 请求忽略电池优化权限。
- 开机广播权限。
- 网络权限。
- 安装 APK 权限。
- 震动权限。
- 使用情况统计权限。
- Shizuku Provider 相关权限。

相关源码：

- `app/src/main/AndroidManifest.xml`

## 27. 当前已具备但主界面暴露不完整的能力

以下能力在客户端底层已经存在，但当前主导航或设置页中没有看到完整的用户流程：

- 邮箱登录完整 UI。
- 账号信息页面。
- 云同步管理页面。
- Google 购买/订阅完整 UI。
- 云备份上传/恢复完整 UI。
- 人脸分析帧上传的用户入口。
- 远端功能开关管理 UI。
- 提醒计划管理 UI。

这些能力的底层模型、数据库表、API 客户端或导入导出字段已经存在，后续可以补齐界面和业务流程。

## 28. 总结

当前客户端已经不仅是一个基础计时器，而是一个以护眼提醒为核心的本地优先 Android 应用。已实现的主要功能包括：

- 护眼休息提醒。
- 番茄钟。
- 统计、目标和导出。
- 近距离检测。
- 眨眼/干眼风险检测。
- 环境光与亮度保护。
- 全屏护眼遮罩。
- 模板与外观个性化。
- 翻译入口。
- 本地备份导入导出。
- 自动更新。
- Shizuku 高级能力。
- 开发者诊断。
- 崩溃报告。
- 开放 API。
- 远端 API 客户端封装。
- 遥测上传。

