package com.chloemlla.lumen.crash

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException

/**
 * API 31+ native exit traces are protobuf, not text. Read only the diagnostic fields from
 * debuggerd/proto/tombstone.proto; memory dumps, logs and unknown OEM fields are skipped.
 * https://android.googlesource.com/platform/system/core/+/master/debuggerd/proto/tombstone.proto
 */
internal object NativeTombstoneTrace {
    private const val MAX_INPUT_BYTES = 128 * 1024
    private const val MAX_OUTPUT_CHARS = 32 * 1024
    private const val MAX_FIELD_CHARS = 1_024
    private const val MAX_FRAMES = 64
    private const val INVALID_TRACE = "<native tombstone truncated or malformed>"

    fun decode(bytes: ByteArray, truncated: Boolean = false): String {
        if (bytes.isEmpty()) return "<native tombstone unavailable>"
        if (bytes.size > MAX_INPUT_BYTES) return INVALID_TRACE
        var pid = 0L
        var tid = 0L
        var arch = 0L
        var fingerprint = ""
        var timestamp = ""
        var commandLine = ""
        var signal = ""
        var abortMessage = ""
        val causes = mutableListOf<String>()
        var malformed = truncated
        try {
            val reader = ProtoReader(bytes)
            while (reader.hasRemaining()) {
                when (reader.nextField()) {
                    1 -> arch = reader.number()
                    2 -> fingerprint = reader.text()
                    4 -> timestamp = reader.text()
                    5 -> pid = reader.number()
                    6 -> tid = reader.number()
                    9 -> {
                        val argument = reader.text()
                        if (commandLine.isEmpty()) commandLine = argument
                    }
                    10 -> signal = readSignal(reader.message())
                    14 -> abortMessage = reader.text()
                    15 -> {
                        val cause = readCause(reader.message())
                        if (causes.size < MAX_FRAMES && cause.isNotBlank()) causes.add(cause)
                    }
                    else -> reader.skip()
                }
            }
        } catch (_: IllegalArgumentException) {
            // Keep completely decoded fields if the bounded input ended partway through a dump.
            malformed = true
        } catch (_: CharacterCodingException) {
            malformed = true
        }
        val thread = try {
            findCrashingThread(bytes, tid)
        } catch (_: IllegalArgumentException) {
            malformed = true
            null
        } catch (_: CharacterCodingException) {
            malformed = true
            null
        }
        return buildString {
            appendLine("Native tombstone (${architectureName(arch)})")
            if (fingerprint.isNotBlank()) appendLine("Build fingerprint: $fingerprint")
            if (timestamp.isNotBlank()) appendLine("Timestamp: $timestamp")
            if (commandLine.isNotBlank()) appendLine("Cmdline: $commandLine")
            if (pid != 0L || tid != 0L) appendLine("pid: $pid, tid: $tid")
            if (signal.isNotBlank()) appendLine(signal)
            if (abortMessage.isNotBlank()) appendLine("Abort message: $abortMessage")
            causes.forEach { appendLine("Cause: $it") }
            if (thread != null) {
                appendLine("Thread: ${thread.name.ifBlank { "unknown" }}")
                thread.frames.forEachIndexed { index, frame ->
                    appendLine("#${index.toString().padStart(2, '0')} $frame")
                }
                if (thread.frames.isEmpty()) appendLine("<native backtrace unavailable>")
            } else {
                appendLine("<crashing thread backtrace unavailable>")
            }
            if (malformed) appendLine(INVALID_TRACE)
        }.take(MAX_OUTPUT_CHARS).trimEnd()
    }

    private data class ThreadTrace(val id: Long, val name: String, val frames: List<String>)

    private fun findCrashingThread(bytes: ByteArray, tid: Long): ThreadTrace? {
        if (tid == 0L) return null
        // A protobuf may place tid after the map. A second bounded pass avoids storing every
        // thread's frames or accidentally reporting the first thread instead of the crashing one.
        val reader = ProtoReader(bytes)
        while (reader.hasRemaining()) {
            if (reader.nextField() != 16) {
                reader.skip()
                continue
            }
            val entry = reader.message()
            var key = 0L
            var thread: ProtoReader? = null
            while (entry.hasRemaining()) {
                when (entry.nextField()) {
                    1 -> key = entry.number()
                    2 -> thread = entry.message()
                    else -> entry.skip()
                }
            }
            if (key == tid && thread != null) return readThread(thread)
        }
        return null
    }

    private fun readThread(reader: ProtoReader): ThreadTrace {
        var id = 0L
        var name = ""
        val frames = mutableListOf<String>()
        while (reader.hasRemaining()) {
            when (reader.nextField()) {
                1 -> id = reader.number()
                2 -> name = reader.text()
                4 -> {
                    if (frames.size < MAX_FRAMES) frames.add(readFrame(reader.message()))
                    else reader.skip()
                }
                else -> reader.skip()
            }
        }
        return ThreadTrace(id, name, frames)
    }

    private fun readFrame(reader: ProtoReader): String {
        var relativePc = 0L
        var function = ""
        var offset = 0L
        var file = ""
        var buildId = ""
        while (reader.hasRemaining()) {
            when (reader.nextField()) {
                1 -> relativePc = reader.number()
                4 -> function = reader.text()
                5 -> offset = reader.number()
                6 -> file = reader.text()
                8 -> buildId = reader.text()
                else -> reader.skip()
            }
        }
        return buildString {
            append("pc ${java.lang.Long.toUnsignedString(relativePc, 16).padStart(16, '0')} ")
            append(file.ifBlank { "<unknown>" })
            if (function.isNotBlank()) append(" ($function+$offset)")
            if (buildId.isNotBlank()) append(" (BuildId: $buildId)")
        }
    }

    private fun readSignal(reader: ProtoReader): String {
        var number = 0L
        var name = ""
        var code = 0L
        var codeName = ""
        while (reader.hasRemaining()) {
            when (reader.nextField()) {
                1 -> number = reader.number()
                2 -> name = reader.text()
                3 -> code = reader.number()
                4 -> codeName = reader.text()
                else -> reader.skip()
            }
        }
        return "signal $number ($name), code $code ($codeName)"
    }

    private fun readCause(reader: ProtoReader): String {
        var text = ""
        while (reader.hasRemaining()) {
            when (reader.nextField()) {
                1 -> text = reader.text()
                else -> reader.skip()
            }
        }
        return text
    }

    private fun architectureName(value: Long): String = when (value) {
        0L -> "arm"
        1L -> "arm64"
        2L -> "x86"
        3L -> "x86_64"
        4L -> "riscv64"
        else -> "unknown"
    }

    private class ProtoReader(
        private val bytes: ByteArray,
        private var position: Int = 0,
        private val end: Int = bytes.size,
    ) {
        private var wireType = -1

        fun hasRemaining(): Boolean = position < end

        fun nextField(): Int {
            val tag = varint()
            require(tag > 0 && tag <= 0xFFFF_FFFFL) { "Invalid protobuf tag" }
            val field = (tag ushr 3).toInt()
            require(field != 0) { "Invalid protobuf field" }
            wireType = (tag and 7).toInt()
            return field
        }

        fun number(): Long {
            require(wireType == 0) { "Expected varint" }
            return varint()
        }

        fun text(): String {
            val nested = message()
            return Charsets.UTF_8.newDecoder()
                .decode(ByteBuffer.wrap(bytes, nested.position, nested.end - nested.position))
                .toString().take(MAX_FIELD_CHARS)
        }

        fun message(): ProtoReader {
            require(wireType == 2) { "Expected length-delimited field" }
            val length = varint()
            require(length >= 0 && length <= (end - position).toLong()) { "Invalid field length" }
            val start = position
            position += length.toInt()
            return ProtoReader(bytes, start, position)
        }

        fun skip() {
            when (wireType) {
                0 -> varint()
                1 -> advance(8)
                2 -> message()
                5 -> advance(4)
                else -> throw IllegalArgumentException("Unsupported wire type")
            }
        }

        private fun advance(count: Int) {
            require(count <= end - position) { "Truncated field" }
            position += count
        }

        private fun varint(): Long {
            var value = 0L
            for (index in 0 until 10) {
                require(position < end) { "Truncated varint" }
                val byte = bytes[position++].toInt() and 0xFF
                if (index == 9) require(byte <= 1) { "Varint overflow" }
                value = value or ((byte and 0x7F).toLong() shl (index * 7))
                if (byte and 0x80 == 0) return value
            }
            throw IllegalArgumentException("Varint overflow")
        }
    }
}
