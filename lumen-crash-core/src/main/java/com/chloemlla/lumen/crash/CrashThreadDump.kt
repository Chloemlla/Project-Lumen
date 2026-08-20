package com.chloemlla.lumen.crash

/**
 * Captures a bounded snapshot from a thread that is independent of the main looper.
 */
internal object CrashThreadDump {
    private const val DEFAULT_MAX_CHARS = 64 * 1024
    private const val INITIAL_BUFFER_CHARS = 8 * 1024

    fun capture(mainThread: Thread, maxChars: Int = DEFAULT_MAX_CHARS): String {
        val limit = maxChars.coerceAtLeast(4_096)
        val stacks = runCatching { Thread.getAllStackTraces() }.getOrDefault(emptyMap())
        val orderedThreads = buildList {
            add(mainThread)
            stacks.keys
                .asSequence()
                .filter { it !== mainThread }
                .sortedBy { it.name }
                .forEach { add(it) }
        }

        val output = buildString(minOf(limit, INITIAL_BUFFER_CHARS)) {
            for (thread in orderedThreads) {
                if (length >= limit) break
                append("--- ")
                    .append(thread.name.ifBlank { "unnamed" })
                    .append(" [")
                    .append(thread.state.name)
                    .append("] ---\n")
                val stack = stacks[thread] ?: runCatching { thread.stackTrace }.getOrDefault(emptyArray())
                if (stack.isEmpty()) {
                    append("  <no stack available>\n")
                } else {
                    for (frame in stack) {
                        if (length >= limit) break
                        append("  at ").append(frame).append('\n')
                    }
                }
            }
        }

        if (output.isNotBlank()) {
            return output.take(limit)
        }
        return "<thread dump unavailable>"
    }
}
