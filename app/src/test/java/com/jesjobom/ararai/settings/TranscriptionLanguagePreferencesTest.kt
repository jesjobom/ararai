package com.jesjobom.ararai.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TranscriptionLanguagePreferencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences(
            SharedPreferencesTranscriptionLanguagePreferences.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }

    @Test
    fun `defaults to automatic and safely decodes unknown values`() {
        assertEquals(
            TranscriptionLanguage.Automatic,
            SharedPreferencesTranscriptionLanguagePreferences(context).language.value,
        )
        assertEquals(
            TranscriptionLanguage.Automatic,
            SharedPreferencesTranscriptionLanguagePreferences.decodeLanguage("FutureValue"),
        )
    }

    @Test
    fun `persists selected language`() {
        SharedPreferencesTranscriptionLanguagePreferences(context)
            .setLanguage(TranscriptionLanguage.Portuguese)

        assertEquals(
            TranscriptionLanguage.Portuguese,
            SharedPreferencesTranscriptionLanguagePreferences(context).language.value,
        )
    }

    @Test
    fun `resolves dynamic and fixed language choices`() {
        val system = { "fr-CA" }
        val ui = { "pt-BR" }

        assertEquals("auto", TranscriptionLanguage.Automatic.resolveLanguageTag(system, ui))
        assertEquals("fr-CA", TranscriptionLanguage.System.resolveLanguageTag(system, ui))
        assertEquals("pt-BR", TranscriptionLanguage.Interface.resolveLanguageTag(system, ui))
        assertEquals("en", TranscriptionLanguage.English.resolveLanguageTag(system, ui))
        assertEquals("pt", TranscriptionLanguage.Portuguese.resolveLanguageTag(system, ui))
    }
}
