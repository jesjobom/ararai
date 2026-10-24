package com.jesjobom.ararai.engine

import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelInputCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LiteRtLmCallbackOverflowTest {
    @Test
    fun `callback overflow reports failure without a completed partial response`() = runTest {
        var cancellations = 0
        var retainedTranscript: String? = null
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
                ): Flow<LiteRtLmChunk> = callbackFlow {
                    val delivery = BoundedCallbackDelivery(this) {
                        close(IllegalStateException("Model event delivery capacity exceeded"))
                    }
                    repeat(300) { delivery.emit(LiteRtLmChunk(text = "chunk")) }
                    // A native onDone may race with the already failed callback queue.
                    // The same delivery gate used by AndroidLiteRtLmSession must reject it.
                    if (delivery.complete()) {
                        retainedTranscript = "partial transcript"
                        close()
                    }
                    awaitClose { }
                }
                override fun cancel() {
                    cancellations++
                }
                override fun close() = Unit
            }
        }
        val engine = LiteRtLmLocalLlmEngine(bridge, StandardTestDispatcher(testScheduler))
        engine.load(
            LocalModel("model", "Model", "/tmp/model.litertlm"),
            InferenceConfig(128, 8, 0.7f, 0.9f),
        )

        val events = engine.generate(PromptRequest("private prompt")).toList()

        assertFalse(events.contains(GenerationEvent.Completed))
        val failure = events.filterIsInstance<GenerationEvent.Failed>().single()
        assertEquals("Model event delivery capacity exceeded", failure.message)
        assertFalse(failure.message.contains("private prompt"))
        assertEquals(null, retainedTranscript)
        assertEquals(1, cancellations)
    }
}
