package com.jesjobom.ararai.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.benchmark.BenchmarkResult
import com.jesjobom.ararai.benchmark.BenchmarkUiState
import com.jesjobom.ararai.benchmark.BenchmarkViewModel
import com.jesjobom.ararai.chat.ChatSessionStore
import com.jesjobom.ararai.chat.ChatViewModel
import com.jesjobom.ararai.engine.ConfiguredLocalLlmEngine
import com.jesjobom.ararai.model.ManagedModelItem
import com.jesjobom.ararai.model.ModelCatalogController
import com.jesjobom.ararai.model.ModelStartupState
import java.util.Locale

private enum class AppDestination {
    Home,
    Chat,
    Diagnostics,
    ModelStatus,
}

@Composable
fun ArarAiApp(
    modelController: ModelCatalogController,
    chatSessionStore: ChatSessionStore,
    systemPrompt: String,
    appVersionLabel: String,
) {
    val modelCatalogState by modelController.state.collectAsState()
    val startupState = modelCatalogState.selectedStartupState
    val modelConfig = modelCatalogState.selectedConfig
    var destination by remember { mutableStateOf(AppDestination.Home) }
    val chatViewModel = remember {
        val availableState = startupState as? ModelStartupState.Available
        ChatViewModel(
            engine = ConfiguredLocalLlmEngine(),
            initialModel = availableState?.model,
            inferenceConfig = availableState?.inference ?: modelConfig.inference,
            systemPrompt = systemPrompt,
            sessionStore = chatSessionStore,
        )
    }
    val benchmarkViewModel = remember {
        BenchmarkViewModel(
            engine = ConfiguredLocalLlmEngine(),
            initialConfig = modelConfig,
            initialState = startupState,
        )
    }
    fun returnHome() {
        when (destination) {
            AppDestination.Chat -> chatViewModel.onLeavingChat()
            AppDestination.Diagnostics -> benchmarkViewModel.onLeavingBenchmark()
            AppDestination.Home, AppDestination.ModelStatus -> Unit
        }
        destination = AppDestination.Home
    }

    BackHandler(enabled = destination != AppDestination.Home) {
        returnHome()
    }

    LaunchedEffect(startupState) {
        chatViewModel.onModelStartupState(startupState)
    }

    LaunchedEffect(modelConfig, startupState) {
        benchmarkViewModel.onSelectedModelState(modelConfig, startupState)
    }

    when (destination) {
        AppDestination.Home -> HomeScreen(
            modelStatus = ModelStatusUiState.from(modelConfig, startupState),
            appVersionLabel = appVersionLabel,
            onOpenChat = { destination = AppDestination.Chat },
            onOpenDiagnostics = { destination = AppDestination.Diagnostics },
            onOpenModelStatus = { destination = AppDestination.ModelStatus },
        )
        AppDestination.Chat -> ChatScreen(
            viewModel = chatViewModel,
            onBack = { returnHome() },
            onRetryModelDownload = { modelController.retry(modelCatalogState.selectedModelId) },
        )
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
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArarAiScaffold(
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
private fun HomeScreen(
    modelStatus: ModelStatusUiState,
    appVersionLabel: String,
    onOpenChat: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenModelStatus: () -> Unit,
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

            ElevatedCard(
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
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                        )
                        Text(
                            text = "Chat",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = "Start a local conversation with the selected model.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = onOpenChat,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                        )
                        Text(
                            text = "Open chat",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            StatusCard(
                title = modelStatus.title,
                value = modelStatus.modelName,
                detail = modelStatus.detail,
                icon = when {
                    modelStatus.progressPercent != null -> Icons.Filled.CloudDownload
                    modelStatus.canRetry -> Icons.Filled.Error
                    modelStatus.title.contains("ready", ignoreCase = true) -> Icons.Filled.CheckCircle
                    else -> Icons.Filled.Storage
                },
                actionLabel = "Manage models",
                onAction = onOpenModelStatus,
            )

            StatusCard(
                title = "Diagnostics",
                value = "Selected model runtime check",
                detail = "Run a single benchmark when you need troubleshooting data.",
                icon = Icons.Filled.Build,
                actionLabel = "Open diagnostics",
                onAction = onOpenDiagnostics,
                secondary = true,
            )
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    value: String,
    detail: String,
    icon: ImageVector,
    actionLabel: String,
    onAction: () -> Unit,
    secondary: Boolean = false,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (secondary) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
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
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
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
            LabeledValue("Output tokens", result.generatedTokens.toString())
            LabeledValue("Output chars", result.generatedCharacters.toString())
            LabeledValue(
                label = "Throughput",
                value = "${String.format(Locale.US, "%.2f", result.tokensPerSecond)} tokens/s",
            )
        }
    }
}

@Composable
private fun ModelStatusScreen(
    models: List<ManagedModelItem>,
    selectedModelId: String,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRedownload: (String) -> Unit,
    onRetry: (String) -> Unit,
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
                )
            }
        }
    }
}

@Composable
private fun ModelCard(
    item: ManagedModelItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: () -> Unit,
    onRedownload: () -> Unit,
    onRetry: () -> Unit,
) {
    val status = ModelStatusUiState.from(item.config, item.state)
    val isAvailable = item.state is ModelStartupState.Available
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text(item.config.runtime.displayName) },
                )
                AssistChip(
                    onClick = {},
                    label = { Text(item.config.acceleration.displayName) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = null,
                        )
                    },
                )
            }

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
                if (!isSelected && isAvailable) {
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
