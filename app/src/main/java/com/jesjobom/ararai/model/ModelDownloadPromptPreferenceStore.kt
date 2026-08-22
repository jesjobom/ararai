package com.jesjobom.ararai.model

import android.content.Context

interface ModelDownloadPromptPreferenceStore {
    val wasHandled: Boolean

    fun markHandled()
}

class InMemoryModelDownloadPromptPreferenceStore(
    initialWasHandled: Boolean = false,
) : ModelDownloadPromptPreferenceStore {
    override var wasHandled: Boolean = initialWasHandled
        private set

    override fun markHandled() {
        wasHandled = true
    }
}

class SharedPreferencesModelDownloadPromptPreferenceStore(
    context: Context,
) : ModelDownloadPromptPreferenceStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override val wasHandled: Boolean
        get() = preferences.getBoolean(KEY_WAS_HANDLED, false)

    override fun markHandled() {
        preferences.edit().putBoolean(KEY_WAS_HANDLED, true).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "ararai_preferences"
        const val KEY_WAS_HANDLED = "initial_model_download_prompt_handled"
    }
}
