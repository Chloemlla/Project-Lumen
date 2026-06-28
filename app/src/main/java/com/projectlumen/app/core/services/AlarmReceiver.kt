package com.projectlumen.app.core.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notifications = NotificationService(context.applicationContext)
        notifications.ensureChannels()
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

    companion object {
        const val ACTION_PRE_ALERT = "com.projectlumen.app.action.PRE_ALERT"
        const val ACTION_BREAK_DUE = "com.projectlumen.app.action.BREAK_DUE"
        const val ACTION_BREAK_DONE = "com.projectlumen.app.action.BREAK_DONE"
        const val ACTION_POMODORO = "com.projectlumen.app.action.POMODORO"
    }
}
