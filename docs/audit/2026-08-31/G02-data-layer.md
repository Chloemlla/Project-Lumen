# G02 数据持久层（Room / Repository / DataStore / MMKV）审查报告

- 审查文件数：34（AppDatabase 1 + DAO 10 + Entity 10 + Repository 10 + preferences 2 + mmkv 1），总行数：2149
- 另核查：`app/schemas/` **不存在**（`room.schemaLocation = "$projectDir/schemas"` 见 `app/build.gradle.kts:301`，`exportSchema = true`，但仓库里 0 个 schema JSON，`.gitignore` 也没写它——即导出物从未提交）
- 结论摘要：这一层的**结构分层是清晰的**（DAO → Repository → StateStore 单向流，无 UI 直读 DAO），但**并发正确性几乎完全缺失**：全组 10 个 repository 里只有 3 把 `Mutex`，其中 2 把只保护"一次性 MMKV 迁移"，1 把保护设备洞察刷新——**没有任何一把锁保护 read-modify-write**。统计累加（`sum += delta`）、运行时状态整体覆盖写、设置整体覆盖写这三条最热的写路径全部裸奔，且 `ProximityDetectionService` / `LightMonitorService` 仍在直连 DAO 旁路 `StatisticsRepository`。值得注意的是：修这些问题的提交 `3e4eb3b`（"fix: serialize runtime/stats read-modify-write cycles"）**只存在于 `origin/ui` 分支，从未合进 `main`**（`git merge-base --is-ancestor 3e4eb3b HEAD` → NO），所以这不是"待发现的隐患"，而是**已被诊断过但丢在分支上的回归**。次严重的是真相源问题：`AppSettingsEntity` 的 46 个字段同时住在 Room 和 MMKV，`runtime_state` 表在 MMKV 迁移后彻底不再写入，而写入顺序恰好是被本项目约定禁止的"Room 先、MMKV 后"。

## 缺陷清单

### [G02-01] `StatisticsRepository` 的统计累加无锁，且两个前台服务仍直连 DAO 旁路它
- 严重度：P0
- 类别：B 并发 / F 持久化
- 位置：
  - `app/src/main/java/com/projectlumen/app/core/repositories/StatisticsRepository.kt:48-57`（`updateEyeStats`）、`59-68`（`updatePomodoroStats`）、`21-33`、`35-46`
  - 旁路 1：`app/src/main/java/com/projectlumen/app/core/proximity/ProximityDetectionService.kt:302-312`（`incrementEyeStats`，被 `182`、`190` 调用）
  - 旁路 2：`app/src/main/java/com/projectlumen/app/core/light/LightMonitorService.kt:183-189`（`incrementLowLightStats`，被 `127` 调用）
- 现状：
  ```kotlin
  // StatisticsRepository.kt:48-57 —— 没有任何锁
  suspend fun updateEyeStats(statsEnabled: Boolean, nowMillis: Long, transform: ...) {
      if (!statsEnabled) return
      val date = todayKey(nowMillis)
      val current = eyeStatsDao.get(date) ?: DailyEyeStatsEntity(statDate = date)
      eyeStatsDao.upsert(transform(current).copy(updatedAt = nowMillis))
  }
  ```
  `daily_eye_stats` 今日这一行的并发写者共 **8 处**：`TimerForegroundService.kt:181/199/213/243`、`AlarmReceiver.kt:120`、`ReminderActionReceiver.kt:70`、`LumenOpenRuntimeController.kt:125`、`ProjectLumenRuntimeFeatureEntry.kt:217`，外加上面两个直连 DAO 的旁路。这些写者分别跑在前台服务协程、`BroadcastReceiver` 的 `goAsync` 协程、相机分析回调协程、光线传感器回调协程和 ViewModel scope 上——彼此完全独立，没有任何串行化点。
- 触发场景：计时服务每个 tick 累加 `workingSeconds` 的同时，相机分析回调累加 `proximityWarningCount`（或光线服务累加 `lowLightWarningCount`）。两者都读到同一行旧值、各自 `copy` 后整行 `upsert` → 后写者把先写者的字段整体回滚。用户开启"距离监测 + 环境光监测 + 计时"三件套时（本 App 的主打组合），这是每分钟都会发生的窗口。
- 影响：统计页的用眼时长、休息次数、距离告警数、干眼告警数持续偏小且不可复现地跳变；`maxContinuousWorkSeconds` 会被回退；基于统计的每日目标达成判定随之出错。数据一旦丢就无法找回。
- 修复方案：
  1. 在 `StatisticsRepository` 加 `private val statsMutex = Mutex()`，把 `updateEyeStats` 与 `updatePomodoroStats` 的 `get → transform → upsert` 整段包进 `statsMutex.withLock { }`。注意 `applyEyeDelta` / `applyPomodoroDelta` 已经委托给这两个函数，无需另加锁（否则不可重入的 `Mutex` 会自死锁）。
  2. 删掉 `ProximityDetectionService.incrementEyeStats` 与 `LightMonitorService.incrementLowLightStats` 里的 `app.database.dailyEyeStatsDao()`，改为 `app.statisticsRepository()`（或按现有写法构造 `StatisticsRepository`）调用 `updateEyeStats(...)`，让全部写者过同一把锁。
  3. `StatisticsRepository` 目前在 6 个地方被 `new` 出来（`TimerForegroundService.kt:73`、`AlarmReceiver.kt:104`、`ReminderActionReceiver.kt:31`、`LumenOpenRuntimeController.kt:28`、`ProjectLumenRepositories.kt:30`、隐含的 telemetry 路径）；实例级 `Mutex` 对多实例无效，必须把锁放在**伴生 object**里（例如 `private object EyeStatsWriteLock { val mutex = Mutex() }`），或把 `StatisticsRepository` 收敛成从 `ProjectLumenApplication` 取的单例。这一步是本条修复能否真正生效的关键。
  4. 参考已有实现：`git show 3e4eb3b -- .../StatisticsRepository.kt`（在 `origin/ui` 分支），它已经做了 1 和 2，但用的是实例级锁，需要按第 3 点补强。
- 风险/注意：`Mutex` 不可重入。若未来在 `transform` lambda 里再调用仓库的其他 `suspend` 加锁方法会死锁，需在函数注释里约束 `transform` 必须是纯函数。另外把两个服务改走仓库后，它们会多一次 `settingsRepository().get()`（仓库内部已判 `statsEnabled`），可以顺手去掉服务里重复的 `statsEnabled` 判断。

### [G02-02] `RuntimeRepository` 是"整体覆盖写 + 无锁"，34 个 `get → copy → upsert` 调用点互相回滚字段
- 严重度：P0
- 类别：B 并发 / F 持久化
- 位置：`app/src/main/java/com/projectlumen/app/core/repositories/RuntimeRepository.kt:28-30`（`RuntimeRepository.upsert`）、`65-69`（`RuntimeStateMmkvStore.upsert`）、`99-104`（`writeToMmkv`）
- 现状：
  ```kotlin
  // RuntimeRepository.kt:65-69 —— 只有 ensureMigrated 用到 migrationLock，写路径完全无锁
  suspend fun upsert(dao: RuntimeStateDao, runtime: RuntimeStateEntity): RuntimeStateEntity {
      ensureMigrated(dao)
      writeToMmkv(runtime)   // 整个 44 字段实体序列化后整体覆盖
      return runtime
  }
  ```
  仓库只暴露"整体覆盖"语义，没有 `update(transform)`。因此全仓库 **34 处**调用都写成 `runtimeRepository.get()?.let { upsert(it.copy(某字段 = 新值)) }`。写者分布在 12 个文件：`TimerForegroundService`、`AlarmReceiver`、`ReminderActionReceiver`、`ProximityDetectionService`、`LightMonitorService`、`DeveloperDebugOverlayService`、`AppLifecycleCoordinator`、`TimerReconciliationWorker`、`ShizukuResilienceWorker`、`BootReceiver`、`LumenOpenRuntimeController`、`ProjectLumenRuntimeFeatureEntry`。
- 触发场景：相机分析回调写 `proximityLastFaceAt` / `proximityTooClose` 的同时，计时服务或 `AlarmReceiver` 推进 `nextReminderAt` / `reminderPhase`。相机侧读到的是推进**之前**的快照，`copy` 只改自己那两个字段，`upsert` 时把 `nextReminderAt`、`reminderPhase`、`breakEndAt` 一起写回旧值。光线服务写 `ambientLastLux`、调试悬浮窗写 `sensorPitchDegrees` 也是同一模式，采样频率是秒级。
- 影响：计时状态被回滚到过去 → `TimerReconciliationWorker` 拿着陈旧的 `nextReminderAt` 与 AlarmManager 对账，会重复排闹钟或把已到期的提醒判成未到期；用户表现为"到点不提醒"、"休息界面重复弹出"、"暂停后自己恢复"。这是端到端功能失效。
- 修复方案：在 `private object RuntimeStateMmkvStore` 内加 `private val writeMutex = Mutex()`（**必须放在 object 里**——`RuntimeRepository` 本身在 `ProjectLumenApplication.kt:368`、`AlarmReceiver.kt`、`EyeCareTelemetryReporter.kt:132`、`DataBackupService` 各处被重复构造，实例级锁无效；而 `RuntimeStateMmkvStore` 是顶层 object，天然共享）。然后在 `RuntimeRepository` 新增
  ```kotlin
  suspend fun update(transform: (RuntimeStateEntity) -> RuntimeStateEntity): RuntimeStateEntity
  ```
  内部 `writeMutex.withLock { writeToMmkv(transform(readFromMmkv() ?: RuntimeStateEntity())) }`，并把 34 个 `get()?.let { upsert(it.copy(...)) }` 调用点逐个改成 `update { it.copy(...) }`。保留 `upsert` 供 `reset` / 备份恢复这类"确实要整体覆盖"的场景，但也要进同一把锁。
- 风险/注意：改 34 个调用点属于跨组改动（G01 / 服务组的文件），修复阶段需要与那些组协调；建议先落地 `update()` API + 锁，再分批迁移调用点。加锁顺序上要保证 `update` 的 lambda 里不再调用其他加锁仓库方法（当前调用点都只是纯 `copy`，安全）。另注意 `migrationLock` 与新 `writeMutex` 是两把锁：`upsert` 里 `ensureMigrated`（拿 `migrationLock`）应放在 `writeMutex.withLock` **之外**，否则要固定"先 writeMutex 再 migrationLock"的顺序并全仓库一致。

### [G02-03] MMKV 迁移后 Room `runtime_state` 表再也不写入，MMKV 成为无回退的唯一真相源；MMKV 内容丢失会从数月前的 Room 快照"复活"运行时状态
- 严重度：P1
- 类别：A 架构 / F 持久化 / E 韧性
- 位置：`app/src/main/java/com/projectlumen/app/core/repositories/RuntimeRepository.kt:65-69`（`dao` 参数在写路径完全未使用）、`71-89`（`ensureMigrated`）、`91-97`（`readFromMmkv` 解析失败静默返回 null）
- 现状：`RuntimeStateMmkvStore.upsert(dao, runtime)` 拿到 `dao` 只为调 `ensureMigrated`，真正的写只有 `writeToMmkv`。也就是说迁移完成那一刻起，`runtime_state` 表的内容被永久冻结。同时"迁移已完成"标记 `__mmkv_migration_complete` 与状态本体 `state_json` **存在同一个 MMKV 文件**里：
  ```kotlin
  if (!mmkv.containsKey(KEY_STATE_JSON)) {
      dao.get()?.let(::writeToMmkv)          // 从 Room 快照恢复
  }
  mmkv.encode(KEY_MMKV_MIGRATION_COMPLETE, true)
  ```
- 触发场景：MMKV 因 CRC 校验失败自我截断（这是 MMKV 遇到写入中断/存储异常时的正常恢复行为），两个 key 一起丢 → 下次启动 `ensureMigrated` 判定"未迁移" → `dao.get()` 取出**迁移当天**那份 Room 快照并写回 MMKV。若当时状态是 `reminderPhase = BREAK`、`breakEndAt` 为数月前的时间戳，App 会以一个早已过期的休息态启动。另一条路径：`readFromMmkv()` 在 JSON 解析失败时 `getOrNull()` 静默返回 null，`getOrDefault()` 于是给出全新默认值，运行时状态无声清零且无任何日志。
- 影响：用户可见的"计时器状态莫名回到很久以前 / 卡在休息界面 / 计时被清零"，且因为没有日志，线上无法定位。另外 `MIGRATION_9_10`、`MIGRATION_6_7` 等仍在给 `runtime_state` 加列（`AppDatabase.kt:219-249`），这些迁移是纯粹的无用功，会误导后续维护者以为 Room 仍是真相源。
- 修复方案：三选一，建议第 2 种。
  1. 回到"Room 为真相源、MMKV 只做加速缓存"：`writeToMmkv` 之后同步 `dao.upsert(runtime)`，且**顺序必须是 MMKV 先、Room 后**（项目约定），读路径 MMKV 未命中时回落 `dao.get()`。
  2. 明确 MMKV 为真相源并去掉误导：把 `__mmkv_migration_complete` 标记搬到独立存储（`SharedPreferences` 或另一个 MMKV id），这样 MMKV 状态文件被截断时不会触发"从陈旧 Room 复活"；同时把 `readFromMmkv()` 的解析失败分支改为记录 breadcrumb（`CrashBreadcrumbs.record`）而非静默 null；并在 `AppDatabase` 里给 `runtime_state` 加注释说明它已是历史遗留，后续迁移不再为它加列。
  3. 彻底删除 `runtime_state` 表与 `RuntimeStateDao`（需要一次 `MIGRATION_18_19` DROP TABLE），代价是丧失迁移回滚能力，不建议。
- 风险/注意：方案 1 会让每次运行时状态写入多一次 SQLite 写（秒级频率），需配合 G02-02 的锁一起做，否则更糟。方案 2 修改 `KEY_MMKV_MIGRATION_COMPLETE` 的存放位置意味着**已升级用户会被判为"未迁移"并再跑一次迁移**——由于 `if (!mmkv.containsKey(KEY_STATE_JSON))` 的守卫，已有 MMKV 状态的用户不会被 Room 快照覆盖，是安全的；但要保留这个守卫，不能顺手删掉。

### [G02-04] `AppSettingsEntity` 的 46 个字段同时住在 Room 与 MMKV，写入顺序违反"MMKV 先于 Room"约定，且无版本戳无法判新旧
- 严重度：P1
- 类别：A 架构 / F 持久化
- 位置：`app/src/main/java/com/projectlumen/app/core/repositories/SettingsRepository.kt:68-77`（`update`）、`38-66`（`ensureDefault`）、`16-27`（读路径）；`app/src/main/java/com/projectlumen/app/core/preferences/EyeCarePreferencesDataStore.kt:101-104`（`saveFromSettings`）、`395-444`（`withEyeCarePreferences`）
- 现状：
  ```kotlin
  // SettingsRepository.kt:68-77
  val current = getOrDefault()
  val updated = transform(current).copy(id = 1, updatedAt = nowMillis)
  dao.upsert(updated)                       // ① 先写 Room
  preferences?.saveFromSettings(updated)    // ② 后写 MMKV
  ```
  读路径 `observe()` / `get()` 一律用 `settings.withEyeCarePreferences(prefs)`，即 **MMKV 无条件覆盖 Room**（`EyeCarePreferencesDataStore.kt:396`，只要 `hasPersistedValues` 为真）。两边都没有版本号或时间戳可比较：`AppSettingsEntity.updatedAt` 只存在于 Room 侧，`EyeCarePreferences` 里没有对应字段。
- 触发场景：用户改一个护眼设置（如把提醒间隔从 20 改成 45），`dao.upsert` 已落盘、`saveFromSettings` 尚未执行时进程被杀（前台服务被系统回收、用户上划清后台、OOM）。重启后读路径拿 MMKV 里的旧值 20 覆盖 Room 的 45 → 用户的修改永久消失；更糟的是下一次 `update()` 会以"20"为基线再写回 Room，把 Room 也纠正回旧值。这正是本项目约定要防的"陈旧 MMKV 永久覆盖新 Room"。
- 影响：设置项静默回滚，用户重复修改同一开关；由于 Room 侧的值会被"纠正"，事后从数据库也看不出用户曾改过。
- 修复方案：
  1. 把 `SettingsRepository.update` 的两次写入顺序**调换**为先 `preferences?.saveFromSettings(updated)` 再 `dao.upsert(updated)`。这样崩溃窗口内的结果是"MMKV 新、Room 旧"，而读路径本来就是 MMKV 优先，用户的修改不丢。`ensureDefault`（`:59-65`）里 `dao.upsert(baseSettings.withEyeCarePreferences(...))` 的分支同理需要检查顺序。
  2. 同样的顺序问题也存在于 `DataBackupService.kt:283-284`（先 `appSettingsDao().upsert` 再 `eyeCarePreferences?.saveFromSettings`），需一并调换——该文件属于服务组，修复阶段请交叉通知。
  3. 中期建议：给 `EyeCarePreferences` 加 `updatedAt: Long` 并在 `writeToMmkv` 写入，读路径改为"取 `updatedAt` 更大的一侧"，从"无条件 MMKV 优先"升级为"最后写入者优先"，这样任何一侧的单边写入都不会造成静默回滚。
- 风险/注意：调换顺序后，如果在 `saveFromSettings` 与 `dao.upsert` 之间崩溃，Room 会短暂落后于 MMKV——但因为读路径 MMKV 优先且 `update()` 每次都会重写 Room，这个不一致会在下一次写入时自愈，比现状安全。第 3 条会改动 `EyeCarePreferences` 的构造签名，`toEyeCarePreferences`（两个重载）与 `readFromMmkv` 都要同步加字段。

### [G02-05] `SettingsRepository.update` / `DailyGoalsRepository.update` 的 `get → transform → upsert` 无锁
- 严重度：P1
- 类别：B 并发
- 位置：`app/src/main/java/com/projectlumen/app/core/repositories/SettingsRepository.kt:68-77`；`app/src/main/java/com/projectlumen/app/core/repositories/DailyGoalsRepository.kt:16-23`
- 现状：两个 `update` 都是"读整行 → 应用 transform → 整行覆盖写"，没有 `Mutex`。`AppSettingsEntity` 有 100+ 字段，覆盖写的杀伤面是整张设置表。
- 触发场景：`ProximityDetectionService.kt:156` 在人脸标定成功后调用 `settingsRepository.update { it.copy(proximityBaselineFaceWidthPercent = ...) }`，这发生在相机分析协程里；同一时刻用户在设置页拨开关（`ProjectLumenSettingsFeatureEntry.kt:78/88/98/105/112/136/147/154/166/172/180` 共 11 个入口）。两者读到同一份快照，各改各的字段，后写者整行覆盖 → 先写者的改动消失。用户开启距离监测后立刻去调其他开关，就是标准触发路径。
- 影响：要么用户刚拨的开关自己弹回去，要么刚标定好的距离基线被清掉（基线丢失后距离判定退化为 `sample.faceWidthPercent` 绝对值，见 `ProximityDetectionService.distanceRatioPercent` 的 `else` 分支，告警阈值变得毫无意义）。
- 修复方案：在 `SettingsRepository` 加 `private val updateMutex = Mutex()`，把 `update` 整个函数体（含 `getOrDefault()`）包进去；`DailyGoalsRepository.update` 同样加一把。**关键**：`SettingsRepository` 在 `ProjectLumenRepositories.kt:24`、`EyeCareTelemetryReporter.kt:77`、`DataBackupService.kt:122`、`ProjectLumenApplication.kt:357` 各处独立构造，实例级 `Mutex` 起不到作用——必须把锁提到文件内的 `private object AppSettingsWriteLock { val mutex = Mutex() }`，或让所有使用方统一取 `ProjectLumenApplication.settingsRepository()` 这一个实例。参考 `origin/ui` 分支 `3e4eb3b` 已加的 `updateMutex`（它用的是实例级锁，需按此加强）。
- 风险/注意：`update` 内部会调用 `preferences?.saveFromSettings`，后者内部无锁，包进同一把锁后写 MMKV 也被串行化，这是期望行为。注意别把 `observe()`（冷流，被 `stateIn` 长期收集）也包进锁里，会造成锁被永久持有。

### [G02-06] `FeatureFlagRepository.upsert` 无锁的列表读改写 + Room 先于 MMKV
- 严重度：P1
- 类别：B 并发 / F 持久化
- 位置：`app/src/main/java/com/projectlumen/app/core/repositories/FeatureFlagRepository.kt:55-65`
- 现状：
  ```kotlin
  dao.upsert(normalized)                    // ① Room 先
  writeToMmkv(
      readFromMmkv()                        // ② 读整个 flag 列表
          .filterNot { it.key == normalized.key }
          .plus(normalized),                // ③ 整个列表覆盖写 MMKV
  )
  ```
  `migrationLock` 只保护 `ensureMigrated`，这段读改写完全裸奔；而"整个列表覆盖写"意味着并发写不同 key 时会互相吞掉。
- 触发场景：`ProjectLumenRemoteFeatureEntry.kt:343` 在远端权益同步后写 `remote_entitlements_synced`，`PrivilegedDeviceControlCoordinator.kt:464/482` 在 Shizuku 策略刷新后写另外两个 flag。这两条路径都由后台协程驱动、可同时发生 → 其中一个 flag 的写入丢失（Room 里有、MMKV 里没有，而读路径只看 MMKV）。此外 Room 先写、MMKV 后写，进程在两者之间死亡同样导致 flag 静默回滚。
- 影响：功能开关状态与实际不符——Room 记着"已开"、`isEnabled()` 却返回 false。由于 flag 用来门禁远端能力与提权设备控制，表现为功能时开时关，且重启无法恢复（MMKV 是权威）。
- 修复方案：把 `migrationLock` 之外新增 `private val upsertLock = Mutex()`（`FeatureFlagMmkvStore` 已是顶层 object，实例共享没问题），`upsert` 里把 ①②③ 整段包进 `upsertLock.withLock { }`，并**把 MMKV 写提到 Room 写之前**：先 `writeToMmkv(合并后的列表)`，再 `dao.upsert(normalized)`。加锁顺序固定为"先 `upsertLock` 后 `migrationLock`"（即 `ensureMigrated` 调用留在 `upsertLock.withLock` 之外，保持现有位置即可），全仓库一致，不会与 G02-02 的 `writeMutex` 互等（两者是不同 object 的独立锁，无嵌套）。
- 风险/注意：`FeatureFlagRepository` 在 `ProjectLumenRepositories.kt:37`、`PrivilegedDeviceControlCoordinator.kt:53`、`DataBackupService.kt:126` 分别构造，但锁在 `object FeatureFlagMmkvStore` 内，天然共享，无需改造构造方式——这一点与 G02-01/G02-05 不同，别照抄那两条的"提升到 object"步骤。

### [G02-07] `entitlements` 表没有唯一索引，`@Upsert` 退化为"每次同步都插入新行"，无界增长
- 严重度：P1
- 类别：A 架构 / F 持久化
- 位置：`app/src/main/java/com/projectlumen/app/core/database/entities/EntitlementEntity.kt:8`（`@PrimaryKey(autoGenerate = true) val id: Long = 0L`，全实体无 `indices`）、`app/src/main/java/com/projectlumen/app/core/database/daos/EntitlementsDao.kt:17-18`、`app/src/main/java/com/projectlumen/app/core/repositories/EntitlementRepository.kt:18-20`
- 现状：调用方从不带 `id` 构造实体：
  ```kotlin
  // ProjectLumenRemoteFeatureEntry.kt:327-341
  entitlements.forEach { remote ->
      entitlementRepository.upsert(
          EntitlementEntity(source = remote.source, productId = remote.productId,
                            purchaseToken = remote.purchaseToken, ...),   // id 走默认值 0L
      )
  }
  ```
  Room 的 `@Upsert` 是"先 INSERT，撞唯一约束才 UPDATE"。`id = 0L` 且 `autoGenerate = true` 时 SQLite 每次都分配新 rowid，**永远不会撞约束**；而 `(source, productId, purchaseToken)` 上没有唯一索引，所以每次同步都是纯插入。`ProjectLumenEntitlementFeatureEntry.kt:18-26` 的手动 Pro 记录、`DataBackupService.kt:361` 的备份导入是同一模式。
- 触发场景：用户每点一次"同步云端权益"（`ProjectLumenRemoteFeatureEntry.kt:140`）或"验证 Google 购买"（`:234`），同一份权益就多一行。反复点 20 次就有 20 条完全相同的记录。备份→恢复循环会把已有行数再翻一倍。
- 影响：`entitlements` 表无界增长；`EntitlementsDao.observeAll()` 无 `LIMIT`，整表进 `ProjectLumenStateStore`（`ProjectLumenStateStore.kt:72`）与 UI；`ProjectLumenRemoteFeatureEntry.kt:236` 用 `entitlementRepository.getAll().size` 当"权益数量"展示给用户，会显示成荒谬的数字；`DataBackupService` 导出的 JSON 随之膨胀。
- 修复方案：给实体加唯一索引并让 `@Upsert` 真正生效——
  ```kotlin
  @Entity(
      tableName = "entitlements",
      indices = [Index(value = ["source", "productId", "purchaseToken"], unique = true)],
  )
  ```
  同时新增 `MIGRATION_18_19`：先按 `(source, productId, purchaseToken)` 分组去重（保留 `id` 最大的一行，`DELETE FROM entitlements WHERE id NOT IN (SELECT MAX(id) FROM entitlements GROUP BY source, productId, purchaseToken)`），再 `CREATE UNIQUE INDEX index_entitlements_source_productId_purchaseToken ON entitlements(source, productId, purchaseToken)`，并把 `AppDatabase.version` 提到 19、加进 `addMigrations`。索引名必须与 Room 生成的一致（`index_<表名>_<列名以_连接>`），否则 Room 打开库时会因 schema 校验失败抛 `IllegalStateException`。
- 风险/注意：去重 SQL 会删掉真实存在的历史行，属于不可逆的数据操作，务必在迁移里只删重复项（上面的 `MAX(id)` 写法保留最新一条）。`purchaseToken` 默认值是 `""`，手动授权（`source = "manual_license"`）的多条记录若 `productId` 相同会被合并成一条——这符合语义，但要确认产品侧不依赖"手动授权次数"。另外加了唯一索引后 `@Upsert` 会走 UPDATE 分支，`id` 会保持原值，依赖 `id DESC` 排序（`EntitlementsDao.kt:11/14`）的展示顺序会变成"按首次插入顺序"而非"按最近同步顺序"，`purchasedAt DESC` 是主排序键所以影响很小。

### [G02-08] `exportSchema = true` 但 `app/schemas/` 从未提交，18 个版本的迁移无任何可对账的基线
- 严重度：P1
- 类别：F 持久化 / H 编译结构
- 位置：`app/src/main/java/com/projectlumen/app/core/database/AppDatabase.kt:44-45`（`version = 18, exportSchema = true`）；`app/build.gradle.kts:301`（`arg("room.schemaLocation", "$projectDir/schemas")`）；`.gitignore` 未包含 `schemas`，但目录实际不存在
- 现状：`fd -H -t d schemas .` 在整个仓库返回空，`fd -H -e json . | rg -i schema` 同样为空。也就是说 kapt 每次在 CI 里生成 schema JSON 后随构建产物一起丢弃，仓库里没有任何版本的基线。
- 触发场景：这不是"运行时会崩"，而是"迁移正确性无法被验证"。具体后果有二：
  1. 只要某个 `Migration` 与实体定义有一列不匹配（类型、`NOT NULL`、主键、索引），Room 在**已升级用户**的设备上打开数据库时抛 `IllegalStateException: Migration didn't properly handle ...` → 应用启动即崩溃，而 CI 里的全新安装完全测不出来（全新安装走 Room 自己生成的 `CREATE TABLE`，永远匹配）。本次审查只能靠人工逐列比对 `MIGRATION_17_18` 的 `CREATE TABLE app_network_controls`（`AppDatabase.kt:289-308`）与 `AppNetworkControlEntity`，确认当前是一致的；但这种人工比对无法保证下一次改动。
  2. 无法使用 `MigrationTestHelper` 写迁移测试（它需要读 `app/schemas/<version>.json`），所以这 17 个迁移一个测试都没有。
- 影响：升级用户的启动崩溃风险，且发布前无从发现。这是本层唯一会导致"100% 启动失败"的类别。
- 修复方案：
  1. 把 `app/schemas/` 纳入版本控制。当前 CI 已经会生成它们（`kapt` 参数已配好），需要在 `build.yml` 的 Android 作业后加一步把 `app/schemas/com.projectlumen.app.core.database.AppDatabase/*.json` 作为 artifact 上传，本地把生成的 18.json（以及能重建的历史版本）提交进仓库。**注意本仓库禁止本地构建**，所以实际操作是：先在 CI 里跑一次并下载 artifact，再把文件提交。
  2. 从下一个版本起，规矩改成"改实体 → 加 Migration → 提交新的 `<version>.json`"，`.gitignore` 里显式不要屏蔽 `app/schemas`。
  3. 有了 schema 后补 `MigrationTestHelper` 测试（`androidTest`，走 CI 的 instrumentation 或至少 Robolectric），至少覆盖 `17 → 18` 与"从 1 一路迁到 18"。
- 风险/注意：只提交 18.json 的价值有限（`MigrationTestHelper` 需要起始版本的 schema）。若历史版本 JSON 已无法重建，退而求其次的做法是在 CI 加一个"schema 漂移检测"步骤：构建后 `git diff --exit-code app/schemas`，一旦实体改了而 schema 没提交就让 CI 失败。这一步不需要历史版本即可生效，建议优先做。

### [G02-09] MMKV 初始化失败被 Application 吞掉，此后设置与运行时状态的每一次访问都抛 `IllegalStateException`，且无 Room 回退
- 严重度：P1
- 类别：E 韧性
- 位置：`app/src/main/java/com/projectlumen/app/core/mmkv/ProjectLumenMmkv.kt:12-23`（`initialize` 失败时抛出）、`40-46`（`checkInitialized` 抛出）；调用侧 `app/src/main/java/com/projectlumen/app/ProjectLumenApplication.kt:207-211`（`runCatching { ... }.onFailure(::recordHandledFailure)` 把异常吞掉）
- 现状：
  ```kotlin
  // ProjectLumenMmkv.kt:40-46
  private fun checkInitialized() {
      if (initialized) return
      initializationFailure?.let { throw IllegalStateException("MMKV storage is unavailable after initialization failure.", it) }
      error("MMKV must be initialized from ProjectLumenApplication before use.")
  }
  ```
  Application 只把初始化失败记成一条 handled failure 就继续启动。但 MMKV 现在承载着设置（`EyeCarePreferencesDataStore`）、运行时状态（`RuntimeRepository`）、功能开关（`FeatureFlagRepository`）、后端连通性（`BackendConnectivityStore`）、安全凭据（`SecureCredentialStore`）——全部没有回退到 Room / DataStore 的路径。
- 触发场景：MMKV 的 native `.so` 加载失败（不受支持的 ABI、16KB page 对齐问题导致 `UnsatisfiedLinkError`、被安全软件拦截、`filesDir` 不可写），或 `MMKV.initialize` 因存储空间耗尽失败。此时 App 正常启动，随后第一次读设置就在协程里抛 `IllegalStateException`。
- 影响：`ProjectLumenStateStore` 的源流在 `stateIn` 的 scope 里抛异常 → 该流终止，UI 永久停在初始空状态（"界面一直转圈/全是默认值且无法保存"）；如果异常逃到没有 `catch` 的服务协程则直接崩溃。用户看到的是"装上就用不了"，而崩溃日志指向的却是设置读取而不是根因。
- 修复方案：`initialize` 失败不应只被吞掉——两处都要改：
  1. `ProjectLumenMmkv` 增加 `fun isAvailable(): Boolean = initialized`，并让 `mmkvWithId` / `multiProcessMmkvWithId` / `encryptedMmkvWithId` 保留抛异常语义（fail-fast 对凭据是对的），但给非安全类存储加一个可空入口 `fun mmkvWithIdOrNull(id: String): MMKV?`。
  2. `EyeCarePreferencesDataStore`、`RuntimeStateMmkvStore`、`FeatureFlagMmkvStore` 的 `mmkv by lazy { ... }` 改为可空 + 当为 null 时回退到各自的 Room DAO / legacy DataStore 路径（`EyeCarePreferencesDataStore` 本来就还持有 `legacyDataStore`，回退成本最低；`RuntimeRepository`、`FeatureFlagRepository` 手里都有 DAO）。
  3. `ProjectLumenApplication.initializeMmkvOrRecordCrash` 在 `onFailure` 分支里额外置一个全局降级标记并在 UI 上给用户一条可见提示，避免"静默不可用"。
- 风险/注意：`initializationFailure` 字段没有 `@Volatile`（`ProjectLumenMmkv.kt:10`），`initialize` 有 `@Synchronized` 但 `checkInitialized` 没有，跨线程读该字段存在可见性问题——最坏情况是抛出的是那条不带 cause 的 `error(...)` 而不是带根因的异常，会让排查更困难。顺手加 `@Volatile` 即可。修复第 2 点时注意不要给 `SecureCredentialStore` 加回退（明文降级会变成安全漏洞，属 G 类）。

### [G02-10] `proximityCheckIntervalMinutes` 的默认值在三处不一致（迁移 5 / 实体 3 / 偏好 3）
- 严重度：P2
- 类别：F 持久化 / A 架构
- 位置：`app/src/main/java/com/projectlumen/app/core/database/AppDatabase.kt:88`（`ADD COLUMN proximityCheckIntervalMinutes INTEGER NOT NULL DEFAULT 5`）vs `app/src/main/java/com/projectlumen/app/core/database/entities/AppSettingsEntity.kt:71`（`= 3`）vs `app/src/main/java/com/projectlumen/app/core/preferences/EyeCarePreferencesDataStore.kt:48`（`= 3`）、`:125`（`?: 3`）、`:212`（`decodeInt(..., 3)`）
- 现状：同一个设置项有 4 处默认值定义，其中 Room 迁移写 5，其余三处写 3。我逐项比对了 `AppDatabase` 全部迁移 SQL 的 `DEFAULT` 与对应实体默认值，**只有这一项不一致**，其余（130 / 38 / 120 / 2 / 10 / 60 / 35 / 85 / 20 / 160 / 180 / 4200 / 1320 / 420 / 70 / 8 / 45 / 'FREE' / 'PAUSE_TIMER' 等）全部对齐。
- 触发场景：v3 升级到 v4 的老用户，`daily`/`app_settings` 表里这一列被填 5；全新安装（Room 用实体默认值建表）得到 3。此外只要 MMKV 侧有持久化值，读路径的 `withEyeCarePreferences` 又会用 3 覆盖 Room 的 5——所以那个 `DEFAULT 5` 实际上是个既不生效又误导人的死值。
- 影响：距离检测的相机唤醒间隔在"老用户 / 新用户"之间不一致（5 分钟 vs 3 分钟），直接影响耗电与告警灵敏度；维护者改默认值时改了一处以为改完了。
- 修复方案：把 `AppDatabase.kt:88` 的 `DEFAULT 5` 改成 `DEFAULT 3` 对齐实体（**不要反过来**——3 是当前实体与偏好层的一致值，改实体会影响所有新装用户）。注意：修改已发布的 `Migration` SQL 只影响**尚未执行过该迁移**的用户，已经跑过 3→4 的存量用户仍是 5，需要额外一条 `MIGRATION_18_19` 做 `UPDATE app_settings SET proximityCheckIntervalMinutes = 3 WHERE proximityCheckIntervalMinutes = 5` 才能真正收敛——但这会覆盖那些**主动**把间隔设成 5 分钟的用户的选择，所以更稳妥的做法是只改迁移 SQL 消除歧义、不做 UPDATE，并在偏好层加注释说明权威默认值只有一处。
- 风险/注意：`DEFAULT` 值只在 `ALTER TABLE ADD COLUMN` 时用于回填，改它不会触发 Room 的 schema 校验失败（Room 容忍数据库侧多出的 `defaultValue`，只在实体显式声明 `@ColumnInfo(defaultValue = ...)` 时才严格比对，本项目没有用这个注解）。所以这个改动是安全的。

### [G02-11] `EyeCarePreferencesDataStore` 的内存缓存是实例级的，第二个实例会永久返回冻结快照
- 严重度：P2
- 类别：A 架构
- 位置：`app/src/main/java/com/projectlumen/app/core/preferences/EyeCarePreferencesDataStore.kt:84`（`private val state by lazy { MutableStateFlow(readFromMmkv()) }`）、`:96-99`（`read()` 直接返回 `state.value`）；第二个实例在 `app/src/main/java/com/projectlumen/app/core/telemetry/EyeCareTelemetryReporter.kt:76-78`
- 现状：`state` 是**类属性**而非 object 属性，只在本实例的 `writeToMmkv`（`:292`）里刷新。对比同组的 `RuntimeStateMmkvStore` / `FeatureFlagMmkvStore` —— 它们把 `state` 放在顶层 `object` 里，天然全进程共享。`EyeCareTelemetryReporter.settingsRepository` 用 `EyeCarePreferencesDataStore(context)` 新建了一个实例，它从不写入，因此 `state` 在第一次 `read()` 时初始化后**再也不更新**。
- 触发场景：用户开机后遥测上报过一次（`state` 定格），随后在设置页把提醒间隔从 20 改成 45、关掉某个 Shizuku 守卫。之后所有遥测快照里这 46 个护眼字段仍是旧值，直到进程重启。
- 影响：上报到后端的设置画像与真实设置长期不符，基于遥测做的诊断/调参会被误导。当前风险仅限"读到旧值"——因为 `DataBackupService` 拿的是 `ProjectLumenApplication.eyeCarePreferences` 这个共享实例（`ProjectLumenApplication.kt:63/68`），没有第二个实例执行写入。但这是个**结构性隐患**：任何未来新建实例并写入的代码都会让共享实例的 `state` 与 MMKV 脱节，进而在下一次 `SettingsRepository.update` 时把陈旧值写回两边（静默回滚）。
- 修复方案：二选一。
  1. 最小改动：让 `EyeCareTelemetryReporter.kt:76-78` 复用 `ProjectLumenApplication.eyeCarePreferences`（它已经持有 `context`，可以 `(context.applicationContext as? ProjectLumenApplication)?.eyeCarePreferences`），并给 `EyeCarePreferencesDataStore` 的构造函数加注释说明"必须全进程单例"。
  2. 结构性修复（推荐）：把 `mmkv` / `state` / `legacyMigrationComplete` / `migrationLock` 抽到文件内的 `private object EyeCarePreferencesMmkvStore`，`EyeCarePreferencesDataStore` 只作为薄封装持有 `Context` 用于 legacy DataStore 迁移。这样与同组另两个 store 的写法一致，也顺带消除了隐患。
- 风险/注意：方案 2 会让 `legacyDataStore`（`Context` 扩展委托 `by preferencesDataStore`）仍留在类里，object 内不能直接访问——迁移函数需要把 `legacyDataStore` 作为参数传进 object（照 `RuntimeStateMmkvStore.ensureMigrated(dao)` 传 DAO 的写法）。

### [G02-12] 全仓库没有任何 `@Transaction` / `withTransaction`，眼部与番茄统计的双表写入不是原子的
- 严重度：P2
- 类别：F 持久化
- 位置：`app/src/main/java/com/projectlumen/app/core/repositories/StatisticsRepository.kt:21-46`（`applyEyeDelta` 与 `applyPomodoroDelta` 被调用方连续调用，各写一张表）；调用点如 `AlarmReceiver.kt:120-121`、`TimerForegroundService.kt:243-244`、`ProjectLumenRuntimeFeatureEntry.kt:217-218`
- 现状：`rg '@Transaction|withTransaction|runInTransaction'` 在 `app/src` 命中 0 处。一次番茄钟阶段切换需要同时更新 `daily_eye_stats`（休息秒数、完成休息次数）与 `daily_pomodoro_stats`（完成番茄数、专注秒数），当前是两次独立的 `upsert`。
- 触发场景：`AlarmReceiver` 在 `goAsync` 的有限时间窗内跑这两次写入；若接收器超时被系统回收、或进程在两次 `upsert` 之间被杀，第一张表已更新、第二张没有。
- 影响：统计页出现自相矛盾的数据（"完成了 5 次休息但只有 4 个番茄"），且这种不一致是永久的——没有对账逻辑会修正它。
- 修复方案：在 `StatisticsRepository` 增加一个合并入口
  ```kotlin
  suspend fun applyDeltas(statsEnabled: Boolean, nowMillis: Long, eye: EyeStatsDelta, pomodoro: PomodoroStatsDelta)
  ```
  内部用 `database.withTransaction { }`（`androidx.room:room-ktx` 已在依赖里，见 `app/build.gradle.kts:333`）把两次读改写包起来；这要求 `StatisticsRepository` 的构造参数从两个 DAO 改为持有 `AppDatabase`（或额外注入它）。然后把上面 3 处"连续调用两个 apply"的调用点改为调 `applyDeltas`。
- 风险/注意：改构造签名会波及 6 处 `StatisticsRepository(...)` 的构造点，需与 G02-01 的"锁提升到 object"一起做，避免两次改动同一批调用点。`withTransaction` 内部会切到 Room 的事务 dispatcher，与 G02-01 的 `Mutex` 嵌套时要保证顺序固定为"先 Mutex 后 withTransaction"，不要反过来。

### [G02-13] 统计表的 `observeAll` / `getAll` 无 `LIMIT`，整表随每个 tick 重新查询并整表重建对象
- 严重度：P2
- 类别：A 架构 / B 并发
- 位置：`app/src/main/java/com/projectlumen/app/core/database/daos/DailyEyeStatsDao.kt:11-12`、`17-18`；`app/src/main/java/com/projectlumen/app/core/database/daos/DailyPomodoroStatsDao.kt:11-12`、`17-18`；`app/src/main/java/com/projectlumen/app/core/repositories/StatisticsRepository.kt:17-19`
- 现状：`SELECT * FROM daily_eye_stats ORDER BY statDate DESC`，无 `LIMIT`。这个 `Flow` 一路喂到 `ProjectLumenStateStore.kt:43/47` 参与 `combine`。而写入方每个统计 tick 都会 `upsert` 今天这一行，触发 Room 的 `InvalidationTracker` → 整表重查 + 整个 `List<DailyEyeStatsEntity>` 重建 + 下游 `distinctUntilChanged` 的整表 `equals` 比较。
- 触发场景：表按天增长，一行/天。用满两三年就是 700~1100 行；`TimerForegroundService` 的 tick 间隔是秒级，即"每几秒做一次上千行的全表查询 + 上千个对象分配 + 上千次 `equals`"。在低端机上表现为统计页滚动掉帧与后台服务持续占用 CPU/唤醒。
- 影响：长期使用后耗电与卡顿逐步恶化，且症状随使用时长增长，难以在短周期测试中发现。`DataBackupService.kt:106-107` 的 `getAll()` 同样是整表读进内存再序列化成 JSON，导出时的内存峰值也随年数增长。
- 修复方案：UI 实际只需要最近一段区间。给 DAO 加带上限的查询并让 `StatisticsRepository.observeEyeStats()` / `observePomodoroStats()` 使用它：
  ```kotlin
  @Query("SELECT * FROM daily_eye_stats ORDER BY statDate DESC LIMIT :limit")
  fun observeRecent(limit: Int): Flow<List<DailyEyeStatsEntity>>
  ```
  取值建议 400（覆盖一年多，足够周/月/年视图与趋势图）。保留无 `LIMIT` 的 `getAll()` 专供备份导出使用。
- 风险/注意：改动前需确认统计/趋势 UI（`ProjectLumen*Screens.kt`，属别组）没有依赖"全量历史"来算累计总量——如果有，正确做法是加一个聚合查询（`SELECT SUM(workingSeconds) ...`）而不是把整表捞到内存里算。修复阶段请先与 UI 组确认这一点，再决定 `limit` 取值。

## 已核查但无问题的点

以下是我确认过**当前实现是正确的**关键设计，修复阶段请不要"顺手改掉"：

- **分层没有被击穿到 UI**：`rg` 全仓库确认没有任何 Compose 屏幕或 ViewModel 直接持有 DAO；`database.xxxDao()` 的调用点全部在 `ProjectLumenRepositories` / `ProjectLumenApplication` 的装配代码、以及服务/备份/遥测里（后三者的旁路问题已在 G02-01 单列）。
- **`distinctUntilChanged` 已经加了**：`ProjectLumenStateStore.kt:28/35/42/46/50/54/71/75/79` 对每条源流都调了 `distinctUntilChanged()`，`ProjectLumenApplication.kt:319` 也有。**不需要**再往 DAO 或 repository 层补——Room 的 `InvalidationTracker` 是按表粒度通知的，无关表的变更不会触发这些查询重发，现有位置已经足够。
- **没有 `@TypeConverter` 是对的**：10 个实体的所有字段都是 `Int` / `Long` / `Float` / `Boolean` / `String`，枚举一律以 `.name` 存字符串（`AppSettingsEntity.kt:15/17/36/64`、`RuntimeStateEntity.kt:12/13/19`），读回时用 `PlanTier.entries.firstOrNull { ... } ?: PlanTier.FREE`（`EntitlementRepository.kt:13`）这种带兜底的匹配而不是会抛异常的 `valueOf`。不存在"TypeConverter 覆盖不全"的问题，也不要为此引入 TypeConverter。
- **`fallbackToDestructiveMigration` 只在 DEBUG 生效**：`AppDatabase.kt:440-442` 被 `if (BuildConfig.DEBUG)` 包住，release 包不会破坏性迁移。这是刻意的开发期便利，不要当成 P0 删掉（但也不要放宽到 release）。
- **`MIGRATION_4_5` 与 `MIGRATION_7_8` 重复调用 `migrateCommerceAndPersonalization` 是安全的**：该函数全程用 `addColumnIfMissing`（`AppDatabase.kt:394-402`，靠 `PRAGMA table_info` 判断，`AppDatabase.kt:404-413`）与 `CREATE TABLE IF NOT EXISTS`，幂等。逐路径核对了 v1→v18 的所有迁移链，不存在"同一列被两次裸 `ALTER TABLE` 添加"导致迁移抛错的路径（早期的裸 `ALTER`（`:62`、`:68-78`、`:84-101`、`:113`、`:119-137`）添加的列与后续 `addColumnIfMissing` 版本不冲突）。
- **`MIGRATION_17_18` 的建表 DDL 与实体一致**：逐列比对 `AppDatabase.kt:289-308` 与 `AppNetworkControlEntity.kt`，列名/类型/`NOT NULL`/主键全部匹配；DDL 里多出的 `DEFAULT ''` / `DEFAULT 0` 不会导致 Room schema 校验失败（Room 只在实体侧显式声明 `defaultValue` 时才严格比对）。实体上没有声明 `indices`，所以不存在"迁移漏建索引"的问题。
- **DataStore 的损坏处理是有的**：`EyeCarePreferencesDataStore.kt:169-177` 在读 legacy DataStore 时 `.catch { if (throwable is IOException) emit(emptyPreferences()) else throw }`，而 `androidx.datastore.core.CorruptionException` 继承自 `IOException`，所以文件损坏会降级为空偏好而不是启动崩溃。虽然没有显式配 `corruptionHandler`，效果等价，不需要补。
- **`first()` 的用法是安全的**：`EyeCarePreferencesDataStore.kt:177` 的 `.first()` 在 `suspend fun ensureLegacyMigrated()` 内、`migrationLock.withLock` 中，不是阻塞调用，且只在一次性 legacy 迁移路径上执行（`legacyMigrationComplete` / MMKV 标记双重短路，`:158-167`），不会每次读设置都碰磁盘。
- **`readFromMmkv` 的 `parsedCache` 是正确的**：`RuntimeRepository.kt:50/93/95` 与 `FeatureFlagRepository.kt:40/89/99` 都以 JSON 原文作为缓存键，`@Volatile` 标注了，缓存不会返回过期解析结果。
- **`softDeleteObsoleteBuiltinTemplates` 的空列表风险不存在**：`TipTemplatesDao.kt:26` 的 `id NOT IN (:retainedIds)` 在 `retainedIds` 为空时会软删除全部内建模板，但唯一调用点 `ProjectLumenTemplatesFeatureEntry.kt:88` 传的是常量 `DefaultTipTemplates.builtinIds`（非空），不会触发。
- **MMKV 单/多进程模式的不一致无害**：`runtime_state` 与 `feature_flags` 用 `MULTI_PROCESS_MODE`、`eye_care_preferences` 用 `SINGLE_PROCESS_MODE`，看似不一致；但 `AndroidManifest.xml` 里没有任何 `android:process`，整个应用单进程，两种模式都正确，差别只是多进程模式多一次文件锁开销。不值得为此改动（改动反而有风险）。
- **`todayKey` 用 wall clock 是合适的**：`core/time/DateKeys.kt:6-11` 用 `ZoneId.systemDefault()` 取本地日期作为统计主键——统计天粒度必须跟随用户本地日历（跨时区旅行时"今天"应该跟着变），这里不该改成单调时钟。真正需要单调时钟的是时长测量，而时长走的是 `coerceElapsedSecondsSince`（`:13-16`）对两个 wall-clock 时间戳做差并对负数归零，属可接受的折中。

