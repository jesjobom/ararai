package com.jesjobom.ararai.engine

import app.cash.turbine.test
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelInputCapabilities
import com.jesjobom.ararai.model.ModelRuntime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LiteRtLmContextUsageTest {
    @Test
    fun `publishes native count even when benchmark metrics are unavailable`() = runTest {
        val bridge = object : LiteRtLmBridge {
            override suspend fun load(
                modelPath: String,
                config: InferenceConfig,
                useGpu: Boolean,
                inputCapabilities: ModelInputCapabilities,
                toolNames: Set<String>,
                profile: LiteRtLmWorkloadProfile,
            ): LiteRtLmSession = object : LiteRtLmSession {
                override fun generate(
                    request: PromptRequest,
                    config: InferenceConfig,
                ): Flow<LiteRtLmChunk> = flowOf(LiteRtLmChunk(contextTokenCount = 128))

                override fun cancel() = Unit

                override fun close() = Unit
            }
        }
        val engine = LiteRtLmLocalLlmEngine(bridge = bridge, dispatcher = StandardTestDispatcher(testScheduler))
        val model = LocalModel("test", "Test", "/tmp/test.litertlm", runtime = ModelRuntime.LiteRtLm)
        val config = InferenceConfig(contextTokens = 128, promptReserveTokens = 8, temperature = 0.7f, topP = 0.9f)
        engine.load(model, config)

        engine.generate(PromptRequest("question")).test {
            assertEquals(GenerationEvent.ConversationTokenCount(128), awaitItem())
            assertEquals(GenerationEvent.Completed, awaitItem())
            awaitComplete()
        }
    }
}
