package com.jesjobom.ararai.knowledge

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolSmokeTestTest {
    @Test
    fun `Wikipedia smoke test calls the tool directly with a fixed English query`() = runTest {
        var request: ToolRequest? = null
        val source =
            KnowledgeSource(
                provider = "Wikipedia",
                title = "Alan Turing",
                canonicalUrl = "https://en.wikipedia.org/wiki/Alan_Turing",
                language = "en",
                retrievedAtMillis = 1L,
            )
        val smokeTest =
            WikipediaSmokeTest { received ->
                request = received
                ToolResult.Success("reference", listOf(source))
            }

        val result = smokeTest.run()

        assertEquals(ToolRequest("Alan Turing", "en"), request)
        assertTrue(result.passed)
        assertEquals(listOf(source), result.sources)
    }

    @Test
    fun `Wikipedia smoke test reports controlled failures`() = runTest {
        val result =
            WikipediaSmokeTest {
                ToolResult.Failure(ToolFailureReason.Unavailable)
            }.run()

        assertFalse(result.passed)
        assertTrue(result.detail.contains("Unavailable"))
    }
}
