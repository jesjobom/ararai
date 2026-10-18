package com.jesjobom.ararai.engine

import kotlinx.coroutines.channels.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedCallbackDeliveryTest {
    @Test
    fun `full callback queue fails once without pretending later events were delivered`() {
        val channel = Channel<String>(capacity = 1)
        var overflows = 0
        val delivery = BoundedCallbackDelivery(channel) { overflows++ }

        assertTrue(delivery.emit("first"))
        assertFalse(delivery.emit("second"))
        assertFalse(delivery.emit("third"))

        assertTrue(delivery.overflowed)
        assertFalse(delivery.complete())
        assertEquals(1, overflows)
        assertEquals("first", channel.tryReceive().getOrNull())
        assertTrue(channel.tryReceive().isFailure)
    }

    @Test
    fun `closed queue is caller cancellation and not overflow`() {
        val channel = Channel<String>(capacity = 1)
        var overflows = 0
        val delivery = BoundedCallbackDelivery(channel) { overflows++ }
        channel.cancel()

        assertFalse(delivery.emit("late"))
        assertFalse(delivery.overflowed)
        assertFalse(delivery.complete())
        assertEquals(0, overflows)
    }
}
