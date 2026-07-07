package com.jesjobom.ararai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.model.ModelConfig
import com.jesjobom.ararai.model.ModelStartupController
import com.jesjobom.ararai.model.ModelStartupState

private enum class AppDestination {
    Home,
    ModelStatus,
}

@Composable
fun ArarAiApp(
    modelConfig: ModelConfig,
    startupController: ModelStartupController,
) {
    val startupState by startupController.state.collectAsState()
    var destination by remember { mutableStateOf(AppDestination.Home) }

    when (destination) {
        AppDestination.Home -> HomeScreen(
            modelStatus = ModelStatusUiState.from(modelConfig, startupState),
            onOpenModelStatus = { destination = AppDestination.ModelStatus },
        )
        AppDestination.ModelStatus -> ModelStatusScreen(
            status = ModelStatusUiState.from(modelConfig, startupState),
            onBack = { destination = AppDestination.Home },
            onRetry = startupController::retry,
        )
    }
}

@Composable
private fun HomeScreen(
    modelStatus: ModelStatusUiState,
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
            onClick = onOpenModelStatus,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Model status")
        }
    }
}

@Composable
private fun ModelStatusScreen(
    status: ModelStatusUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedButton(onClick = onBack) {
            Text("Back")
        }

        Text(
            text = "Model status",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
                Text(text = status.title)
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
                if (status.canRetry) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}
