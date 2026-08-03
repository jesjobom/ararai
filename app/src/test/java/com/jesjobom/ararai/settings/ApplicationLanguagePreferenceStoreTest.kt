package com.jesjobom.ararai.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jesjobom.ararai.settings.SharedPreferencesApplicationLanguagePreferenceStore.Companion.decodeLanguage
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ApplicationLanguagePreferenceStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences(
            SharedPreferencesApplicationLanguagePreferenceStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }

    @Test
    fun `missing and unknown stored values follow system`() {
        assertEquals(ApplicationLanguage.System, decodeLanguage(null))
        assertEquals(ApplicationLanguage.System, decodeLanguage("FutureLanguage"))
    }

    @Test
    fun `store persists every supported language`() {
        val store = SharedPreferencesApplicationLanguagePreferenceStore(context)

        ApplicationLanguage.entries.forEach { language ->
            store.setLanguage(language)
            assertEquals(language, SharedPreferencesApplicationLanguagePreferenceStore(context).language)
        }
    }

    @Test
    fun `localized context uses selected language tag`() {
        val localized =
            SharedPreferencesApplicationLanguagePreferenceStore.localizedContext(
                context,
                ApplicationLanguage.PortugueseBrazil,
            )

        @Suppress("DEPRECATION")
        assertEquals("pt-BR", localized.resources.configuration.locale.toLanguageTag())
    }
}
