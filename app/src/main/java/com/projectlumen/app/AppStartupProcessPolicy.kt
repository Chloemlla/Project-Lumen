package com.projectlumen.app

/** Host startup requires the default process name and app UID; isolated services can share the name. */
internal object AppStartupProcessPolicy {
    fun shouldInitializeHost(
        packageName: String?,
        processName: String?,
        uid: Int?,
        applicationUid: Int?,
    ): Boolean =
        uid != null && applicationUid != null && uid >= 0 && applicationUid >= 0 &&
            uid == applicationUid &&
            !packageName.isNullOrBlank() && !processName.isNullOrBlank() && processName == packageName
}
