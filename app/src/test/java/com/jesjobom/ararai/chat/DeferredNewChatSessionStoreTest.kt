package com.jesjobom.ararai.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeferredNewChatSessionStoreTest {
    @Test
    fun `new chat is reused and not persisted before receiving a title`() {
        val persisted = InMemoryChatSessionStore()
        val store = DeferredNewChatSessionStore(persisted)

        val first = store.createSession("New chat")
        val second = store.createSession("New chat")

        assertEquals(first.id, second.id)
        assertTrue(persisted.listSessions().isEmpty())
        assertEquals(listOf(first.id), store.listSessions().map(ChatSession::id))
    }

    @Test
    fun `renaming persists staged voice content and keeps public session id stable`() {
        val persisted = InMemoryChatSessionStore()
        val store = DeferredNewChatSessionStore(persisted)
        val pending = store.createSession("New chat")
        val staged = store.appendMessage(pending.id, ChatRole.User, "Hello from voice")

        store.updateMessage(staged.id, MessageContent.TextPrompt("Transcribed voice"))
        store.renameSession(pending.id, "Transcribed voice")

        assertEquals(1, persisted.listSessions().size)
        assertEquals("Transcribed voice", persisted.listSessions().single().title)
        assertEquals(pending.id, store.listSessions().single().id)
        assertEquals("Transcribed voice", store.getMessages(pending.id).single().text)
    }

    @Test
    fun `bounded queries preserve pending public session identity across promotion`() {
        val persisted = InMemoryChatSessionStore()
        val store = DeferredNewChatSessionStore(persisted)
        val pending = store.createSession("New chat")
        repeat(4) { index ->
            store.appendMessage(
                pending.id,
                ChatRole.User,
                MessageContent.AudioPromptContent(AudioPrompt("file:///voice-$index.wav", "audio/wav")),
            )
        }

        assertEquals(4, store.countMessages(pending.id))
        assertEquals(
            listOf("file:///voice-2.wav", "file:///voice-3.wav"),
            store.getRecentMessages(pending.id, 2).map { (it.content as MessageContent.AudioPromptContent).audio.uri },
        )
        assertEquals(4, store.mediaUrisForSession(pending.id).size)

        store.renameSession(pending.id, "Promoted")

        assertEquals(4, store.countMessages(pending.id))
        assertTrue(store.getRecentMessages(pending.id, 2).all { it.sessionId == pending.id })
        assertEquals(4, store.mediaUrisForSession(pending.id).size)
    }
}
