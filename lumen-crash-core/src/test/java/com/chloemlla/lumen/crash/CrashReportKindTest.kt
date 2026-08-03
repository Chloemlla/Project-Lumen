package com.chloemlla.lumen.crash

import org.junit.Assert.assertEquals
import org.junit.Test

class CrashReportKindTest {
    @Test
    fun unknownWireValueFallsBackToCrashForOlderOrCorruptReports() {
        assertEquals(CrashReportKind.CRASH, CrashReportKind.fromWireValue("future_kind"))
        assertEquals(CrashReportKind.CRASH, CrashReportKind.fromWireValue(null))
    }

    @Test
    fun watchdogKindsUseStableWireValues() {
        assertEquals("anr", CrashReportKind.ANR.wireValue)
        assertEquals("startup_hang", CrashReportKind.STARTUP_HANG.wireValue)
        assertEquals("freeze", CrashReportKind.FREEZE.wireValue)
    }
}
