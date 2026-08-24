package com.jesjobom.ararai.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jesjobom.ararai.R
import com.jesjobom.ararai.settings.ApplicationLanguage
import com.jesjobom.ararai.settings.SharedPreferencesApplicationLanguagePreferenceStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Suppress("MaxLineLength")
class LocalizedSessionFallbackTitleTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val instant = Instant.parse("2026-08-23T20:15:00Z").toEpochMilli()
    private val timeZone = TimeZone.getTimeZone("America/Toronto")

    @Test
    fun `formats audio-message fallback title in configured English`() {
        val localizedContext = localizedContext(ApplicationLanguage.English)

        assertEquals(
            "Voice message · Aug 23, 2026, 4:15:00 PM",
            localizedSessionFallbackTitle(
                context = localizedContext,
                titleResource = R.string.audio_message_session_fallback_title,
                nowMillis = instant,
                timeZone = timeZone,
            ),
        )
    }

    @Test
    fun `formats voice-chat fallback title in configured Brazilian Portuguese`() {
        val localizedContext = localizedContext(ApplicationLanguage.PortugueseBrazil)

        assertEquals(
            "Conversa por voz · 23 de ago. de 2026 16:15:00",
            localizedSessionFallbackTitle(
                context = localizedContext,
                titleResource = R.string.voice_session_fallback_title,
                nowMillis = instant,
                timeZone = timeZone,
            ),
        )
    }

    private fun localizedContext(language: ApplicationLanguage): Context = SharedPreferencesApplicationLanguagePreferenceStore.localizedContext(context, language)
}
