package com.projectlumen.app.app

import com.projectlumen.app.core.services.ExportService

internal class ProjectLumenSharingFeatureEntry(
    private val export: ExportService,
    private val stateProvider: () -> ProjectLumenUiState,
) {
    fun shareStatistics() {
        val state = stateProvider()
        if (!state.settings.statsEnabled) return
        export.shareCsv(state.eyeStats, state.pomodoroStats)
    }

    fun shareStatisticsImage() {
        val state = stateProvider()
        if (!state.settings.statsEnabled) return
        export.shareStatsImage(state.eyeStats, state.pomodoroStats)
    }

    fun shareMonthlyReportPdf() {
        val state = stateProvider()
        if (!state.settings.statsEnabled) return
        export.shareMonthlyPdf(state.eyeStats, state.pomodoroStats)
    }
}
