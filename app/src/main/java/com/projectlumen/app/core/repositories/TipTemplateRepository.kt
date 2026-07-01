package com.projectlumen.app.core.repositories

import com.projectlumen.app.core.database.daos.TipTemplatesDao
import com.projectlumen.app.core.database.entities.TipTemplateEntity
import kotlinx.coroutines.flow.Flow

class TipTemplateRepository(private val dao: TipTemplatesDao) {
    fun observeAll(): Flow<List<TipTemplateEntity>> = dao.observeAll()

    suspend fun get(id: Long): TipTemplateEntity? = dao.get(id)

    suspend fun upsert(template: TipTemplateEntity) {
        dao.upsert(template)
    }

    suspend fun softDeleteObsoleteBuiltinTemplates(retainedIds: List<Long>, deletedAt: Long) {
        dao.softDeleteObsoleteBuiltinTemplates(retainedIds, deletedAt)
    }
}
