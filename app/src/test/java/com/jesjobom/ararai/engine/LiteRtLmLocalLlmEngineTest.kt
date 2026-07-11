package com.jesjobom.ararai.engine

import app.cash.turbine.test
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelAccelerationPolicy
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
        val bridge = RecordingBridge(chunks = listOf("ola", " mundo"))
        val engine = LiteRtLmLocalLlmEngine(
            bridge = bridge,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        engine.load(model, config)

        assertEquals("/tmp/gemma.litertlm", bridge.loadedModelPath)
        assertEquals(config, bridge.loadedConfig)
        assertEquals(true, bridge.loadedUseGpu)

        engine.generate(PromptRequest("oi")).test {
            assertEquals(GenerationEvent.Token("ola"), awaitItem())
            assertEquals(GenerationEvent.Token(" mundo"), awaitItem())
            assertEquals(GenerationEvent.Completed, awaitItem())
            awaitComplete()
        }
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
        chunks: List<String> = emptyList(),
        failure: Throwable? = null,
    ) : LiteRtLmBridge {
        val session = RecordingSession(chunks, failure)
        var loadedModelPath: String? = null
            private set
        var loadedConfig: InferenceConfig? = null
            private set
        var loadedUseGpu: Boolean? = null
            private set

        override suspend fun load(
            modelPath: String,
            config: InferenceConfig,
            useGpu: Boolean,
        ): LiteRtLmSession {
            loadedModelPath = modelPath
            loadedConfig = config
            loadedUseGpu = useGpu
            return session
        }
    }

    private class RecordingSession(
        private val chunks: List<String>,
        private val failure: Throwable?,
    ) : LiteRtLmSession {
        var closed = false
            private set
        var cancelled = false
            private set

        override fun generate(prompt: String, config: InferenceConfig): Flow<String> =
            if (failure != null) {
                flow { throw failure }
            } else {
                flowOf(*chunks.toTypedArray())
            }

        override fun cancel() {
            cancelled = true
        }

        override fun close() {
            closed = true
        }
    }
}
