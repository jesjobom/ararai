package com.jesjobom.ararai.chat

import app.cash.turbine.test
import com.jesjobom.ararai.engine.FakeLocalLlmEngine
import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelStartupState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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
    fun `keeps submit disabled for blank prompt even when model is available`() {
        val viewModel = ChatViewModel(
            engine = FakeLocalLlmEngine(chunks = listOf("ignored")),
            initialModel = model,
            inferenceConfig = inferenceConfig,
        )

        viewModel.onPromptChanged("   ")

        assertFalse(viewModel.uiState.value.canSubmit)
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
            assertFalse(loading.canSubmit)

            val loaded = awaitItem()
            assertFalse(loaded.isLoadingModel)
            assertTrue(loaded.isGenerating)

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

    @Test
    fun `surfaces generation failure and preserves messages`() = runTest {
        val viewModel = ChatViewModel(
            engine = FailingEngine("generation failed"),
            initialModel = model,
            inferenceConfig = inferenceConfig,
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.onPromptChanged("oi")
            awaitItem()
            viewModel.submitPrompt()

            val loading = awaitItem()
            assertEquals(2, loading.messages.size)
            assertTrue(loading.isGenerating)

            val loaded = awaitItem()
            assertFalse(loaded.isLoadingModel)
            assertTrue(loaded.isGenerating)

            val failed = awaitItem()
            assertFalse(failed.isGenerating)
            assertEquals("generation failed", failed.error)
            assertEquals("oi", failed.messages.first().text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `surfaces load failure and preserves submitted message`() = runTest {
        val viewModel = ChatViewModel(
            engine = LoadFailingEngine("load failed"),
            initialModel = model,
            inferenceConfig = inferenceConfig,
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.onPromptChanged("oi")
            awaitItem()
            viewModel.submitPrompt()

            val loading = awaitItem()
            assertTrue(loading.isLoadingModel)
            assertFalse(loading.canSubmit)

            val failed = awaitItem()
            assertFalse(failed.isLoadingModel)
            assertFalse(failed.isGenerating)
            assertEquals("load failed", failed.error)
            assertEquals("oi", failed.messages.first().text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `blocks second submit while first generation is active`() = runTest {
        val engine = SlowEngine()
        val viewModel = ChatViewModel(
            engine = engine,
            initialModel = model,
            inferenceConfig = inferenceConfig,
            scope = this,
        )

        viewModel.onPromptChanged("first")
        viewModel.submitPrompt()
        viewModel.onPromptChanged("second")
        viewModel.submitPrompt()
        runCurrent()

        assertEquals(1, engine.generateCalls)
        assertEquals(2, viewModel.uiState.value.messages.size)
        assertFalse(viewModel.uiState.value.canSubmit)

        viewModel.onLeavingChat()
        runCurrent()
    }

    @Test
    fun `leaving chat cancels active generation and unloads engine`() = runTest {
        val engine = SlowEngine()
        val viewModel = ChatViewModel(
            engine = engine,
            initialModel = model,
            inferenceConfig = inferenceConfig,
            scope = this,
        )

        viewModel.onPromptChanged("hello")
        viewModel.submitPrompt()
        runCurrent()

        viewModel.onLeavingChat()
        runCurrent()

        assertTrue(engine.unloadCalls > 0)
        assertFalse(viewModel.uiState.value.isGenerating)
        assertFalse(viewModel.uiState.value.isLoadingModel)
    }

    @Test
    fun `cancel generation stops active job and unloads engine`() = runTest {
        val engine = SlowEngine()
        val viewModel = ChatViewModel(
            engine = engine,
            initialModel = model,
            inferenceConfig = inferenceConfig,
            scope = this,
        )

        viewModel.onPromptChanged("hello")
        viewModel.submitPrompt()
        runCurrent()

        viewModel.cancelGeneration()
        runCurrent()

        assertTrue(engine.unloadCalls > 0)
        assertFalse(viewModel.uiState.value.isGenerating)
        assertFalse(viewModel.uiState.value.isLoadingModel)
        assertEquals("partial", viewModel.uiState.value.messages.last().text)
    }


    @Test
    fun `unloads engine when model becomes unavailable`() = runTest {
        val engine = SlowEngine()
        val viewModel = ChatViewModel(
            engine = engine,
            initialModel = model,
            inferenceConfig = inferenceConfig,
            scope = this,
        )

        viewModel.onModelStartupState(ModelStartupState.Missing)
        runCurrent()

        assertEquals(1, engine.unloadCalls)
        assertFalse(viewModel.uiState.value.canSubmit)
    }

    private class FailingEngine(
        private val message: String,
    ) : LocalLlmEngine {
        override suspend fun load(model: LocalModel, config: InferenceConfig) = Unit

        override fun generate(request: PromptRequest): Flow<GenerationEvent> =
            flowOf(GenerationEvent.Failed(message))

        override suspend fun unload() = Unit
    }

    private class LoadFailingEngine(
        private val message: String,
    ) : LocalLlmEngine {
        override suspend fun load(model: LocalModel, config: InferenceConfig) {
            error(message)
        }

        override fun generate(request: PromptRequest): Flow<GenerationEvent> =
            flowOf(GenerationEvent.Token("unexpected"))

        override suspend fun unload() = Unit
    }

    private class SlowEngine : LocalLlmEngine {
        var generateCalls = 0
            private set
        var unloadCalls = 0
            private set

        override suspend fun load(model: LocalModel, config: InferenceConfig) = Unit

        override fun generate(request: PromptRequest): Flow<GenerationEvent> = flow {
            generateCalls += 1
            emit(GenerationEvent.Token("partial"))
            kotlinx.coroutines.delay(Long.MAX_VALUE)
        }

        override suspend fun unload() {
            unloadCalls += 1
        }
    }
}
