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
        AppSettingsWriteLock.mutex.withLock {
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
    }

    suspend fun update(
        nowMillis: Long = System.currentTimeMillis(),
        transform: (AppSettingsEntity) -> AppSettingsEntity,
    ): AppSettingsEntity {
        return AppSettingsWriteLock.mutex.withLock {
            val current = getOrDefault()
            val updated = transform(current).copy(id = 1, updatedAt = nowMillis)
            // MMKV 是读路径的权威源，必须先落地；否则两次写之间被杀会让陈旧 MMKV 永久覆盖新 Room。
            preferences?.saveFromSettings(updated)
            dao.upsert(updated)
            updated
        }
    }
}

// 仓库在 5 处独立构造，实例级 Mutex 串行不了并发写者，锁必须挂在进程级 object 上。
private object AppSettingsWriteLock {
    val mutex = Mutex()
}
