package com.jesjobom.ararai.ui

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.jesjobom.ararai.chat.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatScreen(
    viewModel: ChatViewModel,
    mediaServices: ChatMediaServices,
    textToSpeechServiceFactory: () -> ChatTextToSpeechService,
    languageIdentifierFactory: () -> ChatLanguageIdentifier,
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
    var textToSpeechState by remember { mutableStateOf(ChatTextToSpeechState()) }
    val textToSpeechController = remember(textToSpeechServiceFactory, languageIdentifierFactory) {
        ChatTextToSpeechController(
            service = textToSpeechServiceFactory(),
            languageIdentifier = languageIdentifierFactory(),
        ) { textToSpeechState = it }
    }
    val messageListState = rememberLazyListState()
    val bottomRequester = remember { BringIntoViewRequester() }
    val isUserDragging by messageListState.interactionSource.collectIsDraggedAsState()
    var followLatestMessages by remember { mutableStateOf(true) }
    val currentSessionTitle = state.sessions.firstOrNull { it.id == state.selectedSessionId }?.title ?: "Chat"
    val modelStatusText = when {
        state.researchInProgress -> knowledgeToolStatusText(state.activeKnowledgeToolName)
        state.isLoadingModel -> "Loading model"
        state.isGenerating -> "Generating"
        else -> state.modelStatus
    }

    DisposableEffect(textToSpeechController) {
        onDispose { textToSpeechController.close() }
    }

    LaunchedEffect(isUserDragging) {
        if (isUserDragging) {
            followLatestMessages = false
        } else if (!messageListState.canScrollForward) {
            followLatestMessages = true
        }
    }

    LaunchedEffect(state.selectedSessionId) {
        textToSpeechController.stop()
        followLatestMessages = true
        state.messages.forEach { message ->
            if (message.isEligibleForTextToSpeech(isStreaming = false)) {
                textToSpeechController.prepare(message.id, message.text)
            }
        }
        if (state.messages.isNotEmpty()) {
            messageListState.scrollToItem(state.messages.size)
        }
    }

    FollowLatestMessagesEffect(
        sessionId = state.selectedSessionId,
        messageCount = state.messages.size,
        displayRevision = state.messageDisplayRevision,
        enabled = followLatestMessages,
        onFollowLatest = bottomRequester::bringIntoView,
    )

    CompletedAssistantPreparationEffect(
        completedMessageId = state.completedAssistantMessageId,
        messages = state.messages,
        onPrepare = textToSpeechController::prepare,
    )

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
                mediaServices = mediaServices,
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
                isBusy = state.isGenerating || state.isLoadingModel || state.isPersistenceBusy,
                onClick = { sessionListOpen = true },
            )

            textToSpeechState.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

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
                    if (state.hasOlderMessages) {
                        item(key = "load-older-messages") {
                            TextButton(
                                onClick = viewModel::loadOlderMessages,
                                enabled = !state.isLoadingOlderMessages,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                val label =
                                    if (state.isLoadingOlderMessages) {
                                        "Loading older messages"
                                    } else {
                                        "Load older messages"
                                    }
                                Text(label)
                            }
                        }
                    }
                    items(state.messages) { message ->
                        val isStreaming = state.isGenerating && message.id == state.messages.lastOrNull()?.id
                        MessageRow(
                            message = message,
                            showReasoning = state.showReasoning,
                            showAudioTranscriptions = state.showAudioTranscriptions,
                            mediaServices = mediaServices,
                            isStreaming = isStreaming,
                            isSpeaking = textToSpeechState.activeMessageId == message.id,
                            isSpeechPrepared = textToSpeechController.isPrepared(message.id),
                            onToggleSpeech = {
                                textToSpeechController.clearError()
                                textToSpeechController.toggle(message.id, message.text)
                            },
                        )
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
            showAudioTranscriptions = state.showAudioTranscriptions,
            onReasoningEnabledChange = viewModel::setReasoningEnabled,
            onShowReasoningChange = viewModel::setShowReasoning,
            onShowAudioTranscriptionsChange = viewModel::setShowAudioTranscriptions,
            onReset = {
                viewModel.setReasoningEnabled(false)
                viewModel.setShowReasoning(false)
                viewModel.setShowAudioTranscriptions(true)
            },
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

internal fun knowledgeToolStatusText(toolName: String?): String = toolName
    ?.takeIf { it.isNotBlank() }
    ?.let { "Using $it" }
    ?: "Using tool"

@Composable
internal fun FollowLatestMessagesEffect(
    sessionId: String?,
    messageCount: Int,
    displayRevision: Long,
    enabled: Boolean,
    onFollowLatest: suspend () -> Unit,
) {
    LaunchedEffect(sessionId, messageCount, displayRevision) {
        if (messageCount > 0 && enabled) onFollowLatest()
    }
}

@Composable
internal fun CompletedAssistantPreparationEffect(
    completedMessageId: String?,
    messages: List<ChatMessage>,
    onPrepare: (String, String) -> Unit,
) {
    LaunchedEffect(completedMessageId) {
        val message = messages.firstOrNull { it.id == completedMessageId }
        if (message != null && message.isEligibleForTextToSpeech(isStreaming = false)) {
            onPrepare(message.id, message.text)
        }
    }
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
