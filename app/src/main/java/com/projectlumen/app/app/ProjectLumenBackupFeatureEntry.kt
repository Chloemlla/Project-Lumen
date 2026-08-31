package com.projectlumen.app.app

import android.net.Uri
import com.projectlumen.app.core.services.BackupImportSummary
import com.projectlumen.app.core.repositories.SettingsRepository
import com.projectlumen.app.core.services.DataBackupService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ProjectLumenBackupFeatureEntry(
    private val scope: CoroutineScope,
    private val backup: DataBackupService,
    private val settingsRepository: SettingsRepository,
    private val runtimeEntry: ProjectLumenRuntimeFeatureEntry,
) {
    private val _importPreview = MutableStateFlow<BackupImportSummary?>(null)
    val importPreview = _importPreview.asStateFlow()
    private val _importError = MutableStateFlow<String?>(null)
    val importError = _importError.asStateFlow()

    fun shareBackup() {
        scope.launch {
            backup.shareBackup()
        }
    }

    fun previewBackupImport(uri: Uri) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { backup.previewImport(uri) } }
                .onSuccess { summary ->
                    _importError.value = null
                    _importPreview.value = summary
                }
                .onFailure(::handleImportFailure)
        }
    }

    fun clearBackupImportPreview() {
        _importPreview.value = null
    }

    fun clearBackupImportError() {
        _importError.value = null
    }

    fun importBackup(uri: Uri) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { backup.importBackup(uri) }
                _importPreview.value = null
                _importError.value = null
                val settings = settingsRepository.getOrDefault()
                runtimeEntry.applySettingsToActiveRuntime(settings, System.currentTimeMillis())
            }.onFailure(::handleImportFailure)
        }
    }

    private fun handleImportFailure(throwable: Throwable) {
        // A cancelled job is not a failure: rethrowing keeps cancellation from surfacing as UI text.
        if (throwable is CancellationException) throw throwable
        failImport(throwable)
    }

    private fun failImport(throwable: Throwable) {
        _importPreview.value = null
        _importError.value = throwable.message?.takeIf { it.isNotBlank() } ?: throwable.javaClass.simpleName
    }
}
