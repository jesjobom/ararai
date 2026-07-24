package com.jesjobom.ararai.chat

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ChatPreferences {
    val showAudioTranscriptions: StateFlow<Boolean>
    fun setShowAudioTranscriptions(show: Boolean)
}

class InMemoryChatPreferences(initialShowAudioTranscriptions: Boolean = true) : ChatPreferences {
    private val mutableShowAudioTranscriptions = MutableStateFlow(initialShowAudioTranscriptions)
    override val showAudioTranscriptions = mutableShowAudioTranscriptions.asStateFlow()
    override fun setShowAudioTranscriptions(show: Boolean) {
        mutableShowAudioTranscriptions.value = show
    }
}

class SharedPreferencesChatPreferences(context: Context) : ChatPreferences {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableShowAudioTranscriptions =
        MutableStateFlow(preferences.getBoolean(KEY_SHOW_AUDIO_TRANSCRIPTIONS, true))
    override val showAudioTranscriptions = mutableShowAudioTranscriptions.asStateFlow()

    override fun setShowAudioTranscriptions(show: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_AUDIO_TRANSCRIPTIONS, show).apply()
        mutableShowAudioTranscriptions.value = show
    }

    private companion object {
        const val PREFERENCES_NAME = "chat_preferences"
        const val KEY_SHOW_AUDIO_TRANSCRIPTIONS = "show_audio_transcriptions"
    }
}
