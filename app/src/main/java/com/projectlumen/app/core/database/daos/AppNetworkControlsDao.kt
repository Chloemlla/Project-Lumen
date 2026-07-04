package com.projectlumen.app.core.database.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.projectlumen.app.core.database.entities.AppNetworkControlEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppNetworkControlsDao {
    @Query("SELECT * FROM app_network_controls ORDER BY networkRestricted DESC, updatedAt DESC, packageName ASC")
    fun observeAll(): Flow<List<AppNetworkControlEntity>>

    @Query("SELECT * FROM app_network_controls WHERE packageName = :packageName")
    suspend fun get(packageName: String): AppNetworkControlEntity?

    @Query("SELECT * FROM app_network_controls ORDER BY networkRestricted DESC, updatedAt DESC, packageName ASC")
    suspend fun getAll(): List<AppNetworkControlEntity>

    @Upsert
    suspend fun upsert(control: AppNetworkControlEntity)
}
