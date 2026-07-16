package com.chloemlla.lumen.crash.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.chloemlla.lumen.crash.CrashReport
import com.chloemlla.lumen.crash.LumenCrash

/**
 * Short host integration path for pending crash UI.
 *
 * Loads any pending report and either shows [LumenCrashReportScreen] or the host [content].
 * Continue clears the pending report by default so the next cold start is not re-blocked.
 */
@Composable
fun LumenCrashGate(
    initialReport: CrashReport? = LumenCrash.loadPendingReportSafely(),
    clearStoredReportOnContinue: Boolean = true,
    onClearStoredReport: (() -> Unit)? = null,
    onContinue: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var pendingReport by remember(initialReport?.reportId) {
        mutableStateOf(initialReport)
    }

    val report = pendingReport
    if (report != null) {
        LumenCrashReportScreen(
            report = report,
            onContinue = {
                pendingReport = null
                onContinue?.invoke()
            },
            clearStoredReportOnContinue = clearStoredReportOnContinue,
            onClearStoredReport = onClearStoredReport,
        )
        return
    }

    content()
}
