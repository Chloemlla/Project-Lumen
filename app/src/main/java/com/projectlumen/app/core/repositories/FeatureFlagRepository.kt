package com.projectlumen.app.core.repositories

import com.projectlumen.app.core.database.daos.FeatureFlagsDao
import com.projectlumen.app.core.database.entities.FeatureFlagEntity
import com.projectlumen.app.core.mmkv.ProjectLumenMmkv
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

class FeatureFlagRepository(private val dao: FeatureFlagsDao) {
    fun observeAll(): Flow<List<FeatureFlagEntity>> = FeatureFlagMmkvStore.observeAll(dao)

    suspend fun isEnabled(key: String): Boolean = FeatureFlagMmkvStore.getAll(dao).firstOrNull { it.key == key }?.enabled == true

    suspend fun getAll(): List<FeatureFlagEntity> = FeatureFlagMmkvStore.getAll(dao)

    suspend fun upsert(flag: FeatureFlagEntity) {
        FeatureFlagMmkvStore.upsert(dao, flag)
    }
}

private object FeatureFlagMmkvStore {
    private const val STORE_ID = "feature_flags"
    private const val KEY_FLAGS_JSON = "flags_json"
    private const val KEY_MMKV_MIGRATION_COMPLETE = "__mmkv_migration_complete"

    private val migrationLock = Mutex()
    private val mmkv by lazy { ProjectLumenMmkv.multiProcessMmkvWithId(STORE_ID) }
    private val state by lazy { MutableStateFlow(readFromMmkv()) }

    fun observeAll(dao: FeatureFlagsDao): Flow<List<FeatureFlagEntity>> {
        return flow {
            ensureMigrated(dao)
            state.value = readFromMmkv()
            emitAll(state)
        }
    }

    suspend fun getAll(dao: FeatureFlagsDao): List<FeatureFlagEntity> {
        ensureMigrated(dao)
        return readFromMmkv().also { state.value = it }
    }

    suspend fun upsert(dao: FeatureFlagsDao, flag: FeatureFlagEntity) {
        ensureMigrated(dao)
        val normalized = flag.copy(key = flag.key.trim())
        if (normalized.key.isBlank()) return
        dao.upsert(normalized)
        writeToMmkv(
            readFromMmkv()
                .filterNot { it.key == normalized.key }
                .plus(normalized)
                .sortedBy { it.key },
        )
    }

    private suspend fun ensureMigrated(dao: FeatureFlagsDao) {
        if (mmkv.decodeBool(KEY_MMKV_MIGRATION_COMPLETE, false)) return
        migrationLock.withLock {
            if (mmkv.decodeBool(KEY_MMKV_MIGRATION_COMPLETE, false)) return
            if (!mmkv.containsKey(KEY_FLAGS_JSON)) {
                dao.getAll().takeIf { it.isNotEmpty() }?.let(::writeToMmkv)
            }
            mmkv.encode(KEY_MMKV_MIGRATION_COMPLETE, true)
            state.value = readFromMmkv()
        }
    }

    private fun readFromMmkv(): List<FeatureFlagEntity> {
        val json = mmkv.decodeString(KEY_FLAGS_JSON)?.takeIf { it.isNotBlank() } ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val flag = array.optJSONObject(index)?.toFeatureFlag() ?: continue
                    if (flag.key.isNotBlank()) add(flag)
                }
            }.sortedBy { it.key }
        }.getOrDefault(emptyList())
    }

    private fun writeToMmkv(flags: List<FeatureFlagEntity>) {
        val array = JSONArray()
        flags.sortedBy { it.key }.forEach { flag ->
            array.put(flag.toJson())
        }
        mmkv.encode(KEY_FLAGS_JSON, array.toString())
        state.value = flags.sortedBy { it.key }
    }

    private fun FeatureFlagEntity.toJson(): JSONObject = JSONObject()
        .put("key", key)
        .put("enabled", enabled)
        .put("payloadJson", payloadJson)
        .put("updatedAt", updatedAt)

    private fun JSONObject.toFeatureFlag(): FeatureFlagEntity {
        return FeatureFlagEntity(
            key = optString("key").trim(),
            enabled = optBoolean("enabled", false),
            payloadJson = optString("payloadJson"),
            updatedAt = optLong("updatedAt", 0L).takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
    }
}
