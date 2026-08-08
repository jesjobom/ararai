package com.jesjobom.ararai.chat

import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.GenerationMetrics
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelCatalog
import com.jesjobom.ararai.model.ModelStartupState
import com.jesjobom.ararai.ui.UserMessageKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("LongParameterList")
class ChatViewModel(
    private val engine: LocalLlmEngine,
    initialModel: LocalModel?,
    inferenceConfig: InferenceConfig,
    private val systemPrompt: String = ModelCatalog.DEFAULT_SYSTEM_PROMPT,
    private val conversationTurnSettingsProvider: (LocalModel?) -> ConversationTurnSettings = {
        ConversationTurnSettings(systemPrompt)
    },
    private val generationConfigProvider: (LocalModel, InferenceConfig) -> InferenceConfig = { _, config -> config },
    private val generationMetricsConsumer: (LocalModel, GenerationMetrics) -> Unit = { _, _ -> },
    private val sessionStore: ChatSessionStore = InMemoryChatSessionStore(),
    private val mediaRepository: ChatMediaRepository = NoOpChatMediaRepository,
    promptContextBuilder: PromptContextBuilder = PromptContextBuilder(),
    private val audioTranscriber: AudioTranscriber = UnavailableAudioTranscriber,
    private val preferences: ChatPreferences = InMemoryChatPreferences(),
    private val conversationSelection: ConversationSelection = ConversationSelection(),
    private val conversationCoordinator: ConversationCoordinator =
        ConversationCoordinator(
            sessionStore = sessionStore,
            contextProjector = ConversationContextProjector(systemPrompt, promptContextBuilder),
        ),
    initialModelStatus: String =
        if (initialModel == null) {
            "Model unavailable"
        } else {
            ChatUiState.MODEL_AVAILABLE
        },
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val persistenceDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    private val assistantPersistenceIntervalMillis: Long = DEFAULT_ASSISTANT_PERSISTENCE_INTERVAL_MILLIS,
    private val assistantPresentationIntervalMillis: Long = DEFAULT_ASSISTANT_PRESENTATION_INTERVAL_MILLIS,
) {
    private val lifecycleJob = SupervisorJob()
    private val scope = CoroutineScope(scope.coroutineContext + lifecycleJob)
    private val closed = AtomicBoolean(false)

    init {
        require(assistantPersistenceIntervalMillis > 0L) {
            "Assistant persistence interval must be positive"
        }
        require(assistantPresentationIntervalMillis > 0L) {
            "Assistant presentation interval must be positive"
        }
    }

    private var model = initialModel
    private var inferenceConfig = inferenceConfig
    private var generationJob: Job? = null
    private var activeAssistantMessageId: String? = null
    private var assistantPersistenceJob: Job? = null
    private var assistantPresentationJob: Job? = null
    private var assistantBuffer: AssistantMessageBuffer? = null
    private var pendingResearchSources = emptyList<com.jesjobom.ararai.knowledge.KnowledgeSource>()
    private val assistantBufferLock = Any()
    private val messageWindowSizes = ConcurrentHashMap<String, Int>()
    private val submissionPending = AtomicBoolean(false)
    private val sessionMutationPending = AtomicBoolean(false)
    private val initialSession =
        conversationSelection.currentSessionId
            ?.let { selected -> sessionStore.listSessions().firstOrNull { it.id == selected } }
            ?: sessionStore.ensureSession().also { conversationSelection.select(it.id) }
    private val _uiState =
        MutableStateFlow(
            ChatUiState(
                modelStatus = initialModelStatus,
                modelStatusKey =
                if (initialModelStatus == ChatUiState.MODEL_AVAILABLE) {
                    UserMessageKey.ModelAvailable
                } else {
                    null
                },
                canAttachImage = initialModel?.inputCapabilities?.image == true,
                canUseAudioPrompt = initialModel.canUseRecordedAudio(),
                canEnableReasoning = initialModel?.reasoningCapabilities?.request == true,
                canShowReasoning = initialModel?.reasoningCapabilities?.output == true,
                sessions = sessionStore.listSessions().toUiState(),
                selectedSessionId = initialSession.id,
                messages = displayedMessages(initialSession.id),
                hasOlderMessages = hasOlderMessages(initialSession.id),
                showAudioTranscriptions = preferences.showAudioTranscriptions.value,
            ),
        )

    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun onPromptChanged(prompt: String) {
        _uiState.update {
            if (it.audioPrompt != null) {
                it.copy(error = null, errorKey = null)
            } else {
                it.copy(prompt = prompt, error = null, errorKey = null)
            }
        }
    }

    fun attachImage(image: ImageAttachment) {
        _uiState.update {
            if (!it.canAttachImage || it.audioPrompt != null) {
                it
            } else {
                it.copy(imageAttachments = it.imageAttachments + image, error = null, errorKey = null)
            }
        }
    }

    fun removeImage(uri: String) {
        val removed = _uiState.value.imageAttachments.any { it.uri == uri }
        _uiState.update {
            it.copy(
                imageAttachments = it.imageAttachments.filterNot { image -> image.uri == uri },
                error = null,
                errorKey = null,
            )
        }
        if (removed) mediaRepository.deleteDraft(uri, sessionStore.referencedMediaUris())
    }

    fun useAudioPrompt(audio: AudioPrompt) {
        val current = _uiState.value
        _uiState.update {
            if (!it.canUseAudioPrompt) {
                it
            } else {
                it.copy(
                    prompt = "",
                    imageAttachments = emptyList(),
                    audioPrompt = audio,
                    error = null,
                    errorKey = null,
                )
            }
        }
        if (current.canUseAudioPrompt) {
            deleteDraftMedia(current.imageAttachments.map { it.uri } + listOfNotNull(current.audioPrompt?.uri))
        }
    }

    fun submitAudioPrompt(audio: AudioPrompt) {
        useAudioPrompt(audio)
        submitPrompt()
    }

    fun clearAudioPrompt() {
        val removedUri = _uiState.value.audioPrompt?.uri
        _uiState.update { it.copy(audioPrompt = null, error = null, errorKey = null) }
        removedUri?.let { mediaRepository.deleteDraft(it, sessionStore.referencedMediaUris()) }
    }

    fun setReasoningEnabled(enabled: Boolean) {
        _uiState.update {
            if (!it.canEnableReasoning && enabled) {
                it
            } else {
                it.copy(reasoningEnabled = enabled && it.canEnableReasoning, error = null, errorKey = null)
            }
        }
    }

    fun setShowReasoning(show: Boolean) {
        _uiState.update {
            if (!it.canShowReasoning && show) {
                it
            } else {
                it.copy(showReasoning = show && it.canShowReasoning, error = null, errorKey = null)
            }
        }
    }

    fun setShowAudioTranscriptions(show: Boolean) {
        preferences.setShowAudioTranscriptions(show)
        _uiState.update { it.copy(showAudioTranscriptions = show) }
    }

    fun createSession() {
        if (closed.get()) return
        runSessionMutation(::createSessionAfterPersistenceDispatch)
    }

    private fun createSessionAfterPersistenceDispatch() {
        if (generationJob?.isActive == true) return
        deleteCurrentDraftMedia()
        val session = sessionStore.createSession("New chat")
        conversationSelection.select(session.id)
        messageWindowSizes[session.id] = INITIAL_MESSAGE_WINDOW_SIZE
        _uiState.update {
            it.copy(
                sessions = sessionStore.listSessions().toUiState(),
                selectedSessionId = session.id,
                messages = emptyList(),
                hasOlderMessages = false,
                prompt = "",
                imageAttachments = emptyList(),
                audioPrompt = null,
                toolInProgress = false,
                activeToolName = null,
                researchSources = emptyList(),
                error = null,
                errorKey = null,
            )
        }
    }

    fun selectSession(sessionId: String) {
        if (closed.get()) return
        runSessionMutation { selectSessionAfterPersistenceDispatch(sessionId) }
    }

    private fun selectSessionAfterPersistenceDispatch(sessionId: String) {
        if (generationJob?.isActive == true) return
        if (sessionStore.listSessions().none { it.id == sessionId }) return
        deleteCurrentDraftMedia()
        conversationSelection.select(sessionId)
        messageWindowSizes[sessionId] = INITIAL_MESSAGE_WINDOW_SIZE
        _uiState.update {
            it.copy(
                selectedSessionId = sessionId,
                sessions = sessionStore.listSessions().toUiState(),
                messages = displayedMessages(sessionId),
                hasOlderMessages = hasOlderMessages(sessionId),
                prompt = "",
                imageAttachments = emptyList(),
                audioPrompt = null,
                toolInProgress = false,
                activeToolName = null,
                researchSources = emptyList(),
                error = null,
                errorKey = null,
            )
        }
    }

    fun renameCurrentSession(title: String) {
        val sessionId = _uiState.value.selectedSessionId ?: return
        renameSession(sessionId, title)
    }

    fun renameSession(
        sessionId: String,
        title: String,
    ) {
        if (closed.get()) return
        runSessionMutation { renameSessionAfterPersistenceDispatch(sessionId, title) }
    }

    private fun renameSessionAfterPersistenceDispatch(
        sessionId: String,
        title: String,
    ) {
        if (_uiState.value.sessions.none { it.id == sessionId }) return
        sessionStore.renameSession(sessionId, title)
        _uiState.update {
            it.copy(sessions = sessionStore.listSessions().toUiState(), error = null, errorKey = null)
        }
    }

    fun deleteCurrentSession() {
        val sessionId = _uiState.value.selectedSessionId ?: return
        deleteSession(sessionId)
    }

    fun deleteSession(sessionId: String) {
        if (closed.get()) return
        runSessionMutation { deleteSessionAfterPersistenceDispatch(sessionId) }
    }

    private fun deleteSessionAfterPersistenceDispatch(sessionId: String) {
        val current = _uiState.value
        if (current.sessions.size <= 1 || generationJob?.isActive == true) return

        val deletingSelectedSession = current.selectedSessionId == sessionId
        if (deletingSelectedSession) deleteCurrentDraftMedia()
        val deletedMedia = sessionStore.mediaUrisForSession(sessionId)
        sessionStore.deleteSession(sessionId)
        conversationSelection.clear(sessionId)
        mediaRepository.deleteUnreferenced(deletedMedia, sessionStore.referencedMediaUris())
        val next =
            if (current.selectedSessionId == sessionId) {
                sessionStore.ensureSession()
            } else {
                sessionStore.listSessions().first { it.id == current.selectedSessionId }
            }
        conversationSelection.select(next.id)
        _uiState.update {
            it.copy(
                sessions = sessionStore.listSessions().toUiState(),
                selectedSessionId = next.id,
                messages = displayedMessages(next.id),
                hasOlderMessages = hasOlderMessages(next.id),
                prompt = "",
                imageAttachments = if (deletingSelectedSession) emptyList() else it.imageAttachments,
                audioPrompt = if (deletingSelectedSession) null else it.audioPrompt,
                error = null,
                errorKey = null,
            )
        }
    }

    fun clearAllSessions() {
        if (closed.get()) return
        runSessionMutation(::clearAllSessionsAfterPersistenceDispatch)
    }

    private fun clearAllSessionsAfterPersistenceDispatch() {
        if (generationJob?.isActive == true) return

        val deletedMedia = sessionStore.referencedMediaUris()
        deleteCurrentDraftMedia()
        sessionStore.clearSessions()
        mediaRepository.deleteUnreferenced(deletedMedia, emptySet())
        val replacement = sessionStore.ensureSession()
        conversationSelection.select(replacement.id)
        activeAssistantMessageId = null
        _uiState.update {
            it.copy(
                sessions = sessionStore.listSessions().toUiState(),
                selectedSessionId = replacement.id,
                messages = emptyList(),
                hasOlderMessages = false,
                prompt = "",
                imageAttachments = emptyList(),
                audioPrompt = null,
                toolInProgress = false,
                activeToolName = null,
                researchSources = emptyList(),
                error = null,
                errorKey = null,
            )
        }
    }

    private fun runSessionMutation(block: () -> Unit) {
        if (closed.get()) return
        if (!sessionMutationPending.compareAndSet(false, true)) return
        _uiState.update { it.copy(isPersistenceBusy = true) }
        scope.launch(persistenceDispatcher) {
            try {
                block()
            } finally {
                sessionMutationPending.set(false)
                _uiState.update { it.copy(isPersistenceBusy = false) }
            }
        }
    }

    fun onModelStartupState(state: ModelStartupState) {
        if (closed.get()) return
        val current = _uiState.value
        if (state is ModelStartupState.Available) {
            val discardedUris =
                buildList {
                    if (!state.model.inputCapabilities.image) addAll(current.imageAttachments.map { it.uri })
                    if (!state.model.canUseRecordedAudio()) current.audioPrompt?.uri?.let(::add)
                }
            deleteDraftMedia(discardedUris)
        } else {
            deleteCurrentDraftMedia()
        }
        when (state) {
            ModelStartupState.Missing -> {
                model = null
                stopActiveGeneration()
                unloadEngine()
                _uiState.update {
                    it.copy(
                        modelStatus = "Model missing; starting download",
                        modelStatusKey = null,
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
                        modelStatusKey = null,
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
                        modelStatusKey = null,
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
                inferenceConfig = requireNotNull(state.inference) {
                    "Selected Chat model does not define inference settings"
                }
                _uiState.update {
                    it.copy(
                        modelStatus = ChatUiState.MODEL_AVAILABLE,
                        modelStatusKey = UserMessageKey.ModelAvailable,
                        isLoadingModel = false,
                        canRetryModelDownload = false,
                        canAttachImage = state.model.inputCapabilities.image,
                        canUseAudioPrompt = state.model.canUseRecordedAudio(),
                        canEnableReasoning = state.model.reasoningCapabilities.request,
                        canShowReasoning = state.model.reasoningCapabilities.output,
                        reasoningEnabled = it.reasoningEnabled && state.model.reasoningCapabilities.request,
                        showReasoning = it.showReasoning && state.model.reasoningCapabilities.output,
                        imageAttachments = if (state.model.inputCapabilities.image) it.imageAttachments else emptyList(),
                        audioPrompt = if (state.model.canUseRecordedAudio()) it.audioPrompt else null,
                        error = null,
                        errorKey = null,
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
                        modelStatusKey = null,
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
        if (closed.get()) return
        val requestedState = _uiState.value
        if (!requestedState.canSubmit || generationJob?.isActive == true) return
        if (!submissionPending.compareAndSet(false, true)) return
        _uiState.update { it.copy(isPersistenceBusy = true) }
        scope.launch(persistenceDispatcher) {
            try {
                submitPromptAfterPersistenceDispatch(requestedState)
            } finally {
                submissionPending.set(false)
                _uiState.update { it.copy(isPersistenceBusy = false) }
            }
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    private fun submitPromptAfterPersistenceDispatch(current: ChatUiState) {
        if (generationJob?.isActive == true) return

        val sessionId = current.selectedSessionId ?: return
        val modelForRequest = model
        val inferenceForRequest =
            modelForRequest?.let { generationConfigProvider(it, inferenceConfig) } ?: inferenceConfig
        val submittedContent = current.toSubmittedContent(audioTranscriber.isAvailable)
        val submittedTitle =
            submittedContent.displayText.ifBlank {
                if ((submittedContent as? MessageContent.TextPrompt)?.imageAttachments?.isNotEmpty() == true) {
                    DEFAULT_IMAGE_PROMPT
                } else {
                    ""
                }
            }
        if (submittedContent !is MessageContent.AudioPromptContent) maybeTitleSession(sessionId, submittedTitle)
        val begunTurn = conversationCoordinator.beginUserTurn(sessionId, submittedContent)
        val history = begunTurn.history
        val userMessage = begunTurn.userMessage
        val requiresTranscriptionBeforeGeneration =
            submittedContent is MessageContent.AudioPromptContent && modelForRequest?.inputCapabilities?.audio != true
        if (!requiresTranscriptionBeforeGeneration) prepareAssistantMessage(sessionId, refresh = false)

        _uiState.update {
            it.copy(
                prompt = current.prompt,
                sessions = sessionStore.listSessions().toUiState(),
                messages = displayedMessages(sessionId),
                hasOlderMessages = hasOlderMessages(sessionId),
                isLoadingModel = true,
                isGenerating = true,
                toolInProgress = false,
                activeToolName = null,
                researchSources = emptyList(),
                error = null,
                errorKey = null,
            )
        }

        generationJob =
            scope.launch {
                if (modelForRequest == null) {
                    finalizeAssistantMessage()
                    _uiState.update {
                        it.copy(
                            isLoadingModel = false,
                            isGenerating = false,
                            error = null,
                            errorKey = UserMessageKey.ModelUnavailable,
                        )
                    }
                    return@launch
                }

                try {
                    val turnSettings = conversationTurnSettingsProvider(modelForRequest)
                    val effectiveContent =
                        if (submittedContent is MessageContent.AudioPromptContent) {
                            if (modelForRequest.inputCapabilities.audio) {
                                startAsyncTranscription(sessionId, userMessage.id, submittedContent)
                                submittedContent
                            } else {
                                transcribeAndPersist(sessionId, userMessage.id, submittedContent)
                                    ?: return@launch
                            }
                        } else {
                            submittedContent
                        }
                    val projected =
                        conversationCoordinator.project(
                            history = history,
                            current = effectiveContent,
                            inferenceConfig = inferenceForRequest,
                            effectiveSystemPrompt = turnSettings.systemInstruction,
                        )
                    val requestContent =
                        if (
                            effectiveContent is MessageContent.AudioPromptContent &&
                            !modelForRequest.inputCapabilities.audio
                        ) {
                            MessageContent.TextPrompt(effectiveContent.transcript.orEmpty())
                        } else {
                            projected.requestContent
                        }
                    if (activeAssistantMessageId == null) prepareAssistantMessage(sessionId)
                    engine.load(modelForRequest, inferenceForRequest)
                    _uiState.update { it.copy(isLoadingModel = false) }

                    conversationCoordinator
                        .generate(
                            engine,
                            PromptRequest(
                                content = requestContent,
                                chatMessages = projected.messages,
                                reasoningEnabled = current.reasoningEnabled && modelForRequest.reasoningCapabilities.request,
                                chatSessionId = sessionId,
                                advertisedToolNames = turnSettings.advertisedToolNames,
                            ),
                        ).collect { event ->
                            when (event) {
                                is GenerationEvent.Token -> appendAssistantToken(event.text)
                                is GenerationEvent.ReasoningToken -> appendAssistantReasoningToken(event.text)
                                is GenerationEvent.Metrics -> generationMetricsConsumer(modelForRequest, event.value)
                                is GenerationEvent.ToolStarted -> {
                                    _uiState.update {
                                        it.copy(
                                            toolInProgress = true,
                                            activeToolName = event.displayName,
                                            researchSources = pendingResearchSources,
                                        )
                                    }
                                }
                                is GenerationEvent.ToolFinished -> {
                                    pendingResearchSources =
                                        (pendingResearchSources + event.sources)
                                            .distinctBy { source -> source.canonicalUrl }
                                    _uiState.update {
                                        it.copy(
                                            toolInProgress = false,
                                            activeToolName = null,
                                            researchSources = pendingResearchSources,
                                        )
                                    }
                                }
                                is GenerationEvent.Failed -> {
                                    pendingResearchSources = emptyList()
                                    flushAssistantPresentation()
                                    flushAssistantMessage()
                                    _uiState.update {
                                        it.copy(
                                            isLoadingModel = false,
                                            isGenerating = false,
                                            toolInProgress = false,
                                            activeToolName = null,
                                            error = event.message,
                                            errorKey = null,
                                        )
                                    }
                                }
                                GenerationEvent.Completed -> {
                                    markAssistantIncompleteWhenNeeded()
                                    attachSourcesToAssistant(pendingResearchSources)
                                    flushAssistantPresentation()
                                    flushAssistantMessage()
                                    _uiState.update {
                                        it.copy(
                                            isLoadingModel = false,
                                            isGenerating = false,
                                            toolInProgress = false,
                                            activeToolName = null,
                                            prompt = "",
                                            imageAttachments = emptyList(),
                                            audioPrompt = null,
                                            sessions = sessionStore.listSessions().toUiState(),
                                            completedAssistantMessageId = activeAssistantMessageId,
                                        )
                                    }
                                }
                            }
                        }
                } catch (error: Throwable) {
                    if (error is kotlinx.coroutines.CancellationException) return@launch
                    flushAssistantPresentation()
                    flushAssistantMessage()
                    _uiState.update {
                        it.copy(
                            isLoadingModel = false,
                            isGenerating = false,
                            error = error.message,
                            errorKey = if (error.message == null) UserMessageKey.GenerationFailed else null,
                        )
                    }
                } finally {
                    finalizeAssistantMessage()
                }
            }
    }

    private fun prepareAssistantMessage(sessionId: String, refresh: Boolean = true) {
        val assistantMessage =
            conversationCoordinator
                .appendAssistant(sessionId, MessageContent.TextPrompt(""))
                .toChatMessage()
        activeAssistantMessageId = assistantMessage.id
        synchronized(assistantBufferLock) {
            assistantBuffer = AssistantMessageBuffer(assistantMessage.id)
        }
        if (refresh) refreshSession(sessionId)
    }

    private fun startAsyncTranscription(
        sessionId: String,
        messageId: String,
        content: MessageContent.AudioPromptContent,
    ) {
        if (!audioTranscriber.isAvailable) return
        scope.launch { transcribeAndPersist(sessionId, messageId, content) }
    }

    private suspend fun transcribeAndPersist(
        sessionId: String,
        messageId: String,
        content: MessageContent.AudioPromptContent,
    ): MessageContent.AudioPromptContent? = try {
        val result = audioTranscriber.transcribe(content.audio)
        val transcript = result.transcript.trim()
        check(transcript.isNotBlank()) { "No speech was recognized" }
        val completed = content.copy(
            transcript = transcript,
            transcriptionStatus = AudioTranscriptionStatus.Completed,
            transcriptionError = null,
            transcriptionFailureKind = null,
            transcriptionDiagnostic = result.diagnosticReport,
            transcriptionMayBeIncomplete = result.mayBeIncomplete,
            transcriptionIncompleteReason = result.incompleteReason,
        )
        sessionStore.updateMessage(messageId, completed)
        maybeTitleSessionFromTranscript(sessionId, transcript)
        refreshSession(sessionId)
        completed
    } catch (error: Throwable) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        val typedFailure = (error as? AudioTranscriptionException)?.failure
        val message = typedFailure?.userMessage ?: error.message ?: "Audio transcription failed"
        sessionStore.updateMessage(
            messageId,
            content.copy(
                transcriptionStatus = AudioTranscriptionStatus.Failed,
                transcriptionError = message,
                transcriptionFailureKind = typedFailure?.kind ?: AudioTranscriptionFailureKind.Unknown,
                transcriptionDiagnostic = typedFailure?.diagnosticReport,
                transcriptionMayBeIncomplete = false,
                transcriptionIncompleteReason = null,
            ),
        )
        refreshSession(sessionId)
        if (model?.inputCapabilities?.audio != true) {
            _uiState.update {
                it.copy(isLoadingModel = false, isGenerating = false, error = message, errorKey = null)
            }
        }
        null
    }

    private fun refreshSession(sessionId: String) {
        _uiState.update { state ->
            if (state.selectedSessionId != sessionId) {
                state
            } else {
                state.copy(
                    sessions = sessionStore.listSessions().toUiState(),
                    messages = displayedMessages(sessionId),
                    hasOlderMessages = hasOlderMessages(sessionId),
                    messageDisplayRevision = state.messageDisplayRevision + 1,
                )
            }
        }
    }

    private fun maybeTitleSessionFromTranscript(sessionId: String, transcript: String) {
        val session = sessionStore.listSessions().firstOrNull { it.id == sessionId } ?: return
        if (session.title == "New chat" && sessionStore.getMessages(sessionId).firstOrNull()?.id != null) {
            sessionStore.renameSession(sessionId, transcript.take(42))
        }
    }

    private fun deleteCurrentDraftMedia() {
        val current = _uiState.value
        deleteDraftMedia(current.imageAttachments.map { it.uri } + listOfNotNull(current.audioPrompt?.uri))
    }

    private fun deleteDraftMedia(uris: Iterable<String>) {
        val persistedUris = sessionStore.referencedMediaUris()
        uris.forEach { mediaRepository.deleteDraft(it, persistedUris) }
    }

    fun onLeavingChat() {
        stopActiveGeneration()
        _uiState.update {
            it.copy(
                isLoadingModel = false,
                isGenerating = false,
                toolInProgress = false,
                activeToolName = null,
                researchSources = emptyList(),
            )
        }
    }

    fun onEnteringChat() {
        if (closed.get()) return
        if (generationJob?.isActive == true) return
        val session =
            conversationSelection.currentSessionId
                ?.let { selected -> sessionStore.listSessions().firstOrNull { it.id == selected } }
                ?: sessionStore.ensureSession().also { conversationSelection.select(it.id) }
        _uiState.update {
            it.copy(
                sessions = sessionStore.listSessions().toUiState(),
                selectedSessionId = session.id,
                messages = displayedMessages(session.id),
                hasOlderMessages = hasOlderMessages(session.id),
                messageDisplayRevision = it.messageDisplayRevision + 1,
            )
        }
    }

    fun loadOlderMessages() {
        if (closed.get()) return
        runSessionMutation(::loadOlderMessagesAfterPersistenceDispatch)
    }

    private fun loadOlderMessagesAfterPersistenceDispatch() {
        val sessionId = _uiState.value.selectedSessionId ?: return
        if (!_uiState.value.hasOlderMessages || _uiState.value.isLoadingOlderMessages) return
        _uiState.update { it.copy(isLoadingOlderMessages = true) }
        messageWindowSizes[sessionId] = messageWindowSize(sessionId) + MESSAGE_WINDOW_PAGE_SIZE
        _uiState.update {
            it.copy(
                messages = displayedMessages(sessionId),
                hasOlderMessages = hasOlderMessages(sessionId),
                isLoadingOlderMessages = false,
                messageDisplayRevision = it.messageDisplayRevision + 1L,
            )
        }
    }

    fun cancelGeneration() {
        if (closed.get()) return
        stopActiveGeneration()
        unloadEngine()
        _uiState.update {
            it.copy(
                isLoadingModel = false,
                isGenerating = false,
                toolInProgress = false,
                activeToolName = null,
                researchSources = emptyList(),
                error = null,
                errorKey = null,
            )
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        generationJob?.cancel()
        assistantPersistenceJob?.cancel()
        assistantPresentationJob?.cancel()
        scope.cancel()
        _uiState.update {
            it.copy(
                isLoadingModel = false,
                isGenerating = false,
                isPersistenceBusy = false,
                toolInProgress = false,
                activeToolName = null,
            )
        }
    }

    private fun stopActiveGeneration() {
        generationJob?.cancel()
        generationJob = null
        finalizeAssistantMessage()
    }

    private fun unloadEngine() {
        scope.launch {
            engine.unload()
        }
    }

    private fun appendAssistantToken(token: String) {
        appendAssistantContent { it.text.append(token) }
    }

    private fun appendAssistantReasoningToken(token: String) {
        appendAssistantContent { it.reasoning.append(token) }
    }

    private fun attachSourcesToAssistant(sources: List<com.jesjobom.ararai.knowledge.KnowledgeSource>) {
        if (sources.isEmpty()) return
        appendAssistantContent { it.sources = sources }
    }

    private fun markAssistantIncompleteWhenNeeded() {
        appendAssistantContent { buffer ->
            if (buffer.text.isBlank() && buffer.reasoning.isNotBlank()) {
                buffer.completionStatus = AssistantCompletionStatus.Incomplete
            }
        }
    }

    private fun appendAssistantContent(update: (AssistantMessageBuffer) -> Unit) {
        val assistantMessageId = activeAssistantMessageId ?: return
        synchronized(assistantBufferLock) {
            val buffer = assistantBuffer?.takeIf { it.messageId == assistantMessageId } ?: return
            update(buffer)
            buffer.persistenceDirty = true
            buffer.presentationDirty = true
        }
        scheduleAssistantPresentation()
        scheduleAssistantPersistence()
    }

    private fun scheduleAssistantPresentation() {
        synchronized(assistantBufferLock) {
            if (assistantPresentationJob?.isActive == true || assistantBuffer?.presentationDirty != true) return
            assistantPresentationJob =
                scope.launch {
                    delay(assistantPresentationIntervalMillis)
                    flushAssistantPresentation()
                }
        }
    }

    private fun flushAssistantPresentation() {
        val pending =
            synchronized(assistantBufferLock) {
                assistantPresentationJob?.cancel()
                assistantPresentationJob = null
                val buffer = assistantBuffer?.takeIf { it.presentationDirty } ?: return
                buffer.presentationDirty = false
                buffer.messageId to buffer.snapshot()
            }
        _uiState.update { state ->
            val assistantIndex = state.messages.indexOfLast { it.id == pending.first }
            if (assistantIndex < 0) return@update state
            val updatedMessages = state.messages.toMutableList()
            updatedMessages[assistantIndex] = updatedMessages[assistantIndex].copy(content = pending.second)
            state.copy(
                messages = updatedMessages,
                messageDisplayRevision = state.messageDisplayRevision + 1L,
            )
        }
    }

    private fun scheduleAssistantPersistence() {
        synchronized(assistantBufferLock) {
            if (assistantPersistenceJob?.isActive == true || assistantBuffer?.persistenceDirty != true) return
            assistantPersistenceJob =
                scope.launch {
                    delay(assistantPersistenceIntervalMillis)
                    flushAssistantMessage()
                }
        }
    }

    private fun flushAssistantMessage() {
        val pending =
            synchronized(assistantBufferLock) {
                assistantPersistenceJob?.cancel()
                assistantPersistenceJob = null
                val buffer = assistantBuffer?.takeIf { it.persistenceDirty } ?: return
                buffer.persistenceDirty = false
                buffer.messageId to buffer.snapshot()
            }
        conversationCoordinator.updateMessage(pending.first, pending.second)
    }

    private fun finalizeAssistantMessage() {
        flushAssistantPresentation()
        flushAssistantMessage()
        synchronized(assistantBufferLock) {
            assistantPresentationJob?.cancel()
            assistantPresentationJob = null
            assistantPersistenceJob?.cancel()
            assistantPersistenceJob = null
            assistantBuffer = null
            activeAssistantMessageId = null
            pendingResearchSources = emptyList()
        }
    }

    private fun maybeTitleSession(
        sessionId: String,
        submittedPrompt: String,
    ) {
        val session = sessionStore.listSessions().firstOrNull { it.id == sessionId } ?: return
        if (session.title != "New chat" || sessionStore.getMessages(sessionId).isNotEmpty()) return
        sessionStore.renameSession(sessionId, submittedPrompt.take(42))
    }

    private fun ChatUiState.toSubmittedContent(transcriptionAvailable: Boolean): MessageContent = audioPrompt?.let {
        MessageContent.AudioPromptContent(
            audio = it,
            transcriptionStatus =
            if (transcriptionAvailable) AudioTranscriptionStatus.Pending else AudioTranscriptionStatus.NotRequested,
        )
    }
        ?: MessageContent.TextPrompt(
            text = prompt.trim(),
            imageAttachments = imageAttachments,
        )

    private fun LocalModel?.canUseRecordedAudio(): Boolean = this?.let {
        it.inputCapabilities.audio || (it.inputCapabilities.text && audioTranscriber.isAvailable)
    } == true

    private fun List<ChatSession>.toUiState(): List<ChatSessionUiState> = map { ChatSessionUiState(id = it.id, title = it.title) }

    private fun List<StoredChatMessage>.toChatMessages(): List<ChatMessage> = map { it.toChatMessage() }

    private fun displayedMessages(sessionId: String): List<ChatMessage> {
        val limit = messageWindowSize(sessionId)
        return sessionStore.getRecentMessages(sessionId, limit).toChatMessages()
    }

    private fun hasOlderMessages(sessionId: String): Boolean {
        val displayedCount = messageWindowSize(sessionId)
        return sessionStore.countMessages(sessionId) > displayedCount
    }

    private fun messageWindowSize(sessionId: String): Int {
        messageWindowSizes.putIfAbsent(sessionId, INITIAL_MESSAGE_WINDOW_SIZE)
        return checkNotNull(messageWindowSizes[sessionId])
    }

    private fun StoredChatMessage.toChatMessage(): ChatMessage = ChatMessage(role = role, content = content, id = id)

    private class AssistantMessageBuffer(
        val messageId: String,
        val text: StringBuilder = StringBuilder(),
        val reasoning: StringBuilder = StringBuilder(),
        var sources: List<com.jesjobom.ararai.knowledge.KnowledgeSource> = emptyList(),
        var completionStatus: AssistantCompletionStatus = AssistantCompletionStatus.Complete,
        var persistenceDirty: Boolean = false,
        var presentationDirty: Boolean = false,
    ) {
        fun snapshot(): MessageContent.TextPrompt = MessageContent.TextPrompt(
            text = text.toString(),
            reasoningText = reasoning.toString(),
            sources = sources,
            completionStatus = completionStatus,
        )
    }

    private companion object {
        const val DEFAULT_ASSISTANT_PERSISTENCE_INTERVAL_MILLIS = 250L
        const val DEFAULT_ASSISTANT_PRESENTATION_INTERVAL_MILLIS = 50L
        const val INITIAL_MESSAGE_WINDOW_SIZE = 100
        const val MESSAGE_WINDOW_PAGE_SIZE = 100
    }
}
