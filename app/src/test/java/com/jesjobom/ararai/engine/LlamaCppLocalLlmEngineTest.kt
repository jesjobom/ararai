package com.jesjobom.ararai.engine

import app.cash.turbine.test
import com.jesjobom.ararai.chat.ImageAttachment
import com.jesjobom.ararai.chat.MessageContent
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelAccelerationPolicy
import com.jesjobom.ararai.model.ModelRuntime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.util.ArrayDeque

class LlamaCppLocalLlmEngineTest {
    private val model =
        LocalModel(
            id = "test",
            name = "Test",
            filePath = "/tmp/test.gguf",
        )
    private val config =
        InferenceConfig(
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
        assertEquals(40, bridge.loadedTopK)
        assertEquals(0.05f, bridge.loadedMinP)
        assertEquals(1.10f, bridge.loadedRepeatPenalty)
    }

    @Test
    fun `requests conservative gpu layer offload by default`() = runTest {
        val bridge = RecordingBridge()
        val engine = LlamaCppLocalLlmEngine(bridge = bridge)

        engine.load(model, config)

        assertEquals(8, bridge.loadedGpuLayerCount)
    }

    @Test
    fun `requests configured gpu layer offload`() = runTest {
        val bridge = RecordingBridge()
        val engine = LlamaCppLocalLlmEngine(bridge = bridge)

        engine.load(model.copy(gpuLayerCount = 16), config)

        assertEquals(16, bridge.loadedGpuLayerCount)
    }

    @Test
    fun `loads cpu only models without gpu offload`() = runTest {
        val bridge = RecordingBridge()
        val engine = LlamaCppLocalLlmEngine(bridge = bridge)

        engine.load(
            model.copy(acceleration = ModelAccelerationPolicy.CpuOnly),
            config,
        )

        assertEquals(0, bridge.loadedGpuLayerCount)
    }

    @Test
    fun `rejects unsupported runtime before native load`() = runTest {
        val bridge = RecordingBridge()
        val engine = LlamaCppLocalLlmEngine(bridge = bridge)

        try {
            engine.load(model.copy(runtime = ModelRuntime.LiteRtLm), config)
            fail("Expected unsupported runtime to throw")
        } catch (error: IllegalStateException) {
            assertEquals("Unsupported local model runtime: LiteRT-LM", error.message)
        }

        assertEquals(0, bridge.loadCount)
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

        assertEquals("User: oi\nAssistant:", bridge.generatedPrompt)
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
    fun `reuses same model when native load configuration is unchanged`() = runTest {
        val bridge = RecordingBridge()
        val engine = LlamaCppLocalLlmEngine(bridge = bridge)

        engine.load(model, config)
        engine.load(model, config)

        assertEquals(1, bridge.loadCount)
        assertEquals(emptyList<Long>(), bridge.unloadedHandles)
    }

    @Test
    fun `reloads same model when any native load parameter changes`() = runTest {
        val changedConfigs =
            listOf(
                config.copy(contextTokens = config.contextTokens + 1),
                config.copy(temperature = config.temperature + 0.1f),
                config.copy(topP = config.topP - 0.1f),
                config.copy(topK = config.topK + 1),
                config.copy(minP = config.minP + 0.01f),
                config.copy(repeatPenalty = config.repeatPenalty + 0.1f),
            )

        changedConfigs.forEach { changedConfig ->
            val bridge = RecordingBridge()
            val engine = LlamaCppLocalLlmEngine(bridge = bridge)

            engine.load(model, config)
            engine.load(model, changedConfig)

            assertEquals("Expected reload for $changedConfig", 2, bridge.loadCount)
            assertEquals("Expected old handle unload for $changedConfig", listOf(42L), bridge.unloadedHandles)
        }
    }

    @Test
    fun `reloads chat model with stable benchmark configuration`() = runTest {
        val bridge = RecordingBridge()
        val engine = LlamaCppLocalLlmEngine(bridge = bridge)
        val chatConfig = config.copy(contextTokens = 4096, temperature = 0.7f, topP = 0.95f)
        val benchmarkConfig = chatConfig.copy(contextTokens = 2048, maxTokens = 128, temperature = 0.2f, topP = 0.9f)

        engine.load(model, chatConfig)
        engine.load(model, benchmarkConfig)

        assertEquals(2, bridge.loadCount)
        assertEquals(2048, bridge.loadedContextTokens)
        assertEquals(0.2f, bridge.loadedTemperature)
        assertEquals(0.9f, bridge.loadedTopP)
    }

    @Test
    fun `reloads same model when acceleration policy changes`() = runTest {
        val bridge = RecordingBridge()
        val engine = LlamaCppLocalLlmEngine(bridge = bridge)

        engine.load(model, config)
        engine.load(model.copy(acceleration = ModelAccelerationPolicy.CpuOnly), config)

        assertEquals(2, bridge.loadCount)
        assertEquals(listOf(8, 0), bridge.loadedGpuLayerCounts)
        assertEquals(listOf(42L), bridge.unloadedHandles)
    }

    @Test
    fun `reloads same model when gpu layer budget changes`() = runTest {
        val bridge = RecordingBridge()
        val engine = LlamaCppLocalLlmEngine(bridge = bridge)

        engine.load(model.copy(gpuLayerCount = 8), config)
        engine.load(model.copy(gpuLayerCount = 16), config)

        assertEquals(2, bridge.loadCount)
        assertEquals(listOf(8, 16), bridge.loadedGpuLayerCounts)
        assertEquals(listOf(42L), bridge.unloadedHandles)
    }

    @Test
    fun `formats prompt with chat template before native generation`() = runTest {
        val bridge =
            RecordingBridge(
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

        assertEquals(listOf(PromptChatMessage(PromptChatRole.User, "oi")), bridge.formatRequestedMessages)
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

        assertEquals("User: oi\nAssistant:", bridge.generatedPrompt)
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
    fun `rejects image request before native generation`() = runTest {
        val bridge = RecordingBridge(tokens = listOf("unexpected"))
        val engine = LlamaCppLocalLlmEngine(bridge = bridge)
        engine.load(model, config)

        engine
            .generate(
                PromptRequest(
                    MessageContent.TextPrompt(
                        text = "describe",
                        imageAttachments = listOf(ImageAttachment("file:///tmp/image.png", "image/png")),
                    ),
                ),
            ).test {
                assertEquals(
                    GenerationEvent.Failed("Selected llama.cpp model does not support image or audio input"),
                    awaitItem(),
                )
                awaitComplete()
            }

        assertEquals(emptyList<Long>(), bridge.generatedHandles)
    }

    @Test
    fun `retries cpu only when gpu generation returns invalid logits before tokens`() = runTest {
        val bridge =
            RecordingBridge(
                generationSteps =
                ArrayDeque(
                    listOf(
                        GenerationStep(error = "Native sampler received invalid logits"),
                        GenerationStep(tokens = listOf("ok")),
                    ),
                ),
            )
        val engine = LlamaCppLocalLlmEngine(bridge = bridge)
        engine.load(model, config)

        engine.generate(PromptRequest("oi")).test {
            assertEquals(GenerationEvent.Token("ok"), awaitItem())
            assertEquals(GenerationEvent.Completed, awaitItem())
            awaitComplete()
        }

        assertEquals(listOf(8, 0), bridge.loadedGpuLayerCounts)
        assertEquals(listOf(42L), bridge.unloadedHandles)
        assertEquals(listOf(42L, 43L), bridge.generatedHandles)
    }

    @Test
    fun `reports cpu fallback failure when retry also returns invalid logits`() = runTest {
        val bridge =
            RecordingBridge(
                generationSteps =
                ArrayDeque(
                    listOf(
                        GenerationStep(error = "Native sampler received invalid logits"),
                        GenerationStep(error = "Native sampler received invalid logits"),
                    ),
                ),
            )
        val engine = LlamaCppLocalLlmEngine(bridge = bridge)
        engine.load(model, config)

        engine.generate(PromptRequest("oi")).test {
            assertEquals(
                GenerationEvent.Failed("CPU fallback failed: Native sampler received invalid logits"),
                awaitItem(),
            )
            awaitComplete()
        }

        assertEquals(listOf(8, 0), bridge.loadedGpuLayerCounts)
    }

    @Test
    fun `does not retry cpu only after native generation emits tokens`() = runTest {
        val bridge =
            RecordingBridge(
                tokens = listOf("partial"),
                generationError = "Native sampler received invalid logits",
            )
        val engine = LlamaCppLocalLlmEngine(bridge = bridge)
        engine.load(model, config)

        engine.generate(PromptRequest("oi")).test {
            assertEquals(GenerationEvent.Token("partial"), awaitItem())
            assertEquals(GenerationEvent.Failed("Native sampler received invalid logits"), awaitItem())
            awaitComplete()
        }

        assertEquals(listOf(8), bridge.loadedGpuLayerCounts)
        assertEquals(1, bridge.loadCount)
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
        private val generationSteps: ArrayDeque<GenerationStep> = ArrayDeque(),
    ) : LlamaNativeBridge {
        var loadedPath: String? = null
            private set
        var loadedContextTokens: Int? = null
            private set
        var loadedTemperature: Float? = null
            private set
        var loadedTopP: Float? = null
            private set
        var loadedTopK: Int? = null
            private set
        var loadedMinP: Float? = null
            private set
        var loadedRepeatPenalty: Float? = null
            private set
        var loadedGpuLayerCount: Int? = null
            private set
        val loadedGpuLayerCounts = mutableListOf<Int>()
        var loadCount: Int = 0
            private set
        var generatedPrompt: String? = null
            private set
        var generatedMaxTokens: Int? = null
            private set
        val generatedHandles = mutableListOf<Long>()
        var formatRequestedMessages: List<PromptChatMessage>? = null
            private set
        val unloadedHandles = mutableListOf<Long>()
        val cancelledHandles = mutableListOf<Long>()

        override fun loadModel(
            modelPath: String,
            contextTokens: Int,
            temperature: Float,
            topP: Float,
            topK: Int,
            minP: Float,
            repeatPenalty: Float,
            gpuLayerCount: Int,
        ): Long {
            loadCount += 1
            loadedPath = modelPath
            loadedContextTokens = contextTokens
            loadedTemperature = temperature
            loadedTopP = topP
            loadedTopK = topK
            loadedMinP = minP
            loadedRepeatPenalty = repeatPenalty
            loadedGpuLayerCount = gpuLayerCount
            loadedGpuLayerCounts += gpuLayerCount
            return 41L + loadCount
        }

        override fun formatChatPrompt(
            handle: Long,
            messages: List<PromptChatMessage>,
        ): String? {
            formatRequestedMessages = messages
            return formattedPrompt
        }

        override fun generate(
            handle: Long,
            prompt: String,
            maxTokens: Int,
            callback: LlamaTokenCallback,
        ): String? {
            generatedHandles += handle
            generatedPrompt = prompt
            generatedMaxTokens = maxTokens
            val step =
                if (generationSteps.isEmpty()) {
                    GenerationStep(tokens = tokens, error = generationError)
                } else {
                    generationSteps.removeFirst()
                }
            for (token in step.tokens) {
                if (!callback.onToken(token)) {
                    cancel(handle)
                    return null
                }
            }
            return step.error
        }

        override fun cancel(handle: Long) {
            cancelledHandles += handle
        }

        override fun unloadModel(handle: Long) {
            unloadedHandles += handle
        }
    }

    private data class GenerationStep(
        val tokens: List<String> = emptyList(),
        val error: String? = null,
    )
}
