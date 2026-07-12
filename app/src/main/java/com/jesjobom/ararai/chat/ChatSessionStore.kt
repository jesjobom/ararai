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
    val text: String,
    val createdAtMillis: Long,
)

interface ChatSessionStore {
    fun ensureSession(): ChatSession
    fun listSessions(): List<ChatSession>
    fun getMessages(sessionId: String): List<StoredChatMessage>
    fun createSession(title: String): ChatSession
    fun renameSession(sessionId: String, title: String): ChatSession
    fun deleteSession(sessionId: String)
    fun appendMessage(sessionId: String, role: ChatRole, text: String): StoredChatMessage
    fun updateMessage(messageId: String, text: String)
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

    override fun appendMessage(sessionId: String, role: ChatRole, text: String): StoredChatMessage {
        val now = nextMessageTimestamp(sessionId)
        val message = StoredChatMessage(
            id = newId(),
            sessionId = sessionId,
            role = role,
            text = text,
            createdAtMillis = now,
        )
        messages[message.id] = message
        touch(sessionId)
        return message
    }

    override fun updateMessage(messageId: String, text: String) {
        val current = messages.getValue(messageId)
        messages[messageId] = current.copy(text = text)
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
                created_at_millis INTEGER NOT NULL,
                FOREIGN KEY(session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX chat_messages_session_created ON chat_messages(session_id, created_at_millis)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

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
            SELECT id, session_id, role, text, created_at_millis
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
                    text = cursor.getString(3),
                    createdAtMillis = cursor.getLong(4),
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
    override fun appendMessage(sessionId: String, role: ChatRole, text: String): StoredChatMessage {
        val now = nextMessageTimestamp(sessionId)
        val message = StoredChatMessage(
            id = newId(),
            sessionId = sessionId,
            role = role,
            text = text,
            createdAtMillis = now,
        )
        val db = writableDatabase
        db.insertOrThrow("chat_messages", null, message.toContentValues())
        db.update(
            "chat_sessions",
            ContentValues().apply { put("updated_at_millis", now) },
            "id = ?",
            arrayOf(sessionId),
        )
        return message
    }

    @Synchronized
    override fun updateMessage(messageId: String, text: String) {
        writableDatabase.update(
            "chat_messages",
            ContentValues().apply { put("text", text) },
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
        ContentValues().apply {
            put("id", id)
            put("session_id", sessionId)
            put("role", role.name)
            put("text", text)
            put("created_at_millis", createdAtMillis)
        }

    private fun nextMessageTimestamp(sessionId: String): Long {
        readableDatabase.rawQuery(
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
        const val DATABASE_VERSION = 1
    }
}

internal fun String.cleanTitle(): String =
    trim().takeIf { it.isNotEmpty() }?.take(80) ?: "New chat"

private fun newId(): String = UUID.randomUUID().toString()

private fun nowMillis(): Long = System.currentTimeMillis()
