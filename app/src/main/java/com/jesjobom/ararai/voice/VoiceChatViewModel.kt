@file:Suppress(
    "TooManyFunctions",
    "TooGenericExceptionCaught",
    "InstanceOfCheckForException",
    "ReturnCount",
    "MaxLineLength",
    "LongParameterList",
    "CyclomaticComplexMethod",
)

package com.jesjobom.ararai.voice

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.jesjobom.ararai.chat.InMemoryChatSessionStore
import com.jesjobom.ararai.chat.MessageContent
import com.jesjobom.ararai.chat.NoOpChatMediaRepository
import com.jesjobom.ararai.chat.UnavailableAudioTranscriber
import com.jesjobom.ararai.chat.mediaUris
import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.LocalLlmWorkload
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelStartupState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

internal class VoiceChatViewModel(
    private val engine: LocalLlmEngine,
    private val systemPrompt: String,
    private val systemInstructionProvider: () -> String = { systemPrompt },
    private val preferences: VoiceChatPreferences,
    private val captureFactory: (VoiceChatSettings) -> VoiceTurnCapture,
    speechQueueFactory: ((IntRange) -> Unit, (IntRange) -> Unit, () -> Unit, (String) -> Unit) -> VoiceSpeechQueue,
    private val sessionStore: ChatSessionStore = InMemoryChatSessionStore(),
    private val mediaRepository: ChatMediaRepository = NoOpChatMediaRepository,
    private val audioTranscriber: AudioTranscriber = UnavailableAudioTranscriber,
    private val conversationSelection: ConversationSelection = ConversationSelection(),
    private val conversationCoordinator: ConversationCoordinator =
        ConversationCoordinator(
            sessionStore = sessionStore,
            contextProjector = ConversationContextProjector(systemPrompt),
        ),
) : ViewModel() {
    private val mutableState = MutableStateFlow(VoiceChatUiState(settings = preferences.settings.value))
    val state: StateFlow<VoiceChatUiState> = mutableState.asStateFlow()
    private var model: LocalModel? = null
    private var inference: InferenceConfig? = null
    private var capture: VoiceTurnCapture? = null
    private var modelLoadJob: Job? = null
    private var generationJob: Job? = null
    private var runId = 0L
    private var turn = 0
    private var currentAudio: File? = null
    private var generationStartedAt = 0L
    private var firstTokenAt: Long? = null
    private var firstSpeechAt: Long? = null
    private var currentCapture: CapturedVoiceTurn? = null
    private var currentSessionId: String? = null
    private var segmenter = VoiceResponseSegmenter(preferences.settings.value.minimumWords)
    private val speechQueue = speechQueueFactory(::onSpeechStarted, ::onSpeechRange, ::onSpeechComplete, ::fail)

    init {
        viewModelScope.launch {
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
                    transcriptionAvailable = audioTranscriber.isAvailable,
                    isLoadingModel = false,
                    isModelLoaded = false,
                )
            }
        }
    }

    fun onEnteringVoiceChat() {
        if (modelLoadJob?.isActive == true) return
        val session =
            conversationSelection.currentSessionId
                ?.let { selected -> sessionStore.listSessions().firstOrNull { it.id == selected } }
                ?: sessionStore.ensureSession().also { conversationSelection.select(it.id) }
        currentSessionId = session.id
        refreshSessions(session.id)
        val activeModel = model ?: return
        val activeInference = inference ?: return
        if (!activeModel.inputCapabilities.audio && !audioTranscriber.isAvailable) return
        mutableState.update { it.copy(isLoadingModel = true, isModelLoaded = false, error = null) }
        modelLoadJob = viewModelScope.launch {
            try {
                val loadStartedAt = System.nanoTime()
                engine.load(activeModel, activeInference)
                val loadMillis = loadStartedAt.elapsedMillis()
                val prepareStartedAt = System.nanoTime()
                val workload = if (activeModel.inputCapabilities.audio) LocalLlmWorkload.Audio else LocalLlmWorkload()
                engine.prepare(workload)
                Log.d(
                    LOG_TAG,
                    "Voice model ready: load=$loadMillis ms, audioPrepare=${prepareStartedAt.elapsedMillis()} ms",
                )
                if (model == activeModel && inference == activeInference) {
                    mutableState.update { it.copy(isLoadingModel = false, isModelLoaded = true) }
                }
            } catch (error: Throwable) {
                if (error !is kotlinx.coroutines.CancellationException) {
                    mutableState.update {
                        it.copy(
                            isLoadingModel = false,
                            isModelLoaded = false,
                            phase = VoiceChatPhase.Error,
                            error = error.message ?: "Model loading failed",
                        )
                    }
                }
            }
        }
    }

    fun updateSettings(settings: VoiceChatSettings) = preferences.update(settings)

    fun createSession() {
        if (state.value.isActive || state.value.isLoadingModel) return
        val session = sessionStore.createSession("New chat")
        selectSession(session.id)
    }

    fun selectSession(sessionId: String) {
        if (state.value.isActive || state.value.isLoadingModel) return
        if (sessionStore.listSessions().none { it.id == sessionId }) return
        currentSessionId = sessionId
        conversationSelection.select(sessionId)
        refreshSessions(sessionId)
    }

    fun renameSession(sessionId: String, title: String) {
        if (state.value.isActive || state.value.isLoadingModel) return
        if (sessionStore.listSessions().none { it.id == sessionId }) return
        sessionStore.renameSession(sessionId, title)
        refreshSessions()
    }

    fun deleteSession(sessionId: String) {
        if (state.value.isActive || state.value.isLoadingModel || state.value.sessions.size <= 1) return
        val deletedMedia = sessionStore.getMessages(sessionId).flatMapTo(linkedSetOf()) { it.content.mediaUris() }
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
        if (state.value.isActive || state.value.isLoadingModel) return
        val deletedMedia = sessionStore.referencedMediaUris()
        sessionStore.clearSessions()
        mediaRepository.deleteUnreferenced(deletedMedia, emptySet())
        val replacement = sessionStore.ensureSession()
        currentSessionId = replacement.id
        conversationSelection.select(replacement.id)
        refreshSessions(replacement.id)
    }

    fun start() {
        if (!state.value.canStart) return
        runId++
        mutableState.update {
            it.copy(phase = VoiceChatPhase.Listening, responsePreview = "", spokenRange = null, readingAnchor = 0, error = null)
        }
        startCapture(runId)
    }

    private fun startCapture(activeRun: Long) {
        if (activeRun != runId || state.value.phase == VoiceChatPhase.Idle) return
        capture?.close()
        capture = captureFactory(preferences.settings.value).also { newCapture ->
            newCapture.start(
                onTurn = { turn -> viewModelScope.launch { processTurn(activeRun, turn) } },
                onError = { message -> viewModelScope.launch { fail(message) } },
            )
        }
        mutableState.update { it.copy(phase = VoiceChatPhase.Listening, error = null) }
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
        generationStartedAt = System.currentTimeMillis()
        firstTokenAt = null
        firstSpeechAt = null
        segmenter = VoiceResponseSegmenter(preferences.settings.value.minimumWords)
        mutableState.update {
            it.copy(phase = VoiceChatPhase.Processing, responsePreview = "", spokenRange = null, readingAnchor = 0)
        }
        val activeModel = model ?: return fail("Model unavailable")
        val activeInference = inference ?: return fail("Inference configuration unavailable")
        val sessionId = currentSessionId ?: return fail("Conversation unavailable")
        generationJob = viewModelScope.launch {
            try {
                val persistentPrompt = persistAudio(captured)
                val initialContent =
                    MessageContent.AudioPromptContent(
                        audio = persistentPrompt,
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
                            viewModelScope.launch {
                                transcribeAndPersist(sessionId, userMessage.id, initialContent)
                            }
                        }
                        initialContent
                    } else {
                        transcribeAndPersist(sessionId, userMessage.id, initialContent)
                            ?: return@launch fail("Audio transcription failed")
                    }
                maybeTitleConversation(sessionId, userMessage.id, effectiveContent)
                val projected =
                    if (activeModel.inputCapabilities.audio) {
                        conversationCoordinator.project(
                            history,
                            effectiveContent,
                            activeInference,
                            systemInstructionProvider(),
                        )
                    } else {
                        conversationCoordinator.project(
                            history,
                            MessageContent.TextPrompt(effectiveContent.transcript.orEmpty()),
                            activeInference,
                            systemInstructionProvider(),
                        )
                    }
                val loadStartedAt = System.nanoTime()
                engine.load(activeModel, activeInference)
                val loadMillis = loadStartedAt.elapsedMillis()
                val prepareStartedAt = System.nanoTime()
                val workload = if (activeModel.inputCapabilities.audio) LocalLlmWorkload.Audio else LocalLlmWorkload()
                engine.prepare(workload)
                Log.d(
                    LOG_TAG,
                    "Voice turn runtime check: load=$loadMillis ms, audioPrepare=${prepareStartedAt.elapsedMillis()} ms",
                )
                val answer = StringBuilder()
                conversationCoordinator.generate(
                    engine,
                    PromptRequest(
                        content = projected.requestContent,
                        chatMessages = projected.messages,
                        chatSessionId = sessionId,
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
                        is GenerationEvent.ReasoningToken, is GenerationEvent.Metrics -> Unit
                        is GenerationEvent.Failed -> fail(event.message)
                        GenerationEvent.Completed -> {
                            conversationCoordinator.appendAssistant(
                                sessionId,
                                MessageContent.TextPrompt(answer.toString()),
                            )
                            segmenter.complete(answer.toString()).forEach(speechQueue::enqueue)
                            speechQueue.markGenerationComplete()
                        }
                    }
                }
            } catch (error: Throwable) {
                if (activeRun == runId && error !is kotlinx.coroutines.CancellationException) fail(error.message ?: "Generation failed")
            }
        }
    }

    private fun persistAudio(captured: CapturedVoiceTurn) = runCatching {
        val destination = mediaRepository.createDraftFile(prefix = "voice_", suffix = ".wav")
        File(captured.prompt.uri).copyTo(destination, overwrite = true)
        captured.prompt.copy(uri = destination.absolutePath, byteSize = destination.length())
    }.getOrElse {
        captured.prompt
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
        val transcript = content.transcript?.takeIf(String::isNotBlank) ?: return
        val session = sessionStore.listSessions().firstOrNull { it.id == sessionId } ?: return
        val isFirstMessage = sessionStore.getMessages(sessionId).firstOrNull()?.id == messageId
        if (session.title == "New chat" && isFirstMessage) {
            sessionStore.renameSession(sessionId, transcript.take(42))
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
        modelLoadJob?.cancel()
        modelLoadJob = null
        mutableState.update { it.copy(isLoadingModel = false) }
        stop()
        clearDiagnostics()
    }

    fun stop() {
        runId++
        capture?.cancel()
        capture = null
        generationJob?.cancel()
        generationJob = null
        speechQueue.stop()
        deleteCurrentAudio()
        mutableState.update {
            it.copy(phase = VoiceChatPhase.Idle, responsePreview = "", spokenRange = null, readingAnchor = 0, error = null)
        }
    }

    fun fail(message: String) {
        capture?.cancel()
        capture = null
        generationJob?.cancel()
        generationJob = null
        speechQueue.stop()
        recordDiagnostic("failed")
        deleteCurrentAudio()
        mutableState.update { it.copy(phase = VoiceChatPhase.Error, error = message) }
    }

    fun dismissError() {
        mutableState.update { it.copy(phase = VoiceChatPhase.Idle, error = null) }
    }

    private fun deleteCurrentAudio() {
        currentAudio?.delete()
        currentAudio = null
    }

    public override fun onCleared() {
        stop()
        speechQueue.close()
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
