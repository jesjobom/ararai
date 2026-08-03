package com.jesjobom.ararai.ui

import android.app.ActivityManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.jesjobom.ararai.chat.eligibleKnowledgeToolNames
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
import com.jesjobom.ararai.knowledge.redactedProviderError
import com.jesjobom.ararai.model.GenerationPreferences
import com.jesjobom.ararai.model.InMemoryGenerationPreferences
import com.jesjobom.ararai.model.ManagedModelItem
import com.jesjobom.ararai.model.ModelCatalogController
import com.jesjobom.ararai.model.ModelPurpose
import com.jesjobom.ararai.model.ModelStartupState
import com.jesjobom.ararai.model.ModelTask
import com.jesjobom.ararai.model.TemperaturePreset
import com.jesjobom.ararai.model.requireInference
import com.jesjobom.ararai.model.resolve
import com.jesjobom.ararai.model.supportsPurpose
import com.jesjobom.ararai.model.supportsTask
import com.jesjobom.ararai.settings.ThemeMode
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
    instructionPreferences: InstructionPreferences = InMemoryInstructionPreferences(),
    generationPreferences: GenerationPreferences = InMemoryGenerationPreferences(),
    webSearchPreferences: WebSearchPreferences = InMemoryWebSearchPreferences(),
    audioTranscriber: AudioTranscriber,
    chatTextToSpeechServiceFactory: () -> ChatTextToSpeechService,
    chatLanguageIdentifierFactory: () -> ChatLanguageIdentifier,
    systemPrompt: String,
    appVersionLabel: String,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    voiceChatPreferences: VoiceChatPreferences,
    voiceTemporaryDirectory: File,
    openModelManagementRequest: Int = 0,
    liteRtLmCacheDir: String? = null,
    webSearchToolFactory: WebSearchToolFactory = WebSearchToolFactory(),
    localLlmEngineFactory: () -> LocalLlmEngine = {
        LiteRtLmLocalLlmEngine(
            bridge =
            AndroidLiteRtLmBridge(
                cacheDir = liteRtLmCacheDir,
                webSearchKnowledgeToolResolver =
                WebSearchKnowledgeToolResolver {
                    webSearchPreferences.settings.value.orderedEnabledProviders
                        .map { provider ->
                            webSearchToolFactory.create(provider) {
                                webSearchPreferences.token(provider)
                            }
                        }
                        .takeIf(List<*>::isNotEmpty)
                        ?.let(::FallbackKnowledgeTool)
                },
            ),
        )
    },
) {
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val modelCatalogState by modelController.state.collectAsState()
    val instructionSettings by instructionPreferences.settings.collectAsState()
    val generationSettings by generationPreferences.state.collectAsState()
    val webSearchSettings by webSearchPreferences.settings.collectAsState()
    val startupState = modelCatalogState.selectedStartupState
    val modelConfig = modelCatalogState.selectedConfig
    var destination by remember { mutableStateOf(AppDestination.Home) }
    var whisperBenchmarkModelId by remember { mutableStateOf<String?>(null) }
    var webSmokeRunning by remember { mutableStateOf<WebSearchProvider?>(null) }
    var webSmokeResults by remember { mutableStateOf<Map<WebSearchProvider, ToolSmokeTestResult>>(emptyMap()) }
    var webSmokeErrors by remember { mutableStateOf<Map<WebSearchProvider, String>>(emptyMap()) }
    val coroutineScope = rememberCoroutineScope()
    val controllers =
        rememberArarAiAppControllers(
            appContext = appContext,
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
            localLlmEngineFactory = localLlmEngineFactory,
            chatTextToSpeechServiceFactory = chatTextToSpeechServiceFactory,
            chatLanguageIdentifierFactory = chatLanguageIdentifierFactory,
        )
    val chatViewModel = controllers.chat
    val benchmarkViewModel = controllers.benchmark
    val voiceChatViewModel = controllers.voiceChat
    fun returnHome() {
        when (destination) {
            AppDestination.Chat -> chatViewModel.onLeavingChat()
            AppDestination.VoiceChat -> voiceChatViewModel.onLeavingVoiceChat()
            AppDestination.Diagnostics -> benchmarkViewModel.onLeavingBenchmark()
            AppDestination.Home,
            AppDestination.ModelStatus,
            AppDestination.WhisperBenchmark,
            AppDestination.Settings,
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
        )
        AppDestination.VoiceChat -> {
            val voiceState by voiceChatViewModel.state.collectAsState()
            VoiceChatScreen(
                state = voiceState,
                onEnter = voiceChatViewModel::onEnteringVoiceChat,
                onStart = voiceChatViewModel::start,
                onStop = voiceChatViewModel::stop,
                onDismissError = voiceChatViewModel::dismissError,
                onSettings = voiceChatViewModel::updateSettings,
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
            onBack = { returnHome() },
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
                ?.knowledgeToolCapabilities
                ?.supports(com.jesjobom.ararai.chat.WIKIPEDIA_SEARCH_TOOL_NAME) == true,
            webSearchCompatible =
            com.jesjobom.ararai.BuildConfig.EXPERIMENTAL_WEB_SEARCH &&
                (startupState as? ModelStartupState.Available)
                    ?.model
                    ?.knowledgeToolCapabilities
                    ?.supports(com.jesjobom.ararai.chat.WEB_SEARCH_TOOL_NAME) == true,
            onInstructionChange = instructionPreferences::setInstruction,
            onRestoreDefault = instructionPreferences::restoreDefault,
            onWikipediaEnabledChange = instructionPreferences::setWikipediaEnabled,
            onContextTokensChange = { generationPreferences.setContextTokens(modelConfig.id, it) },
            onTemperatureChange = { generationPreferences.setTemperature(modelConfig.id, it) },
            onRestoreGenerationDefaults = { generationPreferences.restoreDefaults(modelConfig.id) },
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
            onBack = { returnHome() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArarAiScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
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
                                contentDescription = "Back",
                            )
                        }
                    }
                },
            )
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
@Suppress("LongParameterList", "LongMethod", "MaxLineLength")
internal fun HomeScreen(
    modelStatus: ModelStatusUiState,
    appVersionLabel: String,
    onOpenChat: () -> Unit,
    onOpenVoiceChat: () -> Unit,
    onOpenModelStatus: () -> Unit,
    onOpenInstructionsTools: () -> Unit = {},
    onOpenSettings: () -> Unit,
) {
    ArarAiScaffold(
        title = "ArarAI",
        subtitle = appVersionLabel,
    ) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Local AI for everyday work",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Use a private on-device model for chat, with model files and runtimes managed locally.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HomeConversationCard(
                title = "Chat",
                detail = "Start a local conversation with the selected model.",
                icon = Icons.AutoMirrored.Filled.Chat,
                onClick = onOpenChat,
            )

            HomeConversationCard(
                title = "Voice Chat",
                detail = "Start a stateless local voice conversation with the selected model.",
                icon = Icons.Filled.Mic,
                onClick = onOpenVoiceChat,
            )

            StatusCard(
                title = "Model Manager",
                value = modelStatus.modelName,
                detail = modelStatus.title,
                tags = modelStatus.capabilities,
                icon = when {
                    modelStatus.progressPercent != null -> Icons.Filled.CloudDownload
                    modelStatus.canRetry -> Icons.Filled.Error
                    modelStatus.title.contains("ready", ignoreCase = true) -> Icons.Filled.CheckCircle
                    else -> Icons.Filled.Storage
                },
                onAction = onOpenModelStatus,
            )

            StatusCard(
                title = "Assistant configuration",
                value = "Customize assistant behavior",
                detail = "Manage instructions, tools, and model generation.",
                icon = Icons.Filled.Bolt,
                onAction = onOpenInstructionsTools,
            )

            StatusCard(
                title = "Settings",
                value = "Appearance and preferences",
                detail = "Choose how ArarAI looks and manage future application options.",
                icon = Icons.Filled.Settings,
                onAction = onOpenSettings,
            )
        }
    }
}

@Composable
@Suppress("LongMethod")
internal fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
) {
    ArarAiScaffold(title = "Settings", onBack = onBack) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = "Theme", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Choose a theme or follow your device setting.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ThemeMode.entries.forEach { mode ->
                Card(
                    onClick = { onThemeModeChange(mode) },
                    modifier = Modifier
                        .testTag("theme-option-${mode.name.lowercase(Locale.ROOT)}")
                        .semantics { selected = themeMode == mode },
                    colors = CardDefaults.cardColors(
                        containerColor = if (themeMode == mode) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(
                            selected = themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                        )
                        Column {
                            Text(
                                text = mode.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = mode.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
internal fun InstructionsAndToolsScreen(
    settings: com.jesjobom.ararai.chat.InstructionSettings,
    generationModel: GenerationModelUiState? = null,
    wikipediaCompatible: Boolean,
    webSearchCompatible: Boolean = false,
    onInstructionChange: (InteractionMode, String) -> Unit,
    onRestoreDefault: (InteractionMode) -> Unit,
    onWikipediaEnabledChange: (Boolean) -> Unit,
    onContextTokensChange: (Int) -> Unit = {},
    onTemperatureChange: (Float) -> Unit = {},
    onRestoreGenerationDefaults: () -> Unit = {},
    webSearchSettings: WebSearchSettings = WebSearchSettings(),
    webSmokeRunning: WebSearchProvider? = null,
    webSmokeResults: Map<WebSearchProvider, ToolSmokeTestResult> = emptyMap(),
    webSmokeErrors: Map<WebSearchProvider, String> = emptyMap(),
    onVerifyWebProvider: (WebSearchProvider, String) -> Unit = { _, _ -> },
    onWebProviderEnabledChange: (WebSearchProvider, Boolean) -> Unit = { _, _ -> },
    onRemoveWebProvider: (WebSearchProvider) -> Unit = {},
    onBack: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    ArarAiScaffold(title = "Assistant configuration", onBack = onBack) { modifier ->
        Column(
            modifier = modifier.padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                listOf("Prompts", "Tools", "Generation").forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label) },
                        modifier = Modifier.testTag("instructions-tools-tab-${label.lowercase()}"),
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
                        webSearchSettings = webSearchSettings,
                        webSearchCompatible = webSearchCompatible,
                        webSmokeRunning = webSmokeRunning,
                        webSmokeResults = webSmokeResults,
                        webSmokeErrors = webSmokeErrors,
                        onVerifyWebProvider = onVerifyWebProvider,
                        onWebProviderEnabledChange = onWebProviderEnabledChange,
                        onRemoveWebProvider = onRemoveWebProvider,
                    )
                    else -> GenerationTab(
                        model = generationModel,
                        onContextTokensChange = onContextTokensChange,
                        onTemperatureChange = onTemperatureChange,
                        onRestoreDefaults = onRestoreGenerationDefaults,
                    )
                }
            }
        }
    }
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
@Suppress("LongMethod", "CyclomaticComplexMethod")
private fun GenerationTab(
    model: GenerationModelUiState?,
    onContextTokensChange: (Int) -> Unit,
    onTemperatureChange: (Float) -> Unit,
    onRestoreDefaults: () -> Unit,
) {
    if (model == null) {
        Text("The selected model does not expose generation settings.")
        return
    }
    Text(model.modelName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Text(
        "Settings are saved separately for each model and apply to future Chat and Voice Chat turns.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text("Context window", style = MaterialTheme.typography.titleMedium)
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
        "Selected: ${model.effectiveContextTokens} tokens. Catalog default: ${model.catalogContextTokens}.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text("Temperature", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TemperaturePreset.entries.forEach { preset ->
            FilterChip(
                selected = model.effectiveTemperature == preset.value,
                onClick = { onTemperatureChange(preset.value) },
                label = { Text(preset.displayName) },
                modifier = Modifier.testTag("generation-temperature-${preset.name.lowercase()}"),
            )
        }
    }
    Text(
        "Selected: ${model.effectiveTemperature}. Catalog default: ${model.catalogTemperature}.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
        onClick = onRestoreDefaults,
        enabled = model.hasOverrides,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Restore catalog defaults")
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Runtime limits", fontWeight = FontWeight.SemiBold)
            Text("Response limit: controlled by model/runtime.")
            Text("Reasoning and the final answer share the total context capacity.")
            Text(if (model.supportsReasoning) "Reasoning supported." else "Reasoning unavailable.")
        }
    }
    Text("Last conversational turn", style = MaterialTheme.typography.titleMedium)
    val metrics = model.metrics
    if (metrics == null) {
        Text("Metrics unavailable.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        LabeledValue("Time to first token", "${metrics.timeToFirstTokenMillis} ms")
        LabeledValue("Prefill tokens", metrics.prefillTokenCount.toString())
        LabeledValue("Prefill speed", String.format(Locale.US, "%.2f tokens/s", metrics.prefillTokensPerSecond))
        LabeledValue("Decode tokens", metrics.decodeTokenCount.toString())
        LabeledValue("Decode speed", String.format(Locale.US, "%.2f tokens/s", metrics.decodeTokensPerSecond))
    }
}

@Composable
private fun InstructionsTab(
    settings: com.jesjobom.ararai.chat.InstructionSettings,
    onInstructionChange: (InteractionMode, String) -> Unit,
    onRestoreDefault: (InteractionMode) -> Unit,
) {
    Text(
        "These instructions customize behavior. ArarAI's application and safety rules remain active.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    InstructionEditor(
        title = "Chat instruction",
        value = settings.chatInstruction,
        tag = "chat-instruction",
        onValueChange = { onInstructionChange(InteractionMode.Chat, it) },
        onRestore = { onRestoreDefault(InteractionMode.Chat) },
    )
    InstructionEditor(
        title = "Voice Chat instruction",
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
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Wikipedia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Uses Wikipedia/MediaWiki for eligible factual searches. " +
                    "Inference and conversation storage remain local.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Use Wikipedia")
                    Text(
                        if (wikipediaCompatible) {
                            "Available for the selected model."
                        } else {
                            "Unavailable for the selected model."
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
    Text("Focused web search", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Text(
        "Experimental providers send the query and retrieval metadata directly from this device. " +
            "Your own provider token and provider terms, retention, quota, and charges apply. " +
            "When both are enabled, Exa runs first and Tavily is used only after a " +
            "controlled provider failure. A fallback can send the same request to both services and " +
            "consume quota from both.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val orderedCards =
        webSearchSettings.orderedEnabledProviders +
            WebSearchProvider.entries.filterNot(webSearchSettings.enabledProviders::contains)
    orderedCards.forEach { provider ->
        WebSearchProviderCard(
            provider = provider,
            configured = webSearchSettings.isConfigured(provider),
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
    var token by remember(provider, configured) { mutableStateOf("") }
    var disclosureAccepted by remember(provider, configured) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(provider.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { uriHandler.openUri(provider.accountUrl) }) {
                Text("Create account / API token ↗")
            }
            Text(
                if (configured) {
                    "Credential configured. The stored token is never displayed again."
                } else {
                    "Enter a user-owned token. It is saved only after a successful verification."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!configured) {
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("${provider.displayName} API token") },
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
                        "I understand that my query and retrieval metadata are sent to " +
                            "${provider.displayName} under my account.",
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
                    Text(if (verifying) "Verifying" else "Verify and enable")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Use for focused web search")
                        Text(
                            when {
                                !compatible -> "Unavailable for the selected model or build."
                                preferred && provider == WebSearchProvider.Exa -> "Enabled as preferred provider."
                                preferred -> "Enabled for focused web search."
                                enabled -> "Enabled as fallback provider."
                                else -> "Configured but disabled."
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
                    Text("Remove credential")
                }
            }
            if (verifying) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            smokeResult?.let { result ->
                Text(
                    if (result.passed) "PASS — ${result.detail}" else "FAIL — ${result.detail}",
                    color =
                    if (result.passed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            smokeError?.let { Text("FAIL — $it", color = MaterialTheme.colorScheme.error) }
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
        TextButton(onClick = onRestore) { Text("Restore default") }
    }
}

private val ThemeMode.displayName: String
    get() = when (this) {
        ThemeMode.System -> "System"
        ThemeMode.Light -> "Light"
        ThemeMode.Dark -> "Dark"
    }

private val ThemeMode.description: String
    get() = when (this) {
        ThemeMode.System -> "Use your device appearance"
        ThemeMode.Light -> "Always use the light appearance"
        ThemeMode.Dark -> "Always use the dark appearance"
    }

@Composable
private fun HomeConversationCard(
    title: String,
    detail: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = icon, contentDescription = null)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun StatusCard(
    title: String,
    value: String,
    detail: String,
    tags: List<String> = emptyList(),
    icon: ImageVector,
    onAction: () -> Unit,
) {
    Card(
        onClick = onAction,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = icon, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CapabilityTags(tags)
        }
    }
}

@Composable
@Suppress("LongMethod")
private fun BenchmarkScreen(
    viewModel: BenchmarkViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    ArarAiScaffold(
        title = "Diagnostics",
        subtitle = "On-demand runtime check",
        onBack = onBack,
    ) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Benchmark is a troubleshooting tool for the selected model. It does not compare models or keep history.",
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
                    text = if (state.isRunning) "Running diagnostic" else "Run diagnostic",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (state.isRunning) {
                OutlinedButton(
                    onClick = viewModel::cancelBenchmark,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancel")
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
            LabeledValue("Status", state.status)
            LabeledValue("Runtime", state.backendLabel)
            LabeledValue("Prompt", state.promptLabel)
            LabeledValue("Context", "${state.contextTokens} tokens")
            LabeledValue("Max output", "${state.maxTokens} tokens")
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
                text = "Diagnostic result",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            LabeledValue("Load", "${result.loadMillis} ms")
            LabeledValue("First token", result.firstTokenMillis?.let { "$it ms" } ?: "n/a")
            LabeledValue("Generation", "${result.generationMillis} ms")
            LabeledValue("Total", "${result.totalMillis} ms")
            LabeledValue("Prefill tokens", result.prefillTokens?.toString() ?: "n/a")
            LabeledValue(
                "Prefill throughput",
                result.prefillTokensPerSecond?.let { "${String.format(Locale.US, "%.2f", it)} tokens/s" } ?: "n/a",
            )
            LabeledValue("Decode tokens", result.decodeTokens?.toString() ?: "n/a")
            LabeledValue(
                "Decode throughput",
                result.decodeTokensPerSecond?.let { "${String.format(Locale.US, "%.2f", it)} tokens/s" } ?: "n/a",
            )
            LabeledValue("Output chars", result.generatedCharacters.toString())
            LabeledValue("Stream chunks", result.streamedChunks.toString())
            LabeledValue(
                label = "Character throughput",
                value = "${String.format(Locale.US, "%.2f", result.charactersPerSecond)} chars/s",
            )
        }
    }
}

@Composable
@Suppress("LongParameterList")
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
) {
    var selectedTab by remember { mutableStateOf(ModelCatalogTab.Chat) }
    ArarAiScaffold(
        title = "Models",
        subtitle = "Local catalog",
        onBack = onBack,
    ) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                ModelCatalogTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) },
                        modifier = Modifier.testTag("models-tab-${tab.name.lowercase(Locale.ROOT)}"),
                    )
                }
            }
            availableMemoryBytes?.let {
                Text(
                    text = "Recommended models fit the ${it.toStorageSize()} currently available memory.",
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
                        text = if (isSelected) "${status.title} - selected" else status.title,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MetadataBadge(item.config.runtime.displayName)
                MetadataBadge(item.config.acceleration.displayName)
                if (isRecommended) {
                    MetadataBadge("Recommended")
                }
                item.config.capabilityLabels().forEach { capability ->
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
                label = if (installedBytes != null) "Installed size" else "Approx. download",
                value = (installedBytes ?: item.config.expectedBytes)?.toStorageSize() ?: "Unknown",
            )
            LabeledValue(
                label = "Recommended free RAM",
                value = item.config.recommendedFreeRamBytes?.toStorageSize() ?: "Not specified",
            )

            Text(
                text = status.detail,
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
                        Text("Use this model")
                    }
                }
                if (isMissing) {
                    Button(onClick = onDownload) {
                        Text("Download")
                    }
                }
                if (isFailed) {
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                }
                if (isDownloading) {
                    OutlinedButton(onClick = onCancelDownload) {
                        Text("Cancel download")
                    }
                }
                if (isAvailable) {
                    Button(onClick = onBenchmark) {
                        Text("Run benchmark")
                    }
                    OutlinedButton(onClick = onRedownload) {
                        Text("Download again")
                    }
                    OutlinedButton(onClick = onDelete) {
                        Text("Delete local file")
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
private fun CapabilityTags(tags: List<String>) {
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
