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

    @Upsert
    suspend fun upsert(entitlement: EntitlementEntity)
}
