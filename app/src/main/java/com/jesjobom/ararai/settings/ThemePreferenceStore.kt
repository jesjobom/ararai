package com.jesjobom.ararai.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { System, Light, Dark }

interface ThemePreferenceStore {
    val themeMode: StateFlow<ThemeMode>

    fun setThemeMode(mode: ThemeMode)
}

class InMemoryThemePreferenceStore(
    initialMode: ThemeMode = ThemeMode.System,
) : ThemePreferenceStore {
    private val mutableThemeMode = MutableStateFlow(initialMode)
    override val themeMode: StateFlow<ThemeMode> = mutableThemeMode.asStateFlow()

    override fun setThemeMode(mode: ThemeMode) {
        mutableThemeMode.value = mode
    }
}

class SharedPreferencesThemePreferenceStore(
    context: Context,
) : ThemePreferenceStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableThemeMode = MutableStateFlow(decodeThemeMode(preferences.getString(KEY_THEME_MODE, null)))
    override val themeMode: StateFlow<ThemeMode> = mutableThemeMode.asStateFlow()

    override fun setThemeMode(mode: ThemeMode) {
        preferences.edit().putString(KEY_THEME_MODE, mode.name).apply()
        mutableThemeMode.value = mode
    }

    internal companion object {
        const val PREFERENCES_NAME = "ararai_preferences"
        const val KEY_THEME_MODE = "theme_mode"

        fun decodeThemeMode(value: String?): ThemeMode = ThemeMode.entries.firstOrNull { it.name == value } ?: ThemeMode.System
    }
}
