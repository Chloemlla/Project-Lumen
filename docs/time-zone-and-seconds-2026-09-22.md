# Project Lumen 时间精度与时区改造方案（2026-09-22）

> 触发：用户要求「所有时间支持精确到秒并且自定义时区 默认时区为+8」。
>
> 本文件是本次改造的唯一依据：每条含唯一编号、文件+行号、缺陷类型、详细症状与根因、建议改法。实现与收尾按编号追溯。
>
> 范围：`app/`（Android 客户端）的全部墙钟时间 —— 显示、输入、以及"墙钟时刻 ↔ 绝对时刻"的换算。不涉及后端 / 构建配置。

---

## 0. 先定死的语义约定

改造前必须把三处歧义定死，否则"改全"无从判定。

| 议题 | 决定 | 理由 |
|---|---|---|
| **时区形态** | 固定 UTC 偏移（`ZoneOffset`），默认 `+08:00`，可选范围 `-12:00 … +14:00`，步进 15 分钟 | 用户原话是"+8"，指名的是偏移量而非 IANA 地区；固定偏移没有夏令时分支，行为可预测、可断言。IANA 地区列表会引入"同名时区在不同日期偏移不同"的行为，超出本次诉求 |
| **时区作用域** | **显示 + 墙钟语义**，不只是显示 | 只改显示会自相矛盾：用户把日程设成 09:00，编辑框按应用时区渲染成 10:00，而闹钟仍按设备时区在 09:00 触发。用户设的"09:00"必须就是应用时区的 09:00 |
| **秒精度作用域** | **墙钟时刻**（所有显示 + 所有可输入的墙钟字段） | 时区对"时长"没有意义，`warnIntervalMinutes`（提醒间隔）、`pomodoroWorkMinutes`（番茄钟时长）这类是时长而非时刻，保持原粒度 |
| **设备时区** | 不再被任何时间路径读取 | 应用时区是唯一权威。默认值 +8 保证中国用户在设备时区为 +8 时行为与改造前**完全一致**（零回归） |

新增单一权威：`core/time/LumenTimeZone.kt`（进程级 `ZoneId` 提供者），由 `SettingsRepository` 在每次读到/写入设置时同步。

---

## 1. 现状清单（改造前真实行为）

### 1.1 时区解析点（33 处，全部读设备时区或硬绑 UTC）

| # | 文件:行 | 作用 | 分类 | 处理 |
|---|---|---|---|---|
| Z-1 | `core/time/QuietHours.kt:52` | 免打扰窗口边界换算 | 解释 | 改 |
| Z-2 | `core/time/QuietHours.kt:68` | 当前时刻→当日本地分钟 | 解释 | 改 |
| Z-3 | `core/time/DateKeys.kt:8` | 统计日键（`todayKey`） | 解释 | 改 |
| Z-4 | `core/schedule/ScheduleMaterializer.kt:18` | 日程展开窗口（默认参） | 解释 | 改（默认参 → 提供者） |
| Z-5 | `core/schedule/ScheduleRecurrenceExpander.kt:35` | 重复规则展开（默认参） | 解释 | 改（默认参 → 提供者） |
| Z-6 | `core/services/ScheduleOverdueNagScheduler.kt:152` | 逾期催办"晚间补催"时刻 | 解释 | 改 |
| Z-7 | `core/services/ScheduleOverdueNagDispatcher.kt:60` | 逾期催办当日边界 | 解释 | 改 |
| Z-8 | `core/services/DataBackupService.kt:335` | 云备份日界 | 解释 | 改 |
| Z-9 | `core/services/DataBackupService.kt:368` | 云备份日界 | 解释 | 改 |
| Z-10 | `core/quarkkeeper/QuarkKeeperModels.kt:218` | 夸克签到 `zone()` 单一入口 | 解释 | 改（仅时区，见 §3） |
| Z-11 | `core/insights/AndroidDeviceInsightDataSource.kt:26` | 设备洞察时段分桶 | 解释 | 改（`ZoneId::systemDefault` → `LumenTimeZone::zoneId`） |
| Z-12 | `core/telemetry/EyeCareTelemetryReporter.kt:365` | 遥测"夜间时段"小时分桶 | 解释 | 改 |
| Z-13 | `app/ProjectLumenUiFormatters.kt:243` | 自动深色窗口判定 `isAutoDarkActive` | 解释+显示 | 改 |
| Z-14 | `app/ProjectLumenHomeScheduleCard.kt:140` | 首页日程卡片日界（默认参） | 显示+解释 | 改 |
| Z-15 | `core/services/NotificationService.kt:1041` | 通知正文时钟时间 | 显示 | 改 |
| Z-16 | `app/ProjectLumenScheduleScreens.kt:102` | 日程编辑：本地时刻 ↔ 绝对时刻 | 显示+解释 | 改 |
| Z-17 | `app/ProjectLumenScheduleScreens.kt:500` | 日程列表时钟渲染 | 显示 | 改 |
| Z-18 | `app/ProjectLumenScheduleScreens.kt:530` | 日程列表时钟渲染 | 显示 | 改 |
| Z-19 | `app/ProjectLumenScheduleScreens.kt:541` | 日程列表日期渲染 | 显示 | 改 |
| Z-20 | `app/ProjectLumenScheduleScreens.kt:551` | 日程列表时钟渲染 | 显示 | 改 |
| Z-21 | `app/ProjectLumenSettingsScreen.kt:1142` | 设置页时间戳 | 显示 | 改 |
| Z-22 | `app/ProjectLumenRemoteCloudCard.kt:217` | 云备份时间戳 | 显示 | 改 |
| Z-23 | `app/ProjectLumenShizukuSettingsSection.kt:574` | Shizuku 最近检查时间 | 显示 | 改 |
| Z-24 | `app/ProjectLumenBuildUpdateNotesScreen.kt:114` | 更新说明时间 | 显示 | 改 |
| Z-25 | `app/ProjectLumenDeveloperDebugScreen.kt:731` | 调试页时间 | 显示 | 改 |
| Z-26 | `app/ProjectLumenBackendConnectivityDeveloperControls.kt:97` | 调试页时间 | 显示 | 改 |
| Z-27 | `app/ProjectLumenAboutAndDialogs.kt:527` | 更新对话框"发布时间"，**硬绑 `ZoneOffset.UTC`** | 显示 | 改 |
| Z-28 | `app/ProjectLumenAboutAndDialogs.kt:528` | 更新对话框"构建时间"，**硬绑 `ZoneOffset.UTC`** | 显示 | 改 |
| Z-29 | `app/ProjectLumenScheduleScreens.kt:641` | `DatePicker` 的 UTC 零点编码 | 编码约定 | **不改** |
| Z-30 | `app/ProjectLumenScheduleScreens.kt:664` | `DatePicker` 的 UTC 零点编码 | 编码约定 | **不改** |
| Z-31 | `app/ProjectLumenScheduleScreens.kt:672` | `DatePicker` 的 UTC 零点编码 | 编码约定 | **不改** |
| Z-32 | `app/ProjectLumenDeveloperDebugScreen.kt:732` | 调试页格式化（已带秒） | 显示 | 仅时区 |
| Z-33 | `app/ProjectLumenShizukuSettingsSection.kt:573` | Shizuku 时间格式化（用 `updateDialogTimeFormatter`，已带秒） | 显示 | 仅时区 |
| Z-34 | `core/schedule/ScheduleOverdueNag.kt:104` | 逾期催办"晚间补催"时刻的构造（**实施时新发现，§1.1 原清单漏列**） | 解释 | 改（入参分钟 → 秒） |

Z-34 说明：`nextEveningNagAt(nowMillis, eveningMinute, zoneId)` 原本收"当日分钟序数"，是 T-5 五个墙钟边界之外的第六处，§1.1 首次清点时漏掉了它（因为它在 `core/schedule/` 而不是跟着设置读取，容易被当成纯计算）。要让晚间补催真正支持秒，必须把它的入参单位从分钟改成秒（`eveningSecondOfDay`，`0..86399`）。该函数只有两个生产调用点（`ScheduleOverdueNagScheduler:181`、`ScheduleOverdueNagDispatcher:57`），都已同步；`ScheduleOverdueNagTest` 的三个用例只把实参由 `1290` 改成 `1290 * 60`（同一时刻 21:30:00），断言一字未动。

Z-29…Z-31 是 `DatePicker` 返回值的解码约定（Material3 的 `DatePicker` 以 UTC 零点表达"某一天"），与用户可见时刻无关，改动会引入日期漂移，故保持 UTC。

### 1.2 时间格式化点（14 处声明 + 若干内联）

| # | 文件:行 | 模式 | 已带秒 | 可见性 |
|---|---|---|---|---|
| F-1 | `app/ProjectLumenAppConstants.kt:209` | `yyyy-MM-dd HH:mm:ss.SSS` | 是 | 崩溃详情 |
| F-2 | `app/ProjectLumenAppConstants.kt:210` | `yyyy-MM-dd HH:mm:ss` | 是 | 更新/Shizuku 时间戳 |
| F-3 | `app/ProjectLumenBackendConnectivityDeveloperControls.kt:102` | `yyyy-MM-dd HH:mm:ss` | 是 | 调试页 |
| F-4 | `app/ProjectLumenDeveloperDebugScreen.kt:732` | `yyyy-MM-dd HH:mm:ss` | 是 | 调试页 |
| F-5 | `app/ProjectLumenBuildUpdateNotesScreen.kt:116` | `ofLocalizedDateTime(MEDIUM)` | 是（MEDIUM 含秒） | 更新说明 |
| F-6 | `app/ProjectLumenRemoteCloudCard.kt:222` | `yyyy-MM-dd HH:mm` | **否** | 云备份时间戳 |
| F-7 | `app/ProjectLumenSettingsScreen.kt:1143` | `yyyy-MM-dd HH:mm` | **否** | 设置页时间戳 |
| F-8 | `core/services/ExportService.kt:223` | `yyyy-MM` | — | 月度导出：既是文件名，也是筛选用键（见 §2 T-10） | 改 |
| F-9 | `core/services/NotificationService.kt:1085` | `HH:mm` | **否** | 通知正文时钟 |
| F-10 | `app/ProjectLumenScheduleScreens.kt:78` | `HH:mm` | **否** | 日程列表时钟 |
| F-11 | `app/ProjectLumenScheduleScreens.kt:611,613` | 纯日期 | — | 日程列表日期 |
| F-12 | `app/ProjectLumenScheduleScreens.kt:618,620` | `yyyy年M月d日HH:mm` / `MMM d, yyyy HH:mm` | **否** | 日程列表日期+时钟 |

F-8 初次清点时被判为"只是导出文件名"，因此列为不动。这个判断是错的：同一个 `monthKey` 还用来筛选 `statDate`（`ExportService.kt:224-225`），而 `statDate` 是 `DateKeys.todayKey` 按**应用时区**写的。它是数据选择键，必须与应用时区一致，详见 T-10。

### 1.3 墙钟设置与输入控件

| 字段 / 控件 | 存储 | 粒度 | 秒？ |
|---|---|---|---|
| `quietStartMinute` / `quietEndMinute`（免打扰起止） | `app_settings` 分钟序数 | 分钟，UI 再吸附到 5 分钟（`snapTimeMinute`） | **否** |
| `autoDarkStartMinute` / `autoDarkEndMinute`（自动深色窗口） | 同上 | 分钟，5 分钟吸附 | **否** |
| `scheduleOverdueNagEveningMinute`（逾期晚间补催） | 同上 | 分钟 | **否** |
| 日程起止 `ScheduleSeriesEntity.startAt/endAt` | **绝对 epoch 毫秒** | 毫秒（存储已够） | 存储够，**编辑器不给输入**（`TimePicker` 只有时分） |
| `schedule_occurrences.*` | 绝对 epoch 毫秒 | 毫秒 | 同上 |
| 时长类：`warnIntervalMinutes`、`pomodoro*Minutes` 等 | 分钟/秒 | 各自原粒度 | **不在本次范围** |

---

## 2. 缺陷清单

### T-1　没有时区设置，全部墙钟时间被钉死在设备时区

- **编号**：T-1
- **文件**：全部 33 处时区解析点（§1.1）
- **类型**：功能缺陷 / 缺失能力
- **症状**：`AppSettingsEntity` 没有任何时区字段（`app/src/main/java/com/projectlumen/app/core/database/entities/AppSettingsEntity.kt:11-119`）。设备时区一旦不是用户想要的时区（设备被锁在 UTC、根改、或用户就是想固定用 +8），通知里的时刻、日程的展开、免打扰窗口、统计日界全部按设备时区走，用户无法纠正。
- **根因**：时区从未被建模为设置项，而是散落 33 处直接调 `ZoneId.systemDefault()` / 硬编码 `ZoneOffset.UTC`。其中 Z-27/Z-28 甚至把更新对话框的发布时间硬绑 UTC，与设备时区都不一致。
- **建议改法**：新增 `core/time/LumenTimeZone.kt` 作为唯一权威；`app_settings` 增列 `timeZoneOffsetSeconds`（默认 `28800` = +08:00）；33 处（除 Z-29…Z-31 三处编码约定）改读该权威。

### T-2　时区变更后日程与闹钟不重算

- **编号**：T-2
- **文件**：`app/ProjectLumenSettingsFeatureEntry.kt:41-78`（设置写入后的统一反应点）、`core/schedule/ScheduleMaterializer.kt:20-57`、`app/ProjectLumenViewModel.kt:173`（`rearm`）
- **类型**：功能缺陷 / 状态不一致
- **症状**：`schedule_occurrences` 的行是按时区展开后**落库**的绝对时刻，`AlarmManager` 的闹钟也是按这些时刻布下的。时区一改，这些已落库的时刻与新时区不再自洽（每天 09:00 的日程仍指向旧时区的 09:00），但没有任何代码重算。用户改完时区，日程要等下一次窗口滑动才慢慢"漂"过去。
- **根因**：设置写入后只有"是否需要重新调度近距离监控 / 灯光 / 调试 / Shizuku"的分支（`ProjectLumenSettingsFeatureEntry.kt:54-76`），没有时区分支。
- **建议改法**：在 `updateSettings` 的 diff 分支里增加"时区变了 → 重新 materialize 全部日程 + 重布闹钟"，复用已有的 `rearm` 回调。

### T-3　通知与常驻状态里的时钟时间只到分

- **编号**：T-3
- **文件**：`core/services/NotificationService.kt:1085`（`CLOCK_TIME_FORMATTER = "HH:mm"`）、`:1040-1042`（`formatClockTime`）
- **类型**：功能缺陷 / 精度截断
- **症状**：预告提醒、休息到点、番茄钟、日程提醒、逾期催办的通知正文里的时刻都只显示到分（如 `22:00`）。用户看到的与其在别处设置的秒级时刻不一致。
- **根因**：单一 pattern 常量把精度截在分钟。
- **建议改法**：模式改为 `HH:mm:ss`，并把时区改读 `LumenTimeZone`。

### T-4　日程列表与日程编辑只到分，编辑器无法输入秒

- **编号**：T-4
- **文件**：`app/ProjectLumenScheduleScreens.kt:78`（列表时钟 formatter）、`:618,620`（列表日期+时钟）、`:333-357`（`rememberTimePickerState` + `TimePicker`）、`:655-661`（`scheduleAtLocalTime(millis, hour, minute, zone)`）
- **类型**：功能缺陷 / 精度截断 + 输入通道缺失
- **症状**：日程列表显示 `09:00`；编辑器用 Material3 `TimePicker`，其 `TimePickerState` 只有 `hour` / `minute`，**没有任何秒输入通道**，`scheduleAtLocalTime` 也只接受时、分。即使存储是毫秒级绝对时刻，用户永远只能表达整分钟。
- **根因**：存储精度够，是输入控件与格式化把精度截断在分钟。
- **建议改法**：列表 formatter 补 `:ss`；编辑器在 `TimePicker` 下补一个秒选择控件，`scheduleAtLocalTime` 增加 `second` 参数，`draft` 与保存路径带上秒。

### T-5　免打扰 / 自动深色 / 逾期补催窗口只到分

- **编号**：T-5
- **文件**：`core/database/entities/AppSettingsEntity.kt:59-63,118`（`autoDarkStartMinute`、`autoDarkEndMinute`、`quietStartMinute`、`quietEndMinute`、`scheduleOverdueNagEveningMinute`）、`core/time/QuietHours.kt:16-78`、`app/ProjectLumenUiFormatters.kt:233-254`（`timeOfDayLabel` / `snapTimeMinute` / `isAutoDarkActive`）、`app/ProjectLumenSettingsTimingSections.kt:90-95`、`app/ProjectLumenSettingsScreen.kt:613-628`
- **类型**：功能缺陷 / 精度截断
- **症状**：这五个墙钟边界在存储层就是"当日分钟序数"（0..1439），`QuietHours.isActive` 用 `localMinuteOfDay` 比较，UI 还额外把值吸附到 5 分钟（`snapTimeMinute`）。用户无法表达 `22:00:30` 这样的边界，最细只能到 5 分钟。
- **根因**：数据结构以分钟为单位，且 UI 层再做一次 5 分钟量化。
- **建议改法**：每个边界**新增**一个秒分量列（`quietStartSecond` 等，0..59），存储层保持不变（避免同名列单位漂移、避免破坏既有 `ReminderEngineTest` / `ScheduleOverdueNagTest` 里 `quietStartMinute = 22 * 60` 的既有语义）；比较与换算改为 `minute * 60 + second`；UI 每个边界补一个 0..59 的秒滑块。

### T-6　云备份与设置页时间戳只到分

- **编号**：T-6
- **文件**：`app/ProjectLumenRemoteCloudCard.kt:222`、`app/ProjectLumenSettingsScreen.kt:1143`
- **类型**：功能缺陷 / 精度截断
- **症状**：云备份的"上次备份时间"、设置页的时间戳都形如 `2026-09-22 04:45`，同一分钟内的多次操作无法区分。
- **根因**：两处各自内联 pattern 缺 `ss`。同项目其它时间戳（F-1…F-5）已经带秒，属遗漏。
- **建议改法**：两处 pattern 补 `:ss`。

### T-7　新设置项不进入备份 / 恢复

- **编号**：T-7
- **文件**：`core/services/DataBackupService.kt:150-200`（导入）、`:413-465`（导出）、`:587-588`（第二处导出）、`:679-680`（默认值解析）
- **类型**：功能缺陷 / 数据完整性
- **症状**：`DataBackupService` 对 `app_settings` 的每个字段都显式列出。新增 `timeZoneOffsetSeconds` 与五个秒分量而不改这里，会导致：备份→换机恢复后时区悄悄回到 +8，秒分量丢失。
- **根因**：JSON 映射是显式白名单，与实体字段无编译期关联。
- **建议改法**：在四处映射里按同类字段的既有位置补上新字段。

### T-8　逾期催办整组设置从未进入备份（实施 B2 时发现的相邻缺陷，本次不修）

- **编号**：T-8
- **文件**：`core/services/DataBackupService.kt`（`app_settings` 的导入 / 导出映射）
- **类型**：功能缺陷 / 数据完整性
- **症状**：`scheduleOverdueNagEnabled`、`scheduleOverdueNagIntervalMinutes`、`scheduleOverdueNagEveningMinute`、`scheduleOverdueNagEveningSecond` 在 `DataBackupService` 的导入与导出映射里**一个都没有**（`rg scheduleOverdueNag` 在该文件零命中）。备份→换机恢复后，用户的逾期催办设置整组回到默认值。
- **根因**：JSON 映射是手写白名单（与 T-7 同一根因），新增字段时漏掉了这一组。这不是本次改造引入的，改造前就已如此。
- **与本次的关系**：`scheduleOverdueNagEveningSecond` 虽然也是本次新增的秒分量，但它的分钟兄弟列同样不在映射里，单独补它会得到"秒恢复了、分钟仍是默认"的半截结果，反而更难排查。
- **建议改法**：**不在本次改**，另起一次改造把 `scheduleOverdueNag*` 四个字段一起补进两处映射（与 T-7 同一手法）。

### T-9 ~ T-14 的实现期发现

T-9…T-12 是 B1–B3 落地后、由一次独立的对抗性复查（`b978bb4..75214fa` 区间）找出来的**遗漏路径**——它们的共同特征是"代码里没有 `systemDefault` 字样，所以文本扫描扫不到"，只有按语义追"这个时刻是从哪个时区算出来的"才会暴露。T-13/T-14 是随之而来的两处可用性缺陷。编号接在 T-8 之后，便于与上文区分。

| 编号 | 文件 | 类型 | 症状与根因 | 修复 commit |
|---|---|---|---|---|
| **T-9** | `app/ProjectLumenBackupFeatureEntry.kt`、`app/ProjectLumenViewModel.kt:197` | 功能缺陷 / 状态不一致 | 恢复备份会写回 `timeZoneOffsetSeconds`，`SettingsRepository` 随即把进程级 `LumenTimeZone` 切到恢复后的时区——但 `schedule_occurrences` 的行与已布下的 `AlarmManager` 闹钟仍是**旧时区**展开出来的绝对时刻，恢复流程里没有任何重算。与 T-2 同根因，只是触发点从"用户改时区"变成"恢复备份"。**改法**：给 `ProjectLumenBackupFeatureEntry` 注入 `rearmSchedule`（复用 `ProjectLumenViewModel` 里 T-2 用的同一个 `rescheduleScheduleReminders`），在 `applySettingsToActiveRuntime` 之后调用 | `4743c71` |
| **T-10** | `core/services/ExportService.kt:227` | 功能缺陷 / 数据选择错误 | 月度 PDF 的 `monthKey` 用 `LocalDate.now()`（设备时区）算出，再拿它 `startsWith` 筛选 `statDate`——而 `statDate` 是 `DateKeys.todayKey` 按**应用时区**写进库的。两者不一致时（设备时区 ≠ 应用时区），月初/月末那几天的数据会被静默丢弃或错纳。**初版方案把这一处列为"只是文件名，不动"，是判断错误**（F-8 已同步更正） | `4743c71` |
| **T-11** | `app/ProjectLumenEyeCareInsights.kt:725` | 功能缺陷 / 残留状态 | `applyFamilyEyeCareMode` 预设免打扰 `21:00–07:00` 时只写 `quietStartMinute = 1260` / `quietEndMinute = 420`，不动新加的秒分量。用户此前把秒调成 `21:00:30`，套用家长模式后窗口会变成 `21:00:30–07:00:xx`——预设声称的整点窗口被旧秒污染。**改法**：预设同时把两个秒分量显式置 0 | `4743c71` |
| **T-12** | `app/ProjectLumenHomeScheduleCard.kt:51` | 功能缺陷 / 陈旧缓存 | `scheduleHomeItems` 的窗口日界由应用时区推出，但 `remember(tasks, nowMillis)` 的键里没有时区。时区一改，首页日程卡片会一直显示旧时区的那一天，直到下一次 `nowMillis` 跳动。**改法**：`LumenTimeZone.zoneId()` 进 `remember` 键 | `4743c71` |
| **T-13** | `app/ProjectLumenFormControls.kt:255`（`NumberSlider`）、五个墙钟滑块 | 可用性 / 显示失真 | 滑块拖动过程中显示的是**原始数值**，所以时间滑块在读秒时会显示 `1320` 而不是 `22:00`。而且分钟滑块提交时经过 `snapTimeMinute` 吸附，若拖动中就按未吸附值给出标签，预览的会是一个**永远不会被存下来**的时刻。**改法**：`NumberSlider` 增 `liveValueLabel: ((Int) -> String)?`（默认 `null`，行为不变），拖动中改走它；五个分钟滑块传的 lambda 一律套 `snapTimeMinute`，秒滑块套 `* 60 + 已有秒`，所以预览值恒等于松手后真正落库的值。因拖拽回调里不能跑 `@Composable`（也就不能用 `stringResource`），新增 `rememberTimeOfDayFormatter()` 返回**普通函数**，`timeOfDayLabel` 改为委托它——格式串只有一个来源 | `5d7b461` |
| **T-14** | `ProjectLumenSettingsScreen.kt`、`ProjectLumenSettingsTimingSections.kt`、`ProjectLumenScheduleOverdueSettings.kt`、`ProjectLumenScheduleScreens.kt` | 可用性 / 标签重复 | 秒滑块的标题沿用了分钟滑块的文案（`auto_dark_start` 等），同一组里出现两行同名控件，用户无从分辨哪行改分、哪行改秒。**改法**：秒行统一改用新的 `time_seconds`（"秒"/"Seconds"），并删掉只此一处使用的 `schedule_time_seconds` | `75214fa` |

T-11 需要说明为什么只改了预设、没改设置滑块：滑块是用户**显式**在调秒，保留现有秒分量是正确行为（否则用户调分钟时秒会被悄悄清零）；而预设是"一键套用一整套值"，它承诺的就是整点窗口，所以必须把秒一并写死。

---

## 3. 与本次改造的边界（明确不动的部分）

- **不改时长类设置**：`warnIntervalMinutes`、`pomodoroWorkMinutes`、`pomodoroShortBreakMinutes`、`pomodoroLongBreakMinutes`、`proximityCheckIntervalMinutes` 等是"时长"而不是"时刻"。时区对时长没有意义；它们的滑块粒度也是刻意选择。若后续要秒级时长，属另一项改造。
- **QuarkKeeper 只接入时区，不改秒**：`core/quarkkeeper` 的签到截止时刻本就是分钟语义（`QuarkKeeperModels.minuteOfDay` / `minutePrecisionLabel` / `snoozeCutoffMinuteOfDay`，并有 `QuarkKeeperClockTest`、`QuarkKeeperCalendarTest` 钉住该设计）。本次只把 `zone()` 接到 `LumenTimeZone`（Z-10），不把它抬到秒。
- **不改 `DatePicker` 的 UTC 零点编码**：Z-29…Z-31（`scheduleUtcMidnightOf` / `scheduleStartOfDay` / `scheduleWithDate`）是 Material3 `DatePicker` 的取值约定，不是用户可见时刻。改它会让"选中的日期"漂一天。
- **不引入 IANA 地区时区**：只有固定偏移。因此不存在夏令时分支，也不存在"同名时区历史偏移不同"。
- **不引入"跟随系统"选项**：用户明确要"默认时区为+8"，默认值即 +8。跟随系统会让"默认"失去意义。
- **月度导出的月份键改为跟随应用时区**（`ExportService.kt:223`）。它最初被列为"只是文件名，不动"，但同一个值也用于筛选按应用时区落库的 `statDate`，因此纳入本次范围（T-10）。文件名本身顺带跟随应用时区——这比跟随设备时区更符合"用户所处的那一天"。
- **不动 `ReminderPlanEntity.quietStartMinute` / `quietEndMinute`**：这两个字段虽与免打扰同名，但 `rg quietStartMinute` 显示除了 `DataBackupService` 的导入导出映射之外**没有任何读取点**——`QuietHours` 只读 `AppSettingsEntity` 上的同名字段。它们是一组从未被消费的休眠列，既不影响任何墙钟行为，也就没有"支持秒"的对象。给它们加秒分量只会扩大迁移面而无收益。若将来计划级免打扰真正接上逻辑，届时再补。
- **不重写通知层、不动后台提醒链路**：`LumenAlertOverlayService` / `LumenAlertPresenter`（刚完成的 A-1…A-5）只被动受益于 formatter 变化。
- **不做与本次无关的重构**：33 处时区点只做"换成权威来源"这一件事，不顺手改结构。

---

## 4. 落地批次

| 批次 | 内容 | 涉及编号 |
|---|---|---|
| B1（基座，协调者亲改） | `LumenTimeZone.kt` 新增；`AppSettingsEntity` 加 6 个字段；Room 迁移 20→21；`SettingsRepository` 播种权威 | T-1（存储侧）、T-5（存储侧）、T-7（实体侧） |
| B2（并行） | ① 显示层：§1.2 的 F-6/F-7/F-9/F-10/F-12 + §1.1 的显示类时区点；② 墙钟设置层：T-5 的比较/换算/UI；③ 日程层：T-4 + 日程相关时区点 + T-2 的重算 | T-2、T-3、T-4、T-6 |
| B3 | 备份映射、设置页时区行、字符串 | T-7、T-1（UI 侧） |

---

## 5. 修复去向核对表

| 编号 | 修复 commit | 状态 |
|---|---|---|
| T-1 | `b978bb4`（`LumenTimeZone` + `timeZoneOffsetSeconds` + 迁移 20→21）、`eb6b520`（33 处解析点全部改读权威） | 已修 |
| T-2 | `eb6b520`（设置 diff 里的时区分支 → `rearm`） | 已修 |
| T-3 | `eb6b520`（`CLOCK_TIME_FORMATTER` → `HH:mm:ss`） | 已修 |
| T-4 | `eb6b520`（列表 formatter 补秒；编辑器补秒滑块；`scheduleAtLocalTime` 加 `second`） | 已修 |
| T-5 | `b978bb4`（5 个秒分量列 + 迁移）、`eb6b520`（`minute * 60 + second` 的比较与换算）、`75214fa`（滑块标签与对话框可达性） | 已修 |
| T-6 | `eb6b520`（两处 pattern 补 `:ss`） | 已修 |
| T-7 | `eb6b520`（四处 JSON 映射补 6 个新字段） | 已修 |
| T-8 | — | **有意不修**（见 §2 T-8：`scheduleOverdueNag*` 整组不在映射里，属改造前既有缺陷，单独补秒分量会得到半截结果，另起一次改造统一补） |
| T-9 | `4743c71` | 已修 |
| T-10 | `4743c71` | 已修 |
| T-11 | `4743c71` | 已修 |
| T-12 | `4743c71` | 已修 |
| T-13 | `5d7b461` | 已修 |
| T-14 | `75214fa` | 已修 |
| Z-34 | `eb6b520`（`nextEveningNagAt` 入参由分钟序数改为秒序数，两个调用点同步） | 已修 |
| 方案本身 | 本文件 | — |

提交序列（`main`，自旧到新）：`b978bb4` → `eb6b520` → `75214fa` → `5d7b461` → `4743c71`。

**验证判据**：本地不跑任何构建/测试，唯一权威是 GitHub Actions 的 `Build Project Lumen Android`（`build.yml`：`testDebugUnitTest` + `lintDebug` + `assembleRelease`）。由于 `build.yml` 会取消被超越的排队运行，**必须核对 tip 提交**而非区间内任意一个。

**已验证**：`eb6b520`（承载 T-1…T-7 与 Z-34 的实现）的 `Build Project Lumen Android` 与 `CodeQL` 均为 `success`。`b978bb4`（基座）的 `Build Project Lumen Android` 为 `success`。`75214fa` / `5d7b461` / `4743c71`（T-9…T-14，只改标签、滑块回调与三个遗漏路径）的结论以 tip 提交的运行为准。

**已知的例外**：`eb6b520` 是唯一一个未签名提交（`%G?` = `N`）；其余为 `G`。原因是提交时 GPG agent 瞬时不可用，按 CLAUDE.md 的"签名失败可退回 `--no-gpg-sign`"处理，**不重写历史**。
