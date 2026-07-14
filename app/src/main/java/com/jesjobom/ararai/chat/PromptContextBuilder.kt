package com.jesjobom.ararai.chat

import com.jesjobom.ararai.engine.PromptChatMessage
import com.jesjobom.ararai.engine.PromptChatRole
import com.jesjobom.ararai.model.InferenceConfig

class PromptContextBuilder(
    private val charsPerToken: Int = 4,
) {
    fun build(
        systemPrompt: String,
        history: List<ChatMessage>,
        userPrompt: String,
        inferenceConfig: InferenceConfig,
    ): List<PromptChatMessage> {
        val systemMessage = PromptChatMessage(PromptChatRole.System, systemPrompt.trim())
        val currentTurn = PromptChatMessage(PromptChatRole.User, userPrompt.trim())
        val maxChars = ((inferenceConfig.contextTokens - inferenceConfig.maxTokens).coerceAtLeast(32) * charsPerToken)
            .coerceAtLeast(systemMessage.estimatedLength() + currentTurn.estimatedLength())
        val selectedHistory = mutableListOf<ChatMessage>()
        var used = systemMessage.estimatedLength() + currentTurn.estimatedLength()

        history.asReversed().forEach { message ->
            val formattedLength = message.toPromptMessage().estimatedLength()
            if (used + formattedLength <= maxChars) {
                selectedHistory += message
                used += formattedLength
            }
        }

        return buildList {
            if (systemMessage.text.isNotBlank()) add(systemMessage)
            selectedHistory.asReversed().forEach { add(it.toPromptMessage()) }
            if (currentTurn.text.isNotBlank()) add(currentTurn)
        }
    }

    private fun ChatMessage.toPromptMessage(): PromptChatMessage {
        val role = when (role) {
            ChatRole.User -> PromptChatRole.User
            ChatRole.Assistant -> PromptChatRole.Assistant
        }
        return PromptChatMessage(role, text.trim())
    }

    private fun PromptChatMessage.estimatedLength(): Int =
        role.transcriptLabel.length + text.length + 3
}
