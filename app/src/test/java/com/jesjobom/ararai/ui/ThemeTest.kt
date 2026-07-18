package com.jesjobom.ararai.ui

import com.jesjobom.ararai.settings.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test
    fun `system mode follows system appearance`() {
        assertTrue(ThemeMode.System.resolveDarkTheme(systemDarkTheme = true))
        assertFalse(ThemeMode.System.resolveDarkTheme(systemDarkTheme = false))
    }

    @Test
    fun `explicit modes ignore system appearance`() {
        assertFalse(ThemeMode.Light.resolveDarkTheme(systemDarkTheme = true))
        assertTrue(ThemeMode.Dark.resolveDarkTheme(systemDarkTheme = false))
    }
}
