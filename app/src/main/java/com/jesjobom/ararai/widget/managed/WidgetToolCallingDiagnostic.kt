@file:Suppress("MaxLineLength", "LongMethod")

package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.jesjobom.ararai.chat.MessageContent
import com.jesjobom.ararai.engine.EphemeralLocalLlmTool
import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.GenerationFailureKind
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.PromptChatMessage
import com.jesjobom.ararai.engine.PromptChatRole
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.WIDGET_AUTHORING_PIPELINE_V1
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.widget.runtime.StrictJson
import com.jesjobom.ararai.widget.runtime.WidgetJavaScriptEngine
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import com.jesjobom.ararai.widget.runtime.sha256
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
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

internal data class WidgetToolCallingDiagnosticCaseResult(
    val id: String,
    val passed: Boolean,
    val outcome: String,
    val durationMillis: Long,
    val schemaSha256: String,
    val captures: List<WidgetToolCallingDiagnosticCapture>,
    val failureStage: WidgetAuthoringStage? = null,
    val failureCode: WidgetAuthoringStageFailureCode? = null,
) {
    init {
        require(CASE_ID_PATTERN.matches(id))
        require(outcome in DIAGNOSTIC_CASE_OUTCOMES)
        require(passed == (outcome == DIAGNOSTIC_OUTCOME_PASS))
        require(durationMillis >= 0)
        require(SHA256_PATTERN.matches(schemaSha256))
        require(captures.map { it.toolName }.distinct().size == captures.size)
        require((failureStage == null) == (failureCode == null))
        require(failureStage == null || outcome == DIAGNOSTIC_PIPELINE_INVALID)
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
) {
    init {
        require(modelId.isNotBlank())
        require(modelName.isNotBlank())
        require(contextTokens > 0)
        require(temperature >= 0f)
        require(modelLoadOutcome in DIAGNOSTIC_LOAD_OUTCOMES)
        require(modelLoadDurationMillis >= 0)
        require(cases.map { it.id }.distinct().size == cases.size)
        require(cases.size <= DIAGNOSTIC_CASE_COUNT)
        require(modelLoadOutcome == DIAGNOSTIC_OUTCOME_PASS || cases.isEmpty())
    }

    val overallPassed: Boolean
        get() = modelLoadOutcome == DIAGNOSTIC_OUTCOME_PASS &&
            cases.size == DIAGNOSTIC_CASE_COUNT &&
            cases.all(WidgetToolCallingDiagnosticCaseResult::passed)

    val abortedAfterTimeout: Boolean
        get() = cases.lastOrNull()?.outcome == DIAGNOSTIC_CASE_TIMEOUT

    fun toCanonicalJson(): String = StrictJson.canonical(
        JsonObject().apply {
            addProperty("suiteVersion", DIAGNOSTIC_SUITE_VERSION)
            addProperty("overallPassed", overallPassed)
            addProperty("containsRawModelOutput", false)
            addProperty("plannedCaseCount", DIAGNOSTIC_CASE_COUNT)
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
            addProperty("failureCode", requireNotNull(failureCode).diagnosticWireName())
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
    ): WidgetToolCallingDiagnosticReport {
        val diagnosticInference = inference.copy(
            // Match the production authoring workload exactly instead of inheriting a
            // smaller Chat preference that can change the diagnostic result.
            contextTokens = maxContextTokens,
            temperature = inference.temperature.coerceAtMost(maxTemperature),
        )
        val diagnosticModel = model.copy(
            toolCapabilities = model.toolCapabilities.copy(
                authoringProtocolNames = model.toolCapabilities.authoringProtocolNames + WIDGET_AUTHORING_PIPELINE_V1,
            ),
        )
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
            runCases(diagnosticModel, diagnosticInference, productionPrompt)
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
        )
    }

    private suspend fun runCases(
        model: LocalModel,
        inference: InferenceConfig,
        productionPrompt: WidgetAuthoringPrompt,
    ): List<WidgetToolCallingDiagnosticCaseResult> {
        val results = mutableListOf<WidgetToolCallingDiagnosticCaseResult>()
        for (testCase in stageDiagnosticCases()) {
            val result = runCase(testCase)
            results += result
            if (result.outcome == DIAGNOSTIC_CASE_TIMEOUT) break
        }
        if (results.none { it.outcome == DIAGNOSTIC_CASE_TIMEOUT }) {
            results += runCompletePipelineCase(model, inference, productionPrompt)
        }
        return results
    }

    private suspend fun runCompletePipelineCase(
        model: LocalModel,
        inference: InferenceConfig,
        productionPrompt: WidgetAuthoringPrompt,
    ): WidgetToolCallingDiagnosticCaseResult {
        val captureMetrics = DiagnosticCaptureMetrics()
        val pipeline = ManagedWidgetAuthoringPipeline(
            modelController = WidgetAuthoringPipelineModelController(
                engine = engine,
                maxContextTokens = maxContextTokens,
                generationTimeoutMillis = caseTimeoutMillis,
                onCapture = captureMetrics::record,
                requireDeclaredProtocol = false,
            ),
            validator = WidgetAuthoringPipelineValidator(registry, javascriptEngine),
            draftBuilder = WidgetDraftBuilder(
                registry,
                JavaScriptWidgetDraftPlanner(javascriptEngine),
            ),
            registry = registry,
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
        )
    }

    private suspend fun runCase(testCase: DiagnosticCase): WidgetToolCallingDiagnosticCaseResult {
        val captureMetrics = DiagnosticCaptureMetrics()
        val capture = DiagnosticCaptureTool(
            testCase.toolName,
            testCase.toolDescriptionJson,
            captureMetrics::record,
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
            systemInstruction = "Call $SUBMIT_WIDGET_FEASIBILITY_TOOL exactly once with protocolVersion 1, outcome unachievable, displayName Diagnostic, enabled false, periodicIntervalHours null, empty tools/runtime/presentation, reason Unsupported, and clarificationQuestion null. Do not answer in plain text.",
            userInstruction = "Submit the feasibility diagnostic artifact.",
        ),
        DiagnosticCase(
            id = "stage_algorithm_schema",
            toolName = SUBMIT_WIDGET_ALGORITHM_TOOL,
            toolDescriptionJson = WidgetAuthoringStageSchemas.algorithm,
            systemInstruction = "Call $SUBMIT_WIDGET_ALGORITHM_TOOL exactly once with protocolVersion 1, one tool_call step whose id is lookup, objective is Lookup, empty dependsOn/runtimeInputs, toolId wikipedia_pages, contractVersion 1, and presentationObjective Display result. Do not answer in plain text.",
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
    ) : EphemeralLocalLlmTool {
        private val calls = AtomicInteger()

        override fun execute(argumentsJson: String): String {
            onCapture(name, argumentsJson.toByteArray(Charsets.UTF_8).size)
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

private fun WidgetAuthoringStage.diagnosticWireName(): String = when (this) {
    WidgetAuthoringStage.Feasibility -> "feasibility"
    WidgetAuthoringStage.Algorithm -> "algorithm"
    WidgetAuthoringStage.CallFunction -> "call_function"
    WidgetAuthoringStage.PlanFunction -> "plan_function"
    WidgetAuthoringStage.RenderFunction -> "render_function"
    WidgetAuthoringStage.AssemblyValidation -> "assembly_validation"
}

private fun WidgetAuthoringStageFailureCode.diagnosticWireName(): String = when (this) {
    WidgetAuthoringStageFailureCode.InvalidSchema -> "invalid_schema"
    WidgetAuthoringStageFailureCode.InvalidAlgorithm -> "invalid_algorithm"
    WidgetAuthoringStageFailureCode.InvalidSource -> "invalid_source"
    WidgetAuthoringStageFailureCode.InvalidToolArguments -> "invalid_tool_arguments"
    WidgetAuthoringStageFailureCode.InvalidPlan -> "invalid_plan"
    WidgetAuthoringStageFailureCode.InvalidPresentation -> "invalid_presentation"
    WidgetAuthoringStageFailureCode.CapabilityExpansion -> "capability_expansion"
    WidgetAuthoringStageFailureCode.ResourceLimit -> "resource_limit"
    WidgetAuthoringStageFailureCode.MissingArtifact -> "missing_artifact"
    WidgetAuthoringStageFailureCode.TimedOut -> "timed_out"
    WidgetAuthoringStageFailureCode.RuntimeUnavailable -> "runtime_unavailable"
}

private fun monotonicMillis(): Long = System.nanoTime() / 1_000_000

internal const val DIAGNOSTIC_OUTCOME_PASS = "pass"
internal const val DIAGNOSTIC_TOOL_CALL_PARSING = "tool_call_parsing"
internal const val DIAGNOSTIC_NO_TOOL_CALL = "no_tool_call"
internal const val DIAGNOSTIC_GENERATION_EXPECTED = "generation_rejected"
internal const val DIAGNOSTIC_GENERATION_FAILED = "generation_failed"
internal const val DIAGNOSTIC_CASE_TIMEOUT = "case_timeout"
internal const val DIAGNOSTIC_LOAD_FAILED = "model_load_failed"
internal const val DIAGNOSTIC_LOAD_INELIGIBLE = "ineligible_model"
internal const val DIAGNOSTIC_PIPELINE_INVALID = "pipeline_invalid"
internal const val DIAGNOSTIC_PIPELINE_TERMINAL = "pipeline_terminal"

private const val DIAGNOSTIC_SUITE_VERSION = 4
private const val DIAGNOSTIC_CASE_COUNT = 6
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
