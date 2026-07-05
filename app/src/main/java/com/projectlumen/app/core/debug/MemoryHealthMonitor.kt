package com.projectlumen.app.core.debug

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MemoryHealthSnapshot(
    val sampledAtMillis: Long = 0L,
    val totalPssKb: Int = 0,
    val javaHeapKb: Int = 0,
    val nativeHeapKb: Int = 0,
    val graphicsKb: Int = 0,
    val systemAvailKb: Long = 0L,
    val systemTotalKb: Long = 0L,
    val systemThresholdKb: Long = 0L,
    val systemLowMemory: Boolean = false,
    val lastTrimLevel: Int = 0,
    val lastTrimAtMillis: Long = 0L,
)

object MemoryHealthMonitor {
    private val _snapshot = MutableStateFlow(MemoryHealthSnapshot())
    val snapshot: StateFlow<MemoryHealthSnapshot> = _snapshot.asStateFlow()

    fun sample(context: Context, nowMillis: Long = System.currentTimeMillis()): MemoryHealthSnapshot {
        return capture(context, nowMillis, trimLevel = null)
    }

    fun recordTrim(
        context: Context,
        level: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): MemoryHealthSnapshot {
        return capture(context, nowMillis, trimLevel = level)
    }

    private fun capture(context: Context, nowMillis: Long, trimLevel: Int?): MemoryHealthSnapshot {
        val processMemory = Debug.MemoryInfo()
        Debug.getMemoryInfo(processMemory)

        val systemMemory = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(systemMemory)

        val previous = _snapshot.value
        val snapshot = MemoryHealthSnapshot(
            sampledAtMillis = nowMillis,
            totalPssKb = processMemory.totalPss,
            javaHeapKb = processMemory.statKb("summary.java-heap").ifZero(processMemory.dalvikPss),
            nativeHeapKb = processMemory.statKb("summary.native-heap").ifZero(processMemory.nativePss),
            graphicsKb = processMemory.statKb("summary.graphics"),
            systemAvailKb = systemMemory.availMem / BYTES_PER_KB,
            systemTotalKb = systemMemory.totalMem / BYTES_PER_KB,
            systemThresholdKb = systemMemory.threshold / BYTES_PER_KB,
            systemLowMemory = systemMemory.lowMemory,
            lastTrimLevel = trimLevel ?: previous.lastTrimLevel,
            lastTrimAtMillis = if (trimLevel != null) nowMillis else previous.lastTrimAtMillis,
        )
        _snapshot.value = snapshot
        return snapshot
    }

    private fun Debug.MemoryInfo.statKb(key: String): Int {
        return getMemoryStat(key)?.toIntOrNull() ?: 0
    }

    private fun Int.ifZero(fallback: Int): Int {
        return if (this > 0) this else fallback
    }

    private const val BYTES_PER_KB = 1024L
}
