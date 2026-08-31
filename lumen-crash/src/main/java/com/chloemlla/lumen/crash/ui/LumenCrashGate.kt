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
 *
 * When [initialReport] is null the report is loaded once per gate instance. Loading it from a
 * default argument instead would re-read storage on the composition thread on every
 * recomposition, and could replace a running host UI with the crash screen mid-session.
 */
@Composable
fun LumenCrashGate(
    initialReport: CrashReport? = null,
    clearStoredReportOnContinue: Boolean = true,
    onClearStoredReport: (() -> Unit)? = null,
    onContinue: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val resolvedReport = initialReport ?: remember { LumenCrash.loadPendingReportSafely() }
    var pendingReport by remember(resolvedReport?.reportId) {
        mutableStateOf(resolvedReport)
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
