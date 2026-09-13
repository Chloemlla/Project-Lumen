package com.projectlumen.app.core.services

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.enums.ActiveEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val app = context.applicationContext as? ProjectLumenApplication
            runCatching {
                app ?: return@runCatching
                // Granting or revoking exact-alarm permission changes how schedule reminders may be
                // scheduled, in both directions. Without this they would keep the degraded inexact
                // alarm chosen when they were first scheduled until the next boot. Isolated in its
                // own runCatching so a schedule failure cannot cost the eye-care alarms their sync.
                runCatching {
                    app.rescheduleScheduleReminders()
                }.onFailure { throwable -> app.recordHandledFailure(throwable) }
                val settings = app.settingsRepository().getOrDefault()
                val runtime = app.runtimeRepository().get() ?: return@runCatching
                app.notifications.syncRuntimeAlarms(settings, runtime)
                if (settings.keepAliveEnabled && runtime.activeEngine != ActiveEngine.IDLE.name) {
                    app.startTimerService()
                }
            }
                .onFailure { throwable -> app?.recordHandledFailure(throwable) }
            pendingResult.finish()
        }
    }
}
