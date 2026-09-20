package com.jesjobom.ararai.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.widget.managed.ConfirmedWidgetRevision
import com.jesjobom.ararai.widget.managed.DispatcherManagedWidgetRepository
import com.jesjobom.ararai.widget.managed.MANAGED_WIDGET_DATABASE_NAME
import com.jesjobom.ararai.widget.managed.ManagedWidgetApplicationServices
import com.jesjobom.ararai.widget.managed.ManagedWidgetDefinition
import com.jesjobom.ararai.widget.managed.ManagedWidgetExecutionCoordinator
import com.jesjobom.ararai.widget.managed.ManagedWidgetManualRefresh
import com.jesjobom.ararai.widget.managed.ManagedWidgetProgramExecutor
import com.jesjobom.ararai.widget.managed.ManagedWidgetProgramResult
import com.jesjobom.ararai.widget.managed.ManagedWidgetScheduleController
import com.jesjobom.ararai.widget.managed.ManagedWidgetScheduler
import com.jesjobom.ararai.widget.managed.SqliteManagedWidgetRepository
import com.jesjobom.ararai.widget.managed.WidgetPresentationCache
import com.jesjobom.ararai.widget.managed.WidgetProgramRevision
import com.jesjobom.ararai.widget.runtime.WidgetExecutionResult
import com.jesjobom.ararai.widget.runtime.WidgetJavaScriptEngine
import com.jesjobom.ararai.widget.runtime.WidgetPresentationNode
import com.jesjobom.ararai.widget.runtime.WidgetProgramParserTest
import com.jesjobom.ararai.widget.runtime.WidgetRequestedLimits
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeFailureCode
import com.jesjobom.ararai.widget.runtime.WidgetScriptResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ManagedWidgetsControllerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: SqliteManagedWidgetRepository
    private lateinit var repository: DispatcherManagedWidgetRepository
    private lateinit var services: ManagedWidgetApplicationServices
    private lateinit var controller: ManagedWidgetsController
    private val scheduler = RecordingScheduler()

    @Before
    fun setUp() {
        context.deleteDatabase(MANAGED_WIDGET_DATABASE_NAME)
        var nextId = 0
        store = SqliteManagedWidgetRepository(context, nowMillis = { 1_000 }, newId = { "widget-${++nextId}" })
        repository = DispatcherManagedWidgetRepository(store, UnconfinedTestDispatcher())
        val scheduleController = ManagedWidgetScheduleController(repository, scheduler)
        val coordinator = ManagedWidgetExecutionCoordinator(
            repository = repository,
            executor = ManagedWidgetProgramExecutor { _, _, _, _ ->
                ManagedWidgetProgramResult(
                    WidgetExecutionResult.Success(
                        WidgetPresentationNode.Text("fresh", "neutral"),
                        emptyList(),
                        emptyList(),
                    ),
                )
            },
            contextProvider = { WidgetRuntimeContext("en", "UTC", "2026-09-07T00:00:00Z", 1) },
            nowMillis = { 2_000 },
        )
        services = ManagedWidgetApplicationServices(
            repository = repository,
            schedules = scheduleController,
            manualRefresh = ManagedWidgetManualRefresh(coordinator),
            toolRegistry = ApplicationToolRegistry(emptyList()),
        )
        controller = ManagedWidgetsController(
            services = services,
            localLlmEngine = NoOpLocalLlmEngine,
            widgetJavaScriptEngine = NoOpWidgetJavaScriptEngine,
        )
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(MANAGED_WIDGET_DATABASE_NAME)
    }

    @Test
    fun `loads only the requested widget and decodes its validated cache`() = runTest {
        val first = repository.createConfirmed(candidate("First"))
        val second = repository.createConfirmed(candidate("Second"))
        repository.replacePresentation(
            WidgetPresentationCache(
                widgetId = second.id,
                revision = 1,
                presentationJson = """{"type":"text","text":"Second result","tone":"neutral"}""",
                completedAtMillis = 1_500,
            ),
        )

        val detail = controller.loadDetail(second.id)

        assertEquals(second.id, detail?.definition?.id)
        assertEquals(WidgetPresentationNode.Text("Second result", "neutral"), detail?.presentation)
        assertEquals(first.id, controller.loadList().first().definition.id)
        assertNull(controller.loadDetail("deleted-widget"))
    }

    @Test
    fun `duplicates an immutable program as a distinct disabled widget`() = runTest {
        val original = repository.createConfirmed(candidate("History", enabled = true))

        val duplicate = controller.duplicate(original.id)!!
        val duplicateRevision = repository.activeRevision(duplicate.id)!!

        assertNotEquals(original.id, duplicate.id)
        assertEquals("History copy", duplicate.displayName)
        assertFalse(duplicate.enabled)
        assertEquals(WidgetProgramParserTest.SOURCE, duplicateRevision.source)
        assertNotEquals(original.consentDigest, duplicate.consentDigest)
        assertTrue(scheduler.cancelled.contains(duplicate.id))
    }

    @Test
    fun `physical diagnostic request uses natural widget behavior language`() {
        assertEquals(
            "Mostre um evento aleatório da Wikipédia para o dia e o mês de hoje. " +
                "Atualize o evento a cada hora.",
            TOOL_CALLING_DIAGNOSTIC_PROMPT,
        )
        assertFalse(TOOL_CALLING_DIAGNOSTIC_PROMPT.contains("widget"))
        assertFalse(TOOL_CALLING_DIAGNOSTIC_PROMPT.contains("wikipedia_on_this_day"))
        assertFalse(TOOL_CALLING_DIAGNOSTIC_PROMPT.contains("runtime."))
    }

    @Test
    fun `explicit feasibility control prompt fixes implementation without requiring widget wording`() {
        assertFalse(TOOL_CALLING_DIAGNOSTIC_EXPLICIT_PROMPT.contains("widget"))
        assertTrue(TOOL_CALLING_DIAGNOSTIC_EXPLICIT_PROMPT.contains("wikipedia_on_this_day@1"))
        assertTrue(TOOL_CALLING_DIAGNOSTIC_EXPLICIT_PROMPT.contains("runtime.currentLocalDateTime()"))
        assertTrue(TOOL_CALLING_DIAGNOSTIC_EXPLICIT_PROMPT.contains("runtime.seededIndex"))
        assertTrue(TOOL_CALLING_DIAGNOSTIC_EXPLICIT_PROMPT.contains("a cada hora"))
    }

    private fun candidate(name: String, enabled: Boolean = false) = ConfirmedWidgetRevision(
        displayName = name,
        enabled = enabled,
        periodicIntervalHours = 24,
        manifestJson = WidgetProgramParserTest.validManifest(),
        source = WidgetProgramParserTest.SOURCE,
        programDigest = "a".repeat(64),
        consentDigest = "b".repeat(64),
    )

    private class RecordingScheduler : ManagedWidgetScheduler {
        val cancelled = mutableListOf<String>()

        override fun reconcile(
            definition: ManagedWidgetDefinition,
            revision: WidgetProgramRevision,
        ) {
            if (!definition.enabled) cancelled += definition.id
        }

        override fun cancel(widgetId: String) {
            cancelled += widgetId
        }

        override fun cancelUnknown(knownWidgetIds: Set<String>) = Unit
    }

    private data object NoOpWidgetJavaScriptEngine : WidgetJavaScriptEngine {
        override suspend fun call(
            source: String,
            entrypoint: String,
            argumentsJson: List<String>,
            limits: WidgetRequestedLimits,
        ): WidgetScriptResult = WidgetScriptResult.Failure(WidgetRuntimeFailureCode.RuntimeUnavailable)
    }

    private data object NoOpLocalLlmEngine : LocalLlmEngine {
        override suspend fun load(model: LocalModel, config: InferenceConfig) = Unit
        override fun generate(request: PromptRequest): Flow<GenerationEvent> = emptyFlow()
        override suspend fun unload() = Unit
    }
}
