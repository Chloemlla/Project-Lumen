package com.projectlumen.app.core.time

import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.enums.QuietMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object QuietHours {
    fun mode(settings: AppSettingsEntity): QuietMode? {
        if (!settings.quietHoursEnabled) return null
        return QuietMode.entries.firstOrNull { it.name == settings.quietMode } ?: QuietMode.PAUSE_TIMER
    }

    fun isActive(settings: AppSettingsEntity, nowMillis: Long): Boolean {
        if (!settings.quietHoursEnabled) return false
        val start = settings.quietStartMinute.coerceIn(0, 1439)
        val end = settings.quietEndMinute.coerceIn(0, 1439)
        if (start == end) return false
        val now = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalTime()
        val currentMinute = now.hour * 60 + now.minute
        return if (start < end) {
            currentMinute in start until end
        } else {
            currentMinute >= start || currentMinute < end
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
        return activeBoundary(settings, nowMillis).first
    }

    fun activeEndMillis(settings: AppSettingsEntity, nowMillis: Long): Long {
        return activeBoundary(settings, nowMillis).second
    }

    private fun activeBoundary(settings: AppSettingsEntity, nowMillis: Long): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val startMinute = settings.quietStartMinute.coerceIn(0, 1439)
        val endMinute = settings.quietEndMinute.coerceIn(0, 1439)
        val currentMinute = now.hour * 60 + now.minute
        val date = now.toLocalDate()
        val (startDate, endDate) = if (startMinute < endMinute) {
            date to date
        } else if (currentMinute >= startMinute) {
            date to date.plusDays(1)
        } else {
            date.minusDays(1) to date
        }
        return minuteAt(startDate, startMinute, zone) to minuteAt(endDate, endMinute, zone)
    }

    private fun minuteAt(date: LocalDate, minuteOfDay: Int, zone: ZoneId): Long {
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    }
}
