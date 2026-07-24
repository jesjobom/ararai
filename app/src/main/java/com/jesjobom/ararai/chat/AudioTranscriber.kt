@file:Suppress("TooGenericExceptionCaught", "TooManyFunctions")

package com.jesjobom.ararai.chat

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface AudioTranscriber {
    val isAvailable: Boolean
    suspend fun transcribe(audio: AudioPrompt): AudioTranscriptionResult
}

data class AudioTranscriptionResult(
    val transcript: String,
    val diagnosticReport: String,
    val mayBeIncomplete: Boolean = false,
    val incompleteReason: String? = null,
)

data class AudioTranscriptionFailure(
    val kind: AudioTranscriptionFailureKind,
    val userMessage: String,
    val diagnosticReport: String,
    val recognizerErrorCode: Int? = null,
)

class AudioTranscriptionException(
    val failure: AudioTranscriptionFailure,
    cause: Throwable? = null,
) : IllegalStateException(failure.userMessage, cause)

object UnavailableAudioTranscriber : AudioTranscriber {
    override val isAvailable = false
    override suspend fun transcribe(audio: AudioPrompt): AudioTranscriptionResult = throw AudioTranscriptionException(
        AudioTranscriptionFailure(
            kind = AudioTranscriptionFailureKind.Unavailable,
            userMessage = "Local audio transcription is unavailable",
            diagnosticReport = "failure_kind=Unavailable",
        ),
    )
}

class AndroidOnDeviceAudioTranscriber(
    context: Context,
    private val locale: () -> Locale = { Locale.getDefault() },
) : AudioTranscriber {
    private val appContext = context.applicationContext

    override val isAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)

    override suspend fun transcribe(audio: AudioPrompt): AudioTranscriptionResult {
        val requestedLocale = locale()
        if (!isAvailable) {
            throw failure(
                kind = AudioTranscriptionFailureKind.Unavailable,
                message = "On-device audio transcription is unavailable",
                locale = requestedLocale,
            )
        }
        val file = File(audio.uri.removePrefix("file://").removePrefix("file:"))
        if (!file.isFile) {
            throw failure(
                kind = AudioTranscriptionFailureKind.InvalidAudio,
                message = "Recorded audio is unavailable",
                locale = requestedLocale,
                file = file,
            )
        }
        val wav = try {
            inspectWav(file)
        } catch (error: Throwable) {
            throw failure(
                kind = AudioTranscriptionFailureKind.InvalidAudio,
                message = "Recorded audio is not a supported PCM WAV",
                locale = requestedLocale,
                file = file,
                cause = error,
            )
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withContext(Dispatchers.Main.immediate) {
                recognizeAdaptively(file, wav, requestedLocale)
            }
        } else {
            throw failure(
                kind = AudioTranscriptionFailureKind.Unavailable,
                message = "On-device audio transcription requires Android 13 or newer",
                locale = requestedLocale,
                file = file,
                wav = wav,
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Suppress("SwallowedException") // Fast failure is retained in the combined sanitized report and as suppressed.
    private suspend fun recognizeAdaptively(
        file: File,
        wav: WavMetadata,
        requestedLocale: Locale,
    ): AudioTranscriptionResult = try {
        recognize(file, wav, requestedLocale, AudioDeliveryMode.Fast)
    } catch (fastError: AudioTranscriptionException) {
        if (!fastError.failure.shouldRetryWithPacing()) throw fastError
        try {
            recognize(file, wav, requestedLocale, AudioDeliveryMode.Paced)
        } catch (pacedError: AudioTranscriptionException) {
            pacedError.addSuppressed(fastError)
            throw AudioTranscriptionException(
                pacedError.failure.copy(
                    diagnosticReport = buildString {
                        appendLine("fast_attempt_begin")
                        appendLine(fastError.failure.diagnosticReport)
                        appendLine("fast_attempt_end")
                        appendLine("paced_attempt_begin")
                        appendLine(pacedError.failure.diagnosticReport)
                        append("paced_attempt_end")
                    },
                ),
                pacedError,
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private suspend fun recognize(
        file: File,
        wav: WavMetadata,
        requestedLocale: Locale,
        deliveryMode: AudioDeliveryMode,
    ): AudioTranscriptionResult = suspendCancellableCoroutine { continuation ->
        val pipe = ParcelFileDescriptor.createPipe()
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        val handler = Handler(Looper.getMainLooper())
        val trace = DiagnosticTrace(requestedLocale, file, wav, deliveryMode)
        val streamingStarted = AtomicBoolean(false)
        val listeningStarted = AtomicBoolean(false)
        val segments = mutableListOf<String>()
        var finished = false

        fun closeResources() {
            handler.removeCallbacksAndMessages(null)
            runCatching { pipe[0].close() }
            runCatching { pipe[1].close() }
            recognizer.destroy()
        }

        fun fail(
            kind: AudioTranscriptionFailureKind,
            message: String,
            cause: Throwable? = null,
            recognizerErrorCode: Int? = null,
        ) {
            if (finished || !continuation.isActive) return
            finished = true
            trace.record("failure:${kind.name}")
            closeResources()
            continuation.resumeWithException(
                AudioTranscriptionException(
                    AudioTranscriptionFailure(kind, message, trace.report(cause), recognizerErrorCode),
                    cause,
                ),
            )
        }

        fun complete(transcript: String, source: String) {
            if (finished || !continuation.isActive) return
            val normalized = normalizeTranscriptionText(transcript)
            if (normalized.isBlank()) {
                fail(AudioTranscriptionFailureKind.EmptyResults, "No speech was recognized")
                return
            }
            finished = true
            trace.record("complete:$source,segments=${segments.size}")
            val assessment = trace.assessSuccess(source)
            val result = AudioTranscriptionResult(
                transcript = normalized,
                diagnosticReport = trace.successReport(source, assessment),
                mayBeIncomplete = assessment.mayBeIncomplete,
                incompleteReason = assessment.reason,
            )
            closeResources()
            continuation.resume(result)
        }

        fun startStreaming() {
            if (!streamingStarted.compareAndSet(false, true)) return
            thread(name = "ChatAudioTranscription", isDaemon = true) {
                try {
                    var copied = 0L
                    val streamStartedAt = SystemClock.elapsedRealtime()
                    ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                        FileInputStream(file).use { input ->
                            check(input.skip(wav.dataOffset) == wav.dataOffset) {
                                "Unable to reach WAV audio data"
                            }
                            val buffer = ByteArray(
                                if (deliveryMode == AudioDeliveryMode.Paced) {
                                    wav.pcmChunkBytes(STREAM_CHUNK_MILLIS)
                                } else {
                                    DEFAULT_BUFFER_SIZE
                                },
                            )
                            var remaining = wav.dataBytes
                            while (remaining > 0L) {
                                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                copied += read
                                remaining -= read
                                if (deliveryMode == AudioDeliveryMode.Paced) {
                                    val expectedElapsed = copied * 1_000L / wav.bytesPerSecond
                                    val elapsed = SystemClock.elapsedRealtime() - streamStartedAt
                                    val waitMillis = expectedElapsed - elapsed
                                    if (waitMillis > 0L) SystemClock.sleep(waitMillis)
                                }
                            }
                        }
                    }
                    trace.recordPipeComplete(copied, SystemClock.elapsedRealtime() - streamStartedAt)
                } catch (error: Throwable) {
                    trace.record("pipe_error:${error.javaClass.simpleName}")
                    handler.post {
                        fail(
                            AudioTranscriptionFailureKind.PipeWriteFailure,
                            "Unable to send recorded audio to the on-device recognizer",
                            error,
                        )
                    }
                }
            }
        }

        recognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val hypotheses = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val confidenceCount = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.size ?: 0
                    trace.record("results:hypotheses=${hypotheses.size},confidence_scores=$confidenceCount")
                    val transcript = hypotheses.firstOrNull()?.trim().orEmpty()
                    if (transcript.isNotBlank()) complete(transcript, "standard_results")
                }

                override fun onSegmentResults(segmentResults: Bundle) {
                    val hypotheses =
                        segmentResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val transcript = hypotheses.firstOrNull()?.trim().orEmpty()
                    trace.record("segment_result:index=${segments.size},hypotheses=${hypotheses.size}")
                    if (transcript.isNotBlank()) segments += transcript
                }

                override fun onEndOfSegmentedSession() {
                    trace.record("segmented_session_end:segments=${segments.size}")
                    complete(aggregateTranscriptionSegments(segments), "segmented_session")
                }

                override fun onError(error: Int) {
                    trace.record("recognizer_error:${recognizerErrorName(error)}($error)")
                    if (segments.isNotEmpty()) {
                        complete(aggregateTranscriptionSegments(segments), "segments_before_error")
                    } else {
                        fail(
                            AudioTranscriptionFailureKind.RecognizerError,
                            "On-device transcription failed: ${recognizerErrorName(error)} ($error)",
                            recognizerErrorCode = error,
                        )
                    }
                }

                override fun onReadyForSpeech(params: Bundle?) {
                    trace.record("ready_for_speech")
                    startStreaming()
                }
                override fun onBeginningOfSpeech() = trace.record("beginning_of_speech")
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = trace.record("buffer_received:${buffer?.size ?: 0}")
                override fun onEndOfSpeech() = trace.record("end_of_speech")
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = trace.record("event:$eventType")
            },
        )

        val intent = createOnDeviceRecognitionIntent(requestedLocale, wav, pipe[0])
        continuation.invokeOnCancellation {
            handler.post {
                if (!finished) {
                    finished = true
                    trace.record("cancelled")
                    recognizer.cancel()
                    closeResources()
                }
            }
        }
        fun startListening() {
            if (finished || !continuation.isActive || !listeningStarted.compareAndSet(false, true)) return
            trace.record("start_listening")
            try {
                recognizer.startListening(intent)
            } catch (error: Throwable) {
                fail(
                    AudioTranscriptionFailureKind.RecognizerError,
                    "Unable to start on-device transcription",
                    error,
                )
                return
            }
            handler.postDelayed(
                {
                    fail(AudioTranscriptionFailureKind.Timeout, "On-device transcription timed out")
                },
                wav.recognitionTimeoutMillis(deliveryMode),
            )
        }

        handler.postDelayed(
            {
                if (!listeningStarted.get()) {
                    trace.record("support_check_timeout")
                    startListening()
                }
            },
            SUPPORT_CHECK_TIMEOUT_MILLIS,
        )
        try {
            recognizer.checkRecognitionSupport(
                intent,
                appContext.mainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                        if (listeningStarted.get() || finished) return
                        val status = onDeviceLanguageSupport(requestedLocale, recognitionSupport)
                        trace.recordLanguageSupport(status, recognitionSupport)
                        when (status) {
                            OnDeviceLanguageSupport.Installed -> startListening()
                            OnDeviceLanguageSupport.Pending -> fail(
                                AudioTranscriptionFailureKind.Unavailable,
                                "On-device language model is still downloading for ${requestedLocale.toLanguageTag()}",
                            )
                            OnDeviceLanguageSupport.Downloadable -> fail(
                                AudioTranscriptionFailureKind.Unavailable,
                                "On-device language model is not installed for ${requestedLocale.toLanguageTag()}",
                            )
                            OnDeviceLanguageSupport.Unsupported -> fail(
                                AudioTranscriptionFailureKind.Unavailable,
                                "On-device transcription does not support ${requestedLocale.toLanguageTag()}",
                            )
                        }
                    }

                    override fun onError(error: Int) {
                        if (listeningStarted.get() || finished) return
                        trace.record("support_check_error:${recognizerErrorName(error)}($error)")
                        startListening()
                    }
                },
            )
        } catch (error: Throwable) {
            trace.record("support_check_exception:${error.javaClass.simpleName}")
            startListening()
        }
    }

    @Suppress("LongParameterList")
    private fun failure(
        kind: AudioTranscriptionFailureKind,
        message: String,
        locale: Locale,
        file: File? = null,
        wav: WavMetadata? = null,
        cause: Throwable? = null,
    ): AudioTranscriptionException {
        val trace = DiagnosticTrace(locale, file, wav)
        trace.record("failure:${kind.name}")
        return AudioTranscriptionException(
            AudioTranscriptionFailure(kind, message, trace.report(cause)),
            cause,
        )
    }

    private companion object {
        const val SUPPORT_CHECK_TIMEOUT_MILLIS = 5_000L
        const val STREAM_CHUNK_MILLIS = 20L
    }
}

internal data class WavMetadata(
    val sampleRate: Int,
    val channelCount: Int,
    val bitsPerSample: Int,
    val dataOffset: Long,
    val dataBytes: Long,
) {
    val bytesPerSecond: Long
        get() = sampleRate.toLong() * channelCount * bitsPerSample / 8L

    val durationMillis: Long
        get() = (dataBytes * 1_000L + bytesPerSecond - 1L) / bytesPerSecond

    fun recognitionTimeoutMillis(deliveryMode: AudioDeliveryMode): Long = when (deliveryMode) {
        AudioDeliveryMode.Fast -> maxOf(10_000L, durationMillis + 5_000L)
        AudioDeliveryMode.Paced -> durationMillis + 15_000L
    }

    fun pcmChunkBytes(durationMillis: Long): Int = (bytesPerSecond * durationMillis / 1_000L).coerceAtLeast(1L).toInt()
}

internal enum class AudioDeliveryMode { Fast, Paced }

internal fun AudioTranscriptionFailure.shouldRetryWithPacing(): Boolean {
    val retryableKind = kind == AudioTranscriptionFailureKind.EmptyResults ||
        kind == AudioTranscriptionFailureKind.Timeout
    val retryableCode = recognizerErrorCode == SpeechRecognizer.ERROR_NO_MATCH ||
        recognizerErrorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
    return retryableKind || retryableCode
}

internal enum class OnDeviceLanguageSupport { Installed, Pending, Downloadable, Unsupported }

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun createOnDeviceRecognitionIntent(
    requestedLocale: Locale,
    wav: WavMetadata,
    audioSource: ParcelFileDescriptor,
): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, requestedLocale.toLanguageTag())
    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, audioSource)
    putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, wav.channelCount)
    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, wav.sampleRate)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun onDeviceLanguageSupport(
    requestedLocale: Locale,
    support: RecognitionSupport,
): OnDeviceLanguageSupport = onDeviceLanguageSupport(
    requestedLocale = requestedLocale,
    installed = support.installedOnDeviceLanguages,
    pending = support.pendingOnDeviceLanguages,
    supported = support.supportedOnDeviceLanguages,
)

internal fun onDeviceLanguageSupport(
    requestedLocale: Locale,
    installed: List<String>,
    pending: List<String>,
    supported: List<String>,
): OnDeviceLanguageSupport = when {
    installed.any { requestedLocale.matchesLanguageTag(it) } -> OnDeviceLanguageSupport.Installed
    pending.any { requestedLocale.matchesLanguageTag(it) } -> OnDeviceLanguageSupport.Pending
    supported.any { requestedLocale.matchesLanguageTag(it) } -> OnDeviceLanguageSupport.Downloadable
    else -> OnDeviceLanguageSupport.Unsupported
}

private fun Locale.matchesLanguageTag(candidate: String): Boolean {
    val normalized = Locale.forLanguageTag(candidate.replace('_', '-'))
    return language.equals(normalized.language, ignoreCase = true) &&
        (country.isBlank() || normalized.country.isBlank() || country.equals(normalized.country, ignoreCase = true))
}

internal fun normalizeTranscriptionText(text: String): String = text.trim().replace(Regex("\\s+"), " ")

internal fun aggregateTranscriptionSegments(segments: List<String>): String = segments
    .map(::normalizeTranscriptionText)
    .filter(String::isNotBlank)
    .joinToString(" ")

internal fun inspectWav(file: File): WavMetadata = RandomAccessFile(file, "r").use { wav ->
    require(wav.readFourCc() == "RIFF") { "Missing RIFF header" }
    wav.readLittleEndianInt()
    require(wav.readFourCc() == "WAVE") { "Missing WAVE header" }
    var format: WavMetadata? = null
    var dataOffset = -1L
    var dataBytes = -1L
    while (wav.filePointer + 8L <= wav.length()) {
        val chunk = wav.readFourCc()
        val chunkSize = wav.readLittleEndianInt().toLong() and 0xffffffffL
        val chunkStart = wav.filePointer
        when (chunk) {
            "fmt " -> {
                require(chunkSize >= 16L) { "Invalid fmt chunk" }
                val encoding = wav.readLittleEndianShort()
                val channels = wav.readLittleEndianShort()
                val sampleRate = wav.readLittleEndianInt()
                wav.seek(wav.filePointer + 6L)
                val bits = wav.readLittleEndianShort()
                require(encoding == 1 && channels > 0 && sampleRate > 0 && bits == 16) {
                    "Only 16-bit PCM WAV is supported"
                }
                format = WavMetadata(sampleRate, channels, bits, 0L, 0L)
            }
            "data" -> {
                dataOffset = chunkStart
                dataBytes = minOf(chunkSize, wav.length() - chunkStart)
            }
        }
        wav.seek(minOf(wav.length(), chunkStart + chunkSize + (chunkSize and 1L)))
    }
    val parsed = requireNotNull(format) { "Missing fmt chunk" }
    require(dataOffset >= 0L && dataBytes > 0L) { "Missing audio data" }
    parsed.copy(dataOffset = dataOffset, dataBytes = dataBytes)
}

private fun RandomAccessFile.readFourCc(): String = ByteArray(4).also(::readFully).toString(Charsets.US_ASCII)

private fun RandomAccessFile.readLittleEndianShort(): Int = readUnsignedByte() or (readUnsignedByte() shl 8)

private fun RandomAccessFile.readLittleEndianInt(): Int = readUnsignedByte() or
    (readUnsignedByte() shl 8) or
    (readUnsignedByte() shl 16) or
    (readUnsignedByte() shl 24)

private class DiagnosticTrace(
    private val locale: Locale,
    private val file: File?,
    private val wav: WavMetadata?,
    private val deliveryMode: AudioDeliveryMode? = null,
) {
    private val startedAt = SystemClock.elapsedRealtime()
    private val events = mutableListOf<String>()
    private var droppedEventCount = 0
    private var streamedBytes: Long? = null
    private var streamDurationMillis: Long? = null

    @Synchronized
    fun record(event: String) {
        if (events.size < MAX_RECORDED_EVENTS) {
            events += "+${SystemClock.elapsedRealtime() - startedAt}ms:$event"
        } else {
            droppedEventCount++
        }
    }

    @Synchronized
    fun recordPipeComplete(bytes: Long, durationMillis: Long) {
        streamedBytes = bytes
        streamDurationMillis = durationMillis
        record("pipe_complete:bytes=$bytes,stream_duration_ms=$durationMillis")
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Synchronized
    fun recordLanguageSupport(status: OnDeviceLanguageSupport, support: RecognitionSupport) {
        record(
            "language_support:status=${status.name},installed=${support.installedOnDeviceLanguages.size}," +
                "pending=${support.pendingOnDeviceLanguages.size},supported=${support.supportedOnDeviceLanguages.size}",
        )
    }

    @Synchronized
    fun report(cause: Throwable? = null): String = buildString {
        appendLine("transcriber=AndroidOnDeviceSpeechRecognizer")
        appendLine("delivery_mode=${deliveryMode?.name ?: "none"}")
        appendLine("android_sdk=${Build.VERSION.SDK_INT}")
        appendLine("device=${sanitize(Build.MANUFACTURER)}/${sanitize(Build.MODEL)}")
        appendLine("locale=${sanitize(locale.toLanguageTag())}")
        appendLine("file_bytes=${file?.takeIf(File::isFile)?.length() ?: -1L}")
        appendLine("wav_sample_rate=${wav?.sampleRate ?: -1}")
        appendLine("wav_channels=${wav?.channelCount ?: -1}")
        appendLine("wav_bits_per_sample=${wav?.bitsPerSample ?: -1}")
        appendLine("wav_data_bytes=${wav?.dataBytes ?: -1L}")
        appendLine("audio_duration_ms=${wav?.durationMillis ?: -1L}")
        appendLine("stream_bytes=${streamedBytes ?: -1L}")
        appendLine("stream_duration_ms=${streamDurationMillis ?: -1L}")
        appendLine("stream_speed_ratio=${streamSpeedRatio()}")
        cause?.let {
            appendLine("cause=${sanitize(it.javaClass.simpleName)}")
        }
        append("events=")
        append(events.joinToString(","))
        if (droppedEventCount > 0) append(",dropped=$droppedEventCount")
    }

    @Synchronized
    fun assessSuccess(source: String): TranscriptionSuccessAssessment = assessTranscriptionSuccess(
        source = source,
        streamedBytes = streamedBytes,
        expectedBytes = wav?.dataBytes,
    )

    @Synchronized
    fun successReport(source: String, assessment: TranscriptionSuccessAssessment): String = buildString {
        appendLine("outcome=success")
        appendLine("completion_source=${sanitize(source)}")
        appendLine("potentially_partial=${assessment.mayBeIncomplete}")
        appendLine("partial_reason=${sanitize(assessment.reason.orEmpty())}")
        append(report())
    }

    private fun sanitize(value: String): String = value
        .replace(Regex("[\\r\\n\\t]"), " ")
        .take(200)

    private fun streamSpeedRatio(): String {
        val expected = wav?.durationMillis
        val actual = streamDurationMillis
        return if (expected == null || actual == null || actual <= 0L) {
            "-1"
        } else {
            String.format(Locale.US, "%.2f", expected.toDouble() / actual)
        }
    }

    private companion object {
        const val MAX_RECORDED_EVENTS = 64
    }
}

internal data class TranscriptionSuccessAssessment(
    val mayBeIncomplete: Boolean,
    val reason: String? = null,
)

internal fun assessTranscriptionSuccess(
    source: String,
    streamedBytes: Long?,
    expectedBytes: Long?,
): TranscriptionSuccessAssessment = when {
    source != "segmented_session" -> TranscriptionSuccessAssessment(
        mayBeIncomplete = true,
        reason = "unexpected_completion_source:$source",
    )
    streamedBytes == null -> TranscriptionSuccessAssessment(
        mayBeIncomplete = true,
        reason = "stream_not_complete",
    )
    streamedBytes != expectedBytes -> TranscriptionSuccessAssessment(
        mayBeIncomplete = true,
        reason = "stream_bytes_mismatch:$streamedBytes/${expectedBytes ?: -1L}",
    )
    else -> TranscriptionSuccessAssessment(mayBeIncomplete = false)
}

@Suppress("CyclomaticComplexMethod")
internal fun recognizerErrorName(error: Int): String = when (error) {
    SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
    SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
    SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
    SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
    SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "ERROR_TOO_MANY_REQUESTS"
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "ERROR_SERVER_DISCONNECTED"
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "ERROR_LANGUAGE_NOT_SUPPORTED"
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "ERROR_LANGUAGE_UNAVAILABLE"
    else -> "ERROR_UNKNOWN"
}
