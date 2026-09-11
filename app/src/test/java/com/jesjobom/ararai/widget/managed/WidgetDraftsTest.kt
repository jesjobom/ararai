package com.jesjobom.ararai.widget.managed

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.jesjobom.ararai.tools.ApplicationTool
import com.jesjobom.ararai.tools.ApplicationToolCategory
import com.jesjobom.ararai.tools.ApplicationToolConsumer
import com.jesjobom.ararai.tools.ApplicationToolContract
import com.jesjobom.ararai.tools.ApplicationToolOperationalState
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.tools.applicationToolBinding
import com.jesjobom.ararai.widget.runtime.PlannedWidgetToolCall
import com.jesjobom.ararai.widget.runtime.WidgetExecutionGrant
import com.jesjobom.ararai.widget.runtime.WidgetJavaScriptEngine
import com.jesjobom.ararai.widget.runtime.WidgetPresentationCapability
import com.jesjobom.ararai.widget.runtime.WidgetProgram
import com.jesjobom.ararai.widget.runtime.WidgetProgramParserTest
import com.jesjobom.ararai.widget.runtime.WidgetRequestedLimits
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeFailureCode
import com.jesjobom.ararai.widget.runtime.WidgetRuntimePolicy
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeValue
import com.jesjobom.ararai.widget.runtime.WidgetScriptResult
import com.jesjobom.ararai.widget.runtime.WidgetToolCapability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class WidgetDraftsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val stores = mutableListOf<SqliteManagedWidgetRepository>()
    private var providerExecutions = 0

    @Before
    fun setUp() {
        context.deleteDatabase(MANAGED_WIDGET_DATABASE_NAME)
        providerExecutions = 0
    }

    @After
    fun tearDown() {
        stores.forEach(SqliteManagedWidgetRepository::close)
        context.deleteDatabase(MANAGED_WIDGET_DATABASE_NAME)
    }

    @Test
    fun `draft builder replaces control fields validates plan and never executes a provider`() = runTest {
        val builder = WidgetDraftBuilder(registry(), planner(validCall()))

        val result = builder.build(proposal(), RUNTIME_CONTEXT)

        assertTrue(result is WidgetDraftBuildResult.Valid)
        val draft = (result as WidgetDraftBuildResult.Valid).draft
        assertEquals(WidgetRuntimePolicy.MAX_MEMORY_BYTES, draft.program.manifest.limits.memoryBytes)
        assertEquals(WidgetRuntimePolicy.MAX_TOOL_CALLS, draft.program.manifest.limits.maxToolCalls)
        assertEquals(1, draft.program.manifest.schemaVersion)
        assertEquals(64, draft.programDigest.length)
        assertEquals(64, draft.consentDigest.length)
        assertEquals(listOf("wikipedia_pages"), draft.summary.plannedToolIds)
        assertEquals(ManagedWidgetPolicy.MAX_OBSERVATION_AGE_DAYS, draft.summary.observationRetentionDays)
        assertTrue(draft.blockedTools.isEmpty())
        assertEquals(0, providerExecutions)
    }

    @Test
    fun `disabled proposal tool blocks enablement but remains a valid reviewable draft`() = runTest {
        val result = WidgetDraftBuilder(registry(enabled = false), planner(validCall()))
            .build(proposal(), RUNTIME_CONTEXT)

        val draft = (result as WidgetDraftBuildResult.Valid).draft
        assertEquals(AVAILABLE_TOOLS, draft.blockedTools)
        assertEquals(0, providerExecutions)
    }

    @Test
    fun `invalid proposal plan and typed arguments cause no side effects`() = runTest {
        val registry = registry()
        var planningCalls = 0
        val noPlanForInvalidProposal = WidgetDraftBuilder(
            registry,
            WidgetDraftPlanner { _, _, _ ->
                planningCalls++
                WidgetDraftPlanResult.Valid(emptyList())
            },
        ).build("{}", RUNTIME_CONTEXT)
        val invalidPlan = WidgetDraftBuilder(
            registry,
            plannerResult(WidgetDraftPlanResult.Invalid(WidgetRuntimeFailureCode.InvalidPlan)),
        ).build(proposal(), RUNTIME_CONTEXT)
        val invalidArguments = WidgetDraftBuilder(
            registry,
            planner(validCall(arguments = """{"query":"history","language":"en","endpoint":"https://evil.test"}""")),
        ).build(proposal(), RUNTIME_CONTEXT)

        assertEquals(WidgetDraftBuildResult.Invalid(WidgetDraftFailureCode.InvalidProposal), noPlanForInvalidProposal)
        assertEquals(0, planningCalls)
        assertEquals(WidgetDraftBuildResult.Invalid(WidgetDraftFailureCode.InvalidPlan), invalidPlan)
        assertEquals(WidgetDraftBuildResult.Invalid(WidgetDraftFailureCode.InvalidToolArguments), invalidArguments)
        assertEquals(0, providerExecutions)
    }

    @Test
    fun `javascript draft planner runs only the plan entrypoint`() = runTest {
        val engine = RecordingPlanEngine()
        val planner = JavaScriptWidgetDraftPlanner(engine)
        val proposal = (
            WidgetProposalParser.parse(proposal(), AVAILABLE_TOOLS) as WidgetProposalParseResult.Valid
            ).proposal
        val built = WidgetDraftBuilder(registry(), planner).build(proposalJson(proposal.source), RUNTIME_CONTEXT)

        assertTrue(built is WidgetDraftBuildResult.Valid)
        assertEquals(listOf("plan"), engine.entrypoints)
    }

    @Test
    fun `semantic and source diff identify every authority expansion`() = runTest {
        val draft = (
            WidgetDraftBuilder(registry(), planner(validCall())).build(
                proposal(interval = 1, presentation = listOf("card", "text", "https_link"), source = EDITED_SOURCE),
                RUNTIME_CONTEXT,
            ) as WidgetDraftBuildResult.Valid
            ).draft
        val currentDefinition = definition()
        val currentRevision = revision()

        val diff = diffWidgetDraft(currentDefinition, currentRevision, draft)

        assertTrue(diff.displayNameChanged)
        assertTrue(diff.scheduleChanged)
        assertTrue(diff.moreFrequentSchedule)
        assertEquals(AVAILABLE_TOOLS, diff.addedTools)
        assertEquals(setOf(WidgetToolCapability("weather_lookup", 1)), diff.removedTools)
        assertEquals(setOf(WidgetPresentationCapability.HttpsLink), diff.addedPresentation)
        assertEquals(setOf("open_validated_https_link"), diff.addedActions)
        assertTrue(diff.source.changed)
        assertTrue(diff.expandsAuthority)
    }

    @Test
    fun `confirmation blocks disabled tools supports disabled save and atomically activates edits`() = runTest {
        val store = store()
        val repository = DispatcherManagedWidgetRepository(store, UnconfinedTestDispatcher())
        val scheduler = RecordingScheduler()
        val confirmation = WidgetDraftConfirmationService(ManagedWidgetScheduleController(repository, scheduler))
        val blockedDraft = (
            WidgetDraftBuilder(registry(enabled = false), planner(validCall()))
                .build(proposal(), RUNTIME_CONTEXT) as WidgetDraftBuildResult.Valid
            ).draft

        assertEquals(
            WidgetConfirmationResult.Blocked(AVAILABLE_TOOLS),
            confirmation.confirmCreate(blockedDraft, WidgetConfirmationMode.CreateAndEnable),
        )
        assertTrue(store.listDefinitions().isEmpty())

        val saved = confirmation.confirmCreate(blockedDraft, WidgetConfirmationMode.SaveDisabled)
        val definition = (saved as WidgetConfirmationResult.Confirmed).definition
        assertFalse(definition.enabled)
        assertEquals(1, definition.activeRevision)

        val enabledDraft = (
            WidgetDraftBuilder(registry(), planner(validCall()))
                .build(proposal(source = EDITED_SOURCE), RUNTIME_CONTEXT) as WidgetDraftBuildResult.Valid
            ).draft
        val edited = confirmation.confirmEdit(
            definition.id,
            enabledDraft,
            WidgetConfirmationMode.CreateAndEnable,
        ) as WidgetConfirmationResult.Confirmed
        assertEquals(definition.id, edited.definition.id)
        assertEquals(2, edited.definition.activeRevision)
        assertTrue(edited.definition.enabled)
        assertNotEquals(definition.consentDigest, edited.definition.consentDigest)
        assertEquals(listOf(2), scheduler.revisions)
    }

    @Test
    fun `failed confirmed edit preserves the prior revision and schedule`() = runTest {
        val store = store()
        val repository = DispatcherManagedWidgetRepository(store, UnconfinedTestDispatcher())
        val scheduler = RecordingScheduler()
        val confirmation = WidgetDraftConfirmationService(ManagedWidgetScheduleController(repository, scheduler))
        val existing = store.createConfirmed(existingCandidate())
        val draft = (
            WidgetDraftBuilder(registry(), planner(validCall()))
                .build(proposal(source = EDITED_SOURCE), RUNTIME_CONTEXT) as WidgetDraftBuildResult.Valid
            ).draft
        store.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_confirmed_edit
            BEFORE INSERT ON widget_program_revisions
            WHEN NEW.revision > 1
            BEGIN
                SELECT RAISE(ABORT, 'injected confirmation failure');
            END
            """.trimIndent(),
        )

        assertEquals(
            WidgetConfirmationResult.Failed,
            confirmation.confirmEdit(existing.id, draft, WidgetConfirmationMode.CreateAndEnable),
        )
        assertEquals(1, store.listDefinitions().single().activeRevision)
        assertEquals(WidgetProgramParserTest.SOURCE, store.activeRevision(existing.id)?.source)
        assertTrue(scheduler.revisions.isEmpty())
        assertTrue(store.acquireRunLease(existing.id) is ManagedWidgetLeaseResult.Acquired)
    }

    private fun registry(enabled: Boolean = true) = ApplicationToolRegistry(
        listOf(
            applicationToolBinding(
                contract = widgetContract(),
                state = { ApplicationToolOperationalState(enabled, ready = true) },
                executor = ApplicationTool("Wikipedia pages", ApplicationToolCategory.ExternalKnowledge) { _: Unit ->
                    providerExecutions++
                },
                decodeArguments = { value ->
                    Unit.takeIf {
                        value.keySet() == setOf("query", "language") &&
                            value.get("query")?.isJsonPrimitive == true &&
                            value.get("language")?.asString == "en"
                    }
                },
                encodeResult = { "{}" },
            ),
        ),
    )

    private fun widgetContract() = ApplicationToolContract(
        id = "wikipedia_pages",
        version = 1,
        displayName = "Wikipedia pages",
        category = ApplicationToolCategory.ExternalKnowledge,
        consumers = setOf(ApplicationToolConsumer.Widget),
        inputSchemaJson = """{"type":"object"}""",
        outputSchemaJson = """{"type":"object"}""",
    )

    private fun planner(call: PlannedWidgetToolCall) = plannerResult(WidgetDraftPlanResult.Valid(listOf(call)))

    private fun plannerResult(result: WidgetDraftPlanResult) = WidgetDraftPlanner { _, _, _ -> result }

    private fun validCall(arguments: String = """{"query":"history","language":"en"}""") = PlannedWidgetToolCall(
        alias = "today",
        tool = AVAILABLE_TOOLS.single(),
        argumentsJson = arguments,
    )

    private fun proposal(
        interval: Long = 24,
        presentation: List<String> = listOf("card", "text"),
        source: String = WidgetProgramParserTest.SOURCE,
    ): String = JsonObject().apply {
        addProperty("proposalVersion", 1)
        addProperty("displayName", "Edited title")
        addProperty("enabled", true)
        addProperty("periodicIntervalHours", interval)
        add(
            "tools",
            JsonArray().apply {
                add(
                    JsonObject().apply {
                        addProperty("id", "wikipedia_pages")
                        addProperty("version", 1)
                    },
                )
            },
        )
        add("runtime", JsonArray().apply { add("local_time") })
        add("presentation", JsonArray().apply { presentation.forEach(::add) })
        addProperty("source", source)
    }.toString()

    private fun proposalJson(source: String) = proposal(source = source)

    private fun definition() = ManagedWidgetDefinition(
        id = "widget-1",
        displayName = "Existing title",
        enabled = true,
        periodicIntervalHours = 24,
        activeRevision = 1,
        consentDigest = "b".repeat(64),
        status = ManagedWidgetStatus.Ready,
        createdAtMillis = 1,
        updatedAtMillis = 1,
    )

    private fun revision() = WidgetProgramRevision(
        widgetId = definition().id,
        revision = 1,
        manifestJson = WidgetProgramParserTest.validManifest().replace(",\"https_link\"", ""),
        source = WidgetProgramParserTest.SOURCE,
        programDigest = "a".repeat(64),
        createdAtMillis = 1,
    )

    private fun existingCandidate() = ConfirmedWidgetRevision(
        displayName = "Existing title",
        enabled = true,
        periodicIntervalHours = 24,
        manifestJson = WidgetProgramParserTest.validManifest(),
        source = WidgetProgramParserTest.SOURCE,
        programDigest = "a".repeat(64),
        consentDigest = "b".repeat(64),
    )

    private fun store() = SqliteManagedWidgetRepository(
        context,
        nowMillis = { 1_000L },
        newId = { "widget-${stores.size + 1}" },
    ).also(stores::add)

    private class RecordingPlanEngine : WidgetJavaScriptEngine {
        val entrypoints = mutableListOf<String>()

        override suspend fun call(
            source: String,
            entrypoint: String,
            argumentsJson: List<String>,
            limits: WidgetRequestedLimits,
        ): WidgetScriptResult {
            entrypoints += entrypoint
            return WidgetScriptResult.Success(
                """[{"alias":"today","toolId":"wikipedia_pages","contractVersion":1,""" +
                    """"arguments":{"query":"history","language":"en"}}]""",
            )
        }
    }

    private class RecordingScheduler : ManagedWidgetScheduler {
        val revisions = mutableListOf<Int>()

        override fun reconcile(definition: ManagedWidgetDefinition, revision: WidgetProgramRevision) {
            if (definition.enabled) revisions += revision.revision
        }

        override fun cancel(widgetId: String) = Unit
        override fun cancelUnknown(knownWidgetIds: Set<String>) = Unit
    }

    companion object {
        private val AVAILABLE_TOOLS = setOf(WidgetToolCapability("wikipedia_pages", 1))
        private val RUNTIME_CONTEXT = WidgetRuntimeContext("en-CA", "America/Toronto", "2026-09-06T12:00:00", 42)
        private const val EDITED_SOURCE =
            "function plan(context) { return []; }\n" +
                "function render(context, outcomes, state) { return {type:'text',text:'edited',tone:'neutral'}; }"
    }
}
