package com.jesjobom.ararai.ui

import android.net.Uri
import com.jesjobom.ararai.chat.AudioPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.io.path.createTempDirectory

@RunWith(RobolectricTestRunner::class)
class ChatMediaServicesTest {
    @Test
    fun `media implementations are replaceable without Android IO`() {
        val root = createTempDirectory("chat-media-services-").toFile()
        val importedFile = File(root, "image.jpg").apply { writeText("fake") }
        val recording = FakeRecording(File(root, "audio.wav"))
        val player = FakePlayer()
        val deleted = mutableListOf<String>()
        val services =
            ChatMediaServices(
                imageImporter = ChatImageImportService { ImportedChatImage(importedFile, "fake.jpg") },
                audioRecorderFactory = ChatAudioRecorderFactory { recording },
                audioPlayerFactory =
                ChatAudioPlayerFactory { _, onCompletion ->
                    player.onCompletion = onCompletion
                    player
                },
                imageDecoder = ChatImageDecoder { _, _ -> null },
                draftCleaner = ChatDraftCleaner(deleted::add),
            )

        assertSame(importedFile, services.imageImporter.import(Uri.EMPTY).file)
        assertSame(recording, services.audioRecorderFactory.create())
        recording.start()
        assertTrue(recording.started)
        assertTrue(recording.stopSafely())

        var completed = false
        val createdPlayer =
            services.audioPlayerFactory.create(AudioPrompt("fake.wav", "audio/wav")) {
                completed = true
            }
        createdPlayer.start()
        assertTrue(createdPlayer.isPlaying)
        player.onCompletion()
        assertTrue(completed)
        createdPlayer.release()
        assertFalse(createdPlayer.isPlaying)

        services.draftCleaner.delete("draft.wav")
        assertEquals(listOf("draft.wav"), deleted)
    }

    private class FakeRecording(
        override val file: File,
    ) : ChatAudioRecording {
        var started = false

        override fun start() {
            started = true
        }

        override fun stopSafely(): Boolean = started
    }

    private class FakePlayer : ChatAudioPlayer {
        override var isPlaying: Boolean = false
            private set
        var onCompletion: () -> Unit = {}

        override fun start() {
            isPlaying = true
        }

        override fun pause() {
            isPlaying = false
        }

        override fun release() {
            isPlaying = false
        }
    }
}
