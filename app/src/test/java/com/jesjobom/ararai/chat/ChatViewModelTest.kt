package com.jesjobom.ararai.chat

import app.cash.turbine.test
import com.jesjobom.ararai.engine.FakeLocalLlmEngine
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelStartupState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatViewModelTest {
    private val model = LocalModel(
        id = "test-model",
        name = "Test Model",
        filePath = "/tmp/test.gguf",
    )

    private val inferenceConfig = InferenceConfig(
        contextTokens = 128,
        temperature = 0.7f,
        topP = 0.9f,
    )

    @Test
    fun `keeps submit disabled until model is available`() {
        val viewModel = ChatViewModel(
            engine = FakeLocalLlmEngine(chunks = listOf("ignored")),
            initialModel = null,
            inferenceConfig = inferenceConfig,
        )

        viewModel.onPromptChanged("hello")

        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `enables submit after startup state provides available model`() {
        val viewModel = ChatViewModel(
            engine = FakeLocalLlmEngine(chunks = listOf("ignored")),
            initialModel = null,
            inferenceConfig = inferenceConfig,
        )

        viewModel.onModelStartupState(ModelStartupState.Available(model, inferenceConfig))
        viewModel.onPromptChanged("hello")

        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `exposes retry when startup download fails`() {
        val viewModel = ChatViewModel(
            engine = FakeLocalLlmEngine(chunks = listOf("ignored")),
            initialModel = null,
            inferenceConfig = inferenceConfig,
        )

        viewModel.onModelStartupState(ModelStartupState.Failed("network down"))

        assertTrue(viewModel.uiState.value.canRetryModelDownload)
        assertEquals("Download failed: network down", viewModel.uiState.value.modelStatus)
    }

    @Test
    fun `formats model download progress`() {
        val viewModel = ChatViewModel(
            engine = FakeLocalLlmEngine(chunks = listOf("ignored")),
            initialModel = null,
            inferenceConfig = inferenceConfig,
        )

        viewModel.onModelStartupState(
            ModelStartupState.Downloading(bytesDownloaded = 50, totalBytes = 100),
        )

        assertEquals("Downloading configured model: 50%", viewModel.uiState.value.modelStatus)
        assertFalse(viewModel.uiState.value.canRetryModelDownload)
    }

    @Test
    fun `streams fake engine chunks into assistant message`() = runTest {
        val viewModel = ChatViewModel(
            engine = FakeLocalLlmEngine(chunks = listOf("ola", " mundo")),
            initialModel = model,
            inferenceConfig = inferenceConfig,
        )

        viewModel.uiState.test {
            assertTrue(awaitItem().canSubmit.not())
            viewModel.onPromptChanged("oi")
            assertTrue(awaitItem().canSubmit)
            viewModel.submitPrompt()

            val loading = awaitItem()
            assertTrue(loading.isGenerating)

            val firstChunk = awaitItem()
            assertEquals("ola", firstChunk.messages.last().text)

            val secondChunk = awaitItem()
            assertEquals("ola mundo", secondChunk.messages.last().text)

            val completed = awaitItem()
            assertFalse(completed.isGenerating)
            assertEquals("", completed.prompt)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
