package com.projectlumen.app.core.database.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.projectlumen.app.core.database.entities.TipTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TipTemplatesDao {
    @Query("SELECT * FROM tip_templates ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<TipTemplateEntity>>

    @Query("SELECT * FROM tip_templates WHERE id = :id")
    suspend fun get(id: Long): TipTemplateEntity?

    @Query("SELECT COUNT(*) FROM tip_templates")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(template: TipTemplateEntity): Long
}
