package com.jesjobom.ararai.engine

import com.jesjobom.ararai.chat.AudioPrompt
import com.jesjobom.ararai.chat.ImageAttachment
import com.jesjobom.ararai.chat.MessageContent
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import kotlinx.coroutines.flow.Flow

interface LocalLlmEngine {
    suspend fun load(model: LocalModel, config: InferenceConfig)
    fun generate(request: PromptRequest): Flow<GenerationEvent>
    suspend fun unload()
}

data class PromptRequest(
    val content: MessageContent,
    val chatMessages: List<PromptChatMessage> = defaultChatMessages(content),
) {
    constructor(prompt: String) : this(MessageContent.TextPrompt(prompt), userChatMessages(prompt))

    val prompt: String
        get() = when (content) {
            is MessageContent.TextPrompt -> content.text
            is MessageContent.AudioPromptContent -> content.audio.displayName ?: "Audio prompt"
        }

    val textPrompt: String?
        get() = (content as? MessageContent.TextPrompt)?.text

    val imageAttachments: List<ImageAttachment>
        get() = (content as? MessageContent.TextPrompt)?.imageAttachments.orEmpty()

    val audioPrompt: AudioPrompt?
        get() = (content as? MessageContent.AudioPromptContent)?.audio

    val plainChatPrompt: String
        get() = chatMessages.toPlainChatPrompt()

    companion object {
        private fun defaultChatMessages(content: MessageContent): List<PromptChatMessage> =
            when (content) {
                is MessageContent.TextPrompt -> userChatMessages(content.text)
                is MessageContent.AudioPromptContent -> emptyList()
            }

        private fun userChatMessages(prompt: String): List<PromptChatMessage> =
            if (prompt.isBlank()) {
                emptyList()
            } else {
                listOf(PromptChatMessage(PromptChatRole.User, prompt))
            }
    }
}

data class PromptChatMessage(
    val role: PromptChatRole,
    val text: String,
)

enum class PromptChatRole(
    val templateRole: String,
    val transcriptLabel: String,
) {
    System("system", "System"),
    User("user", "User"),
    Assistant("assistant", "Assistant"),
}

fun List<PromptChatMessage>.toPlainChatPrompt(): String =
    this.filter { it.text.isNotBlank() }.let { messages ->
        buildString {
            messages.forEach { message ->
                append(message.role.transcriptLabel)
                append(": ")
                append(message.text.trim())
                append('\n')
            }
            append("Assistant:")
        }
    }

sealed interface GenerationEvent {
    data class Token(val text: String) : GenerationEvent
    data class Failed(val message: String) : GenerationEvent
    data object Completed : GenerationEvent
}
