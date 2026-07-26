package com.jesjobom.ararai.chat

import com.jesjobom.ararai.model.ManagedModelItem
import com.jesjobom.ararai.model.ModelStartupState
import com.jesjobom.ararai.model.ModelTask
import com.jesjobom.ararai.model.supportsTask
import com.jesjobom.ararai.whisper.WhisperRuntime
import com.jesjobom.ararai.whisper.WhisperRuntimeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class WhisperCppAudioTranscriber(
    private val models: () -> List<ManagedModelItem>,
    private val transcribeWithRuntime: (String, String, String, Int) -> WhisperRuntimeResult =
        WhisperRuntime::transcribe,
) : AudioTranscriber {
    override val isAvailable: Boolean
        get() = selectedModel() != null

    @Suppress("TooGenericExceptionCaught") // JNI/runtime failures are normalized at the transcriber boundary.
    override suspend fun transcribe(audio: AudioPrompt): AudioTranscriptionResult {
        val item = selectedModel() ?: throw failure(
            kind = AudioTranscriptionFailureKind.Unavailable,
            message = "Download a Whisper transcription model in Models",
        )
        val available = item.state as ModelStartupState.Available
        val audioFile = File(audio.uri.removePrefix("file://").removePrefix("file:"))
        if (!audioFile.isFile) {
            throw failure(
                kind = AudioTranscriptionFailureKind.InvalidAudio,
                message = "Recorded audio is unavailable",
                modelId = item.config.id,
            )
        }

        val result = try {
            withContext(Dispatchers.Default) {
                transcribeWithRuntime(
                    available.model.filePath,
                    audioFile.absolutePath,
                    AUTO_DETECT_LANGUAGE,
                    TRANSCRIPTION_THREADS,
                )
            }
        } catch (error: RuntimeException) {
            throw failure(
                kind = AudioTranscriptionFailureKind.RecognizerError,
                message = "Whisper could not transcribe this audio",
                modelId = item.config.id,
                cause = error,
            )
        }
        val transcript = result.text.trim().replace(Regex("\\s+"), " ")
        if (transcript.isEmpty()) {
            throw failure(
                kind = AudioTranscriptionFailureKind.EmptyResults,
                message = "No speech was recognized",
                modelId = item.config.id,
            )
        }
        return AudioTranscriptionResult(
            transcript = transcript,
            diagnosticReport = diagnostic(item.config.id, result),
        )
    }

    private fun selectedModel(): ManagedModelItem? = models().firstOrNull {
        it.config.supportsTask(ModelTask.Transcription) && it.state is ModelStartupState.Available
    }

    private fun failure(
        kind: AudioTranscriptionFailureKind,
        message: String,
        modelId: String? = null,
        cause: Throwable? = null,
    ): AudioTranscriptionException = AudioTranscriptionException(
        failure = AudioTranscriptionFailure(
            kind = kind,
            userMessage = message,
            diagnosticReport = buildString {
                appendLine("transcriber=WhisperCpp")
                appendLine("failure_kind=${kind.name}")
                modelId?.let { appendLine("model_id=$it") }
                cause?.let { append("cause=${it::class.java.simpleName}") }
            }.trim(),
        ),
        cause = cause,
    )

    private fun diagnostic(modelId: String, result: WhisperRuntimeResult): String = buildString {
        appendLine("transcriber=WhisperCpp")
        appendLine("model_id=$modelId")
        appendLine("language=$AUTO_DETECT_LANGUAGE")
        appendLine("threads=${result.threads}")
        appendLine("audio_duration_ms=${result.audioMillis}")
        appendLine("load_duration_ms=${result.loadMillis}")
        appendLine("transcription_duration_ms=${result.transcriptionMillis}")
        append("real_time_factor=${String.format(Locale.US, "%.3f", result.realTimeFactor)}")
    }

    private companion object {
        const val AUTO_DETECT_LANGUAGE = "auto"
        const val TRANSCRIPTION_THREADS = 6
    }
}
