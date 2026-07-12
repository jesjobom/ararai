package com.jesjobom.ararai.chat

import com.jesjobom.ararai.model.InferenceConfig

class PromptContextBuilder(
    private val charsPerToken: Int = 4,
) {
    fun build(
        systemPrompt: String,
        history: List<ChatMessage>,
        userPrompt: String,
        inferenceConfig: InferenceConfig,
    ): String {
        val header = "System: ${systemPrompt.trim()}\n\n"
        val currentTurn = "User: ${userPrompt.trim()}\nAssistant:"
        val maxChars = ((inferenceConfig.contextTokens - inferenceConfig.maxTokens).coerceAtLeast(32) * charsPerToken)
            .coerceAtLeast(header.length + currentTurn.length)
        val selectedHistory = mutableListOf<ChatMessage>()
        var used = header.length + currentTurn.length

        history.asReversed().forEach { message ->
            val formattedLength = message.toPromptLine().length
            if (used + formattedLength <= maxChars) {
                selectedHistory += message
                used += formattedLength
            }
        }

        return buildString {
            append(header)
            selectedHistory.asReversed().forEach { append(it.toPromptLine()) }
            append(currentTurn)
        }
    }

    private fun ChatMessage.toPromptLine(): String {
        val role = when (role) {
            ChatRole.User -> "User"
            ChatRole.Assistant -> "Assistant"
        }
        return "$role: ${text.trim()}\n"
    }
}
