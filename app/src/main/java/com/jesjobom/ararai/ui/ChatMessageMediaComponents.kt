package com.jesjobom.ararai.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.R
import com.jesjobom.ararai.chat.AssistantCompletionStatus
import com.jesjobom.ararai.chat.AudioPrompt
import com.jesjobom.ararai.chat.AudioTranscriptionStatus
import com.jesjobom.ararai.chat.ChatMessage
import com.jesjobom.ararai.chat.ChatRole
import com.jesjobom.ararai.chat.MessageContent

@Composable
internal fun AttachmentRow(
    label: String,
    imageUri: String? = null,
    onRemove: () -> Unit,
    mediaServices: ChatMediaServices,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        imageUri?.let {
            ImageThumbnail(uri = it, sizeDp = 44, mediaServices = mediaServices)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_remove),
            )
        }
    }
}

@Composable
internal fun AudioPlaybackRow(
    audio: AudioPrompt,
    compact: Boolean = false,
    mediaServices: ChatMediaServices,
) {
    val playbackFailed = stringResource(R.string.chat_playback_failed)
    val secondaryTextColor = LocalContentColor.current.copy(alpha = 0.74f)
    val playerState = remember(audio.uri) { mutableStateOf<ChatAudioPlayer?>(null) }
    var isPlaying by remember(audio.uri) { mutableStateOf(false) }
    var playbackError by remember(audio.uri) { mutableStateOf<String?>(null) }
    fun releasePlayer() {
        playerState.value?.release()
        playerState.value = null
        isPlaying = false
    }
    fun ensurePlayer(): ChatAudioPlayer? {
        playerState.value?.let { return it }
        return try {
            mediaServices.audioPlayerFactory.create(audio) { isPlaying = false }.also {
                playerState.value = it
            }
        } catch (error: Throwable) {
            playbackError = error.message ?: playbackFailed
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
                    contentDescription =
                    stringResource(
                        if (isPlaying) R.string.chat_pause_audio else R.string.chat_play_audio,
                    ),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                if (!compact) {
                    Text(
                        text = audio.displayName ?: stringResource(R.string.chat_audio_prompt),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    text = audio.durationMillis?.let(::formatDuration) ?: stringResource(R.string.chat_audio),
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
@Suppress("LongParameterList", "LongMethod")
internal fun MessageRow(
    message: ChatMessage,
    showReasoning: Boolean,
    showAudioTranscriptions: Boolean,
    mediaServices: ChatMediaServices,
    isStreaming: Boolean = false,
    isSpeaking: Boolean = false,
    isSpeechPrepared: Boolean = false,
    onToggleSpeech: () -> Unit = {},
    onReport: (() -> Unit)? = null,
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
    val label = if (isUser) stringResource(R.string.chat_you) else stringResource(R.string.app_name)
    val selectionColors = if (isUser) {
        TextSelectionColors(
            handleColor = MaterialTheme.colorScheme.onPrimary,
            backgroundColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.32f),
        )
    } else {
        TextSelectionColors(
            handleColor = MaterialTheme.colorScheme.primary,
            backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
        )
    }

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
                CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                    SelectionContainer {
                        MessageContentView(
                            message.content,
                            showReasoning = showReasoning,
                            showAudioTranscriptions = showAudioTranscriptions,
                            mediaServices = mediaServices,
                            isStreaming = isStreaming,
                        )
                    }
                }
                if (message.isEligibleForTextToSpeech(isStreaming) || onReport != null) {
                    Row(modifier = Modifier.align(Alignment.End)) {
                        onReport?.let { report ->
                            IconButton(
                                onClick = report,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Flag,
                                    contentDescription = stringResource(R.string.report_response_action),
                                )
                            }
                        }
                        if (message.isEligibleForTextToSpeech(isStreaming)) {
                            IconButton(
                                onClick = onToggleSpeech,
                                enabled = isSpeaking || isSpeechPrepared,
                                modifier = Modifier.size(36.dp),
                            ) {
                                val speechIcon = if (isSpeaking) {
                                    Icons.Filled.Stop
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                }
                                val speechDescription = if (isSpeaking) {
                                    R.string.stop_response_speech
                                } else {
                                    R.string.play_response_speech
                                }
                                Icon(
                                    imageVector = speechIcon,
                                    contentDescription = stringResource(speechDescription),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("LongMethod")
internal fun MessageContentView(
    content: MessageContent,
    showReasoning: Boolean,
    showAudioTranscriptions: Boolean,
    mediaServices: ChatMediaServices,
    isStreaming: Boolean = false,
) {
    when (content) {
        is MessageContent.TextPrompt -> {
            val uriHandler = LocalUriHandler.current
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                text = stringResource(R.string.chat_reasoning),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            MarkdownText(text = content.reasoningText)
                        }
                    }
                }
                content.imageAttachments.forEach { image ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        HistoricalImage(
                            uri = image.uri,
                            label = image.displayName ?: stringResource(R.string.chat_attachment_image),
                            mediaServices = mediaServices,
                        )
                        Text(
                            text = image.displayName ?: stringResource(R.string.chat_attachment_image),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                if (content.completionStatus == AssistantCompletionStatus.Incomplete && !isStreaming) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                stringResource(R.string.chat_incomplete_response),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(R.string.chat_no_final_answer),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (content.text.isNotBlank()) {
                    MarkdownText(text = content.text)
                } else if (content.completionStatus == AssistantCompletionStatus.Complete) {
                    MarkdownText(text = "...")
                }
                if (content.sources.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.chat_sources),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    content.sources.forEach { source ->
                        Text(
                            text = "${source.title} · ${source.language.uppercase()}",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) {
                                    uriHandler.openUri(source.canonicalUrl)
                                }.padding(vertical = 6.dp),
                        )
                    }
                }
            }
        }
        is MessageContent.AudioPromptContent -> AudioPromptContentView(
            content = content,
            showAudioTranscriptions = showAudioTranscriptions,
            mediaServices = mediaServices,
        )
    }
}

@Composable
private fun AudioPromptContentView(
    content: MessageContent.AudioPromptContent,
    showAudioTranscriptions: Boolean,
    mediaServices: ChatMediaServices,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        content.imageAttachments.forEach { image ->
            HistoricalImage(
                uri = image.uri,
                label = image.displayName ?: stringResource(R.string.chat_attachment_image),
                mediaServices = mediaServices,
            )
            Text(
                text = image.displayName ?: stringResource(R.string.chat_attachment_image),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        AudioPlaybackRow(audio = content.audio, mediaServices = mediaServices)
        when (content.transcriptionStatus) {
            AudioTranscriptionStatus.Pending -> Text(
                stringResource(R.string.chat_transcribing),
                style = MaterialTheme.typography.labelMedium,
            )
            AudioTranscriptionStatus.Completed -> CompletedTranscriptionView(content, showAudioTranscriptions)
            AudioTranscriptionStatus.Failed -> TranscriptionFailureView(content)
            AudioTranscriptionStatus.NotRequested -> Unit
        }
    }
}

@Composable
private fun CompletedTranscriptionView(
    content: MessageContent.AudioPromptContent,
    showAudioTranscriptions: Boolean,
) {
    if (showAudioTranscriptions) MarkdownText(content.transcript.orEmpty())
    if (content.transcriptionMayBeIncomplete) {
        Text(
            stringResource(R.string.chat_transcription_incomplete),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun TranscriptionFailureView(content: MessageContent.AudioPromptContent) {
    Text(
        content.transcriptionError ?: stringResource(R.string.chat_transcription_failed),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
private fun HistoricalImage(
    uri: String,
    label: String,
    mediaServices: ChatMediaServices,
) {
    var expanded by remember(uri) { mutableStateOf(false) }
    ImageThumbnail(
        uri = uri,
        sizeDp = 156,
        mediaServices = mediaServices,
        contentDescription = label,
        onClick = { expanded = true },
    )
    if (expanded) {
        EnlargedImageDialog(
            uri = uri,
            label = label,
            mediaServices = mediaServices,
            onDismiss = { expanded = false },
        )
    }
}

@Composable
private fun EnlargedImageDialog(
    uri: String,
    label: String,
    mediaServices: ChatMediaServices,
    onDismiss: () -> Unit,
) {
    val bitmap = remember(uri, mediaServices.imageDecoder) {
        mediaServices.imageDecoder.decodeThumbnail(uri, 2_048)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = label,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(it.width.toFloat() / it.height.coerceAtLeast(1))
                        .testTag("expanded-chat-image"),
                    contentScale = ContentScale.Fit,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
internal fun ImageThumbnail(
    uri: String,
    sizeDp: Int,
    mediaServices: ChatMediaServices,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val bitmap = remember(uri, mediaServices.imageDecoder) {
        mediaServices.imageDecoder.decodeThumbnail(uri, 256)
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = contentDescription,
            modifier =
            Modifier
                .size(sizeDp.dp)
                .then(
                    if (onClick == null) {
                        Modifier
                    } else {
                        Modifier.clickable(role = Role.Button, onClick = onClick)
                    },
                ),
            contentScale = ContentScale.Crop,
        )
    }
}
