package com.jesjobom.ararai.chat

data class ChatUiState(
    val modelStatus: String,
    val prompt: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val canRetryModelDownload: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean
        get() = modelStatus == MODEL_AVAILABLE && prompt.isNotBlank() && !isGenerating

    companion object {
        const val MODEL_AVAILABLE = "Model available"
    }
}

data class ChatMessage(
    val role: ChatRole,
    val text: String,
)

enum class ChatRole {
    User,
    Assistant,
}
