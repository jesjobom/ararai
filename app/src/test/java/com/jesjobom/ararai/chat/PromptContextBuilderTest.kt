package com.jesjobom.ararai.chat

import com.jesjobom.ararai.model.InferenceConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptContextBuilderTest {
    @Test
    fun `builds prompt with system prompt history and current user turn`() {
        val prompt = PromptContextBuilder().build(
            systemPrompt = "Be useful.",
            history = listOf(
                ChatMessage(ChatRole.User, "Earlier question"),
                ChatMessage(ChatRole.Assistant, "Earlier answer"),
            ),
            userPrompt = "Current question",
            inferenceConfig = InferenceConfig(contextTokens = 128, maxTokens = 16, temperature = 0.7f, topP = 0.9f),
        )

        assertTrue(prompt.contains("System: Be useful."))
        assertTrue(prompt.contains("User: Earlier question"))
        assertTrue(prompt.contains("Assistant: Earlier answer"))
        assertTrue(prompt.endsWith("User: Current question\nAssistant:"))
    }

    @Test
    fun `keeps newest history inside budget`() {
        val prompt = PromptContextBuilder(charsPerToken = 1).build(
            systemPrompt = "Short.",
            history = listOf(
                ChatMessage(ChatRole.User, "old message that should be omitted"),
                ChatMessage(ChatRole.Assistant, "new reply"),
            ),
            userPrompt = "new question",
            inferenceConfig = InferenceConfig(contextTokens = 70, maxTokens = 1, temperature = 0.7f, topP = 0.9f),
        )

        assertFalse(prompt.contains("old message that should be omitted"))
        assertTrue(prompt.contains("Assistant: new reply"))
        assertTrue(prompt.contains("User: new question"))
    }
}
