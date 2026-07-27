package com.jesjobom.ararai.benchmark

import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.GenerationMetrics
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelAccelerationPolicy
import com.jesjobom.ararai.model.ModelConfig
import com.jesjobom.ararai.model.ModelStartupState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
    private val model =
        LocalModel(
            id = "test-model",
            name = "Test Model",
            filePath = "/tmp/test.gguf",
        )

    private val config =
        ModelConfig(
            id = "test-model",
            name = "Test Model",
            url = "https://example.com/test.gguf",
            fileName = "test.gguf",
            relativePath = "models/test.gguf",
            sha256 = "abc",
            expectedBytes = 123L,
            inference =
            InferenceConfig(
                contextTokens = 4096,
                maxTokens = 512,
                temperature = 0.7f,
                topP = 0.9f,
            ),
        )

    @Test
    fun `runs benchmark and records stable metrics`() = runTest {
        val engine =
            RecordingEngine(
                events =
                listOf(
                    GenerationEvent.Token("hello"),
                    GenerationEvent.Token(" world"),
                    GenerationEvent.Metrics(
                        GenerationMetrics(
                            timeToFirstTokenMillis = 40,
                            prefillTokenCount = 12,
                            prefillTokensPerSecond = 120.0,
                            decodeTokenCount = 2,
                            decodeTokensPerSecond = 20.0,
                        ),
                    ),
                    GenerationEvent.Completed,
                ),
            )
        val viewModel =
            BenchmarkViewModel(
                engine = engine,
                initialConfig = config,
                initialState = ModelStartupState.Available(model, config.inference),
                clock =
                SequenceClock(
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
        assertEquals(12, result!!.prefillTokens)
        assertEquals(120.0, result.prefillTokensPerSecond!!, 0.001)
        assertEquals(2, result.decodeTokens)
        assertEquals(20.0, result.decodeTokensPerSecond!!, 0.001)
        assertEquals(11, result.generatedCharacters)
        assertEquals(2, result.streamedChunks)
        assertEquals(100L, result.loadMillis)
        assertEquals(40L, result.firstTokenMillis)
        assertEquals(250L, result.generationMillis)
        assertEquals(350L, result.totalMillis)
        assertEquals(44.0, result.charactersPerSecond, 0.001)
        assertEquals(1, engine.loadCalls)
        assertEquals(1, engine.generateCalls)
        assertEquals(1, engine.unloadCalls)
        assertEquals(2048, engine.loadedConfig?.contextTokens)
        assertEquals(128, engine.loadedConfig?.maxTokens)
        assertEquals(0.2f, engine.loadedConfig?.temperature)
        assertEquals(0.9f, engine.loadedConfig?.topP)
    }

    @Test
    fun `does not run benchmark until selected model is available`() = runTest {
        val engine = RecordingEngine()
        val viewModel =
            BenchmarkViewModel(
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
    fun `does not label streamed chunks as tokens when runtime metrics are unavailable`() = runTest {
        val engine =
            RecordingEngine(
                events =
                listOf(
                    GenerationEvent.Token("one chunk"),
                    GenerationEvent.Completed,
                ),
            )
        val viewModel =
            BenchmarkViewModel(
                engine = engine,
                initialConfig = config,
                initialState = ModelStartupState.Available(model, config.inference),
                clock =
                SequenceClock(
                    0L,
                    100_000_000L,
                    100_000_000L,
                    150_000_000L,
                    300_000_000L,
                ),
                scope = this,
            )

        viewModel.runBenchmark()
        runCurrent()

        val result = viewModel.uiState.value.result!!
        assertEquals(null, result.prefillTokens)
        assertEquals(null, result.decodeTokens)
        assertEquals(1, result.streamedChunks)
        assertEquals(9, result.generatedCharacters)
        assertEquals(45.0, result.charactersPerSecond, 0.001)
    }

    @Test
    fun `uses selected model runtime label`() = runTest {
        val viewModel =
            BenchmarkViewModel(
                engine = RecordingEngine(),
                initialConfig = config,
                initialState = ModelStartupState.Available(model, config.inference),
                scope = this,
            )

        assertEquals("LiteRT-LM GPU preferred", viewModel.uiState.value.backendLabel)
    }

    @Test
    fun `uses selected model acceleration in runtime label`() = runTest {
        val cpuModel = model.copy(acceleration = ModelAccelerationPolicy.CpuOnly)
        val viewModel =
            BenchmarkViewModel(
                engine = RecordingEngine(),
                initialConfig = config.copy(acceleration = ModelAccelerationPolicy.GpuPreferred),
                initialState = ModelStartupState.Available(cpuModel, config.inference),
                scope = this,
            )

        assertEquals("LiteRT-LM CPU only", viewModel.uiState.value.backendLabel)
    }

    @Test
    fun `surfaces generation failure`() = runTest {
        val engine =
            RecordingEngine(
                events = listOf(GenerationEvent.Failed("decode failed")),
            )
        val viewModel =
            BenchmarkViewModel(
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

    @Test
    fun `cancel benchmark stops active run and unloads engine`() = runTest {
        val engine = SlowEngine()
        val viewModel =
            BenchmarkViewModel(
                engine = engine,
                initialConfig = config,
                initialState = ModelStartupState.Available(model, config.inference),
                scope = this,
            )

        viewModel.runBenchmark()
        runCurrent()

        viewModel.cancelBenchmark()
        runCurrent()

        assertEquals("Benchmark canceled", viewModel.uiState.value.status)
        assertFalse(viewModel.uiState.value.isRunning)
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

        override suspend fun load(
            model: LocalModel,
            config: InferenceConfig,
        ) {
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

    private class SlowEngine : LocalLlmEngine {
        var unloadCalls = 0
            private set

        override suspend fun load(
            model: LocalModel,
            config: InferenceConfig,
        ) = Unit

        override fun generate(request: PromptRequest): Flow<GenerationEvent> = flow {
            emit(GenerationEvent.Token("partial"))
            kotlinx.coroutines.delay(Long.MAX_VALUE)
        }

        override suspend fun unload() {
            unloadCalls += 1
        }
    }
}
