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
        assertEquals(result.sources, result.webEvidence.map { it.source })
        assertTrue(result.webEvidence.all { it.excerpts.isNotEmpty() && it.excerpts.size <= 2 })
        assertTrue(result.webEvidence.flatMap { it.excerpts }.all(result.untrustedContext::contains))
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

    @Test
    fun `Chat budgets the complete escaped model response and scales beyond widget limits`() {
        val candidates = listOf(
            FocusedEvidenceCandidate(
                "Quoted source",
                "https://example.com/quoted",
                listOf("""A "quoted" \ path """ + "evidence ".repeat(800)),
            ),
        )
        val small = normalizer.normalize(WebSearchProvider.Exa, "en", candidates, WebEvidencePolicy.Chat(2_048))
            as ToolResult.Success
        val large = normalizer.normalize(WebSearchProvider.Exa, "en", candidates, WebEvidencePolicy.Chat(8_192))
            as ToolResult.Success
        val widget = normalizer.normalize(WebSearchProvider.Exa, "en", candidates) as ToolResult.Success

        assertTrue(WebEvidencePolicy.modelSuccessJson(small.untrustedContext).length <= 2_048)
        assertTrue(WebEvidencePolicy.modelSuccessJson(large.untrustedContext).length <= 8_192)
        assertTrue(large.untrustedContext.length > 1_800)
        assertTrue(large.webEvidence.single().excerpts.single().length > 500)
        assertTrue(
            small.webEvidence.single().excerpts.single().length < large.webEvidence.single().excerpts.single().length,
        )
        assertTrue(widget.untrustedContext.length <= 1_800)
        assertTrue(widget.webEvidence.single().excerpts.single().length <= 500)
        assertEquals(small.sources, small.webEvidence.map { it.source })
        assertEquals(large.sources, large.webEvidence.map { it.source })
    }

    @Test
    fun `Chat omits source metadata when no meaningful excerpt fits and never splits emoji`() {
        val oversizedUrl = "https://example.com/" + "a".repeat(180)
        assertEquals(
            ToolResult.Failure(ToolFailureReason.NoResults),
            normalizer.normalize(
                WebSearchProvider.Exa,
                "en",
                listOf(FocusedEvidenceCandidate("Long", oversizedUrl, listOf("evidence"))),
                WebEvidencePolicy.Chat(128),
            ),
        )
        val emoji = "\uD83D\uDE80".repeat(2_000)
        val result = normalizer.normalize(
            WebSearchProvider.Exa,
            "en",
            listOf(FocusedEvidenceCandidate("Emoji", "https://example.com/emoji", listOf(emoji))),
            WebEvidencePolicy.Chat(2_048),
        ) as ToolResult.Success
        val excerpt = result.webEvidence.single().excerpts.single()
        assertFalse(excerpt.last().isHighSurrogate())
        assertTrue(WebEvidencePolicy.modelSuccessJson(result.untrustedContext).length <= 2_048)
    }
}
