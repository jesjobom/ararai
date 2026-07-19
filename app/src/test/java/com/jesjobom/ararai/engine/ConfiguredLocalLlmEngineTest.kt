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
    private val config =
        InferenceConfig(
            contextTokens = 128,
            temperature = 0.7f,
            topP = 0.9f,
        )

    @Test
    fun `delegates llama cpp models to llama cpp engine`() = runTest {
        val llamaCppEngine =
            RecordingEngine(
                events = listOf(GenerationEvent.Token("ok"), GenerationEvent.Completed),
            )
        val engine = ConfiguredLocalLlmEngine(llamaCppEngine = llamaCppEngine)
        val model =
            LocalModel(
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
    fun `delegates litert lm models to litert lm engine`() = runTest {
        val llamaCppEngine = RecordingEngine()
        val liteRtLmEngine =
            RecordingEngine(
                events = listOf(GenerationEvent.Token("gemma"), GenerationEvent.Completed),
            )
        val engine =
            ConfiguredLocalLlmEngine(
                llamaCppEngine = llamaCppEngine,
                liteRtLmEngine = liteRtLmEngine,
            )
        val model =
            LocalModel(
                id = "gemma-litert",
                name = "Gemma LiteRT",
                filePath = "/tmp/gemma.task",
                runtime = ModelRuntime.LiteRtLm,
            )

        engine.load(model, config)

        assertEquals(null, llamaCppEngine.loadedModel)
        assertEquals(model, liteRtLmEngine.loadedModel)
        engine.generate(PromptRequest("oi")).test {
            assertEquals(GenerationEvent.Token("gemma"), awaitItem())
            assertEquals(GenerationEvent.Completed, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `unloads previous runtime before switching engines`() = runTest {
        val llamaCppEngine = RecordingEngine()
        val liteRtLmEngine = RecordingEngine()
        val engine = ConfiguredLocalLlmEngine(llamaCppEngine, liteRtLmEngine)
        val llama = LocalModel("llama", "Llama", "/tmp/llama.gguf", runtime = ModelRuntime.LlamaCpp)
        val gemma = LocalModel("gemma", "Gemma", "/tmp/gemma.litertlm", runtime = ModelRuntime.LiteRtLm)

        engine.load(llama, config)
        engine.load(gemma, config)

        assertEquals(1, llamaCppEngine.unloadCalls)
        assertEquals(gemma, liteRtLmEngine.loadedModel)
    }

    private class RecordingEngine(
        private val events: List<GenerationEvent> = emptyList(),
    ) : LocalLlmEngine {
        var loadedModel: LocalModel? = null
            private set
        var unloadCalls: Int = 0
            private set

        override suspend fun load(
            model: LocalModel,
            config: InferenceConfig,
        ) {
            loadedModel = model
        }

        override fun generate(request: PromptRequest): Flow<GenerationEvent> = flowOf(*events.toTypedArray())

        override suspend fun unload() {
            unloadCalls += 1
        }
    }
}
