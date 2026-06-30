package com.projectlumen.app.core.proximity

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.core.content.ContextCompat
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.repositories.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.proximityEventDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "proximity_event_state",
)

class ProximityEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action !in triggerActions) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? ProjectLumenApplication ?: return@launch
                val settings = SettingsRepository(app.database.appSettingsDao(), app.eyeCarePreferences).get()
                if (settings?.proximityMonitoringEnabled != true && settings?.blinkMonitoringEnabled != true) return@launch
                if (settings.developerModeEnabled && !settings.developerUnlockTriggerEnabled) return@launch
                if (!hasCameraPermission(app)) return@launch
                if (!shouldRunEventSample(app)) return@launch
                if (!ProximityTriggerGate(app).canRun(settings)) return@launch
                ProximityDetectionWorker.enqueueNext(app, delaySeconds = 0)
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
        private val LAST_TRIGGER_AT = longPreferencesKey("last_trigger_at")
        private const val MIN_EVENT_TRIGGER_INTERVAL_MS = 60_000L

        private fun hasCameraPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        }

        private suspend fun shouldRunEventSample(context: Context): Boolean {
            val now = System.currentTimeMillis()
            val lastTriggerAt = context.proximityEventDataStore.data
                .catch { throwable ->
                    if (throwable is IOException) {
                        emit(emptyPreferences())
                    } else {
                        throw throwable
                    }
                }
                .map { preferences -> preferences[LAST_TRIGGER_AT] ?: 0L }
                .first()
            if (now - lastTriggerAt < MIN_EVENT_TRIGGER_INTERVAL_MS) return false
            context.proximityEventDataStore.edit { preferences ->
                preferences[LAST_TRIGGER_AT] = now
            }
            return true
        }
    }
}
