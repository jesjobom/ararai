@file:Suppress("MaxLineLength", "LongMethod", "CyclomaticComplexMethod")

package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.jesjobom.ararai.chat.MessageContent
import com.jesjobom.ararai.engine.EphemeralLocalLlmTool
import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.GenerationFailureKind
import com.jesjobom.ararai.engine.ImmediateLocalLlmRecoveryGate
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.LocalLlmRecoveryGate
import com.jesjobom.ararai.engine.PromptChatMessage
import com.jesjobom.ararai.engine.PromptChatRole
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.WIDGET_AUTHORING_PIPELINE_V1
import com.jesjobom.ararai.tools.ApplicationToolConsumer
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.widget.runtime.StrictJson
import com.jesjobom.ararai.widget.runtime.WidgetJavaScriptEngine
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import com.jesjobom.ararai.widget.runtime.WidgetToolCapability
import com.jesjobom.ararai.widget.runtime.sha256
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal data class WidgetToolCallingDiagnosticEnvironment(
    val generatedAtUtc: String,
    val manufacturer: String,
    val deviceModel: String,
    val androidRelease: String,
    val sdkInt: Int,
    val buildDisplay: String,
    val supportedAbis: List<String>,
    val appVersion: String,
    val versionCode: Long,
    val buildType: String,
    val apkSha256: String,
    val modelArtifactSha256: String,
) {
    init {
        require(generatedAtUtc.isNotBlank())
        require(manufacturer.isNotBlank())
        require(deviceModel.isNotBlank())
        require(androidRelease.isNotBlank())
        require(sdkInt > 0)
        require(buildDisplay.isNotBlank())
        require(supportedAbis.isNotEmpty() && supportedAbis.all(String::isNotBlank))
        require(appVersion.isNotBlank())
        require(versionCode > 0)
        require(buildType.isNotBlank())
        require(SHA256_PATTERN.matches(apkSha256))
        require(SHA256_PATTERN.matches(modelArtifactSha256))
    }
}

internal data class WidgetToolCallingDiagnosticCapture(
    val toolName: String,
    val callCount: Int,
    val argumentBytes: Long,
) {
    init {
        require(toolName in DIAGNOSTIC_STAGE_TOOL_NAMES)
        require(callCount > 0)
        require(argumentBytes >= 0)
    }
}

internal enum class WidgetToolCallingDiagnosticMode(
    val wireName: String,
    val plannedCaseCount: Int,
) {
    FullMatrix("full_matrix", DIAGNOSTIC_CASE_COUNT),
    FeasibilityFullNatural("feasibility_full_natural", 1),
    FeasibilityCompactNatural("feasibility_compact_natural", 1),
    FeasibilityCompactExplicit("feasibility_compact_explicit", 1),
    AlgorithmNatural("algorithm_natural", 1),
    AlgorithmReloadNatural("algorithm_reload_natural", 1),
    CompletePipelineCompactNatural("complete_pipeline_compact_natural", 1),
}

internal data class WidgetDiagnosticInputMetrics(
    val systemInstructionUtf8Bytes: Int,
    val userTextUtf8Bytes: Int,
    val contextUtf8Bytes: Int,
    val schemaUtf8Bytes: Int,
    val estimatedInputChars: Int,
    val contextSha256: String,
) {
    init {
        require(systemInstructionUtf8Bytes > 0)
        require(userTextUtf8Bytes > 0)
        require(contextUtf8Bytes > 0)
        require(schemaUtf8Bytes > 0)
        require(estimatedInputChars > 0)
        require(SHA256_PATTERN.matches(contextSha256))
    }
}

internal data class WidgetDiagnosticLifecycle(
    val firstGenerationEventMillis: Long?,
    val toolCaptureMillis: Long?,
    val terminalEventMillis: Long?,
    val watchdogMillis: Long?,
    val returnMillis: Long,
    val cleanupOverrunMillis: Long?,
) {
    init {
        listOfNotNull(
            firstGenerationEventMillis,
            toolCaptureMillis,
            terminalEventMillis,
            watchdogMillis,
            cleanupOverrunMillis,
        ).forEach { require(it >= 0) }
        require(returnMillis >= 0)
        require((watchdogMillis == null) == (cleanupOverrunMillis == null))
    }
}

internal data class WidgetDiagnosticRoundLifecycle(
    val stage: WidgetAuthoringStage,
    val attempt: Int,
    val isRepair: Boolean,
    val outcome: WidgetAuthoringRoundOutcome,
    val firstGenerationEventMillis: Long?,
    val toolCaptureMillis: Long?,
    val terminalEventMillis: Long?,
    val watchdogMillis: Long?,
    val returnMillis: Long,
    val cleanupOverrunMillis: Long?,
) {
    init {
        require(attempt in 1..WidgetAuthoringPipelinePolicy.MAX_REPAIRS + 1)
        listOfNotNull(
            firstGenerationEventMillis,
            toolCaptureMillis,
            terminalEventMillis,
            watchdogMillis,
            cleanupOverrunMillis,
        ).forEach { require(it >= 0) }
        require(returnMillis >= 0)
        require((watchdogMillis == null) == (cleanupOverrunMillis == null))
    }
}

internal data class WidgetDiagnosticRawExchange(
    val sequence: Int,
    val caseId: String,
    val stage: WidgetAuthoringStage,
    val toolName: String,
    val repairCode: WidgetAuthoringStageFailureCode?,
    val systemInstruction: String,
    val userText: String,
    val contextJson: String?,
    val toolDescriptionJson: String,
    val capturedArgumentsJson: List<String>,
)

internal data class WidgetDiagnosticRawTrace(
    val exchanges: List<WidgetDiagnosticRawExchange>,
)

internal data class WidgetToolCallingDiagnosticCaseResult(
    val id: String,
    val passed: Boolean,
    val outcome: String,
    val durationMillis: Long,
    val schemaSha256: String,
    val captures: List<WidgetToolCallingDiagnosticCapture>,
    val failureStage: WidgetAuthoringStage? = null,
    val failureCode: WidgetAuthoringStageFailureCode? = null,
    val attemptFailures: List<WidgetAuthoringAttemptFailure> = emptyList(),
    val inputMetrics: WidgetDiagnosticInputMetrics? = null,
    val lifecycle: WidgetDiagnosticLifecycle? = null,
    val roundLifecycles: List<WidgetDiagnosticRoundLifecycle> = emptyList(),
) {
    init {
        require(CASE_ID_PATTERN.matches(id))
        require(outcome in DIAGNOSTIC_CASE_OUTCOMES)
        require(passed == (outcome == DIAGNOSTIC_OUTCOME_PASS))
        require(durationMillis >= 0)
        require(SHA256_PATTERN.matches(schemaSha256))
        require(captures.map { it.toolName }.distinct().size == captures.size)
        require((failureStage == null) == (failureCode == null))
        require(failureStage == null || outcome == DIAGNOSTIC_PIPELINE_INVALID || outcome == DIAGNOSTIC_CASE_TIMEOUT)
        require(attemptFailures.size <= WidgetAuthoringPipelinePolicy.MAX_GENERATIONS)
        require(attemptFailures.distinctBy { it.stage to it.attempt }.size == attemptFailures.size)
        require(roundLifecycles.size <= WidgetAuthoringPipelinePolicy.MAX_GENERATIONS)
        require(roundLifecycles.distinctBy { it.stage to it.attempt }.size == roundLifecycles.size)
    }

    val toolCallObserved: Boolean
        get() = captures.isNotEmpty()

    val captureCount: Int
        get() = captures.sumOf(WidgetToolCallingDiagnosticCapture::callCount)

    val argumentBytes: Long?
        get() = if (captures.isEmpty()) {
            null
        } else {
            captures.sumOf(WidgetToolCallingDiagnosticCapture::argumentBytes)
        }
}

internal data class WidgetToolCallingDiagnosticReport(
    val environment: WidgetToolCallingDiagnosticEnvironment,
    val modelId: String,
    val modelName: String,
    val contextTokens: Int,
    val temperature: Float,
    val modelLoadOutcome: String,
    val modelLoadDurationMillis: Long,
    val cases: List<WidgetToolCallingDiagnosticCaseResult>,
    val mode: WidgetToolCallingDiagnosticMode = WidgetToolCallingDiagnosticMode.FullMatrix,
    val rawTrace: WidgetDiagnosticRawTrace? = null,
) {
    init {
        require(modelId.isNotBlank())
        require(modelName.isNotBlank())
        require(contextTokens > 0)
        require(temperature >= 0f)
        require(modelLoadOutcome in DIAGNOSTIC_LOAD_OUTCOMES)
        require(modelLoadDurationMillis >= 0)
        require(cases.map { it.id }.distinct().size == cases.size)
        require(cases.size <= mode.plannedCaseCount)
        require(modelLoadOutcome == DIAGNOSTIC_OUTCOME_PASS || cases.isEmpty())
    }

    val overallPassed: Boolean
        get() = modelLoadOutcome == DIAGNOSTIC_OUTCOME_PASS &&
            cases.size == mode.plannedCaseCount &&
            cases.all(WidgetToolCallingDiagnosticCaseResult::passed)

    val abortedAfterTimeout: Boolean
        get() = cases.lastOrNull()?.outcome == DIAGNOSTIC_CASE_TIMEOUT

    fun controlledLogLines(): List<String> = buildList {
        add("report mode=${mode.wireName} overallPassed=$overallPassed cases=${cases.size}")
        cases.forEach { result ->
            add(
                "case id=${result.id} outcome=${result.outcome} durationMillis=${result.durationMillis} " +
                    "captureCount=${result.captureCount} " +
                    "failureStage=${result.failureStage?.diagnosticWireName() ?: "none"} " +
                    "failureCode=${result.failureCode?.diagnosticWireName ?: "none"}",
            )
            result.inputMetrics?.let { input ->
                add(
                    "input id=${result.id} systemBytes=${input.systemInstructionUtf8Bytes} " +
                        "userBytes=${input.userTextUtf8Bytes} contextBytes=${input.contextUtf8Bytes} " +
                        "schemaBytes=${input.schemaUtf8Bytes} estimatedChars=${input.estimatedInputChars} " +
                        "contextSha256=${input.contextSha256}",
                )
            }
            result.lifecycle?.let { lifecycle ->
                add(
                    "lifecycle id=${result.id} firstEventMillis=${lifecycle.firstGenerationEventMillis ?: "none"} " +
                        "captureMillis=${lifecycle.toolCaptureMillis ?: "none"} " +
                        "terminalMillis=${lifecycle.terminalEventMillis ?: "none"} " +
                        "watchdogMillis=${lifecycle.watchdogMillis ?: "none"} " +
                        "returnMillis=${lifecycle.returnMillis} " +
                        "cleanupOverrunMillis=${lifecycle.cleanupOverrunMillis ?: "none"}",
                )
            }
            result.attemptFailures.forEach { failure ->
                add(
                    "attemptFailure stage=${failure.stage.diagnosticWireName()} attempt=${failure.attempt} " +
                        "code=${failure.code.diagnosticWireName} " +
                        "argumentBytes=${failure.argumentBytes ?: "none"}",
                )
            }
            result.roundLifecycles.forEach { lifecycle ->
                add(
                    "roundLifecycle stage=${lifecycle.stage.diagnosticWireName()} attempt=${lifecycle.attempt} " +
                        "repair=${lifecycle.isRepair} outcome=${lifecycle.outcome.diagnosticWireName} " +
                        "firstEventMillis=${lifecycle.firstGenerationEventMillis ?: "none"} " +
                        "captureMillis=${lifecycle.toolCaptureMillis ?: "none"} " +
                        "terminalMillis=${lifecycle.terminalEventMillis ?: "none"} " +
                        "watchdogMillis=${lifecycle.watchdogMillis ?: "none"} " +
                        "returnMillis=${lifecycle.returnMillis} " +
                        "cleanupOverrunMillis=${lifecycle.cleanupOverrunMillis ?: "none"}",
                )
            }
        }
    }

    fun toCanonicalJson(): String = StrictJson.canonical(
        JsonObject().apply {
            addProperty("suiteVersion", DIAGNOSTIC_SUITE_VERSION)
            addProperty("diagnosticMode", mode.wireName)
            addProperty("overallPassed", overallPassed)
            addProperty("containsRawModelOutput", false)
            addProperty("plannedCaseCount", mode.plannedCaseCount)
            addProperty("executedCaseCount", cases.size)
            addProperty("abortedAfterTimeout", abortedAfterTimeout)
            add("environment", environment.toJson())
            add(
                "model",
                JsonObject().apply {
                    addProperty("id", modelId)
                    addProperty("name", modelName)
                    addProperty("artifactSha256", environment.modelArtifactSha256)
                    addProperty("contextTokens", contextTokens)
                    addProperty("temperature", temperature)
                    addProperty("loadOutcome", modelLoadOutcome)
                    addProperty("loadDurationMillis", modelLoadDurationMillis)
                },
            )
            add(
                "cases",
                JsonArray().also { values -> cases.forEach { values.add(it.toJson()) } },
            )
        },
    )

    fun toRawDiagnosticJson(): String? {
        val trace = rawTrace ?: return null
        return StrictJson.canonical(
            JsonObject().apply {
                addProperty("rawDiagnosticFormatVersion", RAW_DIAGNOSTIC_FORMAT_VERSION)
                addProperty("suiteVersion", DIAGNOSTIC_SUITE_VERSION)
                addProperty("diagnosticMode", mode.wireName)
                addProperty("containsRawModelOutput", true)
                addProperty(
                    "warning",
                    "Debug-only diagnostic. It contains exact model tool arguments and may contain user content or generated source.",
                )
                add("environment", environment.toJson())
                add(
                    "model",
                    JsonObject().apply {
                        addProperty("id", modelId)
                        addProperty("name", modelName)
                        addProperty("artifactSha256", environment.modelArtifactSha256)
                        addProperty("contextTokens", contextTokens)
                        addProperty("temperature", temperature)
                    },
                )
                add(
                    "exchanges",
                    JsonArray().also { values -> trace.exchanges.forEach { values.add(it.toRawJson()) } },
                )
            },
        )
    }

    private fun WidgetToolCallingDiagnosticEnvironment.toJson() = JsonObject().apply {
        addProperty("generatedAtUtc", generatedAtUtc)
        addProperty("manufacturer", manufacturer)
        addProperty("deviceModel", deviceModel)
        addProperty("androidRelease", androidRelease)
        addProperty("sdkInt", sdkInt)
        addProperty("buildDisplay", buildDisplay)
        add("supportedAbis", JsonArray().also { values -> supportedAbis.forEach(values::add) })
        addProperty("appVersion", appVersion)
        addProperty("versionCode", versionCode)
        addProperty("buildType", buildType)
        addProperty("apkSha256", apkSha256)
    }

    private fun WidgetDiagnosticRawExchange.toRawJson() = JsonObject().apply {
        addProperty("sequence", sequence)
        addProperty("caseId", caseId)
        addProperty("stage", stage.diagnosticWireName())
        addProperty("toolName", toolName)
        if (repairCode == null) add("repairCode", null) else addProperty("repairCode", repairCode.diagnosticWireName)
        addProperty("systemInstruction", systemInstruction)
        addProperty("userText", userText)
        if (contextJson == null) add("contextJson", null) else addProperty("contextJson", contextJson)
        addProperty("toolDescriptionJson", toolDescriptionJson)
        add(
            "capturedArgumentsJson",
            JsonArray().also { values -> capturedArgumentsJson.forEach(values::add) },
        )
    }

    private fun WidgetToolCallingDiagnosticCaseResult.toJson() = JsonObject().apply {
        addProperty("id", id)
        addProperty("passed", passed)
        addProperty("outcome", outcome)
        addProperty("durationMillis", durationMillis)
        addProperty("schemaSha256", schemaSha256)
        addProperty("toolCallObserved", toolCallObserved)
        addProperty("captureCount", captureCount)
        if (argumentBytes == null) add("argumentBytes", null) else addProperty("argumentBytes", argumentBytes)
        if (failureStage == null) {
            add("failureStage", null)
            add("failureCode", null)
        } else {
            addProperty("failureStage", failureStage.diagnosticWireName())
            addProperty("failureCode", requireNotNull(failureCode).diagnosticWireName)
        }
        add(
            "captures",
            JsonArray().also { values ->
                captures.forEach { capture ->
                    values.add(
                        JsonObject().apply {
                            addProperty("toolName", capture.toolName)
                            addProperty("callCount", capture.callCount)
                            addProperty("argumentBytes", capture.argumentBytes)
                        },
                    )
                }
            },
        )
        add(
            "attemptFailures",
            JsonArray().also { values ->
                attemptFailures.forEach { failure ->
                    values.add(
                        JsonObject().apply {
                            addProperty("stage", failure.stage.diagnosticWireName())
                            addProperty("attempt", failure.attempt)
                            addProperty("code", failure.code.diagnosticWireName)
                            if (failure.argumentBytes == null) {
                                add("argumentBytes", null)
                            } else {
                                addProperty("argumentBytes", failure.argumentBytes)
                            }
                        },
                    )
                }
            },
        )
        inputMetrics?.let { input ->
            add(
                "inputMetrics",
                JsonObject().apply {
                    addProperty("systemInstructionUtf8Bytes", input.systemInstructionUtf8Bytes)
                    addProperty("userTextUtf8Bytes", input.userTextUtf8Bytes)
                    addProperty("contextUtf8Bytes", input.contextUtf8Bytes)
                    addProperty("schemaUtf8Bytes", input.schemaUtf8Bytes)
                    addProperty("estimatedInputChars", input.estimatedInputChars)
                    addProperty("contextSha256", input.contextSha256)
                },
            )
        }
        lifecycle?.let { trace ->
            add(
                "lifecycle",
                JsonObject().apply {
                    addNullableLong("firstGenerationEventMillis", trace.firstGenerationEventMillis)
                    addNullableLong("toolCaptureMillis", trace.toolCaptureMillis)
                    addNullableLong("terminalEventMillis", trace.terminalEventMillis)
                    addNullableLong("watchdogMillis", trace.watchdogMillis)
                    addProperty("returnMillis", trace.returnMillis)
                    addNullableLong("cleanupOverrunMillis", trace.cleanupOverrunMillis)
                },
            )
        }
        add(
            "roundLifecycles",
            JsonArray().also { values ->
                roundLifecycles.forEach { trace ->
                    values.add(
                        JsonObject().apply {
                            addProperty("stage", trace.stage.diagnosticWireName())
                            addProperty("attempt", trace.attempt)
                            addProperty("repair", trace.isRepair)
                            addProperty("outcome", trace.outcome.diagnosticWireName)
                            addNullableLong("firstGenerationEventMillis", trace.firstGenerationEventMillis)
                            addNullableLong("toolCaptureMillis", trace.toolCaptureMillis)
                            addNullableLong("terminalEventMillis", trace.terminalEventMillis)
                            addNullableLong("watchdogMillis", trace.watchdogMillis)
                            addProperty("returnMillis", trace.returnMillis)
                            addNullableLong("cleanupOverrunMillis", trace.cleanupOverrunMillis)
                        },
                    )
                }
            },
        )
    }

    private fun JsonObject.addNullableLong(name: String, value: Long?) {
        if (value == null) add(name, null) else addProperty(name, value)
    }
}

internal class WidgetToolCallingDiagnosticRunner(
    private val engine: LocalLlmEngine,
    private val registry: ApplicationToolRegistry,
    private val javascriptEngine: WidgetJavaScriptEngine,
    private val runtimeContextProvider: () -> WidgetRuntimeContext,
    private val maxContextTokens: Int = MAX_WIDGET_AUTHORING_CONTEXT_TOKENS,
    private val maxTemperature: Float = MAX_WIDGET_AUTHORING_TEMPERATURE,
    private val caseTimeoutMillis: Long = WIDGET_AUTHORING_TIMEOUT_MILLIS,
    private val recoveryGate: LocalLlmRecoveryGate = ImmediateLocalLlmRecoveryGate,
) {
    init {
        require(maxContextTokens > 0)
        require(maxTemperature >= 0f)
        require(caseTimeoutMillis > 0)
    }

    suspend fun run(
        model: LocalModel,
        inference: InferenceConfig,
        productionPrompt: WidgetAuthoringPrompt,
        environment: WidgetToolCallingDiagnosticEnvironment,
        mode: WidgetToolCallingDiagnosticMode = WidgetToolCallingDiagnosticMode.FullMatrix,
        captureRawArtifacts: Boolean = false,
    ): WidgetToolCallingDiagnosticReport {
        val rawTrace = DiagnosticRawTraceCollector(captureRawArtifacts)
        val diagnosticInference = inference.copy(
            // Match the production authoring workload exactly instead of inheriting a
            // smaller Chat preference that can change the diagnostic result.
            contextTokens = maxContextTokens,
            temperature = inference.temperature.coerceAtMost(maxTemperature),
        )
        val diagnosticModel = model.forAuthoringCharacterization()
        val loadStarted = monotonicMillis()
        val loadOutcome = try {
            // Diagnostics intentionally characterize a selected candidate before it is
            // granted the production protocol capability in the static catalog.
            engine.load(diagnosticModel, diagnosticInference)
            DIAGNOSTIC_OUTCOME_PASS
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DIAGNOSTIC_LOAD_FAILED
        }
        val loadDuration = monotonicMillis() - loadStarted
        val cases = if (loadOutcome == DIAGNOSTIC_OUTCOME_PASS) {
            when (mode) {
                WidgetToolCallingDiagnosticMode.FullMatrix -> {
                    runCases(diagnosticModel, diagnosticInference, productionPrompt, rawTrace)
                }
                WidgetToolCallingDiagnosticMode.FeasibilityFullNatural,
                WidgetToolCallingDiagnosticMode.FeasibilityCompactNatural,
                WidgetToolCallingDiagnosticMode.FeasibilityCompactExplicit,
                -> listOf(runFeasibilityProbeCase(productionPrompt, mode, rawTrace))
                WidgetToolCallingDiagnosticMode.AlgorithmNatural -> {
                    listOf(runAlgorithmProbeCase(productionPrompt, rawTrace))
                }
                WidgetToolCallingDiagnosticMode.AlgorithmReloadNatural -> {
                    listOf(
                        runAlgorithmReloadProbeCase(
                            diagnosticModel,
                            diagnosticInference,
                            productionPrompt,
                            rawTrace,
                        ),
                    )
                }
                WidgetToolCallingDiagnosticMode.CompletePipelineCompactNatural -> {
                    listOf(runCompletePipelineCase(diagnosticModel, diagnosticInference, productionPrompt, rawTrace))
                }
            }
        } else {
            emptyList()
        }
        return WidgetToolCallingDiagnosticReport(
            environment = environment,
            modelId = model.id,
            modelName = model.name,
            contextTokens = diagnosticInference.contextTokens,
            temperature = diagnosticInference.temperature,
            modelLoadOutcome = loadOutcome,
            modelLoadDurationMillis = loadDuration,
            cases = cases,
            mode = mode,
            rawTrace = rawTrace.snapshot(),
        )
    }

    private suspend fun runFeasibilityProbeCase(
        productionPrompt: WidgetAuthoringPrompt,
        mode: WidgetToolCallingDiagnosticMode,
        rawTrace: DiagnosticRawTraceCollector,
    ): WidgetToolCallingDiagnosticCaseResult {
        require(mode != WidgetToolCallingDiagnosticMode.FullMatrix)
        val context = diagnosticFeasibilityContext(
            productionPrompt,
            compact = mode != WidgetToolCallingDiagnosticMode.FeasibilityFullNatural,
        )
        val round = WidgetAuthoringRoundRequest(
            stage = WidgetAuthoringStage.Feasibility,
            toolName = SUBMIT_WIDGET_FEASIBILITY_TOOL,
            toolDescriptionJson = WidgetAuthoringStageSchemas.feasibility,
            objective = "Decide feasibility and select the minimum fixed capability envelope for the user's widget.",
            contextJson = context,
        )
        return runStageProbeCase(mode.wireName, round, rawTrace) { null }
    }

    private suspend fun runAlgorithmProbeCase(
        productionPrompt: WidgetAuthoringPrompt,
        rawTrace: DiagnosticRawTraceCollector,
        id: String = WidgetToolCallingDiagnosticMode.AlgorithmNatural.wireName,
    ): WidgetToolCallingDiagnosticCaseResult {
        val feasibility = algorithmProbeFeasibility()
        val round = WidgetAuthoringRoundRequest(
            stage = WidgetAuthoringStage.Algorithm,
            toolName = SUBMIT_WIDGET_ALGORITHM_TOOL,
            toolDescriptionJson = WidgetAuthoringStageSchemas.algorithm,
            objective = "Design an ordered typed algorithm inside the frozen capability envelope. Tool calls may not depend on live tool results.",
            contextJson = algorithmContext(productionPrompt, feasibility),
        )
        return runStageProbeCase(
            id,
            round,
            rawTrace,
        ) { raw ->
            when (val parsed = WidgetAlgorithmParser.parse(raw, feasibility)) {
                is WidgetAlgorithmParseResult.Valid -> null
                is WidgetAlgorithmParseResult.Invalid -> parsed.code
            }
        }
    }

    private suspend fun runAlgorithmReloadProbeCase(
        model: LocalModel,
        inference: InferenceConfig,
        productionPrompt: WidgetAuthoringPrompt,
        rawTrace: DiagnosticRawTraceCollector,
    ): WidgetToolCallingDiagnosticCaseResult {
        val started = monotonicMillis()
        return try {
            if (!engine.reloadWhenReady(model, inference, recoveryGate::awaitReady)) {
                return WidgetToolCallingDiagnosticCaseResult(
                    id = WidgetToolCallingDiagnosticMode.AlgorithmReloadNatural.wireName,
                    passed = false,
                    outcome = DIAGNOSTIC_CASE_TIMEOUT,
                    durationMillis = monotonicMillis() - started,
                    schemaSha256 = sha256(WidgetAuthoringStageSchemas.algorithm),
                    captures = emptyList(),
                )
            }
            runAlgorithmProbeCase(
                productionPrompt = productionPrompt,
                rawTrace = rawTrace,
                id = WidgetToolCallingDiagnosticMode.AlgorithmReloadNatural.wireName,
            ).let { result ->
                result.copy(durationMillis = monotonicMillis() - started)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            WidgetToolCallingDiagnosticCaseResult(
                id = WidgetToolCallingDiagnosticMode.AlgorithmReloadNatural.wireName,
                passed = false,
                outcome = DIAGNOSTIC_GENERATION_FAILED,
                durationMillis = monotonicMillis() - started,
                schemaSha256 = sha256(WidgetAuthoringStageSchemas.algorithm),
                captures = emptyList(),
            )
        }
    }

    private fun algorithmProbeFeasibility(): WidgetFeasibilityArtifact {
        val availableTools = registry.descriptors()
            .filter { ApplicationToolConsumer.Widget in it.consumers }
            .mapTo(mutableSetOf()) { WidgetToolCapability(it.id, it.version) }
        val artifact = JsonObject().apply {
            addProperty("outcome", WidgetFeasibilityOutcome.Achievable.wireName)
            addProperty("message", "Random Wikipedia event for today")
            addProperty("periodicIntervalHours", 1)
            add("toolIds", JsonArray().apply { add("wikipedia_on_this_day") })
        }
        return when (val parsed = WidgetFeasibilityParser.parse(artifact.toString(), availableTools)) {
            is WidgetFeasibilityParseResult.Valid -> parsed.artifact
            is WidgetFeasibilityParseResult.Invalid -> error("Diagnostic feasibility fixture is invalid")
        }
    }

    private suspend fun runStageProbeCase(
        id: String,
        round: WidgetAuthoringRoundRequest,
        rawTrace: DiagnosticRawTraceCollector,
        validate: (String) -> WidgetAuthoringStageFailureCode?,
    ): WidgetToolCallingDiagnosticCaseResult {
        val context = round.contextJson
        val inputMetrics = WidgetDiagnosticInputMetrics(
            systemInstructionUtf8Bytes = stageSystemInstruction(round).utf8Size(),
            userTextUtf8Bytes = round.toUserText().utf8Size(),
            contextUtf8Bytes = context.utf8Size(),
            schemaUtf8Bytes = round.toolDescriptionJson.utf8Size(),
            estimatedInputChars = round.estimatedInputChars(),
            contextSha256 = sha256(context),
        )
        val started = monotonicMillis()
        val firstEventAt = AtomicLong(UNSET_MILLIS)
        val captureAt = AtomicLong(UNSET_MILLIS)
        val terminalAt = AtomicLong(UNSET_MILLIS)
        val captureMetrics = DiagnosticCaptureMetrics()
        val capture = WidgetAuthoringStageCaptureTool(
            name = round.toolName,
            descriptionJson = round.toolDescriptionJson,
            onCapture = { toolName, argumentBytes ->
                captureAt.compareAndSet(UNSET_MILLIS, monotonicMillis() - started)
                captureMetrics.record(toolName, argumentBytes)
            },
            onRawCapture = rawTrace::recordCapture,
        )
        val userText = round.toUserText()
        rawTrace.recordRound(id, round)
        val request = PromptRequest(
            content = MessageContent.TextPrompt(userText),
            chatMessages = listOf(
                PromptChatMessage(PromptChatRole.System, stageSystemInstruction(round)),
                PromptChatMessage(PromptChatRole.User, userText),
            ),
            chatSessionId = null,
            advertisedToolNames = setOf(round.toolName),
            ephemeralTools = listOf(capture),
        )
        val terminal = try {
            withTimeoutOrNull(caseTimeoutMillis) {
                engine.generate(request)
                    .onEach { event ->
                        val elapsed = monotonicMillis() - started
                        firstEventAt.compareAndSet(UNSET_MILLIS, elapsed)
                        if (event is GenerationEvent.Completed || event is GenerationEvent.Failed) {
                            terminalAt.compareAndSet(UNSET_MILLIS, elapsed)
                        }
                    }
                    .firstOrNull { it is GenerationEvent.Completed || it is GenerationEvent.Failed }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            GenerationEvent.Failed("sanitized", GenerationFailureKind.Unexpected)
        }
        val returnedAt = monotonicMillis() - started
        val captured = capture.capturedOrNull()
        val validationFailure = if (terminal == GenerationEvent.Completed && captured != null) {
            validate(captured)
        } else {
            null
        }
        val outcome = when {
            validationFailure != null -> DIAGNOSTIC_PIPELINE_INVALID
            terminal == null -> DIAGNOSTIC_CASE_TIMEOUT
            terminal == GenerationEvent.Completed && captureMetrics.observed -> DIAGNOSTIC_OUTCOME_PASS
            terminal == GenerationEvent.Completed -> DIAGNOSTIC_NO_TOOL_CALL
            terminal is GenerationEvent.Failed -> when (terminal.kind) {
                GenerationFailureKind.Expected -> DIAGNOSTIC_GENERATION_EXPECTED
                GenerationFailureKind.ToolCallParsing -> DIAGNOSTIC_TOOL_CALL_PARSING
                GenerationFailureKind.Unexpected -> DIAGNOSTIC_GENERATION_FAILED
            }
            else -> DIAGNOSTIC_GENERATION_FAILED
        }
        val timedOut = terminal == null
        val failureCode = validationFailure ?: WidgetAuthoringStageFailureCode.TimedOut.takeIf { timedOut }
        return WidgetToolCallingDiagnosticCaseResult(
            id = id,
            passed = outcome == DIAGNOSTIC_OUTCOME_PASS,
            outcome = outcome,
            durationMillis = returnedAt,
            schemaSha256 = sha256(round.toolDescriptionJson),
            captures = captureMetrics.snapshot(),
            failureStage = round.stage.takeIf { failureCode != null },
            failureCode = failureCode,
            attemptFailures = if (failureCode != null) {
                listOf(
                    WidgetAuthoringAttemptFailure(
                        round.stage,
                        attempt = 1,
                        code = failureCode,
                        argumentBytes = captured?.utf8Size()?.toLong(),
                    ),
                )
            } else {
                emptyList()
            },
            inputMetrics = inputMetrics,
            lifecycle = WidgetDiagnosticLifecycle(
                firstGenerationEventMillis = firstEventAt.valueOrNull(),
                toolCaptureMillis = captureAt.valueOrNull(),
                terminalEventMillis = terminalAt.valueOrNull(),
                watchdogMillis = caseTimeoutMillis.takeIf { timedOut },
                returnMillis = returnedAt,
                cleanupOverrunMillis = (returnedAt - caseTimeoutMillis).coerceAtLeast(0).takeIf { timedOut },
            ),
        )
    }

    private suspend fun runCases(
        model: LocalModel,
        inference: InferenceConfig,
        productionPrompt: WidgetAuthoringPrompt,
        rawTrace: DiagnosticRawTraceCollector,
    ): List<WidgetToolCallingDiagnosticCaseResult> {
        val results = mutableListOf<WidgetToolCallingDiagnosticCaseResult>()
        for (testCase in stageDiagnosticCases()) {
            val result = runCase(testCase, rawTrace)
            results += result
            if (result.outcome == DIAGNOSTIC_CASE_TIMEOUT) break
        }
        if (results.none { it.outcome == DIAGNOSTIC_CASE_TIMEOUT }) {
            results += runCompletePipelineCase(model, inference, productionPrompt, rawTrace)
        }
        return results
    }

    private suspend fun runCompletePipelineCase(
        model: LocalModel,
        inference: InferenceConfig,
        productionPrompt: WidgetAuthoringPrompt,
        rawTrace: DiagnosticRawTraceCollector,
    ): WidgetToolCallingDiagnosticCaseResult {
        val captureMetrics = DiagnosticCaptureMetrics()
        val attemptFailures = mutableListOf<WidgetAuthoringAttemptFailure>()
        val roundLifecycles = DiagnosticRoundLifecycleCollector()
        val pipeline = ManagedWidgetAuthoringPipeline(
            modelController = WidgetAuthoringPipelineModelController(
                engine = engine,
                maxContextTokens = maxContextTokens,
                generationTimeoutMillis = caseTimeoutMillis,
                onCapture = captureMetrics::record,
                onRoundRequest = { request -> rawTrace.recordRound("complete_synthetic_pipeline", request) },
                onRawCapture = rawTrace::recordCapture,
                onRoundLifecycle = roundLifecycles::record,
                requireDeclaredProtocol = false,
                recoveryGate = recoveryGate,
            ),
            validator = WidgetAuthoringPipelineValidator(registry, javascriptEngine),
            draftBuilder = WidgetDraftBuilder(
                registry,
                JavaScriptWidgetDraftPlanner(javascriptEngine),
            ),
            registry = registry,
            onAttemptFailure = attemptFailures::add,
        )
        val started = monotonicMillis()
        var failureStage: WidgetAuthoringStage? = null
        var failureCode: WidgetAuthoringStageFailureCode? = null
        val outcome = try {
            when (
                val generated = pipeline.generate(
                    model,
                    inference,
                    productionPrompt,
                    runtimeContextProvider(),
                )
            ) {
                is WidgetAuthoringPipelineResult.DraftReady -> DIAGNOSTIC_OUTCOME_PASS
                WidgetAuthoringPipelineResult.TimedOut -> DIAGNOSTIC_CASE_TIMEOUT
                WidgetAuthoringPipelineResult.ModelLoadFailed -> DIAGNOSTIC_LOAD_FAILED
                WidgetAuthoringPipelineResult.ModelUnavailable -> DIAGNOSTIC_LOAD_INELIGIBLE
                is WidgetAuthoringPipelineResult.StageFailed -> {
                    failureStage = generated.stage
                    failureCode = generated.code
                    DIAGNOSTIC_PIPELINE_INVALID
                }
                is WidgetAuthoringPipelineResult.Unachievable,
                is WidgetAuthoringPipelineResult.NeedsClarification,
                -> DIAGNOSTIC_PIPELINE_TERMINAL
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DIAGNOSTIC_GENERATION_FAILED
        } finally {
            pipeline.clear()
        }
        return WidgetToolCallingDiagnosticCaseResult(
            id = "complete_synthetic_pipeline",
            passed = outcome == DIAGNOSTIC_OUTCOME_PASS,
            outcome = outcome,
            durationMillis = monotonicMillis() - started,
            schemaSha256 = sha256(
                listOf(
                    WidgetAuthoringStageSchemas.feasibility,
                    WidgetAuthoringStageSchemas.algorithm,
                    WidgetAuthoringStageSchemas.sourceFragment(SUBMIT_WIDGET_CALL_FUNCTION_TOOL),
                    WidgetAuthoringStageSchemas.sourceFragment(SUBMIT_WIDGET_PLAN_FUNCTION_TOOL),
                    WidgetAuthoringStageSchemas.sourceFragment(SUBMIT_WIDGET_RENDER_FUNCTION_TOOL),
                ).joinToString("\n"),
            ),
            captures = captureMetrics.snapshot(),
            failureStage = failureStage,
            failureCode = failureCode,
            attemptFailures = attemptFailures.toList(),
            roundLifecycles = roundLifecycles.snapshot(),
        )
    }

    private suspend fun runCase(
        testCase: DiagnosticCase,
        rawTrace: DiagnosticRawTraceCollector,
    ): WidgetToolCallingDiagnosticCaseResult {
        val captureMetrics = DiagnosticCaptureMetrics()
        val capture = DiagnosticCaptureTool(
            testCase.toolName,
            testCase.toolDescriptionJson,
            onCapture = captureMetrics::record,
            onRawCapture = rawTrace::recordCapture,
        )
        rawTrace.record(
            caseId = testCase.id,
            stage = testCase.toolName.diagnosticStage(),
            toolName = testCase.toolName,
            repairCode = null,
            systemInstruction = testCase.systemInstruction,
            userText = testCase.userInstruction,
            contextJson = null,
            toolDescriptionJson = testCase.toolDescriptionJson,
        )
        val started = monotonicMillis()
        val terminal = try {
            withTimeoutOrNull(caseTimeoutMillis) {
                engine.generate(testCase.request(capture))
                    .firstOrNull { it is GenerationEvent.Completed || it is GenerationEvent.Failed }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            GenerationEvent.Failed("sanitized", GenerationFailureKind.Unexpected)
        }
        val outcome = when (terminal) {
            null -> DIAGNOSTIC_CASE_TIMEOUT
            GenerationEvent.Completed -> if (captureMetrics.observed) {
                DIAGNOSTIC_OUTCOME_PASS
            } else {
                DIAGNOSTIC_NO_TOOL_CALL
            }
            is GenerationEvent.Failed -> when (terminal.kind) {
                GenerationFailureKind.Expected -> DIAGNOSTIC_GENERATION_EXPECTED
                GenerationFailureKind.ToolCallParsing -> DIAGNOSTIC_TOOL_CALL_PARSING
                GenerationFailureKind.Unexpected -> DIAGNOSTIC_GENERATION_FAILED
            }
            else -> DIAGNOSTIC_GENERATION_FAILED
        }
        return WidgetToolCallingDiagnosticCaseResult(
            id = testCase.id,
            passed = outcome == DIAGNOSTIC_OUTCOME_PASS,
            outcome = outcome,
            durationMillis = monotonicMillis() - started,
            schemaSha256 = sha256(testCase.toolDescriptionJson),
            captures = captureMetrics.snapshot(),
        )
    }

    private fun stageDiagnosticCases(): List<DiagnosticCase> = listOf(
        DiagnosticCase(
            id = "stage_feasibility_schema",
            toolName = SUBMIT_WIDGET_FEASIBILITY_TOOL,
            toolDescriptionJson = WidgetAuthoringStageSchemas.feasibility,
            systemInstruction = "Call $SUBMIT_WIDGET_FEASIBILITY_TOOL exactly once with outcome unachievable, message Unsupported, periodicIntervalHours null, and empty toolIds. Do not answer in plain text.",
            userInstruction = "Submit the feasibility diagnostic artifact.",
        ),
        DiagnosticCase(
            id = "stage_algorithm_schema",
            toolName = SUBMIT_WIDGET_ALGORITHM_TOOL,
            toolDescriptionJson = WidgetAuthoringStageSchemas.algorithm,
            systemInstruction = "Call $SUBMIT_WIDGET_ALGORITHM_TOOL exactly once with protocolVersion 1, one tool_call step whose id is lookup, objective is Lookup, empty dependsOn, toolId wikipedia_pages, and presentationObjective Display result. Do not answer in plain text.",
            userInstruction = "Submit the algorithm diagnostic artifact.",
        ),
        DiagnosticCase(
            id = "stage_call_function_schema",
            toolName = SUBMIT_WIDGET_CALL_FUNCTION_TOOL,
            toolDescriptionJson = WidgetAuthoringStageSchemas.sourceFragment(SUBMIT_WIDGET_CALL_FUNCTION_TOOL),
            systemInstruction = "Call $SUBMIT_WIDGET_CALL_FUNCTION_TOOL exactly once with protocolVersion 1, artifactId lookup, functionName buildCallLookup, inputNames containing runtime, and source set to function buildCallLookup(runtime) { return {}; }. Do not answer in plain text.",
            userInstruction = "Submit the call-function diagnostic artifact.",
        ),
        DiagnosticCase(
            id = "stage_plan_function_schema",
            toolName = SUBMIT_WIDGET_PLAN_FUNCTION_TOOL,
            toolDescriptionJson = WidgetAuthoringStageSchemas.sourceFragment(SUBMIT_WIDGET_PLAN_FUNCTION_TOOL),
            systemInstruction = "Call $SUBMIT_WIDGET_PLAN_FUNCTION_TOOL exactly once with protocolVersion 1, artifactId plan, functionName plan, inputNames containing runtime, and source set to function plan(runtime) { return []; }. Do not answer in plain text.",
            userInstruction = "Submit the plan-function diagnostic artifact.",
        ),
        DiagnosticCase(
            id = "stage_render_function_schema",
            toolName = SUBMIT_WIDGET_RENDER_FUNCTION_TOOL,
            toolDescriptionJson = WidgetAuthoringStageSchemas.sourceFragment(SUBMIT_WIDGET_RENDER_FUNCTION_TOOL),
            systemInstruction = "Call $SUBMIT_WIDGET_RENDER_FUNCTION_TOOL exactly once with protocolVersion 1, artifactId render, functionName render, inputNames runtime/outcomes/state, and source set to function render(runtime, outcomes, state) { return {type:'text',text:'ok',tone:'neutral'}; }. Do not answer in plain text.",
            userInstruction = "Submit the render-function diagnostic artifact.",
        ),
    )

    private data class DiagnosticCase(
        val id: String,
        val toolName: String,
        val toolDescriptionJson: String,
        val systemInstruction: String,
        val userInstruction: String,
    ) {
        fun request(tool: EphemeralLocalLlmTool) = PromptRequest(
            content = MessageContent.TextPrompt(userInstruction),
            chatMessages = listOf(
                PromptChatMessage(PromptChatRole.System, systemInstruction),
                PromptChatMessage(PromptChatRole.User, userInstruction),
            ),
            chatSessionId = null,
            advertisedToolNames = setOf(toolName),
            ephemeralTools = listOf(tool),
        )
    }

    private class DiagnosticCaptureTool(
        override val name: String,
        override val descriptionJson: String,
        private val onCapture: (toolName: String, argumentBytes: Int) -> Unit,
        private val onRawCapture: (toolName: String, argumentsJson: String) -> Unit,
    ) : EphemeralLocalLlmTool {
        private val calls = AtomicInteger()

        override fun execute(argumentsJson: String): String {
            onCapture(name, argumentsJson.toByteArray(Charsets.UTF_8).size)
            onRawCapture(name, argumentsJson)
            return if (calls.incrementAndGet() == 1) {
                """{"accepted":true}"""
            } else {
                """{"accepted":false,"error":"CALL_LIMIT_REACHED"}"""
            }
        }
    }
}

private class DiagnosticCaptureMetrics {
    private class Counter {
        val calls = AtomicInteger()
        val bytes = AtomicLong()
    }

    private val counters = ConcurrentHashMap<String, Counter>()

    val observed: Boolean
        get() = counters.isNotEmpty()

    fun record(toolName: String, argumentBytes: Int) {
        require(toolName in DIAGNOSTIC_STAGE_TOOL_NAMES)
        require(argumentBytes >= 0)
        counters.computeIfAbsent(toolName) { Counter() }.also { counter ->
            counter.calls.incrementAndGet()
            counter.bytes.addAndGet(argumentBytes.toLong())
        }
    }

    fun snapshot(): List<WidgetToolCallingDiagnosticCapture> = counters.entries
        .sortedBy { it.key }
        .map { (toolName, counter) ->
            WidgetToolCallingDiagnosticCapture(
                toolName = toolName,
                callCount = counter.calls.get(),
                argumentBytes = counter.bytes.get(),
            )
        }
}

private class DiagnosticRoundLifecycleCollector {
    private val attemptsByStage = mutableMapOf<WidgetAuthoringStage, Int>()
    private val lifecycles = mutableListOf<WidgetDiagnosticRoundLifecycle>()

    @Synchronized
    fun record(lifecycle: WidgetAuthoringRoundLifecycle) {
        val attempt = attemptsByStage.getOrDefault(lifecycle.stage, 0) + 1
        attemptsByStage[lifecycle.stage] = attempt
        lifecycles += WidgetDiagnosticRoundLifecycle(
            stage = lifecycle.stage,
            attempt = attempt,
            isRepair = lifecycle.isRepair,
            outcome = lifecycle.outcome,
            firstGenerationEventMillis = lifecycle.firstGenerationEventMillis,
            toolCaptureMillis = lifecycle.toolCaptureMillis,
            terminalEventMillis = lifecycle.terminalEventMillis,
            watchdogMillis = lifecycle.watchdogMillis,
            returnMillis = lifecycle.returnMillis,
            cleanupOverrunMillis = lifecycle.cleanupOverrunMillis,
        )
    }

    @Synchronized
    fun snapshot(): List<WidgetDiagnosticRoundLifecycle> = lifecycles.toList()
}

private class DiagnosticRawTraceCollector(private val enabled: Boolean) {
    private data class MutableExchange(
        val sequence: Int,
        val caseId: String,
        val stage: WidgetAuthoringStage,
        val toolName: String,
        val repairCode: WidgetAuthoringStageFailureCode?,
        val systemInstruction: String,
        val userText: String,
        val contextJson: String?,
        val toolDescriptionJson: String,
        val capturedArgumentsJson: MutableList<String> = mutableListOf(),
    )

    private val exchanges = mutableListOf<MutableExchange>()

    fun recordRound(caseId: String, request: WidgetAuthoringRoundRequest) {
        record(
            caseId = caseId,
            stage = request.stage,
            toolName = request.toolName,
            repairCode = request.repairCode,
            systemInstruction = stageSystemInstruction(request),
            userText = request.toUserText(),
            contextJson = request.contextJson,
            toolDescriptionJson = request.toolDescriptionJson,
        )
    }

    @Synchronized
    fun record(
        caseId: String,
        stage: WidgetAuthoringStage,
        toolName: String,
        repairCode: WidgetAuthoringStageFailureCode?,
        systemInstruction: String,
        userText: String,
        contextJson: String?,
        toolDescriptionJson: String,
    ) {
        if (!enabled) return
        exchanges += MutableExchange(
            sequence = exchanges.size + 1,
            caseId = caseId,
            stage = stage,
            toolName = toolName,
            repairCode = repairCode,
            systemInstruction = systemInstruction,
            userText = userText,
            contextJson = contextJson,
            toolDescriptionJson = toolDescriptionJson,
        )
    }

    @Synchronized
    fun recordCapture(toolName: String, argumentsJson: String) {
        if (!enabled) return
        exchanges.lastOrNull { it.toolName == toolName }?.capturedArgumentsJson?.add(argumentsJson)
    }

    @Synchronized
    fun snapshot(): WidgetDiagnosticRawTrace? = if (enabled) {
        WidgetDiagnosticRawTrace(
            exchanges.map { exchange ->
                WidgetDiagnosticRawExchange(
                    sequence = exchange.sequence,
                    caseId = exchange.caseId,
                    stage = exchange.stage,
                    toolName = exchange.toolName,
                    repairCode = exchange.repairCode,
                    systemInstruction = exchange.systemInstruction,
                    userText = exchange.userText,
                    contextJson = exchange.contextJson,
                    toolDescriptionJson = exchange.toolDescriptionJson,
                    capturedArgumentsJson = exchange.capturedArgumentsJson.toList(),
                )
            },
        )
    } else {
        null
    }
}

private fun String.diagnosticStage(): WidgetAuthoringStage = when (this) {
    SUBMIT_WIDGET_FEASIBILITY_TOOL -> WidgetAuthoringStage.Feasibility
    SUBMIT_WIDGET_ALGORITHM_TOOL -> WidgetAuthoringStage.Algorithm
    SUBMIT_WIDGET_CALL_FUNCTION_TOOL -> WidgetAuthoringStage.CallFunction
    SUBMIT_WIDGET_PLAN_FUNCTION_TOOL -> WidgetAuthoringStage.PlanFunction
    SUBMIT_WIDGET_RENDER_FUNCTION_TOOL -> WidgetAuthoringStage.RenderFunction
    else -> error("Unexpected diagnostic tool")
}

private fun WidgetAuthoringStage.diagnosticWireName(): String = when (this) {
    WidgetAuthoringStage.Feasibility -> "feasibility"
    WidgetAuthoringStage.Algorithm -> "algorithm"
    WidgetAuthoringStage.CallFunction -> "call_function"
    WidgetAuthoringStage.PlanFunction -> "plan_function"
    WidgetAuthoringStage.RenderFunction -> "render_function"
    WidgetAuthoringStage.AssemblyValidation -> "assembly_validation"
}

private fun monotonicMillis(): Long = System.nanoTime() / 1_000_000

internal const val DIAGNOSTIC_OUTCOME_PASS = "pass"
internal const val DIAGNOSTIC_TOOL_CALL_PARSING = "tool_call_parsing"
internal const val DIAGNOSTIC_NO_TOOL_CALL = "no_tool_call"
internal const val DIAGNOSTIC_GENERATION_EXPECTED = "generation_rejected"

/**
 * Characterization harnesses (diagnostic probes and the background authoring
 * probe) load the selected candidate with the staged authoring protocol
 * capability even though the static catalog does not grant it yet. Without
 * it, the engine's tool-support gate rejects every ephemeral authoring-tool
 * request before generation starts (tools_unsupported).
 */
internal fun LocalModel.forAuthoringCharacterization(): LocalModel = copy(
    toolCapabilities = toolCapabilities.copy(
        authoringProtocolNames = toolCapabilities.authoringProtocolNames + WIDGET_AUTHORING_PIPELINE_V1,
    ),
)
internal const val DIAGNOSTIC_GENERATION_FAILED = "generation_failed"
internal const val DIAGNOSTIC_CASE_TIMEOUT = "case_timeout"
internal const val DIAGNOSTIC_LOAD_FAILED = "model_load_failed"
internal const val DIAGNOSTIC_LOAD_INELIGIBLE = "ineligible_model"
internal const val DIAGNOSTIC_PIPELINE_INVALID = "pipeline_invalid"
internal const val DIAGNOSTIC_PIPELINE_TERMINAL = "pipeline_terminal"

private const val DIAGNOSTIC_SUITE_VERSION = 15
private const val RAW_DIAGNOSTIC_FORMAT_VERSION = 1
private const val DIAGNOSTIC_CASE_COUNT = 6
private const val UNSET_MILLIS = -1L
private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
private val CASE_ID_PATTERN = Regex("[a-z][a-z0-9_]{0,63}")
private val DIAGNOSTIC_STAGE_TOOL_NAMES = setOf(
    SUBMIT_WIDGET_FEASIBILITY_TOOL,
    SUBMIT_WIDGET_ALGORITHM_TOOL,
    SUBMIT_WIDGET_CALL_FUNCTION_TOOL,
    SUBMIT_WIDGET_PLAN_FUNCTION_TOOL,
    SUBMIT_WIDGET_RENDER_FUNCTION_TOOL,
)
private val DIAGNOSTIC_LOAD_OUTCOMES = setOf(
    DIAGNOSTIC_OUTCOME_PASS,
    DIAGNOSTIC_LOAD_FAILED,
    DIAGNOSTIC_LOAD_INELIGIBLE,
)
private val DIAGNOSTIC_CASE_OUTCOMES = setOf(
    DIAGNOSTIC_OUTCOME_PASS,
    DIAGNOSTIC_TOOL_CALL_PARSING,
    DIAGNOSTIC_NO_TOOL_CALL,
    DIAGNOSTIC_GENERATION_EXPECTED,
    DIAGNOSTIC_GENERATION_FAILED,
    DIAGNOSTIC_CASE_TIMEOUT,
    DIAGNOSTIC_LOAD_FAILED,
    DIAGNOSTIC_LOAD_INELIGIBLE,
    DIAGNOSTIC_PIPELINE_INVALID,
    DIAGNOSTIC_PIPELINE_TERMINAL,
)

private fun AtomicLong.valueOrNull(): Long? = get().takeIf { it != UNSET_MILLIS }
