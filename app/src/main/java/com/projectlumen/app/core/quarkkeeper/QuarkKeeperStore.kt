package com.projectlumen.app.core.quarkkeeper

import com.projectlumen.app.core.mmkv.ProjectLumenMmkv
import com.projectlumen.app.core.time.todayKey
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local key-value storage for the check-in guard.
 *
 * A single MMKV file rather than a Room table: the PRD requires lightweight local key-value state
 * with no network path, the whole payload is a handful of scalars plus one line-per-day log, and the
 * log is read as a unit by the dashboard and the statistics alike — a table would add a migration and
 * a DAO without buying a single query.
 *
 * Every read goes through an in-memory [MutableStateFlow] that is rehydrated from disk on first
 * access, so the alarm receivers, the foreground service and the UI all observe one value and a write
 * from any of them is visible to the others without a reload.
 */
object QuarkKeeperStore {

    private val lock = Any()

    private val mmkv: MMKV by lazy { ProjectLumenMmkv.mmkvWithId(MMKV_ID) }

    private val _state: MutableStateFlow<QuarkKeeperSnapshot> by lazy { MutableStateFlow(load()) }

    val state: StateFlow<QuarkKeeperSnapshot> get() = _state.asStateFlow()

    fun snapshot(): QuarkKeeperSnapshot = _state.value

    /**
     * Today's row, with yesterday's row treated as absent.
     *
     * The 00:00 reset is derived here instead of being driven by a midnight alarm: a device that is
     * off, in Doze, or has its clock changed never fires that alarm, and a rollover that depends on
     * one would leave yesterday's "already checked in" standing as today's status — the one failure
     * that silently cancels the whole guard.
     */
    fun currentToday(nowMillis: Long = System.currentTimeMillis()): QuarkKeeperTodayStatus {
        val today = _state.value.today
        val dateKey = todayKey(nowMillis)
        return if (today.dateKey == dateKey) today else QuarkKeeperTodayStatus(dateKey = dateKey)
    }

    fun updateSettings(transform: (QuarkKeeperSettings) -> QuarkKeeperSettings): QuarkKeeperSettings {
        val updated = transform(_state.value.settings).sanitized()
        mutate { current -> current.copy(settings = updated) }
        return updated
    }

    /** Records today as done. Returns the persisted day row so callers can react to the new streak. */
    fun markCheckedIn(nowMillis: Long): QuarkKeeperTodayStatus {
        val dateKey = todayKey(nowMillis)
        val record = QuarkKeeperCheckinRecord(dateKey = dateKey, checkedInAtMillis = nowMillis)
        var result = QuarkKeeperTodayStatus(dateKey = dateKey, checkedInAtMillis = nowMillis)
        mutate { current ->
            val previousToday = current.today
            result = QuarkKeeperTodayStatus(
                dateKey = dateKey,
                checkedInAtMillis = nowMillis,
                // The nag round survives a check-in and its undo: it counts how many times today has
                // already been interrupted, and restarting the count would let a user who checks in
                // and undoes the action be alerted from round one again.
                nagRound = if (previousToday.dateKey == dateKey) previousToday.nagRound else 0,
            )
            current.copy(
                today = result,
                records = (current.records.filterNot { existing -> existing.dateKey == dateKey } + record)
                    .sortedBy { existing -> existing.dateKey }
                    .takeLast(MAX_STORED_DAYS),
            )
        }
        return result
    }

    /**
     * Removes one day from the log and clears today's row when it is the same day.
     *
     * The undo exists because the third button on the forced alert is a one-tap confirmation: a
     * mis-tap would otherwise silently end the day's monitoring with no way back.
     */
    fun undoCheckIn(dateKey: String, nowMillis: Long = System.currentTimeMillis()) {
        mutate { current ->
            val clearedToday = if (current.today.dateKey == dateKey) {
                current.today.copy(checkedInAtMillis = 0L, snoozedUntilMillis = 0L)
            } else {
                current.today
            }
            current.copy(
                today = clearedToday,
                records = current.records.filterNot { existing -> existing.dateKey == dateKey },
            )
        }
    }

    fun setSnooze(untilMillis: Long, nowMillis: Long = System.currentTimeMillis()) {
        mutate { current ->
            val dateKey = todayKey(nowMillis)
            val today = if (current.today.dateKey == dateKey) {
                current.today.copy(snoozedUntilMillis = untilMillis)
            } else {
                QuarkKeeperTodayStatus(dateKey = dateKey, snoozedUntilMillis = untilMillis)
            }
            current.copy(today = today)
        }
    }

    fun clearSnooze(nowMillis: Long = System.currentTimeMillis()) {
        val dateKey = todayKey(nowMillis)
        mutate { current ->
            if (current.today.dateKey != dateKey || current.today.snoozedUntilMillis == 0L) return@mutate current
            current.copy(today = current.today.copy(snoozedUntilMillis = 0L))
        }
    }

    /** Counts one escalated round for today and returns the new total. */
    fun bumpNagRound(nowMillis: Long = System.currentTimeMillis()): Int {
        val dateKey = todayKey(nowMillis)
        var round = 1
        mutate { current ->
            round = if (current.today.dateKey == dateKey) current.today.nagRound + 1 else 1
            val today = if (current.today.dateKey == dateKey) {
                current.today.copy(nagRound = round)
            } else {
                QuarkKeeperTodayStatus(dateKey = dateKey, nagRound = round)
            }
            current.copy(today = today)
        }
        return round
    }

    private fun mutate(transform: (QuarkKeeperSnapshot) -> QuarkKeeperSnapshot) {
        synchronized(lock) {
            val next = transform(_state.value)
            if (next == _state.value) return
            persist(next)
            _state.value = next
        }
    }

    private fun load(): QuarkKeeperSnapshot {
        return runCatching {
            val settings = QuarkKeeperSettings(
                enabled = mmkv.decodeBool(KEY_ENABLED, false),
                regularMinuteOfDay = mmkv.decodeInt(
                    KEY_REGULAR_MINUTE,
                    QuarkKeeperSettings.DEFAULT_REGULAR_MINUTE_OF_DAY,
                ),
                forcedMinuteOfDay = mmkv.decodeInt(
                    KEY_FORCED_MINUTE,
                    QuarkKeeperSettings.DEFAULT_FORCED_MINUTE_OF_DAY,
                ),
                snoozeMinutes = mmkv.decodeInt(KEY_SNOOZE_MINUTES, QuarkKeeperSettings.DEFAULT_SNOOZE_MINUTES),
                snoozeCutoffMinuteOfDay = mmkv.decodeInt(
                    KEY_SNOOZE_CUTOFF_MINUTE,
                    QuarkKeeperSettings.DEFAULT_SNOOZE_CUTOFF_MINUTE_OF_DAY,
                ),
                soundEnabled = mmkv.decodeBool(KEY_SOUND_ENABLED, true),
            ).sanitized()
            QuarkKeeperSnapshot(
                settings = settings,
                today = QuarkKeeperTodayStatus(
                    dateKey = mmkv.decodeString(KEY_TODAY_DATE, "").orEmpty(),
                    checkedInAtMillis = mmkv.decodeLong(KEY_TODAY_CHECKED_IN_AT, 0L),
                    snoozedUntilMillis = mmkv.decodeLong(KEY_TODAY_SNOOZED_UNTIL, 0L),
                    nagRound = mmkv.decodeInt(KEY_TODAY_NAG_ROUND, 0),
                ),
                records = decodeRecords(mmkv.decodeString(KEY_RECORDS, "").orEmpty()),
            )
        }.getOrElse { QuarkKeeperSnapshot() }
    }

    private fun persist(snapshot: QuarkKeeperSnapshot) {
        val settings = snapshot.settings
        mmkv.encode(KEY_ENABLED, settings.enabled)
        mmkv.encode(KEY_REGULAR_MINUTE, settings.regularMinuteOfDay)
        mmkv.encode(KEY_FORCED_MINUTE, settings.forcedMinuteOfDay)
        mmkv.encode(KEY_SNOOZE_MINUTES, settings.snoozeMinutes)
        mmkv.encode(KEY_SNOOZE_CUTOFF_MINUTE, settings.snoozeCutoffMinuteOfDay)
        mmkv.encode(KEY_SOUND_ENABLED, settings.soundEnabled)
        mmkv.encode(KEY_TODAY_DATE, snapshot.today.dateKey)
        mmkv.encode(KEY_TODAY_CHECKED_IN_AT, snapshot.today.checkedInAtMillis)
        mmkv.encode(KEY_TODAY_SNOOZED_UNTIL, snapshot.today.snoozedUntilMillis)
        mmkv.encode(KEY_TODAY_NAG_ROUND, snapshot.today.nagRound)
        mmkv.encode(KEY_RECORDS, encodeRecords(snapshot.records))
    }

    // "2026-09-19|1758289440000" per line. A pipe-delimited log keeps the file readable in a bug
    // report and needs no serializer dependency; the date keys it stores are already ISO-8601, so
    // neither field can contain the delimiter.
    private fun encodeRecords(records: List<QuarkKeeperCheckinRecord>): String {
        return records.joinToString(separator = "\n") { record ->
            "${record.dateKey}$FIELD_SEPARATOR${record.checkedInAtMillis}"
        }
    }

    private fun decodeRecords(encoded: String): List<QuarkKeeperCheckinRecord> {
        if (encoded.isBlank()) return emptyList()
        return encoded.lineSequence()
            .mapNotNull { line ->
                val separatorIndex = line.indexOf(FIELD_SEPARATOR)
                if (separatorIndex <= 0) return@mapNotNull null
                val dateKey = line.substring(0, separatorIndex)
                val millis = line.substring(separatorIndex + 1).toLongOrNull() ?: return@mapNotNull null
                if (dateKey.isBlank() || millis <= 0L) return@mapNotNull null
                QuarkKeeperCheckinRecord(dateKey = dateKey, checkedInAtMillis = millis)
            }
            .toList()
    }

    private const val MMKV_ID = "quark_keeper"
    private const val FIELD_SEPARATOR = '|'

    /** Two years of history: enough for every streak the dashboard can show, small enough to decode per read. */
    private const val MAX_STORED_DAYS = 730

    private const val KEY_ENABLED = "enabled"
    private const val KEY_REGULAR_MINUTE = "regular_minute"
    private const val KEY_FORCED_MINUTE = "forced_minute"
    private const val KEY_SNOOZE_MINUTES = "snooze_minutes"
    private const val KEY_SNOOZE_CUTOFF_MINUTE = "snooze_cutoff_minute"
    private const val KEY_SOUND_ENABLED = "sound_enabled"
    private const val KEY_TODAY_DATE = "today_date"
    private const val KEY_TODAY_CHECKED_IN_AT = "today_checked_in_at"
    private const val KEY_TODAY_SNOOZED_UNTIL = "today_snoozed_until"
    private const val KEY_TODAY_NAG_ROUND = "today_nag_round"
    private const val KEY_RECORDS = "records"
}
