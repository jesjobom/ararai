package com.jesjobom.ararai.chat

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.jesjobom.ararai.knowledge.KnowledgeSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChatSessionStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val openStores = mutableListOf<SqliteChatSessionStore>()

    @Before
    fun setUp() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        openStores.forEach(SqliteChatSessionStore::close)
        openStores.clear()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun `upgrades legacy text history to structured text prompts`() {
        createLegacyDatabase()

        val messages = store().getMessages("session-1")

        assertEquals(1, messages.size)
        assertEquals("Legacy question", messages.single().text)
        assertTrue(messages.single().content is MessageContent.TextPrompt)
        assertEquals(
            "Legacy question",
            (messages.single().content as MessageContent.TextPrompt).text,
        )
        assertEquals(
            AssistantCompletionStatus.Complete,
            (messages.single().content as MessageContent.TextPrompt).completionStatus,
        )
    }

    @Test
    fun `version two migration preserves structured messages and backfills media references`() {
        createVersionTwoDatabase()

        val store = store()
        val messages = store.getMessages("session-1")

        assertEquals(3, messages.size)
        assertEquals("Reasoned answer", messages[0].text)
        assertEquals("Private reasoning", (messages[0].content as MessageContent.TextPrompt).reasoningText)
        assertEquals("file:///chat/image.jpg", messages[1].content.mediaUris().single())
        assertEquals("file:///chat/audio.m4a", messages[2].content.mediaUris().single())
        assertEquals(
            setOf("file:///chat/image.jpg", "file:///chat/audio.m4a"),
            store.referencedMediaUris(),
        )
    }

    @Test
    fun `persists incomplete assistant status and defaults legacy messages to complete`() {
        val store = store()
        val session = store.createSession("Status")
        val incomplete =
            store.appendMessage(
                session.id,
                ChatRole.Assistant,
                MessageContent.TextPrompt(
                    text = "",
                    reasoningText = "partial",
                    completionStatus = AssistantCompletionStatus.Incomplete,
                ),
            )

        val restored =
            store.getMessages(session.id).single { it.id == incomplete.id }.content as MessageContent.TextPrompt

        assertEquals(AssistantCompletionStatus.Incomplete, restored.completionStatus)
        assertEquals("partial", restored.reasoningText)
    }

    @Test
    fun `treats persisted blank completed assistant output as incomplete`() {
        val store = store()
        val session = store.createSession("Blank response")

        val message =
            store.appendMessage(
                sessionId = session.id,
                role = ChatRole.Assistant,
                content = MessageContent.TextPrompt(""),
            )

        val restored = store.getMessages(session.id).single { it.id == message.id }
        val content = restored.content as MessageContent.TextPrompt

        assertEquals(AssistantCompletionStatus.Incomplete, content.completionStatus)
    }

    @Test
    fun `in memory and sqlite stores return the same distinct media references`() {
        val sqlite = store()
        val memory = InMemoryChatSessionStore()

        listOf<ChatSessionStore>(sqlite, memory).forEach { candidate ->
            val first = candidate.createSession("First")
            val second = candidate.createSession("Second")
            candidate.appendMessage(
                first.id,
                ChatRole.User,
                MessageContent.TextPrompt(
                    text = "images",
                    imageAttachments =
                    listOf(
                        ImageAttachment("file:///shared.jpg", "image/jpeg"),
                        ImageAttachment("file:///first.jpg", "image/jpeg"),
                    ),
                ),
            )
            candidate.appendMessage(
                second.id,
                ChatRole.User,
                MessageContent.AudioPromptContent(AudioPrompt("file:///shared.jpg", "audio/mp4")),
            )
        }

        val expected = setOf("file:///shared.jpg", "file:///first.jpg")
        assertEquals(expected, memory.referencedMediaUris())
        assertEquals(expected, sqlite.referencedMediaUris())
    }

    @Test
    fun `in memory and sqlite stores return bounded recent messages in chronological order`() {
        val sqlite = store()
        val memory = InMemoryChatSessionStore()

        listOf<ChatSessionStore>(sqlite, memory).forEach { candidate ->
            val session = candidate.createSession("Long history")
            repeat(8) { index -> candidate.appendMessage(session.id, ChatRole.User, "message-$index") }

            assertEquals(8, candidate.countMessages(session.id))
            assertEquals(
                listOf("message-5", "message-6", "message-7"),
                candidate.getRecentMessages(session.id, 3).map(StoredChatMessage::text),
            )
            assertTrue(candidate.getRecentMessages("missing-session", 3).isEmpty())
            assertEquals(0, candidate.countMessages("missing-session"))
            assertThrows(IllegalArgumentException::class.java) {
                candidate.getRecentMessages(session.id, 0)
            }
        }
    }

    @Test
    fun `session media lookup returns only distinct references owned by that session`() {
        val sqlite = store()
        val memory = InMemoryChatSessionStore()

        listOf<ChatSessionStore>(sqlite, memory).forEach { candidate ->
            val first = candidate.createSession("First")
            val second = candidate.createSession("Second")
            candidate.appendMessage(
                first.id,
                ChatRole.User,
                MessageContent.TextPrompt(
                    "first",
                    listOf(
                        ImageAttachment("file:///shared.jpg", "image/jpeg"),
                        ImageAttachment("file:///first.jpg", "image/jpeg"),
                    ),
                ),
            )
            candidate.appendMessage(
                second.id,
                ChatRole.User,
                MessageContent.AudioPromptContent(AudioPrompt("file:///shared.jpg", "audio/wav")),
            )

            assertEquals(
                setOf("file:///shared.jpg", "file:///first.jpg"),
                candidate.mediaUrisForSession(first.id),
            )
            assertEquals(setOf("file:///shared.jpg"), candidate.mediaUrisForSession(second.id))
            assertTrue(candidate.mediaUrisForSession("missing-session").isEmpty())
        }
    }

    @Test
    fun `sqlite reference lookup does not decode complete message payloads`() {
        val store = store()
        repeat(12) { index ->
            val session = store.createSession("Session $index")
            store.appendMessage(
                session.id,
                ChatRole.User,
                MessageContent.TextPrompt(
                    text = "message $index",
                    imageAttachments = listOf(ImageAttachment("file:///image-$index.jpg", "image/jpeg")),
                ),
            )
        }
        store.writableDatabase.execSQL("UPDATE chat_messages SET content_payload = 'invalid-payload'")

        val references = store.referencedMediaUris()

        assertEquals(12, references.size)
        assertTrue(references.contains("file:///image-0.jpg"))
        assertTrue(references.contains("file:///image-11.jpg"))
    }

    @Test
    fun `message updates replace media references atomically`() {
        val store = store()
        val session = store.createSession("Media")
        val message =
            store.appendMessage(
                session.id,
                ChatRole.User,
                MessageContent.TextPrompt(
                    "before",
                    imageAttachments = listOf(ImageAttachment("file:///before.jpg", "image/jpeg")),
                ),
            )

        store.updateMessage(
            message.id,
            MessageContent.AudioPromptContent(AudioPrompt("file:///after.m4a", "audio/mp4")),
        )

        assertEquals(setOf("file:///after.m4a"), store.referencedMediaUris())
    }

    @Test
    fun `sqlite store round trips audio transcription state`() {
        val store = store()
        val session = store.createSession("Transcribed")
        store.appendMessage(
            session.id,
            ChatRole.User,
            MessageContent.AudioPromptContent(
                audio = AudioPrompt("file:///spoken.wav", "audio/wav", durationMillis = 900),
                imageAttachments = listOf(ImageAttachment("file:///spoken.jpg", "image/jpeg", "spoken.jpg", 42)),
                transcript = "hello locally",
                transcriptionStatus = AudioTranscriptionStatus.Completed,
                transcriptionFailureKind = AudioTranscriptionFailureKind.EmptyResults,
                transcriptionDiagnostic = "events=results:hypotheses=0",
                transcriptionMayBeIncomplete = true,
                transcriptionIncompleteReason = "unexpected_completion_source:standard_results",
            ),
        )

        val content = store.getMessages(session.id).single().content as MessageContent.AudioPromptContent

        assertEquals("hello locally", content.transcript)
        assertEquals("file:///spoken.jpg", content.imageAttachments.single().uri)
        assertEquals(
            setOf("file:///spoken.wav", "file:///spoken.jpg"),
            store.referencedMediaUris(),
        )
        assertEquals(AudioTranscriptionStatus.Completed, content.transcriptionStatus)
        assertEquals(AudioTranscriptionFailureKind.EmptyResults, content.transcriptionFailureKind)
        assertEquals("events=results:hypotheses=0", content.transcriptionDiagnostic)
        assertTrue(content.transcriptionMayBeIncomplete)
        assertEquals(
            "unexpected_completion_source:standard_results",
            content.transcriptionIncompleteReason,
        )
        assertEquals("hello locally", content.displayText)
    }

    @Test
    fun `failed reference replacement rolls back message and its old references`() {
        val store = store()
        val session = store.createSession("Media")
        val message =
            store.appendMessage(
                session.id,
                ChatRole.User,
                MessageContent.TextPrompt(
                    "before",
                    imageAttachments = listOf(ImageAttachment("file:///before.jpg", "image/jpeg")),
                ),
            )
        store.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_media_reference_insert
            BEFORE INSERT ON chat_media_references
            WHEN NEW.uri = 'file:///blocked.jpg'
            BEGIN
                SELECT RAISE(ABORT, 'injected reference failure');
            END
            """.trimIndent(),
        )

        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            store.updateMessage(
                message.id,
                MessageContent.TextPrompt(
                    "after",
                    imageAttachments = listOf(ImageAttachment("file:///blocked.jpg", "image/jpeg")),
                ),
            )
        }

        assertEquals("before", store.getMessages(session.id).single().text)
        assertEquals(setOf("file:///before.jpg"), store.referencedMediaUris())
    }

    @Test
    fun `shared media remains referenced until every owning session is deleted`() {
        val store = store()
        val first = store.createSession("First")
        val second = store.createSession("Second")
        val shared =
            MessageContent.TextPrompt(
                "shared",
                imageAttachments = listOf(ImageAttachment("file:///shared.jpg", "image/jpeg")),
            )
        store.appendMessage(first.id, ChatRole.User, shared)
        store.appendMessage(second.id, ChatRole.User, shared)

        store.deleteSession(first.id)
        assertEquals(setOf("file:///shared.jpg"), store.referencedMediaUris())

        store.deleteSession(second.id)
        assertTrue(store.referencedMediaUris().isEmpty())
    }

    @Test
    fun `persists assistant reasoning separately from final text`() {
        val store = store()
        val session = store.ensureSession()
        val message =
            store.appendMessage(
                sessionId = session.id,
                role = ChatRole.Assistant,
                content =
                MessageContent.TextPrompt(
                    text = "Final answer",
                    reasoningText = "Private scratchpad",
                ),
            )

        val restored = store().getMessages(session.id).single { it.id == message.id }
        val content = restored.content as MessageContent.TextPrompt

        assertEquals("Final answer", content.text)
        assertEquals("Private scratchpad", content.reasoningText)
        assertEquals("Final answer", restored.text)
    }

    @Test
    fun `persists bounded source metadata with assistant answer across restart`() {
        val store = store()
        val session = store.ensureSession()
        val source =
            KnowledgeSource(
                provider = "Wikipedia",
                title = "Ada Lovelace",
                canonicalUrl = "https://en.wikipedia.org/wiki/Ada_Lovelace",
                language = "en",
                retrievedAtMillis = 1234L,
            )
        val message =
            store.appendMessage(
                session.id,
                ChatRole.Assistant,
                MessageContent.TextPrompt(text = "Final answer", sources = listOf(source)),
            )

        val restored = store().getMessages(session.id).single { it.id == message.id }

        assertEquals(listOf(source), (restored.content as MessageContent.TextPrompt).sources)
        assertEquals("Final answer", restored.text)
    }

    @Test
    fun `clears every session and message`() {
        val store = store()
        val first = store.ensureSession()
        store.appendMessage(first.id, ChatRole.User, "first")
        val second = store.createSession("Second")
        store.appendMessage(second.id, ChatRole.Assistant, "second")

        store.clearSessions()

        assertTrue(store.listSessions().isEmpty())
        assertTrue(store.getMessages(first.id).isEmpty())
        assertTrue(store.getMessages(second.id).isEmpty())
    }

    @Test
    fun `successful append commits message timestamp and reorders session together`() {
        val store = store()
        val first = store.createSession("First")
        val second = store.createSession("Second")
        store.writableDatabase.execSQL(
            "UPDATE chat_sessions SET updated_at_millis = CASE id WHEN ? THEN 100 ELSE 200 END",
            arrayOf(first.id),
        )

        val message = store.appendMessage(first.id, ChatRole.User, "new message")

        assertEquals(message.id, store.getMessages(first.id).single().id)
        assertEquals(message.createdAtMillis, store.listSessions().first { it.id == first.id }.updatedAtMillis)
        assertEquals(first.id, store.listSessions().first().id)
        assertEquals(second.id, store.listSessions().last().id)
    }

    @Test
    fun `sqlite append to missing session fails without orphan message`() {
        val store = store()

        assertThrows(ChatPersistenceException::class.java) {
            store.appendMessage("missing-session", ChatRole.User, "orphan")
        }

        assertTrue(store.getMessages("missing-session").isEmpty())
        assertEquals(0, messageCount(store))
        assertTrue(store.referencedMediaUris().isEmpty())
    }

    @Test
    fun `in memory append to missing session follows atomic store contract`() {
        val store = InMemoryChatSessionStore()

        assertThrows(ChatPersistenceException::class.java) {
            store.appendMessage("missing-session", ChatRole.User, "orphan")
        }

        assertTrue(store.getMessages("missing-session").isEmpty())
    }

    @Test
    fun `session update failure rolls back inserted message and timestamp`() {
        val store = store()
        val session = store.ensureSession()
        val originalTimestamp = session.updatedAtMillis
        store.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_chat_session_update
            BEFORE UPDATE OF updated_at_millis ON chat_sessions
            BEGIN
                SELECT RAISE(ABORT, 'injected session update failure');
            END
            """.trimIndent(),
        )

        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            store.appendMessage(session.id, ChatRole.User, "must roll back")
        }

        assertTrue(store.getMessages(session.id).isEmpty())
        assertEquals(originalTimestamp, store.listSessions().single().updatedAtMillis)
        assertTrue(store.referencedMediaUris().isEmpty())
    }

    private fun messageCount(store: SqliteChatSessionStore): Int = store.readableDatabase.rawQuery("SELECT COUNT(*) FROM chat_messages", emptyArray()).use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
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

    private fun createVersionTwoDatabase() {
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
                    content_kind TEXT NOT NULL DEFAULT 'text',
                    content_payload TEXT,
                    created_at_millis INTEGER NOT NULL,
                    FOREIGN KEY(session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX chat_messages_session_created ON chat_messages(session_id, created_at_millis)")
            db.execSQL("INSERT INTO chat_sessions VALUES('session-1', 'Migrated', 100, 200)")
            insertVersionTwoMessage(
                db = db,
                id = "reasoning",
                role = "Assistant",
                text = "Reasoned answer",
                kind = "text",
                payload = "${encodeField("Reasoned answer")}\nreasoning\t${encodeField("Private reasoning")}\n",
                createdAt = 110,
            )
            insertVersionTwoMessage(
                db = db,
                id = "image",
                role = "User",
                text = "Image",
                kind = "text",
                payload = "${encodeField(
                    "Image",
                )}\nimage\t${encodeField(
                    "file:///chat/image.jpg",
                )}\t${encodeField("image/jpeg")}\t${encodeField("image.jpg")}\t${encodeField("42")}\n",
                createdAt = 120,
            )
            insertVersionTwoMessage(
                db = db,
                id = "audio",
                role = "User",
                text = "Audio",
                kind = "audio",
                payload =
                listOf("file:///chat/audio.m4a", "audio/mp4", "audio.m4a", "84", "1000")
                    .joinToString("\t", transform = ::encodeField),
                createdAt = 130,
            )
            db.version = 2
        }
    }

    private fun insertVersionTwoMessage(
        db: SQLiteDatabase,
        id: String,
        role: String,
        text: String,
        kind: String,
        payload: String,
        createdAt: Long,
    ) {
        db.execSQL(
            """
            INSERT INTO chat_messages(
                id, session_id, role, text, content_kind, content_payload, created_at_millis
            ) VALUES(?, 'session-1', ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(id, role, text, kind, payload, createdAt),
        )
    }

    private fun encodeField(value: String): String = java.util.Base64
        .getEncoder()
        .encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun store(): SqliteChatSessionStore = SqliteChatSessionStore(context).also(openStores::add)

    private companion object {
        const val DATABASE_NAME = "ararai_chat.db"
    }
}
