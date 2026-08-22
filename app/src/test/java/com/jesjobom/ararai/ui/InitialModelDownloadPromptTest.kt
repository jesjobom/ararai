package com.jesjobom.ararai.ui

import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ManagedModelItem
import com.jesjobom.ararai.model.ModelConfig
import com.jesjobom.ararai.model.ModelPurpose
import com.jesjobom.ararai.model.ModelStartupState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialModelDownloadPromptTest {
    @Test
    fun `shows only when prompt is unhandled and no chat model is available`() {
        assertTrue(
            shouldShowInitialModelDownloadPrompt(
                wasHandled = false,
                hasAvailableChatModel = false,
            ),
        )
        assertFalse(
            shouldShowInitialModelDownloadPrompt(
                wasHandled = true,
                hasAvailableChatModel = false,
            ),
        )
        assertFalse(
            shouldShowInitialModelDownloadPrompt(
                wasHandled = false,
                hasAvailableChatModel = true,
            ),
        )
    }

    @Test
    fun `chat availability ignores transcription artifacts and accepts any valid chat model`() {
        val inference = InferenceConfig(contextTokens = 128, temperature = 0.7f, topP = 0.9f)
        val missingSelectedChat = configuredItem("selected", setOf(ModelPurpose.Chat), ModelStartupState.Missing)
        val availableTranscription =
            configuredItem(
                "whisper",
                setOf(ModelPurpose.Utility),
                ModelStartupState.Available(
                    LocalModel(id = "whisper", name = "whisper", filePath = "/whisper"),
                    inference = null,
                ),
            )
        val availableOtherChat =
            configuredItem(
                "other",
                setOf(ModelPurpose.Chat),
                ModelStartupState.Available(
                    LocalModel(id = "other", name = "other", filePath = "/other"),
                    inference = inference,
                ),
                inference = inference,
            )

        assertFalse(listOf(missingSelectedChat, availableTranscription).hasAvailableChatModel())
        assertTrue(listOf(missingSelectedChat, availableTranscription, availableOtherChat).hasAvailableChatModel())
    }

    private fun configuredItem(
        id: String,
        purposes: Set<ModelPurpose>,
        state: ModelStartupState,
        inference: InferenceConfig? = null,
    ): ManagedModelItem = ManagedModelItem(
        config =
        ModelConfig(
            id = id,
            name = id,
            url = "https://example.com/$id",
            fileName = "$id.bin",
            relativePath = "models/$id.bin",
            sha256 = "a".repeat(64),
            expectedBytes = 1,
            purposes = purposes,
            inference = inference,
        ),
        state = state,
    )
}
