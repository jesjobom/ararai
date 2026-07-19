package com.jesjobom.ararai.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

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
    fun `referenced prefix larger than limit does not starve later orphan`() {
        val mediaDir = createTempDirectory("chat-media-").toFile()
        val repository = FileChatMediaRepository(mediaDir)
        val referenced =
            (0..DEFAULT_RECONCILIATION_FILE_LIMIT).map { index ->
                File(mediaDir, "a-referenced-${index.toString().padStart(3, '0')}.jpg").apply { writeText("keep") }
            }
        val orphan = File(mediaDir, "z-orphan.jpg").apply { writeText("orphan") }

        repository.reconcile(
            persistedUris = referenced.mapTo(mutableSetOf(), File::getAbsolutePath),
            maxFiles = DEFAULT_RECONCILIATION_FILE_LIMIT,
        )

        assertTrue(referenced.all(File::exists))
        assertFalse(orphan.exists())
    }

    @Test
    fun `reconciliation limit counts only unreferenced deletion candidates`() {
        val mediaDir = createTempDirectory("chat-media-").toFile()
        val repository = FileChatMediaRepository(mediaDir)
        val referenced = File(mediaDir, "a-referenced.jpg").apply { writeText("keep") }
        val orphans =
            listOf("b-orphan.jpg", "c-orphan.jpg", "d-orphan.jpg")
                .map { name -> File(mediaDir, name).apply { writeText("orphan") } }

        repository.reconcile(setOf(referenced.absolutePath), maxFiles = 2)

        assertTrue(referenced.exists())
        assertFalse(orphans[0].exists())
        assertFalse(orphans[1].exists())
        assertTrue(orphans[2].exists())
    }

    @Test
    fun `zero reconciliation limit performs no deletion`() {
        val mediaDir = createTempDirectory("chat-media-").toFile()
        val orphan = File(mediaDir, "orphan.jpg").apply { writeText("orphan") }

        FileChatMediaRepository(mediaDir).reconcile(emptySet(), maxFiles = 0)

        assertTrue(orphan.exists())
    }

    @Test
    fun `reconciliation continues after a deletion failure`() {
        val mediaDir = createTempDirectory("chat-media-").toFile()
        val first = File(mediaDir, "a-orphan.jpg").apply { writeText("orphan") }
        val second = File(mediaDir, "b-orphan.jpg").apply { writeText("orphan") }
        val attempted = mutableListOf<String>()
        val repository =
            FileChatMediaRepository(mediaDir) { file ->
                attempted += file.name
                file != first && file.delete()
            }

        repository.reconcile(emptySet(), maxFiles = 2)

        assertEquals(listOf(first.name, second.name), attempted)
        assertTrue(first.exists())
        assertFalse(second.exists())
    }

    @Test
    fun `content and external references never expand repository ownership`() {
        val root = createTempDirectory("chat-media-").toFile()
        val mediaDir = File(root, "chat_media").apply { mkdirs() }
        val outside = File(root, "outside.jpg").apply { writeText("outside") }
        val orphan = File(mediaDir, "orphan.jpg").apply { writeText("orphan") }

        FileChatMediaRepository(mediaDir).reconcile(
            persistedUris = setOf("content://provider/media/1", outside.absolutePath),
            maxFiles = 1,
        )

        assertTrue(outside.exists())
        assertFalse(orphan.exists())
    }

    @Test
    fun `out of directory candidates are never deleted`() {
        val root = createTempDirectory("chat-media-").toFile()
        val repository = FileChatMediaRepository(File(root, "chat_media"))
        val outside = File(root, "outside.wav").apply { writeText("outside") }

        repository.deleteUnreferenced(setOf(outside.absolutePath), emptySet())

        assertTrue(outside.exists())
    }

    private fun repository(): FileChatMediaRepository = FileChatMediaRepository(createTempDirectory("chat-media-").toFile())
}
