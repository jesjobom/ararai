package com.jesjobom.ararai.ui

import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelConfig
import com.jesjobom.ararai.model.ModelStartupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelStatusUiStateTest {
    @Test
    fun `formats known progress percent and detail`() {
        val state = ModelStatusUiState.from(
            config = config(),
            startupState = ModelStartupState.Downloading(bytesDownloaded = 50, totalBytes = 100),
        )

        assertEquals("Downloading model", state.title)
        assertEquals(50, state.progressPercent)
        assertEquals("50 B / 100 B", state.detail)
        assertFalse(state.canRetry)
    }

    @Test
    fun `retry is available only on failed state`() {
        val failed = ModelStatusUiState.from(
            config = config(),
            startupState = ModelStartupState.Failed("network down"),
        )
        val downloading = ModelStatusUiState.from(
            config = config(),
            startupState = ModelStartupState.Downloading(),
        )

        assertTrue(failed.canRetry)
        assertFalse(downloading.canRetry)
    }

    @Test
    fun `available state shows ready model`() {
        val state = ModelStatusUiState.from(
            config = config(),
            startupState = ModelStartupState.Available(
                model = LocalModel(id = "model", name = "Ready Model", filePath = "/tmp/model.gguf"),
                inference = config().inference,
            ),
        )

        assertEquals("Model ready", state.title)
        assertEquals("Ready Model", state.modelName)
        assertNull(state.progressPercent)
    }

    private fun config(): ModelConfig =
        ModelConfig(
            id = "model",
            name = "Configured Model",
            url = "https://example.com/model.gguf",
            fileName = "model.gguf",
            relativePath = "models/model.gguf",
            sha256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            expectedBytes = 100,
            inference = InferenceConfig(contextTokens = 128, temperature = 0.7f, topP = 0.9f),
        )
}
