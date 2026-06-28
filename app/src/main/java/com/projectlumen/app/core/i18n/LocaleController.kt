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
                LocaleList.forLanguageTags(languageCode)
            }
        }
    }

    fun wrap(base: Context, languageCode: String): Context {
        if (languageCode == SYSTEM) return base
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocales(LocaleList(locale))
        return base.createConfigurationContext(configuration)
    }
}
