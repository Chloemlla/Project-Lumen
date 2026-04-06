# Project-Lumen Flutter 手机端落地开发文档

## 1. 文档目标

本文件用于指导将当前 Windows/WPF 版 Project-Lumen 迁移到 Flutter 手机端。

目标不是复刻桌面窗口系统，而是保留以下核心业务能力：

- 用眼提醒计时
- 预提醒
- 休息倒计时
- 番茄钟
- 周/月统计
- 提醒模板与主题配置
- 本地通知、音频提醒、导出分享

本文件直接面向本地开发，不讨论发布、商业化和服务端。

## 2. 迁移原则

### 2.1 必须保留的业务语义

- 每隔 `warnIntervalMinutes` 触发一次用眼提醒
- 支持提醒前 `preAlertSeconds` 的预提醒
- 支持“开始休息 / 跳过本次”
- 休息状态为秒级倒计时
- 统计每日工作时长、休息时长、跳过次数
- 支持番茄工作、短休息、长休息循环
- 支持主题、语言、声音、自定义提醒文案

### 2.2 不要照搬的桌面能力

- 托盘图标
- 多显示器全屏覆盖
- Win32 前台窗口/进程检测
- 鼠标穿透
- 桌面快捷方式
- EXE 自更新器
- WPF 拖拽式全屏布局设计器

### 2.3 手机端的替代方案

- 全屏提醒窗口改为：本地通知 + 应用内休息页
- 托盘改为：首页状态卡片 + 系统通知
- 进程白名单改为：静默时段 / 手动暂停 / 系统免打扰适配
- 桌面级常驻计时器改为：持久化时间戳 + 恢复计算

## 3. 推荐技术栈

- Flutter: `stable`
- 状态管理: `riverpod`
- 路由: `go_router`
- 数据库: `drift` 或 `sqflite`
- 模型生成: `freezed` + `json_serializable`
- 通知: `flutter_local_notifications`
- 后台任务: `workmanager`
- 音频播放: `just_audio`
- 本地存储补充: `shared_preferences`
- 图表: `fl_chart`
- 文件分享: `share_plus`
- 文件选择: `file_picker`
- 国际化: `intl`

建议优先 `drift`，因为数据结构明确、可迁移性强、类型安全高。

## 4. Flutter 端推荐目录结构

```text
lib/
  app/
    app.dart
    bootstrap.dart
    router/
      app_router.dart
      route_names.dart
    theme/
      app_theme.dart
      app_colors.dart
      app_typography.dart
    l10n/
      l10n.dart

  core/
    constants/
      app_constants.dart
      notification_ids.dart
    enums/
      reminder_action.dart
      reminder_phase.dart
      pomodoro_phase.dart
      app_theme_mode.dart
    errors/
      app_exception.dart
    utils/
      date_time_x.dart
      duration_x.dart
      logger.dart
    services/
      clock_service.dart
      app_lifecycle_service.dart
      notification_service.dart
      audio_service.dart
      export_service.dart
      share_service.dart
      permission_service.dart
      background_sync_service.dart
    storage/
      db/
        app_database.dart
        tables/
          app_settings_table.dart
          runtime_state_table.dart
          daily_eye_stats_table.dart
          daily_pomodoro_stats_table.dart
          tip_templates_table.dart
        daos/
          app_settings_dao.dart
          runtime_state_dao.dart
          daily_eye_stats_dao.dart
          daily_pomodoro_stats_dao.dart
          tip_templates_dao.dart
      prefs/
        app_prefs.dart

  features/
    home/
      presentation/
        pages/
          home_page.dart
        widgets/
          today_summary_card.dart
          next_reminder_card.dart
          quick_actions_bar.dart

    reminder/
      domain/
        models/
          reminder_settings.dart
          reminder_runtime_state.dart
        repositories/
          reminder_repository.dart
      application/
        reminder_controller.dart
        reminder_recovery_service.dart
        reminder_scheduler.dart
      presentation/
        pages/
          break_page.dart
          pre_alert_sheet.dart
        widgets/
          countdown_ring.dart
          break_action_buttons.dart

    pomodoro/
      domain/
        models/
          pomodoro_settings.dart
          pomodoro_runtime_state.dart
      application/
        pomodoro_controller.dart
        pomodoro_scheduler.dart
      presentation/
        pages/
          pomodoro_page.dart
        widgets/
          pomodoro_timer_card.dart
          pomodoro_cycle_bar.dart

    statistics/
      domain/
        models/
          daily_eye_stat.dart
          daily_pomodoro_stat.dart
          statistics_summary.dart
      application/
        statistics_service.dart
      presentation/
        pages/
          statistics_page.dart
        widgets/
          weekly_summary_cards.dart
          monthly_eye_chart.dart
          monthly_pomodoro_chart.dart

    settings/
      domain/
        models/
          app_settings.dart
      application/
        settings_controller.dart
      presentation/
        pages/
          settings_page.dart
        sections/
          general_settings_section.dart
          reminder_settings_section.dart
          pomodoro_settings_section.dart
          appearance_settings_section.dart
          sound_settings_section.dart
          notification_settings_section.dart

    tip_template/
      domain/
        models/
          tip_template.dart
          tip_template_layout.dart
      application/
        tip_template_controller.dart
      presentation/
        pages/
          tip_template_page.dart
          tip_template_preview_page.dart
        widgets/
          template_selector.dart
          template_preview.dart

    about/
      presentation/
        pages/
          about_page.dart

  shared/
    widgets/
      app_scaffold.dart
      app_card.dart
      app_switch_tile.dart
      app_slider_tile.dart
      app_section_title.dart
      app_empty_view.dart
      app_loading_view.dart
```

## 5. 每个模块的职责

### 5.1 `app`

负责应用入口、主题、路由、语言初始化。

### 5.2 `core`

负责跨业务的底层能力：

- 数据库
- 通知
- 音频
- 导出
- 后台恢复
- 日志
- 生命周期

### 5.3 `features/reminder`

负责普通用眼提醒模式：

- 工作计时
- 预提醒
- 休息倒计时
- 通知动作
- 状态恢复

### 5.4 `features/pomodoro`

负责番茄钟模式：

- 专注计时
- 短休息/长休息
- 轮次推进
- 完成次数统计

### 5.5 `features/statistics`

负责：

- 周统计
- 月统计
- 趋势图
- 导出分享

### 5.6 `features/settings`

负责所有配置项的展示、编辑、保存。

### 5.7 `features/tip_template`

负责提醒页模板管理。

手机端不建议做 WPF 那种自由拖拽设计器，建议改成：

- 内置模板
- 可替换背景
- 可改主文案
- 可改按钮文案
- 可改主色

## 6. 数据库设计

建议数据库文件统一命名为：

- `project_lumen_mobile.db`

建议所有时长统一存储为整数秒，避免桌面版那种小时/分钟混用导致精度和统计口径混乱。

---

## 6.1 `app_settings`

单行表，固定 `id = 1`。

字段建议：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | INTEGER PK | 固定 1 |
| language_code | TEXT | `zh` / `en` |
| theme_mode | TEXT | `system` / `light` / `dark` |
| use_auto_dark_window | INTEGER | 是否启用时间段深色模式 |
| auto_dark_start_minute | INTEGER | 例如 18:00 存 1080 |
| auto_dark_end_minute | INTEGER | 例如 06:00 存 360 |
| reminder_enabled | INTEGER | 是否启用普通提醒 |
| warn_interval_minutes | INTEGER | 提醒间隔 |
| rest_duration_seconds | INTEGER | 休息时长 |
| stats_enabled | INTEGER | 是否记录统计 |
| sound_enabled | INTEGER | 休息结束音效总开关 |
| rest_sound_path | TEXT | 自定义休息结束音频 |
| pre_alert_enabled | INTEGER | 是否启用预提醒 |
| pre_alert_seconds | INTEGER | 提前秒数 |
| pre_alert_default_action | TEXT | `start_break` / `skip_break` |
| pre_alert_title | TEXT | 预提醒标题 |
| pre_alert_subtitle | TEXT | 预提醒副标题 |
| pre_alert_message | TEXT | 预提醒正文 |
| pre_alert_icon_path | TEXT | 预提醒图标 |
| pre_alert_sound_enabled | INTEGER | 预提醒音效 |
| ask_before_break | INTEGER | 是否先询问再休息 |
| disable_skip | INTEGER | 是否禁用跳过 |
| timeout_auto_break | INTEGER | 未处理时自动开始休息 |
| pomodoro_enabled | INTEGER | 是否启用番茄模式 |
| pomodoro_work_minutes | INTEGER | 番茄工作时长 |
| pomodoro_short_break_minutes | INTEGER | 短休息 |
| pomodoro_long_break_minutes | INTEGER | 长休息 |
| pomodoro_interactive_mode | INTEGER | 是否交互模式 |
| pomodoro_work_start_sound_enabled | INTEGER | 工作开始音 |
| pomodoro_work_end_sound_enabled | INTEGER | 工作结束音 |
| pomodoro_work_start_sound_path | TEXT | 自定义工作开始音 |
| pomodoro_work_end_sound_path | TEXT | 自定义工作结束音 |
| active_tip_template_id | INTEGER | 当前提醒模板 |
| stats_work_image_path | TEXT | 统计页工作图可选 |
| stats_rest_image_path | TEXT | 统计页休息图可选 |
| stats_skip_image_path | TEXT | 统计页跳过图可选 |
| updated_at | TEXT | ISO8601 |

---

## 6.2 `runtime_state`

该表是手机端最关键的一张表，用于应用恢复。

单行表，固定 `id = 1`。

字段建议：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | INTEGER PK | 固定 1 |
| active_engine | TEXT | `idle` / `reminder` / `pomodoro` |
| reminder_phase | TEXT | `idle` / `working` / `pre_alert` / `awaiting_action` / `resting` / `paused` |
| reminder_started_at | TEXT | 当前普通提醒阶段开始时间 |
| next_pre_alert_at | TEXT | 下一次预提醒时间 |
| next_reminder_at | TEXT | 下一次正式提醒时间 |
| break_started_at | TEXT | 休息开始时间 |
| break_end_at | TEXT | 休息结束时间 |
| pomodoro_phase | TEXT | `idle` / `focus` / `short_break` / `long_break` / `awaiting_focus_confirm` |
| pomodoro_phase_started_at | TEXT | 当前番茄阶段开始时间 |
| pomodoro_phase_end_at | TEXT | 当前番茄阶段结束时间 |
| pomodoro_cycle_index | INTEGER | 当前是第几轮，1-4 |
| is_manually_paused | INTEGER | 是否手动暂停 |
| paused_at | TEXT | 暂停时间 |
| suspended_until | TEXT | 用户暂停到某个时间点 |
| last_foreground_at | TEXT | 上次进入前台 |
| last_background_at | TEXT | 上次进入后台 |
| updated_at | TEXT | 更新时间 |

这张表用于解决：

- 应用杀进程后恢复
- 通知点击后恢复
- 重启手机后恢复
- iOS 无法长期后台运行时的状态重建

---

## 6.3 `daily_eye_stats`

按日聚合，替代桌面端 `Statistics` 表。

字段建议：

| 字段 | 类型 | 说明 |
|---|---|---|
| stat_date | TEXT PK | `YYYY-MM-DD` |
| working_seconds | INTEGER | 当日累计工作秒数 |
| rest_seconds | INTEGER | 当日累计休息秒数 |
| skip_count | INTEGER | 当日跳过次数 |
| completed_break_count | INTEGER | 当日完成休息次数 |
| pre_alert_count | INTEGER | 当日预提醒次数 |
| updated_at | TEXT | 更新时间 |

说明：

- 桌面版把工作时长按小时存浮点、休息时长按分钟存浮点，手机端统一改为秒。
- 页面展示时再格式化为小时/分钟。

---

## 6.4 `daily_pomodoro_stats`

按日聚合，替代桌面端 `Tomatos` 表。

字段建议：

| 字段 | 类型 | 说明 |
|---|---|---|
| stat_date | TEXT PK | `YYYY-MM-DD` |
| completed_tomato_count | INTEGER | 完成的番茄数 |
| restart_count | INTEGER | 中途重启番茄次数 |
| completed_focus_sessions | INTEGER | 完成专注次数 |
| total_focus_seconds | INTEGER | 专注总秒数 |
| total_break_seconds | INTEGER | 番茄休息总秒数 |
| updated_at | TEXT | 更新时间 |

---

## 6.5 `tip_templates`

用于手机端提醒页模板。

字段建议：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | INTEGER PK AUTOINCREMENT | 主键 |
| name | TEXT | 模板名 |
| is_builtin | INTEGER | 是否内置 |
| background_type | TEXT | `solid` / `image` / `gradient` |
| background_value | TEXT | 颜色值、图片路径或 JSON |
| primary_color | TEXT | 主色 |
| title_text | TEXT | 主标题 |
| subtitle_text | TEXT | 副标题 |
| image_path | TEXT | 顶部插图 |
| show_skip_button | INTEGER | 是否显示跳过按钮 |
| layout_json | TEXT | 扩展布局 JSON |
| sort_order | INTEGER | 排序 |
| created_at | TEXT | 创建时间 |
| updated_at | TEXT | 更新时间 |

`layout_json` 建议只做轻量扩展，不做像素级绝对定位编辑器。

示例：

```json
{
  "titleStyle": {
    "fontSize": 28,
    "fontWeight": "w700"
  },
  "countdownStyle": {
    "shape": "circle",
    "accentColor": "#4F6BED"
  },
  "buttonStyle": {
    "radius": 14
  }
}
```

---

## 6.6 可选表 `event_logs`

V1 可不做。

如果后续需要追查状态错乱或做详细导出，可增加：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | INTEGER PK AUTOINCREMENT | 主键 |
| event_type | TEXT | 事件类型 |
| payload_json | TEXT | 负载 |
| created_at | TEXT | 时间 |

## 7. 核心枚举设计

### 7.1 `ReminderPhase`

```dart
enum ReminderPhase {
  idle,
  working,
  preAlert,
  awaitingAction,
  resting,
  paused,
}
```

### 7.2 `ReminderAction`

```dart
enum ReminderAction {
  startBreak,
  skipBreak,
}
```

### 7.3 `PomodoroPhase`

```dart
enum PomodoroPhase {
  idle,
  awaitingFocusConfirm,
  focus,
  shortBreak,
  longBreak,
}
```

### 7.4 `ActiveEngine`

```dart
enum ActiveEngine {
  idle,
  reminder,
  pomodoro,
}
```

## 8. 状态机设计

---

## 8.1 普通提醒状态机

### 状态定义

- `idle`: 未启动
- `working`: 正在累计工作时间
- `preAlert`: 预提醒已发出，等待正式提醒到来
- `awaitingAction`: 正式提醒已触发，等待用户选择
- `resting`: 休息倒计时中
- `paused`: 手动暂停或临时静默

### 事件定义

- `appStarted`
- `startReminder`
- `preAlertDue`
- `reminderDue`
- `userTapStartBreak`
- `userTapSkipBreak`
- `actionTimeout`
- `breakFinished`
- `pauseReminder`
- `resumeReminder`
- `suspendUntil`
- `switchToPomodoro`

### 转移规则

#### `idle -> working`

触发条件：

- 用户开启普通提醒模式
- 应用恢复时判定为应处于工作状态

动作：

- 写入 `reminder_started_at`
- 计算 `next_pre_alert_at`
- 计算 `next_reminder_at`
- 安排本地通知

#### `working -> preAlert`

触发条件：

- 当前时间达到 `next_pre_alert_at`

动作：

- 前台：显示预提醒底部弹层或弹窗
- 后台：发送预提醒通知
- 累加 `pre_alert_count`

注意：

- `preAlert` 只是表现层状态，底层计时仍以 `next_reminder_at` 为准

#### `working/preAlert -> awaitingAction`

触发条件：

- 当前时间达到 `next_reminder_at`

动作：

- 前台：打开休息页或休息弹窗
- 后台：发送正式提醒通知
- 如果 `ask_before_break = false`，直接进入 `resting`

#### `awaitingAction -> resting`

触发条件：

- 用户点击“开始休息”
- 或 `disable_skip = true`
- 或超时且 `timeout_auto_break = true`

动作：

- 写入 `break_started_at`
- 写入 `break_end_at`
- 累加 `completed_break_count`
- 安排休息结束通知

#### `awaitingAction -> working`

触发条件：

- 用户点击“跳过本次”
- 或预提醒默认动作是跳过且无进一步交互

动作：

- `skip_count + 1`
- 重新计算下一轮 `next_pre_alert_at` 和 `next_reminder_at`

#### `resting -> working`

触发条件：

- 当前时间达到 `break_end_at`
- 或用户提前结束休息

动作：

- `rest_seconds += 本次休息时长`
- 播放休息结束音
- 清空休息字段
- 进入下一轮工作阶段

#### `working/awaitingAction/resting -> paused`

触发条件：

- 用户手动暂停
- 用户启用静默到某时刻

动作：

- 取消现有通知
- 持久化暂停状态

#### `paused -> working`

触发条件：

- 用户恢复
- 静默到期

动作：

- 以当前时刻重新开始一轮工作计时

---

## 8.2 番茄钟状态机

### 状态定义

- `idle`
- `awaitingFocusConfirm`
- `focus`
- `shortBreak`
- `longBreak`

### 事件定义

- `startPomodoro`
- `confirmFocusStart`
- `focusFinished`
- `breakFinished`
- `skipBreak`
- `stopPomodoro`
- `switchToReminder`

### 转移规则

#### `idle -> awaitingFocusConfirm`

仅在开启交互模式时成立。

否则直接：

- `idle -> focus`

#### `awaitingFocusConfirm -> focus`

触发条件：

- 用户点击开始专注

动作：

- 记录 `pomodoro_phase_started_at`
- 记录 `pomodoro_phase_end_at`
- 播放工作开始提示音

#### `focus -> shortBreak`

触发条件：

- 完成 1, 2, 3 轮专注

动作：

- `completed_focus_sessions + 1`
- `total_focus_seconds += 本轮专注时长`
- 安排短休息通知

#### `focus -> longBreak`

触发条件：

- 完成第 4 轮专注

动作：

- `completed_focus_sessions + 1`
- `completed_tomato_count + 1`
- `total_focus_seconds += 本轮专注时长`
- 安排长休息通知

#### `shortBreak/longBreak -> focus`

触发条件：

- 休息结束
- 或用户手动开始下一轮

动作：

- `total_break_seconds += 本轮休息时长`
- 如果是 `longBreak`，将 `pomodoro_cycle_index` 重置为 1
- 否则 `pomodoro_cycle_index + 1`

#### 任意状态 -> idle

触发条件：

- 用户停止番茄钟
- 用户切回普通提醒

动作：

- 取消通知
- 清空番茄运行时状态
- 若中途退出，则 `restart_count + 1`

## 9. 时间恢复策略

手机端不能依赖常驻 `Timer.periodic` 作为唯一真相。

必须以数据库中的时间戳恢复状态。

### 9.1 应用启动恢复算法

启动时执行：

1. 读取 `runtime_state`
2. 判断 `active_engine`
3. 根据 `phase_end_at / next_reminder_at / break_end_at` 与 `now` 对比恢复状态
4. 补统计
5. 重新调度通知

### 9.2 普通提醒恢复规则

- 若 `reminder_phase = working` 且 `now < next_pre_alert_at`
  - 继续 `working`
- 若 `next_pre_alert_at <= now < next_reminder_at`
  - 前台可恢复为 `preAlert`
  - 后台只需重排正式提醒
- 若 `now >= next_reminder_at` 且还未进入 `resting`
  - 若 `ask_before_break = false` 或 `timeout_auto_break = true`
    - 直接补转为 `resting`
  - 否则恢复为 `awaitingAction`
- 若 `reminder_phase = resting`
  - 若 `now < break_end_at`
    - 继续倒计时
  - 若 `now >= break_end_at`
    - 结算后转 `working`

### 9.3 番茄钟恢复规则

- 若 `pomodoro_phase = focus` 且 `now >= pomodoro_phase_end_at`
  - 直接补转为休息阶段
- 若 `pomodoro_phase = shortBreak/longBreak` 且 `now >= pomodoro_phase_end_at`
  - 直接补转为下一轮专注
- 若在交互模式下杀进程
  - 恢复为 `awaitingFocusConfirm`

## 10. Repository 和 Controller 设计

## 10.1 Repository

建议定义：

- `SettingsRepository`
- `ReminderRepository`
- `PomodoroRepository`
- `StatisticsRepository`
- `TipTemplateRepository`

职责：

- 读写数据库
- 提供 stream/watch 接口
- 不做 UI 逻辑

## 10.2 Controller

建议定义：

- `SettingsController`
- `ReminderController`
- `PomodoroController`
- `StatisticsController`
- `TipTemplateController`

职责：

- 调度 repository
- 调用通知服务、音频服务
- 进行状态机流转
- 暴露 `AsyncValue` / `Notifier` 给页面

## 11. 页面清单

下面这份页面清单是按“可落地开发”拆的，不是概念图。

---

## 11.1 `HomePage`

用途：

- 首页总览
- 展示当前模式
- 展示下一次提醒时间
- 快速开始/暂停提醒
- 快速开始/停止番茄钟

核心区域：

- 今日汇总卡片
- 下一次提醒卡片
- 当前运行状态卡片
- 快捷操作栏

---

## 11.2 `BreakPage`

用途：

- 正式提醒触发后进入的休息页面
- 显示倒计时、主文案、跳过/开始休息按钮

状态：

- 等待选择态
- 休息倒计时态
- 结束态

组件建议：

- 倒计时圆环
- 模板背景
- 主标题、副标题
- 开始休息按钮
- 跳过本次按钮

---

## 11.3 `PomodoroPage`

用途：

- 独立番茄钟页面
- 展示当前阶段、剩余时间、当前轮次

核心区域：

- 当前阶段卡片
- 大号倒计时
- 1-4 轮进度指示条
- 开始/暂停/停止按钮

---

## 11.4 `StatisticsPage`

用途：

- 展示周/月统计
- 用眼时长、休息时长、跳过次数、番茄数

分区建议：

- 本周概览
- 本月概览
- 月工作趋势图
- 月休息趋势图
- 月跳过趋势图
- 月番茄趋势图
- 导出分享

---

## 11.5 `SettingsPage`

建议分为 6 个 section：

- 通用设置
- 提醒设置
- 番茄设置
- 外观设置
- 声音设置
- 通知设置

### 通用设置

- 语言
- 主题模式
- 是否启用统计
- 静默时段

### 提醒设置

- 提醒间隔
- 休息时长
- 是否先询问
- 是否禁用跳过
- 未处理是否自动休息

### 预提醒设置

- 是否启用预提醒
- 提前秒数
- 默认动作
- 标题/副标题/正文
- 图标
- 预提醒声音

### 番茄设置

- 工作时长
- 短休息
- 长休息
- 是否交互模式
- 开始/结束声音

### 外观设置

- 模板选择
- 自动深色模式时间段
- 统计页占位图

---

## 11.6 `TipTemplatePage`

用途：

- 选择提醒模板
- 编辑提醒文案
- 预览样式

V1 建议支持：

- 3 套内置模板
- 修改背景色
- 修改背景图
- 修改主标题
- 修改副标题
- 是否显示跳过按钮

不建议 V1 做自由拖拽设计器。

---

## 11.7 `TipTemplatePreviewPage`

用途：

- 预览模板在“等待选择态”和“休息倒计时态”下的显示

---

## 11.8 `AboutPage`

用途：

- 版本号
- 项目介绍
- 开源链接
- 问题反馈

---

## 11.9 `ExportSheet` 或分享弹层

用途：

- 导出月度 Excel/CSV
- 分享统计图

## 12. 页面与桌面功能映射

| 桌面功能 | 手机端页面/模块 |
|---|---|
| 托盘状态 | `HomePage` |
| 提醒全屏窗口 | `BreakPage` |
| 预提醒 Toast | 系统通知 + 前台底部弹层 |
| 番茄模式提示 | `PomodoroPage` + 系统通知 |
| 统计窗口 | `StatisticsPage` |
| 选项窗口 | `SettingsPage` |
| 提醒窗口设计器 | `TipTemplatePage` |
| 更新窗口 | 不做，交由应用商店 |

## 13. 通知设计

建议预留以下通知类型：

- 普通提醒预提醒
- 普通提醒正式提醒
- 休息结束提醒
- 番茄专注开始
- 番茄专注结束
- 番茄休息结束

建议预留以下通知动作：

- `start_break`
- `skip_break`
- `open_break_page`
- `start_focus`
- `stop_pomodoro`

Android 建议支持通知按钮动作。

iOS 不要依赖通知动作闭环完成所有逻辑，应保证点击通知进入应用后可恢复当前状态。

## 14. 统计口径

为避免桌面版的混乱口径，手机端统一如下：

- `working_seconds`: 用户处于工作计时状态累计秒数
- `rest_seconds`: 用户处于休息倒计时状态累计秒数
- `skip_count`: 正式提醒后跳过次数
- `completed_break_count`: 正式进入休息并完成次数
- `completed_tomato_count`: 每完成 4 次专注并进入长休息后记 1 个

页面显示格式建议：

- 小时显示：`working_seconds / 3600`
- 分钟显示：`rest_seconds / 60`

## 15. 导出设计

V1 推荐：

- 导出 CSV 或 XLSX
- 分享 PNG 趋势图

导出字段建议：

- 日期
- 工作时长（分钟）
- 休息时长（分钟）
- 跳过次数
- 完成休息次数
- 番茄数

## 16. 本地开发优先级

### Phase 1

- 目录搭建
- 数据库表
- 设置页最小版
- 普通提醒状态机
- 通知服务

### Phase 2

- 休息页
- 预提醒
- 首页总览
- 基础统计

### Phase 3

- 番茄钟
- 周/月图表
- 导出分享

### Phase 4

- 模板系统
- 自动深色模式
- 自定义图片/音频

## 17. V1 可直接开工的最小实现范围

如果目标是最快交付，建议先做这个版本：

- 首页
- 设置页
- 普通提醒
- 休息页
- 预提醒
- 本地统计
- 番茄钟
- 周/月统计页

先不做：

- 模板编辑器
- 统计图导出为图片
- 复杂通知动作
- 自定义外部音频路径
- 统计页自定义占位图

## 18. 推荐开发顺序

1. 建立 Flutter 工程骨架和目录结构
2. 定义所有 `enum / model / repository interface`
3. 完成数据库建表
4. 完成 `SettingsRepository`
5. 完成 `ReminderController`
6. 接入通知调度和恢复逻辑
7. 完成 `BreakPage`
8. 完成 `HomePage`
9. 完成 `PomodoroController`
10. 完成 `PomodoroPage`
11. 完成统计页
12. 最后补模板和导出

## 19. 关键落地约束

### 19.1 Android

- 可做通知按钮动作
- 可用前台服务增强长计时可靠性
- 需考虑不同 ROM 的后台限制

### 19.2 iOS

- 不可假设长期后台常驻
- 必须依赖本地通知 + 时间戳恢复
- App 被系统回收后，进入前台时必须重新推导状态

### 19.3 通用约束

- 运行时状态不能只放内存
- 所有“结束时间”必须持久化
- 通知只是提醒渠道，不是业务真相
- 业务真相必须来自 `runtime_state`

## 20. 建议的首批 Dart 文件

建议先建这些文件，作为第一批可编译骨架：

- `lib/core/storage/db/app_database.dart`
- `lib/core/storage/db/tables/app_settings_table.dart`
- `lib/core/storage/db/tables/runtime_state_table.dart`
- `lib/core/storage/db/tables/daily_eye_stats_table.dart`
- `lib/core/storage/db/tables/daily_pomodoro_stats_table.dart`
- `lib/core/storage/db/tables/tip_templates_table.dart`
- `lib/features/settings/domain/models/app_settings.dart`
- `lib/features/reminder/domain/models/reminder_runtime_state.dart`
- `lib/features/pomodoro/domain/models/pomodoro_runtime_state.dart`
- `lib/features/reminder/application/reminder_controller.dart`
- `lib/features/pomodoro/application/pomodoro_controller.dart`
- `lib/features/statistics/application/statistics_service.dart`
- `lib/features/home/presentation/pages/home_page.dart`
- `lib/features/reminder/presentation/pages/break_page.dart`
- `lib/features/pomodoro/presentation/pages/pomodoro_page.dart`
- `lib/features/statistics/presentation/pages/statistics_page.dart`
- `lib/features/settings/presentation/pages/settings_page.dart`

## 21. 最终建议

要想“一下子就能开发出来”，最重要的不是先画页面，而是先把这三样钉死：

- 数据表
- 状态机
- 恢复逻辑

这三个一旦稳定，Flutter 页面只是消费状态。

如果一开始就先做 UI，再回头补状态恢复，后面一定会返工。
