package com.projectlumen.app.core.preferences

import android.content.Context
import androidx.annotation.StringRes

/**
 * Persists Settings page section expand/collapse choices so users can continue where they left off
 * after leaving the page, process death, or app restart.
 */
internal object SettingsSectionExpansionStore {
    private const val PREFS_NAME = "lumen_settings_section_expansion"

    fun isExpanded(context: Context, @StringRes titleRes: Int, default: Boolean): Boolean {
        val prefs = prefs(context)
        val key = key(titleRes)
        if (!prefs.contains(key)) return default
        return prefs.getBoolean(key, default)
    }

    fun setExpanded(context: Context, @StringRes titleRes: Int, expanded: Boolean) {
        prefs(context).edit().putBoolean(key(titleRes), expanded).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(@StringRes titleRes: Int): String = "section_$titleRes"
}
