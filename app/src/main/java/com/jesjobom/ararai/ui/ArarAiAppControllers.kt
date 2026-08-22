package com.jesjobom.ararai.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.jesjobom.ararai.benchmark.BenchmarkViewModel
import com.jesjobom.ararai.chat.AudioTranscriber
import com.jesjobom.ararai.chat.ChatMediaRepository
import com.jesjobom.ararai.chat.ChatPreferences
import com.jesjobom.ararai.chat.ChatSessionStore
import com.jesjobom.ararai.chat.ChatViewModel
import com.jesjobom.ararai.chat.ConversationContextProjector
import com.jesjobom.ararai.chat.ConversationCoordinator
import com.jesjobom.ararai.chat.ConversationSelection
import com.jesjobom.ararai.chat.InstructionPreferences
import com.jesjobom.ararai.chat.InteractionMode
import com.jesjobom.ararai.chat.conversationTurnSettings
import com.jesjobom.ararai.chat.eligibleToolNames
import com.jesjobom.ararai.engine.AppLocalLlmRuntime
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.knowledge.WebSearchPreferences
import com.jesjobom.ararai.model.GenerationPreferences
import com.jesjobom.ararai.model.ModelCatalogController
import com.jesjobom.ararai.model.ModelStartupState
import com.jesjobom.ararai.model.requireInference
import com.jesjobom.ararai.model.resolve
import com.jesjobom.ararai.voice.AndroidVoiceTurnCapture
import com.jesjobom.ararai.voice.SequentialVoiceSpeechQueue
import com.jesjobom.ararai.voice.VoiceChatPreferences
import com.jesjobom.ararai.voice.VoiceChatViewModel
import kotlinx.coroutines.Dispatchers
import java.io.Closeable
import java.io.File
import java.text.DateFormat
import java.util.Date

internal data class ArarAiAppControllers(
    val runtime: AppLocalLlmRuntime,
    val chat: ChatViewModel,
    val benchmark: BenchmarkViewModel,
    val voiceChat: VoiceChatViewModel,
) : Closeable {
    override fun close() {
        chat.close()
        voiceChat.close()
        benchmark.onLeavingBenchmark()
        runtime.close()
    }
}

@Composable
@Suppress("LongParameterList", "LongMethod")
internal fun rememberArarAiAppControllers(
    appContext: Context,
    modelController: ModelCatalogController,
    startupState: ModelStartupState,
    chatSessionStore: ChatSessionStore,
    chatMediaRepository: ChatMediaRepository,
    chatPreferences: ChatPreferences,
    instructionPreferences: InstructionPreferences,
    generationPreferences: GenerationPreferences,
    webSearchPreferences: WebSearchPreferences,
    audioTranscriber: AudioTranscriber,
    voiceChatPreferences: VoiceChatPreferences,
    voiceTemporaryDirectory: File,
    systemPrompt: String,
    localLlmEngineFactory: () -> LocalLlmEngine,
    chatTextToSpeechServiceFactory: () -> ChatTextToSpeechService,
    chatLanguageIdentifierFactory: () -> ChatLanguageIdentifier,
): ArarAiAppControllers {
    val controllers = remember {
        val runtime = AppLocalLlmRuntime(engineFactory = localLlmEngineFactory)
        val conversationSelection = ConversationSelection()
        val conversationCoordinator =
            ConversationCoordinator(
                sessionStore = chatSessionStore,
                contextProjector = ConversationContextProjector(systemPrompt),
            )
        val modelConfig = modelController.state.value.selectedConfig
        val availableState = startupState as? ModelStartupState.Available
        val chat =
            ChatViewModel(
                engine = runtime.engine,
                initialModel = availableState?.model,
                inferenceConfig = availableState?.inference ?: modelConfig.requireInference(),
                systemPrompt = systemPrompt,
                conversationTurnSettingsProvider = { activeModel ->
                    val settings = instructionPreferences.settings.value
                    conversationTurnSettings(
                        settings,
                        InteractionMode.Chat,
                        eligibleToolNames(
                            settings,
                            activeModel,
                            webSearchPreferences.settings.value.preferredProvider,
                            com.jesjobom.ararai.BuildConfig.EXPERIMENTAL_WEB_SEARCH,
                        ),
                        webSearchProvider = webSearchPreferences.settings.value.preferredProvider,
                        webSearchFallbackProvider =
                        webSearchPreferences.settings.value.orderedEnabledProviders.getOrNull(1),
                    )
                },
                generationConfigProvider = { activeModel, catalog ->
                    generationPreferences.state.value.resolve(activeModel.id, catalog)
                },
                generationMetricsConsumer = { activeModel, metrics ->
                    generationPreferences.recordMetrics(activeModel.id, metrics)
                },
                sessionStore = chatSessionStore,
                mediaRepository = chatMediaRepository,
                preferences = chatPreferences,
                audioTranscriber = audioTranscriber,
                audioSessionTitleProvider = {
                    appContext.getString(
                        com.jesjobom.ararai.R.string.audio_message_session_fallback_title,
                        DateFormat
                            .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
                            .format(Date()),
                    )
                },
                conversationSelection = conversationSelection,
                conversationCoordinator = conversationCoordinator,
                persistenceDispatcher = Dispatchers.IO,
            )
        val benchmark =
            BenchmarkViewModel(
                engine = runtime.engine,
                initialConfig = modelConfig,
                initialState = startupState,
            )
        val voiceChat =
            VoiceChatViewModel(
                engine = runtime.engine,
                systemPrompt = systemPrompt,
                conversationTurnSettingsProvider = { activeModel ->
                    val settings = instructionPreferences.settings.value
                    conversationTurnSettings(
                        settings,
                        InteractionMode.Voice,
                        eligibleToolNames(
                            settings,
                            activeModel,
                            webSearchPreferences.settings.value.preferredProvider,
                            com.jesjobom.ararai.BuildConfig.EXPERIMENTAL_WEB_SEARCH,
                        ),
                        webSearchProvider = webSearchPreferences.settings.value.preferredProvider,
                        webSearchFallbackProvider =
                        webSearchPreferences.settings.value.orderedEnabledProviders.getOrNull(1),
                    )
                },
                generationConfigProvider = { activeModel, catalog ->
                    generationPreferences.state.value.resolve(activeModel.id, catalog)
                },
                generationMetricsConsumer = { activeModel, metrics ->
                    generationPreferences.recordMetrics(activeModel.id, metrics)
                },
                preferences = voiceChatPreferences,
                captureFactory = { settings ->
                    AndroidVoiceTurnCapture(appContext, voiceTemporaryDirectory, settings)
                },
                speechQueueFactory = { onStarted, onRange, onComplete, onError ->
                    SequentialVoiceSpeechQueue(
                        speech = chatTextToSpeechServiceFactory(),
                        languageIdentifier = chatLanguageIdentifierFactory(),
                        speechRate = { voiceChatPreferences.settings.value.speechRateMultiplier },
                        onSpeechStarted = onStarted,
                        onSpeechRange = onRange,
                        onQueueComplete = onComplete,
                        onError = onError,
                    )
                },
                sessionStore = chatSessionStore,
                mediaRepository = chatMediaRepository,
                audioTranscriber = audioTranscriber,
                voiceSessionTitleProvider = {
                    appContext.getString(
                        com.jesjobom.ararai.R.string.voice_session_fallback_title,
                        DateFormat
                            .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
                            .format(Date()),
                    )
                },
                conversationSelection = conversationSelection,
                conversationCoordinator = conversationCoordinator,
                persistenceDispatcher = Dispatchers.IO,
            )
        ArarAiAppControllers(runtime, chat, benchmark, voiceChat)
    }
    DisposableEffect(controllers) {
        onDispose(controllers::close)
    }
    return controllers
}
