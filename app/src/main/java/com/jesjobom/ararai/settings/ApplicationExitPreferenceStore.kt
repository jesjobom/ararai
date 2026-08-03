package com.jesjobom.ararai.settings

import android.content.Context

interface ApplicationExitPreferenceStore {
    val shouldConfirmExit: Boolean

    fun disableExitConfirmation()
}

class SharedPreferencesApplicationExitPreferenceStore(
    context: Context,
) : ApplicationExitPreferenceStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override val shouldConfirmExit: Boolean
        get() = preferences.getBoolean(KEY_CONFIRM_EXIT, true)

    override fun disableExitConfirmation() {
        preferences.edit().putBoolean(KEY_CONFIRM_EXIT, false).apply()
    }

    companion object {
        const val PREFERENCES_NAME = "ararai_preferences"
        const val KEY_CONFIRM_EXIT = "confirm_application_exit"
    }
}
