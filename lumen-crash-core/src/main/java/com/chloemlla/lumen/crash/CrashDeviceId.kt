package com.chloemlla.lumen.crash

import android.content.Context
import java.util.UUID

/**
 * Default per-install device identifier for anonymous crash uploads.
 *
 * Persists a random UUID in SharedPreferences so the SDK can tag reports with a
 * stable device ID without requiring any host configuration.
 */
object CrashDeviceId {
    private const val PREFS_NAME = "lumen_crash_sdk"
    private const val KEY_DEVICE_INSTALLATION_ID = "device_installation_id"

    @JvmStatic
    fun resolve(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_INSTALLATION_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_INSTALLATION_ID, created).apply()
        return created
    }
}
