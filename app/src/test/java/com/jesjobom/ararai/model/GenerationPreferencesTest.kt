package com.jesjobom.ararai.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jesjobom.ararai.engine.GenerationMetrics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GenerationPreferencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val catalog =
        InferenceConfig(
            contextTokens = 2_048,
            promptReserveTokens = 512,
            temperature = 0.7f,
            topP = 0.9f,
        )

    @After
    fun clearPreferences() {
        context.getSharedPreferences("generation_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `resolves independent overrides per model and restores catalog defaults`() {
        val preferences = InMemoryGenerationPreferences()

        preferences.setContextTokens("e2b", 4_096)
        preferences.setTemperature("e2b", TemperaturePreset.Precise.value)

        assertEquals(4_096, preferences.state.value.resolve("e2b", catalog).contextTokens)
        assertEquals(0.2f, preferences.state.value.resolve("e2b", catalog).temperature)
        assertEquals(catalog, preferences.state.value.resolve("e4b", catalog))

        preferences.restoreDefaults("e2b")

        assertEquals(catalog, preferences.state.value.resolve("e2b", catalog))
        assertFalse(preferences.state.value.overrides.containsKey("e2b"))
    }

    @Test
    fun `rejects invalid values and retains runtime metrics separately`() {
        val preferences = InMemoryGenerationPreferences()
        val metrics = GenerationMetrics(120, 20, 100.0, 8, 16.0)

        assertThrows(IllegalArgumentException::class.java) {
            preferences.setContextTokens("e2b", 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            preferences.setTemperature("e2b", Float.NaN)
        }

        preferences.recordMetrics("e2b", metrics)

        assertEquals(metrics, preferences.state.value.lastMetricsByModel["e2b"])
        assertTrue(preferences.state.value.overrides.isEmpty())
    }

    @Test
    fun `shared preferences restore per model overrides after recreation`() {
        SharedPreferencesGenerationPreferences(context).apply {
            setContextTokens("e4b", 8_192)
            setTemperature("e4b", 0.35f)
        }

        val restored = SharedPreferencesGenerationPreferences(context).state.value.resolve("e4b", catalog)

        assertEquals(8_192, restored.contextTokens)
        assertEquals(0.35f, restored.temperature)
    }
}
