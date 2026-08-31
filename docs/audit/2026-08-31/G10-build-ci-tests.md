# G10 构建工程化 / Gradle / Manifest / 混淆规则 / CI 工作流 / 单元测试 审查报告

- 审查文件数：52，总行数：约 6340
  - Gradle：`build.gradle.kts`(8)、`settings.gradle.kts`(62)、`gradle.properties`(8)、`app/build.gradle.kts`(324)、`baselineprofile/build.gradle.kts`(52)
  - 混淆：`app/proguard-rules.pro`(107)（`lumen-crash*` 三个模块的 `*.pro` 归 G11）
  - 清单与主题：`app/src/main/AndroidManifest.xml`(197)、`res/raw/keep.xml`(3)、6 个 `styles.xml`(约 70)
  - CI：`.github/workflows/` 8 个 yml + `.github/actions/setup-android-native-toolchain/action.yml`(37)，约 1533 行
  - 测试：`app/src/test/` 20 个 kt(1547)、`baselineprofile/.../BaselineProfileGenerator.kt`(170)
  - 脚本：`scripts/` 9 个(2252)

- 结论摘要：**构建脚本本身（`app/build.gradle.kts`、CMake 注入、签名装配、ABI 切分与 baseline profile 的互斥处理）质量相当高，真正的系统性风险全部集中在"发布流水线"与"R8 规则的反射盲区"两处。**最严重的是三件事：① `build.yml` 在**任意分支**的每次 push 都会发布一个 `make_latest: true` 的正式 Release 并向后端 admin API 推送 `force-update`，而单元测试与 lint 排在发布**之后**执行——未合并、未验证的分支代码可以直接成为全量用户的强制更新；② `versionCode` 取自 `GITHUB_RUN_NUMBER`，而 run number 是**按工作流各自独立计数**的，`release.yml`（只在 tag 上跑）的计数远小于 `build.yml`，导致正式 tag 包的 versionCode 低于分支包，客户端版本比较彻底错乱；③ R8 侧缺两条关键 keep：ML Kit/Firebase `ComponentRegistrar` 的无参构造、以及 Shizuku UserService 的无参构造，二者都只在 minify 的 release 里静默失效（debug 与 CI 全绿），分别打掉人脸检测与按应用网络管控这两个核心能力。另外请求签名密钥有一个硬编码字面量兜底值，secret 缺失时会静默出一个"密钥公开可读"的 release 包。团队交给我核实的"原生库缺失导致发版即崩"这条**不成立**（详见 G10-12 与末尾核查结论）。

## 缺陷清单

### [G10-01] ML Kit / Firebase `ComponentRegistrar` 的无参构造无 keep 规则，minify release 下人脸检测静默失效
- 严重度：P0
- 类别：G 安全 / D 生命周期与框架约束
- 位置：`app/proguard-rules.pro`（全文 107 行，**不存在**任何 `ComponentRegistrar` 相关规则）；`app/build.gradle.kts:198`（`isMinifyEnabled = true`）、`:203-206`（叠加 `proguard-android-optimize.txt`）、`app/proguard-rules.pro:116-121`（`-optimizationpasses 5` / `-allowaccessmodification` / `-overloadaggressively` / `-repackageclasses`）；依赖见 `app/build.gradle.kts:339-340`
- 现状：
  ```
  # app/proguard-rules.pro 全文检索结果：ComponentRegistrar / firebase / mlkit 命中 0 处
  # app/build.gradle.kts:339
  implementation("com.google.mlkit:face-detection:16.1.7")
  implementation("com.google.mlkit:face-mesh-detection:16.0.0-beta3")
  ```
  ML Kit 的组件发现依赖 `com.google.firebase.components.ComponentRegistrar` 实现类，由 `MlKitComponentDiscoveryService` 的 manifest meta-data 按类名反射 `newInstance()` 实例化。仓库自己的规则文件里没有任何一条覆盖它，完全依赖 AAR 自带的 consumer rules——而历史上那批 consumer rules 只写了 `-keep class * implements ...ComponentRegistrar`（保住类名）而没有 `{ <init>(); }`（保住构造）。
- 触发场景：release 构建（`minifyEnabled=true`）下 R8 判定 `<init>()V` 无活跃调用点并裁掉；组件发现时反射实例化失败被 ML Kit 静默吞掉 → `FaceDetection.getClient()` 拿到空工厂。debug 不混淆、CI 的 `testDebugUnitTest` / `lintDebug` 也都不混淆，**所有验证环节全绿**。
- 影响：装了正式包的用户打开"视距检测 / 疲劳检测"（`ProximityDetectionService`，相机 + 人脸检测/人脸网格）时，`getClient()` 返回的检测器 NPE 或整条链路无声中断，护眼核心功能不可用；开发机复现不了。
- 修复方案：在 `app/proguard-rules.pro` 的 JNI 段之后追加（注意必须是 `<init>();` 而不是 `public <init>();`，ProGuard 语法里后者不匹配 Kotlin/Java 生成的默认构造在部分可见性下的情形）：
  ```
  -keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
  -keep,allowobfuscation @interface com.google.firebase.components.ComponentRegistrar
  -keep class com.google.mlkit.common.internal.MlKitComponentDiscoveryService { <init>(); }
  -keep class com.google.firebase.components.** { *; }
  ```
- 风险/注意：会略微增大包体（几 KB 量级）。修复后**无法用 debug 构建验证**，必须看 minify release 的 `mapping.txt` 里 ComponentRegistrar 实现类是否仍带构造，或者在真机跑一次视距检测。与 G10-08（mapping 不上传）耦合：不先修 G10-08，这条改完也验不了。

### [G10-02] 任意分支 push 即发布 `make_latest` 正式 Release 并向后端推 `force-update`，而单测/lint 排在发布之后
- 严重度：P0
- 类别：A 架构与设计 / E 韧性
- 位置：`.github/workflows/build.yml:3-9`（触发条件）、`:357-368`（发布）、`:377-430`（推 admin API）、`:432-440`（单测）、`:442-450`（lint）
- 现状：
  ```yaml
  # :3-9
  on:
    push:
      branches: ["**"]
  # :357-368  —— 步骤顺序上排在第 9 位
  - name: Automatic release
    if: success() && github.event_name != 'pull_request'
    uses: softprops/action-gh-release@v2
    with: { draft: false, make_latest: true, tag_name: "v${{ env.PROJECT_LUMEN_VERSION_NAME }}-${{ env.PROJECT_LUMEN_SHORT_HASH }}", files: release-assets/* }
  # :432-440  —— 步骤顺序上排在第 12 位（发布之后）
  - name: Run state machine unit tests
    if: ${{ !cancelled() }}
    run: gradle testDebugUnitTest --no-daemon --warning-mode all --stacktrace
  ```
  紧随发布之后的 `:377` 步骤把 `release-manifest.json` 包装成 `{"action":"force-update", ...}` POST 到 `${API_BASE}/admin/actions`。
- 触发场景：开发者推任意一条特性分支（含 WIP、含明知会挂测试的中间提交）。`if: success()` 只看到"发布之前的步骤都成功"，此时测试压根还没跑。
- 影响：（a）未合并的分支产物成为 GitHub 的 `latest` release，并被后端标记为 force-update 全量下发给真实用户；（b）测试失败只让工作流最终变红，**Release 已经发出去且不会自动撤回**；（c）release 列表被每次 push 刷屏，无法区分正式版与试验版。
- 修复方案：`.github/workflows/build.yml`——① 把 `Run state machine unit tests`(`:432`) 与 `Run Android lint`(`:442`) 两个步骤整体移到 `Build release APK`(`:185`) 之前（或至少移到 `Automatic release`(`:357`) 之前），并去掉 `if: ${{ !cancelled() }}` 改为默认的 fail-fast；② 把 `Automatic release` 与 `Sync release manifest to admin API` 的条件收紧为 `github.ref == 'refs/heads/main'`（发布正式版的职责本来就在 `release.yml`，更彻底的做法是把这两步从 `build.yml` 里删掉，只保留 `Upload release APK` 的 artifact）；③ 给工作流加 `concurrency: { group: ${{ github.workflow }}-${{ github.ref }}, cancel-in-progress: false }`，避免并发 push 同时向 admin API 推两次 force-update。
- 风险/注意：把测试前置会让"拿一个包"的等待变长（现在 baseline profile 生成本身就要跑模拟器）。若团队确实依赖分支包做验证，保留 artifact 上传即可，不要保留 Release 发布。改动会改变现有的"每次 push 都有 latest 包"习惯，需要和使用者对齐。

### [G10-03] `versionCode` 取自 `GITHUB_RUN_NUMBER`，两个工作流各自独立计数 → tag 正式包的 versionCode 低于分支包
- 严重度：P0
- 类别：A 架构与设计 / F 持久化一致性
- 位置：`.github/workflows/build.yml:61-69`、`.github/workflows/release.yml:64-72`、`.github/workflows/lumen-ui-tuner.yml:113-121`；消费侧 `app/build.gradle.kts:52-55`
- 现状：
  ```bash
  # build.yml:61 与 release.yml:64 完全相同
  VERSION_CODE="${GITHUB_RUN_NUMBER:-$(date -u +%s)}"
  ...
  echo "PROJECT_LUMEN_VERSION_CODE=${VERSION_CODE}" >> "$GITHUB_ENV"
  ```
  ```kotlin
  // app/build.gradle.kts:52-55 —— env 优先级最高，彻底覆盖由 versionName 推导的值
  val projectLumenVersionCode = providers.environmentVariable("PROJECT_LUMEN_VERSION_CODE")
      .orNull?.toIntOrNull()
      ?: projectLumenVersionCodeFromName(projectLumenVersionName)
  ```
  `github.run_number` 是**每个工作流文件各自维护**的自增计数器，不是仓库全局的。`build.yml` 在每次 push 都跑（计数已经很大），`release.yml` 只在 `v*` tag 上跑（计数很小）。同时 `build.yml:365` 与 `release.yml:294` 都会创建 Release，`release-manifest.json` 里的 `versionCode`(`build.yml:337` / `release.yml:267`) 就是这个 run number。
- 触发场景：任何一次正式 tag 发布。假设 `build.yml` 已跑到第 480 次、`release.yml` 才第 6 次，则 tag 包 `versionCode=6`，而之前分支包 `versionCode=480`。
- 影响：（a）装过分支包的用户永远收不到正式版更新（客户端按 versionCode 比大小，正式版看起来是"降级"）；（b）Android 系统层面也拒绝以低 versionCode 覆盖安装，用户必须卸载重装、丢掉本地 Room 数据；（c）`versionName` 与 `versionCode` 完全脱钩，线上崩溃无法凭 versionCode 定位到版本；（d）工作流文件被重建或仓库迁移时 run number 归零，versionCode 直接回退。
- 修复方案：删掉两个工作流里的 `VERSION_CODE="${GITHUB_RUN_NUMBER:-...}"` 与随后的 `echo "PROJECT_LUMEN_VERSION_CODE=..."`（`build.yml:61-64`+`:69`，`release.yml:64-67`+`:72`，`lumen-ui-tuner.yml` 同段），让 `app/build.gradle.kts:21-31` 的 `projectLumenVersionCodeFromName()` 成为唯一真相源（它已经实现了 `major*10000 + minor*100 + patch` 的单调映射）。若确实需要同一 versionName 出多个包，改为 `versionCodeFromName * 100 + 单调后缀`，且后缀必须来自全局单调的量（如提交计数 `git rev-list --count HEAD`），绝不能用 run number。
- 风险/注意：切换后**新的 versionCode 会比现存线上包的 run-number 值小**（例如 `1.2.3` → 10203，远小于 480）。必须一次性核对当前线上最大 versionCode，必要时先把 `app/application.version` 抬高，或临时用 `PROJECT_LUMEN_VERSION_CODE` 显式指定一个大于线上最大值的数做过渡，否则会造成一次全量用户无法更新。这条修复不能盲改，必须先查线上现值。

### [G10-04] 请求签名密钥有硬编码字面量兜底，secret 缺失时静默出一个"密钥公开可读"的 release 包
- 严重度：P0
- 类别：G 安全
- 位置：`app/build.gradle.kts:113-119`（兜底值）、`:171-180`（编译进 `.so` 的 CMake define）、`:154`（完整性开关随 cert 为空而关闭）；对照 `:138-143`（证书固定的 `require` 硬校验）
- 现状（四个 secret 类配置的兜底默认值，逐条结论）：
  ```kotlin
  // :113-119  PROJECT_LUMEN_REQUEST_SIGNING_SECRET
  ?: "project-lumen-local-request-signing-key"      // ← 硬编码字面量兜底
  // :120-126  PROJECT_LUMEN_RELEASE_CERT_SHA256
  ?: ""                                              // ← 空串
  // :82-88    PROJECT_LUMEN_TELEMETRY_ACCESS_TOKEN
  ?: ""                                              // ← 空串
  // :127-133  PROJECT_LUMEN_OPEN_API_TRUSTED_SIGNATURE_SHA256
  ?: ""                                              // ← 空串
  // :68-81    API_BASE_URL / TRANSLATION_API_BASE_URL
  ?: "https://tts.chloemlla.com/api/lumen" / "https://tts.chloemlla.com"   // ← 真实生产域名
  ```
  结论：**没有把真密钥提交进仓库**（`project-lumen-local-request-signing-key` 是占位字符串，不是可用凭据），也**不会导致签名恒失败**（APK 签名走 `signingConfigs`，与这些值无关）。真正的问题是三条 fail-open：
  1. 该字面量会经 `:175` 编译进 `liblumen_security.so`，成为 APK 里所有人可读的 HMAC 密钥。secret 未配置/为空时构建照常成功，产出的 release 包对后端的请求签名可被任意第三方伪造。
  2. `:154` `APP_INTEGRITY_ENFORCEMENT_ENABLED = projectLumenReleaseCertSha256.isNotBlank()` —— cert SHA-256 缺失时完整性门禁**自动关闭**，不报错。
  3. 同一份文件里，证书固定却有硬校验（`:138-143` 两条 `require`，**两个 host 都覆盖了**，这点是对的）。同样的严格度没有施加到签名密钥与 cert 上，标准不一致。
- 触发场景：secret 名字写错、fork 仓库、secret 被误删、或在本地/第三方 CI 上跑 `assembleRelease`。`build.yml:199` 只是把 secret 透传为 env，**没有任何"release 构建必须有 secret"的断言**。
- 影响：产出并可能被 `build.yml:357` 自动发布的正式包里，请求签名密钥是仓库里的明文字符串；攻击者可无限伪造带合法签名的后端请求（遥测投毒、滥用翻译/人脸分析额度），同时 app 完整性校验处于关闭状态。全程无任何构建告警。
- 修复方案：在 `app/build.gradle.kts` 的 `android { }` 内、`:143` 那两条 `require` 旁边，补一条只对 release 生效的断言。因为这些值在配置阶段求出，用 `gradle.startParameter.taskNames` 判定 release 意图最简单：
  ```kotlin
  val projectLumenIsReleaseBuild = gradle.startParameter.taskNames.any {
      it.contains("Release", ignoreCase = true) || it.contains("bundle", ignoreCase = true)
  }
  require(!projectLumenIsReleaseBuild || projectLumenRequestSigningSecret != "project-lumen-local-request-signing-key") {
      "Release builds must provide PROJECT_LUMEN_REQUEST_SIGNING_SECRET."
  }
  require(!projectLumenIsReleaseBuild || projectLumenReleaseCertSha256.isNotBlank()) {
      "Release builds must provide PROJECT_LUMEN_RELEASE_CERT_SHA256."
  }
  ```
  另外把兜底字面量本身改成明显不可用的占位（如 `"REPLACE_ME_DEBUG_ONLY"`），让它在日志/逆向里一眼可辨。
- 风险/注意：加了断言后，**任何没有配 secret 的环境将无法再构建 release**——包括开发者本机的 `assembleRelease` 排查、以及 fork 仓库的 CI。若需要保留本地 release 排查能力，加一个显式的 `-PprojectLumenAllowInsecureRelease=true` 逃生口，但绝不能让它成为默认值。注意 `PROJECT_LUMEN_TELEMETRY_ACCESS_TOKEN` 与 `OPEN_API_TRUSTED_SIGNATURE_SHA256` 空串是否属于"必须有"，需要先确认对应功能的降级行为（属 G07/G08 组判断），本条不擅自加断言。

### [G10-05] Shizuku UserService 的无参构造无 keep 规则，release 下按应用网络管控整体失效
- 严重度：P0
- 类别：G 安全 / D 生命周期与框架约束
- 位置：`app/proguard-rules.pro`（**无**任何覆盖 `ShizukuShellUserService` 的规则；`:14-21` 只覆盖 `extends android.app.Service`，`:47-50` 只覆盖 `NativeSecurityBridge` 与 `native <methods>`）；被反射的类 `app/src/main/java/com/projectlumen/app/core/shizuku/ShizukuShellUserService.kt:8`；绑定点 `core/shizuku/ShizukuCapabilityManager.kt:551-553`
- 现状：
  ```kotlin
  // ShizukuShellUserService.kt:8 —— 继承 Binder，不是 Service，现有 keep 规则全都不匹配
  class ShizukuShellUserService : Binder() { ... }
  // ShizukuCapabilityManager.kt:551-553 —— 只传类名，从不在 app 进程内 new 它
  Shizuku.bindUserService(
      Shizuku.UserServiceArgs(
          ComponentName(context.packageName, ShizukuShellUserService::class.java.name),
  ```
  Shizuku 在**自己的 shell 进程**里加载宿主 APK 的 classloader，按 `ComponentName` 的类名做 `Class.forName(...).getConstructor()` 实例化。app 侧从头到尾没有一处 `ShizukuShellUserService()` 调用，因此 R8 判定其默认构造 `<init>()V` 不可达并裁掉——和 G10-01 是同一种失效模式。类名本身不受影响（`::class.java.name` 拿到的是混淆后的名字，两侧自然一致），`DESCRIPTOR`(`:75`) 是硬编码字符串常量也不受混淆影响，所以**只有构造函数这一个点会断**。
- 触发场景：安装 minify release 包 → 授权 Shizuku → 触发按应用网络管控。debug 包不混淆，一切正常。
- 影响：`bindUserService` 在 shell 进程侧实例化失败，`ShizukuCapabilityManager` 拿不到 binder，所有依赖提权 shell 的能力（按应用断网、`PrivilegedDeviceControlCoordinator` 的特权路径）在正式包上端到端失效；用户看到的是"已授权 Shizuku 但功能没反应"，且开发机 100% 复现不了。
- 修复方案：在 `app/proguard-rules.pro` 的 `:47-50` JNI 段附近追加：
  ```
  # Shizuku instantiates the user service reflectively inside its own shell process.
  -keep class com.projectlumen.app.core.shizuku.ShizukuShellUserService {
      <init>();
      public boolean onTransact(int, android.os.Parcel, android.os.Parcel, int);
  }
  ```
  只保构造与 `onTransact` 即可，不必 `{ *; }`（`onTransact` 是框架方法的 override，本身已被保留，写上是为了显式表达契约）。
- 风险/注意：无行为副作用。**需注意 `:120 -repackageclasses` 会把该类挪到根包**——这不影响功能（两侧类名同源），但排查时 mapping 文件是唯一线索，与 G10-08 耦合。修复后同样只能在 minify release 真机上验证。

### [G10-06] `settings.gradle.kts` 声明了明文 HTTP 且硬编码凭据的第三方 Maven 仓库，且无 group 限定
- 严重度：P1
- 类别：G 安全
- 位置：`settings.gradle.kts:33-40`
- 现状：
  ```kotlin
  maven {
      isAllowInsecureProtocol = true
      url = uri("http://nexus.itgsa.com:5566/repository/release/")
      credentials {
          username = "developer"
          password = "developer!@#"
      }
  }
  ```
  对比同文件 `:41-43` 的 jitpack 与 `:44-57` 的 GitHub Packages 都做了 `content { includeGroup(...) }` 限定，而这个仓库**没有任何 group 过滤**，会被用于解析所有在 `google()` / `mavenCentral()` 里找不到的坐标。凭据是明文字面量，直接进了 git 历史。
- 触发场景：每次依赖解析（含 CI 的每次构建）都会对该 HTTP 端点发起明文请求并附带 Basic 凭据。任何能在 CI runner 与该主机之间做中间人的位置，都可以（a）抓到凭据、（b）对未被前序仓库命中的坐标返回任意构造的 jar/aar。
- 影响：供应链投毒面。攻击者只要控制一个能被该仓库解析到的坐标，就能把任意代码注入正式 APK；且凭据永久存在于 git 历史中。
- 修复方案：确认当前是否真有依赖来自该仓库（我在 `app/build.gradle.kts:305-353` 的依赖表中未发现任何 ITGSA 坐标，`lumen-crash*` 三个模块归 G11 核实）。若无依赖来自它，**直接删掉 `:33-40` 整块**。若确有依赖：① 改为 `https`（去掉 `isAllowInsecureProtocol`）；② 加 `content { includeGroup("<实际 group>") }`；③ 凭据改为 `providers.gradleProperty("itgsa.user")` / `providers.environmentVariable(...)`，并把当前这对明文凭据视为已泄露、请仓库所有者自行决定是否轮换。
- 风险/注意：删除前必须确认无依赖来自它，否则依赖解析会失败（**但按纪律不得在本机跑 gradle 验证，只能靠通读三个模块的依赖表 + 推 CI 观察**）。凭据轮换属仓库所有者决策，本报告只报不改。

### [G10-07] `dependabot-maintenance.yml` 把 USER_PAT 持久化进工作区、用可变分支 ref 的第三方 action、并自动 `git push origin main`
- 严重度：P1
- 类别：G 安全
- 位置：`.github/workflows/dependabot-maintenance.yml:36-37`、`:55`、`:59-60`、`:73`、`:80`、`:94`、`:108-109`、`:264`
- 现状：
  ```yaml
  - uses: actions/checkout@v4
    with:
      token: ${{ secrets.USER_PAT }}     # :37  未设 persist-credentials: false
  - uses: dtolnay/rust-toolchain@stable  # :55  @stable 是分支，不是 tag，完全可变
  - env:
      USER_PAT: ${{ secrets.USER_PAT }}  # :59
      GITHUB_TOKEN: ${{ secrets.USER_PAT }}
    run: node scripts/fix-dependabot-alerts.js
  - run: |
      git add -A                          # :73
      git push origin main                # :80
  ```
  三个叠加问题：（1）`actions/checkout` 默认 `persist-credentials: true`，PAT 会被写入 `.git/config`，此后**任意步骤**（包括 `scripts/fix-dependabot-alerts.js` 拉起的 pnpm/cargo 子进程及其依赖脚本）都能读到；（2）`dtolnay/rust-toolchain@stable` 引用的是**分支**，上游任何一次提交都会立刻在本仓库以 `contents: write` + PAT 的权限执行；（3）`git add -A` 提交整棵工作树的一切改动后直接推 `main`，无 review、无构建门禁。
- 触发场景：`:29-31` 的触发条件是 push 到 main 且改动了该 workflow 或 `scripts/fix-dependabot-alerts.js`，以及 `workflow_dispatch`。任何一次上游 action 被投毒、或脚本被改动，都会在有写权 PAT 的环境中执行。
- 影响：PAT 权限通常远大于 `GITHUB_TOKEN`（可能跨仓库），泄露后果不限于本仓库；自动推 main 会把未验证的依赖变更直接落到主干。
- 修复方案：① `:36-37` 补 `persist-credentials: false`，推送改用显式 `git push https://x-access-token:${USER_PAT}@github.com/${{ github.repository }} HEAD:main`（或直接改用 `secrets.GITHUB_TOKEN`——本 job 的操作范围不需要 PAT）；② `:55` 的 `dtolnay/rust-toolchain@stable` **本仓库已无 Rust 工程**（全仓检索无 `Cargo.toml`），直接删掉该步骤；同理 `:47-52` 的 pnpm 也可删（本仓库三个 `package.json` 都用 npm，无 `pnpm-lock.yaml`）；③ 把 `actions/checkout@v4` / `actions/setup-node@v4` / `pnpm/action-setup@v4` 全部改为 commit SHA 锁定；④ `git add -A` 改为只 add 明确的清单文件。
- 风险/注意：本工作流的另一半 `manage-prs`(`:83` 起) 实际上是**死代码**：`force-merge` 的门禁检查的是名为 `Docker Build Verification` 的 check(`:108-109`)，本仓库不存在任何 Docker 工作流，因此 `forceMergePR` 恒走 `skipped` 分支；同时仓库**没有 `.github/dependabot.yml`**，Dependabot 不会创建版本升级 PR。整个工作流连同 `scripts/fix-dependabot-alerts.js`(1441 行，含 pnpm + cargo 双通道) 都是从别的仓库整体搬过来的，建议整体删除而不是逐点加固——这是最省事也最安全的处置。删除前请确认没有人在手动用 `workflow_dispatch` 跑 `fix-alerts`。

### [G10-08] mapping 文件既被重定向出标准位置、也从未上传，线上混淆崩溃无法还原
- 严重度：P1
- 类别：G 安全 / A 架构与设计
- 位置：`app/proguard-rules.pro:121`；`.github/workflows/build.yml:452-462`（上传清单）、`:370-375`（release artifact）、`.github/workflows/release.yml:95-105`、`:287-292`
- 现状：
  ```
  # app/proguard-rules.pro:121  —— 相对路径，覆盖 AGP 默认的 build/outputs/mapping/release/mapping.txt
  -printmapping mapping.txt
  ```
  ```yaml
  # build.yml:456-462  —— 只上传测试与 lint 报告，没有 mapping
  path: |
    app/build/reports/tests/**
    app/build/reports/lint-results-debug.html
    app/build/reports/lint-results-debug.xml
    build/reports/problems/**
  ```
  两个工作流的三处上传步骤（`build.yml:370`、`build.yml:452`、`release.yml:95`、`release.yml:287`）都不含 `app/build/outputs/mapping/**`。
- 触发场景：任何一次正式包的线上崩溃。
- 影响：本项目自带崩溃上报（`lumen-crash`）与 `CrashReportPasteUploader`，但拿到的堆栈全是 `a.b.c(SourceFile:1)`——**混淆栈永久无法还原**，因为 mapping 随 runner 销毁而丢失。这会直接导致 G10-01、G10-05 这类"只在 release 崩"的问题无从定位。叠加 `:116-120` 的 `-repackageclasses` / `-overloadaggressively`，人工反推的可能性也基本为零。
- 修复方案：① 删掉 `app/proguard-rules.pro:121` 的 `-printmapping mapping.txt`（AGP 默认就会输出到 `app/build/outputs/mapping/<variant>/mapping.txt`，显式指定相对路径只会把它写到 R8 任务的工作目录去、并让 AGP 的产物注册失效）；② 在 `build.yml:452` 与 `release.yml:95` 的 `Upload verification reports` 的 `path` 里各加一行 `app/build/outputs/mapping/**`；③ 更彻底的做法是把 `mapping.txt` 一并放进 `release-assets/`（`build.yml:274` / `release.yml:200` 的 `Prepare release assets`），使其与 APK 同生命周期归档。
- 风险/注意：mapping 文件**会显著降低逆向难度**——它是反混淆字典。若归档到 public release assets 就等于公开了混淆映射，与 `:116-120` 的"反逆向加固"意图直接冲突。建议只上传为 artifact（默认 `retention-days` 内仅协作者可下），**不要**放进公开 release assets。这一点必须和作者确认取舍。

### [G10-09] `release.yml` 不回填 release 证书 SHA-256，正式 tag 包的完整性门禁被静默关闭
- 严重度：P1
- 类别：G 安全
- 位置：`.github/workflows/release.yml:181`，对照 `.github/workflows/build.yml:141-145` 与 `:200`；消费点 `app/build.gradle.kts:154`
- 现状：
  ```yaml
  # build.yml:141-145 —— 从 keystore 现算 cert SHA-256 并回填到 env
  CERT_SHA256="$(keytool -exportcert ... | openssl dgst -sha256 -hex | awk '{print toupper($2)}')"
  echo "PROJECT_LUMEN_RELEASE_CERT_SHA256=${CERT_SHA256}" >> "$GITHUB_ENV"
  # build.yml:200 —— secret 缺失时兜底用现算值
  PROJECT_LUMEN_RELEASE_CERT_SHA256: ${{ secrets.PROJECT_LUMEN_RELEASE_CERT_SHA256 || env.PROJECT_LUMEN_RELEASE_CERT_SHA256 }}

  # release.yml:181 —— 只有 secret，没有 keytool 现算、没有 || env 兜底
  PROJECT_LUMEN_RELEASE_CERT_SHA256: ${{ secrets.PROJECT_LUMEN_RELEASE_CERT_SHA256 }}
  ```
  `release.yml` 的 `Write signing config`(`:107-164`) 相比 `build.yml` 的同名步骤，**恰好少了 `:141-145` 这段现算 + 回填**。而 `app/build.gradle.kts:154` 是 `APP_INTEGRITY_ENFORCEMENT_ENABLED = projectLumenReleaseCertSha256.isNotBlank()`。
- 触发场景：`PROJECT_LUMEN_RELEASE_CERT_SHA256` secret 未配置（而 `build.yml` 因为有现算兜底，此时表现正常，掩盖了问题）时打一个 `v*` tag。
- 影响：**同一份源码，两条流水线产出的安全姿态不同**——`build.yml` 的分支包开启了完整性门禁与原生证明校验，而 `release.yml` 的正式 tag 包 `APP_INTEGRITY_ENFORCEMENT_ENABLED=false`、`.so` 里的 `LUMEN_RELEASE_CERT_SHA256` 为空串，重打包/篡改检测完全失效。正式版反而比试验版更不安全，且不会有任何告警。
- 修复方案：把 `build.yml:141-145` 那 5 行原样搬进 `release.yml` 的 `Write signing config` 步骤（插在 `:158` 的 `cat /tmp/keystore-alias.txt` 之后），并把 `:181` 改为 `${{ secrets.PROJECT_LUMEN_RELEASE_CERT_SHA256 || env.PROJECT_LUMEN_RELEASE_CERT_SHA256 }}`。更好的做法是把两个工作流共有的 `Write signing config` 抽成一个 composite action（`.github/actions/` 下已有此模式），彻底消除两份脚本漂移——现在这两段近 60 行的脚本已经不一致了，这就是漂移的证据。
- 风险/注意：与 G10-04 的 `require` 断言配合时注意顺序：若先加了 G10-04 的断言而没修这条，`release.yml` 会**直接构建失败**（这比静默关闭好，但会挡住发版）。建议两条一起改。

### [G10-10] `lumen-ui-tuner.yml` 在带 USER_PAT 的工作区里执行 `npm install`，并把重新生成的 lockfile 提交推回分支
- 严重度：P1
- 类别：G 安全 / A 架构与设计
- 位置：`.github/workflows/lumen-ui-tuner.yml:53-56`（checkout 带 PAT）、`:62-66`（`npm install`）、`:68-88`（提交并推 lockfile）
- 现状：
  ```yaml
  - uses: actions/checkout@v5
    with:
      token: ${{ secrets.USER_PAT || github.token }}   # :55  未设 persist-credentials: false
      fetch-depth: 0
  - working-directory: tools/lumen-ui-tuner
    run: |
      npm install        # :64  非 npm ci；执行全部依赖的 postinstall
      npm run build
  - name: Commit generated tuner lockfile                # :68-88
    run: |
      git add tools/lumen-ui-tuner/package-lock.json
      ... git push origin "HEAD:refs/heads/$BRANCH_NAME"
  ```
- 触发场景：改动 `tools/lumen-ui-tuner/**`、`design/lumen-ui-tokens.json`、`app/build.gradle.kts` 或几个指定的 Kotlin UI 文件时（`:6-18` 的 paths 过滤）触发。
- 影响：（a）任一 npm 依赖（含传递依赖）的 `postinstall` 脚本都在一个 `.git/config` 里存着 USER_PAT 的工作区中执行，可直接读取并外传该 PAT；（b）`npm install`（而非 `npm ci`）会按 semver 范围重新解析依赖，CI 随后把**它自己生成的** lockfile 提交推回分支——依赖版本的真相源从人手里转移到了 CI 的解析时刻，静默的传递依赖升级会被自动固化进仓库。
- 修复方案：① `:55` 后补 `persist-credentials: false`，并把 `:68-88` 的推送改为显式带 token 的 URL；如果这个 job 只需要读仓库，直接把 `token:` 改成 `${{ github.token }}`；② `:64` 改为 `npm ci`（`tools/lumen-ui-tuner/package-lock.json` 已在仓库中），并删掉 `:68-88` 整个 `Commit generated tuner lockfile` 步骤——lockfile 应由开发者本地 `npm install` 后手工提交；③ `reactivecircus/android-emulator-runner@v2`(`:124`)、`actions/checkout@v5`、`actions/setup-node@v5` 等第三方 action 改为 SHA 锁定。
- 风险/注意：改成 `npm ci` 后，若 `package.json` 与 `package-lock.json` 不同步会**直接构建失败**（这是期望行为，但第一次改动时可能需要先本地同步一次 lockfile 并提交）。删掉自动提交步骤后，忘记提交 lockfile 的 PR 会在 CI 挂掉——这正是应有的反馈。

### [G10-11] Manifest 完全没有 `<queries>`，改用 `QUERY_ALL_PACKAGES` 兜住包可见性
- 严重度：P1
- 类别：G 安全
- 位置：`app/src/main/AndroidManifest.xml:35-37`（`QUERY_ALL_PACKAGES`），全文**无 `<queries>` 元素**
- 现状：
  ```xml
  <uses-permission
      android:name="android.permission.QUERY_ALL_PACKAGES"
      tools:ignore="QueryAllPackagesPermission" />
  ```
  `tools:ignore` 把 lint 的告警压掉了。功能上这确实让 Shizuku / Clash 集成的 `resolveActivity` / `queryIntentActivities` 能正常工作（所以不是功能缺陷），但代价是拿到了**读取全部已安装应用清单**的最高权限。
- 触发场景：常态。应用一旦安装即持有该权限。
- 影响：（a）隐私面过大——这是最敏感的一类信号，一个护眼应用持有它很难解释，且与项目自带的"隐私中心"叙事冲突；（b）若未来上架 Google Play，`QUERY_ALL_PACKAGES` 属受限权限，需要提交声明，多数护眼类用途会被驳回；（c）`tools:ignore` 让这个决定失去了后续复核的提醒。
- 修复方案：用 `<queries>` 精确声明需要看见的目标，然后删掉 `QUERY_ALL_PACKAGES`。需要 `rg` 出全部 `resolveActivity` / `queryIntentActivities` / `getPackageInfo` 调用点来确定清单，典型形态如下（具体 action/package 需按实际调用点补全）：
  ```xml
  <queries>
      <package android:name="moe.shizuku.privileged.api" />
      <package android:name="com.github.kr328.clash" />
      <intent><action android:name="android.intent.action.VIEW" /><data android:scheme="https" /></intent>
      <intent><action android:name="android.settings.SETTINGS" /></intent>
  </queries>
  ```
  另一种情形：若"按应用网络管控"功能本质上就是要枚举用户所有应用给用户勾选，那 `QUERY_ALL_PACKAGES` 是**不可避免**的，此时应当保留，但要在 `tools:ignore` 旁写清理由，并在隐私中心明示。
- 风险/注意：**这条不能盲改**。删掉 `QUERY_ALL_PACKAGES` 而 `<queries>` 漏了某个目标，会让对应的 `resolveActivity` 恒返回 null——正是 brief 里点名的那类静默失效，且只在 Android 11+ 真机上体现。修复前必须先把调用点枚举完整（涉及 G05 Shizuku 组 / G07 网络组的文件，建议跨组确认后再动）。

### [G10-12] 原生工具链步骤在 3 个工作流里被注释掉，CI 预装的 NDK 版本与 `gradle.properties` 钉的版本不一致；16 KB 对齐校验也被注释
- 严重度：P1
- 类别：A 架构与设计 / E 韧性
- 位置：`.github/workflows/build.yml:55-56`、`release.yml:52-53`、`codeql.yml:78-80`、`lumen-ui-tuner.yml:104-105`（四处被注释的 composite action 调用）；`build.yml:41`+`:45`、`release.yml:38`+`:42`、`codeql.yml:57`+`:60`（硬编码的 `ndk;30.0.15729638`）；`gradle.properties:7-8`；`.github/actions/setup-android-native-toolchain/action.yml:15-16`+`:36`；`release.yml:197-198`（被注释的 16 KB 校验）；`app/build.gradle.kts:46`、`:171-180`、`:240-245`
- 现状：
  ```yaml
  # build.yml:41 —— 硬编码安装 NDK 30.0.15729638，失败被 || true 吞掉
  "$SDK_MANAGER" --install "ndk;30.0.15729638" 2>&1 || true
  # build.yml:55-56 —— 真正会按 gradle.properties 安装正确版本的步骤被注释
  # - name: Set up Android native toolchain
  #   uses: ./.github/actions/setup-android-native-toolchain
  # release.yml:197-198 —— 16 KB 对齐校验被注释
  # - name: Verify 16 KB native library alignment
  #   run: python3 scripts/verify_android_16kb_alignment.py app/build/outputs/apk/release/*.apk
  ```
  ```properties
  # gradle.properties:7-8 —— 实际被 AGP 使用的版本
  projectLumenNdkVersion=28.2.13676358
  projectLumenCmakeVersion=3.22.1
  ```
  被注释的 composite action 恰恰是唯一正确的那个：它从 `gradle.properties` 读出版本再装（`action.yml:15-16`+`:36`），而现在留下的是一句硬编码 `ndk;30.0.15729638`——**版本号与 `projectLumenNdkVersion` 不符**，且 `cmake;3.22.1` 完全没装。
- **关于"release 包有没有 `liblumen_security.so`"的结论（团队点名要）：有，不存在 `UnsatisfiedLinkError` 发版即崩风险。** 依据三条：① `app/build.gradle.kts:240-245` 的 `externalNativeBuild.cmake.path` 是**无条件**配置的，`:46` 的 `ndkVersion = providers.gradleProperty("projectLumenNdkVersion").get()` 也是无条件的（`.get()` 缺属性即抛异常），因此原生构建不是"可跳过"的——NDK 不可用时 `assembleRelease` 会**硬失败**，而不会静默产出缺 `.so` 的 APK；换言之 CI 只要是绿的，`.so` 就在包里。② AGP 在 SDK 许可已接受（`build.yml:40`）的情况下会自动下载 `ndkVersion` 指定的 NDK 与 `cmake` 版本，所以缺少 composite action 只是让下载发生在 gradle 阶段而非预置阶段。③ 即便 `.so` 真的缺失，Kotlin 侧 `core/security/NativeSecurityBridge.kt:4-8` 已经用 `runCatching { System.loadLibrary(...) }` + `isAvailable` 做了降级，全部 `external fun` 都有 `...OrNull()` 包装（`:10-42`），只会静默降级不会崩。**真正的风险是这个降级太安静**：`requestSigningSecretOrNull()` 返回 null 时请求签名与完整性校验一起失效，没有任何遥测——但那属于 G06 安全组的判断，本组不重复报。
- 触发场景：（a）每次 CI 都白下一个用不上的 NDK（约 1 GB 级），三个工作流各下一次；（b）`|| true` 吞掉安装失败，一旦 AGP 的自动下载被禁用或网络抖动，报错会推迟到 gradle 阶段、以晦涩的 CMake/NDK 错误呈现；（c）16 KB 对齐从未被校验。
- 影响：CI 时长与流量的稳定浪费；原生工具链版本实际由 AGP 自动下载决定而非由仓库钉死，**可复现性打折**；16 KB 页对齐（Android 15+/16 的硬要求，`app/src/main/cpp/CMakeLists.txt:7-9` 已加 `-Wl,-z,max-page-size=16384`，`app/build.gradle.kts:247-252` 也设了 `useLegacyPackaging = false`）没有任何自动化验证，而 `pickFirsts += "**/libc++_shared.so"`(`:250`) 引入的那份 `libc++_shared.so` 来自 NDK 而不是本项目 CMake，其对齐性完全取决于实际生效的 NDK 版本——恰恰是当前最不确定的那一项。
- 修复方案：① 取消 `build.yml:55-56`、`release.yml:52-53`、`codeql.yml:78-80` 三处注释，启用 `./.github/actions/setup-android-native-toolchain`（它会装对版本，也会装 cmake）；② 同时删掉 `build.yml:41`+`:45`、`release.yml:38`+`:42`、`codeql.yml:57`+`:60` 六行硬编码的 `ndk;30.0.15729638` 安装；③ 取消 `release.yml:197-198` 的注释，启用 `scripts/verify_android_16kb_alignment.py`（该脚本 105 行，自行解析 ELF 的 `PT_LOAD` 对齐与 ZIP 内偏移，实现是完整可用的），并把同一步骤也加进 `build.yml`（插在 `:219 Inspect APK signing` 之后）。
- 风险/注意：注释这些步骤大概率是当初为了绕过某次 CI 失败（`action.yml:36` 的 `yes | sdkmanager ...` 在部分 runner 镜像上会因 `set -e` + SIGPIPE 而非零退出）。恢复时若再次失败，正确做法是给那一行加 `|| true` **仅针对 `yes` 管道**、或改用 `sdkmanager --sdk_root=... --install`，而不是把整步注释掉。启用 16 KB 校验后可能立刻发现现存包不合规——这是暴露真实问题，不要用注释再压回去。

### [G10-13] 3 个单元测试是"源码文本断言"型，会硬性锁死后续重构方式（修复阶段必读）
- 严重度：P2
- 类别：H 编译与结构 / D 生命周期与框架约束
- 位置：`app/src/test/java/com/projectlumen/app/core/api/BackendCommunicationArchitectureTest.kt`(92 行)、`core/services/ForegroundServiceArchitectureTest.kt`(60 行)、`core/security/AppIntegrityGuardScopeTest.kt`(38 行)
- 现状：这三个测试不调用被测代码，而是用 `File(...).readText()` 去 grep **生产源码的字面文本**。以下是全部断言字符串，**修复其他组的缺陷时改动到这些文件就会挂测试，且报错发生在测试阶段而非编译阶段**：

  `ForegroundServiceArchitectureTest.kt`
  - `:16` 这 5 个文件必须各自出现字面量 `ForegroundServiceController.promote(`：`core/services/TimerForegroundService.kt`、`core/proximity/ProximityDetectionService.kt`、`core/light/LightMonitorService.kt`、`core/overlay/EyeProtectionOverlayService.kt`、`core/debug/DeveloperDebugOverlayService.kt`（`:57-64` 的清单；文件被改名/拆分/删除也会挂）
  - `:32-33` 除 `ForegroundServiceController.kt` 外，**任何 .kt 文件**都不得出现 `ContextCompat.startForegroundService(` 或 `ServiceCompat.startForeground(`

  `AppIntegrityGuardScopeTest.kt`
  - `:16-17` `core/security/AppIntegrityGuard.kt` 必须存在字面量 `fun enforce(context: Context) {`，且其后紧跟的下一个 `\n    fun ` 之前构成"函数体"
  - `:19`+`:26` 该函数体内必须出现 `Application.getProcessName() != appContext.packageName`，且其位置**必须在** `NativeSecurityBridge.isNativeEnvironmentAllowedOrNull` 之前
  - `:22` 该函数体内必须出现 `return`

  `BackendCommunicationArchitectureTest.kt`
  - `:11-21` `core/api/ProjectLumenApiClient.kt`：以 `private suspend fun <T> request(` 为分界，其之前每一处 `= request(` 后面必须紧接 `capability = BackendCapability.`
  - `:24-29` 同文件中 `backendGate.requireExecutable(capability)` 的字符位置必须早于 `ProjectLumenRequestSigner.headers` 和 `httpClient.newCall(request).execute()`
  - `:35-41` 全源码树中出现 `ProjectLumenApiClient(` 的文件**有且仅有** `ProjectLumenApplication.kt` 一个（新增任何构造点或测试替身都会挂）
  - `:43-45` `ProjectLumenApplication.kt` 须含 `ProjectLumenApiClient(`、`backendGate = backendConnectivity`、`healthProbe = { apiClient.health() }`
  - `:48` `core/update/UpdateChecker.kt` 须含 `backendGate.decision(BackendCapability.RELEASE_DISCOVERY)`
  - `:50-51` `app/ProjectLumenApp.kt` 须含 `apiClient = application.apiClient`、`backendGate = application.backendConnectivity`
  - `:57-58` `app/ProjectLumenTranslationScreen.kt` 须含 `ProjectLumenTranslationApiClient` 且**不得**含 `BackendCapability`
  - `:61-62` `core/telemetry/EyeCareTelemetryReporter.kt` 须含 `decision(BackendCapability.TELEMETRY)` 与 `decision(BackendCapability.FACE_ANALYSIS)`
  - `:64` `core/proximity/ProximityDetectionService.kt` 须含 `decision(BackendCapability.FACE_ANALYSIS)`
  - `:66-67` `core/devicecontrol/PrivilegedDeviceControlCoordinator.kt` 须含 `decision(BackendCapability.DEVICE_CONTROL)` 与 `onBackendUnavailable`
  - `:73-76` `app/ProjectLumenSettingsScreen.kt` 须含 `if (backendFeaturesVisible)`、`RemoteCloudAccountCard(`、`cloudCapabilityVisible = backendFeaturesVisible`、`backendFeaturesVisible = backendFeaturesVisible`
  - `:79-80` `app/ProjectLumenSettingsPrivacyCenter.kt` 须含 `if (backendFeaturesVisible)`、`PermissionSetupTarget.DIAGNOSTICS`
  - `:82-83` `app/ProjectLumenShizukuSettingsSection.kt` 须含 `if (backendFeaturesVisible)`、`ShizukuDiagnosticUploadSettings`
  - `:86` `app/ProjectLumenDeveloperDebugScreen.kt` 须含 `BackendConnectivityDeveloperControls(`
  - `:88-89` `app/ProjectLumenBackendConnectivityDeveloperControls.kt` 须含 `backend_connectivity_force_enable`、`onRefresh`
- 触发场景：任何针对上述 20 余个生产文件的重命名、参数名调整、抽取函数、换用命名参数、格式化换行（会破坏 `= request(` 紧邻 `capability = ` 这类断言）。
- 影响：这些断言把架构约束落在了**文本层**而非类型层，重构成本被人为放大；报错信息（如 `"$relativePath must promote through ForegroundServiceController"`）不指向真正的原因；`:12-14` 那种 `substringBefore` / `split` 的解析对格式极其敏感，一次 ktlint 格式化就可能全红。
- 修复方案：本组不建议在本次修复中删除它们（它们确实在守护真实的架构约束）。正确处置分两步：① **修复阶段先记住这份清单，改动上述文件后同步更新断言**；② 中期把每条文本断言换成真正的行为测试——例如 `ForegroundServiceArchitectureTest` 改为给 `ForegroundServiceController` 注入一个假 starter 并断言"每个 service 的启动路径都经过它"；`BackendCommunicationArchitectureTest:24-29` 的顺序断言改为在 `ProjectLumenApiClient` 里注入 spy 的 gate/signer/httpClient 并断言调用次序。
- 风险/注意：`:35-41` 那条"`ProjectLumenApiClient(` 只能出现在 `ProjectLumenApplication.kt`"尤其危险——**给 `ProjectLumenApiClient` 写单元测试会直接让这条测试变红**。如果修复阶段要补 API 客户端的测试（G10-18 提到的覆盖盲区），必须同时放宽这条断言（把过滤条件加上 `app/src/test` 排除，注意它 `:35` 的 `walkTopDown()` 起点是 `app/src/main/java/com/projectlumen/app`，测试目录本不在范围内——所以真正会踩到的是"在 main 里新增任何工厂/DI 容器"这种改法）。

### [G10-14] `applicationId` 存在 3 个独立的真相源，改包名会让 CI 在发版前一步挂掉
- 严重度：P2
- 类别：A 架构与设计
- 位置：`app/build.gradle.kts:13`（`val projectLumenApplicationId = "com.chloemlla.projectlumen"`）、`baselineprofile/src/main/java/com/projectlumen/baselineprofile/BaselineProfileGenerator.kt`（companion 里 `TARGET_PACKAGE = "com.chloemlla.projectlumen"`）、`scripts/run-lumen-topbar-screenshot-test.sh:43-44`（`adb uninstall com.chloemlla.projectlumen.test` / `com.chloemlla.projectlumen`）、`:46`（`adb pull /sdcard/Android/data/com.chloemlla.projectlumen/...`）
- 现状：三处各写一遍字面量 `com.chloemlla.projectlumen`，彼此没有任何联系。`baselineprofile` 那一份最要紧——`build.yml:163-183` 的 `Generate release baseline profile` 排在 `Build release APK`(`:185`) 之前且没有 `continue-on-error`。
- 触发场景：改 `applicationId`（或加多环境后缀）时只改了 `app/build.gradle.kts:13`。
- 影响：`BaselineProfileGenerator` 找不到目标包 → 抛出它自带的 `error("Target package ... failed to stay running after launch ...")` → **整个 `build.yml` 在发布前中断**，而报错文本还会把人引向"Application/Activity 崩溃、缺 x86_64 库、完整性门禁"三个错误方向（该 message 明文列了这三个猜测）。同理截图脚本的 `adb uninstall` 会静默无效（有 `|| true`），残留旧包导致签名冲突安装失败。
- 修复方案：让 `baselineprofile` 从 `:app` 拿包名而不是硬编码。最简做法是在 `baselineprofile/build.gradle.kts` 的 `defaultConfig` 里加 instrumentation 参数：
  ```kotlin
  testInstrumentationRunnerArguments["lumenTargetPackage"] = "com.chloemlla.projectlumen"
  ```
  生成器侧改为 `InstrumentationRegistry.getArguments().getString("lumenTargetPackage")`；再把那个字面量提取成根级 `gradle.properties` 的一个属性，供 `app` 与 `baselineprofile` 共同引用。脚本侧改为从新增的 `app/application.id` 文件读取（仓库已有 `app/application.version` 的先例，照抄这个模式最自然）。
- 风险/注意：改 `testInstrumentationRunnerArguments` 会影响 `baselineprofile/build.gradle.kts:18-19` 已有的 `androidx.benchmark.suppressErrors` 参数，追加而不是覆盖。若嫌改动面大，最低成本的缓解是在 `app/build.gradle.kts:13` 旁加注释指出另外两个副本位置——但那只是缓解，不是修复。

### [G10-15] `minSdk 29` 下 4 个 `styles.xml` 变体中的 `Theme.ProjectLumen` 永远不会生效
- 严重度：P2
- 类别：D 生命周期与框架约束
- 位置：`app/src/main/res/values/styles.xml:3-8`、`values-night/styles.xml:3-9`、`values-v28/styles.xml`（整文件）、`values-night-v28/styles.xml`（整文件）；对照 `app/build.gradle.kts:159`（`minSdk = 29`）
- 现状：6 个 `styles.xml` 把 `Theme.ProjectLumen` 定义了 6 遍（`values` / `values-night` / `values-v28` / `values-night-v28` / `values-v29` / `values-night-v29`）。因为 `minSdk = 29`，**任何能装上这个 app 的设备都满足 `-v29`**，资源限定符匹配总选中 `-v29` 那份，于是：
  - `values-v28/styles.xml` 与 `values-night-v28/styles.xml` 两个文件**整体是死资源**
  - `values/styles.xml:3-8` 与 `values-night/styles.xml:3-9` 里的 `Theme.ProjectLumen` 运行时也从不生效（只在编译期参与资源定义）
  - 真正生效的只有 `values-v29`（浅色）与 `values-night-v29`（深色）
- 触发场景：任何人为调整状态栏/导航栏/刘海行为去改 `values/styles.xml` 或 `values-v28/styles.xml`——改完打包，**真机上毫无变化**，极易被误判成"缓存问题"而反复折腾。
- 影响：不是运行时 bug，而是高概率的排查陷阱。6 份定义里 4 份是噪声，且它们之间已出现属性差异（只有 `-v29` 两份带 `android:enforceNavigationBarContrast`；`-v28`/`-v29` 四份带 `android:windowLayoutInDisplayCutoutMode`，`values`/`values-night` 两份没有），进一步强化了"改哪份都可能对"的错觉。
- 修复方案：删掉 `values-v28/styles.xml` 与 `values-night-v28/styles.xml`；把 `values-v29/styles.xml` 的全部属性合并回 `values/styles.xml`、`values-night-v29/styles.xml` 合并回 `values-night/styles.xml`，再删掉两个 `-v29` 目录下的 styles.xml。最终只留两份，各含完整属性集。
- 风险/注意：`Theme.Lumen.TransparentOverlay`（`RestOverlayActivity` 使用，见 `AndroidManifest.xml:91`）**只在 `values/` 与 `values-night/` 里定义**，合并时不要漏掉它；它的 `parent="Theme.ProjectLumen"` 按运行时配置解析，合并后行为不变。合并后务必确认 `android:windowLayoutInDisplayCutoutMode` 与 `android:enforceNavigationBarContrast` 都进了两份最终文件，否则刘海机与导航栏配色会回退。

### [G10-16] CodeQL 未覆盖 C/C++，且只扫 `main` 分支，而 `build.yml` 从任意分支发版
- 严重度：P2
- 类别：A 架构与设计
- 位置：`.github/workflows/codeql.yml:36-40`（语言矩阵）、`:5-9`（分支限定）、`:89-94`（死分支）、`:22`（超时）、`:102-104`（构建步骤）
- 现状：
  ```yaml
  matrix:
    include:
      - language: java-kotlin
        build-mode: manual
      - language: actions
        build-mode: default
  ```
  矩阵只有 `java-kotlin` 与 `actions`，**没有 `c-cpp`**。而 `app/src/main/cpp/lumen_security.cpp` 是整个安全模型的根（编译进 `.so` 的签名密钥、证书 SHA-256、包名校验，代码里还有 `extern "C" char **environ` 遍历、`/proc/self/cmdline` 解析等易出内存问题的处理），恰恰最该被静态扫描，却零覆盖。另外 `:89-94` 的 `build-mode == 'none'` 分支在当前矩阵下永远不成立，是死配置。
- 触发场景：`:5-9` 限定只在 `main` 的 push/PR 与每周定时（`:10-11`）运行。而 G10-02 已指出 `build.yml` 会从任意分支发布正式包——**发出去的包可能从未被 CodeQL 扫过**。
- 影响：原生安全层的内存安全问题（缓冲区、格式化字符串、`environ` 越界）无任何自动化检出；非 main 分支的产物在没有安全扫描的情况下直达用户。
- 修复方案：① `:36-40` 矩阵增加 `- language: c-cpp` + `build-mode: manual`；`:102` 现有的 `gradle assembleDebug` 步骤同时满足两种语言，只需把它的 `if: matrix.language == 'java-kotlin'` 放宽为 `if: matrix.build-mode == 'manual'`（同理 `:46`/`:52`/`:70` 三个 setup 步骤的条件也要放宽）；② 删掉 `:89-94` 的死分支；③ `:22` 的 `timeout-minutes: 30` 对"完整 Android debug 构建 + CodeQL 建库"偏紧，抬到 60。
- 风险/注意：加 `c-cpp` 必须与 G10-12 一起做（恢复 `codeql.yml:78-80` 的原生工具链步骤），否则新矩阵项拿不到编译数据会直接失败。首次启用 c-cpp 大概率报出一批新告警，需预留处理时间；`security-events: write` 权限（`:26`）已具备，无需调整。

### [G10-17] 仓库里提交了约 14 MB 的陈旧源码归档（`Project-Lumen.zip` / `*.7z`）
- 严重度：P2
- 类别：A 架构与设计 / G 安全
- 位置：仓库根 `Project-Lumen.zip`（596,686 字节，334 个条目，**已被 git 跟踪**）、`Project-Lumen-flutter-2026.6.28.7z`（13,893,454 字节，**已被 git 跟踪**）
- 现状：`git ls-files` 确认两者都在版本控制中。我只读检查了 zip 的条目清单，**未发现 `.jks` / `.keystore` / `.env` / `.pem` / `.p12` / `*.properties` 等敏感文件**（这一点是好消息）；`.7z` 在不解压的情况下无法核查条目，内容未知。
- 触发场景：任何一次 `git clone`。
- 影响：（a）约 14 MB 二进制永久留在 git 历史里，clone 成本与仓库体积被持续抬高，删文件也回收不了；（b）zip 里是一份完整的陈旧源码快照——若被解压到工作树，全仓 `rg` 会命中副本，制造"同一事实的第二个真相源"；（c）`.7z` 内容未经核查，是一个未知的敏感信息暴露面。
- 修复方案：`git rm --cached Project-Lumen.zip Project-Lumen-flutter-2026.6.28.7z`，在 `.gitignore` 追加 `*.zip` / `*.7z`，本地文件移到仓库外保存（**不要直接删**）。若要真正回收体积需 `git filter-repo` 重写历史，那会改写所有 commit hash，属高风险操作。
- 风险/注意：**先核查 `.7z` 的内容再决定处置方式。** 如果里面含任何凭据，"从当前提交删除"是不够的（历史里仍可取回），必须走凭据轮换 + 历史重写。历史重写与凭据轮换都属仓库所有者决策，本报告只报不改，也不擅自解压。

### [G10-18] 高风险模块的单测覆盖盲区（并发、时间边界、持久化顺序、请求签名）
- 严重度：P2
- 类别：H 编译与结构
- 位置：`app/src/test/`（20 个文件，1547 行）
- 现状：扣掉 G10-13 的 3 个文本断言测试，真正的行为测试有 17 个，全部集中在**纯函数式策略层**：`BackendCommunicationPolicyTest`(95)、`BackendConnectivityControllerTest`(136)、`ReminderEngineTest`(148)、`PomodoroEngineTest`(70)、`DeviceInsightAnalyzerTest`(110)、`CertificatePinPolicyTest`(22)、`SecureOkHttpFactoryTest`(39)、`ForegroundServiceControllerTest`(63)、`ForegroundServiceStartEligibilityTest`(34)、`ProximityCameraForegroundEligibilityTest`(51)、`ProjectLumenAppNetworkControlStateTest`(124)、`ShizukuNetworkRestrictionStateTest`(82) 等。这些测试质量不错（`BackendCommunicationPolicyTest` 覆盖了 TTL 边界、开发者强开、退避上界；`ForegroundServiceControllerTest` 按 `Build.VERSION_CODES` 分档）。**完全没有测试的高风险区**：
  - `ProjectLumenStateStore` 的 `combine` + `stateIn` 合成（brief 点名的单向状态流枢纽）
  - MMKV 与 Room 的写入顺序（brief 的 F 类硬约束"MMKV 必须先于 Room"），无任何测试锁定
  - `AlarmReceiver` / `TimerReconciliationWorker` 的对账与时间边界（跨零点、系统时间被改）
  - `ProjectLumenRequestSigner` 的 HMAC 签名产出（正是 G10-04 涉及的密钥路径）
  - `ShizukuShellUserService.execute`（`:29-66`，3 个线程 + `join` 超时 + `process.destroy()` 的并发逻辑，典型易错点，零测试）
  - Room 迁移与 `app/schemas` 导出的一致性
- 触发场景：上述任一模块被修改时 CI 不给任何反馈——`testDebugUnitTest` 全绿不代表这些路径没被改坏。
- 影响：这是本次审查中其他组报出的并发/持久化缺陷在修复后**无法被回归保护**的根因。修复阶段改状态流或 MMKV/Room 顺序时，正确性只能靠人工 review。
- 修复方案：优先补三个，投入产出比最高且都能在纯 JVM 跑：① `ProjectLumenRequestSigner` 的签名向量测试（固定 secret + 固定输入 → 固定签名，顺带锁死 G10-04 的密钥读取路径）；② MMKV/Room 写入顺序测试（用假 store 接口记录调用序列并断言先后）；③ 时间边界用例（`ReminderEngineTest` / `PomodoroEngineTest` 已有骨架，补跨零点与时间回拨）。`ProjectLumenStateStore` 需要新增 `testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")`（当前 `app/build.gradle.kts:344-345` 只有 `junit:junit:4.13.2` 与 `org.json:json:20240303`）。
- 风险/注意：补 `ProjectLumenApiClient` 相关测试前先看 G10-13 关于 `BackendCommunicationArchitectureTest:35-41` 的提醒。所有新测试必须避免触碰 `LumenToast`——`core/toast/LumenToast.kt:86` 是 `object`，`:90` 有 **eager** 的 `Handler(Looper.getMainLooper())`，纯 JVM 下类初始化即 NPE → `ExceptionInInitializerError`。这正是 brief 点名的历史事故形态；目前恰好没有测试碰到它，但它仍是活雷（`core/network/ClashPartnerCompat.kt:87` 已改成 `by lazy` 修好了同类问题，`LumenToast` 没跟上）。该文件归属其他组，故不单列缺陷，只在此提示。

### [G10-19] 打包进 APK 的"更新说明"直接展示整段 commit body，会把内部提交信息带给用户
- 严重度：P2
- 类别：G 安全
- 位置：`scripts/generate_build_update_notes.py:72`（`"body": body`）、`:60-61`（取 `%b`）；调用点 `.github/workflows/build.yml:74-83`（输出到 `app/src/main/assets/build-update-notes.json`）
- 现状：
  ```python
  # :60-61 取完整 commit body，:72 原样写进随包资源
  body = git_message(args.repository_root, args.commit_hash, "%b")
  ...
  "body": body,
  "highlights": extract_highlights(subject, body),
  ```
  `extract_highlights`(`:41-56`) 只抽 `- ` / `* ` 开头的行做要点，但 `body` 字段本身是**未加工全文**，会随 APK 分发并在应用内展示。
- 触发场景：本仓库的提交信息由 agent 自动生成，body 里常含内部文件路径、模块名、工具署名尾注（`Co-Authored-By:` 等），以及"修复 XX 越界崩溃"这类实现细节。
- 影响：用户看到的"更新内容"混入内部实现细节与开发工具痕迹；若某次提交信息提到了尚未修复的安全问题或内部主机名，等于随包公开。与本项目"用户可见文案只讲效果与隐私影响、不展开实现细节"的既有约定冲突。
- 修复方案：在 `scripts/generate_build_update_notes.py` 里过滤 body：① 丢弃 trailer 行（`^[A-Za-z-]+:\s` 形式）；② 把 `payload["body"]` 改为 `"\n".join(highlights)`，让 body 与要点一致；③ 更彻底的做法是引入人工维护的 `app/release-notes/<versionName>.md`，脚本只在该文件缺失时回落到 commit subject。
- 风险/注意：消费侧是 `core/update/BuildUpdateNotesParser`（有 `BuildUpdateNotesParserTest`，88 行）。只改 `body` 的**内容**而不动 JSON 字段名，则该测试不受影响；若要改结构必须同步改测试。另注意 `app/src/main/assets/` **整个目录都不在 git 跟踪范围内**（`git ls-files app/src/main/assets` 为空输出），该 JSON 只在 CI 里存在——本机/IDE 构建时该资源缺失，解析器必须能容忍缺失（请 G09 组确认）。

### [G10-20] `android:intentMatchingFlags="enforceIntentFilter"` 与两个无 intent-filter 的 receiver 可能冲突（需确认）
- 严重度：P2（需确认）
- 类别：D 生命周期与框架约束
- 位置：`app/src/main/AndroidManifest.xml:49`；受影响组件 `:110-112`（`AlarmReceiver`，无 intent-filter）、`:122-124`（`ReminderActionReceiver`，无 intent-filter）
- 现状：
  ```xml
  <application ... android:intentMatchingFlags="enforceIntentFilter" ...>   <!-- :49 -->
      <receiver android:name=".core.services.AlarmReceiver" android:exported="false" />          <!-- :110-112 无 filter -->
      <receiver android:name=".core.services.ReminderActionReceiver" android:exported="false" /> <!-- :122-124 无 filter -->
  ```
  这两个 receiver 靠 `AlarmManager` 与通知的 `PendingIntent` 投递（显式 Intent，无 filter）。`enforceIntentFilter` 是 Android 16 引入的严格 Intent 匹配开关，其约束主要针对**跨应用**投递与隐式 Intent，同应用显式 Intent 与 PendingIntent 通常在豁免范围内——所以这**很可能没有问题**；但 `targetSdk = 37` 叠加这个显式开关，风险窗口真实存在，且失效形态是静默的。
- 触发场景：Android 16+ 真机上闹钟到点、或用户点击通知上的操作按钮。
- 影响：若判断有误，则精确闹钟与通知操作按钮在 Android 16+ 上被系统丢弃——计时/提醒功能整体失效，且只在高版本系统上出现，低版本测试机全绿。
- 修复方案：**先确认**（查 Android 16 `enforceIntentFilter` 的豁免范围文档，或在 API 36+ 模拟器上验证 `AlarmReceiver` 是否收到广播）。若确有影响，最小改动是给两个 receiver 各加一个自有 action 的 intent-filter：
  ```xml
  <receiver android:name=".core.services.AlarmReceiver" android:exported="false">
      <intent-filter><action android:name="com.chloemlla.projectlumen.action.ALARM_TICK" /></intent-filter>
  </receiver>
  ```
  并让调度侧的 Intent 带上该 action。**不要**用删掉 `intentMatchingFlags` 的方式绕过——那会一并放弃 Intent 重定向防护。
- 风险/注意：标注"需确认"，请勿在未验证前改动。给 receiver 加 action 需要同步改调度处（`core/services/` 下构造 PendingIntent 的地方，属 G03 组文件），跨组改动需先对齐。更要紧的是：**改变 PendingIntent 的 Intent 内容会改变 `filterEquals` 判定**，可能导致升级后旧的已注册闹钟无法被新代码取消，需要在升级路径上一次性重排所有闹钟。

## 已核查但无问题的点

以下都是我逐行确认过、**设计正确、修复阶段请勿"顺手改掉"**的点。

1. **`project_lumen-release.jks` 没有进入版本控制（团队点名要，附实际输出）。**
   - `git ls-files --error-unmatch project_lumen-release.jks` → `error: pathspec 'project_lumen-release.jks' did not match any file(s) known to git`
   - `git log --all --oneline -- project_lumen-release.jks` → **空输出**（历史中从未出现过）
   - `git check-ignore -v project_lumen-release.jks` → `.gitignore:8:*.jks	project_lumen-release.jks`
   - `git ls-files` 全仓筛 `jks|keystore|pem|p12` → 无命中
   - 文件确实存在于工作树（2774 字节），但被 `.gitignore:8` 的 `*.jks` 覆盖（`:9` 另有 `*.keystore`）。
   - `app/build.gradle.kts:183-192` 的 `signingConfigs` **不引用**这个文件，而是读 `PROJECT_LUMEN_STORE_FILE` / `_STORE_PASSWORD` / `_KEY_ALIAS` / `_KEY_PASSWORD` 四个 gradle property（`:9-12`）；`:14-19` 的 `projectLumenReleaseSigningConfigured` 要求四者全部非空，`:200-202` 才挂上 signingConfig。CI 侧由 `build.yml:122-150` / `release.yml:144-163` 从 `KEYSTORE_BASE64` secret 解码成 `${{ github.workspace }}/project_lumen.jks` 后追加写入 `gradle.properties`。**密钥只存在于 GitHub secrets 与本地工作树，仓库里没有。**
   - **结论：这一项不是缺陷，无需处置，也不需要轮换密钥。** 附一条排查经验：我最初用 `fd -H -e jks` 没找到它，是因为 fd 默认遵守 `.gitignore`，需要 `-I` 才能看到——记录在此以免后续复核时产生分歧。
2. **Manifest 的 `exported` 门禁逐个组件核对结论（团队点名要）：全部正确，无裸暴露组件。**
   - `:58-72` `MainActivity` `exported=true` —— MAIN/LAUNCHER 启动入口，必须公开，正确。
   - `:73-84` `openapi.DashboardActivity` `exported=true` + `android:permission="com.project.lumen.permission.ACCESS_LUMEN_CORE"` ✓
   - `:85-96` `openapi.RestOverlayActivity` `exported=true` + `android:permission="...TRIGGER_LUMEN_CONTROL"` ✓
   - `:97-108` `openapi.VisualMonitorActivity` `exported=true` + `ACCESS_LUMEN_CORE` ✓
   - `:188-195` `openapi.LumenOpenService` `exported=true` + `ACCESS_LUMEN_CORE` ✓
   - `:207-213` `rikka.shizuku.ShizukuProvider` `exported=true` + `android:permission="android.permission.INTERACT_ACROSS_USERS_FULL"` —— Shizuku 官方要求的写法，该权限只有 shell/system 持有，等效"仅 Shizuku 可访问"，正确。
   - 其余全部 `exported=false`：5 个 receiver（`:110`、`:114`、`:122`、`:126`、`:138`）、5 个前台服务（`:147`、`:156`、`:161`、`:170`、`:179`）、`FileProvider`（`:197-205`，配 `grantUriPermissions=true` + `@xml/file_paths`）✓
   - 自定义权限声明正确：`ACCESS_LUMEN_CORE` = `dangerous`(`:5-9`)、`TRIGGER_LUMEN_CONTROL` = `signature`(`:10-14`)，两者都带 `android:label` / `android:description`（dangerous 级权限在授权弹窗上显示所必需）。未声明 `permissionGroup`——API 29+ 上自定义权限统一归入"其他权限"分组，不影响授予流程，**不是缺陷**。
   - 附注（产品决策，不作缺陷）：`TRIGGER_LUMEN_CONTROL` 用 `signature` 意味着只有同签名应用能触发 `RestOverlayActivity`，真正的第三方应用无法调用。这与"对外开放 API"的定位存在张力，但也可能是有意的（只放开读、不放开控制）。
3. **`foregroundServiceType` 齐全且与权限匹配（团队点名要）。** 5 个前台服务全部声明了 type：`:150` `specialUse`、`:159` `camera`、`:164` `specialUse`、`:173` `specialUse`、`:182` `specialUse`；4 个 `specialUse` 都按 Android 14 要求配了 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` 说明文本（`:151-153`、`:165-167`、`:174-176`、`:183-185`）；权限侧 `:23` `FOREGROUND_SERVICE`、`:24` `FOREGROUND_SERVICE_SPECIAL_USE`、`:25` `FOREGROUND_SERVICE_CAMERA` 一一对应；`camera` 型服务另有 `:19` 的运行时 `CAMERA` 权限与 `:39-41` 的 `uses-feature required=false`。**这一块是对的，不要动。**（注：Android 14+ 禁止从后台启动 `camera` 型前台服务，那是启动时机问题，属 G03/G04 组，清单侧无误。）
4. **`MainActivity` 基类与主题匹配（brief 点名的历史 P0，当前一致）。** `app/src/main/java/com/projectlumen/app/MainActivity.kt:33` 是 `open class MainActivity : ComponentActivity()`，主题是平台 Material（`values/styles.xml:3` `parent="android:style/Theme.Material.Light.NoActionBar"`，`values-night/styles.xml:3` 为 `Theme.Material.NoActionBar`）。`openapi/ExternalActivities.kt:5-9` 的三个 Activity 都继承 `MainActivity`，同样一致。⚠️ 修复阶段**严禁**把 `ComponentActivity` 改成 `AppCompatActivity`（会立刻触发历史事故：AppCompat 需要 AppCompat 主题，否则 `onCreate` 抛异常、"应用启动但从未可见"）；反之若有人要把主题换成 `Theme.MaterialComponents`/`Theme.AppCompat`，必须同步改基类。

5. **`app/build.gradle.kts` 里几处容易被误改的正确设计：**
   - `:210-227` ABI splits 在 baseline profile 任务期间自动禁用（`isBaselineProfileTask` 按任务名判定），注释写清了原因（托管 x86_64 模拟器装到单 ABI 包会立刻死进程）。这个绕法是对的，别删。
   - `:134-143` 证书固定的三段式处理：先在 `:134-137` 把"未开启固定"时的 pins 归零避免残留配置误生效，再用两条 `require`(`:138-143`) 保证"开启固定但没配 pins 就构建失败"——**api 与 translation 两个 host 都覆盖了**。这是本文件里最规范的一段，是 G10-04 应该照抄的样板。
   - `:21-31` `projectLumenVersionCodeFromName` 的 `major*10000 + minor*100 + patch` + `coerceIn(1, Int.MAX_VALUE)` 实现正确，是 G10-03 应该回归到的那个真相源。
   - `:272-288` JetBrains Mono 子集字体的 20 KB 上限校验挂在 `preBuild` 上，`inputs.file` + `doLast` 的写法正确。
   - `:247-252` `useLegacyPackaging = false` + `pickFirsts += "**/libc++_shared.so"`：前者是 16 KB 页对齐的前提，后者解决多模块 STL 冲突，都正确。
   - `:298-303` kapt 的 `room.incremental` 与 `room.schemaLocation`（指向 `$projectDir/schemas`）配置正确，schema 会被导出。
   - `:33-37` `projectLumenBuildConfigString` 对反斜杠与双引号做了转义，`buildConfigField` 注入字符串的方式是安全的（不会被注入闭合引号）。
6. **ProGuard 规则里已经写对的部分（勿删）：**
   - `:47` `-keep class ...NativeSecurityBridge { *; }` + `:48-50` `-keepclasseswithmembernames class * { native <methods>; }`：JNI 走静态名称绑定（`app/src/main/cpp/lumen_security.cpp:232-275` 是 `Java_com_projectlumen_app_core_security_NativeSecurityBridge_*` 形式），这两条**必须保留**，否则混淆后原生方法解析失败。全局那条也顺带保住了 MMKV 等其他用静态 JNI 名的库。
   - `:6-27` MainActivity / Application / BroadcastReceiver / Service / ListenableWorker 的 keep。ProGuard 的 `extends` 覆盖间接子类，所以 `CoroutineWorker` 的实现类、`LifecycleService` 的子类都在内。
   - `:30-33` Room 的 `AppDatabase` / `AppDatabase_Impl` / `@Entity` / `@Dao` keep；`:36-40` 持久化枚举的 `values()` / `valueOf()` keep（枚举名进了 Room 与 preferences 并按字符串比对，必须保）。
   - `:43-45` `@JavascriptInterface` 方法 keep。
   - `:53-56` `com.project.lumen.open.**`（AIDL 生成类与对外接口）keep —— 第三方调用方依赖稳定名称，必须保；`:57` `openapi.**` 亦然。
   - **`@TypeConverter` 无需额外 keep**：Room 生成的 `AppDatabase_Impl` 在编译期直接静态调用转换器，不走反射，重命名安全。已确认项目**没有**任何基于反射的 JSON 序列化（依赖表无 Gson / Moshi / kotlinx-serialization，`app/build.gradle.kts:345` 的 `org.json` 只在 `testImplementation`），因此数据模型类不需要 keep 规则。
   - `:119` `-obfuscationdictionary obfuscation-dictionary.txt` 引用的 `app/obfuscation-dictionary.txt` **确实存在**（缺失会让 R8 构建失败），已核实。
   - 附注（P2 以下，未单列）：`:116` `-optimizationpasses 5` 与 `:118` `-overloadaggressively` 会被 R8 忽略并打印 "Ignoring option"，属无效配置；`:60-63` 的 4 条 `-dontwarn androidx.*` 范围偏宽，会掩盖真实的缺类告警。二者都不影响正确性，列此备查。
7. **`baselineprofile` 模块接入方式正确。** `app/build.gradle.kts:266-270` 的 `automaticGenerationDuringBuild = false` + `mergeIntoMain = true` + `saveInSrc = true`，配合 `build.yml:163-183` 先显式跑 `:app:generateBaselineProfile` 再跑 `assembleRelease`——**两次独立的 gradle 调用**让 `:213-219` 的 splits 互斥判定各自读到正确的 `gradle.startParameter`，设计是对的（同一次调用里做不到）。`baselineprofile/build.gradle.kts:29-40` 的 `pixel6Api35` 托管设备显式钉了 `testedAbi = "x86_64"`（注释说明 AGP 9 将不再默认），`:18-19` 的 `androidx.benchmark.suppressErrors=EMULATOR,LOW-BATTERY` 也是 CI 上必需的，都正确。仓库未启用配置缓存（`gradle.properties` 无 `org.gradle.configuration-cache`），所以读 `startParameter` 不会踩配置缓存的坑。
   - ⚠️ 但要知道后果：**baseline profile 生成失败会阻塞发版**（`build.yml:163` 无 `continue-on-error`，且排在 `:185` 构建 APK 之前）。模拟器抖动即挡住整条流水线。这是有意的严格设定还是遗漏，请作者定；若要放宽，加 `continue-on-error: true` 即可（代价是启动性能优化静默失效）。
8. **测试侧已确认无 `Handler(Looper)` 类加载雷。** `app/src/test/` 里唯一的 Android 导入是 `android.os.Build`（3 个文件：`ForegroundServiceControllerTest:3`、`ForegroundServiceStartEligibilityTest:3`、`ProximityCameraForegroundEligibilityTest:3`），且只用 `Build.VERSION_CODES.*` —— 那是编译期常量，Kotlin 直接内联，不触发 android.jar 调用，纯 JVM `testDebugUnitTest` 安全。`SecureOkHttpFactoryTest` 会触达 `ClashPartnerCompat.shouldSkipManualProxy()`，而 `core/network/ClashPartnerCompat.kt:87` 的 `mainHandler` 已经是 `by lazy`（历史事故已修好），该测试 `:23-24` 的注释还明确记录了这个约束。**修复阶段勿把 `by lazy` 改回 eager 初始化。** 另外 `app/build.gradle.kts` 没配 `testOptions { unitTests.isReturnDefaultValues = true }`，当前测试也确实不需要它——这是好事（默认值会掩盖误用框架 API），不要为了图方便加上。
9. **`res/raw/keep.xml` 正确。** `tools:keep="@string/lumen_crash_*,@plurals/lumen_crash_*,@string/crash_report_*"` 保住了崩溃 SDK 按名称查找的资源，与 `app/build.gradle.kts:199` 的 `isShrinkResources = true` 配套，必需。
10. **8 个工作流中不存在 `pull_request_target`（团队点名要）。** 全部 8 个 yml 逐个确认，均无该触发器，因此**不存在"把仓库 secret 交给任意 PR 作者"的那类 P0 供应链风险**。`build.yml` / `lumen-ui-tuner.yml` / `codeql.yml` 用的都是普通 `pull_request`，fork PR 拿不到 secrets——副作用是 fork PR 会在 `build.yml:117-120` 的签名 secret 检查处 `exit 1`，这是安全的失败方向，可以接受。
    - 但**第三方 action 普遍未用 commit SHA 锁定**：`softprops/action-gh-release@v2`（`build.yml:359`、`release.yml:296`，带 `contents: write` + GITHUB_TOKEN）、`gradle/actions/setup-gradle@v4`、`reactivecircus/android-emulator-runner@v2`、`pnpm/action-setup@v4`、`android-actions/setup-android@v3`、以及最严重的 `dtolnay/rust-toolchain@stable`（**分支引用**，见 G10-07）。可变 tag 被重打是真实的攻击手法，建议全部改为 SHA 锁定——已在 G10-07 / G10-10 里给出具体位置，此处不重复列为独立缺陷。
11. **CI 日志里的 secret 处理基本得当。** `build.yml:99-105` / `release.yml:121-127` 只打印"是否存在"与长度；密码写入 `gradle.properties` 后 `build.yml:152-154` / `release.yml` 用 `sed 's/=.*/=<redacted>/'` 遮蔽回显。`:104` 的 `KEY_ALIAS value` 虽然直接 echo，但 GitHub Actions 对 secrets 有自动遮蔽，实际输出为 `***`。keystore 解码到 `${{ github.workspace }}/project_lumen.jks`，被 `.gitignore:8` 覆盖，**不会被 `lumen-ui-tuner.yml:68-88` 的自动提交步骤误带入**（那一步只 `git add` 指定的 lockfile）。`build.yml:141-144` 打印的 cert SHA-256 是公开信息，不算泄露。**这一块无需改动。**
12. **`application` 级安全属性取值全部正确（团队点名要）：** `:45` `allowBackup="false"` ✓、`:47` `usesCleartextTraffic="false"` ✓、`:53` `networkSecurityConfig="@xml/network_security_config"` ✓、`:46` `installLocation="internalOnly"` ✓、`:48` `enableOnBackInvokedCallback="true"` ✓、`:52` `localeConfig` ✓；**全文没有 `android:debuggable`**（正确——应由构建类型控制，`app/build.gradle.kts:196-197` 已显式 `isDebuggable = false` / `isJniDebuggable = false`）。
13. **`design/` 目录挂载为 assets 是安全的。** `app/build.gradle.kts:258-262` 把 `../design` 加为 assets 源，该目录只有 `lumen-ui-tokens.json`（1210 字节），无敏感内容，不会误打包源码或密钥。
14. **`settings.gradle.kts` 其余部分规范。** `:1-7` pluginManagement 仓库；`:12-26` CRooot 本地复合构建的 opt-in 开关（注释解释了 AGP 版本可能不一致所以默认关闭，`:17` 还要求目录真实存在）；`:29` `RepositoriesMode.FAIL_ON_PROJECT_REPOS`；`:41-43` jitpack 限定到 `com.github.Tencent.soter`；`:44-57` GitHub Packages 限定到 `com.chloemlla.crooot` 且凭据走 property/env 而非硬编码 —— 除 G10-06 那一块，其余都对。`:62-66` 的 5 个 include 与磁盘上的模块目录一致。
    - 附注（未单列为缺陷）：仓库**没有版本目录**（无 `gradle/libs.versions.toml`），依赖版本散在各模块的 `build.gradle.kts` 里。这是可维护性问题而非缺陷——`app/build.gradle.kts:305-353` 的依赖表内部无版本冲突，Compose 走 BOM(`:308`)，AGP/Kotlin 版本统一在根 `build.gradle.kts:1-8` 声明。另外 `:65` 的 `:lumen-crash-sample` 会参与根级聚合任务（`gradle test lint` 这类不带 `:app:` 前缀的调用），是 CI 时长的一部分；`lumen-crash*` 三个模块的构建配置归 G11 判断。
15. **`remotion-android-product-animation.yml` 与 `vitepress-docs.yml` 权限基本最小化。** 前者 `permissions: contents: read` ✓；后者声明了 `pages: write` / `id-token: write` 但并无实际 Pages 部署步骤（只 build + upload artifact），属多余权限，风险很低，未单列为缺陷——要收紧只需删掉那两行。两者都有 `concurrency` 组，而 `build.yml` / `release.yml` / `codeql.yml` **没有**（已在 G10-02 的修复方案里提出为 `build.yml` 补上）。
    - ⚠️ **操作提醒（与本次审查直接相关）**：`vitepress-docs.yml:9-14` 的 paths 过滤是 `docs/**`，而本次审查的全部报告都写在 `docs/audit/2026-08-31/` 下。**提交这些报告会触发 VitePress 文档站构建**，且这些内含具体漏洞位置与代码片段的报告会进入公开文档站的构建范围。建议把审查报告移到 `docs/` 之外，或在 VitePress 配置里排除 `docs/audit/**`，或至少在推送前确认文档站的发布可见性。





