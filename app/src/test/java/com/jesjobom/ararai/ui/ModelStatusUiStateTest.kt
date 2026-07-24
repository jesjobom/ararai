package com.jesjobom.ararai.ui

import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelAccelerationPolicy
import com.jesjobom.ararai.model.ModelConfig
import com.jesjobom.ararai.model.ModelInputCapabilities
import com.jesjobom.ararai.model.ModelReasoningCapabilities
import com.jesjobom.ararai.model.ModelStartupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelStatusUiStateTest {
    @Test
    fun `formats known progress percent and detail`() {
        val state =
            ModelStatusUiState.from(
                config = config(),
                startupState = ModelStartupState.Downloading(bytesDownloaded = 50, totalBytes = 100),
            )

        assertEquals("Downloading model", state.title)
        assertEquals(50, state.progressPercent)
        assertEquals("50 B / 100 B", state.detail)
        assertFalse(state.canRetry)
    }

    @Test
    fun `omits progress percent until total bytes are known`() {
        val state =
            ModelStatusUiState.from(
                config = config(),
                startupState = ModelStartupState.Downloading(bytesDownloaded = 50, totalBytes = null),
            )

        assertEquals("Downloading model", state.title)
        assertNull(state.progressPercent)
        assertEquals("Waiting for download progress", state.detail)
    }

    @Test
    fun `clamps download progress above one hundred percent`() {
        val state =
            ModelStatusUiState.from(
                config = config(),
                startupState = ModelStartupState.Downloading(bytesDownloaded = 150, totalBytes = 100),
            )

        assertEquals(100, state.progressPercent)
        assertEquals("150 B / 100 B", state.detail)
    }

    @Test
    fun `formats large byte counts in megabytes`() {
        val state =
            ModelStatusUiState.from(
                config = config(),
                startupState =
                ModelStartupState.Downloading(
                    bytesDownloaded = 2L * 1024L * 1024L,
                    totalBytes = 4L * 1024L * 1024L,
                ),
            )

        assertEquals(50, state.progressPercent)
        assertEquals("2.0 MB / 4.0 MB", state.detail)
    }

    @Test
    fun `retry is available only on failed state`() {
        val failed =
            ModelStatusUiState.from(
                config = config(),
                startupState = ModelStartupState.Failed("network down"),
            )
        val downloading =
            ModelStatusUiState.from(
                config = config(),
                startupState = ModelStartupState.Downloading(),
            )

        assertTrue(failed.canRetry)
        assertFalse(downloading.canRetry)
    }

    @Test
    fun `available state shows ready model`() {
        val state =
            ModelStatusUiState.from(
                config = config(),
                startupState =
                ModelStartupState.Available(
                    model = LocalModel(id = "model", name = "Ready Model", filePath = "/tmp/model.gguf"),
                    inference = config().inference,
                ),
            )

        assertEquals("Model ready", state.title)
        assertEquals("Ready Model", state.modelName)
        assertEquals("Available locally", state.detail)
        assertNull(state.progressPercent)
    }

    @Test
    fun `exposes model capabilities as presentation labels`() {
        val config =
            config().copy(
                acceleration = ModelAccelerationPolicy.GpuPreferred,
                inputCapabilities = ModelInputCapabilities(text = true, image = true, audio = true),
                reasoningCapabilities = ModelReasoningCapabilities(request = true, output = true),
            )

        val state = ModelStatusUiState.from(config, ModelStartupState.Missing)

        assertEquals(
            listOf("Stable", "Chat", "Text", "Voice", "Image", "Reasoning", "GPU"),
            state.capabilities,
        )
    }

    private fun config(): ModelConfig = ModelConfig(
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
