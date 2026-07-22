package com.jesjobom.ararai.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceChatUiStateTest {
    @Test
    fun `start remains disabled while audio model is loading`() {
        val state =
            VoiceChatUiState(
                modelAvailable = true,
                modelSupportsAudio = true,
                isLoadingModel = true,
            )

        assertFalse(state.canStart)
    }

    @Test
    fun `start becomes enabled after audio model finishes loading`() {
        val state =
            VoiceChatUiState(
                modelAvailable = true,
                modelSupportsAudio = true,
                isModelLoaded = true,
            )

        assertTrue(state.canStart)
    }

    @Test
    fun `loaded model without audio support cannot start voice chat`() {
        val state =
            VoiceChatUiState(
                modelAvailable = true,
                modelSupportsAudio = false,
                isModelLoaded = true,
            )

        assertFalse(state.canStart)
    }
}
