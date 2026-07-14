package com.jesjobom.ararai.chat

data class ChatUiState(
    val modelStatus: String,
    val prompt: String = "",
    val imageAttachments: List<ImageAttachment> = emptyList(),
    val audioPrompt: AudioPrompt? = null,
    val canAttachImage: Boolean = false,
    val canUseAudioPrompt: Boolean = false,
    val sessions: List<ChatSessionUiState> = emptyList(),
    val selectedSessionId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isLoadingModel: Boolean = false,
    val isGenerating: Boolean = false,
    val canRetryModelDownload: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean
        get() = modelStatus == MODEL_AVAILABLE &&
            selectedSessionId != null &&
            hasSubmittableDraft &&
            !isLoadingModel &&
            !isGenerating

    val hasSubmittableDraft: Boolean
        get() = if (audioPrompt != null) {
            prompt.isBlank()
        } else {
            prompt.isNotBlank() || imageAttachments.isNotEmpty()
        }

    val canDeleteCurrentSession: Boolean
        get() = selectedSessionId != null && sessions.size > 1

    companion object {
        const val MODEL_AVAILABLE = "Model available"
    }
}

data class ChatSessionUiState(
    val id: String,
    val title: String,
)

data class ChatMessage(
    val role: ChatRole,
    val content: MessageContent,
    val id: String = "",
) {
    constructor(role: ChatRole, text: String, id: String = "") : this(
        role = role,
        content = MessageContent.TextPrompt(text = text),
        id = id,
    )

    val text: String
        get() = content.displayText
}

sealed interface MessageContent {
    data class TextPrompt(
        val text: String,
        val imageAttachments: List<ImageAttachment> = emptyList(),
    ) : MessageContent

    data class AudioPromptContent(
        val audio: AudioPrompt,
    ) : MessageContent
}

data class ImageAttachment(
    val uri: String,
    val mimeType: String,
    val displayName: String? = null,
    val byteSize: Long? = null,
)

data class AudioPrompt(
    val uri: String,
    val mimeType: String,
    val displayName: String? = null,
    val byteSize: Long? = null,
    val durationMillis: Long? = null,
)

val MessageContent.displayText: String
    get() = when (this) {
        is MessageContent.TextPrompt -> text
        is MessageContent.AudioPromptContent -> audio.displayName ?: "Audio prompt"
    }

enum class ChatRole {
    User,
    Assistant,
}
