package com.jesjobom.ararai.engine

import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelAccelerationPolicy
import com.jesjobom.ararai.model.ModelInputCapabilities
import com.jesjobom.ararai.model.ModelRuntime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LiteRtLmGenerationSerializationTest {
    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun `shared engine serializes concurrent generations`() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var calls = 0
        var active = 0
        var maximumActive = 0
        val session = object : LiteRtLmSession {
            override fun generate(request: PromptRequest, config: InferenceConfig): Flow<LiteRtLmChunk> = flow {
                calls += 1
                active += 1
                maximumActive = maxOf(maximumActive, active)
                try {
                    if (calls == 1) {
                        firstStarted.complete(Unit)
                        releaseFirst.await()
                    }
                    emit(LiteRtLmChunk(text = request.textPrompt.orEmpty()))
                } finally {
                    active -= 1
                }
            }

            override fun cancel() = Unit

            override fun close() = Unit
        }
        val bridge = object : LiteRtLmBridge {
            override suspend fun load(
                modelPath: String,
                config: InferenceConfig,
                useGpu: Boolean,
                inputCapabilities: ModelInputCapabilities,
                toolNames: Set<String>,
                profile: LiteRtLmWorkloadProfile,
            ): LiteRtLmSession = session
        }
        val engine = LiteRtLmLocalLlmEngine(bridge, StandardTestDispatcher(testScheduler))
        engine.load(MODEL, CONFIG)

        val first = async { engine.generate(PromptRequest("first")).toList() }
        runCurrent()
        firstStarted.await()
        val second = async { engine.generate(PromptRequest("second")).toList() }
        runCurrent()

        assertEquals(1, calls)
        assertEquals(1, maximumActive)
        releaseFirst.complete(Unit)
        advanceUntilIdle()
        first.await()
        second.await()
        assertEquals(2, calls)
        assertEquals(1, maximumActive)
    }

    private companion object {
        val CONFIG = InferenceConfig(128, 8, 0.7f, 0.9f)
        val MODEL = LocalModel(
            id = "gemma-litert",
            name = "Gemma LiteRT",
            filePath = "/tmp/gemma.litertlm",
            runtime = ModelRuntime.LiteRtLm,
            acceleration = ModelAccelerationPolicy.GpuPreferred,
        )
    }
}
