package com.jesjobom.ararai.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GenerationFailureTest {
    @Test
    fun `classifies tool call parser failures and preserves their cause`() {
        val technicalMessage = "INVALID_ARGUMENT: Failed to parse tool calls from code block"
        val cause = IllegalArgumentException(technicalMessage)

        val failure = cause.toGenerationFailure()

        assertEquals(technicalMessage, failure.message)
        assertEquals(GenerationFailureKind.ToolCallParsing, failure.kind)
        assertSame(cause, failure.cause)
    }

    @Test
    fun `classifies other runtime failures as unexpected`() {
        val cause = IllegalStateException("boom")

        val failure = cause.toGenerationFailure()

        assertEquals("boom", failure.message)
        assertEquals(GenerationFailureKind.Unexpected, failure.kind)
        assertSame(cause, failure.cause)
    }
}
