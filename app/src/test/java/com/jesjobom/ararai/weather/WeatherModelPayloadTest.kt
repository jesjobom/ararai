package com.jesjobom.ararai.weather

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherModelPayloadTest {
    @Test
    fun `summarizes a full day for the model without changing daily totals`() {
        val source = JsonObject().apply {
            addProperty("kind", "success")
            add("days", JsonArray().apply { add(JsonObject().apply { addProperty("precipitationSumMm", 6.0) }) })
            add(
                "hours",
                JsonArray().apply {
                    repeat(24) { hour ->
                        add(
                            JsonObject().apply {
                                addProperty("time", "2026-11-08T${hour.toString().padStart(2, '0')}:00")
                                addProperty("precipitationProbabilityPercent", if (hour == 8) 80 else 10)
                                addProperty("precipitationMm", if (hour in 6..11) 1.0 else 0.0)
                            },
                        )
                    }
                },
            )
        }

        val compact = JsonParser.parseString(weatherPayloadForModel(source.toString())).asJsonObject

        assertFalse(compact.has("hours"))
        assertEquals(6.0, compact.getAsJsonArray("days")[0].asJsonObject.get("precipitationSumMm").asDouble, 0.0)
        val windows = compact.getAsJsonArray("rainWindows")
        assertEquals(4, windows.size())
        assertEquals("2026-11-08T06:00", windows[1].asJsonObject.get("startTime").asString)
        assertEquals("2026-11-08T11:00", windows[1].asJsonObject.get("lastHour").asString)
        assertEquals(80.0, windows[1].asJsonObject.get("maxRainProbabilityPercent").asDouble, 0.0)
        assertEquals(6.0, windows[1].asJsonObject.get("precipitationSumMm").asDouble, 0.0)
        assertTrue(compact.toString().length < source.toString().length / 2)
    }
}
