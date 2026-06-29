# Project-Lumen Kotlin (Android) 端落地开发文档

## 1. 文档目标

本文件用于指导将位于本地 `D:\repohistory\Project-Lumen` 目录下的当前 Windows/WPF 版迁移到 Android 原生（Kotlin）端。

目标不是复刻桌面窗口系统，而是保留以下核心业务能力：

* 用眼提醒计时
* 预提醒
* 休息倒计时
* 番茄钟
* 周/月统计
* 提醒模板与主题配置
* 本地通知、音频提醒、导出分享

本文件直接面向本地原生开发，不讨论发布、商业化和服务端。

## 2. 迁移原则

### 2.1 必须保留的业务语义

* 每隔 `warnIntervalMinutes` 触发一次用眼提醒
* 支持提醒前 `preAlertSeconds` 的预提醒
* 支持“开始休息 / 跳过本次”
* 休息状态为秒级倒计时
* 统计每日工作时长、休息时长、跳过次数
* 支持番茄工作、短休息、长休息循环
* 支持主题、语言、声音、自定义提醒文案

### 2.2 不要照搬的桌面能力

* 托盘图标
* 多显示器全屏覆盖
* Win32 前台窗口/进程检测
* 鼠标穿透
* 桌面快捷方式
* EXE 自更新器
* WPF 拖拽式全屏布局设计器

### 2.3 手机端的替代方案

* 全屏提醒窗口改为：本地通知 + 应用内休息页
* 托盘改为：首页状态卡片 + 系统通知
* 进程白名单改为：静默时段 / 手动暂停 / 系统免打扰适配
* 桌面级常驻计时器改为：持久化时间戳 + 恢复计算

## 3. 推荐技术栈

针对 Android 原生开发，建议采用以下现代技术栈进行等价替换：

* **UI 框架**: `Jetpack Compose`
* **状态管理**: `ViewModel` + `StateFlow` / `SharedFlow`
* **路由**: `Jetpack Navigation Compose`
* **数据库**: `Room` (底层仍为 SQLite)
* **序列化**: `kotlinx.serialization` 或 `Gson`/`Moshi`
* **通知**: `NotificationManagerCompat`
* **后台任务**: `WorkManager` + `Foreground Service` (前台服务以保活长计时)
* **音频播放**: `ExoPlayer` 或 `MediaPlayer`
* **本地存储补充**: `DataStore (Preferences)`
* **图表**: `Vico` 或 `MPAndroidChart`
* **文件分享**: `Intent.ACTION_SEND`
* **文件选择**: `Storage Access Framework (SAF)`

## 4. Android 端推荐包结构

```text
com.projectlumen.app/
  di/
    AppModule.kt
    DatabaseModule.kt
  core/
    constants/
      AppConstants.kt
      NotificationIds.kt
    enums/
      ReminderAction.kt
      ReminderPhase.kt
      PomodoroPhase.kt
      AppThemeMode.kt
    errors/
      AppException.kt
    utils/
      DateTimeExtensions.kt
      DurationExtensions.kt
      Logger.kt
    services/
      ClockManager.kt
      NotificationHelper.kt
      AudioPlayer.kt
      ExportHelper.kt
    database/
      AppDatabase.kt
      entities/
        AppSettingsEntity.kt
        RuntimeStateEntity.kt
        DailyEyeStatsEntity.kt
        DailyPomodoroStatsEntity.kt
        TipTemplateEntity.kt
      daos/
        AppSettingsDao.kt
        RuntimeStateDao.kt
        DailyEyeStatsDao.kt
        DailyPomodoroStatsDao.kt
        TipTemplatesDao.kt
    datastore/
      AppPreferences.kt

  feature_home/
    presentation/
      HomeScreen.kt
      components/
        TodaySummaryCard.kt
        NextReminderCard.kt
        QuickActionsBar.kt

  feature_reminder/
    domain/
      models/
        ReminderSettings.kt
        ReminderRuntimeState.kt
      repositories/
        ReminderRepository.kt
    data/
      ReminderRepositoryImpl.kt
    presentation/
      ReminderViewModel.kt
      BreakScreen.kt
      PreAlertBottomSheet.kt
      components/
        CountdownRing.kt
        BreakActionButtons.kt

  feature_pomodoro/
    domain/
      models/
        PomodoroSettings.kt
        PomodoroRuntimeState.kt
      repositories/
        PomodoroRepository.kt
    presentation/
      PomodoroViewModel.kt
      PomodoroScreen.kt
      components/
        PomodoroTimerCard.kt
        PomodoroCycleBar.kt

  feature_statistics/
    domain/
      models/
        DailyEyeStat.kt
        DailyPomodoroStat.kt
        StatisticsSummary.kt
    presentation/
      StatisticsViewModel.kt
      StatisticsScreen.kt
      components/
        WeeklySummaryCards.kt
        MonthlyCharts.kt

  feature_settings/
    domain/
      models/
        AppSettings.kt
    presentation/
      SettingsViewModel.kt
      SettingsScreen.kt
      sections/
        GeneralSettingsSection.kt
        ReminderSettingsSection.kt
        PomodoroSettingsSection.kt
        AppearanceSettingsSection.kt
        SoundSettingsSection.kt

  feature_template/
    domain/
      models/
        TipTemplate.kt
        TipTemplateLayout.kt
    presentation/
      TemplateViewModel.kt
      TemplateScreen.kt
      TemplatePreviewScreen.kt
      components/
        TemplateSelector.kt
        TemplatePreview.kt

  ui/
    theme/
      Color.kt
      Theme.kt
      Typography.kt
    components/
      AppScaffold.kt
      AppCard.kt
      AppSwitchTile.kt

```

## 5. 每个模块的职责

### 5.1 `app` & `di`

负责应用入口 (`Application` 类)、Hilt 依赖注入初始化、全局主题、导航路由映射。

### 5.2 `core`

负责跨业务的底层能力：数据库 (Room)、通知、音频、导出、后台恢复、日志、生命周期。

### 5.3 `feature_reminder`

负责普通用眼提醒模式：工作计时、预提醒、休息倒计时、通知动作、状态恢复。

### 5.4 `feature_pomodoro`

负责番茄钟模式：专注计时、短休息/长休息、轮次推进、完成次数统计。

### 5.5 `feature_statistics`

负责周统计、月统计、趋势图、导出分享。

### 5.6 `feature_settings`

负责所有配置项的展示、编辑、保存。

### 5.7 `feature_template`

负责提醒页模板管理。手机端改为：内置模板、可替换背景、可改主文案、可改按钮文案、可改主色。

## 6. 数据库设计 (Room 实体定义)

建议数据库文件统一命名为：`project_lumen_mobile.db`。所有时长统一存储为整数秒。

### 6.1 `app_settings` (AppSettingsEntity)

单行表，固定 `id = 1`。Room 中布尔值自动映射为 INTEGER。

| 字段 | 类型 (SQLite) | 说明 |
| --- | --- | --- |
| id | INTEGER PK | 固定 1 |
| language_code | TEXT | `zh` / `en` |
| theme_mode | TEXT | `system` / `light` / `dark` |
| use_auto_dark_window | INTEGER | 是否启用时间段深色模式 (1/0) |
| auto_dark_start_minute | INTEGER | 例如 18:00 存 1080 |
| auto_dark_end_minute | INTEGER | 例如 06:00 存 360 |
| reminder_enabled | INTEGER | 是否启用普通提醒 (1/0) |
| warn_interval_minutes | INTEGER | 提醒间隔 |
| rest_duration_seconds | INTEGER | 休息时长 |
| stats_enabled | INTEGER | 是否记录统计 (1/0) |
| sound_enabled | INTEGER | 休息结束音效总开关 (1/0) |
| rest_sound_path | TEXT | 自定义休息结束音频路径 |
| pre_alert_enabled | INTEGER | 是否启用预提醒 (1/0) |
| pre_alert_seconds | INTEGER | 提前秒数 |
| pre_alert_default_action | TEXT | `start_break` / `skip_break` |
| pre_alert_title | TEXT | 预提醒标题 |
| pre_alert_subtitle | TEXT | 预提醒副标题 |
| pre_alert_message | TEXT | 预提醒正文 |
| pre_alert_icon_path | TEXT | 预提醒图标路径 |
| pre_alert_sound_enabled | INTEGER | 预提醒音效开关 (1/0) |
| ask_before_break | INTEGER | 是否先询问再休息 (1/0) |
| disable_skip | INTEGER | 是否禁用跳过 (1/0) |
| timeout_auto_break | INTEGER | 未处理时自动开始休息 (1/0) |
| pomodoro_enabled | INTEGER | 是否启用番茄模式 (1/0) |
| pomodoro_work_minutes | INTEGER | 番茄工作时长 |
| pomodoro_short_break_minutes | INTEGER | 短休息时长 |
| pomodoro_long_break_minutes | INTEGER | 长休息时长 |
| pomodoro_interactive_mode | INTEGER | 是否交互模式 (1/0) |
| pomodoro_work_start_sound_enabled | INTEGER | 工作开始音开关 (1/0) |
| pomodoro_work_end_sound_enabled | INTEGER | 工作结束音开关 (1/0) |
| pomodoro_work_start_sound_path | TEXT | 自定义工作开始音路径 |
| pomodoro_work_end_sound_path | TEXT | 自定义工作结束音路径 |
| active_tip_template_id | INTEGER | 当前提醒模板 ID |
| stats_work_image_path | TEXT | 统计页工作图可选路径 |
| stats_rest_image_path | TEXT | 统计页休息图可选路径 |
| stats_skip_image_path | TEXT | 统计页跳过图可选路径 |
| updated_at | TEXT | ISO8601 或使用 LONG 存毫秒 |

### 6.2 `runtime_state` (RuntimeStateEntity)

手机端最关键的表，用于应用恢复。单行表，固定 `id = 1`。建议 Android 中时间统一存 LONG (Unix 毫秒级时间戳)。

| 字段 | 类型 (SQLite) | 说明 |
| --- | --- | --- |
| id | INTEGER PK | 固定 1 |
| active_engine | TEXT | `idle` / `reminder` / `pomodoro` |
| reminder_phase | TEXT | `idle` / `working` / `pre_alert` / `awaiting_action` / `resting` / `paused` |
| reminder_started_at | INTEGER (LONG) | 当前普通提醒阶段开始时间戳 |
| next_pre_alert_at | INTEGER (LONG) | 下一次预提醒时间戳 |
| next_reminder_at | INTEGER (LONG) | 下一次正式提醒时间戳 |
| break_started_at | INTEGER (LONG) | 休息开始时间戳 |
| break_end_at | INTEGER (LONG) | 休息结束时间戳 |
| pomodoro_phase | TEXT | `idle` / `focus` / `short_break` / `long_break` / `awaiting_focus_confirm` |
| pomodoro_phase_started_at | INTEGER (LONG) | 当前番茄阶段开始时间戳 |
| pomodoro_phase_end_at | INTEGER (LONG) | 当前番茄阶段结束时间戳 |
| pomodoro_cycle_index | INTEGER | 当前是第几轮，1-4 |
| is_manually_paused | INTEGER | 是否手动暂停 (1/0) |
| paused_at | INTEGER (LONG) | 暂停时间戳 |
| suspended_until | INTEGER (LONG) | 用户暂停到某个时间点的时间戳 |
| last_foreground_at | INTEGER (LONG) | 上次进入前台时间戳 |
| last_background_at | INTEGER (LONG) | 上次进入后台时间戳 |
| updated_at | INTEGER (LONG) | 更新时间戳 |

### 6.3 `daily_eye_stats` (DailyEyeStatsEntity)

按日聚合，替代桌面端 `Statistics` 表。

| 字段 | 类型 (SQLite) | 说明 |
| --- | --- | --- |
| stat_date | TEXT PK | `YYYY-MM-DD` |
| working_seconds | INTEGER | 当日累计工作秒数 |
| rest_seconds | INTEGER | 当日累计休息秒数 |
| skip_count | INTEGER | 当日跳过次数 |
| completed_break_count | INTEGER | 当日完成休息次数 |
| pre_alert_count | INTEGER | 当日预提醒次数 |
| updated_at | INTEGER (LONG) | 更新时间戳 |

### 6.4 `daily_pomodoro_stats` (DailyPomodoroStatsEntity)

按日聚合，替代桌面端 `Tomatos` 表。

| 字段 | 类型 (SQLite) | 说明 |
| --- | --- | --- |
| stat_date | TEXT PK | `YYYY-MM-DD` |
| completed_tomato_count | INTEGER | 完成的完整番茄数 |
| restart_count | INTEGER | 中途重启番茄次数 |
| completed_focus_sessions | INTEGER | 完成的专注次数 |
| total_focus_seconds | INTEGER | 专注总秒数 |
| total_break_seconds | INTEGER | 番茄休息总秒数 |
| updated_at | INTEGER (LONG) | 更新时间戳 |

### 6.5 `tip_templates` (TipTemplateEntity)

用于手机端提醒页模板。

| 字段 | 类型 (SQLite) | 说明 |
| --- | --- | --- |
| id | INTEGER PK AUTO | 主键 |
| name | TEXT | 模板名 |
| is_builtin | INTEGER | 是否内置 (1/0) |
| background_type | TEXT | `solid` / `image` / `gradient` |
| background_value | TEXT | 颜色值、图片路径或 JSON |
| primary_color | TEXT | 主色 (HEX) |
| title_text | TEXT | 主标题 |
| subtitle_text | TEXT | 副标题 |
| image_path | TEXT | 顶部插图路径 |
| show_skip_button | INTEGER | 是否显示跳过按钮 (1/0) |
| layout_json | TEXT | 扩展布局 JSON 字符串 |
| sort_order | INTEGER | 排序权重 |
| created_at | INTEGER (LONG) | 创建时间戳 |
| updated_at | INTEGER (LONG) | 更新时间戳 |

`layout_json` 示例：

```json
{
  "titleStyle": { "fontSize": 28, "fontWeight": "w700" },
  "countdownStyle": { "shape": "circle", "accentColor": "#4F6BED" },
  "buttonStyle": { "radius": 14 }
}

```

### 6.6 `event_logs` (EventLogEntity) - 可选

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | INTEGER PK AUTO | 主键 |
| event_type | TEXT | 事件类型 |
| payload_json | TEXT | 负载 |
| created_at | INTEGER (LONG) | 记录时间 |

## 7. 核心枚举设计 (Kotlin)

```kotlin
enum class ReminderPhase {
    IDLE, WORKING, PRE_ALERT, AWAITING_ACTION, RESTING, PAUSED
}

enum class ReminderAction {
    START_BREAK, SKIP_BREAK
}

enum class PomodoroPhase {
    IDLE, AWAITING_FOCUS_CONFIRM, FOCUS, SHORT_BREAK, LONG_BREAK
}

enum class ActiveEngine {
    IDLE, REMINDER, POMODORO
}

```

## 8. 状态机设计

### 8.1 普通提醒状态机

**状态定义**：`IDLE`, `WORKING`, `PRE_ALERT`, `AWAITING_ACTION`, `RESTING`, `PAUSED`

**转移规则**：

* **`IDLE -> WORKING`**: 开启提醒或恢复状态判定工作时。计算 `next_pre_alert_at` 和 `next_reminder_at`，写入 `reminder_started_at`，安排本地通知（AlarmManager 或 WorkManager）。
* **`WORKING -> PRE_ALERT`**: 达到 `next_pre_alert_at`。前台显底部弹层，后台发预提醒通知。底层计时仍以 `next_reminder_at` 为准。
* **`WORKING/PRE_ALERT -> AWAITING_ACTION`**: 达到 `next_reminder_at`。前台弹休息页，后台发正式通知。若 `ask_before_break = false` 直接转 `RESTING`。
* **`AWAITING_ACTION -> RESTING`**: 用户点击“开始休息”，或 `disable_skip = true`，或超时且 `timeout_auto_break = true`。写入休息结束时间，安排休息结束通知。
* **`AWAITING_ACTION -> WORKING`**: 点击“跳过本次”。`skip_count + 1`，重新计算下一轮时刻。
* **`RESTING -> WORKING`**: 休息结束。累加时长，播放音效，进入下一轮工作。
* **`WORKING/AWAITING_ACTION/RESTING -> PAUSED`**: 手动暂停或静默。持久化暂停状态并取消待发通知。
* **`PAUSED -> WORKING`**: 恢复或静默到期。重新开始工作计时。

### 8.2 番茄钟状态机

**状态定义**：`IDLE`, `AWAITING_FOCUS_CONFIRM`, `FOCUS`, `SHORT_BREAK`, `LONG_BREAK`

**转移规则**：

* **`IDLE -> AWAITING_FOCUS_CONFIRM`**: 开启交互模式。否则直接转 `FOCUS`。
* **`AWAITING_FOCUS_CONFIRM -> FOCUS`**: 点击开始。记录起始与结束时间，播开始音效。
* **`FOCUS -> SHORT_BREAK`**: 完成 1/2/3 轮。`completed_focus_sessions + 1`，累加总长，安排短休息通知。
* **`FOCUS -> LONG_BREAK`**: 完成第 4 轮。`completed_focus_sessions + 1`，`completed_tomato_count + 1`，累加总长，安排长休息通知。
* **`SHORT_BREAK/LONG_BREAK -> FOCUS`**: 休息结束。累加休息时长。若为长休息则重置轮次为 1，否则轮次 +1。
* **`ANY -> IDLE`**: 停止番茄钟。取消通知，清空运行时状态。中途退出 `restart_count + 1`。

## 9. 时间恢复策略

手机端绝不能依赖常驻内存的协程/Timer 作为唯一真相，必须以 Room DB 中的时间戳为准。

### 9.1 应用启动恢复算法

1. 读取 `runtime_state`。
2. 判定 `active_engine`。
3. 根据当前时间 `now` 与 `phase_end_at` / `next_reminder_at` / `break_end_at` 的对比来决定状态流转。
4. 结算离线期间错过的统计。
5. 重新通过 `AlarmManager` 调度未完成的通知。

### 9.2 普通提醒恢复规则

* `phase = WORKING` 且 `now < next_pre_alert_at`: 继续工作态。
* `next_pre_alert_at <= now < next_reminder_at`: 恢复为预提醒态，重排正式通知。
* `now >= next_reminder_at` 且未进休息:
* 若不询问或自动休息：补转 `RESTING`。
* 否则：恢复为 `AWAITING_ACTION` 等待用户处理。


* `phase = RESTING`:
* 若 `now < break_end_at`: 恢复倒计时 UI。
* 若 `now >= break_end_at`: 结算休息时长，直接流转回 `WORKING`。



### 9.3 番茄钟恢复规则

* `phase = FOCUS` 且 `now >= end_at`: 补转为短/长休息。
* `phase = SHORT_BREAK/LONG_BREAK` 且 `now >= end_at`: 补转为下一轮专注。
* 交互模式下杀进程恢复：直接回退到 `AWAITING_FOCUS_CONFIRM`。

## 10. Repository 和 ViewModel 设计

### 10.1 Repository (Data Layer)

定义接口：`SettingsRepository`, `ReminderRepository`, `PomodoroRepository`, `StatisticsRepository`, `TipTemplateRepository`。
**职责**：封装 Room DAO 的读写，暴露 `Flow<T>` 供上层监听。绝不包含 Android Context UI 逻辑。

### 10.2 ViewModel (Presentation Layer)

定义：`SettingsViewModel`, `ReminderViewModel`, `PomodoroViewModel`, `StatisticsViewModel`。
**职责**：注入 Repository、`NotificationHelper`、`AudioPlayer`。执行状态机的核心转移逻辑（`updatePhase`），对外暴露 `StateFlow` 给 Compose 界面消费。

## 11. 页面清单

### 11.1 `HomeScreen`

用途：首页总览，展示当前模式、下一次提醒时间。快速开始/暂停。包含今日汇总卡片、当前运行状态卡片、快捷操作栏。

### 11.2 `BreakScreen`

用途：正式提醒触发后的全屏休息页。
状态：等待选择态、休息倒计时态。
UI 元素：倒计时圆环、模板配置的背景/文案、开始休息/跳过本次按钮。

### 11.3 `PomodoroScreen`

用途：独立番茄钟专注/休息页。包含当前阶段标题、大号倒计时、1-4 轮进度条指示器。

### 11.4 `StatisticsScreen`

用途：周/月统计展示。包含本周/本月概览、各类指标趋势图（工作、休息、跳过、番茄），以及导出和分享入口。

### 11.5 `SettingsScreen`

分为 6 个子区域 (Sections):

* **通用**：语言、主题、统计开关、静默时段。
* **提醒**：间隔、时长、是否询问、禁用跳过、自动休息。
* **预提醒**：开关、提前秒数、默认动作、文案、图标、音效。
* **番茄**：长短休息及专注时长配置、交互模式、音效开关/路径。
* **外观**：模板选择、深色模式时间段、统计页占位图。
* **通知**：系统通知权限引导与渠道设置。

### 11.6 `TemplateScreen`

用途：选择与编辑内置模板（背景色、背景图、主副标题、是否显示跳过按钮）。

### 11.7 `TemplatePreviewScreen`

用途：预览模板在“等待选择”与“倒计时”两种状态下的呈现。

### 11.8 `AboutScreen`

用途：版本号、介绍、开源链接反馈。

### 11.9 `ExportBottomSheet` (导出分享弹层)

用途：呼出 SAF 导出 CSV/XLSX，或生成图片调用 `Intent.ACTION_SEND`。

## 12. 页面与桌面功能映射

| 桌面版功能 | Android 端对应实现 |
| --- | --- |
| 托盘状态 | `HomeScreen` 状态卡片 |
| 提醒全屏窗口 | `BreakScreen` |
| 预提醒 Toast | 系统前台通知 + 底部弹层 (BottomSheet) |
| 番茄模式提示 | `PomodoroScreen` + 系统通知 |
| 统计窗口 | `StatisticsScreen` |
| 选项窗口 | `SettingsScreen` |
| 提醒窗口设计器 | `TemplateScreen` (轻量化配置) |
| 自更新器 | 移除，交由 Google Play 或应用内下载 APK |

## 13. 通知设计

预留以下通知渠道与类型：

* 普通提醒：预提醒、正式提醒、休息结束。
* 番茄钟：专注开始、专注结束、休息结束。

Android 可充分利用 `PendingIntent` 作为通知按钮的快速操作：

* `ACTION_START_BREAK`
* `ACTION_SKIP_BREAK`
* `ACTION_OPEN_BREAK_PAGE`
* `ACTION_START_FOCUS`
* `ACTION_STOP_POMODORO`

**重点**：点击通知唤起应用时，依然要通过 `runtime_state` 的恢复逻辑来重构界面，而非仅仅依赖 Intent Extras。

## 14. 统计口径

Android 端严格统一口径，全部基于秒计算聚合：

* `working_seconds`: 用户处于工作计时状态累计秒数
* `rest_seconds`: 用户处于休息倒计时状态累计秒数
* `skip_count`: 正式提醒后跳过次数
* `completed_break_count`: 正式进入休息并完成次数
* `completed_tomato_count`: 每完成 4 次专注并进入长休息后记 1 个
* UI 层显示时，`working_seconds / 3600` 转为小时，`rest_seconds / 60` 转为分钟。

## 15. 导出设计

V1 推荐实现：

* 利用 CSV 格式导出数据。
* 利用 Android Canvas 绘制图表 Bitmap 并通过原生系统分享 (ShareSheet)。

## 16. 本地开发优先级

* **Phase 1**: 建立工程、Room 建表、SettingsScreen 最小化、核心普通提醒状态机流转。
* **Phase 2**: BreakScreen、预提醒机制、HomeScreen 骨架、基础本地通知。
* **Phase 3**: 完善统计库写入、Pomodoro 完整流程。
* **Phase 4**: 图表渲染、模板配置扩展、自定义音效与导出功能。

## 17. V1 可直接开工的最小实现范围

建议最快交付的核心版本包含：

* HomeScreen
* SettingsScreen (不包含外观模板等高级设置)
* 普通提醒及 BreakScreen (默认样式)
* 预提醒 (系统弹窗为主)
* 基础统计与 Pomodoro 功能

**已补齐实现**：模板系统、统计图片分享、自定义外部音频文件加载均已接入当前 Kotlin 端：

* 模板页支持选择内置模板，并可为模板绑定自定义图片用于预览展示。
* 统计页同时支持 CSV 分享和 PNG 统计图片分享。
* 设置页支持选择休息完成音、番茄专注开始音、番茄专注结束音，并持久化外部文件读取权限。

## 18. 推荐开发顺序

1. 建立基于 Hilt + Compose 的工程结构。
2. 翻译所有 `Enum` / `Entity` 模型。
3. 完成 Room 数据库 `AppDatabase` 和 DAOs。
4. 编写 `SettingsRepository` 并绑定 UI。
5. 编写核心 `ReminderViewModel` 状态机。
6. 接入 `AlarmManager` / `WorkManager` 处理后台调度。
7. 编写 `BreakScreen` 与 `HomeScreen` 消费流。
8. 编写 `PomodoroViewModel` 及其页面。
9. 最后补充图表展示和导出。

## 19. 关键落地约束

### 19.1 Android 特有约束

* **后台存活率**：为保证倒计时和系统通知的准时性，强烈建议对计时器引擎使用 `Foreground Service` 配合常驻通知栏。
* **权限管理**：Android 13+ 需要动态申请 `POST_NOTIFICATIONS` 权限。若需要精准闹钟（不受 Doze 模式影响），需申请 `SCHEDULE_EXACT_ALARM`。

### 19.2 通用约束

* **内存不可靠**：运行时状态绝对不能只维护在 ViewModel 内存中。每次状态转移都必须同步 `UPDATE` 到 Room 的 `runtime_state` 表。
* **通知非真相**：通知被划掉或延迟触发不应影响应用内的真实时间戳倒计时。

## 20. 建议的首批 Kotlin 文件创建清单

这些文件构成了可编译的第一阶段骨架：

* `core/database/AppDatabase.kt`
* `core/database/entities/AppSettingsEntity.kt`
* `core/database/entities/RuntimeStateEntity.kt`
* `core/database/entities/DailyEyeStatsEntity.kt`
* `core/database/entities/DailyPomodoroStatsEntity.kt`
* `core/database/entities/TipTemplateEntity.kt`
* `feature_settings/domain/models/AppSettings.kt`
* `feature_reminder/domain/models/ReminderRuntimeState.kt`
* `feature_reminder/presentation/ReminderViewModel.kt`
* `feature_pomodoro/presentation/PomodoroViewModel.kt`
* `feature_home/presentation/HomeScreen.kt`
* `feature_reminder/presentation/BreakScreen.kt`

## 21. 最终建议

要想“一下子就能开发出来”，最重要的不是先画 Compose 界面，而是先把这三样钉死：

* **Room 数据表（尤其是 RuntimeState）**
* **状态机转移流（ViewModel 核心逻辑）**
* **时间戳恢复与对齐策略**

这三个底层逻辑一旦在 Kotlin 中跑通并通过了单元测试，Jetpack Compose 界面只是单纯地基于 `StateFlow` 进行声明式重组 (Recomposition) 而已。如果一开始就手搓动画和 UI，一旦生命周期被系统回收打断，后续一定会因状态丢失而返工。
