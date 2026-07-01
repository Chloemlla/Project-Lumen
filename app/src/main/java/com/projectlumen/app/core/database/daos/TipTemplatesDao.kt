package com.projectlumen.app.core.database.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.projectlumen.app.core.database.entities.TipTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TipTemplatesDao {
    @Query("SELECT * FROM tip_templates WHERE deletedAt = 0 ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<TipTemplateEntity>>

    @Query("SELECT * FROM tip_templates WHERE id = :id AND deletedAt = 0")
    suspend fun get(id: Long): TipTemplateEntity?

    @Query("SELECT COUNT(*) FROM tip_templates WHERE deletedAt = 0")
    suspend fun count(): Int

    @Query("SELECT * FROM tip_templates ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllIncludingDeleted(): List<TipTemplateEntity>

    @Query("UPDATE tip_templates SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE isBuiltin = 1 AND id NOT IN (:retainedIds) AND deletedAt = 0")
    suspend fun softDeleteObsoleteBuiltinTemplates(retainedIds: List<Long>, deletedAt: Long)

    @Upsert
    suspend fun upsert(template: TipTemplateEntity)
}
