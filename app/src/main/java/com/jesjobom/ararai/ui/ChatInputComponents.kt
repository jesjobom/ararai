package com.jesjobom.ararai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.chat.AudioPrompt
import com.jesjobom.ararai.chat.ImageAttachment
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ChatInputBar(
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
    mediaServices: ChatMediaServices,
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        var imageImportError by remember { mutableStateOf<String?>(null) }
        var isImportingImage by remember { mutableStateOf(false) }
        var audioRecorderOpen by remember { mutableStateOf(false) }
        var activeRecorder by remember { mutableStateOf<ChatAudioRecording?>(null) }
        var activeRecordingFile by remember { mutableStateOf<File?>(null) }
        var recordingStartedAtMillis by remember { mutableStateOf(0L) }
        var recordingDurationMillis by remember { mutableStateOf(0L) }
        var recordedAudio by remember { mutableStateOf<RecordedAudio?>(null) }
        var recordingError by remember { mutableStateOf<String?>(null) }
        fun discardRecording() {
            activeRecorder?.stopSafely()
            activeRecorder = null
            activeRecordingFile?.absolutePath?.let(mediaServices.draftCleaner::delete)
            activeRecordingFile = null
            recordedAudio?.file?.absolutePath?.let(mediaServices.draftCleaner::delete)
            recordedAudio = null
            recordingStartedAtMillis = 0L
            recordingDurationMillis = 0L
        }
        fun startAudioRecording() {
            discardRecording()
            try {
                val recorder = mediaServices.audioRecorderFactory.create()
                val file = recorder.file
                activeRecordingFile = file
                activeRecorder = recorder
                recorder.start()
                recordingStartedAtMillis = SystemClock.elapsedRealtime()
                recordingDurationMillis = 0L
                recordingError = null
            } catch (error: Throwable) {
                activeRecorder?.stopSafely()
                activeRecorder = null
                activeRecordingFile?.absolutePath?.let(mediaServices.draftCleaner::delete)
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
                mediaServices.draftCleaner.delete(file.absolutePath)
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
            imageImportError = null
            isImportingImage = true
            coroutineScope.launch {
                try {
                    val imported = withContext(Dispatchers.IO) { mediaServices.imageImporter.import(uri) }
                    onAttachImage(
                        ImageAttachment(
                            uri = imported.file.absolutePath,
                            mimeType = "image/jpeg",
                            displayName = imported.displayName,
                            byteSize = imported.file.length(),
                        ),
                    )
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    imageImportError = error.message ?: "Unable to import selected image"
                } finally {
                    isImportingImage = false
                }
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                discardRecording()
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
            (imageImportError ?: error)?.let {
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
            } else {
                if (imageAttachments.isNotEmpty() || audioPrompt != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        imageAttachments.forEach { image ->
                            AttachmentRow(
                                label = image.displayName ?: "Image",
                                imageUri = image.uri,
                                onRemove = { onRemoveImage(image.uri) },
                                mediaServices = mediaServices,
                            )
                        }
                        audioPrompt?.let { audio ->
                            AttachmentRow(
                                label = audio.displayName ?: "Audio prompt",
                                onRemove = onClearAudioPrompt,
                                mediaServices = mediaServices,
                            )
                        }
                    }
                }

                if (canAttachImage || canUseAudioPrompt) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (canAttachImage && audioPrompt == null) {
                            OutlinedButton(
                                onClick = { imagePicker.launch(arrayOf("image/*")) },
                                enabled = !isImportingImage,
                            ) {
                                Icon(imageVector = Icons.Filled.AttachFile, contentDescription = null)
                                Text(
                                    text = "Image",
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                        }
                        if (canUseAudioPrompt && imageAttachments.isEmpty() && prompt.isBlank()) {
                            OutlinedButton(onClick = ::openAudioRecorder) {
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
                    enabled = audioPrompt == null,
                    trailingIcon = {
                        FilledIconButton(
                            onClick = onSubmit,
                            enabled = canSubmit,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                            )
                        }
                    },
                )
            }
        }

        if (audioRecorderOpen) {
            AudioRecorderDialog(
                isRecording = activeRecorder != null,
                recordedAudio = recordedAudio,
                mediaServices = mediaServices,
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
internal fun AudioRecorderDialog(
    isRecording: Boolean,
    recordedAudio: RecordedAudio?,
    recordingDurationMillis: Long,
    error: String?,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onUseRecording: () -> Unit,
    onDismiss: () -> Unit,
    mediaServices: ChatMediaServices,
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
                            mediaServices = mediaServices,
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
