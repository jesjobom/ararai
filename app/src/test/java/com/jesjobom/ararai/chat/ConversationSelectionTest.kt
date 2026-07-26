package com.jesjobom.ararai.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationSelectionTest {
    @Test
    fun `shares current conversation across destinations`() {
        val selection = ConversationSelection()

        selection.select("chat-session")

        assertEquals("chat-session", selection.currentSessionId)
        selection.clear("another-session")
        assertEquals("chat-session", selection.currentSessionId)
        selection.clear("chat-session")
        assertNull(selection.currentSessionId)
    }
}
