package com.jesjobom.ararai.knowledge

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchComparisonHarnessTest {
    private val question =
        WebSearchComparisonQuestion("q1", "en", "current", "Current fact?", "Current evidence")
    private val config =
        WebSearchComparisonConfig("e2b", "1", "sha256:prompt", 2_048, 0.7f, 0.95f, true, 2)

    @Test
    fun `pairs every provider and run with a fresh conversation and fixed configuration`() = runBlocking {
        var resets = 0
        val observed = mutableListOf<Triple<WebSearchProvider, Int, WebSearchComparisonConfig>>()
        val harness = WebSearchComparisonHarness(
            resetConversation = { resets++ },
            runner = WebSearchComparisonRunner { _, isolation, run, configuration ->
                assertFalse(isolation.wikipediaEnabled)
                assertEquals(setOf(isolation.provider), isolation.enabledWebProviders)
                observed += Triple(isolation.provider, run, configuration)
                outcome(providerLatencyMillis = run * 100L)
            },
        )

        val records = harness.execute(listOf(question), config)

        assertEquals(4, resets)
        assertEquals(4, records.size)
        assertEquals(WebSearchProvider.entries.toSet(), records.map { it.provider }.toSet())
        assertEquals(setOf(1, 2), records.map { it.runIndex }.toSet())
        assertTrue(observed.all { it.third == config })
    }

    @Test
    fun `aggregates paired outcomes and emits only bounded redacted fields`() = runBlocking {
        val secret = "tvly-secret-that-must-not-appear"
        val records = WebSearchComparisonHarness(
            resetConversation = {},
            runner = WebSearchComparisonRunner { _, isolation, run, _ ->
                val latencyMultiplier = if (isolation.provider == WebSearchProvider.Tavily) 100L else 200L
                outcome(
                    providerLatencyMillis = run * latencyMultiplier,
                    providerOutcome = "success",
                )
            },
        ).execute(listOf(question), config)

        val report = WebSearchComparisonReport.toRedactedJson(records)
        val aggregates = WebSearchComparisonReport.aggregate(records)

        assertFalse(report.contains(secret))
        assertFalse(report.contains("answer"))
        assertFalse(report.contains("evidenceText"))
        assertEquals(150L, aggregates.first { it.provider == WebSearchProvider.Tavily }.medianProviderLatencyMillis)
        assertEquals(2, aggregates.first().completedRuns)
    }

    @Test
    fun `rejects invalid configuration and records interruption reproducibly`() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) { config.copy(runsPerProvider = 0) }
        val harness = WebSearchComparisonHarness(
            resetConversation = {},
            runner = WebSearchComparisonRunner { _, _, _, _ -> throw CancellationException("stop") },
        )

        val records = harness.execute(listOf(question), config)

        assertEquals(1, records.size)
        assertTrue(records.single().interrupted)
        assertEquals(null, records.single().outcome)
    }

    @Test
    fun `resumes without repeating checkpointed tuples and checkpoints each new record`() = runBlocking {
        val completed =
            WebSearchComparisonRecord(
                questionId = question.id,
                provider = WebSearchProvider.Tavily,
                runIndex = 1,
                configuration = config,
                outcome = outcome(100L),
                interrupted = false,
            )
        val executed = mutableListOf<Pair<WebSearchProvider, Int>>()
        val checkpointed = mutableListOf<WebSearchComparisonRecord>()
        var resets = 0
        val harness =
            WebSearchComparisonHarness(
                resetConversation = { resets++ },
                runner = WebSearchComparisonRunner { _, isolation, run, _ ->
                    executed += isolation.provider to run
                    outcome(200L)
                },
            )

        val records =
            harness.execute(
                questions = listOf(question),
                configuration = config,
                completedRecords = listOf(completed),
                onRecord = checkpointed::add,
            )

        assertEquals(4, records.size)
        assertEquals(3, resets)
        assertEquals(3, checkpointed.size)
        assertFalse(WebSearchProvider.Tavily to 1 in executed)
    }

    private fun outcome(
        providerLatencyMillis: Long,
        providerOutcome: String = "success",
    ) = WebSearchComparisonOutcome(
        providerOutcome = providerOutcome,
        evidenceCharacters = 500,
        sourceCount = 2,
        estimatedCostUsd = 0.001,
        providerLatencyMillis = providerLatencyMillis,
        modelLatencyMillis = 1_000,
        inputTokens = 100,
        outputTokens = 50,
        completed = true,
        citationCount = 2,
        score = WebSearchComparisonScore(2, 2, 2, 2, 2),
    )
}
