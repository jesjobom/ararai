package com.jesjobom.ararai.voice

import android.os.Looper
import com.jesjobom.ararai.chat.AudioPrompt
import com.jesjobom.ararai.chat.AudioTranscriber
import com.jesjobom.ararai.chat.AudioTranscriptionResult
import com.jesjobom.ararai.chat.ChatMediaRepository
import com.jesjobom.ararai.chat.ChatRole
import com.jesjobom.ararai.chat.ChatSessionStore
import com.jesjobom.ararai.chat.DeferredNewChatSessionStore
import com.jesjobom.ararai.chat.FileChatMediaRepository
import com.jesjobom.ararai.chat.ImageAttachment
import com.jesjobom.ararai.chat.InMemoryChatSessionStore
import com.jesjobom.ararai.chat.MessageContent
import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.LocalLlmWorkload
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelInputCapabilities
import com.jesjobom.ararai.model.ModelStartupState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceChatViewModelTest {
    private val model =
        LocalModel(
            id = "voice-test-model",
            name = "Voice Test Model",
            filePath = "/tmp/voice-test.task",
            inputCapabilities = ModelInputCapabilities(audio = true),
        )
    private val inference = InferenceConfig(contextTokens = 2_048, temperature = 0.2f, topP = 0.9f)

    @Test
    fun `entering voice chat loads model and prepares audio workload before enabling start`() {
        val engine = RecordingEngine(flowOf(GenerationEvent.Completed))
        val harness = harness(engine)

        harness.viewModel.onModelStartupState(ModelStartupState.Available(model, inference))
        harness.viewModel.onEnteringVoiceChat()

        waitUntil { harness.viewModel.state.value.canStart }
        assertEquals(listOf(model to inference), engine.loads)
        assertEquals(listOf(LocalLlmWorkload.Audio), engine.preparedWorkloads)
        assertFalse(harness.viewModel.state.value.isLoadingModel)
        assertTrue(harness.viewModel.state.value.isModelLoaded)
    }

    @Test
    fun `leaving voice chat invalidates readiness of the shared runtime profile`() {
        val harness = harness(RecordingEngine(flowOf(GenerationEvent.Completed)))

        harness.viewModel.onModelStartupState(ModelStartupState.Available(model, inference))
        harness.viewModel.onEnteringVoiceChat()
        waitUntil { harness.viewModel.state.value.canStart }

        harness.viewModel.onLeavingVoiceChat()

        assertFalse(harness.viewModel.state.value.isModelLoaded)
        assertFalse(harness.viewModel.state.value.canStart)
    }

    @Test
    fun `completed direct audio turn persists owned media and answer then resumes listening`() {
        val engine =
            RecordingEngine(
                flowOf(
                    GenerationEvent.Token("A local answer."),
                    GenerationEvent.Completed,
                ),
            )
        val harness = harness(engine)
        prepareAndStart(harness)
        val temporaryCapture = harness.captureFactory.latest!!.emitNewTurn()

        waitUntil {
            harness.viewModel.state.value.phase == VoiceChatPhase.Listening &&
                harness.store.allMessages().any { it.role == ChatRole.Assistant }
        }

        val messages = harness.store.allMessages()
        val userAudio = (messages.first { it.role == ChatRole.User }.content as MessageContent.AudioPromptContent).audio
        val assistant = messages.last { it.role == ChatRole.Assistant }.content as MessageContent.TextPrompt
        assertEquals("A local answer.", assistant.text)
        assertEquals(listOf("A local answer."), harness.speechFactory.segments)
        assertTrue(File(userAudio.uri).exists())
        assertEquals(harness.mediaDirectory.canonicalFile, File(userAudio.uri).canonicalFile.parentFile)
        assertFalse(temporaryCapture.exists())
        assertEquals(1, harness.viewModel.state.value.diagnostics.size)
        assertEquals("completed", harness.viewModel.state.value.diagnostics.single().outcome)
        harness.viewModel.stop()
    }

    @Test
    fun `direct audio without whisper promotes session with fallback title before creating another`() {
        val harness =
            harness(
                RecordingEngine(flowOf(GenerationEvent.Token("A local answer."), GenerationEvent.Completed)),
                deferredSessions = true,
            )
        prepareAndStart(harness)
        harness.captureFactory.latest!!.emitNewTurn()

        waitUntil { harness.store.allMessages().any { it.role == ChatRole.Assistant } }
        val firstSession = harness.store.listSessions().single()
        assertEquals("Voice chat · test", firstSession.title)
        assertEquals(
            "Voice chat · test",
            harness.viewModel.state.value.sessions
                .single { it.id == harness.viewModel.state.value.selectedSessionId }
                .title,
        )

        harness.viewModel.stop()
        harness.viewModel.createSession()

        waitUntil { harness.viewModel.state.value.selectedSessionId != firstSession.id }
        assertEquals(2, harness.store.listSessions().size)
        assertTrue(harness.store.getMessages(harness.viewModel.state.value.selectedSessionId!!).isEmpty())
        assertEquals(2, harness.store.getMessages(firstSession.id).size)
    }

    @Test
    fun `fallback title uses transcription state captured with the voice message`() {
        val changingAvailability = ChangingAvailabilityAudioTranscriber(availableFromCheck = 4)
        val harness =
            harness(
                RecordingEngine(flowOf(GenerationEvent.Token("A local answer."), GenerationEvent.Completed)),
                deferredSessions = true,
                audioTranscriber = changingAvailability,
            )
        prepareAndStart(harness)
        harness.captureFactory.latest!!.emitNewTurn()

        waitUntil { harness.store.allMessages().any { it.role == ChatRole.Assistant } }

        assertEquals("Voice chat · test", harness.store.listSessions().single().title)
        assertEquals(0, changingAvailability.transcribeCalls)
    }

    @Test
    fun `calculator lifecycle persists only final voice answer`() {
        val engine = RecordingEngine(
            flowOf(
                GenerationEvent.ToolStarted("calculator", "Local calculator"),
                GenerationEvent.ToolFinished("calculator"),
                GenerationEvent.Token("The result is 42."),
                GenerationEvent.Completed,
            ),
        )
        val harness = harness(engine)
        prepareAndStart(harness)
        harness.captureFactory.latest!!.emitNewTurn()

        waitUntil { harness.store.allMessages().any { it.role == ChatRole.Assistant } }

        val assistant = harness.store.allMessages().last().content as MessageContent.TextPrompt
        assertEquals("The result is 42.", assistant.text)
        assertTrue(assistant.sources.isEmpty())
        assertFalse(harness.viewModel.state.value.toolInProgress)
        harness.viewModel.stop()
    }

    @Test
    fun `generation failure enters controlled error state and removes temporary capture`() {
        val harness = harness(RecordingEngine(flowOf(GenerationEvent.Failed("generation failed"))))
        prepareAndStart(harness)
        val temporaryCapture = harness.captureFactory.latest!!.emitNewTurn()

        waitUntil { harness.viewModel.state.value.phase == VoiceChatPhase.Error }

        assertEquals("generation failed", harness.viewModel.state.value.error)
        assertFalse(temporaryCapture.exists())
        assertEquals("failed", harness.viewModel.state.value.diagnostics.single().outcome)
        assertTrue(harness.store.allMessages().any { it.role == ChatRole.User })
    }

    @Test
    fun `media copy failure stores no user message and never starts generation`() {
        val engine = RecordingEngine(flowOf(GenerationEvent.Completed))
        val harness = harness(engine, mediaRepository = FailingChatMediaRepository)
        prepareAndStart(harness)
        val temporaryCapture = harness.captureFactory.latest!!.emitNewTurn()

        waitUntil { harness.viewModel.state.value.phase == VoiceChatPhase.Error }

        assertTrue(harness.store.allMessages().isEmpty())
        assertEquals(0, engine.generateCalls)
        assertFalse(temporaryCapture.exists())
    }

    @Test
    fun `stopping active generation cancels engine and temporary capture then returns idle`() {
        val engine = CancellableEngine()
        val harness = harness(engine)
        prepareAndStart(harness)
        val temporaryCapture = harness.captureFactory.latest!!.emitNewTurn()
        waitUntil { engine.started.get() == 1 }

        harness.viewModel.stop()

        waitUntil { engine.cancellations.get() == 1 }
        assertEquals(VoiceChatPhase.Idle, harness.viewModel.state.value.phase)
        assertFalse(temporaryCapture.exists())
        assertFalse(harness.viewModel.state.value.toolInProgress)
    }

    @Test
    fun `stopping while listening cancels active capture and returns idle`() {
        val harness = harness(RecordingEngine(flowOf(GenerationEvent.Completed)))
        prepareAndStart(harness)

        harness.viewModel.stop()

        assertEquals(1, harness.captureFactory.latest!!.cancellations)
        assertEquals(VoiceChatPhase.Idle, harness.viewModel.state.value.phase)
    }

    @Test
    fun `camera interactions restart silence window without stopping capture`() {
        val harness = harness(RecordingEngine(flowOf(GenerationEvent.Completed)))
        val imageModel = model.copy(inputCapabilities = ModelInputCapabilities(text = true, image = true, audio = true))
        harness.viewModel.onModelStartupState(ModelStartupState.Available(imageModel, inference))
        harness.viewModel.onEnteringVoiceChat()
        waitUntil { harness.viewModel.state.value.canStart }
        harness.viewModel.start()
        waitUntil { harness.captureFactory.latest?.started == true }

        harness.viewModel.onCameraOpened()
        harness.viewModel.onCameraPreviewReady()
        harness.viewModel.onCameraClosed()

        assertEquals(3, harness.captureFactory.latest!!.silenceWindowResets)
        assertEquals(0, harness.captureFactory.latest!!.cancellations)
        assertEquals(VoiceChatPhase.Listening, harness.viewModel.state.value.phase)
    }

    @Test
    fun `valid pause with open camera requests automatic photo before generation`() {
        val engine = RecordingEngine(flowOf(GenerationEvent.Completed))
        val harness = harness(engine)
        val imageModel = model.copy(inputCapabilities = ModelInputCapabilities(text = true, image = true, audio = true))
        harness.viewModel.onModelStartupState(ModelStartupState.Available(imageModel, inference))
        harness.viewModel.onEnteringVoiceChat()
        waitUntil { harness.viewModel.state.value.canStart }
        harness.viewModel.start()
        waitUntil { harness.captureFactory.latest?.started == true }
        harness.viewModel.onCameraOpened()

        harness.captureFactory.latest!!.emitNewTurn()
        waitUntil { harness.viewModel.state.value.automaticPhotoCaptureRequestId != null }

        assertTrue(harness.store.allMessages().isEmpty())
        assertEquals(0, engine.generateCalls)
        val image = File(harness.mediaDirectory, "automatic.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(7, 8, 9))
        }
        harness.viewModel.useCapturedImage(ImageAttachment(image.absolutePath, "image/jpeg"))
        waitUntil { harness.store.allMessages().any { it.role == ChatRole.Assistant } }

        val content =
            harness.store.allMessages().first { it.role == ChatRole.User }.content
                as MessageContent.AudioPromptContent
        assertEquals(image.absolutePath, content.imageAttachments.single().uri)
        assertFalse(harness.viewModel.state.value.cameraFlowActive)
        assertEquals(null, harness.viewModel.state.value.automaticPhotoCaptureRequestId)
    }

    @Test
    fun `closing camera after automatic request submits completed audio without image`() {
        val engine = RecordingEngine(flowOf(GenerationEvent.Completed))
        val harness = harness(engine)
        val imageModel = model.copy(inputCapabilities = ModelInputCapabilities(text = true, image = true, audio = true))
        harness.viewModel.onModelStartupState(ModelStartupState.Available(imageModel, inference))
        harness.viewModel.onEnteringVoiceChat()
        waitUntil { harness.viewModel.state.value.canStart }
        harness.viewModel.start()
        waitUntil { harness.captureFactory.latest?.started == true }
        harness.viewModel.onCameraOpened()
        harness.captureFactory.latest!!.emitNewTurn()
        waitUntil { harness.viewModel.state.value.automaticPhotoCaptureRequestId != null }

        harness.viewModel.onCameraClosed()
        waitUntil { harness.store.allMessages().any { it.role == ChatRole.Assistant } }

        val content =
            harness.store.allMessages().first { it.role == ChatRole.User }.content
                as MessageContent.AudioPromptContent
        assertTrue(content.imageAttachments.isEmpty())
        assertEquals(1, engine.generateCalls)
    }

    @Test
    fun `captured image is persisted with the next direct audio turn`() {
        val engine = RecordingEngine(flowOf(GenerationEvent.Completed))
        val harness = harness(engine)
        val imageModel = model.copy(inputCapabilities = ModelInputCapabilities(text = true, image = true, audio = true))
        harness.viewModel.onModelStartupState(ModelStartupState.Available(imageModel, inference))
        harness.viewModel.onEnteringVoiceChat()
        waitUntil { harness.viewModel.state.value.canStart }
        harness.viewModel.start()
        waitUntil { harness.captureFactory.latest?.started == true }
        harness.viewModel.onCameraOpened()
        val image = File(harness.mediaDirectory, "captured.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(4, 5, 6))
        }
        harness.viewModel.useCapturedImage(ImageAttachment(image.absolutePath, "image/jpeg"))

        assertFalse(harness.viewModel.state.value.cameraFlowActive)
        assertEquals(2, harness.captureFactory.latest!!.silenceWindowResets)

        harness.captureFactory.latest!!.emitNewTurn()
        waitUntil { harness.store.allMessages().any { it.role == ChatRole.Assistant } }

        val content =
            harness.store
                .allMessages()
                .first { it.role == ChatRole.User }
                .content as MessageContent.AudioPromptContent
        assertEquals(image.absolutePath, content.imageAttachments.single().uri)
        assertEquals(null, harness.viewModel.state.value.automaticPhotoCaptureRequestId)
        assertEquals(LocalLlmWorkload(image = true, audio = true), engine.preparedWorkloads.last())
    }

    private fun prepareAndStart(harness: Harness) {
        harness.viewModel.onModelStartupState(ModelStartupState.Available(model, inference))
        harness.viewModel.onEnteringVoiceChat()
        waitUntil { harness.viewModel.state.value.canStart }
        harness.viewModel.start()
        waitUntil { harness.captureFactory.latest?.started == true }
    }

    private fun harness(
        engine: LocalLlmEngine,
        mediaRepository: ChatMediaRepository? = null,
        deferredSessions: Boolean = false,
        audioTranscriber: AudioTranscriber = com.jesjobom.ararai.chat.UnavailableAudioTranscriber,
    ): Harness {
        val root = createTempDirectory("voice-view-model-test").toFile()
        val mediaDirectory = File(root, "media")
        val captureDirectory = File(root, "capture").apply { mkdirs() }
        val persistedStore = InMemoryChatSessionStore()
        val store: ChatSessionStore =
            if (deferredSessions) DeferredNewChatSessionStore(persistedStore) else persistedStore
        val captureFactory = RecordingCaptureFactory(captureDirectory)
        val speechFactory = CompletingSpeechQueueFactory()
        val viewModel =
            VoiceChatViewModel(
                engine = engine,
                systemPrompt = "Test",
                preferences = InMemoryVoiceChatPreferences(VoiceChatSettings(minimumWords = 1)),
                captureFactory = captureFactory::create,
                speechQueueFactory = speechFactory::create,
                sessionStore = store,
                mediaRepository = mediaRepository ?: FileChatMediaRepository(mediaDirectory),
                audioTranscriber = audioTranscriber,
                voiceSessionTitleProvider = { "Voice chat · test" },
            )
        return Harness(viewModel, store, captureFactory, speechFactory, mediaDirectory)
    }

    private fun waitUntil(
        timeoutMillis: Long = 5_000L,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10L)
        }
        assertTrue("Condition was not met within ${timeoutMillis}ms", condition())
    }

    private data class Harness(
        val viewModel: VoiceChatViewModel,
        val store: ChatSessionStore,
        val captureFactory: RecordingCaptureFactory,
        val speechFactory: CompletingSpeechQueueFactory,
        val mediaDirectory: File,
    )
}

private class ChangingAvailabilityAudioTranscriber(
    private val availableFromCheck: Int,
) : AudioTranscriber {
    private var availabilityChecks = 0
    var transcribeCalls = 0
        private set

    override val isAvailable: Boolean
        get() = ++availabilityChecks >= availableFromCheck

    override suspend fun transcribe(audio: AudioPrompt): AudioTranscriptionResult {
        transcribeCalls++
        return AudioTranscriptionResult("unexpected", "test")
    }
}

private class RecordingEngine(
    private val events: Flow<GenerationEvent>,
) : LocalLlmEngine {
    val loads = mutableListOf<Pair<LocalModel, InferenceConfig>>()
    val preparedWorkloads = mutableListOf<LocalLlmWorkload>()
    var generateCalls = 0

    override suspend fun load(model: LocalModel, config: InferenceConfig) {
        loads += model to config
    }

    override suspend fun prepare(workload: LocalLlmWorkload) {
        preparedWorkloads += workload
    }

    override fun generate(request: PromptRequest): Flow<GenerationEvent> {
        generateCalls++
        return events
    }

    override suspend fun unload() = Unit
}

private class CancellableEngine : LocalLlmEngine {
    val started = AtomicInteger()
    val cancellations = AtomicInteger()

    override suspend fun load(model: LocalModel, config: InferenceConfig) = Unit

    override fun generate(request: PromptRequest): Flow<GenerationEvent> = flow {
        started.incrementAndGet()
        try {
            awaitCancellation()
        } catch (error: CancellationException) {
            cancellations.incrementAndGet()
            throw error
        }
    }

    override suspend fun unload() = Unit
}

private class RecordingCaptureFactory(
    private val directory: File,
) {
    val requestedSettings = mutableListOf<VoiceChatSettings>()
    var latest: RecordingCapture? = null
        private set

    fun create(settings: VoiceChatSettings): VoiceTurnCapture {
        requestedSettings += settings
        return RecordingCapture(directory).also { latest = it }
    }
}

private class RecordingCapture(
    private val directory: File,
) : VoiceTurnCapture {
    var started = false
        private set
    var cancellations = 0
        private set
    var silenceWindowResets = 0
    private var onTurn: ((CapturedVoiceTurn) -> Unit)? = null

    override fun start(onTurn: (CapturedVoiceTurn) -> Unit, onError: (String) -> Unit) {
        started = true
        this.onTurn = onTurn
    }

    fun emitNewTurn(): File {
        val file = File.createTempFile("voice-capture-", ".wav", directory).apply { writeBytes(byteArrayOf(1, 2, 3)) }
        checkNotNull(onTurn).invoke(
            CapturedVoiceTurn(
                prompt = AudioPrompt(file.absolutePath, "audio/wav", "voice.wav", file.length()),
                speechMillis = 1_000L,
                noiseSuppressionActive = true,
            ),
        )
        return file
    }

    override fun resetSilenceWindow() {
        silenceWindowResets++
    }

    override fun cancel() {
        cancellations++
    }

    override fun close() = Unit
}

private class CompletingSpeechQueueFactory {
    val segments = mutableListOf<String>()

    fun create(
        onStarted: (IntRange) -> Unit,
        onRange: (IntRange) -> Unit,
        onComplete: () -> Unit,
        @Suppress("UNUSED_PARAMETER") onError: (com.jesjobom.ararai.ui.UserMessageKey) -> Unit,
    ): VoiceSpeechQueue = object : VoiceSpeechQueue {
        override fun enqueue(segment: VoiceSpeechSegment) {
            segments += segment.speechText
            onStarted(segment.sourceRange)
            onRange(segment.sourceRange)
        }

        override fun markGenerationComplete() = onComplete()

        override fun stop() = Unit

        override fun close() = Unit
    }
}

private object FailingChatMediaRepository : ChatMediaRepository {
    override fun createDraftFile(prefix: String, suffix: String): File = error("media unavailable")

    override fun deleteDraft(uri: String, persistedUris: Set<String>) = Unit

    override fun deleteUnreferenced(candidateUris: Set<String>, persistedUris: Set<String>) = Unit

    override fun reconcile(persistedUris: Set<String>, maxFiles: Int) = Unit
}

private fun ChatSessionStore.allMessages() = listSessions().flatMap { getMessages(it.id) }
