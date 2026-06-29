package com.projectlumen.app.core.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.enums.ReminderPhase
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
                val settings = app.database.appSettingsDao().get()
                val runtime = app.database.runtimeStateDao().get()
                if (settings?.proximityMonitoringEnabled == true) {
                    app.scheduleProximityMonitoring()
                }
                if (settings?.keepAliveEnabled == true && runtime?.activeEngine != ActiveEngine.IDLE.name) {
                    app.startTimerService()
                }
                if (settings?.notificationEnabled == true && runtime?.activeEngine == ActiveEngine.REMINDER.name) {
                    when (runtime.reminderPhase) {
                        ReminderPhase.WORKING.name,
                        ReminderPhase.PRE_ALERT.name,
                        ReminderPhase.AWAITING_ACTION.name -> {
                            app.notifications.scheduleReminder(runtime.nextPreAlertAt, runtime.nextReminderAt)
                            app.startTimerService()
                            app.notifications.showOngoingStatus(runtime)
                        }

                        ReminderPhase.RESTING.name -> {
                            app.notifications.scheduleBreakDone(runtime.breakEndAt)
                            app.startTimerService()
                            app.notifications.showOngoingStatus(runtime)
                        }
                    }
                }
            }
            pendingResult.finish()
        }
    }
}
