@file:Suppress("MaxLineLength")

package com.jesjobom.ararai.voice

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

interface VoiceChatPreferences {
    val settings: StateFlow<VoiceChatSettings>
    fun update(settings: VoiceChatSettings)
}

class InMemoryVoiceChatPreferences(initial: VoiceChatSettings = VoiceChatSettings()) : VoiceChatPreferences {
    private val mutableSettings = MutableStateFlow(initial.validated())
    override val settings: StateFlow<VoiceChatSettings> = mutableSettings.asStateFlow()
    override fun update(settings: VoiceChatSettings) {
        mutableSettings.value = settings.validated()
    }
}

class SharedPreferencesVoiceChatPreferences(context: Context) : VoiceChatPreferences {
    private val preferences = context.getSharedPreferences("voice_chat_preferences", Context.MODE_PRIVATE)
    private val mutableSettings = MutableStateFlow(read())
    override val settings: StateFlow<VoiceChatSettings> = mutableSettings.asStateFlow()

    override fun update(settings: VoiceChatSettings) {
        val valid = settings.validated()
        preferences.edit()
            .putInt("pause_millis", valid.pauseMillis)
            .putInt("minimum_words", valid.minimumWords)
            .putFloat("speech_rate_multiplier", valid.speechRateMultiplier)
            .remove("speech_rate")
            .putString("vad_provider", valid.vadProvider.name)
            .putString("vad_mode", valid.vadMode.name)
            .putInt("speech_confirmation_millis", valid.speechConfirmationMillis)
            .putInt("pre_roll_millis", valid.preRollMillis)
            .putInt("minimum_speech_millis", valid.minimumSpeechMillis)
            .putString("capture_source", valid.captureSource.name)
            .putBoolean("noise_suppression", valid.noiseSuppressionRequested)
            .apply()
        mutableSettings.value = valid
    }

    private fun read() = VoiceChatSettings(
        pauseMillis = preferences.getInt("pause_millis", VoiceChatSettings.DEFAULT_PAUSE_MILLIS),
        minimumWords = preferences.getInt("minimum_words", VoiceChatSettings.DEFAULT_MINIMUM_WORDS),
        speechRateMultiplier = readSpeechRate(),
        vadProvider = enumValue(preferences.getString("vad_provider", null), VadProvider.Silero),
        vadMode = enumValue(preferences.getString("vad_mode", null), VadMode.Aggressive),
        speechConfirmationMillis = preferences.getInt("speech_confirmation_millis", VoiceChatSettings.DEFAULT_SPEECH_CONFIRMATION_MILLIS),
        preRollMillis = preferences.getInt("pre_roll_millis", VoiceChatSettings.DEFAULT_PRE_ROLL_MILLIS),
        minimumSpeechMillis = preferences.getInt("minimum_speech_millis", VoiceChatSettings.DEFAULT_MINIMUM_SPEECH_MILLIS),
        captureSource = enumValue(preferences.getString("capture_source", null), VoiceCaptureSource.VoiceRecognition),
        noiseSuppressionRequested = preferences.getBoolean("noise_suppression", true),
    ).validated()

    private fun readSpeechRate(): Float {
        if (preferences.contains("speech_rate_multiplier")) {
            return runCatching {
                preferences.getFloat("speech_rate_multiplier", VoiceChatSettings.DEFAULT_SPEECH_RATE)
            }.getOrDefault(VoiceChatSettings.DEFAULT_SPEECH_RATE)
        }
        return when (runCatching { preferences.getString("speech_rate", null) }.getOrNull()) {
            "VerySlow" -> 0.65f
            "Slow" -> 0.85f
            "Fast" -> 1.2f
            "VeryFast" -> 1.4f
            else -> VoiceChatSettings.DEFAULT_SPEECH_RATE
        }
    }
}

internal fun VoiceChatSettings.validated() = copy(
    pauseMillis = pauseMillis.takeIf {
        it in VoiceChatSettings.MIN_PAUSE_MILLIS..VoiceChatSettings.MAX_PAUSE_MILLIS &&
            (it - VoiceChatSettings.MIN_PAUSE_MILLIS) % VoiceChatSettings.PAUSE_STEP_MILLIS == 0
    } ?: VoiceChatSettings.DEFAULT_PAUSE_MILLIS,
    minimumWords = minimumWords.takeIf { it in VoiceChatSettings.MIN_WORDS..VoiceChatSettings.MAX_WORDS }
        ?: VoiceChatSettings.DEFAULT_MINIMUM_WORDS,
    speechRateMultiplier = speechRateMultiplier.validSpeechRate(),
    speechConfirmationMillis = speechConfirmationMillis.validAudioTiming(
        VoiceChatSettings.MIN_SPEECH_CONFIRMATION_MILLIS,
        VoiceChatSettings.MAX_SPEECH_CONFIRMATION_MILLIS,
        VoiceChatSettings.DEFAULT_SPEECH_CONFIRMATION_MILLIS,
    ),
    preRollMillis = preRollMillis.validAudioTiming(
        VoiceChatSettings.MIN_PRE_ROLL_MILLIS,
        VoiceChatSettings.MAX_PRE_ROLL_MILLIS,
        VoiceChatSettings.DEFAULT_PRE_ROLL_MILLIS,
    ),
    minimumSpeechMillis = minimumSpeechMillis.validAudioTiming(
        VoiceChatSettings.MIN_SPEECH_MILLIS,
        VoiceChatSettings.MAX_SPEECH_MILLIS,
        VoiceChatSettings.DEFAULT_MINIMUM_SPEECH_MILLIS,
    ),
)

private fun Float.validSpeechRate(): Float {
    if (!isFinite() || this !in VoiceChatSettings.MIN_SPEECH_RATE..VoiceChatSettings.MAX_SPEECH_RATE) {
        return VoiceChatSettings.DEFAULT_SPEECH_RATE
    }
    return (this * 10f).roundToInt() / 10f
}

private fun Int.validAudioTiming(minimum: Int, maximum: Int, fallback: Int): Int = takeIf { it in minimum..maximum && (it - minimum) % VoiceChatSettings.AUDIO_TIMING_STEP_MILLIS == 0 } ?: fallback

private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T = enumValues<T>().firstOrNull { it.name == value } ?: fallback
