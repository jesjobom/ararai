package com.jesjobom.ararai.chat

import java.io.File
import java.util.UUID

interface ChatMediaRepository {
    fun createDraftFile(
        prefix: String,
        suffix: String,
    ): File

    fun deleteDraft(
        uri: String,
        persistedUris: Set<String>,
    )

    fun deleteUnreferenced(
        candidateUris: Set<String>,
        persistedUris: Set<String>,
    )

    fun reconcile(
        persistedUris: Set<String>,
        maxFiles: Int = DEFAULT_RECONCILIATION_FILE_LIMIT,
    )
}

class FileChatMediaRepository(
    private val mediaDir: File,
    private val deleteFile: (File) -> Boolean = File::delete,
) : ChatMediaRepository {
    override fun createDraftFile(
        prefix: String,
        suffix: String,
    ): File {
        require(prefix.isNotBlank() && prefix.none { it == '/' || it == '\\' })
        require(suffix.none { it == '/' || it == '\\' })
        check(mediaDir.exists() || mediaDir.mkdirs()) { "Unable to create Chat media directory" }
        return File(mediaDir, "$prefix${UUID.randomUUID()}$suffix")
    }

    override fun deleteDraft(
        uri: String,
        persistedUris: Set<String>,
    ) {
        deleteUnreferenced(setOf(uri), persistedUris)
    }

    override fun deleteUnreferenced(
        candidateUris: Set<String>,
        persistedUris: Set<String>,
    ) {
        val referencedFiles = persistedUris.mapNotNullTo(mutableSetOf()) { ownedFile(it) }
        candidateUris
            .asSequence()
            .mapNotNull(::ownedFile)
            .filterNot(referencedFiles::contains)
            .forEach { deleteFile(it) }
    }

    override fun reconcile(
        persistedUris: Set<String>,
        maxFiles: Int,
    ) {
        require(maxFiles >= 0)
        if (!mediaDir.isDirectory || maxFiles == 0) return
        val referencedFiles = persistedUris.mapNotNullTo(mutableSetOf()) { ownedFile(it) }
        mediaDir
            .listFiles()
            .orEmpty()
            .asSequence()
            .filter(File::isFile)
            .sortedBy(File::getName)
            .mapNotNull { ownedFile(it.absolutePath) }
            .filterNot(referencedFiles::contains)
            .take(maxFiles)
            .forEach { deleteFile(it) }
    }

    private fun ownedFile(uri: String): File? {
        if (uri.startsWith("content://")) return null
        val rawPath =
            if (uri.startsWith("file://")) {
                runCatching { java.net.URI(uri).path }.getOrNull()
            } else {
                uri
            }?.takeIf(String::isNotBlank) ?: return null
        val root = runCatching { mediaDir.canonicalFile }.getOrNull() ?: return null
        val candidate = runCatching { File(rawPath).canonicalFile }.getOrNull() ?: return null
        return candidate.takeIf { it.parentFile == root }
    }
}

object NoOpChatMediaRepository : ChatMediaRepository {
    override fun createDraftFile(
        prefix: String,
        suffix: String,
    ): File = error("Chat media storage is unavailable")

    override fun deleteDraft(
        uri: String,
        persistedUris: Set<String>,
    ) = Unit

    override fun deleteUnreferenced(
        candidateUris: Set<String>,
        persistedUris: Set<String>,
    ) = Unit

    override fun reconcile(
        persistedUris: Set<String>,
        maxFiles: Int,
    ) = Unit
}

const val DEFAULT_RECONCILIATION_FILE_LIMIT = 256
