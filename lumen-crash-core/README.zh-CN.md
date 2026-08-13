# Lumen Crash SDK Core

仅采集的制品，不含 Compose UI。

[English](./README.md) | [中文](./README.zh-CN.md)

| 项目 | 值 |
|---|---|
| 模块 | `:lumen-crash-core` |
| Maven | `com.chloemlla.lumen:lumen-crash-core` |
| 包含 | install/record/store/breadcrumbs/ANR + 启动看门狗/作者保护/粘贴上传 |
| 不含 | Compose 崩溃页 / 文件分享 UI |

需要崩溃报告 UI 时优先用 bundle（`com.chloemlla.lumen:lumen-crash`）。
仅做 Flutter 桥接或只需采集 + 持久化的宿主用 core。

## 推荐接入方式（最先推荐）

从**本地暂存的 Maven 布局**消费 `lumen-crash-core`，而不是把 Gradle 直接指向 GitHub Packages。

GitHub Packages 对**每次**下载都要求 GitHub token——即使包是公开的。宿主如果直接从
`maven.pkg.github.com/Chloemlla/Project-Lumen` 解析，本地构建需要 `gpr.user` / `gpr.key`
（带 `read:packages` 的 PAT），而且只有在 GitHub Actions 里因为隐式使用注入的
`GITHUB_TOKEN` 才开箱即用。把 release 资产本地暂存后，第三方应用就完全不再需要这套鉴权。

### 1. 把最新 release 解析到本地 Maven 目录

宿主侧的解析脚本查询 GitHub API，取最新的非 draft `lumen-crash-v*` release，再把
AAR/POM 资产下载到 `android/local-maven`。API 可匿名访问，但传入 `GITHUB_TOKEN`
（Actions 自动注入，本地可用 `gh auth token`）可避开匿名 60 次/小时的限流：

```bash
# scripts/resolve-lumen-crash.sh（示意）
OWNER_REPO="Chloemlla/Project-Lumen"
RELEASES="$(curl -fsSL \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer ${GITHUB_TOKEN:-}" \
  "https://api.github.com/repos/${OWNER_REPO}/releases?per_page=100")"
VERSION="$(printf '%s' "$RELEASES" | python3 -c '
import json, sys
releases = json.load(sys.stdin)
cands = [r for r in releases if not r.get("draft")
         and str(r.get("tag_name", "")).startswith("lumen-crash-v")]
cands.sort(key=lambda r: r.get("published_at") or r.get("created_at") or "")
print(cands[-1]["tag_name"].removeprefix("lumen-crash-v"))'
)"
# 把 lumen-crash-core-${VERSION}.aar / .pom 暂存到
# android/local-maven/com/chloemlla/lumen/lumen-crash-core/${VERSION}/
```

把解析到的版本通过 property 或环境变量交给 Gradle：

```properties
# android/gradle.properties
lumenCrashVersion=0.1.0
```

### 2. 注册本地仓库；GitHub Packages 仅作为带凭据的兜底

在 `android/settings.gradle` 中，从暂存目录解析 `lumen-crash-core`，并且**只有存在凭据时**
才注册 GitHub Packages——空凭据会让 Gradle 返回 HTTP 401 并中止解析，即使 `local-maven`
已经包含该 AAR：

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
        // 优先：由 scripts/resolve-lumen-crash.* 暂存的 release 资产，CI 无需
        // 为 lumen-crash-core 配置跨仓库 GitHub Packages 鉴权。
        maven {
            name = "LumenCrashLocal"
            url = uri("${settingsDir}/local-maven")
        }
        def gprUser = providers.gradleProperty("gpr.user").orNull
            ?: System.getenv("GITHUB_ACTOR")
        def gprKey = providers.gradleProperty("gpr.key").orNull
            ?: System.getenv("GITHUB_TOKEN")
        // 只有存在凭据才注册 GitHub Packages。空凭据会返回 401 并中止解析，
        // 即使 local-maven 已经有该 AAR。
        if (gprUser && gprKey) {
            maven {
                name = "GitHubPackagesProjectLumen"
                url = uri("https://maven.pkg.github.com/Chloemlla/Project-Lumen")
                credentials {
                    username = gprUser
                    password = gprKey
                }
            }
        }
    }
}
```

### 3. 声明依赖（仅采集）

```kotlin
val lumenCrashVersion =
    providers.gradleProperty("lumenCrashVersion")
        .orElse(providers.environmentVariable("LUMEN_CRASH_VERSION"))
        .orElse("0.1.0")
        .get()

implementation("com.chloemlla.lumen:lumen-crash-core:$lumenCrashVersion")
```

### 为什么是这个顺序

- 本地暂存让第三方宿主对依赖本身**无需任何 GitHub Packages 凭据**——只需要查最新版本的
  GitHub API 调用，而 CI 用注入的 `GITHUB_TOKEN` 即可满足。
- 存在凭据时 GitHub Packages 仍是有效兜底：宿主可以不经过暂存步骤直接解析，代价是
  处处都要 `read:packages` 鉴权。
- 运行时接入（看门狗配置、`markStartupComplete()`）见 [看门狗](#看门狗)。

## 看门狗

`LumenCrash` 保留既有未捕获异常路径，并新增后台主 Looper 看门狗。当主线程超过配置的
超时时间未处理心跳时，持久化一条 `CrashReportKind.FREEZE` 报告。它运行在主 Looper 之外，
因此 UI 被阻塞时仍能采集线程转储。

```kotlin
LumenCrash.install(this) {
    anrWatchdogEnabled = true
    anrWatchdogTimeoutMillis = 5_000L
    anrWatchdogCheckIntervalMillis = 1_000L
    onAnrDetected = { report -> /* 从 worker 上报遥测 */ }
}
```

对于可能在渲染首帧前无限等待的启动路径，可显式开启启动看门狗，并在宿主的首帧回调中
调用 `markStartupComplete()`：

```kotlin
LumenCrash.install(this) {
    startupHangWatchdogEnabled = true
    startupHangTimeoutMillis = 15_000L
}

// 在首个可用帧之后调用，不要放在 Application.onCreate() 中。
LumenCrash.markStartupComplete()
```

启动报告使用 `CrashReportKind.STARTUP_HANG`；主 Looper 报告使用
`CrashReportKind.FREEZE`。没有 `kind` 或 `durationMillis` 的既有 JSON 仍按普通崩溃报告加载。
