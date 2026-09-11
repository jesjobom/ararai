package com.jesjobom.ararai.widget.managed

import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.LocalLlmEngine
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

    private fun request(context: String = "{}") = WidgetAuthoringRoundRequest(
        stage = WidgetAuthoringStage.Feasibility,
        toolName = SUBMIT_WIDGET_FEASIBILITY_TOOL,
        toolDescriptionJson = WidgetAuthoringStageSchemas.feasibility,
        objective = "Analyze feasibility",
        contextJson = context,
    )

    private class RecordingStageEngine(private val wait: Boolean) : LocalLlmEngine {
        var loadedConfig: InferenceConfig? = null
        var released = false
        val requests = mutableListOf<PromptRequest>()

        override suspend fun load(model: LocalModel, config: InferenceConfig) {
            loadedConfig = config
        }

        override fun generate(request: PromptRequest): Flow<GenerationEvent> = flow {
            requests += request
            try {
                if (wait) awaitCancellation() else emit(GenerationEvent.Completed)
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
