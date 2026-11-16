package com.jesjobom.ararai.chat

import com.jesjobom.ararai.engine.PromptChatMessage
import com.jesjobom.ararai.engine.PromptChatRole
import com.jesjobom.ararai.model.InferenceConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptContextBuilderTest {
    @Test
    fun `builds prompt with system prompt history and current user turn`() {
        val messages =
            PromptContextBuilder().build(
                systemPrompt = "Be useful.",
                history =
                listOf(
                    ChatMessage(ChatRole.User, "Earlier question"),
                    ChatMessage(ChatRole.Assistant, "Earlier answer"),
                ),
                userPrompt = "Current question",
                inferenceConfig =
                InferenceConfig(
                    contextTokens = 128,
                    promptReserveTokens = 16,
                    temperature = 0.7f,
                    topP = 0.9f,
                ),
            )

        assertEquals(
            listOf(
                PromptChatMessage(PromptChatRole.System, "Be useful."),
                PromptChatMessage(PromptChatRole.User, "Earlier question"),
                PromptChatMessage(PromptChatRole.Assistant, "Earlier answer"),
                PromptChatMessage(PromptChatRole.User, "Current question"),
            ),
            messages,
        )
    }

    @Test
    fun `keeps newest history inside budget`() {
        val projection =
            PromptContextBuilder(charsPerToken = 1).project(
                systemPrompt = "Short.",
                history =
                listOf(
                    ChatMessage(ChatRole.User, "old message that should be omitted", id = "old-user"),
                    ChatMessage(ChatRole.Assistant, "old reply", id = "old-assistant"),
                    ChatMessage(ChatRole.User, "new question", id = "new-user"),
                    ChatMessage(ChatRole.Assistant, "new reply", id = "new-assistant"),
                ),
                userPrompt = "follow up",
                inferenceConfig =
                InferenceConfig(
                    contextTokens = 90,
                    promptReserveTokens = 1,
                    temperature = 0.7f,
                    topP = 0.9f,
                ),
            )
        val text = projection.messages.joinToString("\n") { it.text }

        assertFalse(text.contains("old message that should be omitted"))
        assertTrue(projection.messages.contains(PromptChatMessage(PromptChatRole.Assistant, "new reply")))
        assertTrue(projection.messages.contains(PromptChatMessage(PromptChatRole.User, "new question")))
        assertEquals("new-user", projection.oldestIncludedHistoryId)
        assertTrue(projection.hasOmittedHistory)
    }

    @Test
    fun `does not backfill older short turns when newest turn does not fit`() {
        val history = listOf(
            ChatMessage(ChatRole.User, "short", id = "old-user"),
            ChatMessage(ChatRole.Assistant, "yes", id = "old-assistant"),
            ChatMessage(ChatRole.User, "x".repeat(100), id = "new-user"),
            ChatMessage(ChatRole.Assistant, "answer", id = "new-assistant"),
        )
        val config = InferenceConfig(100, 1, 0.7f, 0.9f)
        val projection = PromptContextBuilder(charsPerToken = 1).project("system", history, "now", config)

        assertEquals(null, projection.oldestIncludedHistoryId)
        assertTrue(projection.hasOmittedHistory)
        assertFalse(projection.messages.any { it.text == "short" })
    }

    @Test
    fun `increasing context restores complete older turns`() {
        val history = listOf(
            ChatMessage(ChatRole.User, "older question", id = "old-user"),
            ChatMessage(ChatRole.Assistant, "older answer", id = "old-assistant"),
            ChatMessage(ChatRole.User, "recent question", id = "new-user"),
            ChatMessage(ChatRole.Assistant, "recent answer", id = "new-assistant"),
        )
        val builder = PromptContextBuilder(charsPerToken = 1)
        val small = builder.project("system", history, "now", InferenceConfig(90, 1, 0.7f, 0.9f))
        val large = builder.project("system", history, "now", InferenceConfig(160, 1, 0.7f, 0.9f))

        assertEquals("new-user", small.oldestIncludedHistoryId)
        assertEquals("old-user", large.oldestIncludedHistoryId)
        assertFalse(large.hasOmittedHistory)
    }

    @Test
    fun `keeps ten percent context headroom beyond output reserve and moves boundary`() {
        val history = listOf(
            ChatMessage(ChatRole.User, "o".repeat(10), id = "old-user"),
            ChatMessage(ChatRole.Assistant, "a".repeat(10), id = "old-assistant"),
            ChatMessage(ChatRole.User, "n".repeat(5), id = "new-user"),
            ChatMessage(ChatRole.Assistant, "r".repeat(5), id = "new-assistant"),
        )
        val builder = PromptContextBuilder(charsPerToken = 1)

        val small = builder.project("", history, "now", InferenceConfig(100, 10, 0.7f, 0.9f))
        val large = builder.project("", history, "now", InferenceConfig(110, 10, 0.7f, 0.9f))

        assertEquals("new-user", small.oldestIncludedHistoryId)
        assertTrue(small.hasOmittedHistory)
        assertEquals("old-user", large.oldestIncludedHistoryId)
        assertFalse(large.hasOmittedHistory)
    }

    @Test
    fun `does not invent history capacity when output reserve consumes context`() {
        val projection = PromptContextBuilder().project(
            systemPrompt = "system",
            history = listOf(ChatMessage(ChatRole.User, "earlier", id = "old-user")),
            userPrompt = "now",
            inferenceConfig = InferenceConfig(128, 128, 0.7f, 0.9f),
        )

        assertEquals(null, projection.oldestIncludedHistoryId)
        assertTrue(projection.hasOmittedHistory)
        assertEquals(listOf("system", "now"), projection.messages.map { it.text })
    }
}
