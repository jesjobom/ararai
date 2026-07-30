package com.jesjobom.ararai.chat

import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class TemporalContext(
    val localDate: String,
    val timeZoneId: String,
    val utcOffset: String,
) {
    fun toSystemInstruction(): String =
        """
        Runtime temporal context:
        Current date: $localDate
        Timezone: $timeZoneId (UTC$utcOffset)
        Use this context for relative dates and date comparisons. Prefer absolute dates when clarifying time-sensitive answers.
        """.trimIndent()
}

class TemporalContextProvider(
    private val clock: Clock = Clock.systemUTC(),
    private val zoneIdProvider: () -> ZoneId = ZoneId::systemDefault,
) {
    fun current(): TemporalContext {
        val zonedNow = clock.instant().atZone(zoneIdProvider())
        return TemporalContext(
            localDate = DateTimeFormatter.ISO_LOCAL_DATE.format(zonedNow),
            timeZoneId = zonedNow.zone.id,
            utcOffset = zonedNow.offset.id.takeUnless { it == "Z" } ?: "+00:00",
        )
    }
}

val SystemTemporalContextProvider = TemporalContextProvider()
