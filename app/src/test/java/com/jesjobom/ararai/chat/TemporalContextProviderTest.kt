package com.jesjobom.ararai.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class TemporalContextProviderTest {
    @Test
    fun `uses the device zone to derive local date and offset`() {
        val provider =
            TemporalContextProvider(
                clock = Clock.fixed(Instant.parse("2026-07-30T02:30:00Z"), ZoneId.of("UTC")),
                zoneIdProvider = { ZoneId.of("America/Toronto") },
            )

        assertEquals(
            TemporalContext(
                localDate = "2026-07-29",
                timeZoneId = "America/Toronto",
                utcOffset = "-04:00",
            ),
            provider.current(),
        )
    }

    @Test
    fun `formats UTC explicitly`() {
        val context =
            TemporalContextProvider(
                clock = Clock.fixed(Instant.parse("2026-07-29T23:00:00Z"), ZoneId.of("UTC")),
                zoneIdProvider = { ZoneId.of("UTC") },
            ).current()

        assertTrue(context.toSystemInstruction().contains("Current date: 2026-07-29"))
        assertTrue(context.toSystemInstruction().contains("Timezone: UTC (UTC+00:00)"))
    }
}
