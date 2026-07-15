package com.jesjobom.ararai.chat

import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.PromptChatMessage
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.engine.toPlainChatPrompt
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelCatalog
import com.jesjobom.ararai.model.ModelStartupState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val engine: LocalLlmEngine,
    initialModel: LocalModel?,
    inferenceConfig: InferenceConfig,
    private val systemPrompt: String = ModelCatalog.DEFAULT_SYSTEM_PROMPT,
    private val sessionStore: ChatSessionStore = InMemoryChatSessionStore(),
    private val promptContextBuilder: PromptContextBuilder = PromptContextBuilder(),
    initialModelStatus: String = if (initialModel == null) {
        "Model unavailable"
    } else {
        ChatUiState.MODEL_AVAILABLE
    },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var model = initialModel
    private var inferenceConfig = inferenceConfig
    private var generationJob: Job? = null
    private var activeAssistantMessageId: String? = null
    private val initialSession = sessionStore.ensureSession()
    private val _uiState = MutableStateFlow(
        ChatUiState(
            modelStatus = initialModelStatus,
            canAttachImage = initialModel?.inputCapabilities?.image == true,
            canUseAudioPrompt = initialModel?.inputCapabilities?.audio == true,
            canEnableReasoning = initialModel?.reasoningCapabilities?.request == true,
            canShowReasoning = initialModel?.reasoningCapabilities?.output == true,
            sessions = sessionStore.listSessions().toUiState(),
            selectedSessionId = initialSession.id,
            messages = sessionStore.getMessages(initialSession.id).toChatMessages(),
        ),
    )

    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun onPromptChanged(prompt: String) {
        _uiState.update {
            if (it.audioPrompt != null) {
                it.copy(error = null)
            } else {
                it.copy(prompt = prompt, error = null)
            }
        }
    }

    fun attachImage(image: ImageAttachment) {
        _uiState.update {
            if (!it.canAttachImage || it.audioPrompt != null) {
                it
            } else {
                it.copy(imageAttachments = it.imageAttachments + image, error = null)
            }
        }
    }

    fun removeImage(uri: String) {
        _uiState.update {
            it.copy(imageAttachments = it.imageAttachments.filterNot { image -> image.uri == uri }, error = null)
        }
    }

    fun useAudioPrompt(audio: AudioPrompt) {
        _uiState.update {
            if (!it.canUseAudioPrompt) {
                it
            } else {
                it.copy(
                    prompt = "",
                    imageAttachments = emptyList(),
                    audioPrompt = audio,
                    error = null,
                )
            }
        }
    }

    fun submitAudioPrompt(audio: AudioPrompt) {
        useAudioPrompt(audio)
        submitPrompt()
    }

    fun clearAudioPrompt() {
        _uiState.update { it.copy(audioPrompt = null, error = null) }
    }

    fun setReasoningEnabled(enabled: Boolean) {
        _uiState.update {
            if (!it.canEnableReasoning && enabled) {
                it
            } else {
                it.copy(reasoningEnabled = enabled && it.canEnableReasoning, error = null)
            }
        }
    }

    fun setShowReasoning(show: Boolean) {
        _uiState.update {
            if (!it.canShowReasoning && show) {
                it
            } else {
                it.copy(showReasoning = show && it.canShowReasoning, error = null)
            }
        }
    }

    fun createSession() {
        if (generationJob?.isActive == true) return
        val session = sessionStore.createSession("New chat")
        _uiState.update {
            it.copy(
                sessions = sessionStore.listSessions().toUiState(),
                selectedSessionId = session.id,
                messages = emptyList(),
                prompt = "",
                imageAttachments = emptyList(),
                audioPrompt = null,
                error = null,
            )
        }
    }

    fun selectSession(sessionId: String) {
        if (generationJob?.isActive == true) return
        if (sessionStore.listSessions().none { it.id == sessionId }) return
        _uiState.update {
            it.copy(
                selectedSessionId = sessionId,
                sessions = sessionStore.listSessions().toUiState(),
                messages = sessionStore.getMessages(sessionId).toChatMessages(),
                prompt = "",
                imageAttachments = emptyList(),
                audioPrompt = null,
                error = null,
            )
        }
    }

    fun renameCurrentSession(title: String) {
        val sessionId = _uiState.value.selectedSessionId ?: return
        sessionStore.renameSession(sessionId, title)
        _uiState.update { it.copy(sessions = sessionStore.listSessions().toUiState(), error = null) }
    }

    fun deleteCurrentSession() {
        val sessionId = _uiState.value.selectedSessionId ?: return
        deleteSession(sessionId)
    }

    fun deleteSession(sessionId: String) {
        val current = _uiState.value
        if (current.sessions.size <= 1 || generationJob?.isActive == true) return

        sessionStore.deleteSession(sessionId)
        val next = if (current.selectedSessionId == sessionId) {
            sessionStore.ensureSession()
        } else {
            sessionStore.listSessions().first { it.id == current.selectedSessionId }
        }
        _uiState.update {
            it.copy(
                sessions = sessionStore.listSessions().toUiState(),
                selectedSessionId = next.id,
                messages = sessionStore.getMessages(next.id).toChatMessages(),
                prompt = "",
                error = null,
            )
        }
    }

    fun onModelStartupState(state: ModelStartupState) {
        when (state) {
            ModelStartupState.Missing -> {
                model = null
                stopActiveGeneration()
                unloadEngine()
                _uiState.update {
                    it.copy(
                        modelStatus = "Model missing; starting download",
                        isLoadingModel = false,
                        isGenerating = false,
                        canRetryModelDownload = false,
                        canAttachImage = false,
                        canUseAudioPrompt = false,
                        canEnableReasoning = false,
                        canShowReasoning = false,
                        reasoningEnabled = false,
                        showReasoning = false,
                        imageAttachments = emptyList(),
                        audioPrompt = null,
                    )
                }
            }
            is ModelStartupState.Invalid -> {
                model = null
                stopActiveGeneration()
                unloadEngine()
                _uiState.update {
                    it.copy(
                        modelStatus = "Model invalid; redownloading",
                        isLoadingModel = false,
                        isGenerating = false,
                        canRetryModelDownload = false,
                        canAttachImage = false,
                        canUseAudioPrompt = false,
                        canEnableReasoning = false,
                        canShowReasoning = false,
                        reasoningEnabled = false,
                        showReasoning = false,
                        imageAttachments = emptyList(),
                        audioPrompt = null,
                    )
                }
            }
            is ModelStartupState.Downloading -> {
                model = null
                stopActiveGeneration()
                unloadEngine()
                _uiState.update {
                    it.copy(
                        modelStatus = state.downloadStatusText(),
                        isLoadingModel = false,
                        isGenerating = false,
                        canRetryModelDownload = false,
                        canAttachImage = false,
                        canUseAudioPrompt = false,
                        canEnableReasoning = false,
                        canShowReasoning = false,
                        reasoningEnabled = false,
                        showReasoning = false,
                        imageAttachments = emptyList(),
                        audioPrompt = null,
                    )
                }
            }
            is ModelStartupState.Available -> {
                model = state.model
                inferenceConfig = state.inference
                _uiState.update {
                    it.copy(
                        modelStatus = ChatUiState.MODEL_AVAILABLE,
                        isLoadingModel = false,
                        canRetryModelDownload = false,
                        canAttachImage = state.model.inputCapabilities.image,
                        canUseAudioPrompt = state.model.inputCapabilities.audio,
                        canEnableReasoning = state.model.reasoningCapabilities.request,
                        canShowReasoning = state.model.reasoningCapabilities.output,
                        reasoningEnabled = it.reasoningEnabled && state.model.reasoningCapabilities.request,
                        showReasoning = it.showReasoning && state.model.reasoningCapabilities.output,
                        imageAttachments = if (state.model.inputCapabilities.image) it.imageAttachments else emptyList(),
                        audioPrompt = if (state.model.inputCapabilities.audio) it.audioPrompt else null,
                        error = null,
                    )
                }
            }
            is ModelStartupState.Failed -> {
                model = null
                stopActiveGeneration()
                unloadEngine()
                _uiState.update {
                    it.copy(
                        modelStatus = "Download failed: ${state.message}",
                        isLoadingModel = false,
                        isGenerating = false,
                        canRetryModelDownload = true,
                        canAttachImage = false,
                        canUseAudioPrompt = false,
                        canEnableReasoning = false,
                        canShowReasoning = false,
                        reasoningEnabled = false,
                        showReasoning = false,
                        imageAttachments = emptyList(),
                        audioPrompt = null,
                    )
                }
            }
        }
    }

    private fun ModelStartupState.Downloading.downloadStatusText(): String {
        val total = totalBytes
        if (total == null || total <= 0L || bytesDownloaded <= 0L) {
            return "Downloading configured model"
        }

        val percent = ((bytesDownloaded * 100) / total).coerceIn(0, 100)
        return "Downloading configured model: $percent%"
    }

    fun submitPrompt() {
        val current = _uiState.value
        if (!current.canSubmit) return
        if (generationJob?.isActive == true) return

        val sessionId = current.selectedSessionId ?: return
        val modelForRequest = model
        val inferenceForRequest = inferenceConfig
        val submittedContent = current.toSubmittedContent()
        val submittedTitle = submittedContent.displayText
        maybeTitleSession(sessionId, submittedTitle)
        val history = sessionStore.getMessages(sessionId).toChatMessages()
        val requestMessages = submittedContent.toRequestMessages(
            systemPrompt = systemPrompt,
            history = history,
            inferenceConfig = inferenceForRequest,
        )
        val requestContent = submittedContent.toRequestContent(requestMessages)
        val userMessage = sessionStore.appendMessage(sessionId, ChatRole.User, submittedContent).toChatMessage()
        val assistantMessage = sessionStore.appendMessage(sessionId, ChatRole.Assistant, "").toChatMessage()
        activeAssistantMessageId = assistantMessage.id

        _uiState.update {
            it.copy(
                prompt = current.prompt,
                sessions = sessionStore.listSessions().toUiState(),
                messages = sessionStore.getMessages(sessionId).toChatMessages(),
                isLoadingModel = true,
                isGenerating = true,
                error = null,
            )
        }

        generationJob = scope.launch {
            if (modelForRequest == null) {
                _uiState.update {
                    it.copy(
                        isLoadingModel = false,
                        isGenerating = false,
                        error = "Model unavailable",
                    )
                }
                return@launch
            }

            try {
                engine.load(modelForRequest, inferenceForRequest)
                _uiState.update { it.copy(isLoadingModel = false) }

                engine.generate(
                    PromptRequest(
                        content = requestContent,
                        chatMessages = requestMessages,
                        reasoningEnabled = current.reasoningEnabled && modelForRequest.reasoningCapabilities.request,
                    ),
                ).collect { event ->
                    when (event) {
                        is GenerationEvent.Token -> appendAssistantToken(event.text)
                        is GenerationEvent.ReasoningToken -> appendAssistantReasoningToken(event.text)
                        is GenerationEvent.Failed -> {
                            _uiState.update {
                                it.copy(
                                    isLoadingModel = false,
                                    isGenerating = false,
                                    error = event.message,
                                )
                            }
                        }
                        GenerationEvent.Completed -> {
                            _uiState.update {
                                it.copy(
                                    isLoadingModel = false,
                                    isGenerating = false,
                                    prompt = "",
                                    imageAttachments = emptyList(),
                                    audioPrompt = null,
                                    sessions = sessionStore.listSessions().toUiState(),
                                )
                            }
                        }
                    }
                }
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) return@launch
                _uiState.update {
                    it.copy(
                        isLoadingModel = false,
                        isGenerating = false,
                        error = error.message ?: "Generation failed",
                    )
                }
            }
        }
    }

    fun onLeavingChat() {
        stopActiveGeneration()
        unloadEngine()
        _uiState.update {
            it.copy(
                isLoadingModel = false,
                isGenerating = false,
            )
        }
    }

    fun cancelGeneration() {
        stopActiveGeneration()
        unloadEngine()
        _uiState.update {
            it.copy(
                isLoadingModel = false,
                isGenerating = false,
                error = null,
            )
        }
    }

    private fun stopActiveGeneration() {
        generationJob?.cancel()
        generationJob = null
        activeAssistantMessageId = null
    }

    private fun unloadEngine() {
        scope.launch {
            engine.unload()
        }
    }

    private fun appendAssistantToken(token: String) {
        appendAssistantContent { current ->
            current.copy(text = current.text + token)
        }
    }

    private fun appendAssistantReasoningToken(token: String) {
        appendAssistantContent { current ->
            current.copy(reasoningText = current.reasoningText + token)
        }
    }

    private fun appendAssistantContent(update: (MessageContent.TextPrompt) -> MessageContent.TextPrompt) {
        val assistantMessageId = activeAssistantMessageId ?: return
        _uiState.update { state ->
            val updatedMessages = state.messages.toMutableList()
            val assistantIndex = updatedMessages.indexOfLast { it.id == assistantMessageId }
            if (assistantIndex >= 0) {
                val currentAssistant = updatedMessages[assistantIndex]
                val currentContent = currentAssistant.content as? MessageContent.TextPrompt ?: return@update state
                val updatedAssistant = currentAssistant.copy(content = update(currentContent))
                updatedMessages[assistantIndex] = updatedAssistant
                sessionStore.updateMessage(assistantMessageId, updatedAssistant.content)
            }
            state.copy(messages = updatedMessages)
        }
    }

    private fun maybeTitleSession(sessionId: String, submittedPrompt: String) {
        val session = sessionStore.listSessions().firstOrNull { it.id == sessionId } ?: return
        if (session.title != "New chat" || sessionStore.getMessages(sessionId).isNotEmpty()) return
        sessionStore.renameSession(sessionId, submittedPrompt.take(42))
    }

    private fun ChatUiState.toSubmittedContent(): MessageContent =
        audioPrompt?.let { MessageContent.AudioPromptContent(it) }
            ?: MessageContent.TextPrompt(
                text = prompt.trim(),
                imageAttachments = imageAttachments,
            )

    private fun MessageContent.toRequestMessages(
        systemPrompt: String,
        history: List<ChatMessage>,
        inferenceConfig: InferenceConfig,
    ): List<PromptChatMessage> =
        when (this) {
            is MessageContent.TextPrompt -> promptContextBuilder.build(
                systemPrompt = systemPrompt,
                history = history,
                userPrompt = text.ifBlank {
                    if (imageAttachments.isNotEmpty()) "Describe this image." else text
                },
                inferenceConfig = inferenceConfig,
            )
            is MessageContent.AudioPromptContent -> emptyList()
        }

    private fun MessageContent.toRequestContent(messages: List<PromptChatMessage>): MessageContent =
        when (this) {
            is MessageContent.TextPrompt -> copy(text = messages.toPlainChatPrompt())
            is MessageContent.AudioPromptContent -> this
        }

    private fun List<ChatSession>.toUiState(): List<ChatSessionUiState> =
        map { ChatSessionUiState(id = it.id, title = it.title) }

    private fun List<StoredChatMessage>.toChatMessages(): List<ChatMessage> =
        map { it.toChatMessage() }

    private fun StoredChatMessage.toChatMessage(): ChatMessage =
        ChatMessage(role = role, content = content, id = id)
}
