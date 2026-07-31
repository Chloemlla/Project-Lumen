package com.projectlumen.app.core.api

import java.io.IOException

enum class BackendHealthStatus {
    UNKNOWN,
    CHECKING,
    REACHABLE,
    UNREACHABLE,
}

enum class BackendCapability {
    HEALTH_PROBE,
    ACCOUNT_SESSION,
    DEVICE_REGISTRATION,
    ENTITLEMENTS_PURCHASE,
    REMOTE_CONFIG,
    CLOUD_SYNC,
    CLOUD_BACKUP,
    TELEMETRY,
    FACE_ANALYSIS,
    DEVICE_CONTROL,
    RELEASE_DISCOVERY,
}

enum class BackendCapabilityReason(val code: String) {
    HEALTH_PROBE("health_probe"),
    REACHABLE("reachable"),
    RECENTLY_REACHABLE("recently_reachable"),
    DEVELOPER_FORCE_ENABLED("developer_force_enabled"),
    UNKNOWN("backend_unknown"),
    CHECKING("backend_checking"),
    UNREACHABLE("backend_unreachable"),
}

data class BackendConnectivityState(
    val status: BackendHealthStatus = BackendHealthStatus.UNKNOWN,
    val lastConfirmedStatus: BackendHealthStatus = BackendHealthStatus.UNKNOWN,
    val lastCheckedAtMillis: Long = 0L,
    val lastReachableAtMillis: Long = 0L,
    val consecutiveFailures: Int = 0,
    val developerForceEnabled: Boolean = false,
    val lastErrorCode: String = "",
)

data class BackendCapabilityDecision(
    val visible: Boolean,
    val executable: Boolean,
    val forced: Boolean,
    val reason: BackendCapabilityReason,
)

class BackendCommunicationBlockedException(
    val capability: BackendCapability,
    val reasonCode: String,
) : IOException("Project Lumen backend communication is disabled: $reasonCode")
