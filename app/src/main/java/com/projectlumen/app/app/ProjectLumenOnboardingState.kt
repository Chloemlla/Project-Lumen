package com.projectlumen.app.app

internal data class ProjectLumenOnboardingState(
    val visible: Boolean = false,
    val deviceFingerprint: String = "",
    val newInstallDetected: Boolean = false,
)
