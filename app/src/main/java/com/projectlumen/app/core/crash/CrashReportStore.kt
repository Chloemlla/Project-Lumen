package com.projectlumen.app.core.crash

import android.content.Context
import org.json.JSONObject
import java.io.File

class CrashReportStore(context: Context) {
    private val file = File(context.filesDir, FILE_NAME)

    fun save(report: CrashReport) {
        file.writeText(report.toJson().toString())
    }

    fun load(): CrashReport? {
        if (!file.exists()) return null
        return runCatching { crashReportFromJson(JSONObject(file.readText())) }.getOrNull()
    }

    fun clear() {
        if (file.exists()) file.delete()
    }

    private fun CrashReport.toJson(): JSONObject = JSONObject().apply {
        put("crashedAtMillis", crashedAtMillis)
        put("crashedAtText", crashedAtText)
        put("exceptionType", exceptionType)
        put("rootCause", rootCause)
        put("systemInfo", systemInfo)
        put("stackTrace", stackTrace)
    }

    private companion object {
        const val FILE_NAME = "crash_report.json"
    }
}
