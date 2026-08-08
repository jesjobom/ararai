package com.jesjobom.ararai.voice

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jesjobom.ararai.chat.ChatRole
import com.jesjobom.ararai.chat.ChatViewModel
import com.jesjobom.ararai.chat.ConversationTurnSettings
import com.jesjobom.ararai.chat.InMemoryChatSessionStore
import com.jesjobom.ararai.chat.MessageContent
import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.knowledge.KnowledgeSource
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelInputCapabilities
import com.jesjobom.ararai.model.ModelStartupState
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class WebSearchChatVoiceParityTest {
    private val model =
        LocalModel(
            id = "web-search-test-model",
            name = "Web Search Test Model",
            filePath = "/data/local/tmp/test.task",
            inputCapabilities = ModelInputCapabilities(audio = true),
        )
    private val inference = InferenceConfig(contextTokens = 2_048, temperature = 0.2f, topP = 0.9f)
    private val turnSettings = ConversationTurnSettings("Test", setOf("web_search"))

    @Test
    fun completedFakeProviderTurnHasTheSameAnswerAndSourcesInChatAndVoice() {
        val source =
            KnowledgeSource(
                provider = "Fake Tavily",
                title = "Current result",
                canonicalUrl = "https://example.com/current",
                language = "en",
                retrievedAtMillis = 42L,
            )
        val events =
            listOf(
                GenerationEvent.ToolStarted("web_search", "Fake Tavily"),
                GenerationEvent.ToolFinished("web_search", listOf(source)),
                GenerationEvent.Token("Final answer from focused evidence."),
                GenerationEvent.Completed,
            )
        val chatStore = InMemoryChatSessionStore()
        val chat =
            ChatViewModel(
                engine = ScriptedWebSearchEngine(events),
                initialModel = model,
                inferenceConfig = inference,
                conversationTurnSettingsProvider = { turnSettings },
                sessionStore = chatStore,
            )

        chat.onPromptChanged("What changed?")
        chat.submitPrompt()
        waitUntil { !chat.uiState.value.isGenerating }

        val voiceStore = InMemoryChatSessionStore()
        val captureFactory = RecordingCaptureFactory()
        val speech = CompletingSpeechQueueFactory()
        val voice =
            VoiceChatViewModel(
                engine = ScriptedWebSearchEngine(events),
                systemPrompt = "Test",
                conversationTurnSettingsProvider = { turnSettings },
                preferences = InMemoryVoiceChatPreferences(VoiceChatSettings(minimumWords = 1)),
                captureFactory = captureFactory::create,
                speechQueueFactory = speech::create,
                sessionStore = voiceStore,
            )
        voice.onModelStartupState(ModelStartupState.Available(model, inference))
        voice.onEnteringVoiceChat()
        waitUntil { voice.state.value.canStart }
        voice.start()
        waitUntil { captureFactory.latest?.started == true }
        captureFactory.latest!!.emit(audioTurn())
        waitUntil { voice.state.value.phase == VoiceChatPhase.Listening && voice.state.value.responsePreview.isNotBlank() }

        val chatAssistant = chatStore.assistantText()
        val voiceAssistant = voiceStore.assistantText()
        assertEquals(chatAssistant.text, voiceAssistant.text)
        assertEquals(listOf(source), chatAssistant.sources)
        assertEquals(chatAssistant.sources, voiceAssistant.sources)
        assertEquals(listOf("Final answer from focused evidence."), speech.segments)
        assertFalse(chat.uiState.value.toolInProgress)
        assertFalse(voice.state.value.toolInProgress)
        assertEquals(null, chat.uiState.value.activeToolName)
        assertEquals(null, voice.state.value.activeToolName)
        voice.stop()
    }

    @Test
    fun cancellingFakeProviderTurnClearsResearchStateInChatAndVoice() {
        val chatEngine = CancellableWebSearchEngine()
        val chat =
            ChatViewModel(
                engine = chatEngine,
                initialModel = model,
                inferenceConfig = inference,
                conversationTurnSettingsProvider = { turnSettings },
            )
        chat.onPromptChanged("Search indefinitely")
        chat.submitPrompt()
        waitUntil { chat.uiState.value.toolInProgress }
        chat.cancelGeneration()
        waitUntil { chatEngine.cancellations.get() == 1 }

        val voiceEngine = CancellableWebSearchEngine()
        val captureFactory = RecordingCaptureFactory()
        val voice =
            VoiceChatViewModel(
                engine = voiceEngine,
                systemPrompt = "Test",
                conversationTurnSettingsProvider = { turnSettings },
                preferences = InMemoryVoiceChatPreferences(),
                captureFactory = captureFactory::create,
                speechQueueFactory = CompletingSpeechQueueFactory()::create,
                sessionStore = InMemoryChatSessionStore(),
            )
        voice.onModelStartupState(ModelStartupState.Available(model, inference))
        voice.onEnteringVoiceChat()
        waitUntil { voice.state.value.canStart }
        voice.start()
        waitUntil { captureFactory.latest?.started == true }
        captureFactory.latest!!.emit(audioTurn())
        waitUntil { voice.state.value.toolInProgress }
        voice.stop()
        waitUntil { voiceEngine.cancellations.get() == 1 }

        assertFalse(chat.uiState.value.toolInProgress)
        assertFalse(voice.state.value.toolInProgress)
        assertEquals(null, chat.uiState.value.activeToolName)
        assertEquals(null, voice.state.value.activeToolName)
        assertEquals(VoiceChatPhase.Idle, voice.state.value.phase)
    }

    private fun audioTurn(): CapturedVoiceTurn {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File.createTempFile("voice-web-search-", ".wav", context.cacheDir).apply { writeBytes(byteArrayOf(1)) }
        return CapturedVoiceTurn(
            prompt = com.jesjobom.ararai.chat.AudioPrompt(file.absolutePath, "audio/wav", "test.wav", 1L),
            speechMillis = 1_000L,
            noiseSuppressionActive = true,
        )
    }

    private fun waitUntil(timeoutMillis: Long = 10_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20L)
        assertTrue("Condition was not met within ${timeoutMillis}ms", condition())
    }
}

private class ScriptedWebSearchEngine(
    private val events: List<GenerationEvent>,
) : LocalLlmEngine {
    override suspend fun load(model: LocalModel, config: InferenceConfig) = Unit
    override fun generate(request: PromptRequest): Flow<GenerationEvent> {
        assertEquals(setOf("web_search"), request.advertisedToolNames)
        return flowOf(*events.toTypedArray())
    }
    override suspend fun unload() = Unit
}

private class CancellableWebSearchEngine : LocalLlmEngine {
    val cancellations = AtomicInteger()
    override suspend fun load(model: LocalModel, config: InferenceConfig) = Unit
    override fun generate(request: PromptRequest): Flow<GenerationEvent> = flow {
        emit(GenerationEvent.ToolStarted("web_search", "Fake Exa"))
        try {
            awaitCancellation()
        } finally {
            cancellations.incrementAndGet()
        }
    }
    override suspend fun unload() = Unit
}

private class RecordingCaptureFactory {
    var latest: RecordingCapture? = null
        private set

    fun create(settings: VoiceChatSettings): VoiceTurnCapture = RecordingCapture().also { latest = it }
}

private class RecordingCapture : VoiceTurnCapture {
    var started = false
        private set
    private var onTurn: ((CapturedVoiceTurn) -> Unit)? = null

    override fun start(onTurn: (CapturedVoiceTurn) -> Unit, onError: (String) -> Unit) {
        started = true
        this.onTurn = onTurn
    }

    fun emit(turn: CapturedVoiceTurn) = checkNotNull(onTurn).invoke(turn)
    override fun cancel() = Unit
    override fun close() = Unit
}

private class CompletingSpeechQueueFactory {
    val segments = mutableListOf<String>()

    fun create(
        onStarted: (IntRange) -> Unit,
        onRange: (IntRange) -> Unit,
        onComplete: () -> Unit,
        onError: (com.jesjobom.ararai.ui.UserMessageKey) -> Unit,
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

private fun InMemoryChatSessionStore.assistantText(): MessageContent.TextPrompt = listSessions()
    .flatMap { getMessages(it.id) }
    .last { it.role == ChatRole.Assistant }
    .content as MessageContent.TextPrompt
