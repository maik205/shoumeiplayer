package com.maik205.shoumeiplayer

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.maik205.shoumeiplayer.domain.settings.DisplayLanguage
import java.util.Locale

/** Applies the language selected in the app settings to the app-owned resources. */
object AppLocaleManager {
    fun apply(context: Context, language: DisplayLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            applyModern(context, language)
        } else {
            applyLegacy(context, language)
        }
    }

    private fun applyModern(context: Context, language: DisplayLanguage) {
        val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
        val requested = LocaleList.forLanguageTags(language.storageId)
        if (localeManager.applicationLocales != requested) {
            localeManager.applicationLocales = requested
        }
    }

    @Suppress("DEPRECATION")
    private fun applyLegacy(context: Context, language: DisplayLanguage) {
        val locale = Locale.forLanguageTag(language.storageId)
        val current = context.resources.configuration.locales[0]
        if (current.language == locale.language) return

        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
        (context as? Activity)?.recreate()
    }
}
