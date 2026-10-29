@file:Suppress("MaxLineLength")

package com.jesjobom.ararai.widget.managed

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.knowledge.WikipediaOnThisDayTool
import com.jesjobom.ararai.knowledge.WikipediaPageSummaryTool
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelToolCapabilities
import com.jesjobom.ararai.model.WIDGET_AUTHORING_PIPELINE_V1
import com.jesjobom.ararai.tools.ApplicationToolOperationalState
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.tools.webSearchContentApplicationTool
import com.jesjobom.ararai.tools.webSearchResultsApplicationTool
import com.jesjobom.ararai.tools.wikipediaOnThisDayApplicationTool
import com.jesjobom.ararai.tools.wikipediaPageSummaryApplicationTool
import com.jesjobom.ararai.widget.runtime.WidgetJavaScriptEngine
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import com.jesjobom.ararai.widget.runtime.WidgetScriptResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetDeterministicWebListStageTest {
    @Test
    fun `ranked web list retry completes without another model generation`() = runTest {
        val fixture = Fixture()
        try {
            fixture.prepareAcceptedStages()
            val retryId = fixture.prepareRenderRetry()
            val result = fixture.runner.run(
                fixture.model,
                InferenceConfig(2_048, 512, 0.7f, 0.9f),
                fixture.prompt,
                WidgetRuntimeContext("en-CA", "America/Toronto", "2026-10-27T00:00:00-04:00", 7),
            )
            val attempt = result.attempts.single { it.id == retryId }
            assertEquals(WidgetAuthoringAttemptStatus.Succeeded, attempt.status)
            assertTrue(requireNotNull(attempt.artifact).contains("\"items\":\"results\""))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `best ranked content result retry completes without another model generation`() = runTest {
        val fixture = Fixture(webContent = true)
        try {
            fixture.prepareAcceptedStages()
            val retryId = fixture.prepareRenderRetry()
            val result = fixture.runner.run(
                fixture.model,
                InferenceConfig(2_048, 512, 0.7f, 0.9f),
                fixture.prompt,
                WidgetRuntimeContext("pt-BR", "America/Sao_Paulo", "2026-10-27T00:00:00-03:00", 7),
            )
            val attempt = result.attempts.single { it.id == retryId }
            assertEquals(WidgetAuthoringAttemptStatus.Succeeded, attempt.status)
            assertTrue(requireNotNull(attempt.artifact).contains("\"body\":\"excerpt\""))
            assertTrue(requireNotNull(attempt.artifact).contains("\"selection\":\"first\""))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `event year title retry completes without another model generation`() = runTest {
        val fixture = Fixture(eventYear = true)
        try {
            fixture.prepareAcceptedStages()
            val retryId = fixture.prepareRenderRetry()
            val result = fixture.runner.run(
                fixture.model,
                InferenceConfig(2_048, 512, 0.7f, 0.9f),
                fixture.prompt,
                WidgetRuntimeContext("pt-BR", "America/Sao_Paulo", "2026-10-29T00:00:00-03:00", 7),
            )
            val attempt = result.attempts.single { it.id == retryId }
            assertEquals(WidgetAuthoringAttemptStatus.Succeeded, attempt.status)
            assertTrue(requireNotNull(attempt.artifact).contains("\"titleSuffixField\":\"year\""))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `selected Wikipedia page retry completes without another model generation`() = runTest {
        val fixture = Fixture(wikipediaPage = true)
        try {
            fixture.prepareAcceptedStages()
            val retryId = fixture.prepareRenderRetry()
            val result = fixture.runner.run(
                fixture.model,
                InferenceConfig(2_048, 512, 0.7f, 0.9f),
                fixture.prompt,
                WidgetRuntimeContext("pt-BR", "America/Sao_Paulo", "2026-10-29T00:00:00-03:00", 7),
            )
            val attempt = result.attempts.single { it.id == retryId }
            assertEquals(WidgetAuthoringAttemptStatus.Succeeded, attempt.status)
            assertTrue(requireNotNull(attempt.artifact).contains("\"body\":\"extract\""))
            assertTrue(requireNotNull(attempt.artifact).contains("\"selection\":\"first\""))
        } finally {
            fixture.close()
        }
    }

    private class Fixture(
        private val webContent: Boolean = false,
        private val eventYear: Boolean = false,
        private val wikipediaPage: Boolean = false,
    ) {
        private val singleCard = webContent || eventYear || wikipediaPage
        private val toolId = when {
            wikipediaPage -> "wikipedia_pages"
            eventYear -> "wikipedia_on_this_day"
            webContent -> "web_search_content"
            else -> "web_search_top_results"
        }
        private val context: Context = ApplicationProvider.getApplicationContext()
        private val store: SqliteManagedWidgetRepository
        private val repository: DispatcherWidgetAuthoringWorkflowRepository
        val model = LocalModel(
            id = "test-model",
            name = "Test model",
            filePath = "/model/test.task",
            toolCapabilities = ModelToolCapabilities(authoringProtocolNames = setOf(WIDGET_AUTHORING_PIPELINE_V1)),
        )
        private val registry = ApplicationToolRegistry(
            listOf(
                if (wikipediaPage) {
                    wikipediaPageSummaryApplicationTool(
                        WikipediaPageSummaryTool { error("Tool not called") },
                        state = { ApplicationToolOperationalState(enabled = true, ready = true) },
                    )
                } else if (eventYear) {
                    wikipediaOnThisDayApplicationTool(
                        WikipediaOnThisDayTool { error("Tool not called") },
                        state = { ApplicationToolOperationalState(enabled = true, ready = true) },
                    )
                } else if (webContent) {
                    webSearchContentApplicationTool(
                        tool = { null },
                        state = { ApplicationToolOperationalState(enabled = true, ready = true) },
                    )
                } else {
                    webSearchResultsApplicationTool(
                        tool = { null },
                        state = { ApplicationToolOperationalState(enabled = true, ready = true) },
                    )
                },
            ),
        )
        val prompt = WidgetAuthoringContextBuilder.build(
            when {
                wikipediaPage -> "Mostre uma página aleatória da Wikipedia para o tema Brasil"
                eventYear -> "Mostre um evento aleatório da Wikipedia para o dia de hoje. Adicione ao título o ano do evento."
                webContent -> "Mostre o melhor resultado para a busca sobre smartphone"
                else -> "Show up to 10 web results for stocks"
            },
            registry.descriptors(),
        )
        val runner: WidgetAuthoringStageRunner
        private lateinit var snapshot: WidgetAuthoringSessionSnapshot

        init {
            context.deleteDatabase(MANAGED_WIDGET_DATABASE_NAME)
            store = SqliteManagedWidgetRepository(context, newId = { java.util.UUID.randomUUID().toString() })
            repository = DispatcherWidgetAuthoringWorkflowRepository(store, UnconfinedTestDispatcher())
            val modelEngine = object : LocalLlmEngine {
                override suspend fun load(model: LocalModel, config: InferenceConfig): Unit = error("No model load expected")
                override fun generate(request: PromptRequest): Flow<GenerationEvent> = error("No generation expected")
                override suspend fun unload(): Unit = error("No model unload expected")
            }
            runner = WidgetAuthoringStageRunner(
                repository,
                WidgetAuthoringPipelineModelController(modelEngine),
                recoveryGate = { error("No recovery wait expected") },
                WidgetAuthoringPipelineValidator(registry, fakeJavaScript()),
                registry,
            )
        }

        private fun fakeJavaScript() = WidgetJavaScriptEngine { source, entrypoint, arguments, _ ->
            assertEquals("render", entrypoint)
            assertTrue(source.contains(if (singleCard) "runtime.collectionCard" else "runtime.collectionList"))
            val outcomes = arguments[1]
            val emptyArray = listOf("\"results\":[]", "\"events\":[]", "\"pages\":[]")
                .any(outcomes::contains)
            WidgetScriptResult.Success(
                if (emptyArray || outcomes.contains("\"status\":\"failure\"")) {
                    """{"type":"card","child":{"type":"text","text":"No results found","tone":"neutral"}}"""
                } else if (wikipediaPage) {
                    assertTrue(source.contains("\"body\":\"extract\""))
                    """{"type":"card","child":{"type":"column","children":[{"type":"text","text":"Test","tone":"neutral"},{"type":"text","text":"value","tone":"neutral"},{"type":"https_link","label":"Test","url":"https://en.wikipedia.org/wiki/Test","sourceAlias":"step_1","sourceField":"pages.0.canonicalUrl"}]}}"""
                } else if (eventYear) {
                    assertTrue(source.contains("\"titleSuffixField\":\"year\""))
                    """{"type":"card","child":{"type":"column","children":[{"type":"text","text":"Test (2000)","tone":"neutral"},{"type":"text","text":"value","tone":"neutral"},{"type":"https_link","label":"Test (2000)","url":"https://en.wikipedia.org/wiki/Test","sourceAlias":"step_1","sourceField":"events.0.canonicalUrl"}]}}"""
                } else if (webContent) {
                    """{"type":"card","child":{"type":"column","children":[{"type":"text","text":"Test","tone":"neutral"},{"type":"text","text":"value","tone":"neutral"},{"type":"https_link","label":"Test","url":"https://en.wikipedia.org/wiki/Test","sourceAlias":"step_1","sourceField":"results.0.canonicalUrl"}]}}"""
                } else {
                    checkNotNull(javaClass.getResource("/widget-ranked-list-three.json")).readText()
                },
            )
        }

        suspend fun prepareAcceptedStages() {
            repository.initialize()
            snapshot = repository.create(
                NewWidgetAuthoringSession(
                    instruction = prompt.userInstruction,
                    targetWidgetId = null,
                    modelId = model.id,
                    modelArtifactDigest = "a".repeat(64),
                    inferenceConfigJson = "{}",
                    toolContractDigest = "b".repeat(64),
                ),
            )
            accept(
                WidgetAuthoringStageKey.Feasibility,
                """{"outcome":"achievable","message":"Search","periodicIntervalHours":12,"toolIds":["$toolId"]}""",
                WidgetAuthoringStageKey.Algorithm,
            )
            accept(
                WidgetAuthoringStageKey.Algorithm,
                algorithmJson(),
                WidgetAuthoringStageKey.CallFunction("step_1"),
            )
            accept(
                WidgetAuthoringStageKey.CallFunction("step_1"),
                callSource(),
                WidgetAuthoringStageKey.Plan,
            )
            accept(
                WidgetAuthoringStageKey.Plan,
                planSource(),
                WidgetAuthoringStageKey.Render,
            )
        }

        private fun algorithmJson(): String {
            val selection = if (wikipediaPage) ",\"selectionMode\":\"seeded_random\"" else ""
            val capability = if (singleCard) "collection_card" else "collection_list"
            return """{"steps":[{"id":"step_1","kind":"tool_call","objective":"Search","dependsOn":[],"toolId":"$toolId"$selection}],"presentationObjective":"Show results","renderCapability":"$capability"}"""
        }

        private fun callSource(): String = when {
            wikipediaPage -> """{"source":"function buildCallStep1(runtime) { return {query:'Brasil',language:'pt',selection:'seeded_random',seed:runtime.seed}; }"}"""
            eventYear -> """{"source":"function buildCallStep1(runtime) { return {month:10,day:29,language:'pt'}; }"}"""
            else -> """{"source":"function buildCallStep1(runtime) { return {query:'stocks'${if (webContent) "" else ",limit:10"}}; }"}"""
        }

        private fun planSource(): String = when {
            wikipediaPage -> """{"source":"function plan(runtime) { return [{alias:'step_1',toolId:'$toolId',contractVersion:2,arguments:{query:'Brasil',language:'pt',selection:'seeded_random',seed:runtime.seed}}]; }"}"""
            eventYear -> """{"source":"function plan(runtime) { return [{alias:'step_1',toolId:'$toolId',contractVersion:1,arguments:{month:10,day:29,language:'pt'}}]; }"}"""
            else -> """{"source":"function plan(runtime) { return [{alias:'step_1',toolId:'$toolId',contractVersion:1,arguments:{query:'stocks'${if (webContent) "" else ",limit:10"}}}]; }"}"""
        }

        private suspend fun accept(stage: WidgetAuthoringStageKey, artifact: String, next: WidgetAuthoringStageKey) {
            val started = repository.startAttempt(
                StartWidgetAuthoringAttempt(
                    snapshot.session.id,
                    snapshot.session.revision,
                    "start-${stage.wireValue}",
                    stage,
                    WidgetAuthoringAttemptKind.Initial,
                    authoringUpstreamDigest(snapshot, stage),
                ),
            ) as StartWidgetAuthoringAttemptResult.Started
            repository.markAttemptDeferred(snapshot.session.id, started.attempt.id)
            repository.markAttemptRunning(snapshot.session.id, started.attempt.id)
            val completed = repository.completeAttempt(
                CompleteWidgetAuthoringAttempt(
                    snapshot.session.id,
                    started.attempt.id,
                    WidgetAuthoringAttemptStatus.Succeeded,
                    artifact,
                    null,
                ),
            )
            snapshot = (
                repository.acceptCandidate(
                    AcceptWidgetAuthoringCandidate(
                        snapshot.session.id,
                        completed.session.revision,
                        "accept-${stage.wireValue}",
                        started.attempt.id,
                        next,
                    ),
                ) as WidgetAuthoringMutationResult.Applied
                ).snapshot
        }

        suspend fun prepareRenderRetry(): String {
            val failed = repository.startAttempt(
                StartWidgetAuthoringAttempt(
                    snapshot.session.id,
                    snapshot.session.revision,
                    "start-render",
                    WidgetAuthoringStageKey.Render,
                    WidgetAuthoringAttemptKind.Initial,
                    authoringUpstreamDigest(snapshot, WidgetAuthoringStageKey.Render),
                ),
            ) as StartWidgetAuthoringAttemptResult.Started
            repository.markAttemptDeferred(snapshot.session.id, failed.attempt.id)
            repository.markAttemptRunning(snapshot.session.id, failed.attempt.id)
            snapshot = repository.completeAttempt(
                CompleteWidgetAuthoringAttempt(
                    snapshot.session.id,
                    failed.attempt.id,
                    WidgetAuthoringAttemptStatus.Failed,
                    null,
                    WidgetAuthoringStageFailureCode.MissingArtifact,
                ),
            )
            val retry = repository.startAttempt(
                StartWidgetAuthoringAttempt(
                    snapshot.session.id,
                    snapshot.session.revision,
                    "retry-render",
                    WidgetAuthoringStageKey.Render,
                    WidgetAuthoringAttemptKind.Repair,
                    authoringUpstreamDigest(snapshot, WidgetAuthoringStageKey.Render),
                ),
            ) as StartWidgetAuthoringAttemptResult.Started
            return retry.attempt.id
        }

        fun close() {
            store.close()
            context.deleteDatabase(MANAGED_WIDGET_DATABASE_NAME)
        }
    }
}
