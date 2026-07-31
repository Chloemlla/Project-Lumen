package com.projectlumen.app.app

import com.projectlumen.app.core.update.BuildMetadata

internal enum class ProjectLumenFirstOpenGate {
    OPEN_SOURCE_NOTICE,
    ONBOARDING,
    BUILD_UPDATE_NOTES,
    NONE,
}

internal data class ProjectLumenFirstOpenGateInput(
    val ossNoticeCompletedAt: Long,
    val onboardingCompletedAt: Long,
    val lastAcknowledgedCommitHash: String,
    val lastAcknowledgedBuildTimeUtcMillis: Long,
    val currentBuild: BuildMetadata,
    val grandfatherOssNotice: Boolean,
    val onboardingEligible: Boolean,
)

internal object ProjectLumenFirstOpenGateResolver {
    fun resolve(input: ProjectLumenFirstOpenGateInput): ProjectLumenFirstOpenGate {
        if (input.ossNoticeCompletedAt <= 0L && !input.grandfatherOssNotice) {
            return ProjectLumenFirstOpenGate.OPEN_SOURCE_NOTICE
        }
        if (input.onboardingCompletedAt <= 0L && input.onboardingEligible) {
            return ProjectLumenFirstOpenGate.ONBOARDING
        }

        val buildAlreadyAcknowledged = input.lastAcknowledgedCommitHash.isNotBlank() &&
            input.lastAcknowledgedCommitHash == input.currentBuild.commitHash &&
            input.lastAcknowledgedBuildTimeUtcMillis > 0L &&
            input.lastAcknowledgedBuildTimeUtcMillis == input.currentBuild.buildTimeUtcMillis
        return if (buildAlreadyAcknowledged) {
            ProjectLumenFirstOpenGate.NONE
        } else {
            ProjectLumenFirstOpenGate.BUILD_UPDATE_NOTES
        }
    }
}
