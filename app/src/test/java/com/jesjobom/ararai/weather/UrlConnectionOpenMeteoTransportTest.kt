package com.jesjobom.ararai.weather

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class UrlConnectionOpenMeteoTransportTest {
    @Test
    fun allowsOnlyProviderHttpsEndpoints() {
        assertTrue(isAllowedOpenMeteoEndpoint(URI("https://api.open-meteo.com/v1/forecast?latitude=0")))
        assertTrue(isAllowedOpenMeteoEndpoint(URI("https://geocoding-api.open-meteo.com/v1/search?name=Brasilia")))
        listOf(
            "http://api.open-meteo.com/v1/forecast?latitude=0",
            "https://api.open-meteo.com.evil.test/v1/forecast?latitude=0",
            "https://api.open-meteo.com/v1/other?latitude=0",
            "https://api.open-meteo.com:443/v1/forecast?latitude=0",
            "https://api.open-meteo.com/v1/forecast",
        ).forEach { assertFalse(isAllowedOpenMeteoEndpoint(URI(it))) }
    }
}
