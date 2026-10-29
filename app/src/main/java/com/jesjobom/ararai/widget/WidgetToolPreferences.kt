package com.jesjobom.ararai.widget

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface WidgetToolPreferences {
    val wikipediaEnabled: StateFlow<Boolean>
    val webSearchEnabled: StateFlow<Boolean>
    fun setWikipediaEnabled(enabled: Boolean)
    fun setWebSearchEnabled(enabled: Boolean)
}

class InMemoryWidgetToolPreferences(
    initialWikipediaEnabled: Boolean = false,
    initialWebSearchEnabled: Boolean = false,
) : WidgetToolPreferences {
    private val mutableWikipediaEnabled = MutableStateFlow(initialWikipediaEnabled)
    private val mutableWebSearchEnabled = MutableStateFlow(initialWebSearchEnabled)
    override val wikipediaEnabled = mutableWikipediaEnabled.asStateFlow()
    override val webSearchEnabled = mutableWebSearchEnabled.asStateFlow()

    override fun setWikipediaEnabled(enabled: Boolean) {
        mutableWikipediaEnabled.value = enabled
    }

    override fun setWebSearchEnabled(enabled: Boolean) {
        mutableWebSearchEnabled.value = enabled
    }
}

class SharedPreferencesWidgetToolPreferences(context: Context) : WidgetToolPreferences {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableWikipediaEnabled: MutableStateFlow<Boolean>
    private val mutableWebSearchEnabled: MutableStateFlow<Boolean>

    init {
        // Snapshot the old shared switch once; future Chat changes must not alter widget availability.
        if (!preferences.contains(KEY_WIDGET_WIKIPEDIA)) {
            preferences.edit()
                .putBoolean(KEY_WIDGET_WIKIPEDIA, preferences.getBoolean(KEY_CHAT_WIKIPEDIA, false))
                .commit()
        }
        mutableWikipediaEnabled = MutableStateFlow(preferences.getBoolean(KEY_WIDGET_WIKIPEDIA, false))
        mutableWebSearchEnabled = MutableStateFlow(preferences.getBoolean(KEY_WIDGET_WEB_SEARCH, false))
    }

    override val wikipediaEnabled: StateFlow<Boolean> get() = mutableWikipediaEnabled.asStateFlow()
    override val webSearchEnabled: StateFlow<Boolean> get() = mutableWebSearchEnabled.asStateFlow()

    override fun setWikipediaEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_WIDGET_WIKIPEDIA, enabled).apply()
        mutableWikipediaEnabled.value = enabled
    }

    override fun setWebSearchEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_WIDGET_WEB_SEARCH, enabled).apply()
        mutableWebSearchEnabled.value = enabled
    }

    private companion object {
        const val PREFERENCES_NAME = "instruction_preferences"
        const val KEY_CHAT_WIKIPEDIA = "wikipedia_enabled"
        const val KEY_WIDGET_WIKIPEDIA = "widget_wikipedia_enabled"
        const val KEY_WIDGET_WEB_SEARCH = "widget_web_search_enabled"
    }
}
