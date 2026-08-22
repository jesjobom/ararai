package com.jesjobom.ararai.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.R

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
        title = stringResource(R.string.app_name),
        subtitle = appVersionLabel,
        showTopBar = false,
    ) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HomeBrandHeader(appVersionLabel = appVersionLabel)
            Text(
                text = stringResource(R.string.home_headline),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.home_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HomeConversationCard(
                title = stringResource(R.string.home_chat_title),
                detail = stringResource(R.string.home_chat_description),
                icon = Icons.AutoMirrored.Filled.Chat,
                onClick = onOpenChat,
            )
            HomeConversationCard(
                title = stringResource(R.string.home_voice_chat_title),
                detail = stringResource(R.string.home_voice_chat_description),
                icon = Icons.Filled.Mic,
                onClick = onOpenVoiceChat,
            )
            StatusCard(
                title = stringResource(R.string.home_model_manager_title),
                value = modelStatus.modelName,
                detail = modelStatus.localizedTitle(),
                tags = modelStatus.localizedCapabilities(),
                icon = when {
                    modelStatus.progressPercent != null -> Icons.Filled.CloudDownload
                    modelStatus.canRetry -> Icons.Filled.Error
                    modelStatus.isReady -> Icons.Filled.CheckCircle
                    else -> Icons.Filled.Storage
                },
                onAction = onOpenModelStatus,
            )
            StatusCard(
                title = stringResource(R.string.home_assistant_configuration_title),
                value = stringResource(R.string.home_assistant_configuration_value),
                detail = stringResource(R.string.home_assistant_configuration_description),
                icon = Icons.Filled.Bolt,
                onAction = onOpenInstructionsTools,
            )
            StatusCard(
                title = stringResource(R.string.settings_title),
                value = stringResource(R.string.home_settings_value),
                detail = stringResource(R.string.home_settings_description),
                icon = Icons.Filled.Settings,
                onAction = onOpenSettings,
            )
        }
    }
}

@Composable
private fun HomeBrandHeader(appVersionLabel: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = homeBrandBackground(LocalArarAiDarkTheme.current),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ararai_wordmark),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f),
                contentScale = ContentScale.Fit,
            )
            Text(
                text = appVersionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.78f),
            )
        }
    }
}

internal fun homeBrandBackground(darkTheme: Boolean): Color = if (darkTheme) Color.Transparent else Color.Black

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
            Text(text = detail, style = MaterialTheme.typography.bodyMedium)
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
                    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(text = value, style = MaterialTheme.typography.bodyMedium)
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
