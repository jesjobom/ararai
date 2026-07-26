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
                        if (current.imageAttachments.isNotEmpty()) "Describe this image." else current.text
                    }
                is MessageContent.AudioPromptContent -> ""
            }
        val messages =
            promptContextBuilder.build(
                systemPrompt = systemPrompt,
                history = reconstructibleHistory,
                userPrompt = userPrompt,
                inferenceConfig = inferenceConfig,
            )
        val requestContent =
            when (current) {
                is MessageContent.TextPrompt -> current.copy(text = messages.toPlainChatPrompt())
                is MessageContent.AudioPromptContent -> current
            }
        return ProjectedConversationContext(messages, requestContent)
    }
}

data class ProjectedConversationContext(
    val messages: List<PromptChatMessage>,
    val requestContent: MessageContent,
)
