# Project Lumen 待办任务 / 日程详情 技术设计（落地核对基线）

> 状态：设计冻结，待实现。
> 本文是本次「首页待办任务事项 + 日程详情编辑」功能的**唯一实现依据**，同时是后续逐条核对落地结果的清单。
> 文末 §12 的核对清单按编号追溯，每条要么实现、要么注明挂起理由，不允许静默消失。
> 关联：`CLAUDE.md`（禁本地构建，一切构建/测试由 GitHub Actions 执行）、`docs/TODO2_EYE_CARE_INSIGHTS_LANDING.md`（同类落地记录格式）。

---

## 0. 需求原文与范围

用户原话：

> 在首页页面里面添加待办任务事项 支持日程详情

并给出一份日程详情编辑页的字段清单：

```
编辑
日程名称
今天 周日
06:30至07:30
重复：每天
类型
个人
提醒
5分钟前
提醒方式
通知提醒
闹钟提醒
全天
开始  2026年9月13日06:30
结束  2026年9月13日07:30
类型  个人
提醒  5分钟前
提醒方式  通知提醒 / 闹钟提醒
重复  每天
结束重复  永不结束
编辑范围：仅更改此日程 / 更改此日程和将来日程 / 更改所有日程
```

经确认，本次范围是**满配三层**：

| 决策点 | 结论 |
|---|---|
| 落地深度 | 完整持久化 + 增删改（Room 建表、重启不丢） |
| 提醒 | **真发**通知与闹钟（AlarmManager 精确闹钟 + 通知渠道；重启后由 BootReceiver 重建） |
| 重复 | **真正展开**重复实例，「编辑范围」三档有真实语义 |

明确**不做**的（避免误解为已实现）：

- 不做日历月视图 / 周视图（首页只做「今天 + 未来 7 天」的待办列表）。
- 不做与系统日历（CalendarContract）或第三方日历的互操作 / 导入导出。
- 不做邀请他人、会议室、时区选择（一律使用设备当前时区）。
- 不做农历、节假日、调休。

---

## 1. 术语与整体结构

| 术语 | 含义 |
|---|---|
| 独立日程 | 不重复的单条待办（`seriesId = 0`） |
| 系列（series） | 一条重复规则模板，存放在 `schedule_series` |
| 实例（occurrence） | 系列按规则展开出的**具体一次**日程，存放在 `schedule_occurrences`；首页列表与详情页读的都是这张表 |
| 「仅更改此日程」 | 把该实例从系列**脱离**（`detached = 1`），此后不再被系列重新生成覆盖 |
| 「更改此日程和将来日程」 | **切分系列**：旧系列截止到该实例之前，从该实例起新建系列 |
| 「更改所有日程」 | 改系列模板本身，并重新物化 |

数据流：

```
ScheduleSeriesEntity ──(ScheduleRecurrenceExpander 展开)──► ScheduleOccurrenceEntity
        ▲                                                            │
        │                                                    ┌───────┴────────┐
   ScheduleRepository ◄──── 编辑/删除（三种 scope）            │                │
        │                                             首页列表(读)      提醒调度(读)
        ▼                                                   │                │
  ProjectLumenStateStore ─► ProjectLumenUiState.scheduleTasks  │      ScheduleReminderScheduler
                                                          Compose       ─► AlarmManager ─► AlarmReceiver
```

---

## 2. 枚举（`core/enums/`，全部新增）

### 2.1 `ScheduleCategory.kt` — 「类型」

```kotlin
package com.projectlumen.app.core.enums

enum class ScheduleCategory { PERSONAL, WORK, STUDY, OTHER }
```

中文文案：个人 / 工作 / 学习 / 其他（见 §10）。

### 2.2 `ScheduleRecurrence.kt` — 「重复」

```kotlin
package com.projectlumen.app.core.enums

enum class ScheduleRecurrence { NONE, DAILY, WEEKDAYS, WEEKLY, MONTHLY, YEARLY }
```

中文文案：不重复 / 每天 / 工作日 / 每周 / 每月 / 每年。

### 2.3 `ScheduleReminderMethod.kt` — 「提醒方式」

```kotlin
package com.projectlumen.app.core.enums

enum class ScheduleReminderMethod { NOTIFICATION, ALARM }
```

中文文案：通知提醒 / 闹钟提醒。

### 2.4 `ScheduleEditScope.kt` — 「编辑范围」

```kotlin
package com.projectlumen.app.core.enums

enum class ScheduleEditScope { THIS_ONLY, THIS_AND_FUTURE, ALL }
```

中文文案：仅更改此日程 / 更改此日程和将来日程 / 更改所有日程。

> 枚举一律用 `.name` 字符串存库，与仓库既有做法一致（`ActiveEngine` / `ReminderPhase` / `QuietMode` / `TemplateBackgroundType`）。
> 读取时统一用 `entries.firstOrNull { it.name == value } ?: <默认值>` 兜底，避免脏数据崩溃。

---

## 3. 数据模型

### 3.1 `schedule_series`（`core/database/entities/ScheduleSeriesEntity.kt`，新增）

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `id` | `Long` | `0` | `@PrimaryKey(autoGenerate = true)` |
| `title` | `String` | — | 日程名称 |
| `category` | `String` | `PERSONAL` | `ScheduleCategory.name` |
| `allDay` | `Boolean` | `false` | 全天 |
| `startAt` | `Long` | — | 系列**首次发生**的开始（锚点，epoch millis） |
| `endAt` | `Long` | — | 系列**首次发生**的结束（锚点） |
| `recurrence` | `String` | `DAILY` | `ScheduleRecurrence.name`，系列不会是 `NONE` |
| `recurrenceUntil` | `Long` | `0` | 「结束重复」当天 **23:59:59.999**；`0` = 永不结束 |
| `reminderMinutesBefore` | `Int` | `-1` | 提前量（分钟）；`-1` = 不提醒；`0` = 准时 |
| `reminderMethod` | `String` | `NOTIFICATION` | `ScheduleReminderMethod.name` |
| `enabled` | `Boolean` | `true` | 预留：关闭系列 |
| `createdAt` | `Long` | — | |
| `updatedAt` | `Long` | — | |
| `deletedAt` | `Long` | `0` | 软删除，`0` = 未删除 |

### 3.2 `schedule_occurrences`（`core/database/entities/ScheduleOccurrenceEntity.kt`，新增）

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `id` | `Long` | `0` | `@PrimaryKey(autoGenerate = true)` |
| `seriesId` | `Long` | `0` | 所属系列；`0` = 独立日程 |
| `title` | `String` | — | 冗余自系列，脱离（`detached`）后可独立编辑 |
| `category` | `String` | `PERSONAL` | 同上 |
| `allDay` | `Boolean` | `false` | |
| `startAt` | `Long` | — | 本次开始 |
| `endAt` | `Long` | — | 本次结束 |
| `reminderMinutesBefore` | `Int` | `-1` | |
| `reminderMethod` | `String` | `NOTIFICATION` | |
| `recurrence` | `String` | `NONE` | 冗余，供详情页显示「重复：每天」；独立日程为 `NONE` |
| `recurrenceUntil` | `Long` | `0` | 冗余，供详情页显示「结束重复」 |
| `originalStartAt` | `Long` | — | **系列内的身份键**：系列规则算出的那次开始时刻，脱离后不变。独立日程 = 自身 `startAt` |
| `detached` | `Boolean` | `false` | 「仅更改此日程」后置 `true` |
| `completed` | `Boolean` | `false` | 待办勾选 |
| `completedAt` | `Long` | `0` | |
| `reminderFiredAt` | `Long` | `0` | 已触发提醒的时刻，防重复响铃 |
| `createdAt` | `Long` | — | |
| `updatedAt` | `Long` | — | |
| `deletedAt` | `Long` | `0` | |

Room 注解：

```kotlin
@Entity(
    tableName = "schedule_occurrences",
    indices = [
        Index("seriesId"),
        Index("startAt"),
        Index("deletedAt"),
    ],
)
```

> **为什么不建 `(seriesId, originalStartAt)` 唯一索引**：独立日程的 `seriesId` 恒为 `0`，两条独立待办可能落在同一 `startAt`，唯一索引会直接插入失败。去重在 `ScheduleMaterializer` 内按 `seriesId + originalStartAt` 集合比对完成（见 §5）。
> **注意**：索引名由 Room 生成为 `index_schedule_occurrences_seriesId` 等；§4 的迁移建表 SQL 必须使用**完全一致**的索引名，否则 Room 打开数据库时 schema 校验不过。

### 3.3 DAO

`core/database/daos/ScheduleSeriesDao.kt`（新增）：

```kotlin
@Dao
interface ScheduleSeriesDao {
    @Query("SELECT * FROM schedule_series WHERE deletedAt = 0 ORDER BY startAt ASC, id ASC")
    fun observeActive(): Flow<List<ScheduleSeriesEntity>>

    @Query("SELECT * FROM schedule_series WHERE deletedAt = 0 ORDER BY startAt ASC, id ASC")
    suspend fun getActive(): List<ScheduleSeriesEntity>

    @Query("SELECT * FROM schedule_series WHERE id = :id AND deletedAt = 0")
    suspend fun get(id: Long): ScheduleSeriesEntity?

    @Upsert
    suspend fun upsert(series: ScheduleSeriesEntity): Long

    @Query("UPDATE schedule_series SET recurrenceUntil = :until, updatedAt = :updatedAt WHERE id = :id AND deletedAt = 0")
    suspend fun truncate(id: Long, until: Long, updatedAt: Long)

    @Query("UPDATE schedule_series SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)
}
```

`core/database/daos/ScheduleOccurrencesDao.kt`（新增）：

```kotlin
@Dao
interface ScheduleOccurrencesDao {
    @Query("SELECT * FROM schedule_occurrences WHERE deletedAt = 0 ORDER BY startAt ASC, id ASC")
    fun observeAll(): Flow<List<ScheduleOccurrenceEntity>>

    @Query("SELECT * FROM schedule_occurrences WHERE deletedAt = 0 AND startAt < :toMillis AND endAt >= :fromMillis ORDER BY startAt ASC, id ASC")
    fun observeRange(fromMillis: Long, toMillis: Long): Flow<List<ScheduleOccurrenceEntity>>

    @Query("SELECT * FROM schedule_occurrences WHERE id = :id AND deletedAt = 0")
    suspend fun get(id: Long): ScheduleOccurrenceEntity?

    @Query("SELECT * FROM schedule_occurrences WHERE seriesId = :seriesId AND deletedAt = 0")
    suspend fun getBySeries(seriesId: Long): List<ScheduleOccurrenceEntity>

    @Query("SELECT * FROM schedule_occurrences WHERE deletedAt = 0 AND startAt >= :fromMillis ORDER BY startAt ASC, id ASC")
    suspend fun getUpcoming(fromMillis: Long): List<ScheduleOccurrenceEntity>

    @Insert
    suspend fun insert(occurrence: ScheduleOccurrenceEntity): Long

    @Insert
    suspend fun insertAll(occurrences: List<ScheduleOccurrenceEntity>): List<Long>

    @Update
    suspend fun update(occurrence: ScheduleOccurrenceEntity)

    @Query("UPDATE schedule_occurrences SET completed = :completed, completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean, completedAt: Long, updatedAt: Long)

    @Query("UPDATE schedule_occurrences SET reminderFiredAt = :firedAt, updatedAt = :firedAt WHERE id = :id")
    suspend fun markReminderFired(id: Long, firedAt: Long)

    @Query("UPDATE schedule_occurrences SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)

    @Query("UPDATE schedule_occurrences SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE seriesId = :seriesId AND detached = 0 AND completed = 0 AND deletedAt = 0 AND originalStartAt >= :fromOriginalStartAt")
    suspend fun softDeleteSeriesFrom(seriesId: Long, fromOriginalStartAt: Long, deletedAt: Long)

    @Query("UPDATE schedule_occurrences SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE seriesId = :seriesId AND detached = 0 AND completed = 0 AND deletedAt = 0")
    suspend fun softDeleteSeriesGenerated(seriesId: Long, deletedAt: Long)

    @Query("UPDATE schedule_occurrences SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE seriesId = :seriesId AND deletedAt = 0")
    suspend fun softDeleteSeriesAll(seriesId: Long, deletedAt: Long)
}
```

> **软删除的取舍（区分两类）**：`softDeleteSeriesFrom`、`softDeleteSeriesGenerated`、`pruneGeneratedOutsideWindow` 带 `detached = 0 AND completed = 0` 过滤 —— 它们清掉的是**可由系列规则原样重建**的行，用户手改（`detached`）与已完成的历史必须保留。
> `softDeleteSeriesAll` **不过滤**：它表达的是「整条系列连同历史一并删除」，此时留下孤儿实例没有意义。
> 另注意 `softDelete(seriesId)` 只软删系列行；在 `update`/`delete` 的 `ALL` 分支里，必须**同时**调用它，否则该系列仍是 active，下一次 `materializeAll` 会立刻把实例重新物化回来。

---

## 4. 数据库迁移 18 → 19

`core/database/AppDatabase.kt`：

- `@Database(..., version = 19)`，`entities` 追加 `ScheduleSeriesEntity::class`、`ScheduleOccurrenceEntity::class`。
- 新增 `abstract fun scheduleSeriesDao(): ScheduleSeriesDao`、`abstract fun scheduleOccurrencesDao(): ScheduleOccurrencesDao`。
- 新增 `MIGRATION_18_19` 并加入 `addMigrations(...)` 列表末尾。
- 迁移体调用新私有函数 `createScheduleTables(db)`，写法与既有 `createAppNetworkControls` 一致。

建表 SQL（字段顺序、`NOT NULL`、默认值必须与实体完全一致）：

```sql
CREATE TABLE IF NOT EXISTS schedule_series (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    title TEXT NOT NULL,
    category TEXT NOT NULL,
    allDay INTEGER NOT NULL,
    startAt INTEGER NOT NULL,
    endAt INTEGER NOT NULL,
    recurrence TEXT NOT NULL,
    recurrenceUntil INTEGER NOT NULL,
    reminderMinutesBefore INTEGER NOT NULL,
    reminderBefore INTEGER NOT NULL,   -- ❌ 不存在此列，勿加，仅示意列必须与实体一一对应
    reminderMethod TEXT NOT NULL,
    enabled INTEGER NOT NULL,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL,
    deletedAt INTEGER NOT NULL
)
```

> 实际 SQL 以实体字段为准（`ScheduleSeriesEntity` 共 14 列，**没有** `reminderBefore`）。上面那一行是给实现者的反例提醒：多一列或少一列都会让 Room 的 schema 校验在**运行期**抛 `IllegalStateException: Migration didn't properly handle`，而 CI 编译期发现不了。

```sql
CREATE TABLE IF NOT EXISTS schedule_occurrences (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    seriesId INTEGER NOT NULL,
    title TEXT NOT NULL,
    category TEXT NOT NULL,
    allDay INTEGER NOT NULL,
    startAt INTEGER NOT NULL,
    endAt INTEGER NOT NULL,
    reminderMinutesBefore INTEGER NOT NULL,
    reminderMethod TEXT NOT NULL,
    recurrence TEXT NOT NULL,
    recurrenceUntil INTEGER NOT NULL,
    originalStartAt INTEGER NOT NULL,
    detached INTEGER NOT NULL,
    completed INTEGER NOT NULL,
    completedAt INTEGER NOT NULL,
    reminderFiredAt INTEGER NOT NULL,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL,
    deletedAt INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS index_schedule_occurrences_seriesId ON schedule_occurrences (seriesId);
CREATE INDEX IF NOT EXISTS index_schedule_occurrences_startAt ON schedule_occurrences (startAt);
CREATE INDEX IF NOT EXISTS index_schedule_occurrences_deletedAt ON schedule_occurrences (deletedAt);
```

> 数据库当前 `version = 18`（`AppDatabase.kt:44`），最后一条迁移是 `MIGRATION_17_18`。
> `AppDatabase.create()` 在 `BuildConfig.DEBUG` 下开了 `fallbackToDestructiveMigration`，所以**调试包掩盖的迁移错误会在 release 包暴露** —— 迁移 SQL 必须一次写对。

---

## 5. 重复展开引擎与物化

### 5.1 `core/schedule/ScheduleRecurrenceExpander.kt`（新增，纯 Kotlin，零 Android 依赖）

```kotlin
object ScheduleRecurrenceExpander {
    const val MAX_OCCURRENCES_PER_SERIES = 400

    fun expand(
        series: ScheduleSeriesEntity,
        fromMillis: Long,
        toMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        limit: Int = MAX_OCCURRENCES_PER_SERIES,
    ): List<Long>   // 返回本次发生的 startAt 列表（升序）
}
```

返回**时刻列表**而非实体，是为了让物化器能自由构造实体、也便于单测断言。

语义（逐条都要实现并测到）：

| 规则 | 步进 | 边界行为 |
|---|---|---|
| `NONE` | — | 仅当 `startAt ∈ [from, to)` 时返回 1 条（实际不会进这里，系列层不收 `NONE`；独立日程不走展开） |
| `DAILY` | `+1 day` | 保持当地墙上时间（`ZonedDateTime.plusDays`，跨夏令时正确） |
| `WEEKDAYS` | `+1 day`，跳过周六周日 | 锚点若落在周末，**不**顺延锚点，而是从锚点当天起逐一判断（周末不产出） |
| `WEEKLY` | `+7 days` | 保持星期几 |
| `MONTHLY` | `+1 month` 同日号 | **目标月没有该日号时跳过该月**（1/31 → 2 月无、3/31 有），不 clamp 到月末。理由：clamp 会让 1/31 起的系列在 2 月变成 2/28，之后永久漂到 28 号，与主流日历（Google Calendar `BYMONTHDAY` 语义）不一致 |
| `YEARLY` | `+1 year` 同月日 | 2/29 在平年**跳过** |

统一规则：

1. 单次时长 `duration = series.endAt - series.startAt`，每次发生 `start = occurrenceStart`、`end = start + duration`。
2. 只产出 `start >= fromMillis && start < toMillis` 的发生时刻。
3. 终止条件：`series.recurrenceUntil > 0 && occurrenceStart > series.recurrenceUntil` → 停止。`recurrenceUntil` 存的是「结束重复」当天的 `23:59:59.999`，所以是**含当日**。
4. 硬上限 `limit` 条，防止无限循环（尤其 `recurrenceUntil = 0`）。
5. 迭代起点直接从 `fromMillis` 附近推算，而非从 `series.startAt` 一天天爬——但必须**保证步进与锚点对齐**（DAILY 对齐到锚点的时分秒；WEEKLY 对齐到锚点星期几；MONTHLY/YEARLY 对齐日号）。
   - 实现建议：从 `series.startAt` 开始按规则步进，跳过 `start < fromMillis` 的项（`limit` 上限保证不会失控）；若单系列跨度极大（数年），用「按天数差直接跳」的快速定位，避免 O(n) 爬行。**两种实现都可接受，但必须有单测钉住结果**。

### 5.2 `core/schedule/ScheduleMaterializer.kt`（新增）

```kotlin
class ScheduleMaterializer(
    private val seriesDao: ScheduleSeriesDao,
    private val occurrencesDao: ScheduleOccurrencesDao,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun materializeAll(nowMillis: Long = System.currentTimeMillis())

    suspend fun materializeSeries(seriesId: Long, nowMillis: Long = System.currentTimeMillis())

    companion object {
        const val PAST_WINDOW_DAYS = 7L
        const val FUTURE_WINDOW_DAYS = 60L
    }
}
```

`materializeSeries` 步骤：

1. 取系列；`null` 或 `deletedAt != 0` → 直接返回。
2. 窗口 `from = now - 7 天`，`to = now + 60 天`；调用展开器得到期望 `startAt` 列表。
3. 读该系列现有未删除实例，建 `originalStartAt` 集合（**`detached` 与 `completed` 的也计入**，避免重复生成）。
4. 对期望列表中集合里没有的，构造实体插入：
   - `originalStartAt = 期望 startAt`
   - `title / category / allDay / reminderMinutesBefore / reminderMethod` 取自系列
   - `recurrence = series.recurrence`、`recurrenceUntil = series.recurrenceUntil`
   - `detached = false`、`completed = false`、`reminderFiredAt = 0`
5. 清理：删除该系列中 `detached = 0 && completed = 0 && deletedAt = 0` 且 `originalStartAt` 落在窗口外的实例（`originalStartAt < from || originalStartAt >= to`）。
   - 用于新建 DAO 方法 `pruneGeneratedOutsideWindow`（见下方补充）。

> DAO 补充方法（`ScheduleOccurrencesDao` 内一并实现）：
> ```kotlin
> @Query("SELECT originalStartAt FROM schedule_occurrences WHERE seriesId = :seriesId AND deletedAt = 0")
> suspend fun getOriginalStartAts(seriesId: Long): List<Long>
>
> @Query("UPDATE schedule_occurrences SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE seriesId = :seriesId AND detached = 0 AND completed = 0 AND deletedAt = 0 AND (originalStartAt < :fromMillis OR originalStartAt >= :toMillis)")
> suspend fun pruneGeneratedOutsideWindow(seriesId: Long, fromMillis: Long, toMillis: Long, deletedAt: Long)
> ```

`materializeAll` 步骤：遍历 `seriesDao.getActive()` 逐个调用 `materializeSeries`；**同时把窗口内的独立日程（`seriesId = 0`）保留原样，不做任何清理**。

调用时机：

- `ScheduleRepository` 构造后首次使用、以及 `ProjectLumenViewModel` 启动流程里（与 `ensureDefault()` 同批）。
- `BootReceiver.restoreScheduledWork` 内（重建提醒前先补齐窗口）。
- 每次系列被创建 / 编辑 / 删除后，由 `ScheduleRepository` 内部触发。

### 5.3 `core/schedule/ScheduleDraft.kt`（新增，UI ↔ 仓储契约）

```kotlin
data class ScheduleDraft(
    val title: String,
    val category: ScheduleCategory,
    val allDay: Boolean,
    val startAt: Long,
    val endAt: Long,
    val reminderMinutesBefore: Int,          // -1 = 不提醒
    val reminderMethod: ScheduleReminderMethod,
    val recurrence: ScheduleRecurrence,
    val recurrenceUntil: Long,               // 0 = 永不结束
)
```

---

## 6. 仓储 `core/repositories/ScheduleRepository.kt`（新增）

```kotlin
class ScheduleRepository(
    private val seriesDao: ScheduleSeriesDao,
    private val occurrencesDao: ScheduleOccurrencesDao,
    private val materializer: ScheduleMaterializer,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val onScheduleChanged: () -> Unit = {},
) {
    fun observeAll(): Flow<List<ScheduleOccurrenceEntity>>

    suspend fun get(id: Long): ScheduleOccurrenceEntity?
    suspend fun getSeries(id: Long): ScheduleSeriesEntity?

    /** 新建。recurrence == NONE → 单条独立日程；否则建系列并展开。返回新建实例 id。 */
    suspend fun create(draft: ScheduleDraft): Long

    /** 编辑已有实例。scope 决定「仅此 / 此及将来 / 全部」语义。返回被编辑（或被新建）的实例 id。 */
    suspend fun update(id: Long, draft: ScheduleDraft, scope: ScheduleEditScope): Long

    /** 删除。scope 语义同 update。 */
    suspend fun delete(id: Long, scope: ScheduleEditScope)

    suspend fun setCompleted(id: Long, completed: Boolean)

    /** `startAt >= nowMillis` 且未完成、有提醒设置的实例，按 startAt 升序，取前 limit 条。供提醒调度使用。 */
    suspend fun upcomingReminders(nowMillis: Long, limit: Int): List<ScheduleOccurrenceEntity>

    suspend fun markReminderFired(id: Long, firedAt: Long)

    /** 启动 / 开机 / 跨天时调用，补齐物化窗口。 */
    suspend fun refreshWindow(nowMillis: Long = now())
}
```

### 6.1 `create(draft)`

- `draft.recurrence == NONE`：
  插入单条，`seriesId = 0`、`originalStartAt = draft.startAt`、`detached = false`。
- 否则：
  1. 插入系列（`enabled = true`）。
  2. 调用 `materializer.materializeSeries(seriesId, now)`。
  3. 返回该系列中 `originalStartAt == draft.startAt` 的实例 id。

> **返回值约定**：`create` 的返回值只供单元测试与「新建后刷新列表」使用，**UI 不得用它做后续寻址**。
> 锚点若早于物化窗口起点（用户建了一条过去很久的重复日程），步骤 3 找不到匹配实例，此时返回 `0L`。
> 这是**有意**的宽松约定，避免「锚点被窗口裁掉」演变成一条隐性契约（见 §13 K-7）。

### 6.2 `update(id, draft, scope)` — 三种编辑范围

设 `target = occurrencesDao.get(id)`，`series = target.seriesId != 0 ? seriesDao.get(target.seriesId) : null`。

**A. 独立日程（`target.seriesId == 0`）**

- 三个 scope 行为一致：直接写回 `target.copy(...)` 的新字段。
- `originalStartAt` 随 `startAt` **同步更新**：独立日程的 `originalStartAt` 恒等于 `startAt`（§3.2）。
- 特殊：`draft.recurrence != NONE` → **升级为系列**：删掉该独立实例（软删），按 `create(draft)` 建系列，返回新实例 id。
- 特殊：改 `recurrence` 为 `NONE` 表示「不再重复」→ 见 D。
- 防御分支：`seriesId != 0` 但系列行已不存在（脏数据）时，降级为 B 的写法（按 `THIS_ONLY` 处理并置 `detached = true`），不抛异常。

**B. `THIS_ONLY`**

1. `target.copy(title, category, allDay, startAt, endAt, reminderMinutesBefore, reminderMethod, detached = true, updatedAt = now)` 写回。
2. **`recurrence` 与 `recurrenceUntil` 不采纳 `draft` 的值**，保持原样（这条实例仍属于原系列，只是这一次被改写）。
   - UI 侧在 `THIS_ONLY` 范围下**禁用**「重复」与「结束重复」控件并给出说明文案（见 §9.4），保证用户预期一致。
3. 系列本身不动，`originalStartAt` 不变。

**C. `THIS_AND_FUTURE`**

> 本分支**不需要 delta**：新系列的锚点直接取 `draft.startAt`，平移量只有 D 才用得到。

1. 若 `target.originalStartAt <= series.startAt`（改的就是系列首次）→ 直接按 **D（ALL）** 处理。
2. 否则：
   - `seriesDao.truncate(series.id, until = target.originalStartAt - 1, updatedAt = now)`（旧系列截止到该次之前）。
   - `occurrencesDao.softDeleteSeriesFrom(series.id, target.originalStartAt, now)`（清掉旧系列从该次起**未完成、未脱离**的已生成实例）。
   - **`draft.recurrence == NONE`（从此刻起不再重复）**：不建新系列，改插一条独立日程（`seriesId = 0`、`originalStartAt = draft.startAt`），`target.completed` 时迁移完成态。系列层不存 `NONE`。
   - 否则建新系列：锚点 `startAt = draft.startAt`、`endAt = draft.startAt + (draft.endAt - draft.startAt)`；`recurrence`、`recurrenceUntil`、`reminder*`、`title`、`category`、`allDay` 全取 `draft`；然后物化。
   - 若 `target.completed`：在新系列中找到 `originalStartAt == draft.startAt` 的实例，置 `completed = true`（迁移完成态）。
   - 返回值：新系列中 `originalStartAt == draft.startAt` 的实例 id；找不到时回退该系列最早一条，再找不到为 `0L`。

**D. `ALL`**

1. `delta = draft.startAt - target.originalStartAt`（用 `originalStartAt` 而非 `startAt`，保证 `detached` 过的实例也能算对）。
2. 平移系列锚点：`startAt = series.startAt + delta`、`endAt = series.endAt + delta`，其余字段取 `draft`（`title / category / allDay / recurrence / recurrenceUntil / reminderMinutesBefore / reminderMethod`）。
3. `occurrencesDao.softDeleteSeriesGenerated(series.id, now)`（清掉**全部未完成、未脱离**的生成实例）。
4. 物化系列。
5. `draft.recurrence == NONE` 时：不保留系列 —— `softDeleteSeriesAll(series.id, now)` **并且** `seriesDao.softDelete(series.id, now)`（少了后者该系列仍是 active，下一次 `materializeAll` 会立刻把实例重新物化回来）—— 然后插一条独立日程（用 `target.completed` 迁移完成态）。

### 6.3 `delete(id, scope)`

- `THIS_ONLY` → `softDelete(id)`。
- `THIS_AND_FUTURE` → `seriesDao.truncate(seriesId, id 的 originalStartAt - 1)` + `softDeleteSeriesFrom(seriesId, originalStartAt)`；若该次就是系列首次，等价于 `ALL`。
- `ALL` → `softDeleteSeriesAll(seriesId)` + `seriesDao.softDelete(seriesId)`。
- 独立日程 → 直接 `softDelete(id)`。

### 6.4 `setCompleted(id, completed)`

`completedAt = completed ? now() : 0`；同时 `occurrencesDao.setCompleted(...)`。
勾选完成**不**影响系列，也**不**取消已排的提醒（提醒触发时再校验 `completed`，见 §7.4）。

---

## 7. 提醒与闹钟链路

### 7.1 常量

`core/services/NotificationChannels.kt` 追加：

```kotlin
const val SCHEDULE_NOTIFICATION = "schedule_reminder"   // 通知提醒
const val SCHEDULE_ALARM = "schedule_alarm"             // 闹钟提醒
```

`core/constants/NotificationIds.kt` 追加：

```kotlin
const val SCHEDULE_REMINDER_BASE = 9600      // 9600..9899，与 occurrence.id 取模映射
const val SCHEDULE_REMINDER_RANGE = 300
```

### 7.2 `core/services/ScheduleReminderScheduler.kt`（新增）

```kotlin
class ScheduleReminderScheduler(private val context: Context) {
    fun schedule(occurrence: ScheduleOccurrenceEntity, triggerAtMillis: Long)
    fun cancel(occurrenceId: Long)
    fun cancelAll(occurrences: List<ScheduleOccurrenceEntity>)
    fun notificationIdFor(occurrenceId: Long): Int
    fun canScheduleExactAlarms(): Boolean          // 与 NotificationService 同实现
    fun canUseFullScreenIntents(): Boolean         // 同上
}
```

- `notificationIdFor(id) = SCHEDULE_REMINDER_BASE + (id % SCHEDULE_REMINDER_RANGE).toInt()`。
- PendingIntent：`getBroadcast(context, notificationIdFor(id), Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_SCHEDULE_REMINDER).setPackage(packageName).putExtra(EXTRA_OCCURRENCE_ID, id), FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)`。
- 触发时刻 `= occurrence.startAt - reminderMinutesBefore * 60_000`；`<= now` 时不排。
- 调度实现与 `NotificationService.schedule` 一致：能精确闹钟就 `setExactAndAllowWhileIdle`，否则退 `setAndAllowWhileIdle`，`SecurityException` 兜底。
- `EXTRA_OCCURRENCE_ID` 常量定义在 `ScheduleReminderScheduler` companion：`const val EXTRA_OCCURRENCE_ID = "com.projectlumen.app.extra.SCHEDULE_OCCURRENCE_ID"`。

### 7.3 `AlarmReceiver` 追加分支

- 新增 `const val ACTION_SCHEDULE_REMINDER = "com.projectlumen.app.action.SCHEDULE_REMINDER"`。
- `onReceive` 内在既有 `runCatching` 块中、`reconcileNow` **之前**加一个提前分支（日程提醒与护眼引擎无关，不应被护眼状态影响）：

```kotlin
if (intent.action == ACTION_SCHEDULE_REMINDER) {
    app ?: return@runCatching
    val occurrenceId = intent.getLongExtra(ScheduleReminderScheduler.EXTRA_OCCURRENCE_ID, 0L)
    if (occurrenceId != 0L) {
        ScheduleReminderDispatcher.dispatch(app, occurrenceId, System.currentTimeMillis())
    }
    // 日程提醒不参与护眼引擎结算，处理完直接结束
    return@runCatching   // 注意：runCatching 内 return 需用 return@runCatching
}
```

> 实现时若 `return@runCatching` 在 `goAsync` 场景下不便，等价做法是把既有护眼底下整段包进 `else { ... }`。两种都接受，但**必须**保证日程提醒分支不会走到 `reconcileNow` / 护眼通知逻辑。

- 日程提醒**不受** `settings.notificationEnabled` 与 `QuietHours` 的护眼静音策略约束？——**受通知权限约束，但不受护眼提醒静音约束**：日程是用户显式创建的待办，`QuietHours` 的护眼语义不适用；`notificationEnabled` 也不应吞掉用户自建的日程提醒。**决定**：日程提醒只看系统通知权限（`POST_NOTIFICATIONS`），不受 App 内护眼开关影响。此决策写进文档以便核对。

### 7.4 `core/services/ScheduleReminderDispatcher.kt`（新增）

```kotlin
object ScheduleReminderDispatcher {
    suspend fun dispatch(app: ProjectLumenApplication, occurrenceId: Long, nowMillis: Long)
}
```

步骤：

1. 读实例：`app.database.scheduleOccurrencesDao().get(occurrenceId)`。
2. `null || deletedAt != 0` → 直接返回（已被删除的日程不响）。
3. 计算 `triggerAt = startAt - reminderMinutesBefore * 60_000`；`notifications` 侧校验：`nowMillis - triggerAt > STALE_AFTER_MILLIS (15 分钟)` → 视为过期，不响（开机后补跑不该弹一堆陈年提醒），但仍标记 `reminderFiredAt`。
4. `completed` → 不响（已勾选完成的待办不再提醒）。
5. `reminderMinutesBefore < 0` → 不响。
6. `reminderFiredAt != 0` → 不响（防重复）。
7. 按 `reminderMethod` 发通知（调用 `NotificationService.showScheduleReminder(...)`，见 §7.5）。
8. `occurrencesDao.markReminderFired(id, nowMillis)`。

> **两点有意为之的语义**（核对时不要当成 bug）：
> 1. 步骤 4/5/6 三条「不响」路径**不**标记 `reminderFiredAt`：它们表达的不是「已提醒过」，而是「这条本来就不该提醒」，而且 §7.6 的重排本身也会过滤掉它们（只排 `triggerAt > now && reminderFiredAt == 0`）。
> 2. 系统通知权限缺失时 `NotificationService.show()` 会静默返回，但步骤 8 仍执行 —— 该提醒就此被消费，不再补发。理由：拒绝通知权限是用户的选择，反复重试只会在用户某次重新授权时一次性炸出一堆过期提醒（与步骤 3 的过期抑制同源）。

### 7.5 `NotificationService` 追加

```kotlin
fun showScheduleReminder(occurrence: ScheduleOccurrenceEntity)
```

- 渠道：`NOTIFICATION` → `NotificationChannels.SCHEDULE_NOTIFICATION`；`ALARM` → `NotificationChannels.SCHEDULE_ALARM`。
- 通知 id：`ScheduleReminderScheduler(context).notificationIdFor(occurrence.id)`。
- 标题 = `occurrence.title`；正文 = 时间描述（`schedule_notification_message` 带 `HH:mm` 参数）。
- `setAutoCancel(true)`、`CATEGORY_REMINDER`（通知提醒）/ `CATEGORY_ALARM`（闹钟提醒）。
- 闹钟提醒额外 `setFullScreenIntent(openAppPendingIntent(id + FULL_SCREEN_REQUEST_CODE_OFFSET), true)`，仅在 `canUseFullScreenIntents()` 为真时设置。
- `ensureChannels()` 追加两个渠道：

| 渠道 id | 名称资源 | 重要性 | 声音 | 振动 |
|---|---|---|---|---|
| `schedule_reminder` | `channel_schedule_reminder` | `IMPORTANCE_HIGH` | 默认 | 开 |
| `schedule_alarm` | `channel_schedule_alarm` | `IMPORTANCE_HIGH` | 默认 | 开 |

> 与既有护眼渠道（`setSound(null, null)` + 振动）不同，日程提醒渠道**保留默认提示音**，因为「提醒方式」的语义就是通知 vs 闹钟；若要静音可走系统渠道设置。

### 7.6 `BootReceiver` 追加

在 `restoreScheduledWork(app)` 内、`reconcileNow` **之前**追加一次 `app.rescheduleScheduleReminders()`。

落盘后的实现把「补窗口 + 建闹钟」收进了 `ProjectLumenApplication` 的单一入口（开机、精确闹钟权限变更、日程编辑三条路径共用，避免三处重复构造仓储）：

```kotlin
// ProjectLumenApplication.kt
val scheduleRepository: ScheduleRepository by lazy {
    ScheduleRepository(
        database.scheduleSeriesDao(),
        database.scheduleOccurrencesDao(),
        ScheduleMaterializer(database.scheduleSeriesDao(), database.scheduleOccurrencesDao()),
    )
}

suspend fun rescheduleScheduleReminders() {
    scheduleRepository.refreshWindow()                    // 先补齐物化窗口
    ScheduleAlarmRestore.rearm(this, scheduleRepository)  // 再重建闹钟
}
```

`BootReceiver` 侧：

```kotlin
runCatching { app.rescheduleScheduleReminders() }
    .onFailure { throwable -> app.recordHandledFailure(throwable) }
```

`core/services/ScheduleAlarmRestore.kt`（新增）：

```kotlin
object ScheduleAlarmRestore {
    const val MAX_SCHEDULED_ALARMS = 12

    suspend fun rearm(app: ProjectLumenApplication, repository: ScheduleRepository, nowMillis: Long = System.currentTimeMillis()) {
        val scheduler = ScheduleReminderScheduler(app)
        val upcoming = repository.upcomingReminders(nowMillis, MAX_SCHEDULED_ALARMS)
        upcoming.forEach { occurrence ->
            val triggerAt = occurrence.startAt - occurrence.reminderMinutesBefore * 60_000L
            if (occurrence.reminderMinutesBefore >= 0 && triggerAt > nowMillis && occurrence.reminderFiredAt == 0L) {
                scheduler.schedule(occurrence, triggerAt)
            }
        }
    }
}
```

> **为什么只排前 12 条**：AlarmManager 的精确闹钟数量有系统级上限（且 Doze 下过量闹钟会被降级），窗口内通常不会超过 12 条待提醒日程。这是**有意的取舍**，写进文档：超过 12 条的日程提醒会延后到下次 App 打开或开机恢复时才排上。

### 7.7 日程变更后的重排

`ScheduleRepository` 在 `create / update / delete / setCompleted / refreshWindow` 完成后，通过构造注入的回调 `onScheduleChanged: () -> Unit`（默认空实现，由 `ProjectLumenApplication` 或 ViewModel 侧装配）触发一次重排。为保持仓储纯粹、可单测，**不在仓储内直接依赖 Android 的 AlarmManager**。

装配位置（二选一，实现时择一并保持一致）：

- `ProjectLumenApplication` 提供 `val scheduleRepository: ScheduleRepository by lazy { ... }` 与 `suspend fun rescheduleScheduleReminders()`。
- `ProjectLumenScheduleFeatureEntry` 在每次写操作后调用 `ScheduleAlarmRestore.rearm(...)`。

> 本次实现**两者都用了**，分工如下：
>
> - `ScheduleRepository` 构造函数保留 `onScheduleChanged: () -> Unit = {}` 参数以便未来下沉，但不使用（四类实例都不传），避免仓储持有 `Context`。
> - 真正的重排入口是 `ProjectLumenApplication.rescheduleScheduleReminders()`（§7.6），开机 / 精确闹钟权限变更 / 日程编辑三条路径共用。
> - `ProjectLumenScheduleFeatureEntry` 的 `rearm: suspend () -> Unit` 由 `ProjectLumenViewModel` 的构造参数 `rescheduleScheduleReminders: suspend () -> Unit` 注入，`MainActivity` 传 `app::rescheduleScheduleReminders`。ViewModel 本身没有 `Context`，只能靠回调。
> - 因此 `persist()` / `delete()` / `setCompleted()` 里的 `rearm()` 调用点位于 FeatureEntry（与本节结论一致），但**实际执行体**在 Application。

### 7.8 `ExactAlarmPermissionReceiver` 追加（启动期之外的第二个重排点）

`core/services/ExactAlarmPermissionReceiver.kt` 原本只在用户授予/撤销精确闹钟权限时重排**护眼**闹钟。日程提醒同样受该权限支配，所以这个 receiver 里也必须补一次：

```kotlin
runCatching {
    app.rescheduleScheduleReminders()   // 同 §7.6 的单一入口
}.onFailure { throwable -> app.recordHandledFailure(throwable) }
```

要点：

- 必须放在 `app.runtimeRepository().get() ?: return@runCatching` **之前**，否则 runtime 为空时日程重排会被整段跳过。
- 单独包一层 `runCatching`：日程失败不能连累护眼闹钟的 `syncRuntimeAlarms`。
- 权限**授予**时把降级的不精确闹钟升级为精确，**撤销**时反向降级。由于 `PendingIntent` 复用同一 request code 且带 `FLAG_UPDATE_CURRENT`，`setExactAndAllowWhileIdle` / `setAndAllowWhileIdle` 会就地替换原闹钟，**无需先 cancel**。

> 不补这一步的后果：用户在系统设置里刚授予精确闹钟权限后，已排的日程提醒仍停留在 `setAndAllowWhileIdle` 的降级排程，要等下次开机或重新打开 App 才升级 —— 这期间提醒可能延迟数分钟到数十分钟。

---

## 8. 状态流接线

### 8.1 `ProjectLumenRepositories`

```kotlin
val schedule = ScheduleRepository(
    database.scheduleSeriesDao(),
    database.scheduleOccurrencesDao(),
    ScheduleMaterializer(database.scheduleSeriesDao(), database.scheduleOccurrencesDao()),
)
```

### 8.2 `ProjectLumenUiState`

新增字段：

```kotlin
val scheduleTasks: List<ScheduleOccurrenceEntity> = emptyList(),
```

### 8.3 `ProjectLumenStateStore`

在 `dataState` 的 `combine` 中追加 `repositories.schedule.observeAll().catch { recordHandledFailure(it); emit(emptyList()) }.distinctUntilChanged()`，写入 `scheduleTasks`。

> `combine` 从 5 参重载升到 6 参重载（Kotlin 的 `combine` 支持到 5 个 Flow 的具名重载；6 个需用 `combine(vararg)` 或嵌套 combine）。
> **实现要求**：改用嵌套 combine（把 `schedule` 与 `deviceInsights` 合并成一个中间 Flow，或把 `baseDataState` 与 `schedule` 先合并），避免依赖不存在的 6 参重载而编译失败。这是本组最容易踩的编译坑。

### 8.4 首页列表的过滤口径

首页卡片只展示**今天 + 未来 7 天**的未完成待办，以及**今天**已完成的条目（保留当日成就感的勾选回显）。

过滤函数（放在 `ProjectLumenHomeScheduleCard.kt` 或 `ProjectLumenScheduleState.kt`，**只放一处**）：

```kotlin
internal fun scheduleHomeItems(
    tasks: List<ScheduleOccurrenceEntity>,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<ScheduleOccurrenceEntity>
```

规则：`deletedAt == 0` 且 `startAt < 今天+8天 00:00`，且（`!completed` 或 `completed && startAt >= 今天 00:00`），按 `startAt` 升序，最多 8 条。

> 已知取舍：UI 侧过滤而非 DAO 分窗查询，原因是 `uiState.nowMillis` 每秒钟变化，无法作为稳定的 Flow 查询参数。物化窗口（`§5.2`）已把总量压在可控范围（7 天前 ~ 60 天后）。

---

## 9. UI 设计

### 9.1 新增路由

`ProjectLumenApp.kt` 的 `Destination` 枚举追加：

```kotlin
SCHEDULE("schedule", R.string.nav_schedule, Icons.Outlined.EventNote, false),
```

- `showInBottomNav = false` → 走二级页（展开式 TopAppBar + 返回箭头 + `BackHandler`），与 `TEMPLATES` / `ABOUT` 一致。
- NavHost 注册：`composable("${Destination.SCHEDULE.route}?scheduleId={scheduleId}") { ... }`，参数 `scheduleId: Long = 0L`；`0` 表示新建。
- 首页卡片点击条目 → `navController.navigate("schedule?scheduleId=$id")`；点「新建」→ `navigate("schedule")`。

### 9.2 `app/ProjectLumenHomeScheduleCard.kt`（新增）

首页卡片，挂在 `HomeScreen` 的 `GoalProgressCard` 之后、`HomeConvenienceCard` 之前（待办是「今天要做什么」，位置要在工具类卡片之前）。

内容：

- `SectionHeader(Icons.Outlined.EventNote, R.string.schedule_home_title)`
- 空态：`EmptyStateMessage(R.string.schedule_home_empty)` + 「新建待办」按钮
- 列表：每条一行
  - 左侧 `Checkbox`（勾选 → `onToggleCompleted(id, checked)`）
  - 中间：标题（已完成加删除线）+ 次要行（`今天 06:30至07:30` / `重复：每天`）
  - 右侧：点击整行进入详情
- 底部：`OutlinedButton`「新建待办」+ （有数据时）`OutlinedButton`「查看全部」→ 进入详情页的列表态？——**不做「查看全部」**，保持首页只有列表 + 新建，避免超范围。

参数签名：

```kotlin
@Composable
internal fun HomeScheduleCard(
    tasks: List<ScheduleOccurrenceEntity>,
    nowMillis: Long,
    onToggleCompleted: (Long, Boolean) -> Unit,
    onOpenSchedule: (Long) -> Unit,
    onCreateSchedule: () -> Unit,
)
```

### 9.3 `app/ProjectLumenScheduleFeatureEntry.kt`（新增）

持有详情页草稿状态与全部写操作，模式对齐 `ProjectLumenTemplatesFeatureEntry`。

```kotlin
internal data class ScheduleDraftState(
    val editingId: Long? = null,          // null = 新建
    val seriesId: Long = 0L,              // 0 = 独立/新建
    val hasSeries: Boolean = false,       // 是否属于重复系列（决定是否弹编辑范围）
    val draft: ScheduleDraft,
    val pendingAction: SchedulePendingAction? = null,   // 等待编辑范围选择的动作
)

internal enum class SchedulePendingAction { SAVE, DELETE }

internal class ProjectLumenScheduleFeatureEntry(
    private val scope: CoroutineScope,
    private val repository: ScheduleRepository,
    private val rearm: suspend () -> Unit,
    private val recordHandledFailure: (Throwable) -> Unit,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    val detailState: StateFlow<ScheduleDraftState?>
    fun openNew()
    fun open(id: Long)
    fun close()
    fun updateDraft(transform: (ScheduleDraft) -> ScheduleDraft)
    fun requestSave()     // 无系列 → 直接保存；有系列 → 置 pendingAction = SAVE
    fun requestDelete()   // 同上
    fun confirmScope(scope: ScheduleEditScope)
    fun dismissScopePrompt()
    fun setCompleted(id: Long, completed: Boolean)
}
```

`open(id)` 的草稿来源：读实例；`recurrence` / `recurrenceUntil` 用实例上的冗余字段；`hasSeries = seriesId != 0L`。

`confirmScope` 完成后关闭详情页（`detailState = null`）并调用 `rearm()`。

### 9.4 `app/ProjectLumenScheduleScreens.kt`（新增）

`ScheduleDetailScreen(uiState, viewModel, onBack)`，字段到控件的映射**严格按用户给的清单**：

| # | 界面字段（用户原文） | 控件 | 数据源 / 行为 |
|---|---|---|---|
| 1 | 编辑 / 新建 | 顶部 `LumenTopBar` 已提供，页面内不再重复 | 标题由 `Destination.SCHEDULE.labelRes` 决定 |
| 2 | 日程名称 | `OutlinedTextField` | `draft.title` |
| 3 | `今天 周日` | `Text`（摘要行第 1 段） | `scheduleDayLabel(startAt, now)` |
| 4 | `06:30至07:30` | `Text`（摘要行第 2 段） | 全天 → 「全天」；否则 `HH:mm至HH:mm` |
| 5 | `重复：每天` | `Text`（摘要行第 3 段） | `重复：${recurrenceLabel}`；`NONE` → 「不重复」 |
| 6 | `类型` / `个人` | `Text`（摘要行第 4 段） | `类型：${categoryLabel}` |
| 7 | `提醒` / `5分钟前` | `Text`（摘要行第 5 段） | `提醒：${reminderLabel}` |
| 8 | 全天 | `SwitchRow` | `draft.allDay`；开启时把时间归整为当天 00:00 ~ 次日 00:00 |
| 9 | 开始 | 行：日期按钮 + 时间按钮 | `Material3 DatePickerDialog` / `TimePickerDialog`（`android.app.TimePickerDialog` 或 M3 `TimePicker`，实现择一） |
| 10 | 结束 | 同上 | 结束早于开始时自动顺延为「开始 + 原时长」（保持原时长，最小 5 分钟） |
| 11 | 类型 | `LumenFlowRow` + `FilterChip` × 4 | 个人 / 工作 / 学习 / 其他 |
| 12 | 提醒 | `LumenFlowRow` + `FilterChip` × 8 | 不提醒(`-1`) / 准时(`0`) / 5分钟前 / 10分钟前 / 15分钟前 / 30分钟前 / 1小时前 / 1天前(`1440`) |
| 13 | 提醒方式 | `LumenFlowRow` + `FilterChip` × 2 | 通知提醒 / 闹钟提醒；`reminderMinutesBefore == -1` 时**禁用** |
| 14 | 重复 | `LumenFlowRow` + `FilterChip` × 6 | 不重复 / 每天 / 工作日 / 每周 / 每月 / 每年 |
| 15 | 结束重复 | 「永不结束」开关 + 结束日期 | 开启 = `recurrenceUntil = 0`；关闭则在「重复」不为 `NONE` 时可选日期（默认 `startAt + 30 天` 当天 23:59:59.999） |
| 16 | 删除 | `OutlinedButton` | 仅编辑已有条目时显示 |
| 17 | 编辑范围 | `AlertDialog` | `hasSeries` 且为编辑态时，保存/删除先弹；三选项对应 `ScheduleEditScope` |

`THIS_ONLY` 的额外约束（与 §6.2-B 对应）：在该范围下保存时，**「重复」与「结束重复」的改动被丢弃**；因此弹层出现时若用户改过这两项，需在弹层内提示「仅更改此日程不会修改重复规则」。实现要求：弹层文案包含该提示（`schedule_scope_this_only_hint`）。

### 9.5 时间/日期格式化工具

放在 `ProjectLumenScheduleScreens.kt` 内的 **internal 顶层函数**（不新建「超级文件」，也不塞进既有的 `ProjectLumenUiFormatters.kt` 以免文件继续膨胀）：

```kotlin
internal fun scheduleDayLabel(startAt: Long, nowMillis: Long): String   // 今天/明天/昨天/M月d日 + 周几
internal fun scheduleTimeRangeLabel(startAt: Long, endAt: Long, allDay: Boolean): String
internal fun scheduleDateTimeLabel(millis: Long): String                // 2026年9月13日06:30
```

### 9.6 首页挂载

`ProjectLumenMainScreens.kt` 的 `HomeScreen` 在 `GoalProgressCard(uiState)` 之后插入：

```kotlin
HomeScheduleCard(
    tasks = uiState.scheduleTasks,
    nowMillis = uiState.nowMillis,
    onToggleCompleted = viewModel::setScheduleCompleted,
    onOpenSchedule = { id -> navController.… },   // 由 ProjectLumenApp 传入回调
    onCreateSchedule = { … },
)
```

`HomeScreen` 已签名 `(uiState, viewModel, openTranslation: () -> Unit)`，新增两个导航回调参数：

```kotlin
internal fun HomeScreen(
    uiState: ProjectLumenUiState,
    viewModel: ProjectLumenViewModel,
    openTranslation: () -> Unit,
    openSchedule: (Long) -> Unit,
    createSchedule: () -> Unit,
)
```

### 9.7 ViewModel 门面

```kotlin
val scheduleDetailState: StateFlow<ScheduleDraftState?>
fun openScheduleDetail(id: Long)     // id = 0L → 新建
fun closeScheduleDetail()
fun updateScheduleDraft(transform: (ScheduleDraft) -> ScheduleDraft)
fun requestSaveSchedule()
fun requestDeleteSchedule()
fun confirmScheduleScope(scope: ScheduleEditScope)
fun dismissScheduleScopePrompt()
fun setScheduleCompleted(id: Long, completed: Boolean)
```

---

## 10. 字符串清单

`res/values/strings.xml`（英文默认）与 `res/values-zh/strings.xml`（中文）**两侧都要加**，名称完全一致。

| 名称 | en | zh |
|---|---|---|
| `nav_schedule` | Schedule | 日程 |
| `schedule_home_title` | To-do & schedule | 待办任务 |
| `schedule_home_empty` | No to-dos yet. Create one to plan your day. | 还没有待办，新建一条来安排今天吧。 |
| `schedule_create` | New to-do | 新建待办 |
| `schedule_new_title` | New schedule | 新建日程 |
| `schedule_edit_title` | Edit | 编辑 |
| `schedule_name_label` | Schedule name | 日程名称 |
| `schedule_name_placeholder` | Enter a name | 请输入日程名称 |
| `schedule_all_day` | All day | 全天 |
| `schedule_start` | Starts | 开始 |
| `schedule_end` | Ends | 结束 |
| `schedule_category_label` | Type | 类型 |
| `schedule_category_personal` | Personal | 个人 |
| `schedule_category_work` | Work | 工作 |
| `schedule_category_study` | Study | 学习 |
| `schedule_category_other` | Other | 其他 |
| `schedule_reminder_label` | Reminder | 提醒 |
| `schedule_reminder_none` | None | 不提醒 |
| `schedule_reminder_on_time` | On time | 准时 |
| `schedule_reminder_minutes_before` | %1$d min before | %1$d分钟前 |
| `schedule_reminder_hour_before` | 1 hour before | 1小时前 |
| `schedule_reminder_day_before` | 1 day before | 1天前 |
| `schedule_reminder_method_label` | Reminder type | 提醒方式 |
| `schedule_reminder_method_notification` | Notification | 通知提醒 |
| `schedule_reminder_method_alarm` | Alarm | 闹钟提醒 |
| `schedule_recurrence_label` | Repeat | 重复 |
| `schedule_recurrence_none` | Does not repeat | 不重复 |
| `schedule_recurrence_daily` | Every day | 每天 |
| `schedule_recurrence_weekdays` | Weekdays | 工作日 |
| `schedule_recurrence_weekly` | Every week | 每周 |
| `schedule_recurrence_monthly` | Every month | 每月 |
| `schedule_recurrence_yearly` | Every year | 每年 |
| `schedule_recurrence_until` | Ends repeat | 结束重复 |
| `schedule_recurrence_never_ends` | Never ends | 永不结束 |
| `schedule_scope_title` | Apply changes to | 编辑范围 |
| `schedule_scope_this_only` | This schedule only | 仅更改此日程 |
| `schedule_scope_this_and_future` | This and future schedules | 更改此日程和将来日程 |
| `schedule_scope_all` | All schedules | 更改所有日程 |
| `schedule_scope_this_only_hint` | Repeating rule changes are ignored for a single schedule. | 仅更改此日程不会修改重复规则。 |
| `schedule_delete` | Delete | 删除 |
| `schedule_delete_confirm_title` | Delete this to-do? | 删除这条待办？ |
| `schedule_save` | Save | 保存 |
| `schedule_cancel` | Cancel | 取消 |
| `schedule_summary_repeat` | Repeat: %1$s | 重复：%1$s |
| `schedule_summary_category` | Type: %1$s | 类型：%1$s |
| `schedule_summary_reminder` | Reminder: %1$s | 提醒：%1$s |
| `schedule_time_range` | %1$s to %2$s | %1$s至%2$s |
| `schedule_day_today` | Today %1$s | 今天 %1$s |
| `schedule_day_tomorrow` | Tomorrow %1$s | 明天 %1$s |
| `schedule_day_yesterday` | Yesterday %1$s | 昨天 %1$s |
| `schedule_notification_message` | Starts at %1$s | %1$s 开始 |
| `channel_schedule_reminder` | Schedule reminders | 日程提醒 |
| `channel_schedule_alarm` | Schedule alarms | 日程闹钟 |

> 周几文案按 §10 现有资源处理：先 `rg "weekday|周日|Mon" res/values*/strings.xml` 确认是否已有可复用的周几字符串；**有则复用，没有才新增** `schedule_weekday_*` 7 条（en: Sun..Sat / zh: 周日..周六）。

---

## 11. 测试清单

`app/src/test/java/com/projectlumen/app/core/schedule/ScheduleRecurrenceExpanderTest.kt`（新增，纯 JVM，CI 的 `testDebugUnitTest` 会跑）：

| 编号 | 用例 |
|---|---|
| T-1 | `DAILY` 锚点 06:30，窗口 3 天 → 恰好 3 个 06:30，且 `end - start` 等于原时长 |
| T-2 | `WEEKDAYS` 锚点落在周六 → 首条产出是下周一 |
| T-3 | `WEEKLY` 锚点 周日 12:00，窗口 4 周 → 每条都是周日 12:00 |
| T-4 | `MONTHLY` 锚点 1/31 → **2 月无产出**，3/31 有产出 |
| T-5 | `MONTHLY` 锚点 1/15 → 2/15、3/15 连续产出 |
| T-6 | `YEARLY` 锚点 2024/2/29 → 2025 无、2028/2/29 有 |
| T-7 | `recurrenceUntil` 设为某日 23:59:59.999 → 该日**含**、次日不含 |
| T-8 | `recurrenceUntil = 0` + 极大窗口 → 产出条数不超过 `limit` |
| T-9 | 窗口 `from` 落在系列中段 → 只返回窗口内的时刻，且首条与锚点**同一墙上时间** |
| T-10 | 跨夏令时地区（`ZoneId.of("America/New_York")`）`DAILY` 保持当地 06:30（UTC 时刻会偏移 1 小时） |

> 测试一律固定 `zoneId` 参数，禁止依赖机器默认时区（CI runner 是 UTC）。

---

## 12. 落地核对清单

实现完成后逐条核对；每条要么 ✅ 已实现（附文件:行号），要么 ⏸ 挂起（附理由）。

> 核对时间 2026-09-13，核对方式：逐文件读落盘代码 + `git diff`，**不采信子代理自述**。
> 行号以核对当次的工作区为准，后续改动可能位移。
>
> **进度：36 / 37 已勾选。** 唯一未勾选的是 C-36（提交推送 + CI 全绿），需推送后由
> `gh api repos/{owner}/{repo}/commits/{sha}/check-runs` 逐 job 判定后回填。
>
> 核对过程中判定失败并已修复的 1 条：**C-22**（早期 `rearm` 只刷新物化窗口、不排闹钟）。

### 12.1 数据层
- [x] C-01 四个枚举文件存在且值与 §2 一致 — `core/enums/{ScheduleRecurrence,ScheduleEditScope,ScheduleReminderMethod,ScheduleCategory}.kt`
- [x] C-02 `ScheduleSeriesEntity` 字段与 §3.1 表逐列一致 — `core/database/entities/ScheduleSeriesEntity.kt`（14 列）
- [x] C-03 `ScheduleOccurrenceEntity` 字段与 §3.2 表逐列一致，含 3 个索引 — `core/database/entities/ScheduleOccurrenceEntity.kt`（19 列，`indices = [seriesId, startAt, deletedAt]`）
- [x] C-04 `ScheduleSeriesDao` 方法齐全（含 `truncate` / `softDelete`） — `core/database/daos/ScheduleSeriesDao.kt:23-27`
- [x] C-05 `ScheduleOccurrencesDao` 方法齐全（含 `getOriginalStartAts` / `pruneGeneratedOutsideWindow` / 三个软删变体） — `core/database/daos/ScheduleOccurrencesDao.kt:28-59`
- [x] C-06 展开引擎 6 种规则 + 4 条统一规则全部实现 — `core/schedule/ScheduleRecurrenceExpander.kt:31-68`（统一规则：`recurrenceUntil` 截断、`toMillis` 截断、`limit` 上限、迭代预算）
- [x] C-07 物化器实现窗口 7 天前 / 60 天后 + 去重 + 清理，且不清理 `detached` / `completed` — `core/schedule/ScheduleMaterializer.kt`（`PAST_WINDOW_DAYS = 7` / `FUTURE_WINDOW_DAYS = 60`；`pruneGeneratedOutsideWindow` 的 SQL 带 `detached = 0 AND completed = 0`）
- [x] C-08 `ScheduleRepository` 公开方法齐全 — `core/repositories/ScheduleRepository.kt`，**实际 10 个**（`observeAll` / `get` / `getSeries` / `create` / `update` / `delete` / `setCompleted` / `upcomingReminders` / `markReminderFired` / `refreshWindow`）。原文写的「7 个」是起草时的估算，以实现为准。
- [x] C-09 三种编辑范围语义与 §6.2 / §6.3 逐条一致 — `core/repositories/ScheduleRepository.kt:68-149`（分发）、`:178-282`（`updateThisOnly` / `updateThisAndFuture` / `updateAll`）
- [x] C-10 展开引擎单测 T-1 ~ T-10 全部落地 — `app/src/test/java/com/projectlumen/app/core/schedule/ScheduleRecurrenceExpanderTest.kt`（10 个测试，各自钉死 `ZoneId`：`shanghai` / `utc` / `newYork`）

### 12.2 接线
- [x] C-11 `AppDatabase` version = 19，实体/DAO 注册齐全 — `core/database/AppDatabase.kt:50`（version）、`:64-65`（两个 DAO 访问器）、`:504`（迁移注册）
- [x] C-12 `MIGRATION_18_19` 的建表 SQL 与实体逐列一致，索引名与 Room 生成名一致 — `core/database/AppDatabase.kt:215-218`（`Migration(18, 19)`）→ `:324`（`createScheduleTables`，2 个 `CREATE TABLE` + 3 个 `CREATE INDEX`，索引名用 Room 的 `index_<表>_<列>` 约定）。**已与实体逐列对照**：Room 的 `TableInfo` 校验只在运行时发生，编译期不会报错，所以这一条必须人肉核对。
- [x] C-13 `ProjectLumenRepositories` 暴露 `schedule` — `app/ProjectLumenRepositories.kt:41-45`
- [x] C-14 `ProjectLumenUiState.scheduleTasks` 存在 — `app/ProjectLumenUiState.kt:42`
- [x] C-15 `ProjectLumenStateStore` 用合法 arity 的 `combine` 接入了 schedule 流（未使用不存在的 6 参重载） — `app/ProjectLumenStateStore.kt:72-75`（内层 2 参 `combine` 产出 `DeviceAndScheduleSnapshot`）、`:100`（`scheduleTasks = deviceAndSchedule.scheduleTasks`）、`:131`。Kotlin 标准库 `combine` **没有** 6 个具名参数的公开重载，所以拆成「内层 2 参 + 外层 5 参」。

### 12.3 提醒
- [x] C-16 两个渠道 id 与重要性/声音/振动与 §7.5 表一致 — `core/services/NotificationChannels.kt`（`schedule_reminder` / `schedule_alarm`）+ `core/services/NotificationService.kt:88-102`（均为 `IMPORTANCE_HIGH` + `enableVibration(true)` + **不调** `setSound(null, null)`，即保留默认提示音）
- [x] C-17 `NotificationIds` 新增 `SCHEDULE_REMINDER_BASE` / `SCHEDULE_REMINDER_RANGE` — `core/constants/NotificationIds.kt:21-22`（`9600` / `300`）
- [x] C-18 `ScheduleReminderScheduler` 方法齐全，id 映射与 §7.2 一致 — `core/services/ScheduleReminderScheduler.kt`（6 个公开方法：`schedule` / `cancel` / `cancelAll` / `notificationIdFor` / `canScheduleExactAlarms` / `canUseFullScreenIntents`；`notificationIdFor` 在 `:37-40`；`schedule` 在 `:23-26` 做 `triggerAt <= now` 拦截）
- [x] C-19 `AlarmReceiver` 有 `ACTION_SCHEDULE_REMINDER` 分支，且**不**走到护眼 `reconcileNow` — `core/services/AlarmReceiver.kt:34-46`（`:45` 的 `return@runCatching` 在 `:50` 的 `reconcileNow` 之前返回）。`pendingResult.finish()` 在 `:102` 的 `runCatching` 之外，所有路径都会走到。
- [x] C-20 `ScheduleReminderDispatcher` 的校验齐全，含 15 分钟过期判定与 `completed` 抑制 — `core/services/ScheduleReminderDispatcher.kt`（顺序：取行 → `deletedAt` → 15 分钟过期（**过期也置 `markReminderFired`**，避免每次恢复都重试）→ `completed` → `reminderMinutesBefore < 0` → `reminderFiredAt != 0` → 发通知 + 置位）
- [x] C-21 先 `refreshWindow()` 再 `rearm()` — `app/ProjectLumenApplication.kt:115-118`（`rescheduleScheduleReminders()` 内部固定顺序），调用点 `core/services/BootReceiver.kt:72-74`，位置在 `:79` 的 `reconcileNow` **之前**且在 `:75` 的 `settings == null` 早退**之前**
- [x] C-22 日程变更后触发重排（位置与文档一致：FeatureEntry 侧） — `app/ProjectLumenScheduleFeatureEntry.kt:112`（`setCompleted`）、`:127`（`persist`）、`:138`（`delete`）三处调用 `rearm()`；执行体由 `app/ProjectLumenViewModel.kt:73,164`（构造参数 `rescheduleScheduleReminders` → `rearm`）与 `MainActivity.kt:139`（`app::rescheduleScheduleReminders`）注入。**这一条在核对中曾判定失败并已修复**：早期实现只做 `refreshWindow()` 不排闹钟，新建的提醒要等下次开机才生效；现已统一到 §7.6 的单一入口。
- [x] C-23 日程提醒不受 `settings.notificationEnabled` 与 `QuietHours` 抑制（按 §7.3 决策） — `core/services/AlarmReceiver.kt:31-33`（分支说明）+ `:45` 提前返回（不经过 `:51-52` 的 `QuietHours` 判定与 `:54` 的 `notificationEnabled` 判定）+ `ScheduleReminderDispatcher.kt` 类注释
- [x] C-37 `ExactAlarmPermissionReceiver` 在 runtime 判空之前补了 `refreshWindow()` + `rearm()`，且独立 `runCatching` 隔离（§7.8） — `core/services/ExactAlarmPermissionReceiver.kt:26-30`，位于 `:31` 的 `app.runtimeRepository().get() ?: return@runCatching` **之前**，独立于外层 `runCatching`

### 12.4 UI
- [x] C-24 `Destination.SCHEDULE` 存在，`showInBottomNav = false` — `app/ProjectLumenApp.kt:123`
- [x] C-25 NavHost 注册带 `scheduleId` 参数的路由，新建与编辑两条入口都可进 — `app/ProjectLumenApp.kt:496`（编辑：`?scheduleId=$scheduleId`）、`:499`（新建：不带参数）、`:505`（路由 `?scheduleId={scheduleId}` + `navArgument` 默认 `0L`，`0L` 走 `openNew()`）
- [x] C-26 详情页 17 项字段与 §9.4 表逐行对应 — `app/ProjectLumenScheduleScreens.kt:134-398`。逐行核对结果：摘要行 5 段在 `ScheduleSummaryText`（`:417-438`，`" · "` 连接）、全天开关 `:149`、开始/结束 `:173-188`、类型 4 项 `:194-200`、提醒 8 项 `:207-216`、提醒方式 2 项 `:219-228`、重复 6 项 `:235-250`、结束重复 `:252-277`、保存/删除 `:281-300`、编辑范围弹层 `:365-398`
- [x] C-27 首页卡片挂在 `GoalProgressCard` 之后 — `app/ProjectLumenMainScreens.kt:131`
- [x] C-28 首页过滤口径与 §8.4 一致（今天+未来 7 天；今天的已完成保留） — `app/ProjectLumenHomeScheduleCard.kt:137-154`（`startAt < 今天+8天 00:00` ∧ (`!completed` ∨ `startAt >= 今天 00:00`)，按 `startAt` 升序，`take(8)`）
- [x] C-29 勾选完成调用 `setScheduleCompleted` 且持久化 — 勾选框 `app/ProjectLumenHomeScheduleCard.kt:91-94` → `ProjectLumenMainScreens.kt:133` → `viewModel.setScheduleCompleted`（`ProjectLumenViewModel.kt:484`）→ `ProjectLumenScheduleFeatureEntry.kt:108-115` → `ScheduleRepository.setCompleted` → `ScheduleOccurrencesDao.setCompleted`（写库并置 `completedAt`）
- [x] C-30 `THIS_ONLY` 下重复改动被丢弃，且弹层带提示文案 — 丢弃在 `ProjectLumenScheduleFeatureEntry.kt:117-132`（`persist` 把 `scope` 透传给 `repository.update`，由 `ScheduleRepository.updateThisOnly` 忽略 `draft.recurrence` / `draft.recurrenceUntil`）；提示文案 `ProjectLumenScheduleScreens.kt:383-389` 渲染 `R.string.schedule_scope_this_only_hint`。**控件未做 `enabled = false`**：文档 §9.4 给的是「改动被丢弃 + 弹层提示」这条路径，已满足。
- [x] C-31 未设置提醒时「提醒方式」不可选 — `app/ProjectLumenScheduleScreens.kt:223`（`enabled = draft.reminderMinutesBefore != SCHEDULE_REMINDER_NONE`）
- [x] C-32 所有字符串在 `values/` 与 `values-zh/` 两侧都存在且名称一致 — `schedule_` 前缀键 **两侧各 57 条**，`diff` 为空（`grep -o 'name="schedule_[a-z_]*"' | sort` 比对）。仓库只有 `values-zh` 一个 locale 目录，无 `MissingTranslation` 风险。

### 12.5 纪律
- [x] C-33 未新增「超级文件」（单文件过大或聚合职责）——详情页与首页卡片分文件 — 新增三个文件：`ProjectLumenScheduleScreens.kt` 654 行、`ProjectLumenScheduleFeatureEntry.kt` 191 行、`ProjectLumenHomeScheduleCard.kt` 154 行；仓库既有最大文件为 1276 行（`ProjectLumenEyeCareInsights.kt`），本次未触碰
- [x] C-34 未新增前台服务（避免触碰 `ForegroundServiceArchitectureTest`） — `AndroidManifest.xml` 未新增 `<service>`；`AlarmReceiver` / `BootReceiver` / `ExactAlarmPermissionReceiver` 三个 `<receiver>` 均**复用既有条目**（日程分支挂在已有的 `AlarmReceiver` 上，靠 explicit Intent 定向，无需新增 intent-filter）
- [x] C-35 未运行任何本地构建/测试命令 — 本次核对只做文件读取与 `grep`/`wc`，编译与测试全部交由 GitHub Actions
- [ ] C-36 提交已推送且 CI（check-runs 逐 job）全绿 — ⏳ **核对时尚未提交**，待推送后回来勾选

---

## 13. 已知取舍汇总（供复核时判断是否接受）

| 编号 | 取舍 | 理由 |
|---|---|---|
| K-1 | 首页列表在 UI 侧过滤，而非 DAO 分窗查询 | `nowMillis` 每秒变化，无法作为稳定的 Flow 查询参数；物化窗口已限制数据量 |
| K-2 | 开机只重排前 12 条提醒 | AlarmManager 精确闹钟有系统级数量上限与 Doze 降级 |
| K-3 | `MONTHLY` 遇到不存在的日号**跳过**该月 | 与 Google Calendar `BYMONTHDAY` 一致；clamp 会导致日期永久漂移 |
| K-4 | 「仅更改此日程」不采纳重复规则改动 | 一次实例不属于系列规则层；UI 侧禁用并提示 |
| K-5 | 超过 15 分钟的过期提醒不补响 | 开机后补跑不应弹一堆陈年提醒 |
| K-6 | 日程提醒不受 App 内护眼静音/开关抑制 | 日历提醒与护眼静音的语义不同，用户自建的提醒不该被护眼设置吞掉 |
| K-7 | `create()` 返回值 UI 不使用 | 锚点早于物化窗口时的返回值语义不稳定，避免变成隐性契约 |

---

## 14. 参考：仓库既有约定（实现时必须遵守）

- **禁本地构建/测试**：`CLAUDE.md` 与 `AGENTS.md` 明确，一切由 GitHub Actions 执行（`gradle assembleRelease` / `testDebugUnitTest` / `lintDebug`）。
- **软删除**：与 `tip_templates` / `reminder_plans` 一致，用 `deletedAt != 0` 表示删除，查询一律带 `deletedAt = 0`。
- **状态单向流动**：DAO/仓储 → `ProjectLumenStateStore.combine` → `ProjectLumenUiState` → `ProjectLumenViewModel`。Compose 不直接读 DAO。
- **手动依赖注入**：无 Hilt/Dagger；服务在 `ProjectLumenApplication` 里 `by lazy` 构造，经构造函数传给 ViewModel。
- **枚举存字符串**：`.name` 入库，读取时 `entries.firstOrNull { ... }` 兜底。
- **提交规范**：Conventional Commits（`feat(schedule): ...`），message 说明「为什么」。
