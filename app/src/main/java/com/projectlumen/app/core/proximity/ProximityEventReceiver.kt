package com.projectlumen.app.core.proximity

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.mmkv.ProjectLumenMmkv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProximityEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action !in triggerActions) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? ProjectLumenApplication ?: return@launch
                val settings = app.settingsRepository().get()
                if (settings?.proximityMonitoringEnabled != true && settings?.blinkMonitoringEnabled != true) return@launch
                if (settings.developerModeEnabled && !settings.developerUnlockTriggerEnabled) return@launch
                if (!hasCameraPermission(app)) return@launch
                if (!shouldRunEventSample()) return@launch
                if (app.shizuku.shouldDeferSampling(settings)) return@launch
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
        private const val STORE_ID = "proximity_event_state"
        private const val KEY_LAST_TRIGGER_AT = "last_trigger_at"
        private const val MIN_EVENT_TRIGGER_INTERVAL_MS = 60_000L

        private fun hasCameraPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        }

        private fun shouldRunEventSample(): Boolean {
            val now = System.currentTimeMillis()
            val store = ProjectLumenMmkv.mmkvWithId(STORE_ID)
            val lastTriggerAt = store.decodeLong(KEY_LAST_TRIGGER_AT, 0L)
            if (now - lastTriggerAt < MIN_EVENT_TRIGGER_INTERVAL_MS) return false
            store.encode(KEY_LAST_TRIGGER_AT, now)
            return true
        }
    }
}
