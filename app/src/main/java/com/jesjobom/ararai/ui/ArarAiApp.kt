package com.jesjobom.ararai.ui

import android.app.ActivityManager
import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.R
import com.jesjobom.ararai.benchmark.BenchmarkResult
import com.jesjobom.ararai.benchmark.BenchmarkUiState
import com.jesjobom.ararai.benchmark.BenchmarkViewModel
import com.jesjobom.ararai.chat.AudioTranscriber
import com.jesjobom.ararai.chat.ChatMediaRepository
import com.jesjobom.ararai.chat.ChatPreferences
import com.jesjobom.ararai.chat.ChatSessionStore
import com.jesjobom.ararai.chat.ChatViewModel
import com.jesjobom.ararai.chat.ConversationContextProjector
import com.jesjobom.ararai.chat.ConversationCoordinator
import com.jesjobom.ararai.chat.ConversationSelection
import com.jesjobom.ararai.chat.InMemoryInstructionPreferences
import com.jesjobom.ararai.chat.InstructionPreferences
import com.jesjobom.ararai.chat.InteractionMode
import com.jesjobom.ararai.chat.conversationTurnSettings
import com.jesjobom.ararai.engine.AndroidLiteRtLmBridge
import com.jesjobom.ararai.engine.AppLocalLlmRuntime
import com.jesjobom.ararai.engine.LiteRtLmLocalLlmEngine
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.WebSearchKnowledgeToolResolver
import com.jesjobom.ararai.knowledge.FallbackKnowledgeTool
import com.jesjobom.ararai.knowledge.InMemoryWebSearchPreferences
import com.jesjobom.ararai.knowledge.ToolSmokeTestResult
import com.jesjobom.ararai.knowledge.WebSearchPreferences
import com.jesjobom.ararai.knowledge.WebSearchProvider
import com.jesjobom.ararai.knowledge.WebSearchSettings
import com.jesjobom.ararai.knowledge.WebSearchSmokeTest
import com.jesjobom.ararai.knowledge.WebSearchToolFactory
import com.jesjobom.ararai.knowledge.WikipediaKnowledgeTool
import com.jesjobom.ararai.knowledge.redactedProviderError
import com.jesjobom.ararai.math.EvalExLocalMathEngine
import com.jesjobom.ararai.model.GenerationPreferences
import com.jesjobom.ararai.model.InMemoryGenerationPreferences
import com.jesjobom.ararai.model.InMemoryModelDownloadPromptPreferenceStore
import com.jesjobom.ararai.model.ManagedModelItem
import com.jesjobom.ararai.model.ModelCatalogController
import com.jesjobom.ararai.model.ModelDownloadPromptPreferenceStore
import com.jesjobom.ararai.model.ModelPurpose
import com.jesjobom.ararai.model.ModelStartupState
import com.jesjobom.ararai.model.ModelTask
import com.jesjobom.ararai.model.TemperaturePreset
import com.jesjobom.ararai.model.requireInference
import com.jesjobom.ararai.model.resolve
import com.jesjobom.ararai.model.supportsPurpose
import com.jesjobom.ararai.model.supportsTask
import com.jesjobom.ararai.reporting.DiagnosticErrorReportCoordinator
import com.jesjobom.ararai.reporting.DiagnosticErrorReportState
import com.jesjobom.ararai.reporting.GeneratedContentReportingController
import com.jesjobom.ararai.reporting.PendingReportQueue
import com.jesjobom.ararai.reporting.ReportDeliveryReceiptStore
import com.jesjobom.ararai.reporting.ReportDeliveryScheduler
import com.jesjobom.ararai.reporting.ReportTechnicalMetadata
import com.jesjobom.ararai.settings.ApplicationLanguage
import com.jesjobom.ararai.settings.InMemoryTranscriptionLanguagePreferences
import com.jesjobom.ararai.settings.ThemeMode
import com.jesjobom.ararai.settings.TranscriptionLanguage
import com.jesjobom.ararai.settings.TranscriptionLanguagePreferences
import com.jesjobom.ararai.tools.ApplicationToolDispatcher
import com.jesjobom.ararai.tools.defaultApplicationToolRegistry
import com.jesjobom.ararai.ui.tour.ScreenTour
import com.jesjobom.ararai.ui.tour.TourOverlay
import com.jesjobom.ararai.ui.tour.TourPreferenceStore
import com.jesjobom.ararai.ui.tour.TourStep
import com.jesjobom.ararai.ui.tour.rememberTourAnchorRegistry
import com.jesjobom.ararai.ui.tour.tourAnchor
import com.jesjobom.ararai.voice.AndroidVoiceTurnCapture
import com.jesjobom.ararai.voice.SequentialVoiceSpeechQueue
import com.jesjobom.ararai.voice.VoiceChatPreferences
import com.jesjobom.ararai.voice.VoiceChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private enum class AppDestination {
    Home,
    Chat,
    VoiceChat,
    Diagnostics,
    ModelStatus,
    WhisperBenchmark,
    Settings,
    OpenSourceLicenses,
    InstructionsTools,
}

@Composable
@Suppress(
    "LongParameterList",
    "LongMethod",
    "CyclomaticComplexMethod",
    "MaxLineLength",
    "TooGenericExceptionCaught",
)
internal fun ArarAiApp(
    modelController: ModelCatalogController,
    chatSessionStore: ChatSessionStore,
    chatMediaRepository: ChatMediaRepository,
    chatMediaServices: ChatMediaServices,
    chatPreferences: ChatPreferences,
    pendingReportQueue: PendingReportQueue,
    reportDeliveryReceiptStore: ReportDeliveryReceiptStore,
    reportDeliveryScheduler: ReportDeliveryScheduler = ReportDeliveryScheduler { },
    diagnosticErrorReportCoordinator: DiagnosticErrorReportCoordinator? = null,
    instructionPreferences: InstructionPreferences = InMemoryInstructionPreferences(),
    generationPreferences: GenerationPreferences = InMemoryGenerationPreferences(),
    modelDownloadPromptPreferenceStore: ModelDownloadPromptPreferenceStore =
        InMemoryModelDownloadPromptPreferenceStore(),
    transcriptionLanguagePreferences: TranscriptionLanguagePreferences =
        InMemoryTranscriptionLanguagePreferences(),
    webSearchPreferences: WebSearchPreferences = InMemoryWebSearchPreferences(),
    audioTranscriber: AudioTranscriber,
    chatTextToSpeechServiceFactory: () -> ChatTextToSpeechService,
    chatLanguageIdentifierFactory: () -> ChatLanguageIdentifier,
    systemPrompt: String,
    appVersionLabel: String,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    applicationLanguage: ApplicationLanguage = ApplicationLanguage.System,
    appliedApplicationLanguage: ApplicationLanguage = applicationLanguage,
    onApplicationLanguageChange: (ApplicationLanguage) -> Unit = {},
    onRestartApplication: () -> Unit = {},
    shouldConfirmExit: Boolean = true,
    onDisableExitConfirmation: () -> Unit = {},
    onExitApplication: () -> Unit = {},
    voiceChatPreferences: VoiceChatPreferences,
    tourPreferenceStore: TourPreferenceStore? = null,
    voiceTemporaryDirectory: File,
    openModelManagementRequest: Int = 0,
    liteRtLmCacheDir: String? = null,
    webSearchToolFactory: WebSearchToolFactory = WebSearchToolFactory(),
    localLlmEngineFactory: (() -> LocalLlmEngine)? = null,
) {
    val resourceContext = androidx.compose.ui.platform.LocalContext.current
    val appContext = resourceContext.applicationContext
    val wikipediaTool = remember { WikipediaKnowledgeTool() }
    val calculatorEngine = remember { EvalExLocalMathEngine() }
    val webSearchResolver = remember(webSearchPreferences, webSearchToolFactory) {
        WebSearchKnowledgeToolResolver {
            webSearchPreferences.settings.value.orderedEnabledProviders
                .map { provider ->
                    webSearchToolFactory.create(provider) {
                        webSearchPreferences.token(provider)
                    }
                }
                .takeIf(List<*>::isNotEmpty)
                ?.let(::FallbackKnowledgeTool)
        }
    }
    val applicationToolRegistry = remember(
        instructionPreferences,
        webSearchPreferences,
        wikipediaTool,
        calculatorEngine,
        webSearchResolver,
    ) {
        defaultApplicationToolRegistry(
            instructionPreferences = instructionPreferences,
            webSearchPreferences = webSearchPreferences,
            wikipediaTool = wikipediaTool,
            webSearchTool = webSearchResolver::resolve,
            calculatorEngine = calculatorEngine,
            experimentalWebSearchEnabled = com.jesjobom.ararai.BuildConfig.EXPERIMENTAL_WEB_SEARCH,
        )
    }
    val applicationToolDispatcher = remember(applicationToolRegistry) {
        ApplicationToolDispatcher(applicationToolRegistry)
    }
    val effectiveLocalLlmEngineFactory = remember(
        localLlmEngineFactory,
        applicationToolDispatcher,
        liteRtLmCacheDir,
        wikipediaTool,
        webSearchResolver,
        calculatorEngine,
    ) {
        localLlmEngineFactory ?: {
            LiteRtLmLocalLlmEngine(
                bridge =
                AndroidLiteRtLmBridge(
                    cacheDir = liteRtLmCacheDir,
                    wikipediaKnowledgeTool = wikipediaTool,
                    webSearchKnowledgeToolResolver = webSearchResolver,
                    calculatorEngine = calculatorEngine,
                    applicationToolDispatcher = applicationToolDispatcher,
                    webSearchDisplayNameProvider = {
                        webSearchPreferences.settings.value.preferredProvider?.displayName
                            ?: "Web search"
                    },
                ),
            )
        }
    }
    val modelCatalogState by modelController.state.collectAsState()
    val instructionSettings by instructionPreferences.settings.collectAsState()
    val generationSettings by generationPreferences.state.collectAsState()
    val transcriptionLanguage by transcriptionLanguagePreferences.language.collectAsState()
    val webSearchSettings by webSearchPreferences.settings.collectAsState()
    val startupState = modelCatalogState.selectedStartupState
    val modelConfig = modelCatalogState.selectedConfig
    val defaultModelConfig = modelController.defaultModelConfig
    val hasAvailableChatModel = modelCatalogState.models.hasAvailableChatModel()
    val hasAvailableTranscriptionModel =
        modelCatalogState.models.any {
            it.config.supportsTask(ModelTask.Transcription) && it.state is ModelStartupState.Available
        }
    val reportController = remember(chatSessionStore, pendingReportQueue) {
        GeneratedContentReportingController(
            chatSessionStore,
            pendingReportQueue,
            reportDeliveryScheduler,
        )
    }
    val reportQueueRevision by pendingReportQueue.revision.collectAsState()
    val pendingReports = remember(reportQueueRevision) { reportController.pendingReports() }
    val latestReportReceipt by reportDeliveryReceiptStore.latestReceipt.collectAsState()
    fun reportMetadata() = ReportTechnicalMetadata(
        appVersion = appVersionLabel,
        localeTag = java.util.Locale.getDefault().toLanguageTag(),
        modelId = modelConfig.id,
        runtime = modelConfig.runtime.name,
    )
    var destination by remember { mutableStateOf(AppDestination.Home) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var showInitialModelDialog by remember {
        mutableStateOf(
            shouldShowInitialModelDownloadPrompt(
                wasHandled = modelDownloadPromptPreferenceStore.wasHandled,
                hasAvailableChatModel = hasAvailableChatModel,
            ),
        )
    }
    var disableExitConfirmation by remember { mutableStateOf(false) }
    var whisperBenchmarkModelId by remember { mutableStateOf<String?>(null) }
    var webSmokeRunning by remember { mutableStateOf<WebSearchProvider?>(null) }
    var webSmokeResults by remember { mutableStateOf<Map<WebSearchProvider, ToolSmokeTestResult>>(emptyMap()) }
    var webSmokeErrors by remember { mutableStateOf<Map<WebSearchProvider, String>>(emptyMap()) }
    val coroutineScope = rememberCoroutineScope()

    fun handleInitialModelPrompt() {
        modelDownloadPromptPreferenceStore.markHandled()
        showInitialModelDialog = false
    }
    val controllers =
        rememberArarAiAppControllers(
            appContext = appContext,
            resourceContext = resourceContext,
            modelController = modelController,
            startupState = startupState,
            chatSessionStore = chatSessionStore,
            chatMediaRepository = chatMediaRepository,
            chatPreferences = chatPreferences,
            instructionPreferences = instructionPreferences,
            generationPreferences = generationPreferences,
            webSearchPreferences = webSearchPreferences,
            audioTranscriber = audioTranscriber,
            voiceChatPreferences = voiceChatPreferences,
            voiceTemporaryDirectory = voiceTemporaryDirectory,
            systemPrompt = systemPrompt,
            chatTextToSpeechServiceFactory = chatTextToSpeechServiceFactory,
            chatLanguageIdentifierFactory = chatLanguageIdentifierFactory,
            diagnosticErrorReportCoordinator = diagnosticErrorReportCoordinator,
            applicationToolRegistry = applicationToolRegistry,
            localLlmEngineFactory = effectiveLocalLlmEngineFactory,
        )
    val chatViewModel = controllers.chat
    val benchmarkViewModel = controllers.benchmark
    val voiceChatViewModel = controllers.voiceChat
    val diagnosticErrorState by diagnosticErrorReportCoordinator
        ?.state
        ?.collectAsState()
        ?: remember { mutableStateOf(null) }

    diagnosticErrorState?.let { reportState ->
        DiagnosticErrorReportDialog(
            state = reportState,
            onDismiss = { diagnosticErrorReportCoordinator?.dismiss() },
            onSend = {
                coroutineScope.launch { diagnosticErrorReportCoordinator?.submit() }
            },
        )
    }
    fun returnHome() {
        when (destination) {
            AppDestination.Chat -> chatViewModel.onLeavingChat()
            AppDestination.VoiceChat -> voiceChatViewModel.onLeavingVoiceChat()
            AppDestination.Diagnostics -> benchmarkViewModel.onLeavingBenchmark()
            AppDestination.Home,
            AppDestination.ModelStatus,
            AppDestination.WhisperBenchmark,
            AppDestination.Settings,
            AppDestination.OpenSourceLicenses,
            AppDestination.InstructionsTools,
            -> Unit
        }
        destination = AppDestination.Home
    }

    BackHandler(enabled = destination != AppDestination.Home) {
        if (destination == AppDestination.WhisperBenchmark || destination == AppDestination.Diagnostics) {
            benchmarkViewModel.onLeavingBenchmark()
            destination = AppDestination.ModelStatus
        } else {
            returnHome()
        }
    }

    BackHandler(enabled = destination == AppDestination.Home) {
        if (shouldConfirmExit) {
            disableExitConfirmation = false
            showExitConfirmation = true
        } else {
            onExitApplication()
        }
    }

    LaunchedEffect(hasAvailableChatModel) {
        if (hasAvailableChatModel && !modelDownloadPromptPreferenceStore.wasHandled) {
            modelDownloadPromptPreferenceStore.markHandled()
            showInitialModelDialog = false
        }
    }

    if (showInitialModelDialog) {
        val approximateSize = defaultModelConfig.expectedBytes?.let {
            Formatter.formatShortFileSize(appContext, it)
        } ?: stringResource(R.string.model_download_size_unknown)
        InitialModelDownloadDialog(
            modelName = defaultModelConfig.name,
            approximateSize = approximateSize,
            onDownload = {
                handleInitialModelPrompt()
                modelController.download(defaultModelConfig.id)
            },
            onViewModels = {
                handleInitialModelPrompt()
                destination = AppDestination.ModelStatus
            },
            onClose = ::handleInitialModelPrompt,
        )
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text(stringResource(R.string.exit_confirmation_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.exit_confirmation_description))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = disableExitConfirmation,
                            onCheckedChange = { disableExitConfirmation = it },
                        )
                        Text(stringResource(R.string.exit_confirmation_do_not_ask_again))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (disableExitConfirmation) onDisableExitConfirmation()
                        showExitConfirmation = false
                        onExitApplication()
                    },
                ) {
                    Text(stringResource(R.string.action_exit))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    LaunchedEffect(openModelManagementRequest) {
        if (openModelManagementRequest > 0) {
            when (destination) {
                AppDestination.Chat -> chatViewModel.onLeavingChat()
                AppDestination.VoiceChat -> voiceChatViewModel.onLeavingVoiceChat()
                AppDestination.Diagnostics -> benchmarkViewModel.onLeavingBenchmark()
                AppDestination.Home,
                AppDestination.ModelStatus,
                AppDestination.WhisperBenchmark,
                AppDestination.Settings,
                AppDestination.OpenSourceLicenses,
                AppDestination.InstructionsTools,
                -> Unit
            }
            destination = AppDestination.ModelStatus
        }
    }

    LaunchedEffect(startupState) {
        chatViewModel.onModelStartupState(startupState)
        voiceChatViewModel.onModelStartupState(startupState)
    }

    LaunchedEffect(modelConfig, startupState) {
        benchmarkViewModel.onSelectedModelState(modelConfig, startupState)
    }

    when (destination) {
        AppDestination.Home -> HomeScreen(
            modelStatus = ModelStatusUiState.from(modelConfig, startupState),
            appVersionLabel = appVersionLabel,
            onOpenChat = {
                chatViewModel.onEnteringChat()
                destination = AppDestination.Chat
            },
            onOpenVoiceChat = { destination = AppDestination.VoiceChat },
            voiceChatAvailable = hasAvailableChatModel,
            onUnavailableVoiceChat = {
                Toast.makeText(
                    appContext,
                    appContext.getString(R.string.model_required_message),
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onOpenModelStatus = { destination = AppDestination.ModelStatus },
            onOpenInstructionsTools = { destination = AppDestination.InstructionsTools },
            onOpenSettings = { destination = AppDestination.Settings },
        )
        AppDestination.Chat -> ChatScreen(
            viewModel = chatViewModel,
            mediaServices = chatMediaServices,
            textToSpeechServiceFactory = chatTextToSpeechServiceFactory,
            languageIdentifierFactory = chatLanguageIdentifierFactory,
            onBack = { returnHome() },
            onRetryModelDownload = { modelController.retry(modelCatalogState.selectedModelId) },
            onReportResponse = { messageId ->
                chatViewModel.uiState.value.selectedSessionId?.let { sessionId ->
                    reportController.draftFor(sessionId, messageId, reportMetadata())
                }
            },
            onReportLatestResponse = {
                chatViewModel.uiState.value.selectedSessionId?.let { sessionId ->
                    reportController.latestDraft(sessionId, reportMetadata())
                }
            },
            onSubmitReport = { draft, reason, comment, contextIds ->
                reportController.submit(draft, reason, comment, contextIds)
            },
            pendingReports = pendingReports,
            latestReportReceipt = latestReportReceipt,
            onDeletePendingReport = { reportId ->
                reportController.deletePending(reportId)
            },
            tourPreferenceStore = tourPreferenceStore,
            transcriptionAvailable = hasAvailableTranscriptionModel,
        )
        AppDestination.VoiceChat -> {
            val voiceState by voiceChatViewModel.state.collectAsState()
            VoiceChatScreen(
                state = voiceState,
                mediaServices = chatMediaServices,
                onEnter = voiceChatViewModel::onEnteringVoiceChat,
                onStart = voiceChatViewModel::start,
                onStop = voiceChatViewModel::stop,
                onDismissError = voiceChatViewModel::dismissError,
                onSettings = voiceChatViewModel::updateSettings,
                onCameraOpened = voiceChatViewModel::onCameraOpened,
                onCameraPreviewReady = voiceChatViewModel::onCameraPreviewReady,
                onCameraClosed = voiceChatViewModel::onCameraClosed,
                onCapturedImage = voiceChatViewModel::useCapturedImage,
                onRemoveCapturedImage = voiceChatViewModel::removeCapturedImage,
                onCreateSession = voiceChatViewModel::createSession,
                onSelectSession = voiceChatViewModel::selectSession,
                onRenameSession = voiceChatViewModel::renameSession,
                onDeleteSession = voiceChatViewModel::deleteSession,
                onClearAllSessions = voiceChatViewModel::clearAllSessions,
                onOpenModels = {
                    voiceChatViewModel.onLeavingVoiceChat()
                    destination = AppDestination.ModelStatus
                },
                onBack = { returnHome() },
                onReportLatestResponse = {
                    voiceState.selectedSessionId?.let { sessionId ->
                        reportController.latestDraft(sessionId, reportMetadata())
                    }
                },
                onSubmitReport = { draft, reason, comment, contextIds ->
                    reportController.submit(draft, reason, comment, contextIds)
                },
                pendingReports = pendingReports,
                latestReportReceipt = latestReportReceipt,
                onDeletePendingReport = { reportId ->
                    reportController.deletePending(reportId)
                },
                tourPreferenceStore = tourPreferenceStore,
            )
        }
        AppDestination.Diagnostics -> BenchmarkScreen(
            viewModel = benchmarkViewModel,
            onBack = {
                benchmarkViewModel.onLeavingBenchmark()
                destination = AppDestination.ModelStatus
            },
        )
        AppDestination.ModelStatus -> ModelStatusScreen(
            models = modelCatalogState.models,
            selectedModelId = modelCatalogState.selectedModelId,
            availableMemoryBytes = remember(appContext) {
                val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                ActivityManager.MemoryInfo().also(manager::getMemoryInfo).availMem
            },
            onBack = { returnHome() },
            onSelect = modelController::select,
            onDownload = modelController::download,
            onCancelDownload = modelController::cancelDownload,
            onDelete = modelController::delete,
            onRedownload = modelController::redownload,
            onRetry = modelController::retry,
            onBenchmark = { modelId ->
                val item = modelCatalogState.models.first { it.config.id == modelId }
                if (item.config.supportsTask(ModelTask.Transcription)) {
                    whisperBenchmarkModelId = modelId
                    destination = AppDestination.WhisperBenchmark
                } else {
                    benchmarkViewModel.onSelectedModelState(item.config, item.state)
                    destination = AppDestination.Diagnostics
                }
            },
            tourPreferenceStore = tourPreferenceStore,
        )
        AppDestination.WhisperBenchmark -> {
            val item = modelCatalogState.models.firstOrNull { it.config.id == whisperBenchmarkModelId }
            if (item == null) {
                LaunchedEffect(Unit) { destination = AppDestination.ModelStatus }
            } else {
                WhisperCandidateBenchmarkScreen(
                    item = item,
                    temporaryDirectory = File(appContext.cacheDir, "whisper-benchmark"),
                    onBack = { destination = AppDestination.ModelStatus },
                )
            }
        }
        AppDestination.Settings -> SettingsScreen(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            applicationLanguage = applicationLanguage,
            appliedApplicationLanguage = appliedApplicationLanguage,
            onApplicationLanguageChange = onApplicationLanguageChange,
            onRestartApplication = onRestartApplication,
            onOpenSourceLicenses = { destination = AppDestination.OpenSourceLicenses },
            onRestoreTours = tourPreferenceStore?.let { store -> store::restoreAll },
            onBack = { returnHome() },
        )
        AppDestination.OpenSourceLicenses -> OpenSourceLicensesScreen(
            onBack = { destination = AppDestination.Settings },
        )
        AppDestination.InstructionsTools -> InstructionsAndToolsScreen(
            settings = instructionSettings,
            generationModel =
            modelConfig.inference?.let { catalogInference ->
                val effective = generationSettings.resolve(modelConfig.id, catalogInference)
                GenerationModelUiState(
                    modelId = modelConfig.id,
                    modelName = modelConfig.name,
                    catalogContextTokens = catalogInference.contextTokens,
                    catalogTemperature = catalogInference.temperature,
                    effectiveContextTokens = effective.contextTokens,
                    effectiveTemperature = effective.temperature,
                    supportsReasoning = modelConfig.reasoningCapabilities.request,
                    metrics = generationSettings.lastMetricsByModel[modelConfig.id],
                    hasOverrides = generationSettings.overrides.containsKey(modelConfig.id),
                )
            },
            wikipediaCompatible =
            (startupState as? ModelStartupState.Available)
                ?.model
                ?.toolCapabilities
                ?.supports(com.jesjobom.ararai.chat.WIKIPEDIA_SEARCH_TOOL_NAME) == true,
            calculatorCompatible =
            (startupState as? ModelStartupState.Available)?.model?.toolCapabilities
                ?.supports(com.jesjobom.ararai.chat.CALCULATOR_TOOL_NAME) == true,
            webSearchCompatible =
            com.jesjobom.ararai.BuildConfig.EXPERIMENTAL_WEB_SEARCH &&
                (startupState as? ModelStartupState.Available)
                    ?.model
                    ?.toolCapabilities
                    ?.supports(com.jesjobom.ararai.chat.WEB_SEARCH_TOOL_NAME) == true,
            onInstructionChange = instructionPreferences::setInstruction,
            onRestoreDefault = instructionPreferences::restoreDefault,
            onWikipediaEnabledChange = instructionPreferences::setWikipediaEnabled,
            onCalculatorEnabledChange = instructionPreferences::setCalculatorEnabled,
            onContextTokensChange = { generationPreferences.setContextTokens(modelConfig.id, it) },
            onTemperatureChange = { generationPreferences.setTemperature(modelConfig.id, it) },
            onRestoreGenerationDefaults = { generationPreferences.restoreDefaults(modelConfig.id) },
            transcriptionLanguage = transcriptionLanguage,
            onTranscriptionLanguageChange = transcriptionLanguagePreferences::setLanguage,
            webSearchSettings = webSearchSettings,
            webSmokeRunning = webSmokeRunning,
            webSmokeResults = webSmokeResults,
            webSmokeErrors = webSmokeErrors,
            onVerifyWebProvider = { provider, token ->
                if (webSmokeRunning == null) {
                    webSmokeRunning = provider
                    webSmokeResults = webSmokeResults - provider
                    webSmokeErrors = webSmokeErrors - provider
                    coroutineScope.launch {
                        try {
                            val result =
                                WebSearchSmokeTest(
                                    provider,
                                    webSearchToolFactory.create(provider) { token },
                                ).run()
                            webSmokeResults = webSmokeResults + (provider to result)
                            if (result.passed) {
                                webSearchPreferences.saveToken(provider, token)
                                webSearchPreferences.setProviderEnabled(provider, true)
                            }
                        } catch (error: RuntimeException) {
                            webSmokeErrors =
                                webSmokeErrors +
                                (provider to redactedProviderError(error, listOf(token)))
                        } finally {
                            webSmokeRunning = null
                        }
                    }
                }
            },
            onWebProviderEnabledChange = { provider, enabled ->
                webSearchPreferences.setProviderEnabled(provider, enabled)
            },
            onRemoveWebProvider = { provider ->
                webSearchPreferences.removeToken(provider)
                webSmokeResults = webSmokeResults - provider
                webSmokeErrors = webSmokeErrors - provider
            },
            tourPreferenceStore = tourPreferenceStore,
            onBack = { returnHome() },
        )
    }
}

internal fun shouldShowInitialModelDownloadPrompt(
    wasHandled: Boolean,
    hasAvailableChatModel: Boolean,
): Boolean = !wasHandled && !hasAvailableChatModel

internal fun List<ManagedModelItem>.hasAvailableChatModel(): Boolean = any { item ->
    item.config.supportsPurpose(ModelPurpose.Chat) && item.state is ModelStartupState.Available
}

@Composable
internal fun InitialModelDownloadDialog(
    modelName: String,
    approximateSize: String,
    onDownload: () -> Unit,
    onViewModels: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.initial_model_dialog_title)) },
        text = {
            Text(
                stringResource(
                    R.string.initial_model_dialog_description,
                    modelName,
                    approximateSize,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onDownload) {
                Text(stringResource(R.string.initial_model_download_default))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onViewModels) {
                    Text(stringResource(R.string.initial_model_view_models))
                }
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.action_close))
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArarAiScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    showTopBar: Boolean = true,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (showTopBar) {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            subtitle?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back),
                                )
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        content(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        )
    }
}

@Composable
@Suppress("LongMethod")
internal fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    applicationLanguage: ApplicationLanguage = ApplicationLanguage.System,
    appliedApplicationLanguage: ApplicationLanguage = applicationLanguage,
    onApplicationLanguageChange: (ApplicationLanguage) -> Unit = {},
    onRestartApplication: () -> Unit = {},
    onOpenSourceLicenses: () -> Unit = {},
    onRestoreTours: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    var restoreToursConfirmationOpen by remember { mutableStateOf(false) }
    ArarAiScaffold(title = stringResource(R.string.settings_title), onBack = onBack) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_general),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.settings_language_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ApplicationLanguage.entries.forEach { language ->
                SettingsOptionCard(
                    selected = applicationLanguage == language,
                    tag = "language-option-${language.name.lowercase(Locale.ROOT)}",
                    title = language.displayName(),
                    description = language.description(),
                    onClick = { onApplicationLanguageChange(language) },
                )
            }
            if (applicationLanguage != appliedApplicationLanguage) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_language_restart_required),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = onRestartApplication) {
                            Text(stringResource(R.string.settings_language_restart_now))
                        }
                    }
                }
            }
            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.settings_theme_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ThemeMode.entries.forEach { mode ->
                SettingsOptionCard(
                    selected = themeMode == mode,
                    tag = "theme-option-${mode.name.lowercase(Locale.ROOT)}",
                    title = mode.displayName(),
                    description = mode.description(),
                    onClick = { onThemeModeChange(mode) },
                )
            }
            if (onRestoreTours != null) {
                Text(
                    text = stringResource(R.string.settings_tours),
                    style = MaterialTheme.typography.titleMedium,
                )
                ElevatedCard(
                    onClick = { restoreToursConfirmationOpen = true },
                    modifier = Modifier.testTag("restore-tours"),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_restore_tours_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.settings_restore_tours_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.settings_legal),
                style = MaterialTheme.typography.titleMedium,
            )
            ElevatedCard(
                onClick = onOpenSourceLicenses,
                modifier = Modifier.testTag("open-source-licenses"),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.open_source_licenses_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.open_source_licenses_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (restoreToursConfirmationOpen) {
        AlertDialog(
            onDismissRequest = { restoreToursConfirmationOpen = false },
            title = { Text(stringResource(R.string.settings_restore_tours_confirm_title)) },
            text = { Text(stringResource(R.string.settings_restore_tours_confirm_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRestoreTours?.invoke()
                        restoreToursConfirmationOpen = false
                    },
                ) {
                    Text(stringResource(R.string.settings_restore_tours_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { restoreToursConfirmationOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
@Suppress("LongParameterList", "LongMethod")
internal fun InstructionsAndToolsScreen(
    settings: com.jesjobom.ararai.chat.InstructionSettings,
    generationModel: GenerationModelUiState? = null,
    wikipediaCompatible: Boolean,
    calculatorCompatible: Boolean = false,
    webSearchCompatible: Boolean = false,
    onInstructionChange: (InteractionMode, String) -> Unit,
    onRestoreDefault: (InteractionMode) -> Unit,
    onWikipediaEnabledChange: (Boolean) -> Unit,
    onCalculatorEnabledChange: (Boolean) -> Unit = {},
    onContextTokensChange: (Int) -> Unit = {},
    onTemperatureChange: (Float) -> Unit = {},
    onRestoreGenerationDefaults: () -> Unit = {},
    transcriptionLanguage: TranscriptionLanguage = TranscriptionLanguage.Automatic,
    onTranscriptionLanguageChange: (TranscriptionLanguage) -> Unit = {},
    webSearchSettings: WebSearchSettings = WebSearchSettings(),
    webSmokeRunning: WebSearchProvider? = null,
    webSmokeResults: Map<WebSearchProvider, ToolSmokeTestResult> = emptyMap(),
    webSmokeErrors: Map<WebSearchProvider, String> = emptyMap(),
    onVerifyWebProvider: (WebSearchProvider, String) -> Unit = { _, _ -> },
    onWebProviderEnabledChange: (WebSearchProvider, Boolean) -> Unit = { _, _ -> },
    onRemoveWebProvider: (WebSearchProvider) -> Unit = {},
    tourPreferenceStore: TourPreferenceStore? = null,
    onBack: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabScrollState = rememberScrollState()
    val tabScrollScope = rememberCoroutineScope()
    val tourAnchors = rememberTourAnchorRegistry()
    Box(Modifier.fillMaxSize()) {
        ArarAiScaffold(title = stringResource(R.string.assistant_configuration_title), onBack = onBack) { modifier ->
            Column(
                modifier = modifier.padding(vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        scrollState = tabScrollState,
                        edgePadding = 0.dp,
                    ) {
                        listOf(
                            "prompts" to stringResource(R.string.assistant_tab_prompts),
                            "tools" to stringResource(R.string.assistant_tab_tools),
                            "generation" to stringResource(R.string.assistant_tab_generation),
                            "audio" to stringResource(R.string.assistant_tab_audio),
                        ).forEachIndexed { index, (tag, label) ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(text = label, maxLines = 1) },
                                modifier =
                                Modifier
                                    .testTag("instructions-tools-tab-$tag")
                                    .then(
                                        when (tag) {
                                            "tools" -> Modifier.tourAnchor(tourAnchors, "assistant-tools-tab")
                                            "generation" -> Modifier.tourAnchor(tourAnchors, "assistant-generation-tab")
                                            else -> Modifier
                                        },
                                    ),
                            )
                        }
                    }
                    if (tabScrollState.canScrollBackward) {
                        TabScrollButton(
                            forward = false,
                            onClick = {
                                tabScrollScope.launch {
                                    tabScrollState.animateScrollTo(
                                        (tabScrollState.value - tabScrollState.viewportSize * 3 / 4).coerceAtLeast(0),
                                    )
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterStart),
                        )
                    }
                    if (tabScrollState.canScrollForward) {
                        TabScrollButton(
                            forward = true,
                            onClick = {
                                tabScrollScope.launch {
                                    tabScrollState.animateScrollTo(
                                        (tabScrollState.value + tabScrollState.viewportSize * 3 / 4)
                                            .coerceAtMost(tabScrollState.maxValue),
                                    )
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (selectedTab) {
                        0 -> InstructionsTab(
                            settings = settings,
                            onInstructionChange = onInstructionChange,
                            onRestoreDefault = onRestoreDefault,
                        )
                        1 -> ToolsTab(
                            settings = settings,
                            wikipediaCompatible = wikipediaCompatible,
                            onWikipediaEnabledChange = onWikipediaEnabledChange,
                            calculatorCompatible = calculatorCompatible,
                            onCalculatorEnabledChange = onCalculatorEnabledChange,
                            webSearchSettings = webSearchSettings,
                            webSearchCompatible = webSearchCompatible,
                            webSmokeRunning = webSmokeRunning,
                            webSmokeResults = webSmokeResults,
                            webSmokeErrors = webSmokeErrors,
                            onVerifyWebProvider = onVerifyWebProvider,
                            onWebProviderEnabledChange = onWebProviderEnabledChange,
                            onRemoveWebProvider = onRemoveWebProvider,
                        )
                        2 -> GenerationTab(
                            model = generationModel,
                            onContextTokensChange = onContextTokensChange,
                            onTemperatureChange = onTemperatureChange,
                            onRestoreDefaults = onRestoreGenerationDefaults,
                        )
                        else -> AudioTab(
                            transcriptionLanguage = transcriptionLanguage,
                            onTranscriptionLanguageChange = onTranscriptionLanguageChange,
                        )
                    }
                }
            }
        }
        tourPreferenceStore?.let { store ->
            TourOverlay(
                tour = ScreenTour.AssistantConfiguration,
                store = store,
                steps =
                listOf(
                    TourStep(
                        id = "assistant-tools",
                        anchorId = "assistant-tools-tab",
                        title = stringResource(R.string.tour_assistant_tools_title),
                        body = stringResource(R.string.tour_assistant_tools_body),
                        targetDescription = stringResource(R.string.tour_assistant_tools_target),
                    ),
                    TourStep(
                        id = "assistant-context",
                        anchorId = "assistant-generation-tab",
                        title = stringResource(R.string.tour_assistant_context_title),
                        body = stringResource(R.string.tour_assistant_context_body),
                        targetDescription = stringResource(R.string.tour_assistant_generation_target),
                    ),
                    TourStep(
                        id = "assistant-temperature",
                        anchorId = "assistant-generation-tab",
                        title = stringResource(R.string.tour_assistant_temperature_title),
                        body = stringResource(R.string.tour_assistant_temperature_body),
                        targetDescription = stringResource(R.string.tour_assistant_generation_target),
                    ),
                ),
                anchors = tourAnchors,
                progressText = { current, total -> stringResource(R.string.tour_progress, current, total) },
                previousLabel = stringResource(R.string.action_back),
                nextLabel = stringResource(R.string.action_next),
                completeLabel = stringResource(R.string.action_complete),
                closeDescription = stringResource(R.string.tour_close_screen),
            )
        }
    }
}

@Composable
private fun TabScrollButton(
    forward: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val direction = if (forward) "forward" else "back"
    IconButton(
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = 4.dp)
            .size(36.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .testTag("assistant-tabs-scroll-$direction"),
    ) {
        Icon(
            imageVector = if (forward) Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(
                if (forward) R.string.assistant_tabs_more_forward else R.string.assistant_tabs_more_back,
            ),
        )
    }
}

@Composable
private fun AudioTab(
    transcriptionLanguage: TranscriptionLanguage,
    onTranscriptionLanguageChange: (TranscriptionLanguage) -> Unit,
) {
    Text(
        text = stringResource(R.string.audio_transcription_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = stringResource(R.string.audio_transcription_description),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TranscriptionLanguage.entries.forEach { language ->
        SettingsOptionCard(
            selected = transcriptionLanguage == language,
            tag = "transcription-language-${language.name.lowercase(Locale.ROOT)}",
            title = language.localizedDisplayName(),
            description = language.localizedDescription(),
            onClick = { onTranscriptionLanguageChange(language) },
        )
    }
    Text(
        text = stringResource(R.string.audio_transcription_future_only),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal data class GenerationModelUiState(
    val modelId: String,
    val modelName: String,
    val catalogContextTokens: Int,
    val catalogTemperature: Float,
    val effectiveContextTokens: Int,
    val effectiveTemperature: Float,
    val supportsReasoning: Boolean,
    val metrics: com.jesjobom.ararai.engine.GenerationMetrics?,
    val hasOverrides: Boolean,
)

@Composable
private fun TemperaturePreset.localizedDisplayName(): String = when (this) {
    TemperaturePreset.Precise -> stringResource(R.string.generation_precise)
    TemperaturePreset.Balanced -> stringResource(R.string.generation_balanced)
    TemperaturePreset.Creative -> stringResource(R.string.generation_creative)
}

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
private fun GenerationTab(
    model: GenerationModelUiState?,
    onContextTokensChange: (Int) -> Unit,
    onTemperatureChange: (Float) -> Unit,
    onRestoreDefaults: () -> Unit,
) {
    if (model == null) {
        Text(stringResource(R.string.generation_unavailable))
        return
    }
    Text(model.modelName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Text(
        stringResource(R.string.generation_scope_description),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(stringResource(R.string.generation_context_window), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1024, 2048, 4096, 6144, 8192).forEach { tokens ->
            FilterChip(
                selected = model.effectiveContextTokens == tokens,
                onClick = { onContextTokensChange(tokens) },
                label = { Text(tokens.toString()) },
                modifier = Modifier.testTag("generation-context-$tokens"),
            )
        }
    }
    Text(
        stringResource(R.string.generation_context_selection, model.effectiveContextTokens, model.catalogContextTokens),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(stringResource(R.string.generation_temperature), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TemperaturePreset.entries.forEach { preset ->
            FilterChip(
                selected = model.effectiveTemperature == preset.value,
                onClick = { onTemperatureChange(preset.value) },
                label = { Text(preset.localizedDisplayName()) },
                modifier = Modifier.testTag("generation-temperature-${preset.name.lowercase()}"),
            )
        }
    }
    Text(
        stringResource(
            R.string.generation_temperature_selection,
            model.effectiveTemperature.toString(),
            model.catalogTemperature.toString(),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
        onClick = onRestoreDefaults,
        enabled = model.hasOverrides,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.generation_restore_defaults))
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.generation_runtime_limits), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.generation_response_limit))
            Text(stringResource(R.string.generation_shared_capacity))
            Text(
                stringResource(
                    if (model.supportsReasoning) {
                        R.string.generation_reasoning_supported
                    } else {
                        R.string.generation_reasoning_unavailable
                    },
                ),
            )
        }
    }
    Text(stringResource(R.string.generation_last_turn), style = MaterialTheme.typography.titleMedium)
    val metrics = model.metrics
    if (metrics == null) {
        Text(
            stringResource(R.string.generation_metrics_unavailable),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        LabeledValue(
            stringResource(R.string.generation_time_first_token),
            stringResource(R.string.diagnostics_millis, metrics.timeToFirstTokenMillis),
        )
        LabeledValue(stringResource(R.string.diagnostics_prefill_tokens), metrics.prefillTokenCount.toString())
        LabeledValue(
            stringResource(R.string.generation_prefill_speed),
            stringResource(
                R.string.diagnostics_tokens_per_second,
                String.format(Locale.US, "%.2f", metrics.prefillTokensPerSecond),
            ),
        )
        LabeledValue(stringResource(R.string.diagnostics_decode_tokens), metrics.decodeTokenCount.toString())
        LabeledValue(
            stringResource(R.string.generation_decode_speed),
            stringResource(
                R.string.diagnostics_tokens_per_second,
                String.format(Locale.US, "%.2f", metrics.decodeTokensPerSecond),
            ),
        )
    }
}

@Composable
private fun InstructionsTab(
    settings: com.jesjobom.ararai.chat.InstructionSettings,
    onInstructionChange: (InteractionMode, String) -> Unit,
    onRestoreDefault: (InteractionMode) -> Unit,
) {
    Text(
        stringResource(R.string.instructions_description),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    InstructionEditor(
        title = stringResource(R.string.instructions_chat),
        value = settings.chatInstruction,
        tag = "chat-instruction",
        onValueChange = { onInstructionChange(InteractionMode.Chat, it) },
        onRestore = { onRestoreDefault(InteractionMode.Chat) },
    )
    InstructionEditor(
        title = stringResource(R.string.instructions_voice_chat),
        value = settings.voiceInstruction,
        tag = "voice-instruction",
        onValueChange = { onInstructionChange(InteractionMode.Voice, it) },
        onRestore = { onRestoreDefault(InteractionMode.Voice) },
    )
}

@Composable
@Suppress("LongParameterList", "LongMethod")
private fun ToolsTab(
    settings: com.jesjobom.ararai.chat.InstructionSettings,
    wikipediaCompatible: Boolean,
    onWikipediaEnabledChange: (Boolean) -> Unit,
    calculatorCompatible: Boolean,
    onCalculatorEnabledChange: (Boolean) -> Unit,
    webSearchSettings: WebSearchSettings,
    webSearchCompatible: Boolean,
    webSmokeRunning: WebSearchProvider?,
    webSmokeResults: Map<WebSearchProvider, ToolSmokeTestResult>,
    webSmokeErrors: Map<WebSearchProvider, String>,
    onVerifyWebProvider: (WebSearchProvider, String) -> Unit,
    onWebProviderEnabledChange: (WebSearchProvider, Boolean) -> Unit,
    onRemoveWebProvider: (WebSearchProvider) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.tools_local_calculator),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.tools_local_calculator_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tools_use_calculator))
                    Text(
                        stringResource(
                            if (calculatorCompatible) {
                                R.string.tools_available_model
                            } else {
                                R.string.tools_unavailable_model
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = settings.calculatorEnabled,
                    onCheckedChange = onCalculatorEnabledChange,
                    enabled = calculatorCompatible || settings.calculatorEnabled,
                    modifier = Modifier.testTag("calculator-enabled"),
                )
            }
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.wikipedia_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.tools_wikipedia_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tools_use_wikipedia))
                    Text(
                        if (wikipediaCompatible) {
                            stringResource(R.string.tools_available_model)
                        } else {
                            stringResource(R.string.tools_unavailable_model)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.wikipediaEnabled,
                    onCheckedChange = onWikipediaEnabledChange,
                    enabled = wikipediaCompatible || settings.wikipediaEnabled,
                    modifier = Modifier.testTag("wikipedia-enabled"),
                )
            }
        }
    }
    Text(
        stringResource(R.string.tools_web_search),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        stringResource(R.string.tools_web_search_description),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val orderedCards =
        webSearchSettings.orderedEnabledProviders +
            WebSearchProvider.entries.filterNot(webSearchSettings.enabledProviders::contains)
    orderedCards.forEach { provider ->
        WebSearchProviderCard(
            provider = provider,
            configured = webSearchSettings.isConfigured(provider),
            unreadable = webSearchSettings.isUnreadable(provider),
            enabled = webSearchSettings.isEnabled(provider),
            preferred = webSearchSettings.isPreferred(provider),
            compatible = webSearchCompatible,
            verifying = webSmokeRunning == provider,
            anotherVerificationRunning = webSmokeRunning != null && webSmokeRunning != provider,
            smokeResult = webSmokeResults[provider],
            smokeError = webSmokeErrors[provider],
            onVerify = { onVerifyWebProvider(provider, it) },
            onEnabledChange = { onWebProviderEnabledChange(provider, it) },
            onRemove = { onRemoveWebProvider(provider) },
        )
    }
}

@Composable
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
private fun WebSearchProviderCard(
    provider: WebSearchProvider,
    configured: Boolean,
    unreadable: Boolean,
    enabled: Boolean,
    preferred: Boolean,
    compatible: Boolean,
    verifying: Boolean,
    anotherVerificationRunning: Boolean,
    smokeResult: ToolSmokeTestResult?,
    smokeError: String?,
    onVerify: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    var token by remember(provider, configured, unreadable) { mutableStateOf("") }
    var disclosureAccepted by remember(provider, configured, unreadable) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(provider.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { uriHandler.openUri(provider.accountUrl) }) {
                Text(stringResource(R.string.tools_create_token))
            }
            Text(
                when {
                    configured -> stringResource(R.string.tools_credential_configured)
                    unreadable -> stringResource(R.string.tools_credential_unreadable)
                    else -> stringResource(R.string.tools_enter_token)
                },
                style = MaterialTheme.typography.bodySmall,
                color =
                if (unreadable) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (!configured) {
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(R.string.tools_api_token_label, provider.displayName)) },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions =
                    androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                    ),
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("web-provider-token-${provider.name.lowercase()}"),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Checkbox(
                        checked = disclosureAccepted,
                        onCheckedChange = { disclosureAccepted = it },
                        modifier =
                        Modifier.testTag(
                            "web-provider-disclosure-${provider.name.lowercase()}",
                        ),
                    )
                    Text(
                        stringResource(R.string.tools_disclosure, provider.displayName),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = { onVerify(token) },
                    enabled =
                    token.isNotBlank() &&
                        disclosureAccepted &&
                        !verifying &&
                        !anotherVerificationRunning,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("web-provider-verify-${provider.name.lowercase()}"),
                ) {
                    Text(
                        stringResource(
                            if (verifying) R.string.tools_verifying else R.string.tools_verify_enable,
                        ),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.tools_use_web_search))
                        Text(
                            when {
                                !compatible -> stringResource(R.string.tools_unavailable_model_build)
                                preferred && provider == WebSearchProvider.Exa ->
                                    stringResource(R.string.tools_preferred_provider)
                                preferred -> stringResource(R.string.tools_enabled_search)
                                enabled -> stringResource(R.string.tools_fallback_provider)
                                else -> stringResource(R.string.tools_configured_disabled)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = onEnabledChange,
                        enabled = compatible || enabled,
                        modifier = Modifier.testTag("web-provider-enabled-${provider.name.lowercase()}"),
                    )
                }
                OutlinedButton(
                    onClick = onRemove,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("web-provider-remove-${provider.name.lowercase()}"),
                ) {
                    Text(stringResource(R.string.tools_remove_credential))
                }
            }
            if (verifying) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            smokeResult?.let { result ->
                Text(
                    stringResource(
                        if (result.passed) R.string.tools_pass_result else R.string.tools_fail_result,
                        result.detail,
                    ),
                    color =
                    if (result.passed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            smokeError?.let {
                Text(stringResource(R.string.tools_fail_result, it), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private val WebSearchProvider.accountUrl: String
    get() = when (this) {
        WebSearchProvider.Tavily -> "https://app.tavily.com/home"
        WebSearchProvider.Exa -> "https://dashboard.exa.ai/api-keys"
    }

@Composable
private fun InstructionEditor(
    title: String,
    value: String,
    tag: String,
    onValueChange: (String) -> Unit,
    onRestore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().testTag(tag),
            minLines = 4,
            supportingText = { Text("${value.length} / 2000") },
        )
        TextButton(onClick = onRestore) { Text(stringResource(R.string.instructions_restore_default)) }
    }
}

@Composable
private fun SettingsOptionCard(
    selected: Boolean,
    tag: String,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.testTag(tag).semantics { this.selected = selected },
        colors = CardDefaults.cardColors(
            containerColor =
            if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ThemeMode.displayName(): String = when (this) {
    ThemeMode.System -> stringResource(R.string.theme_system)
    ThemeMode.Light -> stringResource(R.string.theme_light)
    ThemeMode.Dark -> stringResource(R.string.theme_dark)
}

@Composable
private fun ThemeMode.description(): String = when (this) {
    ThemeMode.System -> stringResource(R.string.theme_system_description)
    ThemeMode.Light -> stringResource(R.string.theme_light_description)
    ThemeMode.Dark -> stringResource(R.string.theme_dark_description)
}

@Composable
private fun ApplicationLanguage.displayName(): String = when (this) {
    ApplicationLanguage.System -> stringResource(R.string.language_system)
    ApplicationLanguage.English -> stringResource(R.string.language_english)
    ApplicationLanguage.PortugueseBrazil -> stringResource(R.string.language_portuguese_brazil)
}

@Composable
private fun ApplicationLanguage.description(): String = when (this) {
    ApplicationLanguage.System -> stringResource(R.string.language_system_description)
    ApplicationLanguage.English -> stringResource(R.string.language_english_description)
    ApplicationLanguage.PortugueseBrazil -> stringResource(R.string.language_portuguese_brazil_description)
}

@Composable
private fun TranscriptionLanguage.localizedDisplayName(): String = when (this) {
    TranscriptionLanguage.Automatic -> stringResource(R.string.transcription_language_automatic)
    TranscriptionLanguage.System -> stringResource(R.string.transcription_language_system)
    TranscriptionLanguage.Interface -> stringResource(R.string.transcription_language_interface)
    TranscriptionLanguage.English -> stringResource(R.string.transcription_language_english)
    TranscriptionLanguage.Portuguese -> stringResource(R.string.transcription_language_portuguese)
}

@Composable
private fun TranscriptionLanguage.localizedDescription(): String = when (this) {
    TranscriptionLanguage.Automatic -> stringResource(R.string.transcription_language_automatic_description)
    TranscriptionLanguage.System -> stringResource(R.string.transcription_language_system_description)
    TranscriptionLanguage.Interface -> stringResource(R.string.transcription_language_interface_description)
    TranscriptionLanguage.English -> stringResource(R.string.transcription_language_english_description)
    TranscriptionLanguage.Portuguese -> stringResource(R.string.transcription_language_portuguese_description)
}

@Composable
@Suppress("LongMethod")
private fun BenchmarkScreen(
    viewModel: BenchmarkViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    ArarAiScaffold(
        title = stringResource(R.string.diagnostics_title),
        subtitle = stringResource(R.string.diagnostics_subtitle),
        onBack = onBack,
    ) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.diagnostics_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            BenchmarkDetailsCard(state)

            Button(
                onClick = viewModel::runBenchmark,
                enabled = state.canRun && !state.isRunning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                Text(
                    text =
                    stringResource(
                        if (state.isRunning) R.string.diagnostics_running else R.string.diagnostics_run,
                    ),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (state.isRunning) {
                OutlinedButton(
                    onClick = viewModel::cancelBenchmark,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }

            state.error?.let { message ->
                ErrorCard(message)
            }
            state.result?.let { result ->
                BenchmarkResultCard(result)
            }
        }
    }
}

@Composable
private fun BenchmarkDetailsCard(state: BenchmarkUiState) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = state.modelName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            LabeledValue(stringResource(R.string.diagnostics_status), state.status)
            LabeledValue(stringResource(R.string.diagnostics_runtime), state.backendLabel)
            LabeledValue(stringResource(R.string.diagnostics_prompt), state.promptLabel)
            LabeledValue(
                stringResource(R.string.diagnostics_context),
                stringResource(R.string.diagnostics_tokens, state.contextTokens),
            )
            LabeledValue(
                stringResource(R.string.diagnostics_max_output),
                stringResource(R.string.diagnostics_tokens, state.maxTokens),
            )
        }
    }
}

@Composable
private fun BenchmarkResultCard(result: BenchmarkResult) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.diagnostics_result),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            BenchmarkTimingValues(result)
            BenchmarkTokenValues(result)
        }
    }
}

@Composable
private fun BenchmarkTimingValues(result: BenchmarkResult) {
    LabeledValue(
        stringResource(R.string.diagnostics_load),
        stringResource(R.string.diagnostics_millis, result.loadMillis),
    )
    LabeledValue(
        stringResource(R.string.diagnostics_first_token),
        result.firstTokenMillis?.let {
            stringResource(R.string.diagnostics_millis, it)
        } ?: stringResource(R.string.not_available_abbreviation),
    )
    LabeledValue(
        stringResource(R.string.diagnostics_generation),
        stringResource(R.string.diagnostics_millis, result.generationMillis),
    )
    LabeledValue(
        stringResource(R.string.diagnostics_total),
        stringResource(R.string.diagnostics_millis, result.totalMillis),
    )
}

@Composable
private fun BenchmarkTokenValues(result: BenchmarkResult) {
    LabeledValue(
        stringResource(R.string.diagnostics_prefill_tokens),
        result.prefillTokens?.toString()
            ?: stringResource(R.string.not_available_abbreviation),
    )
    LabeledValue(
        stringResource(R.string.diagnostics_prefill_throughput),
        result.prefillTokensPerSecond?.let {
            stringResource(
                R.string.diagnostics_tokens_per_second,
                String.format(Locale.US, "%.2f", it),
            )
        } ?: stringResource(R.string.not_available_abbreviation),
    )
    LabeledValue(
        stringResource(R.string.diagnostics_decode_tokens),
        result.decodeTokens?.toString()
            ?: stringResource(R.string.not_available_abbreviation),
    )
    LabeledValue(
        stringResource(R.string.diagnostics_decode_throughput),
        result.decodeTokensPerSecond?.let {
            stringResource(
                R.string.diagnostics_tokens_per_second,
                String.format(Locale.US, "%.2f", it),
            )
        } ?: stringResource(R.string.not_available_abbreviation),
    )
    LabeledValue(stringResource(R.string.diagnostics_output_chars), result.generatedCharacters.toString())
    LabeledValue(stringResource(R.string.diagnostics_stream_chunks), result.streamedChunks.toString())
    LabeledValue(
        label = stringResource(R.string.diagnostics_character_throughput),
        value = stringResource(
            R.string.diagnostics_chars_per_second,
            String.format(Locale.US, "%.2f", result.charactersPerSecond),
        ),
    )
}

@Composable
@Suppress("LongParameterList", "LongMethod")
internal fun ModelStatusScreen(
    models: List<ManagedModelItem>,
    selectedModelId: String,
    availableMemoryBytes: Long? = null,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRedownload: (String) -> Unit,
    onRetry: (String) -> Unit,
    onBenchmark: (String) -> Unit = {},
    tourPreferenceStore: TourPreferenceStore? = null,
) {
    var selectedTab by remember { mutableStateOf(ModelCatalogTab.Chat) }
    val tourAnchors = rememberTourAnchorRegistry()
    Box(Modifier.fillMaxSize()) {
        ArarAiScaffold(
            title = stringResource(R.string.models_title),
            subtitle = stringResource(R.string.models_subtitle),
            onBack = onBack,
        ) { modifier ->
            Column(
                modifier =
                modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                    ModelCatalogTab.entries.forEach { tab ->
                        val anchorId =
                            if (tab == ModelCatalogTab.Chat) "models-chat-tab" else "models-transcription-tab"
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(tab.label()) },
                            modifier =
                            Modifier
                                .testTag("models-tab-${tab.name.lowercase(Locale.ROOT)}")
                                .tourAnchor(tourAnchors, anchorId),
                        )
                    }
                }
                availableMemoryBytes?.let {
                    Text(
                        text = stringResource(R.string.models_recommendation_description, it.toStorageSize()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                models.forTab(selectedTab).forEach { item ->
                    ModelCard(
                        item = item,
                        isSelected = item.config.id == selectedModelId,
                        onSelect = { onSelect(item.config.id) },
                        onDownload = { onDownload(item.config.id) },
                        onCancelDownload = { onCancelDownload(item.config.id) },
                        onDelete = { onDelete(item.config.id) },
                        onRedownload = { onRedownload(item.config.id) },
                        onRetry = { onRetry(item.config.id) },
                        isRecommended = item.isRecommendedFor(availableMemoryBytes),
                        onBenchmark = { onBenchmark(item.config.id) },
                    )
                }
            }
        }
        tourPreferenceStore?.let { store ->
            TourOverlay(
                tour = ScreenTour.ModelManagement,
                store = store,
                steps =
                listOf(
                    TourStep(
                        id = "models-active",
                        anchorId = "models-chat-tab",
                        title = stringResource(R.string.tour_models_active_title),
                        body = stringResource(R.string.tour_models_active_body),
                        targetDescription = stringResource(R.string.tour_models_chat_target),
                    ),
                    TourStep(
                        id = "models-transcription",
                        anchorId = "models-transcription-tab",
                        title = stringResource(R.string.tour_models_transcription_title),
                        body = stringResource(R.string.tour_models_transcription_body),
                        targetDescription = stringResource(R.string.tour_models_transcription_target),
                    ),
                ),
                anchors = tourAnchors,
                progressText = { current, total -> stringResource(R.string.tour_progress, current, total) },
                previousLabel = stringResource(R.string.action_back),
                nextLabel = stringResource(R.string.action_next),
                completeLabel = stringResource(R.string.action_complete),
                closeDescription = stringResource(R.string.tour_close_screen),
            )
        }
    }
}

@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod", "LongParameterList")
private fun ModelCard(
    item: ManagedModelItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: () -> Unit,
    onRedownload: () -> Unit,
    onRetry: () -> Unit,
    isRecommended: Boolean,
    onBenchmark: () -> Unit,
) {
    val status = ModelStatusUiState.from(item.config, item.state)
    val localizedStatusTitle = status.localizedTitle()
    val localizedStatusDetail = status.localizedDetail()
    val isAvailable = item.state is ModelStartupState.Available
    val isChatModel = item.config.supportsPurpose(ModelPurpose.Chat)
    val isMissing = item.state is ModelStartupState.Missing
    val isDownloading = item.state is ModelStartupState.Downloading
    val isFailed = item.state is ModelStartupState.Failed

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = Icons.Filled.Memory, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = status.modelName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (isSelected) {
                            stringResource(R.string.models_selected_status, localizedStatusTitle)
                        } else {
                            localizedStatusTitle
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MetadataBadge(item.config.runtime.displayName)
                MetadataBadge(
                    stringResource(
                        if (item.config.acceleration == com.jesjobom.ararai.model.ModelAccelerationPolicy.CpuOnly) {
                            R.string.model_acceleration_cpu_only
                        } else {
                            R.string.model_acceleration_gpu_preferred
                        },
                    ),
                )
                if (isRecommended) {
                    MetadataBadge(stringResource(R.string.models_recommended))
                }
                status.localizedCapabilities().forEach { capability ->
                    MetadataBadge(capability)
                }
            }

            val installedBytes = (item.state as? ModelStartupState.Available)
                ?.model
                ?.filePath
                ?.let(::File)
                ?.takeIf(File::isFile)
                ?.length()
                ?.takeIf { it > 0L }
            LabeledValue(
                label = if (installedBytes != null) {
                    stringResource(R.string.models_installed_size)
                } else {
                    stringResource(R.string.models_approximate_download)
                },
                value = (installedBytes ?: item.config.expectedBytes)?.toStorageSize()
                    ?: stringResource(R.string.models_unknown),
            )
            LabeledValue(
                label = stringResource(R.string.models_recommended_ram),
                value = item.config.recommendedFreeRamBytes?.toStorageSize()
                    ?: stringResource(R.string.models_not_specified),
            )

            Text(
                text = localizedStatusDetail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            status.progressPercent?.let { percent ->
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isChatModel && !isSelected && isAvailable) {
                    Button(onClick = onSelect, enabled = !isDownloading) {
                        Text(stringResource(R.string.models_use))
                    }
                }
                if (isMissing) {
                    Button(onClick = onDownload) {
                        Text(stringResource(R.string.models_download))
                    }
                }
                if (isFailed) {
                    Button(onClick = onRetry) {
                        Text(stringResource(R.string.models_retry))
                    }
                }
                if (isDownloading) {
                    OutlinedButton(onClick = onCancelDownload) {
                        Text(stringResource(R.string.models_cancel_download))
                    }
                }
                if (isAvailable) {
                    Button(onClick = onBenchmark) {
                        Text(stringResource(R.string.models_run_benchmark))
                    }
                    OutlinedButton(onClick = onRedownload) {
                        Text(stringResource(R.string.models_download_again))
                    }
                    OutlinedButton(onClick = onDelete) {
                        Text(stringResource(R.string.models_delete_local_file))
                    }
                }
            }
            if (isDownloading) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
internal fun CapabilityTags(tags: List<String>) {
    if (tags.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.forEach { tag ->
            MetadataBadge(tag)
        }
    }
}

@Composable
private fun MetadataBadge(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private fun Long.toStorageSize(): String {
    val gibibyte = 1024.0 * 1024.0 * 1024.0
    val mebibyte = 1024.0 * 1024.0
    return if (this >= gibibyte) {
        String.format(Locale.US, "%.1f GB", this / gibibyte)
    } else {
        String.format(Locale.US, "%.0f MB", this / mebibyte)
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ErrorCard(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Filled.Error, contentDescription = null)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
