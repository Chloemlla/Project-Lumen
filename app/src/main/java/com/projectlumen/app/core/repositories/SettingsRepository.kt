package com.projectlumen.app.core.repositories

import com.projectlumen.app.core.database.daos.AppSettingsDao
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.preferences.EyeCarePreferencesDataStore
import com.projectlumen.app.core.preferences.withEyeCarePreferences
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SettingsRepository(
    private val dao: AppSettingsDao,
    private val preferences: EyeCarePreferencesDataStore? = null,
    private val deviceInstallationIdProvider: ((String?) -> String)? = null,
) {
    // Serializes the whole-row read-modify-write in update() so concurrent writers
    // (settings UI, ProximityDetectionService, remote sync) cannot clobber each other's fields.
    private val updateMutex = Mutex()
    fun observe(): Flow<AppSettingsEntity?> {
        val preferencesStore = preferences ?: return dao.observe()
        return combine(dao.observe(), preferencesStore.observe()) { settings, persistedPreferences ->
            settings?.withEyeCarePreferences(persistedPreferences)
        }
    }

    suspend fun get(): AppSettingsEntity? {
        val settings = dao.get() ?: return null
        val persistedPreferences = preferences?.read() ?: return settings
        return settings.withEyeCarePreferences(persistedPreferences)
    }

    suspend fun getOrDefault(): AppSettingsEntity {
        get()?.let { return it }
        val defaultSettings = AppSettingsEntity(
            deviceInstallationId = deviceInstallationIdProvider?.invoke(null).orEmpty(),
        )
        val persistedPreferences = preferences?.read() ?: return defaultSettings
        return defaultSettings.withEyeCarePreferences(persistedPreferences)
    }

    suspend fun ensureDefault() {
        val current = dao.get()
        val preferredDeviceInstallationId = deviceInstallationIdProvider?.invoke(current?.deviceInstallationId)
        val baseSettings = if (current == null) {
            AppSettingsEntity(
                deviceInstallationId = preferredDeviceInstallationId ?: UUID.randomUUID().toString(),
            ).also { dao.upsert(it) }
        } else if (current.deviceInstallationId.isBlank()) {
            current.copy(
                deviceInstallationId = preferredDeviceInstallationId ?: UUID.randomUUID().toString(),
                updatedAt = System.currentTimeMillis(),
            ).also { dao.upsert(it) }
        } else if (preferredDeviceInstallationId != null && preferredDeviceInstallationId != current.deviceInstallationId) {
            current.copy(
                deviceInstallationId = preferredDeviceInstallationId,
                updatedAt = System.currentTimeMillis(),
            ).also { dao.upsert(it) }
        } else {
            current
        }

        val preferencesStore = preferences ?: return
        val persistedPreferences = preferencesStore.read()
        if (persistedPreferences.hasPersistedValues) {
            dao.upsert(baseSettings.withEyeCarePreferences(persistedPreferences))
        } else {
            preferencesStore.saveFromSettings(baseSettings)
        }
    }

    suspend fun update(
        nowMillis: Long = System.currentTimeMillis(),
        transform: (AppSettingsEntity) -> AppSettingsEntity,
    ): AppSettingsEntity = updateMutex.withLock {
        val current = getOrDefault()
        val updated = transform(current).copy(id = 1, updatedAt = nowMillis)
        // MMKV first (authoritative for these fields on the read side), then Room, so a
        // crash or MMKV write before the persisted-preferences write cannot let stale MMKV
        // values permanently override fresh Room values.
        preferences?.saveFromSettings(updated)
        dao.upsert(updated)
        updated
    }
}
