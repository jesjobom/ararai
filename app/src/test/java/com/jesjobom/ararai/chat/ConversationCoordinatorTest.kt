package com.jesjobom.ararai.chat

import app.cash.turbine.test
import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationCoordinatorTest {
    @Test
    fun `forwards knowledge lifecycle events without transforming protocol`() = runTest {
        val events =
            listOf(
                GenerationEvent.ToolStarted("wikipedia_search"),
                GenerationEvent.ToolFinished("wikipedia_search"),
                GenerationEvent.Completed,
            )
        val engine = RecordingEngine(events)
        val coordinator =
            ConversationCoordinator(
                sessionStore = InMemoryChatSessionStore(),
                contextProjector = ConversationContextProjector("system"),
            )
        val request = PromptRequest("research")

        coordinator.generate(engine, request).test {
            events.forEach { assertEquals(it, awaitItem()) }
            awaitComplete()
        }
        assertEquals(request, engine.request)
    }

    private class RecordingEngine(
        private val events: List<GenerationEvent>,
    ) : LocalLlmEngine {
        var request: PromptRequest? = null

        override suspend fun load(
            model: LocalModel,
            config: InferenceConfig,
        ) = Unit

        override fun generate(request: PromptRequest): Flow<GenerationEvent> {
            this.request = request
            return flowOf(*events.toTypedArray())
        }

        override suspend fun unload() = Unit
    }
}
