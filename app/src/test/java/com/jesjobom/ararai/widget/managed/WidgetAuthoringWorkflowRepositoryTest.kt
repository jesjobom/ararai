package com.jesjobom.ararai.widget.managed

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class WidgetAuthoringWorkflowRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repositories = mutableListOf<SqliteManagedWidgetRepository>()
    private var nextId = 0
    private var now = 1_000L

    @Before
    fun setUp() {
        context.deleteDatabase(MANAGED_WIDGET_DATABASE_NAME)
    }

    @After
    fun tearDown() {
        repositories.forEach(SqliteManagedWidgetRepository::close)
        repositories.clear()
        context.deleteDatabase(MANAGED_WIDGET_DATABASE_NAME)
    }

    @Test
    fun `session round trips frozen input and enforces single active workflow`() {
        val repository = repository()

        val created = repository.createAuthoringSession(request())

        assertEquals(1, created.session.revision)
        assertEquals(WidgetAuthoringSessionStatus.AwaitingStage, created.session.status)
        assertEquals(WidgetAuthoringStageKey.Feasibility, created.session.currentStage)
        assertEquals(created, repository.activeAuthoringSession())
        assertThrows(ManagedWidgetPersistenceException::class.java) {
            repository.createAuthoringSession(request(instruction = "another"))
        }
    }

    @Test
    fun `successful attempt is durable reviewable and idempotent by action id`() {
        val repository = repository()
        val session = repository.createAuthoringSession(request()).session
        val start = StartWidgetAuthoringAttempt(
            sessionId = session.id,
            expectedSessionRevision = session.revision,
            actionId = "start-feasibility",
            stage = WidgetAuthoringStageKey.Feasibility,
            kind = WidgetAuthoringAttemptKind.Initial,
            upstreamDigest = EMPTY_DIGEST,
        )

        val started = repository.startAuthoringAttempt(start) as StartWidgetAuthoringAttemptResult.Started
        val duplicate = repository.startAuthoringAttempt(start) as StartWidgetAuthoringAttemptResult.Duplicate
        assertEquals(started.attempt.id, duplicate.attempt.id)
        assertEquals(1, repository.authoringSession(session.id)?.attempts?.size)

        now = 2_000L
        repository.markAuthoringAttemptRunning(session.id, started.attempt.id)
        now = 3_000L
        val completed = repository.completeAuthoringAttempt(
            CompleteWidgetAuthoringAttempt(
                sessionId = session.id,
                attemptId = started.attempt.id,
                status = WidgetAuthoringAttemptStatus.Succeeded,
                artifact = FEASIBILITY,
                failureCode = null,
            ),
        )

        assertEquals(WidgetAuthoringSessionStatus.AwaitingReview, completed.session.status)
        assertEquals(FEASIBILITY, completed.attempts.single().artifact)
        assertEquals(authoringDigest(FEASIBILITY), completed.attempts.single().artifactDigest)
    }

    @Test
    fun `accepted replacement marks exactly supplied descendants stale`() {
        val repository = repository()
        var snapshot = repository.createAuthoringSession(request())
        snapshot = acceptSuccessfulStage(repository, snapshot, WidgetAuthoringStageKey.Feasibility, "feasibility", "{}")
        snapshot = acceptSuccessfulStage(repository, snapshot, WidgetAuthoringStageKey.Algorithm, "algorithm", "{}")
        val call = WidgetAuthoringStageKey.CallFunction("weather")
        snapshot = acceptSuccessfulStage(repository, snapshot, call, "call", "{}")
        snapshot = acceptSuccessfulStage(repository, snapshot, WidgetAuthoringStageKey.Plan, "plan", "{}")

        val reprocess = repository.startAuthoringAttempt(
            StartWidgetAuthoringAttempt(
                sessionId = snapshot.session.id,
                expectedSessionRevision = snapshot.session.revision,
                actionId = "reprocess-algorithm",
                stage = WidgetAuthoringStageKey.Algorithm,
                kind = WidgetAuthoringAttemptKind.Reprocess,
                upstreamDigest = EMPTY_DIGEST,
            ),
        ) as StartWidgetAuthoringAttemptResult.Started
        repository.completeAuthoringAttempt(
            CompleteWidgetAuthoringAttempt(
                snapshot.session.id,
                reprocess.attempt.id,
                WidgetAuthoringAttemptStatus.Succeeded,
                "{\"replacement\":true}",
                null,
            ),
        )
        val ready = requireNotNull(repository.authoringSession(snapshot.session.id))
        val result = repository.acceptAuthoringCandidate(
            AcceptWidgetAuthoringCandidate(
                sessionId = snapshot.session.id,
                expectedSessionRevision = ready.session.revision,
                actionId = "accept-replacement",
                attemptId = reprocess.attempt.id,
                nextStage = call,
            ),
        ) as WidgetAuthoringMutationResult.Applied

        assertEquals(
            WidgetAuthoringCheckpointStatus.Accepted,
            result.snapshot.checkpoints.single {
                it.stage == WidgetAuthoringStageKey.Algorithm
            }.status,
        )
        assertEquals(
            setOf(call, WidgetAuthoringStageKey.Plan),
            result.snapshot.checkpoints.filter { it.status == WidgetAuthoringCheckpointStatus.Stale }
                .mapTo(mutableSetOf(), WidgetAuthoringCheckpoint::stage),
        )
    }

    @Test
    fun `call replacement invalidates aggregate descendants but preserves sibling checkpoint`() {
        val repository = repository()
        var snapshot = repository.createAuthoringSession(request())
        snapshot = acceptSuccessfulStage(repository, snapshot, WidgetAuthoringStageKey.Feasibility, "f", "{}")
        val firstCall = WidgetAuthoringStageKey.CallFunction("weather")
        val secondCall = WidgetAuthoringStageKey.CallFunction("calendar")
        snapshot = acceptSuccessfulStage(
            repository,
            snapshot,
            WidgetAuthoringStageKey.Algorithm,
            "a",
            "{}",
            firstCall,
        )
        snapshot = acceptSuccessfulStage(repository, snapshot, firstCall, "c1", "{}", secondCall)
        snapshot = acceptSuccessfulStage(repository, snapshot, secondCall, "c2", "{}", WidgetAuthoringStageKey.Plan)
        snapshot = acceptSuccessfulStage(repository, snapshot, WidgetAuthoringStageKey.Plan, "p", "{}")
        val replacement = repository.startAuthoringAttempt(
            StartWidgetAuthoringAttempt(
                snapshot.session.id,
                snapshot.session.revision,
                "replace-call",
                firstCall,
                WidgetAuthoringAttemptKind.Reprocess,
                EMPTY_DIGEST,
            ),
        ) as StartWidgetAuthoringAttemptResult.Started
        val completed = repository.completeAuthoringAttempt(
            CompleteWidgetAuthoringAttempt(
                snapshot.session.id,
                replacement.attempt.id,
                WidgetAuthoringAttemptStatus.Succeeded,
                "{\"replacement\":true}",
                null,
            ),
        )

        val accepted = repository.acceptAuthoringCandidate(
            AcceptWidgetAuthoringCandidate(
                snapshot.session.id,
                completed.session.revision,
                "accept-call-replacement",
                replacement.attempt.id,
                WidgetAuthoringStageKey.Plan,
            ),
        ) as WidgetAuthoringMutationResult.Applied

        assertEquals(
            WidgetAuthoringCheckpointStatus.Accepted,
            accepted.snapshot.checkpoints.single {
                it.stage == secondCall
            }.status,
        )
        assertEquals(
            WidgetAuthoringCheckpointStatus.Stale,
            accepted.snapshot.checkpoints.single {
                it.stage == WidgetAuthoringStageKey.Plan
            }.status,
        )
    }

    @Test
    @Suppress("LongMethod")
    fun `failed replacement preserves accepted graph and consumes persisted repair budget`() {
        var repository = repository()
        var snapshot = repository.createAuthoringSession(request())
        snapshot = acceptSuccessfulStage(repository, snapshot, WidgetAuthoringStageKey.Feasibility, "f", "{}")
        val replacement = repository.startAuthoringAttempt(
            StartWidgetAuthoringAttempt(
                snapshot.session.id,
                snapshot.session.revision,
                "replace",
                WidgetAuthoringStageKey.Feasibility,
                WidgetAuthoringAttemptKind.Reprocess,
                EMPTY_DIGEST,
            ),
        ) as StartWidgetAuthoringAttemptResult.Started
        snapshot = repository.completeAuthoringAttempt(
            CompleteWidgetAuthoringAttempt(
                snapshot.session.id,
                replacement.attempt.id,
                WidgetAuthoringAttemptStatus.Failed,
                "{}",
                WidgetAuthoringStageFailureCode.InvalidFeasibilityShape,
            ),
        )
        val acceptedBeforeRestart = snapshot.checkpoints.single()
        repository.close()
        repositories.remove(repository)
        repository = repository()
        snapshot = requireNotNull(repository.activeAuthoringSession())

        assertEquals(acceptedBeforeRestart, snapshot.checkpoints.single())
        repeat(2) { repairIndex ->
            val repair = repository.startAuthoringAttempt(
                StartWidgetAuthoringAttempt(
                    snapshot.session.id,
                    snapshot.session.revision,
                    "replacement-repair-$repairIndex",
                    WidgetAuthoringStageKey.Feasibility,
                    WidgetAuthoringAttemptKind.Repair,
                    EMPTY_DIGEST,
                ),
            ) as StartWidgetAuthoringAttemptResult.Started
            snapshot = repository.completeAuthoringAttempt(
                CompleteWidgetAuthoringAttempt(
                    snapshot.session.id,
                    repair.attempt.id,
                    WidgetAuthoringAttemptStatus.Failed,
                    "{}",
                    WidgetAuthoringStageFailureCode.InvalidFeasibilityShape,
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            repository.startAuthoringAttempt(
                StartWidgetAuthoringAttempt(
                    snapshot.session.id,
                    snapshot.session.revision,
                    "replacement-repair-exhausted",
                    WidgetAuthoringStageKey.Feasibility,
                    WidgetAuthoringAttemptKind.Repair,
                    EMPTY_DIGEST,
                ),
            )
        }
        assertEquals(WidgetAuthoringCheckpointStatus.Accepted, snapshot.checkpoints.single().status)
    }

    @Test
    fun `startup reconciliation interrupts active attempt without removing checkpoints`() {
        val repository = repository()
        var snapshot = repository.createAuthoringSession(request())
        snapshot = acceptSuccessfulStage(repository, snapshot, WidgetAuthoringStageKey.Feasibility, "first", "{}")
        val started = repository.startAuthoringAttempt(
            StartWidgetAuthoringAttempt(
                snapshot.session.id,
                snapshot.session.revision,
                "algorithm",
                WidgetAuthoringStageKey.Algorithm,
                WidgetAuthoringAttemptKind.Initial,
                EMPTY_DIGEST,
            ),
        ) as StartWidgetAuthoringAttemptResult.Started
        repository.markAuthoringAttemptRunning(snapshot.session.id, started.attempt.id)

        val reconciled = requireNotNull(repository.reconcileInterruptedAuthoring())

        assertEquals(WidgetAuthoringAttemptStatus.Interrupted, reconciled.attempts.last().status)
        assertEquals(WidgetAuthoringSessionStatus.AwaitingRetry, reconciled.session.status)
        assertEquals(1, reconciled.checkpoints.size)
        assertEquals(WidgetAuthoringCheckpointStatus.Accepted, reconciled.checkpoints.single().status)
    }

    @Test
    fun `checkpoint transaction failure preserves last committed candidate revision`() {
        val repository = repository()
        val session = repository.createAuthoringSession(request()).session
        val started = repository.startAuthoringAttempt(
            StartWidgetAuthoringAttempt(
                session.id,
                session.revision,
                "start",
                WidgetAuthoringStageKey.Feasibility,
                WidgetAuthoringAttemptKind.Initial,
                EMPTY_DIGEST,
            ),
        ) as StartWidgetAuthoringAttemptResult.Started
        val completed = repository.completeAuthoringAttempt(
            CompleteWidgetAuthoringAttempt(
                session.id,
                started.attempt.id,
                WidgetAuthoringAttemptStatus.Succeeded,
                FEASIBILITY,
                null,
            ),
        )
        repository.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_authoring_checkpoint
            BEFORE INSERT ON widget_authoring_checkpoints
            BEGIN
                SELECT RAISE(ABORT, 'injected checkpoint failure');
            END
            """.trimIndent(),
        )

        assertThrows(SQLiteException::class.java) {
            repository.acceptAuthoringCandidate(
                AcceptWidgetAuthoringCandidate(
                    session.id,
                    completed.session.revision,
                    "accept",
                    started.attempt.id,
                    WidgetAuthoringStageKey.Algorithm,
                ),
            )
        }

        val restored = requireNotNull(repository.authoringSession(session.id))
        assertEquals(completed.session.revision, restored.session.revision)
        assertEquals(WidgetAuthoringSessionStatus.AwaitingReview, restored.session.status)
        assertTrue(restored.checkpoints.isEmpty())
        assertEquals(
            0,
            repository.writableDatabase.rawQuery(
                "SELECT count(*) FROM widget_authoring_actions WHERE action_id = 'accept'",
                emptyArray(),
            ).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            },
        )
    }

    @Test
    fun `stale session revision cannot start or accept work`() {
        val repository = repository()
        val session = repository.createAuthoringSession(request()).session

        val start = repository.startAuthoringAttempt(
            StartWidgetAuthoringAttempt(
                session.id,
                session.revision + 1,
                "stale-start",
                WidgetAuthoringStageKey.Feasibility,
                WidgetAuthoringAttemptKind.Initial,
                EMPTY_DIGEST,
            ),
        )

        assertTrue(start is StartWidgetAuthoringAttemptResult.Stale)
        assertTrue(requireNotNull(repository.authoringSession(session.id)).attempts.isEmpty())
    }

    @Test
    fun `repair budget survives reload and discard requires current inactive revision`() {
        val repository = repository()
        var snapshot = repository.createAuthoringSession(request())
        repeat(3) { index ->
            val kind = if (index == 0) WidgetAuthoringAttemptKind.Initial else WidgetAuthoringAttemptKind.Repair
            val started = repository.startAuthoringAttempt(
                StartWidgetAuthoringAttempt(
                    snapshot.session.id,
                    snapshot.session.revision,
                    "attempt-$index",
                    WidgetAuthoringStageKey.Feasibility,
                    kind,
                    EMPTY_DIGEST,
                ),
            ) as StartWidgetAuthoringAttemptResult.Started
            snapshot = repository.completeAuthoringAttempt(
                CompleteWidgetAuthoringAttempt(
                    snapshot.session.id,
                    started.attempt.id,
                    WidgetAuthoringAttemptStatus.Failed,
                    "{}",
                    WidgetAuthoringStageFailureCode.InvalidFeasibilityShape,
                ),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            repository.startAuthoringAttempt(
                StartWidgetAuthoringAttempt(
                    snapshot.session.id,
                    snapshot.session.revision,
                    "attempt-3",
                    WidgetAuthoringStageKey.Feasibility,
                    WidgetAuthoringAttemptKind.Repair,
                    EMPTY_DIGEST,
                ),
            )
        }
        assertFalse(repository.discardAuthoringSession(snapshot.session.id, snapshot.session.revision - 1))
        assertTrue(repository.discardAuthoringSession(snapshot.session.id, snapshot.session.revision))
        assertEquals(null, repository.activeAuthoringSession())
    }

    @Test
    fun `final confirmation atomically promotes widget and removes transient session`() {
        val repository = repository()
        val created = repository.createAuthoringSession(request())
        var ready = acceptSuccessfulStage(
            repository,
            created,
            WidgetAuthoringStageKey.Feasibility,
            "feasibility",
            FEASIBILITY,
        )
        ready = acceptSuccessfulStage(repository, ready, WidgetAuthoringStageKey.Algorithm, "algorithm", "{}")
        ready = acceptSuccessfulStage(
            repository,
            ready,
            WidgetAuthoringStageKey.CallFunction("weather"),
            "call",
            "{}",
        )
        ready = acceptSuccessfulStage(repository, ready, WidgetAuthoringStageKey.Plan, "plan", "{}")
        ready = acceptSuccessfulStage(repository, ready, WidgetAuthoringStageKey.Render, "render", "{}")

        val result = repository.finalizeAuthoringSession(
            ready.session.id,
            ready.session.revision,
            confirmedRevision(),
        ) as FinalizeWidgetAuthoringResult.Finalized

        assertEquals(1, result.definition.activeRevision)
        assertEquals(null, repository.authoringSession(ready.session.id))
        assertEquals(result.definition, repository.listDefinitions().single())
        assertEquals(SOURCE, repository.activeRevision(result.definition.id)?.source)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `dispatcher repository publishes durable session transitions`() = runTest {
        val store = repository()
        val repository = DispatcherWidgetAuthoringWorkflowRepository(
            store,
            UnconfinedTestDispatcher(testScheduler),
        )

        assertEquals(null, repository.initialize())
        val created = repository.create(request())

        assertEquals(created, repository.activeSession.value)
        assertTrue(repository.discard(created.session.id, created.session.revision))
        assertEquals(null, repository.activeSession.value)
    }

    private fun acceptSuccessfulStage(
        repository: SqliteManagedWidgetRepository,
        initial: WidgetAuthoringSessionSnapshot,
        stage: WidgetAuthoringStageKey,
        actionSuffix: String,
        artifact: String,
        nextStage: WidgetAuthoringStageKey? = nextStageAfter(stage),
    ): WidgetAuthoringSessionSnapshot {
        val started = repository.startAuthoringAttempt(
            StartWidgetAuthoringAttempt(
                initial.session.id,
                initial.session.revision,
                "start-$actionSuffix",
                stage,
                WidgetAuthoringAttemptKind.Initial,
                EMPTY_DIGEST,
            ),
        ) as StartWidgetAuthoringAttemptResult.Started
        val completed = repository.completeAuthoringAttempt(
            CompleteWidgetAuthoringAttempt(
                initial.session.id,
                started.attempt.id,
                WidgetAuthoringAttemptStatus.Succeeded,
                artifact,
                null,
            ),
        )
        return (
            repository.acceptAuthoringCandidate(
                AcceptWidgetAuthoringCandidate(
                    initial.session.id,
                    completed.session.revision,
                    "accept-$actionSuffix",
                    started.attempt.id,
                    nextStage,
                ),
            ) as WidgetAuthoringMutationResult.Applied
            ).snapshot
    }

    private fun nextStageAfter(stage: WidgetAuthoringStageKey): WidgetAuthoringStageKey? = when (stage) {
        WidgetAuthoringStageKey.Feasibility -> WidgetAuthoringStageKey.Algorithm
        WidgetAuthoringStageKey.Algorithm -> WidgetAuthoringStageKey.CallFunction("weather")
        is WidgetAuthoringStageKey.CallFunction -> WidgetAuthoringStageKey.Plan
        WidgetAuthoringStageKey.Plan -> WidgetAuthoringStageKey.Render
        WidgetAuthoringStageKey.Render, WidgetAuthoringStageKey.Assembly -> null
    }

    private fun repository(): SqliteManagedWidgetRepository = SqliteManagedWidgetRepository(
        context = context,
        nowMillis = { now++ },
        newId = { "workflow-${++nextId}" },
    ).also(repositories::add)

    private fun request(instruction: String = "Create a weather widget") = NewWidgetAuthoringSession(
        instruction = instruction,
        targetWidgetId = null,
        modelId = "e4b",
        modelArtifactDigest = "a".repeat(64),
        inferenceConfigJson = "{}",
        toolContractDigest = "b".repeat(64),
    )

    private fun confirmedRevision() = ConfirmedWidgetRevision(
        displayName = "Weather",
        enabled = true,
        periodicIntervalHours = 24,
        manifestJson = MANIFEST,
        source = SOURCE,
        programDigest = "c".repeat(64),
        consentDigest = "d".repeat(64),
    )

    private companion object {
        val EMPTY_DIGEST = "0".repeat(64)
        const val FEASIBILITY = "{\"outcome\":\"achievable\"}"
        const val MANIFEST = "{\"manifestVersion\":1,\"apiVersion\":1}"
        const val SOURCE = "function plan(){return []}"
    }
}
