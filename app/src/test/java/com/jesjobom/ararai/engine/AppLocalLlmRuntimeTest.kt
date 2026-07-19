package com.jesjobom.ararai.engine

import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AppLocalLlmRuntimeTest {
    @Test
    fun `constructs one engine and exposes the same instance to all consumers`() {
        var constructionCount = 0
        val expected = RecordingEngine()
        val runtime =
            AppLocalLlmRuntime {
                constructionCount += 1
                expected
            }

        val chatEngine = runtime.engine
        val benchmarkEngine = runtime.engine

        assertEquals(1, constructionCount)
        assertSame(expected, chatEngine)
        assertSame(chatEngine, benchmarkEngine)
    }

    private class RecordingEngine : LocalLlmEngine {
        override suspend fun load(
            model: LocalModel,
            config: InferenceConfig,
        ) = Unit

        override fun generate(request: PromptRequest): Flow<GenerationEvent> = emptyFlow()

        override suspend fun unload() = Unit
    }
}
