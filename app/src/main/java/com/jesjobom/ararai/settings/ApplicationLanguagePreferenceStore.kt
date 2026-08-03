package com.jesjobom.ararai.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

enum class ApplicationLanguage(val languageTag: String?) {
    System(null),
    English("en"),
    PortugueseBrazil("pt-BR"),
}

interface ApplicationLanguagePreferenceStore {
    val language: ApplicationLanguage

    fun setLanguage(language: ApplicationLanguage)
}

class SharedPreferencesApplicationLanguagePreferenceStore(
    context: Context,
) : ApplicationLanguagePreferenceStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override val language: ApplicationLanguage
        get() = decodeLanguage(preferences.getString(KEY_APPLICATION_LANGUAGE, null))

    override fun setLanguage(language: ApplicationLanguage) {
        preferences.edit().putString(KEY_APPLICATION_LANGUAGE, language.name).apply()
    }

    companion object {
        const val PREFERENCES_NAME = "ararai_preferences"
        const val KEY_APPLICATION_LANGUAGE = "application_language"

        fun decodeLanguage(value: String?): ApplicationLanguage = ApplicationLanguage.entries.firstOrNull { it.name == value } ?: ApplicationLanguage.System

        fun localizedContext(base: Context, language: ApplicationLanguage): Context {
            val tag = language.languageTag ?: return base
            val locale = Locale.forLanguageTag(tag)
            val configuration = Configuration(base.resources.configuration).apply {
                setLocale(locale)
                setLayoutDirection(locale)
            }
            return base.createConfigurationContext(configuration)
        }
    }
}
