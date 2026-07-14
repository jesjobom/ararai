package com.jesjobom.ararai.chat

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChatSessionStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun `upgrades legacy text history to structured text prompts`() {
        createLegacyDatabase()

        val messages = SqliteChatSessionStore(context).getMessages("session-1")

        assertEquals(1, messages.size)
        assertEquals("Legacy question", messages.single().text)
        assertTrue(messages.single().content is MessageContent.TextPrompt)
        assertEquals(
            "Legacy question",
            (messages.single().content as MessageContent.TextPrompt).text,
        )
    }

    @Test
    fun `persists assistant reasoning separately from final text`() {
        val store = SqliteChatSessionStore(context)
        val session = store.ensureSession()
        val message = store.appendMessage(
            sessionId = session.id,
            role = ChatRole.Assistant,
            content = MessageContent.TextPrompt(
                text = "Final answer",
                reasoningText = "Private scratchpad",
            ),
        )

        val restored = SqliteChatSessionStore(context).getMessages(session.id).single { it.id == message.id }
        val content = restored.content as MessageContent.TextPrompt

        assertEquals("Final answer", content.text)
        assertEquals("Private scratchpad", content.reasoningText)
        assertEquals("Final answer", restored.text)
    }

    private fun createLegacyDatabase() {
        val databaseFile = context.getDatabasePath(DATABASE_NAME)
        databaseFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { db ->
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
            db.execSQL(
                """
                INSERT INTO chat_sessions(id, title, created_at_millis, updated_at_millis)
                VALUES('session-1', 'Legacy chat', 100, 200)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO chat_messages(id, session_id, role, text, created_at_millis)
                VALUES('message-1', 'session-1', 'User', 'Legacy question', 150)
                """.trimIndent(),
            )
            db.version = 1
        }
    }

    private companion object {
        const val DATABASE_NAME = "ararai_chat.db"
    }
}
