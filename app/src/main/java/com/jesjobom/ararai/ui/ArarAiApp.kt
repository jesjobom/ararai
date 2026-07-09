package com.jesjobom.ararai.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.benchmark.BenchmarkResult
import com.jesjobom.ararai.benchmark.BenchmarkUiState
import com.jesjobom.ararai.benchmark.BenchmarkViewModel
import com.jesjobom.ararai.chat.ChatViewModel
import com.jesjobom.ararai.engine.LlamaCppLocalLlmEngine
import com.jesjobom.ararai.model.ManagedModelItem
import com.jesjobom.ararai.model.ModelCatalogController
import com.jesjobom.ararai.model.ModelStartupState
import java.util.Locale

private enum class AppDestination {
    Home,
    Chat,
    Benchmark,
    ModelStatus,
}

@Composable
fun ArarAiApp(
    modelController: ModelCatalogController,
) {
    val modelCatalogState by modelController.state.collectAsState()
    val startupState = modelCatalogState.selectedStartupState
    val modelConfig = modelCatalogState.selectedConfig
    var destination by remember { mutableStateOf(AppDestination.Home) }
    val chatViewModel = remember {
        val availableState = startupState as? ModelStartupState.Available
        ChatViewModel(
            engine = LlamaCppLocalLlmEngine(),
            initialModel = availableState?.model,
            inferenceConfig = availableState?.inference ?: modelConfig.inference,
        )
    }
    val benchmarkViewModel = remember {
        BenchmarkViewModel(
            engine = LlamaCppLocalLlmEngine(),
            initialConfig = modelConfig,
            initialState = startupState,
        )
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
            onOpenChat = { destination = AppDestination.Chat },
            onOpenBenchmark = { destination = AppDestination.Benchmark },
            onOpenModelStatus = { destination = AppDestination.ModelStatus },
        )
        AppDestination.Chat -> ChatScreen(
            viewModel = chatViewModel,
            onBack = {
                chatViewModel.onLeavingChat()
                destination = AppDestination.Home
            },
            onRetryModelDownload = { modelController.retry(modelCatalogState.selectedModelId) },
        )
        AppDestination.Benchmark -> BenchmarkScreen(
            viewModel = benchmarkViewModel,
            onBack = {
                benchmarkViewModel.onLeavingBenchmark()
                destination = AppDestination.Home
            },
        )
        AppDestination.ModelStatus -> ModelStatusScreen(
            models = modelCatalogState.models,
            selectedModelId = modelCatalogState.selectedModelId,
            onBack = { destination = AppDestination.Home },
            onSelect = modelController::select,
            onDownload = modelController::download,
            onDelete = modelController::delete,
            onRedownload = modelController::redownload,
            onRetry = modelController::retry,
        )
    }
}

@Composable
private fun HomeScreen(
    modelStatus: ModelStatusUiState,
    onOpenChat: () -> Unit,
    onOpenBenchmark: () -> Unit,
    onOpenModelStatus: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(
                text = "ArarAI",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Local AI hub",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            shape = MaterialTheme.shapes.small,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = modelStatus.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = modelStatus.modelName,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = modelStatus.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Button(
            onClick = onOpenChat,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Chat")
        }

        OutlinedButton(
            onClick = onOpenBenchmark,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Benchmark")
        }

        OutlinedButton(
            onClick = onOpenModelStatus,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Models")
        }
    }
}

@Composable
private fun BenchmarkScreen(
    viewModel: BenchmarkViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedButton(onClick = onBack) {
            Text("Back")
        }

        Text(
            text = "Benchmark",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        BenchmarkDetailsCard(state)

        Button(
            onClick = viewModel::runBenchmark,
            enabled = state.canRun && !state.isRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isRunning) "Running" else "Run benchmark")
        }

        state.error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        state.result?.let { result ->
            BenchmarkResultCard(result)
        }
    }
}

@Composable
private fun BenchmarkDetailsCard(state: BenchmarkUiState) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = state.modelName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = "Status: ${state.status}")
            Text(text = "Backend: ${state.backendLabel}")
            Text(text = "Prompt: ${state.promptLabel}")
            Text(text = "Context: ${state.contextTokens} tokens")
            Text(text = "Max output: ${state.maxTokens} tokens")
        }
    }
}

@Composable
private fun BenchmarkResultCard(result: BenchmarkResult) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Latest result",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = "Load: ${result.loadMillis} ms")
            Text(text = "First token: ${result.firstTokenMillis?.let { "$it ms" } ?: "n/a"}")
            Text(text = "Generation: ${result.generationMillis} ms")
            Text(text = "Total: ${result.totalMillis} ms")
            Text(text = "Output tokens: ${result.generatedTokens}")
            Text(text = "Output chars: ${result.generatedCharacters}")
            Text(
                text = "Throughput: ${
                    String.format(Locale.US, "%.2f", result.tokensPerSecond)
                } tokens/s",
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
    onDelete: (String) -> Unit,
    onRedownload: (String) -> Unit,
    onRetry: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedButton(onClick = onBack) {
            Text("Back")
        }

        Text(
            text = "Models",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        models.forEach { item ->
            ModelCard(
                item = item,
                isSelected = item.config.id == selectedModelId,
                onSelect = { onSelect(item.config.id) },
                onDownload = { onDownload(item.config.id) },
                onDelete = { onDelete(item.config.id) },
                onRedownload = { onRedownload(item.config.id) },
                onRetry = { onRetry(item.config.id) },
            )
        }
    }
}

@Composable
private fun ModelCard(
    item: ManagedModelItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
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
        ),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = status.modelName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = if (isSelected) "${status.title} - selected" else status.title)
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
                    OutlinedButton(onClick = onSelect, enabled = !isDownloading) {
                        Text("Use")
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
                if (isAvailable) {
                    OutlinedButton(onClick = onRedownload) {
                        Text("Update")
                    }
                    OutlinedButton(onClick = onDelete) {
                        Text("Delete")
                    }
                }
            }
            if (isDownloading) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
