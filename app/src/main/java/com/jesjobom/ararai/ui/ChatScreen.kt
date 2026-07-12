package com.jesjobom.ararai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.chat.ChatMessage
import com.jesjobom.ararai.chat.ChatRole
import com.jesjobom.ararai.chat.ChatSessionUiState
import com.jesjobom.ararai.chat.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onRetryModelDownload: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    var sessionListOpen by remember { mutableStateOf(false) }
    var renameDialogOpen by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    val currentSessionTitle = state.sessions.firstOrNull { it.id == state.selectedSessionId }?.title ?: "Chat"
    val modelStatusText = when {
        state.isLoadingModel -> "Loading model"
        state.isGenerating -> "Generating"
        else -> state.modelStatus
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Chat",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = modelStatusText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        bottomBar = {
            ChatInputBar(
                prompt = state.prompt,
                onPromptChanged = viewModel::onPromptChanged,
                canSubmit = state.canSubmit,
                isGenerating = state.isGenerating,
                error = state.error,
                onSubmit = viewModel::submitPrompt,
                onCancelGeneration = viewModel::cancelGeneration,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            SessionListButton(
                title = currentSessionTitle,
                isBusy = state.isGenerating || state.isLoadingModel,
                onClick = { sessionListOpen = true },
            )

            if (state.canRetryModelDownload) {
                Button(
                    onClick = onRetryModelDownload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                    Text(
                        text = "Retry model download",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            if (state.messages.isEmpty()) {
                EmptyChatState(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.messages) { message ->
                        MessageRow(message = message)
                    }
                }
            }
        }
    }

    if (sessionListOpen) {
        SessionListDialog(
            sessions = state.sessions,
            selectedSessionId = state.selectedSessionId,
            canDelete = state.canDeleteCurrentSession,
            onDismiss = { sessionListOpen = false },
            onCreate = {
                viewModel.createSession()
                sessionListOpen = false
            },
            onSelect = {
                viewModel.selectSession(it)
                sessionListOpen = false
            },
            onRename = {
                renameText = currentSessionTitle
                renameDialogOpen = true
                sessionListOpen = false
            },
            onDelete = viewModel::deleteSession,
        )
    }

    if (renameDialogOpen) {
        RenameSessionDialog(
            title = renameText,
            onTitleChange = { renameText = it },
            onDismiss = { renameDialogOpen = false },
            onConfirm = {
                viewModel.renameCurrentSession(renameText)
                renameDialogOpen = false
            },
        )
    }
}

@Composable
private fun SessionListButton(
    title: String,
    isBusy: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = !isBusy,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Icon(imageVector = Icons.AutoMirrored.Filled.Chat, contentDescription = null)
        Text(
            text = title,
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
        )
        Text("Sessions")
    }
}

@Composable
private fun SessionListDialog(
    sessions: List<ChatSessionUiState>,
    selectedSessionId: String?,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    onSelect: (String) -> Unit,
    onRename: () -> Unit,
    onDelete: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chat sessions") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sessions) { session ->
                    SessionListItem(
                        session = session,
                        isSelected = session.id == selectedSessionId,
                        canDelete = canDelete,
                        onSelect = { onSelect(session.id) },
                        onDelete = { onDelete(session.id) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreate) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Text(
                    text = "New",
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRename, enabled = selectedSessionId != null) {
                    Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
                    Text(
                        text = "Rename",
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
    )
}

@Composable
private fun SessionListItem(
    session: ChatSessionUiState,
    isSelected: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onSelect,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = session.title,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            IconButton(onClick = onDelete, enabled = canDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete chat",
                )
            }
            if (isSelected) {
                Text(
                    text = "Current",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun RenameSessionDialog(
    title: String,
    onTitleChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename chat") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                label = { Text("Title") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun EmptyChatState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Start a local conversation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Your prompt stays on this device and is processed by the selected local model.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    prompt: String,
    onPromptChanged: (String) -> Unit,
    canSubmit: Boolean,
    isGenerating: Boolean,
    error: String?,
    onSubmit: () -> Unit,
    onCancelGeneration: () -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (isGenerating) {
                Button(
                    onClick = onCancelGeneration,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = null)
                    Text(
                        text = "Cancel generation",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChanged,
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 5,
                label = { Text("Message") },
                enabled = !isGenerating,
                trailingIcon = {
                    FilledIconButton(
                        onClick = onSubmit,
                        enabled = canSubmit,
                        modifier = Modifier.size(40.dp),
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun MessageRow(message: ChatMessage) {
    val isUser = message.role == ChatRole.User
    val containerColor = if (isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val label = if (isUser) "You" else "ArarAI"

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 340.dp),
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = label,
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
}
