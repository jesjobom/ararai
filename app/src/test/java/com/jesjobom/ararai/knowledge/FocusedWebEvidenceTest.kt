package com.jesjobom.ararai.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusedWebEvidenceTest {
    private val normalizer = FocusedWebEvidenceNormalizer(clock = { 42L })

    @Test
    fun `normalizes focused evidence under every shared budget`() {
        val result =
            normalizer.normalize(
                provider = WebSearchProvider.Tavily,
                language = "pt",
                candidates =
                (1..5).map { index ->
                    FocusedEvidenceCandidate(
                        title = " Source   $index ",
                        canonicalUrl = "https://example.com/$index",
                        excerpts =
                        listOf(
                            "A".repeat(700),
                            "B".repeat(700),
                            "ignored third excerpt",
                        ),
                    )
                },
            ) as ToolResult.Success

        assertTrue(result.untrustedContext.length <= FocusedWebEvidenceNormalizer.MAX_CONTEXT_LENGTH)
        assertTrue(result.untrustedContext.startsWith("UNTRUSTED EXTERNAL REFERENCE DATA"))
        assertTrue(result.sources.size <= FocusedWebEvidenceNormalizer.MAX_SOURCES)
        assertEquals(42L, result.sources.first().retrievedAtMillis)
        assertEquals("Tavily Web Search", result.sources.first().provider)
    }

    @Test
    fun `rejects unsafe sources and deduplicates urls and excerpts`() {
        val result =
            normalizer.normalize(
                provider = WebSearchProvider.Exa,
                language = "en",
                candidates =
                listOf(
                    FocusedEvidenceCandidate("Unsafe", "http://example.com", listOf("ignored")),
                    FocusedEvidenceCandidate("First", "https://example.com/a", listOf("same", "same")),
                    FocusedEvidenceCandidate("Duplicate", "https://example.com/a", listOf("other")),
                ),
            ) as ToolResult.Success

        assertEquals(1, result.sources.size)
        assertEquals(1, Regex("\\nsame").findAll(result.untrustedContext).count())
        assertFalse(result.untrustedContext.contains("ignored"))
        assertFalse(result.untrustedContext.contains("other"))
    }

    @Test
    fun `does not split a surrogate pair at an excerpt boundary`() {
        val prefix = "a".repeat(FocusedWebEvidenceNormalizer.MAX_EXCERPT_LENGTH - 1)
        val result =
            normalizer.normalize(
                provider = WebSearchProvider.Exa,
                language = "en",
                candidates =
                listOf(
                    FocusedEvidenceCandidate(
                        "Unicode",
                        "https://example.com/unicode",
                        listOf(prefix + "\uD83D\uDE80" + "tail"),
                    ),
                ),
            ) as ToolResult.Success

        assertFalse(result.untrustedContext.contains('\uD83D').xor(result.untrustedContext.contains('\uDE80')))
    }

    @Test
    fun `returns no results when every source is invalid`() {
        assertEquals(
            ToolResult.Failure(ToolFailureReason.NoResults),
            normalizer.normalize(
                WebSearchProvider.Tavily,
                "en",
                listOf(FocusedEvidenceCandidate("", "file:///tmp/a", listOf("text"))),
            ),
        )
    }
}
