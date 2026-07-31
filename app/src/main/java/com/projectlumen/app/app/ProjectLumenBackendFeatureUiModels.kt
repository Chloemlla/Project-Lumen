package com.projectlumen.app.app

import com.projectlumen.app.core.api.BackendCapability
import com.projectlumen.app.core.api.BackendCapabilityDecision
import com.projectlumen.app.core.api.BackendCommunicationPolicy
import com.projectlumen.app.core.api.BackendConnectivityState

internal fun mainBackendUiDecision(
    state: BackendConnectivityState,
    nowMillis: Long,
): BackendCapabilityDecision {
    return BackendCommunicationPolicy.resolve(
        state = state,
        capability = BackendCapability.ACCOUNT_SESSION,
        nowMillis = nowMillis,
    )
}

internal data class GrowthCapabilitySummary(
    val activeCount: Int,
    val totalCount: Int,
)

internal fun growthCapabilitySummary(
    proTemplatesReady: Boolean,
    advancedReportsReady: Boolean,
    cloudSyncReady: Boolean,
    familyModeReady: Boolean,
    aiGuidanceReady: Boolean,
    cloudCapabilityVisible: Boolean,
): GrowthCapabilitySummary {
    val readiness = buildList {
        add(proTemplatesReady)
        add(advancedReportsReady)
        if (cloudCapabilityVisible) add(cloudSyncReady)
        add(familyModeReady)
        add(aiGuidanceReady)
    }
    return GrowthCapabilitySummary(
        activeCount = readiness.count { it },
        totalCount = readiness.size,
    )
}
