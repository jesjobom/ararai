package com.jesjobom.ararai.benchmark

import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelConfig
import com.jesjobom.ararai.model.ModelStartupState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BenchmarkViewModelTest {
    private val model = LocalModel(
        id = "test-model",
        name = "Test Model",
        filePath = "/tmp/test.gguf",
    )

    private val config = ModelConfig(
        id = "test-model",
        name = "Test Model",
        url = "https://example.com/test.gguf",
        fileName = "test.gguf",
        relativePath = "models/test.gguf",
        sha256 = "abc",
        expectedBytes = 123L,
        inference = InferenceConfig(
            contextTokens = 4096,
            maxTokens = 512,
            temperature = 0.7f,
            topP = 0.9f,
        ),
    )

    @Test
    fun `runs benchmark and records stable metrics`() = runTest {
        val engine = RecordingEngine(
            events = listOf(
                GenerationEvent.Token("hello"),
                GenerationEvent.Token(" world"),
                GenerationEvent.Completed,
            ),
        )
        val viewModel = BenchmarkViewModel(
            engine = engine,
            initialConfig = config,
            initialState = ModelStartupState.Available(model, config.inference),
            clock = SequenceClock(
                0L,
                100_000_000L,
                100_000_000L,
                150_000_000L,
                200_000_000L,
                350_000_000L,
            ),
            scope = this,
        )

        viewModel.runBenchmark()
        runCurrent()

        val state = viewModel.uiState.value
        val result = state.result
        assertNotNull(result)
        assertEquals("Benchmark complete", state.status)
        assertFalse(state.isRunning)
        assertEquals(2, result!!.generatedTokens)
        assertEquals(11, result.generatedCharacters)
        assertEquals(100L, result.loadMillis)
        assertEquals(50L, result.firstTokenMillis)
        assertEquals(250L, result.generationMillis)
        assertEquals(350L, result.totalMillis)
        assertEquals(8.0, result.tokensPerSecond, 0.001)
        assertEquals(1, engine.loadCalls)
        assertEquals(1, engine.generateCalls)
        assertEquals(1, engine.unloadCalls)
        assertEquals(2048, engine.loadedConfig?.contextTokens)
        assertEquals(128, engine.loadedConfig?.maxTokens)
    }

    @Test
    fun `does not run benchmark until selected model is available`() = runTest {
        val engine = RecordingEngine()
        val viewModel = BenchmarkViewModel(
            engine = engine,
            initialConfig = config,
            initialState = ModelStartupState.Missing,
            scope = this,
        )

        viewModel.runBenchmark()
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.canRun)
        assertEquals("Selected model must be available locally", state.status)
        assertEquals(0, engine.loadCalls)
        assertEquals(0, engine.generateCalls)
    }

    @Test
    fun `uses gpu default backend label`() = runTest {
        val viewModel = BenchmarkViewModel(
            engine = RecordingEngine(),
            initialConfig = config,
            initialState = ModelStartupState.Available(model, config.inference),
            scope = this,
        )

        assertEquals("llama.cpp Vulkan GPU", viewModel.uiState.value.backendLabel)
    }

    @Test
    fun `surfaces generation failure`() = runTest {
        val engine = RecordingEngine(
            events = listOf(GenerationEvent.Failed("decode failed")),
        )
        val viewModel = BenchmarkViewModel(
            engine = engine,
            initialConfig = config,
            initialState = ModelStartupState.Available(model, config.inference),
            scope = this,
        )

        viewModel.runBenchmark()
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals("Benchmark failed", state.status)
        assertEquals("decode failed", state.error)
        assertFalse(state.isRunning)
        assertEquals(1, engine.unloadCalls)
    }

    private class RecordingEngine(
        private val events: List<GenerationEvent> = emptyList(),
    ) : LocalLlmEngine {
        var loadCalls = 0
            private set
        var generateCalls = 0
            private set
        var unloadCalls = 0
            private set
        var loadedConfig: InferenceConfig? = null
            private set

        override suspend fun load(model: LocalModel, config: InferenceConfig) {
            loadCalls += 1
            loadedConfig = config
        }

        override fun generate(request: PromptRequest): Flow<GenerationEvent> {
            generateCalls += 1
            return flowOf(*events.toTypedArray())
        }

        override suspend fun unload() {
            unloadCalls += 1
        }
    }

    private class SequenceClock(
        vararg values: Long,
    ) : BenchmarkClock {
        private val values = values.toMutableList()

        override fun nowNanos(): Long {
            check(values.isNotEmpty()) { "SequenceClock exhausted" }
            return values.removeAt(0)
        }
    }
}
