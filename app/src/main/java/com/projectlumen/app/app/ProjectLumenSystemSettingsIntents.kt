package com.projectlumen.app.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.projectlumen.app.R

internal fun openUsageAccessSettings(context: Context) {
    val packageUri = Uri.parse("package:${context.packageName}")
    startFirstAvailableSettingsActivity(
        context,
        listOf(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
        ),
    )
}

internal fun openAppDetailsSettings(context: Context) {
    val packageUri = Uri.parse("package:${context.packageName}")
    startFirstAvailableSettingsActivity(
        context,
        listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
            Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
        ),
    )
}

internal fun openSystemBatteryUsageSettings(context: Context) {
    val packageUri = Uri.parse("package:${context.packageName}")
    startFirstAvailableSettingsActivity(
        context,
        listOf(
            Intent(ACTION_VIEW_ADVANCED_POWER_USAGE_DETAIL, packageUri),
            Intent(ACTION_BATTERY_USAGE_SETTINGS),
            Intent(ACTION_POWER_USAGE_SUMMARY),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
        ),
    )
}

private fun startFirstAvailableSettingsActivity(context: Context, intents: List<Intent>) {
    val launched = intents.any { intent ->
        if (intent.resolveActivity(context.packageManager) == null) return@any false
        runCatching { context.startActivity(intent) }.isSuccess
    }
    if (!launched) {
        Toast.makeText(context, R.string.system_settings_open_failed, Toast.LENGTH_SHORT).show()
    }
}

private const val ACTION_BATTERY_USAGE_SETTINGS = "android.settings.BATTERY_USAGE_SETTINGS"
private const val ACTION_POWER_USAGE_SUMMARY = "android.intent.action.POWER_USAGE_SUMMARY"
private const val ACTION_VIEW_ADVANCED_POWER_USAGE_DETAIL = "android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL"
