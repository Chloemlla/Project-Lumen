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
       schedule(occurrence.id, nowMillis + index * SWEEP_STAGGER_MILLIS)
   }
9. interval = ScheduleOverdueNag.clampIntervalMinutes(settings.scheduleOverdueNagIntervalMinutes)
   —— 只作为第 5 步过滤口径的输入之一，实际每轮间隔由 Dispatcher 在触发时读取
```

这一步的**幂等性**是设计核心：它不假设自己知道「已经排了什么」，而是每次都把期望状态和当前全量状态对齐。因此它可以在开机、权限变更、设置改动、任何一次日程写入之后被无脑重复调用。

> 第 8 步用 **当前时刻** 而不是 `endAt` 作为首轮触发点：历史遗留项的 `endAt` 在过去，拿它当触发点会被 `schedule()` 的「过去不排」规则吞掉。

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

新增 `app/src/test/java/com/projectlumen/app/core/schedule/ScheduleOverdueNagTest.kt`（纯 JVM，钉死 `ZoneId` 无关——本对象只用毫秒，不碰时区）。

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

### 11.1 数据层
- [ ] C-01 `AppSettingsEntity` 追加 `scheduleOverdueNagEnabled` / `scheduleOverdueNagIntervalMinutes`，默认值 `false` / `120`
- [ ] C-02 `AppDatabase` version = 20
- [ ] C-03 `MIGRATION_19_20` 用 `addColumnIfMissing`，默认值与实体一致
- [ ] C-04 `MIGRATION_19_20` 已加入 `addMigrations(...)` 列表
- [ ] C-05 `ScheduleOccurrencesDao.getActive()` 存在
- [ ] C-06 `ScheduleOverdueNag` 五个成员齐全，`isOverdue` 四条判定齐全
- [ ] C-07 单测 N-1 ~ N-10 全部落地

### 11.2 调度与触发
- [ ] C-08 `NotificationIds` 的 `9900/300` 与 `9600/300` 不重叠
- [ ] C-09 `ScheduleOverdueNagScheduler` 用 `setAndAllowWhileIdle`，不检查精确闹钟权限
- [ ] C-10 `rearmAll` 在开关关闭时撤销全部闹钟
- [ ] C-11 `rearmAll` 会撤销「已完成 / 未到期 / 超龄」三项的闹钟
- [ ] C-12 `rearmAll` 的首轮触发点是 `now + index * 15s`，不是 `endAt`
- [ ] C-13 `ScheduleOverdueNagDispatcher` 的 8 步顺序与 §5 一致
- [ ] C-14 触发后无条件排下一轮（**没有**过期不补的判定）
- [ ] C-15 `AlarmReceiver` 有 `ACTION_SCHEDULE_OVERDUE_NAG` 分支且**提前返回**，不落到 `reconcileNow`

### 11.3 接线与通知
- [ ] C-16 `rescheduleScheduleReminders()` 追加了 `rearmAll`
- [ ] C-17 `setCompleted` 后 `rearmAll` 会撤销该实例的闹钟（勾选即停）
- [ ] C-18 新渠道 `schedule_overdue` 为 `IMPORTANCE_HIGH` + 默认声音 + 振动
- [ ] C-19 `showScheduleOverdue` 走 `show(...)`，**未**新增 `setOnlyAlertOnce(true)`
- [ ] C-20 **未**新增 `<service>` / `<receiver>`，`AndroidManifest.xml` 无改动
- [ ] C-21 轰炸不受 `QuietHours` 与 `settings.notificationEnabled` 影响（D-3）

### 11.4 UI
- [ ] C-22 `ScheduleOverdueNagCard` 用 `SettingsSection`，自动进分组工具栏
- [ ] C-23 间隔滑杆仅在开关打开时渲染
- [ ] C-24 滑杆范围 5..360、5 分钟粒度、`clampIntervalMinutes` 兜底
- [ ] C-25 提示文案 `schedule_overdue_nag_hint` 明确写出「免打扰/护眼开关不压制」
- [ ] C-26 两个 ViewModel 门面方法都是**先落库再重排**
- [ ] C-27 9 条字符串在 `values/` 与 `values-zh/` 两侧键集一致

### 11.5 纪律
- [ ] C-28 未新增「超级文件」（卡片独立成文件，设置页只加一行调用）
- [ ] C-29 未运行任何本地构建/测试命令
- [ ] C-30 提交已推送且 CI（check-runs 逐 job）全绿

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
