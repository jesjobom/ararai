package com.jesjobom.ararai.benchmark

import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.GenerationMetrics
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelConfig
import com.jesjobom.ararai.model.ModelStartupState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BenchmarkViewModel(
    private val engine: LocalLlmEngine,
    initialConfig: ModelConfig,
    initialState: ModelStartupState,
    private val clock: BenchmarkClock = SystemBenchmarkClock,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var selectedConfig = initialConfig
    private var selectedModel = (initialState as? ModelStartupState.Available)?.model
    private var selectedInference = stableBenchmarkConfig(initialConfig.inference)
    private var benchmarkJob: Job? = null

    private val _uiState = MutableStateFlow(createState())
    val uiState: StateFlow<BenchmarkUiState> = _uiState.asStateFlow()

    fun onSelectedModelState(config: ModelConfig, state: ModelStartupState) {
        selectedConfig = config
        selectedModel = (state as? ModelStartupState.Available)?.model
        selectedInference = stableBenchmarkConfig(config.inference)

        if (selectedModel == null) {
            benchmarkJob?.cancel()
            benchmarkJob = null
            scope.launch { engine.unload() }
        }

        _uiState.value = createState()
    }

    fun runBenchmark() {
        val current = _uiState.value
        if (!current.canRun || current.isRunning || benchmarkJob?.isActive == true) return

        val model = selectedModel ?: return
        val inference = selectedInference

        benchmarkJob = scope.launch {
            _uiState.update {
                it.copy(
                    isRunning = true,
                    status = "Loading model",
                    result = null,
                    error = null,
                )
            }

            try {
                val loadStart = clock.nowNanos()
                engine.load(model, inference)
                val loadNanos = clock.nowNanos() - loadStart

                var streamedChunks = 0
                var generatedCharacters = 0
                var firstTokenNanos: Long? = null
                var runtimeMetrics: GenerationMetrics? = null
                var failure: String? = null
                var completed = false
                val generationStart = clock.nowNanos()

                _uiState.update { it.copy(status = "Generating benchmark response") }

                engine.generate(PromptRequest(BENCHMARK_PROMPT)).collect { event ->
                    when (event) {
                        is GenerationEvent.Token -> {
                            val now = clock.nowNanos()
                            if (firstTokenNanos == null) {
                                firstTokenNanos = now - generationStart
                            }
                            streamedChunks += 1
                            generatedCharacters += event.text.length
                        }
                        is GenerationEvent.ReasoningToken -> Unit
                        is GenerationEvent.Metrics -> runtimeMetrics = event.value
                        is GenerationEvent.Failed -> failure = event.message
                        GenerationEvent.Completed -> completed = true
                    }
                }

                val generationNanos = clock.nowNanos() - generationStart
                val error = failure ?: if (!completed) "Benchmark did not complete" else null
                if (error != null) {
                    _uiState.update {
                        it.copy(
                            isRunning = false,
                            status = "Benchmark failed",
                            error = error,
                        )
                    }
                    return@launch
                }

                val result = BenchmarkResult(
                    loadMillis = nanosToMillis(loadNanos),
                    firstTokenMillis = runtimeMetrics?.timeToFirstTokenMillis
                        ?: firstTokenNanos?.let(::nanosToMillis),
                    generationMillis = nanosToMillis(generationNanos),
                    totalMillis = nanosToMillis(loadNanos + generationNanos),
                    prefillTokens = runtimeMetrics?.prefillTokenCount,
                    prefillTokensPerSecond = runtimeMetrics?.prefillTokensPerSecond,
                    decodeTokens = runtimeMetrics?.decodeTokenCount,
                    decodeTokensPerSecond = runtimeMetrics?.decodeTokensPerSecond,
                    generatedCharacters = generatedCharacters,
                    streamedChunks = streamedChunks,
                    charactersPerSecond = ratePerSecond(generatedCharacters, generationNanos),
                )
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        status = "Benchmark complete",
                        result = result,
                    )
                }
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) return@launch
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        status = "Benchmark failed",
                        error = error.message ?: "Benchmark failed",
                    )
                }
            } finally {
                engine.unload()
            }
        }
    }

    fun onLeavingBenchmark() {
        val activeJob = benchmarkJob
        activeJob?.cancel()
        benchmarkJob = null
        if (activeJob == null) {
            scope.launch { engine.unload() }
        }
        _uiState.update {
            it.copy(
                isRunning = false,
                status = if (it.canRun) "Ready" else "Selected model must be available locally",
            )
        }
    }

    fun cancelBenchmark() {
        val activeJob = benchmarkJob
        activeJob?.cancel()
        benchmarkJob = null
        if (activeJob == null) {
            scope.launch { engine.unload() }
        }
        _uiState.update {
            it.copy(
                isRunning = false,
                status = "Benchmark canceled",
            )
        }
    }

    private fun createState(): BenchmarkUiState {
        val canRun = selectedModel != null
        return BenchmarkUiState(
            modelName = selectedModel?.name ?: selectedConfig.name,
            backendLabel = runtimeLabel(),
            promptLabel = BENCHMARK_PROMPT_LABEL,
            contextTokens = selectedInference.contextTokens,
            maxTokens = selectedInference.maxTokens,
            canRun = canRun,
        )
    }

    private fun runtimeLabel(): String {
        val runtime = selectedModel?.runtime ?: selectedConfig.runtime
        val acceleration = selectedModel?.acceleration ?: selectedConfig.acceleration
        return "${runtime.displayName} ${acceleration.displayName}"
    }

    private companion object {
        const val BENCHMARK_PROMPT_LABEL = "Baseline device prompt"
        const val BENCHMARK_MAX_TOKENS = 128
        const val BENCHMARK_PROMPT =
            "Write one concise paragraph explaining why on-device AI performance should be benchmarked with stable parameters."

        fun stableBenchmarkConfig(config: InferenceConfig): InferenceConfig =
            config.copy(
                contextTokens = config.contextTokens.coerceAtMost(2048),
                maxTokens = config.maxTokens.coerceAtMost(BENCHMARK_MAX_TOKENS),
                temperature = 0.2f,
                topP = 0.9f,
            )

        fun nanosToMillis(nanos: Long): Long = nanos / 1_000_000L

        fun ratePerSecond(count: Int, durationNanos: Long): Double {
            if (count == 0 || durationNanos <= 0L) return 0.0
            return count * 1_000_000_000.0 / durationNanos
        }
    }
}

fun interface BenchmarkClock {
    fun nowNanos(): Long
}

object SystemBenchmarkClock : BenchmarkClock {
    override fun nowNanos(): Long = System.nanoTime()
}
