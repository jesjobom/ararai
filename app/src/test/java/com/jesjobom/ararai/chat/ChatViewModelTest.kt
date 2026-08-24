package com.jesjobom.ararai.chat

import app.cash.turbine.test
import com.jesjobom.ararai.engine.FakeLocalLlmEngine
import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.GenerationFailureKind
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.PromptChatMessage
import com.jesjobom.ararai.engine.PromptChatRole
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.knowledge.KnowledgeSource
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelInputCapabilities
import com.jesjobom.ararai.model.ModelReasoningCapabilities
import com.jesjobom.ararai.model.ModelStartupState
import com.jesjobom.ararai.ui.UserMessageKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.io.path.createTempDirectory

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val model =
        LocalModel(
            id = "test-model",
            name = "Test Model",
            filePath = "/tmp/test.gguf",
        )

    private val inferenceConfig =
        InferenceConfig(
            contextTokens = 128,
            temperature = 0.7f,
            topP = 0.9f,
        )

    @Test
    fun `keeps submit disabled until model is available`() {
        val viewModel =
            ChatViewModel(
                engine = FakeLocalLlmEngine(chunks = listOf("ignored")),
                initialModel = null,
                inferenceConfig = inferenceConfig,
            )

        viewModel.onPromptChanged("hello")

        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `enables submit after startup state provides available model`() {
        val viewModel =
            ChatViewModel(
                engine = FakeLocalLlmEngine(chunks = listOf("ignored")),
                initialModel = null,
                inferenceConfig = inferenceConfig,
            )

        viewModel.onModelStartupState(ModelStartupState.Available(model, inferenceConfig))

        assertEquals(UserMessageKey.ModelAvailable, viewModel.uiState.value.modelStatusKey)
        viewModel.onPromptChanged("hello")

        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `keeps submit disabled for blank prompt even when model is available`() {
        val viewModel =
            ChatViewModel(
                engine = FakeLocalLlmEngine(chunks = listOf("ignored")),
                initialModel = model,
                inferenceConfig = inferenceConfig,
            )

        viewModel.onPromptChanged("   ")

        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `enables submit for image only draft when model supports images`() {
        val viewModel =
            ChatViewModel(
                engine = FakeLocalLlmEngine(chunks = listOf("ignored")),
                initialModel = model.copy(inputCapabilities = ModelInputCapabilities(image = true)),
                inferenceConfig = inferenceConfig,
            )

        viewModel.attachImage(ImageAttachment("/tmp/image.jpg", "image/jpeg", "image.jpg"))

        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `exposes retry when startup download fails`() {
        val viewModel =
            ChatViewModel(
                engine = FakeLocalLlmEngine(chunks = listOf("ignored")),
                initialModel = null,
                inferenceConfig = inferenceConfig,
            )

        viewModel.onModelStartupState(ModelStartupState.Failed("network down"))

        assertTrue(viewModel.uiState.value.canRetryModelDownload)
        assertEquals("Download failed: network down", viewModel.uiState.value.modelStatus)
    }

    @Test
    fun `formats model download progress`() {
        val viewModel =
            ChatViewModel(
                engine = FakeLocalLlmEngine(chunks = listOf("ignored")),
                initialModel = null,
                inferenceConfig = inferenceConfig,
            )

        viewModel.onModelStartupState(
            ModelStartupState.Downloading(bytesDownloaded = 50, totalBytes = 100),
        )

        assertEquals("Downloading configured model: 50%", viewModel.uiState.value.modelStatus)
        assertFalse(viewModel.uiState.value.canRetryModelDownload)
    }

    @Test
    fun `streams fake engine chunks into assistant message`() = runTest {
        val viewModel =
            ChatViewModel(
                engine = FakeLocalLlmEngine(chunks = listOf("ola", " mundo")),
                initialModel = model,
                inferenceConfig = inferenceConfig,
            )

        viewModel.uiState.test {
            assertTrue(awaitItem().canSubmit.not())
            viewModel.onPromptChanged("oi")
            assertTrue(awaitItem().canSubmit)
            viewModel.submitPrompt()

            var loading = awaitItem()
            while (!loading.isGenerating) loading = awaitItem()
            assertTrue(loading.isGenerating)
            assertFalse(loading.canSubmit)

            var loaded = awaitItem()
            while (loaded.isLoadingModel) loaded = awaitItem()
            assertFalse(loaded.isLoadingModel)
            assertTrue(loaded.isGenerating)

            var completed = awaitItem()
            while (completed.isGenerating) {
                completed = awaitItem()
            }
            assertFalse(completed.isGenerating)
            assertEquals("", completed.prompt)
            assertEquals("ola mundo", completed.messages.last().text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `surfaces generation failure and preserves messages`() = runTest {
        val viewModel =
            ChatViewModel(
                engine = FailingEngine("generation failed"),
                initialModel = model,
                inferenceConfig = inferenceConfig,
                scope = this,
            )

        viewModel.onPromptChanged("oi")
        viewModel.submitPrompt()
        runCurrent()

        val failed = viewModel.uiState.value
        assertFalse(failed.isGenerating)
        assertEquals(null, failed.error)
        assertEquals(UserMessageKey.GenerationFailed, failed.errorKey)
        assertEquals(2, failed.messages.size)
        assertEquals("oi", failed.messages.first().text)
        assertEquals("oi", failed.prompt)
    }

    @Test
    fun `surfaces load failure and preserves submitted message`() = runTest {
        val engine = LoadFailingEngine("load failed")
        val viewModel =
            ChatViewModel(
                engine = engine,
                initialModel = model,
                inferenceConfig = inferenceConfig,
            )

        viewModel.uiState.test {
            awaitItem()
            viewModel.onPromptChanged("oi")
            awaitItem()
            viewModel.submitPrompt()

            engine.loadStarted.await()
            val loading = viewModel.uiState.value
            assertTrue(loading.isGenerating)
            assertTrue(loading.isLoadingModel)
            assertFalse(loading.canSubmit)

            engine.failLoad()
            var failed = awaitItem()
            while (failed.error == null) failed = awaitItem()
            assertFalse(failed.isLoadingModel)
            assertFalse(failed.isGenerating)
            assertEquals("load failed", failed.error)
            assertEquals("oi", failed.messages.first().text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `offers unexpected generation throwable with bounded runtime context`() = runTest {
        val engine = LoadFailingEngine("Failed to parse tool calls from code block: private output")
        var captured: Pair<Throwable, com.jesjobom.ararai.reporting.DiagnosticOperationContext>? = null
        val viewModel =
            ChatViewModel(
                engine = engine,
                initialModel = model,
                inferenceConfig = inferenceConfig.copy(contextTokens = 6144),
                conversationTurnSettingsProvider = {
                    ConversationTurnSettings("system", setOf(WEB_SEARCH_TOOL_NAME))
                },
                unexpectedErrorConsumer = { error, context -> captured = error to context },
                scope = this,
            )

        viewModel.onPromptChanged("private question")
        viewModel.submitPrompt()
        engine.loadStarted.await()
        engine.failLoad()
        runCurrent()

        assertEquals("Failed to parse tool calls from code block: private output", captured?.first?.message)
        assertEquals("chat_generation", captured?.second?.stage)
        assertEquals("test-model", captured?.second?.modelId)
        assertEquals(6144, captured?.second?.contextTokens)
        assertEquals(setOf(WEB_SEARCH_TOOL_NAME), captured?.second?.enabledToolNames)
    }

    @Test
    fun `offers failed generation event and hides its technical message from chat`() = runTest {
        val technicalMessage = "Failed to parse tool calls from code block: private output"
        var captured: Pair<GenerationEvent.Failed, com.jesjobom.ararai.reporting.DiagnosticOperationContext>? = null
        val viewModel =
            ChatViewModel(
                engine = FailingEngine(technicalMessage, GenerationFailureKind.ToolCallParsing),
                initialModel = model,
                inferenceConfig = inferenceConfig.copy(contextTokens = 6144),
                conversationTurnSettingsProvider = {
                    ConversationTurnSettings("system", setOf(WEB_SEARCH_TOOL_NAME))
                },
                generationFailureConsumer = { failure, context -> captured = failure to context },
                scope = this,
            )

        viewModel.onPromptChanged("private question")
        viewModel.submitPrompt()
        runCurrent()

        assertEquals(technicalMessage, captured?.first?.message)
        assertEquals("chat_generation", captured?.second?.stage)
        assertEquals(6144, captured?.second?.contextTokens)
        assertEquals(setOf(WEB_SEARCH_TOOL_NAME), captured?.second?.enabledToolNames)
        assertEquals(null, viewModel.uiState.value.error)
        assertEquals(UserMessageKey.GenerationFailed, viewModel.uiState.value.errorKey)
    }

    @Test
    fun `blocks second submit while first generation is active`() = runTest {
        val engine = SlowEngine()
        val viewModel =
            ChatViewModel(
                engine = engine,
                initialModel = model,
                inferenceConfig = inferenceConfig,
                scope = this,
            )

        viewModel.onPromptChanged("first")
        viewModel.submitPrompt()
        viewModel.onPromptChanged("second")
        viewModel.submitPrompt()
        runCurrent()

        assertEquals(1, engine.generateCalls)
        assertEquals(2, viewModel.uiState.value.messages.size)
        assertFalse(viewModel.uiState.value.canSubmit)

        viewModel.onLeavingChat()
        runCurrent()
    }

    @Test
    fun `leaving chat cancels active generation and keeps engine loaded`() = runTest {
        val engine = SlowEngine()
        val viewModel =
            ChatViewModel(
                engine = engine,
                initialModel = model,
                inferenceConfig = inferenceConfig,
                scope = this,
            )

        viewModel.onPromptChanged("hello")
        viewModel.submitPrompt()
        runCurrent()

        viewModel.onLeavingChat()
        runCurrent()

        assertEquals(0, engine.unloadCalls)
        assertFalse(viewModel.uiState.value.isGenerating)
        assertFalse(viewModel.uiState.value.isLoadingModel)
    }

    @Test
    fun `cancel generation stops active job and unloads engine`() = runTest {
        val engine = SlowEngine()
        val viewModel =
            ChatViewModel(
                engine = engine,
                initialModel = model,
                inferenceConfig = inferenceConfig,
                scope = this,
            )

        viewModel.onPromptChanged("hello")
        viewModel.submitPrompt()
        runCurrent()

        viewModel.cancelGeneration()
        runCurrent()

        assertTrue(engine.unloadCalls > 0)
        assertFalse(viewModel.uiState.value.isGenerating)
        assertFalse(viewModel.uiState.value.isLoadingModel)
        assertEquals(
            "partial",
            viewModel.uiState.value.messages
                .last()
                .text,
        )
        assertEquals("hello", viewModel.uiState.value.prompt)
    }

    @Test
    fun `manages persistent chat sessions`() {
        val store = InMemoryChatSessionStore()
        val viewModel =
            ChatViewModel(
                engine = FakeLocalLlmEngine(chunks = listOf("ignored")),
                initialModel = model,
                inferenceConfig = inferenceConfig,
                sessionStore = store,
            )

        val firstSession = viewModel.uiState.value.selectedSessionId
        viewModel.createSession()
        val secondSession = viewModel.uiState.value.selectedSessionId

        assertEquals(2, viewModel.uiState.value.sessions.size)
        assertTrue(firstSession != secondSession)

        viewModel.renameCurrentSession("Work notes")
        assertEquals(
            "Work notes",
            viewModel.uiState.value.sessions
                .first { it.id == secondSession }
                .title,
        )

        viewModel.renameSession(firstSession!!, "Earlier chat")
        assertEquals(
            "Earlier chat",
            viewModel.uiState.value.sessions
                .first { it.id == firstSession }
                .title,
        )
        assertEquals(secondSession, viewModel.uiState.value.selectedSessionId)

        viewModel.selectSession(firstSession)
        assertEquals(firstSession, viewModel.uiState.value.selectedSessionId)

        viewModel.selectSession(secondSession!!)
        viewModel.deleteCurrentSession()

        assertEquals(1, viewModel.uiState.value.sessions.size)
        assertEquals(firstSession, viewModel.uiState.value.selectedSessionId)
    }

    @Test
    fun `loads long conversation history in bounded recent pages`() {
        val store = InMemoryChatSessionStore()
        val session = store.ensureSession()
        repeat(250) { index -> store.appendMessage(session.id, ChatRole.User, "message-$index") }
        val viewModel =
            ChatViewModel(
                engine = FakeLocalLlmEngine(chunks = emptyList()),
                initialModel = model,
                inferenceConfig = inferenceConfig,
                sessionStore = store,
            )

        assertEquals(100, viewModel.uiState.value.messages.size)
        assertEquals("message-150", viewModel.uiState.value.messages.first().text)
        assertTrue(viewModel.uiState.value.hasOlderMessages)

        viewModel.loadOlderMessages()
        assertEquals(200, viewModel.uiState.value.messages.size)
        assertEquals("message-50", viewModel.uiState.value.messages.first().text)
        assertTrue(viewModel.uiState.value.hasOlderMessages)

        viewModel.loadOlderMessages()
        assertEquals(250, viewModel.uiState.value.messages.size)
        assertEquals("message-0", viewModel.uiState.value.messages.first().text)
        assertFalse(viewModel.uiState.value.hasOlderMessages)
    }

    @Test
    fun `interactive persistence leaves the calling thread`() {
        val store = ThreadRecordingChatSessionStore()
        val callerThread = Thread.currentThread().name
        val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "chat-persistence-test") }
        val dispatcher = executor.asCoroutineDispatcher()
        try {
            val viewModel =
                ChatViewModel(
                    engine = FakeLocalLlmEngine(chunks = listOf("answer")),
                    initialModel = model,
                    inferenceConfig = inferenceConfig,
                    sessionStore = store,
                    persistenceDispatcher = dispatcher,
                )
            store.threadNames.clear()

            viewModel.onPromptChanged("hello")
            viewModel.submitPrompt()
            waitUntil {
                !viewModel.uiState.value.isGenerating &&
                    viewModel.uiState.value.messages.any { it.role == ChatRole.Assistant && it.text == "answer" }
            }

            assertTrue(store.threadNames.none { it == callerThread })
            assertTrue(store.threadNames.toString(), store.threadNames.any { it.contains("chat-persistence-test") })

            val previousSession = viewModel.uiState.value.selectedSessionId
            store.threadNames.clear()
            viewModel.createSession()
            waitUntil { viewModel.uiState.value.selectedSessionId != previousSession }
            assertTrue(store.threadNames.none { it == callerThread })
            assertTrue(store.threadNames.toString(), store.threadNames.any { it.contains("chat-persistence-test") })
        } finally {
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun `clears every session and creates a new empty session`() {
        val store = InMemoryChatSessionStore()
        val first = store.ensureSession()
        store.appendMessage(first.id, ChatRole.User, "first message")
        val viewModel =
            ChatViewModel(
                engine = FakeLocalLlmEngine(chunks = listOf("ignored")),
                initialModel = model,
                inferenceConfig = inferenceConfig,
                sessionStore = store,
            )
        viewModel.createSession()
        val second = viewModel.uiState.value.selectedSessionId!!
        store.appendMessage(second, ChatRole.User, "second message")

        viewModel.clearAllSessions()

        val state = viewModel.uiState.value
        assertEquals(1, state.sessions.size)
        assertTrue(state.selectedSessionId != first.id)
        assertTrue(state.selectedSessionId != second)
        assertTrue(state.messages.isEmpty())
        assertTrue(store.getMessages(state.selectedSessionId!!).isEmpty())
    }

    @Test
    fun `removing a draft attachment deletes its app owned file`() {
        val mediaRepository = FileChatMediaRepository(createTempDirectory("chat-media-").toFile())
        val image = mediaRepository.createDraftFile("image-", ".jpg").apply { writeText("draft") }
        val viewModel =
            ChatViewModel(
                engine = FakeLocalLlmEngine(chunks = emptyList()),
                initialModel = model.copy(inputCapabilities = ModelInputCapabilities(image = true)),
                inferenceConfig = inferenceConfig,
                mediaRepository = mediaRepository,
            )

        viewModel.attachImage(ImageAttachment(image.absolutePath, "image/jpeg"))
        viewModel.removeImage(image.absolutePath)

        assertFalse(image.exists())
    }

    @Test
    fun `deleting a session removes only media without remaining references`() {
        val store = InMemoryChatSessionStore()
        val first = store.ensureSession()
        val mediaRepository = FileChatMediaRepository(createTempDirectory("chat-media-").toFile())
        val exclusive = mediaRepository.createDraftFile("image-", ".jpg").apply { writeText("exclusive") }
        val shared = mediaRepository.createDraftFile("image-", ".jpg").apply { writeText("shared") }
        store.appendMessage(
            first.id,
            ChatRole.User,
            MessageContent.TextPrompt(
                "first",
                listOf(
                    ImageAttachment(exclusive.absolutePath, "image/jpeg"),
                    ImageAttachment(shared.absolutePath, "image/jpeg"),
                ),
            ),
        )
        val second = store.createSession("Second")
        store.appendMessage(
            second.id,
            ChatRole.User,
            MessageContent.TextPrompt(
                "second",
                listOf(
                    ImageAttachment(shared.absolutePath, "image/jpeg"),
                ),
            ),
        )
        val viewModel =
            ChatViewModel(
                engine = FakeLocalLlmEngine(chunks = emptyList()),
                initialModel = model,
                inferenceConfig = inferenceConfig,
                sessionStore = store,
                mediaRepository = mediaRepository,
            )

        viewModel.deleteSession(first.id)

        assertFalse(exclusive.exists())
        assertTrue(shared.exists())
    }

    @Test
    fun `clearing sessions removes all persisted media`() {
        val store = InMemoryChatSessionStore()
        val session = store.ensureSession()
        val mediaRepository = FileChatMediaRepository(createTempDirectory("chat-media-").toFile())
        val audio = mediaRepository.createDraftFile("recording-", ".wav").apply { writeText("audio") }
        store.appendMessage(
            session.id,
            ChatRole.User,
            MessageContent.AudioPromptContent(AudioPrompt(audio.absolutePath, "audio/wav")),
        )
        val viewModel =
            ChatViewModel(
                engine = FakeLocalLlmEngine(chunks = emptyList()),
                initialModel = model,
                inferenceConfig = inferenceConfig,
                sessionStore = store,
                mediaRepository = mediaRepository,
            )

        viewModel.clearAllSessions()

        assertFalse(audio.exists())
    }

    @Test
    fun `sends system prompt and recent session history to engine`() = runTest {
        val store = InMemoryChatSessionStore()
        val session = store.ensureSession()
        store.appendMessage(session.id, ChatRole.User, "Earlier question")
        store.appendMessage(session.id, ChatRole.Assistant, "Earlier answer")
        val engine = CapturingEngine()
        val viewModel =
            ChatViewModel(
                engine = engine,
                initialModel = model,
                inferenceConfig = inferenceConfig,
                systemPrompt = "Be useful.",
                sessionStore = store,
                scope = this,
            )

        viewModel.onPromptChanged("Current question")
        viewModel.submitPrompt()
        runCurrent()

        assertEquals(
            listOf(
                PromptChatMessage(PromptChatRole.System, "Be useful."),
                PromptChatMessage(PromptChatRole.User, "Earlier question"),
                PromptChatMessage(PromptChatRole.Assistant, "Earlier answer"),
                PromptChatMessage(PromptChatRole.User, "Current question"),
            ),
            engine.lastRequest!!.chatMessages,
        )
        assertEquals(session.id, engine.lastRequest!!.chatSessionId)
        assertEquals(
            "Current question",
            store
                .getMessages(session.id)
                .filter { it.role == ChatRole.User }
                .last()
                .text,
        )
    }

    @Test
    fun `captures instruction and advertised skills from one turn snapshot`() = runTest {
        val engine = CapturingEngine()
        var snapshots = 0
        val viewModel =
            ChatViewModel(
                engine = engine,
                initialModel = model,
                inferenceConfig = inferenceConfig,
                conversationTurnSettingsProvider = {
                    snapshots += 1
                    ConversationTurnSettings(
                        systemInstruction = "Turn-specific instruction.",
                        advertisedToolNames = setOf("wikipedia_search", "calendar_lookup"),
                    )
                },
                scope = this,
            )

        viewModel.onPromptChanged("Current question")
        viewModel.submitPrompt()
        runCurrent()

        val request = engine.lastRequest!!
        assertEquals(1, snapshots)
        assertEquals(
            PromptChatMessage(PromptChatRole.System, "Turn-specific instruction."),
            request.chatMessages.first(),
        )
        assertEquals(setOf("wikipedia_search", "calendar_lookup"), request.advertisedToolNames)
    }

    @Test
    fun `submits text prompt with image attachment`() = runTest {
        val engine = CapturingEngine()
        val viewModel =
            ChatViewModel(
                engine = engine,
                initialModel = model.copy(inputCapabilities = ModelInputCapabilities(image = true)),
                inferenceConfig = inferenceConfig,
                scope = this,
            )

        viewModel.onPromptChanged("Describe this")
        viewModel.attachImage(ImageAttachment("file:///tmp/image.png", "image/png", "image.png"))
        viewModel.submitPrompt()
        runCurrent()

        val content = engine.lastRequest!!.content as MessageContent.TextPrompt
        assertTrue(content.text.contains("User: Describe this"))
        assertEquals(listOf(ImageAttachment("file:///tmp/image.png", "image/png", "image.png")), content.imageAttachments)
        assertEquals(
            1,
            viewModel.uiState.value.messages
                .first()
                .content
                .let { it as MessageContent.TextPrompt }
                .imageAttachments.size,
        )
    }

    @Test
    fun `image only first turn uses default description prompt as session title`() = runTest {
        val viewModel =
            ChatViewModel(
                engine = CapturingEngine(),
                initialModel = model.copy(inputCapabilities = ModelInputCapabilities(image = true)),
                inferenceConfig = inferenceConfig,
                scope = this,
            )

        viewModel.attachImage(ImageAttachment("file:///tmp/image.png", "image/png"))
        viewModel.submitPrompt()
        runCurrent()

        assertEquals("Describe this image.", viewModel.uiState.value.sessions.first().title)
    }

    @Test
    fun `audio prompt excludes typed text but retains current images`() = runTest {
        val engine = CapturingEngine()
        val viewModel =
            ChatViewModel(
                engine = engine,
                initialModel = model.copy(inputCapabilities = ModelInputCapabilities(image = true, audio = true)),
                inferenceConfig = inferenceConfig,
                scope = this,
            )

        viewModel.onPromptChanged("typed text")
        viewModel.attachImage(ImageAttachment("file:///tmp/image.png", "image/png"))
        viewModel.useAudioPrompt(AudioPrompt("file:///tmp/audio.wav", "audio/wav", "audio.wav"))

        assertEquals("", viewModel.uiState.value.prompt)
        assertEquals(
            listOf(ImageAttachment("file:///tmp/image.png", "image/png")),
            viewModel.uiState.value.imageAttachments,
        )
        assertTrue(viewModel.uiState.value.canSubmit)

        viewModel.onPromptChanged("should be ignored")
        viewModel.submitPrompt()
        runCurrent()

        val submitted = engine.lastRequest!!.content as MessageContent.AudioPromptContent
        assertEquals("file:///tmp/image.png", submitted.imageAttachments.single().uri)
        assertEquals("", viewModel.uiState.value.prompt)
    }

    @Test
    fun `submits recorded audio prompt directly`() = runTest {
        val engine = CapturingEngine()
        val viewModel =
            ChatViewModel(
                engine = engine,
                initialModel = model.copy(inputCapabilities = ModelInputCapabilities(audio = true)),
                inferenceConfig = inferenceConfig,
                scope = this,
            )
        val audio = AudioPrompt("/tmp/recording.wav", "audio/wav", "recording.wav", durationMillis = 1_000)

        viewModel.submitAudioPrompt(audio)
        runCurrent()

        assertTrue(engine.lastRequest!!.content is MessageContent.AudioPromptContent)
        assertEquals(audio, (engine.lastRequest!!.content as MessageContent.AudioPromptContent).audio)
        assertEquals(
            audio,
            (
                viewModel.uiState.value.messages
                    .first()
                    .content as MessageContent.AudioPromptContent
                ).audio,
        )
    }

    @Test
    fun `audio prompt includes system prompt and recent textual history`() = runTest {
        val store = InMemoryChatSessionStore()
        val session = store.ensureSession()
        store.appendMessage(session.id, ChatRole.User, "Earlier question")
        store.appendMessage(session.id, ChatRole.Assistant, "Earlier answer")
        store.appendMessage(
            session.id,
            ChatRole.User,
            MessageContent.TextPrompt(
                text = "Image question",
                imageAttachments = listOf(ImageAttachment("/tmp/old.png", "image/png")),
            ),
        )
        val engine = CapturingEngine()
        val viewModel =
            ChatViewModel(
                engine = engine,
                initialModel = model.copy(inputCapabilities = ModelInputCapabilities(audio = true)),
                inferenceConfig = inferenceConfig,
                systemPrompt = "Be useful.",
                sessionStore = store,
                scope = this,
            )
        val audio = AudioPrompt("/tmp/current.wav", "audio/wav")

        viewModel.submitAudioPrompt(audio)
        runCurrent()

        val request = engine.lastRequest!!
        assertEquals(audio, request.audioPrompt)
        assertTrue(request.chatMessages.any { it.role == PromptChatRole.System && it.text == "Be useful." })
        assertTrue(request.chatMessages.any { it.role == PromptChatRole.User && it.text == "Earlier question" })
        assertTrue(request.chatMessages.any { it.role == PromptChatRole.Assistant && it.text == "Earlier answer" })
        assertTrue(request.chatMessages.any { it.role == PromptChatRole.User && it.text == "Image question" })
        assertEquals(listOf(audio), listOfNotNull(request.audioPrompt))
        assertTrue(request.imageAttachments.isEmpty())
    }

    @Test
    fun `transcribes before sending audio to text only model`() = runTest {
        val engine = CapturingEngine()
        val store = InMemoryChatSessionStore()
        val viewModel =
            ChatViewModel(
                engine = engine,
                initialModel = model,
                inferenceConfig = inferenceConfig,
                sessionStore = store,
                audioTranscriber = FakeAudioTranscriber("spoken question"),
                scope = this,
            )

        assertTrue(viewModel.uiState.value.canUseAudioPrompt)
        viewModel.submitAudioPrompt(AudioPrompt("/tmp/question.wav", "audio/wav"))
        runCurrent()

        assertEquals("spoken question", engine.lastRequest!!.textPrompt)
        val content = viewModel.uiState.value.messages.first().content as MessageContent.AudioPromptContent
        assertEquals("spoken question", content.transcript)
        assertEquals(AudioTranscriptionStatus.Completed, content.transcriptionStatus)
        assertEquals("spoken question", viewModel.uiState.value.sessions.first().title)
    }

    @Test
    fun `direct audio without whisper promotes regular chat with fallback title`() = runTest {
        val store = DeferredNewChatSessionStore(InMemoryChatSessionStore())
        val viewModel =
            ChatViewModel(
                engine = CapturingEngine(),
                initialModel = model.copy(inputCapabilities = ModelInputCapabilities(audio = true)),
                inferenceConfig = inferenceConfig,
                sessionStore = store,
                audioTranscriber = UnavailableAudioTranscriber,
                audioSessionTitleProvider = { "Voice message · test" },
                scope = this,
            )

        viewModel.submitAudioPrompt(AudioPrompt("/tmp/question.wav", "audio/wav"))
        runCurrent()

        assertEquals("Voice message · test", store.listSessions().single().title)
        assertEquals("Voice message · test", viewModel.uiState.value.sessions.single().title)
        val content = viewModel.uiState.value.messages.first().content as MessageContent.AudioPromptContent
        assertEquals(AudioTranscriptionStatus.NotRequested, content.transcriptionStatus)
    }

    @Test
    fun `persists successful transcription diagnostics and partial warning`() = runTest {
        val store = InMemoryChatSessionStore()
        val viewModel = ChatViewModel(
            engine = CapturingEngine(),
            initialModel = model,
            inferenceConfig = inferenceConfig,
            sessionStore = store,
            audioTranscriber = FakeAudioTranscriber(
                transcript = "partial speech",
                diagnostic = "outcome=success\ncompletion_source=standard_results",
                mayBeIncomplete = true,
                incompleteReason = "unexpected_completion_source:standard_results",
            ),
            scope = this,
        )

        viewModel.submitAudioPrompt(AudioPrompt("/tmp/question.wav", "audio/wav"))
        runCurrent()

        val content = viewModel.uiState.value.messages.first().content as MessageContent.AudioPromptContent
        assertEquals("outcome=success\ncompletion_source=standard_results", content.transcriptionDiagnostic)
        assertTrue(content.transcriptionMayBeIncomplete)
        assertEquals("unexpected_completion_source:standard_results", content.transcriptionIncompleteReason)
    }

    @Test
    fun `keeps direct audio generation when asynchronous transcription fails`() = runTest {
        val engine = CapturingEngine()
        val viewModel =
            ChatViewModel(
                engine = engine,
                initialModel = model.copy(inputCapabilities = ModelInputCapabilities(audio = true)),
                inferenceConfig = inferenceConfig,
                audioTranscriber = FakeAudioTranscriber(
                    failure = AudioTranscriptionFailure(
                        AudioTranscriptionFailureKind.EmptyResults,
                        "not understood",
                        "events=results:hypotheses=0",
                    ),
                ),
                scope = this,
            )

        viewModel.submitAudioPrompt(AudioPrompt("/tmp/question.wav", "audio/wav"))
        runCurrent()

        assertTrue(engine.lastRequest!!.content is MessageContent.AudioPromptContent)
        val content = viewModel.uiState.value.messages.first().content as MessageContent.AudioPromptContent
        assertEquals(AudioTranscriptionStatus.Failed, content.transcriptionStatus)
        assertEquals(AudioTranscriptionFailureKind.EmptyResults, content.transcriptionFailureKind)
        assertEquals("events=results:hypotheses=0", content.transcriptionDiagnostic)
    }

    @Test
    fun `blocks text only generation when required transcription fails`() = runTest {
        val engine = CapturingEngine()
        val viewModel =
            ChatViewModel(
                engine = engine,
                initialModel = model,
                inferenceConfig = inferenceConfig,
                audioTranscriber = FakeAudioTranscriber(error = "not understood"),
                scope = this,
            )

        viewModel.submitAudioPrompt(AudioPrompt("/tmp/question.wav", "audio/wav"))
        runCurrent()

        assertEquals(null, engine.lastRequest)
        assertEquals("not understood", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isGenerating)
        assertEquals(1, viewModel.uiState.value.messages.size)
    }

    @Test
    fun `persists transcript visibility preference independently from content`() {
        val preferences = InMemoryChatPreferences()
        val viewModel =
            ChatViewModel(
                engine = CapturingEngine(),
                initialModel = model,
                inferenceConfig = inferenceConfig,
                preferences = preferences,
            )

        viewModel.setShowAudioTranscriptions(false)

        assertFalse(viewModel.uiState.value.showAudioTranscriptions)
        assertFalse(preferences.showAudioTranscriptions.value)
    }

    @Test
    fun `gates reasoning settings by selected model capabilities`() = runTest {
        val engine = CapturingEngine()
        val reasoningModel =
            model.copy(
                reasoningCapabilities =
                ModelReasoningCapabilities(
                    request = true,
                    output = true,
                ),
            )
        val viewModel =
            ChatViewModel(
                engine = engine,
                initialModel = reasoningModel,
                inferenceConfig = inferenceConfig,
                scope = this,
            )

        viewModel.setReasoningEnabled(true)
        viewModel.setShowReasoning(true)
        viewModel.onPromptChanged("think")
        viewModel.submitPrompt()
        runCurrent()

        assertTrue(viewModel.uiState.value.reasoningEnabled)
        assertTrue(viewModel.uiState.value.showReasoning)
        assertTrue(engine.lastRequest!!.reasoningEnabled)

        viewModel.onModelStartupState(ModelStartupState.Available(model, inferenceConfig))

        assertFalse(viewModel.uiState.value.canEnableReasoning)
        assertFalse(viewModel.uiState.value.canShowReasoning)
        assertFalse(viewModel.uiState.value.reasoningEnabled)
        assertFalse(viewModel.uiState.value.showReasoning)
    }

    @Test
    fun `restores persisted reasoning choices after recreation and capability changes`() {
        val reasoningModel =
            model.copy(
                reasoningCapabilities = ModelReasoningCapabilities(request = true, output = true),
            )
        val preferences =
            InMemoryChatPreferences(
                initialReasoningEnabled = true,
                initialShowReasoning = true,
            )
        val recreated =
            ChatViewModel(
                engine = CapturingEngine(),
                initialModel = reasoningModel,
                inferenceConfig = inferenceConfig,
                preferences = preferences,
            )

        assertTrue(recreated.uiState.value.reasoningEnabled)
        assertTrue(recreated.uiState.value.showReasoning)

        recreated.onModelStartupState(ModelStartupState.Available(model, inferenceConfig))
        assertFalse(recreated.uiState.value.reasoningEnabled)
        assertFalse(recreated.uiState.value.showReasoning)
        assertTrue(preferences.reasoningEnabled.value)
        assertTrue(preferences.showReasoning.value)

        recreated.onModelStartupState(ModelStartupState.Available(reasoningModel, inferenceConfig))
        assertTrue(recreated.uiState.value.reasoningEnabled)
        assertTrue(recreated.uiState.value.showReasoning)
    }

    @Test
    fun `ignores reasoning enable request when selected model does not support it`() {
        val viewModel =
            ChatViewModel(
                engine = FakeLocalLlmEngine(chunks = listOf("ignored")),
                initialModel = model,
                inferenceConfig = inferenceConfig,
            )

        viewModel.setReasoningEnabled(true)
        viewModel.setShowReasoning(true)

        assertFalse(viewModel.uiState.value.reasoningEnabled)
        assertFalse(viewModel.uiState.value.showReasoning)
    }

    @Test
    fun `stores reasoning tokens separately from assistant answer`() = runTest {
        val engine = ReasoningEngine()
        val viewModel =
            ChatViewModel(
                engine = engine,
                initialModel =
                model.copy(
                    reasoningCapabilities =
                    ModelReasoningCapabilities(
                        request = true,
                        output = true,
                    ),
                ),
                inferenceConfig = inferenceConfig,
                scope = this,
            )

        viewModel.setReasoningEnabled(true)
        viewModel.onPromptChanged("solve")
        viewModel.submitPrompt()
        runCurrent()

        val content =
            viewModel.uiState.value.messages
                .last()
                .content as MessageContent.TextPrompt
        assertEquals("final", content.text)
        assertEquals("because ", content.reasoningText)
    }

    @Test
    fun `marks reasoning without final answer as incomplete`() = runTest {
        val store = InMemoryChatSessionStore()
        val viewModel =
            ChatViewModel(
                engine =
                EventStreamingEngine(
                    listOf(
                        GenerationEvent.ReasoningToken("unfinished reasoning"),
                        GenerationEvent.Completed,
                    ),
                ),
                initialModel =
                model.copy(
                    reasoningCapabilities = ModelReasoningCapabilities(request = true, output = true),
                ),
                inferenceConfig = inferenceConfig,
                sessionStore = store,
                scope = this,
            )

        viewModel.onPromptChanged("solve")
        viewModel.submitPrompt()
        runCurrent()

        val content = store.getMessages(store.listSessions().first().id).last().content as MessageContent.TextPrompt
        assertEquals("", content.text)
        assertEquals("unfinished reasoning", content.reasoningText)
        assertEquals(AssistantCompletionStatus.Incomplete, content.completionStatus)
    }

    @Test
    fun `marks completed generation without any model output as incomplete`() = runTest {
        val store = InMemoryChatSessionStore()
        val viewModel =
            ChatViewModel(
                engine = EventStreamingEngine(listOf(GenerationEvent.Completed)),
                initialModel = model,
                inferenceConfig = inferenceConfig,
                sessionStore = store,
                scope = this,
            )

        viewModel.onPromptChanged("answer")
        viewModel.submitPrompt()
        runCurrent()

        val content = store.getMessages(store.listSessions().first().id).last().content as MessageContent.TextPrompt
        assertEquals("", content.text)
        assertEquals("", content.reasoningText)
        assertEquals(AssistantCompletionStatus.Incomplete, content.completionStatus)
    }

    @Test
    fun `uses effective generation configuration and records metrics`() = runTest {
        val engine = CapturingEngine()
        var recorded: com.jesjobom.ararai.engine.GenerationMetrics? = null
        val metrics = com.jesjobom.ararai.engine.GenerationMetrics(10, 2, 20.0, 3, 30.0)
        val viewModel =
            ChatViewModel(
                engine =
                EventStreamingEngine(
                    listOf(
                        GenerationEvent.Metrics(metrics),
                        GenerationEvent.Token("answer"),
                        GenerationEvent.Completed,
                    ),
                    onLoad = { engine.loadedConfig = it },
                ),
                initialModel = model,
                inferenceConfig = inferenceConfig,
                generationConfigProvider = { _, config ->
                    config.copy(contextTokens = 4_096, temperature = 0.2f)
                },
                generationMetricsConsumer = { _, value -> recorded = value },
                scope = this,
            )

        viewModel.onPromptChanged("hello")
        viewModel.submitPrompt()
        runCurrent()

        assertEquals(4_096, engine.loadedConfig?.contextTokens)
        assertEquals(0.2f, engine.loadedConfig?.temperature)
        assertEquals(metrics, recorded)
    }

    @Test
    fun `coalesces streamed presentation independently from persistence`() = runTest {
        val store = CountingChatSessionStore()
        val viewModel =
            ChatViewModel(
                engine = BatchedStreamingEngine(),
                initialModel = model,
                inferenceConfig = inferenceConfig,
                sessionStore = store,
                scope = this,
                assistantPersistenceIntervalMillis = 250L,
                assistantPresentationIntervalMillis = 50L,
            )

        viewModel.onPromptChanged("hello")
        viewModel.submitPrompt()
        runCurrent()

        assertEquals(
            "",
            viewModel.uiState.value.messages
                .last()
                .text,
        )
        assertEquals(0L, viewModel.uiState.value.messageDisplayRevision)
        assertEquals("", store.latestAssistantText())
        assertEquals(0, store.updateCalls)

        advanceTimeBy(50L)
        runCurrent()

        assertEquals(
            "three chunks",
            viewModel.uiState.value.messages
                .last()
                .text,
        )
        assertEquals(1L, viewModel.uiState.value.messageDisplayRevision)
        assertEquals("", store.latestAssistantText())
        assertEquals(0, store.updateCalls)

        advanceTimeBy(200L)
        runCurrent()

        assertEquals("three chunks", store.latestAssistantText())
        assertEquals(1, store.updateCalls)
        viewModel.cancelGeneration()
        runCurrent()
    }

    @Test
    fun `flushes pending assistant content on completion`() = runTest {
        val store = CountingChatSessionStore()
        val viewModel =
            streamingViewModel(
                engine = TerminalStreamingEngine(GenerationEvent.Completed),
                store = store,
            )

        viewModel.onPromptChanged("hello")
        viewModel.submitPrompt()
        runCurrent()

        assertEquals("partial", store.latestAssistantText())
        assertEquals(
            "partial",
            viewModel.uiState.value.messages
                .last()
                .text,
        )
        assertEquals(1L, viewModel.uiState.value.messageDisplayRevision)
        assertEquals(1, store.updateCalls)
        assertFalse(viewModel.uiState.value.isGenerating)
        assertEquals(
            viewModel.uiState.value.messages
                .last()
                .id,
            viewModel.uiState.value.completedAssistantMessageId,
        )
    }

    @Test
    fun `captures completed knowledge sources without persisting tool protocol`() = runTest {
        val source =
            KnowledgeSource(
                provider = "Wikipedia",
                title = "Alan Turing",
                canonicalUrl = "https://en.wikipedia.org/wiki/Alan_Turing",
                language = "en",
                retrievedAtMillis = 42L,
            )
        val webSource =
            KnowledgeSource(
                provider = "Exa Web Search",
                title = "Current reference",
                canonicalUrl = "https://example.com/current",
                language = "en",
                retrievedAtMillis = 43L,
            )
        val store = CountingChatSessionStore()
        val viewModel =
            streamingViewModel(
                engine =
                EventStreamingEngine(
                    listOf(
                        GenerationEvent.ToolStarted("wikipedia_search"),
                        GenerationEvent.ToolFinished(
                            toolName = "wikipedia_search",
                            sources = listOf(source),
                        ),
                        GenerationEvent.ToolStarted("web_search"),
                        GenerationEvent.ToolFinished(
                            toolName = "web_search",
                            sources = listOf(webSource),
                        ),
                        GenerationEvent.ToolStarted("calculator", "Local calculator"),
                        GenerationEvent.ToolFinished(toolName = "calculator"),
                        GenerationEvent.Token("Final answer"),
                        GenerationEvent.Completed,
                    ),
                ),
                store = store,
            )

        viewModel.onPromptChanged("research")
        viewModel.submitPrompt()
        runCurrent()

        assertFalse(viewModel.uiState.value.toolInProgress)
        assertEquals(listOf(source, webSource), viewModel.uiState.value.researchSources)
        assertEquals("Final answer", store.latestAssistantText())
        assertEquals(
            listOf(source, webSource),
            store.latestAssistantContent().sources,
        )
    }

    @Test
    fun `flushes pending assistant content on generation failure`() = runTest {
        val store = CountingChatSessionStore()
        val viewModel =
            streamingViewModel(
                engine = TerminalStreamingEngine(GenerationEvent.Failed("failed")),
                store = store,
            )

        viewModel.onPromptChanged("hello")
        viewModel.submitPrompt()
        runCurrent()

        assertEquals("partial", store.latestAssistantText())
        assertEquals(
            "partial",
            viewModel.uiState.value.messages
                .last()
                .text,
        )
        assertEquals(1, store.updateCalls)
        assertEquals(null, viewModel.uiState.value.error)
        assertEquals(UserMessageKey.GenerationFailed, viewModel.uiState.value.errorKey)
        assertEquals(null, viewModel.uiState.value.completedAssistantMessageId)
    }

    @Test
    fun `flushes pending assistant content before cancellation`() = runTest {
        val store = CountingChatSessionStore()
        val viewModel = streamingViewModel(engine = SlowEngine(), store = store)

        viewModel.onPromptChanged("hello")
        viewModel.submitPrompt()
        runCurrent()
        viewModel.cancelGeneration()
        runCurrent()

        assertEquals("partial", store.latestAssistantText())
        assertEquals(
            "partial",
            viewModel.uiState.value.messages
                .last()
                .text,
        )
        assertEquals(1, store.updateCalls)
        assertEquals(null, viewModel.uiState.value.completedAssistantMessageId)
    }

    @Test
    fun `flushes pending assistant content when leaving chat`() = runTest {
        val store = CountingChatSessionStore()
        val viewModel = streamingViewModel(engine = SlowEngine(), store = store)

        viewModel.onPromptChanged("hello")
        viewModel.submitPrompt()
        runCurrent()
        viewModel.onLeavingChat()
        runCurrent()

        assertEquals("partial", store.latestAssistantText())
        assertEquals(1, store.updateCalls)
    }

    private fun kotlinx.coroutines.test.TestScope.streamingViewModel(
        engine: LocalLlmEngine,
        store: CountingChatSessionStore,
    ): ChatViewModel = ChatViewModel(
        engine = engine,
        initialModel = model,
        inferenceConfig = inferenceConfig,
        sessionStore = store,
        scope = this,
        assistantPersistenceIntervalMillis = 250L,
        assistantPresentationIntervalMillis = 50L,
    )

    @Test
    fun `unloads engine when model becomes unavailable`() = runTest {
        val engine = SlowEngine()
        val viewModel =
            ChatViewModel(
                engine = engine,
                initialModel = model,
                inferenceConfig = inferenceConfig,
                scope = this,
            )

        viewModel.onModelStartupState(ModelStartupState.Missing)
        runCurrent()

        assertEquals(1, engine.unloadCalls)
        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `close cancels owned work without cancelling parent scope and rejects new submissions`() = runTest {
        val engine = SlowEngine()
        val viewModel =
            ChatViewModel(
                engine = engine,
                initialModel = model,
                inferenceConfig = inferenceConfig,
                scope = this,
            )

        viewModel.onPromptChanged("first")
        viewModel.submitPrompt()
        runCurrent()
        assertEquals(1, engine.generateCalls)

        viewModel.close()
        viewModel.close()
        runCurrent()

        assertTrue(currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive == true)
        assertFalse(viewModel.uiState.value.isGenerating)
        viewModel.onPromptChanged("second")
        viewModel.submitPrompt()
        runCurrent()
        assertEquals(1, engine.generateCalls)
    }

    private fun waitUntil(
        timeoutMillis: Long = 5_000L,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10L)
        assertTrue("Condition was not met within ${timeoutMillis}ms", condition())
    }

    private class ThreadRecordingChatSessionStore(
        private val delegate: ChatSessionStore = InMemoryChatSessionStore(),
    ) : ChatSessionStore by delegate {
        val threadNames = CopyOnWriteArrayList<String>()

        override fun listSessions(): List<ChatSession> = recorded(delegate::listSessions)

        override fun getRecentMessages(
            sessionId: String,
            limit: Int,
        ): List<StoredChatMessage> = recorded { delegate.getRecentMessages(sessionId, limit) }

        override fun countMessages(sessionId: String): Int = recorded { delegate.countMessages(sessionId) }

        override fun createSession(title: String): ChatSession = recorded { delegate.createSession(title) }

        override fun appendMessage(
            sessionId: String,
            role: ChatRole,
            content: MessageContent,
        ): StoredChatMessage = recorded { delegate.appendMessage(sessionId, role, content) }

        override fun updateMessage(
            messageId: String,
            content: MessageContent,
        ) = recorded { delegate.updateMessage(messageId, content) }

        private fun <T> recorded(block: () -> T): T {
            threadNames += Thread.currentThread().name
            return block()
        }
    }

    private class FailingEngine(
        private val message: String,
        private val kind: GenerationFailureKind = GenerationFailureKind.Unexpected,
    ) : LocalLlmEngine {
        override suspend fun load(
            model: LocalModel,
            config: InferenceConfig,
        ) = Unit

        override fun generate(request: PromptRequest): Flow<GenerationEvent> = flowOf(
            GenerationEvent.Failed(message = message, kind = kind),
        )

        override suspend fun unload() = Unit
    }

    private class LoadFailingEngine(
        private val message: String,
    ) : LocalLlmEngine {
        val loadStarted = CompletableDeferred<Unit>()
        private val loadFailure = CompletableDeferred<Unit>()

        override suspend fun load(
            model: LocalModel,
            config: InferenceConfig,
        ) {
            loadStarted.complete(Unit)
            loadFailure.await()
            error(message)
        }

        fun failLoad() {
            loadFailure.complete(Unit)
        }

        override fun generate(request: PromptRequest): Flow<GenerationEvent> = flowOf(GenerationEvent.Token("unexpected"))

        override suspend fun unload() = Unit
    }

    private class SlowEngine : LocalLlmEngine {
        var generateCalls = 0
            private set
        var unloadCalls = 0
            private set

        override suspend fun load(
            model: LocalModel,
            config: InferenceConfig,
        ) = Unit

        override fun generate(request: PromptRequest): Flow<GenerationEvent> = flow {
            generateCalls += 1
            emit(GenerationEvent.Token("partial"))
            kotlinx.coroutines.delay(Long.MAX_VALUE)
        }

        override suspend fun unload() {
            unloadCalls += 1
        }
    }

    private class BatchedStreamingEngine : LocalLlmEngine {
        override suspend fun load(
            model: LocalModel,
            config: InferenceConfig,
        ) = Unit

        override fun generate(request: PromptRequest): Flow<GenerationEvent> = flow {
            emit(GenerationEvent.Token("three"))
            emit(GenerationEvent.Token(" chunks"))
            kotlinx.coroutines.delay(Long.MAX_VALUE)
        }

        override suspend fun unload() = Unit
    }

    private class TerminalStreamingEngine(
        private val terminalEvent: GenerationEvent,
    ) : LocalLlmEngine {
        override suspend fun load(
            model: LocalModel,
            config: InferenceConfig,
        ) = Unit

        override fun generate(request: PromptRequest): Flow<GenerationEvent> = flowOf(
            GenerationEvent.Token("partial"),
            terminalEvent,
        )

        override suspend fun unload() = Unit
    }

    private class EventStreamingEngine(
        private val events: List<GenerationEvent>,
        private val onLoad: (InferenceConfig) -> Unit = {},
    ) : LocalLlmEngine {
        override suspend fun load(
            model: LocalModel,
            config: InferenceConfig,
        ) = onLoad(config)

        override fun generate(request: PromptRequest): Flow<GenerationEvent> = flowOf(*events.toTypedArray())

        override suspend fun unload() = Unit
    }

    private class CountingChatSessionStore(
        private val delegate: ChatSessionStore = InMemoryChatSessionStore(),
    ) : ChatSessionStore by delegate {
        var updateCalls: Int = 0
            private set

        override fun updateMessage(
            messageId: String,
            content: MessageContent,
        ) {
            updateCalls += 1
            delegate.updateMessage(messageId, content)
        }

        fun latestAssistantText(): String = latestAssistantContent().text

        fun latestAssistantContent(): MessageContent.TextPrompt {
            val session = delegate.listSessions().first()
            return delegate
                .getMessages(session.id)
                .last { it.role == ChatRole.Assistant }
                .content as MessageContent.TextPrompt
        }
    }

    private class CapturingEngine : LocalLlmEngine {
        var loadedConfig: InferenceConfig? = null
        var lastPrompt: String? = null
            private set
        var lastRequest: PromptRequest? = null
            private set

        override suspend fun load(
            model: LocalModel,
            config: InferenceConfig,
        ) = Unit

        override fun generate(request: PromptRequest): Flow<GenerationEvent> {
            lastRequest = request
            lastPrompt = request.prompt
            return flowOf(GenerationEvent.Completed)
        }

        override suspend fun unload() = Unit
    }

    private class FakeAudioTranscriber(
        private val transcript: String = "",
        private val error: String? = null,
        private val failure: AudioTranscriptionFailure? = null,
        private val diagnostic: String = "outcome=success",
        private val mayBeIncomplete: Boolean = false,
        private val incompleteReason: String? = null,
    ) : AudioTranscriber {
        override val isAvailable = true
        override suspend fun transcribe(audio: AudioPrompt): AudioTranscriptionResult {
            failure?.let { throw AudioTranscriptionException(it) }
            error?.let { throw IllegalStateException(it) }
            return AudioTranscriptionResult(transcript, diagnostic, mayBeIncomplete, incompleteReason)
        }
    }

    private class ReasoningEngine : LocalLlmEngine {
        override suspend fun load(
            model: LocalModel,
            config: InferenceConfig,
        ) = Unit

        override fun generate(request: PromptRequest): Flow<GenerationEvent> = flowOf(
            GenerationEvent.ReasoningToken("because "),
            GenerationEvent.Token("final"),
            GenerationEvent.Completed,
        )

        override suspend fun unload() = Unit
    }
}
