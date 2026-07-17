package com.jesjobom.ararai.chat

import app.cash.turbine.test
import com.jesjobom.ararai.engine.FakeLocalLlmEngine
import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.PromptChatMessage
import com.jesjobom.ararai.engine.PromptChatRole
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelInputCapabilities
import com.jesjobom.ararai.model.ModelReasoningCapabilities
import com.jesjobom.ararai.model.ModelStartupState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val model = LocalModel(
        id = "test-model",
        name = "Test Model",
        filePath = "/tmp/test.gguf",
    )

    private val inferenceConfig = InferenceConfig(
        contextTokens = 128,
        temperature = 0.7f,
        topP = 0.9f,
    )

    @Test
    fun `keeps submit disabled until model is available`() {
        val viewModel = ChatViewModel(
            engine = FakeLocalLlmEngine(chunks = listOf("ignored")),
            initialModel = null,
            inferenceConfig = inferenceConfig,
        )

        viewModel.onPromptChanged("hello")

        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `enables submit after startup state provides available model`() {
        val viewModel = ChatViewModel(
            engine = FakeLocalLlmEngine(chunks = listOf("ignored")),
            initialModel = null,
            inferenceConfig = inferenceConfig,
        )

        viewModel.onModelStartupState(ModelStartupState.Available(model, inferenceConfig))
        viewModel.onPromptChanged("hello")

        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `keeps submit disabled for blank prompt even when model is available`() {
        val viewModel = ChatViewModel(
            engine = FakeLocalLlmEngine(chunks = listOf("ignored")),
            initialModel = model,
            inferenceConfig = inferenceConfig,
        )

        viewModel.onPromptChanged("   ")

        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `enables submit for image only draft when model supports images`() {
        val viewModel = ChatViewModel(
            engine = FakeLocalLlmEngine(chunks = listOf("ignored")),
            initialModel = model.copy(inputCapabilities = ModelInputCapabilities(image = true)),
            inferenceConfig = inferenceConfig,
        )

        viewModel.attachImage(ImageAttachment("/tmp/image.jpg", "image/jpeg", "image.jpg"))

        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `exposes retry when startup download fails`() {
        val viewModel = ChatViewModel(
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
        val viewModel = ChatViewModel(
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
        val viewModel = ChatViewModel(
            engine = FakeLocalLlmEngine(chunks = listOf("ola", " mundo")),
            initialModel = model,
            inferenceConfig = inferenceConfig,
        )

        viewModel.uiState.test {
            assertTrue(awaitItem().canSubmit.not())
            viewModel.onPromptChanged("oi")
            assertTrue(awaitItem().canSubmit)
            viewModel.submitPrompt()

            val loading = awaitItem()
            assertTrue(loading.isGenerating)
            assertFalse(loading.canSubmit)

            val loaded = awaitItem()
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
        val viewModel = ChatViewModel(
            engine = FailingEngine("generation failed"),
            initialModel = model,
            inferenceConfig = inferenceConfig,
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.onPromptChanged("oi")
            awaitItem()
            viewModel.submitPrompt()

            val loading = awaitItem()
            assertEquals(2, loading.messages.size)
            assertTrue(loading.isGenerating)

            var failed = awaitItem()
            while (failed.error == null) {
                failed = awaitItem()
            }
            assertFalse(failed.isGenerating)
            assertEquals("generation failed", failed.error)
            assertEquals("oi", failed.messages.first().text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `surfaces load failure and preserves submitted message`() = runTest {
        val viewModel = ChatViewModel(
            engine = LoadFailingEngine("load failed"),
            initialModel = model,
            inferenceConfig = inferenceConfig,
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.onPromptChanged("oi")
            awaitItem()
            viewModel.submitPrompt()

            val loading = awaitItem()
            assertTrue(loading.isLoadingModel)
            assertFalse(loading.canSubmit)

            val failed = awaitItem()
            assertFalse(failed.isLoadingModel)
            assertFalse(failed.isGenerating)
            assertEquals("load failed", failed.error)
            assertEquals("oi", failed.messages.first().text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `blocks second submit while first generation is active`() = runTest {
        val engine = SlowEngine()
        val viewModel = ChatViewModel(
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
        val viewModel = ChatViewModel(
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
        val viewModel = ChatViewModel(
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
        assertEquals("partial", viewModel.uiState.value.messages.last().text)
    }

    @Test
    fun `manages persistent chat sessions`() {
        val store = InMemoryChatSessionStore()
        val viewModel = ChatViewModel(
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
        assertEquals("Work notes", viewModel.uiState.value.sessions.first { it.id == secondSession }.title)

        viewModel.renameSession(firstSession!!, "Earlier chat")
        assertEquals("Earlier chat", viewModel.uiState.value.sessions.first { it.id == firstSession }.title)
        assertEquals(secondSession, viewModel.uiState.value.selectedSessionId)

        viewModel.selectSession(firstSession)
        assertEquals(firstSession, viewModel.uiState.value.selectedSessionId)

        viewModel.selectSession(secondSession!!)
        viewModel.deleteCurrentSession()

        assertEquals(1, viewModel.uiState.value.sessions.size)
        assertEquals(firstSession, viewModel.uiState.value.selectedSessionId)
    }

    @Test
    fun `clears every session and creates a new empty session`() {
        val store = InMemoryChatSessionStore()
        val first = store.ensureSession()
        store.appendMessage(first.id, ChatRole.User, "first message")
        val viewModel = ChatViewModel(
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
        val viewModel = ChatViewModel(
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
        store.appendMessage(first.id, ChatRole.User, MessageContent.TextPrompt("first", listOf(
            ImageAttachment(exclusive.absolutePath, "image/jpeg"),
            ImageAttachment(shared.absolutePath, "image/jpeg"),
        )))
        val second = store.createSession("Second")
        store.appendMessage(second.id, ChatRole.User, MessageContent.TextPrompt("second", listOf(
            ImageAttachment(shared.absolutePath, "image/jpeg"),
        )))
        val viewModel = ChatViewModel(
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
        val viewModel = ChatViewModel(
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
        val viewModel = ChatViewModel(
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
        assertEquals("Current question", store.getMessages(session.id).filter { it.role == ChatRole.User }.last().text)
    }

    @Test
    fun `submits text prompt with image attachment`() = runTest {
        val engine = CapturingEngine()
        val viewModel = ChatViewModel(
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
        assertEquals(1, viewModel.uiState.value.messages.first().content.let { it as MessageContent.TextPrompt }.imageAttachments.size)
    }

    @Test
    fun `audio prompt is mutually exclusive with text and images`() = runTest {
        val engine = CapturingEngine()
        val viewModel = ChatViewModel(
            engine = engine,
            initialModel = model.copy(inputCapabilities = ModelInputCapabilities(image = true, audio = true)),
            inferenceConfig = inferenceConfig,
            scope = this,
        )

        viewModel.onPromptChanged("typed text")
        viewModel.attachImage(ImageAttachment("file:///tmp/image.png", "image/png"))
        viewModel.useAudioPrompt(AudioPrompt("file:///tmp/audio.wav", "audio/wav", "audio.wav"))

        assertEquals("", viewModel.uiState.value.prompt)
        assertEquals(emptyList<ImageAttachment>(), viewModel.uiState.value.imageAttachments)
        assertTrue(viewModel.uiState.value.canSubmit)

        viewModel.onPromptChanged("should be ignored")
        viewModel.submitPrompt()
        runCurrent()

        assertTrue(engine.lastRequest!!.content is MessageContent.AudioPromptContent)
        assertEquals("", viewModel.uiState.value.prompt)
    }

    @Test
    fun `submits recorded audio prompt directly`() = runTest {
        val engine = CapturingEngine()
        val viewModel = ChatViewModel(
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
        assertEquals(audio, (viewModel.uiState.value.messages.first().content as MessageContent.AudioPromptContent).audio)
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
        val viewModel = ChatViewModel(
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
    fun `gates reasoning settings by selected model capabilities`() = runTest {
        val engine = CapturingEngine()
        val reasoningModel = model.copy(
            reasoningCapabilities = ModelReasoningCapabilities(
                request = true,
                output = true,
            ),
        )
        val viewModel = ChatViewModel(
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
    fun `ignores reasoning enable request when selected model does not support it`() {
        val viewModel = ChatViewModel(
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
        val viewModel = ChatViewModel(
            engine = engine,
            initialModel = model.copy(
                reasoningCapabilities = ModelReasoningCapabilities(
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

        val content = viewModel.uiState.value.messages.last().content as MessageContent.TextPrompt
        assertEquals("final", content.text)
        assertEquals("because ", content.reasoningText)
    }

    @Test
    fun `batches streamed assistant persistence without delaying UI updates`() = runTest {
        val store = CountingChatSessionStore()
        val viewModel = ChatViewModel(
            engine = BatchedStreamingEngine(),
            initialModel = model,
            inferenceConfig = inferenceConfig,
            sessionStore = store,
            scope = this,
            assistantPersistenceIntervalMillis = 250L,
        )

        viewModel.onPromptChanged("hello")
        viewModel.submitPrompt()
        runCurrent()

        assertEquals("three chunks", viewModel.uiState.value.messages.last().text)
        assertEquals("", store.latestAssistantText())
        assertEquals(0, store.updateCalls)

        advanceTimeBy(250L)
        runCurrent()

        assertEquals("three chunks", store.latestAssistantText())
        assertEquals(1, store.updateCalls)
        viewModel.cancelGeneration()
        runCurrent()
    }

    @Test
    fun `flushes pending assistant content on completion`() = runTest {
        val store = CountingChatSessionStore()
        val viewModel = streamingViewModel(
            engine = TerminalStreamingEngine(GenerationEvent.Completed),
            store = store,
        )

        viewModel.onPromptChanged("hello")
        viewModel.submitPrompt()
        runCurrent()

        assertEquals("partial", store.latestAssistantText())
        assertEquals(1, store.updateCalls)
        assertFalse(viewModel.uiState.value.isGenerating)
    }

    @Test
    fun `flushes pending assistant content on generation failure`() = runTest {
        val store = CountingChatSessionStore()
        val viewModel = streamingViewModel(
            engine = TerminalStreamingEngine(GenerationEvent.Failed("failed")),
            store = store,
        )

        viewModel.onPromptChanged("hello")
        viewModel.submitPrompt()
        runCurrent()

        assertEquals("partial", store.latestAssistantText())
        assertEquals(1, store.updateCalls)
        assertEquals("failed", viewModel.uiState.value.error)
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
        assertEquals(1, store.updateCalls)
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
    )


    @Test
    fun `unloads engine when model becomes unavailable`() = runTest {
        val engine = SlowEngine()
        val viewModel = ChatViewModel(
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

    private class FailingEngine(
        private val message: String,
    ) : LocalLlmEngine {
        override suspend fun load(model: LocalModel, config: InferenceConfig) = Unit

        override fun generate(request: PromptRequest): Flow<GenerationEvent> =
            flowOf(GenerationEvent.Failed(message))

        override suspend fun unload() = Unit
    }

    private class LoadFailingEngine(
        private val message: String,
    ) : LocalLlmEngine {
        override suspend fun load(model: LocalModel, config: InferenceConfig) {
            error(message)
        }

        override fun generate(request: PromptRequest): Flow<GenerationEvent> =
            flowOf(GenerationEvent.Token("unexpected"))

        override suspend fun unload() = Unit
    }

    private class SlowEngine : LocalLlmEngine {
        var generateCalls = 0
            private set
        var unloadCalls = 0
            private set

        override suspend fun load(model: LocalModel, config: InferenceConfig) = Unit

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
        override suspend fun load(model: LocalModel, config: InferenceConfig) = Unit

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
        override suspend fun load(model: LocalModel, config: InferenceConfig) = Unit

        override fun generate(request: PromptRequest): Flow<GenerationEvent> = flowOf(
            GenerationEvent.Token("partial"),
            terminalEvent,
        )

        override suspend fun unload() = Unit
    }

    private class CountingChatSessionStore(
        private val delegate: ChatSessionStore = InMemoryChatSessionStore(),
    ) : ChatSessionStore by delegate {
        var updateCalls: Int = 0
            private set

        override fun updateMessage(messageId: String, content: MessageContent) {
            updateCalls += 1
            delegate.updateMessage(messageId, content)
        }

        fun latestAssistantText(): String {
            val session = delegate.listSessions().first()
            return delegate.getMessages(session.id).last { it.role == ChatRole.Assistant }.text
        }
    }

    private class CapturingEngine : LocalLlmEngine {
        var lastPrompt: String? = null
            private set
        var lastRequest: PromptRequest? = null
            private set

        override suspend fun load(model: LocalModel, config: InferenceConfig) = Unit

        override fun generate(request: PromptRequest): Flow<GenerationEvent> {
            lastRequest = request
            lastPrompt = request.prompt
            return flowOf(GenerationEvent.Completed)
        }

        override suspend fun unload() = Unit
    }

    private class ReasoningEngine : LocalLlmEngine {
        override suspend fun load(model: LocalModel, config: InferenceConfig) = Unit

        override fun generate(request: PromptRequest): Flow<GenerationEvent> =
            flowOf(
                GenerationEvent.ReasoningToken("because "),
                GenerationEvent.Token("final"),
                GenerationEvent.Completed,
            )

        override suspend fun unload() = Unit
    }
}
