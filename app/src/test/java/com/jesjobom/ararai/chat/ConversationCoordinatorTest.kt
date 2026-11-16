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
    fun `large context pages beyond the old 512 message limit`() {
        val store = InMemoryChatSessionStore()
        val session = store.createSession("Long history")
        val first = store.appendMessage(session.id, ChatRole.User, "question 0")
        store.appendMessage(session.id, ChatRole.Assistant, "answer 0")
        repeat(260) { index ->
            store.appendMessage(session.id, ChatRole.User, "question ${index + 1}")
            store.appendMessage(session.id, ChatRole.Assistant, "answer ${index + 1}")
        }
        val coordinator = ConversationCoordinator(store, ConversationContextProjector("system"))
        val projection = coordinator.preview(
            session.id,
            MessageContent.TextPrompt("next"),
            InferenceConfig(8_192, 128, 0.7f, 0.9f),
            "system",
        )

        assertEquals(first.id, projection.oldestIncludedHistoryId)
        assertEquals(false, projection.hasOmittedHistory)
    }

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
