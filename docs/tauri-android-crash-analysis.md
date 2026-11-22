# Tauri Android（WebView 套壳）崩溃诊断与 Lumen Crash SDK 支持

> 适用版本：`lumen-crash-core` / `lumen-crash` 0.1.x 及以后
> 调研对象：`tauri-apps/tauri@dev` 的 Android 运行时（`TauriActivity` / `tauri-macros` / `tauri-cli` 模板）
> 本文同时是 SDK 的设计说明：为什么新增 `CrashComponent`、多进程 prior-exit 上报、`recordExternalFailure` 与 `CrashExternalInbox`。

---

## 1. 为什么套壳应用的崩溃"看起来会消失"

传统 Android 崩溃由 `Thread.setDefaultUncaughtExceptionHandler` 捕获，SDK 拿到的是 Java `Throwable`。套壳应用（Tauri / Capacitor / 纯 `WebView`）有四类失败不在这个通道里：

| 失败形态 | 真实运行时 | 系统留下的证据 | 旧版 SDK 的表现 |
| --- | --- | --- | --- |
| Rust panic 逸出应用入口 | Rust（`lib[app]_lib.so`） | `ApplicationExitInfo`：`REASON_CRASH_NATIVE` + `SIGABRT (6)`，tombstone 里**没有 abort message** | 只显示"进程异常退出"，无栈、无归属 |
| Rust worker 线程 panic | Rust | 通常**什么都不留**（进程没死） | 完全看不到 |
| WebView 渲染进程死亡 | Chromium `:sandboxed_process0` | `REASON_CRASH` + 渲染进程名；主进程仍活着 | 主进程没崩 → 看不到；一旦被采集又会误报成主进程崩溃 |
| 主线程卡在 JNI/Rust | 主线程 | `REASON_ANR` + `getAnrInfo()` | 采到 ANR，但不区分"卡在原生层" |
| JS 未捕获异常 | WebView JS 引擎 | 只有 `window.onerror` 能拿到 | 完全看不到 |
| 原生库加载失败 | Linker | `java.lang.UnsatisfiedLinkError`（有时带 `lib[app]_lib.so`） | 归为普通 Java 崩溃，看不出是 Rust 库 |

所以"支持套壳应用"不是加一个标题常量，而是要把**归属（哪个运行时）**、**系统退出证据（信号/重要性/RSS）**、**外部运行时上报通道**补齐。

---

## 2. Tauri Android 架构速览（决定崩溃形态的部分）

从 `tauri-apps/tauri@dev` 的源码确认：

- `crates/tauri/mobile/android-codegen/TauriActivity.kt`
  `abstract class TauriActivity : WryActivity()`，把 `onCreate` / `onResume` / `onNewIntent` 等生命周期转发给 `PluginManager`。宿主写 `class MainActivity : TauriActivity()`。
- `crates/tauri-macros/src/mobile.rs`
  `#[tauri::mobile_entry_point]` 生成的 `start_app` 被包在 `std::panic::catch_unwind` 里；捕获到展开失败时只做 `eprintln!("attempt to unwind out of \`rust\` with err: {:?}")`，然后**直接 `std::process::abort()`**。
  ⇒ Release 包 stderr 被丢弃，tombstone 里只有 `signal 6 (SIGABRT)` 与 `lib[app]_lib.so` 的原始 PC，**没有 abort message**。
- `crates/tauri/mobile/android/src/main/java/app/tauri/Logger.kt`
  tag `"Tauri"`，只在 `shouldLog()`（debug 构建）时输出 ⇒ 不能把 release 端的诊断建立在 logcat 上。
- `crates/tauri-cli/templates/mobile/android/buildSrc/src/main/kotlin/RustPlugin.kt`
  每个 ABI 一个 product flavor，Rust 产物固定为 `lib[app]_lib.so`（Cargo `[lib] name = "[app]_lib"`）。这个名字同时是符号化提示。
- Rust 端没有默认的 `panic::set_hook` → panic 信息既不入文件也不入 JVM，只有 abort。

**结论**：Tauri 宿主必须自己接一个"外部上报通道"，SDK 负责把通道里的内容变成可读报告。

---

## 3. SDK 现在能做什么（新增能力）

### 3.1 `CrashComponent`：报告归属

`CrashReport.component` 取值：

| 值 | 含义 | 判定依据 |
| --- | --- | --- |
| `rust_native` | Rust 代码（Tauri/Wry） | tombstone/栈里出现 `rust_begin_unwind`、`core::panicking`、`cargo/registry`，或库名含 `rust` / `tauri` / `wry` / `tao` / `*_lib.so` |
| `native` | 其他原生代码 | `.so` 帧带 `offset`/`BuildId`/`pc`，或 `REASON_CRASH_NATIVE`/`REASON_SIGNALED` |
| `webview_renderer` | WebView 渲染进程死亡 | 进程名含 `:sandboxed_process` / `:privileged_process` / `:webview` |
| `webview` | WebView 胶水层 Java 栈 | 栈里出现 `android.webkit.*` / `org.chromium.*` |
| `java` | 普通 JVM 崩溃 | 以上都不匹配 |
| `anr` | 主线程无响应 | `ANR` / `STARTUP_HANG` / `FREEZE` 报告 |
| `host_external` | 宿主运行时上报 | `CrashReportKind.EXTERNAL` |
| `unknown` | 旧报告（无该字段） | JSON 缺字段时的兼容值 |

分类在 `CrashReport.fromThrowable` / `fromWatchdog` / `fromPriorExit` / `fromExternal` 内自动完成，宿主无需传参。崩溃界面首屏标题按归属变化（"原生层已中止" / "网页渲染进程已重启" / "应用无响应" …），不再一律"已保存崩溃报告"。

### 3.2 多进程 prior-exit 采集

`PriorExitCrashCollector` 现在：

- 用 `getHistoricalProcessExitReasons(packageName, /* pid */ 0, /* maxNum */ 32)` 取**整个包所有进程**的退出记录（旧实现把窗口大小当 pid 传，实际只查了一个进程）。
- 一次返回**最多 5 条**未上报记录，而不是"最新一条"。
- 记录 `ApplicationExitInfo.getStatus()`（信号号，如 `SIGABRT (6)`）、`getImportance()`、`getRss()` 到报告里。
- 去重标记取"已处理的最大时间戳"，每条死亡只在紧接着的那次启动上报一次。

**进程归属规则**（关键）：只有主进程（`processName == packageName`，或没有 `:` 前缀）的死亡会占用唯一的"待展示槽位"；`:sandboxed_process0`（渲染进程）与其它子进程的死亡只走 `onReportSaved` + 后端上传。
⇒ 渲染进程崩了、壳还活着时，不会在下次启动给用户弹一整屏"应用已崩溃"。

### 3.3 `LumenCrash.recordExternalFailure(...)`

给宿主自己的运行时用（JS 桥、Rust panic hook、Flutter）：

```kotlin
val report = LumenCrash.recordExternalFailure(
    source = "webview.js",              // 谁上报的
    message = "TypeError: x is undefined",
    stackTrace = jsStackOrNull,          // 可为空
    occurredAtMillis = 0L,               // 0 = 用当前时间
    threadName = "js",
    claimPendingSlot = false,            // 见下
)
```

- 不抛异常（未安装或输入不可用时返回 `null`），可以从任意线程调用。
- `claimPendingSlot = false`（默认）：只上传 + 回调，不占用待展示槽位 —— **适合可恢复的 JS 报错**，避免错误风暴把槽位占满。
- `claimPendingSlot = true`：把报告写进待展示槽位，下次启动弹崩溃界面 —— **适合进程已死的证据**。
- 受"非致命上报"的频率限制（1 分钟 1 次）保护，不会打爆上传队列。

### 3.4 `CrashExternalInbox`：把文件变成报告

原生运行时（不能方便地跨 JNI 调 Kotlin）可以往目录里丢文件，SDK 在 `install` 后的后台任务里自动转换：

```kotlin
LumenCrash.install(application) {
    externalInboxDirectory = File(filesDir, "crash-inbox")   // 自动扫描
}
// 或手动触发
CrashExternalInbox.ingest(File(filesDir, "crash-inbox"))
```

支持格式：

- `*.json`：`{"source": "...", "message": "...", "stackTrace": "...", "timestampMillis": 0, "thread": "..."}`
  （`msg`/`error`、`stack`/`backtrace`、`timestamp`/`occurredAtMillis` 作为别名）
- `*.txt` / `*.log`：首行是消息，其余行是栈。

单文件读取上限 64 KiB，每次最多处理 8 个；**只有成功生成报告才删除文件**，失败留待下次。

---

## 4. 接入配方

### 4.1 Kotlin 侧（Application）

```kotlin
class DemoApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        LumenCrash.installSafely(this) {
            externalInboxDirectory = File(filesDir, "crash-inbox")
            startupHangWatchdogEnabled = true
            anrWatchdogEnabled = true
        }
    }
}
```

`Rust panic hook` 写文件时，目录必须存在：Rust 侧第一次写入前 `create_dir_all`。

### 4.2 Rust 侧：panic hook 落盘

```toml
# src-tauri/Cargo.toml
[dependencies]
serde_json = "1"
```

```rust
// src-tauri/src/lib.rs
use std::{fs, io::Write, path::PathBuf};

// Tauri 在 Kotlin 侧把 filesDir 传给 Rust（例如通过初始化参数）
fn inbox_dir(files_dir: &str) -> PathBuf {
    PathBuf::from(files_dir).join("crash-inbox")
}

pub fn install_panic_hook(files_dir: String) {
    let default_hook = std::panic::take_hook();
    std::panic::set_hook(Box::new(move |info| {
        default_hook(info); // 保留 stderr / logcat 里的原始输出

        let dir = inbox_dir(&files_dir);
        let _ = fs::create_dir_all(&dir);
        let thread = std::thread::current();
        let payload = info
            .payload()
            .downcast_ref::<&str>()
            .map(|s| s.to_string())
            .or_else(|| info.payload().downcast_ref::<String>().cloned())
            .unwrap_or_else(|| "rust panic".to_string());
        let location = info
            .location()
            .map(|l| format!("{}:{}:{}", l.file(), l.line(), l.column()))
            .unwrap_or_default();
        let record = serde_json::json!({
            "source": "rust.panic",
            "message": format!("{payload} (at {location})"),
            // RUST_BACKTRACE=1 时 info 里不含栈，需要 std::backtrace::Backtrace
            "stackTrace": std::backtrace::Backtrace::force_capture().to_string(),
            "thread": thread.name().unwrap_or("unnamed"),
            "timestampMillis": std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .map(|d| d.as_millis())
                .unwrap_or(0),
        });
        let path = dir.join(format!("rust-panic-{}.json", record["timestampMillis"]));
        if let Ok(mut file) = fs::File::create(path) {
            let _ = file.write_all(record.to_string().as_bytes());
        }
    }));
}
```

要点：

- `default_hook` 先调用，保证 logcat 仍有 `thread 'x' panicked at ...`（debug 构建）。
- hook 内**不要**做网络/阻塞操作：`abort()` 紧随其后，进程随时会死。
- `std::backtrace::Backtrace::force_capture()` 在 release 下同样可用（需要未剥离符号才有可读帧）。
- worker 线程 panic 不会 abort 进程，这类报告会以 `claimPendingSlot` 语义从 inbox 进来（进程下次启动时上报）。

### 4.3 JS 侧：`window.onerror` / `unhandledrejection`

```ts
// 通过 @tauri-apps/api 的 invoke 交给 Rust，Rust 再写 inbox 或直接桥到 Kotlin
import { invoke } from "@tauri-apps/api/core";

window.addEventListener("error", (event) => {
  void invoke("record_js_error", {
    message: String(event.message ?? event.error),
    stack: String(event.error?.stack ?? ""),
    source: "webview.js",
    timestampMillis: Date.now(),
  });
});

window.addEventListener("unhandledrejection", (event) => {
  void invoke("record_js_error", {
    message: `unhandled rejection: ${String(event.reason)}`,
    stack: String((event.reason as Error | undefined)?.stack ?? ""),
    source: "webview.js",
    timestampMillis: Date.now(),
  });
});
```

Rust 命令侧把这两个字段写成 `*.json` 丢进 `crash-inbox` 即可（与 4.2 同一格式，`source` 换成 `webview.js`）。

---

## 5. 诊断剧本

拿到一份报告后，按 `component` → 退出证据 → 库名三步走：

1. **`component = rust_native` + `退出原因 REASON_CRASH_NATIVE / SIGABRT (6)`**
   → 典型"panic 逸出 `start_app` 后被 `abort()`"。看 `根因`：inbox 里的 panic 消息（若有）就是真相；没有 inbox 时只能靠 tombstone 的 PC 做符号化。
   符号化：用 `nativeLibraries` 里的库名（`lib[app]_lib.so`）+ tombstone 的 `BuildId` 找对应 ABI 的未剥离 `.so`，`llvm-symbolizer` / `ndk-stack`。
2. **`component = rust_native`，但退出原因是 `REASON_CRASH`/Java 栈**
   → 多半是 `UnsatisfiedLinkError` 或 FFI 参数穿越（Rust 里 `unwrap()` 一个 Kotlin 传进来的 `null`）。
3. **`component = webview_renderer`**
   → 页面侧问题（OOM/无限递归/渲染崩溃）。主进程没死，报告的 `processName` 带 `:sandboxed_process0`；此时报告不会弹崩溃界面，只上传。
4. **`component = anr`**
   → 主线程被 Rust 调用占住。看 `线程转储` 里是否停在 `Native method` / `lib[app]_lib.so`；`importance` 高说明用户正在前台。
5. **`component = host_external` + `Captured by: webview.js`**
   → JS 未捕获异常，看 `根因` 与 JS 栈（`at app.js:12:5` 这类）。
6. **`nativeLibraries` 为空但 `component = native`**
   → tombstone 被截断或进程死在 linker；结合 `systemInfo` 的 ABI 判断是否只缺某个 ABI 的产物。

---

## 6. 已知限制

- **符号化需要调试符号**：SDK 只能给出库名、BuildId、原始 PC。`lib[app]_lib.so` 需用构建该版本时的 `buildId` 匹配，否则地址无意义。
- **`abort()` 前的最后一份 panic 文件可能丢失**：hook 写文件与进程被杀竞争，极端情况下文件为空；此时退化为只有 tombstone。
- **`REASON_CRASH_NATIVE` 的 tombstone 只在 API 31+ 可解析**（protobuf 格式）；API 30 走 `REASON_CRASH_NATIVE` 但没有可读栈。
- **渲染进程报告不占槽位**：如果宿主只依赖"崩溃界面"来做提示，这类事件只能通过 `onReportSaved` / 后端看到。
- **JS 报文本地不做源码映射**：`app.js:12:5` 需要源映射表才能还原到 TS 源文件。
- **外部上报受频率限制**：`recordExternalFailure` 与 `recordNonFatal` 共用 1 分钟 1 次的上传节流；本地报告对象仍会返回给调用方。

---

## 7. 相关文件

| 文件 | 作用 |
| --- | --- |
| `lumen-crash-core/.../CrashAnalysis.kt` | `CrashComponent`、分类器、`ExitSignals`、`NativeLibraries`、进程归属判定 |
| `lumen-crash-core/.../CrashExternalInbox.kt` | 外部失败文件的解析与摄取 |
| `lumen-crash-core/.../PriorExitCrashCollector.kt` | 多进程 prior-exit 采集与退出证据 |
| `lumen-crash-core/.../CrashReport.kt` | `component` / `exitStatus` / `nativeLibraries` / `externalSource` 字段与 `fromExternal` |
| `lumen-crash-core/.../LumenCrash.kt` | `recordExternalFailure`、槽位归属、inbox 自动摄取 |
| `lumen-crash/.../LumenCrashReportScreen.kt` | 按归属变化的标题/文案与新增信息项 |
| `lumen-crash-core/src/test/.../CrashAnalysisTest.kt` | 分类规则、信号名、库名提取、外部记录解析、JSON 兼容性 |
