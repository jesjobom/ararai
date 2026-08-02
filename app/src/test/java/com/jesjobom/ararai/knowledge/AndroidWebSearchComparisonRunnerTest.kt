package com.jesjobom.ararai.knowledge

import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class AndroidWebSearchComparisonRunnerTest {
    @Test
    fun `runs isolated provider with frozen configuration and keeps answer only in private review`() = runBlocking {
        val reviews = mutableListOf<WebSearchComparisonReviewEntry>()
        val engine = RecordingEngine()
        val runner =
            AndroidWebSearchComparisonRunner(
                availableModels = { mapOf("e2b" to LocalModel("e2b", "E2B", "/model")) },
                engineFactory =
                WebSearchComparisonEngineFactory { _, telemetry ->
                    telemetry.set(WebSearchProviderTelemetry("success", 600, 2, 120L))
                    engine
                },
                reviewSink = reviews::add,
            )
        val configuration = WebSearchComparisonConfig("e2b", "1", "prompt", 6_144, 0.2f, 0.9f, false, 2)

        val outcome = runner.run(
            WebSearchComparisonQuestion("q", "en", "current", "What changed?", "Current evidence"),
            WebSearchComparisonIsolation(WebSearchProvider.Exa),
            1,
            configuration,
        )

        assertTrue(outcome.completed)
        assertEquals(600, outcome.evidenceCharacters)
        assertEquals(2, outcome.sourceCount)
        assertEquals(6_144, engine.loadedConfig?.contextTokens)
        assertEquals(setOf("web_search"), engine.request?.advertisedToolNames)
        assertEquals("Answer https://example.com", reviews.single().answer)
    }

    @Test
    fun `stores resumable metadata separately from private review answer`() {
        val directory = Files.createTempDirectory("web-comparison").toFile()
        val store = WebSearchComparisonCheckpointStore(directory)
        val record =
            WebSearchComparisonRecord(
                "q",
                WebSearchProvider.Tavily,
                1,
                WebSearchComparisonConfig("e2b", "1", "prompt", 6_144, 0.2f, 0.9f, false, 2),
                WebSearchComparisonOutcome("success", 100, 1, null, 10, 20, null, null, true, 1, null),
                false,
            )
        store.write(listOf(record))
        store.writeReview(WebSearchComparisonReviewEntry("q", WebSearchProvider.Tavily, 1, "e2b", "secret answer"))

        assertEquals(listOf(record), store.read())
        assertFalse(store.report.readText().contains("secret answer"))
        assertTrue(store.review.readText().contains("secret answer"))
    }
}

private class RecordingEngine : LocalLlmEngine {
    var loadedConfig: InferenceConfig? = null
    var request: PromptRequest? = null

    override suspend fun load(model: LocalModel, config: InferenceConfig) {
        loadedConfig = config
    }

    override fun generate(request: PromptRequest): Flow<GenerationEvent> {
        this.request = request
        return flowOf(GenerationEvent.Token("Answer https://example.com"), GenerationEvent.Completed)
    }

    override suspend fun unload() = Unit
}
