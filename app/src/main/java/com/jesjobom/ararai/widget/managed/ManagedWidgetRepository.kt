@file:Suppress("TooManyFunctions")

package com.jesjobom.ararai.widget.managed

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.jesjobom.ararai.widget.runtime.WidgetRuntimePolicy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.util.UUID

internal const val MANAGED_WIDGET_DATABASE_NAME = "ararai_widgets.db"
internal const val MANAGED_WIDGET_DATABASE_VERSION = 1

internal class ManagedWidgetPersistenceException(message: String) : IllegalStateException(message)

internal enum class ManagedWidgetStatus { Ready, NeedsAttention }

internal data class ManagedWidgetDefinition(
    val id: String,
    val displayName: String,
    val enabled: Boolean,
    val periodicIntervalHours: Long?,
    val activeRevision: Int,
    val consentDigest: String,
    val status: ManagedWidgetStatus,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val lastAttemptAtMillis: Long? = null,
    val lastSuccessAtMillis: Long? = null,
)

internal data class WidgetProgramRevision(
    val widgetId: String,
    val revision: Int,
    val manifestJson: String,
    val source: String,
    val programDigest: String,
    val createdAtMillis: Long,
)

internal data class ConfirmedWidgetRevision(
    val displayName: String,
    val enabled: Boolean,
    val periodicIntervalHours: Long?,
    val manifestJson: String,
    val source: String,
    val programDigest: String,
    val consentDigest: String,
) {
    init {
        require(displayName.isNotBlank() && displayName.length <= MAX_DISPLAY_NAME_CHARS)
        require(
            periodicIntervalHours == null ||
                periodicIntervalHours in ManagedWidgetPolicy.SUPPORTED_PERIODIC_INTERVAL_HOURS,
        )
        require(manifestJson.utf8Size() <= WidgetRuntimePolicy.MAX_MANIFEST_BYTES)
        require(source.utf8Size() <= ManagedWidgetPolicy.MAX_PROGRAM_SOURCE_BYTES)
        require(SHA_256_PATTERN.matches(programDigest))
        require(SHA_256_PATTERN.matches(consentDigest))
    }
}

internal interface ManagedWidgetStore {
    fun listDefinitions(): List<ManagedWidgetDefinition>

    fun createConfirmed(candidate: ConfirmedWidgetRevision): ManagedWidgetDefinition

    fun replaceActiveRevision(
        widgetId: String,
        candidate: ConfirmedWidgetRevision,
    ): ManagedWidgetDefinition

    fun listRevisions(widgetId: String): List<WidgetProgramRevision>

    fun activeRevision(widgetId: String): WidgetProgramRevision?

    fun acquireRunLease(widgetId: String): ManagedWidgetLeaseResult

    fun completeRun(
        lease: ManagedWidgetRunLease,
        completion: ManagedWidgetRunCompletion,
    ): ManagedWidgetCommitResult

    fun replacePresentation(cache: WidgetPresentationCache)

    fun cachedPresentation(widgetId: String): WidgetPresentationCache?

    fun recordRun(run: NewWidgetRun): WidgetRunRecord

    fun listRuns(widgetId: String): List<WidgetRunRecord>

    fun appendObservations(
        widgetId: String,
        observations: List<NewWidgetObservation>,
    )

    fun listObservations(widgetId: String): List<WidgetObservation>

    fun nearestObservation(
        widgetId: String,
        name: String,
        targetTimestampMillis: Long,
        toleranceMillis: Long,
    ): WidgetObservation?

    fun setEnabled(
        widgetId: String,
        enabled: Boolean,
    ): ManagedWidgetDefinition

    fun delete(widgetId: String)
}

internal interface ManagedWidgetRepository {
    suspend fun listDefinitions(): List<ManagedWidgetDefinition>

    suspend fun createConfirmed(candidate: ConfirmedWidgetRevision): ManagedWidgetDefinition

    suspend fun replaceActiveRevision(
        widgetId: String,
        candidate: ConfirmedWidgetRevision,
    ): ManagedWidgetDefinition

    suspend fun listRevisions(widgetId: String): List<WidgetProgramRevision>

    suspend fun activeRevision(widgetId: String): WidgetProgramRevision?

    suspend fun acquireRunLease(widgetId: String): ManagedWidgetLeaseResult

    suspend fun completeRun(
        lease: ManagedWidgetRunLease,
        completion: ManagedWidgetRunCompletion,
    ): ManagedWidgetCommitResult

    suspend fun replacePresentation(cache: WidgetPresentationCache)

    suspend fun cachedPresentation(widgetId: String): WidgetPresentationCache?

    suspend fun recordRun(run: NewWidgetRun): WidgetRunRecord

    suspend fun listRuns(widgetId: String): List<WidgetRunRecord>

    suspend fun appendObservations(
        widgetId: String,
        observations: List<NewWidgetObservation>,
    )

    suspend fun listObservations(widgetId: String): List<WidgetObservation>

    suspend fun nearestObservation(
        widgetId: String,
        name: String,
        targetTimestampMillis: Long,
        toleranceMillis: Long,
    ): WidgetObservation?

    suspend fun setEnabled(
        widgetId: String,
        enabled: Boolean,
    ): ManagedWidgetDefinition

    suspend fun delete(widgetId: String)
}

internal class DispatcherManagedWidgetRepository(
    private val store: ManagedWidgetStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ManagedWidgetRepository {
    override suspend fun listDefinitions(): List<ManagedWidgetDefinition> = databaseCall(store::listDefinitions)

    override suspend fun createConfirmed(
        candidate: ConfirmedWidgetRevision,
    ): ManagedWidgetDefinition = databaseCall { store.createConfirmed(candidate) }

    override suspend fun replaceActiveRevision(
        widgetId: String,
        candidate: ConfirmedWidgetRevision,
    ): ManagedWidgetDefinition = databaseCall {
        store.replaceActiveRevision(widgetId, candidate)
    }

    override suspend fun listRevisions(
        widgetId: String,
    ): List<WidgetProgramRevision> = databaseCall { store.listRevisions(widgetId) }

    override suspend fun activeRevision(
        widgetId: String,
    ): WidgetProgramRevision? = databaseCall { store.activeRevision(widgetId) }

    override suspend fun acquireRunLease(widgetId: String): ManagedWidgetLeaseResult = databaseCall {
        store.acquireRunLease(widgetId)
    }

    override suspend fun completeRun(
        lease: ManagedWidgetRunLease,
        completion: ManagedWidgetRunCompletion,
    ): ManagedWidgetCommitResult = databaseCall { store.completeRun(lease, completion) }

    override suspend fun replacePresentation(cache: WidgetPresentationCache): Unit = databaseCall {
        store.replacePresentation(cache)
    }

    override suspend fun cachedPresentation(
        widgetId: String,
    ): WidgetPresentationCache? = databaseCall { store.cachedPresentation(widgetId) }

    override suspend fun recordRun(run: NewWidgetRun): WidgetRunRecord = databaseCall { store.recordRun(run) }

    override suspend fun listRuns(widgetId: String): List<WidgetRunRecord> = databaseCall { store.listRuns(widgetId) }

    override suspend fun appendObservations(
        widgetId: String,
        observations: List<NewWidgetObservation>,
    ): Unit = databaseCall {
        store.appendObservations(widgetId, observations)
    }

    override suspend fun listObservations(
        widgetId: String,
    ): List<WidgetObservation> = databaseCall { store.listObservations(widgetId) }

    override suspend fun nearestObservation(
        widgetId: String,
        name: String,
        targetTimestampMillis: Long,
        toleranceMillis: Long,
    ): WidgetObservation? = databaseCall {
        store.nearestObservation(widgetId, name, targetTimestampMillis, toleranceMillis)
    }

    override suspend fun setEnabled(
        widgetId: String,
        enabled: Boolean,
    ): ManagedWidgetDefinition = databaseCall {
        store.setEnabled(widgetId, enabled)
    }

    override suspend fun delete(widgetId: String): Unit = databaseCall {
        store.delete(widgetId)
    }

    private suspend fun <T> databaseCall(block: () -> T): T = withContext(dispatcher) { block() }
}

@Suppress("LargeClass")
internal class SqliteManagedWidgetRepository(
    context: Context,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : SQLiteOpenHelper(
    context,
    MANAGED_WIDGET_DATABASE_NAME,
    null,
    MANAGED_WIDGET_DATABASE_VERSION,
),
    ManagedWidgetStore {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createDefinitionTables(db)
        createPresentationTable(db)
        createHistoryTables(db)
        createObservationTable(db)
        createRevisionGuardsAndIndexes(db)
    }

    private fun createDefinitionTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE managed_widgets(
                id TEXT PRIMARY KEY,
                display_name TEXT NOT NULL CHECK(length(display_name) BETWEEN 1 AND $MAX_DISPLAY_NAME_CHARS),
                enabled INTEGER NOT NULL CHECK(enabled IN (0, 1)),
                periodic_interval_hours INTEGER,
                active_revision INTEGER NOT NULL CHECK(active_revision > 0),
                consent_digest TEXT NOT NULL CHECK(length(consent_digest) = 64),
                status TEXT NOT NULL,
                created_at_millis INTEGER NOT NULL,
                updated_at_millis INTEGER NOT NULL,
                last_attempt_at_millis INTEGER,
                last_success_at_millis INTEGER,
                active_run_id TEXT,
                active_run_started_at_millis INTEGER,
                CHECK((active_run_id IS NULL) = (active_run_started_at_millis IS NULL)),
                FOREIGN KEY(id, active_revision)
                    REFERENCES widget_program_revisions(widget_id, revision)
                    DEFERRABLE INITIALLY DEFERRED
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE widget_program_revisions(
                widget_id TEXT NOT NULL,
                revision INTEGER NOT NULL CHECK(revision > 0),
                manifest_json TEXT NOT NULL,
                source TEXT NOT NULL,
                program_digest TEXT NOT NULL CHECK(length(program_digest) = 64),
                created_at_millis INTEGER NOT NULL,
                PRIMARY KEY(widget_id, revision),
                FOREIGN KEY(widget_id) REFERENCES managed_widgets(id) ON DELETE CASCADE
                    DEFERRABLE INITIALLY DEFERRED
            )
            """.trimIndent(),
        )
    }

    private fun createPresentationTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE widget_presentations(
                widget_id TEXT PRIMARY KEY,
                revision INTEGER NOT NULL,
                presentation_json TEXT NOT NULL,
                completed_at_millis INTEGER NOT NULL,
                FOREIGN KEY(widget_id, revision)
                    REFERENCES widget_program_revisions(widget_id, revision) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
    }

    private fun createHistoryTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE widget_runs(
                id TEXT PRIMARY KEY,
                widget_id TEXT NOT NULL,
                revision INTEGER NOT NULL,
                started_at_millis INTEGER NOT NULL,
                completed_at_millis INTEGER,
                duration_millis INTEGER,
                planned_tool_ids TEXT NOT NULL,
                outcome_code TEXT NOT NULL,
                diagnostic TEXT,
                FOREIGN KEY(widget_id, revision)
                    REFERENCES widget_program_revisions(widget_id, revision) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
    }

    private fun createObservationTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE widget_observations(
                widget_id TEXT NOT NULL,
                name TEXT NOT NULL,
                observed_at_millis INTEGER NOT NULL,
                value_type TEXT NOT NULL,
                value_text TEXT NOT NULL,
                PRIMARY KEY(widget_id, name, observed_at_millis),
                FOREIGN KEY(widget_id) REFERENCES managed_widgets(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
    }

    private fun createRevisionGuardsAndIndexes(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TRIGGER immutable_widget_program_revisions
            BEFORE UPDATE ON widget_program_revisions
            BEGIN
                SELECT RAISE(ABORT, 'widget program revisions are immutable');
            END
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX widget_runs_owner_time ON widget_runs(widget_id, started_at_millis DESC)")
        db.execSQL(
            "CREATE INDEX widget_observations_owner_name_time " +
                "ON widget_observations(widget_id, name, observed_at_millis DESC)",
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ): Unit = throw ManagedWidgetPersistenceException(
        "Unsupported managed-widget database upgrade: $oldVersion to $newVersion",
    )

    @Synchronized
    override fun listDefinitions(): List<ManagedWidgetDefinition> {
        readableDatabase.rawQuery(
            """
            SELECT w.id, w.display_name, w.enabled, w.periodic_interval_hours,
                   w.active_revision, w.consent_digest, w.status,
                   w.created_at_millis, w.updated_at_millis,
                   w.last_attempt_at_millis, w.last_success_at_millis, r.revision
            FROM managed_widgets w
            LEFT JOIN widget_program_revisions r
              ON r.widget_id = w.id AND r.revision = w.active_revision
            ORDER BY w.created_at_millis ASC, w.id ASC
            """.trimIndent(),
            emptyArray(),
        ).use { cursor ->
            val definitions = mutableListOf<ManagedWidgetDefinition>()
            while (cursor.moveToNext()) {
                if (cursor.isNull(11)) {
                    throw ManagedWidgetPersistenceException(
                        "Managed widget has a missing active revision: ${cursor.getString(0)}",
                    )
                }
                definitions += cursor.toDefinition()
            }
            return definitions
        }
    }

    @Synchronized
    override fun createConfirmed(candidate: ConfirmedWidgetRevision): ManagedWidgetDefinition {
        require(listDefinitions().size < ManagedWidgetPolicy.MAX_WIDGETS) { "Managed widget limit reached" }
        val id = newId()
        require(WIDGET_ID_PATTERN.matches(id)) { "Invalid managed widget id" }
        val now = nowMillis()
        writableDatabase.inTransaction {
            insertOrThrow(
                "managed_widgets",
                null,
                candidate.definitionValues(id, revision = 1, createdAtMillis = now),
            )
            insertOrThrow(
                "widget_program_revisions",
                null,
                candidate.revisionValues(id, revision = 1, createdAtMillis = now),
            )
        }
        return definition(id)
    }

    @Synchronized
    override fun replaceActiveRevision(
        widgetId: String,
        candidate: ConfirmedWidgetRevision,
    ): ManagedWidgetDefinition {
        val current = definition(widgetId)
        val revisions = listRevisions(widgetId)
        val nextRevision = (revisions.maxOfOrNull(WidgetProgramRevision::revision) ?: 0) + 1
        val now = nowMillis()
        writableDatabase.inTransaction {
            insertOrThrow(
                "widget_program_revisions",
                null,
                candidate.revisionValues(widgetId, nextRevision, now),
            )
            val updated = update(
                "managed_widgets",
                candidate.definitionValues(widgetId, nextRevision, current.createdAtMillis),
                "id = ? AND active_revision = ?",
                arrayOf(widgetId, current.activeRevision.toString()),
            )
            if (updated != 1) {
                throw ManagedWidgetPersistenceException("Managed widget changed during revision activation")
            }
            pruneRevisions(this, widgetId)
        }
        return definition(widgetId)
    }

    @Synchronized
    override fun listRevisions(widgetId: String): List<WidgetProgramRevision> {
        readableDatabase.rawQuery(
            """
            SELECT widget_id, revision, manifest_json, source, program_digest, created_at_millis
            FROM widget_program_revisions
            WHERE widget_id = ?
            ORDER BY revision ASC
            """.trimIndent(),
            arrayOf(widgetId),
        ).use { cursor ->
            val revisions = mutableListOf<WidgetProgramRevision>()
            while (cursor.moveToNext()) revisions += cursor.toRevision()
            return revisions
        }
    }

    @Synchronized
    override fun activeRevision(widgetId: String): WidgetProgramRevision? {
        val active = listDefinitions().firstOrNull { it.id == widgetId }?.activeRevision ?: return null
        return listRevisions(widgetId).firstOrNull { it.revision == active }
            ?: throw ManagedWidgetPersistenceException("Managed widget active revision disappeared: $widgetId")
    }

    @Synchronized
    @Suppress("LongMethod")
    override fun acquireRunLease(widgetId: String): ManagedWidgetLeaseResult {
        requireValidWidgetId(widgetId)
        val now = nowMillis()
        var result: ManagedWidgetLeaseResult = ManagedWidgetLeaseResult.Missing
        writableDatabase.inTransaction {
            rawQuery(
                """
                SELECT enabled, active_revision, consent_digest, active_run_id,
                       active_run_started_at_millis
                FROM managed_widgets
                WHERE id = ?
                """.trimIndent(),
                arrayOf(widgetId),
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@inTransaction
                if (cursor.getInt(0) != 1) {
                    result = ManagedWidgetLeaseResult.Disabled
                    return@inTransaction
                }
                val activeRunId = if (cursor.isNull(3)) null else cursor.getString(3)
                val activeRunStarted = if (cursor.isNull(4)) null else cursor.getLong(4)
                if (
                    activeRunId != null &&
                    activeRunStarted != null &&
                    now - activeRunStarted < ManagedWidgetPolicy.MAX_EXECUTION_LEASE_MILLIS
                ) {
                    result = ManagedWidgetLeaseResult.Busy
                    return@inTransaction
                }
                if (activeRunId != null) expireRunLease(this, widgetId, activeRunId, now)

                val revision = cursor.getInt(1)
                val consentDigest = cursor.getString(2)
                val program = revision(this, widgetId, revision)
                    ?: throw ManagedWidgetPersistenceException("Managed widget active revision disappeared: $widgetId")
                val runId = newId().also(::requireValidWidgetId)
                insertOrThrow(
                    "widget_runs",
                    null,
                    NewWidgetRun(
                        widgetId = widgetId,
                        revision = revision,
                        startedAtMillis = now,
                        completedAtMillis = null,
                        plannedToolIds = emptyList(),
                        outcome = WidgetRunOutcome.InProgress,
                        diagnostic = null,
                    ).toContentValues(runId),
                )
                val updated = update(
                    "managed_widgets",
                    ContentValues().apply {
                        put("active_run_id", runId)
                        put("active_run_started_at_millis", now)
                        put("last_attempt_at_millis", now)
                    },
                    "id = ? AND enabled = 1 AND active_revision = ? AND consent_digest = ? " +
                        "AND active_run_id IS NULL",
                    arrayOf(widgetId, revision.toString(), consentDigest),
                )
                if (updated != 1) throw ManagedWidgetPersistenceException("Could not acquire widget run lease")
                result = ManagedWidgetLeaseResult.Acquired(
                    ManagedWidgetRunLease(runId, widgetId, revision, consentDigest, now, program),
                )
            }
        }
        return result
    }

    @Synchronized
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override fun completeRun(
        lease: ManagedWidgetRunLease,
        completion: ManagedWidgetRunCompletion,
    ): ManagedWidgetCommitResult {
        require(completion.completedAtMillis >= lease.startedAtMillis)
        var result = ManagedWidgetCommitResult.Discarded
        writableDatabase.inTransaction {
            rawQuery(
                """
                SELECT enabled, active_revision, consent_digest, active_run_id
                FROM managed_widgets
                WHERE id = ?
                """.trimIndent(),
                arrayOf(lease.widgetId),
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@inTransaction
                val ownsLease = !cursor.isNull(3) && cursor.getString(3) == lease.runId
                if (!ownsLease) return@inTransaction
                val stillCurrent = cursor.getInt(0) == 1 &&
                    cursor.getInt(1) == lease.revision &&
                    cursor.getString(2) == lease.consentDigest
                if (!stillCurrent) {
                    finishRunRow(
                        this,
                        lease,
                        completion.copy(
                            outcome = WidgetRunOutcome.Discarded,
                            diagnostic = null,
                            presentationJson = null,
                            observations = emptyList(),
                            disableExecution = false,
                        ),
                    )
                    clearLease(this, lease.widgetId, lease.runId)
                    pruneRuns(this, lease.widgetId)
                    return@inTransaction
                }

                finishRunRow(this, lease, completion)
                if (completion.presentationJson != null) {
                    insertWithOnConflict(
                        "widget_presentations",
                        null,
                        ContentValues().apply {
                            put("widget_id", lease.widgetId)
                            put("revision", lease.revision)
                            put("presentation_json", completion.presentationJson)
                            put("completed_at_millis", completion.completedAtMillis)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE,
                    ).also { rowId ->
                        if (rowId == -1L) {
                            throw ManagedWidgetPersistenceException("Could not replace widget presentation")
                        }
                    }
                }
                completion.observations.forEach { observation ->
                    insertOrThrow("widget_observations", null, observation.toContentValues(lease.widgetId))
                }
                update(
                    "managed_widgets",
                    ContentValues().apply {
                        put(
                            "status",
                            if (completion.outcome == WidgetRunOutcome.Success) {
                                ManagedWidgetStatus.Ready.name
                            } else {
                                ManagedWidgetStatus.NeedsAttention.name
                            },
                        )
                        if (completion.disableExecution) put("enabled", 0)
                        put("last_attempt_at_millis", completion.completedAtMillis)
                        if (completion.outcome == WidgetRunOutcome.Success) {
                            put("last_success_at_millis", completion.completedAtMillis)
                        }
                        putNull("active_run_id")
                        putNull("active_run_started_at_millis")
                        put("updated_at_millis", completion.completedAtMillis)
                    },
                    "id = ? AND active_run_id = ?",
                    arrayOf(lease.widgetId, lease.runId),
                ).also { updated ->
                    if (updated != 1) throw ManagedWidgetPersistenceException("Widget run lease changed at commit")
                }
                pruneRuns(this, lease.widgetId)
                pruneObservations(this, lease.widgetId)
                result = ManagedWidgetCommitResult.Stored
            }
        }
        return result
    }

    @Synchronized
    override fun replacePresentation(cache: WidgetPresentationCache) {
        writableDatabase.insertWithOnConflict(
            "widget_presentations",
            null,
            ContentValues().apply {
                put("widget_id", cache.widgetId)
                put("revision", cache.revision)
                put("presentation_json", cache.presentationJson)
                put("completed_at_millis", cache.completedAtMillis)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        ).also { rowId ->
            if (rowId == -1L) throw ManagedWidgetPersistenceException("Could not replace widget presentation")
        }
    }

    @Synchronized
    override fun cachedPresentation(widgetId: String): WidgetPresentationCache? {
        requireValidWidgetId(widgetId)
        readableDatabase.rawQuery(
            """
            SELECT widget_id, revision, presentation_json, completed_at_millis
            FROM widget_presentations
            WHERE widget_id = ?
            """.trimIndent(),
            arrayOf(widgetId),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return try {
                WidgetPresentationCache(
                    widgetId = cursor.getString(0),
                    revision = cursor.getInt(1),
                    presentationJson = cursor.getString(2),
                    completedAtMillis = cursor.getLong(3),
                )
            } catch (_: IllegalArgumentException) {
                throw ManagedWidgetPersistenceException("Invalid cached presentation row")
            }
        }
    }

    @Synchronized
    override fun recordRun(run: NewWidgetRun): WidgetRunRecord {
        val record = WidgetRunRecord(
            id = newId().also(::requireValidWidgetId),
            widgetId = run.widgetId,
            revision = run.revision,
            startedAtMillis = run.startedAtMillis,
            completedAtMillis = run.completedAtMillis,
            durationMillis = run.completedAtMillis?.minus(run.startedAtMillis),
            plannedToolIds = run.plannedToolIds,
            outcome = run.outcome,
            diagnostic = run.diagnostic,
        )
        writableDatabase.inTransaction {
            insertOrThrow("widget_runs", null, record.toContentValues())
            pruneRuns(this, run.widgetId)
        }
        return record
    }

    @Synchronized
    override fun listRuns(widgetId: String): List<WidgetRunRecord> {
        requireValidWidgetId(widgetId)
        readableDatabase.rawQuery(
            """
            SELECT id, widget_id, revision, started_at_millis, completed_at_millis,
                   duration_millis, planned_tool_ids, outcome_code, diagnostic
            FROM widget_runs
            WHERE widget_id = ?
            ORDER BY started_at_millis DESC, id DESC
            """.trimIndent(),
            arrayOf(widgetId),
        ).use { cursor ->
            val runs = mutableListOf<WidgetRunRecord>()
            while (cursor.moveToNext()) runs += cursor.toRunRecord()
            return runs
        }
    }

    @Synchronized
    override fun appendObservations(
        widgetId: String,
        observations: List<NewWidgetObservation>,
    ) {
        requireValidWidgetId(widgetId)
        if (observations.isEmpty()) return
        writableDatabase.inTransaction {
            observations.forEach { observation ->
                insertOrThrow("widget_observations", null, observation.toContentValues(widgetId))
            }
            pruneObservations(this, widgetId)
        }
    }

    @Synchronized
    override fun listObservations(widgetId: String): List<WidgetObservation> {
        requireValidWidgetId(widgetId)
        readableDatabase.rawQuery(
            """
            SELECT widget_id, name, observed_at_millis, value_type, value_text
            FROM widget_observations
            WHERE widget_id = ?
            ORDER BY observed_at_millis DESC, name ASC
            """.trimIndent(),
            arrayOf(widgetId),
        ).use { cursor ->
            val observations = mutableListOf<WidgetObservation>()
            while (cursor.moveToNext()) observations += cursor.toObservation()
            return observations
        }
    }

    @Synchronized
    override fun nearestObservation(
        widgetId: String,
        name: String,
        targetTimestampMillis: Long,
        toleranceMillis: Long,
    ): WidgetObservation? {
        requireValidWidgetId(widgetId)
        require(targetTimestampMillis >= 0 && toleranceMillis >= 0)
        require(OBSERVATION_NAME_PATTERN.matches(name))
        readableDatabase.rawQuery(
            """
            SELECT widget_id, name, observed_at_millis, value_type, value_text
            FROM widget_observations
            WHERE widget_id = ? AND name = ?
              AND ABS(observed_at_millis - CAST(? AS INTEGER)) <= CAST(? AS INTEGER)
            ORDER BY ABS(observed_at_millis - CAST(? AS INTEGER)) ASC, observed_at_millis DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(
                widgetId,
                name,
                targetTimestampMillis.toString(),
                toleranceMillis.toString(),
                targetTimestampMillis.toString(),
            ),
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toObservation() else null
        }
    }

    @Synchronized
    override fun setEnabled(
        widgetId: String,
        enabled: Boolean,
    ): ManagedWidgetDefinition {
        requireValidWidgetId(widgetId)
        val updated = writableDatabase.update(
            "managed_widgets",
            ContentValues().apply {
                put("enabled", if (enabled) 1 else 0)
                put("updated_at_millis", nowMillis())
            },
            "id = ?",
            arrayOf(widgetId),
        )
        if (updated != 1) throw NoSuchElementException("Managed widget does not exist: $widgetId")
        return definition(widgetId)
    }

    @Synchronized
    override fun delete(widgetId: String) {
        requireValidWidgetId(widgetId)
        writableDatabase.inTransaction {
            delete("managed_widgets", "id = ?", arrayOf(widgetId))
        }
    }

    private fun definition(widgetId: String): ManagedWidgetDefinition = listDefinitions()
        .firstOrNull { it.id == widgetId }
        ?: throw NoSuchElementException("Managed widget does not exist: $widgetId")

    private fun revision(
        database: SQLiteDatabase,
        widgetId: String,
        revision: Int,
    ): WidgetProgramRevision? = database.rawQuery(
        """
        SELECT widget_id, revision, manifest_json, source, program_digest, created_at_millis
        FROM widget_program_revisions
        WHERE widget_id = ? AND revision = ?
        """.trimIndent(),
        arrayOf(widgetId, revision.toString()),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.toRevision() else null }

    private fun expireRunLease(
        database: SQLiteDatabase,
        widgetId: String,
        runId: String,
        completedAtMillis: Long,
    ) {
        database.update(
            "widget_runs",
            ContentValues().apply {
                put("completed_at_millis", completedAtMillis)
                put("duration_millis", 0L.coerceAtLeast(completedAtMillis - runStartedAt(database, runId)))
                put("outcome_code", WidgetRunOutcome.ControlledFailure.name)
                put("diagnostic", WidgetRunDiagnostic.LeaseExpired.name)
            },
            "id = ? AND widget_id = ? AND outcome_code = ?",
            arrayOf(runId, widgetId, WidgetRunOutcome.InProgress.name),
        )
        clearLease(database, widgetId, runId)
    }

    private fun runStartedAt(
        database: SQLiteDatabase,
        runId: String,
    ): Long = database.rawQuery(
        "SELECT started_at_millis FROM widget_runs WHERE id = ?",
        arrayOf(runId),
    ).use { cursor ->
        if (!cursor.moveToFirst()) throw ManagedWidgetPersistenceException("Active widget run disappeared")
        cursor.getLong(0)
    }

    private fun finishRunRow(
        database: SQLiteDatabase,
        lease: ManagedWidgetRunLease,
        completion: ManagedWidgetRunCompletion,
    ) {
        database.update(
            "widget_runs",
            ContentValues().apply {
                put("completed_at_millis", completion.completedAtMillis)
                put("duration_millis", completion.completedAtMillis - lease.startedAtMillis)
                put("planned_tool_ids", completion.plannedToolIds.toJson())
                put("outcome_code", completion.outcome.name)
                if (completion.diagnostic == null) {
                    putNull("diagnostic")
                } else {
                    put("diagnostic", completion.diagnostic.name)
                }
            },
            "id = ? AND widget_id = ? AND outcome_code = ?",
            arrayOf(lease.runId, lease.widgetId, WidgetRunOutcome.InProgress.name),
        ).also { updated ->
            if (updated != 1) throw ManagedWidgetPersistenceException("Widget run attempt disappeared")
        }
    }

    private fun clearLease(
        database: SQLiteDatabase,
        widgetId: String,
        runId: String,
    ) {
        database.update(
            "managed_widgets",
            ContentValues().apply {
                putNull("active_run_id")
                putNull("active_run_started_at_millis")
            },
            "id = ? AND active_run_id = ?",
            arrayOf(widgetId, runId),
        )
    }

    private fun pruneRevisions(
        database: SQLiteDatabase,
        widgetId: String,
    ) {
        database.execSQL(
            """
            DELETE FROM widget_program_revisions
            WHERE widget_id = ? AND revision IN (
                SELECT revision FROM widget_program_revisions
                WHERE widget_id = ?
                ORDER BY revision DESC
                LIMIT -1 OFFSET ${ManagedWidgetPolicy.MAX_REVISIONS_PER_WIDGET}
            )
            """.trimIndent(),
            arrayOf(widgetId, widgetId),
        )
    }

    private fun pruneRuns(
        database: SQLiteDatabase,
        widgetId: String,
    ) {
        val cutoff = nowMillis() - ManagedWidgetPolicy.MAX_RUN_AGE_DAYS * MILLIS_PER_DAY
        database.delete(
            "widget_runs",
            "widget_id = ? AND started_at_millis < ?",
            arrayOf(widgetId, cutoff.toString()),
        )
        database.execSQL(
            """
            DELETE FROM widget_runs
            WHERE id IN (
                SELECT id FROM widget_runs
                WHERE widget_id = ?
                ORDER BY started_at_millis DESC, id DESC
                LIMIT -1 OFFSET ${ManagedWidgetPolicy.MAX_RUNS_PER_WIDGET}
            )
            """.trimIndent(),
            arrayOf(widgetId),
        )
    }

    private fun pruneObservations(
        database: SQLiteDatabase,
        widgetId: String,
    ) {
        val cutoff = nowMillis() - ManagedWidgetPolicy.MAX_OBSERVATION_AGE_DAYS * MILLIS_PER_DAY
        database.delete(
            "widget_observations",
            "widget_id = ? AND observed_at_millis < ?",
            arrayOf(widgetId, cutoff.toString()),
        )
        database.execSQL(
            """
            DELETE FROM widget_observations
            WHERE rowid IN (
                SELECT rowid FROM widget_observations
                WHERE widget_id = ?
                ORDER BY observed_at_millis DESC, name ASC
                LIMIT -1 OFFSET ${ManagedWidgetPolicy.MAX_OBSERVATIONS_PER_WIDGET}
            )
            """.trimIndent(),
            arrayOf(widgetId),
        )
    }

    private fun ConfirmedWidgetRevision.definitionValues(
        widgetId: String,
        revision: Int,
        createdAtMillis: Long,
    ): ContentValues = ContentValues().apply {
        put("id", widgetId)
        put("display_name", displayName.trim())
        put("enabled", if (enabled) 1 else 0)
        if (periodicIntervalHours == null) {
            putNull("periodic_interval_hours")
        } else {
            put("periodic_interval_hours", periodicIntervalHours)
        }
        put("active_revision", revision)
        put("consent_digest", consentDigest)
        put("status", ManagedWidgetStatus.Ready.name)
        put("created_at_millis", createdAtMillis)
        put("updated_at_millis", nowMillis())
    }

    private fun ConfirmedWidgetRevision.revisionValues(
        widgetId: String,
        revision: Int,
        createdAtMillis: Long,
    ): ContentValues = ContentValues().apply {
        put("widget_id", widgetId)
        put("revision", revision)
        put("manifest_json", manifestJson)
        put("source", source)
        put("program_digest", programDigest)
        put("created_at_millis", createdAtMillis)
    }

    private fun WidgetRunRecord.toContentValues(): ContentValues = NewWidgetRun(
        widgetId = widgetId,
        revision = revision,
        startedAtMillis = startedAtMillis,
        completedAtMillis = completedAtMillis,
        plannedToolIds = plannedToolIds,
        outcome = outcome,
        diagnostic = diagnostic,
    ).toContentValues(id)

    private fun NewWidgetRun.toContentValues(runId: String): ContentValues = ContentValues().apply {
        put("id", runId)
        put("widget_id", widgetId)
        put("revision", revision)
        put("started_at_millis", startedAtMillis)
        if (completedAtMillis == null) {
            putNull("completed_at_millis")
        } else {
            put("completed_at_millis", completedAtMillis)
        }
        val durationMillis = completedAtMillis?.minus(startedAtMillis)
        if (durationMillis == null) putNull("duration_millis") else put("duration_millis", durationMillis)
        put("planned_tool_ids", plannedToolIds.toJson())
        put("outcome_code", outcome.name)
        if (diagnostic == null) putNull("diagnostic") else put("diagnostic", diagnostic.name)
    }

    private fun NewWidgetObservation.toContentValues(widgetId: String): ContentValues {
        val (type, text) = observationValueColumns(value)
        return ContentValues().apply {
            put("widget_id", widgetId)
            put("name", name)
            put("observed_at_millis", observedAtMillis)
            put("value_type", type)
            put("value_text", text)
        }
    }

    private fun Cursor.toDefinition(): ManagedWidgetDefinition = try {
        ManagedWidgetDefinition(
            id = getString(0),
            displayName = getString(1).also { require(it.isNotBlank() && it.length <= MAX_DISPLAY_NAME_CHARS) },
            enabled = getInt(2).let { value ->
                require(value == 0 || value == 1)
                value == 1
            },
            periodicIntervalHours = if (isNull(3)) {
                null
            } else {
                getLong(3).also {
                    require(it in ManagedWidgetPolicy.SUPPORTED_PERIODIC_INTERVAL_HOURS)
                }
            },
            activeRevision = getInt(4).also { require(it > 0) },
            consentDigest = getString(5).also { require(SHA_256_PATTERN.matches(it)) },
            status = ManagedWidgetStatus.valueOf(getString(6)),
            createdAtMillis = getLong(7),
            updatedAtMillis = getLong(8),
            lastAttemptAtMillis = if (isNull(9)) null else getLong(9),
            lastSuccessAtMillis = if (isNull(10)) null else getLong(10),
        )
    } catch (_: IllegalArgumentException) {
        throw ManagedWidgetPersistenceException("Invalid managed widget row: ${getString(0)}")
    }

    private fun Cursor.toRevision(): WidgetProgramRevision = try {
        WidgetProgramRevision(
            widgetId = getString(0),
            revision = getInt(1).also { require(it > 0) },
            manifestJson = getString(2).also {
                require(it.utf8Size() <= WidgetRuntimePolicy.MAX_MANIFEST_BYTES)
            },
            source = getString(3).also {
                require(it.utf8Size() <= ManagedWidgetPolicy.MAX_PROGRAM_SOURCE_BYTES)
            },
            programDigest = getString(4).also { require(SHA_256_PATTERN.matches(it)) },
            createdAtMillis = getLong(5),
        )
    } catch (_: IllegalArgumentException) {
        throw ManagedWidgetPersistenceException("Invalid managed widget revision row")
    }

    private fun Cursor.toRunRecord(): WidgetRunRecord = try {
        val completed = if (isNull(4)) null else getLong(4)
        val decoded = NewWidgetRun(
            widgetId = getString(1),
            revision = getInt(2),
            startedAtMillis = getLong(3),
            completedAtMillis = completed,
            plannedToolIds = getString(6).decodeToolIds(),
            outcome = WidgetRunOutcome.valueOf(getString(7)),
            diagnostic = if (isNull(8)) null else WidgetRunDiagnostic.valueOf(getString(8)),
        )
        WidgetRunRecord(
            id = getString(0).also(::requireValidWidgetId),
            widgetId = decoded.widgetId,
            revision = decoded.revision,
            startedAtMillis = decoded.startedAtMillis,
            completedAtMillis = decoded.completedAtMillis,
            durationMillis = if (isNull(5)) {
                null
            } else {
                getLong(5).also { duration ->
                    require(duration == completed?.minus(decoded.startedAtMillis))
                }
            },
            plannedToolIds = decoded.plannedToolIds,
            outcome = decoded.outcome,
            diagnostic = decoded.diagnostic,
        )
    } catch (_: IllegalArgumentException) {
        throw ManagedWidgetPersistenceException("Invalid managed widget run row")
    }

    private fun Cursor.toObservation(): WidgetObservation = try {
        WidgetObservation(
            widgetId = getString(0).also(::requireValidWidgetId),
            name = getString(1),
            observedAtMillis = getLong(2),
            value = decodeObservationValue(getString(3), getString(4)),
        ).also {
            NewWidgetObservation(it.name, it.observedAtMillis, it.value)
        }
    } catch (_: IllegalArgumentException) {
        throw ManagedWidgetPersistenceException("Invalid managed widget observation row")
    }
}

private inline fun <T> SQLiteDatabase.inTransaction(block: SQLiteDatabase.() -> T): T {
    beginTransaction()
    try {
        val result = block()
        setTransactionSuccessful()
        return result
    } finally {
        endTransaction()
    }
}

internal fun String.utf8Size(): Int = toByteArray(StandardCharsets.UTF_8).size

private fun List<String>.toJson(): String = JsonArray().also { array -> forEach(array::add) }.toString()

private fun String.decodeToolIds(): List<String> {
    val parsed = JsonParser.parseString(this)
    require(parsed.isJsonArray)
    return parsed.asJsonArray.map { element ->
        require(element.isJsonPrimitive && element.asJsonPrimitive.isString)
        element.asString
    }
}

private fun observationValueColumns(value: WidgetObservationValue): Pair<String, String> = when (value) {
    is WidgetObservationValue.Text -> "text" to value.value
    is WidgetObservationValue.Number -> "number" to value.value
    is WidgetObservationValue.BooleanValue -> "boolean" to value.value.toString()
}

private fun decodeObservationValue(
    type: String,
    text: String,
): WidgetObservationValue = when (type) {
    "text" -> WidgetObservationValue.Text(text)
    "number" -> WidgetObservationValue.Number(text)
    "boolean" -> WidgetObservationValue.BooleanValue(
        when (text) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("Invalid boolean observation")
        },
    )
    else -> throw IllegalArgumentException("Invalid observation type")
}

private const val MAX_DISPLAY_NAME_CHARS = 80
private const val MILLIS_PER_DAY = 86_400_000L
private val SHA_256_PATTERN = Regex("[a-f0-9]{64}")
