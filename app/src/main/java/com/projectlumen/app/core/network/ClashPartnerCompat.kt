package com.projectlumen.app.core.network

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import androidx.core.content.getSystemService
import java.util.concurrent.CopyOnWriteArrayList

/** CMFA 授予的 `partnerStatus` 读取层级，对应 provider 的 `accessTier` 字段。 */
enum class ClashAccess { Unavailable, Denied, Basic, Full }

/**
 * 读出 CMFA 授予的层级。apiVersion 3 起 `accessTier` 明确回传 `denied`/`basic`/`full`；
 * 更早的 CMFA 不带该字段，但那时能读到内容就等于拿到了全部字段，所以按 [ClashAccess.Full] 处理。
 */
internal fun parseClashAccess(values: Map<String, Any?>): ClashAccess =
    when (values["accessTier"] as? String) {
        "denied" -> ClashAccess.Denied
        "basic" -> ClashAccess.Basic
        "full" -> ClashAccess.Full
        else -> if (values.isEmpty()) ClashAccess.Unavailable else ClashAccess.Full
    }

/**
 * 把 CMFA 的机器可读 `deniedReason` 翻成用户能照着做的一句中文。
 */
internal fun describeDeniedReason(reason: String?): String = when (reason) {
    "pending_user_approval" -> "等待在 Clash 中确认配对：打开 Clash 主页或点击配对通知即可授权"
    "denied_by_user" -> "已在 Clash 中拒绝授权，可在 Clash 主页「伙伴应用」里撤销"
    "signer_unverified" -> "Clash 未登记 Project-Lumen 的签名证书，只开放基础状态；在「伙伴应用」里允许即可读取完整状态"
    "not_partner" -> "Clash 没把 Project-Lumen 认成伙伴应用，请更新 Clash 到支持伙伴配对的版本"
    "no_signature" -> "Clash 读不到 Project-Lumen 的签名信息，无法完成配对"
    null -> "Clash 未说明原因"
    else -> "Clash 返回原因：$reason"
}

/**
 * 一次 `partnerStatus` 查询的结果：层级、拒绝原因，以及真正读到的字段。
 * [values] 只在层级可读（Basic/Full）时非空——被拒时返回的 bundle 也非空，但只带
 * apiVersion/accessTier/deniedReason，绝不能把它当成一份全 false 的状态。
 */
private data class PartnerRead(
    val access: ClashAccess,
    val deniedReason: String?,
    val values: Map<String, Any?>?,
)

private val UNAVAILABLE_PARTNER = PartnerRead(ClashAccess.Unavailable, null, null)

/**
 * Full ClashMeta VPN partner adapt for Project-Lumen (system utility).
 *
 * Official Clash Meta packages only:
 * - com.github.metacubex.clash
 * - com.github.metacubex.clash.meta
 * - com.github.metacubex.clash.alpha
 *
 * - Detect Clash install / VPN / partnerStatus ContentProvider
 * - Bind process to VPN Network while auto-adapt is on and Clash is routing
 *   so OkHttp / WebView / HttpURLConnection cannot escape via allowBypass
 * - Rebind when VPN Network handle is replaced (Clash restart)
 * - Clear binding when Clash is off or auto-adapt is disabled
 * - shouldSkipManualProxy forces Proxy.NO_PROXY on HTTP stacks
 */
object ClashPartnerCompat {
    private const val PREFS = "clash_partner_compat"
    private const val KEY_AUTO_ADAPT = "clash_auto_adapt"
    private const val METHOD_PARTNER_STATUS = "partnerStatus"

    private val clashPackages = listOf(
        "com.github.metacubex.clash",
        "com.github.metacubex.clash.meta",
        "com.github.metacubex.clash.alpha",
    )

    // Lazy: JVM unit tests load this object via shouldSkipManualProxy() without a
    // prepared main Looper; constructing Handler eagerly crashes class init.
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    // Lazy for the same reason: android.net.Uri is unavailable during JVM unit tests.
    private val partnerStatusUris by lazy {
        clashPackages.map { pkg ->
            pkg to Uri
                .Builder()
                .scheme("content")
                .authority("$pkg.status")
                .build()
        }
    }
    private val listeners = CopyOnWriteArrayList<(Status) -> Unit>()

    /**
     * Optional hook invoked after status/binding changes so host code can
     * rebuild HTTP clients / image loaders on the new process network.
     */
    @Volatile
    private var networkAdaptHook: (() -> Unit)? = null

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    @Volatile
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var lastVpnActive: Boolean? = null

    @Volatile
    private var lastVpnNetwork: Network? = null

    @Volatile
    private var boundVpnNetwork: Network? = null

    @Volatile
    var status: Status = Status()
        private set

    data class Status(
        val clashInstalled: Boolean = false,
        val vpnActive: Boolean = false,
        val clashVpnRunning: Boolean = false,
        val partnerAppAutoAdapt: Boolean = true,
        val profileName: String? = null,
        val clashPackage: String? = null,
        /**
         * True when Clash StatusProvider responded. Prefer [clashVpnRunning]
         * over generic VPN heuristics so a non-Clash VPN is not treated as
         * Clash routing.
         */
        val partnerStatusAvailable: Boolean = false,
        /**
         * CMFA 授予的读取层级；被拒时 [partnerDeniedReason] 给出可操作的解决文案。
         */
        val partnerAccess: ClashAccess = ClashAccess.Unavailable,
        val partnerDeniedReason: String? = null,
        val processBound: Boolean = false,
    ) {
        val isClashVpnRouting: Boolean
            get() =
                if (partnerStatusAvailable) {
                    clashVpnRunning
                } else {
                    clashInstalled && vpnActive
                }
    }

    fun isAutoAdaptEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_ADAPT, true)

    fun setAutoAdaptEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_AUTO_ADAPT, enabled) }
        refresh(context.applicationContext)
    }

    fun statusLabel(context: Context): String {
        val enabled = isAutoAdaptEnabled(context)
        val s = status
        return when {
            !enabled -> "已关闭自动适配"
            !s.clashInstalled -> "未检测到 Clash Meta"
            s.partnerAccess == ClashAccess.Denied ->
                "读不到 Clash 状态 · ${describeDeniedReason(s.partnerDeniedReason)}"
            s.isClashVpnRouting -> {
                val profile = s.profileName
                val bound = if (s.processBound) " · 进程已绑定" else ""
                if (!profile.isNullOrBlank()) {
                    "VPN 已连接 · $profile$bound"
                } else {
                    "VPN 已连接 · 流量自动经 Clash$bound"
                }
            }
            else -> "已安装 Clash · 等待开启 VPN"
        }
    }

    fun start(context: Context) {
        val app = context.applicationContext
        appContext = app
        refresh(app)
        startNetworkWatch(app)
    }

    fun setNetworkAdaptHook(hook: (() -> Unit)?) {
        networkAdaptHook = hook
    }

    fun refresh(context: Context? = null) {
        val ctx = context?.applicationContext ?: appContext ?: return
        val previousBound = boundVpnNetwork
        val next = buildStatus(ctx)
        status = next
        // Keep this process on the VPN network while Clash is routing so
        // OkHttp/WebView/HttpURLConnection cannot escape via allowBypass.
        applyVpnProcessBinding(ctx, next)
        // Rebuild status after binding so processBound reflects truth.
        val bound = boundVpnNetwork != null
        val published =
            if (next.processBound == bound) {
                next
            } else {
                next.copy(processBound = bound)
            }
        status = published
        if (previousBound != boundVpnNetwork) {
            runCatching { networkAdaptHook?.invoke() }
        }
        listeners.forEach { listener ->
            mainHandler.post { listener(published) }
        }
    }

    fun addListener(listener: (Status) -> Unit) {
        listeners.add(listener)
        listener(status)
    }

    fun removeListener(listener: (Status) -> Unit) {
        listeners.remove(listener)
    }

    fun shouldSkipManualProxy(context: Context): Boolean =
        isAutoAdaptEnabled(context) && status.isClashVpnRouting

    /**
     * Context-free variant for HTTP factories that have no [Context] handle.
     * Uses the [appContext] captured by [start]; returns false before start.
     *
     * When true, process sockets are already bound to the VPN network — callers
     * must not stack an additional app-level or JVM system proxy.
     */
    fun shouldSkipManualProxy(): Boolean {
        val ctx = appContext ?: return false
        return shouldSkipManualProxy(ctx)
    }

    private fun prefs(context: Context): SharedPreferences =
        cachedPrefs ?: context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .also { cachedPrefs = it }

    private fun buildStatus(context: Context): Status {
        val clashInstalled = isClashInstalled(context)
        val vpnActive = isVpnActive(context)
        val read = queryPartnerStatus(context)
        val status = read.values
        // Provider 状态可信（Basic/Full）时以它为准。被拒时返回的 bundle 也非空但没有任何
        // 状态字段，必须退回「Clash 已装且 VPN 活跃」的启发式——否则会把一次拒绝误判成
        // 「Clash 没在路由」，在一条活着的隧道上再叠一层手动代理。
        val clashVpnRunning =
            if (status != null) {
                status["vpnRunning"] as? Boolean == true &&
                    status["partnerAppAutoAdapt"] as? Boolean == true
            } else {
                clashInstalled && vpnActive
            }
        return Status(
            clashInstalled = clashInstalled,
            vpnActive = vpnActive,
            clashVpnRunning = clashVpnRunning,
            partnerAppAutoAdapt = (status?.get("partnerAppAutoAdapt") as? Boolean)
                ?: (status?.get("piliPlusAutoAdapt") as? Boolean)
                ?: true,
            profileName = status?.get("name") as? String,
            clashPackage = status?.get("package") as? String,
            partnerStatusAvailable = status != null,
            partnerAccess = read.access,
            partnerDeniedReason = read.deniedReason,
            processBound = boundVpnNetwork != null,
        )
    }

    private fun startNetworkWatch(context: Context) {
        if (networkCallback != null) return
        val cm = context.getSystemService<ConnectivityManager>() ?: return
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = onNetworkMaybeChanged()

                override fun onLost(network: Network) = onNetworkMaybeChanged()

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) = onNetworkMaybeChanged()
            }
        networkCallback = callback
        val request =
            NetworkRequest
                .Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
        runCatching { cm.registerNetworkCallback(request, callback) }
            .onFailure {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        cm.registerDefaultNetworkCallback(callback)
                    }
                }
            }
    }

    private fun onNetworkMaybeChanged() {
        val context = appContext ?: return
        val cm = context.getSystemService<ConnectivityManager>()
        val vpnNetwork = cm?.let { findVpnNetwork(it) }
        val vpnActive = vpnNetwork != null
        // Re-evaluate when VPN goes up/down *or* the underlying Network handle
        // is replaced (Clash restart / re-establish) so process binding follows.
        if (lastVpnActive == vpnActive && lastVpnNetwork == vpnNetwork) return
        lastVpnActive = vpnActive
        lastVpnNetwork = vpnNetwork
        refresh(context)
    }

    private fun isVpnActive(context: Context): Boolean {
        val cm = context.getSystemService<ConnectivityManager>() ?: return false
        return findVpnNetwork(cm) != null
    }

    private fun isClashInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return clashPackages.any { pkg ->
            try {
                pm.getApplicationInfo(pkg, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    private fun queryPartnerStatus(context: Context): PartnerRead {
        val resolver = context.contentResolver
        for ((pkg, uri) in partnerStatusUris) {
            val bundle =
                runCatching {
                    resolver.call(uri, METHOD_PARTNER_STATUS, null, null)
                }.getOrNull() ?: continue
            val values = mapOf(
                "accessTier" to bundle.getString("accessTier"),
                "deniedReason" to bundle.getString("deniedReason"),
                "running" to bundle.getBoolean("running", false),
                "vpnRunning" to bundle.getBoolean("vpnRunning", false),
                "partnerAppAutoAdapt" to
                    bundle.getBoolean(
                        "partnerAppAutoAdapt",
                        bundle.getBoolean("piliPlusAutoAdapt", true),
                    ),
                "piliPlusAutoAdapt" to bundle.getBoolean("piliPlusAutoAdapt", true),
                "name" to bundle.getString("name"),
                "package" to (bundle.getString("package") ?: pkg),
            )
            val access = parseClashAccess(values)
            return PartnerRead(
                access = access,
                deniedReason = values["deniedReason"] as? String,
                values = values.takeIf { access != ClashAccess.Denied },
            )
        }
        return UNAVAILABLE_PARTNER
    }

    /**
     * Bind (or unbind) this process to the active VPN network while Clash is
     * routing. Without this, [NetworkCapabilities.NET_CAPABILITY_NOT_VPN]
     * requests and [android.net.VpnService.allowBypass] can let OkHttp/WebView
     * leave the tunnel even though Clash is "on".
     */
    private fun applyVpnProcessBinding(context: Context, status: Status) {
        val cm = context.getSystemService<ConnectivityManager>() ?: return
        if (!isAutoAdaptEnabled(context) || !status.isClashVpnRouting) {
            clearProcessNetworkBinding(cm)
            return
        }
        val vpn = findVpnNetwork(cm)
        if (vpn == null) {
            // Status says routing but no VPN Network is visible yet — drop any
            // stale binding so we do not stick to a dead Network handle.
            clearProcessNetworkBinding(cm)
            return
        }
        if (boundVpnNetwork == vpn) return
        runCatching {
            cm.bindProcessToNetwork(vpn)
            boundVpnNetwork = vpn
        }.onFailure {
            boundVpnNetwork = null
        }
    }

    private fun clearProcessNetworkBinding(cm: ConnectivityManager) {
        if (boundVpnNetwork == null && cm.boundNetworkForProcess == null) return
        runCatching { cm.bindProcessToNetwork(null) }
        boundVpnNetwork = null
    }

    private fun findVpnNetwork(cm: ConnectivityManager): Network? {
        @Suppress("DEPRECATION")
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return network
            }
        }
        return null
    }
}
