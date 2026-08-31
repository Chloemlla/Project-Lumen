package com.projectlumen.app.core.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.enums.ActiveEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Rebuild pending schedules after device boot / package replace.
 *
 * Android 15 force-stop cancels pending intents; when the app later leaves STOPPED state the
 * system may re-deliver BOOT_COMPLETED-like opportunities. We treat boot and package-replace as
 * recovery points to re-register alarms/workers.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!isRecoveryAction(intent.action)) return
        // LOCKED_BOOT_COMPLETED arrives before the first unlock, when credential-encrypted storage
        // (Room, MMKV, DataStore) cannot be opened yet; the later BOOT_COMPLETED is the real
        // recovery point.
        if (!isStorageUnlocked(context)) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val app = context.applicationContext as? ProjectLumenApplication
            runCatching {
                app ?: return@runCatching
                restoreScheduledWork(app)
            }.onFailure { throwable -> app?.recordHandledFailure(throwable) }
            pendingResult.finish()
        }
    }

    private fun isStorageUnlocked(context: Context): Boolean {
        return context.getSystemService(UserManager::class.java)?.isUserUnlocked != false
    }

    companion object {
        fun isRecoveryAction(action: String?): Boolean {
            return action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                action == "android.intent.action.QUICKBOOT_POWERON"
        }

        suspend fun restoreScheduledWork(app: ProjectLumenApplication) {
            val settingsRepository = app.settingsRepository()
            val settings = settingsRepository.get()
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
            if (settings == null) return
            // A phase that fell due while the device was off has to be advanced before alarms are
            // re-armed; every stored trigger time is in the past by now and would be dropped.
            // Re-arming also matters when idle: exact-alarm permission may have changed.
            val runtime = AlarmReceiver.reconcileNow(
                app = app,
                notifications = app.notifications,
                settings = settings,
                nowMillis = System.currentTimeMillis(),
                capStatsDelta = true,
            )
            if (runtime.activeEngine == ActiveEngine.IDLE.name) return
            if (settings.keepAliveEnabled || settings.notificationEnabled) {
                app.startTimerService()
            }
            if (settings.notificationEnabled) {
                app.notifications.showOngoingStatus(runtime)
            }
        }
    }
}
