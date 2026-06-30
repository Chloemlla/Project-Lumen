package com.projectlumen.app.core.billing

import com.projectlumen.app.core.enums.PlanTier
import com.projectlumen.app.core.enums.PremiumFeature
import kotlinx.coroutines.flow.Flow

interface EntitlementChecker {
    fun observeTier(): Flow<PlanTier>
    fun canUse(tier: PlanTier, feature: PremiumFeature): Boolean
}
