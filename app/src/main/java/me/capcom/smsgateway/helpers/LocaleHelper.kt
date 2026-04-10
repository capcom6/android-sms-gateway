package me.capcom.smsgateway.helpers

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

object LocaleHelper {
    fun onAttach(context: Context): Context {
        val lang = SettingsHelper(context).language
        return updateResources(context, lang)
    }

    fun setLocale(context: Context, language: String): Context {
        SettingsHelper(context).language = language

        return updateResources(context, language)
    }

    private fun updateResources(context: Context, language: String): Context {
        val locale = if (language.isEmpty()) {
            Resources.getSystem().configuration.locale
        } else {
            Locale.forLanguageTag(language)
        }
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)

        return context.createConfigurationContext(configuration)
    }
}
