package com.jesjobom.ararai.engine

import app.cash.turbine.test
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelRuntime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ConfiguredLocalLlmEngineTest {
    private val config = InferenceConfig(
        contextTokens = 128,
        temperature = 0.7f,
        topP = 0.9f,
    )

    @Test
    fun `delegates llama cpp models to llama cpp engine`() = runTest {
        val llamaCppEngine = RecordingEngine(
            events = listOf(GenerationEvent.Token("ok"), GenerationEvent.Completed),
        )
        val engine = ConfiguredLocalLlmEngine(llamaCppEngine = llamaCppEngine)
        val model = LocalModel(
            id = "llama",
            name = "Llama",
            filePath = "/tmp/llama.gguf",
            runtime = ModelRuntime.LlamaCpp,
        )

        engine.load(model, config)

        assertEquals(model, llamaCppEngine.loadedModel)
        engine.generate(PromptRequest("oi")).test {
            assertEquals(GenerationEvent.Token("ok"), awaitItem())
            assertEquals(GenerationEvent.Completed, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `fails fast for litert lm until runtime is implemented`() = runTest {
        val llamaCppEngine = RecordingEngine()
        val engine = ConfiguredLocalLlmEngine(llamaCppEngine = llamaCppEngine)
        val model = LocalModel(
            id = "gemma-litert",
            name = "Gemma LiteRT",
            filePath = "/tmp/gemma.task",
            runtime = ModelRuntime.LiteRtLm,
        )

        try {
            engine.load(model, config)
            fail("Expected LiteRT-LM load to throw")
        } catch (error: IllegalStateException) {
            assertEquals("LiteRT-LM runtime is not implemented yet", error.message)
        }

        assertEquals(null, llamaCppEngine.loadedModel)
    }

    private class RecordingEngine(
        private val events: List<GenerationEvent> = emptyList(),
    ) : LocalLlmEngine {
        var loadedModel: LocalModel? = null
            private set

        override suspend fun load(model: LocalModel, config: InferenceConfig) {
            loadedModel = model
        }

        override fun generate(request: PromptRequest): Flow<GenerationEvent> =
            flowOf(*events.toTypedArray())

        override suspend fun unload() = Unit
    }
}
