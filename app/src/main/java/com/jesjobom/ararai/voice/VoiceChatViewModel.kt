@file:Suppress(
    "TooManyFunctions",
    "TooGenericExceptionCaught",
    "InstanceOfCheckForException",
    "ReturnCount",
    "MaxLineLength",
    "LongParameterList",
    "CyclomaticComplexMethod",
    "LargeClass",
)

package com.jesjobom.ararai.voice

import android.util.Log
import com.jesjobom.ararai.chat.AssistantCompletionStatus
import com.jesjobom.ararai.chat.AudioTranscriber
import com.jesjobom.ararai.chat.AudioTranscriptionStatus
import com.jesjobom.ararai.chat.ChatMediaRepository
import com.jesjobom.ararai.chat.ChatRole
import com.jesjobom.ararai.chat.ChatSession
import com.jesjobom.ararai.chat.ChatSessionStore
import com.jesjobom.ararai.chat.ChatSessionUiState
import com.jesjobom.ararai.chat.ConversationContextProjector
import com.jesjobom.ararai.chat.ConversationCoordinator
import com.jesjobom.ararai.chat.ConversationSelection
import com.jesjobom.ararai.chat.ConversationTurnSettings
import com.jesjobom.ararai.chat.ImageAttachment
import com.jesjobom.ararai.chat.InMemoryChatSessionStore
import com.jesjobom.ararai.chat.MessageContent
import com.jesjobom.ararai.chat.NoOpChatMediaRepository
import com.jesjobom.ararai.chat.UnavailableAudioTranscriber
import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.GenerationMetrics
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.LocalLlmWorkload
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelStartupState
import com.jesjobom.ararai.ui.UserMessageKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

internal class VoiceChatViewModel(
    private val engine: LocalLlmEngine,
    private val systemPrompt: String,
    private val conversationTurnSettingsProvider: (LocalModel?) -> ConversationTurnSettings = {
        ConversationTurnSettings(systemPrompt)
    },
    private val generationConfigProvider: (LocalModel, InferenceConfig) -> InferenceConfig = { _, config -> config },
    private val generationMetricsConsumer: (LocalModel, GenerationMetrics) -> Unit = { _, _ -> },
    private val preferences: VoiceChatPreferences,
    private val captureFactory: (VoiceChatSettings) -> VoiceTurnCapture,
    speechQueueFactory: ((IntRange) -> Unit, (IntRange) -> Unit, () -> Unit, (UserMessageKey) -> Unit) -> VoiceSpeechQueue,
    private val sessionStore: ChatSessionStore = InMemoryChatSessionStore(),
    private val mediaRepository: ChatMediaRepository = NoOpChatMediaRepository,
    private val audioTranscriber: AudioTranscriber = UnavailableAudioTranscriber,
    private val voiceSessionTitleProvider: () -> String = { "Voice chat" },
    private val conversationSelection: ConversationSelection = ConversationSelection(),
    private val conversationCoordinator: ConversationCoordinator =
        ConversationCoordinator(
            sessionStore = sessionStore,
            contextProjector = ConversationContextProjector(systemPrompt),
        ),
    private val persistenceDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {
    private val lifecycleJob = SupervisorJob()
    private val scope = CoroutineScope(scope.coroutineContext + lifecycleJob)
    private val mutableState = MutableStateFlow(VoiceChatUiState(settings = preferences.settings.value))
    val state: StateFlow<VoiceChatUiState> = mutableState.asStateFlow()
    private var model: LocalModel? = null
    private var inference: InferenceConfig? = null
    private var capture: VoiceTurnCapture? = null
    private var modelLoadJob: Job? = null
    private var modelLoadAttempt = 0L
    private var generationJob: Job? = null
    private var runId = 0L
    private var turn = 0
    private var currentAudio: File? = null
    private var generationStartedAt = 0L
    private var firstTokenAt: Long? = null
    private var firstSpeechAt: Long? = null
    private var currentCapture: CapturedVoiceTurn? = null
    private var deferredCameraTurn: Pair<Long, CapturedVoiceTurn>? = null
    private var automaticPhotoCaptureSequence = 0L
    private var currentSessionId: String? = null
    private val sessionMutationPending = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private var segmenter = VoiceResponseSegmenter(preferences.settings.value.minimumWords)
    private val speechQueue = speechQueueFactory(::onSpeechStarted, ::onSpeechRange, ::onSpeechComplete, ::fail)

    init {
        scope.launch {
            preferences.settings.collect { settings -> mutableState.update { it.copy(settings = settings) } }
        }
    }

    fun onModelStartupState(startup: ModelStartupState) {
        if (startup is ModelStartupState.Available) {
            val modelChanged = model != startup.model || inference != startup.inference
            if (model != null && modelChanged) stop()
            model = startup.model
            inference = startup.inference
            mutableState.update {
                it.copy(
                    modelAvailable = true,
                    modelSupportsAudio = startup.model.inputCapabilities.audio,
                    modelSupportsImage = startup.model.inputCapabilities.image,
                    canEnableReasoning = startup.model.reasoningCapabilities.request,
                    transcriptionAvailable = audioTranscriber.isAvailable,
                    isModelLoaded = it.isModelLoaded && !modelChanged,
                )
            }
        } else {
            stop()
            model = null
            inference = null
            mutableState.update {
                it.copy(
                    modelAvailable = false,
                    modelSupportsAudio = false,
                    modelSupportsImage = false,
                    canEnableReasoning = false,
                    transcriptionAvailable = audioTranscriber.isAvailable,
                    isLoadingModel = false,
                    isModelLoaded = false,
                )
            }
        }
    }

    fun onEnteringVoiceChat() {
        if (modelLoadJob?.isActive == true) return
        val activeModel = model ?: return
        val activeInference = inference?.let { generationConfigProvider(activeModel, it) } ?: return
        if (!activeModel.inputCapabilities.audio && !audioTranscriber.isAvailable) return
        // The shared runtime may have been reconfigured by normal Chat while
        // Voice Chat was off screen. Invalidate the prior readiness state
        // synchronously so Start cannot race ahead of multimodal preparation.
        mutableState.update {
            it.copy(isLoadingModel = true, isModelLoaded = false, error = null, errorKey = null)
        }
        val activeAttempt = ++modelLoadAttempt
        modelLoadJob = scope.launch(persistenceDispatcher) {
            val session = sessionStore.createSession("New chat")
            conversationSelection.select(session.id)
            currentSessionId = session.id
            refreshSessions(session.id)
            try {
                val loadStartedAt = System.nanoTime()
                val workload = if (activeModel.inputCapabilities.audio) LocalLlmWorkload.Audio else LocalLlmWorkload()
                engine.loadForWorkload(activeModel, activeInference, workload)
                val loadMillis = loadStartedAt.elapsedMillis()
                Log.d(
                    LOG_TAG,
                    "Voice model ready: workload=$workload, load=$loadMillis ms",
                )
                if (
                    activeAttempt == modelLoadAttempt &&
                    model == activeModel &&
                    inference?.let { generationConfigProvider(activeModel, it) } == activeInference
                ) {
                    mutableState.update { it.copy(isLoadingModel = false, isModelLoaded = true) }
                }
            } catch (error: Throwable) {
                if (error !is kotlinx.coroutines.CancellationException && activeAttempt == modelLoadAttempt) {
                    mutableState.update {
                        it.copy(
                            isLoadingModel = false,
                            isModelLoaded = false,
                            phase = VoiceChatPhase.Error,
                            error = error.message,
                            errorKey = if (error.message == null) UserMessageKey.ModelLoadingFailed else null,
                        )
                    }
                }
            }
        }
    }

    fun updateSettings(settings: VoiceChatSettings) = preferences.update(settings)

    fun onCameraOpened() {
        if (state.value.phase != VoiceChatPhase.Listening) return
        capture?.resetSilenceWindow()
        mutableState.update { it.copy(cameraFlowActive = true) }
    }

    fun onCameraPreviewReady() {
        if (!state.value.cameraFlowActive) return
        capture?.resetSilenceWindow()
    }

    fun onCameraClosed() {
        val deferred = deferredCameraTurn.also { deferredCameraTurn = null }
        mutableState.update {
            it.copy(
                cameraFlowActive = false,
                automaticPhotoCaptureRequestId = null,
            )
        }
        if (deferred != null) {
            processTurn(deferred.first, deferred.second)
        } else if (state.value.phase == VoiceChatPhase.Listening) {
            capture?.resetSilenceWindow()
        }
    }

    fun useCapturedImage(image: ImageAttachment) {
        if (!state.value.canCapturePhoto) {
            mediaRepository.deleteDraft(image.uri, sessionStore.referencedMediaUris())
            return
        }
        val previous = state.value.pendingImageAttachment
        val deferred = deferredCameraTurn.also { deferredCameraTurn = null }
        mutableState.update {
            it.copy(
                pendingImageAttachment = image,
                cameraFlowActive = false,
                automaticPhotoCaptureRequestId = null,
            )
        }
        previous?.uri?.let { mediaRepository.deleteDraft(it, sessionStore.referencedMediaUris()) }
        if (deferred != null) {
            processTurn(deferred.first, deferred.second)
        } else {
            capture?.resetSilenceWindow()
        }
    }

    fun removeCapturedImage() {
        val previous = state.value.pendingImageAttachment
        mutableState.update { it.copy(pendingImageAttachment = null) }
        previous?.uri?.let { mediaRepository.deleteDraft(it, sessionStore.referencedMediaUris()) }
    }

    fun createSession() {
        runSessionMutation(::createSessionAfterPersistenceDispatch)
    }

    private fun createSessionAfterPersistenceDispatch() {
        if (state.value.isActive || state.value.isLoadingModel) return
        val session = sessionStore.createSession("New chat")
        currentSessionId = session.id
        conversationSelection.select(session.id)
        refreshSessions(session.id)
    }

    fun selectSession(sessionId: String) {
        runSessionMutation { selectSessionAfterPersistenceDispatch(sessionId) }
    }

    private fun selectSessionAfterPersistenceDispatch(sessionId: String) {
        if (state.value.isActive || state.value.isLoadingModel) return
        if (sessionStore.listSessions().none { it.id == sessionId }) return
        currentSessionId = sessionId
        conversationSelection.select(sessionId)
        refreshSessions(sessionId)
    }

    fun renameSession(sessionId: String, title: String) {
        runSessionMutation { renameSessionAfterPersistenceDispatch(sessionId, title) }
    }

    private fun renameSessionAfterPersistenceDispatch(sessionId: String, title: String) {
        if (state.value.isActive || state.value.isLoadingModel) return
        if (sessionStore.listSessions().none { it.id == sessionId }) return
        sessionStore.renameSession(sessionId, title)
        refreshSessions()
    }

    fun deleteSession(sessionId: String) {
        runSessionMutation { deleteSessionAfterPersistenceDispatch(sessionId) }
    }

    private fun deleteSessionAfterPersistenceDispatch(sessionId: String) {
        if (state.value.isActive || state.value.isLoadingModel || state.value.sessions.size <= 1) return
        val deletedMedia = sessionStore.mediaUrisForSession(sessionId)
        sessionStore.deleteSession(sessionId)
        conversationSelection.clear(sessionId)
        mediaRepository.deleteUnreferenced(deletedMedia, sessionStore.referencedMediaUris())
        val next =
            currentSessionId
                ?.takeUnless { it == sessionId }
                ?.let { selected -> sessionStore.listSessions().firstOrNull { it.id == selected } }
                ?: sessionStore.ensureSession()
        currentSessionId = next.id
        conversationSelection.select(next.id)
        refreshSessions(next.id)
    }

    fun clearAllSessions() {
        runSessionMutation(::clearAllSessionsAfterPersistenceDispatch)
    }

    private fun clearAllSessionsAfterPersistenceDispatch() {
        if (state.value.isActive || state.value.isLoadingModel) return
        val deletedMedia = sessionStore.referencedMediaUris()
        sessionStore.clearSessions()
        mediaRepository.deleteUnreferenced(deletedMedia, emptySet())
        val replacement = sessionStore.ensureSession()
        currentSessionId = replacement.id
        conversationSelection.select(replacement.id)
        refreshSessions(replacement.id)
    }

    private fun runSessionMutation(block: () -> Unit) {
        if (!sessionMutationPending.compareAndSet(false, true)) return
        scope.launch(persistenceDispatcher) {
            try {
                block()
            } finally {
                sessionMutationPending.set(false)
            }
        }
    }

    fun start() {
        if (!state.value.canStart) return
        runId++
        mutableState.update {
            it.copy(
                phase = VoiceChatPhase.Listening,
                responsePreview = "",
                spokenRange = null,
                readingAnchor = 0,
                notice = null,
                noticeKey = null,
                error = null,
                errorKey = null,
            )
        }
        startCapture(runId)
    }

    private fun startCapture(activeRun: Long) {
        if (activeRun != runId || state.value.phase == VoiceChatPhase.Idle) return
        capture?.close()
        capture = captureFactory(preferences.settings.value).also { newCapture ->
            newCapture.start(
                onTurn = { turn -> scope.launch { onCapturedTurn(activeRun, turn) } },
                onError = { message -> scope.launch { fail(message) } },
            )
        }
        mutableState.update { it.copy(phase = VoiceChatPhase.Listening, error = null, errorKey = null) }
    }

    private fun onCapturedTurn(activeRun: Long, captured: CapturedVoiceTurn) {
        if (activeRun != runId) {
            File(captured.prompt.uri).delete()
            return
        }
        if (state.value.cameraFlowActive && state.value.pendingImageAttachment == null) {
            capture = null
            deferredCameraTurn = activeRun to captured
            automaticPhotoCaptureSequence++
            mutableState.update {
                it.copy(automaticPhotoCaptureRequestId = automaticPhotoCaptureSequence)
            }
            return
        }
        processTurn(activeRun, captured)
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun processTurn(activeRun: Long, captured: CapturedVoiceTurn) {
        if (activeRun != runId) {
            File(captured.prompt.uri).delete()
            return
        }
        capture = null
        currentAudio = File(captured.prompt.uri)
        currentCapture = captured
        val capturedImage = state.value.pendingImageAttachment
        generationStartedAt = System.currentTimeMillis()
        firstTokenAt = null
        firstSpeechAt = null
        segmenter = VoiceResponseSegmenter(preferences.settings.value.minimumWords)
        mutableState.update {
            it.copy(
                phase = VoiceChatPhase.Processing,
                responsePreview = "",
                spokenRange = null,
                readingAnchor = 0,
                toolInProgress = false,
                activeToolName = null,
                researchSources = emptyList(),
                notice = null,
                noticeKey = null,
            )
        }
        val activeModel = model ?: return fail("Model unavailable")
        val activeInference =
            inference?.let { generationConfigProvider(activeModel, it) }
                ?: return fail("Inference configuration unavailable")
        val sessionId = currentSessionId ?: return fail("Conversation unavailable")
        generationJob = scope.launch(persistenceDispatcher) {
            try {
                val persistentPrompt = persistAudio(captured)
                val initialContent =
                    MessageContent.AudioPromptContent(
                        audio = persistentPrompt,
                        imageAttachments = listOfNotNull(capturedImage),
                        transcriptionStatus =
                        if (audioTranscriber.isAvailable) {
                            AudioTranscriptionStatus.Pending
                        } else {
                            AudioTranscriptionStatus.NotRequested
                        },
                    )
                val begunTurn = conversationCoordinator.beginUserTurn(sessionId, initialContent)
                val history = begunTurn.history
                val userMessage = begunTurn.userMessage
                val effectiveContent =
                    if (activeModel.inputCapabilities.audio) {
                        if (audioTranscriber.isAvailable) {
                            scope.launch {
                                transcribeAndPersist(sessionId, userMessage.id, initialContent)
                            }
                        }
                        initialContent
                    } else {
                        transcribeAndPersist(sessionId, userMessage.id, initialContent)
                            ?: return@launch fail("Audio transcription failed")
                    }
                maybeTitleConversation(sessionId, userMessage.id, effectiveContent)
                val turnSettings = conversationTurnSettingsProvider(activeModel)
                val projected =
                    if (activeModel.inputCapabilities.audio) {
                        conversationCoordinator.project(
                            history,
                            effectiveContent,
                            activeInference,
                            turnSettings.systemInstruction,
                        )
                    } else {
                        conversationCoordinator.project(
                            history,
                            MessageContent.TextPrompt(
                                text = effectiveContent.transcript.orEmpty(),
                                imageAttachments = effectiveContent.imageAttachments,
                            ),
                            activeInference,
                            turnSettings.systemInstruction,
                        )
                    }
                val loadStartedAt = System.nanoTime()
                engine.load(activeModel, activeInference)
                val loadMillis = loadStartedAt.elapsedMillis()
                val prepareStartedAt = System.nanoTime()
                val workload = LocalLlmWorkload(
                    image = effectiveContent.imageAttachments.isNotEmpty(),
                    audio = activeModel.inputCapabilities.audio,
                )
                engine.prepare(workload)
                Log.d(
                    LOG_TAG,
                    "Voice turn runtime check: load=$loadMillis ms, audioPrepare=${prepareStartedAt.elapsedMillis()} ms",
                )
                val answer = StringBuilder()
                val reasoning = StringBuilder()
                var answerSources = emptyList<com.jesjobom.ararai.knowledge.KnowledgeSource>()
                conversationCoordinator.generate(
                    engine,
                    PromptRequest(
                        content = projected.requestContent,
                        chatMessages = projected.messages,
                        reasoningEnabled =
                        mutableState.value.settings.reasoningEnabled &&
                            activeModel.reasoningCapabilities.request,
                        chatSessionId = sessionId,
                        advertisedToolNames = turnSettings.advertisedToolNames,
                    ),
                ).collect { event ->
                    if (activeRun != runId) return@collect
                    when (event) {
                        is GenerationEvent.Token -> {
                            if (firstTokenAt == null) {
                                val now = System.currentTimeMillis()
                                firstTokenAt = now
                                Log.d(LOG_TAG, "Voice turn first token: ${now - generationStartedAt} ms")
                            }
                            answer.append(event.text)
                            mutableState.update { it.copy(responsePreview = answer.toString()) }
                            segmenter.append(answer.toString()).forEach(speechQueue::enqueue)
                        }
                        is GenerationEvent.ReasoningToken -> reasoning.append(event.text)
                        is GenerationEvent.Metrics -> generationMetricsConsumer(activeModel, event.value)
                        is GenerationEvent.ToolStarted -> {
                            mutableState.update {
                                it.copy(
                                    toolInProgress = true,
                                    activeToolName = event.displayName,
                                    researchSources = answerSources,
                                )
                            }
                        }
                        is GenerationEvent.ToolFinished -> {
                            answerSources =
                                (answerSources + event.sources)
                                    .distinctBy { source -> source.canonicalUrl }
                            mutableState.update {
                                it.copy(
                                    toolInProgress = false,
                                    activeToolName = null,
                                    researchSources = answerSources,
                                )
                            }
                        }
                        is GenerationEvent.Failed -> fail(event.message)
                        GenerationEvent.Completed -> {
                            mutableState.update { it.copy(pendingImageAttachment = null) }
                            val incomplete = answer.isBlank() && reasoning.isNotBlank()
                            conversationCoordinator.appendAssistant(
                                sessionId,
                                MessageContent.TextPrompt(
                                    text = answer.toString(),
                                    reasoningText = reasoning.toString(),
                                    sources = answerSources,
                                    completionStatus =
                                    if (incomplete) {
                                        AssistantCompletionStatus.Incomplete
                                    } else {
                                        AssistantCompletionStatus.Complete
                                    },
                                ),
                            )
                            if (incomplete) {
                                mutableState.update {
                                    it.copy(
                                        responsePreview = "",
                                        notice = null,
                                        noticeKey = UserMessageKey.NoFinalAnswer,
                                    )
                                }
                            } else {
                                segmenter.complete(answer.toString()).forEach(speechQueue::enqueue)
                            }
                            speechQueue.markGenerationComplete()
                        }
                    }
                }
            } catch (error: Throwable) {
                if (activeRun == runId && error !is kotlinx.coroutines.CancellationException) fail(error.message ?: "Generation failed")
            }
        }
    }

    private fun persistAudio(captured: CapturedVoiceTurn): com.jesjobom.ararai.chat.AudioPrompt {
        val destination = mediaRepository.createDraftFile(prefix = "voice_", suffix = ".wav")
        return try {
            File(captured.prompt.uri).copyTo(destination, overwrite = true)
            captured.prompt.copy(uri = destination.absolutePath, byteSize = destination.length())
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    private suspend fun transcribeAndPersist(
        sessionId: String,
        messageId: String,
        content: MessageContent.AudioPromptContent,
    ): MessageContent.AudioPromptContent? = try {
        val result = audioTranscriber.transcribe(content.audio)
        val transcript = result.transcript.trim()
        if (transcript.isBlank()) return null
        content.copy(
            transcript = transcript,
            transcriptionStatus = AudioTranscriptionStatus.Completed,
            transcriptionDiagnostic = result.diagnosticReport,
            transcriptionMayBeIncomplete = result.mayBeIncomplete,
            transcriptionIncompleteReason = result.incompleteReason,
        ).also {
            conversationCoordinator.updateMessage(messageId, it)
            maybeTitleConversation(sessionId, messageId, it)
        }
    } catch (error: Throwable) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        content.copy(
            transcriptionStatus = AudioTranscriptionStatus.Failed,
            transcriptionError = error.message ?: "Audio transcription failed",
        ).also { conversationCoordinator.updateMessage(messageId, it) }
        null
    }

    private fun maybeTitleConversation(
        sessionId: String,
        messageId: String,
        content: MessageContent.AudioPromptContent,
    ) {
        val session = sessionStore.listSessions().firstOrNull { it.id == sessionId } ?: return
        val isFirstMessage = sessionStore.getMessages(sessionId).firstOrNull()?.id == messageId
        if (session.title == "New chat" && isFirstMessage) {
            val title =
                content.transcript
                    ?.takeIf(String::isNotBlank)
                    ?.take(42)
                    ?: voiceSessionTitleProvider()
                        .takeIf { content.transcriptionStatus == AudioTranscriptionStatus.NotRequested }
                    ?: return
            sessionStore.renameSession(sessionId, title)
            refreshSessions(sessionId)
        }
    }

    private fun refreshSessions(selectedSessionId: String? = currentSessionId) {
        mutableState.update {
            it.copy(
                sessions = sessionStore.listSessions().toUiState(),
                selectedSessionId = selectedSessionId,
            )
        }
    }

    private fun onSpeechStarted(sourceRange: IntRange) {
        if (firstSpeechAt == null) firstSpeechAt = System.currentTimeMillis()
        mutableState.update {
            it.copy(phase = VoiceChatPhase.Speaking, spokenRange = sourceRange, readingAnchor = sourceRange.first)
        }
    }

    private fun onSpeechRange(sourceRange: IntRange) {
        mutableState.update { current ->
            val bounded = sourceRange.boundedBy(current.responsePreview.length) ?: return@update current
            current.copy(spokenRange = bounded, readingAnchor = bounded.last)
        }
    }

    private fun onSpeechComplete() {
        if (state.value.phase == VoiceChatPhase.Idle) return
        mutableState.update {
            it.copy(spokenRange = null, readingAnchor = it.responsePreview.lastIndex.coerceAtLeast(0))
        }
        recordDiagnostic("completed")
        deleteCurrentAudio()
        startCapture(runId)
    }

    private fun recordDiagnostic(outcome: String) {
        val captured = currentCapture ?: return
        val diagnostic = VoiceDiagnostic(
            turn = ++turn,
            vadProvider = preferences.settings.value.vadProvider,
            captureSource = preferences.settings.value.captureSource,
            noiseSuppressionActive = captured.noiseSuppressionActive,
            speechMillis = captured.speechMillis,
            inferenceToFirstTokenMillis = firstTokenAt?.minus(generationStartedAt),
            inferenceToFirstSpeechMillis = firstSpeechAt?.minus(generationStartedAt),
            outcome = outcome,
        )
        mutableState.update { it.copy(diagnostics = (it.diagnostics + diagnostic).takeLast(20)) }
        currentCapture = null
    }

    fun clearDiagnostics() = mutableState.update { it.copy(diagnostics = emptyList()) }

    fun onLeavingVoiceChat() {
        modelLoadAttempt++
        modelLoadJob?.cancel()
        modelLoadJob = null
        // Readiness is profile-specific. Normal Chat can change the shared
        // runtime before the next visit, so it must be proven again on entry.
        mutableState.update { it.copy(isLoadingModel = false, isModelLoaded = false) }
        stop()
        clearDiagnostics()
    }

    fun stop() {
        runId++
        deferredCameraTurn?.second?.prompt?.uri?.let { File(it).delete() }
        deferredCameraTurn = null
        capture?.cancel()
        capture = null
        generationJob?.cancel()
        generationJob = null
        speechQueue.stop()
        deleteCurrentAudio()
        removeCapturedImage()
        mutableState.update {
            it.copy(
                phase = VoiceChatPhase.Idle,
                responsePreview = "",
                spokenRange = null,
                readingAnchor = 0,
                toolInProgress = false,
                activeToolName = null,
                notice = null,
                noticeKey = null,
                error = null,
                errorKey = null,
                cameraFlowActive = false,
                automaticPhotoCaptureRequestId = null,
            )
        }
    }

    fun fail(message: String) {
        deferredCameraTurn?.second?.prompt?.uri?.let { File(it).delete() }
        deferredCameraTurn = null
        capture?.cancel()
        capture = null
        generationJob?.cancel()
        generationJob = null
        speechQueue.stop()
        recordDiagnostic("failed")
        deleteCurrentAudio()
        removeCapturedImage()
        mutableState.update {
            it.copy(
                phase = VoiceChatPhase.Error,
                toolInProgress = false,
                activeToolName = null,
                error = message,
                errorKey = null,
            )
        }
    }

    private fun fail(messageKey: UserMessageKey) {
        deferredCameraTurn?.second?.prompt?.uri?.let { File(it).delete() }
        deferredCameraTurn = null
        capture?.cancel()
        capture = null
        generationJob?.cancel()
        generationJob = null
        speechQueue.stop()
        recordDiagnostic("failed")
        deleteCurrentAudio()
        removeCapturedImage()
        mutableState.update {
            it.copy(
                phase = VoiceChatPhase.Error,
                toolInProgress = false,
                activeToolName = null,
                error = null,
                errorKey = messageKey,
            )
        }
    }

    fun dismissError() {
        mutableState.update { it.copy(phase = VoiceChatPhase.Idle, error = null, errorKey = null) }
    }

    private fun deleteCurrentAudio() {
        currentAudio?.delete()
        currentAudio = null
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        stop()
        speechQueue.close()
        scope.cancel()
    }

    private fun Long.elapsedMillis(): Long = (System.nanoTime() - this) / 1_000_000

    private fun IntRange.boundedBy(textLength: Int): IntRange? {
        if (textLength <= 0) return null
        val boundedStart = first.coerceIn(0, textLength - 1)
        val boundedEnd = last.coerceIn(boundedStart, textLength - 1)
        return boundedStart..boundedEnd
    }

    private fun List<ChatSession>.toUiState(): List<ChatSessionUiState> = map { ChatSessionUiState(id = it.id, title = it.title) }

    private companion object {
        const val LOG_TAG = "ArarAI.Voice"
    }
}
