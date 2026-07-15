package com.jesjobom.ararai.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatAudioRecorderTest {
    @Test
    fun `formats recorded audio prompt metadata`() {
        val file = File.createTempFile("recording-", ".wav").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }

        val prompt = recordedAudioPrompt(file = file, durationMillis = 12_345)

        assertEquals(file.absolutePath, prompt.uri)
        assertEquals("audio/wav", prompt.mimeType)
        assertEquals(file.name, prompt.displayName)
        assertEquals(3L, prompt.byteSize)
        assertEquals(12_345L, prompt.durationMillis)
    }

    @Test
    fun `creates pcm wav header for recorded audio`() {
        val header = createWavHeader(dataSize = 32_000, sampleRate = 16_000)

        assertEquals("RIFF", header.decodeAscii(0, 4))
        assertEquals(32_036, header.readLittleEndianInt(4))
        assertEquals("WAVE", header.decodeAscii(8, 4))
        assertEquals("fmt ", header.decodeAscii(12, 4))
        assertEquals(16, header.readLittleEndianInt(16))
        assertEquals(1, header.readLittleEndianShort(20))
        assertEquals(1, header.readLittleEndianShort(22))
        assertEquals(16_000, header.readLittleEndianInt(24))
        assertEquals(32_000, header.readLittleEndianInt(28))
        assertEquals(2, header.readLittleEndianShort(32))
        assertEquals(16, header.readLittleEndianShort(34))
        assertEquals("data", header.decodeAscii(36, 4))
        assertEquals(32_000, header.readLittleEndianInt(40))
    }

    @Test
    fun `formats recording duration`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:03", formatDuration(3_400))
        assertEquals("1:05", formatDuration(65_999))
    }
}

private fun ByteArray.decodeAscii(offset: Int, length: Int): String =
    copyOfRange(offset, offset + length).decodeToString()

private fun ByteArray.readLittleEndianInt(offset: Int): Int =
    (this[offset].toInt() and 0xff) or
        ((this[offset + 1].toInt() and 0xff) shl 8) or
        ((this[offset + 2].toInt() and 0xff) shl 16) or
        ((this[offset + 3].toInt() and 0xff) shl 24)

private fun ByteArray.readLittleEndianShort(offset: Int): Int =
    (this[offset].toInt() and 0xff) or
        ((this[offset + 1].toInt() and 0xff) shl 8)
