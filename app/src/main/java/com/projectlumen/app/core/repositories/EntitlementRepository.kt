package com.projectlumen.app.core.repositories

import com.projectlumen.app.core.database.daos.EntitlementsDao
import com.projectlumen.app.core.database.entities.EntitlementEntity
import com.projectlumen.app.core.enums.PlanTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EntitlementRepository(private val dao: EntitlementsDao) {
    fun observeAll(): Flow<List<EntitlementEntity>> = dao.observeAll()

    fun observeTier(settingsTier: Flow<String>): Flow<PlanTier> {
        return settingsTier.map { PlanTier.entries.firstOrNull { tier -> tier.name.equals(it, ignoreCase = true) } ?: PlanTier.FREE }
    }

    suspend fun getAll(): List<EntitlementEntity> = dao.getAll()

    suspend fun upsert(entitlement: EntitlementEntity) {
        dao.upsert(entitlement)
    }
}
