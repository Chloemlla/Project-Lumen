package com.projectlumen.app.core.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.R
import com.projectlumen.app.core.overlay.EyeProtectionOverlayService
import com.projectlumen.app.core.repositories.SettingsRepository
import com.projectlumen.app.core.time.QuietHours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val app = context.applicationContext as ProjectLumenApplication
                val notifications = NotificationService(context.applicationContext)
                val settings = SettingsRepository(app.database.appSettingsDao(), app.eyeCarePreferences).get()
                val suppressReminder = settings != null &&
                    QuietHours.suppressesReminderNotifications(settings, System.currentTimeMillis()) &&
                    intent.action in setOf(ACTION_PRE_ALERT, ACTION_BREAK_DUE, ACTION_BREAK_DONE)
                notifications.ensureChannels()
                if (!suppressReminder) {
                    when (intent.action) {
                        ACTION_PRE_ALERT -> notifications.showPreAlert()
                        ACTION_BREAK_DUE -> notifications.showReminderDue()
                        ACTION_BREAK_DONE -> notifications.showBreakDone()
                        ACTION_POMODORO -> notifications.showPomodoro(
                            context.getString(com.projectlumen.app.R.string.pomodoro_title),
                            context.getString(com.projectlumen.app.R.string.pomodoro_notification_message),
                        )
                    }
                }
                if (
                    intent.action == ACTION_BREAK_DUE &&
                    settings?.globalOverlayEnabled == true &&
                    !suppressReminder
                ) {
                    EyeProtectionOverlayService.show(
                        context = context.applicationContext,
                        title = context.getString(R.string.overlay_break_title),
                        message = context.getString(R.string.overlay_break_message),
                        durationSeconds = settings.restDurationSeconds.coerceAtLeast(settings.overlayRestDurationSeconds),
                    )
                }
            }
            pendingResult.finish()
        }
    }

    companion object {
        const val ACTION_PRE_ALERT = "com.projectlumen.app.action.PRE_ALERT"
        const val ACTION_BREAK_DUE = "com.projectlumen.app.action.BREAK_DUE"
        const val ACTION_BREAK_DONE = "com.projectlumen.app.action.BREAK_DONE"
        const val ACTION_POMODORO = "com.projectlumen.app.action.POMODORO"
    }
}
