package com.projectlumen.app.core.time

import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The zone every wall-clock time in this app is expressed in — both when one is shown and when one is
 * read back into an instant.
 *
 * It is a fixed UTC offset rather than an IANA region, because the setting the user picks is "how far
 * ahead of UTC my clock is" and a fixed offset has no daylight-saving branch to reason about. The
 * default is +08:00, which is also what a device already on UTC+8 was using before this setting
 * existed, so the default changes nothing for those users.
 *
 * This is a process-wide authority rather than a parameter because the zone is needed in places that
 * have no settings to hand — notification text, alarm scheduling, day-bucketed statistics. The single
 * writer is `SettingsRepository`, which seeds it from the persisted row on every read and write: any
 * code path that has the settings therefore has the right zone, and a process starting up for a worker
 * or an alarm picks it up as soon as it reads them.
 */
object LumenTimeZone {
    /** +08:00. */
    const val DEFAULT_OFFSET_SECONDS: Int = 8 * 60 * 60

    const val MIN_OFFSET_SECONDS: Int = -12 * 60 * 60
    const val MAX_OFFSET_SECONDS: Int = 14 * 60 * 60

    @Volatile
    var offsetSeconds: Int = DEFAULT_OFFSET_SECONDS
        private set

    // Cached so that formatting a time — which happens once a second while a timer runs — is a field
    // read rather than an allocation.
    @Volatile
    private var zone: ZoneId = ZoneOffset.ofTotalSeconds(DEFAULT_OFFSET_SECONDS)

    fun zoneId(): ZoneId = zone

    fun update(offsetSeconds: Int) {
        val clamped = clamp(offsetSeconds)
        if (clamped == this.offsetSeconds) return
        this.offsetSeconds = clamped
        zone = ZoneOffset.ofTotalSeconds(clamped)
    }

    /** Keeps a stored or picked value inside the range a real zone can occupy. */
    fun clamp(offsetSeconds: Int): Int = offsetSeconds.coerceIn(MIN_OFFSET_SECONDS, MAX_OFFSET_SECONDS)

    /** `UTC+08:00`. */
    fun label(offsetSeconds: Int): String {
        val clamped = clamp(offsetSeconds)
        val sign = if (clamped < 0) "-" else "+"
        val absoluteMinutes = if (clamped < 0) -clamped / 60 else clamped / 60
        return "UTC$sign%02d:%02d".format(absoluteMinutes / 60, absoluteMinutes % 60)
    }
}
