package com.projectlumen.app.app

import androidx.compose.runtime.Composable

@Composable
internal fun ProjectLumenAutomaticFirstOpenGateHost(
    viewModel: ProjectLumenViewModel,
    ossNoticeState: ProjectLumenOssNoticeState,
    onboardingState: ProjectLumenOnboardingState,
    buildUpdateNotesState: ProjectLumenBuildUpdateNotesState,
): Boolean {
    return when {
        ossNoticeState.visible && !ossNoticeState.reopenMode -> {
            ProjectLumenOpenSourceNoticeScreen(onContinue = viewModel::completeOssNotice)
            true
        }
        onboardingState.visible -> {
            ProjectLumenOnboardingScreen(
                state = onboardingState,
                onComplete = viewModel::completeOnboarding,
            )
            true
        }
        buildUpdateNotesState.visible && !buildUpdateNotesState.reopenMode -> {
            val notes = buildUpdateNotesState.notes
            if (notes != null) {
                ProjectLumenBuildUpdateNotesScreen(
                    notes = notes,
                    onContinue = viewModel::completeBuildUpdateNotes,
                )
            }
            notes != null
        }
        else -> false
    }
}

@Composable
internal fun ProjectLumenReopenedFirstOpenGateOverlay(
    viewModel: ProjectLumenViewModel,
    ossNoticeState: ProjectLumenOssNoticeState,
    buildUpdateNotesState: ProjectLumenBuildUpdateNotesState,
) {
    when {
        ossNoticeState.visible && ossNoticeState.reopenMode -> {
            ProjectLumenOpenSourceNoticeScreen(
                onContinue = viewModel::completeOssNotice,
                onDismiss = viewModel::dismissOssNotice,
            )
        }
        buildUpdateNotesState.visible && buildUpdateNotesState.reopenMode -> {
            buildUpdateNotesState.notes?.let { notes ->
                ProjectLumenBuildUpdateNotesScreen(
                    notes = notes,
                    onContinue = viewModel::completeBuildUpdateNotes,
                    onDismiss = viewModel::dismissBuildUpdateNotes,
                )
            }
        }
    }
}
