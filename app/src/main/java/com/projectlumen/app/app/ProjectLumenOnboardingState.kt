package com.projectlumen.app.app

data class ProjectLumenOnboardingState(
    val visible: Boolean = false,
    val deviceFingerprint: String = "",
    val newInstallDetected: Boolean = false,
)
