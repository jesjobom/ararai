package com.jesjobom.ararai.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChatPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences("chat_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `shows audio transcriptions by default and persists changes`() {
        val preferences = SharedPreferencesChatPreferences(context)
        assertTrue(preferences.showAudioTranscriptions.value)

        preferences.setShowAudioTranscriptions(false)

        assertFalse(SharedPreferencesChatPreferences(context).showAudioTranscriptions.value)
    }
}
