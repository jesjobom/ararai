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
        val history = sessionStore.getRecentMessages(sessionId, CONTEXT_HISTORY_PAGE_SIZE)
        val message = sessionStore.appendMessage(sessionId, ChatRole.User, content)
        return BegunConversationTurn(history = history, userMessage = message)
    }

    fun project(
        sessionId: String,
        history: List<StoredChatMessage>,
        current: MessageContent,
        inferenceConfig: InferenceConfig,
        effectiveSystemPrompt: String? = null,
    ): ProjectedConversationContext {
        var loadedHistory = history
        fun projectLoaded(): ProjectedConversationContext = if (effectiveSystemPrompt == null) {
            contextProjector.project(loadedHistory, current, inferenceConfig)
        } else {
            contextProjector.project(loadedHistory, current, inferenceConfig, effectiveSystemPrompt)
        }
        var projected = projectLoaded()
        while (
            loadedHistory.size >= CONTEXT_HISTORY_PAGE_SIZE &&
            !(projected.hasOmittedHistory && loadedHistory.first().role == ChatRole.User)
        ) {
            val older =
                sessionStore.getMessagesBefore(sessionId, loadedHistory.first(), CONTEXT_HISTORY_PAGE_SIZE)
            if (older.isEmpty()) break
            loadedHistory = older + loadedHistory
            projected = projectLoaded()
        }
        return projected
    }

    fun preview(
        sessionId: String,
        current: MessageContent,
        inferenceConfig: InferenceConfig,
        effectiveSystemPrompt: String,
    ): ProjectedConversationContext = project(
        sessionId = sessionId,
        history = sessionStore.getRecentMessages(sessionId, CONTEXT_HISTORY_PAGE_SIZE),
        current = current,
        inferenceConfig = inferenceConfig,
        effectiveSystemPrompt = effectiveSystemPrompt,
    )

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

    private companion object {
        const val CONTEXT_HISTORY_PAGE_SIZE = 64
    }
}

data class BegunConversationTurn(
    val history: List<StoredChatMessage>,
    val userMessage: StoredChatMessage,
)
