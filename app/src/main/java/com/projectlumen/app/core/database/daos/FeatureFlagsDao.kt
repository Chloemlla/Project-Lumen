package com.projectlumen.app.core.database.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.projectlumen.app.core.database.entities.FeatureFlagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FeatureFlagsDao {
    @Query("SELECT * FROM feature_flags ORDER BY key ASC")
    fun observeAll(): Flow<List<FeatureFlagEntity>>

    @Query("SELECT * FROM feature_flags WHERE `key` = :key")
    suspend fun get(key: String): FeatureFlagEntity?

    @Query("SELECT * FROM feature_flags ORDER BY key ASC")
    suspend fun getAll(): List<FeatureFlagEntity>

    @Upsert
    suspend fun upsert(flag: FeatureFlagEntity)
}
