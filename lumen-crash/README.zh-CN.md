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
- [自动发布](#自动发布)
- [消费已发布 SDK](#消费已发布-sdk)
  - [每次发布会产出什么](#1每次发布会产出什么)
  - [自动解析 main 最新自动发版](#2如何选择解析版本)
  - [GitHub Packages](#5方式-bgithub-packages外部应用推荐)
  - [GitHub Packages Maven 使用教程](#51快速开始github-packages-maven-包)
  - [Release 资产 / 本地 Maven](#6方式-c只使用-github-release-资产不走-packages-鉴权)
  - [排障清单](#9排障清单)
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
  - [第三方必须配置的混淆豁免](#第三方必须配置的混淆豁免)
- [接入坑点](#接入坑点)
- [测试](#测试)
- [Project Lumen 宿主说明](#project-lumen-宿主说明)
- [范围外事项](#范围外事项)

## 功能特性

- 未捕获异常采集，并与既有 `UncaughtExceptionHandler` 链式衔接
- 多路径原子持久化到应用专属**外部**存储（`getExternalFilesDir` / `externalCacheDir`）
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
  sdk.version
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

## 自动发布

SDK 通过 GitHub Actions 工作流自动发布：

- 工作流：`.github/workflows/lumen-crash-sdk-release.yml`
- 版本源：`lumen-crash/sdk.version`
- Maven 坐标：`com.chloemlla.lumen:lumen-crash:<version>`

### 触发方式

| 触发 | 版本 / tag 行为 |
|---|---|
| 推送到 `main`，且改动了 `lumen-crash/**` 或该 workflow | 版本 = `<sdk.version>-<shortSha>`，tag = `lumen-crash-v<version>` |
| 推送 tag `lumen-crash-vX.Y.Z` | 版本 = `X.Y.Z`，使用该精确 tag 发布 |
| 手动 `workflow_dispatch` | 可选版本覆盖；默认同时发布 GitHub Release 与 Packages |

### 发布流水线

1. 解析版本元数据
2. 运行 `:lumen-crash:test`
3. 组装 release AAR（`:lumen-crash:assembleRelease`）
4. 将 Maven 制品发布到本地仓库，便于打包资产
5. 收集 AAR / POM / sources / checksums 与 `sdk-manifest.json`
6. 创建 GitHub Release（tag 形如 `lumen-crash-v...`）
7. 将同一 Maven publication 发布到 GitHub Packages

### 手动稳定版 tag 示例

```bash
# 需要时先修改 lumen-crash/sdk.version
git tag lumen-crash-v0.1.0
git push origin lumen-crash-v0.1.0
```

## 消费已发布 SDK

本节是面向 `.github/workflows/lumen-crash-sdk-release.yml` **发布产物** 的详细使用教程。

### 1）每次发布会产出什么

每次 SDK 发布成功后，会同时得到：

1. 一个 GitHub Release（tag 形如 `lumen-crash-v<version>`）
2. 同一套 Maven 制品到 **GitHub Packages**
3. 名为 `lumen-crash-sdk-<version>` 的 workflow artifact

常见发布资产：

| 资产 | 示例 | 用途 |
|---|---|---|
| Release AAR | `lumen-crash-0.1.0.aar` | Android 库二进制包 |
| POM | `lumen-crash-0.1.0.pom` | Maven 坐标与依赖元数据 |
| Gradle Module Metadata | `lumen-crash-0.1.0.module` | Gradle variant 元数据 |
| Sources JAR | `lumen-crash-0.1.0-sources.jar` | IDE 源码跳转 |
| 校验文件 | `checksums.txt` | 全部资产的 SHA-256 |
| 清单 | `sdk-manifest.json` | 机器可读发布元数据 |
| 说明 | `release-notes.md` | 人类可读发布摘要 |

Maven 坐标：

```text
com.chloemlla.lumen:lumen-crash:<version>
```

仓库地址：

```text
https://maven.pkg.github.com/Chloemlla/Project-Lumen
```

Release 页面模式：

```text
https://github.com/Chloemlla/Project-Lumen/releases/tag/lumen-crash-v<version>
```

### 2）如何选择 / 解析版本

默认消费方式：**始终自动解析 Project Lumen 上 `main` 分支为 `lumen-crash` 自动发版的最新版本**。正常外部接入**不要**在源码里硬编码钉死版本号。

| 场景 | 版本形态 | 要求 / 推荐来源 |
|---|---|---|
| 外部应用默认接入（**必须**） | 最新 `0.1.0-<shortSha>` | 每次 CI/配置时自动解析最新 `lumen-crash-v*` main 发版 |
| 本 monorepo 本地开发 | 工程模块 | `implementation(project(":lumen-crash"))` |
| 仅限临时例外冻结 | 纯 `X.Y.Z` | 只允许短期排障使用；不是默认文档路径 |

**要求：** 宿主应依赖 `com.chloemlla.lumen:lumen-crash:<auto-resolved-latest>`，其中 `<auto-resolved-latest>` 来自 2.1 解析器，并在每次 CI/配置时刷新。不要把手工维护的硬编码版本当作长期接入方式。

#### 2.1 自动解析最新 main 自动发版

`main` 自动发版由 `.github/workflows/lumen-crash-sdk-release.yml` 生成，规则为：

- 版本形态：`<sdk.version>-<shortSha>`（示例：`0.1.0-1a2b3c4d`）
- tag 形态：`lumen-crash-v<version>`（示例：`lumen-crash-v0.1.0-1a2b3c4d`）

请使用 GitHub Releases API，并按 `lumen-crash-v` 前缀过滤。**不要单独使用** `/releases/latest`，因为本仓库还可能发布非 SDK 的 release。

bash / Git Bash：

```bash
# 依赖：curl + jq
# 可选：export GH_TOKEN=...（API 鉴权 / 限流时需要）

OWNER_REPO="Chloemlla/Project-Lumen"
API="https://api.github.com/repos/${OWNER_REPO}/releases?per_page=100"
AUTH_HEADER=()
if [ -n "${GH_TOKEN:-${GITHUB_TOKEN:-}}" ]; then
  AUTH_HEADER=(-H "Authorization: Bearer ${GH_TOKEN:-$GITHUB_TOKEN}")
fi

VERSION="$(
  curl -fsSL "${AUTH_HEADER[@]}" \
    -H "Accept: application/vnd.github+json" \
    "$API" \
  | jq -r '
      [.[]
        | select(.draft == false)
        | select(.tag_name | startswith("lumen-crash-v"))
      ]
      | sort_by(.published_at // .created_at)
      | reverse
      | .[0].tag_name
      | sub("^lumen-crash-v"; "")
    '
)"

if [ -z "$VERSION" ] || [ "$VERSION" = "null" ]; then
  echo "No lumen-crash release found" >&2
  exit 1
fi

echo "Resolved latest lumen-crash main auto release: ${VERSION}"
echo "implementation(\"com.chloemlla.lumen:lumen-crash:${VERSION}\")"
echo "Release page: https://github.com/${OWNER_REPO}/releases/tag/lumen-crash-v${VERSION}"
```

PowerShell：

```powershell
# 可选：$env:GH_TOKEN = "..."
$ownerRepo = "Chloemlla/Project-Lumen"
$headers = @{
  Accept = "application/vnd.github+json"
  "User-Agent" = "lumen-crash-version-resolver"
}
$token = $env:GH_TOKEN
if (-not $token) { $token = $env:GITHUB_TOKEN }
if ($token) { $headers.Authorization = "Bearer $token" }

$releases = Invoke-RestMethod `
  -Uri "https://api.github.com/repos/$ownerRepo/releases?per_page=100" `
  -Headers $headers

$latest = $releases |
  Where-Object { -not $_.draft -and $_.tag_name -like "lumen-crash-v*" } |
  Sort-Object { if ($_.published_at) { [datetime]$_.published_at } else { [datetime]$_.created_at } } -Descending |
  Select-Object -First 1

if (-not $latest) {
  throw "No lumen-crash release found"
}

$version = $latest.tag_name -replace '^lumen-crash-v', ''
Write-Host "Resolved latest lumen-crash main auto release: $version"
Write-Host "implementation(\"com.chloemlla.lumen:lumen-crash:$version\")"
Write-Host "Release page: https://github.com/$ownerRepo/releases/tag/lumen-crash-v$version"
```

GitHub CLI 一行命令：

```bash
gh release list --repo Chloemlla/Project-Lumen --limit 100   | awk '/^lumen-crash-v/ { print $1; exit }'   | sed 's/^lumen-crash-v//'
```

解析完成后，把自动解析到的最新版本注入 Gradle。优先用 env / property 注入，**不要在源码中硬编码钉死**。

推荐 CI / 本地模式：

```bash
VERSION="$(...上面的解析命令...)"
echo "LUMEN_CRASH_VERSION=${VERSION}" >> "$GITHUB_ENV"
# 或本地 Gradle：
# echo "lumenCrashVersion=${VERSION}" >> gradle.properties
```

```kotlin
// app/build.gradle.kts
val lumenCrashVersion =
    providers.gradleProperty("lumenCrashVersion")
        .orElse(providers.environmentVariable("LUMEN_CRASH_VERSION"))
        .orNull
        ?: error("请先解析最新 lumen-crash（见 README 2.1）")

dependencies {
    implementation("com.chloemlla.lumen:lumen-crash:$lumenCrashVersion")
}
```

正常接入路径**不要**提交永久硬编码行，例如 `implementation("com.chloemlla.lumen:lumen-crash:0.1.0-1a2b3c4d")`。版本必须来自 latest-main 自动解析器。

#### 2.2 其他版本来源

这些只是同一套“自动最新”策略的检查辅助，不是长期硬编码钉死版本的理由：

- GitHub Release 标题 / tag（`lumen-crash-v0.1.0-1a2b3c4d` => `0.1.0-1a2b3c4d`）
- release 资产 `sdk-manifest.json` 的 `version` 字段
- GitHub Packages 的 package 版本列表

像 `lumen-crash-v0.1.0` 这样的纯稳定 tag 只用于临时例外冻结。默认外部接入始终是：每次自动解析最新的 `main` `lumen-crash-v*` 发版。

### 3）下载后先做完整性校验

若你手动下载 AAR 再接入 CI 或正式宿主，请先校验：

```bash
# Linux / macOS / Git Bash
sha256sum -c checksums.txt

# 或只校验单个文件
sha256sum lumen-crash-0.1.0.aar
# 与 checksums.txt 中对应行比对
```

```powershell
# Windows PowerShell
Get-FileHash .\lumen-crash-0.1.0.aar -Algorithm SHA256
# 与 checksums.txt 比对
```

同时打开 `sdk-manifest.json` 确认：

- `groupId` = `com.chloemlla.lumen`
- `artifactId` = `lumen-crash`
- `version` 与下载资产一致
- `maven.coordinates` 与你的 Gradle 依赖行一致

### 4）方式 A：本 monorepo 工程模块

适用于 Project Lumen 仓库内部。

```kotlin
// settings.gradle.kts
include(":lumen-crash")

// app/build.gradle.kts
dependencies {
    implementation(project(":lumen-crash"))
}
```

优点：

- 无需鉴权
- 直接跟踪源码
- 最适合本地功能开发

缺点：

- 不能直接给外部宿主工程复用

### 5）方式 B：GitHub Packages（外部应用推荐）

这是**外部 Android 应用**消费已发布 Maven 包的推荐方式。

#### 5.1 快速开始：GitHub Packages Maven 包

只需要最短路径时，按这份清单走：

1. 自动解析 Project Lumen 上 `main` 为 `lumen-crash` 自动发版的最新版本（见 [第 2 节](#2如何选择解析版本)）。**不要**硬编码钉死版本。
2. 创建带 `read:packages` 权限的 GitHub token。
3. 把凭证写到 `~/.gradle/gradle.properties`（**不要提交**到仓库）。
4. 在 `settings.gradle.kts` 添加 GitHub Packages Maven 仓库。
5. 依赖 `com.chloemlla.lumen:lumen-crash:$lumenCrashVersion`，其中 `$lumenCrashVersion` 为自动解析到的最新值。
6. Sync Gradle，并接入 `LumenCrash.install(...)` 与待处理崩溃报告 UI。

| 字段 | 值 |
|---|---|
| Group ID | `com.chloemlla.lumen` |
| Artifact ID | `lumen-crash` |
| 默认版本来源 | **自动最新** `main` 发版（`0.1.0-<shortSha>`） |
| 版本策略 | 始终自动解析；正常接入不要硬编码钉死 |
| 示例解析版本（瞬时） | `0.1.0-1a2b3c4d` |
| 完整坐标形态 | `com.chloemlla.lumen:lumen-crash:<auto-resolved-latest>` |
| Maven 仓库 | `https://maven.pkg.github.com/Chloemlla/Project-Lumen` |
| Packages 页面 | `https://github.com/Chloemlla/Project-Lumen/packages` |
| 自动发版 tag 模式 | `https://github.com/Chloemlla/Project-Lumen/releases/tag/lumen-crash-v0.1.0-<shortSha>` |

自动解析后的 Gradle 依赖形态：

```kotlin
implementation("com.chloemlla.lumen:lumen-crash:$lumenCrashVersion")
// $lumenCrashVersion 来自 2.1 / env / gradle.properties，不是硬编码 pin
```

#### 5.2 查找 / 解析已发布版本

必选路径：每次 CI/配置时自动解析 Project Lumen `main` 发布的最新 `lumen-crash-v*`（见 2.1），再把该值注入 Gradle。**不要**把它作为永久写死在源码中的常量。

| 来源 | 如何使用 |
|---|---|
| **必须：** 最新 main 自动发版 | 运行 2.1 解析脚本；形态 `0.1.0-<shortSha>` |
| GitHub Release tag | `lumen-crash-v0.1.0-1a2b3c4d` => 依赖版本 `0.1.0-1a2b3c4d` |
| Release 资产 `sdk-manifest.json` | 字段 `version` 与 `maven.coordinates` |
| GitHub Packages 的 package 版本列表 | 如 `0.1.0-1a2b3c4d` |

外部接入始终跟踪最新 main 自动发版（`0.1.0-<shortSha>`）。不要把永久硬编码的稳定冻结写成默认依赖行。

#### 5.3 创建读权限 token

即使在某些账号/组织配置下 package 看起来是公开的，GitHub Packages 仍可能要求鉴权。请创建可读取本仓库 packages 的 token：

| 运行环境 | 凭证 |
|---|---|
| 本机 | 带 `read:packages` 的 classic PAT 或 fine-grained token |
| 同一仓库的 GitHub Actions | 具备 `packages: read` 的 `GITHUB_TOKEN` |
| 其他仓库 / 外部 CI | 带 `read:packages` 的专用 PAT/fine-grained token，存为 secret |

Token 规则：

- 用户名填 GitHub 用户名（或 token 所属身份）。
- 密码 / token 值填 PAT 或 CI token，**不是** GitHub 登录密码。
- 若账号/组织启用了 SAML SSO，先给 token 完成 SSO 授权。
- 永远不要把 token 提交进 git。

Classic PAT 最小权限：

```text
read:packages
```

若 package 为私有，或组织要求更广的 package 访问，请同时确保 token 能读取所属仓库。

#### 5.4 把凭证放在仓库外

推荐本机文件：`~/.gradle/gradle.properties`

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_PAT_OR_TOKEN
```

Windows 示例路径：

```text
C:\Users\<you>\.gradle\gradle.properties
```

macOS / Linux 示例路径：

```text
~/.gradle/gradle.properties
```

环境变量写法：

```bash
# bash / zsh / Git Bash
export GITHUB_ACTOR=YOUR_GITHUB_USERNAME
export GITHUB_TOKEN=YOUR_GITHUB_PAT_OR_TOKEN
```

```powershell
# Windows PowerShell
$env:GITHUB_ACTOR = "YOUR_GITHUB_USERNAME"
$env:GITHUB_TOKEN = "YOUR_GITHUB_PAT_OR_TOKEN"
```

**不要**把真实 token 写进会提交的工程 `gradle.properties`。

#### 5.5 只配置一次 Maven 仓库

在消费方应用的 `settings.gradle.kts`：

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "GitHubPackagesProjectLumen"
            url = uri("https://maven.pkg.github.com/Chloemlla/Project-Lumen")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

注意：

- 保留 `google()` 与 `mavenCentral()`，这样 AndroidX / Compose 传递依赖仍可解析。
- 凭证写在这个仓库块里，不要在源码中硬编码密钥。
- 若工程仍使用根 `build.gradle.kts` / `allprojects.repositories`，把同一 Maven 块加到那里。

Groovy `settings.gradle` 等价写法：

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "GitHubPackagesProjectLumen"
            url = uri("https://maven.pkg.github.com/Chloemlla/Project-Lumen")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

#### 5.6 声明依赖

通常在宿主模块 `app/build.gradle.kts`：

```kotlin
// app/build.gradle.kts
val lumenCrashVersion =
    providers.gradleProperty("lumenCrashVersion")
        .orElse(providers.environmentVariable("LUMEN_CRASH_VERSION"))
        .orNull
        ?: error("请先解析最新 lumen-crash（见 README 2.1）")

dependencies {
    // 必须：使用 2.1 / 5.2 自动解析的最新 main 发版
    // 不要在源码中硬编码 com.chloemlla.lumen:lumen-crash:X.Y.Z-<sha>
    implementation("com.chloemlla.lumen:lumen-crash:$lumenCrashVersion")
}
```

Groovy：

```groovy
def lumenCrashVersion = findProperty("lumenCrashVersion") ?: System.getenv("LUMEN_CRASH_VERSION")
if (!lumenCrashVersion) {
    throw new GradleException("请先解析最新 lumen-crash（见 README 2.1）")
}

dependencies {
    implementation "com.chloemlla.lumen:lumen-crash:$lumenCrashVersion"
}
```

`$lumenCrashVersion` 必须来自 latest-main 自动解析器。正常接入**不要**硬编码钉死固定版本字符串。

#### 5.7 Sync、解析并验证

1. 在 Android Studio 同步 Gradle，或执行：

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
```

2. 确认依赖树包含已解析到的最新坐标，例如：

```text
com.chloemlla.lumen:lumen-crash:<auto-resolved-latest>
# 某次解析结果示例：com.chloemlla.lumen:lumen-crash:0.1.0-1a2b3c4d
```

3. 可选冒烟检查：

```bash
# 仅解析
./gradlew :app:compileDebugKotlin --dry-run

# 完整编译
./gradlew :app:compileDebugKotlin
```

若解析失败，跳到 [排障清单](#9排障清单)。

#### 5.8 宿主工程要求

SDK 以 Compose 为主，并会通过 `api` 暴露 Material3 / window-size-class：

- 宿主 `minSdk` 建议 `>= 26`
- 宿主需启用 Compose
- 推荐 Kotlin / JVM 17
- 若宿主已使用 Compose Material3，通常无需额外拼装 Compose 依赖

示例：

```kotlin
android {
    compileSdk = 35 // 或更高

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
```

#### 5.9 包解析成功后的最小代码

Gradle 能下载 package 后，接入下面 3 个宿主触点。

尽早 install：

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
                onCrashSaved = { report ->
                    // 可选：宿主上传 / 遥测调度
                },
            ),
        )
    }
}
```

启动时按待处理报告门禁 UI：

```kotlin
setContent {
    val report = LumenCrash.loadPendingReport()
    if (report != null) {
        LumenCrashReportScreen(
            report = report,
            onContinue = {
                LumenCrash.clearPendingReport()
                // recreate() 或切回正常应用内容
            },
        )
    } else {
        App()
    }
}
```

可选：已处理异常 / 面包屑：

```kotlin
LumenCrash.recordBreadcrumb("CheckoutScreen.submit")
runCatching { riskyWork() }
    .onFailure { LumenCrash.record(it) }
```

#### 5.10 消费方 CI 示例

同一仓库 / 默认可读取 package 的 token：

```yaml
- name: Build consumer app
  env:
    GITHUB_ACTOR: ${{ github.actor }}
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
  run: ./gradlew :app:assembleRelease --no-daemon
```

默认 token 读不到本 package 的外部仓库：

```yaml
- name: Build consumer app
  env:
    GITHUB_ACTOR: ${{ github.actor }}
    GITHUB_TOKEN: ${{ secrets.LUMEN_CRASH_READ_PACKAGES_TOKEN }}
  run: ./gradlew :app:assembleRelease --no-daemon
```

把 `LUMEN_CRASH_READ_PACKAGES_TOKEN` 配成带 `read:packages` 的仓库 secret。

#### 5.11 常见 GitHub Packages 误区

| 误区 | 结果 | 修复 |
|---|---|---|
| 仓库 URL 写错 | `Could not find ... lumen-crash` | 使用 `https://maven.pkg.github.com/Chloemlla/Project-Lumen` |
| 缺少 credentials 块 | `401 Unauthorized` | 添加 `credentials { ... }` 并设置 `gpr.*` 或环境变量 |
| token 没有 `read:packages` | `401` / `403` | 重建具备 package 读权限的 token |
| 未做 SSO 授权 | `403 Forbidden` | 给 token 完成组织 SSO 授权 |
| 版本号写错 | 找不到 package | 从 Packages / Release / `sdk-manifest.json` 复制自动解析到的最新版本 |
| 凭证被提交 | 密钥泄露 | 挪到 `~/.gradle/gradle.properties` 或 CI secrets，并轮换 token |
| 只用裸 AAR 而不是 Maven 坐标 | 丢失传递依赖 | 优先 `implementation("com.chloemlla.lumen:lumen-crash:<auto-resolved-latest>")` |

### 6）方式 C：只使用 GitHub Release 资产（不走 Packages 鉴权）

适合能下载 Release 文件，但不想在每个消费方配置 GitHub Packages 凭证的场景。

#### 6.1 下载资产

从 `lumen-crash-v<version>` 发布页至少下载：

- `lumen-crash-<version>.aar`
- `lumen-crash-<version>.pom`（建议）
- `checksums.txt`
- `sdk-manifest.json`

并按上文完成校验。

#### 6.2 组装本地 Maven 仓库

按标准坐标路径放置文件：

```text
local-maven/
  com/
    chloemlla/
      lumen/
        lumen-crash/
          0.1.0/
            lumen-crash-0.1.0.aar
            lumen-crash-0.1.0.pom
            lumen-crash-0.1.0.module          # 可选，建议保留
            lumen-crash-0.1.0-sources.jar     # 可选
```

再让 Gradle 指向该目录：

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            name = "LumenCrashLocal"
            url = uri("${rootDir}/local-maven")
        }
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.chloemlla.lumen:lumen-crash:0.1.0")
}
```

#### 6.3 直接依赖 AAR 文件（仅建议用于快速冒烟）

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(files("libs/lumen-crash-0.1.0.aar"))
}
```

说明：

- 适合快速编译验证
- 正式接入仍建议走 Maven 坐标，以便保留 POM 传递依赖信息
- 若使用裸 AAR，可能需要手动对齐 Compose Material3 / activity-compose 版本

### 7）依赖解析成功后的端到端接入

当 Gradle 已能解析 `lumen-crash` 后，按以下 3 个接入点集成。

#### 7.1 尽早安装

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
                onCrashSaved = { report ->
                    // 宿主上传 / 遥测调度
                },
            ),
        )
    }
}
```

#### 7.2 用待处理报告拦截 UI

```kotlin
setContent {
    val report = LumenCrash.loadPendingReport()
    if (report != null) {
        LumenCrashReportScreen(
            report = report,
            onContinue = {
                LumenCrash.clearPendingReport()
                // recreate()，或切换到正常应用内容
            },
        )
    } else {
        App()
    }
}
```

#### 7.3 可选：面包屑与已捕获异常

```kotlin
LumenCrash.recordBreadcrumb("CheckoutScreen.submit")
runCatching { riskyWork() }
    .onFailure { LumenCrash.record(it) }
```

#### 7.4 可选：文件分享支持

若需要崩溃页“分享文件”，请配置宿主 `FileProvider`，并把 authority 传入 `LumenCrashConfig.fileProviderAuthority`。仅文本分享时可不配置。

### 8）CI 使用方式

消费方仓库的 GitHub Actions 示例：

```yaml
- name: Build consumer app
  env:
    GITHUB_ACTOR: ${{ github.actor }}
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
  run: |
    ./gradlew :app:assembleRelease --no-daemon
```

若从其他仓库读取私有 package，请改用具备 `read:packages` 的 PAT secret，而不是默认无权读取该 package 的 token。

### 9）排障清单

| 现象 | 常见原因 | 处理 |
|---|---|---|
| `Could not find com.chloemlla.lumen:lumen-crash:...` | 未配置 GitHub Packages 或版本写错 | 补仓库地址，并用 release/manifest 中的精确版本 |
| `401 Unauthorized` / `403 Forbidden` | token 无 `read:packages` 或未 SSO 授权 | 重建/授权 token，检查 `gpr.user` / `gpr.key` |
| 依赖解析成功但 Compose UI 符号缺失 | 宿主未启用 Compose | 设置 `buildFeatures.compose = true` |
| 无法使用文件分享 | 未配置 `fileProviderAuthority` | 配置 FileProvider 并传入 authority |
| 预览/手动 `fromThrowable` 编译失败 | 缺少 `CrashAppInfo` | 传入应用元信息，或改用 `LumenCrash.record(throwable)` |
| 校验和不匹配 | 下载不完整/损坏 | 重新下载资产并复核 `checksums.txt` |

### 10）生产环境推荐路径

外部宿主应用建议：

1. 每次 CI/配置时自动解析 Project Lumen `main` 上 `lumen-crash` 的最新自动发版（`lumen-crash-vX.Y.Z-<shortSha>`）
2. 通过 **GitHub Packages Maven 坐标** 消费 `com.chloemlla.lumen:lumen-crash:<auto-resolved-latest>`
3. 凭证不进版本库
4. 持续重新解析最新 main 自动发版；正常接入**不要**硬编码钉死版本
5. 避免在源码中长期硬编码冻结版本
6. 校验 `sdk-manifest.json` 与 checksums 以确认解析结果
7. 上线前完成 `install` + 待处理报告 UI 拦截

本 monorepo 建议：

1. 继续使用 `implementation(project(":lumen-crash"))`
2. 仅在验证外部消费打包时使用已发布产物

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

`CrashReportStore` 会把 `crash_report.json` 原子写入应用专属**外部**目录，而不是应用私有内部目录：

1. `context.getExternalFilesDir("lumen-crash")`
2. `context.getExternalFilesDir(null)/lumen-crash`
3. `context.externalCacheDir/lumen-crash`

任一**外部**路径写入成功即视为保存成功。成功保存后，会删除 `filesDir` / `noBackupFilesDir` / `cacheDir` 下的旧私有副本。

加载顺序：

1. 先读外部目录
2. 仅在迁移时回读旧私有目录；若读到有效旧报告，会重写到外部存储

清除时会删除全部外部副本和旧私有副本。

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

`LumenCrashReportScreen` 在可拿到 `Activity` 时使用 `calculateWindowSizeClass`；否则结合 `BoxWithConstraints` 与 `LocalConfiguration` 的宽高判断。页面会应用 `WindowInsets.safeDrawing`，在 edge-to-edge 宿主下避开手机状态栏/导航栏与挖孔区域。

| 布局信号 | 行为 |
|---|---|
| 窄宽（`< 600.dp` 或 Compact / 手机） | 内容全宽，12–16.dp 更紧内边距，更小 Hero 字号，堆栈标题与展开按钮上下排布，操作按钮纵向排列 |
| 中等宽度 | 内容最大 720.dp，内边距 20.dp；高度非 Compact 时次要操作两列排布 |
| 超宽（`>= 840.dp` 或 Expanded） | 内容最大 960.dp，更宽的元信息胶囊；高度非 Compact 时次要操作可横向排列 |
| 矮高（`< 560.dp` 或 Compact） | 更紧的纵向间距；降低堆栈预览最大高度，保证短屏手机上主操作可达 |

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
    <external-cache-path name="external_cache" path="." />
</paths>
```

SDK 的“分享文件”会优先写入外部 cache（不可用时回退内部 cache）下的 UTF-8 `.txt`，并向目标应用授予 URI 读权限。若未配置 authority，UI 会显示库内“文件分享不可用”文案，但仍可进行文本分享。

## 宿主产品文案

共享崩溃 UI 的标签、操作按钮、分享选项文案和堆栈提示只保留在 SDK：

- `src/main/res/values/strings.xml`（英文）
- `src/main/res/values-zh/strings.xml`（中文）

Project Lumen 宿主只保留 3 个产品侧覆盖项，并通过 config 注入：

- `crash_report_title`
- `crash_report_message`
- `crash_report_share_subject`

不要在宿主再复制一套共享 UI 字符串，也不要覆盖作者标识。

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

### 第三方必须配置的混淆豁免

如果宿主 app 开启了 ProGuard / R8，**必须把 `com.chloemlla.lumen.crash.**` 当作混淆豁免面处理**。

禁止：

- 混淆 / 重命名 Lumen Crash 公开 API 类
- 收缩删除作者署名常量
- 剥离运行时完整性校验入口

否则可能出现 install/崩溃页 fail-closed（`SecurityException` 或阻断崩溃页），以及复制/分享丢失作者署名。

### 自动豁免路径（优先）

库自身默认关闭 release minify。AAR 会携带 `consumer-rules.pro`；当你依赖：

```kotlin
implementation("com.chloemlla.lumen:lumen-crash:0.1.0")
```

Android Gradle Plugin 通常会自动把这些 consumer rules 合并进**宿主 app** 的混淆配置。

正常通过 Maven / GitHub Packages 依赖时，这条自动合并路径就是优先推荐的豁免方式，**通常不必**手抄规则。

### 显式宿主豁免（推荐备份）

如果你的宿主：

- 开启了 `isMinifyEnabled = true`
- 会剥离 consumer rules
- 使用了自定义 shrinker 流水线
- 或希望在 app 模块显式备份

请把下面这段**必需豁免规则**加到宿主 `app/proguard-rules.pro`：

```proguard
############################################################
# Lumen Crash SDK 混淆豁免
# 坐标：com.chloemlla.lumen:lumen-crash
# 放到宿主 app/proguard-rules.pro
############################################################

# 必需：作者署名 + 完整性校验
-keep class com.chloemlla.lumen.crash.CrashAuthorAttribution {
    public static final java.lang.String *;
}
-keep class com.chloemlla.lumen.crash.AuthorIntegrity {
    public static *** verifyOrThrow();
    public static *** fingerprintHex();
}

# 必需备份：保留宿主会调用的公开 SDK API
-keep class com.chloemlla.lumen.crash.LumenCrash { *; }
-keep class com.chloemlla.lumen.crash.LumenCrashConfig { *; }
-keep class com.chloemlla.lumen.crash.CrashReport { *; }
-keep class com.chloemlla.lumen.crash.CrashAppInfo { *; }
-keep class com.chloemlla.lumen.crash.CrashReportStore { *; }
-keep class com.chloemlla.lumen.crash.CrashBreadcrumbs { *; }
-keep class com.chloemlla.lumen.crash.ui.LumenCrashReportScreenKt { *; }

# 包级豁免（第三方宿主的安全默认）
-keep class com.chloemlla.lumen.crash.** { *; }
-dontwarn com.chloemlla.lumen.crash.**
```

宿主 release 混淆示例：

```kotlin
// app/build.gradle.kts
android {
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro", // 必须包含上面的 Lumen Crash 豁免块
            )
        }
    }
}
```

### Compose / Material3 说明

本 SDK 以 Compose 为主，并通过 `api` 暴露 Material3 / window-size-class。宿主 release 构建中，不要添加会大面积剥离 Compose runtime 的激进规则。

### 为什么必须豁免

- `CrashAuthorAttribution` 常量会被多点作者完整性校验读取。
- `AuthorIntegrity.verifyOrThrow()` / `fingerprintHex()` 会在安装、报告构建/加载/导出、UI 打开时执行。
- 宿主集成会调用 `LumenCrash`、`LumenCrashConfig`、`CrashReport`、`LumenCrashReportScreen`。
- 若这些符号被重命名或删除，install/UI 可能 fail-closed，抛出 `SecurityException` 或进入阻断崩溃页。

### 开启混淆后如何验证

1. 构建依赖了 `lumen-crash` 的 minify release APK/AAB。
2. 触发测试崩溃，或打开崩溃报告预览路径。
3. 确认：
   - `LumenCrash.install(...)` 仍成功
   - 待处理报告 UI 能打开
   - 复制 / 分享内容仍包含作者署名

## 接入坑点

宿主接入中的高频踩坑点。可当作上线前 checklist；细节见下文链接章节。

### 依赖与版本

- **不要**单独使用 `/releases/latest`。本仓库可能同时发布非 SDK release。只认 `lumen-crash-v*` 前缀 tag。
- **必须：** 自动解析最新 `main` 的 `lumen-crash-v*` 发版，并注入为 `com.chloemlla.lumen:lumen-crash:<auto-resolved-latest>`。正常外部接入**不要**硬编码钉死固定版本。
- Maven 本身没有魔法浮动 `latest` 坐标；保持最新的正统方式是 2.1 解析器 + 每次 CI/配置时的 property/env 注入。
- GitHub Packages 需要鉴权读取（`read:packages`）。若组织启用 SSO，token 必须先完成 SSO 授权。
- 凭据放仓库外（`~/.gradle/gradle.properties` 或 CI secrets），**不要提交** token。
- `Could not find` / `401` / `403` 几乎总是：Packages 仓库 URL、过期/错误版本字符串、token 三者之一错误。见 [排障清单](#9排障清单)。

### 宿主环境

- 宿主 `minSdk` 必须 `>= 26`。建议 Kotlin / JVM 17。
- SDK 是 Compose-first。宿主必须开启 `buildFeatures.compose = true`，否则可能出现“依赖解析成功但崩溃 UI 符号/运行失败”。
- Material3 与 window-size-class 以 `api` 依赖对外暴露；宿主若已使用 Compose Material3，通常无需再手写一遍 Compose 依赖。
- 不要在 release minify 中加入会 strip Compose runtime 的宽泛规则。

### 必做的 3 个宿主触点

缺任何一个都算接入不完整：

1. **尽早 install**：放在 `Application.attachBaseContext`，确保未捕获异常 handler 在后期启动逻辑前生效。见 [最小集成](#最小集成3-个宿主接入点)。
2. **启动 UI 闸门**：先 `LumenCrash.loadPendingReport()` -> `LumenCrashReportScreen`，再进入正常应用内容。
3. **记录面包屑 / 可恢复失败**：关键路径调用 `recordBreadcrumb` / `record(throwable)`。

另外：

- 优先用 `LumenCrash.record(throwable)`，不要轻易手写报告构建。直接调用 `CrashReport.fromThrowable(...)` 时必须自备完整 `CrashAppInfo`。
- 未 install 时调用 `LumenCrash.store()` 会抛异常。
- continue 时要清存储（`clearPendingReport()` 或 screen 的 clear 路径）。否则下次冷启动仍会被同一份报告拦截。

### 文件分享与产品文案

- 文本分享无需宿主额外配置。文件分享需要宿主 `FileProvider` + `LumenCrashConfig.fileProviderAuthority`。未配置 authority 时仍可文本分享，UI 会显示库内 “file share unavailable” 文案。
- Provider paths 必须覆盖 SDK 分享写入使用的 cache / external-cache。见 [文件分享配置](#文件分享配置)。
- 产品向文案只通过配置注入（`reportTitle`、`reportMessage`、`shareSubject`）。不要在宿主重抄整套共享 UI 字符串。
- 作者署名不可配置、不可隐藏。尝试剥离会 fail-closed。

### 持久化假设

- 报告主路径写在应用专属**外部**目录（`getExternalFilesDir` / external cache），不是内部 private storage。
- 只要任一外部路径写成功即算保存成功。legacy private 路径仅用于迁移。
- 不要假设“清应用数据”只对应内部 `filesDir`。应调用 `LumenCrash.clearPendingReport()`，或同时清理外部存储位置。

### ProGuard / R8

- 宿主开启 minify 时，必须把 `com.chloemlla.lumen.crash.**` 当作第三方豁免面。见 [ProGuard / R8](#proguard--r8)。
- 优先依赖 AAR 自带 `consumer-rules.pro` 自动合并。若宿主剥离 consumer rules 或使用自定义 shrinker，必须把显式 keep 块写入宿主 `proguard-rules.pro`。
- 至少 keep：`CrashAuthorAttribution`、`AuthorIntegrity`、公开 API 类，以及对 `com.chloemlla.lumen.crash.**` 的 package-level keep/dontwarn。
- 混淆/裁剪作者常量或 integrity 入口会导致 install/`SecurityException`、崩溃页 blocked，或 copy/share 丢失作者 footer。
- 开启 minify 后务必验证：install 成功、pending UI 可打开、copy/share 仍含作者署名。

### 作者完整性是 fail-closed

- 校验会在 install、报告构建/加载/导出、UI 打开时执行。
- mismatch 不是软提示：会抛异常或进入阻断页。
- 这用于抬高“静默去署名”的成本；对源码级 fork 的绝对防护不在范围内。

### 职责边界

- 上传/遥测留在宿主（`onCrashSaved` / continue 钩子）。SDK 不提供崩溃后端。
- 抽离到本模块后，不要再在宿主重写一套 crash 核心克隆（如 `core/crash`、替代 `LumenCrashReportScreen` 的自定义崩溃页）。

### 稳妥生产路径

1. 自动解析最新 main 自动发版版本（`lumen-crash-vX.Y.Z-<shortSha>`）并注入依赖；**不要**硬编码钉死。
2. 通过 GitHub Packages 接入，凭据放仓库外。
3. 在 `attachBaseContext` 安装。
4. 启动时用 pending-report UI 闸门。
5. 如需文件分享，配置 FileProvider。
6. 验证 release minify 保留作者完整性与公开 API。
7. 发版前冒烟：崩溃捕获、pending UI、复制、分享。

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
