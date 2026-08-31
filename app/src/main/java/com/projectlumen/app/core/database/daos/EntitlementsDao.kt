package com.projectlumen.app.core.database.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.projectlumen.app.core.database.entities.EntitlementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntitlementsDao {
    @Query("SELECT * FROM entitlements ORDER BY purchasedAt DESC, id DESC")
    fun observeAll(): Flow<List<EntitlementEntity>>

    @Query("SELECT * FROM entitlements ORDER BY purchasedAt DESC, id DESC")
    suspend fun getAll(): List<EntitlementEntity>

    @Query(
        "SELECT * FROM entitlements WHERE source = :source AND productId = :productId " +
            "AND purchaseToken = :purchaseToken ORDER BY id DESC LIMIT 1",
    )
    suspend fun findByIdentity(source: String, productId: String, purchaseToken: String): EntitlementEntity?

    @Upsert
    suspend fun upsert(entitlement: EntitlementEntity)
}
