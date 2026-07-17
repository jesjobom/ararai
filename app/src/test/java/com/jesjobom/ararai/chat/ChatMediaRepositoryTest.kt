package com.jesjobom.ararai.chat

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMediaRepositoryTest {
    @Test
    fun `removes unreferenced draft and preserves persisted draft`() {
        val repository = repository()
        val removed = repository.createDraftFile("image-", ".jpg").apply { writeText("draft") }
        val preserved = repository.createDraftFile("image-", ".jpg").apply { writeText("persisted") }

        repository.deleteDraft(removed.absolutePath, setOf(preserved.absolutePath))
        repository.deleteDraft(preserved.absolutePath, setOf(preserved.absolutePath))

        assertFalse(removed.exists())
        assertTrue(preserved.exists())
    }

    @Test
    fun `shared references preserve media until the final reference is removed`() {
        val repository = repository()
        val shared = repository.createDraftFile("image-", ".jpg").apply { writeText("shared") }

        repository.deleteUnreferenced(setOf(shared.absolutePath), setOf(shared.absolutePath))
        assertTrue(shared.exists())

        repository.deleteUnreferenced(setOf(shared.absolutePath), emptySet())
        assertFalse(shared.exists())
    }

    @Test
    fun `reconciliation removes only bounded unreferenced files in canonical directory`() {
        val root = createTempDirectory("chat-media-").toFile()
        val mediaDir = File(root, "chat_media").apply { mkdirs() }
        val repository = FileChatMediaRepository(mediaDir)
        val firstOrphan = File(mediaDir, "a-orphan.jpg").apply { writeText("orphan") }
        val secondOrphan = File(mediaDir, "b-orphan.jpg").apply { writeText("orphan") }
        val referenced = File(mediaDir, "z-referenced.jpg").apply { writeText("keep") }
        val outside = File(root, "outside.jpg").apply { writeText("outside") }

        repository.reconcile(setOf(referenced.absolutePath), maxFiles = 1)

        assertTrue(referenced.exists())
        assertTrue(outside.exists())
        assertFalse(firstOrphan.exists())
        assertTrue(secondOrphan.exists())
    }

    @Test
    fun `out of directory candidates are never deleted`() {
        val root = createTempDirectory("chat-media-").toFile()
        val repository = FileChatMediaRepository(File(root, "chat_media"))
        val outside = File(root, "outside.wav").apply { writeText("outside") }

        repository.deleteUnreferenced(setOf(outside.absolutePath), emptySet())

        assertTrue(outside.exists())
    }

    private fun repository(): FileChatMediaRepository =
        FileChatMediaRepository(createTempDirectory("chat-media-").toFile())
}
