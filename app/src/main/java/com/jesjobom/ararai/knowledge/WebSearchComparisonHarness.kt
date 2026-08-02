@file:Suppress("MaxLineLength")

package com.jesjobom.ararai.knowledge

import com.google.gson.GsonBuilder
import kotlinx.coroutines.CancellationException

data class WebSearchComparisonQuestion(
    val id: String,
    val language: String,
    val category: String,
    val prompt: String,
    val focus: String,
)

data class WebSearchComparisonConfig(
    val modelId: String,
    val modelVersion: String,
    val instructionFingerprint: String,
    val contextTokens: Int,
    val temperature: Float,
    val topP: Float,
    val reasoningEnabled: Boolean,
    val runsPerProvider: Int = 3,
) {
    init {
        require(modelId.isNotBlank() && modelVersion.isNotBlank())
        require(instructionFingerprint.isNotBlank())
        require(contextTokens > 0 && temperature >= 0f && topP in 0f..1f)
        require(runsPerProvider > 0)
    }
}

data class WebSearchComparisonScore(
    val correctness: Int,
    val sourceRelevance: Int,
    val attribution: Int,
    val freshness: Int,
    val uncertainty: Int,
) {
    init {
        require(listOf(correctness, sourceRelevance, attribution, freshness, uncertainty).all { it in 0..2 })
    }
}

data class WebSearchComparisonOutcome(
    val providerOutcome: String,
    val evidenceCharacters: Int,
    val sourceCount: Int,
    val estimatedCostUsd: Double?,
    val providerLatencyMillis: Long,
    val modelLatencyMillis: Long,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val completed: Boolean,
    val citationCount: Int,
    val score: WebSearchComparisonScore?,
)

data class WebSearchComparisonRecord(
    val questionId: String,
    val provider: WebSearchProvider,
    val runIndex: Int,
    val configuration: WebSearchComparisonConfig,
    val outcome: WebSearchComparisonOutcome?,
    val interrupted: Boolean,
)

data class WebSearchComparisonIsolation(
    val provider: WebSearchProvider,
    val wikipediaEnabled: Boolean = false,
    val enabledWebProviders: Set<WebSearchProvider> = setOf(provider),
) {
    init {
        require(!wikipediaEnabled)
        require(enabledWebProviders == setOf(provider))
    }
}

fun interface WebSearchComparisonRunner {
    suspend fun run(
        question: WebSearchComparisonQuestion,
        isolation: WebSearchComparisonIsolation,
        runIndex: Int,
        configuration: WebSearchComparisonConfig,
    ): WebSearchComparisonOutcome
}

class WebSearchComparisonHarness(
    private val resetConversation: suspend () -> Unit,
    private val runner: WebSearchComparisonRunner,
) {
    suspend fun execute(
        questions: List<WebSearchComparisonQuestion>,
        configuration: WebSearchComparisonConfig,
        completedRecords: List<WebSearchComparisonRecord> = emptyList(),
        onRecord: suspend (WebSearchComparisonRecord) -> Unit = {},
    ): List<WebSearchComparisonRecord> = buildList {
        addAll(completedRecords)
        val completedKeys = completedRecords.mapTo(mutableSetOf()) { it.key() }
        questions.forEach { question ->
            repeat(configuration.runsPerProvider) { zeroBasedRun ->
                WebSearchProvider.entries.forEach { provider ->
                    val runIndex = zeroBasedRun + 1
                    val key = ComparisonRecordKey(question.id, provider, runIndex, configuration.modelId)
                    if (key in completedKeys) return@forEach
                    resetConversation()
                    val isolation = WebSearchComparisonIsolation(provider)
                    try {
                        val record = WebSearchComparisonRecord(
                            questionId = question.id,
                            provider = provider,
                            runIndex = runIndex,
                            configuration = configuration,
                            outcome = runner.run(question, isolation, runIndex, configuration),
                            interrupted = false,
                        )
                        add(record)
                        onRecord(record)
                    } catch (_: CancellationException) {
                        val record = WebSearchComparisonRecord(
                            questionId = question.id,
                            provider = provider,
                            runIndex = runIndex,
                            configuration = configuration,
                            outcome = null,
                            interrupted = true,
                        )
                        add(record)
                        onRecord(record)
                        return@buildList
                    }
                }
            }
        }
    }
}

private data class ComparisonRecordKey(
    val questionId: String,
    val provider: WebSearchProvider,
    val runIndex: Int,
    val modelId: String,
)

private fun WebSearchComparisonRecord.key() = ComparisonRecordKey(questionId, provider, runIndex, configuration.modelId)

data class WebSearchProviderAggregate(
    val provider: WebSearchProvider,
    val totalRuns: Int,
    val completedRuns: Int,
    val interruptedRuns: Int,
    val meanEvidenceCharacters: Double,
    val meanSourceCount: Double,
    val meanEstimatedCostUsd: Double,
    val medianProviderLatencyMillis: Long,
)

object WebSearchComparisonReport {
    fun aggregate(records: List<WebSearchComparisonRecord>): List<WebSearchProviderAggregate> = WebSearchProvider.entries.map { provider ->
        val providerRecords = records.filter { it.provider == provider }
        val outcomes = providerRecords.mapNotNull(WebSearchComparisonRecord::outcome)
        WebSearchProviderAggregate(
            provider = provider,
            totalRuns = providerRecords.size,
            completedRuns = outcomes.count(WebSearchComparisonOutcome::completed),
            interruptedRuns = providerRecords.count(WebSearchComparisonRecord::interrupted),
            meanEvidenceCharacters = outcomes.map(WebSearchComparisonOutcome::evidenceCharacters).intAverageOrZero(),
            meanSourceCount = outcomes.map(WebSearchComparisonOutcome::sourceCount).intAverageOrZero(),
            meanEstimatedCostUsd =
            outcomes.mapNotNull(WebSearchComparisonOutcome::estimatedCostUsd).doubleAverageOrZero(),
            medianProviderLatencyMillis = outcomes.map(WebSearchComparisonOutcome::providerLatencyMillis).medianOrZero(),
        )
    }

    fun toRedactedJson(records: List<WebSearchComparisonRecord>): String = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(
        mapOf(
            "records" to records,
            "aggregates" to aggregate(records),
        ),
    )
}

private fun List<Int>.intAverageOrZero(): Double = if (isEmpty()) 0.0 else average()
private fun List<Double>.doubleAverageOrZero(): Double = if (isEmpty()) 0.0 else average()
private fun List<Long>.medianOrZero(): Long {
    if (isEmpty()) return 0L
    val sorted = sorted()
    return if (sorted.size % 2 == 1) {
        sorted[sorted.size / 2]
    } else {
        (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2L
    }
}
