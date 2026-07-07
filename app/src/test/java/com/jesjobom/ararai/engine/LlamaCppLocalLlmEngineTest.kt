package com.jesjobom.ararai.engine

import app.cash.turbine.test
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LlamaCppLocalLlmEngineTest {
    private val model = LocalModel(
        id = "test",
        name = "Test",
        filePath = "/tmp/test.gguf",
    )
    private val config = InferenceConfig(
        contextTokens = 128,
        temperature = 0.7f,
        topP = 0.9f,
    )

    @Test
    fun `loads model through native bridge with inference config`() = runTest {
        val bridge = RecordingBridge()
        val engine = LlamaCppLocalLlmEngine(bridge = bridge)

        engine.load(model, config)

        assertEquals("/tmp/test.gguf", bridge.loadedPath)
        assertEquals(128, bridge.loadedContextTokens)
        assertEquals(0.7f, bridge.loadedTemperature)
        assertEquals(0.9f, bridge.loadedTopP)
    }

    @Test
    fun `emits generated native tokens and completion`() = runTest {
        val bridge = RecordingBridge(tokens = listOf("ola", " mundo"))
        val engine = LlamaCppLocalLlmEngine(bridge = bridge)
        engine.load(model, config)

        engine.generate(PromptRequest("oi")).test {
            assertEquals(GenerationEvent.Token("ola"), awaitItem())
            assertEquals(GenerationEvent.Token(" mundo"), awaitItem())
            assertEquals(GenerationEvent.Completed, awaitItem())
            awaitComplete()
        }

        assertEquals("oi", bridge.generatedPrompt)
    }

    @Test
    fun `uses configured max tokens for native generation`() = runTest {
        val bridge = RecordingBridge(tokens = listOf("ok"))
        val engine = LlamaCppLocalLlmEngine(bridge = bridge)
        engine.load(model, config.copy(maxTokens = 384))

        engine.generate(PromptRequest("oi")).test {
            assertEquals(GenerationEvent.Token("ok"), awaitItem())
            assertEquals(GenerationEvent.Completed, awaitItem())
            awaitComplete()
        }

        assertEquals(384, bridge.generatedMaxTokens)
    }

    @Test
    fun `updates max tokens when same model is already loaded`() = runTest {
        val bridge = RecordingBridge(tokens = listOf("ok"))
        val engine = LlamaCppLocalLlmEngine(bridge = bridge)
        engine.load(model, config.copy(maxTokens = 128))
        engine.load(model, config.copy(maxTokens = 768))

        engine.generate(PromptRequest("oi")).test {
            assertEquals(GenerationEvent.Token("ok"), awaitItem())
            assertEquals(GenerationEvent.Completed, awaitItem())
            awaitComplete()
        }

        assertEquals(1, bridge.loadCount)
        assertEquals(768, bridge.generatedMaxTokens)
    }

    @Test
    fun `formats prompt with chat template before native generation`() = runTest {
        val bridge = RecordingBridge(
            tokens = listOf("ok"),
            formattedPrompt = "<chat><user>oi</user><assistant>",
        )
        val engine = LlamaCppLocalLlmEngine(bridge = bridge)
        engine.load(model, config)

        engine.generate(PromptRequest("oi")).test {
            assertEquals(GenerationEvent.Token("ok"), awaitItem())
            assertEquals(GenerationEvent.Completed, awaitItem())
            awaitComplete()
        }

        assertEquals("oi", bridge.formatRequestedPrompt)
        assertEquals("<chat><user>oi</user><assistant>", bridge.generatedPrompt)
    }

    @Test
    fun `falls back to raw prompt when chat template is unavailable`() = runTest {
        val bridge = RecordingBridge(tokens = listOf("ok"), formattedPrompt = null)
        val engine = LlamaCppLocalLlmEngine(bridge = bridge)
        engine.load(model, config)

        engine.generate(PromptRequest("oi")).test {
            assertEquals(GenerationEvent.Token("ok"), awaitItem())
            assertEquals(GenerationEvent.Completed, awaitItem())
            awaitComplete()
        }

        assertEquals("oi", bridge.generatedPrompt)
    }

    @Test
    fun `emits failure when native bridge returns generation error`() = runTest {
        val bridge = RecordingBridge(generationError = "native failed")
        val engine = LlamaCppLocalLlmEngine(bridge = bridge)
        engine.load(model, config)

        engine.generate(PromptRequest("oi")).test {
            assertEquals(GenerationEvent.Failed("native failed"), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `unloads loaded native handle`() = runTest {
        val bridge = RecordingBridge()
        val engine = LlamaCppLocalLlmEngine(bridge = bridge)

        engine.load(model, config)
        engine.unload()

        assertEquals(listOf(42L), bridge.unloadedHandles)
    }

    @Test
    fun `fails generation before load`() = runTest {
        val engine = LlamaCppLocalLlmEngine(bridge = RecordingBridge())

        engine.generate(PromptRequest("oi")).test {
            assertEquals(GenerationEvent.Failed("Model is not loaded"), awaitItem())
            awaitComplete()
        }
    }

    private class RecordingBridge(
        private val tokens: List<String> = emptyList(),
        private val generationError: String? = null,
        private val formattedPrompt: String? = null,
    ) : LlamaNativeBridge {
        var loadedPath: String? = null
            private set
        var loadedContextTokens: Int? = null
            private set
        var loadedTemperature: Float? = null
            private set
        var loadedTopP: Float? = null
            private set
        var loadCount: Int = 0
            private set
        var generatedPrompt: String? = null
            private set
        var generatedMaxTokens: Int? = null
            private set
        var formatRequestedPrompt: String? = null
            private set
        val unloadedHandles = mutableListOf<Long>()
        val cancelledHandles = mutableListOf<Long>()

        override fun loadModel(
            modelPath: String,
            contextTokens: Int,
            temperature: Float,
            topP: Float,
        ): Long {
            loadCount += 1
            loadedPath = modelPath
            loadedContextTokens = contextTokens
            loadedTemperature = temperature
            loadedTopP = topP
            return 42L
        }

        override fun formatChatPrompt(handle: Long, prompt: String): String? {
            formatRequestedPrompt = prompt
            return formattedPrompt
        }

        override fun generate(
            handle: Long,
            prompt: String,
            maxTokens: Int,
            callback: LlamaTokenCallback,
        ): String? {
            generatedPrompt = prompt
            generatedMaxTokens = maxTokens
            for (token in tokens) {
                if (!callback.onToken(token)) {
                    cancel(handle)
                    return null
                }
            }
            return generationError
        }

        override fun cancel(handle: Long) {
            cancelledHandles += handle
        }

        override fun unloadModel(handle: Long) {
            unloadedHandles += handle
        }
    }

}
