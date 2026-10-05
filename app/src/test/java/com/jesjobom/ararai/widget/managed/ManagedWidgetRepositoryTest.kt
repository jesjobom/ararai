package com.jesjobom.ararai.widget.managed

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ManagedWidgetRepositoryTest {
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
    fun `new repository is empty and uses a separate widget database`() {
        val repository = repository()

        assertTrue(repository.listDefinitions().isEmpty())
        assertNotEquals("ararai_chat.db", MANAGED_WIDGET_DATABASE_NAME)
        assertEquals(
            MANAGED_WIDGET_DATABASE_VERSION,
            repository.writableDatabase.version,
        )
        assertEquals(
            1,
            repository.writableDatabase.rawQuery("PRAGMA foreign_keys", emptyArray()).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            },
        )
        assertEquals(
            setOf(
                "managed_widgets",
                "widget_observations",
                "widget_presentations",
                "widget_program_revisions",
                "widget_runs",
                "widget_authoring_actions",
                "widget_authoring_attempts",
                "widget_authoring_checkpoints",
                "widget_authoring_sessions",
            ),
            repository.writableDatabase.rawQuery(
                "SELECT name FROM sqlite_master " +
                    "WHERE type = 'table' AND (name LIKE 'widget_%' OR name = 'managed_widgets')",
                emptyArray(),
            ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } },
        )
    }

    @Test
    fun `confirmed first creation atomically stores definition revision and consent`() {
        val repository = repository()

        val created = repository.createConfirmed(fixture())

        assertEquals("widget-1", created.id)
        assertEquals(1, created.activeRevision)
        assertEquals(DIGEST_B, created.consentDigest)
        assertEquals(listOf(created), repository.listDefinitions())
        assertEquals(
            listOf(
                WidgetProgramRevision(
                    widgetId = created.id,
                    revision = 1,
                    manifestJson = MANIFEST,
                    source = SOURCE,
                    programDigest = DIGEST_A,
                    createdAtMillis = 1_000L,
                ),
            ),
            repository.listRevisions(created.id),
        )
    }

    @Test
    fun `program revisions are immutable`() {
        val repository = repository()
        val created = repository.createConfirmed(fixture())

        assertThrows(SQLiteException::class.java) {
            repository.writableDatabase.execSQL(
                "UPDATE widget_program_revisions SET source = 'changed' WHERE widget_id = ? AND revision = 1",
                arrayOf(created.id),
            )
        }

        assertEquals(SOURCE, repository.listRevisions(created.id).single().source)
    }

    @Test
    fun `confirmed edit appends a revision and atomically replaces active consent`() {
        val repository = repository()
        val created = repository.createConfirmed(fixture())
        now = 2_000L

        val updated = repository.replaceActiveRevision(
            created.id,
            fixture(
                displayName = "Edited widget",
                source = "function plan(){return []}",
                programDigest = DIGEST_C,
                consentDigest = DIGEST_D,
            ),
        )

        assertEquals(2, updated.activeRevision)
        assertEquals("Edited widget", updated.displayName)
        assertEquals(DIGEST_D, updated.consentDigest)
        assertEquals(listOf(1, 2), repository.listRevisions(created.id).map(WidgetProgramRevision::revision))
        assertEquals(DIGEST_A, repository.listRevisions(created.id).first().programDigest)
        assertEquals(DIGEST_C, repository.activeRevision(created.id)?.programDigest)
    }

    @Test
    fun `invalid consent digest is rejected before mutation`() {
        val repository = repository()

        assertThrows(IllegalArgumentException::class.java) {
            repository.createConfirmed(fixture(consentDigest = "not-a-digest"))
        }

        assertTrue(repository.listDefinitions().isEmpty())
    }

    @Test
    fun `unsupported database schema fails closed and leaves its file intact`() {
        val path = context.getDatabasePath(MANAGED_WIDGET_DATABASE_NAME)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { it.version = MANAGED_WIDGET_DATABASE_VERSION + 1 }

        val repository = repository()

        assertThrows(SQLiteException::class.java) { repository.listDefinitions() }
        assertTrue(path.exists())
    }

    @Test
    fun `version one database migrates additively and preserves every existing record`() {
        createVersionOneDatabase()

        val repository = repository()

        assertEquals(MANAGED_WIDGET_DATABASE_VERSION, repository.writableDatabase.version)
        assertEquals("legacy-widget", repository.listDefinitions().single().id)
        assertEquals(SOURCE, repository.listRevisions("legacy-widget").single().source)
        assertEquals("{\"type\":\"text\"}", repository.cachedPresentation("legacy-widget")?.presentationJson)
        assertEquals("legacy-run", repository.listRuns("legacy-widget").single().id)
        val observation = repository.listObservations("legacy-widget").single().value as WidgetObservationValue.Text
        assertEquals("sunny", observation.value)
        assertTrue(repository.activeAuthoringSession() == null)
        assertEquals(
            4,
            repository.writableDatabase.rawQuery(
                "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name LIKE 'widget_authoring_%'",
                emptyArray(),
            ).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            },
        )
    }

    @Test
    fun `dangling active revision is reported as corruption`() {
        var repository = repository()
        val created = repository.createConfirmed(fixture())
        repository.close()
        repositories.remove(repository)
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(MANAGED_WIDGET_DATABASE_NAME).path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { database ->
            database.execSQL("PRAGMA foreign_keys = OFF")
            database.execSQL("UPDATE managed_widgets SET active_revision = 99 WHERE id = ?", arrayOf(created.id))
        }
        repository = repository()

        assertThrows(ManagedWidgetPersistenceException::class.java) {
            repository.listDefinitions()
        }
    }

    @Test
    fun `failed revision insert rolls back first creation`() {
        val repository = repository()
        repository.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_widget_revision
            BEFORE INSERT ON widget_program_revisions
            BEGIN
                SELECT RAISE(ABORT, 'injected revision failure');
            END
            """.trimIndent(),
        )

        assertThrows(SQLiteException::class.java) { repository.createConfirmed(fixture()) }

        assertTrue(repository.listDefinitions().isEmpty())
    }

    private fun repository(): SqliteManagedWidgetRepository = SqliteManagedWidgetRepository(
        context = context,
        nowMillis = { now },
        newId = { "widget-${++nextId}" },
    ).also(repositories::add)

    @Suppress("LongMethod")
    private fun createVersionOneDatabase() {
        val path = context.getDatabasePath(MANAGED_WIDGET_DATABASE_NAME)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { database ->
            database.execSQL(
                """
                CREATE TABLE managed_widgets(
                    id TEXT PRIMARY KEY, display_name TEXT NOT NULL, enabled INTEGER NOT NULL,
                    periodic_interval_hours INTEGER, active_revision INTEGER NOT NULL,
                    consent_digest TEXT NOT NULL, status TEXT NOT NULL,
                    created_at_millis INTEGER NOT NULL, updated_at_millis INTEGER NOT NULL,
                    last_attempt_at_millis INTEGER, last_success_at_millis INTEGER,
                    active_run_id TEXT, active_run_started_at_millis INTEGER
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE TABLE widget_program_revisions(
                    widget_id TEXT NOT NULL, revision INTEGER NOT NULL,
                    manifest_json TEXT NOT NULL, source TEXT NOT NULL,
                    program_digest TEXT NOT NULL, created_at_millis INTEGER NOT NULL,
                    PRIMARY KEY(widget_id, revision)
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE TABLE widget_presentations(
                    widget_id TEXT PRIMARY KEY, revision INTEGER NOT NULL,
                    presentation_json TEXT NOT NULL, completed_at_millis INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE TABLE widget_runs(
                    id TEXT PRIMARY KEY, widget_id TEXT NOT NULL, revision INTEGER NOT NULL,
                    started_at_millis INTEGER NOT NULL, completed_at_millis INTEGER,
                    duration_millis INTEGER, planned_tool_ids TEXT NOT NULL,
                    outcome_code TEXT NOT NULL, diagnostic TEXT
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE TABLE widget_observations(
                    widget_id TEXT NOT NULL, name TEXT NOT NULL,
                    observed_at_millis INTEGER NOT NULL, value_type TEXT NOT NULL,
                    value_text TEXT NOT NULL,
                    PRIMARY KEY(widget_id, name, observed_at_millis)
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO managed_widgets VALUES(
                    'legacy-widget', 'Legacy', 1, 24, 1, '$DIGEST_B', 'Ready',
                    100, 200, 300, 400, NULL, NULL
                )
                """.trimIndent(),
            )
            database.execSQL(
                "INSERT INTO widget_program_revisions VALUES(" +
                    "'legacy-widget', 1, '$MANIFEST', '$SOURCE', '$DIGEST_A', 100)",
            )
            database.execSQL(
                "INSERT INTO widget_presentations VALUES(" +
                    "'legacy-widget', 1, '{\"type\":\"text\"}', 400)",
            )
            database.execSQL(
                "INSERT INTO widget_runs VALUES(" +
                    "'legacy-run', 'legacy-widget', 1, 300, 400, 100, '[]', 'Success', NULL)",
            )
            database.execSQL(
                "INSERT INTO widget_observations VALUES(" +
                    "'legacy-widget', 'condition', 400, 'text', 'sunny')",
            )
            database.version = 1
        }
    }

    private fun fixture(
        displayName: String = "On this day",
        source: String = SOURCE,
        programDigest: String = DIGEST_A,
        consentDigest: String = DIGEST_B,
    ) = ConfirmedWidgetRevision(
        displayName = displayName,
        enabled = true,
        periodicIntervalHours = 24,
        manifestJson = MANIFEST,
        source = source,
        programDigest = programDigest,
        consentDigest = consentDigest,
    )

    private companion object {
        const val MANIFEST = """{"manifestVersion":1,"apiVersion":1}"""
        const val SOURCE = "function plan(){return []}"
        val DIGEST_A = "a".repeat(64)
        val DIGEST_B = "b".repeat(64)
        val DIGEST_C = "c".repeat(64)
        val DIGEST_D = "d".repeat(64)
    }
}
