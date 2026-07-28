package com.jesjobom.ararai.benchmark

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallingCharacterizationTest {
    @Test
    fun `runner evaluates deterministic no-call and one-call cases`() = runTest {
        val engine =
            ScriptedCharacterizer { case ->
                if (case.expectedBehavior == ExpectedToolBehavior.NoCall) {
                    observation(answer = "Rain falls softly.")
                } else {
                    observation(
                        answer = "A final synthesized answer. ARARAI_42",
                        arguments = """{"query":"Ada Lovelace","language":"pt"}""",
                    )
                }
            }
        val cases =
            listOf(
                ToolCallingCase("direct", "hello", ExpectedToolBehavior.NoCall),
                ToolCallingCase(
                    "search",
                    "pesquise",
                    ExpectedToolBehavior.OneCall,
                    expectedLanguage = "pt",
                ),
            )

        val report =
            ToolCallingCharacterizationRunner(engine, cases)
                .run("gemma", "sha", repetitions = 2)

        assertTrue(report.passed)
        assertEquals(4, report.results.size)
        assertEquals(4, report.passedCount)
        assertTrue(report.asText().contains("verdict=PASS"))
    }

    @Test
    fun `runner rejects protocol leakage and repeated calls`() = runTest {
        val engine =
            ScriptedCharacterizer {
                ToolCallingObservation(
                    finalAnswer = "```json tool_response",
                    invocations =
                    listOf(
                        ToolInvocation("""{"query":"x","language":"en"}""", 1),
                        ToolInvocation("""{"query":"y","language":"en"}""", 1),
                    ),
                    firstTokenMillis = 1,
                    totalMillis = 2,
                )
            }
        val case =
            ToolCallingCase(
                "single",
                "search",
                ExpectedToolBehavior.OneCall,
                expectedLanguage = "en",
            )

        val report = ToolCallingCharacterizationRunner(engine, listOf(case)).run("gemma", "sha", 1)

        assertFalse(report.passed)
        assertTrue(report.results.single().reason.contains("protocol"))
    }

    @Test
    fun `runner records cooperative cancellation case`() = runTest {
        val engine =
            ScriptedCharacterizer {
                delay(Long.MAX_VALUE)
                observation("unreachable")
            }
        val case =
            ToolCallingCase(
                id = "cancel",
                prompt = "search",
                expectedBehavior = ExpectedToolBehavior.Cancellation,
                cancelAfterMillis = 10,
            )

        val report = ToolCallingCharacterizationRunner(engine, listOf(case)).run("gemma", "sha", 1)

        assertTrue(report.passed)
        assertTrue(report.results.single().observation.cancelled)
    }

    @Test
    fun `runner records start runtime events and callback timeout as failed result`() = runTest {
        val progress = mutableListOf<ToolCallingCaseProgress>()
        val engine =
            object : ToolCallingCharacterizationEngine {
                override suspend fun runToolCallingCase(
                    case: ToolCallingCase,
                    onEvent: (ToolCallingRuntimeEvent) -> Unit,
                ): ToolCallingObservation {
                    onEvent(ToolCallingRuntimeEvent.Message("partial ", 12))
                    onEvent(ToolCallingRuntimeEvent.Message("response", 18))
                    delay(Long.MAX_VALUE)
                    return observation("unreachable")
                }
            }
        val case = ToolCallingCase("stalled", "hello", ExpectedToolBehavior.NoCall)

        val report =
            ToolCallingCharacterizationRunner(engine, listOf(case), caseTimeoutMillis = 50)
                .run("gemma", "sha", 1, progress::add)

        assertFalse(report.passed)
        assertTrue(report.results.single().observation.timedOut)
        assertEquals("partial response", report.results.single().observation.finalAnswer)
        assertEquals(ToolCallingCasePhase.Started, progress.first().phase)
        assertEquals(ToolCallingCasePhase.TimedOut, progress.last().phase)
        assertTrue(progress.any { it.detail?.contains("onMessage") == true })
    }

    @Test
    fun `runner aborts matrix after first timeout`() = runTest {
        val executedCases = mutableListOf<String>()
        val engine =
            object : ToolCallingCharacterizationEngine {
                override suspend fun runToolCallingCase(
                    case: ToolCallingCase,
                    onEvent: (ToolCallingRuntimeEvent) -> Unit,
                ): ToolCallingObservation {
                    executedCases += case.id
                    delay(Long.MAX_VALUE)
                    return observation("unreachable")
                }
            }
        val cases =
            listOf(
                ToolCallingCase("first", "hello", ExpectedToolBehavior.NoCall),
                ToolCallingCase("unsafe-after-timeout", "search", ExpectedToolBehavior.OneCall),
            )

        val report =
            ToolCallingCharacterizationRunner(engine, cases, caseTimeoutMillis = 50)
                .run("gemma", "sha", 1)

        assertEquals(listOf("first"), executedCases)
        assertEquals(1, report.results.size)
        assertTrue(report.results.single().observation.timedOut)
    }

    @Test
    fun `runner reports structured success separately from cleanup timeout`() = runTest {
        val case =
            ToolCallingCase(
                id = "english-search",
                prompt = "search",
                expectedBehavior = ExpectedToolBehavior.OneCall,
                expectedLanguage = "en",
                expectedEvidenceTerms = listOf("British mathematician"),
            )
        val observation =
            observation(
                answer = "Alan Turing was a British mathematician.",
                arguments = """{"query":"Alan Turing","language":"en"}""",
            )
        val resultEngine =
            object : ToolCallingCharacterizationEngine {
                override suspend fun runToolCallingCase(
                    case: ToolCallingCase,
                    onEvent: (ToolCallingRuntimeEvent) -> Unit,
                ): ToolCallingObservation {
                    onEvent(ToolCallingRuntimeEvent.CleanupStarted(10))
                    return observation
                }
            }

        val report =
            ToolCallingCharacterizationRunner(
                resultEngine,
                listOf(case),
                cleanupTimeoutMillis = 50,
            ).run("gemma", "sha", 1)

        val result = report.results.single()
        assertFalse(result.passed)
        assertTrue(result.structuredBehaviorPassed)
        assertFalse(result.cleanupPassed)
        assertTrue(result.reason.contains("cleanup timed out"))
        assertTrue(result.observation.cleanupTimedOut)
    }

    @Test
    fun `runner approves multi-turn reuse with exact aggregate call count and evidence`() = runTest {
        val case =
            ToolCallingCase(
                id = "multi-turn-reuse",
                prompt = "multi turn",
                expectedBehavior = ExpectedToolBehavior.MultiTurnReuse,
                turns =
                listOf(
                    ToolCallingTurn("direct", "haiku", expectedCalls = 0),
                    ToolCallingTurn(
                        "search",
                        "search Turing",
                        expectedCalls = 1,
                        expectedEvidenceTerms = listOf("computer scientist"),
                    ),
                    ToolCallingTurn(
                        "follow-up",
                        "restate",
                        expectedCalls = 0,
                        expectedEvidenceTerms = listOf("mathematician"),
                    ),
                    ToolCallingTurn(
                        "portuguese",
                        "search Ada",
                        expectedCalls = 1,
                        expectedEvidenceTerms = listOf("programação"),
                    ),
                ),
            )
        val engine =
            ScriptedCharacterizer {
                ToolCallingObservation(
                    finalAnswer =
                    "[turn=direct calls=0] Rain.\n" +
                        "[turn=search calls=1] Turing was a computer scientist.\n" +
                        "[turn=follow-up calls=0] He was a mathematician.\n" +
                        "[turn=portuguese calls=1] Ada foi pioneira da programação.",
                    invocations =
                    listOf(
                        ToolInvocation("""{"query":"Alan Turing","language":"en"}""", 1),
                        ToolInvocation("""{"query":"Ada Lovelace","language":"pt"}""", 1),
                    ),
                    firstTokenMillis = 1,
                    totalMillis = 4,
                )
            }

        val report = ToolCallingCharacterizationRunner(engine, listOf(case)).run("gemma", "sha", 1)

        assertTrue(report.passed)
        assertTrue(report.results.single().structuredBehaviorPassed)
    }

    private fun observation(
        answer: String,
        arguments: String? = null,
    ) = ToolCallingObservation(
        finalAnswer = answer,
        invocations = arguments?.let { listOf(ToolInvocation(it, 3)) }.orEmpty(),
        firstTokenMillis = 5,
        totalMillis = 10,
    )

    private fun interface Script {
        suspend fun run(case: ToolCallingCase): ToolCallingObservation
    }

    private class ScriptedCharacterizer(
        private val script: Script,
    ) : ToolCallingCharacterizationEngine {
        override suspend fun runToolCallingCase(
            case: ToolCallingCase,
            onEvent: (ToolCallingRuntimeEvent) -> Unit,
        ): ToolCallingObservation = try {
            script.run(case)
        } finally {
            onEvent(ToolCallingRuntimeEvent.CleanupStarted(10))
            onEvent(ToolCallingRuntimeEvent.CleanupCompleted(11))
        }
    }
}
