package com.projectlumen.app.core.api

fun interface BackendCapabilityGate {
    fun decision(capability: BackendCapability): BackendCapabilityDecision

    fun requireExecutable(capability: BackendCapability) {
        val decision = decision(capability)
        if (!decision.executable) {
            throw BackendCommunicationBlockedException(capability, decision.reason.code)
        }
    }
}

object AllowAllBackendCapabilityGate : BackendCapabilityGate {
    override fun decision(capability: BackendCapability): BackendCapabilityDecision {
        return BackendCapabilityDecision(
            visible = true,
            executable = true,
            forced = false,
            reason = if (capability == BackendCapability.HEALTH_PROBE) {
                BackendCapabilityReason.HEALTH_PROBE
            } else {
                BackendCapabilityReason.REACHABLE
            },
        )
    }
}

object BackendCommunicationPolicy {
    const val RECENT_REACHABLE_TTL_MILLIS = 5 * 60_000L

    fun resolve(
        state: BackendConnectivityState,
        capability: BackendCapability,
        nowMillis: Long,
    ): BackendCapabilityDecision {
        if (capability == BackendCapability.HEALTH_PROBE) {
            return allowed(BackendCapabilityReason.HEALTH_PROBE)
        }
        if (state.developerForceEnabled) {
            return allowed(BackendCapabilityReason.DEVELOPER_FORCE_ENABLED, forced = true)
        }
        if (state.status == BackendHealthStatus.REACHABLE) {
            return allowed(BackendCapabilityReason.REACHABLE)
        }
        if (
            state.status == BackendHealthStatus.CHECKING &&
            state.lastConfirmedStatus == BackendHealthStatus.REACHABLE &&
            state.lastReachableAtMillis > 0L &&
            nowMillis - state.lastReachableAtMillis in 0L..RECENT_REACHABLE_TTL_MILLIS
        ) {
            return allowed(BackendCapabilityReason.RECENTLY_REACHABLE)
        }
        val reason = when (state.status) {
            BackendHealthStatus.UNKNOWN -> BackendCapabilityReason.UNKNOWN
            BackendHealthStatus.CHECKING -> BackendCapabilityReason.CHECKING
            BackendHealthStatus.UNREACHABLE -> BackendCapabilityReason.UNREACHABLE
            BackendHealthStatus.REACHABLE -> BackendCapabilityReason.REACHABLE
        }
        return BackendCapabilityDecision(
            visible = false,
            executable = false,
            forced = false,
            reason = reason,
        )
    }

    private fun allowed(
        reason: BackendCapabilityReason,
        forced: Boolean = false,
    ): BackendCapabilityDecision {
        return BackendCapabilityDecision(
            visible = true,
            executable = true,
            forced = forced,
            reason = reason,
        )
    }
}

object BackendRetryPolicy {
    private val retryDelaysMillis = longArrayOf(5_000L, 30_000L, 120_000L, 300_000L)

    fun delayMillis(consecutiveFailures: Int): Long {
        val index = (consecutiveFailures.coerceAtLeast(1) - 1).coerceAtMost(retryDelaysMillis.lastIndex)
        return retryDelaysMillis[index]
    }
}
