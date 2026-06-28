package com.projectlumen.app.core.database.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RuntimeStateDao {
    @Query("SELECT * FROM runtime_state WHERE id = 1")
    fun observe(): Flow<RuntimeStateEntity?>

    @Query("SELECT * FROM runtime_state WHERE id = 1")
    suspend fun get(): RuntimeStateEntity?

    @Upsert
    suspend fun upsert(state: RuntimeStateEntity)
}
