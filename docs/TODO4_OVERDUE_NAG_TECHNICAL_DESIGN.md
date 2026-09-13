# TODO4 待办逾期持续轰炸提醒 — 技术设计（落地核对基线）

> 本文件是**冻结的接口契约**，后续实现必须与之一致；核对时逐条对 §11 清单打勾。
> 上一份（待办/日程本体）在 `TODO3_SCHEDULE_TASK_TECHNICAL_DESIGN.md`，本次是它的增量。

---

## 0. 需求原文与已确认的口径

用户原文：

> 待办项目必须在当天之内完成。在设置中添加开关，并且若一个项目未及时完成，隔一段时间持续不断弹轰炸系统通知、实时通知，直到用户手动勾选为止。并且这个设置由用户手动打开。隔三到两个小时，这些时间间隔可以由用户自己设置。

四个会改变实现的分歧点已由用户拍板：

| 编号 | 分歧点 | 用户选择 | 落在代码里的含义 |
|---|---|---|---|
| D-1 | 什么时刻算逾期 | **过了结束时刻就算逾期** | `nowMillis > occurrence.endAt` 且未完成 |
| D-2 | 间隔怎么给 | **用户自由输入任意分钟数** | 走 `NumberSlider`，5..360 分钟、5 分钟粒度，默认 120 |
| D-3 | 免打扰时段是否压制 | **静音时段也照常轰炸** | 不查 `QuietHours`，也不查 `settings.notificationEnabled` |
| D-4 | 历史遗留怎么办 | **历史遗留也一起催** | 开开关时把所有已逾期未完成项一次性纳入队列 |

**不受本功能影响的既有行为**：TODO3 的「开始前提醒」（`ScheduleReminderScheduler` / `ScheduleReminderDispatcher` / 两个 `schedule_*` 渠道）完全不动。本功能是叠加在它之上的第二条链路。

---

## 1. 术语与整体结构

| 术语 | 含义 |
|---|---|
| 逾期（overdue） | `deletedAt == 0` 且 `completed == false` 且 `endAt < now` 的实例 |
| 轰炸（nag） | 对同一条逾期实例按用户设定的间隔反复投递高重要性通知，直到它被勾选、被删除、或开关关闭 |
| 扫描（sweep） | 遍历全部活跃实例，把「该排的排上、不该排的撤销」，是幂等的全量对齐 |

```
app_settings.scheduleOverdueNagEnabled / .scheduleOverdueNagIntervalMinutes
        │
        ▼
ProjectLumenApplication.rescheduleScheduleReminders()   ← 开机 / 权限变更 / 日程写入 / 设置改动
        │
        ├─ ScheduleAlarmRestore.rearm(...)              （TODO3 既有：开始前提醒）
        └─ ScheduleOverdueNagScheduler.rearmAll(...)    （本次新增：逾期轰炸对齐）
                    │
                    ▼
        AlarmManager.setAndAllowWhileIdle ──► AlarmReceiver
                    │                            └─ ACTION_SCHEDULE_OVERDUE_NAG
                    ▼                                        │
        ScheduleOverdueNagScheduler.schedule(下一轮) ◄───────┤
                                                             ▼
                                          ScheduleOverdueNagDispatcher.dispatch(...)
                                                             │
                                                             ▼
                                          NotificationService.showScheduleOverdue(...)
                                                             （渠道 schedule_overdue）
```

**为什么用 `setAndAllowWhileIdle` 而不是 `setExactAndAllowWhileIdle`**：轰炸是「隔一段时间的重复提示」，精确到秒没有意义；用不精确闹钟可以**完全不依赖精确闹钟权限**，也就不需要在用户撤销权限时降级。Doze 下 `setAndAllowWhileIdle` 允许每 9 分钟一次，本功能的最小间隔是 5 分钟——**这是取 5 而非 1 的原因**，低于 9 分钟会在 Doze 下被系统合并。

---

## 2. 设置字段与数据库迁移

### 2.1 `AppSettingsEntity` 追加两列

```kotlin
val scheduleOverdueNagEnabled: Boolean = false,        // 默认关，用户必须手动打开（需求原文）
val scheduleOverdueNagIntervalMinutes: Int = 120,      // 默认 2 小时
```

### 2.2 `AppDatabase` version 19 → 20

```kotlin
private val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        addColumnIfMissing(db, "app_settings", "scheduleOverdueNagEnabled", "INTEGER NOT NULL DEFAULT 0")
        addColumnIfMissing(db, "app_settings", "scheduleOverdueNagIntervalMinutes", "INTEGER NOT NULL DEFAULT 120")
    }
}
```

要点：

- 必须复用既有的 `addColumnIfMissing`（`AppDatabase.kt` 已有），不要写裸 `ALTER TABLE`。
- **默认值必须与实体默认值一致**：`DEFAULT 0` / `DEFAULT 120`。不一致会在升级用户的设备上产生「升级后开关莫名打开」或间隔错值，而 Room 的 `TableInfo` 校验**不看默认值**，编译期与运行期都不会报错。
- 新迁移必须同时加进 `builder.addMigrations(...)` 列表，否则升级用户的库版本停在 19，Room 会抛 `IllegalStateException`。

### 2.3 `ScheduleOccurrencesDao` 追加一个查询（不涉及版本号）

```kotlin
@Query("SELECT * FROM schedule_occurrences WHERE deletedAt = 0")
suspend fun getActive(): List<ScheduleOccurrenceEntity>
```

扫描需要**全量**活跃实例（含 `completed = 1` 的），因为「不该排的要撤销」必须能看到已完成的行。既有的 `observeAll()` 是 `Flow`，不适合在 `suspend` 的重排路径里取一次快照。

---

## 3. 纯逻辑 `core/schedule/ScheduleOverdueNag.kt`（新增）

不带 Android 依赖，可在纯 JVM 单测里跑（与 `ScheduleRecurrenceExpander` 同样的理由）。

```kotlin
object ScheduleOverdueNag {
    const val MIN_INTERVAL_MINUTES = 5
    const val MAX_INTERVAL_MINUTES = 360
    const val DEFAULT_INTERVAL_MINUTES = 120

    /** 比这更早的逾期项不再轰炸，避免一年前没勾的待办永远弹。 */
    const val MAX_OVERDUE_AGE_DAYS = 30L

    /** 扫描时给同一批逾期项错开首轮触发，避免一次涌出十几条通知同时响。 */
    const val SWEEP_STAGGER_MILLIS = 15_000L

    fun isOverdue(occurrence: ScheduleOccurrenceEntity, nowMillis: Long): Boolean
    fun overdueItems(occurrences: List<ScheduleOccurrenceEntity>, nowMillis: Long): List<ScheduleOccurrenceEntity>
    fun clampIntervalMinutes(minutes: Int): Int
    fun nextNagAt(nowMillis: Long, intervalMinutes: Int): Long
}
```

### 3.1 `isOverdue(occurrence, nowMillis)`

四条同时成立才算逾期：

1. `occurrence.deletedAt == 0L`
2. `!occurrence.completed`
3. `occurrence.endAt < nowMillis`（**严格小于**：`endAt` 那一毫秒还算当天之内）
4. `nowMillis - occurrence.endAt <= MAX_OVERDUE_AGE_DAYS * 86_400_000L`

### 3.2 `overdueItems(occurrences, nowMillis)`

- 过滤出 `isOverdue` 的项，**按 `endAt` 升序**返回（越早逾期的越先被催）。
- 不做 `take(n)` 截断：用户选的 D-4 是「历史遗留也一起催」，截断会违背该选择。

### 3.3 `clampIntervalMinutes(minutes)`

`minutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)`。写在纯逻辑里而不是 UI 里，是为了让「从旧版本库读到一个越界值」也能被兜住。

### 3.4 `nextNagAt(nowMillis, intervalMinutes)`

`nowMillis + clampIntervalMinutes(intervalMinutes) * 60_000L`。

---

## 4. 调度 `core/services/ScheduleOverdueNagScheduler.kt`（新增）

```kotlin
class ScheduleOverdueNagScheduler(private val context: Context) {
    fun schedule(occurrenceId: Long, triggerAtMillis: Long)
    fun cancel(occurrenceId: Long)
    fun notificationIdFor(occurrenceId: Long): Int

    companion object {
        const val EXTRA_OCCURRENCE_ID = "com.projectlumen.app.extra.SCHEDULE_OVERDUE_OCCURRENCE_ID"
        suspend fun rearmAll(app: ProjectLumenApplication, nowMillis: Long = System.currentTimeMillis())
    }
}
```

### 4.1 id 与 request code 映射

```kotlin
fun notificationIdFor(occurrenceId: Long): Int =
    NotificationIds.SCHEDULE_OVERDUE_BASE + (occurrenceId % NotificationIds.SCHEDULE_OVERDUE_RANGE).toInt()
```

`NotificationIds` 追加：

```kotlin
const val SCHEDULE_OVERDUE_BASE = 9900
const val SCHEDULE_OVERDUE_RANGE = 300
```

> `9600 + 300 = 9900`，即 `SCHEDULE_REMINDER_BASE..+RANGE` 的末尾正好是 `9899`，两段**不重叠**。重叠本身不会崩（`Intent` 的 action 不同），但会让「开始前提醒」和「逾期轰炸」互相顶掉对方的闹钟，属于必须避免的静默故障。

同一个值同时用作**通知 id** 和 **alarm request code**（与 `ScheduleReminderScheduler` 一致）。

### 4.2 `schedule(occurrenceId, triggerAtMillis)`

- `triggerAtMillis <= now` 时**不排**，直接返回。
- `alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)`。
- **不检查、也不申请精确闹钟权限**（理由见 §1）。
- `PendingIntent.getBroadcast(context, notificationIdFor(id), intent, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)`，`intent` 为：

```kotlin
Intent(context, AlarmReceiver::class.java)
    .setAction(AlarmReceiver.ACTION_SCHEDULE_OVERDUE_NAG)
    .setPackage(context.packageName)
    .putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
```

### 4.3 `rearmAll(app, nowMillis)` — 幂等全量对齐

```
1. settings = app.settingsRepository().getOrDefault()
2. all = app.database.scheduleOccurrencesDao().getActive()
3. enabled = settings.scheduleOverdueNagEnabled
4. 若 !enabled：all.forEach { cancel(it.id) }，return
5. overdue = ScheduleOverdueNag.overdueItems(all, nowMillis)
6. overdueIds = overdue 的 id 集合
7. all.filter { it.id !in overdueIds }.forEach { cancel(it.id) }     ← 已完成 / 未到期 / 超龄的闹钟在这里被撤销
8. overdue.forEachIndexed { index, occurrence ->
       schedule(occurrence.id, nowMillis + (index + 1) * SWEEP_STAGGER_MILLIS)
   }
9. interval = ScheduleOverdueNag.clampIntervalMinutes(settings.scheduleOverdueNagIntervalMinutes)
   —— 只作为第 5 步过滤口径的输入之一，实际每轮间隔由 Dispatcher 在触发时读取
```

这一步的**幂等性**是设计核心：它不假设自己知道「已经排了什么」，而是每次都把期望状态和当前全量状态对齐。因此它可以在开机、权限变更、设置改动、任何一次日程写入之后被无脑重复调用。

> 第 8 步用 **当前时刻** 而不是 `endAt` 作为首轮触发点：历史遗留项的 `endAt` 在过去，拿它当触发点会被 `schedule()` 的「过去不排」规则吞掉。
>
> 第 8 步的倍数是 **`index + 1` 而不是 `index`**：`index == 0` 那一项算出的触发点恰好等于 `nowMillis`，而 `schedule()` 内部会**再读一次真实时钟**，此时该值已经变成过去，于是「最该被催的那一条」在每一轮扫描里都会被静默丢弃。`+ 1` 保证最小偏移是一整个 `SWEEP_STAGGER_MILLIS`。（这是实现阶段发现并修正的，原设计写的是 `index`。）

---

## 5. 触发 `core/services/ScheduleOverdueNagDispatcher.kt`（新增）

```kotlin
object ScheduleOverdueNagDispatcher {
    suspend fun dispatch(app: ProjectLumenApplication, occurrenceId: Long, nowMillis: Long)
}
```

步骤（顺序不可调换）：

1. `occurrence = app.database.scheduleOccurrencesDao().get(occurrenceId) ?: return`
   —— 查询自带 `deletedAt = 0`，行不存在即已删除。
2. `if (occurrence.completed) return` —— **用户手动勾选即停**（需求原文的停止条件）。
3. `settings = app.settingsRepository().getOrDefault()`
4. `if (!settings.scheduleOverdueNagEnabled) return` —— 开关关闭即停。
5. `if (!ScheduleOverdueNag.isOverdue(occurrence, nowMillis)) return` —— 例如被编辑成未来的日程。
6. `app.notifications.ensureChannels()`
7. `app.notifications.showScheduleOverdue(occurrence)`
8. `if (!occurrence.completed) { … }` —— 无条件再排下一轮：
   `ScheduleOverdueNagScheduler(app).schedule(occurrenceId, ScheduleOverdueNag.nextNagAt(nowMillis, settings.scheduleOverdueNagIntervalMinutes))`

**与 TODO3 的 `ScheduleReminderDispatcher` 的关键差异：这里没有「过期不补」的判定。** 开始前提醒的语义是「一次性的、错过就算了」，逾期轰炸的语义恰恰相反——**错过必须补上**，否则设备休眠一晚就等于轰炸自己停了，违背「直到用户勾选为止」。

**第 2 步的 `completed` 抑制是唯一的自动停止条件**，因此在 `setCompleted` 路径上必须同时撤销闹钟（见 §6.3），否则已勾选的项还会再响一轮才停。

---

## 6. 接线

### 6.1 `NotificationIds` 追加

```kotlin
const val SCHEDULE_OVERDUE_BASE = 9900
const val SCHEDULE_OVERDUE_RANGE = 300
```

### 6.2 `AlarmReceiver` 追加分支

```kotlin
const val ACTION_SCHEDULE_OVERDUE_NAG = "com.projectlumen.app.action.SCHEDULE_OVERDUE_NAG"
```

在 `onReceive` 的 `runCatching` 内、**TODO3 那条日程开始前提醒分支之后、护眼 `reconcileNow` 之前**插入，并以 `return@runCatching` 收尾：

```kotlin
if (intent.action == ACTION_SCHEDULE_OVERDUE_NAG) {
    val occurrenceId = intent.getLongExtra(ScheduleOverdueNagScheduler.EXTRA_OCCURRENCE_ID, 0L)
    if (occurrenceId != 0L) {
        app.notifications.ensureChannels()
        ScheduleOverdueNagDispatcher.dispatch(app, occurrenceId, System.currentTimeMillis())
    }
    return@runCatching
}
```

**必须提前返回**：否则这条闹钟会落进护眼引擎的 `reconcileNow`，把用户自己写的待办当成一次护眼相位推进，污染统计数据。

### 6.3 `ProjectLumenApplication.rescheduleScheduleReminders()` 追加一行

```kotlin
suspend fun rescheduleScheduleReminders() {
    scheduleRepository.refreshWindow()
    ScheduleAlarmRestore.rearm(this, scheduleRepository)
    ScheduleOverdueNagScheduler.rearmAll(this)          // ← 新增
}
```

这一行使**开机（`BootReceiver`）、精确闹钟权限变更（`ExactAlarmPermissionReceiver`）、日程编辑（`ProjectLumenScheduleFeatureEntry` 的 `rearm`）、设置改动（§8.2）四条路径**自动都覆盖到轰炸链路，不需要各自补代码。

`ProjectLumenScheduleFeatureEntry.setCompleted` 已调用 `rearm()`，因此勾选完成后 `rearmAll` 会把该实例的闹钟撤销——**§5 第 2 步与这里是一对**，缺一个就会出现「勾了还响一轮」。

### 6.4 无新增 `<service>`、无新增 `<receiver>`

轰炸复用既有的 `AlarmReceiver`（靠 explicit intent 定位，不需要新增 intent-filter），`AndroidManifest.xml` **不改**，以免触碰 `ForegroundServiceArchitectureTest` 等既有守卫测试。

---

## 7. 通知与渠道

### 7.1 新渠道 `NotificationChannels.SCHEDULE_OVERDUE`

```kotlin
const val SCHEDULE_OVERDUE = "schedule_overdue"
```

| 渠道 id | 名称资源 | 重要性 | 声音 | 振动 |
|---|---|---|---|---|
| `schedule_overdue` | `channel_schedule_overdue` | `IMPORTANCE_HIGH` | 默认 | 开 |

**单独开渠道而不是复用 `schedule_alarm`**：轰炸是用户可能想单独静音的行为，混进「开始前提醒」会让用户无法只关掉轰炸而保留正常提醒。

### 7.2 `NotificationService.showScheduleOverdue(occurrence)`

```kotlin
fun showScheduleOverdue(occurrence: ScheduleOccurrenceEntity) {
    show(
        id = ScheduleOverdueNagScheduler(context).notificationIdFor(occurrence.id),
        channel = NotificationChannels.SCHEDULE_OVERDUE,
        title = occurrence.title,
        message = context.getString(R.string.schedule_overdue_message, formatClockTime(occurrence.endAt)),
        priority = NotificationCompat.PRIORITY_HIGH,
        includeBreakActions = false,
    )
}
```

**反复响铃依赖的是 `show(...)` 里没有 `setOnlyAlertOnce(true)`**（默认 `false`，每次 `notify` 都会重新响铃/振动）。这是本功能成立的前提，实现时**不要**给 `show` 加这个 flag，也不要在本方法里另建 builder。

`fullScreen` 保持 `false`：全屏 intent 在 Android 14+ 需要额外权限，且对一条待办而言过激。

---

## 8. 状态流与 UI 接线

### 8.1 设置项读取

`AppSettingsEntity` 已经通过 `ProjectLumenStateStore` → `ProjectLumenUiState.settings` 流到 UI，**不需要新增任何 state 字段**。

### 8.2 `ProjectLumenViewModel` 追加两个门面方法

```kotlin
fun setScheduleOverdueNagEnabled(enabled: Boolean) {
    updateSettings { it.copy(scheduleOverdueNagEnabled = enabled) }
    reportingScope.launch { rescheduleScheduleReminders() }
}

fun setScheduleOverdueNagIntervalMinutes(minutes: Int) {
    val clamped = ScheduleOverdueNag.clampIntervalMinutes(minutes)
    updateSettings { it.copy(scheduleOverdueNagIntervalMinutes = clamped) }
    reportingScope.launch { rescheduleScheduleReminders() }
}
```

两个方法都必须**先落库再重排**：`rescheduleScheduleReminders()` 内部会重新读 `app_settings`，顺序颠倒会读到旧值。

### 8.3 新文件 `app/ProjectLumenScheduleOverdueSettings.kt`

```kotlin
@Composable
internal fun ScheduleOverdueNagCard(settings: AppSettingsEntity, viewModel: ProjectLumenViewModel)
```

- 用 `SettingsSection(R.string.schedule_overdue_nag_section, Icons.Outlined.NotificationsActive) { ... }` —— 与其它设置卡片一致，且 `SettingsSection` 会**自动**注册进 `SettingsSectionGroupController`，不需要手工挂进工具栏。
- 内容：
  1. `SwitchRow(R.string.schedule_overdue_nag_enabled, …)` → `viewModel.setScheduleOverdueNagEnabled(it)`
  2. **仅当开关为开时**渲染 `NumberSlider(R.string.schedule_overdue_nag_interval, Icons.Outlined.Schedule, settings.scheduleOverdueNagIntervalMinutes, 5f..360f, 0, label) { viewModel.setScheduleOverdueNagIntervalMinutes(snapToFive(it)) }`
     —— `NumberSlider` 没有 `enabled` 参数，用「条件渲染」表达禁用，避免改动公共控件签名波及全站。
  3. `Text(R.string.schedule_overdue_nag_hint)`，说明「过了结束时刻仍未勾选会按此间隔持续提醒，直到勾选完成；免打扰时段与护眼通知开关都不会压住它」。
- 间隔文案 `internal fun scheduleOverdueIntervalLabel(minutes: Int): String`：
  - `minutes < 60` → `schedule_overdue_nag_interval_minutes`
  - `minutes % 60 == 0` → `schedule_overdue_nag_interval_hours`
  - 否则 → `schedule_overdue_nag_interval_hours_minutes`

### 8.4 在设置页挂载

`ProjectLumenSettingsScreen.kt` 的 `LumenPage { ... }` 内，紧跟 `HomeScheduleCard` 所属的日程相关卡片之后调用一次 `ScheduleOverdueNagCard(settings, viewModel)`。**只加一行调用**，卡片本体在新文件里，避免设置页继续膨胀。

---

## 9. 字符串清单（`values/` 与 `values-zh/` **两侧键集必须完全一致**，共 9 条）

| key | 英文 | 中文 |
|---|---|---|
| `schedule_overdue_nag_section` | Overdue reminders | 待办逾期提醒 |
| `schedule_overdue_nag_enabled` | Keep reminding until checked off | 未勾选就持续提醒 |
| `schedule_overdue_nag_interval` | Reminder interval | 提醒间隔 |
| `schedule_overdue_nag_hint` | A to-do past its end time keeps notifying at this interval until you check it off. Quiet hours and the eye-care notification switch do not silence it. | 过了结束时刻仍未勾选的待办会按此间隔持续通知，直到你勾选完成为止。免打扰时段与护眼通知开关都不会压制它。 |
| `schedule_overdue_nag_interval_minutes` | %1$d min | %1$d 分钟 |
| `schedule_overdue_nag_interval_hours` | %1$d h | %1$d 小时 |
| `schedule_overdue_nag_interval_hours_minutes` | %1$d h %2$d min | %1$d 小时 %2$d 分钟 |
| `channel_schedule_overdue` | Overdue to-do reminders | 待办逾期提醒 |
| `schedule_overdue_message` | Overdue · was due %1$s | 已逾期 · 原定 %1$s 结束 |

> 仓库只有 `values-zh` 一个 locale 目录，不存在 `MissingTranslation` 风险；但**漏一条**会让中文用户看到英文，因此核对时用 `grep -o 'name="schedule_overdue[a-z_]*"' | sort` 两侧比对。

---

## 10. 测试

新增 `app/src/test/java/com/projectlumen/app/core/schedule/ScheduleOverdueNagTest.kt`（纯 JVM）。

> **§13 之后本节的「钉死 `ZoneId` 无关」已不再成立**：`endedInMorning` / `nextEveningNagAt` 本质是本地墙钟问题，必须显式传入 `ZoneId`。N-1 ~ N-10 仍不涉及时区；N-11 ~ N-17 显式指定时区，因此这些用例在 UTC 的 CI runner 上结果与开发机一致。

| 编号 | 用例 | 断言 |
|---|---|---|
| N-1 | `endAt` 已过、未完成、未删除 | `isOverdue == true` |
| N-2 | `endAt` 恰好等于 `now` | `isOverdue == false`（严格小于） |
| N-3 | `endAt` 在未来 | `isOverdue == false` |
| N-4 | 已完成 | `isOverdue == false` |
| N-5 | 已软删 | `isOverdue == false` |
| N-6 | `endAt` 在 30 天前 | `isOverdue == false`（超龄） |
| N-7 | `endAt` 在 29 天前 | `isOverdue == true`（边界内） |
| N-8 | `overdueItems` 排序 | 按 `endAt` 升序，且混入的未逾期项被剔除 |
| N-9 | `clampIntervalMinutes` | `1 → 5`、`5 → 5`、`120 → 120`、`360 → 360`、`9999 → 360` |
| N-10 | `nextNagAt` | `now + 120 * 60_000`；间隔传 `0` 时按 `MIN_INTERVAL_MINUTES` 计 |

---

## 11. 落地核对清单

实现完成后逐条核对；每条要么 ✅ 已实现（附文件:行号），要么 ⏸ 挂起（附理由）。

> **进度：30 / 30 已勾选**（C-30 已由 `9704962` 的 `Build Project Lumen Android` 结论 `success` 判定；C-31 ~ C-46 见 §13.11）。
> 核对方式：逐文件读落盘代码 + `git diff`，**不采信子代理自述**。
> 核对过程中发现并已修正的 3 条：**C-12**（`index` → `index + 1`，原写法会让每轮最该催的那条被静默丢弃）、文档 §13.6 关于 `armEveningSlot` 的表述失真（代码最终是单侧方案，文档一度写成双侧）、以及两处「控件数量变了但注释没跟」的失真。

### 11.1 数据层
- [x] C-01 `AppSettingsEntity.kt:115-118` —— 三列齐全：`scheduleOverdueNagEnabled = false` / `scheduleOverdueNagIntervalMinutes = 120` / `scheduleOverdueNagEveningMinute = 1290`
- [x] C-02 `AppDatabase.kt:50` —— `version = 20`
- [x] C-03 `AppDatabase.kt:221-226` —— `MIGRATION_19_20` 三行均走 `addColumnIfMissing`，默认值与实体逐一对齐（`0` / `120` / `1290`）
- [x] C-04 `AppDatabase.kt:513` —— `MIGRATION_19_20` 已在 `addMigrations(...)` 列表内
- [x] C-05 `ScheduleOccurrencesDao.kt:30` —— `getActive()` 存在，且含注释说明为何不能用 `observeAll()`
- [x] C-06 `ScheduleOverdueNag.kt:27-45, 56-81, 91-115` —— 成员齐全；`isOverdue` 四条判定按「软删 → 已完成 → 未到期 → 超龄」顺序短路
- [x] C-07 `ScheduleOverdueNagTest.kt:34-134` —— N-1 ~ N-10 全部落地，另多一条 `stillOverdueAtExactlyMaxAge` 钉住 `<=` 边界

### 11.2 调度与触发
- [x] C-08 `NotificationIds.kt:21-26` —— `9600..9899` 与 `9900..10199` 不重叠，注释写明「重叠会让两条链路互顶」
- [x] C-09 `ScheduleOverdueNagScheduler.kt:49-57` —— 用 `setAndAllowWhileIdle`，全文件**无** `canScheduleExactAlarms` / 权限检查
- [x] C-10 `ScheduleOverdueNagScheduler.kt:132-135` —— 开关关闭时 `all.forEach { cancel(it.id) }` 后 `return`
- [x] C-11 `ScheduleOverdueNagScheduler.kt:144-145` —— `all.filter { it.id !in overdueIds }` 覆盖「已完成 / 未到期 / 超龄」三类
- [x] C-12 `ScheduleOverdueNagScheduler.kt:146-154` —— 首轮触发点是 `nowMillis + (index + 1) * SWEEP_STAGGER_MILLIS`，不是 `endAt`；**`+ 1` 是实现阶段修正的**，理由见 §4.3
- [x] C-13 `ScheduleOverdueNagDispatcher.kt:90-103` —— `resolveValidated` 内五步顺序与 §5 第 1–5 步一致
- [x] C-14 `ScheduleOverdueNagDispatcher.kt:38-42` —— 无条件排下一轮；全文件**无** `STALE_AFTER_MILLIS` 一类判定
- [x] C-15 `AlarmReceiver.kt:49-59` —— 分支存在且以 `return@runCatching` 收尾，不会落进 `reconcileNow`

### 11.3 接线与通知
- [x] C-16 `ProjectLumenApplication.kt:117-120` —— `rescheduleScheduleReminders()` 末尾追加 `ScheduleOverdueNagScheduler.rearmAll(this)`
- [x] C-17 `ProjectLumenScheduleFeatureEntry.kt:108-112` —— `setCompleted` 落库后调 `rearm()`；配合 `ScheduleOverdueNagScheduler.kt:63-68` 的 `cancel` 同时撤两槽，构成「勾选即停」
- [x] C-18 `NotificationService.kt:105-112` —— `IMPORTANCE_HIGH` + `enableVibration(true)`；全仓仅一处 `setOnlyAlertOnce`（`:183`，FGS 常驻通知专用），与本渠道无关
- [x] C-19 `NotificationService.kt:276-285` —— `showScheduleOverdue` 直接走 `show(...)`；`:504-506` 有「刻意不加 `setOnlyAlertOnce(true)`」的注释
- [x] C-20 —— `git status` 中**无** `AndroidManifest.xml`，无新增 `<service>` / `<receiver>`
- [x] C-21 `ScheduleOverdueNagDispatcher.kt:99-101` —— 只查开关与 `isOverdue`，**无** `QuietHours` / `notificationEnabled` 判定（D-3）

### 11.4 UI
- [x] C-22 `ProjectLumenScheduleOverdueSettings.kt:16` —— 用 `SettingsSection(@StringRes, icon)` 重载；调用点 `ProjectLumenSettingsScreen.kt:564` 落在 `CompositionLocalProvider(LocalSettingsSectionGroup ...)`（`:506`）作用域内，展开/折叠全站联动
- [x] C-23 `ProjectLumenScheduleOverdueSettings.kt:27` —— 两个滑杆都在 `if (settings.scheduleOverdueNagEnabled)` 内
- [x] C-24 `ProjectLumenScheduleOverdueSettings.kt:28-37, 69-72` —— 范围 `5f..360f`，`snapOverdueNagMinutes` 吸附到 5 分钟并用 `clampIntervalMinutes` 兜底
- [x] C-25 `values/strings.xml:993` / `values-zh/strings.xml:992` —— 提示文案已写明「免打扰时段与护眼通知开关都不会压制它们」
- [x] C-26 `ProjectLumenViewModel.kt:492-510` —— 三个门面方法均**先 `updateSettings` 再 `reportingScope.launch { rescheduleScheduleReminders() }``
- [x] C-27 —— `rg -o 'name="schedule_overdue[a-z_]*"' | sort` 两侧均为 9 键且完全一致

### 11.5 纪律
- [x] C-28 —— 卡片独立成 `ProjectLumenScheduleOverdueSettings.kt`，设置页只加 1 行调用（`:564`）
- [x] C-29 —— 本轮未在本机执行任何 gradle / 构建 / 测试 / lint 命令
- [x] C-30 提交已推送且 CI（check-runs 逐 job）全绿 —— `9704962` 的 `Build Project Lumen Android` run `34732440505` 结论 `success`（`3c02ac3` 那次是 `cancelled`，被 `9704962` 以 concurrency 顶掉；两者代码一致，`9704962` 仅改文档，故本次判定同样覆盖 `3c02ac3` 落盘的代码）

---

## 12. 已知取舍（供复核时判断是否接受）

| 编号 | 取舍 | 理由 |
|---|---|---|
| K-1 | 逾期超过 30 天的项不再轰炸 | 用户选的是「直到勾选为止」，但没有上界就意味着一年前没勾的待办会永远弹。30 天是安全阀；若要无限期，把 `MAX_OVERDUE_AGE_DAYS` 调大或改为 `Long.MAX_VALUE` 的行为需一并确认 |
| K-2 | 最小间隔 5 分钟，不是 1 分钟 | Doze 下 `setAndAllowWhileIdle` 最快约 9 分钟一次；低于该值用户会以为「设置了没生效」 |
| K-3 | 间隔粒度 5 分钟，不是任意整数分钟 | `NumberSlider` 是本站设置项的统一数值控件，没有 `enabled`/自由文本模式；要 1 分钟粒度就得新增一个数字输入控件，超出本次范围 |
| K-4 | 轰炸不查 `QuietHours` / `notificationEnabled` | 用户明确选择「静音时段也照常轰炸」；且 TODO3 的开始前提醒已有同样先例 |
| K-5 | 同一实例的通知是**复用同一个 id 反复响**，不堆叠成多条 | 堆叠会让用户下拉栏被同一条待办塞满，反而更难找到真正需要处理的那条；反复响铃已满足「轰炸」 |
| K-6 | 首轮轰炸在开启开关后 15 秒 × 序号 陆续到达 | 用户选了「历史遗留也一起催」，若同一刻全部触发会瞬间涌出十几条；错开是礼仪性让步，不影响后续按间隔循环 |
| K-7 | 提醒的精确时刻由系统决定，可能有数分钟偏差 | 不精确闹钟的固有性质；对「每 2 小时催一次」没有实际影响 |
| K-8 | 未做「通知内直接勾选完成」的动作按钮 | 需求只要求「直到用户手动勾选为止」，点击通知打开 App 即可完成；动作按钮属增量，若需要另行确认 |

---

## 13. 增量：晚间补催（第二轮）

> 本节是**后续追加**的需求，§1–§12 保持原样；第二节实现以本节为准，与前面冲突处以本节为准。

### 13.1 需求原文与已确认的口径

用户原文：

> 如果结束时间在上午，那么在下午晚上 9 点半的时候也弹出来通知一下子。

| 编号 | 分歧点 | 用户选择 | 含义 |
|---|---|---|---|
| E-1 | 晚间时刻是否可配 | **做成可设置的时间，默认 21:30** | 新增设置项 `scheduleOverdueNagEveningMinute`，默认 `1290` |
| E-2 | 「上午」的界线 | **12:00 为界** | `endAt` 的**本地时刻** `< 720` 分钟 |
| E-3 | 催几天 | **逾期期间每晚都催** | 只要还没勾选，每晚都在该时刻补一次 |
| E-4 | 与间隔轰炸的关系 | **额外必定响一次（独立于间隔）** | 间隔循环照常，晚间这一次是**额外的**，两者互不挤占 |

### 13.2 为什么是「第二个闹钟槽位」而不是「调整间隔排程」

E-4 决定了不能把晚间时刻并进 `nextNagAt` 的间隔计算里：那样只是把某一次挪个位置，总次数不变，且间隔恰好跨过 21:30 时不会落在 21:30 上。要「必定响一次」，就必须为同一条实例**同时**持有两枚闹钟：

| 槽位 | Intent action | 触发逻辑 |
|---|---|---|
| 间隔槽 | `ACTION_SCHEDULE_OVERDUE_NAG` | 每轮触发后自排 `now + interval` |
| 晚间槽 | `ACTION_SCHEDULE_OVERDUE_EVENING` | 每轮触发后自排「下一个晚间时刻」 |

**两枚闹钟共用同一个 request code 是安全的**：`PendingIntent` 的身份是 `(requestCode, Intent.filterEquals)`，而 `filterEquals` 比较 action/data/type/class/categories —— 两个 Intent 的 **action 不同**，因此得到两个不同的 `PendingIntent`，不会互相顶掉。这一点必须写进代码注释：它是「靠 action 区分」而非「靠 request code 区分」，改动 action 常量时两枚闹钟会静默合并。

**但通知 id 必须共用**（`notificationIdFor(occurrenceId)`），否则同一件事会在下拉栏里堆成两条。

**`cancel(occurrenceId)` 必须同时撤销两枚**，否则关掉开关后晚间槽还会在 21:30 响一次。

### 13.3 设置字段（第二个新列）

`AppSettingsEntity` 追加：

```kotlin
val scheduleOverdueNagEveningMinute: Int = 1290,   // 21:30，自午夜起的分钟数
```

`MIGRATION_19_20` 追加一行：

```kotlin
addColumnIfMissing(db, "app_settings", "scheduleOverdueNagEveningMinute", "INTEGER NOT NULL DEFAULT 1290")
```

沿用 0..1435 与既有 `autoDarkStartMinute` / `quietStartMinute` 相同的「自午夜起分钟数」表示法，理由：设置页已有 `timeOfDayLabel` + 取整工具一套，直接复用。

### 13.4 纯逻辑 `ScheduleOverdueNag` 追加两个成员

```kotlin
/** 结束时刻早于中午 12:00 —— 这类待办当天早上就该做完，晚上再补催一次。 */
const val MORNING_END_MINUTE = 12 * 60
const val DEFAULT_EVENING_MINUTE = 21 * 60 + 30

fun endedInMorning(occurrence: ScheduleOccurrenceEntity, zoneId: ZoneId): Boolean
fun nextEveningNagAt(nowMillis: Long, eveningMinute: Int, zoneId: ZoneId): Long
```

- `endedInMorning`：把 `occurrence.endAt` 转到 `zoneId` 取本地时刻，比较 `hour * 60 + minute < MORNING_END_MINUTE`。
  **必须是本地时刻，不能用 UTC**：`endAt` 是 epoch 毫秒，直接取模 86400000 得到的是 UTC 当日偏移，东八区用户的 07:30 会被算成 23:30。
- `nextEveningNagAt`：以 `zoneId` 求「今天该时刻」；若 `<= nowMillis` 则顺延到明天的该时刻。即「只要还没到今晚这一刻就排今晚，否则排明晚」。
  `eveningMinute` 由调用方先 `coerceIn(0, 1435)`。

> `ScheduleOverdueNag` 原本被设计为**不碰时区**（§3 与 §10 都写了「钉死 `ZoneId` 无关」）。本节打破该约束，因此该文件的类注释与 §10 的开头说明必须同步更新，并在此处记录原因：判定「上午」和「晚间时刻」本质都是本地时间概念，无法回避。

### 13.5 `ScheduleOverdueNagScheduler` 追加

```kotlin
fun scheduleEvening(occurrenceId: Long, triggerAtMillis: Long)
fun cancel(occurrenceId: Long)   // 改为同时撤销两个 action 的 PendingIntent
```

- `scheduleEvening` 与 `schedule` 完全同构，只是 action 换成 `ACTION_SCHEDULE_OVERDUE_EVENING`，且同样用 `setAndAllowWhileIdle`（晚间这一刻精确到秒同样无意义）。
- `rearmAll` 的第 7 步（撤销不该排的）与第 8 步（排上该排的）都要**同时处理两个槽位**：

```
7.  all.filter { it.id !in overdueIds }.forEach { cancel(it.id) }        // cancel 内部两枚一起撤
8.  overdue.forEachIndexed { index, occurrence ->
        schedule(occurrence.id, nowMillis + index * SWEEP_STAGGER_MILLIS)
    }
8b. overdue.filter { ScheduleOverdueNag.endedInMorning(it, zone) }
        .forEach { occurrence ->
            scheduleEvening(occurrence.id,
                ScheduleOverdueNag.nextEveningNagAt(nowMillis, eveningMinute, zone))
        }
```

**8b 刻意不做 `index` 错开**：晚间是一次「当天收尾」的固定时点，所有上午结束的逾期项在同一时刻提醒正是该时点的本意；而第 8 步的间隔循环若不错开，开启开关那一刻会一次涌出十几条，那是另一个问题。这条差异要写进注释，否则会被后人当成漏写。

`zone = ZoneId.systemDefault()`，在 `rearmAll` 内取一次即可（不要每项各取一次，跨零点时会不一致）。
`eveningMinute = settings.scheduleOverdueNagEveningMinute.coerceIn(0, 1435)`。

### 13.6 `ScheduleOverdueNagDispatcher` 追加

把既有的 8 步校验抽成一个私有方法，两个公开入口共用，避免复制粘贴：

```kotlin
suspend fun dispatch(app: ProjectLumenApplication, occurrenceId: Long, nowMillis: Long)
suspend fun dispatchEvening(app: ProjectLumenApplication, occurrenceId: Long, nowMillis: Long)

private suspend fun resolveValidated(
    app: ProjectLumenApplication,
    occurrenceId: Long,
    nowMillis: Long,
): Pair<ScheduleOccurrenceEntity, AppSettingsEntity>?
```

`resolveValidated` 承担原第 1–5 步（取行 → `completed` → 取设置 → 开关 → `isOverdue`），返回 `null` 表示应当静默退出。

两个入口的差异只在**重排**，且**各自只重排自己那一枚**：

- `dispatch`（间隔槽触发）：发通知 → 排下一轮**间隔**槽。**不碰晚间槽。**
- `dispatchEvening`（晚间槽触发）：发通知 → 排下一晚**晚间**槽。**不碰间隔槽。**

> **为什么不做交叉补排**（这里曾一度改为「两个入口都调同一个 `armEveningSlot` 补排晚间槽」，最终又收回单侧方案）：
>
> 交叉补排的动机是「`dispatch` 万一漏判 `endedInMorning`，就会给下午结束的待办也催」。但单侧方案下这个风险**根本不存在**：`dispatch` 不碰晚间槽，就无需在那里再判一次；一条待办**只可能通过 `rearmAll` 第 8b 步**进入晚间链路，而那处是全仓**唯一**的 `endedInMorning` 判定点（交叉补排方案反而让判定点从 1 处变成 3 处）。
>
> 单侧方案还额外消掉两个副作用：间隔计时不会在 21:30 被重置；一次间隔轮也不会把当晚仍待发的晚间闹钟改写成明晚。
>
> 代价是 `dispatchEvening` 不再顺带修复「间隔槽丢失」——但这本就不是可达状态（间隔槽存在 ⇒ `rearmAll` 排过它 ⇒ 同一次 `rearmAll` 也排过晚间槽），且下一次 `rearmAll`（开机 / 打开应用 / 日程写入 / 设置改动）会把两枚都收敛回期望状态。

**两个入口都发通知**，用的是同一个通知 id。因此 21:30 那一下会把间隔槽最近一条通知就地更新并重新响铃，不会堆叠。

> 原 §5 第 8 步的「无条件再排下一轮、没有过期不补判定」在 `dispatch` 一侧保持不变；`dispatchEvening` 同样无条件排下一晚，理由一致。

### 13.7 `AlarmReceiver` 追加第二个分支

在 §6.2 的分支之后、护眼 `reconcileNow` 之前：

```kotlin
if (intent.action == ACTION_SCHEDULE_OVERDUE_EVENING) {
    val occurrenceId = intent.getLongExtra(ScheduleOverdueNagScheduler.EXTRA_OCCURRENCE_ID, 0L)
    if (occurrenceId != 0L) {
        app.notifications.ensureChannels()
        ScheduleOverdueNagDispatcher.dispatchEvening(app, occurrenceId, System.currentTimeMillis())
    }
    return@runCatching
}
```

同样**必须提前返回**。

### 13.8 设置页追加

`ScheduleOverdueNagCard` 在间隔滑杆之后追加第三个控件（同样仅在开关打开时渲染）：

```kotlin
NumberSlider(
    R.string.schedule_overdue_nag_evening_time,
    Icons.Outlined.Schedule,
    settings.scheduleOverdueNagEveningMinute,
    0f..1435f,
    0,
    timeOfDayLabel(settings.scheduleOverdueNagEveningMinute),
) { viewModel.setScheduleOverdueNagEveningMinute(snapTimeMinute(it)) }
```

- `timeOfDayLabel(totalMinutes: Int)` 与 `snapTimeMinute(value: Int)` 已存在于 `app/ProjectLumenUiFormatters.kt`，均为 `internal`，**同包 `com.projectlumen.app.app`，不需要 import、也不需要改可见性**。直接复用，**不要复制第二份**（复制出的两份必然漂移）。
- 两个工具都与 `autoDarkStartMinute` / `autoDarkEndMinute` 用的是同一套，因此晚间时刻的交互与设置页既有时间项**完全一致**：5 分钟粒度、`time_value` 文案。
- `snapTimeMinute` 内已含 `coerceIn(0, 1435)`，所以 ViewModel 一侧的 `coerceIn` 是第二道防线而非唯一防线。
- **不改** `NumberSlider` 的签名。

`ProjectLumenViewModel` 追加第三个门面方法，与 §8.2 同构（先落库再重排）：

```kotlin
fun setScheduleOverdueNagEveningMinute(minute: Int) {
    val clamped = minute.coerceIn(0, 1435)
    CrashBreadcrumbs.record("Action setScheduleOverdueNagEveningMinute=$clamped")
    updateSettings { it.copy(scheduleOverdueNagEveningMinute = clamped) }
    reportingScope.launch { rescheduleScheduleReminders() }
}
```

（`CrashBreadcrumbs.record` 与 §8.2 两个方法保持一致——n3-ui 实现时给那两个方法加了，这里同样加，否则同一张卡片里三个控件只有两个留痕。）

### 13.9 字符串追加（两侧各 +1，并修订 1 条）

| key | 英文 | 中文 |
|---|---|---|
| `schedule_overdue_nag_evening_time` | Evening reminder | 晚间补催时间 |

并把 `schedule_overdue_nag_hint` 修订为：

| 语言 | 新文案 |
|---|---|
| en | A to-do past its end time keeps notifying at this interval until you check it off. Ones that ended in the morning also remind you once every evening. Quiet hours and the eye-care notification switch do not silence either. |
| zh | 过了结束时刻仍未勾选的待办会按此间隔持续通知，直到你勾选完成为止；上午结束的待办每晚还会再补催一次。免打扰时段与护眼通知开关都不会压制它们。 |

### 13.10 测试追加

| 编号 | 用例 | 断言 |
|---|---|---|
| N-11 | `endedInMorning`：本地 07:30 结束 | true |
| N-12 | `endedInMorning`：本地 12:00 结束 | false（严格小于） |
| N-13 | `endedInMorning`：本地 21:00 结束 | false |
| N-14 | `endedInMorning` 的时区正确性 | 同一个 epoch 毫秒，在 `Asia/Shanghai` 判 true，在 `America/New_York` 判 false（用来钉死「不能用 UTC 取模」） |
| N-15 | `nextEveningNagAt`：now 早于今晚该时刻 | 返回**今天**该时刻 |
| N-16 | `nextEveningNagAt`：now 晚于今晚该时刻 | 返回**明天**该时刻 |
| N-17 | `nextEveningNagAt` 跨夏令时 | 在 `America/New_York` 的 DST 切换日，返回的本地时刻仍是 21:30 |

### 13.11 本节核对清单（与 §11 并行勾选）

> **进度：16 / 16 已勾选**（C-46 随 C-30 一起由 `9704962` 的 CI 结论判定）。

- [x] C-31 `AppSettingsEntity.kt:117-118` —— 追加 `scheduleOverdueNagEveningMinute`，默认 `1290`
- [x] C-32 `AppDatabase.kt:225` —— `MIGRATION_19_20` 第三列，`INTEGER NOT NULL DEFAULT 1290`；version 未再 bump，未新增迁移对象
- [x] C-33 `ScheduleOverdueNag.kt:15-19, 42, 45, 91-115` —— 四个成员齐全；类 KDoc 已改写，明确「这两个成员依赖时区是不可避免的」
- [x] C-34 `ScheduleOverdueNag.kt:92-93` —— 用 `Instant.ofEpochMilli(...).atZone(zoneId)` 取本地时刻；KDoc 点明 `endAt % 86_400_000` 会把东八区 07:30 算成 23:30
- [x] C-35 `ScheduleOverdueNagScheduler.kt:45-47, 63-68, 104-107` —— `scheduleEvening` 存在；`cancel` 遍历 `NAG_ACTIONS`，两枚一起撤
- [x] C-36 `ScheduleOverdueNagScheduler.kt:155-172` —— 第 8b 步已加，**未**做 index 错开，注释写明与第 8 步相反的取舍理由
- [x] C-37 `ScheduleOverdueNagScheduler.kt:23-27` —— 注释写明身份是 `(requestCode, Intent.filterEquals)`、靠 **action** 区分，并警告把两个 action 常量改成相同会静默合并两枚闹钟；`AlarmReceiver.kt:142 / 145` 两条字符串确实不同
- [x] C-38 `ScheduleOverdueNagDispatcher.kt:69-82` —— 两个入口共用 `resolveValidated`；`:37-44` 与 `:53-62` 各自只重排自己那一枚（无交叉补排），`endedInMorning` 全仓仅在 `ScheduleOverdueNagScheduler.kt:162` 一处判定
- [x] C-39 `ScheduleOverdueNagDispatcher.kt:34-35, 48-49` —— 两个入口都发 `showScheduleOverdue`，通知 id 同为 `notificationIdFor(occurrenceId)`
- [x] C-40 `AlarmReceiver.kt:62-76` —— `ACTION_SCHEDULE_OVERDUE_EVENING` 分支存在且提前返回
- [x] C-41 `ProjectLumenScheduleOverdueSettings.kt:38-47` —— 晚间时间滑杆在既有 `if (settings.scheduleOverdueNagEnabled)` 块内
- [x] C-42 `ProjectLumenViewModel.kt:506` 与 `ScheduleOverdueNag.kt:105` 双重 `coerceIn(0, 1435)`；`ProjectLumenUiFormatters.kt:239` 的 `snapTimeMinute` 内还有第三道
- [x] C-43 `ProjectLumenViewModel.kt:505-510` —— 先 `updateSettings` 再 `rescheduleScheduleReminders()`，且 `coerceIn(0, 1435)`
- [x] C-44 `ScheduleOverdueNagTest.kt:156-236` —— N-11 ~ N-17 落地；N-17 断言 DST 切换后本地仍是 21:30，且偏移由 `-5` 变 `-4`
- [x] C-45 `values/strings.xml:992-993` / `values-zh/strings.xml:991-992` —— 两侧新增同一键且位置对应，`schedule_overdue_nag_hint` 已修订
- [x] C-46 提交已推送且 CI 全绿 —— 同 C-30，`9704962` 的 `Build Project Lumen Android` 结论 `success`

### 13.12 本节取舍

| 编号 | 取舍 | 理由 |
|---|---|---|
| K-9 | 晚间补催不做错开，所有上午结束的逾期项在同一时刻响 | 那是「当天收尾」的固定时点，本该同时到达；错开会让它漂移到 21:31、21:32，反而破坏「9 点半」这个语义。与 K-6 的差异是有意的 |
| K-10 | 已过今晚时刻时排到明晚，而不是立刻补一次 | 用户说的是「晚上 9 点半弹一下」，22:00 开机时立刻补一条会让人莫名其妙；间隔槽本来就会继续催，不会漏掉 |
| K-11 | 晚间时刻与设置页既有时间项一样是 5 分钟粒度 | 与 `autoDarkStartMinute` / `autoDarkEndMinute` 复用同一套 `timeOfDayLabel` + `snapTimeMinute`，交互一致；21:30 本身就在 5 分钟网格上，粒度不会妨碍本需求 |
| K-12 | 一条**已经逾期**的待办若被编辑成「结束时刻从上午改到下午」，它先前那枚晚间闹钟不会被撤销，仍会每晚催，直到下一轮 `rearmAll` 把它当普通逾期项处理 | `rearmAll` 的撤销集合是「不在 `overdue` 里」的项，而这条待办**仍在逾期集合内**，因此走不到 `cancel`；晚间槽是逾期集合的一个子集，撤销逻辑没有按子集细分。要修就得让 `cancel` 拆成「撤两枚 / 只撤晚间」，为这个很窄的场景增加一层结构。实际后果偏「多催一次」而非「漏催」，与需求方向一致，故记录不修。触发条件需同时满足：已被催过 → 编辑结束时刻跨过 12:00 → 且仍处于逾期 |
