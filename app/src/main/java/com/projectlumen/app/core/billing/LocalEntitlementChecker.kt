package com.projectlumen.app.core.billing

import com.projectlumen.app.core.enums.PlanTier
import com.projectlumen.app.core.enums.PremiumFeature
import kotlinx.coroutines.flow.Flow

class LocalEntitlementChecker(
    private val tierFlow: Flow<PlanTier>,
) : EntitlementChecker {
    override fun observeTier(): Flow<PlanTier> = tierFlow

    override fun canUse(tier: PlanTier, feature: PremiumFeature): Boolean {
        return when (feature) {
            PremiumFeature.PRO_TEMPLATES,
            PremiumFeature.ADVANCED_STATISTICS,
            PremiumFeature.LOCAL_BACKUP,
            PremiumFeature.MULTIPLE_REMINDER_PLANS,
            PremiumFeature.ADVANCED_EXPORT -> tier >= PlanTier.PRO
            PremiumFeature.CLOUD_SYNC -> tier >= PlanTier.PLUS
        }
    }
}
