package com.jesjobom.ararai.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TranscriptionLanguage(val fixedLanguageTag: String? = null) {
    Automatic,
    System,
    Interface,
    English("en"),
    Portuguese("pt"),
}

interface TranscriptionLanguagePreferences {
    val language: StateFlow<TranscriptionLanguage>

    fun setLanguage(language: TranscriptionLanguage)
}

open class InMemoryTranscriptionLanguagePreferences(
    initialLanguage: TranscriptionLanguage = TranscriptionLanguage.Automatic,
) : TranscriptionLanguagePreferences {
    private val mutableLanguage = MutableStateFlow(initialLanguage)
    override val language: StateFlow<TranscriptionLanguage> = mutableLanguage.asStateFlow()

    override fun setLanguage(language: TranscriptionLanguage) {
        mutableLanguage.value = language
        persist(language)
    }

    protected open fun persist(language: TranscriptionLanguage) = Unit
}

class SharedPreferencesTranscriptionLanguagePreferences(
    context: Context,
) : InMemoryTranscriptionLanguagePreferences(loadInitial(context)) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun persist(language: TranscriptionLanguage) {
        preferences.edit().putString(KEY_LANGUAGE, language.name).apply()
    }

    companion object {
        const val PREFERENCES_NAME = "transcription_preferences"
        const val KEY_LANGUAGE = "language"

        @Suppress("MaxLineLength")
        fun decodeLanguage(value: String?): TranscriptionLanguage = TranscriptionLanguage.entries.firstOrNull { it.name == value }
            ?: TranscriptionLanguage.Automatic

        private fun loadInitial(context: Context): TranscriptionLanguage = decodeLanguage(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, null),
        )
    }
}

fun TranscriptionLanguage.resolveLanguageTag(
    systemLanguageTag: () -> String,
    interfaceLanguageTag: () -> String,
): String = when (this) {
    TranscriptionLanguage.Automatic -> "auto"
    TranscriptionLanguage.System -> systemLanguageTag()
    TranscriptionLanguage.Interface -> interfaceLanguageTag()
    TranscriptionLanguage.English,
    TranscriptionLanguage.Portuguese,
    -> requireNotNull(fixedLanguageTag)
}
