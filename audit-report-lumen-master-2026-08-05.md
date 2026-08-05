# Project Lumen — 全库静态审计汇总报告

**日期:** 2026-08-05
**方法:** 6 个并行 agent 分模块扫描（tame-legacy-codebase 方法）
**仓库状态:** clean `main` @ `17a4546`

---

## 审计范围总览

| 模块 | Agent | 文件数 | 源码行 | 报告文件 |
|------|-------|-------:|-------:|---------|
| Android App (`app/`) | 1 | 220 | ~39,775 | `audit-report-lumen-app-2026-08-05.md` |
| Rust Backend (`backend/src/`) | 2 | 51 | ~8,049 | `audit-report-lumen-backend-2026-08-05.md` |
| Admin Dashboard (`backend/admin/`) | 3 | 26 | ~4,230 | `audit-report-lumen-admin-2026-08-05.md` |
| Lumen Crash SDK (3 模块) | 4 | 23 | ~3,158 | `audit-report-lumen-crash-2026-08-05.md` |
| CI/Build/Docs/Scripts | 5 | ~100 | ~14,400 | `audit-report-lumen-build-2026-08-05.md` |
| Tools/Resources/Remotion | 6 | 35 | ~5,650 | `audit-report-lumen-tools-2026-08-05.md` |
| **总计** | **6 agents** | **~455** | **~75,262** | |

---

## 各模块评分

| 维度 | App | Backend | Admin | Crash | Build | Tools | **加权平均** |
|------|:---:|:-------:|:-----:|:-----:|:-----:|:-----:|:-----------:|
| **Security** | 5.0 | 5.0 | 5.0 | 6.0 | 4.0 | 6.0 | **4.8** |
| **Stability** | 6.5 | 6.0 | 6.0 | 5.5 | 5.0 | 6.0 | **6.0** |
| **Performance** | 6.0 | 5.0 | 7.0 | 6.5 | 6.0 | 5.0 | **5.9** |
| **Testing** | 4.5 | 3.0 | 1.0 | 6.5 | 6.0 | 5.0 | **4.4** |
| **Maintainability** | 5.0 | 6.0 | 6.0 | 7.0 | 5.0 | 5.0 | **5.4** |
| **Design** | 7.0 | 6.0 | 7.0 | 7.0 | 5.0 | 7.0 | **6.5** |
| **Release** | 6.0 | 5.0 | 4.0 | 7.5 | 4.0 | 6.0 | **5.3** |
| **Overall** | **5.6** | **5.0** | **5.0** | **6.6** | **5.0** | **5.7** | **5.4** |

---

## 发现汇总

| 严重度 | App | Backend | Admin | Crash | Build | Tools | **总计** |
|--------|:---:|:-------:|:-----:|:-----:|:-----:|:-----:|:--------:|
| **Critical** | 1 | 0 | 0 | 0 | 1 | 0 | **2** |
| **High** | 4 | 3 | 3 | 1 | 7 | 1 | **19** |
| **Medium** | 16 | 12 | 8 | 6 | 22 | 6 | **70** |
| **Low** | 15 | 12 | 12 | 8 | 15 | 7 | **69** |
| **Info** | 12 | 8 | 8 | 6 | 8 | 5 | **47** |
| **总计** | **48** | **35** | **31** | **21** | **53** | **19** | **207** |

---

## Critical 发现 (2 项)

### C-01: HMAC 请求签名密钥硬编码回退 [App S-01 / Build S-06]
**文件:** `app/build.gradle.kts:113-119`、`app/src/main/cpp/lumen_security.cpp:14-16`、`core/security/ProjectLumenRequestSigner.kt:71`、`backend/src/config.rs:66`
**描述:** 签名密钥在 Gradle、原生 `.so` 和 Kotlin 签名器中都有硬编码回退值 `project-lumen-local-request-signing-key`。任何未设置 `PROJECT_LUMEN_REQUEST_SIGNING_SECRET` 环境变量的构建（包括本地构建、CodeQL 的 `assembleDebug`、以及任何未来配置错误的 CI 运行）都会使用这个公开已知的常量签署请求。后端仅记录警告日志，不会拒绝该密钥。

### C-02: 硬编码 Maven 凭证 + HTTP 依赖传输 [Build S-01/S-02]
**文件:** `settings.gradle.kts:33-40`
**描述:** Gradle 构建使用固定凭证 `developer` / `developer!@#` 通过明文 HTTP 从第三方 Nexus 服务器 (`http://nexus.itgsa.com:5566`) 解析依赖。任何能读取该仓库的人都可以认证该 Nexus，且由于传输未加密，中间人攻击者可以替换任意 JAR/AAR 工件。

---

## High 发现 (19 项)

### 安全类 (12 项)

| ID | 来源 | 文件 | 描述 |
|----|------|------|------|
| H-01 | App S-02 | `MainActivity.kt:153-171` | **Open API 权限绕过。** `MainActivity` 是 exported 且无权限，处理 `ACTION_TRIGGER_REST` 等。有权限门的子类 (`RestOverlayActivity` 等) 可通过直接启动 `MainActivity` 绕过。 |
| H-02 | App S-03 | `openapi/LumenOpenService.kt:63-103` | **AIDL 服务权限不足。** exported 的 AIDL 服务仅由 dangerous 级别的 `ACCESS_LUMEN_CORE` 保护，且签名验证默认为空（禁用）。任何被用户授予该权限的 app 都可以读取眼疲劳/屏幕时间数据。 |
| H-03 | App S-04 | `PrivilegedDeviceControlCoordinator.kt:181-398` | **后端控制的"静默视觉"会话。** 远程 `DeviceControlPolicy` 可以启用摄像头帧上传，本地"同意"仅是间接的接近/眨眼开关。 |
| H-04 | App S-05 | `EntitlementEntity.kt:7-18` / `DataBackupService.kt:531-541` | **购买令牌明文存储。** Play 购买令牌和原始载荷以明文存储在 Room 中，以明文 JSON 导出备份。 |
| H-05 | Backend B1 | `store/entitlements.rs:57-113` | **购买验证是存根。** Google Play 购买"验证"从未调用 Play Developer API。默认情况下每笔购买都存储为 `pending`，且永远不会有任何操作将其提升为 `active`。 |
| H-06 | Backend B2 | `auth_context.rs:11-70` | **设备安全门可被客户端伪造。** `require_device_security` 信任客户端提供的证据，且从不检查 `verified` 标志。 |
| H-07 | Backend B3 | `store/admin_auth.rs:16-26` | **管理员登录无速率限制。** 静态密码 `change-me`，无 constant-time 比较，无速率限制。 |
| H-08 | Build S-03 | `build-artifacts.yml:130-157` | **未锁定远程部署脚本。** 从另一个仓库的可变 `main` 分支下载并执行 `deploy_image.js`，SSH 密钥和管理员密码在环境变量中。 |
| H-09 | Build S-04 | `build-artifacts.yml:96-157` | **生产部署无审批门。** 每次推送到 `main` 都会自动部署，没有环境保护或审批门。 |
| H-10 | Build S-05 | `dependabot-maintenance.yml:59-231` | **PAT 自动推送和强制合并。** 使用个人访问令牌绕过分支保护，自动推送并强制合并 Dependabot PR。 |

### 发布类 (3 项)

| ID | 来源 | 文件 | 描述 |
|----|------|------|------|
| H-11 | Build R-01 | `build.yml:357-368` | **每次分支推送都创建 GitHub Release。** 任何分支的每次推送都会创建 `draft:false`、`make_latest:true` 的 Release，功能分支的 WIP 构建会成为应用自动更新源。 |
| H-12 | Build ST-01 | `build.yml:3-10` | **无路径过滤的触发。** 工作流在 `push: branches: "**"` 上触发，无路径过滤，每次提交都会触发完整发布流水线。 |
| H-13 | Build T-01 | `build.yml:357 vs 423/433` | **Release 在测试/ lint 之前创建。** GitHub Release 在单元测试和 lint 运行之前创建，测试失败不会阻止发布。 |

### 稳定性类 (2 项)

| ID | 来源 | 文件 | 描述 |
|----|------|------|------|
| H-14 | Build ST-02 | `build.yml:85-120` | **Fork PR 构建总是失败。** `Write signing config` 步骤在 fork PR 上 secrets 为空时硬失败，所有 fork PR 在测试之前就崩溃。 |
| H-15 | Crash STA-01 | `LumenCrash.kt:149-150` | **崩溃处理器回退不安全。** 未捕获异常处理器的回退路径未包装在 `runCatching` 中，OOM 或 R8 剥离完整性常量会中止处理器，无法保存报告或链接到前一个处理器。 |

### 性能类 (1 项)

| ID | 来源 | 文件 | 描述 |
|----|------|------|------|
| H-16 | Tools REMO-001 | `remotion-android-product-animation.yml:33-42` | **4K 视频渲染每次推送都运行。** 每次推送都渲染 20:50 分钟、37,500 帧的 4K 视频，软件 x264 编码可能超过 6 小时超时限制。 |

---

## 按维度汇总

### 安全 (4.8/10)
- **最严重:** 硬编码密钥回退、HTTP 明文 Maven 仓库、Open API 权限绕过、设备安全门可伪造、购买验证是存根
- **正面:** `SecureCredentialStore` (AES256-GCM)、`SecureOkHttpFactory` (HTTPS + 证书锁定)、原生反篡改层 (`lumen_security.cpp`)、请求签名设计规范、后端默认 fail-closed
- **建议:** 使发布构建 `require()` 所有 secrets；移除或保护 HTTP Maven 仓库；为 Open API 添加签名级权限；实现真正的购买验证；在设备安全门中强制执行 `verified` 标志

### 稳定性 (6.0/10)
- **最严重:** 崩溃处理器回退不安全、Binder 线程上的 `runBlocking`、非事务性统计读写、广播接收器过载、`goAsync()` 超时风险
- **正面:** 大量 `runCatching` 防御性编程、FGS 控制器处理拒绝后的优雅降级、AlarmManager + WorkManager 双重恢复机制
- **建议:** 包装崩溃处理器回退、为 Room 写操作添加 `@Transaction`、将广播接收器转为 `WorkManager`、在 Binder 线程使用 `CoroutineScope` + 超时

### 性能 (5.9/10)
- **最严重:** 三个服务以 ~1 Hz 写入 `runtime_state`、`ProjectLumenUiState` 每秒重建、无界 DAO flow、同步导出、每次转换上传遥测
- **正面:** Gradle 构建缓存、Docker GHA 缓存、基线 profile 生成、R8 压缩 + 资源缩减
- **建议:** 合并状态写入、定期导出派生状态到 UI、为 DAO 查询添加分页、使用 `withContext(IO)` 包裹导出

### 测试 (4.4/10)
- **最严重:** 零 DAO/迁移测试、零仓库/ViewModel/StateStore 测试、零 AIDL/OpenAPI 测试、零安全层测试、管理员仪表板零测试
- **正面:** 纯逻辑引擎有良好测试（PomodoroEngine、ReminderEngine、策略、解析器、门控）
- **建议:** 添加 Room DAO + 迁移测试、仓库并发测试、AIDL 绑定测试、CI 中为管理员仪表板添加测试框架

### 可维护性 (5.4/10)
- **最严重:** 单表 `AppSettingsEntity` (~100 列)、1313 行的设置屏幕、手写 JSON 序列化在 4+ 文件中重复、`settings.gradle.kts` 中 ~200 行签名/发布块重复
- **正面:** 清晰的层分离、手动 DI 模式、单向数据流、`lumen-crash` 异常清晰的文档
- **建议:** 拆分 `AppSettingsEntity`、提取共享 JSON 序列化、提取复合 GitHub Actions、提交 npm lockfiles

### 设计 (6.5/10)
- **最严重:** Room + MMKV 双数据源、Open API Activity-as-subclass 模式、远程策略作为 feature flag、"静默视觉"同意模型薄弱
- **正面:** 清晰的 routes→store→models→repo→stateStore→ViewModel 分层、手动 DI 与构造函数注入 lambda 回调、单一 `ProjectLumenUiState` 通过 `combine+stateIn`、`FeatureEntry` 模式
- **建议:** 为每个聚合选择一个存储、为 Open API 重新设计权限模型、为远程摄像头会话添加明确同意 UI

### 发布 (5.3/10)
- **最严重:** 每次推送都创建 Release、无测试门控、无锁文件、Docker 无 CA 证书、base image 浮动标签
- **正面:** R8 + 压缩、ABI 分割、ProGuard 规则、multi-stage Dockerfile、`lumen-crash` 专业发布流水线
- **建议:** 仅从 main/tags 发布、测试通过后再创建 Release、提交所有 npm lockfiles、Docker 安装 CA 证书、固定 base image 版本

---

## 优先级修复建议

### 立即修复 (Critical)
1. **移除 HTTP Maven 仓库 + 硬编码凭证** — `settings.gradle.kts`
2. **使发布构建 require() 签名密钥** — 消除 `project-lumen-local-request-signing-key` 回退

### 高优先级 (High)
3. **锁定部署脚本 + 添加环境审批门** — `build-artifacts.yml`
4. **停止每次分支推送自动创建 Release** — 仅从 main/tags 发布
5. **修复 Open API 权限绕过** — `MainActivity` exported 权限
6. **实现真正的购买验证** — `store/entitlements.rs`
7. **在设备安全门中强制执行 `verified` 标志** — `auth_context.rs`
8. **为管理员登录添加速率限制** — `store/admin_auth.rs`
9. **包装崩溃处理器回退** — `LumenCrash.kt`
10. **修复测试/ lint 在 Release 创建之前的顺序** — `build.yml`
11. **添加 Docker CA 证书 + 提交 npm lockfiles**
12. **使 4K Remotion 渲染为手动触发** — `remotion-android-product-animation.yml`

### 中等优先级 (Medium)
13. 为 Room 统计写操作添加 `@Transaction`
14. 提交 Room schema 导出
15. 添加 `dataExtractionRules` 限制备份
16. 移除 `resources/` 死代码（1.1 MB 未引用图标）
17. 添加无界集合的 TTL 索引（后端 20+ 集合）
18. 添加优雅关闭 (`with_graceful_shutdown`)
19. 修复 CI NDK 版本不匹配
20. 修复 `release.yml` 跳过基线 profile 生成
21. 为管理员仪表板添加测试框架
22. 为 `requestJson` 添加 `AbortController`/超时
23. 添加 React 错误边界
24. 修复 `runtime_state` 双数据源（Room + MMKV）

---

## 文件清单

| 报告 | 路径 |
|------|------|
| Android App | `audit-report-lumen-app-2026-08-05.md` |
| Rust Backend | `audit-report-lumen-backend-2026-08-05.md` |
| Admin Dashboard | `audit-report-lumen-admin-2026-08-05.md` |
| Lumen Crash SDK | `audit-report-lumen-crash-2026-08-05.md` |
| CI/Build/Docs | `audit-report-lumen-build-2026-08-05.md` |
| Tools/Resources | `audit-report-lumen-tools-2026-08-05.md` |
| **汇总报告** | **`audit-report-lumen-master-2026-08-05.md`** (本文件) |

---

*由 6 个并行 tame-legacy-codebase 审查 agent 生成。无本地构建/测试（按仓库策略，所有构建在 GitHub Actions 中运行）。*