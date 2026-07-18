package com.jesjobom.ararai.settings

import com.jesjobom.ararai.settings.SharedPreferencesThemePreferenceStore.Companion.decodeThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePreferenceStoreTest {
    @Test
    fun `missing and unknown stored values follow system`() {
        assertEquals(ThemeMode.System, decodeThemeMode(null))
        assertEquals(ThemeMode.System, decodeThemeMode("FutureMode"))
    }

    @Test
    fun `stored enum names restore every supported mode`() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, decodeThemeMode(mode.name))
        }
    }

    @Test
    fun `in-memory store publishes updates`() {
        val store = InMemoryThemePreferenceStore()

        store.setThemeMode(ThemeMode.Dark)

        assertEquals(ThemeMode.Dark, store.themeMode.value)
    }
}
