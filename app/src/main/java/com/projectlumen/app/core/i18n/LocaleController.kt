package com.projectlumen.app.core.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleController {
    const val SYSTEM = "system"
    const val CHINESE = "zh"
    const val ENGLISH = "en"

    fun apply(context: Context, languageCode: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                localeManager.applicationLocales = if (normalize(languageCode) == SYSTEM) {
                    LocaleList.getEmptyLocaleList()
                } else {
                    localeListFor(normalize(languageCode))
                }
            }
        }
    }

    fun wrap(base: Context, languageCode: String): Context {
        val normalized = normalize(languageCode)
        if (normalized == SYSTEM) return base
        return runCatching {
            val configuration = Configuration(base.resources.configuration)
            configuration.setLocales(localeListFor(normalized))
            base.createConfigurationContext(configuration)
        }.getOrElse { base }
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

    private fun localeListFor(languageCode: String): LocaleList {
        val locale = Locale.forLanguageTag(languageCode)
        return if (locale.language.isBlank()) {
            LocaleList.getEmptyLocaleList()
        } else {
            LocaleList(locale)
        }
    }
}
