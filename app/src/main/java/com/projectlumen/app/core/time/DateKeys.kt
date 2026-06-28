package com.projectlumen.app.core.time

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

fun todayKey(nowMillis: Long = System.currentTimeMillis()): String {
    return Instant.ofEpochMilli(nowMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(dateFormatter)
}

fun Long.coerceElapsedSecondsSince(startMillis: Long): Long {
    if (this <= 0L || startMillis <= 0L || this <= startMillis) return 0L
    return (this - startMillis) / 1000L
}
