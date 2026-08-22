package com.jesjobom.ararai.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDownloadPromptPreferenceStoreTest {
    @Test
    fun `prompt starts unhandled and remains handled after acknowledgement`() {
        val store = InMemoryModelDownloadPromptPreferenceStore()

        assertFalse(store.wasHandled)

        store.markHandled()

        assertTrue(store.wasHandled)
    }

    @Test
    fun `handled state can be restored`() {
        val store = InMemoryModelDownloadPromptPreferenceStore(initialWasHandled = true)

        assertTrue(store.wasHandled)
    }
}
