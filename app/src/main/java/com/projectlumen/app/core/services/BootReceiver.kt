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
 * Rebuild pending schedules after device boot / package replace / a clock change.
 *
 * Android 15 force-stop cancels pending intents; when the app later leaves STOPPED state the
 * system may re-deliver BOOT_COMPLETED-like opportunities. We treat boot and package-replace as
 * recovery points to re-register alarms/workers.
 *
 * A clock change is the third kind, and it is here rather than in a receiver of its own because
 * the reason is identical: every alarm this app arms is RTC-based, so a manual time change or a
 * timezone change moves all of them at once and the recovery is the same sweep. The guard's
 * check-in compensation rides along on the same call — it is the one part of the app whose whole
 * job is tied to a wall-clock time, so a clock that moved is exactly when it has to re-decide.
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
        /**
         * The actions [restoreScheduledWork] is the right answer to.
         *
         * Anything reaching this receiver that is not listed here is ignored rather than restored:
         * the component is exported to no one, so the list only ever has to be right about what the
         * platform itself delivers. `ACTION_TIME_CHANGED` is the constant for the manifest's
         * `android.intent.action.TIME_SET` — the two names differ, and the manifest carries the one
         * the platform actually sends.
         */
        fun isRecoveryAction(action: String?): Boolean {
            return action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                action == Intent.ACTION_TIME_CHANGED ||
                action == Intent.ACTION_TIMEZONE_CHANGED ||
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
            // Schedule reminders depend on the user's own to-dos, not on any eye-care setting, so
            // they are restored before the `settings == null` gate below. Shared with a schedule
            // edit and with an exact-alarm permission change so all three take the same path.
            // Isolated because a schedule failure must not cost the eye-care alarms their re-arm.
            runCatching {
                app.rescheduleScheduleReminders()
            }.onFailure { throwable -> app.recordHandledFailure(throwable) }
            // The guard's alarms are lost by the same reboot, and it does not depend on the user's
            // to-dos or on any eye-care setting, so it is restored here too rather than after the
            // `settings == null` gate. Isolated for the same reason as the schedule re-arm above.
            // A clock change lands here as well, which is the case that matters most to the guard:
            // its day is defined by wall-clock times, so a moved clock is the moment its stored
            // decision about today has to be re-taken rather than trusted.
            runCatching {
                app.reconcileQuarkKeeper()
            }.onFailure { throwable -> app.recordHandledFailure(throwable) }
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
