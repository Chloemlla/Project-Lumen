package com.projectlumen.app.core.time

import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.enums.QuietMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object QuietHours {
    /** Both stored boundaries are wall-clock seconds of day, so a day's worth is the whole range. */
    private const val SECONDS_PER_DAY = 86_400L

    fun mode(settings: AppSettingsEntity): QuietMode? {
        if (!settings.quietHoursEnabled) return null
        return QuietMode.entries.firstOrNull { it.name == settings.quietMode } ?: QuietMode.PAUSE_TIMER
    }

    fun isActive(settings: AppSettingsEntity, nowMillis: Long): Boolean {
        if (!settings.quietHoursEnabled) return false
        val start = startSecondOfDay(settings)
        val end = endSecondOfDay(settings)
        if (start == end) return false
        val currentSecond = localSecondOfDay(nowMillis)
        return if (start < end) {
            currentSecond in start until end
        } else {
            currentSecond >= start || currentSecond < end
        }
    }

    fun isPauseTimerActive(settings: AppSettingsEntity, nowMillis: Long): Boolean {
        return mode(settings) == QuietMode.PAUSE_TIMER && isActive(settings, nowMillis)
    }

    fun suppressesReminderNotifications(settings: AppSettingsEntity, nowMillis: Long): Boolean {
        val activeMode = mode(settings) ?: return false
        return isActive(settings, nowMillis) &&
            (activeMode == QuietMode.SILENT_NOTIFICATIONS || activeMode == QuietMode.RECORD_ONLY)
    }

    fun recordOnlyActive(settings: AppSettingsEntity, nowMillis: Long): Boolean {
        return mode(settings) == QuietMode.RECORD_ONLY && isActive(settings, nowMillis)
    }

    fun activeStartMillis(settings: AppSettingsEntity, nowMillis: Long): Long {
        return activeBoundary(settings, nowMillis, wantStart = true)
    }

    fun activeEndMillis(settings: AppSettingsEntity, nowMillis: Long): Long {
        return activeBoundary(settings, nowMillis, wantStart = false)
    }

    private fun activeBoundary(settings: AppSettingsEntity, nowMillis: Long, wantStart: Boolean): Long {
        val zone = LumenTimeZone.zoneId()
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val startSecond = startSecondOfDay(settings)
        val endSecond = endSecondOfDay(settings)
        val currentSecond = now.hour * 3600 + now.minute * 60 + now.second
        val date = now.toLocalDate()
        return if (startSecond < endSecond) {
            if (wantStart) secondAt(date, startSecond, zone) else secondAt(date, endSecond, zone)
        } else if (currentSecond >= startSecond) {
            if (wantStart) secondAt(date, startSecond, zone) else secondAt(date.plusDays(1), endSecond, zone)
        } else {
            if (wantStart) secondAt(date.minusDays(1), startSecond, zone) else secondAt(date, endSecond, zone)
        }
    }

    /** The boundary the user picked, as seconds of day: the minute field plus its second component. */
    private fun startSecondOfDay(settings: AppSettingsEntity): Int =
        (settings.quietStartMinute * 60 + settings.quietStartSecond).coerceIn(0, 86_399)

    private fun endSecondOfDay(settings: AppSettingsEntity): Int =
        (settings.quietEndMinute * 60 + settings.quietEndSecond).coerceIn(0, 86_399)

    private fun localSecondOfDay(nowMillis: Long): Int {
        val localSeconds = nowMillis.floorDiv(1000L) + LumenTimeZone.offsetSeconds
        return localSeconds.mod(SECONDS_PER_DAY).toInt()
    }

    private fun secondAt(date: LocalDate, secondOfDay: Int, zone: ZoneId): Long {
        val time = LocalTime.ofSecondOfDay(secondOfDay.toLong())
        return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    }
}
