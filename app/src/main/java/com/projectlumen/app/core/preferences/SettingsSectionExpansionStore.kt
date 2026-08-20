package com.projectlumen.app.core.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes

/**
 * Persists Settings page section expand/collapse choices so users can continue where they left off
 * after leaving the page, process death, or app restart.
 */
internal object SettingsSectionExpansionStore {
    private const val PREFS_NAME = "lumen_settings_section_expansion"

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    fun isExpanded(context: Context, title: String, default: Boolean): Boolean {
        return prefs(context).getBoolean(key(title), default)
    }

    fun setExpanded(context: Context, title: String, expanded: Boolean) {
        prefs(context).edit().putBoolean(key(title), expanded).apply()
    }

    fun isExpanded(context: Context, @StringRes titleRes: Int, default: Boolean): Boolean {
        return prefs(context).getBoolean(key(titleRes), default)
    }

    fun setExpanded(context: Context, @StringRes titleRes: Int, expanded: Boolean) {
        prefs(context).edit().putBoolean(key(titleRes), expanded).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        cachedPrefs ?: context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .also { cachedPrefs = it }

    private fun key(title: String): String = "section_str_$title"

    private fun key(@StringRes titleRes: Int): String = "section_$titleRes"
}
