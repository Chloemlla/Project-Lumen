# Vivo Adaptation Doc Review Package

- Generated at: `2026-07-16 13:13:26Z`
- Repo root: `F:\Repositories\GitHub\Project-Lumen`
- Output dir: `F:\Repositories\GitHub\Project-Lumen\docs\vivo-adaptation`

## Documents

### 832 — Android 16 开发者适配文档

- Page: https://dev.vivo.com.cn/documentCenter/doc/832
- API: `https://dev.vivo.com.cn/webapi/doc/info?id=832`
- Breadcrumbs: vivo系统适配指南,Android适配,Android 16 开发者适配文档
- Version code: 3
- Update time (UTC): 2025-06-11T03:42:43+00:00
- Package: `docs/vivo-adaptation/832-android-16`

Headings:

- 1.1 scheduleAtFixedRate方法适配
- 1.2 优雅字体 API 已废弃并停用
- 1.3 自适应布局
- 1.4 需要迁移或停用预测性返回
- 1.5 健康与健身权限适配
- 1.6 本地网络权限
- 2.1 ART内部变更
- 2.2 增强了针对 intent 重定向攻击的安全性
- 2.3 JobScheduler配额优化
- 2.4 被废弃的空作业停止原因
- 2.5 完全弃用JobInfo#setImportantWhileForeground
- 2.6 有序广播优先级有效范围不再是全局
- 2.7 无边框设计停用退出选项
- 2.8 后台启动Activity权限变更
- 2.9 自动填充API弃用
- 2.10 用于处理键盘绑定丢失和加密更改的新 intent
- 2.11 Java API变更
- 2.11.1 java.lang.Thread类变更
- 2.11.2 java.util.Locale类变更
- 2.12 改进了无障碍功能API
- 3.1 密钥共享API
- 3.2 在 ApplicationStartInfo 中增加启动的组件类型
- 3.3 以进度为中心的通知
- 3.3.1 效果
- 3.3.2 模版样式
- 3.3.3 使用方式
- 3.4 使用用带缓存的Binder接口减少IPC调用次数
- 3.5 自适应刷新率

### 1010 — Android 17 开发者适配文档

- Page: https://dev.vivo.com.cn/documentCenter/doc/1010
- API: `https://dev.vivo.com.cn/webapi/doc/info?id=1010`
- Breadcrumbs: vivo系统适配指南,Android适配,Android 17 开发者适配文档
- Version code: 3
- Update time (UTC): 2026-04-24T08:51:25+00:00
- Package: `docs/vivo-adaptation/1010-android-17`

Headings:

- 一、概览
- 二、应用适配 -> 所有应用
- 2.1 后台音频强化
- 2.2 限制隐式 URI 授权
- 2.3 密钥库限制
- 2.4 在旋转后恢复默认 IME 可见性
- 2.5 触控板指针捕获模式变更
- 2.6 线程优先级设置范围强制校验
- 2.7 已回收 Parcel 对象的访问限制
- 2.8 文件操作模式严格校验
- 2.9 以下config变化不会触发activity relaunch
- 2.10 短信动态密码保护
- 2.11 Android developer verification
- 三、应用适配 -> targetSdk 37
- 3.1 本地网络权限保护
- 3.2 新的 MessageQueue 无锁实现
- 3.3 本地主机保护
- 3.4 更安全的本地动态代码加载
- 3.5 大屏自适应
- 3.6 Activity安全性增强
- 3.7 默认启用证书透明度
- 3.8 Parcel 数据加固
- 3.9 静态 final 字段现在不可修改
- 3.10 NPU权限声明
- 3.11 usesClearTraffic 弃用计划
- 四、新功能和api
- 4.1 复杂 IME 实体键盘输入的无障碍支持
- 4.2 新的 ProfilingManager 触发器
- 4.3 JobDebugInfo API
- 五、参考链接

## Repo keyword scan

This is a helper scan, not a full compliance verdict. Use it to jump into likely code paths when reviewing the doc.

### `scheduleAtFixedRate` (3 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:26:### 3. scheduleAtFixedRate backlog behavior (targetSdk 36+)`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:30:- No `ScheduledExecutorService.scheduleAtFixedRate` usage found.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:107:- 定时：`scheduleAtFixedRate`、`ScheduledExecutorService``

### `ScheduledExecutorService` (2 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:30:- No `ScheduledExecutorService.scheduleAtFixedRate` usage found.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:107:- 定时：`scheduleAtFixedRate`、`ScheduledExecutorService``

### `elegantTextHeight` (2 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:33:### 4. elegantTextHeight deprecated`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:38:- No `elegantTextHeight=false` dependency.`

### `enableOnBackInvokedCallback` (3 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:22:- Application sets `android:enableOnBackInvokedCallback="true"`.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:106:- 返回：`enableOnBackInvokedCallback`、`BackHandler`、`onBackPressed``
- `app/src/main/AndroidManifest.xml:46:android:enableOnBackInvokedCallback="true"`

### `BackHandler` (21 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:23:- Navigation interception uses Compose `BackHandler` (Activity `OnBackPressedDispatcher`), not `onBackPressed()`.`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:24:- Secondary destinations and WebView dismiss use `BackHandler`.`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:73:- Predictive back: secondary pages animate back through `BackHandler` / NavController.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:94:用命中行快速跳到 Manifest、BackHandler、Alarm、Share Intent、网络配置等位置。`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:106:- 返回：`enableOnBackInvokedCallback`、`BackHandler`、`onBackPressed``
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenAboutAndDialogs.kt:23:import androidx.activity.compose.BackHandler`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenApp.kt:6:import androidx.activity.compose.BackHandler`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenApp.kt:345:BackHandler(enabled = !currentDestination.showInBottomNav) {`

### `onBackPressed` (3 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:19:Predictive back system animations are enabled by default; `onBackPressed` / raw KEYCODE_BACK paths are obsolete.`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:23:- Navigation interception uses Compose `BackHandler` (Activity `OnBackPressedDispatcher`), not `onBackPressed()`.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:106:- 返回：`enableOnBackInvokedCallback`、`BackHandler`、`onBackPressed``

### `OnBackPressed` (1 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:23:- Navigation interception uses Compose `BackHandler` (Activity `OnBackPressedDispatcher`), not `onBackPressed()`.`

### `screenOrientation` (1 hit lines, showing up to 8)

- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:105:- 大屏：`screenOrientation`、`resizeableActivity`、`setRequestedOrientation``

### `resizeableActivity` (7 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:14:- `resizeableActivity="true"` on primary activities.`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:32:- Keep `resizeableActivity="true"`.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:105:- 大屏：`screenOrientation`、`resizeableActivity`、`setRequestedOrientation``
- `app/src/main/AndroidManifest.xml:60:android:resizeableActivity="true"`
- `app/src/main/AndroidManifest.xml:76:android:resizeableActivity="true"`
- `app/src/main/AndroidManifest.xml:88:android:resizeableActivity="true"`
- `app/src/main/AndroidManifest.xml:100:android:resizeableActivity="true"`

### `setRequestedOrientation` (1 hit lines, showing up to 8)

- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:105:- 大屏：`screenOrientation`、`resizeableActivity`、`setRequestedOrientation``

### `ACCESS_LOCAL_NETWORK` (4 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:52:- No `NEARBY_WIFI_DEVICES` / `ACCESS_LOCAL_NETWORK` declaration unless a future feature needs LAN.`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:45:`ACCESS_LOCAL_NETWORK` becomes required for LAN/mDNS access when targetSdk >= 37.`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:49:- No `ACCESS_LOCAL_NETWORK` declaration unless a future feature needs LAN/mDNS.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:109:- 网络：`usesCleartextTraffic`、`networkSecurityConfig`、`ACCESS_LOCAL_NETWORK``

### `NEARBY_WIFI_DEVICES` (1 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:52:- No `NEARBY_WIFI_DEVICES` / `ACCESS_LOCAL_NETWORK` declaration unless a future feature needs LAN.`

### `BODY_SENSORS` (2 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:41:Heart-rate and related APIs move from `BODY_SENSORS` to `android.permission.health.*`.`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:44:- App does not use heart-rate / BODY_SENSORS APIs.`

### `usesCleartextTraffic` (6 hit lines, showing up to 8)

- `docs/ANDROID_17_VIVO_ADAPTATION.md:37:`usesCleartextTraffic` is on a deprecation path; prefer `networkSecurityConfig`.`
- `docs/SYSTEM_UPDATE_STRATEGY_IMPLEMENTATION.md:378:- `android:usesCleartextTraffic="false"``
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:109:- 网络：`usesCleartextTraffic`、`networkSecurityConfig`、`ACCESS_LOCAL_NETWORK``
- `app/src/main/AndroidManifest.xml:54:android:usesCleartextTraffic="false"`
- `app/src/main/res/xml/network_security_config.xml:4:- Prefer networkSecurityConfig over android:usesCleartextTraffic.`
- `app/src/main/res/xml/network_security_config.xml:5:- usesCleartextTraffic is planned for deprecation; cleartext must be domain-scoped here if ever needed.`

### `networkSecurityConfig` (5 hit lines, showing up to 8)

- `docs/ANDROID_17_VIVO_ADAPTATION.md:37:`usesCleartextTraffic` is on a deprecation path; prefer `networkSecurityConfig`.`
- `docs/SYSTEM_UPDATE_STRATEGY_IMPLEMENTATION.md:379:- `android:networkSecurityConfig="@xml/network_security_config"``
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:109:- 网络：`usesCleartextTraffic`、`networkSecurityConfig`、`ACCESS_LOCAL_NETWORK``
- `app/src/main/AndroidManifest.xml:50:android:networkSecurityConfig="@xml/network_security_config"`
- `app/src/main/res/xml/network_security_config.xml:4:- Prefer networkSecurityConfig over android:usesCleartextTraffic.`

### `FLAG_GRANT_READ_URI_PERMISSION` (10 hit lines, showing up to 8)

- `docs/ANDROID_17_VIVO_ADAPTATION.md:25:- Always attach `ClipData` + `FLAG_GRANT_READ_URI_PERMISSION` for FileProvider shares and APK install views.`
- `docs/SYSTEM_UPDATE_STRATEGY_IMPLEMENTATION.md:361:FLAG_GRANT_READ_URI_PERMISSION`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:108:- 分享/URI：`ACTION_SEND`、`FLAG_GRANT_READ_URI_PERMISSION`、`FileProvider``
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenUiFormatters.kt:394:context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)`
- `app/src/main/java/com/projectlumen/app/core/share/SecureShareIntents.kt:12:* Always attach ClipData + FLAG_GRANT_READ_URI_PERMISSION so receivers can open FileProvider URIs.`
- `app/src/main/java/com/projectlumen/app/core/share/SecureShareIntents.kt:28:addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)`
- `app/src/main/java/com/projectlumen/app/core/share/SecureShareIntents.kt:32:addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)`
- `app/src/main/java/com/projectlumen/app/core/share/SecureShareIntents.kt:45:addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)`

### `ACTION_SEND` (8 hit lines, showing up to 8)

- `docs/ANDROID_17_VIVO_ADAPTATION.md:21:Do not rely on implicit URI grants for `ACTION_SEND` / `ACTION_SEND_MULTIPLE`.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:108:- 分享/URI：`ACTION_SEND`、`FLAG_GRANT_READ_URI_PERMISSION`、`FileProvider``
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenHomeConvenienceCard.kt:138:val intent = Intent(Intent.ACTION_SEND).apply {`
- `app/src/main/java/com/projectlumen/app/core/share/SecureShareIntents.kt:11:* Vivo/Android guidance: do not rely on implicit URI grants for ACTION_SEND*.`
- `app/src/main/java/com/projectlumen/app/core/share/SecureShareIntents.kt:23:val intent = Intent(Intent.ACTION_SEND).apply {`
- `lumen-crash/src/main/java/com/chloemlla/lumen/crash/ui/LumenCrashReportScreen.kt:1228:val intent = Intent(Intent.ACTION_SEND).apply {`
- `lumen-crash/src/main/java/com/chloemlla/lumen/crash/ui/LumenCrashReportScreen.kt:1264:val intent = Intent(Intent.ACTION_SEND).apply {`
- `lumen-crash/src/main/java/com/chloemlla/lumen/crash/ui/LumenCrashReportScreen.kt:1432:val intent = Intent(Intent.ACTION_SEND).apply {`

### `FileProvider` (52 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:75:- Share backup / export / install APK still open with granted FileProvider URIs.`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:25:- Always attach `ClipData` + `FLAG_GRANT_READ_URI_PERMISSION` for FileProvider shares and APK install views.`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:55:- FileProvider for shared/exported files`
- `docs/NEXT_STEPS_FEATURE_RECOMMENDATIONS.md:650:- 所有文件通过 `FileProvider` 分享，不暴露真实路径。`
- `docs/SYSTEM_UPDATE_STRATEGY_IMPLEMENTATION.md:29:- Android 权限与 FileProvider：`app/src/main/AndroidManifest.xml`、`app/src/main/res/xml/file_paths.xml``
- `docs/SYSTEM_UPDATE_STRATEGY_IMPLEMENTATION.md:365:APK 文件通过 FileProvider 暴露：`
- `docs/SYSTEM_UPDATE_STRATEGY_IMPLEMENTATION.md:380:- FileProvider `exported=false``
- `docs/SYSTEM_UPDATE_STRATEGY_IMPLEMENTATION.md:381:- FileProvider `grantUriPermissions=true``

### `mediaPlayback` (5 hit lines, showing up to 8)

- `docs/ANDROID_17_VIVO_ADAPTATION.md:12:Android 17 requires foreground-visible state or a `mediaPlayback` FGS with While-In-Use capability for sustained audio output.`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:17:- Do not introduce continuous background media playback without a `mediaPlayback` FGS.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:110:- 音频/FGS：`mediaPlayback`、`USAGE_NOTIFICATION`、`FOREGROUND_SERVICE``
- `app/src/main/java/com/projectlumen/app/core/services/AudioService.kt:18:* Android 17 tightens background audio: continuous media requires mediaPlayback FGS with WIU.`
- `app/src/main/java/com/projectlumen/app/core/services/NotificationService.kt:46:// Transient cue only; continuous media belongs to mediaPlayback FGS under Android 17.`

### `FOREGROUND_SERVICE` (14 hit lines, showing up to 8)

- `docs/NEXT_STEPS_FEATURE_RECOMMENDATIONS.md:486:- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` / `FOREGROUND_SERVICE_CAMERA`：后台计时、相机采样、光照监测。`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:110:- 音频/FGS：`mediaPlayback`、`USAGE_NOTIFICATION`、`FOREGROUND_SERVICE``
- `.trellis/spec/frontend/android-foreground-services.md:7:- Trigger: Android client code starts `ProximityDetectionService`, or any future foreground service using `FOREGROUND_SERVICE_TYPE_CAMERA`.`
- `.trellis/spec/frontend/android-foreground-services.md:34:- Android rejects `FOREGROUND_SERVICE_TYPE_CAMERA` at `startForeground` -> record crash, stop service, return `START_NOT_STICKY`.`
- `.trellis/spec/frontend/android-foreground-services.md:45:- Bad: calling `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_CAMERA)` before checking `Manifest.permission.CAMERA`.`
- `.trellis/spec/frontend/android-foreground-services.md:77:ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,`
- `app/src/main/AndroidManifest.xml:23:<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />`
- `app/src/main/AndroidManifest.xml:24:<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />`

### `USAGE_NOTIFICATION` (4 hit lines, showing up to 8)

- `docs/ANDROID_17_VIVO_ADAPTATION.md:16:- `AudioService` uses `AudioAttributes.USAGE_NOTIFICATION` / `CONTENT_TYPE_SONIFICATION`.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:110:- 音频/FGS：`mediaPlayback`、`USAGE_NOTIFICATION`、`FOREGROUND_SERVICE``
- `app/src/main/java/com/projectlumen/app/core/services/AudioService.kt:19:* Lumen only needs transient notification cues, so we use USAGE_NOTIFICATION attributes and avoid`
- `app/src/main/java/com/projectlumen/app/core/services/AudioService.kt:54:.setUsage(AudioAttributes.USAGE_NOTIFICATION)`

### `HiddenApiBypass` (2 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:64:Avoid HiddenApiBypass and unsupported packers that rely on ART internals.`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:67:- No HiddenApiBypass dependency.`

### `targetSdk` (15 hit lines, showing up to 8)

- `CLAUDE.md:77:- SDK: `minSdk 26`, `compileSdk`/`targetSdk 37`, Java 21 toolchain. ABI splits (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`) + universal APK. The bundled JetBra`
- `app/build.gradle.kts:159:targetSdk = 37`
- `baselineprofile/build.gradle.kts:16:targetSdk = 37`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:5:Project Lumen currently ships with `compileSdk/targetSdk = 37` (above Android 16 / API 36). This note tracks Vivo Android 16 guidance that still applies.`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:9:### 1. Adaptive / large-screen layout (targetSdk 36+)`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:18:### 2. Predictive back migration (targetSdk 16+)`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:26:### 3. scheduleAtFixedRate backlog behavior (targetSdk 36+)`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:7:Project Lumen currently ships with `compileSdk/targetSdk = 37`. This note tracks the high-priority items from the Vivo guide and the repo response.`

### `compileSdk` (14 hit lines, showing up to 8)

- `CLAUDE.md:77:- SDK: `minSdk 26`, `compileSdk`/`targetSdk 37`, Java 21 toolchain. ABI splits (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`) + universal APK. The bundled JetBra`
- `app/build.gradle.kts:45:compileSdk = 37`
- `baselineprofile/build.gradle.kts:11:compileSdk = 37`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:5:Project Lumen currently ships with `compileSdk/targetSdk = 37` (above Android 16 / API 36). This note tracks Vivo Android 16 guidance that still applies.`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:7:Project Lumen currently ships with `compileSdk/targetSdk = 37`. This note tracks the high-priority items from the Vivo guide and the repo response.`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:53:- `targetSdk/compileSdk = 37``
- `docs/NEXT_STEPS_FEATURE_RECOMMENDATIONS.md:19:- `compileSdk = 37`、`targetSdk = 37`、`minSdk = 26`。`
- `lumen-crash/build.gradle.kts:30:compileSdk = 37`

## Suggested review flow

1. Read `content.txt` / `headings.txt` for each package.
2. Map high/medium priority sections to app surfaces:
   - Manifest / permissions / FGS
   - Activity orientation / large-screen behavior
   - Back navigation
   - Background timers / alarms
   - Share / FileProvider / intent grants
   - Network security / cleartext
3. Update adaptation notes under `docs/ANDROID_*_VIVO_ADAPTATION.md`.
4. Implement only the gaps that apply to this product.

