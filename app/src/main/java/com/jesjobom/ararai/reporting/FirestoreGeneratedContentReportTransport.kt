package com.jesjobom.ararai.reporting

import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Date
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val REPORT_COLLECTION = "generated_content_reports"
private const val REPORT_RETENTION_DAYS = 90L
private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L

class FirestoreGeneratedContentReportTransport(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : GeneratedContentReportTransport {
    override suspend fun submit(payload: GeneratedContentReportPayload): ReportDeliveryResult = try {
        val user = auth.currentUser ?: auth.signInAnonymously().await().user
            ?: return ReportDeliveryResult.TransientFailure
        val documentId = "${user.uid}_${payload.reportId}"
        val document = firestore.collection(REPORT_COLLECTION).document(documentId)
        try {
            document.set(payload.toFirestoreDocument(user.uid)).await()
        } catch (error: FirebaseFirestoreException) {
            if (error.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw error
            val existing = document.get().await()
            check(
                existing.exists() &&
                    existing.getString("ownerUid") == user.uid &&
                    existing.getString("reportId") == payload.reportId,
            ) { "The existing report does not match the idempotency key" }
        }
        ReportDeliveryResult.Accepted(documentId)
    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
        error.toDeliveryResult()
    }
}

private fun GeneratedContentReportPayload.toFirestoreDocument(ownerUid: String): Map<String, Any?> = mapOf(
    "schemaVersion" to schemaVersion,
    "reportId" to reportId,
    "ownerUid" to ownerUid,
    "reportedResponse" to reportedResponse,
    "reason" to reason.name,
    "comment" to comment,
    "context" to context.map { item ->
        mapOf(
            "role" to item.role.name,
            "text" to item.text,
        )
    },
    "media" to mapOf(
        "image" to mediaPresence.image,
        "audio" to mediaPresence.audio,
        "transcript" to mediaPresence.transcript,
    ),
    "metadata" to mapOf(
        "appVersion" to metadata.appVersion,
        "localeTag" to metadata.localeTag,
        "modelId" to metadata.modelId,
        "runtime" to metadata.runtime,
    ),
    "reportedAt" to Timestamp(Date(reportedAtEpochMillis)),
    "createdAt" to FieldValue.serverTimestamp(),
    "expiresAt" to Timestamp(Date(reportedAtEpochMillis + REPORT_RETENTION_DAYS * MILLIS_PER_DAY)),
)

private fun Exception.toDeliveryResult(): ReportDeliveryResult = when (this) {
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.ABORTED,
        FirebaseFirestoreException.Code.ALREADY_EXISTS,
        FirebaseFirestoreException.Code.CANCELLED,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        FirebaseFirestoreException.Code.INTERNAL,
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.UNKNOWN,
        -> ReportDeliveryResult.TransientFailure

        else -> ReportDeliveryResult.PermanentFailure
    }
    is FirebaseException -> ReportDeliveryResult.TransientFailure
    is IllegalStateException -> ReportDeliveryResult.PermanentFailure
    else -> ReportDeliveryResult.TransientFailure
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { completed ->
        if (completed.isSuccessful) {
            continuation.resume(completed.result)
        } else {
            continuation.resumeWithException(
                completed.exception ?: IllegalStateException("Firebase task failed without an exception"),
            )
        }
    }
}
