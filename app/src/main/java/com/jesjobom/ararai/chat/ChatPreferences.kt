package com.jesjobom.ararai.chat

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ChatPreferences {
    val showAudioTranscriptions: StateFlow<Boolean>
    val reasoningEnabled: StateFlow<Boolean>
    val showReasoning: StateFlow<Boolean>
    fun setShowAudioTranscriptions(show: Boolean)
    fun setReasoningEnabled(enabled: Boolean)
    fun setShowReasoning(show: Boolean)
}

class InMemoryChatPreferences(
    initialShowAudioTranscriptions: Boolean = true,
    initialReasoningEnabled: Boolean = false,
    initialShowReasoning: Boolean = false,
) : ChatPreferences {
    private val mutableShowAudioTranscriptions = MutableStateFlow(initialShowAudioTranscriptions)
    private val mutableReasoningEnabled = MutableStateFlow(initialReasoningEnabled)
    private val mutableShowReasoning = MutableStateFlow(initialShowReasoning)
    override val showAudioTranscriptions = mutableShowAudioTranscriptions.asStateFlow()
    override val reasoningEnabled = mutableReasoningEnabled.asStateFlow()
    override val showReasoning = mutableShowReasoning.asStateFlow()

    override fun setShowAudioTranscriptions(show: Boolean) {
        mutableShowAudioTranscriptions.value = show
    }

    override fun setReasoningEnabled(enabled: Boolean) {
        mutableReasoningEnabled.value = enabled
    }

    override fun setShowReasoning(show: Boolean) {
        mutableShowReasoning.value = show
    }
}

class SharedPreferencesChatPreferences(context: Context) : ChatPreferences {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableShowAudioTranscriptions =
        MutableStateFlow(preferences.getBoolean(KEY_SHOW_AUDIO_TRANSCRIPTIONS, true))
    private val mutableReasoningEnabled =
        MutableStateFlow(preferences.getBoolean(KEY_REASONING_ENABLED, false))
    private val mutableShowReasoning =
        MutableStateFlow(preferences.getBoolean(KEY_SHOW_REASONING, false))
    override val showAudioTranscriptions = mutableShowAudioTranscriptions.asStateFlow()
    override val reasoningEnabled = mutableReasoningEnabled.asStateFlow()
    override val showReasoning = mutableShowReasoning.asStateFlow()

    override fun setShowAudioTranscriptions(show: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_AUDIO_TRANSCRIPTIONS, show).apply()
        mutableShowAudioTranscriptions.value = show
    }

    override fun setReasoningEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_REASONING_ENABLED, enabled).apply()
        mutableReasoningEnabled.value = enabled
    }

    override fun setShowReasoning(show: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_REASONING, show).apply()
        mutableShowReasoning.value = show
    }

    private companion object {
        const val PREFERENCES_NAME = "chat_preferences"
        const val KEY_SHOW_AUDIO_TRANSCRIPTIONS = "show_audio_transcriptions"
        const val KEY_REASONING_ENABLED = "reasoning_enabled"
        const val KEY_SHOW_REASONING = "show_reasoning"
    }
}
