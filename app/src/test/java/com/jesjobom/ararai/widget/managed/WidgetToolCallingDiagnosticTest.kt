@file:Suppress("MaxLineLength")

package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.GenerationFailureKind
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.tools.ApplicationTool
import com.jesjobom.ararai.tools.ApplicationToolCategory
import com.jesjobom.ararai.tools.ApplicationToolConsumer
import com.jesjobom.ararai.tools.ApplicationToolContract
import com.jesjobom.ararai.tools.ApplicationToolOperationalState
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.tools.applicationToolBinding
import com.jesjobom.ararai.widget.runtime.WidgetJavaScriptEngine
import com.jesjobom.ararai.widget.runtime.WidgetRequestedLimits
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import com.jesjobom.ararai.widget.runtime.WidgetScriptResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetToolCallingDiagnosticTest {
    @Test
    fun `matrix characterizes each stage and complete pipeline without exporting raw model data`() = runTest {
        val engine = DiagnosticFakeEngine(DiagnosticMode.Pass)
        val report = runner(engine).run(
            model = model(),
            inference = INFERENCE.copy(contextTokens = 2_048, temperature = 0.7f),
            productionPrompt = prompt("private prompt marker"),
            environment = environment(),
        )

        assertTrue(report.toCanonicalJson(), report.overallPassed)
        assertEquals(6, report.cases.size)
        assertTrue(report.cases.all { it.passed && it.toolCallObserved && it.argumentBytes != null })
        assertTrue(report.cases.all { it.failureStage == null && it.failureCode == null })
        assertTrue(report.cases.all { it.attemptFailures.isEmpty() })
        assertTrue(report.cases.take(5).all { it.captures.single().callCount == 1 })
        assertEquals(EXPECTED_STAGE_TOOLS, report.cases.last().captures.mapTo(mutableSetOf()) { it.toolName })
        assertEquals(6, report.cases.map { it.schemaSha256 }.distinct().size)
        assertEquals(MAX_WIDGET_AUTHORING_CONTEXT_TOKENS, engine.loadedInference?.contextTokens)
        assertEquals(MAX_WIDGET_AUTHORING_TEMPERATURE, engine.loadedInference?.temperature)
        assertTrue(engine.requests.all { it.chatSessionId == null })
        assertTrue(engine.requests.all { it.advertisedToolNames.size == 1 })
        assertTrue(engine.requests.all { it.ephemeralTools.single().name in EXPECTED_STAGE_TOOLS })

        val encoded = report.toCanonicalJson()
        assertEquals(15, JsonParser.parseString(encoded).asJsonObject.get("suiteVersion").asInt)
        assertTrue(encoded.contains("\"containsRawModelOutput\":false"))
        assertTrue(encoded.contains("\"failureStage\":null"))
        assertTrue(encoded.contains("\"failureCode\":null"))
        assertTrue(encoded.contains("\"captures\":[{"))
        assertTrue(encoded.contains("\"apkSha256\":\"${"a".repeat(64)}\""))
        assertTrue(encoded.contains("\"artifactSha256\":\"${"b".repeat(64)}\""))
        assertFalse(encoded.contains("private prompt marker"))
        assertFalse(encoded.contains(DiagnosticFakeEngine.RAW_ARGUMENT_MARKER))
        assertFalse(encoded.contains(CALL_SOURCE))
    }

    @Test
    fun `cold feasibility probes expose bounded input and lifecycle metrics without raw context`() = runTest {
        val full = runner(DiagnosticFakeEngine(DiagnosticMode.Pass)).run(
            model(),
            INFERENCE,
            prompt("private natural prompt marker"),
            environment(),
            WidgetToolCallingDiagnosticMode.FeasibilityFullNatural,
        )
        val compact = runner(DiagnosticFakeEngine(DiagnosticMode.Pass)).run(
            model(),
            INFERENCE,
            prompt("private natural prompt marker"),
            environment(),
            WidgetToolCallingDiagnosticMode.FeasibilityCompactNatural,
        )

        assertTrue(full.overallPassed)
        assertTrue(compact.overallPassed)
        assertEquals(1, full.cases.size)
        assertEquals(1, compact.cases.size)
        assertEquals("feasibility_full_natural", full.mode.wireName)
        assertEquals("feasibility_compact_natural", compact.mode.wireName)
        val fullInput = full.cases.single().inputMetrics!!
        val compactInput = compact.cases.single().inputMetrics!!
        assertTrue(fullInput.contextUtf8Bytes > compactInput.contextUtf8Bytes)
        assertEquals(WidgetAuthoringStageSchemas.feasibility.toByteArray().size, compactInput.schemaUtf8Bytes)
        assertTrue(compactInput.systemInstructionUtf8Bytes > 0)
        assertTrue(compactInput.userTextUtf8Bytes > 0)
        assertTrue(compactInput.estimatedInputChars > compactInput.contextUtf8Bytes)
        assertTrue(compactInput.contextSha256.matches(Regex("[0-9a-f]{64}")))
        val lifecycle = compact.cases.single().lifecycle!!
        assertTrue(lifecycle.firstGenerationEventMillis != null)
        assertTrue(lifecycle.terminalEventMillis != null)
        assertEquals(null, lifecycle.watchdogMillis)
        assertEquals(null, lifecycle.cleanupOverrunMillis)
        assertTrue(lifecycle.returnMillis >= lifecycle.terminalEventMillis!!)

        val encoded = compact.toCanonicalJson()
        assertTrue(encoded.contains("\"diagnosticMode\":\"feasibility_compact_natural\""))
        assertTrue(encoded.contains("\"plannedCaseCount\":1"))
        assertTrue(encoded.contains("\"contextUtf8Bytes\":"))
        assertTrue(encoded.contains("\"firstGenerationEventMillis\":"))
        assertFalse(encoded.contains("private natural prompt marker"))
        assertFalse(encoded.contains("runtime.seededIndex"))
        assertFalse(encoded.contains(DiagnosticFakeEngine.RAW_ARGUMENT_MARKER))
    }

    @Test
    fun `timed out feasibility probe distinguishes watchdog from cleanup return`() = runTest {
        val report = runner(DiagnosticFakeEngine(DiagnosticMode.Timeout), timeoutMillis = 1)
            .run(
                model(),
                INFERENCE,
                prompt("private prompt marker"),
                environment(),
                WidgetToolCallingDiagnosticMode.FeasibilityCompactNatural,
            )

        val result = report.cases.single()
        assertEquals(DIAGNOSTIC_CASE_TIMEOUT, result.outcome)
        assertEquals(WidgetAuthoringStage.Feasibility, result.failureStage)
        assertEquals(WidgetAuthoringStageFailureCode.TimedOut, result.failureCode)
        assertEquals(1L, result.lifecycle?.watchdogMillis)
        assertTrue(requireNotNull(result.lifecycle?.cleanupOverrunMillis) >= 0)
        assertTrue(requireNotNull(result.lifecycle).returnMillis >= 1)
        assertFalse(report.toCanonicalJson().contains("private prompt marker"))
    }

    @Test
    fun `cold algorithm probe uses production context lifecycle and semantic validation`() = runTest {
        val engine = DiagnosticFakeEngine(DiagnosticMode.Pass)
        val report = runner(engine).run(
            model(),
            INFERENCE,
            prompt("private algorithm prompt marker"),
            environment(),
            WidgetToolCallingDiagnosticMode.AlgorithmNatural,
        )

        assertTrue(report.toCanonicalJson(), report.overallPassed)
        assertEquals("algorithm_natural", report.mode.wireName)
        assertEquals(SUBMIT_WIDGET_ALGORITHM_TOOL, engine.requests.single().ephemeralTools.single().name)
        val result = report.cases.single()
        assertEquals(WidgetAuthoringStageSchemas.algorithm.toByteArray().size, result.inputMetrics?.schemaUtf8Bytes)
        assertTrue(requireNotNull(result.inputMetrics).contextUtf8Bytes > 0)
        assertTrue(result.lifecycle?.toolCaptureMillis != null)
        assertTrue(result.lifecycle?.terminalEventMillis != null)
        assertTrue(result.attemptFailures.isEmpty())
        val encoded = report.toCanonicalJson()
        assertFalse(encoded.contains("private algorithm prompt marker"))
        assertFalse(encoded.contains(DiagnosticFakeEngine.RAW_ARGUMENT_MARKER))

        val invalid = runner(DiagnosticFakeEngine(DiagnosticMode.InvalidPipelineAlgorithm)).run(
            model(),
            INFERENCE,
            prompt(),
            environment(),
            WidgetToolCallingDiagnosticMode.AlgorithmNatural,
        ).cases.single()
        assertEquals(DIAGNOSTIC_PIPELINE_INVALID, invalid.outcome)
        assertEquals(WidgetAuthoringStage.Algorithm, invalid.failureStage)
        assertEquals(WidgetAuthoringStageFailureCode.InvalidAlgorithm, invalid.failureCode)
        assertEquals(2L, invalid.attemptFailures.single().argumentBytes)
    }

    @Test
    fun `recovery algorithm probe unloads waits and reloads once before isolated generation`() = runTest {
        val engine = DiagnosticFakeEngine(DiagnosticMode.Pass)

        val report = runner(engine).run(
            model(),
            INFERENCE,
            prompt("private reload algorithm prompt marker"),
            environment(),
            WidgetToolCallingDiagnosticMode.AlgorithmReloadNatural,
        )

        assertTrue(report.toCanonicalJson(), report.overallPassed)
        assertEquals("algorithm_reload_natural", report.mode.wireName)
        assertEquals(2, engine.loadCount)
        assertEquals(1, engine.unloadCount)
        assertEquals(SUBMIT_WIDGET_ALGORITHM_TOOL, engine.requests.single().ephemeralTools.single().name)
        assertEquals("algorithm_reload_natural", report.cases.single().id)
    }

    @Test
    fun `cold complete pipeline runs without preceding schema matrix`() = runTest {
        val engine = DiagnosticFakeEngine(DiagnosticMode.Pass)

        val report = runner(engine).run(
            model(),
            INFERENCE,
            prompt("private natural prompt marker"),
            environment(),
            WidgetToolCallingDiagnosticMode.CompletePipelineCompactNatural,
        )

        assertTrue(report.toCanonicalJson(), report.overallPassed)
        assertEquals("complete_pipeline_compact_natural", report.mode.wireName)
        assertEquals(1, report.cases.size)
        assertEquals("complete_synthetic_pipeline", report.cases.single().id)
        assertEquals(5, engine.requests.size)
        assertEquals(
            listOf(
                SUBMIT_WIDGET_FEASIBILITY_TOOL,
                SUBMIT_WIDGET_ALGORITHM_TOOL,
                SUBMIT_WIDGET_CALL_FUNCTION_TOOL,
                SUBMIT_WIDGET_PLAN_FUNCTION_TOOL,
                SUBMIT_WIDGET_RENDER_FUNCTION_TOOL,
            ),
            engine.requests.map { it.ephemeralTools.single().name },
        )
        assertEquals(5, report.cases.single().roundLifecycles.size)
        assertEquals(
            listOf(
                WidgetAuthoringStage.Feasibility,
                WidgetAuthoringStage.Algorithm,
                WidgetAuthoringStage.CallFunction,
                WidgetAuthoringStage.PlanFunction,
                WidgetAuthoringStage.RenderFunction,
            ),
            report.cases.single().roundLifecycles.map { it.stage },
        )
        assertTrue(report.cases.single().roundLifecycles.all { it.outcome == WidgetAuthoringRoundOutcome.Captured })
        assertTrue(report.toCanonicalJson().contains("\"roundLifecycles\":[{"))
        assertTrue(report.controlledLogLines().any { it.startsWith("roundLifecycle stage=algorithm attempt=1") })
        assertFalse(report.toCanonicalJson().contains("private natural prompt marker"))
    }

    @Test
    fun `opt in raw trace exports exact requests and captures without contaminating sanitized report`() = runTest {
        val report = runner(DiagnosticFakeEngine(DiagnosticMode.Pass)).run(
            model(),
            INFERENCE,
            prompt("private raw export marker"),
            environment(),
            WidgetToolCallingDiagnosticMode.CompletePipelineCompactNatural,
            captureRawArtifacts = true,
        )

        val sanitized = report.toCanonicalJson()
        val raw = requireNotNull(report.toRawDiagnosticJson())
        val trace = requireNotNull(report.rawTrace)
        assertFalse(sanitized.contains("private raw export marker"))
        assertFalse(sanitized.contains(CALL_SOURCE))
        assertTrue(raw.contains("\"containsRawModelOutput\":true"))
        assertTrue(raw.contains("private raw export marker"))
        assertTrue(
            trace.exchanges.any { exchange ->
                exchange.capturedArgumentsJson.any { arguments ->
                    JsonParser.parseString(arguments).asJsonObject.get("source")?.asString == CALL_SOURCE
                }
            },
        )
        assertTrue(raw.contains("\"capturedArgumentsJson\":["))
        assertEquals(5, trace.exchanges.size)
        assertEquals(
            listOf(
                SUBMIT_WIDGET_FEASIBILITY_TOOL,
                SUBMIT_WIDGET_ALGORITHM_TOOL,
                SUBMIT_WIDGET_CALL_FUNCTION_TOOL,
                SUBMIT_WIDGET_PLAN_FUNCTION_TOOL,
                SUBMIT_WIDGET_RENDER_FUNCTION_TOOL,
            ),
            trace.exchanges.map { it.toolName },
        )
        assertTrue(trace.exchanges.all { it.capturedArgumentsJson.size == 1 })
    }

    @Test
    fun `raw trace is absent unless explicitly enabled`() = runTest {
        val report = runner(DiagnosticFakeEngine(DiagnosticMode.Pass)).run(
            model(),
            INFERENCE,
            prompt("private disabled raw marker"),
            environment(),
            WidgetToolCallingDiagnosticMode.FeasibilityCompactNatural,
        )

        assertEquals(null, report.rawTrace)
        assertEquals(null, report.toRawDiagnosticJson())
    }

    @Test
    fun `complete pipeline reports controlled stage failure and aggregate captures without raw artifacts`() = runTest {
        val report = runner(DiagnosticFakeEngine(DiagnosticMode.InvalidPipelineAlgorithm))
            .run(model(), INFERENCE, prompt("private prompt marker"), environment())

        assertFalse(report.overallPassed)
        val complete = report.cases.last()
        assertEquals(DIAGNOSTIC_PIPELINE_INVALID, complete.outcome)
        assertEquals(WidgetAuthoringStage.Algorithm, complete.failureStage)
        assertEquals(WidgetAuthoringStageFailureCode.InvalidAlgorithm, complete.failureCode)
        assertEquals(
            listOf(
                WidgetToolCallingDiagnosticCapture(
                    toolName = SUBMIT_WIDGET_ALGORITHM_TOOL,
                    callCount = 3,
                    argumentBytes = 6,
                ),
                WidgetToolCallingDiagnosticCapture(
                    toolName = SUBMIT_WIDGET_FEASIBILITY_TOOL,
                    callCount = 1,
                    argumentBytes = feasibilityArtifact().toByteArray().size.toLong(),
                ),
            ),
            complete.captures,
        )
        assertEquals(4, complete.captureCount)
        assertEquals(6 + feasibilityArtifact().toByteArray().size.toLong(), complete.argumentBytes)
        assertEquals(
            (1..3).map { attempt ->
                WidgetAuthoringAttemptFailure(
                    stage = WidgetAuthoringStage.Algorithm,
                    attempt = attempt,
                    code = WidgetAuthoringStageFailureCode.InvalidAlgorithm,
                    argumentBytes = 2,
                )
            },
            complete.attemptFailures,
        )

        val encoded = report.toCanonicalJson()
        assertTrue(encoded.contains("\"failureStage\":\"algorithm\""))
        assertTrue(encoded.contains("\"failureCode\":\"invalid_algorithm\""))
        assertTrue(encoded.contains("\"captureCount\":4"))
        assertTrue(encoded.contains("\"attemptFailures\":[{"))
        val algorithmCapture = JsonParser.parseString(encoded).asJsonObject
            .getAsJsonArray("cases")
            .last().asJsonObject
            .getAsJsonArray("captures")
            .first().asJsonObject
        assertEquals(SUBMIT_WIDGET_ALGORITHM_TOOL, algorithmCapture.get("toolName").asString)
        assertEquals(3, algorithmCapture.get("callCount").asInt)
        assertEquals(6, algorithmCapture.get("argumentBytes").asLong)
        assertFalse(encoded.contains("private prompt marker"))
        assertFalse(encoded.contains(DiagnosticFakeEngine.RAW_ARGUMENT_MARKER))
        assertFalse(encoded.contains(DiagnosticFakeEngine.RAW_EXCEPTION_MARKER))
    }

    @Test
    fun `complete pipeline exposes sanitized lifecycle for every algorithm timeout`() = runTest {
        val report = runner(DiagnosticFakeEngine(DiagnosticMode.AlgorithmTimeout), timeoutMillis = 1).run(
            model(),
            INFERENCE,
            prompt("private lifecycle marker"),
            environment(),
            WidgetToolCallingDiagnosticMode.CompletePipelineCompactNatural,
        )

        val complete = report.cases.single()
        assertEquals(DIAGNOSTIC_PIPELINE_INVALID, complete.outcome)
        assertEquals(WidgetAuthoringStage.Algorithm, complete.failureStage)
        assertEquals(WidgetAuthoringStageFailureCode.TimedOut, complete.failureCode)
        assertEquals(4, complete.roundLifecycles.size)
        assertEquals(WidgetAuthoringRoundOutcome.Captured, complete.roundLifecycles.first().outcome)
        complete.roundLifecycles.drop(1).forEachIndexed { index, lifecycle ->
            assertEquals(WidgetAuthoringStage.Algorithm, lifecycle.stage)
            assertEquals(index + 1, lifecycle.attempt)
            assertEquals(index > 0, lifecycle.isRepair)
            assertEquals(WidgetAuthoringRoundOutcome.TimedOut, lifecycle.outcome)
            assertEquals(null, lifecycle.firstGenerationEventMillis)
            assertEquals(null, lifecycle.toolCaptureMillis)
            assertEquals(null, lifecycle.terminalEventMillis)
            assertEquals(1L, lifecycle.watchdogMillis)
            assertTrue(lifecycle.returnMillis >= 0)
            assertTrue(requireNotNull(lifecycle.cleanupOverrunMillis) >= 0)
        }
        val encoded = report.toCanonicalJson()
        val controlledLog = report.controlledLogLines().joinToString("\n")
        assertTrue(encoded.contains("\"roundLifecycles\":[{"))
        assertTrue(encoded.contains("\"outcome\":\"timed_out\""))
        assertTrue(controlledLog.contains("roundLifecycle stage=algorithm attempt=3"))
        assertTrue(controlledLog.contains("firstEventMillis=none"))
        assertFalse(encoded.contains("private lifecycle marker"))
    }

    @Test
    fun `complete pipeline exports structural subcode and attempt sequence without rejected values`() = runTest {
        val report = runner(DiagnosticFakeEngine(DiagnosticMode.InvalidPipelineFeasibilityFields))
            .run(model(), INFERENCE, prompt("private prompt marker"), environment())

        assertFalse(report.overallPassed)
        val complete = report.cases.last()
        assertEquals(DIAGNOSTIC_PIPELINE_INVALID, complete.outcome)
        assertEquals(WidgetAuthoringStage.Feasibility, complete.failureStage)
        assertEquals(
            WidgetAuthoringStageFailureCode.InvalidFeasibilityFields,
            complete.failureCode,
        )
        assertEquals(
            listOf(
                WidgetToolCallingDiagnosticCapture(
                    toolName = SUBMIT_WIDGET_FEASIBILITY_TOOL,
                    callCount = 3,
                    argumentBytes = invalidFieldsArtifact().toByteArray().size.toLong() * 3,
                ),
            ),
            complete.captures,
        )
        assertEquals(
            (1..3).map { attempt ->
                WidgetAuthoringAttemptFailure(
                    stage = WidgetAuthoringStage.Feasibility,
                    attempt = attempt,
                    code = WidgetAuthoringStageFailureCode.InvalidFeasibilityFields,
                    argumentBytes = invalidFieldsArtifact().toByteArray().size.toLong(),
                )
            },
            complete.attemptFailures,
        )

        val encoded = report.toCanonicalJson()
        val controlledLog = report.controlledLogLines().joinToString("\n")
        assertTrue(encoded.contains("\"failureStage\":\"feasibility\""))
        assertTrue(encoded.contains("\"failureCode\":\"invalid_feasibility_fields\""))
        assertTrue(encoded.contains("\"attempt\":3"))
        assertTrue(controlledLog.contains("attemptFailure stage=feasibility attempt=3"))
        assertTrue(controlledLog.contains("code=invalid_feasibility_fields"))
        assertFalse(encoded.contains("raw-shape-marker"))
        assertFalse(controlledLog.contains("raw-shape-marker"))
        assertFalse(controlledLog.contains("private prompt marker"))
    }

    @Test
    fun `tool parsing failures are categorized without retaining exception text`() = runTest {
        val report = runner(DiagnosticFakeEngine(DiagnosticMode.ToolCallParsing))
            .run(model(), INFERENCE, prompt(), environment())

        assertFalse(report.overallPassed)
        assertEquals(List(5) { DIAGNOSTIC_TOOL_CALL_PARSING }, report.cases.take(5).map { it.outcome })
        assertEquals(DIAGNOSTIC_PIPELINE_INVALID, report.cases.last().outcome)
        assertTrue(report.cases.none { it.toolCallObserved })
        assertFalse(report.toCanonicalJson().contains(DiagnosticFakeEngine.RAW_EXCEPTION_MARKER))
    }

    @Test
    fun `load failure creates a bounded shareable report without running cases`() = runTest {
        val report = runner(DiagnosticFakeEngine(DiagnosticMode.LoadFailure))
            .run(model(), INFERENCE, prompt(), environment())

        assertEquals(DIAGNOSTIC_LOAD_FAILED, report.modelLoadOutcome)
        assertFalse(report.overallPassed)
        assertTrue(report.cases.isEmpty())
        assertTrue(report.toCanonicalJson().contains("\"loadOutcome\":\"model_load_failed\""))
    }

    @Test
    fun `matrix aborts after a callback timeout instead of reusing the runtime`() = runTest {
        val engine = DiagnosticFakeEngine(DiagnosticMode.Timeout)
        val report = runner(engine, timeoutMillis = 1_000)
            .run(model(), INFERENCE, prompt(), environment())

        assertTrue(report.abortedAfterTimeout)
        assertFalse(report.overallPassed)
        assertEquals(1, report.cases.size)
        assertEquals(DIAGNOSTIC_CASE_TIMEOUT, report.cases.single().outcome)
        assertEquals(1, engine.requests.size)
        assertTrue(report.toCanonicalJson().contains("\"abortedAfterTimeout\":true"))
    }

    private fun runner(
        engine: LocalLlmEngine,
        timeoutMillis: Long = WIDGET_AUTHORING_TIMEOUT_MILLIS,
    ) = WidgetToolCallingDiagnosticRunner(
        engine = engine,
        registry = registry(),
        javascriptEngine = DiagnosticJavaScriptEngine(),
        runtimeContextProvider = { RUNTIME },
        caseTimeoutMillis = timeoutMillis,
    )

    private fun model() = LocalModel(
        id = "gemma-e4b-candidate",
        name = "Gemma E4B candidate",
        filePath = "/models/e4b.litertlm",
    )

    private fun prompt(instruction: String = "Create a widget") = WidgetAuthoringPrompt(
        instruction,
        """{"authoringTask":{"mode":"create","instructionSemantics":"behavior"},"supportedIntervalsHours":[1,6,12,24],"supportedRuntimeValues":["locale","local_time","seed","timezone"],"supportedPresentation":["card","https_link","text"],"maxSourceBytes":32768,"programApi":{"randomSelection":"runtime.seededIndex(length)","irrelevantLaterStageDetail":"${"x".repeat(1024)}"},"tools":[{"id":"wikipedia_on_this_day","version":1,"displayName":"Wikipedia on this day","networkRequired":true,"inputSchema":{"type":"object"},"outputSchema":{"type":"object","properties":{"kind":{"type":"string"},"events":{"type":"array","items":{"type":"object","properties":{"year":{"type":"integer"},"text":{"type":"string"},"canonicalUrl":{"type":"string"}}}}}}}],"wikipediaBehaviorGuidance":{"toolId":"wikipedia_on_this_day","contractVersion":1,"selection":"runtime.seededIndex(events.length)"},"currentWidget":null}""",
    )

    private fun environment() = WidgetToolCallingDiagnosticEnvironment(
        generatedAtUtc = "2026-09-08T18:00:00Z",
        manufacturer = "Google",
        deviceModel = "Pixel",
        androidRelease = "16",
        sdkInt = 36,
        buildDisplay = "build",
        supportedAbis = listOf("arm64-v8a"),
        appVersion = "1.0",
        versionCode = 1,
        buildType = "debug",
        apkSha256 = "a".repeat(64),
        modelArtifactSha256 = "b".repeat(64),
    )

    private companion object {
        val INFERENCE = InferenceConfig(4_096, 128, 0.2f, 0.95f)
        val RUNTIME = WidgetRuntimeContext("en-CA", "America/Toronto", "2026-09-10T12:00:00-04:00", 42)
        val EXPECTED_STAGE_TOOLS = setOf(
            SUBMIT_WIDGET_FEASIBILITY_TOOL,
            SUBMIT_WIDGET_ALGORITHM_TOOL,
            SUBMIT_WIDGET_CALL_FUNCTION_TOOL,
            SUBMIT_WIDGET_PLAN_FUNCTION_TOOL,
            SUBMIT_WIDGET_RENDER_FUNCTION_TOOL,
        )
    }
}

private enum class DiagnosticMode {
    Pass,
    InvalidPipelineAlgorithm,
    InvalidPipelineFeasibilityFields,
    AlgorithmTimeout,
    ToolCallParsing,
    LoadFailure,
    Timeout,
}

private class DiagnosticFakeEngine(private val mode: DiagnosticMode) : LocalLlmEngine {
    var loadedInference: InferenceConfig? = null
    var loadCount = 0
    var unloadCount = 0
    val requests = mutableListOf<PromptRequest>()

    override suspend fun load(model: LocalModel, config: InferenceConfig) {
        if (mode == DiagnosticMode.LoadFailure) error(RAW_EXCEPTION_MARKER)
        loadCount += 1
        loadedInference = config
    }

    override fun generate(request: PromptRequest): Flow<GenerationEvent> = flow {
        requests += request
        when (mode) {
            DiagnosticMode.ToolCallParsing -> emit(
                GenerationEvent.Failed(RAW_EXCEPTION_MARKER, GenerationFailureKind.ToolCallParsing),
            )
            DiagnosticMode.Pass,
            DiagnosticMode.InvalidPipelineAlgorithm,
            DiagnosticMode.InvalidPipelineFeasibilityFields,
            DiagnosticMode.AlgorithmTimeout,
            -> {
                val toolName = request.ephemeralTools.single().name
                if (mode == DiagnosticMode.AlgorithmTimeout && toolName == SUBMIT_WIDGET_ALGORITHM_TOOL) {
                    kotlinx.coroutines.awaitCancellation()
                }
                val artifact = if (!request.plainChatPrompt.contains("Objective:")) {
                    "{\"value\":\"$RAW_ARGUMENT_MARKER\"}"
                } else if (
                    mode == DiagnosticMode.InvalidPipelineAlgorithm &&
                    toolName == SUBMIT_WIDGET_ALGORITHM_TOOL
                ) {
                    "{}"
                } else if (
                    mode == DiagnosticMode.InvalidPipelineFeasibilityFields &&
                    toolName == SUBMIT_WIDGET_FEASIBILITY_TOOL
                ) {
                    invalidFieldsArtifact()
                } else {
                    pipelineArtifact(toolName)
                }
                request.ephemeralTools.single().execute(artifact)
                emit(GenerationEvent.Completed)
            }
            DiagnosticMode.Timeout -> kotlinx.coroutines.awaitCancellation()
            DiagnosticMode.LoadFailure -> error("unreachable")
        }
    }

    override suspend fun unload() {
        unloadCount += 1
    }

    private fun pipelineArtifact(toolName: String): String = when (toolName) {
        SUBMIT_WIDGET_FEASIBILITY_TOOL -> feasibilityArtifact()
        SUBMIT_WIDGET_ALGORITHM_TOOL -> algorithmArtifact()
        SUBMIT_WIDGET_CALL_FUNCTION_TOOL -> fragmentArtifact(
            "events_call",
            "buildCallEventsCall",
            listOf("runtime"),
            CALL_SOURCE,
        )
        SUBMIT_WIDGET_PLAN_FUNCTION_TOOL -> fragmentArtifact("plan", "plan", listOf("runtime"), PLAN_SOURCE)
        SUBMIT_WIDGET_RENDER_FUNCTION_TOOL -> fragmentArtifact(
            "render",
            "render",
            listOf("runtime", "outcomes", "state"),
            RENDER_SOURCE,
        )
        else -> error("Unexpected tool $toolName")
    }

    companion object {
        const val RAW_ARGUMENT_MARKER = "raw-secret-argument"
        const val RAW_EXCEPTION_MARKER = "raw-secret-exception"
    }
}

private class DiagnosticJavaScriptEngine : WidgetJavaScriptEngine {
    override suspend fun call(
        source: String,
        entrypoint: String,
        argumentsJson: List<String>,
        limits: WidgetRequestedLimits,
    ): WidgetScriptResult = when (entrypoint) {
        "buildCallEventsCall" -> WidgetScriptResult.Success(VALID_CALL)
        "plan" -> WidgetScriptResult.Success("[$VALID_CALL]")
        "render" -> WidgetScriptResult.Success("""{"type":"text","text":"Synthetic","tone":"neutral"}""")
        else -> error("Unexpected entrypoint $entrypoint")
    }
}

private fun registry() = ApplicationToolRegistry(
    listOf(
        applicationToolBinding(
            contract = ApplicationToolContract(
                id = "wikipedia_on_this_day",
                version = 1,
                displayName = "Wikipedia on this day",
                category = ApplicationToolCategory.ExternalKnowledge,
                consumers = setOf(ApplicationToolConsumer.Widget),
                inputSchemaJson = """{"type":"object"}""",
                outputSchemaJson = """{"type":"object","properties":{"kind":{"type":"string"},"events":{"type":"array","items":{"type":"object","properties":{"year":{"type":"integer"},"text":{"type":"string"},"canonicalUrl":{"type":"string"}}}}}}""",
            ),
            state = { ApplicationToolOperationalState(enabled = true, ready = true) },
            executor = ApplicationTool(
                "Wikipedia on this day",
                ApplicationToolCategory.ExternalKnowledge,
            ) { _: Unit -> Unit },
            decodeArguments = { arguments ->
                Unit.takeIf {
                    arguments.keySet() == setOf("month", "day", "language") &&
                        arguments.get("month")?.asInt in 1..12 &&
                        arguments.get("day")?.asInt in 1..31 &&
                        arguments.get("language")?.asString == "en"
                }
            },
            encodeResult = { "{}" },
        ),
    ),
)

private fun feasibilityArtifact(): String = JsonObject().apply {
    addProperty("outcome", "achievable")
    addProperty("message", "Today in history")
    addProperty("periodicIntervalHours", 24)
    add("toolIds", JsonArray().apply { add("wikipedia_on_this_day") })
}.toString()

private fun invalidFieldsArtifact(): String = JsonParser.parseString(feasibilityArtifact()).asJsonObject.apply {
    addProperty("unexpectedField", "raw-shape-marker")
}.toString()

private fun algorithmArtifact(): String = JsonObject().apply {
    addProperty("protocolVersion", 1)
    add(
        "steps",
        JsonArray().apply {
            add(
                JsonObject().apply {
                    addProperty("id", "events_call")
                    addProperty("kind", "tool_call")
                    addProperty("objective", "Load events using execution-time date")
                    add("dependsOn", JsonArray())
                    addProperty("toolId", "wikipedia_on_this_day")
                    addProperty("contractVersion", 1)
                    add(
                        "runtimeInputs",
                        JsonArray().apply {
                            add("locale")
                            add("local_time")
                        },
                    )
                },
            )
        },
    )
    addProperty("presentationObjective", "Show one event")
}.toString()

private fun fragmentArtifact(id: String, name: String, inputs: List<String>, source: String): String = JsonObject().apply {
    addProperty("protocolVersion", 1)
    addProperty("artifactId", id)
    addProperty("functionName", name)
    add("inputNames", JsonArray().apply { inputs.forEach(::add) })
    addProperty("source", source)
}.toString()

private const val CALL_SOURCE = """function buildCallEventsCall(runtime) {
  const now = runtime.currentLocalDateTime();
  return {alias:'events_call',toolId:'wikipedia_on_this_day',contractVersion:1,arguments:{month:now.month,day:now.day,language:runtime.language}};
}"""
private const val PLAN_SOURCE = """function plan(runtime) {
  return [buildCallEventsCall(runtime)];
}"""
private const val RENDER_SOURCE = """function render(runtime, outcomes, state) {
  return {type:'text',text:'Synthetic',tone:'neutral'};
}"""
private const val VALID_CALL = """{"alias":"events_call","toolId":"wikipedia_on_this_day","contractVersion":1,"arguments":{"month":9,"day":10,"language":"en"}}"""
