package com.projectlumen.app.core.time

import java.time.Instant
import java.time.ZoneId

fun todayKey(nowMillis: Long = System.currentTimeMillis()): String {
    return Instant.ofEpochMilli(nowMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toString()
}

fun Long.coerceElapsedSecondsSince(startMillis: Long): Long {
    if (this <= 0L || startMillis <= 0L || this <= startMillis) return 0L
    return (this - startMillis) / 1000L
}

/**
 * Upper bound for any single elapsed computation that feeds eye statistics. Work ticks run every
 * second and alarm settlements are schedule-bounded, so a delta larger than this means either a
 * wall-clock change or the loop slept through the interval — billing it wholesale would corrupt the
 * daily stats.
 */
const val MAX_SINGLE_ELAPSED_SECONDS = 4 * 60 * 60L
