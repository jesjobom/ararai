package com.jesjobom.ararai.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.Image
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onRetryModelDownload: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    var sessionListOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var renameDialogOpen by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    val messageListState = rememberLazyListState()
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

    LaunchedEffect(messageListState, state.messages.size) {
        snapshotFlow {
            messageListState.isScrollInProgress to messageListState.isAtBottom(state.messages.size)
        }.distinctUntilChanged().collect { (isScrolling, isAtBottom) ->
            if (isScrolling) {
                followLatestMessages = isAtBottom
            }
        }
    }

    LaunchedEffect(state.selectedSessionId) {
        followLatestMessages = true
        if (state.messages.isNotEmpty()) {
            messageListState.scrollToItem(state.messages.lastIndex)
        }
    }

    LaunchedEffect(state.selectedSessionId, state.messages.size, latestMessageKey) {
        if (state.messages.isNotEmpty() && followLatestMessages) {
            messageListState.animateScrollToItem(state.messages.lastIndex)
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
                onUseAudioPrompt = viewModel::useAudioPrompt,
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
            onDismiss = { renameDialogOpen = false },
            onConfirm = {
                viewModel.renameCurrentSession(renameText)
                renameDialogOpen = false
            },
        )
    }
}

private fun LazyListState.isAtBottom(itemCount: Int): Boolean {
    if (itemCount == 0) return true
    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return true
    return lastVisible >= itemCount - 1
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
    onUseAudioPrompt: (AudioPrompt) -> Unit,
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
        val audioPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            val imported = context.importChatMedia(uri, "audio")
            onUseAudioPrompt(
                AudioPrompt(
                    uri = imported.file.absolutePath,
                    mimeType = context.contentResolver.getType(uri) ?: "audio/*",
                    displayName = imported.displayName,
                    byteSize = imported.file.length(),
                ),
            )
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
                            onClick = { audioPicker.launch(arrayOf("audio/*")) },
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
    }
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
                        Text(
                            text = content.reasoningText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
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
            Text(
                text = content.text.ifBlank { "..." },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        is MessageContent.AudioPromptContent -> {
            Text(
                text = content.audio.displayName ?: "Audio prompt",
                style = MaterialTheme.typography.bodyLarge,
            )
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

private fun Context.importChatMedia(uri: Uri, prefix: String): ImportedMedia {
    val displayName = uri.displayName(this)
    val extension = displayName?.substringAfterLast('.', missingDelimiterValue = "")?.takeIf { it.isNotBlank() }
    val dir = File(filesDir, "chat_media").apply { mkdirs() }
    val file = File(
        dir,
        buildString {
            append(prefix)
            append('-')
            append(System.currentTimeMillis())
            append('-')
            append(java.util.UUID.randomUUID())
            if (extension != null) {
                append('.')
                append(extension)
            }
        },
    )
    contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    } ?: error("Unable to open selected media")
    return ImportedMedia(file = file, displayName = displayName)
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
