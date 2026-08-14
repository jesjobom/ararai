package com.jesjobom.ararai.reporting

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.jesjobom.ararai.chat.ChatRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

const val MAX_PENDING_REPORTS = 10
const val MAX_PENDING_REPORT_BYTES = 32_000
const val MAX_REPORT_ATTEMPTS = 5
const val MAX_PENDING_REPORT_AGE_MILLIS = 7L * 24L * 60L * 60L * 1_000L

enum class PendingReportStatus { Pending, Sending, PermanentFailure }

data class PendingReport(
    val payload: GeneratedContentReportPayload,
    val status: PendingReportStatus,
    val createdAtEpochMillis: Long,
    val attemptCount: Int,
    val lastAttemptAtEpochMillis: Long? = null,
)

interface PendingReportQueue {
    val revision: StateFlow<Long>
        get() = EMPTY_QUEUE_REVISION

    fun enqueue(payload: GeneratedContentReportPayload, nowEpochMillis: Long): PendingReport
    fun list(nowEpochMillis: Long): List<PendingReport>
    fun markAttempt(reportId: String, nowEpochMillis: Long): PendingReport?
    fun markPermanentFailure(reportId: String): PendingReport?
    fun delete(reportId: String): Boolean
    fun deleteExpired(nowEpochMillis: Long): Int
}

class PendingReportQueueException(message: String) : IllegalStateException(message)

@Suppress("TooManyFunctions")
class SqlitePendingReportQueue(context: Context) :
    SQLiteOpenHelper(
        context,
        DATABASE_NAME,
        null,
        DATABASE_VERSION,
    ),
    PendingReportQueue {
    private val mutableRevision = MutableStateFlow(0L)
    override val revision: StateFlow<Long> = mutableRevision

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE pending_reports(
                report_id TEXT PRIMARY KEY,
                payload TEXT NOT NULL,
                payload_bytes INTEGER NOT NULL,
                status TEXT NOT NULL,
                created_at_millis INTEGER NOT NULL,
                attempt_count INTEGER NOT NULL,
                last_attempt_at_millis INTEGER
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    @Suppress("ThrowsCount")
    override fun enqueue(payload: GeneratedContentReportPayload, nowEpochMillis: Long): PendingReport {
        deleteExpired(nowEpochMillis)
        val encoded = ReportPayloadCodec.encode(payload)
        val byteCount = encoded.toByteArray(Charsets.UTF_8).size
        if (byteCount > MAX_PENDING_REPORT_BYTES) {
            throw PendingReportQueueException("Report exceeds the local queue size limit")
        }
        if (count() >= MAX_PENDING_REPORTS && find(payload.reportId) == null) {
            throw PendingReportQueueException("The pending report queue is full")
        }
        val pending = PendingReport(payload, PendingReportStatus.Pending, nowEpochMillis, 0)
        writableDatabase.insertWithOnConflict(
            "pending_reports",
            null,
            pending.toValues(encoded, byteCount),
            SQLiteDatabase.CONFLICT_IGNORE,
        )
        mutableRevision.value += 1
        return find(payload.reportId) ?: throw PendingReportQueueException("Unable to persist report")
    }

    @Synchronized
    override fun list(nowEpochMillis: Long): List<PendingReport> {
        deleteExpired(nowEpochMillis)
        readableDatabase.rawQuery(
            """
            SELECT payload, status, created_at_millis, attempt_count, last_attempt_at_millis
            FROM pending_reports ORDER BY created_at_millis ASC, report_id ASC
            """.trimIndent(),
            emptyArray(),
        ).use { cursor ->
            val reports = mutableListOf<PendingReport>()
            while (cursor.moveToNext()) reports += cursor.toPendingReport()
            return reports
        }
    }

    @Synchronized
    @Suppress("ReturnCount")
    override fun markAttempt(reportId: String, nowEpochMillis: Long): PendingReport? {
        val current = find(reportId) ?: return null
        if (current.status == PendingReportStatus.PermanentFailure) return current
        val attempts = current.attemptCount + 1
        val status = if (attempts >= MAX_REPORT_ATTEMPTS) {
            PendingReportStatus.PermanentFailure
        } else {
            PendingReportStatus.Sending
        }
        writableDatabase.update(
            "pending_reports",
            ContentValues().apply {
                put("attempt_count", attempts)
                put("last_attempt_at_millis", nowEpochMillis)
                put("status", status.name)
            },
            "report_id = ?",
            arrayOf(reportId),
        )
        mutableRevision.value += 1
        return find(reportId)
    }

    @Synchronized
    override fun markPermanentFailure(reportId: String): PendingReport? {
        val changed = writableDatabase.update(
            "pending_reports",
            ContentValues().apply { put("status", PendingReportStatus.PermanentFailure.name) },
            "report_id = ?",
            arrayOf(reportId),
        )
        if (changed > 0) mutableRevision.value += 1
        return find(reportId)
    }

    @Synchronized
    override fun delete(reportId: String): Boolean {
        val deleted = writableDatabase.delete(
            "pending_reports",
            "report_id = ?",
            arrayOf(reportId),
        ) > 0
        if (deleted) mutableRevision.value += 1
        return deleted
    }

    @Synchronized
    override fun deleteExpired(nowEpochMillis: Long): Int {
        val deleted = writableDatabase.delete(
            "pending_reports",
            "created_at_millis < ?",
            arrayOf((nowEpochMillis - MAX_PENDING_REPORT_AGE_MILLIS).toString()),
        )
        if (deleted > 0) mutableRevision.value += 1
        return deleted
    }

    private fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM pending_reports", emptyArray()).use {
        if (it.moveToFirst()) it.getInt(0) else 0
    }

    private fun find(reportId: String): PendingReport? = readableDatabase.rawQuery(
        """
        SELECT payload, status, created_at_millis, attempt_count, last_attempt_at_millis
        FROM pending_reports WHERE report_id = ?
        """.trimIndent(),
        arrayOf(reportId),
    ).use { if (it.moveToFirst()) it.toPendingReport() else null }

    private fun PendingReport.toValues(encoded: String, byteCount: Int) = ContentValues().apply {
        put("report_id", payload.reportId)
        put("payload", encoded)
        put("payload_bytes", byteCount)
        put("status", status.name)
        put("created_at_millis", createdAtEpochMillis)
        put("attempt_count", attemptCount)
    }

    private fun android.database.Cursor.toPendingReport() = PendingReport(
        payload = ReportPayloadCodec.decode(getString(0)),
        status = PendingReportStatus.valueOf(getString(1)),
        createdAtEpochMillis = getLong(2),
        attemptCount = getInt(3),
        lastAttemptAtEpochMillis = if (isNull(4)) null else getLong(4),
    )

    private companion object {
        const val DATABASE_NAME = "ararai_pending_reports.db"
        const val DATABASE_VERSION = 1
    }
}

private val EMPTY_QUEUE_REVISION = MutableStateFlow(0L)

private object ReportPayloadCodec {
    fun encode(payload: GeneratedContentReportPayload): String = JSONObject().apply {
        put("schemaVersion", payload.schemaVersion)
        put("reportId", payload.reportId)
        put("reportedResponse", payload.reportedResponse)
        put("reason", payload.reason.name)
        put("comment", payload.comment ?: JSONObject.NULL)
        put("reportedAtEpochMillis", payload.reportedAtEpochMillis)
        put(
            "context",
            JSONArray().apply {
                payload.context.forEach { item ->
                    put(
                        JSONObject().apply {
                            put("messageId", item.messageId)
                            put("role", item.role.name)
                            put("text", item.text)
                        },
                    )
                }
            },
        )
        put(
            "media",
            JSONObject().apply {
                put("image", payload.mediaPresence.image)
                put("audio", payload.mediaPresence.audio)
                put("transcript", payload.mediaPresence.transcript)
            },
        )
        put(
            "metadata",
            JSONObject().apply {
                put("appVersion", payload.metadata.appVersion)
                put("localeTag", payload.metadata.localeTag)
                put("modelId", payload.metadata.modelId)
                put("runtime", payload.metadata.runtime)
            },
        )
    }.toString()

    fun decode(encoded: String): GeneratedContentReportPayload {
        val json = JSONObject(encoded)
        val context = json.getJSONArray("context")
        val media = json.getJSONObject("media")
        val metadata = json.getJSONObject("metadata")
        return GeneratedContentReportPayload(
            schemaVersion = json.getInt("schemaVersion"),
            reportId = json.getString("reportId"),
            reportedResponse = json.getString("reportedResponse"),
            reason = ReportReason.valueOf(json.getString("reason")),
            comment = if (json.isNull("comment")) null else json.getString("comment"),
            context = (0 until context.length()).map { index ->
                context.getJSONObject(index).let {
                    ReportContextItem(
                        messageId = it.getString("messageId"),
                        role = ChatRole.valueOf(it.getString("role")),
                        text = it.getString("text"),
                    )
                }
            },
            mediaPresence = ReportMediaPresence(
                image = media.getBoolean("image"),
                audio = media.getBoolean("audio"),
                transcript = media.getBoolean("transcript"),
            ),
            metadata = ReportTechnicalMetadata(
                appVersion = metadata.getString("appVersion"),
                localeTag = metadata.getString("localeTag"),
                modelId = metadata.getString("modelId"),
                runtime = metadata.getString("runtime"),
            ),
            reportedAtEpochMillis = json.getLong("reportedAtEpochMillis"),
        )
    }
}
