package com.projectlumen.app.core.api

import com.projectlumen.app.core.mmkv.ProjectLumenMmkv

internal data class PersistedBackendConnectivity(
    val lastConfirmedStatus: BackendHealthStatus = BackendHealthStatus.UNKNOWN,
    val lastCheckedAtMillis: Long = 0L,
    val lastReachableAtMillis: Long = 0L,
    val consecutiveFailures: Int = 0,
    val developerForceEnabled: Boolean = false,
    val lastErrorCode: String = "",
)

internal interface BackendConnectivityPersistence {
    fun load(): PersistedBackendConnectivity
    fun save(value: PersistedBackendConnectivity)
}

internal class MmkvBackendConnectivityPersistence : BackendConnectivityPersistence {
    private val mmkv by lazy { ProjectLumenMmkv.mmkvWithId(STORE_ID) }

    override fun load(): PersistedBackendConnectivity {
        return runCatching {
            PersistedBackendConnectivity(
                lastConfirmedStatus = mmkv.decodeString(KEY_LAST_CONFIRMED_STATUS)
                    ?.let(::parseStableStatus)
                    ?: BackendHealthStatus.UNKNOWN,
                lastCheckedAtMillis = mmkv.decodeLong(KEY_LAST_CHECKED_AT, 0L).coerceAtLeast(0L),
                lastReachableAtMillis = mmkv.decodeLong(KEY_LAST_REACHABLE_AT, 0L).coerceAtLeast(0L),
                consecutiveFailures = mmkv.decodeInt(KEY_CONSECUTIVE_FAILURES, 0).coerceAtLeast(0),
                developerForceEnabled = mmkv.decodeBool(KEY_DEVELOPER_FORCE_ENABLED, false),
                lastErrorCode = mmkv.decodeString(KEY_LAST_ERROR_CODE).orEmpty().take(MAX_ERROR_CODE_LENGTH),
            )
        }.getOrDefault(PersistedBackendConnectivity())
    }

    override fun save(value: PersistedBackendConnectivity) {
        runCatching {
            mmkv.encode(KEY_LAST_CONFIRMED_STATUS, stableStatus(value.lastConfirmedStatus).name)
            mmkv.encode(KEY_LAST_CHECKED_AT, value.lastCheckedAtMillis.coerceAtLeast(0L))
            mmkv.encode(KEY_LAST_REACHABLE_AT, value.lastReachableAtMillis.coerceAtLeast(0L))
            mmkv.encode(KEY_CONSECUTIVE_FAILURES, value.consecutiveFailures.coerceAtLeast(0))
            mmkv.encode(KEY_DEVELOPER_FORCE_ENABLED, value.developerForceEnabled)
            mmkv.encode(KEY_LAST_ERROR_CODE, value.lastErrorCode.take(MAX_ERROR_CODE_LENGTH))
        }
    }

    private fun parseStableStatus(value: String): BackendHealthStatus {
        return runCatching { BackendHealthStatus.valueOf(value) }
            .getOrDefault(BackendHealthStatus.UNKNOWN)
            .let(::stableStatus)
    }

    private fun stableStatus(status: BackendHealthStatus): BackendHealthStatus {
        return when (status) {
            BackendHealthStatus.REACHABLE,
            BackendHealthStatus.UNREACHABLE -> status
            BackendHealthStatus.UNKNOWN,
            BackendHealthStatus.CHECKING -> BackendHealthStatus.UNKNOWN
        }
    }

    private companion object {
        private const val STORE_ID = "backend_connectivity"
        private const val KEY_LAST_CONFIRMED_STATUS = "last_confirmed_status"
        private const val KEY_LAST_CHECKED_AT = "last_checked_at"
        private const val KEY_LAST_REACHABLE_AT = "last_reachable_at"
        private const val KEY_CONSECUTIVE_FAILURES = "consecutive_failures"
        private const val KEY_DEVELOPER_FORCE_ENABLED = "developer_force_enabled"
        private const val KEY_LAST_ERROR_CODE = "last_error_code"
        private const val MAX_ERROR_CODE_LENGTH = 64
    }
}
