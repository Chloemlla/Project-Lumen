# Project Lumen 系统更新策略技术实现复现指南

本文档整理当前 Project Lumen Android 客户端的系统更新策略和技术实现，用于以后按同一方案复现。本文只描述现有实现路径和复现约束，不引入新的本地构建、测试或安装步骤；实际构建、测试、发布均应通过 GitHub Actions workflow 执行。

## 1. 策略目标

Project Lumen 的更新策略是“GitHub Releases 作为发布源 + 应用启动自动检查 + 设置页手动检查 + 客户端下载并交给系统安装器安装”。

核心目标：

- 让每个 Android 构建携带可比较的版本元数据。
- 让客户端只信任 HTTPS GitHub Release API 和 HTTPS 下载地址。
- 用语义版本优先判断更新，用发布时间作为兜底判断。
- 只下载带 SHA-256 校验值的 APK 资源。
- 优先匹配当前设备 ABI 的 APK，找不到时使用通用包或跳转 Release 页面。
- 不在应用内静默安装，下载完成后交给 Android 系统安装器和未知来源安装授权流程。
- 自动检查只在应用 ready 后触发一次，手动检查不受自动检查开关影响。

## 2. 相关源码位置

- 版本元数据模型：`app/src/main/java/com/projectlumen/app/core/update/UpdateModels.kt`
- 更新检测器：`app/src/main/java/com/projectlumen/app/core/update/UpdateChecker.kt`
- APK 下载与安装器：`app/src/main/java/com/projectlumen/app/core/update/UpdateInstaller.kt`
- 根 UI 接线和状态机：`app/src/main/java/com/projectlumen/app/app/ProjectLumenApp.kt`
- 更新弹窗：`app/src/main/java/com/projectlumen/app/app/ProjectLumenAboutAndDialogs.kt`
- 设置页开关入口：`app/src/main/java/com/projectlumen/app/app/ProjectLumenSettingsScreen.kt`
- 设置持久化入口：`app/src/main/java/com/projectlumen/app/app/ProjectLumenSettingsFeatureEntry.kt`
- 设置实体和迁移：`app/src/main/java/com/projectlumen/app/core/database/entities/AppSettingsEntity.kt`、`app/src/main/java/com/projectlumen/app/core/database/AppDatabase.kt`
- Android 权限与 FileProvider：`app/src/main/AndroidManifest.xml`、`app/src/main/res/xml/file_paths.xml`
- 构建期版本注入：`app/build.gradle.kts`
- Android 发布 workflow：`.github/workflows/build.yml`、`.github/workflows/release.yml`

## 3. 发布端实现

### 3.1 版本号来源

默认版本名来自 `app/application.version`。当前文件值为 `1.0.1`。

GitHub Actions 在 workflow 中读取该文件，并写入以下环境变量：

- `PROJECT_LUMEN_VERSION_NAME`
- `PROJECT_LUMEN_VERSION_CODE`
- `PROJECT_LUMEN_BUILD_TIME_UTC_MILLIS`
- `PROJECT_LUMEN_COMMIT_HASH`
- `PROJECT_LUMEN_SHORT_HASH`

tag 触发的 release workflow 会优先从 tag 名中提取版本号：

- tag 格式：`v*`
- tag 示例：`v1.0.1`
- 提取后版本名：`1.0.1`

普通分支构建使用 `app/application.version`。

### 3.2 构建期注入到 BuildConfig

`app/build.gradle.kts` 在 `defaultConfig` 中将版本元数据注入到 `BuildConfig`：

- `BuildConfig.VERSION_NAME`
- `BuildConfig.VERSION_CODE`
- `BuildConfig.BUILD_TIME_UTC_MILLIS`
- `BuildConfig.COMMIT_HASH`
- `BuildConfig.SHORT_HASH`

如果 workflow 没有传入环境变量，本地 Gradle 配置存在兜底逻辑：

- `versionName` 读取 `app/application.version`，空值时回退 `1.0.0`。
- `versionCode` 从语义版本计算，公式为 `major * 10000 + minor * 100 + patch`。
- `buildTimeUtcMillis` 使用当前时间。
- `commitHash` 和 `shortHash` 通过 `git rev-parse` 读取。

复现时应继续让 GitHub Actions 显式注入这些值，避免不同构建机器得出不可追踪的版本元数据。

### 3.3 APK 产物命名

release workflow 构建 release APK 后，将产物复制到 `release-assets`：

- 通用包：`Project-Lumen_android_${VERSION}.apk`
- ABI 包：`Project-Lumen_android_${VERSION}_${variant}.apk`

其中：

- `VERSION="${PROJECT_LUMEN_VERSION_NAME}-${PROJECT_LUMEN_SHORT_HASH}"`
- `variant` 取值：`armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64`

示例：

```text
Project-Lumen_android_1.0.1-a1b2c3d4.apk
Project-Lumen_android_1.0.1-a1b2c3d4_arm64-v8a.apk
checksums.txt
```

### 3.4 校验文件

workflow 在 `release-assets` 目录执行：

```bash
sha256sum *.apk > checksums.txt
```

`checksums.txt` 必须作为 GitHub Release asset 一起上传。客户端只会把带 SHA-256 校验值的 APK 视为可直接下载资源；缺少校验值时会回退到 Release 页面或在下载阶段报错。

### 3.5 GitHub Release

更新源固定为 GitHub Latest Release：

```text
https://api.github.com/repos/Chloemlla/Project-Lumen/releases/latest
```

客户端依赖以下 Release 字段：

- `tag_name`：远端版本标签，参与语义版本解析和展示。
- `name`：Release 标题，用于展示发布说明标题。
- `body`：Release 正文，用于展示发布说明和解析内联 SHA-256。
- `html_url`：无法直接下载时的浏览器兜底地址。
- `published_at`：发布时间，作为更新判定兜底依据。
- `assets`：APK 和 checksum 资源列表。

## 4. 客户端数据模型

`UpdateModels.kt` 定义了客户端更新领域模型。

### 4.1 BuildMetadata

`BuildMetadata.current()` 从 `BuildConfig` 读取当前安装包元数据：

- `versionName`
- `versionCode`
- `buildTimeUtcMillis`
- `commitHash`
- `shortHash`

这是本地版本基线。

### 4.2 ReleaseAsset

Release asset 包含：

- `name`
- `downloadUrl`
- `contentType`
- `sha256`

`sha256` 初始来自 checksum 解析结果。只有 APK 且能匹配到 checksum 时，才会带上该字段。

### 4.3 ReleaseInfo

ReleaseInfo 是 GitHub Latest Release 响应的客户端抽象：

- `tagName`
- `releaseName`
- `body`
- `htmlUrl`
- `publishedAtUtcMillis`
- `assets`

### 4.4 UpdateCandidate

发现更新后返回 `UpdateCandidate`：

- `currentBuild`
- `release`
- `matchedAsset`
- `matchReason`

`matchReason` 取值：

- `SEMANTIC_VERSION`：远端语义版本高于本地。
- `PUBLISHED_AT`：语义版本没有更高，但 Release 发布时间晚于本地构建时间。

## 5. 更新检测流程

检测入口为 `UpdateChecker.checkForUpdate()`。

完整流程：

1. 请求 GitHub Latest Release API。
2. 解析 Release 基本字段和 assets。
3. 从 Release body 和 checksum assets 中提取 APK SHA-256。
4. 为 APK asset 回填匹配到的 SHA-256。
5. 解析本地版本描述符。
6. 判断远端 tag 是否与本地版本和短 hash 完全一致。
7. 比较远端和本地语义版本。
8. 比较远端 `published_at` 与本地 `BUILD_TIME_UTC_MILLIS`。
9. 若存在更新，选择最佳 APK asset。
10. 返回 `UpdateCandidate`。

### 5.1 网络请求约束

检测请求使用 `HttpURLConnection`，并强制 HTTPS：

- 不是 `https` 协议时抛出 `IOException`。
- 优先通过 `ConnectivityManager.activeNetwork.openConnection()` 绑定当前网络。
- 没有 active network 时回退 `URL.openConnection()`。
- 连接和读取超时均为 6000 ms。

GitHub API 请求头：

```text
Accept: application/vnd.github+json
X-GitHub-Api-Version: 2022-11-28
User-Agent: Project-Lumen
```

checksum 文本资源请求头：

```text
Accept: text/plain, application/octet-stream
User-Agent: Project-Lumen
```

### 5.2 版本描述符解析

客户端支持以下 tag 或版本字符串形态：

- `1.0.1`
- `v1.0.1`
- `1.0.1-a1b2c3d4`
- `v1.0.1-a1b2c3d4`
- `1.0.1 (a1b2c3d4)`
- `1.0.1+metadata`

解析规则：

- 先去掉开头的 `v` 或 `V`。
- 版本主体在 `(`、`+`、`-` 之前截断。
- 语义版本只取前三段数字：`major.minor.patch`。
- 缺少 minor 或 patch 时按 0 处理。
- 短 hash 支持两种提取方式：
  - 结尾括号：`(abcdef12)`
  - 后缀：`-abcdef12` 或 `_abcdef12`
- 短 hash 长度要求 7 到 40 位十六进制字符。

### 5.3 判定规则

精确一致规则：

- 远端语义版本等于本地语义版本。
- 远端短 hash 和本地短 hash 均非空。
- 两者短 hash 忽略大小写相等。
- 满足时直接判定“无更新”。

更新规则：

- `remoteSemanticVersion > localSemanticVersion` 时有更新。
- 或 `remotePublishedAt > localBuildTime + 90000ms` 时有更新。

`90000ms` 是发布时间容差，用来避免 GitHub Release 发布时间和构建时间之间的微小偏差造成误报。

匹配原因：

- 语义版本更高时返回 `SEMANTIC_VERSION`。
- 只有发布时间更晚时返回 `PUBLISHED_AT`。

检测失败策略：

- `checkForUpdate()` 内部会对不合法响应抛出异常。
- 根 UI 手动检查会展示错误弹窗。
- 自动检查失败不打断用户流程，只将本次自动检查标记为已尝试。

## 6. SHA-256 解析策略

客户端从两个来源收集 checksum：

- Release body 中的任意 SHA-256 行。
- 名称包含 `checksum` 或 `sha256` 的非 APK asset。

解析规则：

- 扫描每一行。
- 匹配 64 位十六进制 SHA-256。
- 在 hash 前后查找 APK 文件名。
- APK 文件名正则支持字母、数字、`.`、`_`、`+`、`-`，并以 `.apk` 结尾。
- 以 APK 文件名小写后的 basename 作为 key。
- 同名 key 后写入的 checksum 覆盖先写入值。

Release body 示例：

```text
8b4f...64位hash...c19a  Project-Lumen_android_1.0.1-a1b2c3d4_arm64-v8a.apk
```

`checksums.txt` 示例：

```text
8b4f...64位hash...c19a  Project-Lumen_android_1.0.1-a1b2c3d4_arm64-v8a.apk
```

## 7. APK asset 选择策略

`UpdateChecker.selectBestAsset()` 只考虑：

- 文件名以 `.apk` 结尾。
- `sha256` 非空。

选择步骤：

1. 读取 `Build.SUPPORTED_ABIS`。
2. 将 ABI token 归一化为小写，并将 `-`、`.` 替换为 `_`。
3. 将 asset 文件名归一化为小写，并将 `-`、`.`、空格替换为 `_`。
4. 如果 asset 文件名包含某个设备 ABI token，则按该 ABI 在 `SUPPORTED_ABIS` 中的顺序评分。
5. 如果没有 ABI 命中，但文件名包含 `universal`，评分为 `10000`。
6. 如果文件名包含 `all`，评分为 `10001`。
7. 其他 APK 评分为 `20000`。
8. 选择评分最低者；评分相同时选择文件名更短者。

当前 workflow 生成通用包文件名没有包含 `universal` 或 `all`，但它仍会作为 `20000` 分兜底。若存在 ABI 专用包，ABI 包会优先于通用包。

更新弹窗中还会调用 UI 层兜底选择：

- `candidate.matchedAsset`
- 若为空，再调用 `chooseFallbackAsset(release.assets)`
- 若仍为空，则打开 Release 页面

## 8. APK 下载与校验

下载入口为 `UpdateInstaller.downloadApk()`。

下载前置条件：

- `ReleaseAsset.sha256` 必须非空。
- 下载 URL 必须是 HTTPS。

下载实现：

- 在 `Dispatchers.IO` 执行。
- 请求头 `Accept: application/octet-stream`。
- 请求头 `User-Agent: Project-Lumen`。
- 连接和读取超时均为 30000 ms。
- 目标文件写入 `context.cacheDir`。
- 文件名来自 asset basename，并将非 `A-Za-z0-9._-` 字符替换为 `_`。
- 下载过程中通过回调返回 `downloadedBytes` 和 `totalBytes`。

校验实现：

- 下载完成后读取缓存 APK 文件计算 SHA-256。
- 与 `asset.sha256` 忽略大小写比较。
- 不一致时删除已下载文件，并抛出 `IOException`。
- 一致时返回缓存文件。

## 9. 安装授权与系统安装器

Project Lumen 不做静默安装。

安装流程：

1. 调用 `PackageManager.canRequestPackageInstalls()` 判断当前应用是否允许请求安装未知来源 APK。
2. 如果允许，调用 `installApk(file)`。
3. 如果不允许，展示安装授权弹窗。
4. 用户点击授权按钮后打开 `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`，URI 为 `package:${context.packageName}`。
5. 用户授权后再次点击安装按钮，打开 Android 系统安装器。

安装 intent：

```text
Intent.ACTION_VIEW
MIME: application/vnd.android.package-archive
FLAG_ACTIVITY_NEW_TASK
FLAG_GRANT_READ_URI_PERMISSION
CATEGORY_DEFAULT
```

APK 文件通过 FileProvider 暴露：

- authority：`${applicationId}.fileprovider`
- paths：`<cache-path name="cache" path="." />`

Manifest 必需权限：

- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.REQUEST_INSTALL_PACKAGES`

应用层配置：

- `android:usesCleartextTraffic="false"`
- `android:networkSecurityConfig="@xml/network_security_config"`
- FileProvider `exported=false`
- FileProvider `grantUriPermissions=true`

## 10. UI 状态机

根组件中定义 `UpdateDialogState`：

- `Hidden`
- `Checking`
- `UpdateAvailable(candidate)`
- `NoUpdate`
- `Downloading(candidate, asset)`
- `InstallAuthorization(candidate, file)`
- `Error(message)`

### 10.1 手动检查

设置页入口：

- 文案：`about_check_updates`
- 回调：`onManualUpdateCheck`
- 行为：`triggerUpdateCheck(manual = true)`

手动检查行为：

- 立即显示 `Checking` 弹窗。
- 有更新显示 `UpdateAvailable`。
- 无更新显示 `NoUpdate`。
- 失败显示 `Error`。

### 10.2 自动检查

自动检查由 `LaunchedEffect(uiState.isReady, uiState.settings.autoUpdateCheckEnabled, autoCheckStarted)` 触发。

触发条件：

- `uiState.isReady == true`
- `settings.autoUpdateCheckEnabled == true`
- `autoCheckStarted == false`
- 当前不处于检查状态

自动检查行为：

- 不展示 `Checking` 弹窗。
- 有更新时展示 `UpdateAvailable`。
- 无更新时保持 `Hidden`。
- 失败时保持 `Hidden`。
- 成功或失败后都将 `autoCheckStarted = true`，避免同一进程反复自动检查。

注意：当前实现没有“每天一次”或“后台周期检查”，只有应用 ready 后的一次自动检查。

### 10.3 下载进度

下载状态使用：

- `downloadingUpdate`
- `downloadProgressBytes`
- `downloadProgressTotalBytes`

若服务端返回有效 `Content-Length`，弹窗显示百分比；否则显示不确定进度文案。

### 10.4 外部链接兜底

当没有可下载 APK asset 时，更新弹窗按钮打开 Release 页面。

实际行为不是直接启动外部浏览器，而是先弹出外部链接确认，再通过 `viewModel.navigateWebPage(url)` 进入应用内 WebView 流程。

## 11. 持久化配置

自动检查开关字段：

```kotlin
autoUpdateCheckEnabled: Boolean = true
```

持久化位置：

- Room 表：`app_settings`
- 实体：`AppSettingsEntity`
- 默认值：`true`
- 迁移：`MIGRATION_1_2` 添加 `autoUpdateCheckEnabled INTEGER NOT NULL DEFAULT 1`

设置页开关调用：

```kotlin
viewModel.setAutoUpdateCheckEnabled(enabled)
```

最终由 `SettingsRepository.update()` 写入数据库并同步到 preferences。

影响范围：

- 关闭后只影响自动检查。
- 手动检查仍可使用。
- 当前实现没有“不再提醒某个版本”的 per-release 记忆。

## 12. 复现步骤

复现该更新策略时，按以下顺序实现。

### 12.1 发布端

1. 在应用模块保留 `application.version` 作为默认版本名。
2. 在 GitHub Actions 中读取版本名、run number、UTC 构建时间、提交 hash。
3. 将这些值写入环境变量。
4. 在 Gradle `defaultConfig` 中注入 `BuildConfig` 字段。
5. 启用 ABI splits，并生成通用 APK。
6. release workflow 构建 release APK。
7. 将通用 APK 和 ABI APK 按固定命名复制到 `release-assets`。
8. 对所有 APK 生成 `checksums.txt`。
9. 上传 APK 和 `checksums.txt` 到 GitHub Release。
10. 确保 GitHub Release 被标记为 latest。

### 12.2 客户端检测

1. 新建 `BuildMetadata`，从 `BuildConfig` 读取当前版本基线。
2. 新建 `ReleaseInfo`、`ReleaseAsset`、`UpdateCandidate` 数据模型。
3. 使用 HTTPS 请求 GitHub `/releases/latest`。
4. 解析 Release 基本信息和 assets。
5. 从 Release body 和 checksum assets 提取 SHA-256。
6. 将 APK 和 SHA-256 按文件名绑定。
7. 解析本地版本和远端 tag 的语义版本、短 hash。
8. 先做精确版本和短 hash 一致判断。
9. 再做语义版本比较。
10. 最后用 `published_at > buildTime + 90000ms` 做兜底判断。
11. 选择当前设备最合适的 APK asset。
12. 返回候选更新。

### 12.3 客户端下载和安装

1. 下载前要求 `sha256` 存在。
2. 下载地址只允许 HTTPS。
3. APK 写入 `cacheDir`。
4. 下载完成后计算 SHA-256。
5. 校验失败删除缓存文件并报错。
6. 校验成功后检查未知来源安装授权。
7. 无权限时打开应用级未知来源设置。
8. 有权限时通过 FileProvider URI 调用系统安装器。

### 12.4 UI 接线

1. 在根组件保存 `UpdateDialogState`。
2. 设置页手动入口调用 `triggerUpdateCheck(manual = true)`。
3. 根组件 `LaunchedEffect` 根据 ready 状态和开关触发自动检查。
4. `Checking`、`NoUpdate`、`Error`、`UpdateAvailable`、`Downloading`、`InstallAuthorization` 分别显示对应弹窗。
5. 下载按钮优先下载匹配 asset，找不到时打开 Release 页面。
6. 下载中展示进度。
7. 安装权限缺失时显示授权弹窗。

## 13. 必须保持的约束

- Release API 和 APK 下载必须使用 HTTPS。
- APK 直接下载必须要求 SHA-256。
- SHA-256 不匹配必须删除下载文件。
- 自动检查失败不能阻塞主界面。
- 手动检查失败必须反馈用户。
- 自动检查开关不能禁用手动检查。
- 不能依赖登录态或用户账号态。
- 不要只用 `versionName` 判断更新；应保留语义版本 + 发布时间兜底。
- 不要静默安装 APK；必须交给系统安装器。
- 不要把本地构建时间作为发布版本号；构建时间只用于更新兜底判断。

## 14. 版本和 tag 约定

推荐 tag：

```text
v<major>.<minor>.<patch>
```

自动发布 workflow 当前会生成包含短 hash 的 tag：

```text
v${PROJECT_LUMEN_VERSION_NAME}-${PROJECT_LUMEN_SHORT_HASH}
```

客户端能够解析这两种 tag。为了减少误判，建议 Release tag、APK 文件名、Release 标题都包含同一组 `versionName-shortHash`。

## 15. 安全边界

当前策略提供以下安全保障：

- HTTPS 强制。
- GitHub Release API 固定源。
- SHA-256 完整性校验。
- 下载文件名净化。
- FileProvider 只开放 cache 路径且不导出 provider。
- 安装由 Android 系统完成。

当前策略不提供以下能力：

- APK 签名证书在线校验。
- Release 签名或 checksum 签名校验。
- 灰度发布。
- 增量更新。
- 后台静默安装。
- 按渠道分发。
- 每个版本只提醒一次。

如以后需要提高供应链安全性，应在 checksum 之外增加签名校验，例如 minisign、cosign、GPG detached signature，或在客户端内置 release signing public key。

## 16. GitHub Actions 验证策略

根据仓库约束，实际构建和测试命令必须在 GitHub workflow 中执行。

当前相关 workflow：

- `.github/workflows/build.yml`
  - Android unit tests
  - Android lint
  - release APK build
  - release assets and checksums
  - non-PR 自动 GitHub Release
  - Rust backend verification
- `.github/workflows/release.yml`
  - tag 触发 release APK build
  - Android unit tests
  - Android lint
  - release assets and checksums
  - GitHub Release creation

复现或修改更新策略后，应通过推送触发 workflow 来验证，不应在本地执行 Gradle、Cargo、安装包构建或安装命令。

## 17. 常见失败场景和处理结果

- GitHub API 非 2xx：手动检查显示错误，自动检查静默失败。
- `published_at` 缺失或非法：检测失败。
- Release 没有 APK asset：提示有更新，但下载按钮回退到 Release 页面。
- APK asset 没有 SHA-256：不会作为首选直接下载资源。
- checksum asset 下载失败：忽略该 checksum 来源，继续使用其他来源。
- APK 下载非 2xx：显示下载失败。
- APK SHA-256 不匹配：删除缓存 APK，显示下载失败。
- 未授权未知来源安装：显示授权弹窗并跳转系统设置。
- 系统安装器无法打开：显示错误弹窗。

## 18. 最小复现清单

复现完成后确认以下清单：

- `BuildConfig` 包含版本名、版本号、构建时间、完整 hash、短 hash。
- Latest Release asset 中至少有一个 APK 和 `checksums.txt`。
- APK 文件名能表达平台、版本和可选 ABI。
- `checksums.txt` 中的 APK 文件名与 Release asset 文件名完全一致。
- 客户端检测请求使用 `https://api.github.com/repos/<owner>/<repo>/releases/latest`。
- 客户端能解析 `v1.2.3` 和 `v1.2.3-abcdef12`。
- 远端语义版本更高时会提示更新。
- 远端发布时间晚于本地构建时间 90 秒以上时会提示更新。
- 同版本同短 hash 不提示更新。
- 缺少 SHA-256 时不直接下载安装 APK。
- 下载后必须校验 SHA-256。
- 无未知来源安装权限时先打开系统授权页。
- 自动检查开关默认开启，关闭后只影响自动检查。
