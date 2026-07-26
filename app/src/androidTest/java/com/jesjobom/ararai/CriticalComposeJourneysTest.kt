package com.jesjobom.ararai

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.jesjobom.ararai.chat.ChatViewModel
import com.jesjobom.ararai.chat.InMemoryChatSessionStore
import com.jesjobom.ararai.chat.NoOpChatMediaRepository
import com.jesjobom.ararai.engine.FakeLocalLlmEngine
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ManagedModelItem
import com.jesjobom.ararai.model.ModelAccelerationPolicy
import com.jesjobom.ararai.model.ModelArtifactFormat
import com.jesjobom.ararai.model.ModelConfig
import com.jesjobom.ararai.model.ModelPurpose
import com.jesjobom.ararai.model.ModelRuntime
import com.jesjobom.ararai.model.ModelStartupState
import com.jesjobom.ararai.model.ModelTask
import com.jesjobom.ararai.settings.ThemeMode
import com.jesjobom.ararai.ui.ChatAudioPlayerFactory
import com.jesjobom.ararai.ui.ChatAudioRecorderFactory
import com.jesjobom.ararai.ui.ChatDraftCleaner
import com.jesjobom.ararai.ui.ChatImageDecoder
import com.jesjobom.ararai.ui.ChatImageImportService
import com.jesjobom.ararai.ui.ChatLanguageIdentifier
import com.jesjobom.ararai.ui.ChatMediaServices
import com.jesjobom.ararai.ui.ChatScreen
import com.jesjobom.ararai.ui.ChatTextToSpeechListener
import com.jesjobom.ararai.ui.ChatTextToSpeechService
import com.jesjobom.ararai.ui.HomeScreen
import com.jesjobom.ararai.ui.ModelStatusScreen
import com.jesjobom.ararai.ui.ModelStatusUiState
import com.jesjobom.ararai.ui.SettingsScreen
import com.jesjobom.ararai.ui.VoiceChatScreen
import com.jesjobom.ararai.voice.VadProvider
import com.jesjobom.ararai.voice.VoiceChatUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CriticalComposeJourneysTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chatSubmitShowsGenerationControlAndStreamedResult() {
        val store = InMemoryChatSessionStore()
        val model = availableModel()
        val viewModel =
            ChatViewModel(
                engine = FakeLocalLlmEngine(chunks = listOf("local ", "response"), delayMillis = 500),
                initialModel = model,
                inferenceConfig = inference,
                systemPrompt = "Be concise",
                sessionStore = store,
            )
        var chatOpen by mutableStateOf(false)

        composeRule.setContent {
            MaterialTheme {
                if (chatOpen) {
                    ChatScreen(
                        viewModel = viewModel,
                        mediaServices = fakeMediaServices(),
                        textToSpeechServiceFactory = ::NoOpTextToSpeechService,
                        languageIdentifierFactory = ::ImmediateLanguageIdentifier,
                        onBack = { chatOpen = false },
                    )
                } else {
                    HomeScreen(
                        modelStatus =
                        ModelStatusUiState(
                            modelName = model.name,
                            title = "Model ready",
                            detail = model.filePath,
                            capabilities = listOf("Text"),
                            progressPercent = null,
                            canRetry = false,
                        ),
                        appVersionLabel = "test",
                        onOpenChat = { chatOpen = true },
                        onOpenVoiceChat = {},
                        onOpenModelStatus = {},
                        onOpenSettings = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Chat").performClick()
        composeRule.onNodeWithText("Message").performTextInput("Hello")
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.onNodeWithText("Cancel generation").assertIsDisplayed()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("local response").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("local response").assertIsDisplayed()
    }

    @Test
    fun activeGenerationCanBeCancelledThroughTheUi() {
        val viewModel =
            ChatViewModel(
                engine = FakeLocalLlmEngine(chunks = listOf("late"), delayMillis = 30_000),
                initialModel = availableModel(),
                inferenceConfig = inference,
                systemPrompt = "Be concise",
                sessionStore = InMemoryChatSessionStore(),
            )
        composeRule.setContent {
            MaterialTheme {
                ChatScreen(
                    viewModel = viewModel,
                    mediaServices = fakeMediaServices(),
                    textToSpeechServiceFactory = ::NoOpTextToSpeechService,
                    languageIdentifierFactory = ::ImmediateLanguageIdentifier,
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Message").performTextInput("Stop this")
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.onNodeWithText("Cancel generation").performClick()
        composeRule.onNodeWithText("Message").assertIsDisplayed()
    }

    @Test
    fun failedModelRetryInvokesCommandAndShowsTransition() {
        val config = modelConfig()
        var item by mutableStateOf(ManagedModelItem(config, ModelStartupState.Failed("offline")))
        val retryCommands = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                ModelStatusScreen(
                    models = listOf(item),
                    selectedModelId = config.id,
                    onBack = {},
                    onSelect = {},
                    onDownload = {},
                    onCancelDownload = {},
                    onDelete = {},
                    onRedownload = {},
                    onRetry = { modelId ->
                        retryCommands += modelId
                        item = item.copy(state = ModelStartupState.Downloading())
                    },
                )
            }
        }

        composeRule.onNodeWithText("Retry").performClick()
        composeRule.runOnIdle { assertEquals(listOf(config.id), retryCommands) }
        composeRule.onNodeWithText("Cancel download").assertIsDisplayed()
    }

    @Test
    fun modelTabsAndAvailableBenchmarkRouteTheExactModel() {
        val reasoning = modelConfig().copy(id = "reasoning", name = "Reasoning model")
        val transcription = modelConfig().copy(
            id = "transcription",
            name = "Transcription model",
            family = "whisper",
            purposes = setOf(ModelPurpose.Utility),
            tasks = setOf(ModelTask.Transcription),
            inference = null,
        )
        val benchmarkCommands = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                ModelStatusScreen(
                    models = listOf(
                        ManagedModelItem(reasoning, ModelStartupState.Missing),
                        ManagedModelItem(
                            transcription,
                            ModelStartupState.Available(
                                LocalModel(
                                    id = transcription.id,
                                    name = transcription.name,
                                    filePath = "/tmp/transcription.bin",
                                ),
                                inference = null,
                            ),
                        ),
                    ),
                    selectedModelId = reasoning.id,
                    availableMemoryBytes = Long.MAX_VALUE,
                    onBack = {},
                    onSelect = {},
                    onDownload = {},
                    onCancelDownload = {},
                    onDelete = {},
                    onRedownload = {},
                    onRetry = {},
                    onBenchmark = { benchmarkCommands += it },
                )
            }
        }

        composeRule.onNodeWithText("Reasoning model").assertIsDisplayed()
        composeRule.onNodeWithText("Transcription").performClick()
        composeRule.onNodeWithText("Transcription model").assertIsDisplayed()
        composeRule.onNodeWithText("Run benchmark").performClick()
        composeRule.runOnIdle { assertEquals(listOf("transcription"), benchmarkCommands) }
    }

    @Test
    fun sessionCanBeRenamedAndDeletedWithoutCoordinates() {
        val store = InMemoryChatSessionStore()
        val original = store.createSession("Original")
        store.createSession("Keep")
        val viewModel =
            ChatViewModel(
                engine = FakeLocalLlmEngine(),
                initialModel = availableModel(),
                inferenceConfig = inference,
                systemPrompt = "Be concise",
                sessionStore = store,
            )
        viewModel.selectSession(original.id)
        composeRule.setContent {
            MaterialTheme {
                ChatScreen(
                    viewModel = viewModel,
                    mediaServices = fakeMediaServices(),
                    textToSpeechServiceFactory = ::NoOpTextToSpeechService,
                    languageIdentifierFactory = ::ImmediateLanguageIdentifier,
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Sessions").performClick()
        composeRule.onNodeWithTag("chat-session-${original.id}").performTouchInput { longClick() }
        composeRule.onNodeWithText("Title").performTextClearance()
        composeRule.onNodeWithText("Title").performTextInput("Renamed")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Renamed").assertIsDisplayed()

        composeRule.onNodeWithText("Sessions").performClick()
        composeRule.onNodeWithTag("delete-chat-${original.id}").performClick()
        composeRule.onNodeWithText("Renamed").assertDoesNotExist()
        composeRule.onNodeWithText("Keep").assertIsDisplayed()
    }

    @Test
    fun themeSelectionUpdatesVisibleSelectedState() {
        var themeMode by mutableStateOf(ThemeMode.System)
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    themeMode = themeMode,
                    onThemeModeChange = { themeMode = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("theme-option-system").assertIsSelected()
        composeRule.onNodeWithTag("theme-option-dark").assertIsNotSelected().performClick()
        composeRule.onNodeWithTag("theme-option-dark").assertIsSelected()
        composeRule.runOnIdle { assertEquals(ThemeMode.Dark, themeMode) }
    }

    @Test
    fun voiceResponsePreviewOpensTheCompleteResponse() {
        val response = "First spoken line. Second spoken line. Complete response remains available."
        composeRule.setContent {
            MaterialTheme {
                VoiceChatScreen(
                    state =
                    VoiceChatUiState(
                        responsePreview = response,
                        spokenRange = 6..11,
                        readingAnchor = 11,
                    ),
                    onEnter = {},
                    onStart = {},
                    onStop = {},
                    onDismissError = {},
                    onSettings = {},
                    onOpenModels = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("voice-response-preview").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Voice Chat").assertIsDisplayed()
        composeRule.onNodeWithText("Local diagnostics", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Response").assertIsDisplayed()
        composeRule.onNodeWithText(response).assertIsDisplayed()
    }

    @Test
    fun voiceChatSettingsSelectReadingSpeed() {
        var savedSettings: com.jesjobom.ararai.voice.VoiceChatSettings? = null
        composeRule.setContent {
            MaterialTheme {
                VoiceChatScreen(
                    state = VoiceChatUiState(),
                    onEnter = {},
                    onStart = {},
                    onStop = {},
                    onDismissError = {},
                    onSettings = { savedSettings = it },
                    onOpenModels = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Voice Chat settings").performClick()
        composeRule.onNodeWithTag("voice-speech-rate-slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(2.0f) }
        composeRule.onNodeWithText("Silero").performClick()
        composeRule.onNodeWithText("WebRTC").performClick()
        composeRule.onNodeWithText("Save").performClick()

        composeRule.runOnIdle {
            assertEquals(2.0f, savedSettings?.speechRateMultiplier)
            assertEquals(VadProvider.WebRtc, savedSettings?.vadProvider)
        }
    }

    private fun availableModel() = LocalModel(
        id = "test-model",
        name = "Test model",
        filePath = "/test/model.gguf",
    )

    private fun modelConfig() = ModelConfig(
        id = "test-model",
        name = "Test model",
        runtime = ModelRuntime.LlamaCpp,
        artifactFormat = ModelArtifactFormat.Gguf,
        acceleration = ModelAccelerationPolicy.CpuOnly,
        url = "https://example.invalid/model.gguf",
        fileName = "model.gguf",
        relativePath = "models/model.gguf",
        sha256 = "0".repeat(64),
        expectedBytes = 1,
        inference = inference,
    )

    private fun fakeMediaServices() = ChatMediaServices(
        imageImporter = ChatImageImportService { error("unused") },
        audioRecorderFactory = ChatAudioRecorderFactory { error("unused") },
        audioPlayerFactory = ChatAudioPlayerFactory { _, _ -> error("unused") },
        imageDecoder = ChatImageDecoder { _, _ -> null },
        draftCleaner = ChatDraftCleaner {},
    )

    private class NoOpTextToSpeechService : ChatTextToSpeechService {
        override fun speak(
            text: String,
            languageTag: String?,
            speechRate: Float,
            listener: ChatTextToSpeechListener,
        ) = Unit

        override fun stop() = Unit

        override fun close() = Unit
    }

    private class ImmediateLanguageIdentifier : ChatLanguageIdentifier {
        override fun identify(
            text: String,
            listener: com.jesjobom.ararai.ui.ChatLanguageIdentificationListener,
        ) = listener.onIdentified("en")

        override fun close() = Unit
    }

    private companion object {
        val inference =
            InferenceConfig(
                contextTokens = 256,
                maxTokens = 32,
                temperature = 0.2f,
                topP = 0.9f,
            )
    }
}
