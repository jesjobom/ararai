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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.R
import com.jesjobom.ararai.chat.AssistantCompletionStatus
import com.jesjobom.ararai.chat.ChatMessage
import com.jesjobom.ararai.chat.ChatRole
import com.jesjobom.ararai.chat.ChatViewModel
import com.jesjobom.ararai.chat.MessageContent
import com.jesjobom.ararai.reporting.GeneratedContentReportDraft
import com.jesjobom.ararai.reporting.PendingReport
import com.jesjobom.ararai.reporting.ReportDeliveryReceipt
import com.jesjobom.ararai.reporting.ReportReason

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatScreen(
    viewModel: ChatViewModel,
    mediaServices: ChatMediaServices,
    textToSpeechServiceFactory: () -> ChatTextToSpeechService,
    languageIdentifierFactory: () -> ChatLanguageIdentifier,
    onBack: () -> Unit,
    onRetryModelDownload: () -> Unit = {},
    onReportResponse: (String) -> GeneratedContentReportDraft? = { null },
    onReportLatestResponse: () -> GeneratedContentReportDraft? = { null },
    onSubmitReport: (GeneratedContentReportDraft, ReportReason, String?, Set<String>) -> Unit = { _, _, _, _ -> },
    pendingReports: List<PendingReport> = emptyList(),
    latestReportReceipt: ReportDeliveryReceipt? = null,
    onDeletePendingReport: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    var sessionListOpen by remember { mutableStateOf(false) }
    var clearSessionsConfirmationOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var renameDialogOpen by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var renameSessionId by remember { mutableStateOf<String?>(null) }
    var reportDraft by remember { mutableStateOf<GeneratedContentReportDraft?>(null) }
    var reportCenterOpen by remember { mutableStateOf(false) }
    var reportCenterDraft by remember { mutableStateOf<GeneratedContentReportDraft?>(null) }
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
    val currentSessionTitle =
        state.sessions.firstOrNull { it.id == state.selectedSessionId }?.title
            ?: stringResource(R.string.chat_title)
    val localizedError = state.errorKey?.localizedText() ?: state.error
    val modelStatusText = when {
        state.toolInProgress ->
            state.activeToolName
                ?.takeIf(String::isNotBlank)
                ?.let { stringResource(R.string.chat_using_named_tool, it) }
                ?: stringResource(R.string.chat_using_tool)
        state.isLoadingModel -> stringResource(R.string.chat_loading_model)
        state.isGenerating -> stringResource(R.string.chat_generating)
        else -> state.modelStatusKey?.localizedText() ?: state.modelStatus
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
                            text = stringResource(R.string.chat_title),
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
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    ReportCenterButton(
                        pendingReports = pendingReports,
                        latestReceipt = latestReportReceipt,
                    ) {
                        reportCenterDraft = onReportLatestResponse()
                        reportCenterOpen = true
                    }
                    IconButton(onClick = { settingsOpen = true }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.chat_settings),
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
                error = localizedError,
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

            textToSpeechState.errorKey?.localizedText()?.let { error ->
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
                        text = stringResource(R.string.chat_retry_model_download),
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
                                        stringResource(R.string.chat_loading_older_messages)
                                    } else {
                                        stringResource(R.string.chat_load_older_messages)
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
                            onReport = if (message.isReportable() && !isStreaming) {
                                { reportDraft = onReportResponse(message.id) }
                            } else {
                                null
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
    reportDraft?.let { draft ->
        GeneratedContentReportDialog(
            draft = draft,
            onSubmit = { reason, comment, contextIds ->
                onSubmitReport(draft, reason, comment, contextIds)
                reportDraft = null
            },
            onDismiss = { reportDraft = null },
        )
    }
    if (reportCenterOpen) {
        ReportCenterDialog(
            draft = reportCenterDraft,
            pendingReports = pendingReports,
            onSubmit = { reason, comment, contextIds ->
                reportCenterDraft?.let { onSubmitReport(it, reason, comment, contextIds) }
                reportCenterOpen = false
            },
            onDeletePendingReport = onDeletePendingReport,
            onDismiss = { reportCenterOpen = false },
        )
    }
}

private fun ChatMessage.isReportable(): Boolean {
    val text = content as? MessageContent.TextPrompt ?: return false
    return role == ChatRole.Assistant &&
        text.completionStatus == AssistantCompletionStatus.Complete &&
        text.text.isNotBlank()
}

internal fun knowledgeToolStatusText(
    toolName: String?,
    unnamedToolText: String = "Using tool",
    namedToolText: (String) -> String = { "Using $it" },
): String = toolName
    ?.takeIf { it.isNotBlank() }
    ?.let(namedToolText)
    ?: unnamedToolText

@Composable
internal fun UserMessageKey.localizedText(): String = when (this) {
    UserMessageKey.ModelAvailable -> stringResource(R.string.chat_model_available)
    UserMessageKey.ModelUnavailable -> stringResource(R.string.error_model_unavailable)
    UserMessageKey.GenerationFailed -> stringResource(R.string.error_generation_failed)
    UserMessageKey.ModelLoadingFailed -> stringResource(R.string.error_model_loading_failed)
    UserMessageKey.NoFinalAnswer -> stringResource(R.string.chat_no_final_answer)
    UserMessageKey.TextToSpeechUnavailable -> stringResource(R.string.error_tts_unavailable)
    UserMessageKey.TextToSpeechInitializationFailed -> stringResource(R.string.error_tts_initialization_failed)
    UserMessageKey.TextToSpeechVoiceUnavailable -> stringResource(R.string.error_tts_voice_unavailable)
    UserMessageKey.TextToSpeechPlaybackFailed -> stringResource(R.string.error_tts_playback_failed)
    UserMessageKey.TextToSpeechRateFailed -> stringResource(R.string.error_tts_rate_failed)
    UserMessageKey.TextToSpeechStartFailed -> stringResource(R.string.error_tts_start_failed)
}

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
                text = stringResource(R.string.chat_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.chat_empty_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
