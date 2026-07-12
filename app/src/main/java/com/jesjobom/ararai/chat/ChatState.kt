package com.jesjobom.ararai.chat

data class ChatUiState(
    val modelStatus: String,
    val prompt: String = "",
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
            prompt.isNotBlank() &&
            !isLoadingModel &&
            !isGenerating

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
    val text: String,
    val id: String = "",
)

enum class ChatRole {
    User,
    Assistant,
}
