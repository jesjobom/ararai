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
        val reconstructibleHistory =
            history
                .filter { message ->
                    val audio = message.content as? MessageContent.AudioPromptContent
                    audio == null || audio.transcriptionStatus == AudioTranscriptionStatus.Completed
                }.map { ChatMessage(role = it.role, content = it.content, id = it.id) }
        val userPrompt =
            when (current) {
                is MessageContent.TextPrompt ->
                    current.text.ifBlank {
                        if (current.imageAttachments.isNotEmpty()) DEFAULT_IMAGE_PROMPT else current.text
                    }
                is MessageContent.AudioPromptContent -> ""
            }
        val messages =
            promptContextBuilder.build(
                systemPrompt = effectiveSystemPrompt,
                history = reconstructibleHistory,
                userPrompt = userPrompt,
                inferenceConfig = inferenceConfig,
            )
        val requestContent =
            when (current) {
                is MessageContent.TextPrompt ->
                    current.copy(
                        text = messages.toPlainChatPrompt(),
                        imageAttachments = current.imageAttachments.ifEmpty { history.latestImageAttachments() },
                    )
                is MessageContent.AudioPromptContent -> current
            }
        return ProjectedConversationContext(messages, requestContent)
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
)
