@file:Suppress(
    "LongParameterList",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "TooGenericExceptionCaught",
    "LoopWithTooManyJumpStatements",
    "LongMethod",
    "MaxLineLength",
)

package com.jesjobom.ararai.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import com.jesjobom.ararai.chat.AudioPrompt
import com.jesjobom.ararai.ui.recordedAudioPrompt
import com.konovalov.vad.silero.VadSilero
import com.konovalov.vad.webrtc.VadWebRTC
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import com.konovalov.vad.silero.config.FrameSize as SileroFrameSize
import com.konovalov.vad.silero.config.Mode as SileroMode
import com.konovalov.vad.silero.config.SampleRate as SileroSampleRate
import com.konovalov.vad.webrtc.config.FrameSize as WebRtcFrameSize
import com.konovalov.vad.webrtc.config.Mode as WebRtcMode
import com.konovalov.vad.webrtc.config.SampleRate as WebRtcSampleRate

internal interface VoiceActivityDetector : Closeable {
    val frameBytes: Int
    fun isSpeech(frame: ByteArray): Boolean
}

internal class WebRtcVoiceActivityDetector(mode: VadMode) : VoiceActivityDetector {
    private val delegate = VadWebRTC(WebRtcSampleRate.SAMPLE_RATE_16K, WebRtcFrameSize.FRAME_SIZE_320, mode.webRtcMode)
    override val frameBytes = 640
    override fun isSpeech(frame: ByteArray) = delegate.isSpeech(frame)
    override fun close() = delegate.close()
}

internal class SileroVoiceActivityDetector(context: Context, mode: VadMode) : VoiceActivityDetector {
    private val delegate = VadSilero(context, SileroSampleRate.SAMPLE_RATE_16K, SileroFrameSize.FRAME_SIZE_512, mode.sileroMode)
    override val frameBytes = 1_024
    override fun isSpeech(frame: ByteArray) = delegate.isSpeech(frame)
    override fun close() = delegate.close()
}

data class CapturedVoiceTurn(
    val prompt: AudioPrompt,
    val speechMillis: Long,
    val noiseSuppressionActive: Boolean,
)

interface VoiceTurnCapture : Closeable {
    fun start(onTurn: (CapturedVoiceTurn) -> Unit, onError: (String) -> Unit)
    fun cancel()
}

class AndroidVoiceTurnCapture(
    private val context: Context,
    private val directory: File,
    private val settings: VoiceChatSettings,
) : VoiceTurnCapture {
    private val active = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var thread: Thread? = null
    private var file: File? = null
    private var suppressor: NoiseSuppressor? = null

    @Suppress("MissingPermission")
    override fun start(onTurn: (CapturedVoiceTurn) -> Unit, onError: (String) -> Unit) {
        check(active.compareAndSet(false, true)) { "Voice capture is already active" }
        val detector = when (settings.vadProvider) {
            VadProvider.WebRtc -> WebRtcVoiceActivityDetector(settings.vadMode)
            VadProvider.Silero -> SileroVoiceActivityDetector(context, settings.vadMode)
        }
        directory.mkdirs()
        val target = File(directory, "voice-${UUID.randomUUID()}.wav").also { file = it }
        val audioRecord = AudioRecord(
            settings.captureSource.androidSource,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), detector.frameBytes * 4),
        )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            detector.close()
            active.set(false)
            audioRecord.release()
            onError("Unable to initialize microphone")
            return
        }
        recorder = audioRecord
        suppressor = createNoiseSuppressor(audioRecord.audioSessionId)
        val nsActive = suppressor?.enabled == true
        audioRecord.startRecording()
        thread = Thread({ captureLoop(audioRecord, detector, target, nsActive, onTurn, onError) }, "VoiceTurnCapture").apply { start() }
    }

    private fun captureLoop(
        audioRecord: AudioRecord,
        detector: VoiceActivityDetector,
        target: File,
        noiseActive: Boolean,
        onTurn: (CapturedVoiceTurn) -> Unit,
        onError: (String) -> Unit,
    ) {
        var bytes = 0L
        var turnCommitted = false
        val frameMillis = detector.frameBytes * 1_000L / (SAMPLE_RATE * 2L)
        val gate = VoiceCaptureGate(settings, frameMillis)
        val bufferedFrames = ArrayDeque<ByteArray>()
        val bufferCapacity = ((settings.preRollMillis + maxOf(settings.speechConfirmationMillis, settings.minimumSpeechMillis)) / frameMillis + 1).toInt()
        try {
            target.outputStream().use { it.write(wavHeader(0)) }
            FileOutputStream(target, true).buffered().use { output ->
                val frame = ByteArray(detector.frameBytes)
                while (active.get()) {
                    val read = audioRecord.read(frame, 0, frame.size)
                    if (read != frame.size) continue
                    if (!turnCommitted) {
                        bufferedFrames.addLast(frame.copyOf())
                        if (bufferedFrames.size > bufferCapacity) bufferedFrames.removeFirst()
                    } else {
                        output.write(frame)
                        bytes += read
                    }
                    when (gate.accept(detector.isSpeech(frame))) {
                        VoiceCaptureDecision.Commit -> {
                            bufferedFrames.forEach {
                                output.write(it)
                                bytes += it.size
                            }
                            bufferedFrames.clear()
                            turnCommitted = true
                        }
                        VoiceCaptureDecision.Finish -> break
                        VoiceCaptureDecision.Reset -> bufferedFrames.clear()
                        VoiceCaptureDecision.Continue -> Unit
                    }
                }
            }
            if (!active.get() || !turnCommitted) {
                target.delete()
                return
            }
            active.set(false)
            target.writeHeader(bytes)
            onTurn(CapturedVoiceTurn(recordedAudioPrompt(target, bytes * 1_000 / (SAMPLE_RATE * 2)), gate.voicedMillis, noiseActive))
        } catch (error: Throwable) {
            target.delete()
            if (active.getAndSet(false)) onError(error.message ?: "Voice capture failed")
        } finally {
            detector.close()
            releasePlatformCapture()
        }
    }

    override fun cancel() {
        active.set(false)
        try {
            recorder?.stop()
        } catch (_: RuntimeException) { }
        if (Thread.currentThread() != thread) thread?.join(1_000)
        releasePlatformCapture()
        file?.delete()
        file = null
    }

    override fun close() = cancel()

    private fun createNoiseSuppressor(sessionId: Int): NoiseSuppressor? {
        if (!settings.noiseSuppressionRequested || !NoiseSuppressor.isAvailable()) return null
        return NoiseSuppressor.create(sessionId)?.also { it.enabled = true }
    }

    private fun releasePlatformCapture() {
        suppressor?.release()
        suppressor = null
        recorder?.release()
        recorder = null
        thread = null
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
    }
}

private val VadMode.webRtcMode: WebRtcMode get() = when (this) {
    VadMode.Normal -> WebRtcMode.NORMAL
    VadMode.Aggressive -> WebRtcMode.AGGRESSIVE
    VadMode.VeryAggressive -> WebRtcMode.VERY_AGGRESSIVE
}

private val VadMode.sileroMode: SileroMode get() = when (this) {
    VadMode.Normal -> SileroMode.NORMAL
    VadMode.Aggressive -> SileroMode.AGGRESSIVE
    VadMode.VeryAggressive -> SileroMode.VERY_AGGRESSIVE
}

fun reconcileVoiceTemporaryFiles(directory: File) {
    directory.listFiles()?.filter { it.isFile && it.name.startsWith("voice-") && it.extension == "wav" }?.forEach(File::delete)
}

private val VoiceCaptureSource.androidSource: Int get() = when (this) {
    VoiceCaptureSource.Microphone -> MediaRecorder.AudioSource.MIC
    VoiceCaptureSource.VoiceRecognition -> MediaRecorder.AudioSource.VOICE_RECOGNITION
    VoiceCaptureSource.VoiceCommunication -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
}

private fun File.writeHeader(dataSize: Long) = RandomAccessFile(this, "rw").use { it.write(wavHeader(dataSize)) }

private fun wavHeader(dataSize: Long): ByteArray {
    val result = ByteArray(44)
    fun ascii(offset: Int, text: String) = text.forEachIndexed { index, char -> result[offset + index] = char.code.toByte() }
    fun int(offset: Int, value: Int) = repeat(4) { result[offset + it] = (value shr (8 * it)).toByte() }
    fun short(offset: Int, value: Int) = repeat(2) { result[offset + it] = (value shr (8 * it)).toByte() }
    ascii(0, "RIFF")
    int(4, (dataSize + 36).coerceAtMost(UInt.MAX_VALUE.toLong()).toInt())
    ascii(8, "WAVE")
    ascii(12, "fmt ")
    int(16, 16)
    short(20, 1)
    short(22, 1)
    int(24, 16_000)
    int(28, 32_000)
    short(32, 2)
    short(34, 16)
    ascii(36, "data")
    int(40, dataSize.coerceAtMost(UInt.MAX_VALUE.toLong()).toInt())
    return result
}
