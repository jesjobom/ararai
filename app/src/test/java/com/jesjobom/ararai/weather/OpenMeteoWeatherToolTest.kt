package com.jesjobom.ararai.weather

import com.jesjobom.ararai.knowledge.KnowledgeHttpResponse
import com.jesjobom.ararai.knowledge.KnowledgeHttpTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoWeatherToolTest {
    @Test
    fun explicitCityUsesGeocodingAndDoesNotReadDeviceLocation() = runBlocking {
        val urls = mutableListOf<String>()
        val tool = OpenMeteoWeatherTool(
            transport = KnowledgeHttpTransport { url ->
                urls += url
                if (url.contains("geocoding-api")) json(GEOCODING) else json(FORECAST)
            },
            clock = { 123L },
        )

        val result = tool.fetch(WeatherRequest("Brasília, BR", WeatherPeriod.Today))

        assertTrue(result is WeatherResult.Success)
        val payload = (result as WeatherResult.Success).payload
        assertEquals("Open-Meteo", payload.get("source").asString)
        assertEquals("Brasília, BR", payload.get("location").asString)
        assertEquals(123L, payload.get("retrievedAtMillis").asLong)
        assertEquals(24, payload.getAsJsonArray("hours").size())
        assertEquals(1, payload.getAsJsonArray("days").size())
        assertTrue(urls.first().startsWith("https://geocoding-api.open-meteo.com/"))
        assertTrue(urls.first().contains("name=Bras%C3%ADlia"))
        assertFalse(urls.first().contains("%2C"))
        assertTrue(urls.last().contains("latitude=-15.79"))
    }

    @Test
    fun missingPlaceNeverContactsOpenMeteo() = runBlocking {
        var contacted = false
        val tool = OpenMeteoWeatherTool(
            transport = KnowledgeHttpTransport {
                contacted = true
                json(FORECAST)
            },
        )

        assertEquals(
            WeatherResult.Failure(WeatherFailure.LocationRequired),
            tool.fetch(WeatherRequest(null, WeatherPeriod.Now)),
        )
        assertEquals(
            WeatherResult.Failure(WeatherFailure.LocationRequired),
            tool.fetch(WeatherRequest("  ", WeatherPeriod.Now)),
        )
        assertFalse(contacted)
    }

    @Test
    fun tomorrowReturnsOnlyTomorrowAndRejectsAmbiguousPlace() = runBlocking {
        val tool = OpenMeteoWeatherTool(
            transport = KnowledgeHttpTransport { url ->
                if (url.contains("geocoding-api")) json(GEOCODING) else json(FORECAST)
            },
        )
        val result = tool.fetch(WeatherRequest("Brasília, BR", WeatherPeriod.Tomorrow))
        assertTrue(result is WeatherResult.Success)
        val payload = (result as WeatherResult.Success).payload
        assertEquals("2026-11-08", payload.getAsJsonArray("days")[0].asJsonObject.get("date").asString)
        assertEquals("2026-11-08T00:00", payload.getAsJsonArray("hours")[0].asJsonObject.get("time").asString)
        assertEquals(24, payload.getAsJsonArray("hours").size())

        val ambiguous = OpenMeteoWeatherTool(
            transport = KnowledgeHttpTransport { json(AMBIGUOUS) },
        )
        assertEquals(
            WeatherResult.Failure(WeatherFailure.AmbiguousLocation),
            ambiguous.fetch(WeatherRequest("Springfield", WeatherPeriod.Now)),
        )
    }

    @Test
    fun qualifierMustMatchGeocodedCountry() = runBlocking {
        var forecastCalled = false
        val tool = OpenMeteoWeatherTool(
            transport = KnowledgeHttpTransport { url ->
                if (url.contains("geocoding-api")) {
                    json(GEOCODING)
                } else {
                    forecastCalled = true
                    json(FORECAST)
                }
            },
        )

        assertEquals(
            WeatherResult.Failure(WeatherFailure.InvalidLocation),
            tool.fetch(WeatherRequest("Brasília, CA", WeatherPeriod.Now)),
        )
        assertFalse(forecastCalled)
    }

    @Test
    fun brazilianStateCodeMatchesProviderAdminNameWithoutIgnoringWrongState() = runBlocking {
        var forecastCalls = 0
        val tool = OpenMeteoWeatherTool(
            transport = KnowledgeHttpTransport { url ->
                if (url.contains("geocoding-api")) {
                    json(BRASILIA_GEOCODING)
                } else {
                    forecastCalls++
                    json(FORECAST)
                }
            },
        )

        listOf("Brasília, DF, Brasil", "Brasília, DF, Brazil").forEach { place ->
            val result = tool.fetch(WeatherRequest(place, WeatherPeriod.Now))
            assertTrue(result is WeatherResult.Success)
            assertEquals("Brasília, BR", (result as WeatherResult.Success).payload.get("location").asString)
        }
        assertEquals(
            WeatherResult.Failure(WeatherFailure.InvalidLocation),
            tool.fetch(WeatherRequest("Brasília, SP, Brasil", WeatherPeriod.Now)),
        )
        assertEquals(
            WeatherResult.Failure(WeatherFailure.InvalidLocation),
            tool.fetch(WeatherRequest("Brasília, DF, México", WeatherPeriod.Now)),
        )
        assertEquals(2, forecastCalls)
    }

    @Test
    fun dominantCityResolvesWithoutQualifierDespiteSmallNamesakes() = runBlocking {
        val urls = mutableListOf<String>()
        val tool = OpenMeteoWeatherTool(
            transport = KnowledgeHttpTransport { url ->
                urls += url
                if (url.contains("geocoding-api")) json(MONTREAL) else json(FORECAST)
            },
        )

        val result = tool.fetch(WeatherRequest("Montreal", WeatherPeriod.Now))

        assertTrue(result is WeatherResult.Success)
        assertEquals("Montreal, CA", (result as WeatherResult.Success).payload.get("location").asString)
        assertTrue(urls.last().contains("latitude=45.5"))
    }

    @Test
    fun multipleAccentedQualifiersMustAllMatchTheSameCity() = runBlocking {
        var forecastCalls = 0
        val tool = OpenMeteoWeatherTool(
            transport = KnowledgeHttpTransport { url ->
                if (url.contains("geocoding-api")) {
                    json(MONTREAL)
                } else {
                    forecastCalls++
                    json(FORECAST)
                }
            },
        )

        val valid = tool.fetch(WeatherRequest("Montréal, Québec, Canadá", WeatherPeriod.Now))
        assertTrue(valid is WeatherResult.Success)
        assertEquals("Montreal, CA", (valid as WeatherResult.Success).payload.get("location").asString)
        assertEquals(
            WeatherResult.Failure(WeatherFailure.InvalidLocation),
            tool.fetch(WeatherRequest("Montréal, Québec, França", WeatherPeriod.Now)),
        )
        assertEquals(
            WeatherResult.Failure(WeatherFailure.InvalidLocation),
            tool.fetch(WeatherRequest("Montréal, , Canadá", WeatherPeriod.Now)),
        )
        assertEquals(1, forecastCalls)
    }

    @Test
    fun similarSizedNamesakesRemainAmbiguousWithinOneCountry() = runBlocking {
        val tool = OpenMeteoWeatherTool(
            transport = KnowledgeHttpTransport { json(SPRINGFIELD_US) },
        )

        assertEquals(
            WeatherResult.Failure(WeatherFailure.AmbiguousLocation),
            tool.fetch(WeatherRequest("Springfield, US", WeatherPeriod.Now)),
        )
    }

    @Test
    fun tomorrowHoursUseLocalDateRatherThanAssuming24HourDays() = runBlocking {
        val shiftedForecast = FORECAST.replace(
            "2026-11-08T00:00",
            "2026-11-07T24:00",
        )
        val tool = OpenMeteoWeatherTool(
            transport = KnowledgeHttpTransport { url ->
                if (url.contains("geocoding-api")) json(GEOCODING) else json(shiftedForecast)
            },
        )

        val result = tool.fetch(WeatherRequest("Brasília, BR", WeatherPeriod.Tomorrow))
        assertTrue(result is WeatherResult.Success)
        val hours = (result as WeatherResult.Success).payload.getAsJsonArray("hours")
        assertEquals(23, hours.size())
        assertEquals("2026-11-08T01:00", hours[0].asJsonObject.get("time").asString)
    }

    private fun json(body: String) = KnowledgeHttpResponse(200, "application/json", body.toByteArray())

    private companion object {
        const val GEOCODING =
            """{"results":[{"name":"Brasília","country_code":"BR","latitude":-15.79,"longitude":-47.88}]}"""
        const val BRASILIA_GEOCODING =
            """{"results":[{"name":"Brasília","country_code":"BR","country":"Brazil","admin1":"Federal District","latitude":-15.79,"longitude":-47.88}]}"""
        const val AMBIGUOUS = """{"results":[""" +
            """{"name":"Springfield","country_code":"US","latitude":39.8,"longitude":-89.6},""" +
            """{"name":"Springfield","country_code":"CA","latitude":45.0,"longitude":-63.0}]}"""
        val MONTREAL = """
            {"results":[
              {"name":"Montreal","country_code":"CA","country":"Canada","admin1":"Quebec",
               "population":1762949,"latitude":45.5,"longitude":-73.6},
              {"name":"Montréal","country_code":"FR","country":"France","admin1":"Occitanie",
               "population":1932,"latitude":43.2,"longitude":2.1},
              {"name":"Montreal","country_code":"US","country":"United States","admin1":"Wisconsin",
               "population":783,"latitude":46.4,"longitude":-90.2}
            ]}
        """.trimIndent()
        val SPRINGFIELD_US = """
            {"results":[
              {"name":"Springfield","country_code":"US","admin1":"Missouri",
               "population":170188,"latitude":37.2,"longitude":-93.3},
              {"name":"Springfield","country_code":"US","admin1":"Illinois",
               "population":114394,"latitude":39.8,"longitude":-89.6}
            ]}
        """.trimIndent()
        val FORECAST: String = buildString {
            append("""{"timezone":"America/Sao_Paulo","current":{"time":"2026-11-07T00:00","temperature_2m":25},""")
            append(""""current_units":{"temperature_2m":"°C"},"daily":{"time":["2026-11-07","2026-11-08"],""")
            append(""""temperature_2m_max":[30,31]},"hourly":{"time":[""")
            append(
                (0 until 48).joinToString(",") { index ->
                    val day = if (index < 24) "07" else "08"
                    val hour = (index % 24).toString().padStart(2, '0')
                    "\"2026-11-${day}T$hour:00\""
                },
            )
            append("""],"temperature_2m":[""")
            append((0 until 48).joinToString(",") { "25" })
            append("]}}")
        }
    }
}
