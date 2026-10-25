package com.chloemlla.lumen.crash

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeTombstoneTraceTest {
    @Test
    fun decodesSignalCauseAndCrashingThreadFrames() {
        val trace = NativeTombstoneTrace.decode(tombstone())

        assertTrue(trace.contains("Native tombstone (arm64)"))
        assertTrue(trace.contains("pid: 123, tid: 456"))
        assertTrue(trace.contains("Cmdline: example.app:virtualization_probe"))
        assertTrue(trace.contains("signal 31 (SIGSYS), code 1 (SYS_SECCOMP)"))
        assertTrue(trace.contains("Cause: seccomp prevented call to disallowed arm64 system call 142"))
        assertTrue(trace.contains("Thread: binder:123_3"))
        assertTrue(trace.contains("#00 pc 0000000000000042 /data/app/libchloemlla-crooot.so (probe+8)"))
        assertTrue(trace.contains("(BuildId: abcdef)"))
        assertFalse(trace.contains("malformed"))
        assertFalse(trace.contains('�'))
    }

    @Test
    fun threadMapOrderAndLateTidDoNotSelectAnUnrelatedThread() {
        val data = message(
            number(1, 1),
            threadEntry(100, "unrelated-thread", "wrong-frame"),
            threadEntry(456, "crashing-thread", "correct-frame"),
            number(6, 456),
        )

        val trace = NativeTombstoneTrace.decode(data)

        assertTrue(trace.contains("crashing-thread"))
        assertTrue(trace.contains("correct-frame"))
        assertFalse(trace.contains("wrong-frame"))
    }

    @Test
    fun findsCrashingThreadAfterManyUnrelatedThreads() {
        val entries = (1L..100L).map { threadEntry(it, "other", "other-frame") }
        val trace = NativeTombstoneTrace.decode(
            number(6, 456) + entries.fold(byteArrayOf()) { bytes, entry -> bytes + entry } +
                threadEntry(456, "crashing-thread", "correct-frame"),
        )

        assertTrue(trace.contains("correct-frame"))
        assertFalse(trace.contains("other-frame"))
    }

    @Test
    fun skipsUnknownFieldsOfEverySupportedWireType() {
        val unknown = message(
            number(1000, 999),
            varint((1001L shl 3) or 1) + ByteArray(8) { 0xFF.toByte() },
            field(1002, byteArrayOf(0, 1, 0xFF.toByte())),
            varint((1003L shl 3) or 5) + ByteArray(4) { 0xFF.toByte() },
        )

        assertEquals(
            NativeTombstoneTrace.decode(tombstone()),
            NativeTombstoneTrace.decode(unknown + tombstone()),
        )
    }

    @Test
    fun truncatedTrailingMemoryDumpKeepsPreviouslyDecodedFrames() {
        val trace = NativeTombstoneTrace.decode(
            tombstone() + varint((17L shl 3) or 2) + varint(50) + byteArrayOf(1, 2),
        )

        assertTrue(trace.contains("libchloemlla-crooot.so"))
        assertTrue(trace.contains("truncated or malformed"))
        assertFalse(trace.contains('�'))
    }

    @Test
    fun explicitInputTruncationIsMarkedEvenIfItEndedAtAFieldBoundary() {
        assertTrue(NativeTombstoneTrace.decode(tombstone(), truncated = true).contains("truncated"))
    }

    @Test
    fun rejectsWrongWireTypeForKnownFields() {
        val trace = NativeTombstoneTrace.decode(text(6, "not a tid"))

        assertTrue(trace.contains("malformed"))
        assertFalse(trace.contains("not a tid"))
    }

    @Test
    fun rejectsOverflowAndInvalidLengthsWithoutThrowing() {
        val malformedInputs = listOf(
            byteArrayOf(0),
            ByteArray(11) { 0x80.toByte() },
            number(1, 1) + varint((2L shl 3) or 2) + varint(Long.MAX_VALUE),
            number(1, 1) + varint((2L shl 3) or 2) + varint(-1L),
            varint((1000L shl 3) or 3),
            varint((1000L shl 3) or 1) + byteArrayOf(1),
        )

        malformedInputs.forEach { bytes ->
            assertTrue(NativeTombstoneTrace.decode(bytes).contains("malformed"))
        }
    }

    @Test
    fun invalidUtf8IsNotRenderedAsReplacementCharacters() {
        val trace = NativeTombstoneTrace.decode(field(2, byteArrayOf(0xFF.toByte())))

        assertTrue(trace.contains("malformed"))
        assertFalse(trace.contains('�'))
    }

    @Test
    fun unavailableCrashingThreadNeverFallsBackToAnotherThread() {
        val trace = NativeTombstoneTrace.decode(
            number(6, 456) + threadEntry(123, "unrelated", "wrong-frame"),
        )

        assertTrue(trace.contains("crashing thread backtrace unavailable"))
        assertFalse(trace.contains("wrong-frame"))
    }

    @Test
    fun limitsFrameCountAndRenderedOutput() {
        val frames = (0..99).map { field(4, text(4, "f".repeat(1024))) }
        val thread = field(16, number(1, 456) + field(2, message(*frames.toTypedArray())))
        val trace = NativeTombstoneTrace.decode(number(6, 456) + thread)

        assertTrue(trace.length <= 32 * 1024)
        assertTrue(trace.contains("#00"))
        assertFalse(trace.contains("#64"))
    }

    @Test
    fun capsFrameCountEvenWithShortFrames() {
        val frames = (0..99).map { field(4, text(4, "f$it")) }
        val trace = NativeTombstoneTrace.decode(
            number(6, 456) + field(16, number(1, 456) + field(2, message(*frames.toTypedArray()))),
        )

        assertTrue(trace.contains("#63"))
        assertFalse(trace.contains("#64"))
    }

    @Test
    fun honorsProto3DefaultArm32AndUnsignedProgramCounter() {
        val frame = field(4, number(1, -1L))
        val trace = NativeTombstoneTrace.decode(
            number(6, 456) + field(16, number(1, 456) + field(2, frame)),
        )

        assertTrue(trace.contains("Native tombstone (arm)"))
        assertTrue(trace.contains("pc ffffffffffffffff"))
    }

    @Test
    fun rejectsOversizedInputAndHandlesEmptyTrace() {
        assertTrue(NativeTombstoneTrace.decode(ByteArray(128 * 1024 + 1)).contains("malformed"))
        assertEquals("<native tombstone unavailable>", NativeTombstoneTrace.decode(byteArrayOf()))
    }

    private fun tombstone(): ByteArray = message(
        number(1, 1),
        text(2, "test-fingerprint"),
        text(4, "2026-10-06 22:10:23"),
        number(5, 123),
        number(6, 456),
        text(9, "example.app:virtualization_probe"),
        field(10, message(number(1, 31), text(2, "SIGSYS"), number(3, 1), text(4, "SYS_SECCOMP"))),
        field(15, text(1, "seccomp prevented call to disallowed arm64 system call 142")),
        threadEntry(456, "binder:123_3", "probe"),
    )

    private fun threadEntry(id: Long, name: String, function: String): ByteArray = field(
        16,
        message(
            number(1, id),
            field(
                2,
                message(
                    number(1, id),
                    text(2, name),
                    field(4, message(number(1, 0x42), text(4, function), number(5, 8),
                        text(6, "/data/app/libchloemlla-crooot.so"), text(8, "abcdef"))),
                ),
            ),
        ),
    )

    private fun message(vararg fields: ByteArray): ByteArray =
        ByteArrayOutputStream().apply { fields.forEach { write(it) } }.toByteArray()

    private fun text(fieldNumber: Int, value: String): ByteArray = field(fieldNumber, value.toByteArray(Charsets.UTF_8))

    private fun field(number: Int, value: ByteArray): ByteArray =
        varint((number.toLong() shl 3) or 2) + varint(value.size.toLong()) + value

    private fun number(field: Int, value: Long): ByteArray = varint(field.toLong() shl 3) + varint(value)

    private fun varint(value: Long): ByteArray = ByteArrayOutputStream().apply {
        var remaining = value
        while (remaining and -128L != 0L) {
            write(((remaining and 0x7F) or 0x80).toInt())
            remaining = remaining ushr 7
        }
        write(remaining.toInt())
    }.toByteArray()
}
