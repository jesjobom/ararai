package com.jesjobom.ararai.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.chat.ChatSessionUiState

@Composable
internal fun SessionListButton(
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
internal fun SessionListDialog(
    sessions: List<ChatSessionUiState>,
    selectedSessionId: String?,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    onSelect: (String) -> Unit,
    onRename: (ChatSessionUiState) -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Chat sessions",
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCreate) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                    Text(
                        text = "New",
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        },
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
                        onRename = { onRename(session) },
                        onDelete = { onDelete(session.id) },
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onClearAll, enabled = sessions.isNotEmpty()) {
                    Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
                    Text(
                        text = "Clear all",
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                TextButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = null)
                    Text(
                        text = "Close",
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        },
    )
}

@Composable
internal fun ClearSessionsConfirmationDialog(
    sessionCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear all sessions?") },
        text = {
            Text(
                "This permanently deletes ${if (sessionCount == 1) "the current session" else "all $sessionCount sessions"} and their messages. A new empty session will be created.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Clear all")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SessionListItem(
    session: ChatSessionUiState,
    isSelected: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .testTag("chat-session-${session.id}")
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onRename,
                onLongClickLabel = "Rename chat",
            ),
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
            Text(
                text = session.title,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
            IconButton(
                onClick = onDelete,
                enabled = canDelete,
                modifier = Modifier.testTag("delete-chat-${session.id}"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete chat",
                )
            }
        }
    }
}

@Composable
internal fun ChatSettingsDialog(
    reasoningEnabled: Boolean,
    showReasoning: Boolean,
    canEnableReasoning: Boolean,
    canShowReasoning: Boolean,
    onReasoningEnabledChange: (Boolean) -> Unit,
    onShowReasoningChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chat settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChatSettingSwitch(
                    title = "Enable reasoning",
                    status = if (canEnableReasoning) null else "Unavailable for this model",
                    checked = reasoningEnabled,
                    enabled = canEnableReasoning,
                    onCheckedChange = onReasoningEnabledChange,
                )
                ChatSettingSwitch(
                    title = "Show reasoning",
                    status = if (canShowReasoning) null else "Unavailable for this model",
                    checked = showReasoning,
                    enabled = canShowReasoning,
                    onCheckedChange = onShowReasoningChange,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
internal fun ChatSettingSwitch(
    title: String,
    status: String?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            status?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
internal fun RenameSessionDialog(
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
