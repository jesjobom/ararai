package com.jesjobom.ararai.widget.managed

import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.GenerationFailureKind
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.PromptChatRole
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelToolCapabilities
import com.jesjobom.ararai.model.WIDGET_AUTHORING_PIPELINE_V1
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetAuthoringPipelineModelTest {
    @Test
    fun `model without declared widget protocol cannot load for production authoring`() = runTest {
        val engine = RecordingStageEngine(wait = false)
        val controller = WidgetAuthoringPipelineModelController(engine)
        val unverifiedModel = MODEL.copy(toolCapabilities = ModelToolCapabilities())

        assertEquals(WidgetAuthoringModelPreparationResult.Ineligible, controller.prepare(unverifiedModel, INFERENCE))
        assertEquals(0, engine.loadCalls)
    }

    @Test
    fun `preparation uses fixed context output reserve and temperature without changing chat settings`() = runTest {
        val engine = RecordingStageEngine(wait = false)
        val controller = WidgetAuthoringPipelineModelController(engine)
        val chat = INFERENCE.copy(contextTokens = 2_048, promptReserveTokens = 256, temperature = 0.8f)

        assertEquals(WidgetAuthoringModelPreparationResult.Ready, controller.prepare(MODEL, chat))

        assertEquals(MAX_WIDGET_AUTHORING_CONTEXT_TOKENS, engine.loadedConfig?.contextTokens)
        assertEquals(WidgetAuthoringPipelinePolicy.OUTPUT_RESERVE_TOKENS, engine.loadedConfig?.promptReserveTokens)
        assertEquals(MAX_WIDGET_AUTHORING_TEMPERATURE, engine.loadedConfig?.temperature)
        assertEquals(2_048, chat.contextTokens)
        assertEquals(256, chat.promptReserveTokens)
    }

    @Test
    fun `generation boundary unloads waits and reloads with the fixed authoring configuration`() = runTest {
        val engine = RecordingStageEngine(wait = false)
        var recoveryCalls = 0
        val controller = WidgetAuthoringPipelineModelController(
            engine,
            recoveryGate = {
                recoveryCalls++
                true
            },
        )

        assertEquals(WidgetAuthoringModelPreparationResult.Ready, controller.prepare(MODEL, INFERENCE))
        assertEquals(WidgetAuthoringModelPreparationResult.Ready, controller.recoverForNextGeneration(MODEL, INFERENCE))

        assertEquals(2, engine.loadCalls)
        assertEquals(1, engine.unloadCalls)
        assertEquals(1, recoveryCalls)
        assertEquals(MAX_WIDGET_AUTHORING_CONTEXT_TOKENS, engine.loadedConfig?.contextTokens)
        assertEquals(WidgetAuthoringPipelinePolicy.OUTPUT_RESERVE_TOKENS, engine.loadedConfig?.promptReserveTokens)
        assertEquals(MAX_WIDGET_AUTHORING_TEMPERATURE, engine.loadedConfig?.temperature)
    }

    @Test
    fun `generation boundary remains unloaded when recovery times out`() = runTest {
        val engine = RecordingStageEngine(wait = false)
        val controller = WidgetAuthoringPipelineModelController(engine, recoveryGate = { false })
        controller.prepare(MODEL, INFERENCE)

        val result = controller.recoverForNextGeneration(MODEL, INFERENCE)

        assertEquals(WidgetAuthoringModelPreparationResult.RecoveryTimedOut, result)
        assertEquals(1, engine.loadCalls)
        assertEquals(1, engine.unloadCalls)
    }

    @Test
    fun `captured artifact is not accepted when generation later fails or ends without completion`() = runTest {
        val failedEngine = RecordingStageEngine(
            wait = false,
            terminal = GenerationEvent.Failed(
                "Model event delivery capacity exceeded",
                GenerationFailureKind.Unexpected,
            ),
        )
        val failedController = WidgetAuthoringPipelineModelController(failedEngine)
        failedController.prepare(MODEL, INFERENCE)
        assertEquals(
            WidgetAuthoringRoundResult.Failed(GenerationFailureKind.Unexpected),
            failedController.capture(MODEL, request()),
        )

        val missingTerminalEngine = RecordingStageEngine(wait = false, terminal = null)
        val missingTerminalController = WidgetAuthoringPipelineModelController(missingTerminalEngine)
        missingTerminalController.prepare(MODEL, INFERENCE)
        assertEquals(WidgetAuthoringRoundResult.NoArtifact, missingTerminalController.capture(MODEL, request()))
    }

    @Test
    fun `completed generation without artifact remains a missing artifact`() = runTest {
        val engine = RecordingStageEngine(wait = false, captureArtifact = false)
        val controller = WidgetAuthoringPipelineModelController(engine)
        controller.prepare(MODEL, INFERENCE)

        assertEquals(WidgetAuthoringRoundResult.NoArtifact, controller.capture(MODEL, request()))
    }

    @Test
    fun `stage refuses an input that cannot fit beside the reserved output`() = runTest {
        val engine = RecordingStageEngine(wait = false)
        val controller = WidgetAuthoringPipelineModelController(engine, maxContextTokens = 1_600)
        controller.prepare(MODEL, INFERENCE)

        val result = controller.capture(MODEL, request(context = "{\"value\":\"${"x".repeat(1_000)}\"}"))

        assertEquals(WidgetAuthoringRoundResult.InputTooLarge, result)
        assertTrue(engine.requests.isEmpty())
    }

    @Test
    fun `feasibility prompt explains the conditional outcome contract without freezing selections`() = runTest {
        val engine = RecordingStageEngine(wait = false)
        val controller = WidgetAuthoringPipelineModelController(engine)
        controller.prepare(MODEL, INFERENCE)

        controller.capture(MODEL, request())

        val system = engine.requests.single().chatMessages.single { it.role == PromptChatRole.System }.text
        assertTrue(system.contains("Return exactly four fields"))
        assertTrue(system.contains("achievable => message is a concise display title"))
        assertTrue(system.contains("unachievable => message is the reason"))
        assertTrue(system.contains("needs_clarification => message is one question"))
        assertTrue(system.contains("Return every required field"))
        assertFalse(system.contains("keep every identifier and capability fixed"))
        assertFalse(WidgetAuthoringStageSchemas.feasibility.contains("\"reason\""))
        assertFalse(WidgetAuthoringStageSchemas.feasibility.contains("\"clarificationQuestion\""))
    }

    @Test
    fun `feasibility repair turns a controlled outcome failure into actionable guidance`() = runTest {
        val engine = RecordingStageEngine(wait = false)
        val controller = WidgetAuthoringPipelineModelController(engine)
        controller.prepare(MODEL, INFERENCE)

        controller.capture(
            MODEL,
            request(
                repairCode = WidgetAuthoringStageFailureCode.InvalidFeasibilityOutcome,
                rejectedArtifact = "{}",
            ),
        )

        val prompt = engine.requests.single().plainChatPrompt
        assertTrue(prompt.contains("Controlled failure: invalid_feasibility_outcome"))
        assertTrue(prompt.contains("Keep outcome=achievable when the request is supported"))
        assertTrue(prompt.contains("Use message as its display name"))
        assertFalse(prompt.contains("Controlled failure: InvalidFeasibilityOutcome"))
    }

    @Test
    fun `source repair requires the current named function instead of another stage artifact`() = runTest {
        val engine = RecordingStageEngine(wait = false)
        val controller = WidgetAuthoringPipelineModelController(engine)
        controller.prepare(MODEL, INFERENCE)

        controller.capture(
            MODEL,
            request(
                repairCode = WidgetAuthoringStageFailureCode.InvalidSource,
                rejectedArtifact = "{}",
            ),
        )

        val prompt = engine.requests.single().plainChatPrompt
        assertTrue(prompt.contains("only source for the current stage"))
        assertTrue(prompt.contains("one named function declaration"))
        assertTrue(prompt.contains("every brace closed"))
        assertTrue(prompt.contains("do not use an anonymous function"))
        assertTrue(prompt.contains("do not use an anonymous function, arrow callback, or Math.random"))
    }

    @Test
    fun `tool argument repair rejects call wrappers and keeps runtime fields conditional`() {
        val request = WidgetAuthoringRoundRequest(
            stage = WidgetAuthoringStage.CallFunction,
            toolName = SUBMIT_WIDGET_CALL_FUNCTION_TOOL,
            toolDescriptionJson = WidgetAuthoringStageSchemas.sourceFragment(SUBMIT_WIDGET_CALL_FUNCTION_TOOL),
            objective = "Generate function buildCallEvents(runtime)",
            contextJson = "{}",
            repairCode = WidgetAuthoringStageFailureCode.InvalidToolArguments,
        )

        val prompt = request.toUserText()
        assertTrue(prompt.contains("bare tool input arguments object"))
        assertTrue(prompt.contains("Never return alias, toolId, contractVersion, args, or an arguments wrapper"))
        assertTrue(prompt.contains("only when those fields are required by the selected tool"))
        assertFalse(prompt.contains("Keep the frozen alias"))
    }

    @Test
    fun `render execution repair regenerates from generic contract without rejected source`() = runTest {
        val engine = RecordingStageEngine(wait = false)
        val controller = WidgetAuthoringPipelineModelController(engine)
        controller.prepare(MODEL, INFERENCE)
        val objective = renderFunctionObjective(
            listOf(
                WidgetSourceFragmentArtifact(
                    artifactId = "get_events",
                    functionName = "buildCallGetEvents",
                    inputNames = listOf("runtime"),
                    source = "function buildCallGetEvents(runtime) { return {}; }",
                ),
            ),
        )

        controller.capture(
            MODEL,
            WidgetAuthoringRoundRequest(
                stage = WidgetAuthoringStage.RenderFunction,
                toolName = SUBMIT_WIDGET_RENDER_FUNCTION_TOOL,
                toolDescriptionJson = WidgetAuthoringStageSchemas.sourceFragment(
                    SUBMIT_WIDGET_RENDER_FUNCTION_TOOL,
                ),
                objective = objective,
                contextJson = "{}",
                repairCode = WidgetAuthoringStageFailureCode.InvalidRenderExecution,
                rejectedArtifact = null,
            ),
        )

        val prompt = engine.requests.single().plainChatPrompt
        assertTrue(prompt.contains("Controlled failure: invalid_render_execution"))
        assertTrue(prompt.contains("Rejected artifact omitted to prevent anchoring"))
        assertTrue(prompt.contains("runtime.pickArrayItem(outcomes, alias, arrayPath"))
        assertTrue(prompt.contains("No Math.random"))
        assertTrue(prompt.contains("and an indexed loop"))
        assertTrue(prompt.contains("Do not build card or https_link objects manually"))
        assertTrue(prompt.contains("runtime.card([title, body, runtime.linkFor(outcomes, alias"))
        assertTrue(prompt.contains("ONE array argument"))
        assertTrue(prompt.contains("Never use card.addText, card.addLinkFor"))
        assertTrue(prompt.contains("or outcomes.getArray"))
        assertTrue(prompt.contains("Formatting a title must not replace a requested body or description"))
        assertTrue(prompt.contains("No Math.random, arrow functions, textNode, or collectionCard"))
        assertFalse(prompt.contains("get_events"))
        assertFalse(prompt.contains("return outcomes.get"))
    }

    @Test
    fun `failed outcome repair preserves custom source failure handling`() = runTest {
        val engine = RecordingStageEngine(wait = false)
        val controller = WidgetAuthoringPipelineModelController(engine)
        controller.prepare(MODEL, INFERENCE)

        controller.capture(
            MODEL,
            WidgetAuthoringRoundRequest(
                stage = WidgetAuthoringStage.RenderFunction,
                toolName = SUBMIT_WIDGET_RENDER_FUNCTION_TOOL,
                toolDescriptionJson = WidgetAuthoringStageSchemas.sourceFragment(SUBMIT_WIDGET_RENDER_FUNCTION_TOOL),
                objective = renderFunctionObjective(),
                contextJson = "{}",
                repairCode = WidgetAuthoringStageFailureCode.InvalidPresentationFailureHandling,
            ),
        )

        val prompt = engine.requests.single().plainChatPrompt
        assertTrue(prompt.contains("Handle failed and empty outcomes"))
        assertTrue(prompt.contains("runtime.pickArrayItem(outcomes, alias, arrayPath"))
        assertTrue(prompt.contains("runtime.isSuccess(outcomes, alias)"))
        assertFalse(prompt.contains("For an array card, use runtime.collectionCard"))
    }

    @Test
    fun `provenance repair requires linkFor node rather than manually assembled link`() = runTest {
        val engine = RecordingStageEngine(wait = false)
        val controller = WidgetAuthoringPipelineModelController(engine)
        controller.prepare(MODEL, INFERENCE)

        controller.capture(
            MODEL,
            WidgetAuthoringRoundRequest(
                stage = WidgetAuthoringStage.RenderFunction,
                toolName = SUBMIT_WIDGET_RENDER_FUNCTION_TOOL,
                toolDescriptionJson = WidgetAuthoringStageSchemas.sourceFragment(SUBMIT_WIDGET_RENDER_FUNCTION_TOOL),
                objective = renderFunctionObjective(),
                contextJson = "{}",
                repairCode = WidgetAuthoringStageFailureCode.InvalidPresentationProvenance,
            ),
        )

        val prompt = engine.requests.single().plainChatPrompt
        assertTrue(prompt.contains("Controlled failure: invalid_presentation_provenance"))
        assertTrue(prompt.contains("runtime.linkFor(outcomes, alias, exactItemUrlPath, label)"))
        assertTrue(prompt.contains("Do not build card or https_link objects manually"))
    }

    @Test
    fun `typed card repair does not ask the model to generate JavaScript`() = runTest {
        val engine = RecordingStageEngine(wait = false)
        val controller = WidgetAuthoringPipelineModelController(engine)
        controller.prepare(MODEL, INFERENCE)

        controller.capture(
            MODEL,
            WidgetAuthoringRoundRequest(
                stage = WidgetAuthoringStage.RenderFunction,
                toolName = SUBMIT_WIDGET_COLLECTION_CARD_TOOL,
                toolDescriptionJson = WidgetAuthoringStageSchemas.collectionCard,
                objective = collectionCardObjective(),
                contextJson = "{}",
                repairCode = WidgetAuthoringStageFailureCode.InvalidPresentationFailureHandling,
            ),
        )

        val prompt = engine.requests.single().plainChatPrompt
        assertTrue(prompt.contains("exact itemFields property names"))
        assertTrue(prompt.contains("Only fallback is literal text"))
        assertTrue(prompt.contains("Do not write JavaScript"))
        assertFalse(prompt.contains("Final source check"))
        assertFalse(prompt.contains("function render(runtime"))
    }

    @Test
    fun `repair artifact policy keeps only localized first repair`() {
        val artifact = "{\"source\":\"rejected\"}"

        assertEquals(
            artifact,
            repairArtifactForPrompt(
                WidgetAuthoringStageFailureCode.InvalidPresentationTone,
                artifact,
                repairAttemptNumber = 2,
            ),
        )
        assertEquals(
            null,
            repairArtifactForPrompt(
                WidgetAuthoringStageFailureCode.InvalidRenderExecution,
                artifact,
                repairAttemptNumber = 2,
            ),
        )
        assertEquals(
            null,
            repairArtifactForPrompt(
                WidgetAuthoringStageFailureCode.InvalidPresentationTone,
                artifact,
                repairAttemptNumber = 3,
            ),
        )
    }

    @Test
    fun `debug hooks receive the exact stage request and captured arguments`() = runTest {
        val engine = RecordingStageEngine(wait = false)
        var observedRequest: WidgetAuthoringRoundRequest? = null
        var observedArguments: String? = null
        var observedLifecycle: WidgetAuthoringRoundLifecycle? = null
        val controller = WidgetAuthoringPipelineModelController(
            engine = engine,
            onRoundRequest = { observedRequest = it },
            onRawCapture = { _, arguments -> observedArguments = arguments },
            onRoundLifecycle = { observedLifecycle = it },
        )
        controller.prepare(MODEL, INFERENCE)

        controller.capture(MODEL, request(context = "{\"private\":true}"))

        assertEquals("{\"private\":true}", observedRequest?.contextJson)
        assertEquals(RecordingStageEngine.CAPTURED_ARGUMENTS, observedArguments)
        assertEquals(WidgetAuthoringRoundOutcome.Captured, observedLifecycle?.outcome)
        assertEquals(WidgetAuthoringStage.Feasibility, observedLifecycle?.stage)
        assertEquals(false, observedLifecycle?.isRepair)
        assertTrue(observedLifecycle?.toolCaptureMillis != null)
        assertTrue(observedLifecycle?.terminalEventMillis != null)
        assertEquals(null, observedLifecycle?.watchdogMillis)
        assertEquals(null, observedLifecycle?.cleanupOverrunMillis)
    }

    @Test
    fun `stage lifecycle records watchdog return and cleanup without content`() = runTest {
        val engine = RecordingStageEngine(wait = true)
        var observedLifecycle: WidgetAuthoringRoundLifecycle? = null
        val controller = WidgetAuthoringPipelineModelController(
            engine = engine,
            generationTimeoutMillis = 1,
            onRoundLifecycle = { observedLifecycle = it },
        )
        controller.prepare(MODEL, INFERENCE)

        assertEquals(WidgetAuthoringRoundResult.TimedOut, controller.capture(MODEL, request()))

        assertEquals(WidgetAuthoringRoundOutcome.TimedOut, observedLifecycle?.outcome)
        assertEquals(null, observedLifecycle?.firstGenerationEventMillis)
        assertEquals(null, observedLifecycle?.toolCaptureMillis)
        assertEquals(null, observedLifecycle?.terminalEventMillis)
        assertEquals(1L, observedLifecycle?.watchdogMillis)
        assertTrue(requireNotNull(observedLifecycle).returnMillis >= 0)
        assertTrue(requireNotNull(observedLifecycle.cleanupOverrunMillis) >= 0)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `caller cancellation releases the active fresh stage conversation`() = runTest {
        val engine = RecordingStageEngine(wait = true)
        val controller = WidgetAuthoringPipelineModelController(engine)
        controller.prepare(MODEL, INFERENCE)
        val job = launch { controller.capture(MODEL, request()) }
        runCurrent()

        job.cancel(CancellationException("navigation"))
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        assertTrue(engine.released)
        assertEquals(null, engine.requests.single().chatSessionId)
    }

    private fun request(
        context: String = "{}",
        repairCode: WidgetAuthoringStageFailureCode? = null,
        rejectedArtifact: String? = null,
    ) = WidgetAuthoringRoundRequest(
        stage = WidgetAuthoringStage.Feasibility,
        toolName = SUBMIT_WIDGET_FEASIBILITY_TOOL,
        toolDescriptionJson = WidgetAuthoringStageSchemas.feasibility,
        objective = "Analyze feasibility",
        contextJson = context,
        repairCode = repairCode,
        rejectedArtifact = rejectedArtifact,
    )

    private class RecordingStageEngine(
        private val wait: Boolean,
        private val terminal: GenerationEvent? = GenerationEvent.Completed,
        private val captureArtifact: Boolean = true,
    ) : LocalLlmEngine {
        var loadedConfig: InferenceConfig? = null
        var loadCalls = 0
        var unloadCalls = 0
        var released = false
        val requests = mutableListOf<PromptRequest>()

        override suspend fun load(model: LocalModel, config: InferenceConfig) {
            loadCalls++
            loadedConfig = config
        }

        companion object {
            const val CAPTURED_ARGUMENTS = "{\"protocolVersion\":1}"
        }

        override fun generate(request: PromptRequest): Flow<GenerationEvent> = flow {
            requests += request
            try {
                if (wait) {
                    awaitCancellation()
                } else {
                    if (captureArtifact) request.ephemeralTools.single().execute(CAPTURED_ARGUMENTS)
                    terminal?.let { emit(it) }
                }
            } finally {
                released = true
            }
        }

        override suspend fun unload() {
            unloadCalls++
        }
    }

    companion object {
        private val MODEL = LocalModel(
            id = "pipeline",
            name = "Pipeline",
            filePath = "/models/pipeline.litertlm",
            toolCapabilities = ModelToolCapabilities(
                authoringProtocolNames = setOf(WIDGET_AUTHORING_PIPELINE_V1),
            ),
        )
        private val INFERENCE = InferenceConfig(4_096, 512, 0.7f, 0.9f)
    }
}
