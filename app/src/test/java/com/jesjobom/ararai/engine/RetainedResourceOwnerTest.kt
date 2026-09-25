package com.jesjobom.ararai.engine

import org.junit.Assert.assertTrue
import org.junit.Test

class RetainedResourceOwnerTest {
    @Test
    fun `disposed resources are tracked by identity rather than value equality`() {
        val cancelled = ValueEqualRecordingResource("same")
        val replacement = ValueEqualRecordingResource("same")
        val owner =
            RetainedResourceOwner<ValueEqualRecordingResource, String>(
                cancelResource = { it.cancelCalls += 1 },
                closeResource = { it.closeCalls += 1 },
            )
        owner.activate(cancelled)
        owner.cancelActive()

        assertTrue(cancelled == replacement)
        assertTrue(owner.activate(replacement))
        assertTrue(owner.retain(replacement, "session-2"))
        assertTrue(owner.retained()?.resource === replacement)
    }

    private class ValueEqualRecordingResource(
        private val value: String,
    ) {
        var cancelCalls: Int = 0
        var closeCalls: Int = 0

        override fun equals(other: Any?): Boolean = other is ValueEqualRecordingResource && value == other.value

        override fun hashCode(): Int = value.hashCode()
    }
}
