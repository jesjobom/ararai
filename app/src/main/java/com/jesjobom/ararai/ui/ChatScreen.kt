package com.jesjobom.ararai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.chat.ChatMessage
import com.jesjobom.ararai.chat.ChatRole
import com.jesjobom.ararai.chat.ChatViewModel

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onRetryModelDownload: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = onBack) {
            Text("Back")
        }

        Text(
            text = "ArarAI",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = state.modelStatus,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.canRetryModelDownload) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onRetryModelDownload) {
                Text("Retry download")
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.messages) { message ->
                MessageRow(message = message)
            }
        }

        state.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.prompt,
                onValueChange = viewModel::onPromptChanged,
                modifier = Modifier.weight(1f),
                minLines = 1,
                maxLines = 4,
                label = { Text("Prompt") },
                enabled = !state.isGenerating,
            )
            Button(
                onClick = viewModel::submitPrompt,
                enabled = state.canSubmit,
            ) {
                Text(if (state.isGenerating) "..." else "Send")
            }
        }
    }
}

@Composable
private fun MessageRow(message: ChatMessage) {
    val containerColor = when (message.role) {
        ChatRole.User -> MaterialTheme.colorScheme.primary
        ChatRole.Assistant -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (message.role) {
        ChatRole.User -> MaterialTheme.colorScheme.onPrimary
        ChatRole.Assistant -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = message.role.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message.text.ifBlank { "..." },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
