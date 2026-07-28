package com.jesjobom.ararai.benchmark

import com.jesjobom.ararai.BuildConfig
import com.jesjobom.ararai.engine.LocalLlmEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

interface ToolCallingCharacterizationEngine {
    suspend fun runToolCallingCase(
        case: ToolCallingCase,
        onEvent: (ToolCallingRuntimeEvent) -> Unit = {},
    ): ToolCallingObservation
}

sealed interface ToolCallingRuntimeEvent {
    data class Message(val text: String, val elapsedMillis: Long) : ToolCallingRuntimeEvent
    data class ToolStarted(val elapsedMillis: Long) : ToolCallingRuntimeEvent
    data class ToolCompleted(val invocation: ToolInvocation) : ToolCallingRuntimeEvent
    data class CleanupStarted(val elapsedMillis: Long) : ToolCallingRuntimeEvent
    data class CleanupCompleted(val elapsedMillis: Long) : ToolCallingRuntimeEvent
    data class Error(val throwable: Throwable, val elapsedMillis: Long) : ToolCallingRuntimeEvent
}

enum class ToolCallingCasePhase { Started, RuntimeEvent, Completed, TimedOut }

data class ToolCallingCaseProgress(
    val completed: Int,
    val total: Int,
    val caseId: String,
    val repetition: Int,
    val phase: ToolCallingCasePhase,
    val detail: String? = null,
)

enum class ExpectedToolBehavior {
    NoCall,
    OneCall,
    MultiTurnReuse,
    Cancellation,
}

enum class DeterministicToolBehavior {
    Success,
    ControlledError,
    DelayedSuccess,
}

data class ToolCallingCase(
    val id: String,
    val prompt: String,
    val expectedBehavior: ExpectedToolBehavior,
    val toolBehavior: DeterministicToolBehavior = DeterministicToolBehavior.Success,
    val expectedLanguage: String? = null,
    val expectedEvidenceTerms: List<String> = emptyList(),
    val cancelAfterMillis: Long? = null,
    val turns: List<ToolCallingTurn> = emptyList(),
)

data class ToolCallingTurn(
    val id: String,
    val prompt: String,
    val expectedCalls: Int,
    val expectedEvidenceTerms: List<String> = emptyList(),
)

data class ToolInvocation(
    val rawArguments: String,
    val elapsedMillis: Long,
)

data class ToolCallingObservation(
    val finalAnswer: String,
    val invocations: List<ToolInvocation>,
    val firstTokenMillis: Long?,
    val totalMillis: Long,
    val cancelled: Boolean = false,
    val timedOut: Boolean = false,
    val cleanupTimedOut: Boolean = false,
)

data class ToolCallingCaseResult(
    val caseId: String,
    val repetition: Int,
    val passed: Boolean,
    val structuredBehaviorPassed: Boolean,
    val cleanupPassed: Boolean,
    val reason: String,
    val observation: ToolCallingObservation,
)

data class ToolCallingCharacterizationReport(
    val modelId: String,
    val modelSha256: String,
    val repetitions: Int,
    val results: List<ToolCallingCaseResult>,
) {
    val passed: Boolean get() = results.isNotEmpty() && results.all(ToolCallingCaseResult::passed)
    val passedCount: Int get() = results.count(ToolCallingCaseResult::passed)

    fun asText(): String = buildString {
        appendLine("ArarAI LiteRT-LM tool-calling characterization")
        appendLine("litertLmVersion=${BuildConfig.LITERT_LM_VERSION}")
        appendLine("model=$modelId")
        appendLine("sha256=$modelSha256")
        appendLine("repetitions=$repetitions")
        appendLine("verdict=${if (passed) "PASS" else "FAIL"}")
        appendLine("passed=$passedCount/${results.size}")
        results.forEach { result ->
            appendLine()
            appendLine("[${result.caseId} #${result.repetition}] ${if (result.passed) "PASS" else "FAIL"}")
            appendLine("reason=${result.reason}")
            appendLine("structuredBehavior=${if (result.structuredBehaviorPassed) "PASS" else "FAIL"}")
            appendLine("cleanup=${if (result.cleanupPassed) "PASS" else "FAIL"}")
            appendLine("calls=${result.observation.invocations.size}")
            appendLine("firstTokenMillis=${result.observation.firstTokenMillis ?: "n/a"}")
            appendLine("totalMillis=${result.observation.totalMillis}")
            appendLine("cancelled=${result.observation.cancelled}")
            appendLine("timedOut=${result.observation.timedOut}")
            result.observation.invocations.forEachIndexed { index, invocation ->
                appendLine("arguments.${index + 1}=${invocation.rawArguments}")
                appendLine("toolMillis.${index + 1}=${invocation.elapsedMillis}")
            }
            appendLine("answer=${result.observation.finalAnswer.replace('\n', ' ').take(MAX_REPORTED_ANSWER_CHARS)}")
        }
    }

    private companion object {
        const val MAX_REPORTED_ANSWER_CHARS = 1_000
    }
}

class ToolCallingCharacterizationRunner(
    private val engine: ToolCallingCharacterizationEngine,
    private val cases: List<ToolCallingCase> = defaultToolCallingCases(),
    private val caseTimeoutMillis: Long = DEFAULT_CASE_TIMEOUT_MILLIS,
    private val cleanupTimeoutMillis: Long = DEFAULT_CLEANUP_TIMEOUT_MILLIS,
) {
    suspend fun run(
        modelId: String,
        modelSha256: String,
        repetitions: Int,
        onProgress: (ToolCallingCaseProgress) -> Unit = {},
    ): ToolCallingCharacterizationReport {
        require(repetitions in 1..MAX_REPETITIONS)
        val total = cases.size * repetitions
        val results = buildList {
            repeat(repetitions) { repetition ->
                cases.forEach { case ->
                    val repetitionNumber = repetition + 1
                    onProgress(
                        ToolCallingCaseProgress(
                            size,
                            total,
                            case.id,
                            repetitionNumber,
                            ToolCallingCasePhase.Started,
                        ),
                    )
                    val execution =
                        execute(case) { detail ->
                            onProgress(
                                ToolCallingCaseProgress(
                                    size,
                                    total,
                                    case.id,
                                    repetitionNumber,
                                    ToolCallingCasePhase.RuntimeEvent,
                                    detail,
                                ),
                            )
                        }
                    val observation = execution.observation
                    add(evaluate(case, repetition + 1, observation))
                    onProgress(
                        ToolCallingCaseProgress(
                            size,
                            total,
                            case.id,
                            repetitionNumber,
                            if (execution.timedOut) ToolCallingCasePhase.TimedOut else ToolCallingCasePhase.Completed,
                            when {
                                execution.timedOut -> "callback timeout after $caseTimeoutMillis ms"
                                observation.cleanupTimedOut ->
                                    "structured behavior completed; cleanup timeout after $cleanupTimeoutMillis ms"
                                else -> null
                            },
                        ),
                    )
                    if (execution.timedOut) return@buildList
                }
            }
        }
        return ToolCallingCharacterizationReport(modelId, modelSha256, repetitions, results)
    }

    @Suppress("LongMethod")
    private suspend fun execute(case: ToolCallingCase, onRuntimeEvent: (String) -> Unit): CaseExecution {
        val startedAt = System.nanoTime()
        val observationLock = Any()
        val accumulatedText = StringBuilder()
        var firstTokenMillis: Long? = null
        val invocations = mutableListOf<ToolInvocation>()
        val cleanupCompleted = CompletableDeferred<Unit>()
        val eventSink: (ToolCallingRuntimeEvent) -> Unit = { event ->
            when (event) {
                is ToolCallingRuntimeEvent.Message -> {
                    synchronized(observationLock) {
                        accumulatedText.append(event.text)
                        if (event.text.isNotBlank() && firstTokenMillis == null) {
                            firstTokenMillis = event.elapsedMillis
                        }
                    }
                    onRuntimeEvent("onMessage elapsedMillis=${event.elapsedMillis} chars=${event.text.length}")
                }
                is ToolCallingRuntimeEvent.ToolStarted ->
                    onRuntimeEvent("toolStarted elapsedMillis=${event.elapsedMillis}")
                is ToolCallingRuntimeEvent.ToolCompleted -> {
                    synchronized(observationLock) {
                        invocations += event.invocation
                    }
                    onRuntimeEvent("toolCompleted toolMillis=${event.invocation.elapsedMillis}")
                }
                is ToolCallingRuntimeEvent.CleanupStarted ->
                    onRuntimeEvent("cleanupStarted elapsedMillis=${event.elapsedMillis}")
                is ToolCallingRuntimeEvent.CleanupCompleted -> {
                    cleanupCompleted.complete(Unit)
                    onRuntimeEvent("cleanupCompleted elapsedMillis=${event.elapsedMillis}")
                }
                is ToolCallingRuntimeEvent.Error ->
                    onRuntimeEvent(
                        "onError elapsedMillis=${event.elapsedMillis} type=${event.throwable::class.qualifiedName} " +
                            "message=${event.throwable.message.orEmpty()}",
                    )
            }
        }
        val cancelAfterMillis = case.cancelAfterMillis
        val observation =
            withTimeoutOrNull(caseTimeoutMillis) {
                if (cancelAfterMillis == null) {
                    engine.runToolCallingCase(case, eventSink)
                } else {
                    executeCancellationCase(case, cancelAfterMillis, eventSink)
                }
            }
        if (observation != null) {
            val cleanupFinished =
                withTimeoutOrNull(cleanupTimeoutMillis) {
                    cleanupCompleted.await()
                    true
                } ?: false
            return CaseExecution(
                observation.copy(cleanupTimedOut = !cleanupFinished),
                false,
            )
        }

        val partialObservation =
            synchronized(observationLock) {
                ToolCallingObservation(
                    finalAnswer = accumulatedText.toString(),
                    invocations = invocations.toList(),
                    firstTokenMillis = firstTokenMillis,
                    totalMillis = (System.nanoTime() - startedAt) / 1_000_000,
                    timedOut = true,
                )
            }
        return CaseExecution(
            partialObservation,
            true,
        )
    }

    private suspend fun executeCancellationCase(
        case: ToolCallingCase,
        cancelAfterMillis: Long,
        onEvent: (ToolCallingRuntimeEvent) -> Unit,
    ): ToolCallingObservation = coroutineScope {
        val startedAt = System.nanoTime()
        val work = async { engine.runToolCallingCase(case, onEvent) }
        delay(cancelAfterMillis)
        work.cancel()
        try {
            work.await()
        } catch (_: CancellationException) {
            ToolCallingObservation(
                finalAnswer = "",
                invocations = emptyList(),
                firstTokenMillis = null,
                totalMillis = (System.nanoTime() - startedAt) / 1_000_000,
                cancelled = true,
            )
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun evaluate(
        case: ToolCallingCase,
        repetition: Int,
        observation: ToolCallingObservation,
    ): ToolCallingCaseResult {
        val leakedProtocol = PROTOCOL_MARKERS.any { observation.finalAnswer.contains(it, ignoreCase = true) }
        val validArguments = observation.invocations.all { invocation ->
            invocation.rawArguments.contains("\"query\"") &&
                (case.expectedLanguage == null || invocation.rawArguments.contains(case.expectedLanguage))
        }
        val validContinuation =
            case.toolBehavior != DeterministicToolBehavior.Success ||
                case.expectedEvidenceTerms.isEmpty() ||
                case.expectedEvidenceTerms.any { observation.finalAnswer.contains(it, ignoreCase = true) }
        val structuredBehaviorPassed = when (case.expectedBehavior) {
            ExpectedToolBehavior.NoCall -> !observation.timedOut && observation.invocations.isEmpty() && !leakedProtocol
            ExpectedToolBehavior.OneCall ->
                !observation.timedOut &&
                    observation.invocations.size == 1 &&
                    validArguments &&
                    validContinuation &&
                    !leakedProtocol &&
                    observation.finalAnswer.isNotBlank()
            ExpectedToolBehavior.MultiTurnReuse ->
                !observation.timedOut &&
                    observation.invocations.size == case.turns.sumOf(ToolCallingTurn::expectedCalls) &&
                    validArguments &&
                    case.turns.all { turn ->
                        turn.expectedEvidenceTerms.isEmpty() ||
                            turn.expectedEvidenceTerms.any {
                                observation.finalAnswer.contains(it, ignoreCase = true)
                            }
                    } &&
                    !leakedProtocol
            ExpectedToolBehavior.Cancellation -> observation.cancelled
        }
        val cleanupPassed = !observation.cleanupTimedOut
        val passed = structuredBehaviorPassed && cleanupPassed
        val reason = when {
            observation.timedOut -> "Callback timeout after $caseTimeoutMillis ms"
            !structuredBehaviorPassed && leakedProtocol -> "Tool protocol leaked into visible output"
            !structuredBehaviorPassed && !validArguments ->
                "Tool arguments were missing query or expected language"
            !structuredBehaviorPassed && !validContinuation ->
                "Final answer did not incorporate deterministic tool evidence"
            !structuredBehaviorPassed ->
                "Expected ${case.expectedBehavior}, observed ${observation.invocations.size} call(s)"
            observation.cleanupTimedOut ->
                "Structured behavior passed; conversation cleanup timed out after $cleanupTimeoutMillis ms"
            passed -> "Expected structured behavior and cleanup observed"
            else -> "Characterization failed"
        }
        return ToolCallingCaseResult(
            case.id,
            repetition,
            passed,
            structuredBehaviorPassed,
            cleanupPassed,
            reason,
            observation,
        )
    }

    private companion object {
        const val MAX_REPETITIONS = 10
        const val DEFAULT_CASE_TIMEOUT_MILLIS = 90_000L
        const val DEFAULT_CLEANUP_TIMEOUT_MILLIS = 10_000L
        val PROTOCOL_MARKERS =
            listOf(
                "<tool_call",
                "tool_response",
                "wikipedia_search(",
                "```json",
            )
    }

    private data class CaseExecution(val observation: ToolCallingObservation, val timedOut: Boolean)
}

@Suppress("MaxLineLength")
fun LocalLlmEngine.toolCallingCharacterizerOrNull(): ToolCallingCharacterizationEngine? = this as? ToolCallingCharacterizationEngine

@Suppress("LongMethod")
fun defaultToolCallingCases(): List<ToolCallingCase> = listOf(
    ToolCallingCase(
        id = "multi-turn-reuse",
        prompt = "Four sequential turns in one tool-enabled conversation.",
        expectedBehavior = ExpectedToolBehavior.MultiTurnReuse,
        turns =
        listOf(
            ToolCallingTurn(
                id = "direct-answer",
                prompt = "Write a two-line haiku about rain. Do not research.",
                expectedCalls = 0,
            ),
            ToolCallingTurn(
                id = "english-search",
                prompt = "Use Wikipedia to identify who Alan Turing was.",
                expectedCalls = 1,
                expectedEvidenceTerms = listOf("British mathematician", "computer scientist"),
            ),
            ToolCallingTurn(
                id = "follow-up",
                prompt =
                "In one short sentence, restate his field using the result you just received. " +
                    "Do not research again.",
                expectedCalls = 0,
                expectedEvidenceTerms = listOf("mathematician", "computer"),
            ),
            ToolCallingTurn(
                id = "portuguese-search",
                prompt = "Agora use a Wikipédia para identificar quem foi Ada Lovelace. Responda em português.",
                expectedCalls = 1,
                expectedEvidenceTerms = listOf("matemática britânica", "programação"),
            ),
        ),
    ),
    ToolCallingCase(
        id = "direct-answer",
        prompt = "Write a two-line haiku about rain. Do not research.",
        expectedBehavior = ExpectedToolBehavior.NoCall,
    ),
    ToolCallingCase(
        id = "english-search",
        prompt = "Use Wikipedia to identify who Alan Turing was.",
        expectedBehavior = ExpectedToolBehavior.OneCall,
        expectedLanguage = "en",
        expectedEvidenceTerms = listOf("British mathematician", "computer scientist"),
    ),
    ToolCallingCase(
        id = "portuguese-search",
        prompt = "Use a Wikipédia para identificar quem foi Ada Lovelace.",
        expectedBehavior = ExpectedToolBehavior.OneCall,
        expectedLanguage = "pt",
        expectedEvidenceTerms = listOf("matemática britânica", "programadora"),
    ),
    ToolCallingCase(
        id = "controlled-error",
        prompt = "Use Wikipedia to identify who Grace Hopper was. If research fails, say so.",
        expectedBehavior = ExpectedToolBehavior.OneCall,
        toolBehavior = DeterministicToolBehavior.ControlledError,
        expectedLanguage = "en",
    ),
    ToolCallingCase(
        id = "single-call-limit",
        prompt = "Use Wikipedia to compare Alan Turing and Ada Lovelace, using only one search call.",
        expectedBehavior = ExpectedToolBehavior.OneCall,
        expectedLanguage = "en",
        expectedEvidenceTerms = listOf("mathematician", "analytical engine"),
    ),
    ToolCallingCase(
        id = "cancellation",
        prompt = "Use Wikipedia to identify who Katherine Johnson was.",
        expectedBehavior = ExpectedToolBehavior.Cancellation,
        toolBehavior = DeterministicToolBehavior.DelayedSuccess,
        expectedLanguage = "en",
        cancelAfterMillis = 250,
    ),
)
