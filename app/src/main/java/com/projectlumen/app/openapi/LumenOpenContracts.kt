package com.projectlumen.app.openapi

import android.content.Intent
import android.os.SystemClock

enum class LumenOpenLaunchTarget {
    DASHBOARD,
    REST,
    VISUAL_MONITOR,
}

data class LumenOpenLaunchRequest(
    val id: Long,
    val target: LumenOpenLaunchTarget,
    val sourceApp: String,
    val restDurationSeconds: Int? = null,
)

object LumenOpenContracts {
    const val PERMISSION_ACCESS_CORE = "com.project.lumen.permission.ACCESS_LUMEN_CORE"
    const val PERMISSION_TRIGGER_CONTROL = "com.project.lumen.permission.TRIGGER_LUMEN_CONTROL"

    const val ACTION_VIEW_DASHBOARD = "com.project.lumen.action.VIEW_DASHBOARD"
    const val ACTION_TRIGGER_REST = "com.project.lumen.action.TRIGGER_REST"
    const val ACTION_START_VISUAL_MONITOR = "com.project.lumen.action.START_VISUAL_MONITOR"
    const val ACTION_BIND_OPEN_API = "com.project.lumen.action.BIND_OPEN_API"

    const val EXTRA_CALLER_PACKAGE = "EXTRA_CALLER_PACKAGE"
    const val EXTRA_REST_DURATION_MIN = "EXTRA_REST_DURATION_MIN"
    const val EXTRA_SOURCE_APP = "com.project.lumen.extra.SOURCE_APP"

    const val SOURCE_APP_PROJECT_LUMEN = "project_lumen"
    const val SOURCE_APP_EXTERNAL = "external"
}

object LumenOpenIntents {
    fun parseLaunchRequest(
        intent: Intent?,
        platformCallerPackage: String?,
    ): LumenOpenLaunchRequest? {
        val target = when (intent?.action) {
            LumenOpenContracts.ACTION_VIEW_DASHBOARD -> LumenOpenLaunchTarget.DASHBOARD
            LumenOpenContracts.ACTION_TRIGGER_REST -> LumenOpenLaunchTarget.REST
            LumenOpenContracts.ACTION_START_VISUAL_MONITOR -> LumenOpenLaunchTarget.VISUAL_MONITOR
            else -> return null
        }
        val sourceApp = sanitizeLumenOpenSourceApp(
            platformCallerPackage
                ?: intent.getStringExtra(LumenOpenContracts.EXTRA_CALLER_PACKAGE)
                ?: intent.getStringExtra(LumenOpenContracts.EXTRA_SOURCE_APP),
            fallback = LumenOpenContracts.SOURCE_APP_EXTERNAL,
        )
        val restDurationSeconds = intent.getPositiveIntExtra(LumenOpenContracts.EXTRA_REST_DURATION_MIN)
            ?.coerceIn(1, 60)
            ?.times(60)
        return LumenOpenLaunchRequest(
            id = SystemClock.elapsedRealtimeNanos(),
            target = target,
            sourceApp = sourceApp,
            restDurationSeconds = restDurationSeconds,
        )
    }

    private fun Intent.getPositiveIntExtra(name: String): Int? {
        if (!hasExtra(name)) return null
        return getIntExtra(name, 0).takeIf { it > 0 }
    }
}

internal fun sanitizeLumenOpenSourceApp(
    sourceApp: String?,
    fallback: String = LumenOpenContracts.SOURCE_APP_PROJECT_LUMEN,
): String {
    val trimmed = sourceApp
        ?.trim()
        ?.take(120)
        ?.takeIf { it.isNotBlank() }
        ?: return fallback
    return if (SOURCE_APP_PATTERN.matches(trimmed)) trimmed else fallback
}

private val SOURCE_APP_PATTERN = Regex("[A-Za-z0-9_.:-]{1,120}")
