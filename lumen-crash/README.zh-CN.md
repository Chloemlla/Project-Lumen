# Lumen Crash SDK

可复用的 Android 崩溃采集与自适应 Compose 崩溃报告 UI，从 Project Lumen 抽离而来。

[English](./README.md) | [中文](./README.zh-CN.md)

| 项目 | 值 |
|---|---|
| 模块 | `:lumen-crash` |
| 包名 | `com.chloemlla.lumen.crash` |
| minSdk | 26 |
| compileSdk | 37 |
| 语言级别 | Java / Kotlin 17 |

## 目录

- [功能特性](#功能特性)
- [模块结构](#模块结构)
- [接入依赖](#接入依赖)
- [最小集成](#最小集成3-个宿主接入点)
- [公开 API](#公开-api)
- [崩溃捕获流程](#崩溃捕获流程)
- [持久化](#持久化)
- [面包屑](#面包屑)
- [自适应 UI](#自适应-ui)
- [文件分享配置](#文件分享配置)
- [宿主产品文案](#宿主产品文案)
- [作者保护](#作者保护)
- [ProGuard / R8](#proguard--r8)
- [测试](#测试)
- [Project Lumen 宿主说明](#project-lumen-宿主说明)
- [范围外事项](#范围外事项)

## 功能特性

- 未捕获异常采集，并与既有 `UncaughtExceptionHandler` 链式衔接
- 多路径原子写本地持久化（`filesDir` / `noBackupFilesDir` / `cacheDir`）
- 面包屑环形缓冲（最多 40 条，自动脱敏）
- 自适应 Material3 崩溃报告页（`WindowSizeClass`）
- 复制报告 ID / 复制完整报告 / 分享文本 / 分享文件（文件分享需要宿主 `FileProvider`）
- 宿主可配置应用元信息与产品文案
- 上报逻辑保留在宿主侧，通过 `onCrashSaved` 回调接入
- **不可配置/不可移除的作者署名**：ChloeMlla + https://github.com/Chloemlla/
- 严格作者完整性校验（失败即阻断）

## 模块结构

```text
lumen-crash/
  build.gradle.kts
  consumer-rules.pro
  README.md
  README.zh-CN.md
  src/main/
    AndroidManifest.xml
    java/com/chloemlla/lumen/crash/
      LumenCrash.kt                 # 公开 install / record / load / clear API
      LumenCrashConfig.kt           # 宿主配置 + CrashAppInfo
      CrashReport.kt                # 报告模型、JSON、剪贴板导出
      CrashReportStore.kt           # 多路径原子持久化
      CrashBreadcrumbs.kt           # 内存环形缓冲
      CrashAuthorAttribution.kt     # 不可覆盖的作者常量
      AuthorIntegrity.kt            # 失败即阻断的完整性校验
      ui/LumenCrashReportScreen.kt  # 自适应 Compose UI
    res/values/strings.xml          # 英文默认文案
    res/values-zh/strings.xml       # 中文默认文案
  src/test/.../AuthorIntegrityTest.kt
```

## 接入依赖

```kotlin
// settings.gradle.kts
include(":lumen-crash")

// app/build.gradle.kts
implementation(project(":lumen-crash"))
```

该库以 Compose 为主，并通过 `api` 暴露 Compose Material3 与 window-size-class 依赖。若宿主应用已经使用 Compose，通常只需引入本模块，无需额外依赖拼装。

## 最小集成（3 个宿主接入点）

### 1）在 `Application` 尽早安装

建议在 `attachBaseContext` 中安装，使未捕获异常处理器尽可能早生效。

```kotlin
class MyApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        LumenCrash.install(
            this,
            LumenCrashConfig(
                appDisplayName = "My App",
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                commitHash = BuildConfig.SHORT_HASH,
                fileProviderAuthority = "${packageName}.fileprovider",
                shareSubject = "Crash report",
                reportTitle = null,   // null => 使用库内字符串资源
                reportMessage = null, // null => 使用库内字符串资源
                onCrashSaved = { report -> /* 可选：上传 */ },
                killProcessWhenNoPreviousHandler = true,
            ),
        )
    }
}
```

### 2）用待处理报告拦截应用内容

```kotlin
setContent {
    val report = LumenCrash.loadPendingReport()
    if (report != null) {
        LumenCrashReportScreen(
            report = report,
            onContinue = {
                LumenCrash.clearPendingReport()
                // recreate()，或继续进入正常应用内容
            },
            clearStoredReportOnContinue = true,
        )
    } else {
        App()
    }
}
```

### 3）记录面包屑 / 手动上报崩溃

```kotlin
LumenCrash.recordBreadcrumb("MainActivity.onCreate")
LumenCrash.record(throwable) // 会持久化，并触发 onCrashSaved
```

## 公开 API

| API | 用途 |
|---|---|
| `LumenCrash.install(application, config)` | 一次性安装：配置、存储、未捕获异常处理器 |
| `LumenCrash.isInstalled()` | 是否已完成安装 |
| `LumenCrash.configOrNull()` | 当前宿主配置；未安装时为 `null` |
| `LumenCrash.store()` | 获取 `CrashReportStore`（未安装会抛异常） |
| `LumenCrash.recordBreadcrumb(event)` | 追加一条脱敏后的面包屑 |
| `LumenCrash.record(throwable)` | 构建并持久化报告，触发 `onCrashSaved` |
| `LumenCrash.loadPendingReport()` | 优先读内存启动报告，否则读磁盘 |
| `LumenCrash.clearPendingReport()` | 清空磁盘存储 + 内存启动报告 |
| `LumenCrash.clearStartupCrashReport()` | 仅清空内存启动报告 |
| `LumenCrash.startupCrashReport` | 最近一次内存中捕获的报告（只读） |
| `LumenCrashReportScreen(...)` | 自适应崩溃 UI |
| `CrashReport.toClipboardText()` | 完整导出文本（会校验作者完整性） |
| `CrashReport.toJson()` / `crashReportFromJson(...)` | 持久化格式辅助方法 |
| `CrashReport.fromThrowable(throwable, appInfo)` | 从异常构建报告（需要 `CrashAppInfo`） |

### `LumenCrashConfig`

| 字段 | 必填 | 说明 |
|---|---|---|
| `appDisplayName` | 是 | 显示在系统信息 / 报告中 |
| `versionName` | 是 | 宿主版本名 |
| `versionCode` | 是 | 宿主版本号 |
| `commitHash` | 否 | 默认 `"unknown"` |
| `fileProviderAuthority` | 否 | 启用分享文件；`null` 时仅支持文本分享 |
| `shareSubject` | 否 | 分享 Intent 主题；缺省回退库内字符串 |
| `reportTitle` | 否 | UI 标题覆盖；`null` 使用库内 EN/ZH 资源 |
| `reportMessage` | 否 | UI 说明覆盖；`null` 使用库内 EN/ZH 资源 |
| `onCrashSaved` | 否 | 保存成功后的宿主上传/遥测钩子 |
| `killProcessWhenNoPreviousHandler` | 否 | 默认 `true`；无旧 handler 时结束进程 |

作者相关字段**不属于**配置，也无法被覆盖。

### `CrashAppInfo`

供底层报告构建方法（如 `CrashReport.fromThrowable(...)`）使用。

| 字段 | 必填 | 说明 |
|---|---|---|
| `appDisplayName` | 是 | 产品/应用显示名 |
| `versionName` | 是 | 版本名 |
| `versionCode` | 是 | 版本号 |
| `commitHash` | 是 | commit / short hash 字符串 |

常规宿主集成应优先使用 `LumenCrash.record(throwable)`，它会从 `LumenCrashConfig` 派生 `CrashAppInfo`。若直接调用 `fromThrowable`（例如开发者调试页预览崩溃报告），调用方必须自行提供 `CrashAppInfo`。

### `LumenCrashReportScreen`

```kotlin
@Composable
fun LumenCrashReportScreen(
    report: CrashReport,
    onContinue: (() -> Unit)? = null,
    clearStoredReportOnContinue: Boolean = true,
    onClearStoredReport: (() -> Unit)? = null,
)
```

- 仅在作者完整性校验通过后才会打开；失败时显示阻断页。
- 标题/说明优先使用 `LumenCrashConfig` 中的非空覆盖值，否则回退库内资源。
- 主要操作：复制报告 ID、复制完整报告、分享、清除并继续。
- `onClearStoredReport` 允许宿主注入额外逻辑（例如先调度上传再清除）。为 `null` 时，界面会调用 `LumenCrash.clearPendingReport()`。

## 崩溃捕获流程

1. `install()` 保存配置、创建 `CrashReportStore`、安装默认未捕获异常处理器，并记录安装面包屑。
2. 发生未捕获异常时：
   - 构建 `CrashReport`（构建失败则生成 fallback 报告）
   - 写入 `startupCrashReport`
   - 通过新的 `CrashReportStore(applicationContext)` 持久化
   - 若存在 `onCrashSaved` 则回调
   - 若存在旧 handler，则继续链式调用
   - 否则可按配置结束进程（`killProcessWhenNoPreviousHandler`）
3. `record(throwable)` 对已捕获异常走同一套构建/保存/回调路径。
4. 下次进程启动：宿主调用 `loadPendingReport()`，并在进入正常 UI 前展示 `LumenCrashReportScreen`。

若尚未 `install`，但异常仍进入 SDK handler 路径，报告构建会回退到包名 / `"unknown"` 应用元信息。

## 持久化

`CrashReportStore` 会原子写入 `crash_report.json` 到以下全部路径：

1. `context.filesDir`
2. `context.noBackupFilesDir`
3. `context.cacheDir`

任一路径写入成功即视为保存成功。加载时返回第一个可读且有效的报告。清除时会删除所有现有副本。

JSON 包含：报告 ID、时间戳、异常/根因、线程/进程、系统信息、堆栈、最近事件，以及强制写入的作者字段。

## 面包屑

- API：`LumenCrash.recordBreadcrumb(event)` 或 `CrashBreadcrumbs.record(event)`
- 容量：40 条（环形缓冲）
- 每条事件在脱敏后截断至 180 字符
- 格式：`HH:mm:ss.SSS  <event>`
- 快照会写入新的 `CrashReport.recentEvents`
- UI 默认展示最近 12 条

脱敏会屏蔽本机 user-home 路径以及 `content://` / `file://` URI。构建报告时，堆栈/根因文本也会应用同样规则。

## 自适应 UI

`LumenCrashReportScreen` 在可拿到 `Activity` 时使用 `calculateWindowSizeClass`；否则回退到 `BoxWithConstraints` 的宽高判断。

| 布局信号 | 行为 |
|---|---|
| 窄宽（`< 600.dp` 或 Compact） | 内容全宽，水平内边距 16.dp，操作按钮纵向排列 |
| 中等宽度 | 内容最大 720.dp，内边距 20.dp |
| 超宽（`>= 840.dp` 或 Expanded） | 内容最大 960.dp，更宽的元信息胶囊；高度非 Compact 时操作按钮可横向排列 |
| 矮高（`< 560.dp` 或 Compact） | 更紧的纵向间距；降低堆栈预览最大高度，保证主操作可达 |

堆栈预览默认折叠为 18 行，用户可展开/收起。作者页脚卡片在完整性校验通过时始终展示。

## 文件分享配置

文本分享无需宿主额外配置。文件分享需要：

1. 将宿主 `FileProvider` authority 传入 `fileProviderAuthority`
2. Provider 路径允许暴露 cache 目录文件

宿主 Provider 示例：

```xml
<!-- AndroidManifest.xml -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

```xml
<!-- res/xml/file_paths.xml -->
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="cache" path="." />
</paths>
```

SDK 的“分享文件”会在 cache 下写入 UTF-8 `.txt`，并向目标应用授予 URI 读权限。若未配置 authority，UI 会显示库内“文件分享不可用”文案，但仍可进行文本分享。

## 宿主产品文案

库默认文案位于：

- `src/main/res/values/strings.xml`（英文）
- `src/main/res/values-zh/strings.xml`（中文）

请通过配置覆盖面向产品的标题/说明/主题，而不是去改作者署名字符串：

```kotlin
LumenCrashConfig(
    appDisplayName = getString(R.string.app_name),
    versionName = BuildConfig.VERSION_NAME,
    versionCode = BuildConfig.VERSION_CODE,
    commitHash = BuildConfig.SHORT_HASH,
    fileProviderAuthority = "${packageName}.fileprovider",
    shareSubject = getString(R.string.crash_report_share_subject),
    reportTitle = getString(R.string.crash_report_title),
    reportMessage = getString(R.string.crash_report_message),
    onCrashSaved = { report -> scheduleUpload(report) },
)
```

上报逻辑**刻意不在 SDK 范围内**。Project Lumen 通过 `onCrashSaved` / 继续时的钩子调度遥测上传，网络策略仍由应用侧掌控。

## 作者保护

作者常量位于 `CrashAuthorAttribution`：

- 名称：`ChloeMlla`
- URL：`https://github.com/Chloemlla/`
- Handle：`chloemlla`
- 指纹：`AUTHOR_NAME|AUTHOR_URL` 的 SHA-256 小写十六进制
- 页脚标签：`Crash SDK by ChloeMlla · https://github.com/Chloemlla/`

强制写入：

- 报告模型（`authorName` / `authorUrl` / `authorFingerprint`）
- JSON 持久化
- 剪贴板 / 分享内容页脚
- 崩溃 UI 作者页脚（无法通过配置隐藏）

`AuthorIntegrity.verifyOrThrow(...)` 会在安装、报告构建、加载/导出路径以及 UI 打开时执行。不匹配会抛出 `SecurityException`（或进入 UI 阻断态）。`consumer-rules.pro` 会保留作者常量与完整性校验入口，支撑多点校验。

> 开源 fork 仍可直接改源码；这里主要防止意外/运行时剥离，并提高静默移除成本。绝对防 fork 不在范围内。

## ProGuard / R8

库自身默认关闭 release minify。宿主开启混淆时应保留 `consumer-rules.pro` 中的规则：

```proguard
-keep class com.chloemlla.lumen.crash.CrashAuthorAttribution {
    public static final java.lang.String *;
}
-keep class com.chloemlla.lumen.crash.AuthorIntegrity {
    public static *** verifyOrThrow();
    public static *** fingerprintHex();
}
```

## 测试

当前单测重点覆盖作者完整性与导出署名：

- `AuthorIntegrityTest.fingerprintMatchesConstant`
- `AuthorIntegrityTest.verifyOrThrowSucceeds`
- `AuthorIntegrityTest.clipboardTextIncludesAuthorAttribution`

本仓库的构建/测试通过 GitHub workflow 验证，不要求在本机跑完整构建。

## Project Lumen 宿主说明

在本 monorepo 中，`:app` 已依赖 `:lumen-crash`，并完成：

- 在 `ProjectLumenApplication.installLumenCrashSdk()` 中安装
- 在 `MainActivity` 用 `LumenCrashReportScreen` 拦截启动 UI
- 也可在 `ProjectLumenApp` 中展示会话内报告
- 通过宿主钩子（`onCrashSaved` / clear 回调）调度崩溃报告上传
- 复用现有宿主 FileProvider authority：`${applicationId}.fileprovider`
- 开发者调试页预览崩溃时，使用 `CrashReport.fromThrowable(..., CrashAppInfo(...))`，并传入应用名与 `BuildConfig` 元信息

抽离完成后，旧的应用内 crash 核心源码已移除；请勿重新引入 `core/crash` 或 `ProjectLumenCrashReportScreen` 这类应用侧重复实现。

## 范围外事项

- 服务端崩溃后端
- 非 Android 平台
- Crashlytics 替代品
- 拆成 core/UI 双制品
- 独立 sample 应用（MVP 以本 README + 宿主应用为准）
- 对源码级 fork 修改的绝对防护
