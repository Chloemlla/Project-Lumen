 安卓商业应用与后端 API 通信安全需求文档 (SRD)
## 1. 传输层安全需求 (Transport Layer Security)
### 1.1 全站 HTTPS 强制性
 * **需求描述：** 应用与后端服务之间的所有网络通信必须强制使用 HTTPS 协议，严禁使用 HTTP 明文传输。
 * **客户端实现：** * 在 res/xml/network_security_config.xml 中配置 cleartextTrafficPermitted="false"，全局禁用明文流量。
   * 线上生产环境包必须关闭 WebView 的脚本注入漏洞，且不允许加载任何非全站 HTTPS 的网页。
 * **后端实现：** 服务器必须禁用 TLS 1.0 和 TLS 1.1，仅支持 **TLS 1.2 和 TLS 1.3**，并配置强加密套件（如支持前向保密 PFS 的 ECDHE 套件）。
### 1.2 SSL 证书绑定 (Certificate Pinning)
 * **需求描述：** 为防止中间人攻击（MITM）及伪造证书授信，App 必须对后端 API 域名的公钥哈希进行绑定。
 * **验收标准：** * 使用 OkHttp 的 CertificatePinner 或安卓原生网络安全配置绑定主流证书及**至少一个备用证书（Backup Pin）**的公钥哈希。
   * 使用 Charles、Burp Suite 等抓包工具在未安装合规代理证书的测试机上测试，App 必须直接阻断连接并提示网络异常。
## 2. 身份认证与凭证管理需求 (Authentication & Credentials)
### 2.1 令牌化身份验证机制
 * **需求描述：** 采用基于 Token 的认证机制（推荐 OAuth 2.0 / JWT），避免在网络中频繁传输或在本地存储用户的明文密码。
 * **设计规范：**
   * **Access Token（访问令牌）：** 有效期不得超过 2 小时，随 HTTP 请求头（Authorization: Bearer <Token>）传输。
   * **Refresh Token（刷新令牌）：** 有效期不超过 30 天，仅用于换取新的 Access Token。
### 2.2 本地凭证安全存储
 * **需求描述：** 任何敏感凭证（如 Access Token、Refresh Token、用户个人隐私数据）绝不允许明文保存在系统公共目录或普通 SharedPreferences 中。
 * **技术要求：** * 必须使用 Android Jetpack 提供的 **EncryptedSharedPreferences**。
   * 加解密密钥必须托管于 **Android Keystore System**，确保密钥由硬件芯片（TEE/SE）隔离保护，不可被外部读取。
## 3. API 防护与防篡改需求 (API Protection)
### 3.1 请求签名校验机制 (Request Signing)
 * **需求描述：** API 请求必须具备防篡改能力，确保前端发送的数据在传输过程中未被中途修改。
 * **实现算法：**
   * 客户端将 请求参数(Body/Query) + 时间戳(Timestamp) + 随机字符串(Nonce) 按照字母序排列。
   * 结合客户端与后端约定的特殊 Key，使用 **HMAC-SHA256** 算法计算出签名（Sign），放入 HTTP Header。
   * 后端收到请求后用相同逻辑验签，不一致则直接返回 403 Forbidden。
### 3.2 防重放攻击 (Anti-Replay)
 * **需求描述：** 防止黑客截获合法的 API 请求包并重复向服务器发送（如重复提交订单、重复消费）。
 * **实现规范：**
   * 每个请求 Header 必须包含 **Timestamp（时间戳）** 和 **Nonce（唯一随机数）**。
   * 后端服务校验：
     1. 检查服务器当前时间与请求时间戳的差值，超过 5 分钟（300秒）的请求直接丢弃。
     2. 将接收到的 Nonce 存入 Redis 缓存，设置 5 分钟过期时间。若在有效期内收到重复的 Nonce，直接拒绝请求。
## 4. 客户端应用完整性与自防卫 (Client Self-Defense)
### 4.1 环境安全能力监测 (Play Integrity)
 * **需求描述：** App 需具备运行时环境检测能力，严禁在不安全、被篡改的环境中执行敏感业务 API。
 * **集成要求：**
   * 集成谷歌官方 **Play Integrity API**（或国内主流厂商的设备指纹 SDK）。
   * 在关键业务接口（如注册、登录、支付）发起前，向后端发送 Integrity Token。后端调用谷歌服务器进行强验证，确保当前 App 是从未被二次打包的正版官方应用，且设备未被 Root 或处于模拟器中。
### 4.2 代码混淆与逆向防护
 * **需求描述：** 提高 App 被反编译和静态分析的成本，防止加解密逻辑和 API 结构轻易泄露。
 * **技术要求：**
   * 生产环境构建（Release Build）必须开启 **R8/ProGuard** 混淆，对所有的 Java/Kotlin 类名、方法名进行混淆。
   * 敏感的核心加解密算法、Key 拼接逻辑、签名盐值（Salt）**严禁以明文硬编码在 Java 资源中**。必须下沉到 C/C++ 层编写（利用 NDK 编译为 .so 文件），并在 C 层加入运行时包名与签名证书哈希的二次校验。
   * 加入基础反调试代码，检测到 android.os.Debug.isDebuggerConnected() 为 True 时或检测到 Frida/Xposed 框架特征时，应用应主动终止进程。

## 5. 代码落地记录
 * **HTTPS 与证书绑定：** Android 默认 API 已切换为 `https://eye.chloemlla.com/api`，`AndroidManifest.xml` 与 `network_security_config.xml` 已全局禁止明文流量；移动端后端 API 请求统一通过 OkHttp，并在 Release 构建要求配置主证书与备用证书 Pin（`PROJECT_LUMEN_API_CERTIFICATE_PINS`）。翻译 API 保持 HTTPS 强制校验，`PROJECT_LUMEN_TRANSLATION_CERTIFICATE_PINS` 有配置时启用证书绑定，未配置时不改变现有翻译接入。
 * **Token 与安全存储：** 后端 Access Token 默认 TTL 调整为 7200 秒，Refresh Token 默认 TTL 为 30 天；客户端新增 `SecureCredentialStore`，使用 Android Keystore + `EncryptedSharedPreferences` 保存访问凭证。
 * **请求签名与防重放：** Android API 请求统一加入 `X-Lumen-Timestamp`、`X-Lumen-Nonce`、`X-Lumen-Signature`；后端 `/api/v1` 安全中间件使用 HMAC-SHA256 验签，校验 300 秒时间窗，并将 Nonce 写入 MongoDB TTL 集合防重放。
 * **Integrity 与关键接口：** 客户端集成 Play Integrity Token 获取能力，登录与 Google 购买校验请求会携带 `X-Lumen-Integrity`；后端可通过 `LUMEN_REQUIRE_PLAY_INTEGRITY=true` 对关键接口强制要求 Integrity Token。
 * **自防卫与混淆：** Release 构建保持 R8/资源收缩开启，已移除全包 keep 规则；签名密钥读取、包名/证书哈希校验、反调试与 Frida/Xposed 基础检测下沉到 NDK `lumen_security`。
