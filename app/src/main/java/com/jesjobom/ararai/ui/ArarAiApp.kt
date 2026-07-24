package com.jesjobom.ararai.ui

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
import androidx.compose.material.icons.filled.Build
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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.jesjobom.ararai.engine.AppLocalLlmRuntime
import com.jesjobom.ararai.engine.ConfiguredLocalLlmEngine
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.model.ManagedModelItem
import com.jesjobom.ararai.model.ModelCatalogController
import com.jesjobom.ararai.model.ModelPurpose
import com.jesjobom.ararai.model.ModelStartupState
import com.jesjobom.ararai.model.ModelTask
import com.jesjobom.ararai.model.requireInference
import com.jesjobom.ararai.model.supportsPurpose
import com.jesjobom.ararai.model.supportsTask
import com.jesjobom.ararai.settings.ThemeMode
import com.jesjobom.ararai.voice.AndroidVoiceTurnCapture
import com.jesjobom.ararai.voice.SequentialVoiceSpeechQueue
import com.jesjobom.ararai.voice.VoiceChatPreferences
import com.jesjobom.ararai.voice.VoiceChatViewModel
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
}

@Composable
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod", "MaxLineLength")
internal fun ArarAiApp(
    modelController: ModelCatalogController,
    chatSessionStore: ChatSessionStore,
    chatMediaRepository: ChatMediaRepository,
    chatMediaServices: ChatMediaServices,
    chatPreferences: ChatPreferences,
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
    localLlmEngineFactory: () -> LocalLlmEngine = {
        ConfiguredLocalLlmEngine(liteRtLmCacheDir = liteRtLmCacheDir)
    },
) {
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val modelCatalogState by modelController.state.collectAsState()
    val startupState = modelCatalogState.selectedStartupState
    val modelConfig = modelCatalogState.selectedConfig
    var destination by remember { mutableStateOf(AppDestination.Home) }
    var whisperBenchmarkModelId by remember { mutableStateOf<String?>(null) }
    val localLlmRuntime = remember(localLlmEngineFactory) {
        AppLocalLlmRuntime(localLlmEngineFactory)
    }
    val chatViewModel = remember {
        val availableState = startupState as? ModelStartupState.Available
        ChatViewModel(
            engine = localLlmRuntime.engine,
            initialModel = availableState?.model,
            inferenceConfig = availableState?.inference ?: modelConfig.requireInference(),
            systemPrompt = systemPrompt,
            sessionStore = chatSessionStore,
            mediaRepository = chatMediaRepository,
            preferences = chatPreferences,
            audioTranscriber = audioTranscriber,
        )
    }
    val benchmarkViewModel = remember {
        BenchmarkViewModel(
            engine = localLlmRuntime.engine,
            initialConfig = modelConfig,
            initialState = startupState,
        )
    }
    val voiceChatViewModel = remember {
        VoiceChatViewModel(
            engine = localLlmRuntime.engine,
            systemPrompt = systemPrompt,
            preferences = voiceChatPreferences,
            captureFactory = { settings -> AndroidVoiceTurnCapture(appContext, voiceTemporaryDirectory, settings) },
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
            -> Unit
        }
        destination = AppDestination.Home
    }

    BackHandler(enabled = destination != AppDestination.Home) {
        if (destination == AppDestination.WhisperBenchmark) {
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
            onOpenChat = { destination = AppDestination.Chat },
            onOpenVoiceChat = { destination = AppDestination.VoiceChat },
            onOpenDiagnostics = { destination = AppDestination.Diagnostics },
            onOpenModelStatus = { destination = AppDestination.ModelStatus },
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
                onOpenModels = {
                    voiceChatViewModel.onLeavingVoiceChat()
                    destination = AppDestination.ModelStatus
                },
                onBack = { returnHome() },
            )
        }
        AppDestination.Diagnostics -> BenchmarkScreen(
            viewModel = benchmarkViewModel,
            onBack = { returnHome() },
        )
        AppDestination.ModelStatus -> ModelStatusScreen(
            models = modelCatalogState.models,
            selectedModelId = modelCatalogState.selectedModelId,
            onBack = { returnHome() },
            onSelect = modelController::select,
            onDownload = modelController::download,
            onCancelDownload = modelController::cancelDownload,
            onDelete = modelController::delete,
            onRedownload = modelController::redownload,
            onRetry = modelController::retry,
            onTestTranscription = { modelId ->
                whisperBenchmarkModelId = modelId
                destination = AppDestination.WhisperBenchmark
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
    onOpenDiagnostics: () -> Unit,
    onOpenModelStatus: () -> Unit,
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
                title = "Diagnostics",
                value = "Selected model runtime check",
                detail = "Run a single benchmark when you need troubleshooting data.",
                icon = Icons.Filled.Build,
                onAction = onOpenDiagnostics,
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
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRedownload: (String) -> Unit,
    onRetry: (String) -> Unit,
    onTestTranscription: (String) -> Unit = {},
) {
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
            models.forEach { item ->
                ModelCard(
                    item = item,
                    isSelected = item.config.id == selectedModelId,
                    onSelect = { onSelect(item.config.id) },
                    onDownload = { onDownload(item.config.id) },
                    onCancelDownload = { onCancelDownload(item.config.id) },
                    onDelete = { onDelete(item.config.id) },
                    onRedownload = { onRedownload(item.config.id) },
                    onRetry = { onRetry(item.config.id) },
                    onTestTranscription = { onTestTranscription(item.config.id) },
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
    onTestTranscription: () -> Unit,
) {
    val status = ModelStatusUiState.from(item.config, item.state)
    val isAvailable = item.state is ModelStartupState.Available
    val isChatModel = item.config.supportsPurpose(ModelPurpose.Chat)
    val isTranscriptionModel = item.config.supportsTask(ModelTask.Transcription)
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
                    if (isTranscriptionModel) {
                        Button(onClick = onTestTranscription) {
                            Text("Test transcription model")
                        }
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
