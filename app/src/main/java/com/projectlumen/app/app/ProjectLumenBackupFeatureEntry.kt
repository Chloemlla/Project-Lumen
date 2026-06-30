package com.projectlumen.app.app

import android.net.Uri
import com.projectlumen.app.core.services.BackupImportSummary
import com.projectlumen.app.core.repositories.SettingsRepository
import com.projectlumen.app.core.services.DataBackupService
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

    fun shareBackup() {
        scope.launch {
            backup.shareBackup()
        }
    }

    fun previewBackupImport(uri: Uri) {
        scope.launch {
            _importPreview.value = withContext(Dispatchers.IO) {
                backup.previewImport(uri)
            }
        }
    }

    fun clearBackupImportPreview() {
        _importPreview.value = null
    }

    fun importBackup(uri: Uri) {
        scope.launch {
            withContext(Dispatchers.IO) {
                backup.importBackup(uri)
            }
            _importPreview.value = null
            val settings = settingsRepository.getOrDefault()
            runtimeEntry.applySettingsToActiveRuntime(settings, System.currentTimeMillis())
        }
    }
}
