package com.jesjobom.ararai.chat

import com.jesjobom.ararai.engine.PromptChatMessage
import com.jesjobom.ararai.engine.toPlainChatPrompt
import com.jesjobom.ararai.model.InferenceConfig

class ConversationContextProjector(
    private val systemPrompt: String,
    private val promptContextBuilder: PromptContextBuilder = PromptContextBuilder(),
) {
    fun project(
        history: List<StoredChatMessage>,
        current: MessageContent,
        inferenceConfig: InferenceConfig,
        effectiveSystemPrompt: String = systemPrompt,
    ): ProjectedConversationContext {
        val reconstructibleHistory = mutableListOf<ChatMessage>()
        var eligibleTurn = false
        history.forEach { message ->
            if (message.role == ChatRole.User) {
                val audio = message.content as? MessageContent.AudioPromptContent
                eligibleTurn = audio == null || audio.transcriptionStatus == AudioTranscriptionStatus.Completed
                if (!eligibleTurn) reconstructibleHistory.clear()
            }
            if (eligibleTurn) {
                reconstructibleHistory += ChatMessage(
                    role = message.role,
                    content = message.content,
                    id = message.id,
                    createdAtMillis = message.createdAtMillis,
                )
            }
        }
        val userPrompt =
            when (current) {
                is MessageContent.TextPrompt ->
                    current.text.ifBlank {
                        if (current.imageAttachments.isNotEmpty()) DEFAULT_IMAGE_PROMPT else current.text
                    }
                is MessageContent.AudioPromptContent -> ""
            }
        val projection =
            promptContextBuilder.project(
                systemPrompt = effectiveSystemPrompt,
                history = reconstructibleHistory,
                userPrompt = userPrompt,
                inferenceConfig = inferenceConfig,
            )
        val requestContent =
            when (current) {
                is MessageContent.TextPrompt ->
                    current.copy(
                        text = projection.messages.toPlainChatPrompt(),
                        imageAttachments = current.imageAttachments.ifEmpty { history.latestImageAttachments() },
                    )
                is MessageContent.AudioPromptContent -> current
            }
        return ProjectedConversationContext(
            messages = projection.messages,
            requestContent = requestContent,
            oldestIncludedHistoryId = projection.oldestIncludedHistoryId,
            hasOmittedHistory = projection.hasOmittedHistory || reconstructibleHistory.size < history.size,
        )
    }

    private fun List<StoredChatMessage>.latestImageAttachments(): List<ImageAttachment> = asReversed()
        .asSequence()
        .filter { it.role == ChatRole.User }
        .mapNotNull { (it.content as? MessageContent.TextPrompt)?.imageAttachments?.takeIf(List<*>::isNotEmpty) }
        .firstOrNull()
        .orEmpty()
}

internal const val DEFAULT_IMAGE_PROMPT = "Describe this image."

data class ProjectedConversationContext(
    val messages: List<PromptChatMessage>,
    val requestContent: MessageContent,
    val oldestIncludedHistoryId: String?,
    val hasOmittedHistory: Boolean,
)
