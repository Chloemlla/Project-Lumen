package com.projectlumen.app.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.projectlumen.app.core.enums.AppThemeMode
import com.projectlumen.app.core.enums.ReminderAction

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val languageCode: String = "system",
    val themeMode: String = AppThemeMode.SYSTEM.name,
    val reminderEnabled: Boolean = true,
    val warnIntervalMinutes: Int = 20,
    val restDurationSeconds: Int = 20,
    val statsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val preAlertEnabled: Boolean = true,
    val preAlertSeconds: Int = 60,
    val preAlertDefaultAction: String = ReminderAction.START_BREAK.name,
    val preAlertTitle: String = "Eye break soon",
    val preAlertSubtitle: String = "Prepare to rest",
    val preAlertMessage: String = "A break will start shortly.",
    val askBeforeBreak: Boolean = true,
    val disableSkip: Boolean = false,
    val timeoutAutoBreak: Boolean = false,
    val pomodoroEnabled: Boolean = true,
    val pomodoroWorkMinutes: Int = 25,
    val pomodoroShortBreakMinutes: Int = 5,
    val pomodoroLongBreakMinutes: Int = 15,
    val pomodoroInteractiveMode: Boolean = false,
    val activeTipTemplateId: Long = 1L,
    val useAutoDarkWindow: Boolean = false,
    val autoDarkStartMinute: Int = 1080,
    val autoDarkEndMinute: Int = 360,
    val notificationEnabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
)
