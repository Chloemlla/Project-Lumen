package com.chloemlla.lumen.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parser tests for [CrashExternalInbox]'s file format.
 *
 * The inbox is the only channel a WebView shell (Tauri, Capacitor) has for handing over a Rust
 * panic or a JS error, so its accepted shapes and its fatal/non-fatal split are pinned here
 * without an Android runtime.
 */
class CrashExternalInboxTest {
    @Test
    fun jsonRecordIsParsedWithEveryField() {
        val record = CrashExternalRecordParser.parse(
            "panic-1.json",
            """
            {
              "source": "rust.panic",
              "message": "called `Option::unwrap()` on a `None` value",
              "stackTrace": "thread 'main' panicked\n#0 libphi_agent_lib.so",
              "timestampMillis": 1732219200000,
              "thread": "main"
            }
            """.trimIndent(),
        )
        requireNotNull(record)
        assertEquals("rust.panic", record.source)
        assertEquals("called `Option::unwrap()` on a `None` value", record.message)
        assertEquals("thread 'main' panicked\n#0 libphi_agent_lib.so", record.stackTrace)
        assertEquals(1732219200000L, record.occurredAtMillis)
        assertEquals("main", record.threadName)
        assertTrue("a file without a fatal flag is post-mortem evidence", record.fatal)
    }

    @Test
    fun nonFatalFlagIsHonouredUnderBothNames() {
        val direct = CrashExternalRecordParser.parse(
            "js-1.json",
            """{"message": "TypeError: x is undefined", "fatal": false}""",
        )
        requireNotNull(direct)
        assertEquals(false, direct.fatal)

        val alias = CrashExternalRecordParser.parse(
            "js-2.json",
            """{"msg": "Unhandled rejection", "isFatal": false}""",
        )
        requireNotNull(alias)
        assertEquals(false, alias.fatal)
    }

    @Test
    fun explicitFatalTrueStaysFatal() {
        val record = CrashExternalRecordParser.parse(
            "panic-2.json",
            """{"message": "abort", "fatal": true}""",
        )
        requireNotNull(record)
        assertTrue(record.fatal)
    }

    @Test
    fun messageFallsBackAcrossAliases() {
        assertEquals(
            "boom",
            CrashExternalRecordParser.parse("a.json", """{"error": "boom"}""")?.message,
        )
        assertEquals(
            "boom",
            CrashExternalRecordParser.parse("b.json", """{"msg": "boom"}""")?.message,
        )
    }

    @Test
    fun jsonWithoutMessageIsRejected() {
        assertNull(CrashExternalRecordParser.parse("a.json", """{"stack": "at x"}"""))
        assertNull(CrashExternalRecordParser.parse("b.json", "   "))
        assertNull(CrashExternalRecordParser.parse("c.json", "[1,2,3]"))
    }

    @Test
    fun plainTextKeepsFirstLineAsMessageAndStaysFatal() {
        val record = CrashExternalRecordParser.parse(
            "renderer.log",
            "WebView renderer died\nat android.webkit.WebViewClassic.handleMessage\n",
        )
        requireNotNull(record)
        assertEquals("native-log", record.source)
        assertEquals("WebView renderer died", record.message)
        assertEquals("at android.webkit.WebViewClassic.handleMessage", record.stackTrace)
        assertTrue(record.fatal)
    }

    @Test
    fun timestampAliasesAreAccepted() {
        assertEquals(
            42L,
            CrashExternalRecordParser.parse("a.json", """{"message":"x","timestamp":42}""")
                ?.occurredAtMillis,
        )
        assertEquals(
            42L,
            CrashExternalRecordParser.parse("a.json", """{"message":"x","occurredAtMillis":42}""")
                ?.occurredAtMillis,
        )
        assertEquals(
            0L,
            CrashExternalRecordParser.parse("a.json", """{"message":"x"}""")?.occurredAtMillis,
        )
    }

    @Test
    fun sourceDefaultsToHostInbox() {
        assertEquals(
            "host-inbox",
            CrashExternalRecordParser.parse("a.json", """{"message":"x"}""")?.source,
        )
        assertEquals(
            "host-inbox",
            CrashExternalRecordParser.parse("a.txt", "first line")?.source,
        )
    }
}
