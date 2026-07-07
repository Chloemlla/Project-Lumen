package com.projectlumen.app.core.proximity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.mmkv.ProjectLumenMmkv
import com.tencent.mmkv.MMKV
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.proximityEventLegacyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "proximity_event_state",
)

class ProximityEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action !in triggerActions) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val app = context.applicationContext as? ProjectLumenApplication
            try {
                app ?: return@launch
                val settings = app.settingsRepository().get()
                if (settings?.proximityMonitoringEnabled != true && settings?.blinkMonitoringEnabled != true) return@launch
                if (settings.developerModeEnabled && !settings.developerUnlockTriggerEnabled) return@launch
                if (!ProximityCameraForegroundEligibility.canStartCameraForegroundService(app)) {
                    return@launch
                }
                if (!shouldRunEventSample(app)) return@launch
                if (app.shizuku.shouldDeferSampling(settings)) return@launch
                if (!ProximityTriggerGate(app).canRun(settings)) return@launch
                ProximityDetectionWorker.enqueueNext(app, delaySeconds = 0)
            } catch (throwable: Throwable) {
                app?.recordCrash(throwable)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val triggerActions = setOf(
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_CONFIGURATION_CHANGED,
        )
        private const val STORE_ID = "proximity_event_state"
        private const val KEY_LAST_TRIGGER_AT = "last_trigger_at"
        private const val KEY_MMKV_MIGRATION_COMPLETE = "__mmkv_migration_complete"
        private const val MIN_EVENT_TRIGGER_INTERVAL_MS = 60_000L
        private val legacyLastTriggerAtKey = longPreferencesKey(KEY_LAST_TRIGGER_AT)
        private val migrationLock = Mutex()

        private suspend fun shouldRunEventSample(context: Context): Boolean {
            val now = System.currentTimeMillis()
            val store = ProjectLumenMmkv.mmkvWithId(STORE_ID)
            migrateLegacyEventState(context.applicationContext, store)
            val lastTriggerAt = store.decodeLong(KEY_LAST_TRIGGER_AT, 0L)
            if (now - lastTriggerAt < MIN_EVENT_TRIGGER_INTERVAL_MS) return false
            store.encode(KEY_LAST_TRIGGER_AT, now)
            return true
        }

        private suspend fun migrateLegacyEventState(context: Context, store: MMKV) {
            if (store.decodeBool(KEY_MMKV_MIGRATION_COMPLETE, false)) return
            migrationLock.withLock {
                if (store.decodeBool(KEY_MMKV_MIGRATION_COMPLETE, false)) return
                if (!store.containsKey(KEY_LAST_TRIGGER_AT)) {
                    val legacyLastTriggerAt = context.proximityEventLegacyDataStore.data
                        .catch { throwable ->
                            if (throwable is IOException) {
                                emit(emptyPreferences())
                            } else {
                                throw throwable
                            }
                        }
                        .first()[legacyLastTriggerAtKey] ?: 0L
                    if (legacyLastTriggerAt > 0L) {
                        store.encode(KEY_LAST_TRIGGER_AT, legacyLastTriggerAt)
                    }
                }
                store.encode(KEY_MMKV_MIGRATION_COMPLETE, true)
            }
        }
    }
}
