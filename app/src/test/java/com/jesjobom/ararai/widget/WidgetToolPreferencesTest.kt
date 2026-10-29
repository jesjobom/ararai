package com.jesjobom.ararai.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetToolPreferencesTest {
    private lateinit var context: Context

    @Before
    fun clearPreferences() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("instruction_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `migration preserves enabled widget state and then separates Chat`() {
        val stored = context.getSharedPreferences("instruction_preferences", Context.MODE_PRIVATE)
        stored.edit().putBoolean("wikipedia_enabled", true).commit()

        val widgets = SharedPreferencesWidgetToolPreferences(context)
        assertTrue(widgets.wikipediaEnabled.value)
        stored.edit().putBoolean("wikipedia_enabled", false).commit()
        assertTrue(SharedPreferencesWidgetToolPreferences(context).wikipediaEnabled.value)

        widgets.setWikipediaEnabled(false)
        assertFalse(SharedPreferencesWidgetToolPreferences(context).wikipediaEnabled.value)
        assertFalse(stored.getBoolean("wikipedia_enabled", true))
    }

    @Test
    fun `migration preserves disabled widget state independently of later Chat changes`() {
        val stored = context.getSharedPreferences("instruction_preferences", Context.MODE_PRIVATE)
        val widgets = SharedPreferencesWidgetToolPreferences(context)
        assertFalse(widgets.wikipediaEnabled.value)
        stored.edit().putBoolean("wikipedia_enabled", true).commit()
        assertFalse(SharedPreferencesWidgetToolPreferences(context).wikipediaEnabled.value)
    }

    @Test
    fun `widget web search defaults off and persists independently`() {
        val widgets = SharedPreferencesWidgetToolPreferences(context)
        assertFalse(widgets.webSearchEnabled.value)

        widgets.setWebSearchEnabled(true)
        assertTrue(SharedPreferencesWidgetToolPreferences(context).webSearchEnabled.value)
        assertFalse(widgets.wikipediaEnabled.value)
    }
}
