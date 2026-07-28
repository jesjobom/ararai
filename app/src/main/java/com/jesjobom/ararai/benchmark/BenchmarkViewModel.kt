package com.jesjobom.ararai.benchmark

import com.jesjobom.ararai.BuildConfig
import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.GenerationMetrics
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.engine.ToolCallingLog
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelConfig
import com.jesjobom.ararai.model.ModelStartupState
import com.jesjobom.ararai.model.requireInference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
    private var selectedInference = stableBenchmarkConfig(initialConfig.requireInference())
    private var benchmarkJob: Job? = null

    private val _uiState = MutableStateFlow(createState())
    val uiState: StateFlow<BenchmarkUiState> = _uiState.asStateFlow()

    fun onSelectedModelState(
        config: ModelConfig,
        state: ModelStartupState,
    ) {
        selectedConfig = config
        selectedModel = (state as? ModelStartupState.Available)?.model
        selectedInference = stableBenchmarkConfig(config.requireInference())

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

        benchmarkJob =
            scope.launch {
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

                    val result =
                        BenchmarkResult(
                            loadMillis = nanosToMillis(loadNanos),
                            firstTokenMillis =
                            runtimeMetrics?.timeToFirstTokenMillis
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

    fun setCharacterizationCase(caseId: String) {
        if (_uiState.value.isRunning || defaultToolCallingCases().none { it.id == caseId }) return
        _uiState.update { it.copy(characterizationCaseId = caseId) }
    }

    @Suppress("ReturnCount", "LongMethod", "CyclomaticComplexMethod")
    fun runToolCallingCharacterization() {
        val current = _uiState.value
        val characterizer = engine.toolCallingCharacterizerOrNull() ?: return
        if (!current.canRun || current.isRunning || benchmarkJob?.isActive == true) return
        val model = selectedModel ?: return
        val inference = selectedInference
        val repetitions = 1
        val selectedCase =
            defaultToolCallingCases().singleOrNull { it.id == current.characterizationCaseId } ?: return
        val startedAt = clock.nowNanos()
        val diagnosticHeader =
            buildString {
                appendLine("ArarAI tool-calling characterization diagnostic")
                appendLine("model=${model.id}")
                appendLine("sha256=${selectedConfig.sha256}")
                appendLine("case=${selectedCase.id}")
                appendLine("phase=load")
                append("event=Starting LiteRT-LM model initialization")
            }
        ToolCallingLog.info(diagnosticHeader)

        benchmarkJob =
            scope.launch {
                _uiState.update {
                    it.copy(
                        isRunning = true,
                        status = "Loading model for tool-calling characterization",
                        characterizationProgress = "Loading ${model.name}",
                        characterizationReport = null,
                        characterizationDiagnostic = diagnosticHeader,
                        error = null,
                    )
                }
                val loadWatchdog =
                    launch {
                        delay(LOAD_WARNING_MILLIS)
                        _uiState.update {
                            it.copy(
                                characterizationProgress =
                                "Still loading ${model.name} in the native LiteRT-LM runtime",
                                characterizationDiagnostic =
                                diagnosticHeader +
                                    "\nelapsedMillis=${nanosToMillis(clock.nowNanos() - startedAt)}" +
                                    "\nwarning=Engine.initialize() has not returned after 60 seconds",
                            )
                        }
                    }
                try {
                    ToolCallingLog.info("engine.load begin model=${model.id}")
                    engine.load(model, inference)
                    loadWatchdog.cancel()
                    val loadMillis = nanosToMillis(clock.nowNanos() - startedAt)
                    ToolCallingLog.info("engine.load end model=${model.id} elapsedMillis=$loadMillis")
                    _uiState.update {
                        it.copy(
                            status = "Running tool-calling characterization",
                            characterizationProgress = "Model loaded in $loadMillis ms; starting matrix",
                            characterizationDiagnostic =
                            diagnosticHeader +
                                "\nloadMillis=$loadMillis" +
                                "\nphase=matrix" +
                                "\nevent=Model initialized; starting deterministic cases",
                        )
                    }
                    val runner = ToolCallingCharacterizationRunner(characterizer, listOf(selectedCase))
                    val report =
                        runner.run(
                            modelId = model.id,
                            modelSha256 = selectedConfig.sha256,
                            repetitions = repetitions,
                        ) { progress ->
                            val logEvent =
                                buildString {
                                    append("case=${progress.caseId}")
                                    append(" repetition=${progress.repetition}")
                                    append(" phase=${progress.phase.name.lowercase()}")
                                    append(" completed=${progress.completed}/${progress.total}")
                                    progress.detail?.let { append(" detail=$it") }
                                }
                            ToolCallingLog.info(logEvent)
                            _uiState.update {
                                val event =
                                    buildString {
                                        append("case=${progress.caseId}")
                                        append(" repetition=${progress.repetition}")
                                        append(" phase=${progress.phase.name.lowercase()}")
                                        progress.detail?.let { append(" detail=$it") }
                                    }
                                it.copy(
                                    status = "Running tool-calling characterization",
                                    characterizationProgress =
                                    "${progress.completed}/${progress.total} · ${progress.caseId} · " +
                                        progress.phase.name.lowercase(),
                                    characterizationDiagnostic =
                                    it.characterizationDiagnostic +
                                        "\n$event",
                                )
                            }
                        }
                    _uiState.update {
                        val status =
                            if (report.passed) {
                                "Tool-calling characterization passed"
                            } else {
                                "Tool-calling characterization failed"
                            }
                        it.copy(
                            isRunning = false,
                            status = status,
                            characterizationProgress = null,
                            characterizationReport = report,
                            characterizationDiagnostic =
                            it.characterizationDiagnostic +
                                "\nphase=complete" +
                                "\nverdict=${if (report.passed) "PASS" else "FAIL"}",
                        )
                    }
                    ToolCallingLog.info(
                        "matrix complete results=${report.results.size} " +
                            "verdict=${if (report.passed) "PASS" else "FAIL"}",
                    )
                } catch (error: Throwable) {
                    if (error is kotlinx.coroutines.CancellationException) return@launch
                    ToolCallingLog.error("characterization failed", error)
                    loadWatchdog.cancel()
                    val diagnostic =
                        buildString {
                            appendLine(_uiState.value.characterizationDiagnostic.orEmpty())
                            appendLine("phase=failed")
                            appendLine("elapsedMillis=${nanosToMillis(clock.nowNanos() - startedAt)}")
                            appendLine("exceptionType=${error::class.qualifiedName}")
                            appendLine("exceptionMessage=${error.message.orEmpty()}")
                            append(error.stackTraceToString())
                        }
                    _uiState.update {
                        it.copy(
                            isRunning = false,
                            status = "Tool-calling characterization failed",
                            characterizationProgress = null,
                            characterizationDiagnostic = diagnostic,
                            error = error.message ?: "Tool-calling characterization failed",
                        )
                    }
                } finally {
                    loadWatchdog.cancel()
                    ToolCallingLog.info("engine.unload begin")
                    engine.unload()
                    ToolCallingLog.info("engine.unload end")
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
            toolCallingSupported = BuildConfig.DEBUG && engine.toolCallingCharacterizerOrNull() != null,
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
        const val LOAD_WARNING_MILLIS = 60_000L
        const val BENCHMARK_PROMPT =
            "Write one concise paragraph explaining why on-device AI performance should be benchmarked with stable parameters."

        fun stableBenchmarkConfig(config: InferenceConfig): InferenceConfig = config.copy(
            contextTokens = config.contextTokens.coerceAtMost(2048),
            maxTokens = config.maxTokens.coerceAtMost(BENCHMARK_MAX_TOKENS),
            temperature = 0.2f,
            topP = 0.9f,
        )

        fun nanosToMillis(nanos: Long): Long = nanos / 1_000_000L

        fun ratePerSecond(
            count: Int,
            durationNanos: Long,
        ): Double {
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
