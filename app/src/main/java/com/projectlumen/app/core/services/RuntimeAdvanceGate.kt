package com.projectlumen.app.core.services

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The timer loop, the alarm receiver and the notification actions are peers: each reads a runtime
 * snapshot, advances the engine and writes it back. Serialising those critical sections in-process
 * keeps the same due phase from being advanced twice (double stats, duplicate tone) and keeps a
 * tick write from rolling back a transition written a moment earlier.
 */
internal object RuntimeAdvanceGate {
    private val mutex = Mutex()

    suspend fun <T> withAdvanceLock(block: suspend () -> T): T = mutex.withLock { block() }
}
