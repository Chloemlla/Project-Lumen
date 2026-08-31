package com.chloemlla.lumen.crash

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

private val crashBreadcrumbTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
private val breadcrumbWindowsHomeRegex = Regex("""[A-Za-z]:\\Users\\[^\\\s]+""")
private val breadcrumbLinuxHomeRegex = Regex("""/home/[^/\s]+""")
private val breadcrumbMacHomeRegex = Regex("""/Users/[^/\s]+""")
private val breadcrumbContentUriRegex = Regex("""content://[^\s]+""")
private val breadcrumbFileUriRegex = Regex("""file://[^\s]+""")
private val breadcrumbBearerTokenRegex = Regex("""(?i)Bearer\s+[A-Za-z0-9._~+/=-]{8,}""")
private val breadcrumbSecretParamRegex =
    Regex("""(?i)\b(token|key|secret|password|passwd|auth|signature)=[^&\s"']+""")

object CrashBreadcrumbs {
    private const val MAX_EVENTS = 40
    private const val MAX_EVENT_LENGTH = 180
    private const val READ_LOCK_TIMEOUT_MILLIS = 50L

    /** Emitted instead of an event list when the writer holds the lock past the read timeout. */
    const val UNAVAILABLE_MARKER: String = "<breadcrumbs unavailable>"

    private val lock = ReentrantLock()
    private val events = ArrayDeque<String>(MAX_EVENTS)
    private var lastEvent: String? = null
    private var lastRepeatCount = 1

    fun record(event: String) {
        val sanitized = sanitize(event).take(MAX_EVENT_LENGTH)
        if (sanitized.isBlank()) return
        // Sanitizing and formatting outside the critical section keeps the window in which a
        // stalled writer can block snapshot() as short as possible.
        val entry = "${formatNow()}  $sanitized"
        lock.lock()
        try {
            if (sanitized == lastEvent && events.isNotEmpty()) {
                // A repeating event (a service loop reporting the same handled failure) must not
                // flush every other breadcrumb out of the ring.
                lastRepeatCount++
                events.removeLast()
                events.addLast("$entry (x$lastRepeatCount)")
                return
            }
            lastEvent = sanitized
            lastRepeatCount = 1
            if (events.size >= MAX_EVENTS) {
                events.removeFirst()
            }
            events.addLast(entry)
        } finally {
            lock.unlock()
        }
    }

    /**
     * Returns the recorded events, or a single [UNAVAILABLE_MARKER] entry when the lock could
     * not be acquired within [READ_LOCK_TIMEOUT_MILLIS].
     *
     * The watchdog calls this from its own thread while the main thread is unresponsive, so it
     * must never block indefinitely: a report without breadcrumbs beats losing both the report
     * and every later watchdog tick.
     */
    fun snapshot(): List<String> {
        if (!tryLockForRead()) return listOf(UNAVAILABLE_MARKER)
        try {
            return events.toList()
        } finally {
            lock.unlock()
        }
    }

    fun clear() {
        lock.lock()
        try {
            events.clear()
            lastEvent = null
            lastRepeatCount = 1
        } finally {
            lock.unlock()
        }
    }

    private fun tryLockForRead(): Boolean {
        return runCatching { lock.tryLock(READ_LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS) }
            .getOrDefault(false)
    }

    private fun formatNow(): String {
        return runCatching {
            Instant.ofEpochMilli(System.currentTimeMillis())
                .atZone(ZoneId.systemDefault())
                .format(crashBreadcrumbTimeFormatter)
        }.getOrDefault("--:--:--.---")
    }

    private fun sanitize(value: String): String {
        return value
            .replace(breadcrumbWindowsHomeRegex, "[user-home]")
            .replace(breadcrumbLinuxHomeRegex, "[user-home]")
            .replace(breadcrumbMacHomeRegex, "[user-home]")
            .replace(breadcrumbContentUriRegex, "[content-uri]")
            .replace(breadcrumbFileUriRegex, "[file-uri]")
            .replace(breadcrumbBearerTokenRegex, "Bearer [redacted]")
            .replace(breadcrumbSecretParamRegex, "$1=[redacted]")
    }
}
