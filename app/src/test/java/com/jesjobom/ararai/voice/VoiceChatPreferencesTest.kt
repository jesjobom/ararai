package com.jesjobom.ararai.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceChatPreferencesTest {
    @Test fun `invalid settings use defaults`() {
        val preferences = InMemoryVoiceChatPreferences(
            VoiceChatSettings(
                pauseMillis = 777,
                minimumWords = 0,
                speechRateMultiplier = 3.0f,
                speechConfirmationMillis = 350,
                preRollMillis = -1,
                minimumSpeechMillis = 2_100,
            ),
        )
        assertEquals(VoiceChatSettings.DEFAULT_PAUSE_MILLIS, preferences.settings.value.pauseMillis)
        assertEquals(VoiceChatSettings.DEFAULT_MINIMUM_WORDS, preferences.settings.value.minimumWords)
        assertEquals(VoiceChatSettings.DEFAULT_SPEECH_RATE, preferences.settings.value.speechRateMultiplier, 0.001f)
        assertEquals(
            VoiceChatSettings.DEFAULT_SPEECH_CONFIRMATION_MILLIS,
            preferences.settings.value.speechConfirmationMillis,
        )
        assertEquals(VoiceChatSettings.DEFAULT_PRE_ROLL_MILLIS, preferences.settings.value.preRollMillis)
        assertEquals(VoiceChatSettings.DEFAULT_MINIMUM_SPEECH_MILLIS, preferences.settings.value.minimumSpeechMillis)
    }

    @Test fun `supported settings are retained`() {
        val preferences = InMemoryVoiceChatPreferences()
        preferences.update(
            VoiceChatSettings(
                reasoningEnabled = true,
                pauseMillis = 2_000,
                minimumWords = 8,
                speechRateMultiplier = 1.7f,
                vadProvider = VadProvider.WebRtc,
                vadMode = VadMode.VeryAggressive,
                speechConfirmationMillis = 400,
                preRollMillis = 200,
                minimumSpeechMillis = 700,
            ),
        )
        assertEquals(true, preferences.settings.value.reasoningEnabled)
        assertEquals(2_000, preferences.settings.value.pauseMillis)
        assertEquals(8, preferences.settings.value.minimumWords)
        assertEquals(1.7f, preferences.settings.value.speechRateMultiplier, 0.001f)
        assertEquals(VadProvider.WebRtc, preferences.settings.value.vadProvider)
        assertEquals(VadMode.VeryAggressive, preferences.settings.value.vadMode)
        assertEquals(400, preferences.settings.value.speechConfirmationMillis)
        assertEquals(200, preferences.settings.value.preRollMillis)
        assertEquals(700, preferences.settings.value.minimumSpeechMillis)
    }

    @Test fun `legacy speech rate is snapped to the new slider scale`() {
        val preferences = InMemoryVoiceChatPreferences(VoiceChatSettings(speechRateMultiplier = 0.65f))

        assertEquals(0.7f, preferences.settings.value.speechRateMultiplier, 0.001f)
    }
}
