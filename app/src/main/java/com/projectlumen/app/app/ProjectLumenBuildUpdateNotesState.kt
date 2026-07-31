package com.projectlumen.app.app

import com.projectlumen.app.core.update.BuildUpdateNotes

data class ProjectLumenBuildUpdateNotesState(
    val visible: Boolean = false,
    val reopenMode: Boolean = false,
    val notes: BuildUpdateNotes? = null,
)
