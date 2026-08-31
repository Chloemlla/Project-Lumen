package com.projectlumen.app.core.security

import java.lang.reflect.Array
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Converts the complete CRooot result into a selectable developer diagnostic report.
 *
 * CRooot deliberately exposes Duck reports as [Any?]. Reflection keeps this display
 * forward-compatible when the SDK adds a new report type or diagnostic field without
 * coupling the app's security layer to every detector model.
 */
internal object CroootReportFormatter {
    private const val MAX_DEPTH = 8
    private const val MAX_ITEMS_PER_COLLECTION = 80
    private const val MAX_REPORT_LENGTH = 120_000

    fun format(value: Any): String {
        val output = StringBuilder()
        val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        appendValue(output, value, 0, visited, "CRoootScanResult")
        if (output.length > MAX_REPORT_LENGTH) {
            output.setLength(MAX_REPORT_LENGTH)
            output.appendLine()
            output.append("… report truncated at $MAX_REPORT_LENGTH characters")
        }
        return output.toString().trimEnd()
    }

    private fun appendValue(
        output: StringBuilder,
        value: Any?,
        depth: Int,
        visited: MutableSet<Any>,
        label: String?,
    ) {
        if (output.length >= MAX_REPORT_LENGTH) return
        val prefix = indent(depth)
        if (label != null) output.append(prefix).append(label).append(": ")
        if (value == null) {
            output.appendLine("null")
            return
        }
        if (value is Enum<*>) {
            output.appendLine(value.name)
            return
        }
        if (value is String || value is Number || value is Boolean || value is Char) {
            output.appendLine(value.toString())
            return
        }
        // Depth and cycle guards apply to every container type, not just reflected objects:
        // a self-referential map/list would otherwise recurse without bound.
        if (depth >= MAX_DEPTH) {
            output.appendLine("<max depth>")
            return
        }
        if (!visited.add(value)) {
            output.appendLine("<cycle>")
            return
        }
        when (value) {
            is Map<*, *> -> appendMap(output, value, depth, visited)
            is Iterable<*> -> appendIterable(output, value, depth, visited)
            else -> {
                if (value.javaClass.isArray) {
                    appendArray(output, value, depth, visited)
                } else {
                    appendObject(output, value, depth, visited)
                }
            }
        }
    }

    private fun appendObject(
        output: StringBuilder,
        value: Any,
        depth: Int,
        visited: MutableSet<Any>,
    ) {
        output.appendLine(value.javaClass.simpleName.ifBlank { value.javaClass.name })
        val fields = allInstanceFields(value.javaClass)
        if (fields.isEmpty()) {
            output.append(indent(depth + 1)).appendLine(value.toString())
            return
        }
        fields.forEach { field ->
            val fieldValue = runCatching {
                field.isAccessible = true
                field.get(value)
            }.getOrElse { "<unavailable: ${it::class.java.simpleName}>" }
            appendValue(output, fieldValue, depth + 1, visited, field.name)
        }
    }

    private fun appendMap(
        output: StringBuilder,
        value: Map<*, *>,
        depth: Int,
        visited: MutableSet<Any>,
    ) {
        output.appendLine("${value.javaClass.simpleName} (${value.size} entries)")
        value.entries.take(MAX_ITEMS_PER_COLLECTION).forEach { entry ->
            appendValue(output, entry.value, depth + 1, visited, entry.key?.toString() ?: "null")
        }
        appendCollectionLimit(output, value.size, depth)
    }

    private fun appendIterable(
        output: StringBuilder,
        value: Iterable<*>,
        depth: Int,
        visited: MutableSet<Any>,
    ) {
        val items = value.toList()
        output.appendLine("${value.javaClass.simpleName} (${items.size} items)")
        items.take(MAX_ITEMS_PER_COLLECTION).forEachIndexed { index, item ->
            appendValue(output, item, depth + 1, visited, "[$index]")
        }
        appendCollectionLimit(output, items.size, depth)
    }

    private fun appendArray(
        output: StringBuilder,
        value: Any,
        depth: Int,
        visited: MutableSet<Any>,
    ) {
        val size = Array.getLength(value)
        output.appendLine("${value.javaClass.simpleName} ($size items)")
        (0 until minOf(size, MAX_ITEMS_PER_COLLECTION)).forEach { index ->
            appendValue(output, Array.get(value, index), depth + 1, visited, "[$index]")
        }
        appendCollectionLimit(output, size, depth)
    }

    private fun appendCollectionLimit(output: StringBuilder, size: Int, depth: Int) {
        if (size > MAX_ITEMS_PER_COLLECTION) {
            output.append(indent(depth + 1))
                .appendLine("… $size total; showing first $MAX_ITEMS_PER_COLLECTION")
        }
    }

    private fun indent(depth: Int): String {
        return INDENTS.getOrNull(depth) ?: "  ".repeat(depth)
    }

    private fun allInstanceFields(type: Class<*>): List<Field> {
        fieldCache[type]?.let { return it }
        val fields = buildList {
            var current: Class<*>? = type
            while (current != null && current != Any::class.java) {
                current.declaredFields
                    .filter { field ->
                        !Modifier.isStatic(field.modifiers) &&
                            !field.isSynthetic
                    }
                    .forEach(::add)
                current = current.superclass
            }
        }.sortedBy(Field::getName)
        fieldCache[type] = fields
        return fields
    }

    private val fieldCache = ConcurrentHashMap<Class<*>, List<Field>>()

    private val INDENTS = List(MAX_DEPTH + 2) { depth -> "  ".repeat(depth) }
}
