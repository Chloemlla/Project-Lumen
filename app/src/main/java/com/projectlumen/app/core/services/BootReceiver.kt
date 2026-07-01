package com.projectlumen.app.core.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.repositories.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val app = context.applicationContext as ProjectLumenApplication
                val settingsRepository = SettingsRepository(app.database.appSettingsDao(), app.eyeCarePreferences)
                val settings = settingsRepository.get()
                val runtime = app.database.runtimeStateDao().get()
                if (settings?.proximityMonitoringEnabled == true || settings?.blinkMonitoringEnabled == true) {
                    app.scheduleProximityMonitoring()
                }
                if (settings?.ambientLightMonitoringEnabled == true || settings?.autoBrightnessEnabled == true) {
                    app.startLightMonitoring()
                }
                if (
                    settings?.shizukuAdvancedModeEnabled == true &&
                    (settings.shizukuServiceRecoveryEnabled || settings.shizukuNativeEyeProtectionEnabled)
                ) {
                    ShizukuResilienceWorker.enqueue(
                        context = app,
                        delayMinutes = if (settings.shizukuNativeEyeProtectionEnabled) 0L else 15L,
                    )
                }
                if (settings?.keepAliveEnabled == true && runtime?.activeEngine != ActiveEngine.IDLE.name) {
                    app.startTimerService()
                }
                if (settings != null && runtime != null && runtime.activeEngine != ActiveEngine.IDLE.name) {
                    app.notifications.syncRuntimeAlarms(settings, runtime)
                    if (settings.notificationEnabled) {
                        app.startTimerService()
                        app.notifications.showOngoingStatus(runtime)
                    }
                }
            }
            pendingResult.finish()
        }
    }
}
