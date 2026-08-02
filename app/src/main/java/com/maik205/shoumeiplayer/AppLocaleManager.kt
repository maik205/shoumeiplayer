package com.maik205.shoumeiplayer

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
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

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun applyModern(context: Context, language: DisplayLanguage) {
        val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
        val requested = if (language == DisplayLanguage.SystemDefault) {
            LocaleList.getEmptyLocaleList()
        } else {
            LocaleList.forLanguageTags(language.storageId)
        }
        if (localeManager.applicationLocales != requested) {
            localeManager.applicationLocales = requested
        }
    }

    @Suppress("DEPRECATION")
    private fun applyLegacy(context: Context, language: DisplayLanguage) {
        // There is no per-app locale override below API 33, so "system default" means restoring
        // the device's own configuration locale rather than forcing a specific language.
        val locale = if (language == DisplayLanguage.SystemDefault) {
            Resources.getSystem().configuration.locales[0]
        } else {
            Locale.forLanguageTag(language.storageId)
        }
        val current = context.resources.configuration.locales[0]
        if (current.language == locale.language) return

        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
        (context as? Activity)?.recreate()
    }
}
