package com.jesjobom.ararai.chat

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

data class ChatSession(
    val id: String,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

data class StoredChatMessage(
    val id: String,
    val sessionId: String,
    val role: ChatRole,
    val content: MessageContent,
    val createdAtMillis: Long,
) {
    constructor(
        id: String,
        sessionId: String,
        role: ChatRole,
        text: String,
        createdAtMillis: Long,
    ) : this(
        id = id,
        sessionId = sessionId,
        role = role,
        content = MessageContent.TextPrompt(text),
        createdAtMillis = createdAtMillis,
    )

    val text: String
        get() = content.displayText
}

class ChatPersistenceException(
    message: String,
) : IllegalStateException(message)

interface ChatSessionStore {
    fun ensureSession(): ChatSession

    fun listSessions(): List<ChatSession>

    fun getMessages(sessionId: String): List<StoredChatMessage>

    fun getRecentMessages(
        sessionId: String,
        limit: Int,
    ): List<StoredChatMessage> {
        require(limit > 0)
        return getMessages(sessionId).takeLast(limit)
    }

    fun countMessages(sessionId: String): Int = getMessages(sessionId).size

    fun mediaUrisForSession(sessionId: String): Set<String> {
        val messages = getMessages(sessionId)
        return messages.flatMapTo(linkedSetOf()) { it.content.mediaUris() }
    }

    fun createSession(title: String): ChatSession

    fun renameSession(
        sessionId: String,
        title: String,
    ): ChatSession

    fun deleteSession(sessionId: String)

    fun clearSessions()

    fun appendMessage(
        sessionId: String,
        role: ChatRole,
        content: MessageContent,
    ): StoredChatMessage

    fun appendMessage(
        sessionId: String,
        role: ChatRole,
        text: String,
    ): StoredChatMessage = appendMessage(sessionId, role, MessageContent.TextPrompt(text))

    fun updateMessage(
        messageId: String,
        content: MessageContent,
    )

    fun updateMessage(
        messageId: String,
        text: String,
    ) {
        updateMessage(messageId, MessageContent.TextPrompt(text))
    }

    fun referencedMediaUris(): Set<String>
}

/**
 * Keeps the single untitled conversation in memory until its first meaningful title is assigned.
 * This prevents abandoned "New chat" rows while still allowing voice turns to stage audio before
 * transcription produces the title.
 */
@Suppress("TooManyFunctions", "MaxLineLength")
class DeferredNewChatSessionStore(
    private val delegate: ChatSessionStore,
) : ChatSessionStore {
    private var pendingSession: ChatSession? = null
    private val pendingMessages = linkedMapOf<String, StoredChatMessage>()
    private val promotedSessionIds = linkedMapOf<String, String>()

    @Synchronized
    override fun ensureSession(): ChatSession = pendingSession ?: delegate.listSessions().firstOrNull()?.let(::withPublicSessionId) ?: createSession("New chat")

    @Synchronized
    override fun listSessions(): List<ChatSession> = (listOfNotNull(pendingSession) + delegate.listSessions().map(::withPublicSessionId))
        .sortedWith(compareByDescending<ChatSession> { it.updatedAtMillis }.thenBy { it.title })

    @Synchronized
    override fun getMessages(sessionId: String): List<StoredChatMessage> = if (pendingSession?.id == sessionId) {
        pendingMessages.values.toList()
    } else {
        delegate.getMessages(resolveSessionId(sessionId)).map { it.copy(sessionId = sessionId) }
    }

    @Synchronized
    override fun getRecentMessages(
        sessionId: String,
        limit: Int,
    ): List<StoredChatMessage> {
        require(limit > 0)
        return if (pendingSession?.id == sessionId) {
            pendingMessages.values.toList().takeLast(limit)
        } else {
            delegate.getRecentMessages(resolveSessionId(sessionId), limit).map { it.copy(sessionId = sessionId) }
        }
    }

    @Synchronized
    override fun countMessages(sessionId: String): Int = if (pendingSession?.id == sessionId) pendingMessages.size else delegate.countMessages(resolveSessionId(sessionId))

    @Synchronized
    override fun mediaUrisForSession(sessionId: String): Set<String> = if (pendingSession?.id == sessionId) {
        pendingMessages.values.flatMapTo(linkedSetOf()) { it.content.mediaUris() }
    } else {
        delegate.mediaUrisForSession(resolveSessionId(sessionId))
    }

    @Synchronized
    override fun createSession(title: String): ChatSession {
        if (title.cleanTitle() != "New chat") return delegate.createSession(title)
        return pendingSession ?: ChatSession(
            id = UUID.randomUUID().toString(),
            title = "New chat",
            createdAtMillis = System.currentTimeMillis(),
            updatedAtMillis = System.currentTimeMillis(),
        ).also { pendingSession = it }
    }

    @Synchronized
    @Suppress("ReturnCount")
    override fun renameSession(sessionId: String, title: String): ChatSession {
        if (pendingSession?.id != sessionId) return delegate.renameSession(resolveSessionId(sessionId), title).let(::withPublicSessionId)
        val cleanTitle = title.cleanTitle()
        if (cleanTitle == "New chat") return pendingSession!!
        val persisted = delegate.createSession(cleanTitle)
        pendingMessages.values.forEach { message ->
            delegate.appendMessage(persisted.id, message.role, message.content)
        }
        promotedSessionIds[sessionId] = persisted.id
        pendingMessages.clear()
        pendingSession = null
        return persisted
    }

    @Synchronized
    override fun deleteSession(sessionId: String) {
        if (pendingSession?.id == sessionId) {
            pendingSession = null
            pendingMessages.clear()
        } else {
            delegate.deleteSession(resolveSessionId(sessionId))
            promotedSessionIds.remove(sessionId)
        }
    }

    @Synchronized
    override fun clearSessions() {
        pendingSession = null
        pendingMessages.clear()
        promotedSessionIds.clear()
        delegate.clearSessions()
    }

    @Synchronized
    override fun appendMessage(
        sessionId: String,
        role: ChatRole,
        content: MessageContent,
    ): StoredChatMessage {
        if (pendingSession?.id != sessionId) return delegate.appendMessage(resolveSessionId(sessionId), role, content)
        val message = StoredChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = role,
            content = content,
            createdAtMillis = System.currentTimeMillis(),
        )
        pendingMessages[message.id] = message
        pendingSession = pendingSession?.copy(updatedAtMillis = message.createdAtMillis)
        return message
    }

    @Synchronized
    override fun updateMessage(messageId: String, content: MessageContent) {
        val pending = pendingMessages[messageId]
        if (pending != null) {
            pendingMessages[messageId] = pending.copy(content = content)
        } else {
            delegate.updateMessage(messageId, content)
        }
    }

    @Synchronized
    override fun referencedMediaUris(): Set<String> = delegate.referencedMediaUris() + pendingMessages.values.flatMap { it.content.mediaUris() }

    private fun resolveSessionId(sessionId: String): String = promotedSessionIds[sessionId] ?: sessionId

    private fun withPublicSessionId(session: ChatSession): ChatSession {
        val publicId = promotedSessionIds.entries.firstOrNull { it.value == session.id }?.key ?: session.id
        return session.copy(id = publicId)
    }
}

fun MessageContent.mediaUris(): Set<String> = when (this) {
    is MessageContent.TextPrompt -> imageAttachments.mapTo(linkedSetOf()) { it.uri }
    is MessageContent.AudioPromptContent -> setOf(audio.uri)
}

class InMemoryChatSessionStore : ChatSessionStore {
    private val sessions = linkedMapOf<String, ChatSession>()
    private val messages = linkedMapOf<String, StoredChatMessage>()

    override fun ensureSession(): ChatSession = sessions.values.maxByOrNull { it.updatedAtMillis } ?: createSession("New chat")

    override fun listSessions(): List<ChatSession> = sessions.values.sortedWith(compareByDescending<ChatSession> { it.updatedAtMillis }.thenBy { it.title })

    override fun getMessages(sessionId: String): List<StoredChatMessage> = messages.values
        .filter { it.sessionId == sessionId }
        .sortedWith(compareBy<StoredChatMessage> { it.createdAtMillis }.thenBy { it.id })

    override fun createSession(title: String): ChatSession {
        val now = nowMillis()
        val session =
            ChatSession(
                id = newId(),
                title = title.cleanTitle(),
                createdAtMillis = now,
                updatedAtMillis = now,
            )
        sessions[session.id] = session
        return session
    }

    override fun renameSession(
        sessionId: String,
        title: String,
    ): ChatSession {
        val current = sessions.getValue(sessionId)
        val updated = current.copy(title = title.cleanTitle(), updatedAtMillis = nowMillis())
        sessions[sessionId] = updated
        return updated
    }

    override fun deleteSession(sessionId: String) {
        sessions.remove(sessionId)
        messages.values.removeAll { it.sessionId == sessionId }
    }

    override fun clearSessions() {
        messages.clear()
        sessions.clear()
    }

    override fun appendMessage(
        sessionId: String,
        role: ChatRole,
        content: MessageContent,
    ): StoredChatMessage {
        val session =
            sessions[sessionId]
                ?: throw ChatPersistenceException("Chat session does not exist: $sessionId")
        val now = nextMessageTimestamp(sessionId)
        val message =
            StoredChatMessage(
                id = newId(),
                sessionId = sessionId,
                role = role,
                content = content,
                createdAtMillis = now,
            )
        messages[message.id] = message
        sessions[sessionId] = session.copy(updatedAtMillis = now)
        return message
    }

    override fun updateMessage(
        messageId: String,
        content: MessageContent,
    ) {
        val current = messages.getValue(messageId)
        messages[messageId] = current.copy(content = content)
        touch(current.sessionId)
    }

    override fun referencedMediaUris(): Set<String> = messages.values
        .asSequence()
        .flatMap { it.content.mediaUris().asSequence() }
        .toSet()

    private fun touch(sessionId: String) {
        sessions[sessionId]?.let { sessions[sessionId] = it.copy(updatedAtMillis = nowMillis()) }
    }

    private fun nextMessageTimestamp(sessionId: String): Long {
        val latest =
            messages.values
                .filter { it.sessionId == sessionId }
                .maxOfOrNull { it.createdAtMillis }
        val now = nowMillis()
        return if (latest == null || now > latest) now else latest + 1L
    }
}

class SqliteChatSessionStore(
    context: Context,
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION),
    ChatSessionStore {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE chat_sessions(
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                created_at_millis INTEGER NOT NULL,
                updated_at_millis INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE chat_messages(
                id TEXT PRIMARY KEY,
                session_id TEXT NOT NULL,
                role TEXT NOT NULL,
                text TEXT NOT NULL,
                content_kind TEXT NOT NULL DEFAULT 'text',
                content_payload TEXT,
                created_at_millis INTEGER NOT NULL,
                FOREIGN KEY(session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX chat_messages_session_created ON chat_messages(session_id, created_at_millis)")
        createMediaReferenceTable(db)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN content_kind TEXT NOT NULL DEFAULT 'text'")
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN content_payload TEXT")
        }
        if (oldVersion < 3) {
            createMediaReferenceTable(db)
            backfillMediaReferences(db)
        }
    }

    @Synchronized
    override fun ensureSession(): ChatSession = listSessions().firstOrNull() ?: createSession("New chat")

    @Synchronized
    override fun listSessions(): List<ChatSession> {
        readableDatabase
            .rawQuery(
                """
                SELECT id, title, created_at_millis, updated_at_millis
                FROM chat_sessions
                ORDER BY updated_at_millis DESC, title ASC
                """.trimIndent(),
                emptyArray(),
            ).use { cursor ->
                val result = mutableListOf<ChatSession>()
                while (cursor.moveToNext()) {
                    result +=
                        ChatSession(
                            id = cursor.getString(0),
                            title = cursor.getString(1),
                            createdAtMillis = cursor.getLong(2),
                            updatedAtMillis = cursor.getLong(3),
                        )
                }
                return result
            }
    }

    @Synchronized
    override fun getMessages(sessionId: String): List<StoredChatMessage> {
        readableDatabase
            .rawQuery(
                """
                SELECT id, session_id, role, text, content_kind, content_payload, created_at_millis
                FROM chat_messages
                WHERE session_id = ?
                ORDER BY created_at_millis ASC, id ASC
                """.trimIndent(),
                arrayOf(sessionId),
            ).use { cursor ->
                val result = mutableListOf<StoredChatMessage>()
                while (cursor.moveToNext()) {
                    result +=
                        StoredChatMessage(
                            id = cursor.getString(0),
                            sessionId = cursor.getString(1),
                            role = ChatRole.valueOf(cursor.getString(2)),
                            content =
                            MessageContentCodec.decode(
                                kind = cursor.getString(4),
                                payload = if (cursor.isNull(5)) null else cursor.getString(5),
                                legacyText = cursor.getString(3),
                            ),
                            createdAtMillis = cursor.getLong(6),
                        )
                }
                return result
            }
    }

    @Synchronized
    override fun getRecentMessages(
        sessionId: String,
        limit: Int,
    ): List<StoredChatMessage> {
        require(limit > 0)
        readableDatabase
            .rawQuery(
                """
                SELECT id, session_id, role, text, content_kind, content_payload, created_at_millis
                FROM chat_messages
                WHERE session_id = ?
                ORDER BY created_at_millis DESC, id DESC
                LIMIT ?
                """.trimIndent(),
                arrayOf(sessionId, limit.toString()),
            ).use { cursor ->
                val result = mutableListOf<StoredChatMessage>()
                while (cursor.moveToNext()) result += cursor.toStoredChatMessage()
                return result.asReversed()
            }
    }

    @Synchronized
    override fun countMessages(sessionId: String): Int {
        readableDatabase
            .rawQuery(
                "SELECT COUNT(*) FROM chat_messages WHERE session_id = ?",
                arrayOf(sessionId),
            ).use { cursor ->
                return if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
    }

    @Synchronized
    override fun mediaUrisForSession(sessionId: String): Set<String> {
        readableDatabase
            .rawQuery(
                "SELECT DISTINCT uri FROM chat_media_references WHERE session_id = ? ORDER BY uri ASC",
                arrayOf(sessionId),
            ).use { cursor ->
                val result = linkedSetOf<String>()
                while (cursor.moveToNext()) result += cursor.getString(0)
                return result
            }
    }

    @Synchronized
    override fun createSession(title: String): ChatSession {
        val now = nowMillis()
        val session =
            ChatSession(
                id = newId(),
                title = title.cleanTitle(),
                createdAtMillis = now,
                updatedAtMillis = now,
            )
        writableDatabase.insertOrThrow("chat_sessions", null, session.toContentValues())
        return session
    }

    @Synchronized
    override fun renameSession(
        sessionId: String,
        title: String,
    ): ChatSession {
        val values =
            ContentValues().apply {
                put("title", title.cleanTitle())
                put("updated_at_millis", nowMillis())
            }
        writableDatabase.update("chat_sessions", values, "id = ?", arrayOf(sessionId))
        return listSessions().first { it.id == sessionId }
    }

    @Synchronized
    override fun deleteSession(sessionId: String) {
        writableDatabase.inTransaction {
            delete("chat_media_references", "session_id = ?", arrayOf(sessionId))
            delete("chat_messages", "session_id = ?", arrayOf(sessionId))
            delete("chat_sessions", "id = ?", arrayOf(sessionId))
        }
    }

    @Synchronized
    override fun clearSessions() {
        writableDatabase.inTransaction {
            delete("chat_media_references", null, null)
            delete("chat_messages", null, null)
            delete("chat_sessions", null, null)
        }
    }

    @Synchronized
    override fun appendMessage(
        sessionId: String,
        role: ChatRole,
        content: MessageContent,
    ): StoredChatMessage {
        val db = writableDatabase
        return db.inTransaction {
            val now = nextMessageTimestamp(this, sessionId)
            val message =
                StoredChatMessage(
                    id = newId(),
                    sessionId = sessionId,
                    role = role,
                    content = content,
                    createdAtMillis = now,
                )
            insertOrThrow("chat_messages", null, message.toContentValues())
            insertMediaReferences(this, message.id, message.sessionId, message.content)
            val updatedSessions =
                update(
                    "chat_sessions",
                    ContentValues().apply { put("updated_at_millis", now) },
                    "id = ?",
                    arrayOf(sessionId),
                )
            if (updatedSessions != 1) {
                throw ChatPersistenceException("Chat session does not exist: $sessionId")
            }
            message
        }
    }

    @Synchronized
    override fun updateMessage(
        messageId: String,
        content: MessageContent,
    ) {
        val encoded = MessageContentCodec.encode(content)
        writableDatabase.inTransaction {
            val updated =
                update(
                    "chat_messages",
                    ContentValues().apply {
                        put("text", content.displayText)
                        put("content_kind", encoded.kind)
                        put("content_payload", encoded.payload)
                    },
                    "id = ?",
                    arrayOf(messageId),
                )
            if (updated == 0) return@inTransaction
            val sessionId =
                rawQuery(
                    "SELECT session_id FROM chat_messages WHERE id = ?",
                    arrayOf(messageId),
                ).use { cursor ->
                    check(cursor.moveToFirst()) { "Updated Chat message disappeared: $messageId" }
                    cursor.getString(0)
                }
            delete("chat_media_references", "message_id = ?", arrayOf(messageId))
            insertMediaReferences(
                this,
                messageId,
                sessionId,
                content,
            )
        }
    }

    @Synchronized
    override fun referencedMediaUris(): Set<String> {
        readableDatabase
            .rawQuery(
                "SELECT DISTINCT uri FROM chat_media_references ORDER BY uri ASC",
                emptyArray(),
            ).use { cursor ->
                val result = linkedSetOf<String>()
                while (cursor.moveToNext()) result += cursor.getString(0)
                return result
            }
    }

    private fun createMediaReferenceTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_media_references(
                message_id TEXT NOT NULL,
                session_id TEXT NOT NULL,
                uri TEXT NOT NULL,
                PRIMARY KEY(message_id, uri),
                FOREIGN KEY(message_id) REFERENCES chat_messages(id) ON DELETE CASCADE,
                FOREIGN KEY(session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS chat_media_references_uri ON chat_media_references(uri)")
        db.execSQL("CREATE INDEX IF NOT EXISTS chat_media_references_session ON chat_media_references(session_id)")
    }

    private fun backfillMediaReferences(db: SQLiteDatabase) {
        db
            .rawQuery(
                """
                SELECT id, session_id, role, text, content_kind, content_payload, created_at_millis
                FROM chat_messages
                """.trimIndent(),
                emptyArray(),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    insertMediaReferences(
                        db,
                        cursor.getString(0),
                        cursor.getString(1),
                        MessageContentCodec.decode(
                            kind = cursor.getString(4),
                            payload = if (cursor.isNull(5)) null else cursor.getString(5),
                            legacyText = cursor.getString(3),
                        ),
                    )
                }
            }
    }

    private fun insertMediaReferences(
        db: SQLiteDatabase,
        messageId: String,
        sessionId: String,
        content: MessageContent,
    ) {
        content.mediaUris().forEach { uri ->
            db.insertOrThrow(
                "chat_media_references",
                null,
                ContentValues().apply {
                    put("message_id", messageId)
                    put("session_id", sessionId)
                    put("uri", uri)
                },
            )
        }
    }

    private fun ChatSession.toContentValues(): ContentValues = ContentValues().apply {
        put("id", id)
        put("title", title)
        put("created_at_millis", createdAtMillis)
        put("updated_at_millis", updatedAtMillis)
    }

    private fun StoredChatMessage.toContentValues(): ContentValues = MessageContentCodec.encode(content).let { encoded ->
        ContentValues().apply {
            put("id", id)
            put("session_id", sessionId)
            put("role", role.name)
            put("text", content.displayText)
            put("content_kind", encoded.kind)
            put("content_payload", encoded.payload)
            put("created_at_millis", createdAtMillis)
        }
    }

    private fun android.database.Cursor.toStoredChatMessage(): StoredChatMessage = StoredChatMessage(
        id = getString(0),
        sessionId = getString(1),
        role = ChatRole.valueOf(getString(2)),
        content =
        MessageContentCodec.decode(
            kind = getString(4),
            payload = if (isNull(5)) null else getString(5),
            legacyText = getString(3),
        ),
        createdAtMillis = getLong(6),
    )

    private fun nextMessageTimestamp(
        db: SQLiteDatabase,
        sessionId: String,
    ): Long {
        db
            .rawQuery(
                "SELECT MAX(created_at_millis) FROM chat_messages WHERE session_id = ?",
                arrayOf(sessionId),
            ).use { cursor ->
                val latest = if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
                val now = nowMillis()
                return if (latest == null || now > latest) now else latest + 1L
            }
    }

    private companion object {
        const val DATABASE_NAME = "ararai_chat.db"
        const val DATABASE_VERSION = 3
    }
}

private inline fun <T> SQLiteDatabase.inTransaction(block: SQLiteDatabase.() -> T): T {
    beginTransaction()
    try {
        val result = block()
        setTransactionSuccessful()
        return result
    } finally {
        endTransaction()
    }
}

private object MessageContentCodec {
    data class Encoded(
        val kind: String,
        val payload: String?,
    )

    fun encode(content: MessageContent): Encoded = when (content) {
        is MessageContent.TextPrompt ->
            Encoded(
                kind = "text",
                payload = encodeTextPrompt(content),
            )
        is MessageContent.AudioPromptContent ->
            Encoded(
                kind = "audio",
                payload =
                listOf(
                    content.audio.uri,
                    content.audio.mimeType,
                    content.audio.displayName.orEmpty(),
                    content.audio.byteSize
                        ?.toString()
                        .orEmpty(),
                    content.audio.durationMillis
                        ?.toString()
                        .orEmpty(),
                    content.transcript.orEmpty(),
                    content.transcriptionStatus.name,
                    content.transcriptionError.orEmpty(),
                    content.transcriptionFailureKind?.name.orEmpty(),
                    content.transcriptionDiagnostic.orEmpty(),
                    content.transcriptionMayBeIncomplete.toString(),
                    content.transcriptionIncompleteReason.orEmpty(),
                ).joinToString(separator = "\t") { it.encodeField() },
            )
    }

    fun decode(
        kind: String?,
        payload: String?,
        legacyText: String,
    ): MessageContent = when (kind) {
        "audio" ->
            payload?.split('\t')?.map { it.decodeField() }?.let { fields ->
                MessageContent.AudioPromptContent(
                    audio = AudioPrompt(
                        uri = fields.getOrElse(0) { "" },
                        mimeType = fields.getOrElse(1) { "audio/*" },
                        displayName = fields.getOrNull(2)?.takeIf { it.isNotBlank() },
                        byteSize = fields.getOrNull(3)?.toLongOrNull(),
                        durationMillis = fields.getOrNull(4)?.toLongOrNull(),
                    ),
                    transcript = fields.getOrNull(5)?.takeIf { it.isNotBlank() },
                    transcriptionStatus =
                    fields.getOrNull(6)
                        ?.let { runCatching { AudioTranscriptionStatus.valueOf(it) }.getOrNull() }
                        ?: AudioTranscriptionStatus.NotRequested,
                    transcriptionError = fields.getOrNull(7)?.takeIf { it.isNotBlank() },
                    transcriptionFailureKind =
                    fields.getOrNull(8)
                        ?.let { runCatching { AudioTranscriptionFailureKind.valueOf(it) }.getOrNull() },
                    transcriptionDiagnostic = fields.getOrNull(9)?.takeIf { it.isNotBlank() },
                    transcriptionMayBeIncomplete = fields.getOrNull(10)?.toBooleanStrictOrNull() ?: false,
                    transcriptionIncompleteReason = fields.getOrNull(11)?.takeIf { it.isNotBlank() },
                )
            } ?: MessageContent.TextPrompt(legacyText)
        else -> decodeTextPrompt(payload, legacyText)
    }

    private fun encodeTextPrompt(content: MessageContent.TextPrompt): String = buildString {
        appendLine(content.text.encodeField())
        if (content.reasoningText.isNotBlank()) {
            append("reasoning\t")
            appendLine(content.reasoningText.encodeField())
        }
        if (content.completionStatus != AssistantCompletionStatus.Complete) {
            append("completion\t")
            appendLine(content.completionStatus.name.encodeField())
        }
        content.imageAttachments.forEach { image ->
            append("image\t")
            append(
                listOf(image.uri, image.mimeType, image.displayName.orEmpty(), image.byteSize?.toString().orEmpty())
                    .joinToString("\t") { it.encodeField() },
            )
            appendLine()
        }
        content.sources.forEach { source ->
            append("source\t")
            append(
                listOf(
                    source.provider,
                    source.title,
                    source.canonicalUrl,
                    source.language,
                    source.retrievedAtMillis.toString(),
                ).joinToString("\t") { it.encodeField() },
            )
            appendLine()
        }
    }

    private fun decodeTextPrompt(
        payload: String?,
        legacyText: String,
    ): MessageContent.TextPrompt {
        if (payload == null) return MessageContent.TextPrompt(legacyText)
        val lines = payload.lineSequence().toList()
        val text = lines.firstOrNull()?.decodeField() ?: legacyText
        val reasoningText =
            lines.drop(1).firstNotNullOfOrNull { line ->
                val fields = line.split('\t')
                fields.getOrNull(1)?.decodeField().takeIf { fields.firstOrNull() == "reasoning" }
            }.orEmpty()
        val images =
            lines.drop(1).mapNotNull { line ->
                val fields = line.split('\t')
                if (fields.firstOrNull() != "image") return@mapNotNull null
                ImageAttachment(
                    uri = fields.getOrElse(1) { "" }.decodeField(),
                    mimeType = fields.getOrElse(2) { "image/*" }.decodeField(),
                    displayName = fields.getOrNull(3)?.decodeField()?.takeIf { it.isNotBlank() },
                    byteSize = fields.getOrNull(4)?.decodeField()?.toLongOrNull(),
                )
            }
        val sources =
            lines.drop(1).mapNotNull { line ->
                val fields = line.split('\t')
                if (fields.firstOrNull() != "source") return@mapNotNull null
                com.jesjobom.ararai.knowledge.KnowledgeSource(
                    provider = fields.getOrElse(1) { "" }.decodeField(),
                    title = fields.getOrElse(2) { "" }.decodeField(),
                    canonicalUrl = fields.getOrElse(3) { "" }.decodeField(),
                    language = fields.getOrElse(4) { "" }.decodeField(),
                    retrievedAtMillis = fields.getOrNull(5)?.decodeField()?.toLongOrNull() ?: 0L,
                )
            }
        val completionStatus =
            lines.drop(1).firstNotNullOfOrNull { line ->
                val fields = line.split('\t')
                fields.getOrNull(1)
                    ?.decodeField()
                    ?.takeIf { fields.firstOrNull() == "completion" }
                    ?.let { runCatching { AssistantCompletionStatus.valueOf(it) }.getOrNull() }
            } ?: AssistantCompletionStatus.Complete
        return MessageContent.TextPrompt(text, images, reasoningText, sources, completionStatus)
    }

    private fun String.encodeField(): String = java.util.Base64
        .getEncoder()
        .encodeToString(toByteArray(Charsets.UTF_8))

    private fun String.decodeField(): String = String(
        java.util.Base64
            .getDecoder()
            .decode(this),
        Charsets.UTF_8,
    )
}

internal fun String.cleanTitle(): String = trim().takeIf { it.isNotEmpty() }?.take(80) ?: "New chat"

private fun newId(): String = UUID.randomUUID().toString()

private fun nowMillis(): Long = System.currentTimeMillis()
