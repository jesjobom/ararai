package com.jesjobom.ararai.reporting

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlin.coroutines.resume

private const val DIAGNOSTIC_REPORT_COLLECTION = "diagnostic_error_reports"
private const val DIAGNOSTIC_REPORT_RETENTION_DAYS = 90L
private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L

class FirestoreRestDiagnosticErrorReportTransport(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val appCheck: FirebaseAppCheck = FirebaseAppCheck.getInstance(),
    private val projectIdProvider: () -> String = {
        checkNotNull(FirebaseApp.getInstance().options.projectId) {
            "Firebase project ID is unavailable"
        }
    },
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
) : DiagnosticErrorReportTransport {
    override suspend fun submit(payload: DiagnosticErrorReportPayload): Boolean {
        val user = (auth.currentUser ?: auth.signInAnonymously().awaitResult()?.user)
            ?.takeIf { it.isAnonymous }
        val authToken = user?.getIdToken(false)?.awaitResult()?.token
        val appCheckToken = appCheck.getAppCheckToken(false).awaitResult()?.token
        return if (user == null || authToken == null || appCheckToken == null) {
            false
        } else {
            withContext(Dispatchers.IO) {
                commitOnce(
                    payload = payload,
                    ownerUid = user.uid,
                    authToken = authToken,
                    appCheckToken = appCheckToken,
                )
            }
        }
    }

    private fun commitOnce(
        payload: DiagnosticErrorReportPayload,
        ownerUid: String,
        authToken: String,
        appCheckToken: String,
    ): Boolean {
        val projectId = projectIdProvider()
        val endpoint = URL(
            "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents:commit",
        )
        val connection = connectionFactory(endpoint)
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = REQUEST_TIMEOUT_MILLIS
            connection.readTimeout = REQUEST_TIMEOUT_MILLIS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Authorization", "Bearer $authToken")
            connection.setRequestProperty("X-Firebase-AppCheck", appCheckToken)
            val requestBody = payload.toFirestoreCommit(projectId, ownerUid).toString()
            connection.outputStream.use { output -> output.write(requestBody.toByteArray(Charsets.UTF_8)) }
            connection.responseCode in 200..299
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitResult(): T? {
        return suspendCancellableCoroutine { continuation ->
            addOnCompleteListener { completed ->
                if (!continuation.isActive) return@addOnCompleteListener
                if (completed.isSuccessful) {
                    continuation.resume(completed.result)
                } else {
                    continuation.resume(null)
                }
            }
        }
    }

    private companion object {
        const val REQUEST_TIMEOUT_MILLIS = 12_000
    }
}

internal fun DiagnosticErrorReportPayload.toFirestoreCommit(
    projectId: String,
    ownerUid: String,
): JSONObject {
    val documentId = "${ownerUid}_$reportId"
    val documentName =
        "projects/$projectId/databases/(default)/documents/$DIAGNOSTIC_REPORT_COLLECTION/$documentId"
    val expiresAt = reportedAtEpochMillis + DIAGNOSTIC_REPORT_RETENTION_DAYS * MILLIS_PER_DAY
    val fields = mapOf(
        "schemaVersion" to firestoreInteger(DiagnosticErrorReportPayload.SCHEMA_VERSION.toLong()),
        "reportId" to firestoreString(reportId),
        "ownerUid" to firestoreString(ownerUid),
        "category" to firestoreString(category.wireValue),
        "stage" to firestoreString(stage),
        "exceptionType" to firestoreString(exceptionType),
        "exceptionSummary" to firestoreString(exceptionSummary),
        "stackSummary" to firestoreArray(stackSummary.map(::firestoreString)),
        "metadata" to firestoreMap(
            mapOf(
                "appVersion" to firestoreString(appVersion),
                "androidApiLevel" to firestoreInteger(androidApiLevel.toLong()),
                "localeTag" to firestoreString(localeTag),
                "modelId" to firestoreNullableString(modelId),
                "runtime" to firestoreNullableString(runtime),
                "contextTokens" to (contextTokens?.let { firestoreInteger(it.toLong()) } ?: firestoreNull()),
                "reasoningEnabled" to firestoreBoolean(reasoningEnabled),
                "enabledToolNames" to firestoreArray(enabledToolNames.map(::firestoreString)),
            ),
        ),
        "reportedAt" to firestoreTimestamp(reportedAtEpochMillis),
        "expiresAt" to firestoreTimestamp(expiresAt),
    )
    val write = JSONObject()
        .put("update", JSONObject().put("name", documentName).put("fields", JSONObject(fields)))
        .put("currentDocument", JSONObject().put("exists", false))
        .put(
            "updateTransforms",
            JSONArray().put(
                JSONObject()
                    .put("fieldPath", "createdAt")
                    .put("setToServerValue", "REQUEST_TIME"),
            ),
        )
    return JSONObject().put("writes", JSONArray().put(write))
}

private fun firestoreString(value: String) = JSONObject().put("stringValue", value)
private fun firestoreInteger(value: Long) = JSONObject().put("integerValue", value.toString())
private fun firestoreBoolean(value: Boolean) = JSONObject().put("booleanValue", value)
private fun firestoreTimestamp(epochMillis: Long) = JSONObject().put(
    "timestampValue",
    Instant.ofEpochMilli(epochMillis).toString(),
)
private fun firestoreNull() = JSONObject().put("nullValue", JSONObject.NULL)
private fun firestoreNullableString(value: String?) = value?.let(::firestoreString) ?: firestoreNull()
private fun firestoreArray(values: List<JSONObject>) = JSONObject().put(
    "arrayValue",
    JSONObject().put("values", JSONArray(values)),
)
private fun firestoreMap(values: Map<String, JSONObject>) = JSONObject().put(
    "mapValue",
    JSONObject().put("fields", JSONObject(values)),
)
