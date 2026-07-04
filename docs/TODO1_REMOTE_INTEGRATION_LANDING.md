# Project Lumen todo1 前后端对接落地记录

本文记录 `docs/todo1` 的客户端与后端落地结果，范围覆盖账号、同步、权益、AI/遥测、安全、远端配置和版本演进。Commercial Edition 分支在此基础上补齐云同步与云备份的商业版保护鉴权。

## 1. 客户端落地

- 设置页新增“账号与云同步”卡片，登录、健康检查、权益刷新和购买校验保持可访问；云同步与云备份操作仅对 Commercial Edition Plus/Team 权益开放。
- 支持后端健康检查，显示当前 API base。
- 支持邮箱验证码登录，登录成功后使用 `SecureCredentialStore` 保存 access token 和 refresh token。
- 支持设备安装 ID 和远端同步 cursor 持久化，用于登录、设备注册、同步和云备份。
- 支持刷新账号：拉取 `/v1/me`、注册 `/v1/devices/register`、拉取 `/v1/entitlements`、拉取 `/v1/config/feature-flags`。
- 支持把远端权益写入本地 `entitlements` 表，并同步更新本地 plan tier。
- 支持把远端 feature flags 写入本地 `feature_flags` 表。
- Plus/Team 用户支持把本地 JSON 备份结构上传到 `/v1/backups`，也支持从 `/v1/backups/latest` 恢复；Free/Pro 用户会在客户端看到商业版 Plus 权益要求，操作层也会拒绝执行。
- Plus/Team 用户支持把本地设置、目标、模板、统计、权益、feature flags、提醒计划打包成增量同步 payload 推送到 `/v1/sync/push`，并通过 `/v1/sync/changes` 拉取、过滤本机变更、落库合并远端变更；Free/Pro 用户不能触发同步。
- 遥测上传继续复用已有 `EyeCareTelemetryReporter`，登录后优先使用安全存储里的 access token。
- TODO1 远端请求使用 C++ 原生 `lumen_security` 防护：应用启动时执行 native 完整性检查，release 请求签名必须通过 native bridge 获取密钥，native bridge 不可用时不再回退到本地默认密钥。
- 客户端设备注册会把 native bridge 可用性、native 环境判定、request signing 策略和 App Integrity 状态写入 `localSecurityConfig`，便于后端/admin 侧识别客户端防护状态。

## 2. 后端落地

- 新增 `/api/openapi.json`，输出第一版机器可读 OpenAPI 路由清单。
- 新增 `/api/v1/devices/register`，用于客户端注册设备安装、版本和本地安全配置摘要。
- 新增 `/api/v1/config/feature-flags`，为客户端提供远端功能开关。
- 新增 `/api/v1/releases/check`，基于现有 admin release 集合提供远端版本检查能力。
- 新增路由均接入现有 API router；`/v1/*` 路由继续走现有请求签名、Play Integrity 条件校验和访问审计中间件。
- Commercial Edition 分支中 `/v1/sync/changes`、`/v1/sync/push`、`/v1/backups`、`/v1/backups/latest` 额外要求当前账号存在 active Plus/Team 权益，否则返回 `403` 和 `commercial_plus_required`。

## 3. 已有能力承接

- 账号：`/v1/auth/email/start`、`/v1/auth/email/verify`、`/v1/auth/session/refresh`。
- 权益：`/v1/entitlements`、`/v1/purchases/google/verify`。
- 同步：`/v1/sync/push`、`/v1/sync/changes`。
- 备份：`/v1/backups`、`/v1/backups/latest`。
- 遥测：`/v1/telemetry`、`/v1/telemetry/debug/latest`。
- AI 分析：`/v1/face-analysis/frames`。
- 安全：HMAC 请求签名、nonce 防重放、Play Integrity 条件校验、客户端证书 pin 配置。
- 商业版保护：云同步和云备份同时具备客户端 Plus gating 与后端 Plus entitlement 鉴权，避免仅靠 UI 限制被绕过。
- 客户端原生防护：`lumen_security.cpp` 负责 native request-signing secret、包名/证书校验、反调试和 Hook artifact 检测；release 远端 API 请求依赖该 native bridge。

## 4. 生产边界

以下内容需要真实外部服务或生产凭据，已保留接口和数据通路，但不能在本地直接验证：

- 邮件验证码真实发送服务。
- Google Play Billing S2S 订阅通知和购买校验凭据。
- Play Integrity 服务端 Google API 校验。
- OSS/S3 人脸帧对象存储直传。
- Kafka/ClickHouse/InfluxDB 等大容量遥测分析链路。
- 正式 release APK 下载地址和灰度策略。

## 5. 验证说明

根据仓库约束，本地不执行实际构建、测试、Lint、安装或依赖安装命令。此次只做源码和资源静态检查；完整验证应由 GitHub Actions workflow 执行。
