package com.jesjobom.ararai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class ChatMessageTimestampTest {
    @Test
    fun `formats persisted message instant with international date and local time zone`() {
        val instant = Instant.parse("2026-08-23T20:15:00Z").toEpochMilli()

        assertEquals(
            "2026-08-23 16:15",
            formatMessageTimestamp(
                createdAtMillis = instant,
                timeZone = TimeZone.getTimeZone("America/Toronto"),
            ),
        )
    }

    @Test
    fun `omits timestamp only for synthetic messages without a persisted instant`() {
        assertNull(formatMessageTimestamp(0L, TimeZone.getTimeZone("UTC")))
        assertEquals("ArarAI", formatMessageHeader("ArarAI", 0L, TimeZone.getTimeZone("UTC")))
    }
}
