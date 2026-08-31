package com.projectlumen.app.core.repositories

import com.projectlumen.app.core.database.daos.DailyEyeStatsDao
import com.projectlumen.app.core.database.daos.DailyPomodoroStatsDao
import com.projectlumen.app.core.database.entities.DailyEyeStatsEntity
import com.projectlumen.app.core.database.entities.DailyPomodoroStatsEntity
import com.projectlumen.app.core.runtime.EyeStatsDelta
import com.projectlumen.app.core.runtime.PomodoroStatsDelta
import com.projectlumen.app.core.time.todayKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

class StatisticsRepository(
    private val eyeStatsDao: DailyEyeStatsDao,
    private val pomodoroStatsDao: DailyPomodoroStatsDao,
) {
    fun observeEyeStats(): Flow<List<DailyEyeStatsEntity>> = eyeStatsDao.observeAll()

    fun observePomodoroStats(): Flow<List<DailyPomodoroStatsEntity>> = pomodoroStatsDao.observeAll()

    suspend fun applyEyeDelta(statsEnabled: Boolean, nowMillis: Long, delta: EyeStatsDelta) {
        if (!statsEnabled || delta.isEmpty) return
        updateEyeStats(statsEnabled, nowMillis) {
            it.copy(
                workingSeconds = it.workingSeconds + delta.workingSeconds,
                restSeconds = it.restSeconds + delta.restSeconds,
                skipCount = it.skipCount + delta.skipCount,
                completedBreakCount = it.completedBreakCount + delta.completedBreakCount,
                preAlertCount = it.preAlertCount + delta.preAlertCount,
                maxContinuousWorkSeconds = max(it.maxContinuousWorkSeconds, delta.maxContinuousWorkSeconds),
            )
        }
    }

    suspend fun applyPomodoroDelta(statsEnabled: Boolean, nowMillis: Long, delta: PomodoroStatsDelta) {
        if (!statsEnabled || delta.isEmpty) return
        updatePomodoroStats(statsEnabled, nowMillis) {
            it.copy(
                completedTomatoCount = it.completedTomatoCount + delta.completedTomatoCount,
                restartCount = it.restartCount + delta.restartCount,
                completedFocusSessions = it.completedFocusSessions + delta.completedFocusSessions,
                totalFocusSeconds = it.totalFocusSeconds + delta.totalFocusSeconds,
                totalBreakSeconds = it.totalBreakSeconds + delta.totalBreakSeconds,
            )
        }
    }

    suspend fun updateEyeStats(
        statsEnabled: Boolean,
        nowMillis: Long,
        transform: (DailyEyeStatsEntity) -> DailyEyeStatsEntity,
    ) {
        if (!statsEnabled) return
        val date = todayKey(nowMillis)
        DailyStatsWriteLocks.eye.withLock {
            val current = eyeStatsDao.get(date) ?: DailyEyeStatsEntity(statDate = date)
            eyeStatsDao.upsert(transform(current).copy(updatedAt = nowMillis))
        }
    }

    suspend fun updatePomodoroStats(
        statsEnabled: Boolean,
        nowMillis: Long,
        transform: (DailyPomodoroStatsEntity) -> DailyPomodoroStatsEntity,
    ) {
        if (!statsEnabled) return
        val date = todayKey(nowMillis)
        DailyStatsWriteLocks.pomodoro.withLock {
            val current = pomodoroStatsDao.get(date) ?: DailyPomodoroStatsEntity(statDate = date)
            pomodoroStatsDao.upsert(transform(current).copy(updatedAt = nowMillis))
        }
    }
}

// 仓库在 6 处独立构造，实例级 Mutex 串行不了并发写者，锁必须挂在进程级 object 上。
private object DailyStatsWriteLocks {
    val eye = Mutex()
    val pomodoro = Mutex()
}
