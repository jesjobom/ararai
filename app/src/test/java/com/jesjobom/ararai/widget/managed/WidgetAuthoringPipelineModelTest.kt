package com.jesjobom.ararai.widget.managed

import com.jesjobom.ararai.engine.GenerationEvent
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
        assertTrue(system.contains("achievable => message is the display name"))
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

    private class RecordingStageEngine(private val wait: Boolean) : LocalLlmEngine {
        var loadedConfig: InferenceConfig? = null
        var released = false
        val requests = mutableListOf<PromptRequest>()

        override suspend fun load(model: LocalModel, config: InferenceConfig) {
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
                    request.ephemeralTools.single().execute(CAPTURED_ARGUMENTS)
                    emit(GenerationEvent.Completed)
                }
            } finally {
                released = true
            }
        }

        override suspend fun unload() = Unit
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
