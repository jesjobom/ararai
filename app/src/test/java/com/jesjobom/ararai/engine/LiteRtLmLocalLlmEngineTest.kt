package com.jesjobom.ararai.engine

import app.cash.turbine.test
import com.jesjobom.ararai.chat.AudioPrompt
import com.jesjobom.ararai.chat.ImageAttachment
import com.jesjobom.ararai.chat.MessageContent
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelAccelerationPolicy
import com.jesjobom.ararai.model.ModelInputCapabilities
import com.jesjobom.ararai.model.ModelRuntime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LiteRtLmLocalLlmEngineTest {
    private val config = InferenceConfig(
        contextTokens = 128,
        maxTokens = 8,
        temperature = 0.7f,
        topP = 0.9f,
    )
    private val model = LocalModel(
        id = "gemma-litert",
        name = "Gemma LiteRT",
        filePath = "/tmp/gemma.litertlm",
        runtime = ModelRuntime.LiteRtLm,
        acceleration = ModelAccelerationPolicy.GpuPreferred,
    )

    @Test
    fun `loads litert lm model with gpu preference and streams chunks`() = runTest {
        val bridge = RecordingBridge(chunks = listOf(LiteRtLmChunk(text = "ola"), LiteRtLmChunk(text = " mundo")))
        val engine = LiteRtLmLocalLlmEngine(
            bridge = bridge,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        engine.load(model, config)

        assertEquals("/tmp/gemma.litertlm", bridge.loadedModelPath)
        assertEquals(config, bridge.loadedConfig)
        assertEquals(true, bridge.loadedUseGpu)
        assertEquals(ModelInputCapabilities(), bridge.loadedInputCapabilities)

        engine.generate(PromptRequest("oi")).test {
            assertEquals(GenerationEvent.Token("ola"), awaitItem())
            assertEquals(GenerationEvent.Token(" mundo"), awaitItem())
            assertEquals(GenerationEvent.Completed, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `streams LiteRT reasoning separately and forwards reasoning preference`() = runTest {
        val bridge = RecordingBridge(
            chunks = listOf(
                LiteRtLmChunk(reasoning = "porque "),
                LiteRtLmChunk(text = "resposta"),
            ),
        )
        val engine = LiteRtLmLocalLlmEngine(
            bridge = bridge,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        engine.load(model, config)
        engine.generate(
            PromptRequest(
                content = MessageContent.TextPrompt("pense"),
                reasoningEnabled = true,
            ),
        ).test {
            assertEquals(GenerationEvent.ReasoningToken("porque "), awaitItem())
            assertEquals(GenerationEvent.Token("resposta"), awaitItem())
            assertEquals(GenerationEvent.Completed, awaitItem())
            awaitComplete()
        }

        assertEquals(true, bridge.session.lastRequest?.reasoningEnabled)
    }

    @Test
    fun `does not cancel LiteRT generation based on callback chunk count`() = runTest {
        val chunks = List(config.maxTokens + 3) { LiteRtLmChunk(text = "chunk-$it") }
        val bridge = RecordingBridge(chunks = chunks)
        val engine = LiteRtLmLocalLlmEngine(
            bridge = bridge,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        engine.load(model, config)
        engine.generate(PromptRequest("continue")).test {
            chunks.forEach { chunk ->
                assertEquals(GenerationEvent.Token(chunk.text), awaitItem())
            }
            assertEquals(GenerationEvent.Completed, awaitItem())
            awaitComplete()
        }

        assertEquals(false, bridge.session.cancelled)
    }

    @Test
    fun `loads cpu backend when acceleration is cpu only`() = runTest {
        val bridge = RecordingBridge()
        val engine = LiteRtLmLocalLlmEngine(
            bridge = bridge,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        engine.load(model.copy(acceleration = ModelAccelerationPolicy.CpuOnly), config)

        assertEquals(false, bridge.loadedUseGpu)
    }

    @Test
    fun `rejects non litert lm model`() = runTest {
        val engine = LiteRtLmLocalLlmEngine(
            bridge = RecordingBridge(),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        try {
            engine.load(model.copy(runtime = ModelRuntime.LlamaCpp), config)
            fail("Expected non LiteRT-LM runtime to fail")
        } catch (error: IllegalStateException) {
            assertEquals("Unsupported local model runtime: llama.cpp", error.message)
        }
    }

    @Test
    fun `reports failure when generation throws`() = runTest {
        val engine = LiteRtLmLocalLlmEngine(
            bridge = RecordingBridge(failure = IllegalStateException("boom")),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        engine.load(model, config)

        engine.generate(PromptRequest("oi")).test {
            assertEquals(GenerationEvent.Failed("boom"), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `passes multimodal request to litert session when capabilities allow it`() = runTest {
        val bridge = RecordingBridge(chunks = listOf(LiteRtLmChunk(text = "ok")))
        val engine = LiteRtLmLocalLlmEngine(
            bridge = bridge,
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val multimodalModel = model.copy(
            inputCapabilities = ModelInputCapabilities(image = true, audio = true),
        )

        engine.load(multimodalModel, config)
        val request = PromptRequest(
            MessageContent.TextPrompt(
                text = "describe",
                imageAttachments = listOf(ImageAttachment("file:///tmp/image.png", "image/png")),
            ),
        )

        engine.generate(request).test {
            assertEquals(GenerationEvent.Token("ok"), awaitItem())
            assertEquals(GenerationEvent.Completed, awaitItem())
            awaitComplete()
        }

        assertEquals(request, bridge.session.lastRequest)
    }

    @Test
    fun `normalizes file uris to litert file paths`() {
        assertEquals("/tmp/image.png", "file:///tmp/image.png".toLiteRtFilePath())
        assertEquals("/data/user/0/com.jesjobom.ararai/files/image.jpg", "file:/data/user/0/com.jesjobom.ararai/files/image.jpg".toLiteRtFilePath())
        assertEquals("/tmp/image.png", "/tmp/image.png".toLiteRtFilePath())
    }

    @Test
    fun `builds LiteRT contents with current audio and textual chat context`() {
        val audio = AudioPrompt("file:///tmp/current.wav", "audio/wav")
        val request = PromptRequest(
            content = MessageContent.AudioPromptContent(audio),
            chatMessages = listOf(
                PromptChatMessage(PromptChatRole.System, "Be useful."),
                PromptChatMessage(PromptChatRole.User, "Earlier question"),
                PromptChatMessage(PromptChatRole.Assistant, "Earlier answer"),
            ),
        )

        val contents = request.toLiteRtInputParts()

        assertEquals(listOf("/tmp/current.wav"), contents.filterIsInstance<LiteRtInputPart.AudioFile>().map { it.path })
        val context = contents.filterIsInstance<LiteRtInputPart.Text>().single().text
        assertTrue(context.contains("System: Be useful."))
        assertTrue(context.contains("User: Earlier question"))
        assertTrue(context.contains("Assistant: Earlier answer"))
    }

    @Test
    fun `rejects unsupported audio request before session generation`() = runTest {
        val bridge = RecordingBridge()
        val engine = LiteRtLmLocalLlmEngine(
            bridge = bridge,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        engine.load(model, config)

        engine.generate(
            PromptRequest(
                MessageContent.AudioPromptContent(
                    AudioPrompt("file:///tmp/audio.wav", "audio/wav"),
                ),
            ),
        ).test {
            assertEquals(GenerationEvent.Failed("Selected model does not support audio input"), awaitItem())
            awaitComplete()
        }

        assertEquals(null, bridge.session.lastRequest)
    }

    @Test
    fun `unload closes the active session`() = runTest {
        val bridge = RecordingBridge()
        val engine = LiteRtLmLocalLlmEngine(
            bridge = bridge,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        engine.load(model, config)
        engine.unload()

        assertTrue(bridge.session.closed)
    }

    private class RecordingBridge(
        chunks: List<LiteRtLmChunk> = emptyList(),
        failure: Throwable? = null,
    ) : LiteRtLmBridge {
        val session = RecordingSession(chunks, failure)
        var loadedModelPath: String? = null
            private set
        var loadedConfig: InferenceConfig? = null
            private set
        var loadedUseGpu: Boolean? = null
            private set
        var loadedInputCapabilities: ModelInputCapabilities? = null
            private set

        override suspend fun load(
            modelPath: String,
            config: InferenceConfig,
            useGpu: Boolean,
            inputCapabilities: ModelInputCapabilities,
        ): LiteRtLmSession {
            loadedModelPath = modelPath
            loadedConfig = config
            loadedUseGpu = useGpu
            loadedInputCapabilities = inputCapabilities
            return session
        }
    }

    private class RecordingSession(
        private val chunks: List<LiteRtLmChunk>,
        private val failure: Throwable?,
    ) : LiteRtLmSession {
        var closed = false
            private set
        var cancelled = false
            private set
        var lastRequest: PromptRequest? = null
            private set

        override fun generate(request: PromptRequest, config: InferenceConfig): Flow<LiteRtLmChunk> {
            lastRequest = request
            return if (failure != null) {
                flow { throw failure }
            } else {
                flowOf(*chunks.toTypedArray())
            }
        }

        override fun cancel() {
            cancelled = true
        }

        override fun close() {
            closed = true
        }
    }
}
