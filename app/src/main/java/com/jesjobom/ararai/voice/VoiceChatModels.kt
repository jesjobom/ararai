package com.jesjobom.ararai.voice

import com.jesjobom.ararai.chat.ChatSessionUiState
import com.jesjobom.ararai.knowledge.KnowledgeSource

enum class VoiceChatPhase { Idle, Listening, Processing, Speaking, Error }

enum class VadProvider { WebRtc, Silero }

enum class VadMode { Normal, Aggressive, VeryAggressive }

enum class VoiceCaptureSource { Microphone, VoiceRecognition, VoiceCommunication }

data class VoiceChatSettings(
    val reasoningEnabled: Boolean = false,
    val pauseMillis: Int = DEFAULT_PAUSE_MILLIS,
    val minimumWords: Int = DEFAULT_MINIMUM_WORDS,
    val speechRateMultiplier: Float = DEFAULT_SPEECH_RATE,
    val vadProvider: VadProvider = VadProvider.Silero,
    val vadMode: VadMode = VadMode.Aggressive,
    val speechConfirmationMillis: Int = DEFAULT_SPEECH_CONFIRMATION_MILLIS,
    val preRollMillis: Int = DEFAULT_PRE_ROLL_MILLIS,
    val minimumSpeechMillis: Int = DEFAULT_MINIMUM_SPEECH_MILLIS,
    val captureSource: VoiceCaptureSource = VoiceCaptureSource.VoiceRecognition,
    val noiseSuppressionRequested: Boolean = true,
) {
    companion object {
        const val DEFAULT_PAUSE_MILLIS = 1_500
        const val DEFAULT_MINIMUM_WORDS = 25
        const val MIN_PAUSE_MILLIS = 500
        const val MAX_PAUSE_MILLIS = 5_000
        const val PAUSE_STEP_MILLIS = 250
        const val MIN_WORDS = 1
        const val MAX_WORDS = 100
        const val DEFAULT_SPEECH_RATE = 1.0f
        const val MIN_SPEECH_RATE = 0.5f
        const val MAX_SPEECH_RATE = 2.0f
        const val SPEECH_RATE_STEP = 0.1f
        const val DEFAULT_SPEECH_CONFIRMATION_MILLIS = 300
        const val DEFAULT_PRE_ROLL_MILLIS = 300
        const val DEFAULT_MINIMUM_SPEECH_MILLIS = 500
        const val MIN_SPEECH_CONFIRMATION_MILLIS = 100
        const val MAX_SPEECH_CONFIRMATION_MILLIS = 1_000
        const val MIN_PRE_ROLL_MILLIS = 0
        const val MAX_PRE_ROLL_MILLIS = 1_000
        const val MIN_SPEECH_MILLIS = 100
        const val MAX_SPEECH_MILLIS = 2_000
        const val AUDIO_TIMING_STEP_MILLIS = 100
    }
}

data class VoiceDiagnostic(
    val turn: Int,
    val vadProvider: VadProvider,
    val captureSource: VoiceCaptureSource,
    val noiseSuppressionActive: Boolean,
    val speechMillis: Long,
    val inferenceToFirstTokenMillis: Long? = null,
    val inferenceToFirstSpeechMillis: Long? = null,
    val outcome: String,
)

data class VoiceChatUiState(
    val phase: VoiceChatPhase = VoiceChatPhase.Idle,
    val modelAvailable: Boolean = false,
    val modelSupportsAudio: Boolean = false,
    val canEnableReasoning: Boolean = false,
    val transcriptionAvailable: Boolean = false,
    val isLoadingModel: Boolean = false,
    val isModelLoaded: Boolean = false,
    val settings: VoiceChatSettings = VoiceChatSettings(),
    val diagnostics: List<VoiceDiagnostic> = emptyList(),
    val responsePreview: String = "",
    val researchInProgress: Boolean = false,
    val researchSources: List<KnowledgeSource> = emptyList(),
    val spokenRange: IntRange? = null,
    val readingAnchor: Int = 0,
    val error: String? = null,
    val sessions: List<ChatSessionUiState> = emptyList(),
    val selectedSessionId: String? = null,
) {
    val canStart: Boolean get() =
        modelAvailable &&
            (modelSupportsAudio || transcriptionAvailable) &&
            isModelLoaded &&
            phase == VoiceChatPhase.Idle
    val isActive: Boolean get() = phase != VoiceChatPhase.Idle && phase != VoiceChatPhase.Error
    val canDeleteCurrentSession: Boolean get() = selectedSessionId != null && sessions.size > 1
}
