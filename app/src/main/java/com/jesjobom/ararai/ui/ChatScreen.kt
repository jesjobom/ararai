package com.jesjobom.ararai.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.chat.AudioPrompt
import com.jesjobom.ararai.chat.ChatMessage
import com.jesjobom.ararai.chat.ChatRole
import com.jesjobom.ararai.chat.ChatSessionUiState
import com.jesjobom.ararai.chat.ChatViewModel
import com.jesjobom.ararai.chat.ImageAttachment
import com.jesjobom.ararai.chat.MessageContent
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onRetryModelDownload: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    var sessionListOpen by remember { mutableStateOf(false) }
    var clearSessionsConfirmationOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var renameDialogOpen by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var renameSessionId by remember { mutableStateOf<String?>(null) }
    val messageListState = rememberLazyListState()
    val bottomRequester = remember { BringIntoViewRequester() }
    val isUserDragging by messageListState.interactionSource.collectIsDraggedAsState()
    var followLatestMessages by remember { mutableStateOf(true) }
    val currentSessionTitle = state.sessions.firstOrNull { it.id == state.selectedSessionId }?.title ?: "Chat"
    val modelStatusText = when {
        state.isLoadingModel -> "Loading model"
        state.isGenerating -> "Generating"
        else -> state.modelStatus
    }
    val latestMessageKey = state.messages.lastOrNull()?.let { message ->
        val reasoningText = (message.content as? MessageContent.TextPrompt)?.reasoningText.orEmpty()
        "${message.id}:${message.text}:$reasoningText"
    }

    LaunchedEffect(isUserDragging) {
        if (isUserDragging) {
            followLatestMessages = false
        } else if (!messageListState.canScrollForward) {
            followLatestMessages = true
        }
    }

    LaunchedEffect(state.selectedSessionId) {
        followLatestMessages = true
        if (state.messages.isNotEmpty()) {
            bottomRequester.bringIntoView()
        }
    }

    LaunchedEffect(state.selectedSessionId, state.messages.size, latestMessageKey) {
        if (state.messages.isNotEmpty() && followLatestMessages) {
            bottomRequester.bringIntoView()
        }
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
                actions = {
                    IconButton(onClick = { settingsOpen = true }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Chat settings",
                        )
                    }
                },
            )
        },
        bottomBar = {
            ChatInputBar(
                prompt = state.prompt,
                imageAttachments = state.imageAttachments,
                audioPrompt = state.audioPrompt,
                canAttachImage = state.canAttachImage,
                canUseAudioPrompt = state.canUseAudioPrompt,
                onPromptChanged = viewModel::onPromptChanged,
                onAttachImage = viewModel::attachImage,
                onRemoveImage = viewModel::removeImage,
                onSendAudioPrompt = viewModel::submitAudioPrompt,
                onClearAudioPrompt = viewModel::clearAudioPrompt,
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
                    state = messageListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.messages) { message ->
                        MessageRow(message = message, showReasoning = state.showReasoning)
                    }
                    item(key = "message-list-bottom") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .bringIntoViewRequester(bottomRequester),
                        )
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
            onRename = { session ->
                renameSessionId = session.id
                renameText = session.title
                renameDialogOpen = true
                sessionListOpen = false
            },
            onDelete = viewModel::deleteSession,
            onClearAll = {
                sessionListOpen = false
                clearSessionsConfirmationOpen = true
            },
        )
    }

    if (clearSessionsConfirmationOpen) {
        ClearSessionsConfirmationDialog(
            sessionCount = state.sessions.size,
            onDismiss = { clearSessionsConfirmationOpen = false },
            onConfirm = {
                viewModel.clearAllSessions()
                clearSessionsConfirmationOpen = false
            },
        )
    }

    if (settingsOpen) {
        ChatSettingsDialog(
            reasoningEnabled = state.reasoningEnabled,
            showReasoning = state.showReasoning,
            canEnableReasoning = state.canEnableReasoning,
            canShowReasoning = state.canShowReasoning,
            onReasoningEnabledChange = viewModel::setReasoningEnabled,
            onShowReasoningChange = viewModel::setShowReasoning,
            onDismiss = { settingsOpen = false },
        )
    }

    if (renameDialogOpen) {
        RenameSessionDialog(
            title = renameText,
            onTitleChange = { renameText = it },
            onDismiss = {
                renameSessionId = null
                renameDialogOpen = false
            },
            onConfirm = {
                renameSessionId?.let { viewModel.renameSession(it, renameText) }
                renameSessionId = null
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
private fun ClearSessionsConfirmationDialog(
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
private fun SessionListItem(
    session: ChatSessionUiState,
    isSelected: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.combinedClickable(
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
            IconButton(onClick = onDelete, enabled = canDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete chat",
                )
            }
        }
    }
}

@Composable
private fun ChatSettingsDialog(
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
private fun ChatSettingSwitch(
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
    imageAttachments: List<ImageAttachment>,
    audioPrompt: AudioPrompt?,
    canAttachImage: Boolean,
    canUseAudioPrompt: Boolean,
    onPromptChanged: (String) -> Unit,
    onAttachImage: (ImageAttachment) -> Unit,
    onRemoveImage: (String) -> Unit,
    onSendAudioPrompt: (AudioPrompt) -> Unit,
    onClearAudioPrompt: () -> Unit,
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
        val context = LocalContext.current
        var audioRecorderOpen by remember { mutableStateOf(false) }
        var activeRecorder by remember { mutableStateOf<ChatAudioRecorder?>(null) }
        var activeRecordingFile by remember { mutableStateOf<File?>(null) }
        var recordingStartedAtMillis by remember { mutableStateOf(0L) }
        var recordingDurationMillis by remember { mutableStateOf(0L) }
        var recordedAudio by remember { mutableStateOf<RecordedAudio?>(null) }
        var recordingError by remember { mutableStateOf<String?>(null) }
        fun discardRecording() {
            activeRecorder?.stopSafely()
            activeRecorder = null
            activeRecordingFile?.delete()
            activeRecordingFile = null
            recordedAudio?.file?.delete()
            recordedAudio = null
            recordingStartedAtMillis = 0L
            recordingDurationMillis = 0L
        }
        fun startAudioRecording() {
            discardRecording()
            try {
                val file = context.createRecordedAudioFile()
                val recorder = ChatAudioRecorder(file)
                recorder.start()
                activeRecordingFile = file
                activeRecorder = recorder
                recordingStartedAtMillis = SystemClock.elapsedRealtime()
                recordingDurationMillis = 0L
                recordingError = null
            } catch (error: Throwable) {
                activeRecorder?.stopSafely()
                activeRecorder = null
                activeRecordingFile?.delete()
                activeRecordingFile = null
                recordingError = error.message ?: "Unable to start recording"
            }
        }
        fun stopAudioRecording() {
            val recorder = activeRecorder ?: return
            val file = activeRecordingFile ?: return
            val durationMillis = (SystemClock.elapsedRealtime() - recordingStartedAtMillis).coerceAtLeast(0L)
            activeRecorder = null
            activeRecordingFile = null
            val finalized = recorder.stopSafely()
            if (finalized && durationMillis >= MIN_RECORDED_AUDIO_DURATION_MILLIS) {
                recordedAudio = RecordedAudio(file = file, durationMillis = durationMillis)
                recordingDurationMillis = durationMillis
                recordingError = null
            } else {
                file.delete()
                recordingError = "Recording was too short"
            }
        }
        val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                startAudioRecording()
            } else {
                recordingError = "Microphone permission denied"
            }
        }
        fun openAudioRecorder() {
            audioRecorderOpen = true
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startAudioRecording()
            } else {
                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        val imagePicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            val imported = context.importChatImage(uri)
            onAttachImage(
                ImageAttachment(
                    uri = imported.file.absolutePath,
                    mimeType = "image/jpeg",
                    displayName = imported.displayName,
                    byteSize = imported.file.length(),
                ),
            )
        }
        DisposableEffect(Unit) {
            onDispose {
                activeRecorder?.stopSafely()
            }
        }
        LaunchedEffect(activeRecorder, recordingStartedAtMillis) {
            while (activeRecorder != null && recordingStartedAtMillis > 0L) {
                recordingDurationMillis = SystemClock.elapsedRealtime() - recordingStartedAtMillis
                delay(250)
            }
        }

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

            if (imageAttachments.isNotEmpty() || audioPrompt != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    imageAttachments.forEach { image ->
                        AttachmentRow(
                            label = image.displayName ?: "Image",
                            imageUri = image.uri,
                            onRemove = { onRemoveImage(image.uri) },
                        )
                    }
                    audioPrompt?.let { audio ->
                        AttachmentRow(
                            label = audio.displayName ?: "Audio prompt",
                            onRemove = onClearAudioPrompt,
                        )
                    }
                }
            }

            if (canAttachImage || canUseAudioPrompt) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canAttachImage && audioPrompt == null) {
                        OutlinedButton(
                            onClick = { imagePicker.launch(arrayOf("image/*")) },
                            enabled = !isGenerating,
                        ) {
                            Icon(imageVector = Icons.Filled.AttachFile, contentDescription = null)
                            Text(
                                text = "Image",
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                    if (canUseAudioPrompt && imageAttachments.isEmpty() && prompt.isBlank()) {
                        OutlinedButton(
                            onClick = ::openAudioRecorder,
                            enabled = !isGenerating,
                        ) {
                            Icon(imageVector = Icons.Filled.GraphicEq, contentDescription = null)
                            Text(
                                text = "Audio",
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChanged,
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 5,
                label = { Text("Message") },
                enabled = !isGenerating && audioPrompt == null,
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

        if (audioRecorderOpen) {
            AudioRecorderDialog(
                isRecording = activeRecorder != null,
                recordedAudio = recordedAudio,
                recordingDurationMillis = recordingDurationMillis,
                error = recordingError,
                onStartRecording = ::openAudioRecorder,
                onStopRecording = ::stopAudioRecording,
                onUseRecording = {
                    val audio = recordedAudio ?: return@AudioRecorderDialog
                    onSendAudioPrompt(audio.toAudioPrompt())
                    recordedAudio = null
                    audioRecorderOpen = false
                },
                onDismiss = {
                    if (activeRecorder == null) {
                        discardRecording()
                        audioRecorderOpen = false
                        recordingError = null
                    }
                },
            )
        }
    }
}

@Composable
private fun AudioRecorderDialog(
    isRecording: Boolean,
    recordedAudio: RecordedAudio?,
    recordingDurationMillis: Long,
    error: String?,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onUseRecording: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio prompt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                when {
                    isRecording -> {
                        Text(
                            text = formatDuration(recordingDurationMillis),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Recording",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    recordedAudio != null -> {
                        Text(
                            text = formatDuration(recordedAudio.durationMillis),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = recordedAudio.file.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AudioPlaybackRow(
                            audio = recordedAudio.toAudioPrompt(),
                            compact = true,
                        )
                    }
                    else -> {
                        Text(
                            text = "Preparing microphone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                isRecording -> {
                    TextButton(onClick = onStopRecording) {
                        Text("Stop")
                    }
                }
                recordedAudio != null -> {
                    TextButton(onClick = onUseRecording) {
                        Text("Send")
                    }
                }
                else -> {
                    TextButton(onClick = onStartRecording) {
                        Text("Record")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isRecording) {
                Text(if (recordedAudio != null) "Cancel" else "Close")
            }
        },
    )
}

@Composable
private fun AttachmentRow(
    label: String,
    imageUri: String? = null,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        imageUri?.let {
            ImageThumbnail(uri = it, sizeDp = 44)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Remove")
        }
    }
}

@Composable
private fun AudioPlaybackRow(
    audio: AudioPrompt,
    compact: Boolean = false,
) {
    val context = LocalContext.current
    val secondaryTextColor = LocalContentColor.current.copy(alpha = 0.74f)
    val playerState = remember(audio.uri) { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember(audio.uri) { mutableStateOf(false) }
    var playbackError by remember(audio.uri) { mutableStateOf<String?>(null) }
    fun releasePlayer() {
        playerState.value?.release()
        playerState.value = null
        isPlaying = false
    }
    fun ensurePlayer(): MediaPlayer? {
        playerState.value?.let { return it }
        return try {
            MediaPlayer().apply {
                if (audio.uri.startsWith("content://")) {
                    setDataSource(context, Uri.parse(audio.uri))
                } else {
                    setDataSource(audio.uri)
                }
                setOnCompletionListener {
                    isPlaying = false
                    it.seekTo(0)
                }
                prepare()
                playerState.value = this
            }
        } catch (error: Throwable) {
            playbackError = error.message ?: "Unable to play audio"
            releasePlayer()
            null
        }
    }

    DisposableEffect(audio.uri) {
        onDispose { releasePlayer() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    val player = ensurePlayer() ?: return@IconButton
                    if (player.isPlaying) {
                        player.pause()
                        isPlaying = false
                    } else {
                        player.start()
                        isPlaying = true
                        playbackError = null
                    }
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause audio" else "Play audio",
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                if (!compact) {
                    Text(
                        text = audio.displayName ?: "Audio prompt",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    text = audio.durationMillis?.let(::formatDuration) ?: "Audio",
                    style = MaterialTheme.typography.labelMedium,
                    color = secondaryTextColor,
                )
            }
        }
        playbackError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun MessageRow(
    message: ChatMessage,
    showReasoning: Boolean,
) {
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
                MessageContentView(message.content, showReasoning = showReasoning)
            }
        }
    }
}

@Composable
private fun MessageContentView(
    content: MessageContent,
    showReasoning: Boolean,
) {
    when (content) {
        is MessageContent.TextPrompt -> {
            if (showReasoning && content.reasoningText.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Reasoning",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        MarkdownText(text = content.reasoningText)
                    }
                }
            }
            content.imageAttachments.forEach { image ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                )
                {
                    ImageThumbnail(uri = image.uri, sizeDp = 156)
                    Text(
                        text = image.displayName ?: "Image",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            MarkdownText(text = content.text.ifBlank { "..." })
        }
        is MessageContent.AudioPromptContent -> {
            AudioPlaybackRow(audio = content.audio)
        }
    }
}

@Composable
private fun ImageThumbnail(
    uri: String,
    sizeDp: Int,
) {
    val bitmap = remember(uri) { decodeThumbnailBitmap(uri) }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(sizeDp.dp),
            contentScale = ContentScale.Crop,
        )
    }
}

private data class ImportedMedia(
    val file: File,
    val displayName: String?,
)

internal data class RecordedAudio(
    val file: File,
    val durationMillis: Long,
) {
    fun toAudioPrompt(): AudioPrompt =
        recordedAudioPrompt(
            file = file,
            durationMillis = durationMillis,
        )
}

internal fun recordedAudioPrompt(
    file: File,
    durationMillis: Long,
): AudioPrompt =
    AudioPrompt(
        uri = file.absolutePath,
        mimeType = RECORDED_AUDIO_MIME_TYPE,
        displayName = file.name,
        byteSize = file.length(),
        durationMillis = durationMillis,
    )

private fun Context.importChatImage(uri: Uri): ImportedMedia {
    val displayName = uri.displayName(this)
    val dir = File(filesDir, "chat_media").apply { mkdirs() }
    val file = File(
        dir,
        "image-${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}.jpg",
    )
    val sourceBytes = contentResolver.openInputStream(uri)?.use { input ->
        input.readBytes()
    } ?: error("Unable to open selected image")

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, bounds)
    val sampleSize = calculateInSampleSize(
        width = bounds.outWidth,
        height = bounds.outHeight,
        maxSize = MAX_IMAGE_INPUT_DIMENSION,
    )
    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val decoded = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, decodeOptions)
        ?: error("Unable to decode selected image")
    val normalized = decoded.scaleToFit(MAX_IMAGE_INPUT_DIMENSION)
    file.outputStream().use { output ->
        normalized.compress(Bitmap.CompressFormat.JPEG, IMAGE_INPUT_JPEG_QUALITY, output)
    }
    if (normalized !== decoded) {
        decoded.recycle()
    }
    normalized.recycle()
    return ImportedMedia(file = file, displayName = displayName)
}

private fun Context.createRecordedAudioFile(): File {
    val dir = File(filesDir, "chat_media").apply { mkdirs() }
    return File(
        dir,
        "recording-${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}.wav",
    )
}

internal class ChatAudioRecorder(
    private val file: File,
    private val sampleRate: Int = RECORDED_AUDIO_SAMPLE_RATE_HZ,
) {
    private val bufferSize: Int = maxOf(
        AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ),
        RECORDED_AUDIO_BUFFER_SIZE_BYTES,
    )
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    @Volatile
    private var isRecording: Boolean = false
    @Volatile
    private var dataSize: Long = 0L

    @Suppress("MissingPermission")
    fun start() {
        require(bufferSize > 0) { "Unable to initialize audio input" }
        file.parentFile?.mkdirs()
        dataSize = 0L
        val recorder = AudioRecord(
            android.media.MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            error("Unable to initialize microphone")
        }
        audioRecord = recorder
        file.outputStream().use { output ->
            output.write(createWavHeader(dataSize = 0L, sampleRate = sampleRate))
        }
        recorder.startRecording()
        isRecording = true
        recordingThread = Thread(
            {
                FileOutputStream(file, true).buffered().use { output ->
                    val buffer = ByteArray(bufferSize)
                    while (isRecording) {
                        val read = recorder.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            output.write(buffer, 0, read)
                            dataSize += read.toLong()
                        }
                    }
                }
            },
            "ChatAudioRecorder",
        ).apply { start() }
    }

    fun stopSafely(): Boolean =
        try {
            isRecording = false
            audioRecord?.stopSafely()
            recordingThread?.join(1_000)
            audioRecord?.release()
            audioRecord = null
            recordingThread = null
            if (dataSize > 0L) {
                file.writeWavHeader(dataSize = dataSize, sampleRate = sampleRate)
            }
            dataSize > 0L
        } catch (_: RuntimeException) {
            false
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
}

private fun AudioRecord.stopSafely() {
    try {
        stop()
    } catch (_: RuntimeException) {
        // The recorder may already be stopped after a setup/read failure.
    }
}

internal fun createWavHeader(
    dataSize: Long,
    sampleRate: Int = RECORDED_AUDIO_SAMPLE_RATE_HZ,
): ByteArray {
    val channels = 1
    val bitsPerSample = 16
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8
    val riffSize = (dataSize + WAV_HEADER_SIZE_BYTES - 8).coerceAtMost(UInt.MAX_VALUE.toLong()).toInt()
    val dataChunkSize = dataSize.coerceAtMost(UInt.MAX_VALUE.toLong()).toInt()
    return ByteArray(WAV_HEADER_SIZE_BYTES).apply {
        writeAscii(0, "RIFF")
        writeLittleEndianInt(4, riffSize)
        writeAscii(8, "WAVE")
        writeAscii(12, "fmt ")
        writeLittleEndianInt(16, 16)
        writeLittleEndianShort(20, 1)
        writeLittleEndianShort(22, channels)
        writeLittleEndianInt(24, sampleRate)
        writeLittleEndianInt(28, byteRate)
        writeLittleEndianShort(32, blockAlign)
        writeLittleEndianShort(34, bitsPerSample)
        writeAscii(36, "data")
        writeLittleEndianInt(40, dataChunkSize)
    }
}

private fun File.writeWavHeader(dataSize: Long, sampleRate: Int) {
    RandomAccessFile(this, "rw").use { file ->
        file.seek(0)
        file.write(createWavHeader(dataSize = dataSize, sampleRate = sampleRate))
    }
}

private fun ByteArray.writeAscii(offset: Int, value: String) {
    value.forEachIndexed { index, char -> this[offset + index] = char.code.toByte() }
}

private fun ByteArray.writeLittleEndianInt(offset: Int, value: Int) {
    this[offset] = value.toByte()
    this[offset + 1] = (value shr 8).toByte()
    this[offset + 2] = (value shr 16).toByte()
    this[offset + 3] = (value shr 24).toByte()
}

private fun ByteArray.writeLittleEndianShort(offset: Int, value: Int) {
    this[offset] = value.toByte()
    this[offset + 1] = (value shr 8).toByte()
}

internal fun formatDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun Bitmap.scaleToFit(maxSize: Int): Bitmap {
    val largestSide = maxOf(width, height)
    if (largestSide <= maxSize) return this
    val scale = maxSize.toFloat() / largestSide.toFloat()
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
}

private fun calculateInSampleSize(width: Int, height: Int, maxSize: Int): Int {
    var sampleSize = 1
    var sampledWidth = width
    var sampledHeight = height
    while (sampledWidth / 2 >= maxSize || sampledHeight / 2 >= maxSize) {
        sampleSize *= 2
        sampledWidth /= 2
        sampledHeight /= 2
    }
    return sampleSize
}

private fun decodeThumbnailBitmap(path: String): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val sampleSize = calculateInSampleSize(
        width = bounds.outWidth,
        height = bounds.outHeight,
        maxSize = THUMBNAIL_DECODE_DIMENSION,
    )
    return BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    )
}

private fun Uri.displayName(context: Context): String? =
    context.contentResolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: lastPathSegment

private const val MAX_IMAGE_INPUT_DIMENSION = 1024
private const val IMAGE_INPUT_JPEG_QUALITY = 88
private const val THUMBNAIL_DECODE_DIMENSION = 256
private const val RECORDED_AUDIO_MIME_TYPE = "audio/wav"
private const val RECORDED_AUDIO_SAMPLE_RATE_HZ = 16_000
private const val RECORDED_AUDIO_BUFFER_SIZE_BYTES = 4096
private const val MIN_RECORDED_AUDIO_DURATION_MILLIS = 250L
private const val WAV_HEADER_SIZE_BYTES = 44
