package com.jesjobom.ararai.chat

import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
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
    private val _uiState = MutableStateFlow(
        ChatUiState(
            modelStatus = initialModelStatus,
        ),
    )

    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun onPromptChanged(prompt: String) {
        _uiState.update { it.copy(prompt = prompt, error = null) }
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

        val modelForRequest = model
        val inferenceForRequest = inferenceConfig
        val submittedPrompt = current.prompt.trim()
        val userMessage = ChatMessage(ChatRole.User, submittedPrompt)
        val assistantMessage = ChatMessage(ChatRole.Assistant, "")

        _uiState.update {
            it.copy(
                prompt = submittedPrompt,
                messages = it.messages + userMessage + assistantMessage,
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

                engine.generate(PromptRequest(submittedPrompt)).collect { event ->
                    when (event) {
                        is GenerationEvent.Token -> appendAssistantToken(event.text)
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
                                )
                            }
                        }
                    }
                }
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
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

    private fun stopActiveGeneration() {
        generationJob?.cancel()
        generationJob = null
    }

    private fun unloadEngine() {
        scope.launch {
            engine.unload()
        }
    }

    private fun appendAssistantToken(token: String) {
        _uiState.update { state ->
            val updatedMessages = state.messages.toMutableList()
            val lastAssistantIndex = updatedMessages.indexOfLast { it.role == ChatRole.Assistant }
            if (lastAssistantIndex >= 0) {
                val currentAssistant = updatedMessages[lastAssistantIndex]
                updatedMessages[lastAssistantIndex] = currentAssistant.copy(
                    text = currentAssistant.text + token,
                )
            }
            state.copy(messages = updatedMessages)
        }
    }
}
