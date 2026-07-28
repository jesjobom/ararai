package com.jesjobom.ararai.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstructionPreferencesTest {
    @Test
    fun `uses independent checked in defaults`() {
        val preferences = InMemoryInstructionPreferences()

        assertEquals(InstructionDefaults.CHAT, preferences.settings.value.chatInstruction)
        assertEquals(InstructionDefaults.VOICE, preferences.settings.value.voiceInstruction)
        assertFalse(preferences.settings.value.wikipediaEnabled)
    }

    @Test
    fun `edits and restores only selected mode`() {
        val preferences = InMemoryInstructionPreferences()
        preferences.setInstruction(InteractionMode.Chat, "  Detailed answers.  ")
        preferences.setInstruction(InteractionMode.Voice, "Short answers.")

        preferences.restoreDefault(InteractionMode.Chat)

        assertEquals(InstructionDefaults.CHAT, preferences.settings.value.chatInstruction)
        assertEquals("Short answers.", preferences.settings.value.voiceInstruction)
    }

    @Test
    fun `effective instruction always retains app invariants`() {
        val settings = InstructionSettings(chatInstruction = "")

        val effective = effectiveSystemInstruction(settings, InteractionMode.Chat)

        assertEquals(InstructionDefaults.APP_INVARIANTS, effective)
        assertTrue(effective.contains("untrusted"))
    }

    @Test
    fun `bounds editable instructions`() {
        val preferences = InMemoryInstructionPreferences()
        preferences.setInstruction(InteractionMode.Chat, "x".repeat(InstructionDefaults.MAX_LENGTH + 50))

        assertEquals(InstructionDefaults.MAX_LENGTH, preferences.settings.value.chatInstruction.length)
    }
}
