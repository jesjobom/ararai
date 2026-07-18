package com.jesjobom.ararai.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.jesjobom.ararai.R
import com.jesjobom.ararai.chat.AudioPrompt
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
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Remove")
        }
    }
}

@Composable
internal fun AudioPlaybackRow(
    audio: AudioPrompt,
    compact: Boolean = false,
    mediaServices: ChatMediaServices,
) {
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
internal fun MessageRow(
    message: ChatMessage,
    showReasoning: Boolean,
    mediaServices: ChatMediaServices,
    isStreaming: Boolean = false,
    isSpeaking: Boolean = false,
    onToggleSpeech: () -> Unit = {},
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
                            mediaServices = mediaServices,
                        )
                    }
                }
                if (message.isEligibleForTextToSpeech(isStreaming)) {
                    IconButton(
                        onClick = onToggleSpeech,
                        modifier = Modifier
                            .align(Alignment.End)
                            .size(36.dp),
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = stringResource(
                                if (isSpeaking) R.string.stop_response_speech else R.string.play_response_speech,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MessageContentView(
    content: MessageContent,
    showReasoning: Boolean,
    mediaServices: ChatMediaServices,
) {
    when (content) {
        is MessageContent.TextPrompt -> {
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
                                text = "Reasoning",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            MarkdownText(text = content.reasoningText)
                        }
                    }
                }
                content.imageAttachments.forEach { image ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ImageThumbnail(uri = image.uri, sizeDp = 156, mediaServices = mediaServices)
                        Text(
                            text = image.displayName ?: "Image",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                MarkdownText(text = content.text.ifBlank { "..." })
            }
        }
        is MessageContent.AudioPromptContent -> {
            AudioPlaybackRow(audio = content.audio, mediaServices = mediaServices)
        }
    }
}

@Composable
internal fun ImageThumbnail(
    uri: String,
    sizeDp: Int,
    mediaServices: ChatMediaServices,
) {
    val bitmap = remember(uri, mediaServices.imageDecoder) {
        mediaServices.imageDecoder.decodeThumbnail(uri, 256)
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(sizeDp.dp),
            contentScale = ContentScale.Crop,
        )
    }
}
