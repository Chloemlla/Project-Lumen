package com.projectlumen.app.app

import com.projectlumen.app.core.database.entities.EntitlementEntity
import com.projectlumen.app.core.enums.PlanTier
import com.projectlumen.app.core.repositories.EntitlementRepository
import com.projectlumen.app.core.repositories.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class ProjectLumenEntitlementFeatureEntry(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val entitlementRepository: EntitlementRepository,
) {
    fun recordManualProEntitlement(productId: String = "manual_pro") {
        scope.launch {
            val nowMillis = System.currentTimeMillis()
            entitlementRepository.upsert(
                EntitlementEntity(
                    source = "manual_license",
                    productId = productId,
                    tier = PlanTier.PRO.name,
                    status = "active",
                    purchasedAt = nowMillis,
                    lastVerifiedAt = nowMillis,
                ),
            )
            settingsRepository.update(nowMillis) {
                it.copy(
                    planTier = PlanTier.PRO.name,
                    entitlementExpiresAt = 0L,
                    lastEntitlementSyncAt = nowMillis,
                )
            }
        }
    }
}
