# Vivo Adaptation Doc Review Package

- Generated at: `2026-07-16 13:29:45Z`
- Repo root: `F:\Repositories\GitHub\Project-Lumen`
- Output dir: `F:\Repositories\GitHub\Project-Lumen\docs\vivo-adaptation`

## Documents

### 797 — Android 15 Beta3 开发者适配文档

- Page: https://dev.vivo.com.cn/documentCenter/doc/797
- API: `https://dev.vivo.com.cn/webapi/doc/info?id=797`
- Breadcrumbs: vivo系统适配指南,Android适配,Android 15 开发者适配文档
- Version code: 3
- Update time (UTC): 2024-07-24T06:22:49+00:00
- Package: `docs/vivo-adaptation/797-android-15-beta3`

Headings:

- 1.1 Stable configuration
- 1.2 dataSync/mediaProcessing foreground service timeout behavior
- 1.3 OpenJdk变更
- 1.4 Stopped状态的变化
- 1.5 Edge-to-edge 模式
- 1.6 使用Spatializer替代Virtualizer
- 1.7 AndroidManifest 字符串属性长度和子标签数量限制
- 1.8 TargetSdk安装限制
- 2.1 弱光增强
- 2.2 HDR 余量控制
- 2.3 封面屏幕支持
- 3.1 对 PdfRenderer API 做重大改进
- 3.2 ApplicationStartInfo API
- 3.3 详细的应用大小信息
- 3.4 SQLite 数据库改进
- 4.1 敏感通知保护服务
- 4.2 屏幕录制检测
- 4.3 扩展的 IntentFilter 功能
- 4.4 Health Connect
- 4.5 端到端加密的密钥管理
- 4.6 使用 fs-verity 保护文件

## Repo keyword scan

This is a helper scan, not a full compliance verdict. Use it to jump into likely code paths when reviewing the doc.

### `scheduleAtFixedRate` (8 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:29:### 3. scheduleAtFixedRate backlog behavior (targetSdk 36+)`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:33:- No `ScheduledExecutorService.scheduleAtFixedRate` usage found.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:109:- 定时：`scheduleAtFixedRate`、`ScheduledExecutorService``
- `docs/vivo-adaptation/REVIEW.md:55:### `scheduleAtFixedRate` (8 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:57:- `docs/ANDROID_16_VIVO_ADAPTATION.md:29:### 3. scheduleAtFixedRate backlog behavior (targetSdk 36+)``
- `docs/vivo-adaptation/REVIEW.md:58:- `docs/ANDROID_16_VIVO_ADAPTATION.md:33:- No `ScheduledExecutorService.scheduleAtFixedRate` usage found.``
- `docs/vivo-adaptation/REVIEW.md:59:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:109:- 定时：`scheduleAtFixedRate`、`ScheduledExecutorService```
- `docs/vivo-adaptation/REVIEW.md:60:- `docs/vivo-adaptation/REVIEW.md:20:- 1.1 scheduleAtFixedRate方法适配``

### `ScheduledExecutorService` (7 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:33:- No `ScheduledExecutorService.scheduleAtFixedRate` usage found.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:109:- 定时：`scheduleAtFixedRate`、`ScheduledExecutorService``
- `docs/vivo-adaptation/REVIEW.md:58:- `docs/ANDROID_16_VIVO_ADAPTATION.md:33:- No `ScheduledExecutorService.scheduleAtFixedRate` usage found.``
- `docs/vivo-adaptation/REVIEW.md:59:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:109:- 定时：`scheduleAtFixedRate`、`ScheduledExecutorService```
- `docs/vivo-adaptation/REVIEW.md:63:- `docs/vivo-adaptation/REVIEW.md:56:- `docs/ANDROID_16_VIVO_ADAPTATION.md:30:- No `ScheduledExecutorService.scheduleAtFixedRate` usage found.```
- `docs/vivo-adaptation/REVIEW.md:64:- `docs/vivo-adaptation/REVIEW.md:57:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:109:- 定时：`scheduleAtFixedRate`、`ScheduledExecutorService````
- `docs/vivo-adaptation/REVIEW.md:66:### `ScheduledExecutorService` (7 hit lines, showing up to 8)`

### `elegantTextHeight` (7 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:37:### 4. elegantTextHeight deprecated`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:42:- No `elegantTextHeight=false` dependency.`
- `docs/vivo-adaptation/REVIEW.md:76:### `elegantTextHeight` (7 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:78:- `docs/ANDROID_16_VIVO_ADAPTATION.md:37:### 4. elegantTextHeight deprecated``
- `docs/vivo-adaptation/REVIEW.md:79:- `docs/ANDROID_16_VIVO_ADAPTATION.md:42:- No `elegantTextHeight=false` dependency.``
- `docs/vivo-adaptation/REVIEW.md:80:- `docs/vivo-adaptation/REVIEW.md:74:### `elegantTextHeight` (5 hit lines, showing up to 8)``
- `docs/vivo-adaptation/REVIEW.md:81:- `docs/vivo-adaptation/REVIEW.md:76:- `docs/ANDROID_16_VIVO_ADAPTATION.md:33:### 4. elegantTextHeight deprecated```

### `enableOnBackInvokedCallback` (8 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:25:- Application sets `android:enableOnBackInvokedCallback="true"`.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:108:- 返回：`enableOnBackInvokedCallback`、`BackHandler`、`onBackPressed``
- `app/src/main/AndroidManifest.xml:46:android:enableOnBackInvokedCallback="true"`
- `docs/vivo-adaptation/REVIEW.md:86:### `enableOnBackInvokedCallback` (8 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:88:- `docs/ANDROID_16_VIVO_ADAPTATION.md:25:- Application sets `android:enableOnBackInvokedCallback="true"`.``
- `docs/vivo-adaptation/REVIEW.md:89:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:108:- 返回：`enableOnBackInvokedCallback`、`BackHandler`、`onBackPressed```
- `docs/vivo-adaptation/REVIEW.md:90:- `app/src/main/AndroidManifest.xml:46:android:enableOnBackInvokedCallback="true"``
- `docs/vivo-adaptation/REVIEW.md:91:- `docs/vivo-adaptation/REVIEW.md:82:### `enableOnBackInvokedCallback` (8 hit lines, showing up to 8)``

### `BackHandler` (26 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:26:- Navigation interception uses Compose `BackHandler` (Activity `OnBackPressedDispatcher`), not `onBackPressed()`.`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:27:- Secondary destinations and WebView dismiss use `BackHandler`.`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:104:- Predictive back: secondary pages animate back through `BackHandler` / NavController.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:96:用命中行快速跳到 Manifest、BackHandler、Alarm、Share Intent、网络配置等位置。`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:108:- 返回：`enableOnBackInvokedCallback`、`BackHandler`、`onBackPressed``
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenAboutAndDialogs.kt:23:import androidx.activity.compose.BackHandler`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenApp.kt:6:import androidx.activity.compose.BackHandler`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenApp.kt:345:BackHandler(enabled = !currentDestination.showInBottomNav) {`

### `onBackPressed` (8 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:22:Predictive back system animations are enabled by default; `onBackPressed` / raw KEYCODE_BACK paths are obsolete.`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:26:- Navigation interception uses Compose `BackHandler` (Activity `OnBackPressedDispatcher`), not `onBackPressed()`.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:108:- 返回：`enableOnBackInvokedCallback`、`BackHandler`、`onBackPressed``
- `docs/vivo-adaptation/REVIEW.md:89:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:108:- 返回：`enableOnBackInvokedCallback`、`BackHandler`、`onBackPressed```
- `docs/vivo-adaptation/REVIEW.md:93:- `docs/vivo-adaptation/REVIEW.md:85:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:108:- 返回：`enableOnBackInvokedCallback`、`BackHandler`、`onBackPressed````
- `docs/vivo-adaptation/REVIEW.md:99:- `docs/ANDROID_16_VIVO_ADAPTATION.md:26:- Navigation interception uses Compose `BackHandler` (Activity `OnBackPressedDispatcher`), not `onBackPressed()`.``
- `docs/vivo-adaptation/REVIEW.md:103:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:108:- 返回：`enableOnBackInvokedCallback`、`BackHandler`、`onBackPressed```
- `docs/vivo-adaptation/REVIEW.md:108:### `onBackPressed` (8 hit lines, showing up to 8)`

### `OnBackPressed` (6 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:26:- Navigation interception uses Compose `BackHandler` (Activity `OnBackPressedDispatcher`), not `onBackPressed()`.`
- `docs/vivo-adaptation/REVIEW.md:99:- `docs/ANDROID_16_VIVO_ADAPTATION.md:26:- Navigation interception uses Compose `BackHandler` (Activity `OnBackPressedDispatcher`), not `onBackPressed()`.``
- `docs/vivo-adaptation/REVIEW.md:111:- `docs/ANDROID_16_VIVO_ADAPTATION.md:26:- Navigation interception uses Compose `BackHandler` (Activity `OnBackPressedDispatcher`), not `onBackPressed()`.``
- `docs/vivo-adaptation/REVIEW.md:116:- `docs/vivo-adaptation/REVIEW.md:95:- `docs/ANDROID_16_VIVO_ADAPTATION.md:23:- Navigation interception uses Compose `BackHandler` (Activity `OnBackPressedDispa`
- `docs/vivo-adaptation/REVIEW.md:119:### `OnBackPressed` (6 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:121:- `docs/ANDROID_16_VIVO_ADAPTATION.md:26:- Navigation interception uses Compose `BackHandler` (Activity `OnBackPressedDispatcher`), not `onBackPressed()`.``

### `predictive` (3 hit lines, showing up to 8)

- `.trellis/tasks/07-16-vivo-doc-832-android16-refresh/prd.md:8:- Revalidated adaptive layout / predictive back / jobs / health / LAN / ART items`
- `docs/vivo-adaptation/REVIEW.md:128:### `predictive` (1 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:130:- `.trellis/tasks/07-16-vivo-doc-832-android16-refresh/prd.md:8:- Revalidated adaptive layout / predictive back / jobs / health / LAN / ART items``

### `screenOrientation` (6 hit lines, showing up to 8)

- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:107:- 大屏：`screenOrientation`、`resizeableActivity`、`setRequestedOrientation``
- `docs/vivo-adaptation/REVIEW.md:132:### `screenOrientation` (6 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:134:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:107:- 大屏：`screenOrientation`、`resizeableActivity`、`setRequestedOrientation```
- `docs/vivo-adaptation/REVIEW.md:135:- `docs/vivo-adaptation/REVIEW.md:123:### `screenOrientation` (5 hit lines, showing up to 8)``
- `docs/vivo-adaptation/REVIEW.md:136:- `docs/vivo-adaptation/REVIEW.md:125:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:107:- 大屏：`screenOrientation`、`resizeableActivity`、`setRequestedOrientation````
- `docs/vivo-adaptation/REVIEW.md:137:- `docs/vivo-adaptation/REVIEW.md:126:- `docs/vivo-adaptation/REVIEW.md:138:### `screenOrientation` (1 hit lines, showing up to 8)```

### `resizeableActivity` (12 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:16:- `resizeableActivity="true"` on primary activities.`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:32:- Keep `resizeableActivity="true"`.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:107:- 大屏：`screenOrientation`、`resizeableActivity`、`setRequestedOrientation``
- `app/src/main/AndroidManifest.xml:61:android:resizeableActivity="true"`
- `app/src/main/AndroidManifest.xml:77:android:resizeableActivity="true"`
- `app/src/main/AndroidManifest.xml:89:android:resizeableActivity="true"`
- `app/src/main/AndroidManifest.xml:101:android:resizeableActivity="true"`
- `docs/vivo-adaptation/REVIEW.md:134:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:107:- 大屏：`screenOrientation`、`resizeableActivity`、`setRequestedOrientation```

### `setRequestedOrientation` (6 hit lines, showing up to 8)

- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:107:- 大屏：`screenOrientation`、`resizeableActivity`、`setRequestedOrientation``
- `docs/vivo-adaptation/REVIEW.md:134:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:107:- 大屏：`screenOrientation`、`resizeableActivity`、`setRequestedOrientation```
- `docs/vivo-adaptation/REVIEW.md:136:- `docs/vivo-adaptation/REVIEW.md:125:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:107:- 大屏：`screenOrientation`、`resizeableActivity`、`setRequestedOrientation````
- `docs/vivo-adaptation/REVIEW.md:138:- `docs/vivo-adaptation/REVIEW.md:127:- `docs/vivo-adaptation/REVIEW.md:140:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:105:- 大屏：`screenOrientation`、`resizeableActi`
- `docs/vivo-adaptation/REVIEW.md:139:- `docs/vivo-adaptation/REVIEW.md:128:- `docs/vivo-adaptation/REVIEW.md:146:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:105:- 大屏：`screenOrientation`、`resizeableActi`
- `docs/vivo-adaptation/REVIEW.md:145:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:107:- 大屏：`screenOrientation`、`resizeableActivity`、`setRequestedOrientation```

### `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` (3 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:19:- No `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` opt-out; we adapt instead of disabling.`
- `docs/vivo-adaptation/REVIEW.md:161:### `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` (1 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:163:- `docs/ANDROID_16_VIVO_ADAPTATION.md:19:- No `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` opt-out; we adapt instead of disabling.``

### `ACCESS_LOCAL_NETWORK` (9 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:56:- No `NEARBY_WIFI_DEVICES` / `ACCESS_LOCAL_NETWORK` declaration unless a future feature needs LAN.`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:45:`ACCESS_LOCAL_NETWORK` becomes required for LAN/mDNS access when targetSdk >= 37.`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:49:- No `ACCESS_LOCAL_NETWORK` declaration unless a future feature needs LAN/mDNS.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:111:- 网络：`usesCleartextTraffic`、`networkSecurityConfig`、`ACCESS_LOCAL_NETWORK``
- `docs/vivo-adaptation/REVIEW.md:165:### `ACCESS_LOCAL_NETWORK` (9 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:167:- `docs/ANDROID_16_VIVO_ADAPTATION.md:56:- No `NEARBY_WIFI_DEVICES` / `ACCESS_LOCAL_NETWORK` declaration unless a future feature needs LAN.``
- `docs/vivo-adaptation/REVIEW.md:168:- `docs/ANDROID_17_VIVO_ADAPTATION.md:45:`ACCESS_LOCAL_NETWORK` becomes required for LAN/mDNS access when targetSdk >= 37.``
- `docs/vivo-adaptation/REVIEW.md:169:- `docs/ANDROID_17_VIVO_ADAPTATION.md:49:- No `ACCESS_LOCAL_NETWORK` declaration unless a future feature needs LAN/mDNS.``

### `NEARBY_WIFI_DEVICES` (6 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:56:- No `NEARBY_WIFI_DEVICES` / `ACCESS_LOCAL_NETWORK` declaration unless a future feature needs LAN.`
- `docs/vivo-adaptation/REVIEW.md:167:- `docs/ANDROID_16_VIVO_ADAPTATION.md:56:- No `NEARBY_WIFI_DEVICES` / `ACCESS_LOCAL_NETWORK` declaration unless a future feature needs LAN.``
- `docs/vivo-adaptation/REVIEW.md:172:- `docs/vivo-adaptation/REVIEW.md:152:- `docs/ANDROID_16_VIVO_ADAPTATION.md:52:- No `NEARBY_WIFI_DEVICES` / `ACCESS_LOCAL_NETWORK` declaration unless a future f`
- `docs/vivo-adaptation/REVIEW.md:176:### `NEARBY_WIFI_DEVICES` (6 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:178:- `docs/ANDROID_16_VIVO_ADAPTATION.md:56:- No `NEARBY_WIFI_DEVICES` / `ACCESS_LOCAL_NETWORK` declaration unless a future feature needs LAN.``
- `docs/vivo-adaptation/REVIEW.md:179:- `docs/vivo-adaptation/REVIEW.md:152:- `docs/ANDROID_16_VIVO_ADAPTATION.md:52:- No `NEARBY_WIFI_DEVICES` / `ACCESS_LOCAL_NETWORK` declaration unless a future f`

### `BODY_SENSORS` (7 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:45:Heart-rate and related APIs move from `BODY_SENSORS` to `android.permission.health.*`.`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:48:- App does not use heart-rate / BODY_SENSORS APIs.`
- `docs/vivo-adaptation/REVIEW.md:185:### `BODY_SENSORS` (7 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:187:- `docs/ANDROID_16_VIVO_ADAPTATION.md:45:Heart-rate and related APIs move from `BODY_SENSORS` to `android.permission.health.*`.``
- `docs/vivo-adaptation/REVIEW.md:188:- `docs/ANDROID_16_VIVO_ADAPTATION.md:48:- App does not use heart-rate / BODY_SENSORS APIs.``
- `docs/vivo-adaptation/REVIEW.md:189:- `docs/vivo-adaptation/REVIEW.md:168:### `BODY_SENSORS` (5 hit lines, showing up to 8)``
- `docs/vivo-adaptation/REVIEW.md:190:- `docs/vivo-adaptation/REVIEW.md:170:- `docs/ANDROID_16_VIVO_ADAPTATION.md:41:Heart-rate and related APIs move from `BODY_SENSORS` to `android.permission.healt`

### `usesCleartextTraffic` (11 hit lines, showing up to 8)

- `docs/ANDROID_17_VIVO_ADAPTATION.md:37:`usesCleartextTraffic` is on a deprecation path; prefer `networkSecurityConfig`.`
- `docs/SYSTEM_UPDATE_STRATEGY_IMPLEMENTATION.md:378:- `android:usesCleartextTraffic="false"``
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:111:- 网络：`usesCleartextTraffic`、`networkSecurityConfig`、`ACCESS_LOCAL_NETWORK``
- `app/src/main/AndroidManifest.xml:55:android:usesCleartextTraffic="false"`
- `app/src/main/res/xml/network_security_config.xml:4:- Prefer networkSecurityConfig over android:usesCleartextTraffic.`
- `app/src/main/res/xml/network_security_config.xml:5:- usesCleartextTraffic is planned for deprecation; cleartext must be domain-scoped here if ever needed.`
- `docs/vivo-adaptation/REVIEW.md:170:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:111:- 网络：`usesCleartextTraffic`、`networkSecurityConfig`、`ACCESS_LOCAL_NETWORK```
- `docs/vivo-adaptation/REVIEW.md:195:### `usesCleartextTraffic` (11 hit lines, showing up to 8)`

### `networkSecurityConfig` (10 hit lines, showing up to 8)

- `docs/ANDROID_17_VIVO_ADAPTATION.md:37:`usesCleartextTraffic` is on a deprecation path; prefer `networkSecurityConfig`.`
- `docs/SYSTEM_UPDATE_STRATEGY_IMPLEMENTATION.md:379:- `android:networkSecurityConfig="@xml/network_security_config"``
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:111:- 网络：`usesCleartextTraffic`、`networkSecurityConfig`、`ACCESS_LOCAL_NETWORK``
- `app/src/main/AndroidManifest.xml:51:android:networkSecurityConfig="@xml/network_security_config"`
- `app/src/main/res/xml/network_security_config.xml:4:- Prefer networkSecurityConfig over android:usesCleartextTraffic.`
- `docs/vivo-adaptation/REVIEW.md:170:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:111:- 网络：`usesCleartextTraffic`、`networkSecurityConfig`、`ACCESS_LOCAL_NETWORK```
- `docs/vivo-adaptation/REVIEW.md:197:- `docs/ANDROID_17_VIVO_ADAPTATION.md:37:`usesCleartextTraffic` is on a deprecation path; prefer `networkSecurityConfig`.``
- `docs/vivo-adaptation/REVIEW.md:199:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:111:- 网络：`usesCleartextTraffic`、`networkSecurityConfig`、`ACCESS_LOCAL_NETWORK```

### `FLAG_GRANT_READ_URI_PERMISSION` (16 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:66:- Share/install stream intents use explicit URI grants via `SecureShareIntents` (`ClipData` + `FLAG_GRANT_READ_URI_PERMISSION`).`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:25:- Always attach `ClipData` + `FLAG_GRANT_READ_URI_PERMISSION` for FileProvider shares and APK install views.`
- `docs/SYSTEM_UPDATE_STRATEGY_IMPLEMENTATION.md:361:FLAG_GRANT_READ_URI_PERMISSION`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:110:- 分享/URI：`ACTION_SEND`、`FLAG_GRANT_READ_URI_PERMISSION`、`FileProvider``
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenUiFormatters.kt:394:context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)`
- `app/src/main/java/com/projectlumen/app/core/share/SecureShareIntents.kt:12:* Always attach ClipData + FLAG_GRANT_READ_URI_PERMISSION so receivers can open FileProvider URIs.`
- `app/src/main/java/com/projectlumen/app/core/share/SecureShareIntents.kt:28:addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)`
- `app/src/main/java/com/projectlumen/app/core/share/SecureShareIntents.kt:32:addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)`

### `ACTION_SEND` (13 hit lines, showing up to 8)

- `docs/ANDROID_17_VIVO_ADAPTATION.md:21:Do not rely on implicit URI grants for `ACTION_SEND` / `ACTION_SEND_MULTIPLE`.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:110:- 分享/URI：`ACTION_SEND`、`FLAG_GRANT_READ_URI_PERMISSION`、`FileProvider``
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenHomeConvenienceCard.kt:138:val intent = Intent(Intent.ACTION_SEND).apply {`
- `app/src/main/java/com/projectlumen/app/core/share/SecureShareIntents.kt:11:* Vivo/Android guidance: do not rely on implicit URI grants for ACTION_SEND*.`
- `app/src/main/java/com/projectlumen/app/core/share/SecureShareIntents.kt:23:val intent = Intent(Intent.ACTION_SEND).apply {`
- `docs/vivo-adaptation/REVIEW.md:222:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:110:- 分享/URI：`ACTION_SEND`、`FLAG_GRANT_READ_URI_PERMISSION`、`FileProvider```
- `docs/vivo-adaptation/REVIEW.md:228:### `ACTION_SEND` (13 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:230:- `docs/ANDROID_17_VIVO_ADAPTATION.md:21:Do not rely on implicit URI grants for `ACTION_SEND` / `ACTION_SEND_MULTIPLE`.``

### `FileProvider` (57 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:106:- Share backup / export / install APK still open with granted FileProvider URIs.`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:25:- Always attach `ClipData` + `FLAG_GRANT_READ_URI_PERMISSION` for FileProvider shares and APK install views.`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:55:- FileProvider for shared/exported files`
- `docs/NEXT_STEPS_FEATURE_RECOMMENDATIONS.md:650:- 所有文件通过 `FileProvider` 分享，不暴露真实路径。`
- `docs/SYSTEM_UPDATE_STRATEGY_IMPLEMENTATION.md:29:- Android 权限与 FileProvider：`app/src/main/AndroidManifest.xml`、`app/src/main/res/xml/file_paths.xml``
- `docs/SYSTEM_UPDATE_STRATEGY_IMPLEMENTATION.md:365:APK 文件通过 FileProvider 暴露：`
- `docs/SYSTEM_UPDATE_STRATEGY_IMPLEMENTATION.md:380:- FileProvider `exported=false``
- `docs/SYSTEM_UPDATE_STRATEGY_IMPLEMENTATION.md:381:- FileProvider `grantUriPermissions=true``

### `mediaPlayback` (10 hit lines, showing up to 8)

- `docs/ANDROID_17_VIVO_ADAPTATION.md:12:Android 17 requires foreground-visible state or a `mediaPlayback` FGS with While-In-Use capability for sustained audio output.`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:17:- Do not introduce continuous background media playback without a `mediaPlayback` FGS.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:112:- 音频/FGS：`mediaPlayback`、`USAGE_NOTIFICATION`、`FOREGROUND_SERVICE``
- `app/src/main/java/com/projectlumen/app/core/services/AudioService.kt:18:* Android 17 tightens background audio: continuous media requires mediaPlayback FGS with WIU.`
- `app/src/main/java/com/projectlumen/app/core/services/NotificationService.kt:46:// Transient cue only; continuous media belongs to mediaPlayback FGS under Android 17.`
- `docs/vivo-adaptation/REVIEW.md:250:### `mediaPlayback` (10 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:252:- `docs/ANDROID_17_VIVO_ADAPTATION.md:12:Android 17 requires foreground-visible state or a `mediaPlayback` FGS with While-In-Use capability for sustained audio `
- `docs/vivo-adaptation/REVIEW.md:253:- `docs/ANDROID_17_VIVO_ADAPTATION.md:17:- Do not introduce continuous background media playback without a `mediaPlayback` FGS.``

### `FOREGROUND_SERVICE` (19 hit lines, showing up to 8)

- `docs/NEXT_STEPS_FEATURE_RECOMMENDATIONS.md:486:- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` / `FOREGROUND_SERVICE_CAMERA`：后台计时、相机采样、光照监测。`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:112:- 音频/FGS：`mediaPlayback`、`USAGE_NOTIFICATION`、`FOREGROUND_SERVICE``
- `.trellis/spec/frontend/android-foreground-services.md:7:- Trigger: Android client code starts `ProximityDetectionService`, or any future foreground service using `FOREGROUND_SERVICE_TYPE_CAMERA`.`
- `.trellis/spec/frontend/android-foreground-services.md:34:- Android rejects `FOREGROUND_SERVICE_TYPE_CAMERA` at `startForeground` -> record crash, stop service, return `START_NOT_STICKY`.`
- `.trellis/spec/frontend/android-foreground-services.md:45:- Bad: calling `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_CAMERA)` before checking `Manifest.permission.CAMERA`.`
- `.trellis/spec/frontend/android-foreground-services.md:77:ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,`
- `app/src/main/AndroidManifest.xml:23:<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />`
- `app/src/main/AndroidManifest.xml:24:<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />`

### `USAGE_NOTIFICATION` (9 hit lines, showing up to 8)

- `docs/ANDROID_17_VIVO_ADAPTATION.md:16:- `AudioService` uses `AudioAttributes.USAGE_NOTIFICATION` / `CONTENT_TYPE_SONIFICATION`.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:112:- 音频/FGS：`mediaPlayback`、`USAGE_NOTIFICATION`、`FOREGROUND_SERVICE``
- `app/src/main/java/com/projectlumen/app/core/services/AudioService.kt:19:* Lumen only needs transient notification cues, so we use USAGE_NOTIFICATION attributes and avoid`
- `app/src/main/java/com/projectlumen/app/core/services/AudioService.kt:54:.setUsage(AudioAttributes.USAGE_NOTIFICATION)`
- `docs/vivo-adaptation/REVIEW.md:254:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:112:- 音频/FGS：`mediaPlayback`、`USAGE_NOTIFICATION`、`FOREGROUND_SERVICE```
- `docs/vivo-adaptation/REVIEW.md:264:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:112:- 音频/FGS：`mediaPlayback`、`USAGE_NOTIFICATION`、`FOREGROUND_SERVICE```
- `docs/vivo-adaptation/REVIEW.md:272:### `USAGE_NOTIFICATION` (9 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:274:- `docs/ANDROID_17_VIVO_ADAPTATION.md:16:- `AudioService` uses `AudioAttributes.USAGE_NOTIFICATION` / `CONTENT_TYPE_SONIFICATION`.``

### `HiddenApiBypass` (7 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:84:Avoid HiddenApiBypass and unsupported packers that rely on ART internals.`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:87:- No HiddenApiBypass dependency.`
- `docs/vivo-adaptation/REVIEW.md:283:### `HiddenApiBypass` (7 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:285:- `docs/ANDROID_16_VIVO_ADAPTATION.md:84:Avoid HiddenApiBypass and unsupported packers that rely on ART internals.``
- `docs/vivo-adaptation/REVIEW.md:286:- `docs/ANDROID_16_VIVO_ADAPTATION.md:87:- No HiddenApiBypass dependency.``
- `docs/vivo-adaptation/REVIEW.md:287:- `docs/vivo-adaptation/REVIEW.md:264:### `HiddenApiBypass` (5 hit lines, showing up to 8)``
- `docs/vivo-adaptation/REVIEW.md:288:- `docs/vivo-adaptation/REVIEW.md:266:- `docs/ANDROID_16_VIVO_ADAPTATION.md:64:Avoid HiddenApiBypass and unsupported packers that rely on ART internals.```

### `targetSdk` (21 hit lines, showing up to 8)

- `CLAUDE.md:77:- SDK: `minSdk 26`, `compileSdk`/`targetSdk 37`, Java 21 toolchain. ABI splits (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`) + universal APK. The bundled JetBra`
- `app/build.gradle.kts:159:targetSdk = 37`
- `baselineprofile/build.gradle.kts:16:targetSdk = 37`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:7:Project Lumen currently ships with `compileSdk/targetSdk = 37` (above Android 16 / API 36). This note tracks Vivo Android 16 guidance that still applies after t`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:11:### 1. Adaptive / large-screen layout (targetSdk 36+)`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:21:### 2. Predictive back migration (targetSdk 16+)`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:29:### 3. scheduleAtFixedRate backlog behavior (targetSdk 36+)`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:69:`windowOptOutEdgeToEdgeEnforcement` is ignored for targetSdk 36+.`

### `compileSdk` (19 hit lines, showing up to 8)

- `CLAUDE.md:77:- SDK: `minSdk 26`, `compileSdk`/`targetSdk 37`, Java 21 toolchain. ABI splits (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`) + universal APK. The bundled JetBra`
- `app/build.gradle.kts:45:compileSdk = 37`
- `baselineprofile/build.gradle.kts:11:compileSdk = 37`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:7:Project Lumen currently ships with `compileSdk/targetSdk = 37` (above Android 16 / API 36). This note tracks Vivo Android 16 guidance that still applies after t`
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

