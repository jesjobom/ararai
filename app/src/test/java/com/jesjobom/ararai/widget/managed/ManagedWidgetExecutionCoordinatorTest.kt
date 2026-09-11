package com.jesjobom.ararai.widget.managed

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonObject
import com.jesjobom.ararai.tools.ApplicationToolRejection
import com.jesjobom.ararai.widget.runtime.WidgetExecutionResult
import com.jesjobom.ararai.widget.runtime.WidgetPresentationNode
import com.jesjobom.ararai.widget.runtime.WidgetProgramParserTest
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeFailureCode
import com.jesjobom.ararai.widget.runtime.WidgetToolCapability
import com.jesjobom.ararai.widget.runtime.WidgetToolOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ManagedWidgetExecutionCoordinatorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val stores = mutableListOf<SqliteManagedWidgetRepository>()
    private var id = 0
    private var now = 1_000L

    @Before
    fun setUp() {
        context.deleteDatabase(MANAGED_WIDGET_DATABASE_NAME)
    }

    @After
    fun tearDown() {
        stores.forEach(SqliteManagedWidgetRepository::close)
        context.deleteDatabase(MANAGED_WIDGET_DATABASE_NAME)
    }

    @Test
    fun `successful run atomically stores active revision cache timestamps and observations`() = runTest {
        val store = store()
        val widget = store.createConfirmed(fixture())
        store.appendObservations(
            widget.id,
            listOf(NewWidgetObservation("previous", 900, WidgetObservationValue.Number("1"))),
        )
        var receivedState = ""
        val coordinator = coordinator(store) { program, grant, _, stateJson ->
            receivedState = stateJson
            assertEquals(program.manifest.capabilities.tools, grant.tools)
            now = 1_050L
            ManagedWidgetProgramResult(
                execution = WidgetExecutionResult.Success(
                    presentation = WidgetPresentationNode.Text("ready", "neutral"),
                    outcomes = listOf(WidgetToolOutcome("today", JsonObject(), null)),
                    plannedTools = listOf(TOOL),
                ),
                observations = listOf(
                    NewWidgetObservation("current", 1_050L, WidgetObservationValue.Text("stored")),
                ),
            )
        }

        assertEquals(ManagedWidgetExecutionStatus.Completed, coordinator.execute(widget.id))

        val definition = store.listDefinitions().single()
        val run = store.listRuns(widget.id).single()
        assertEquals(WidgetRunOutcome.Success, run.outcome)
        assertEquals(1, run.revision)
        assertEquals(1_000L, run.startedAtMillis)
        assertEquals(1_050L, run.completedAtMillis)
        assertEquals(listOf("weather_lookup"), run.plannedToolIds)
        assertEquals(1_050L, definition.lastAttemptAtMillis)
        assertEquals(1_050L, definition.lastSuccessAtMillis)
        assertEquals(ManagedWidgetStatus.Ready, definition.status)
        assertTrue(store.cachedPresentation(widget.id)?.presentationJson?.contains("ready") == true)
        assertEquals(2, store.listObservations(widget.id).size)
        assertTrue(receivedState.contains("previous"))
        assertFalse(receivedState.contains(widget.id))
    }

    @Test
    fun `repository lease coalesces races and discards result after edit disable and delete`() {
        val store = store()
        val widget = store.createConfirmed(fixture())
        val first = (store.acquireRunLease(widget.id) as ManagedWidgetLeaseResult.Acquired).lease

        assertEquals(ManagedWidgetLeaseResult.Busy, store.acquireRunLease(widget.id))
        store.replaceActiveRevision(widget.id, fixture(displayName = "Edited"))
        assertEquals(ManagedWidgetCommitResult.Discarded, store.completeRun(first, success(first)))
        assertEquals(WidgetRunOutcome.Discarded, store.listRuns(widget.id).single().outcome)
        assertNull(store.cachedPresentation(widget.id))

        val second = (store.acquireRunLease(widget.id) as ManagedWidgetLeaseResult.Acquired).lease
        store.writableDatabase.execSQL(
            "UPDATE managed_widgets SET consent_digest = ? WHERE id = ?",
            arrayOf("c".repeat(64), widget.id),
        )
        assertEquals(ManagedWidgetCommitResult.Discarded, store.completeRun(second, success(second)))
        assertNull(store.cachedPresentation(widget.id))

        val third = (store.acquireRunLease(widget.id) as ManagedWidgetLeaseResult.Acquired).lease
        store.setEnabled(widget.id, false)
        assertEquals(ManagedWidgetCommitResult.Discarded, store.completeRun(third, success(third)))
        assertNull(store.cachedPresentation(widget.id))

        store.setEnabled(widget.id, true)
        val fourth = (store.acquireRunLease(widget.id) as ManagedWidgetLeaseResult.Acquired).lease
        store.delete(widget.id)
        assertEquals(ManagedWidgetCommitResult.Discarded, store.completeRun(fourth, success(fourth)))
        assertTrue(store.listRuns(widget.id).isEmpty())
        assertTrue(store.listObservations(widget.id).isEmpty())
    }

    @Test
    fun `concurrent manual and scheduled executions dispatch only one owned run`() = runTest {
        val store = store()
        val widget = store.createConfirmed(fixture())
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var executions = 0
        val coordinator = coordinator(store) { _, _, _, _ ->
            executions++
            started.complete(Unit)
            release.await()
            successfulProgramResult()
        }
        val first = async { coordinator.execute(widget.id) }
        started.await()

        val competing = coordinator.execute(widget.id)
        release.complete(Unit)

        assertEquals(ManagedWidgetExecutionStatus.Coalesced, competing)
        assertEquals(ManagedWidgetExecutionStatus.Completed, first.await())
        assertEquals(1, executions)
        assertEquals(1, store.listRuns(widget.id).size)
    }

    @Test
    fun `expired persisted lease is closed with stable diagnostic before recovery`() {
        val store = store()
        val widget = store.createConfirmed(fixture())
        store.acquireRunLease(widget.id) as ManagedWidgetLeaseResult.Acquired
        now += ManagedWidgetPolicy.MAX_EXECUTION_LEASE_MILLIS

        val recovered = store.acquireRunLease(widget.id)

        assertTrue(recovered is ManagedWidgetLeaseResult.Acquired)
        val expired = store.listRuns(widget.id).first { it.outcome == WidgetRunOutcome.ControlledFailure }
        assertEquals(WidgetRunDiagnostic.LeaseExpired, expired.diagnostic)
    }

    @Test
    fun `invalid active program retains cache marks stale and disables execution`() = runTest {
        val store = store()
        val widget = store.createConfirmed(fixture(manifestJson = "{}"))
        store.replacePresentation(WidgetPresentationCache(widget.id, 1, PRESENTATION, 900L))
        now = 1_100L

        val status = coordinator(store) { _, _, _, _ -> error("must not execute") }.execute(widget.id)

        assertEquals(ManagedWidgetExecutionStatus.Completed, status)
        val definition = store.listDefinitions().single()
        assertFalse(definition.enabled)
        assertEquals(ManagedWidgetStatus.NeedsAttention, definition.status)
        assertEquals(PRESENTATION, store.cachedPresentation(widget.id)?.presentationJson)
        assertEquals(WidgetRunDiagnostic.InvalidProgram, store.listRuns(widget.id).single().diagnostic)
    }

    @Test
    fun `tool rejection and arbitrary runtime exception become sanitized controlled failures`() = runTest {
        val store = store()
        val toolFailure = store.createConfirmed(fixture(displayName = "Tool failure"))
        store.replacePresentation(WidgetPresentationCache(toolFailure.id, 1, PRESENTATION, 900L))
        val toolCoordinator = coordinator(store) { _, _, _, _ ->
            ManagedWidgetProgramResult(
                WidgetExecutionResult.Success(
                    WidgetPresentationNode.Text("must not cache", "neutral"),
                    listOf(WidgetToolOutcome("today", null, ApplicationToolRejection.NotConfigured)),
                    listOf(TOOL),
                ),
            )
        }

        assertEquals(ManagedWidgetExecutionStatus.Completed, toolCoordinator.execute(toolFailure.id))
        assertEquals(PRESENTATION, store.cachedPresentation(toolFailure.id)?.presentationJson)
        assertEquals(WidgetRunDiagnostic.ToolUnavailable, store.listRuns(toolFailure.id).single().diagnostic)

        val runtimeFailure = store.createConfirmed(fixture(displayName = "Runtime failure"))
        val secret = "credential=do-not-store"
        val runtimeCoordinator = coordinator(store) { _, _, _, _ -> error(secret) }
        assertEquals(ManagedWidgetExecutionStatus.Completed, runtimeCoordinator.execute(runtimeFailure.id))
        val runs = store.listRuns(runtimeFailure.id)
        assertEquals(WidgetRunDiagnostic.RuntimeUnavailable, runs.single().diagnostic)
        assertFalse(runs.toString().contains(secret))
    }

    @Test
    fun `bounded runtime failures retain stale cache with only stable diagnostics`() = runTest {
        val store = store()
        val failures = listOf(
            WidgetRuntimeFailureCode.CapabilityDenied,
            WidgetRuntimeFailureCode.InvalidPlan,
            WidgetRuntimeFailureCode.InvalidPresentation,
            WidgetRuntimeFailureCode.ScriptError,
            WidgetRuntimeFailureCode.ResourceLimit,
            WidgetRuntimeFailureCode.RuntimeUnavailable,
        )
        failures.forEachIndexed { index, failure ->
            val widget = store.createConfirmed(fixture(displayName = "Failure $index"))
            store.replacePresentation(WidgetPresentationCache(widget.id, 1, PRESENTATION, 900L))
            val coordinator = coordinator(store) { _, _, _, _ ->
                ManagedWidgetProgramResult(WidgetExecutionResult.Failure(failure, listOf(TOOL)))
            }

            assertEquals(ManagedWidgetExecutionStatus.Completed, coordinator.execute(widget.id))
            assertEquals(PRESENTATION, store.cachedPresentation(widget.id)?.presentationJson)
            assertEquals(failure.name, store.listRuns(widget.id).single().diagnostic?.name)
            assertEquals(
                ManagedWidgetStatus.NeedsAttention,
                store.listDefinitions().first { it.id == widget.id }.status,
            )
        }
    }

    @Test
    fun `cache run and observations roll back together when final sink fails`() {
        val store = store()
        val widget = store.createConfirmed(fixture())
        val lease = (store.acquireRunLease(widget.id) as ManagedWidgetLeaseResult.Acquired).lease
        store.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_observation_commit
            BEFORE INSERT ON widget_observations
            BEGIN
                SELECT RAISE(ABORT, 'injected observation failure');
            END
            """.trimIndent(),
        )

        assertThrows(SQLiteException::class.java) { store.completeRun(lease, success(lease)) }

        assertNull(store.cachedPresentation(widget.id))
        assertTrue(store.listObservations(widget.id).isEmpty())
        assertEquals(WidgetRunOutcome.InProgress, store.listRuns(widget.id).single().outcome)
        assertEquals(ManagedWidgetLeaseResult.Busy, store.acquireRunLease(widget.id))
    }

    @Test
    fun `caller cancellation releases lease records stale attempt and remains cancellable`() = runTest {
        val store = store()
        val widget = store.createConfirmed(fixture())
        store.replacePresentation(WidgetPresentationCache(widget.id, 1, PRESENTATION, 900L))
        val execution = async {
            coordinator(store) { _, _, _, _ -> awaitCancellation() }.execute(widget.id)
        }
        runCurrent()

        execution.cancelAndJoin()

        assertTrue(execution.isCancelled)
        assertEquals(PRESENTATION, store.cachedPresentation(widget.id)?.presentationJson)
        assertEquals(WidgetRunOutcome.Cancelled, store.listRuns(widget.id).single().outcome)
        assertEquals(WidgetRunDiagnostic.Cancelled, store.listRuns(widget.id).single().diagnostic)
        assertTrue(store.acquireRunLease(widget.id) is ManagedWidgetLeaseResult.Acquired)
    }

    @Test
    fun `manual refresh uses shared coordinator and refuses disabled widget`() = runTest {
        val store = store()
        val widget = store.createConfirmed(fixture())
        store.setEnabled(widget.id, false)
        var executions = 0
        val refresh = ManagedWidgetManualRefresh(
            coordinator(store) { _, _, _, _ ->
                executions++
                successfulProgramResult()
            },
        )

        assertEquals(ManagedWidgetExecutionStatus.Disabled, refresh.refresh(widget.id))
        assertEquals(0, executions)
        assertTrue(store.listRuns(widget.id).isEmpty())
    }

    @Test
    fun `schedule controller reconciles create edit enable disable and deletion state`() = runTest {
        val store = store()
        val repository = DispatcherManagedWidgetRepository(store, UnconfinedTestDispatcher())
        val scheduler = RecordingScheduler()
        val controller = ManagedWidgetScheduleController(repository, scheduler)
        val widget = controller.createConfirmed(fixture())

        controller.replaceActiveRevision(widget.id, fixture(displayName = "Edited"))
        controller.setEnabled(widget.id, false)
        controller.setEnabled(widget.id, true)
        controller.delete(widget.id)

        assertEquals(listOf(1, 2, 2), scheduler.revisions)
        assertEquals(listOf(widget.id, widget.id), scheduler.cancelled)
        assertTrue(store.listDefinitions().isEmpty())
    }

    @Test
    fun `startup reconciliation is repeatable and prunes work outside stored definitions`() = runTest {
        val store = store()
        val repository = DispatcherManagedWidgetRepository(store, UnconfinedTestDispatcher())
        val scheduler = RecordingScheduler()
        val widget = repository.createConfirmed(fixture())
        val reconciler = ManagedWidgetStartupReconciler(repository, scheduler)

        reconciler.reconcile()
        reconciler.reconcile()

        assertEquals(listOf(1, 1), scheduler.revisions)
        assertEquals(listOf(setOf(widget.id), setOf(widget.id)), scheduler.knownWidgetIds)
    }

    private fun coordinator(
        store: SqliteManagedWidgetRepository,
        executor: ManagedWidgetProgramExecutor,
    ) = ManagedWidgetExecutionCoordinator(
        repository = DispatcherManagedWidgetRepository(store, UnconfinedTestDispatcher()),
        executor = executor,
        contextProvider = { WidgetRuntimeContext("en-CA", "America/Toronto", "2026-09-05T12:00:00", 42) },
        nowMillis = { now },
    )

    private fun store() = SqliteManagedWidgetRepository(
        context = context,
        nowMillis = { now },
        newId = { "id-${++id}" },
    ).also(stores::add)

    private fun success(lease: ManagedWidgetRunLease) = ManagedWidgetRunCompletion(
        completedAtMillis = now.coerceAtLeast(lease.startedAtMillis),
        plannedToolIds = listOf("weather_lookup"),
        outcome = WidgetRunOutcome.Success,
        diagnostic = null,
        presentationJson = PRESENTATION,
        observations = listOf(NewWidgetObservation("value", now, WidgetObservationValue.Text("late"))),
    )

    private fun successfulProgramResult() = ManagedWidgetProgramResult(
        WidgetExecutionResult.Success(
            WidgetPresentationNode.Text("ready", "neutral"),
            listOf(WidgetToolOutcome("today", JsonObject(), null)),
            listOf(TOOL),
        ),
    )

    private class RecordingScheduler : ManagedWidgetScheduler {
        val revisions = mutableListOf<Int>()
        val cancelled = mutableListOf<String>()
        val knownWidgetIds = mutableListOf<Set<String>>()

        override fun reconcile(
            definition: ManagedWidgetDefinition,
            revision: WidgetProgramRevision,
        ) {
            if (definition.enabled) revisions += revision.revision else cancelled += definition.id
        }

        override fun cancel(widgetId: String) {
            cancelled += widgetId
        }

        override fun cancelUnknown(knownWidgetIds: Set<String>) {
            this.knownWidgetIds += knownWidgetIds
        }
    }

    private fun fixture(
        displayName: String = "On this day",
        manifestJson: String = WidgetProgramParserTest.validManifest(),
    ) = ConfirmedWidgetRevision(
        displayName = displayName,
        enabled = true,
        periodicIntervalHours = 24,
        manifestJson = manifestJson,
        source = WidgetProgramParserTest.SOURCE,
        programDigest = "a".repeat(64),
        consentDigest = "b".repeat(64),
    )

    private companion object {
        val TOOL = WidgetToolCapability("weather_lookup", 1)
        const val PRESENTATION = """{"type":"text","text":"cached","tone":"neutral"}"""
    }
}
