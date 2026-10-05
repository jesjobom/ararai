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
internal const val MANAGED_WIDGET_DATABASE_VERSION = 2

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

internal interface WidgetAuthoringWorkflowStore {
    fun createAuthoringSession(request: NewWidgetAuthoringSession): WidgetAuthoringSessionSnapshot

    fun activeAuthoringSession(): WidgetAuthoringSessionSnapshot?

    fun authoringSession(sessionId: String): WidgetAuthoringSessionSnapshot?

    fun startAuthoringAttempt(command: StartWidgetAuthoringAttempt): StartWidgetAuthoringAttemptResult

    fun markAuthoringAttemptDeferred(
        sessionId: String,
        attemptId: String,
    ): WidgetAuthoringSessionSnapshot

    fun markAuthoringAttemptRunning(
        sessionId: String,
        attemptId: String,
    ): WidgetAuthoringSessionSnapshot

    fun completeAuthoringAttempt(command: CompleteWidgetAuthoringAttempt): WidgetAuthoringSessionSnapshot

    fun acceptAuthoringCandidate(command: AcceptWidgetAuthoringCandidate): WidgetAuthoringMutationResult

    fun reconcileInterruptedAuthoring(): WidgetAuthoringSessionSnapshot?

    fun finalizeAuthoringSession(
        sessionId: String,
        expectedSessionRevision: Int,
        candidate: ConfirmedWidgetRevision,
    ): FinalizeWidgetAuthoringResult

    fun discardAuthoringSession(
        sessionId: String,
        expectedSessionRevision: Int,
    ): Boolean
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
    ManagedWidgetStore,
    WidgetAuthoringWorkflowStore {
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
        createAuthoringTables(db)
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

    @Suppress("LongMethod")
    private fun createAuthoringTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE widget_authoring_sessions(
                id TEXT PRIMARY KEY,
                revision INTEGER NOT NULL CHECK(revision > 0),
                status TEXT NOT NULL,
                instruction TEXT NOT NULL
                    CHECK(length(CAST(instruction AS BLOB)) BETWEEN 1 AND ${WidgetAuthoringWorkflowPolicy.MAX_INSTRUCTION_BYTES}),
                target_widget_id TEXT,
                model_id TEXT NOT NULL,
                model_artifact_digest TEXT NOT NULL CHECK(length(model_artifact_digest) = 64),
                inference_config_json TEXT NOT NULL
                    CHECK(length(CAST(inference_config_json AS BLOB)) <= ${WidgetAuthoringWorkflowPolicy.MAX_INFERENCE_CONFIG_BYTES}),
                protocol_version INTEGER NOT NULL CHECK(protocol_version > 0),
                schema_version INTEGER NOT NULL CHECK(schema_version > 0),
                tool_contract_digest TEXT NOT NULL CHECK(length(tool_contract_digest) = 64),
                current_stage_key TEXT NOT NULL
                    CHECK(length(current_stage_key) BETWEEN 1 AND ${WidgetAuthoringWorkflowPolicy.MAX_STAGE_KEY_CHARS}),
                active_attempt_id TEXT,
                created_at_millis INTEGER NOT NULL,
                updated_at_millis INTEGER NOT NULL,
                FOREIGN KEY(active_attempt_id) REFERENCES widget_authoring_attempts(id)
                    DEFERRABLE INITIALLY DEFERRED
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE widget_authoring_attempts(
                id TEXT PRIMARY KEY,
                session_id TEXT NOT NULL,
                action_id TEXT NOT NULL
                    CHECK(length(action_id) BETWEEN 1 AND ${WidgetAuthoringWorkflowPolicy.MAX_ACTION_ID_CHARS}),
                stage_key TEXT NOT NULL
                    CHECK(length(stage_key) BETWEEN 1 AND ${WidgetAuthoringWorkflowPolicy.MAX_STAGE_KEY_CHARS}),
                stage_revision INTEGER NOT NULL CHECK(stage_revision > 0),
                attempt_number INTEGER NOT NULL CHECK(attempt_number > 0),
                kind TEXT NOT NULL,
                status TEXT NOT NULL,
                artifact TEXT CHECK(
                    artifact IS NULL OR
                    length(CAST(artifact AS BLOB)) <= ${WidgetAuthoringPipelinePolicy.MAX_ARTIFACT_BYTES}
                ),
                artifact_digest TEXT CHECK(artifact_digest IS NULL OR length(artifact_digest) = 64),
                upstream_digest TEXT NOT NULL CHECK(length(upstream_digest) = 64),
                failure_code TEXT,
                started_at_millis INTEGER,
                completed_at_millis INTEGER,
                FOREIGN KEY(session_id) REFERENCES widget_authoring_sessions(id) ON DELETE CASCADE,
                UNIQUE(session_id, action_id),
                UNIQUE(session_id, stage_key, stage_revision, attempt_number),
                CHECK((artifact IS NULL) = (artifact_digest IS NULL)),
                CHECK(completed_at_millis IS NULL OR started_at_millis IS NOT NULL)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE widget_authoring_checkpoints(
                session_id TEXT NOT NULL,
                stage_key TEXT NOT NULL,
                stage_revision INTEGER NOT NULL CHECK(stage_revision > 0),
                accepted_attempt_id TEXT NOT NULL,
                artifact TEXT NOT NULL
                    CHECK(length(CAST(artifact AS BLOB)) <= ${WidgetAuthoringPipelinePolicy.MAX_ARTIFACT_BYTES}),
                artifact_digest TEXT NOT NULL CHECK(length(artifact_digest) = 64),
                upstream_digest TEXT NOT NULL CHECK(length(upstream_digest) = 64),
                status TEXT NOT NULL,
                accepted_at_millis INTEGER NOT NULL,
                PRIMARY KEY(session_id, stage_key),
                FOREIGN KEY(session_id) REFERENCES widget_authoring_sessions(id) ON DELETE CASCADE,
                FOREIGN KEY(accepted_attempt_id) REFERENCES widget_authoring_attempts(id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE widget_authoring_actions(
                session_id TEXT NOT NULL,
                action_id TEXT NOT NULL,
                action_kind TEXT NOT NULL,
                resulting_session_revision INTEGER NOT NULL CHECK(resulting_session_revision > 0),
                created_at_millis INTEGER NOT NULL,
                PRIMARY KEY(session_id, action_id),
                FOREIGN KEY(session_id) REFERENCES widget_authoring_sessions(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX one_active_widget_authoring_session
            ON widget_authoring_sessions((1))
            WHERE status != 'Completed'
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX one_active_widget_authoring_attempt
            ON widget_authoring_attempts(session_id)
            WHERE status IN ('Queued', 'Deferred', 'Running')
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX widget_authoring_attempt_history
            ON widget_authoring_attempts(session_id, stage_key, stage_revision, attempt_number)
            """.trimIndent(),
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        if (oldVersion == 1 && newVersion == 2) {
            createAuthoringTables(db)
            return
        }
        throw ManagedWidgetPersistenceException(
            "Unsupported managed-widget database upgrade: $oldVersion to $newVersion",
        )
    }

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

    @Synchronized
    override fun createAuthoringSession(request: NewWidgetAuthoringSession): WidgetAuthoringSessionSnapshot {
        activeAuthoringSession()?.let {
            throw ManagedWidgetPersistenceException("A widget authoring session is already active")
        }
        val sessionId = newId().also(::requireValidWidgetId)
        val now = nowMillis()
        writableDatabase.insertOrThrow(
            "widget_authoring_sessions",
            null,
            ContentValues().apply {
                put("id", sessionId)
                put("revision", 1)
                put("status", WidgetAuthoringSessionStatus.AwaitingStage.name)
                put("instruction", request.instruction)
                if (request.targetWidgetId == null) {
                    putNull("target_widget_id")
                } else {
                    put("target_widget_id", request.targetWidgetId)
                }
                put("model_id", request.modelId)
                put("model_artifact_digest", request.modelArtifactDigest)
                put("inference_config_json", request.inferenceConfigJson)
                put("protocol_version", request.protocolVersion)
                put("schema_version", request.schemaVersion)
                put("tool_contract_digest", request.toolContractDigest)
                put("current_stage_key", WidgetAuthoringStageKey.Feasibility.wireValue)
                putNull("active_attempt_id")
                put("created_at_millis", now)
                put("updated_at_millis", now)
            },
        )
        return requireNotNull(authoringSession(sessionId))
    }

    @Synchronized
    override fun activeAuthoringSession(): WidgetAuthoringSessionSnapshot? {
        readableDatabase.rawQuery(
            """
            SELECT id FROM widget_authoring_sessions
            WHERE status != ?
            ORDER BY created_at_millis ASC, id ASC
            """.trimIndent(),
            arrayOf(WidgetAuthoringSessionStatus.Completed.name),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val sessionId = cursor.getString(0)
            if (cursor.moveToNext()) {
                throw ManagedWidgetPersistenceException("Multiple active widget authoring sessions")
            }
            return requireNotNull(authoringSession(sessionId))
        }
    }

    @Synchronized
    override fun authoringSession(sessionId: String): WidgetAuthoringSessionSnapshot? {
        requireValidWidgetId(sessionId)
        val session = readableDatabase.rawQuery(
            """
            SELECT id, revision, status, instruction, target_widget_id, model_id,
                   model_artifact_digest, inference_config_json, protocol_version,
                   schema_version, tool_contract_digest, current_stage_key,
                   active_attempt_id, created_at_millis, updated_at_millis
            FROM widget_authoring_sessions
            WHERE id = ?
            """.trimIndent(),
            arrayOf(sessionId),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            cursor.toAuthoringSession()
        }
        val attempts = readableDatabase.rawQuery(
            """
            SELECT id, session_id, action_id, stage_key, stage_revision,
                   attempt_number, kind, status, artifact, artifact_digest,
                   upstream_digest, failure_code, started_at_millis,
                   completed_at_millis
            FROM widget_authoring_attempts
            WHERE session_id = ?
            ORDER BY rowid ASC
            """.trimIndent(),
            arrayOf(sessionId),
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toAuthoringAttempt()) } }
        val checkpoints = readableDatabase.rawQuery(
            """
            SELECT session_id, stage_key, stage_revision, accepted_attempt_id,
                   artifact, artifact_digest, upstream_digest, status,
                   accepted_at_millis
            FROM widget_authoring_checkpoints
            WHERE session_id = ?
            ORDER BY rowid ASC
            """.trimIndent(),
            arrayOf(sessionId),
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toAuthoringCheckpoint()) } }
        return WidgetAuthoringSessionSnapshot(session, attempts, checkpoints)
    }

    @Synchronized
    @Suppress("LongMethod", "ReturnCount")
    override fun startAuthoringAttempt(command: StartWidgetAuthoringAttempt): StartWidgetAuthoringAttemptResult {
        requireValidWidgetId(command.sessionId)
        existingAttemptForAction(command.sessionId, command.actionId)?.let { existing ->
            return StartWidgetAuthoringAttemptResult.Duplicate(
                existing,
                requireNotNull(authoringSession(command.sessionId)).session.revision,
            )
        }
        val snapshot = requireNotNull(authoringSession(command.sessionId))
        if (snapshot.session.revision != command.expectedSessionRevision) {
            return StartWidgetAuthoringAttemptResult.Stale(snapshot.session.revision)
        }
        require(snapshot.session.activeAttemptId == null) { "An authoring attempt is already active" }
        WidgetAuthoringWorkflowStateMachine.requireAllowed(
            snapshot,
            when (command.kind) {
                WidgetAuthoringAttemptKind.Initial -> WidgetAuthoringUserAction.Start
                WidgetAuthoringAttemptKind.Repair -> WidgetAuthoringUserAction.Retry
                WidgetAuthoringAttemptKind.Reprocess -> WidgetAuthoringUserAction.Reprocess
            },
        )
        val coordinates = nextAttemptCoordinates(snapshot, command)
        val attemptId = newId().also(::requireValidWidgetId)
        val nextRevision = snapshot.session.revision + 1
        val now = nowMillis()
        writableDatabase.inTransaction {
            insertOrThrow(
                "widget_authoring_attempts",
                null,
                ContentValues().apply {
                    put("id", attemptId)
                    put("session_id", command.sessionId)
                    put("action_id", command.actionId)
                    put("stage_key", command.stage.wireValue)
                    put("stage_revision", coordinates.first)
                    put("attempt_number", coordinates.second)
                    put("kind", command.kind.name)
                    put("status", WidgetAuthoringAttemptStatus.Queued.name)
                    putNull("artifact")
                    putNull("artifact_digest")
                    put("upstream_digest", command.upstreamDigest)
                    putNull("failure_code")
                    putNull("started_at_millis")
                    putNull("completed_at_millis")
                },
            )
            recordAuthoringAction(this, command.sessionId, command.actionId, "start", nextRevision, now)
            updateAuthoringSession(
                database = this,
                sessionId = command.sessionId,
                expectedRevision = snapshot.session.revision,
                nextRevision = nextRevision,
                status = WidgetAuthoringSessionStatus.AttemptQueued,
                currentStage = command.stage,
                activeAttemptId = attemptId,
                now = now,
            )
        }
        val attempt = requireNotNull(existingAttemptForAction(command.sessionId, command.actionId))
        return StartWidgetAuthoringAttemptResult.Started(attempt, nextRevision)
    }

    @Synchronized
    override fun markAuthoringAttemptRunning(
        sessionId: String,
        attemptId: String,
    ): WidgetAuthoringSessionSnapshot {
        val snapshot = requireNotNull(authoringSession(sessionId))
        require(snapshot.session.activeAttemptId == attemptId)
        val attempt = snapshot.attempts.single { it.id == attemptId }
        require(attempt.status in setOf(WidgetAuthoringAttemptStatus.Queued, WidgetAuthoringAttemptStatus.Deferred))
        val now = nowMillis()
        val nextRevision = snapshot.session.revision + 1
        writableDatabase.inTransaction {
            update(
                "widget_authoring_attempts",
                ContentValues().apply {
                    put("status", WidgetAuthoringAttemptStatus.Running.name)
                    put("started_at_millis", now)
                },
                "id = ? AND session_id = ? AND status IN (?, ?)",
                arrayOf(
                    attemptId,
                    sessionId,
                    WidgetAuthoringAttemptStatus.Queued.name,
                    WidgetAuthoringAttemptStatus.Deferred.name,
                ),
            ).also { if (it != 1) throw ManagedWidgetPersistenceException("Authoring attempt changed") }
            updateAuthoringSession(
                this,
                sessionId,
                snapshot.session.revision,
                nextRevision,
                WidgetAuthoringSessionStatus.AttemptRunning,
                attempt.stage,
                attemptId,
                now,
            )
        }
        return requireNotNull(authoringSession(sessionId))
    }

    @Synchronized
    override fun markAuthoringAttemptDeferred(
        sessionId: String,
        attemptId: String,
    ): WidgetAuthoringSessionSnapshot {
        val snapshot = requireNotNull(authoringSession(sessionId))
        require(snapshot.session.activeAttemptId == attemptId)
        val attempt = snapshot.attempts.single { it.id == attemptId }
        require(attempt.status == WidgetAuthoringAttemptStatus.Queued)
        val now = nowMillis()
        val nextRevision = snapshot.session.revision + 1
        writableDatabase.inTransaction {
            update(
                "widget_authoring_attempts",
                ContentValues().apply { put("status", WidgetAuthoringAttemptStatus.Deferred.name) },
                "id = ? AND session_id = ? AND status = ?",
                arrayOf(attemptId, sessionId, WidgetAuthoringAttemptStatus.Queued.name),
            ).also { if (it != 1) throw ManagedWidgetPersistenceException("Authoring attempt changed") }
            updateAuthoringSession(
                this,
                sessionId,
                snapshot.session.revision,
                nextRevision,
                WidgetAuthoringSessionStatus.AttemptDeferred,
                attempt.stage,
                attemptId,
                now,
            )
        }
        return requireNotNull(authoringSession(sessionId))
    }

    @Synchronized
    override fun completeAuthoringAttempt(command: CompleteWidgetAuthoringAttempt): WidgetAuthoringSessionSnapshot {
        val snapshot = requireNotNull(authoringSession(command.sessionId))
        require(snapshot.session.activeAttemptId == command.attemptId)
        val attempt = snapshot.attempts.single { it.id == command.attemptId }
        require(attempt.status in ACTIVE_ATTEMPT_STATUSES)
        val now = nowMillis()
        val nextRevision = snapshot.session.revision + 1
        val artifactDigest = command.artifact?.let(::authoringDigest)
        val nextStatus = if (command.status == WidgetAuthoringAttemptStatus.Succeeded) {
            WidgetAuthoringSessionStatus.AwaitingReview
        } else {
            WidgetAuthoringSessionStatus.AwaitingRetry
        }
        writableDatabase.inTransaction {
            update(
                "widget_authoring_attempts",
                ContentValues().apply {
                    put("status", command.status.name)
                    if (command.artifact == null) putNull("artifact") else put("artifact", command.artifact)
                    if (artifactDigest == null) putNull("artifact_digest") else put("artifact_digest", artifactDigest)
                    if (command.failureCode == null) {
                        putNull("failure_code")
                    } else {
                        put("failure_code", command.failureCode.name)
                    }
                    if (attempt.startedAtMillis == null) put("started_at_millis", now)
                    put("completed_at_millis", now)
                },
                "id = ? AND session_id = ? AND status IN (?, ?, ?)",
                arrayOf(
                    command.attemptId,
                    command.sessionId,
                    WidgetAuthoringAttemptStatus.Queued.name,
                    WidgetAuthoringAttemptStatus.Deferred.name,
                    WidgetAuthoringAttemptStatus.Running.name,
                ),
            ).also { if (it != 1) throw ManagedWidgetPersistenceException("Authoring attempt changed") }
            updateAuthoringSession(
                this,
                command.sessionId,
                snapshot.session.revision,
                nextRevision,
                nextStatus,
                attempt.stage,
                null,
                now,
            )
        }
        return requireNotNull(authoringSession(command.sessionId))
    }

    @Synchronized
    @Suppress("LongMethod", "ReturnCount")
    override fun acceptAuthoringCandidate(command: AcceptWidgetAuthoringCandidate): WidgetAuthoringMutationResult {
        require(command.actionId.isNotBlank())
        authoringActionRevision(command.sessionId, command.actionId)?.let {
            return WidgetAuthoringMutationResult.Duplicate(requireNotNull(authoringSession(command.sessionId)))
        }
        val snapshot = requireNotNull(authoringSession(command.sessionId))
        if (snapshot.session.revision != command.expectedSessionRevision) {
            return WidgetAuthoringMutationResult.Stale(snapshot)
        }
        WidgetAuthoringWorkflowStateMachine.requireAllowed(snapshot, WidgetAuthoringUserAction.Continue)
        require(snapshot.session.status == WidgetAuthoringSessionStatus.AwaitingReview)
        require(snapshot.session.activeAttemptId == null)
        val attempt = snapshot.attempts.single { it.id == command.attemptId }
        require(attempt.status == WidgetAuthoringAttemptStatus.Succeeded)
        require(attempt.stage == snapshot.session.currentStage)
        requireValidAuthoringStageTransition(attempt.stage, command.nextStage)
        val artifact = requireNotNull(attempt.artifact)
        val artifactDigest = requireNotNull(attempt.artifactDigest)
        val now = nowMillis()
        val nextRevision = snapshot.session.revision + 1
        writableDatabase.inTransaction {
            insertWithOnConflict(
                "widget_authoring_checkpoints",
                null,
                ContentValues().apply {
                    put("session_id", command.sessionId)
                    put("stage_key", attempt.stage.wireValue)
                    put("stage_revision", attempt.stageRevision)
                    put("accepted_attempt_id", attempt.id)
                    put("artifact", artifact)
                    put("artifact_digest", artifactDigest)
                    put("upstream_digest", attempt.upstreamDigest)
                    put("status", WidgetAuthoringCheckpointStatus.Accepted.name)
                    put("accepted_at_millis", now)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            ).also { if (it == -1L) throw ManagedWidgetPersistenceException("Could not accept checkpoint") }
            authoringDescendants(snapshot, attempt.stage).forEach { descendant ->
                update(
                    "widget_authoring_checkpoints",
                    ContentValues().apply { put("status", WidgetAuthoringCheckpointStatus.Stale.name) },
                    "session_id = ? AND stage_key = ?",
                    arrayOf(command.sessionId, descendant.wireValue),
                )
            }
            recordAuthoringAction(this, command.sessionId, command.actionId, "accept", nextRevision, now)
            updateAuthoringSession(
                this,
                command.sessionId,
                snapshot.session.revision,
                nextRevision,
                if (command.nextStage == null) {
                    WidgetAuthoringSessionStatus.ReadyForPreview
                } else {
                    WidgetAuthoringSessionStatus.AwaitingStage
                },
                command.nextStage ?: WidgetAuthoringStageKey.Assembly,
                null,
                now,
            )
        }
        return WidgetAuthoringMutationResult.Applied(requireNotNull(authoringSession(command.sessionId)))
    }

    @Synchronized
    @Suppress("ReturnCount")
    override fun reconcileInterruptedAuthoring(): WidgetAuthoringSessionSnapshot? {
        val snapshot = activeAuthoringSession() ?: return null
        val attemptId = snapshot.session.activeAttemptId ?: return snapshot
        val attempt = snapshot.attempts.single { it.id == attemptId }
        if (attempt.status !in ACTIVE_ATTEMPT_STATUSES) {
            throw ManagedWidgetPersistenceException("Session points to a terminal authoring attempt")
        }
        return completeAuthoringAttempt(
            CompleteWidgetAuthoringAttempt(
                sessionId = snapshot.session.id,
                attemptId = attemptId,
                status = WidgetAuthoringAttemptStatus.Interrupted,
                artifact = null,
                failureCode = WidgetAuthoringStageFailureCode.RuntimeUnavailable,
            ),
        )
    }

    @Synchronized
    @Suppress("LongMethod", "ReturnCount")
    override fun finalizeAuthoringSession(
        sessionId: String,
        expectedSessionRevision: Int,
        candidate: ConfirmedWidgetRevision,
    ): FinalizeWidgetAuthoringResult {
        val snapshot = authoringSession(sessionId) ?: return FinalizeWidgetAuthoringResult.Missing
        if (snapshot.session.revision != expectedSessionRevision) {
            return FinalizeWidgetAuthoringResult.Stale(snapshot)
        }
        WidgetAuthoringWorkflowStateMachine.requireAllowed(snapshot, WidgetAuthoringUserAction.Confirm)
        requireFinalizable(snapshot)
        val now = nowMillis()
        val target = finalizationTarget(snapshot, now)
        writableDatabase.inTransaction {
            if (target.expectedActiveRevision == null) {
                insertOrThrow(
                    "managed_widgets",
                    null,
                    candidate.definitionValues(target.widgetId, target.nextRevision, target.createdAtMillis),
                )
            }
            insertOrThrow(
                "widget_program_revisions",
                null,
                candidate.revisionValues(target.widgetId, target.nextRevision, now),
            )
            if (target.expectedActiveRevision != null) {
                update(
                    "managed_widgets",
                    candidate.definitionValues(target.widgetId, target.nextRevision, target.createdAtMillis),
                    "id = ? AND active_revision = ?",
                    arrayOf(target.widgetId, target.expectedActiveRevision.toString()),
                ).also {
                    if (it != 1) throw ManagedWidgetPersistenceException("Managed widget changed during finalization")
                }
                pruneRevisions(this, target.widgetId)
            }
            delete(
                "widget_authoring_sessions",
                "id = ? AND revision = ? AND active_attempt_id IS NULL",
                arrayOf(sessionId, expectedSessionRevision.toString()),
            ).also {
                if (it != 1) throw ManagedWidgetPersistenceException("Widget authoring session changed")
            }
        }
        return FinalizeWidgetAuthoringResult.Finalized(definition(target.widgetId))
    }

    private fun requireFinalizable(snapshot: WidgetAuthoringSessionSnapshot) {
        val hasCompleteGraph = WidgetAuthoringWorkflowStateMachine.run { snapshot.hasCompleteAcceptedGraph() }
        require(snapshot.session.activeAttemptId == null)
        require(hasCompleteGraph)
        require(
            snapshot.session.status == WidgetAuthoringSessionStatus.ReadyForPreview ||
                snapshot.session.status == WidgetAuthoringSessionStatus.AwaitingRetry,
        )
    }

    private fun finalizationTarget(
        snapshot: WidgetAuthoringSessionSnapshot,
        now: Long,
    ): WidgetAuthoringFinalizationTarget {
        val targetWidgetId = snapshot.session.targetWidgetId
        if (targetWidgetId == null) {
            require(listDefinitions().size < ManagedWidgetPolicy.MAX_WIDGETS) { "Managed widget limit reached" }
            return WidgetAuthoringFinalizationTarget(
                widgetId = newId().also(::requireValidWidgetId),
                nextRevision = 1,
                createdAtMillis = now,
                expectedActiveRevision = null,
            )
        }
        val current = definition(targetWidgetId)
        return WidgetAuthoringFinalizationTarget(
            widgetId = targetWidgetId,
            nextRevision = (listRevisions(targetWidgetId).maxOfOrNull(WidgetProgramRevision::revision) ?: 0) + 1,
            createdAtMillis = current.createdAtMillis,
            expectedActiveRevision = current.activeRevision,
        )
    }

    @Synchronized
    @Suppress("ReturnCount")
    override fun discardAuthoringSession(
        sessionId: String,
        expectedSessionRevision: Int,
    ): Boolean {
        val snapshot = authoringSession(sessionId) ?: return false
        if (snapshot.session.revision != expectedSessionRevision) return false
        WidgetAuthoringWorkflowStateMachine.requireAllowed(snapshot, WidgetAuthoringUserAction.Discard)
        require(snapshot.session.activeAttemptId == null) { "Cannot discard an active authoring attempt" }
        return writableDatabase.delete(
            "widget_authoring_sessions",
            "id = ? AND revision = ? AND active_attempt_id IS NULL",
            arrayOf(sessionId, expectedSessionRevision.toString()),
        ) == 1
    }

    private fun nextAttemptCoordinates(
        snapshot: WidgetAuthoringSessionSnapshot,
        command: StartWidgetAuthoringAttempt,
    ): Pair<Int, Int> {
        val attempts = snapshot.attempts.filter { it.stage == command.stage }
        val checkpoint = snapshot.checkpoints.singleOrNull { it.stage == command.stage }
        return when (command.kind) {
            WidgetAuthoringAttemptKind.Initial -> {
                require(snapshot.session.status == WidgetAuthoringSessionStatus.AwaitingStage)
                require(snapshot.session.currentStage == command.stage)
                val nextStageRevision = maxOf(
                    checkpoint?.stageRevision ?: 0,
                    attempts.maxOfOrNull(WidgetAuthoringAttempt::stageRevision) ?: 0,
                ) + 1
                nextStageRevision to 1
            }
            WidgetAuthoringAttemptKind.Repair -> {
                require(snapshot.session.status == WidgetAuthoringSessionStatus.AwaitingRetry)
                require(snapshot.session.currentStage == command.stage)
                val preceding = attempts.maxWithOrNull(
                    compareBy<WidgetAuthoringAttempt> { it.stageRevision }.thenBy { it.attemptNumber },
                ) ?: throw IllegalArgumentException("A repair requires a preceding attempt")
                require(preceding.status != WidgetAuthoringAttemptStatus.Succeeded)
                require(preceding.attemptNumber <= WidgetAuthoringWorkflowPolicy.MAX_REPAIRS_PER_STAGE_REVISION)
                preceding.stageRevision to preceding.attemptNumber + 1
            }
            WidgetAuthoringAttemptKind.Reprocess -> {
                require(
                    snapshot.session.status in setOf(
                        WidgetAuthoringSessionStatus.AwaitingStage,
                        WidgetAuthoringSessionStatus.AwaitingRetry,
                        WidgetAuthoringSessionStatus.ReadyForPreview,
                    ),
                )
                require(checkpoint?.status == WidgetAuthoringCheckpointStatus.Accepted)
                val nextStageRevision = maxOf(
                    checkpoint.stageRevision,
                    attempts.maxOfOrNull(WidgetAuthoringAttempt::stageRevision) ?: 0,
                ) + 1
                nextStageRevision to 1
            }
        }
    }

    private fun authoringDescendants(
        snapshot: WidgetAuthoringSessionSnapshot,
        stage: WidgetAuthoringStageKey,
    ): Set<WidgetAuthoringStageKey> {
        val callStages = (
            snapshot.checkpoints.map(WidgetAuthoringCheckpoint::stage) +
                snapshot.attempts.map(WidgetAuthoringAttempt::stage)
            )
            .filterIsInstance<WidgetAuthoringStageKey.CallFunction>()
            .distinctBy(WidgetAuthoringStageKey.CallFunction::stepId)
        return when (stage) {
            WidgetAuthoringStageKey.Feasibility -> buildSet {
                add(WidgetAuthoringStageKey.Algorithm)
                addAll(callStages)
                add(WidgetAuthoringStageKey.Plan)
                add(WidgetAuthoringStageKey.Render)
                add(WidgetAuthoringStageKey.Assembly)
            }
            WidgetAuthoringStageKey.Algorithm -> buildSet {
                addAll(callStages)
                add(WidgetAuthoringStageKey.Plan)
                add(WidgetAuthoringStageKey.Render)
                add(WidgetAuthoringStageKey.Assembly)
            }
            is WidgetAuthoringStageKey.CallFunction -> setOf(
                WidgetAuthoringStageKey.Plan,
                WidgetAuthoringStageKey.Render,
                WidgetAuthoringStageKey.Assembly,
            )
            WidgetAuthoringStageKey.Plan -> setOf(
                WidgetAuthoringStageKey.Render,
                WidgetAuthoringStageKey.Assembly,
            )
            WidgetAuthoringStageKey.Render -> setOf(WidgetAuthoringStageKey.Assembly)
            WidgetAuthoringStageKey.Assembly -> emptySet()
        }
    }

    private fun existingAttemptForAction(
        sessionId: String,
        actionId: String,
    ): WidgetAuthoringAttempt? = readableDatabase.rawQuery(
        """
        SELECT id, session_id, action_id, stage_key, stage_revision,
               attempt_number, kind, status, artifact, artifact_digest,
               upstream_digest, failure_code, started_at_millis,
               completed_at_millis
        FROM widget_authoring_attempts
        WHERE session_id = ? AND action_id = ?
        """.trimIndent(),
        arrayOf(sessionId, actionId),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.toAuthoringAttempt() else null }

    private fun authoringActionRevision(
        sessionId: String,
        actionId: String,
    ): Int? = readableDatabase.rawQuery(
        """
        SELECT resulting_session_revision
        FROM widget_authoring_actions
        WHERE session_id = ? AND action_id = ?
        """.trimIndent(),
        arrayOf(sessionId, actionId),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else null }

    private fun recordAuthoringAction(
        database: SQLiteDatabase,
        sessionId: String,
        actionId: String,
        kind: String,
        resultingRevision: Int,
        now: Long,
    ) {
        database.insertOrThrow(
            "widget_authoring_actions",
            null,
            ContentValues().apply {
                put("session_id", sessionId)
                put("action_id", actionId)
                put("action_kind", kind)
                put("resulting_session_revision", resultingRevision)
                put("created_at_millis", now)
            },
        )
    }

    @Suppress("LongParameterList")
    private fun updateAuthoringSession(
        database: SQLiteDatabase,
        sessionId: String,
        expectedRevision: Int,
        nextRevision: Int,
        status: WidgetAuthoringSessionStatus,
        currentStage: WidgetAuthoringStageKey,
        activeAttemptId: String?,
        now: Long,
    ) {
        database.update(
            "widget_authoring_sessions",
            ContentValues().apply {
                put("revision", nextRevision)
                put("status", status.name)
                put("current_stage_key", currentStage.wireValue)
                if (activeAttemptId == null) {
                    putNull("active_attempt_id")
                } else {
                    put(
                        "active_attempt_id",
                        activeAttemptId,
                    )
                }
                put("updated_at_millis", now)
            },
            "id = ? AND revision = ?",
            arrayOf(sessionId, expectedRevision.toString()),
        ).also {
            if (it != 1) throw ManagedWidgetPersistenceException("Widget authoring session changed")
        }
    }

    private fun Cursor.toAuthoringSession(): WidgetAuthoringSession = try {
        WidgetAuthoringSession(
            id = getString(0).also(::requireValidWidgetId),
            revision = getInt(1).also { require(it > 0) },
            status = WidgetAuthoringSessionStatus.valueOf(getString(2)),
            instruction = getString(3),
            targetWidgetId = if (isNull(4)) null else getString(4).also(::requireValidWidgetId),
            modelId = getString(5),
            modelArtifactDigest = getString(6).also { require(SHA_256_PATTERN.matches(it)) },
            inferenceConfigJson = getString(7),
            protocolVersion = getInt(8).also { require(it > 0) },
            schemaVersion = getInt(9).also { require(it > 0) },
            toolContractDigest = getString(10).also { require(SHA_256_PATTERN.matches(it)) },
            currentStage = WidgetAuthoringStageKey.parse(getString(11)),
            activeAttemptId = if (isNull(12)) null else getString(12).also(::requireValidWidgetId),
            createdAtMillis = getLong(13),
            updatedAtMillis = getLong(14),
        )
    } catch (_: IllegalArgumentException) {
        throw ManagedWidgetPersistenceException("Invalid widget authoring session row")
    }

    private fun Cursor.toAuthoringAttempt(): WidgetAuthoringAttempt = try {
        WidgetAuthoringAttempt(
            id = getString(0).also(::requireValidWidgetId),
            sessionId = getString(1).also(::requireValidWidgetId),
            actionId = getString(2),
            stage = WidgetAuthoringStageKey.parse(getString(3)),
            stageRevision = getInt(4).also { require(it > 0) },
            attemptNumber = getInt(5).also { require(it > 0) },
            kind = WidgetAuthoringAttemptKind.valueOf(getString(6)),
            status = WidgetAuthoringAttemptStatus.valueOf(getString(7)),
            artifact = if (isNull(8)) null else getString(8),
            artifactDigest = if (isNull(9)) {
                null
            } else {
                getString(9).also {
                    require(SHA_256_PATTERN.matches(it))
                }
            },
            upstreamDigest = getString(10).also { require(SHA_256_PATTERN.matches(it)) },
            failureCode = if (isNull(11)) null else WidgetAuthoringStageFailureCode.valueOf(getString(11)),
            startedAtMillis = if (isNull(12)) null else getLong(12),
            completedAtMillis = if (isNull(13)) null else getLong(13),
        )
    } catch (_: IllegalArgumentException) {
        throw ManagedWidgetPersistenceException("Invalid widget authoring attempt row")
    }

    private fun Cursor.toAuthoringCheckpoint(): WidgetAuthoringCheckpoint = try {
        WidgetAuthoringCheckpoint(
            sessionId = getString(0).also(::requireValidWidgetId),
            stage = WidgetAuthoringStageKey.parse(getString(1)),
            stageRevision = getInt(2).also { require(it > 0) },
            acceptedAttemptId = getString(3).also(::requireValidWidgetId),
            artifact = getString(4),
            artifactDigest = getString(5).also { require(SHA_256_PATTERN.matches(it)) },
            upstreamDigest = getString(6).also { require(SHA_256_PATTERN.matches(it)) },
            status = WidgetAuthoringCheckpointStatus.valueOf(getString(7)),
            acceptedAtMillis = getLong(8),
        )
    } catch (_: IllegalArgumentException) {
        throw ManagedWidgetPersistenceException("Invalid widget authoring checkpoint row")
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

private data class WidgetAuthoringFinalizationTarget(
    val widgetId: String,
    val nextRevision: Int,
    val createdAtMillis: Long,
    val expectedActiveRevision: Int?,
)

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
