package com.projectlumen.app.core.repositories

import android.util.Log
import com.projectlumen.app.core.database.daos.RuntimeStateDao
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.enums.PomodoroPhase
import com.projectlumen.app.core.enums.ReminderPhase
import com.projectlumen.app.core.mmkv.ProjectLumenMmkv
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

class RuntimeRepository(private val dao: RuntimeStateDao) {
    fun observe(): Flow<RuntimeStateEntity?> = RuntimeStateMmkvStore.observe(dao)

    suspend fun get(): RuntimeStateEntity? = RuntimeStateMmkvStore.get(dao)

    suspend fun getOrDefault(): RuntimeStateEntity = get() ?: RuntimeStateEntity()

    suspend fun ensureDefault() {
        RuntimeStateMmkvStore.ensureDefault(dao)
    }

    suspend fun upsert(state: RuntimeStateEntity): RuntimeStateEntity {
        return RuntimeStateMmkvStore.upsert(dao, state.copy(id = 1))
    }

    // 读改写的唯一安全入口：transform 必须是纯函数，锁不可重入。
    suspend fun update(transform: (RuntimeStateEntity) -> RuntimeStateEntity): RuntimeStateEntity {
        return RuntimeStateMmkvStore.update(dao) { transform(it).copy(id = 1) }
    }

    suspend fun reset(nowMillis: Long): RuntimeStateEntity {
        return upsert(RuntimeStateEntity(updatedAt = nowMillis))
    }
}

private object RuntimeStateMmkvStore {
    private const val TAG = "RuntimeStateStore"
    private const val STORE_ID = "runtime_state"
    private const val MIGRATION_STORE_ID = "runtime_state_migration"
    private const val KEY_STATE_JSON = "state_json"
    private const val KEY_MMKV_MIGRATION_COMPLETE = "__mmkv_migration_complete"

    private val migrationLock = Mutex()
    private val writeMutex = Mutex()
    private val mmkv by lazy { ProjectLumenMmkv.multiProcessMmkvWithId(STORE_ID) }

    // 迁移标记与状态本体分文件存放：状态文件被 CRC 截断时不会被判成"未迁移"而从陈旧 Room 快照复活。
    private val migrationMmkv by lazy { ProjectLumenMmkv.multiProcessMmkvWithId(MIGRATION_STORE_ID) }
    private val state by lazy { MutableStateFlow(readFromMmkv()) }

    @Volatile
    private var migrationComplete = false

    @Volatile
    private var parsedCache: Pair<String, RuntimeStateEntity>? = null

    fun observe(dao: RuntimeStateDao): Flow<RuntimeStateEntity?> {
        return flow {
            ensureMigrated(dao)
            state.value = readFromMmkv()
            emitAll(state)
        }
    }

    suspend fun get(dao: RuntimeStateDao): RuntimeStateEntity? {
        ensureMigrated(dao)
        return readFromMmkv().also { state.value = it }
    }

    suspend fun ensureDefault(dao: RuntimeStateDao) {
        ensureMigrated(dao)
        writeMutex.withLock {
            if (readFromMmkv() == null) writeToMmkv(RuntimeStateEntity())
        }
    }

    suspend fun upsert(dao: RuntimeStateDao, runtime: RuntimeStateEntity): RuntimeStateEntity {
        ensureMigrated(dao)
        writeMutex.withLock { writeToMmkv(runtime) }
        return runtime
    }

    suspend fun update(
        dao: RuntimeStateDao,
        transform: (RuntimeStateEntity) -> RuntimeStateEntity,
    ): RuntimeStateEntity {
        ensureMigrated(dao)
        return writeMutex.withLock {
            val next = transform(readFromMmkv() ?: RuntimeStateEntity())
            writeToMmkv(next)
            next
        }
    }

    private suspend fun ensureMigrated(dao: RuntimeStateDao) {
        if (migrationComplete) return
        if (migrationMmkv.decodeBool(KEY_MMKV_MIGRATION_COMPLETE, false)) {
            migrationComplete = true
            return
        }
        migrationLock.withLock {
            if (migrationMmkv.decodeBool(KEY_MMKV_MIGRATION_COMPLETE, false)) {
                migrationComplete = true
                return
            }
            if (!mmkv.containsKey(KEY_STATE_JSON)) {
                dao.get()?.let(::writeToMmkv)
            }
            migrationMmkv.encode(KEY_MMKV_MIGRATION_COMPLETE, true)
            migrationComplete = true
            state.value = readFromMmkv()
        }
    }

    private fun readFromMmkv(): RuntimeStateEntity? {
        val json = mmkv.decodeString(KEY_STATE_JSON)?.takeIf { it.isNotBlank() } ?: return null
        parsedCache?.let { cached -> if (cached.first == json) return cached.second }
        val runtime = runCatching { JSONObject(json).toRuntimeState() }
            .onFailure { throwable -> Log.w(TAG, "Discarding unparsable runtime state snapshot.", throwable) }
            .getOrNull() ?: return null
        parsedCache = json to runtime
        return runtime
    }

    private fun writeToMmkv(runtime: RuntimeStateEntity) {
        val json = runtime.toJson().toString()
        mmkv.encode(KEY_STATE_JSON, json)
        parsedCache = json to runtime
        state.value = runtime
    }

    private fun RuntimeStateEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("activeEngine", activeEngine)
        .put("reminderPhase", reminderPhase)
        .put("reminderStartedAt", reminderStartedAt)
        .put("nextPreAlertAt", nextPreAlertAt)
        .put("nextReminderAt", nextReminderAt)
        .put("breakStartedAt", breakStartedAt)
        .put("breakEndAt", breakEndAt)
        .put("pomodoroPhase", pomodoroPhase)
        .put("pomodoroPhaseStartedAt", pomodoroPhaseStartedAt)
        .put("pomodoroPhaseEndAt", pomodoroPhaseEndAt)
        .put("pomodoroCycleIndex", pomodoroCycleIndex)
        .put("isManuallyPaused", isManuallyPaused)
        .put("pausedAt", pausedAt)
        .put("suspendedUntil", suspendedUntil)
        .put("lastForegroundAt", lastForegroundAt)
        .put("lastBackgroundAt", lastBackgroundAt)
        .put("lastStatsTickAt", lastStatsTickAt)
        .put("proximityMonitoringActive", proximityMonitoringActive)
        .put("proximityTooClose", proximityTooClose)
        .put("proximityLastFaceAt", proximityLastFaceAt)
        .put("proximityCloseStartedAt", proximityCloseStartedAt)
        .put("proximityCloseTickAt", proximityCloseTickAt)
        .put("proximityLastWarningAt", proximityLastWarningAt)
        .put("proximityLastRatioPercent", proximityLastRatioPercent)
        .put("blinkLastBlinkAt", blinkLastBlinkAt)
        .put("blinkLastWarningAt", blinkLastWarningAt)
        .put("blinkLastEyeOpenProbabilityPercent", blinkLastEyeOpenProbabilityPercent)
        .put("ambientLastLux", ambientLastLux.toDouble())
        .put("ambientTooDark", ambientTooDark)
        .put("ambientLastWarningAt", ambientLastWarningAt)
        .put("proximityDebugInferenceMillis", proximityDebugInferenceMillis)
        .put("proximityDebugCameraLatencyMillis", proximityDebugCameraLatencyMillis)
        .put("proximityDebugFaceWidthPx", proximityDebugFaceWidthPx)
        .put("foregroundServiceStartedAt", foregroundServiceStartedAt)
        .put("foregroundServiceStoppedAt", foregroundServiceStoppedAt)
        .put("foregroundServiceLastTaskRemovedAt", foregroundServiceLastTaskRemovedAt)
        .put("foregroundServiceLastStickyRestartAt", foregroundServiceLastStickyRestartAt)
        .put("developerLastLowMemorySimulatedAt", developerLastLowMemorySimulatedAt)
        .put("sensorPitchDegrees", sensorPitchDegrees.toDouble())
        .put("sensorRollDegrees", sensorRollDegrees.toDouble())
        .put("sensorYawDegrees", sensorYawDegrees.toDouble())
        .put("sensorLastAccelerationMagnitude", sensorLastAccelerationMagnitude.toDouble())
        .put("updatedAt", updatedAt)

    private fun JSONObject.toRuntimeState(): RuntimeStateEntity {
        val defaults = RuntimeStateEntity()
        return RuntimeStateEntity(
            id = optInt("id", 1),
            activeEngine = optString("activeEngine", ActiveEngine.IDLE.name).ifBlank { defaults.activeEngine },
            reminderPhase = optString("reminderPhase", ReminderPhase.IDLE.name).ifBlank { defaults.reminderPhase },
            reminderStartedAt = optLong("reminderStartedAt", defaults.reminderStartedAt),
            nextPreAlertAt = optLong("nextPreAlertAt", defaults.nextPreAlertAt),
            nextReminderAt = optLong("nextReminderAt", defaults.nextReminderAt),
            breakStartedAt = optLong("breakStartedAt", defaults.breakStartedAt),
            breakEndAt = optLong("breakEndAt", defaults.breakEndAt),
            pomodoroPhase = optString("pomodoroPhase", PomodoroPhase.IDLE.name).ifBlank { defaults.pomodoroPhase },
            pomodoroPhaseStartedAt = optLong("pomodoroPhaseStartedAt", defaults.pomodoroPhaseStartedAt),
            pomodoroPhaseEndAt = optLong("pomodoroPhaseEndAt", defaults.pomodoroPhaseEndAt),
            pomodoroCycleIndex = optInt("pomodoroCycleIndex", defaults.pomodoroCycleIndex),
            isManuallyPaused = optBoolean("isManuallyPaused", defaults.isManuallyPaused),
            pausedAt = optLong("pausedAt", defaults.pausedAt),
            suspendedUntil = optLong("suspendedUntil", defaults.suspendedUntil),
            lastForegroundAt = optLong("lastForegroundAt", defaults.lastForegroundAt),
            lastBackgroundAt = optLong("lastBackgroundAt", defaults.lastBackgroundAt),
            lastStatsTickAt = optLong("lastStatsTickAt", defaults.lastStatsTickAt),
            proximityMonitoringActive = optBoolean("proximityMonitoringActive", defaults.proximityMonitoringActive),
            proximityTooClose = optBoolean("proximityTooClose", defaults.proximityTooClose),
            proximityLastFaceAt = optLong("proximityLastFaceAt", defaults.proximityLastFaceAt),
            proximityCloseStartedAt = optLong("proximityCloseStartedAt", defaults.proximityCloseStartedAt),
            proximityCloseTickAt = optLong("proximityCloseTickAt", defaults.proximityCloseTickAt),
            proximityLastWarningAt = optLong("proximityLastWarningAt", defaults.proximityLastWarningAt),
            proximityLastRatioPercent = optInt("proximityLastRatioPercent", defaults.proximityLastRatioPercent),
            blinkLastBlinkAt = optLong("blinkLastBlinkAt", defaults.blinkLastBlinkAt),
            blinkLastWarningAt = optLong("blinkLastWarningAt", defaults.blinkLastWarningAt),
            blinkLastEyeOpenProbabilityPercent = optInt(
                "blinkLastEyeOpenProbabilityPercent",
                defaults.blinkLastEyeOpenProbabilityPercent,
            ),
            ambientLastLux = optDouble("ambientLastLux", defaults.ambientLastLux.toDouble()).toFloat(),
            ambientTooDark = optBoolean("ambientTooDark", defaults.ambientTooDark),
            ambientLastWarningAt = optLong("ambientLastWarningAt", defaults.ambientLastWarningAt),
            proximityDebugInferenceMillis = optLong(
                "proximityDebugInferenceMillis",
                defaults.proximityDebugInferenceMillis,
            ),
            proximityDebugCameraLatencyMillis = optLong(
                "proximityDebugCameraLatencyMillis",
                defaults.proximityDebugCameraLatencyMillis,
            ),
            proximityDebugFaceWidthPx = optInt("proximityDebugFaceWidthPx", defaults.proximityDebugFaceWidthPx),
            foregroundServiceStartedAt = optLong("foregroundServiceStartedAt", defaults.foregroundServiceStartedAt),
            foregroundServiceStoppedAt = optLong("foregroundServiceStoppedAt", defaults.foregroundServiceStoppedAt),
            foregroundServiceLastTaskRemovedAt = optLong(
                "foregroundServiceLastTaskRemovedAt",
                defaults.foregroundServiceLastTaskRemovedAt,
            ),
            foregroundServiceLastStickyRestartAt = optLong(
                "foregroundServiceLastStickyRestartAt",
                defaults.foregroundServiceLastStickyRestartAt,
            ),
            developerLastLowMemorySimulatedAt = optLong(
                "developerLastLowMemorySimulatedAt",
                defaults.developerLastLowMemorySimulatedAt,
            ),
            sensorPitchDegrees = optDouble("sensorPitchDegrees", defaults.sensorPitchDegrees.toDouble()).toFloat(),
            sensorRollDegrees = optDouble("sensorRollDegrees", defaults.sensorRollDegrees.toDouble()).toFloat(),
            sensorYawDegrees = optDouble("sensorYawDegrees", defaults.sensorYawDegrees.toDouble()).toFloat(),
            sensorLastAccelerationMagnitude = optDouble(
                "sensorLastAccelerationMagnitude",
                defaults.sensorLastAccelerationMagnitude.toDouble(),
            ).toFloat(),
            updatedAt = optLong("updatedAt", defaults.updatedAt),
        )
    }
}
