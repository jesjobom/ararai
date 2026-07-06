package com.jesjobom.ararai.chat

import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelStartupState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
                _uiState.update {
                    it.copy(
                        modelStatus = "Model missing; starting download",
                        canRetryModelDownload = false,
                    )
                }
            }
            is ModelStartupState.Invalid -> {
                model = null
                _uiState.update {
                    it.copy(
                        modelStatus = "Model invalid; redownloading",
                        canRetryModelDownload = false,
                    )
                }
            }
            ModelStartupState.Downloading -> {
                model = null
                _uiState.update {
                    it.copy(
                        modelStatus = "Downloading configured model",
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
                        canRetryModelDownload = false,
                        error = null,
                    )
                }
            }
            is ModelStartupState.Failed -> {
                model = null
                _uiState.update {
                    it.copy(
                        modelStatus = "Download failed: ${state.message}",
                        canRetryModelDownload = true,
                    )
                }
            }
        }
    }

    fun submitPrompt() {
        val current = _uiState.value
        if (!current.canSubmit) return

        val modelForRequest = model
        val inferenceForRequest = inferenceConfig
        val submittedPrompt = current.prompt.trim()
        val userMessage = ChatMessage(ChatRole.User, submittedPrompt)
        val assistantMessage = ChatMessage(ChatRole.Assistant, "")

        _uiState.update {
            it.copy(
                prompt = submittedPrompt,
                messages = it.messages + userMessage + assistantMessage,
                isGenerating = true,
                error = null,
            )
        }

        scope.launch {
            if (modelForRequest == null) {
                _uiState.update {
                    it.copy(isGenerating = false, error = "Model unavailable")
                }
                return@launch
            }

            engine.load(modelForRequest, inferenceForRequest)
            engine.generate(PromptRequest(submittedPrompt)).collect { event ->
                when (event) {
                    is GenerationEvent.Token -> appendAssistantToken(event.text)
                    is GenerationEvent.Failed -> {
                        _uiState.update {
                            it.copy(isGenerating = false, error = event.message)
                        }
                    }
                    GenerationEvent.Completed -> {
                        _uiState.update {
                            it.copy(isGenerating = false, prompt = "")
                        }
                    }
                }
            }
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
