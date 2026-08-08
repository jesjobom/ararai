package com.jesjobom.ararai.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedPreferencesInstructionPreferencesTest {
    private lateinit var context: Context

    @Before
    fun clearPreferences() {
        context = ApplicationProvider.getApplicationContext()
        context
            .getSharedPreferences("instruction_preferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `persists independent mode instructions and tool preferences`() {
        val first = SharedPreferencesInstructionPreferences(context)
        first.setInstruction(InteractionMode.Chat, "Detailed answers.")
        first.setInstruction(InteractionMode.Voice, "Brief answers.")
        first.setWikipediaEnabled(true)
        first.setCalculatorEnabled(true)

        val restored = SharedPreferencesInstructionPreferences(context)

        assertEquals("Detailed answers.", restored.settings.value.chatInstruction)
        assertEquals("Brief answers.", restored.settings.value.voiceInstruction)
        assertTrue(restored.settings.value.wikipediaEnabled)
        assertTrue(restored.settings.value.calculatorEnabled)
    }

    @Test
    fun `restore default removes customization without changing the other mode`() {
        val first = SharedPreferencesInstructionPreferences(context)
        first.setInstruction(InteractionMode.Chat, "Custom Chat.")
        first.setInstruction(InteractionMode.Voice, "Custom Voice.")
        first.restoreDefault(InteractionMode.Chat)

        val restored = SharedPreferencesInstructionPreferences(context)

        assertEquals(InstructionDefaults.CHAT, restored.settings.value.chatInstruction)
        assertEquals("Custom Voice.", restored.settings.value.voiceInstruction)
        assertFalse(restored.settings.value.wikipediaEnabled)
        assertFalse(restored.settings.value.calculatorEnabled)
    }
}
