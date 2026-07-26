package com.jesjobom.ararai.chat

import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.model.InferenceConfig
import kotlinx.coroutines.flow.Flow

class ConversationCoordinator(
    private val sessionStore: ChatSessionStore,
    private val contextProjector: ConversationContextProjector,
) {
    fun beginUserTurn(
        sessionId: String,
        content: MessageContent,
    ): BegunConversationTurn {
        val history = sessionStore.getMessages(sessionId)
        val message = sessionStore.appendMessage(sessionId, ChatRole.User, content)
        return BegunConversationTurn(history = history, userMessage = message)
    }

    fun project(
        history: List<StoredChatMessage>,
        current: MessageContent,
        inferenceConfig: InferenceConfig,
    ): ProjectedConversationContext = contextProjector.project(history, current, inferenceConfig)

    fun appendAssistant(
        sessionId: String,
        content: MessageContent.TextPrompt,
    ): StoredChatMessage = sessionStore.appendMessage(sessionId, ChatRole.Assistant, content)

    fun updateMessage(
        messageId: String,
        content: MessageContent,
    ) {
        sessionStore.updateMessage(messageId, content)
    }

    fun generate(
        engine: LocalLlmEngine,
        request: PromptRequest,
    ): Flow<GenerationEvent> = engine.generate(request)
}

data class BegunConversationTurn(
    val history: List<StoredChatMessage>,
    val userMessage: StoredChatMessage,
)
