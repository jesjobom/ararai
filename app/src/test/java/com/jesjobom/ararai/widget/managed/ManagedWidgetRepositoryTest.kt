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
