package com.jesjobom.ararai.chat

import android.content.Context
import android.os.ParcelFileDescriptor
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class AudioTranscriberTest {
    @Test
    @Config(sdk = [32])
    fun `file transcription is unavailable below api 33`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertFalse(AndroidOnDeviceAudioTranscriber(context).isAvailable)
    }

    @Test
    fun `recognizer errors have stable diagnostic names`() {
        assertEquals("ERROR_NO_MATCH", recognizerErrorName(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals("ERROR_AUDIO", recognizerErrorName(SpeechRecognizer.ERROR_AUDIO))
        assertEquals("ERROR_UNKNOWN", recognizerErrorName(Int.MAX_VALUE))
    }

    @Test
    fun `wav inspection finds pcm data after optional chunks`() {
        val file = kotlin.io.path.createTempFile(suffix = ".wav").toFile()
        try {
            file.outputStream().use { output ->
                output.write("RIFF".toByteArray())
                output.write(littleEndianInt(52))
                output.write("WAVE".toByteArray())
                output.write("JUNK".toByteArray())
                output.write(littleEndianInt(4))
                output.write(ByteArray(4))
                output.write("fmt ".toByteArray())
                output.write(littleEndianInt(16))
                output.write(littleEndianShort(1))
                output.write(littleEndianShort(1))
                output.write(littleEndianInt(16_000))
                output.write(littleEndianInt(32_000))
                output.write(littleEndianShort(2))
                output.write(littleEndianShort(16))
                output.write("data".toByteArray())
                output.write(littleEndianInt(4))
                output.write(ByteArray(4))
            }

            val metadata = inspectWav(file)

            assertEquals(16_000, metadata.sampleRate)
            assertEquals(1, metadata.channelCount)
            assertEquals(16, metadata.bitsPerSample)
            assertEquals(56L, metadata.dataOffset)
            assertEquals(4L, metadata.dataBytes)
            assertEquals(32_000L, metadata.bytesPerSecond)
            assertEquals(640, metadata.pcmChunkBytes(20L))
            assertEquals(1L, metadata.durationMillis)
            assertEquals(10_000L, metadata.recognitionTimeoutMillis(AudioDeliveryMode.Fast))
            assertEquals(15_001L, metadata.recognitionTimeoutMillis(AudioDeliveryMode.Paced))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `recorded wav timing derives from pcm format and data size`() {
        val metadata = WavMetadata(
            sampleRate = 16_000,
            channelCount = 1,
            bitsPerSample = 16,
            dataOffset = 44L,
            dataBytes = 199_680L,
        )

        assertEquals(6_240L, metadata.durationMillis)
        assertEquals(11_240L, metadata.recognitionTimeoutMillis(AudioDeliveryMode.Fast))
        assertEquals(21_240L, metadata.recognitionTimeoutMillis(AudioDeliveryMode.Paced))
        assertEquals(640, metadata.pcmChunkBytes(20L))
    }

    @Test
    fun `language support prioritizes installed and accepts generic language tag`() {
        assertEquals(
            OnDeviceLanguageSupport.Installed,
            onDeviceLanguageSupport(
                requestedLocale = Locale.forLanguageTag("pt-BR"),
                installed = listOf("en-US", "pt"),
                pending = emptyList(),
                supported = listOf("pt-BR"),
            ),
        )
    }

    @Test
    fun `language support distinguishes pending downloadable and unsupported`() {
        val locale = Locale.forLanguageTag("pt-BR")

        assertEquals(
            OnDeviceLanguageSupport.Pending,
            onDeviceLanguageSupport(locale, emptyList(), listOf("pt_BR"), listOf("pt-BR")),
        )
        assertEquals(
            OnDeviceLanguageSupport.Downloadable,
            onDeviceLanguageSupport(locale, emptyList(), emptyList(), listOf("pt-BR")),
        )
        assertEquals(
            OnDeviceLanguageSupport.Unsupported,
            onDeviceLanguageSupport(locale, listOf("en-US"), emptyList(), listOf("es-ES")),
        )
    }

    @Test
    fun `segmented transcripts are normalized and joined in order`() {
        assertEquals(
            "primeiro segmento segundo segmento",
            aggregateTranscriptionSegments(
                listOf(" primeiro   segmento ", "", "  ", "segundo\nsegmento"),
            ),
        )
    }

    @Test
    fun `only recognition outcomes retry with pacing`() {
        assertTrue(
            AudioTranscriptionFailure(
                AudioTranscriptionFailureKind.EmptyResults,
                "empty",
                "diagnostic",
            ).shouldRetryWithPacing(),
        )
        assertTrue(
            AudioTranscriptionFailure(
                AudioTranscriptionFailureKind.RecognizerError,
                "no match",
                "diagnostic",
                SpeechRecognizer.ERROR_NO_MATCH,
            ).shouldRetryWithPacing(),
        )
        assertFalse(
            AudioTranscriptionFailure(
                AudioTranscriptionFailureKind.PipeWriteFailure,
                "pipe",
                "diagnostic",
            ).shouldRetryWithPacing(),
        )
    }

    @Test
    fun `successful segmented completion after full stream is not suspicious`() {
        assertEquals(
            TranscriptionSuccessAssessment(mayBeIncomplete = false),
            assessTranscriptionSuccess("segmented_session", streamedBytes = 32_000L, expectedBytes = 32_000L),
        )
    }

    @Test
    fun `fallback completion sources and incomplete streams are suspicious`() {
        assertEquals(
            "unexpected_completion_source:standard_results",
            assessTranscriptionSuccess("standard_results", 32_000L, 32_000L).reason,
        )
        assertEquals(
            "stream_not_complete",
            assessTranscriptionSuccess("segmented_session", null, 32_000L).reason,
        )
        assertEquals(
            "stream_bytes_mismatch:16000/32000",
            assessTranscriptionSuccess("segmented_session", 16_000L, 32_000L).reason,
        )
    }

    @Test
    @Config(sdk = [33])
    fun `recognition intent requests segmented quality formatting`() {
        val pipe = ParcelFileDescriptor.createPipe()
        try {
            val intent = createOnDeviceRecognitionIntent(
                requestedLocale = Locale.forLanguageTag("pt-BR"),
                wav = WavMetadata(16_000, 1, 16, 44L, 32_000L),
                audioSource = pipe[0],
            )

            assertEquals("pt-BR", intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
            assertEquals(
                RecognizerIntent.EXTRA_AUDIO_SOURCE,
                intent.getStringExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION),
            )
            assertEquals(
                RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY,
                intent.getStringExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING),
            )
        } finally {
            pipe.forEach(ParcelFileDescriptor::close)
        }
    }

    private fun littleEndianShort(value: Int): ByteArray = byteArrayOf(
        value.toByte(),
        (value shr 8).toByte(),
    )

    private fun littleEndianInt(value: Int): ByteArray = byteArrayOf(
        value.toByte(),
        (value shr 8).toByte(),
        (value shr 16).toByte(),
        (value shr 24).toByte(),
    )
}
