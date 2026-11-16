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
    ): List<PromptChatMessage> = project(systemPrompt, history, userPrompt, inferenceConfig).messages

    fun project(
        systemPrompt: String,
        history: List<ChatMessage>,
        userPrompt: String,
        inferenceConfig: InferenceConfig,
    ): PromptContextProjection {
        val systemMessage = PromptChatMessage(PromptChatRole.System, systemPrompt.trim())
        val currentTurn = PromptChatMessage(PromptChatRole.User, userPrompt.trim())
        val contextTokens = inferenceConfig.contextTokens
        val headroomTokens = contextTokens / 10 + if (contextTokens % 10 == 0) 0 else 1
        val maxChars =
            (contextTokens - inferenceConfig.promptReserveTokens - headroomTokens).coerceAtLeast(0) * charsPerToken
        val turns = mutableListOf<MutableList<ChatMessage>>()
        history.forEach { message ->
            if (message.role == ChatRole.User) {
                turns += mutableListOf(message)
            } else {
                turns.lastOrNull()?.add(message)
            }
        }
        val selectedTurns = mutableListOf<List<ChatMessage>>()
        var used = systemMessage.estimatedLength() + currentTurn.estimatedLength()

        for (turn in turns.asReversed()) {
            val formattedLength = turn.sumOf { it.toPromptMessage().estimatedLength() }
            if (used + formattedLength > maxChars) break
            selectedTurns += turn
            used += formattedLength
        }
        val selectedHistory = selectedTurns.asReversed().flatten()

        val messages = buildList {
            if (systemMessage.text.isNotBlank()) add(systemMessage)
            selectedHistory.forEach { add(it.toPromptMessage()) }
            if (currentTurn.text.isNotBlank()) add(currentTurn)
        }
        return PromptContextProjection(
            messages = messages,
            oldestIncludedHistoryId = selectedHistory.firstOrNull()?.id,
            hasOmittedHistory = selectedHistory.size < history.size,
        )
    }

    private fun ChatMessage.toPromptMessage(): PromptChatMessage {
        val role =
            when (role) {
                ChatRole.User -> PromptChatRole.User
                ChatRole.Assistant -> PromptChatRole.Assistant
            }
        return PromptChatMessage(role, text.trim())
    }

    private fun PromptChatMessage.estimatedLength(): Int = role.transcriptLabel.length + text.length + 3
}

data class PromptContextProjection(
    val messages: List<PromptChatMessage>,
    val oldestIncludedHistoryId: String?,
    val hasOmittedHistory: Boolean,
)
