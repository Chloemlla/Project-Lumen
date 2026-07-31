package com.projectlumen.app.app

import com.projectlumen.app.core.api.BackendConnectivityState
import com.projectlumen.app.core.api.BackendHealthStatus
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendFeatureVisibilityTest {
    @Test
    fun mainBackendUiDecisionFollowsEffectiveAccessInsteadOfActualStatusAlone() {
        val blocked = mainBackendUiDecision(
            state = BackendConnectivityState(status = BackendHealthStatus.UNREACHABLE),
            nowMillis = 1_000L,
        )
        val forced = mainBackendUiDecision(
            state = BackendConnectivityState(
                status = BackendHealthStatus.UNREACHABLE,
                developerForceEnabled = true,
            ),
            nowMillis = 1_000L,
        )

        assertFalse(blocked.visible)
        assertTrue(forced.visible)
        assertTrue(forced.forced)
    }

    @Test
    fun diagnosticsDisappearWhileLocalPermissionControlsRemain() {
        val targets = privacyControlTiles(
            settings = AppSettingsEntity(diagnosticTelemetryUploadEnabled = true),
            permissionRequirements = noPermissionRequirements(),
            shizukuReady = true,
            backendFeaturesVisible = false,
        ).map { it.target }

        assertFalse(PermissionSetupTarget.DIAGNOSTICS in targets)
        assertTrue(PermissionSetupTarget.STATISTICS in targets)
        assertTrue(PermissionSetupTarget.NOTIFICATIONS in targets)
        assertTrue(PermissionSetupTarget.SHIZUKU in targets)
    }

    @Test
    fun diagnosticsReturnWhenBackendAccessIsEffective() {
        val targets = privacyControlTiles(
            settings = AppSettingsEntity(),
            permissionRequirements = noPermissionRequirements(),
            shizukuReady = false,
            backendFeaturesVisible = true,
        ).map { it.target }

        assertTrue(PermissionSetupTarget.DIAGNOSTICS in targets)
    }

    @Test
    fun cloudCapabilityIsRemovedFromGrowthDenominatorWhenHidden() {
        val hidden = growthCapabilitySummary(
            proTemplatesReady = true,
            advancedReportsReady = true,
            cloudSyncReady = true,
            familyModeReady = false,
            aiGuidanceReady = true,
            cloudCapabilityVisible = false,
        )
        val visible = growthCapabilitySummary(
            proTemplatesReady = true,
            advancedReportsReady = true,
            cloudSyncReady = true,
            familyModeReady = false,
            aiGuidanceReady = true,
            cloudCapabilityVisible = true,
        )

        assertEquals(GrowthCapabilitySummary(activeCount = 3, totalCount = 4), hidden)
        assertEquals(GrowthCapabilitySummary(activeCount = 4, totalCount = 5), visible)
    }

    private fun noPermissionRequirements() = PermissionRequirements(
        notification = false,
        camera = false,
        exactAlarm = false,
        fullScreenIntent = false,
        overlay = false,
        writeSettings = false,
        usageAccess = false,
    )
}
