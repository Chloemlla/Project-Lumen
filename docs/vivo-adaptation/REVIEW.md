# Vivo Adaptation Doc Review Package

- Generated at: `2026-07-16 13:50:59Z`
- Repo root: `F:\Repositories\GitHub\Project-Lumen`
- Output dir: `F:\Repositories\GitHub\Project-Lumen\docs\vivo-adaptation`

## Documents

### 428 — Android 11应用适配指南

- Page: https://dev.vivo.com.cn/documentCenter/doc/428
- API: `https://dev.vivo.com.cn/webapi/doc/info?id=428`
- Breadcrumbs: vivo系统适配指南,Android适配,Android 11应用适配指南
- Version code: 11
- Update time (UTC): 2023-08-01T06:18:09+00:00
- Package: `docs/vivo-adaptation/428-android-11`

Headings:

- 1.1 vivo机器升级Android 11指导
- 1.2 vivo云真机调试
- 1.3 google原生机升级Android 11
- 2.1 兼容性
- 2.1.1 分区存储
- 2.1.2 单次授权
- 2.1.3 后台位置信息访问权限获取方式
- 2.1.4 软件包可见性
- 2.1.5 新的前台服务类型
- 2.1.6 自定义视图消息框使用受限
- 2.1.7 非SDK接口名单更新
- 2.2 新的交互体验和方式
- 2.2.1 聊天气泡
- 2.2.2 新的输入法键盘过渡动画
- 2.3 硬件层面的新支持
- 2.3.1 Android 11将更好地支持各类手机屏幕，以提升用户体验
- 2.3.2 Android 11支持并发使用多个摄像头
- 2.4 增强5G支持
- 2.5 其他功能
- 2.5.1 ADB增量APK安装
- 2.5.2 应用进程退出原因
- 2.5.3 动态资源加载器

## Repo keyword scan

This is a helper scan, not a full compliance verdict. Use it to jump into likely code paths when reviewing the doc.

### `scheduleAtFixedRate` (8 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:29:### 3. scheduleAtFixedRate backlog behavior (targetSdk 36+)`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:33:- No `ScheduledExecutorService.scheduleAtFixedRate` usage found.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:114:- 定时：`scheduleAtFixedRate`、`ScheduledExecutorService``
- `docs/vivo-adaptation/REVIEW.md:48:### `scheduleAtFixedRate` (8 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:50:- `docs/ANDROID_16_VIVO_ADAPTATION.md:29:### 3. scheduleAtFixedRate backlog behavior (targetSdk 36+)``
- `docs/vivo-adaptation/REVIEW.md:51:- `docs/ANDROID_16_VIVO_ADAPTATION.md:33:- No `ScheduledExecutorService.scheduleAtFixedRate` usage found.``
- `docs/vivo-adaptation/REVIEW.md:52:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:114:- 定时：`scheduleAtFixedRate`、`ScheduledExecutorService```
- `docs/vivo-adaptation/REVIEW.md:53:- `docs/vivo-adaptation/REVIEW.md:48:### `scheduleAtFixedRate` (8 hit lines, showing up to 8)``

### `ScheduledExecutorService` (7 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:33:- No `ScheduledExecutorService.scheduleAtFixedRate` usage found.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:114:- 定时：`scheduleAtFixedRate`、`ScheduledExecutorService``
- `docs/vivo-adaptation/REVIEW.md:51:- `docs/ANDROID_16_VIVO_ADAPTATION.md:33:- No `ScheduledExecutorService.scheduleAtFixedRate` usage found.``
- `docs/vivo-adaptation/REVIEW.md:52:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:114:- 定时：`scheduleAtFixedRate`、`ScheduledExecutorService```
- `docs/vivo-adaptation/REVIEW.md:55:- `docs/vivo-adaptation/REVIEW.md:51:- `docs/ANDROID_16_VIVO_ADAPTATION.md:33:- No `ScheduledExecutorService.scheduleAtFixedRate` usage found.```
- `docs/vivo-adaptation/REVIEW.md:56:- `docs/vivo-adaptation/REVIEW.md:52:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:112:- 定时：`scheduleAtFixedRate`、`ScheduledExecutorService````
- `docs/vivo-adaptation/REVIEW.md:59:### `ScheduledExecutorService` (7 hit lines, showing up to 8)`

### `elegantTextHeight` (7 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:37:### 4. elegantTextHeight deprecated`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:42:- No `elegantTextHeight=false` dependency.`
- `docs/vivo-adaptation/REVIEW.md:69:### `elegantTextHeight` (7 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:71:- `docs/ANDROID_16_VIVO_ADAPTATION.md:37:### 4. elegantTextHeight deprecated``
- `docs/vivo-adaptation/REVIEW.md:72:- `docs/ANDROID_16_VIVO_ADAPTATION.md:42:- No `elegantTextHeight=false` dependency.``
- `docs/vivo-adaptation/REVIEW.md:73:- `docs/vivo-adaptation/REVIEW.md:69:### `elegantTextHeight` (7 hit lines, showing up to 8)``
- `docs/vivo-adaptation/REVIEW.md:74:- `docs/vivo-adaptation/REVIEW.md:71:- `docs/ANDROID_16_VIVO_ADAPTATION.md:37:### 4. elegantTextHeight deprecated```

### `enableOnBackInvokedCallback` (8 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:25:- Application sets `android:enableOnBackInvokedCallback="true"`.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:113:- 返回：`enableOnBackInvokedCallback`、`BackHandler`、`onBackPressed``
- `app/src/main/AndroidManifest.xml:46:android:enableOnBackInvokedCallback="true"`
- `docs/vivo-adaptation/REVIEW.md:79:### `enableOnBackInvokedCallback` (8 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:81:- `docs/ANDROID_16_VIVO_ADAPTATION.md:25:- Application sets `android:enableOnBackInvokedCallback="true"`.``
- `docs/vivo-adaptation/REVIEW.md:82:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:113:- 返回：`enableOnBackInvokedCallback`、`BackHandler`、`onBackPressed```
- `docs/vivo-adaptation/REVIEW.md:83:- `app/src/main/AndroidManifest.xml:46:android:enableOnBackInvokedCallback="true"``
- `docs/vivo-adaptation/REVIEW.md:84:- `docs/vivo-adaptation/REVIEW.md:79:### `enableOnBackInvokedCallback` (8 hit lines, showing up to 8)``

### `BackHandler` (26 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:26:- Navigation interception uses Compose `BackHandler` (Activity `OnBackPressedDispatcher`), not `onBackPressed()`.`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:27:- Secondary destinations and WebView dismiss use `BackHandler`.`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:104:- Predictive back: secondary pages animate back through `BackHandler` / NavController.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:100:用命中行快速跳到 Manifest、BackHandler、Alarm、Share Intent、网络配置等位置。`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:113:- 返回：`enableOnBackInvokedCallback`、`BackHandler`、`onBackPressed``
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenAboutAndDialogs.kt:23:import androidx.activity.compose.BackHandler`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenApp.kt:6:import androidx.activity.compose.BackHandler`
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenApp.kt:345:BackHandler(enabled = !currentDestination.showInBottomNav) {`

### `onBackPressed` (8 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:22:Predictive back system animations are enabled by default; `onBackPressed` / raw KEYCODE_BACK paths are obsolete.`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:26:- Navigation interception uses Compose `BackHandler` (Activity `OnBackPressedDispatcher`), not `onBackPressed()`.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:113:- 返回：`enableOnBackInvokedCallback`、`BackHandler`、`onBackPressed``
- `docs/vivo-adaptation/REVIEW.md:82:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:113:- 返回：`enableOnBackInvokedCallback`、`BackHandler`、`onBackPressed```
- `docs/vivo-adaptation/REVIEW.md:86:- `docs/vivo-adaptation/REVIEW.md:82:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:111:- 返回：`enableOnBackInvokedCallback`、`BackHandler`、`onBackPressed````
- `docs/vivo-adaptation/REVIEW.md:92:- `docs/ANDROID_16_VIVO_ADAPTATION.md:26:- Navigation interception uses Compose `BackHandler` (Activity `OnBackPressedDispatcher`), not `onBackPressed()`.``
- `docs/vivo-adaptation/REVIEW.md:96:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:113:- 返回：`enableOnBackInvokedCallback`、`BackHandler`、`onBackPressed```
- `docs/vivo-adaptation/REVIEW.md:101:### `onBackPressed` (8 hit lines, showing up to 8)`

### `OnBackPressed` (6 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:26:- Navigation interception uses Compose `BackHandler` (Activity `OnBackPressedDispatcher`), not `onBackPressed()`.`
- `docs/vivo-adaptation/REVIEW.md:92:- `docs/ANDROID_16_VIVO_ADAPTATION.md:26:- Navigation interception uses Compose `BackHandler` (Activity `OnBackPressedDispatcher`), not `onBackPressed()`.``
- `docs/vivo-adaptation/REVIEW.md:104:- `docs/ANDROID_16_VIVO_ADAPTATION.md:26:- Navigation interception uses Compose `BackHandler` (Activity `OnBackPressedDispatcher`), not `onBackPressed()`.``
- `docs/vivo-adaptation/REVIEW.md:108:- `docs/vivo-adaptation/REVIEW.md:92:- `docs/ANDROID_16_VIVO_ADAPTATION.md:26:- Navigation interception uses Compose `BackHandler` (Activity `OnBackPressedDispa`
- `docs/vivo-adaptation/REVIEW.md:112:### `OnBackPressed` (6 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:114:- `docs/ANDROID_16_VIVO_ADAPTATION.md:26:- Navigation interception uses Compose `BackHandler` (Activity `OnBackPressedDispatcher`), not `onBackPressed()`.``

### `predictive` (8 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:112:- 2026-07-16: Re-fetched docs 797 + 832 together; reconfirmed predictive back, adaptive layout, intentMatchingFlags, and non-use of fixed-rate executors.`
- `.trellis/tasks/07-16-vivo-doc-797-832-refresh/prd.md:9:- Confirmed Android 16 predictive back / adaptive layout / intent matching already present`
- `.trellis/tasks/07-16-vivo-doc-832-android16-refresh/prd.md:8:- Revalidated adaptive layout / predictive back / jobs / health / LAN / ART items`
- `docs/vivo-adaptation/REVIEW.md:121:### `predictive` (8 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:123:- `docs/ANDROID_16_VIVO_ADAPTATION.md:112:- 2026-07-16: Re-fetched docs 797 + 832 together; reconfirmed predictive back, adaptive layout, intentMatchingFlags, a`
- `docs/vivo-adaptation/REVIEW.md:124:- `.trellis/tasks/07-16-vivo-doc-797-832-refresh/prd.md:9:- Confirmed Android 16 predictive back / adaptive layout / intent matching already present``
- `docs/vivo-adaptation/REVIEW.md:125:- `.trellis/tasks/07-16-vivo-doc-832-android16-refresh/prd.md:8:- Revalidated adaptive layout / predictive back / jobs / health / LAN / ART items``
- `docs/vivo-adaptation/REVIEW.md:126:- `docs/vivo-adaptation/REVIEW.md:121:### `predictive` (8 hit lines, showing up to 8)``

### `screenOrientation` (6 hit lines, showing up to 8)

- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:112:- 大屏：`screenOrientation`、`resizeableActivity`、`setRequestedOrientation``
- `docs/vivo-adaptation/REVIEW.md:132:### `screenOrientation` (6 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:134:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:112:- 大屏：`screenOrientation`、`resizeableActivity`、`setRequestedOrientation```
- `docs/vivo-adaptation/REVIEW.md:135:- `docs/vivo-adaptation/REVIEW.md:132:### `screenOrientation` (6 hit lines, showing up to 8)``
- `docs/vivo-adaptation/REVIEW.md:136:- `docs/vivo-adaptation/REVIEW.md:134:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:110:- 大屏：`screenOrientation`、`resizeableActivity`、`setRequestedOrientation````
- `docs/vivo-adaptation/REVIEW.md:137:- `docs/vivo-adaptation/REVIEW.md:135:- `docs/vivo-adaptation/REVIEW.md:122:### `screenOrientation` (6 hit lines, showing up to 8)```

### `resizeableActivity` (12 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:16:- `resizeableActivity="true"` on primary activities.`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:37:- Keep `resizeableActivity="true"`.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:112:- 大屏：`screenOrientation`、`resizeableActivity`、`setRequestedOrientation``
- `app/src/main/AndroidManifest.xml:60:android:resizeableActivity="true"`
- `app/src/main/AndroidManifest.xml:76:android:resizeableActivity="true"`
- `app/src/main/AndroidManifest.xml:88:android:resizeableActivity="true"`
- `app/src/main/AndroidManifest.xml:100:android:resizeableActivity="true"`
- `docs/vivo-adaptation/REVIEW.md:134:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:112:- 大屏：`screenOrientation`、`resizeableActivity`、`setRequestedOrientation```

### `setRequestedOrientation` (6 hit lines, showing up to 8)

- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:112:- 大屏：`screenOrientation`、`resizeableActivity`、`setRequestedOrientation``
- `docs/vivo-adaptation/REVIEW.md:134:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:112:- 大屏：`screenOrientation`、`resizeableActivity`、`setRequestedOrientation```
- `docs/vivo-adaptation/REVIEW.md:136:- `docs/vivo-adaptation/REVIEW.md:134:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:110:- 大屏：`screenOrientation`、`resizeableActivity`、`setRequestedOrientation````
- `docs/vivo-adaptation/REVIEW.md:138:- `docs/vivo-adaptation/REVIEW.md:136:- `docs/vivo-adaptation/REVIEW.md:124:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:110:- 大屏：`screenOrientation`、`resizeableActi`
- `docs/vivo-adaptation/REVIEW.md:145:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:112:- 大屏：`screenOrientation`、`resizeableActivity`、`setRequestedOrientation```
- `docs/vivo-adaptation/REVIEW.md:150:- `docs/vivo-adaptation/REVIEW.md:134:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:110:- 大屏：`screenOrientation`、`resizeableActivity`、`setRequestedOrientation````

### `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` (7 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:19:- No `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` opt-out; we adapt instead of disabling.`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:40:- No reliance on `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`.`
- `docs/vivo-adaptation/REVIEW.md:161:### `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` (7 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:163:- `docs/ANDROID_16_VIVO_ADAPTATION.md:19:- No `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` opt-out; we adapt instead of disabling.``
- `docs/vivo-adaptation/REVIEW.md:164:- `docs/ANDROID_17_VIVO_ADAPTATION.md:40:- No reliance on `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`.``
- `docs/vivo-adaptation/REVIEW.md:165:- `docs/vivo-adaptation/REVIEW.md:161:### `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` (7 hit lines, showing up to 8)``
- `docs/vivo-adaptation/REVIEW.md:166:- `docs/vivo-adaptation/REVIEW.md:163:- `docs/ANDROID_16_VIVO_ADAPTATION.md:19:- No `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` opt-out; we adapt instead of`

### `ACCESS_LOCAL_NETWORK` (10 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:56:- No `NEARBY_WIFI_DEVICES` / `ACCESS_LOCAL_NETWORK` declaration unless a future feature needs LAN.`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:52:`ACCESS_LOCAL_NETWORK` becomes required for LAN/mDNS access when targetSdk >= 37.`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:56:- No `ACCESS_LOCAL_NETWORK` declaration unless a future feature needs LAN/mDNS.`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:109:- No LAN-only feature is introduced without `ACCESS_LOCAL_NETWORK` + runtime UX.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:116:- 网络：`usesCleartextTraffic`、`networkSecurityConfig`、`ACCESS_LOCAL_NETWORK``
- `docs/vivo-adaptation/REVIEW.md:171:### `ACCESS_LOCAL_NETWORK` (10 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:173:- `docs/ANDROID_16_VIVO_ADAPTATION.md:56:- No `NEARBY_WIFI_DEVICES` / `ACCESS_LOCAL_NETWORK` declaration unless a future feature needs LAN.``
- `docs/vivo-adaptation/REVIEW.md:174:- `docs/ANDROID_17_VIVO_ADAPTATION.md:52:`ACCESS_LOCAL_NETWORK` becomes required for LAN/mDNS access when targetSdk >= 37.``

### `NEARBY_WIFI_DEVICES` (7 hit lines, showing up to 8)

- `docs/ANDROID_13_VIVO_ADAPTATION.md:60:- No Wi-Fi RTT/hotspot management that needs `NEARBY_WIFI_DEVICES`.`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:56:- No `NEARBY_WIFI_DEVICES` / `ACCESS_LOCAL_NETWORK` declaration unless a future feature needs LAN.`
- `docs/vivo-adaptation/REVIEW.md:173:- `docs/ANDROID_16_VIVO_ADAPTATION.md:56:- No `NEARBY_WIFI_DEVICES` / `ACCESS_LOCAL_NETWORK` declaration unless a future feature needs LAN.``
- `docs/vivo-adaptation/REVIEW.md:179:- `docs/vivo-adaptation/REVIEW.md:173:- `docs/ANDROID_16_VIVO_ADAPTATION.md:56:- No `NEARBY_WIFI_DEVICES` / `ACCESS_LOCAL_NETWORK` declaration unless a future f`
- `docs/vivo-adaptation/REVIEW.md:182:### `NEARBY_WIFI_DEVICES` (7 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:184:- `docs/ANDROID_13_VIVO_ADAPTATION.md:60:- No Wi-Fi RTT/hotspot management that needs `NEARBY_WIFI_DEVICES`.``
- `docs/vivo-adaptation/REVIEW.md:185:- `docs/ANDROID_16_VIVO_ADAPTATION.md:56:- No `NEARBY_WIFI_DEVICES` / `ACCESS_LOCAL_NETWORK` declaration unless a future feature needs LAN.``

### `BODY_SENSORS` (8 hit lines, showing up to 8)

- `docs/ANDROID_13_VIVO_ADAPTATION.md:61:- No body-sensor / heart-rate APIs; no `BODY_SENSORS(_BACKGROUND)`.`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:45:Heart-rate and related APIs move from `BODY_SENSORS` to `android.permission.health.*`.`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:48:- App does not use heart-rate / BODY_SENSORS APIs.`
- `docs/vivo-adaptation/REVIEW.md:192:### `BODY_SENSORS` (8 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:194:- `docs/ANDROID_13_VIVO_ADAPTATION.md:61:- No body-sensor / heart-rate APIs; no `BODY_SENSORS(_BACKGROUND)`.``
- `docs/vivo-adaptation/REVIEW.md:195:- `docs/ANDROID_16_VIVO_ADAPTATION.md:45:Heart-rate and related APIs move from `BODY_SENSORS` to `android.permission.health.*`.``
- `docs/vivo-adaptation/REVIEW.md:196:- `docs/ANDROID_16_VIVO_ADAPTATION.md:48:- App does not use heart-rate / BODY_SENSORS APIs.``
- `docs/vivo-adaptation/REVIEW.md:197:- `docs/vivo-adaptation/REVIEW.md:192:### `BODY_SENSORS` (8 hit lines, showing up to 8)``

### `usesCleartextTraffic` (13 hit lines, showing up to 8)

- `docs/ANDROID_17_VIVO_ADAPTATION.md:43:`usesCleartextTraffic` is on a deprecation path; prefer `networkSecurityConfig`.`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:46:- `minSdk = 26` (>= 24), so Manifest no longer declares `usesCleartextTraffic`.`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:113:- 2026-07-16: Re-fetched doc 1010 via `scripts/fetch_vivo_adaptation_doc.py 1010 --scan-repo`, revalidated high-priority items, removed Manifest `usesCleartextT`
- `docs/SYSTEM_UPDATE_STRATEGY_IMPLEMENTATION.md:378:- `android:usesCleartextTraffic="false"``
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:116:- 网络：`usesCleartextTraffic`、`networkSecurityConfig`、`ACCESS_LOCAL_NETWORK``
- `.trellis/tasks/07-16-vivo-doc-1010-android17-refresh/prd.md:9:- Removed android:usesCleartextTraffic; networkSecurityConfig is sole policy source`
- `app/src/main/res/xml/network_security_config.xml:4:- Prefer networkSecurityConfig over android:usesCleartextTraffic.`
- `app/src/main/res/xml/network_security_config.xml:5:- usesCleartextTraffic is on a deprecation path; cleartext must be domain-scoped here if ever needed.`

### `networkSecurityConfig` (11 hit lines, showing up to 8)

- `docs/ANDROID_17_VIVO_ADAPTATION.md:43:`usesCleartextTraffic` is on a deprecation path; prefer `networkSecurityConfig`.`
- `docs/SYSTEM_UPDATE_STRATEGY_IMPLEMENTATION.md:379:- `android:networkSecurityConfig="@xml/network_security_config"``
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:116:- 网络：`usesCleartextTraffic`、`networkSecurityConfig`、`ACCESS_LOCAL_NETWORK``
- `.trellis/tasks/07-16-vivo-doc-1010-android17-refresh/prd.md:9:- Removed android:usesCleartextTraffic; networkSecurityConfig is sole policy source`
- `app/src/main/AndroidManifest.xml:51:android:networkSecurityConfig="@xml/network_security_config"`
- `app/src/main/res/xml/network_security_config.xml:4:- Prefer networkSecurityConfig over android:usesCleartextTraffic.`
- `docs/vivo-adaptation/REVIEW.md:177:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:116:- 网络：`usesCleartextTraffic`、`networkSecurityConfig`、`ACCESS_LOCAL_NETWORK```
- `docs/vivo-adaptation/REVIEW.md:205:- `docs/ANDROID_17_VIVO_ADAPTATION.md:43:`usesCleartextTraffic` is on a deprecation path; prefer `networkSecurityConfig`.``

### `FLAG_GRANT_READ_URI_PERMISSION` (16 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:66:- Share/install stream intents use explicit URI grants via `SecureShareIntents` (`ClipData` + `FLAG_GRANT_READ_URI_PERMISSION`).`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:28:- Always attach `ClipData` + `FLAG_GRANT_READ_URI_PERMISSION` for FileProvider shares and APK install views.`
- `docs/SYSTEM_UPDATE_STRATEGY_IMPLEMENTATION.md:361:FLAG_GRANT_READ_URI_PERMISSION`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:115:- 分享/URI：`ACTION_SEND`、`FLAG_GRANT_READ_URI_PERMISSION`、`FileProvider``
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenUiFormatters.kt:394:context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)`
- `app/src/main/java/com/projectlumen/app/core/share/SecureShareIntents.kt:12:* Always attach ClipData + FLAG_GRANT_READ_URI_PERMISSION so receivers can open FileProvider URIs.`
- `app/src/main/java/com/projectlumen/app/core/share/SecureShareIntents.kt:28:addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)`
- `app/src/main/java/com/projectlumen/app/core/share/SecureShareIntents.kt:32:addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)`

### `ACTION_SEND` (13 hit lines, showing up to 8)

- `docs/ANDROID_17_VIVO_ADAPTATION.md:24:Do not rely on implicit URI grants for `ACTION_SEND` / `ACTION_SEND_MULTIPLE` / stream URIs.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:115:- 分享/URI：`ACTION_SEND`、`FLAG_GRANT_READ_URI_PERMISSION`、`FileProvider``
- `app/src/main/java/com/projectlumen/app/app/ProjectLumenHomeConvenienceCard.kt:138:val intent = Intent(Intent.ACTION_SEND).apply {`
- `app/src/main/java/com/projectlumen/app/core/share/SecureShareIntents.kt:11:* Vivo/Android guidance: do not rely on implicit URI grants for ACTION_SEND*.`
- `app/src/main/java/com/projectlumen/app/core/share/SecureShareIntents.kt:23:val intent = Intent(Intent.ACTION_SEND).apply {`
- `docs/vivo-adaptation/REVIEW.md:230:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:115:- 分享/URI：`ACTION_SEND`、`FLAG_GRANT_READ_URI_PERMISSION`、`FileProvider```
- `docs/vivo-adaptation/REVIEW.md:236:### `ACTION_SEND` (13 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:238:- `docs/ANDROID_17_VIVO_ADAPTATION.md:24:Do not rely on implicit URI grants for `ACTION_SEND` / `ACTION_SEND_MULTIPLE` / stream URIs.``

### `FileProvider` (57 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:106:- Share backup / export / install APK still open with granted FileProvider URIs.`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:28:- Always attach `ClipData` + `FLAG_GRANT_READ_URI_PERMISSION` for FileProvider shares and APK install views.`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:106:- Backup / export / APK install shares open with explicit FileProvider URI grants.`
- `docs/NEXT_STEPS_FEATURE_RECOMMENDATIONS.md:650:- 所有文件通过 `FileProvider` 分享，不暴露真实路径。`
- `docs/SYSTEM_UPDATE_STRATEGY_IMPLEMENTATION.md:29:- Android 权限与 FileProvider：`app/src/main/AndroidManifest.xml`、`app/src/main/res/xml/file_paths.xml``
- `docs/SYSTEM_UPDATE_STRATEGY_IMPLEMENTATION.md:365:APK 文件通过 FileProvider 暴露：`
- `docs/SYSTEM_UPDATE_STRATEGY_IMPLEMENTATION.md:380:- FileProvider `exported=false``
- `docs/SYSTEM_UPDATE_STRATEGY_IMPLEMENTATION.md:381:- FileProvider `grantUriPermissions=true``

### `mediaPlayback` (10 hit lines, showing up to 8)

- `docs/ANDROID_17_VIVO_ADAPTATION.md:14:Android 17 requires foreground-visible state or a `mediaPlayback` FGS with While-In-Use capability for sustained audio output.`
- `docs/ANDROID_17_VIVO_ADAPTATION.md:20:- Do not introduce continuous background media playback without a `mediaPlayback` FGS.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:117:- 音频/FGS：`mediaPlayback`、`USAGE_NOTIFICATION`、`FOREGROUND_SERVICE``
- `app/src/main/java/com/projectlumen/app/core/services/AudioService.kt:18:* Android 17 tightens background audio: continuous media requires mediaPlayback FGS with WIU.`
- `app/src/main/java/com/projectlumen/app/core/services/NotificationService.kt:46:// Transient cue only; continuous media belongs to mediaPlayback FGS under Android 17.`
- `docs/vivo-adaptation/REVIEW.md:258:### `mediaPlayback` (10 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:260:- `docs/ANDROID_17_VIVO_ADAPTATION.md:14:Android 17 requires foreground-visible state or a `mediaPlayback` FGS with While-In-Use capability for sustained audio `
- `docs/vivo-adaptation/REVIEW.md:261:- `docs/ANDROID_17_VIVO_ADAPTATION.md:20:- Do not introduce continuous background media playback without a `mediaPlayback` FGS.``

### `FOREGROUND_SERVICE` (21 hit lines, showing up to 8)

- `docs/ANDROID_12_VIVO_ADAPTATION.md:45:- Shared FGS notification helper calls `setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)`.`
- `docs/NEXT_STEPS_FEATURE_RECOMMENDATIONS.md:486:- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` / `FOREGROUND_SERVICE_CAMERA`：后台计时、相机采样、光照监测。`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:117:- 音频/FGS：`mediaPlayback`、`USAGE_NOTIFICATION`、`FOREGROUND_SERVICE``
- `.trellis/spec/frontend/android-foreground-services.md:7:- Trigger: Android client code starts `ProximityDetectionService`, or any future foreground service using `FOREGROUND_SERVICE_TYPE_CAMERA`.`
- `.trellis/spec/frontend/android-foreground-services.md:34:- Android rejects `FOREGROUND_SERVICE_TYPE_CAMERA` at `startForeground` -> record crash, stop service, return `START_NOT_STICKY`.`
- `.trellis/spec/frontend/android-foreground-services.md:45:- Bad: calling `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_CAMERA)` before checking `Manifest.permission.CAMERA`.`
- `.trellis/spec/frontend/android-foreground-services.md:77:ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,`
- `app/src/main/AndroidManifest.xml:23:<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />`

### `USAGE_NOTIFICATION` (9 hit lines, showing up to 8)

- `docs/ANDROID_17_VIVO_ADAPTATION.md:18:- `AudioService` uses `AudioAttributes.USAGE_NOTIFICATION` / `CONTENT_TYPE_SONIFICATION`.`
- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:117:- 音频/FGS：`mediaPlayback`、`USAGE_NOTIFICATION`、`FOREGROUND_SERVICE``
- `app/src/main/java/com/projectlumen/app/core/services/AudioService.kt:19:* Lumen only needs transient notification cues, so we use USAGE_NOTIFICATION attributes and avoid`
- `app/src/main/java/com/projectlumen/app/core/services/AudioService.kt:54:.setUsage(AudioAttributes.USAGE_NOTIFICATION)`
- `docs/vivo-adaptation/REVIEW.md:262:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:117:- 音频/FGS：`mediaPlayback`、`USAGE_NOTIFICATION`、`FOREGROUND_SERVICE```
- `docs/vivo-adaptation/REVIEW.md:273:- `docs/VIVO_ADAPTATION_DOC_WORKFLOW.md:117:- 音频/FGS：`mediaPlayback`、`USAGE_NOTIFICATION`、`FOREGROUND_SERVICE```
- `docs/vivo-adaptation/REVIEW.md:280:### `USAGE_NOTIFICATION` (9 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:282:- `docs/ANDROID_17_VIVO_ADAPTATION.md:18:- `AudioService` uses `AudioAttributes.USAGE_NOTIFICATION` / `CONTENT_TYPE_SONIFICATION`.``

### `HiddenApiBypass` (7 hit lines, showing up to 8)

- `docs/ANDROID_16_VIVO_ADAPTATION.md:84:Avoid HiddenApiBypass and unsupported packers that rely on ART internals.`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:87:- No HiddenApiBypass dependency.`
- `docs/vivo-adaptation/REVIEW.md:291:### `HiddenApiBypass` (7 hit lines, showing up to 8)`
- `docs/vivo-adaptation/REVIEW.md:293:- `docs/ANDROID_16_VIVO_ADAPTATION.md:84:Avoid HiddenApiBypass and unsupported packers that rely on ART internals.``
- `docs/vivo-adaptation/REVIEW.md:294:- `docs/ANDROID_16_VIVO_ADAPTATION.md:87:- No HiddenApiBypass dependency.``
- `docs/vivo-adaptation/REVIEW.md:295:- `docs/vivo-adaptation/REVIEW.md:291:### `HiddenApiBypass` (7 hit lines, showing up to 8)``
- `docs/vivo-adaptation/REVIEW.md:296:- `docs/vivo-adaptation/REVIEW.md:293:- `docs/ANDROID_16_VIVO_ADAPTATION.md:84:Avoid HiddenApiBypass and unsupported packers that rely on ART internals.```

### `targetSdk` (32 hit lines, showing up to 8)

- `CLAUDE.md:77:- SDK: `minSdk 26`, `compileSdk`/`targetSdk 37`, Java 21 toolchain. ABI splits (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`) + universal APK. The bundled JetBra`
- `app/build.gradle.kts:159:targetSdk = 37`
- `baselineprofile/build.gradle.kts:16:targetSdk = 37`
- `docs/ANDROID_12_VIVO_ADAPTATION.md:13:Project Lumen currently ships with `compileSdk/targetSdk = 37` (above Android 12 / API 31). This note tracks the Android 12 items that still matter after the 20`
- `docs/ANDROID_13_VIVO_ADAPTATION.md:13:Project Lumen currently ships with `compileSdk/targetSdk = 37` (above Android 13 / API 33). This note tracks the Android 13 items that still matter after the 20`
- `docs/ANDROID_14_VIVO_ADAPTATION.md:13:Project Lumen currently ships with `compileSdk/targetSdk = 37` (above Android 14 / API 34). This note tracks the Android 14 items that still matter after the 20`
- `docs/ANDROID_14_VIVO_ADAPTATION.md:17:### 1. Exact alarms denied by default (targetSdk 33+)`
- `docs/ANDROID_14_VIVO_ADAPTATION.md:63:### 6. Minimum installable targetSdk`

### `compileSdk` (23 hit lines, showing up to 8)

- `CLAUDE.md:77:- SDK: `minSdk 26`, `compileSdk`/`targetSdk 37`, Java 21 toolchain. ABI splits (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`) + universal APK. The bundled JetBra`
- `app/build.gradle.kts:45:compileSdk = 37`
- `baselineprofile/build.gradle.kts:11:compileSdk = 37`
- `docs/ANDROID_12_VIVO_ADAPTATION.md:13:Project Lumen currently ships with `compileSdk/targetSdk = 37` (above Android 12 / API 31). This note tracks the Android 12 items that still matter after the 20`
- `docs/ANDROID_13_VIVO_ADAPTATION.md:13:Project Lumen currently ships with `compileSdk/targetSdk = 37` (above Android 13 / API 33). This note tracks the Android 13 items that still matter after the 20`
- `docs/ANDROID_14_VIVO_ADAPTATION.md:13:Project Lumen currently ships with `compileSdk/targetSdk = 37` (above Android 14 / API 34). This note tracks the Android 14 items that still matter after the 20`
- `docs/ANDROID_15_VIVO_ADAPTATION.md:13:Project Lumen currently ships with `compileSdk/targetSdk = 37` (above Android 15 / API 35). This note tracks the Android 15 items that still matter after the 20`
- `docs/ANDROID_16_VIVO_ADAPTATION.md:7:Project Lumen currently ships with `compileSdk/targetSdk = 37` (above Android 16 / API 36). This note tracks Vivo Android 16 guidance that still applies after t`

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

