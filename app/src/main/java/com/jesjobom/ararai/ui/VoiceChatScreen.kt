@file:Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod", "MaxLineLength")

package com.jesjobom.ararai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.R
import com.jesjobom.ararai.voice.VadMode
import com.jesjobom.ararai.voice.VadProvider
import com.jesjobom.ararai.voice.VoiceCaptureSource
import com.jesjobom.ararai.voice.VoiceChatPhase
import com.jesjobom.ararai.voice.VoiceChatSettings
import com.jesjobom.ararai.voice.VoiceChatUiState
import java.util.Locale
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun VoiceChatScreen(
    state: VoiceChatUiState,
    onEnter: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDismissError: () -> Unit,
    onSettings: (VoiceChatSettings) -> Unit,
    onCreateSession: () -> Unit = {},
    onSelectSession: (String) -> Unit = {},
    onRenameSession: (String, String) -> Unit = { _, _ -> },
    onDeleteSession: (String) -> Unit = {},
    onClearAllSessions: () -> Unit = {},
    onOpenModels: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var showFullResponse by remember { mutableStateOf(false) }
    var sessionListOpen by remember { mutableStateOf(false) }
    var clearSessionsConfirmationOpen by remember { mutableStateOf(false) }
    var renameDialogOpen by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var renameSessionId by remember { mutableStateOf<String?>(null) }
    val currentSessionTitle =
        state.sessions.firstOrNull { it.id == state.selectedSessionId }?.title
            ?: stringResource(R.string.voice_new_chat)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onStart()
    }
    DisposableEffect(Unit) {
        onEnter()
        onDispose(onStop)
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.voice_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSettings = true },
                        enabled = !state.isActive,
                    ) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.voice_settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            SessionListButton(
                title = currentSessionTitle,
                isBusy = state.isActive || state.isLoadingModel,
                onClick = { sessionListOpen = true },
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(phaseLabel(state.phase), style = MaterialTheme.typography.headlineSmall)
                Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                    if (state.researchInProgress) {
                        Text(
                            state.activeKnowledgeToolName
                                ?.takeIf(String::isNotBlank)
                                ?.let { stringResource(R.string.chat_using_named_tool, it) }
                                ?: stringResource(R.string.chat_using_tool),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (state.isLoadingModel) Text(stringResource(R.string.voice_loading_model))
                if (!state.modelSupportsAudio && !state.transcriptionAvailable) {
                    Text(stringResource(R.string.voice_model_requirement))
                    OutlinedButton(onClick = onOpenModels) { Text(stringResource(R.string.voice_manage_models)) }
                }
                (state.noticeKey?.localizedText() ?: state.notice)?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary)
                }
                (state.errorKey?.localizedText() ?: state.error)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                if (state.responsePreview.isNotBlank()) {
                    ResponseReadingViewport(
                        text = state.responsePreview,
                        spokenRange = state.spokenRange,
                        readingAnchor = state.readingAnchor,
                        onExpand = { showFullResponse = true },
                    )
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Button(
                    onClick = {
                        if (state.isActive) {
                            onStop()
                        } else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            onStart()
                        } else {
                            permission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    enabled = state.isActive || state.canStart || state.phase == VoiceChatPhase.Error,
                    modifier = Modifier.size(176.dp),
                ) {
                    Icon(if (state.isActive) Icons.Filled.Close else Icons.Filled.Mic, null, Modifier.size(48.dp))
                    Text(
                        stringResource(if (state.isActive) R.string.voice_stop else R.string.voice_start),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
    if (state.phase == VoiceChatPhase.Error) {
        AlertDialog(
            onDismissRequest = onDismissError,
            confirmButton = { TextButton(onClick = onDismissError) { Text(stringResource(R.string.action_close)) } },
            title = { Text(stringResource(R.string.voice_stopped_title)) },
            text = { Text(state.errorKey?.localizedText() ?: state.error.orEmpty()) },
        )
    }
    if (showSettings) {
        VoiceChatSettingsDialog(
            initial = state.settings,
            canEnableReasoning = state.canEnableReasoning,
            onDismiss = { showSettings = false },
            onSettingsChange = onSettings,
        )
    }
    if (showFullResponse) {
        ExpandedResponseDialog(
            text = state.responsePreview,
            spokenRange = state.spokenRange,
            readingAnchor = state.readingAnchor,
            onDismiss = { showFullResponse = false },
        )
    }
    if (sessionListOpen) {
        SessionListDialog(
            sessions = state.sessions,
            selectedSessionId = state.selectedSessionId,
            canDelete = state.canDeleteCurrentSession,
            onDismiss = { sessionListOpen = false },
            onCreate = {
                onCreateSession()
                sessionListOpen = false
            },
            onSelect = {
                onSelectSession(it)
                sessionListOpen = false
            },
            onRename = { session ->
                renameSessionId = session.id
                renameText = session.title
                renameDialogOpen = true
                sessionListOpen = false
            },
            onDelete = onDeleteSession,
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
                onClearAllSessions()
                clearSessionsConfirmationOpen = false
            },
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
                renameSessionId?.let { onRenameSession(it, renameText) }
                renameSessionId = null
                renameDialogOpen = false
            },
        )
    }
}

@Composable
private fun ResponseReadingViewport(
    text: String,
    spokenRange: IntRange?,
    readingAnchor: Int,
    onExpand: () -> Unit,
) {
    Surface(
        onClick = onExpand,
        modifier = Modifier.fillMaxWidth().testTag("voice-response-preview"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        FollowingResponseText(
            text = text,
            spokenRange = spokenRange,
            readingAnchor = readingAnchor,
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ExpandedResponseDialog(
    text: String,
    spokenRange: IntRange?,
    readingAnchor: Int,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.voice_response)) },
        text = {
            FollowingResponseText(
                text = text,
                spokenRange = spokenRange,
                readingAnchor = readingAnchor,
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun FollowingResponseText(
    text: String,
    spokenRange: IntRange?,
    readingAnchor: Int,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var layoutResult by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    val highlightColor = MaterialTheme.colorScheme.secondaryContainer
    val annotatedText = remember(text, spokenRange, highlightColor) {
        buildAnnotatedString {
            val range = spokenRange?.boundedBy(text.length)
            if (range == null) {
                append(text)
            } else {
                append(text.substring(0, range.first))
                withStyle(SpanStyle(background = highlightColor)) {
                    append(text.substring(range.first, range.last + 1))
                }
                append(text.substring(range.last + 1))
            }
        }
    }
    LaunchedEffect(readingAnchor, layoutResult, scrollState.maxValue) {
        val layout = layoutResult ?: return@LaunchedEffect
        if (text.isEmpty()) return@LaunchedEffect
        val line = layout.getLineForOffset(readingAnchor.coerceIn(0, text.lastIndex))
        scrollState.animateScrollTo(layout.getLineTop(line).toInt().coerceIn(0, scrollState.maxValue))
    }
    Text(
        text = annotatedText,
        modifier = modifier.verticalScroll(scrollState),
        style = MaterialTheme.typography.bodyLarge,
        onTextLayout = { layoutResult = it },
    )
}

private fun IntRange.boundedBy(textLength: Int): IntRange? {
    if (textLength <= 0 || first >= textLength || last < 0) return null
    return first.coerceAtLeast(0)..last.coerceAtMost(textLength - 1)
}

@Composable
private fun VoiceChatSettingsDialog(
    initial: VoiceChatSettings,
    canEnableReasoning: Boolean,
    onDismiss: () -> Unit,
    onSettingsChange: (VoiceChatSettings) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    val updateValue: (VoiceChatSettings) -> Unit = {
        value = it
        onSettingsChange(it)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.voice_settings)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.voice_enable_reasoning))
                        if (!canEnableReasoning) {
                            Text(
                                stringResource(R.string.chat_unavailable_for_model),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = value.reasoningEnabled && canEnableReasoning,
                        enabled = canEnableReasoning,
                        onCheckedChange = { updateValue(value.copy(reasoningEnabled = it)) },
                        modifier = Modifier.testTag("voice-reasoning-switch"),
                    )
                }
                Text(stringResource(R.string.voice_reading_speed, value.speechRateMultiplier.asRateLabel()))
                Slider(
                    value = value.speechRateMultiplier,
                    modifier = Modifier.testTag("voice-speech-rate-slider"),
                    onValueChange = { raw ->
                        val snapped = (raw * 10f).roundToInt() / 10f
                        updateValue(
                            value.copy(
                                speechRateMultiplier = snapped.coerceIn(
                                    VoiceChatSettings.MIN_SPEECH_RATE,
                                    VoiceChatSettings.MAX_SPEECH_RATE,
                                ),
                            ),
                        )
                    },
                    valueRange = VoiceChatSettings.MIN_SPEECH_RATE..VoiceChatSettings.MAX_SPEECH_RATE,
                    steps = (
                        (VoiceChatSettings.MAX_SPEECH_RATE - VoiceChatSettings.MIN_SPEECH_RATE) /
                            VoiceChatSettings.SPEECH_RATE_STEP
                        ).roundToInt() - 1,
                )
                Text(stringResource(R.string.voice_pause_before_answer, value.pauseMillis))
                Text(
                    stringResource(R.string.voice_pause_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(value = value.pauseMillis.toFloat(), onValueChange = { raw ->
                    val step = ((raw.toInt() - 500) / 250) * 250 + 500
                    updateValue(value.copy(pauseMillis = step.coerceIn(500, 5_000)))
                }, valueRange = 500f..5_000f, steps = 17)
                Text(stringResource(R.string.voice_minimum_response_words, value.minimumWords))
                Slider(value = value.minimumWords.toFloat(), onValueChange = { updateValue(value.copy(minimumWords = it.toInt().coerceIn(1, 100))) }, valueRange = 1f..100f, steps = 98)
                SettingsDropdown(
                    label = stringResource(R.string.voice_experimental_vad),
                    selected = value.vadProvider,
                    options = VadProvider.entries,
                    optionLabel = { it.displayName() },
                    onSelect = { updateValue(value.copy(vadProvider = it)) },
                )
                SettingsDropdown(
                    label = stringResource(R.string.voice_vad_sensitivity),
                    selected = value.vadMode,
                    options = VadMode.entries,
                    optionLabel = { it.displayName() },
                    onSelect = { updateValue(value.copy(vadMode = it)) },
                )
                AudioTimingSlider(
                    label = stringResource(R.string.voice_speech_confirmation),
                    value = value.speechConfirmationMillis,
                    range = VoiceChatSettings.MIN_SPEECH_CONFIRMATION_MILLIS..VoiceChatSettings.MAX_SPEECH_CONFIRMATION_MILLIS,
                    onValue = { updateValue(value.copy(speechConfirmationMillis = it)) },
                )
                AudioTimingSlider(
                    label = stringResource(R.string.voice_pre_roll),
                    value = value.preRollMillis,
                    range = VoiceChatSettings.MIN_PRE_ROLL_MILLIS..VoiceChatSettings.MAX_PRE_ROLL_MILLIS,
                    onValue = { updateValue(value.copy(preRollMillis = it)) },
                )
                AudioTimingSlider(
                    label = stringResource(R.string.voice_minimum_speech),
                    value = value.minimumSpeechMillis,
                    range = VoiceChatSettings.MIN_SPEECH_MILLIS..VoiceChatSettings.MAX_SPEECH_MILLIS,
                    onValue = { updateValue(value.copy(minimumSpeechMillis = it)) },
                )
                SettingsDropdown(
                    label = stringResource(R.string.voice_capture_source),
                    selected = value.captureSource,
                    options = VoiceCaptureSource.entries,
                    optionLabel = { it.displayName() },
                    onSelect = { updateValue(value.copy(captureSource = it)) },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.voice_noise_suppression), modifier = Modifier.weight(1f))
                    Switch(checked = value.noiseSuppressionRequested, onCheckedChange = { updateValue(value.copy(noiseSuppressionRequested = it)) })
                }
                Text(stringResource(R.string.voice_experimental_restart_note), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        dismissButton = {
            TextButton(onClick = { updateValue(VoiceChatSettings()) }) { Text(stringResource(R.string.action_reset)) }
        },
    )
}

@Composable
private fun <T> SettingsDropdown(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(optionLabel(selected), modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

private fun Float.asRateLabel(): String = String.format(Locale.US, "%.1fx", this)

@Composable
private fun VadProvider.displayName(): String = when (this) {
    VadProvider.WebRtc -> "WebRTC"
    VadProvider.Silero -> "Silero"
}

@Composable
private fun VadMode.displayName(): String = when (this) {
    VadMode.Normal -> stringResource(R.string.voice_vad_normal)
    VadMode.Aggressive -> stringResource(R.string.voice_vad_aggressive)
    VadMode.VeryAggressive -> stringResource(R.string.voice_vad_very_aggressive)
}

@Composable
private fun VoiceCaptureSource.displayName(): String = when (this) {
    VoiceCaptureSource.Microphone -> stringResource(R.string.voice_capture_microphone)
    VoiceCaptureSource.VoiceRecognition -> stringResource(R.string.voice_capture_recognition)
    VoiceCaptureSource.VoiceCommunication -> stringResource(R.string.voice_capture_communication)
}

@Composable
private fun AudioTimingSlider(label: String, value: Int, range: IntRange, onValue: (Int) -> Unit) {
    Text(stringResource(R.string.voice_timing_millis, label, value))
    Slider(
        value = value.toFloat(),
        onValueChange = { raw ->
            val step = VoiceChatSettings.AUDIO_TIMING_STEP_MILLIS
            val snapped = ((raw.toInt() - range.first) / step) * step + range.first
            onValue(snapped.coerceIn(range))
        },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = (range.last - range.first) / VoiceChatSettings.AUDIO_TIMING_STEP_MILLIS - 1,
    )
}

@Composable
private fun phaseLabel(phase: VoiceChatPhase) = when (phase) {
    VoiceChatPhase.Idle -> stringResource(R.string.voice_phase_ready)
    VoiceChatPhase.Listening -> stringResource(R.string.voice_phase_listening)
    VoiceChatPhase.Processing -> stringResource(R.string.voice_phase_thinking)
    VoiceChatPhase.Speaking -> stringResource(R.string.voice_phase_speaking)
    VoiceChatPhase.Error -> stringResource(R.string.voice_phase_stopped)
}
