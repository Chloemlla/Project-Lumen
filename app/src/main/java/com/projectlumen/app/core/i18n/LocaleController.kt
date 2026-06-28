package com.projectlumen.app.core.i18n

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LocaleController {
    const val SYSTEM = "system"
    const val CHINESE = "zh"
    const val ENGLISH = "en"

    fun apply(languageCode: String) {
        runCatching {
            AppCompatDelegate.setApplicationLocales(localeListFor(normalize(languageCode)))
        }
    }

    fun wrap(base: Context, languageCode: String): Context {
        return base
    }

    fun normalize(languageCode: String?): String {
        val value = languageCode?.trim().orEmpty()
        return when {
            value.isEmpty() -> SYSTEM
            value.equals(SYSTEM, ignoreCase = true) -> SYSTEM
            value.equals(CHINESE, ignoreCase = true) -> CHINESE
            value.equals(ENGLISH, ignoreCase = true) -> ENGLISH
            else -> value.lowercase(Locale.ROOT)
        }
    }

    private fun localeListFor(languageCode: String): LocaleListCompat {
        return if (languageCode == SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageCode)
        }
    }
}
