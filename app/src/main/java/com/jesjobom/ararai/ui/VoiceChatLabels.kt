package com.jesjobom.ararai.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jesjobom.ararai.R
import com.jesjobom.ararai.voice.VadMode
import com.jesjobom.ararai.voice.VadProvider
import com.jesjobom.ararai.voice.VoiceCaptureSource
import com.jesjobom.ararai.voice.VoiceChatPhase
import java.util.Locale

internal fun Float.asRateLabel(): String = String.format(Locale.US, "%.1fx", this)

@Composable
internal fun VadProvider.displayName(): String = when (this) {
    VadProvider.WebRtc -> "WebRTC"
    VadProvider.Silero -> "Silero"
}

@Composable
internal fun VadMode.displayName(): String = when (this) {
    VadMode.Normal -> stringResource(R.string.voice_vad_normal)
    VadMode.Aggressive -> stringResource(R.string.voice_vad_aggressive)
    VadMode.VeryAggressive -> stringResource(R.string.voice_vad_very_aggressive)
}

@Composable
internal fun VoiceCaptureSource.displayName(): String = when (this) {
    VoiceCaptureSource.Microphone -> stringResource(R.string.voice_capture_microphone)
    VoiceCaptureSource.VoiceRecognition -> stringResource(R.string.voice_capture_recognition)
    VoiceCaptureSource.VoiceCommunication -> stringResource(R.string.voice_capture_communication)
}

@Composable
internal fun phaseLabel(phase: VoiceChatPhase): String = when (phase) {
    VoiceChatPhase.Idle -> stringResource(R.string.voice_phase_ready)
    VoiceChatPhase.Listening -> stringResource(R.string.voice_phase_listening)
    VoiceChatPhase.Processing -> stringResource(R.string.voice_phase_thinking)
    VoiceChatPhase.Speaking -> stringResource(R.string.voice_phase_speaking)
    VoiceChatPhase.Error -> stringResource(R.string.voice_phase_stopped)
}
