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
            val localeManager = context.getSystemService(LocaleManager::class.java)
            localeManager.applicationLocales = if (languageCode == SYSTEM) {
                LocaleList.getEmptyLocaleList()
            } else {
                localeListFor(languageCode)
            }
        }
    }

    fun wrap(base: Context, languageCode: String): Context {
        if (languageCode == SYSTEM) return base
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocales(localeListFor(languageCode))
        return base.createConfigurationContext(configuration)
    }

    private fun localeListFor(languageCode: String): LocaleList {
        val locale = Locale.forLanguageTag(languageCode)
        return LocaleList(locale)
    }
}
