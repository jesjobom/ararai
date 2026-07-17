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

class ChatPersistenceException(message: String) : IllegalStateException(message)

interface ChatSessionStore {
    fun ensureSession(): ChatSession
    fun listSessions(): List<ChatSession>
    fun getMessages(sessionId: String): List<StoredChatMessage>
    fun createSession(title: String): ChatSession
    fun renameSession(sessionId: String, title: String): ChatSession
    fun deleteSession(sessionId: String)
    fun clearSessions()
    fun appendMessage(sessionId: String, role: ChatRole, content: MessageContent): StoredChatMessage
    fun appendMessage(sessionId: String, role: ChatRole, text: String): StoredChatMessage =
        appendMessage(sessionId, role, MessageContent.TextPrompt(text))
    fun updateMessage(messageId: String, content: MessageContent)
    fun updateMessage(messageId: String, text: String) {
        updateMessage(messageId, MessageContent.TextPrompt(text))
    }

    fun referencedMediaUris(): Set<String> =
        listSessions().asSequence()
            .flatMap { getMessages(it.id).asSequence() }
            .flatMap { it.content.mediaUris().asSequence() }
            .toSet()
}

fun MessageContent.mediaUris(): Set<String> =
    when (this) {
        is MessageContent.TextPrompt -> imageAttachments.mapTo(linkedSetOf()) { it.uri }
        is MessageContent.AudioPromptContent -> setOf(audio.uri)
    }

class InMemoryChatSessionStore : ChatSessionStore {
    private val sessions = linkedMapOf<String, ChatSession>()
    private val messages = linkedMapOf<String, StoredChatMessage>()

    override fun ensureSession(): ChatSession =
        sessions.values.maxByOrNull { it.updatedAtMillis } ?: createSession("New chat")

    override fun listSessions(): List<ChatSession> =
        sessions.values.sortedWith(compareByDescending<ChatSession> { it.updatedAtMillis }.thenBy { it.title })

    override fun getMessages(sessionId: String): List<StoredChatMessage> =
        messages.values
            .filter { it.sessionId == sessionId }
            .sortedWith(compareBy<StoredChatMessage> { it.createdAtMillis }.thenBy { it.id })

    override fun createSession(title: String): ChatSession {
        val now = nowMillis()
        val session = ChatSession(
            id = newId(),
            title = title.cleanTitle(),
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        sessions[session.id] = session
        return session
    }

    override fun renameSession(sessionId: String, title: String): ChatSession {
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

    override fun appendMessage(sessionId: String, role: ChatRole, content: MessageContent): StoredChatMessage {
        val session = sessions[sessionId]
            ?: throw ChatPersistenceException("Chat session does not exist: $sessionId")
        val now = nextMessageTimestamp(sessionId)
        val message = StoredChatMessage(
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

    override fun updateMessage(messageId: String, content: MessageContent) {
        val current = messages.getValue(messageId)
        messages[messageId] = current.copy(content = content)
        touch(current.sessionId)
    }

    private fun touch(sessionId: String) {
        sessions[sessionId]?.let { sessions[sessionId] = it.copy(updatedAtMillis = nowMillis()) }
    }

    private fun nextMessageTimestamp(sessionId: String): Long {
        val latest = messages.values
            .filter { it.sessionId == sessionId }
            .maxOfOrNull { it.createdAtMillis }
        val now = nowMillis()
        return if (latest == null || now > latest) now else latest + 1L
    }
}

class SqliteChatSessionStore(
    context: Context,
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), ChatSessionStore {
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN content_kind TEXT NOT NULL DEFAULT 'text'")
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN content_payload TEXT")
        }
    }

    @Synchronized
    override fun ensureSession(): ChatSession =
        listSessions().firstOrNull() ?: createSession("New chat")

    @Synchronized
    override fun listSessions(): List<ChatSession> {
        readableDatabase.rawQuery(
            """
            SELECT id, title, created_at_millis, updated_at_millis
            FROM chat_sessions
            ORDER BY updated_at_millis DESC, title ASC
            """.trimIndent(),
            emptyArray(),
        ).use { cursor ->
            val result = mutableListOf<ChatSession>()
            while (cursor.moveToNext()) {
                result += ChatSession(
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
        readableDatabase.rawQuery(
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
                result += StoredChatMessage(
                    id = cursor.getString(0),
                    sessionId = cursor.getString(1),
                    role = ChatRole.valueOf(cursor.getString(2)),
                    content = MessageContentCodec.decode(
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
    override fun createSession(title: String): ChatSession {
        val now = nowMillis()
        val session = ChatSession(
            id = newId(),
            title = title.cleanTitle(),
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        writableDatabase.insertOrThrow("chat_sessions", null, session.toContentValues())
        return session
    }

    @Synchronized
    override fun renameSession(sessionId: String, title: String): ChatSession {
        val values = ContentValues().apply {
            put("title", title.cleanTitle())
            put("updated_at_millis", nowMillis())
        }
        writableDatabase.update("chat_sessions", values, "id = ?", arrayOf(sessionId))
        return listSessions().first { it.id == sessionId }
    }

    @Synchronized
    override fun deleteSession(sessionId: String) {
        val db = writableDatabase
        db.delete("chat_messages", "session_id = ?", arrayOf(sessionId))
        db.delete("chat_sessions", "id = ?", arrayOf(sessionId))
    }

    @Synchronized
    override fun clearSessions() {
        writableDatabase.inTransaction {
            delete("chat_messages", null, null)
            delete("chat_sessions", null, null)
        }
    }

    @Synchronized
    override fun appendMessage(sessionId: String, role: ChatRole, content: MessageContent): StoredChatMessage {
        val db = writableDatabase
        return db.inTransaction {
            val now = nextMessageTimestamp(this, sessionId)
            val message = StoredChatMessage(
                id = newId(),
                sessionId = sessionId,
                role = role,
                content = content,
                createdAtMillis = now,
            )
            insertOrThrow("chat_messages", null, message.toContentValues())
            val updatedSessions = update(
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
    override fun updateMessage(messageId: String, content: MessageContent) {
        val encoded = MessageContentCodec.encode(content)
        writableDatabase.update(
            "chat_messages",
            ContentValues().apply {
                put("text", content.displayText)
                put("content_kind", encoded.kind)
                put("content_payload", encoded.payload)
            },
            "id = ?",
            arrayOf(messageId),
        )
    }

    private fun ChatSession.toContentValues(): ContentValues =
        ContentValues().apply {
            put("id", id)
            put("title", title)
            put("created_at_millis", createdAtMillis)
            put("updated_at_millis", updatedAtMillis)
        }

    private fun StoredChatMessage.toContentValues(): ContentValues =
        MessageContentCodec.encode(content).let { encoded ->
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

    private fun nextMessageTimestamp(db: SQLiteDatabase, sessionId: String): Long {
        db.rawQuery(
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
        const val DATABASE_VERSION = 2
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

    fun encode(content: MessageContent): Encoded =
        when (content) {
            is MessageContent.TextPrompt -> Encoded(
                kind = "text",
                payload = buildString {
                    appendLine(content.text.encodeField())
                    if (content.reasoningText.isNotBlank()) {
                        append("reasoning")
                        append('\t')
                        appendLine(content.reasoningText.encodeField())
                    }
                    content.imageAttachments.forEach { image ->
                        append("image")
                        append('\t')
                        append(image.uri.encodeField())
                        append('\t')
                        append(image.mimeType.encodeField())
                        append('\t')
                        append((image.displayName ?: "").encodeField())
                        append('\t')
                        append(image.byteSize?.toString().orEmpty().encodeField())
                        appendLine()
                    }
                },
            )
            is MessageContent.AudioPromptContent -> Encoded(
                kind = "audio",
                payload = listOf(
                    content.audio.uri,
                    content.audio.mimeType,
                    content.audio.displayName.orEmpty(),
                    content.audio.byteSize?.toString().orEmpty(),
                    content.audio.durationMillis?.toString().orEmpty(),
                ).joinToString(separator = "\t") { it.encodeField() },
            )
        }

    fun decode(kind: String?, payload: String?, legacyText: String): MessageContent =
        when (kind) {
            "audio" -> payload?.split('\t')?.map { it.decodeField() }?.let { fields ->
                MessageContent.AudioPromptContent(
                    AudioPrompt(
                        uri = fields.getOrElse(0) { "" },
                        mimeType = fields.getOrElse(1) { "audio/*" },
                        displayName = fields.getOrNull(2)?.takeIf { it.isNotBlank() },
                        byteSize = fields.getOrNull(3)?.toLongOrNull(),
                        durationMillis = fields.getOrNull(4)?.toLongOrNull(),
                    ),
                )
            } ?: MessageContent.TextPrompt(legacyText)
            else -> {
                if (payload == null) return MessageContent.TextPrompt(legacyText)
                val lines = payload.lineSequence().toList()
                val text = lines.firstOrNull()?.decodeField() ?: legacyText
                val reasoningText = lines.drop(1).firstNotNullOfOrNull { line ->
                    val fields = line.split('\t')
                    if (fields.firstOrNull() == "reasoning") {
                        fields.getOrNull(1)?.decodeField()
                    } else {
                        null
                    }
                }.orEmpty()
                val images = lines.drop(1).mapNotNull { line ->
                    val fields = line.split('\t')
                    if (fields.firstOrNull() != "image") return@mapNotNull null
                    ImageAttachment(
                        uri = fields.getOrElse(1) { "" }.decodeField(),
                        mimeType = fields.getOrElse(2) { "image/*" }.decodeField(),
                        displayName = fields.getOrNull(3)?.decodeField()?.takeIf { it.isNotBlank() },
                        byteSize = fields.getOrNull(4)?.decodeField()?.toLongOrNull(),
                    )
                }
                MessageContent.TextPrompt(text, images, reasoningText)
            }
        }

    private fun String.encodeField(): String =
        java.util.Base64.getEncoder().encodeToString(toByteArray(Charsets.UTF_8))

    private fun String.decodeField(): String =
        String(java.util.Base64.getDecoder().decode(this), Charsets.UTF_8)
}

internal fun String.cleanTitle(): String =
    trim().takeIf { it.isNotEmpty() }?.take(80) ?: "New chat"

private fun newId(): String = UUID.randomUUID().toString()

private fun nowMillis(): Long = System.currentTimeMillis()
