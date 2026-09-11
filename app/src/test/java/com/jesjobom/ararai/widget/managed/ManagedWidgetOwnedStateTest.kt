package com.jesjobom.ararai.widget.managed

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ManagedWidgetOwnedStateTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repositories = mutableListOf<SqliteManagedWidgetRepository>()
    private var id = 0
    private var now = 1_000L

    @Before
    fun setUp() {
        context.deleteDatabase(MANAGED_WIDGET_DATABASE_NAME)
    }

    @After
    fun tearDown() {
        repositories.forEach(SqliteManagedWidgetRepository::close)
        context.deleteDatabase(MANAGED_WIDGET_DATABASE_NAME)
    }

    @Test
    fun `cache is revision bound and replaced only by valid owned state`() {
        val repository = repository()
        val widget = repository.createConfirmed(fixture())

        repository.replacePresentation(
            WidgetPresentationCache(widget.id, 1, PRESENTATION, completedAtMillis = 2_000L),
        )

        assertEquals(
            WidgetPresentationCache(widget.id, 1, PRESENTATION, completedAtMillis = 2_000L),
            repository.cachedPresentation(widget.id),
        )
        assertThrows(SQLiteException::class.java) {
            repository.replacePresentation(
                WidgetPresentationCache(widget.id, 99, PRESENTATION, completedAtMillis = 3_000L),
            )
        }
        assertEquals(1, repository.cachedPresentation(widget.id)?.revision)
    }

    @Test
    fun `runs are sanitized typed and retained by count and age`() {
        val repository = repository()
        val widget = repository.createConfirmed(fixture())
        val day = MILLIS_PER_DAY
        now = 50L * day

        repository.recordRun(
            NewWidgetRun(
                widgetId = widget.id,
                revision = 1,
                startedAtMillis = day,
                completedAtMillis = day + 5,
                plannedToolIds = listOf("wikipedia_pages"),
                outcome = WidgetRunOutcome.Success,
                diagnostic = null,
            ),
        )
        repeat(ManagedWidgetPolicy.MAX_RUNS_PER_WIDGET + 5) { index ->
            repository.recordRun(
                NewWidgetRun(
                    widgetId = widget.id,
                    revision = 1,
                    startedAtMillis = now + index,
                    completedAtMillis = now + index + 1,
                    plannedToolIds = emptyList(),
                    outcome = WidgetRunOutcome.ControlledFailure,
                    diagnostic = WidgetRunDiagnostic.RuntimeUnavailable,
                ),
            )
        }

        val runs = repository.listRuns(widget.id)
        assertEquals(ManagedWidgetPolicy.MAX_RUNS_PER_WIDGET, runs.size)
        assertTrue(runs.none { it.startedAtMillis == day })
        assertEquals(WidgetRunDiagnostic.RuntimeUnavailable, runs.first().diagnostic)
        assertThrows(IllegalArgumentException::class.java) {
            NewWidgetRun(
                widgetId = widget.id,
                revision = 1,
                startedAtMillis = now,
                completedAtMillis = now,
                plannedToolIds = emptyList(),
                outcome = WidgetRunOutcome.Success,
                diagnostic = WidgetRunDiagnostic.RuntimeUnavailable,
            )
        }
    }

    @Test
    fun `typed observations are owner scoped and support nearest lookup with tolerance`() {
        val repository = repository()
        val first = repository.createConfirmed(fixture())
        val second = repository.createConfirmed(fixture(displayName = "Second"))
        now = 100L * MILLIS_PER_DAY
        repository.appendObservations(
            first.id,
            listOf(
                NewWidgetObservation("count", now - 10_000, WidgetObservationValue.Number("2.5")),
                NewWidgetObservation("count", now - 5_000, WidgetObservationValue.Number("3")),
                NewWidgetObservation("visible", now - 4_000, WidgetObservationValue.BooleanValue(true)),
            ),
        )

        val firstHistory = repository.ownedHistory(first.id)
        val secondHistory = repository.ownedHistory(second.id)
        assertEquals(
            WidgetObservationValue.Number("3"),
            firstHistory.nearest("count", now - 5_500, toleranceMillis = 1_000)?.value,
        )
        assertEquals(3, firstHistory.list().size)
        assertNull(firstHistory.nearest("count", now - 20_000, toleranceMillis = 1_000))
        assertNull(secondHistory.nearest("count", now - 5_500, toleranceMillis = 1_000))
        assertThrows(IllegalArgumentException::class.java) {
            NewWidgetObservation("invalid name", now, WidgetObservationValue.Text("value"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WidgetObservationValue.Number("01")
        }
    }

    @Test
    fun `observation retention applies both age and owner count limits`() {
        val repository = repository()
        val widget = repository.createConfirmed(fixture())
        now = 100L * MILLIS_PER_DAY
        repository.appendObservations(
            widget.id,
            listOf(NewWidgetObservation("old", now - 91L * MILLIS_PER_DAY, WidgetObservationValue.Text("old"))),
        )
        repeat(ManagedWidgetPolicy.MAX_OBSERVATIONS_PER_WIDGET + 4) { index ->
            repository.appendObservations(
                widget.id,
                listOf(NewWidgetObservation("value", now + index, WidgetObservationValue.Text("$index"))),
            )
        }

        val observations = repository.listObservations(widget.id)
        assertEquals(ManagedWidgetPolicy.MAX_OBSERVATIONS_PER_WIDGET, observations.size)
        assertTrue(observations.none { it.name == "old" })
        assertEquals("259", (observations.first().value as WidgetObservationValue.Text).value)
    }

    @Test
    fun `observation batch rolls back when one row conflicts`() {
        val repository = repository()
        val widget = repository.createConfirmed(fixture())
        val duplicate = NewWidgetObservation("value", 2_000L, WidgetObservationValue.Text("same-key"))

        assertThrows(SQLiteException::class.java) {
            repository.appendObservations(widget.id, listOf(duplicate, duplicate))
        }

        assertTrue(repository.listObservations(widget.id).isEmpty())
    }

    @Test
    fun `confirmed revisions evict only the oldest inactive revision`() {
        val repository = repository()
        val widget = repository.createConfirmed(fixture())

        repeat(ManagedWidgetPolicy.MAX_REVISIONS_PER_WIDGET + 2) { index ->
            now++
            repository.replaceActiveRevision(
                widget.id,
                fixture(
                    displayName = "Revision ${index + 2}",
                    source = "source-${index + 2}",
                    programDigest = digest((index + 2) % 10),
                    consentDigest = digest((index + 3) % 10),
                ),
            )
        }

        val definition = repository.listDefinitions().single()
        val revisions = repository.listRevisions(widget.id)
        assertEquals(ManagedWidgetPolicy.MAX_REVISIONS_PER_WIDGET, revisions.size)
        assertEquals(ManagedWidgetPolicy.MAX_REVISIONS_PER_WIDGET + 3, definition.activeRevision)
        assertEquals(4, revisions.first().revision)
    }

    @Test
    fun `failed confirmed edit preserves active revision definition and cache`() {
        val repository = repository()
        val widget = repository.createConfirmed(fixture())
        repository.replacePresentation(WidgetPresentationCache(widget.id, 1, PRESENTATION, 2_000L))
        repository.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_widget_activation
            BEFORE UPDATE ON managed_widgets
            BEGIN
                SELECT RAISE(ABORT, 'injected activation failure');
            END
            """.trimIndent(),
        )

        assertThrows(SQLiteException::class.java) {
            repository.replaceActiveRevision(
                widget.id,
                fixture(source = "edited", programDigest = DIGEST_C, consentDigest = DIGEST_D),
            )
        }

        assertEquals(1, repository.listDefinitions().single().activeRevision)
        assertEquals(listOf(1), repository.listRevisions(widget.id).map(WidgetProgramRevision::revision))
        assertEquals(PRESENTATION, repository.cachedPresentation(widget.id)?.presentationJson)
    }

    @Test
    fun `unconfirmed candidate creates no durable revision`() {
        val repository = repository()
        val widget = repository.createConfirmed(fixture())

        val unconfirmed = fixture(source = "unconfirmed", programDigest = DIGEST_C, consentDigest = DIGEST_D)

        assertEquals(DIGEST_C, unconfirmed.programDigest)
        assertEquals(1, repository.listDefinitions().single().activeRevision)
        assertEquals(listOf(1), repository.listRevisions(widget.id).map(WidgetProgramRevision::revision))
    }

    @Test
    fun `disable and complete deletion reject late owned writes`() {
        val repository = repository()
        val widget = repository.createConfirmed(fixture())
        repository.replacePresentation(WidgetPresentationCache(widget.id, 1, PRESENTATION, 2_000L))
        repository.recordRun(successfulRun(widget.id))
        repository.appendObservations(
            widget.id,
            listOf(NewWidgetObservation("value", 2_000L, WidgetObservationValue.Text("stored"))),
        )

        assertFalse(repository.setEnabled(widget.id, false).enabled)
        repository.delete(widget.id)

        assertTrue(repository.listDefinitions().isEmpty())
        assertTrue(repository.listRevisions(widget.id).isEmpty())
        assertNull(repository.cachedPresentation(widget.id))
        assertTrue(repository.listRuns(widget.id).isEmpty())
        assertTrue(repository.listObservations(widget.id).isEmpty())
        assertThrows(SQLiteException::class.java) {
            repository.recordRun(successfulRun(widget.id))
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `dispatcher facade never runs database work on the caller`() = runTest {
        val store = repository()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository: ManagedWidgetRepository = DispatcherManagedWidgetRepository(store, dispatcher)

        val pending = async { repository.listDefinitions() }

        assertFalse(pending.isCompleted)
        testScheduler.runCurrent()
        assertEquals(emptyList<ManagedWidgetDefinition>(), pending.await())
    }

    private fun successfulRun(widgetId: String) = NewWidgetRun(
        widgetId = widgetId,
        revision = 1,
        startedAtMillis = 2_000L,
        completedAtMillis = 2_010L,
        plannedToolIds = listOf("wikipedia_pages"),
        outcome = WidgetRunOutcome.Success,
        diagnostic = null,
    )

    private fun repository(): SqliteManagedWidgetRepository = SqliteManagedWidgetRepository(
        context = context,
        nowMillis = { now },
        newId = { "id-${++id}" },
    ).also(repositories::add)

    private fun fixture(
        displayName: String = "On this day",
        source: String = "source-1",
        programDigest: String = DIGEST_A,
        consentDigest: String = DIGEST_B,
    ) = ConfirmedWidgetRevision(
        displayName = displayName,
        enabled = true,
        periodicIntervalHours = 24,
        manifestJson = "{}",
        source = source,
        programDigest = programDigest,
        consentDigest = consentDigest,
    )

    private fun digest(value: Int): String = value.toString().repeat(64)

    private companion object {
        const val PRESENTATION = """{"type":"text","text":"ready","tone":"neutral"}"""
        const val MILLIS_PER_DAY = 86_400_000L
        val DIGEST_A = "a".repeat(64)
        val DIGEST_B = "b".repeat(64)
        val DIGEST_C = "c".repeat(64)
        val DIGEST_D = "d".repeat(64)
    }
}
