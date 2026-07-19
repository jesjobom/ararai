package com.jesjobom.ararai.ui

import android.media.AudioFormat
import android.media.AudioRecord
import com.jesjobom.ararai.chat.AudioPrompt
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

internal data class RecordedAudio(
    val file: File,
    val durationMillis: Long,
) {
    fun toAudioPrompt(): AudioPrompt = recordedAudioPrompt(
        file = file,
        durationMillis = durationMillis,
    )
}

internal fun recordedAudioPrompt(
    file: File,
    durationMillis: Long,
): AudioPrompt = AudioPrompt(
    uri = file.absolutePath,
    mimeType = RECORDED_AUDIO_MIME_TYPE,
    displayName = file.name,
    byteSize = file.length(),
    durationMillis = durationMillis,
)

internal class ChatAudioRecorder(
    override val file: File,
    private val sampleRate: Int = RECORDED_AUDIO_SAMPLE_RATE_HZ,
) : ChatAudioRecording {
    private val bufferSize: Int =
        maxOf(
            AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ),
            RECORDED_AUDIO_BUFFER_SIZE_BYTES,
        )
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    @Volatile
    private var isRecording: Boolean = false

    @Volatile
    private var dataSize: Long = 0L

    @Suppress("MissingPermission")
    override fun start() {
        require(bufferSize > 0) { "Unable to initialize audio input" }
        file.parentFile?.mkdirs()
        dataSize = 0L
        val recorder =
            AudioRecord(
                android.media.MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            error("Unable to initialize microphone")
        }
        audioRecord = recorder
        file.outputStream().use { output ->
            output.write(createWavHeader(dataSize = 0L, sampleRate = sampleRate))
        }
        recorder.startRecording()
        isRecording = true
        recordingThread =
            Thread(
                {
                    FileOutputStream(file, true).buffered().use { output ->
                        val buffer = ByteArray(bufferSize)
                        while (isRecording) {
                            val read = recorder.read(buffer, 0, buffer.size)
                            if (read > 0) {
                                output.write(buffer, 0, read)
                                dataSize += read.toLong()
                            }
                        }
                    }
                },
                "ChatAudioRecorder",
            ).apply { start() }
    }

    override fun stopSafely(): Boolean = try {
        isRecording = false
        audioRecord?.stopSafely()
        recordingThread?.join(1_000)
        audioRecord?.release()
        audioRecord = null
        recordingThread = null
        if (dataSize > 0L) {
            file.writeWavHeader(dataSize = dataSize, sampleRate = sampleRate)
        }
        dataSize > 0L
    } catch (_: RuntimeException) {
        false
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }
}

private fun AudioRecord.stopSafely() {
    try {
        stop()
    } catch (_: RuntimeException) {
        // The recorder may already be stopped after a setup/read failure.
    }
}

internal fun createWavHeader(
    dataSize: Long,
    sampleRate: Int = RECORDED_AUDIO_SAMPLE_RATE_HZ,
): ByteArray {
    val channels = 1
    val bitsPerSample = 16
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8
    val riffSize = (dataSize + WAV_HEADER_SIZE_BYTES - 8).coerceAtMost(UInt.MAX_VALUE.toLong()).toInt()
    val dataChunkSize = dataSize.coerceAtMost(UInt.MAX_VALUE.toLong()).toInt()
    return ByteArray(WAV_HEADER_SIZE_BYTES).apply {
        writeAscii(0, "RIFF")
        writeLittleEndianInt(4, riffSize)
        writeAscii(8, "WAVE")
        writeAscii(12, "fmt ")
        writeLittleEndianInt(16, 16)
        writeLittleEndianShort(20, 1)
        writeLittleEndianShort(22, channels)
        writeLittleEndianInt(24, sampleRate)
        writeLittleEndianInt(28, byteRate)
        writeLittleEndianShort(32, blockAlign)
        writeLittleEndianShort(34, bitsPerSample)
        writeAscii(36, "data")
        writeLittleEndianInt(40, dataChunkSize)
    }
}

private fun File.writeWavHeader(
    dataSize: Long,
    sampleRate: Int,
) {
    RandomAccessFile(this, "rw").use { file ->
        file.seek(0)
        file.write(createWavHeader(dataSize = dataSize, sampleRate = sampleRate))
    }
}

private fun ByteArray.writeAscii(
    offset: Int,
    value: String,
) {
    value.forEachIndexed { index, char -> this[offset + index] = char.code.toByte() }
}

private fun ByteArray.writeLittleEndianInt(
    offset: Int,
    value: Int,
) {
    this[offset] = value.toByte()
    this[offset + 1] = (value shr 8).toByte()
    this[offset + 2] = (value shr 16).toByte()
    this[offset + 3] = (value shr 24).toByte()
}

private fun ByteArray.writeLittleEndianShort(
    offset: Int,
    value: Int,
) {
    this[offset] = value.toByte()
    this[offset + 1] = (value shr 8).toByte()
}

internal fun formatDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private const val RECORDED_AUDIO_MIME_TYPE = "audio/wav"
private const val RECORDED_AUDIO_SAMPLE_RATE_HZ = 16_000
private const val RECORDED_AUDIO_BUFFER_SIZE_BYTES = 4096
internal const val MIN_RECORDED_AUDIO_DURATION_MILLIS = 250L
private const val WAV_HEADER_SIZE_BYTES = 44
