package com.jesjobom.ararai.knowledge

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FallbackKnowledgeToolTest {
    @Test
    fun `returns primary success without calling fallback`() = runTest {
        val expected = success("Primary")
        val primary = RecordingTool("Primary", expected)
        val fallback = RecordingTool("Fallback", success("Fallback"))

        val result = FallbackKnowledgeTool(listOf(primary, fallback)).execute(request())

        assertSame(expected, result)
        assertEquals(1, primary.calls)
        assertEquals(0, fallback.calls)
    }

    @Test
    fun `calls fallback after provider failure`() = runTest {
        val expected = success("Fallback")
        val primary = RecordingTool("Primary", ToolResult.Failure(ToolFailureReason.RateLimited))
        val fallback = RecordingTool("Fallback", expected)

        val result = FallbackKnowledgeTool(listOf(primary, fallback)).execute(request())

        assertSame(expected, result)
        assertEquals(1, primary.calls)
        assertEquals(1, fallback.calls)
    }

    @Test
    fun `does not call fallback after cancellation or invalid arguments`() = runTest {
        listOf(ToolFailureReason.Cancelled, ToolFailureReason.InvalidArguments).forEach { reason ->
            val primary = RecordingTool("Primary", ToolResult.Failure(reason))
            val fallback = RecordingTool("Fallback", success("Fallback"))

            val result = FallbackKnowledgeTool(listOf(primary, fallback)).execute(request())

            assertEquals(ToolResult.Failure(reason), result)
            assertEquals(0, fallback.calls)
        }
    }

    private fun request() = ToolRequest(query = "query", focus = "focus")

    private fun success(provider: String) = ToolResult.Success(
        untrustedContext = provider,
        sources =
        listOf(
            KnowledgeSource(
                provider = provider,
                title = "Title",
                canonicalUrl = "https://example.com",
                language = "en",
                retrievedAtMillis = 1L,
            ),
        ),
    )

    private class RecordingTool(
        override val displayName: String,
        private val result: ToolResult,
    ) : KnowledgeTool {
        var calls = 0

        override suspend fun execute(request: ToolRequest): ToolResult {
            calls += 1
            return result
        }
    }
}
