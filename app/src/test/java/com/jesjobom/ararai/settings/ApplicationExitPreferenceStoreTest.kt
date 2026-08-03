package com.jesjobom.ararai.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ApplicationExitPreferenceStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences(
            SharedPreferencesApplicationExitPreferenceStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }

    @Test
    fun `exit confirmation is enabled by default and can be disabled persistently`() {
        val store = SharedPreferencesApplicationExitPreferenceStore(context)

        assertTrue(store.shouldConfirmExit)
        store.disableExitConfirmation()

        assertFalse(SharedPreferencesApplicationExitPreferenceStore(context).shouldConfirmExit)
    }
}
