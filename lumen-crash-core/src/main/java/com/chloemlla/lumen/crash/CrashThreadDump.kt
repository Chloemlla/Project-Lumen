package com.chloemlla.lumen.crash

/**
 * Captures a bounded snapshot from a thread that is independent of the main looper.
 */
internal object CrashThreadDump {
    private const val DEFAULT_MAX_CHARS = 64 * 1024

    fun capture(mainThread: Thread, maxChars: Int = DEFAULT_MAX_CHARS): String {
        val stacks = runCatching { Thread.getAllStackTraces() }.getOrDefault(emptyMap())
        val orderedThreads = buildList {
            add(mainThread)
            stacks.keys
                .asSequence()
                .filter { it !== mainThread }
                .sortedBy { it.name }
                .forEach { add(it) }
        }

        val output = buildString {
            orderedThreads.forEach { thread ->
                append("--- ")
                    .append(thread.name.ifBlank { "unnamed" })
                    .append(" [")
                    .append(thread.state.name)
                    .append("] ---\n")
                val stack = stacks[thread] ?: runCatching { thread.stackTrace }.getOrDefault(emptyArray())
                if (stack.isEmpty()) {
                    append("  <no stack available>\n")
                } else {
                    stack.forEach { frame ->
                        append("  at ").append(frame).append('\n')
                    }
                }
            }
        }

        if (output.isNotBlank()) {
            return output.take(maxChars.coerceAtLeast(4_096))
        }
        return "<thread dump unavailable>"
    }
}
