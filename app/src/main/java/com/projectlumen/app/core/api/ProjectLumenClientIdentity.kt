package com.projectlumen.app.core.api

import android.os.Build

/**
 * Project-Lumen 访问 Synapse 时的官方客户端身份。
 *
 * Synapse 用 `X-Client-Name` / `X-Platform` / `X-Device-Id` / `X-Device-Name` 决定这条会话
 * 属于谁（后端 `getAuthSessionMetadata()` 的候选键），字面量与 Synapse-Client 保持同一套：
 * 设备名按 `MANUFACTURER MODEL` 拼，平台写成 `Android`。没有这几个头，后端只能从 user-agent
 * 反推，「设备与会话」里就只剩一个未知客户端，撤销也认不出是哪台机器。
 */
internal object ProjectLumenClientIdentity {
    const val CLIENT_NAME: String = "Project-Lumen"
    const val PLATFORM: String = "Android"
    const val USER_AGENT: String = "Project-Lumen-Android"

    /**
     * 安装标识的唯一来源（`SecureCredentialStore.deviceInstallationId()`）。
     * 首访闸门的指纹也用它：两处各读一份会让令牌上的指纹与设备分组里的标识对不上。
     */
    @Volatile
    private var deviceIdProvider: (() -> String?)? = null

    fun installDeviceIdProvider(provider: () -> String?) {
        deviceIdProvider = provider
    }

    fun deviceInstallationId(): String? =
        runCatching { deviceIdProvider?.invoke()?.trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

    /** 与 Synapse-Client `defaultDeviceName()` 同构造，面板里两台安卓设备才会长得一样。 */
    val deviceName: String
        get() = runCatching {
            listOf(Build.MANUFACTURER, Build.MODEL).joinToString(" ").trim()
        }.getOrDefault("").ifBlank { "Android device" }

    /** 设备标识缺失时只发客户端名与平台：把空串写进 `X-Device-Id` 会让后端把
     * 「没有设备标识」当成一个真实的设备分组。 */
    fun headers(): Map<String, String> = headers(deviceInstallationId())

    fun headers(deviceInstallationId: String?): Map<String, String> {
        val installation = deviceInstallationId?.trim()?.takeIf { it.isNotEmpty() }
        val resolvedDeviceName = deviceName
        return buildMap {
            put("X-Client-Name", CLIENT_NAME)
            put("X-Platform", PLATFORM)
            put("X-Device-Name", resolvedDeviceName)
            if (installation != null) put("X-Device-Id", installation)
        }
    }
}
