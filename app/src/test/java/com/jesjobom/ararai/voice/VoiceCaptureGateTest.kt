package com.jesjobom.ararai.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCaptureGateTest {
    private val settings =
        VoiceChatSettings(
            speechConfirmationMillis = 300,
            minimumSpeechMillis = 500,
            pauseMillis = 1_500,
        )

    @Test fun `isolated noise does not commit a turn`() {
        val gate = VoiceCaptureGate(settings, frameMillis = 100)

        repeat(2) { assertEquals(VoiceCaptureDecision.Continue, gate.accept(speech = true)) }
        repeat(20) { assertEquals(VoiceCaptureDecision.Continue, gate.accept(speech = false)) }

        assertEquals(0, gate.voicedMillis)
    }

    @Test fun `confirmed minimum speech commits and trailing pause finishes`() {
        val gate = VoiceCaptureGate(settings, frameMillis = 100)

        repeat(4) { assertEquals(VoiceCaptureDecision.Continue, gate.accept(speech = true)) }
        assertEquals(VoiceCaptureDecision.Commit, gate.accept(speech = true))
        repeat(14) { assertEquals(VoiceCaptureDecision.Continue, gate.accept(speech = false)) }
        assertEquals(VoiceCaptureDecision.Finish, gate.accept(speech = false))
    }

    @Test fun `confirmed but unusable candidate resets after pause`() {
        val gate = VoiceCaptureGate(settings, frameMillis = 100)

        repeat(3) { assertEquals(VoiceCaptureDecision.Continue, gate.accept(speech = true)) }
        repeat(14) { assertEquals(VoiceCaptureDecision.Continue, gate.accept(speech = false)) }
        assertEquals(VoiceCaptureDecision.Reset, gate.accept(speech = false))
        assertEquals(0, gate.voicedMillis)
    }
}
