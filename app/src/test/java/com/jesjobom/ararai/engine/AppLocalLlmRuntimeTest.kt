package com.jesjobom.ararai.engine

import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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

    @Test
    fun `close unloads the shared engine once`() = runTest {
        val engine = RecordingEngine()
        val runtime = AppLocalLlmRuntime(scope = this) { engine }

        runtime.close()
        runtime.close()
        runCurrent()

        assertEquals(1, engine.unloadCalls)
    }

    private class RecordingEngine : LocalLlmEngine {
        var unloadCalls = 0

        override suspend fun load(
            model: LocalModel,
            config: InferenceConfig,
        ) = Unit

        override fun generate(request: PromptRequest): Flow<GenerationEvent> = emptyFlow()

        override suspend fun unload() {
            unloadCalls += 1
        }
    }
}
