package com.projectlumen.app.core.repositories

import com.projectlumen.app.core.database.daos.EntitlementsDao
import com.projectlumen.app.core.database.entities.EntitlementEntity
import com.projectlumen.app.core.enums.PlanTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class EntitlementRepository(private val dao: EntitlementsDao) {
    fun observeAll(): Flow<List<EntitlementEntity>> = dao.observeAll()

    fun observeTier(settingsTier: Flow<String>): Flow<PlanTier> {
        return settingsTier.map { PlanTier.entries.firstOrNull { tier -> tier.name.equals(it, ignoreCase = true) } ?: PlanTier.FREE }
    }

    suspend fun getAll(): List<EntitlementEntity> = dao.getAll()

    suspend fun upsert(entitlement: EntitlementEntity) {
        // id 走 autoGenerate 默认值 0 时 @Upsert 永远撞不上主键约束，必须先按业务标识解析已有行，否则每次同步都新插一行。
        EntitlementWriteLock.mutex.withLock {
            val resolvedId = if (entitlement.id != 0L) {
                entitlement.id
            } else {
                dao.findByIdentity(entitlement.source, entitlement.productId, entitlement.purchaseToken)?.id ?: 0L
            }
            dao.upsert(if (resolvedId == entitlement.id) entitlement else entitlement.copy(id = resolvedId))
        }
    }
}

private object EntitlementWriteLock {
    val mutex = Mutex()
}
