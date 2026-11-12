# 崩溃 `0d56b7959042` 定位与修复：WorkManager 未初始化

- 报告：`D:\Downloads\crash-0d56b7959042.txt`（non_fatal，2026-11-02 00:08:52，`com.chloemlla.projectlumen` 1.0.1 (608)，commit `fffd332d`，Xiaomi 21091116AC / Android 11 / SDK 30）
- 结论：**`fffd332d` 是当前 `main` 的祖先，缺陷在 HEAD 依然存在**（`git merge-base --is-ancestor fffd332d HEAD` 成立，且此后没有任何 WorkManager 初始化相关改动）。

## 一、症状

```
java.lang.IllegalStateException: WorkManager is not initialized properly.  You have explicitly
disabled WorkManagerInitializer in your manifest, have not manually called WorkManager#initialize
at this point, and your Application does not implement Configuration.Provider.
  at fq4.bu(WorkManagerImpl.getInstance)              ← tc3.bq: ProximityDetectionWorker.enqueueNext
  at tc3.bq(ProximityDetectionWorker.enqueueNext)
  at ProjectLumenApplication.scheduleProximityMonitoring
  at f52.bo                                           ← BootReceiver$Companion.restoreScheduledWork
  at js.invokeSuspend                                 ← BootReceiver.onReceive 的 goAsync 协程
  ... DefaultDispatcher-worker-1
```

同一份报告的面包屑：`Application.attachBaseContext`(.317) → `Application.onCreate`(.332) → `MMKV initialized`(.795) → `Backend connectivity status=checking`(.808) → `Certificate pinning inactive`(.888) → `Handled failure captured: java.lang.IllegalStateException`(.900)。即：**进程冷启动后约 600 ms，在一条 IO 协程里第一次触碰 WorkManager 就抛异常**，被捕获后按 non_fatal 上报，进程没有死。

## 二、根因（已在 `androidx.work:work-runtime-ktx:2.11.2` 实物上验证）

1. WorkManager 的初始化由 AAR manifest 里的 `androidx.startup.InitializationProvider` + `<meta-data android:name="androidx.work.WorkManagerInitializer">` 完成，而且这个 provider **没有 `android:directBootAware`**（`javap`/AAR manifest 均已确认）。直接启动窗口内（重启后、用户首次解锁前）系统只实例化 `directBootAware` 的 provider，`androidx.startup` 被整体跳过 —— 而且这个"没初始化"的状态会被该进程**记住一辈子**，解锁之后也不会补上。
2. 本仓库唯一的 `directBootAware` 组件是 `BootReceiver`（`LOCKED_BOOT_COMPLETED` / `QUICKBOOT_POWERON`，MIUI 会投递后者）。因此"重启→解锁前的进程创建"必然由它发起：进程起来了、`Application.onCreate` 跑完了、provider 却没跑。
3. 解锁后 `BootReceiver.onReceive` 的 `isUserUnlocked` 守卫放行，`restoreScheduledWork` 读到 Room 里的设置（此时 CE 存储已可用），接着 `scheduleProximityMonitoring()` → `WorkManager.getInstance()`。
4. `WorkManagerImpl.getInstance(Context)` 的回落分支只认 `Configuration.Provider`：本 Application 没有实现它，于是直接抛上面那句 `IllegalStateException`（`javap -c` 反汇编可见 `instanceof androidx/work/Configuration$Provider` 与那句字符串是同一个分支）。
5. 该异常被 `BootReceiver.onReceive` 的 `runCatching{}.onFailure{ recordHandledFailure }` 吞掉 → 报告被标为 non_fatal、面包屑写 "Handled failure captured"，进程存活。用户看不到崩溃，但**近距离/眨眼监控这次完全没有排上**，直到下一次成功入队（下一次启动或下一个 `BOOT_COMPLETED`）才自愈。

排除项：eager initializer 若真的抛异常，`androidx.startup.AppInitializer.doInitialize` 会包成 `StartupException` 外抛，进程会在 provider 创建阶段就崩（fatal），与本报告的 non_fatal + `Handled failure captured` 不符；仓库 manifest 也没有任何 `tools:node="remove"` / `WorkManager.initialize` 调用。

## 三、修复（三层，互不替代）

| 编号 | 位置 | 改法 |
|---|---|---|
| WM-01 | `app/.../ProjectLumenApplication.kt` | 实现 `Configuration.Provider`，`workManagerConfiguration = Configuration.Builder().build()`。`WorkManagerImpl.getInstance` 一旦发现静态实例缺失就走这个 provider 自初始化（官方对"initializer 没跑"的唯一补救路径）。eager initializer 本来就用同一份默认配置，正常启动路径行为不变 |
| WM-02 | `app/.../core/work/WorkScheduler.kt`（新增） | 全应用唯一的 WorkManager 触达点：先 `UserManager.isUserUnlocked`（CE 存储未解锁时 WorkManager 的数据库也打不开，半初始化比不初始化更糟）再 `getInstance`；任何失败都就地吞掉——记日志 + 面包屑 + **每进程最多一次** non_fatal 上报，然后返回 `false`，不再把异常甩进调用方的协程 |
| WM-03 | 三个 Worker 的 companion | `ProximityDetectionWorker` / `TimerReconciliationWorker` / `ShizukuResilienceWorker` 的 enqueue/cancel 全部改走 `WorkScheduler`；`BootReceiver` 的解锁判断也改为复用 `WorkScheduler.isStorageUnlocked`，全仓库只保留一份"存储可用"定义 |
| WM-04 | `app/.../core/lifecycle/AppLifecycleCoordinator.kt` | `BootReceiver.restoreScheduledWork(app)` 加 `runCatching` + `recordHandledFailure`：恢复扫描碰到 Room/AlarmManager/WorkManager 的任何失败，都不能连坐本次前台恢复（暂停时长结算、通知同步） |
| WM-05 | `app/src/test/.../core/work/WorkSchedulerContractTest.kt`（新增） | 源码级回归：Application 必须实现 `Configuration.Provider`；`WorkManager.getInstance(`/`initialize(` 只允许出现在 `WorkScheduler.kt`；WorkScheduler 必须先判解锁再触碰 WorkManager；三个 Worker 必须走 `WorkScheduler` |

为什么不只做 WM-01：直接启动窗口内 Application 已经不完整初始化过，`WorkManager.initialize` 若在 CE 存储仍锁定的时候被触发，会在坏状态上落一个半初始化的单例；WM-02 的解锁闸门把这个窗口挡在外面，等进程真正可用后再补初始化。反过来只做 WM-02 也不行：没有 `Configuration.Provider`，解锁后的第一次 `getInstance` 依然会抛，只是被吞掉、变成"永远排不上"的静默失效。

## 四、验证

- 静态：`Configuration.Provider` 的 `getWorkManagerConfiguration()` 与 [WM-02] 的 `instanceof` 分支已在 2.11.2 的 `classes.jar` 上 `javap` 确认；源码括号平衡自查通过（无本地构建，见 CLAUDE.md）。
- CI：`Build Project Lumen Android`（`gradle assembleRelease` + `testDebugUnitTest` + `lintDebug`）全绿为唯一判据。
- 线上：下次带修复的版本开机后不再出现该崩溃组；若仍出现，`WorkScheduler` 会带出 `WorkManager unavailable: getInstance ( … )` 面包屑与一条 non_fatal，可直接区分"锁定窗口跳过"（只有 info 日志，不上报）与真实故障。
