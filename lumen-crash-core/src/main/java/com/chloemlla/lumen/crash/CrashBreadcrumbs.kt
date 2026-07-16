package com.chloemlla.lumen.crash

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val crashBreadcrumbTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

object CrashBreadcrumbs {
    private const val MAX_EVENTS = 40
    private val events = ArrayDeque<String>(MAX_EVENTS)

    @Synchronized
    fun record(event: String) {
        val sanitized = sanitize(event).take(180)
        if (sanitized.isBlank()) return
        if (events.size >= MAX_EVENTS) {
            events.removeFirst()
        }
        val time = Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(ZoneId.systemDefault())
            .format(crashBreadcrumbTimeFormatter)
        events.addLast("$time  $sanitized")
    }

    @Synchronized
    fun snapshot(): List<String> = events.toList()

    @Synchronized
    fun clear() {
        events.clear()
    }

    private fun sanitize(value: String): String {
        return value
            .replace(Regex("""[A-Za-z]:\\Users\\[^\\\s]+"""), "[user-home]")
            .replace(Regex("""/home/[^/\s]+"""), "[user-home]")
            .replace(Regex("""/Users/[^/\s]+"""), "[user-home]")
            .replace(Regex("""content://[^\s]+"""), "[content-uri]")
            .replace(Regex("""file://[^\s]+"""), "[file-uri]")
    }
}
