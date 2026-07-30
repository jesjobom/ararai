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
        val messages =
            PromptContextBuilder(charsPerToken = 1).build(
                systemPrompt = "Short.",
                history =
                listOf(
                    ChatMessage(ChatRole.User, "old message that should be omitted"),
                    ChatMessage(ChatRole.Assistant, "new reply"),
                ),
                userPrompt = "new question",
                inferenceConfig =
                InferenceConfig(
                    contextTokens = 70,
                    promptReserveTokens = 1,
                    temperature = 0.7f,
                    topP = 0.9f,
                ),
            )
        val text = messages.joinToString("\n") { it.text }

        assertFalse(text.contains("old message that should be omitted"))
        assertTrue(messages.contains(PromptChatMessage(PromptChatRole.Assistant, "new reply")))
        assertTrue(messages.contains(PromptChatMessage(PromptChatRole.User, "new question")))
    }
}
