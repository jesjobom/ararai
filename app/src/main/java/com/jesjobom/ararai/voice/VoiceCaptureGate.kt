package com.jesjobom.ararai.voice

internal enum class VoiceCaptureDecision { Continue, Commit, Finish, Reset }

internal class VoiceCaptureGate(
    private val settings: VoiceChatSettings,
    private val frameMillis: Long,
) {
    var voicedMillis: Long = 0
        private set

    private var consecutiveSpeechMillis = 0L
    private var silenceMillis = 0L
    private var speechConfirmed = false
    private var committed = false

    fun accept(speech: Boolean): VoiceCaptureDecision {
        if (speech) {
            consecutiveSpeechMillis += frameMillis
            voicedMillis += frameMillis
            speechConfirmed = speechConfirmed || consecutiveSpeechMillis >= settings.speechConfirmationMillis
            silenceMillis = 0
        } else {
            consecutiveSpeechMillis = 0
            if (speechConfirmed) {
                silenceMillis += frameMillis
            } else {
                voicedMillis = 0
            }
        }

        val decision = when {
            !committed && speechConfirmed && voicedMillis >= settings.minimumSpeechMillis -> {
                committed = true
                VoiceCaptureDecision.Commit
            }
            speechConfirmed && silenceMillis >= settings.pauseMillis && committed -> VoiceCaptureDecision.Finish
            speechConfirmed && silenceMillis >= settings.pauseMillis -> {
                reset()
                VoiceCaptureDecision.Reset
            }
            else -> VoiceCaptureDecision.Continue
        }
        return decision
    }

    fun resetSilenceWindow() {
        silenceMillis = 0L
        consecutiveSpeechMillis = 0L
    }

    private fun reset() {
        voicedMillis = 0
        consecutiveSpeechMillis = 0
        silenceMillis = 0
        speechConfirmed = false
    }
}
