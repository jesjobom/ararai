package com.jesjobom.ararai.reporting

import com.google.firebase.FirebaseException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.jesjobom.ararai.chat.ChatRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FirestoreGeneratedContentReportTransportTest {
    @Test
    fun `maps the reviewed payload to the exact Firestore schema`() {
        val payload = payload()

        val document = payload.toFirestoreDocument(OWNER_UID)

        assertEquals(
            setOf(
                "schemaVersion",
                "reportId",
                "ownerUid",
                "reportedResponse",
                "reason",
                "comment",
                "context",
                "media",
                "metadata",
                "reportedAt",
                "createdAt",
                "expiresAt",
            ),
            document.keys,
        )
        assertEquals(OWNER_UID, document["ownerUid"])
        assertEquals(payload.reportId, document["reportId"])
        assertEquals("Privacy", document["reason"])
        assertEquals(Timestamp(Date(REPORTED_AT)), document["reportedAt"])
        assertEquals(Timestamp(Date(REPORTED_AT + NINETY_DAYS)), document["expiresAt"])
        assertTrue(document["createdAt"] is FieldValue)
        assertEquals(
            listOf(mapOf("role" to "User", "text" to "selected context")),
            document["context"],
        )
        assertEquals(mapOf("image" to true, "audio" to false, "transcript" to false), document["media"])
        assertFalse(document.containsKey("imageBytes"))
        assertFalse(document.containsKey("audioBytes"))
        assertFalse(document.containsKey("reasoning"))
    }

    @Test
    fun `classifies retryable Firestore failures`() {
        val retryableCodes = listOf(
            FirebaseFirestoreException.Code.ABORTED,
            FirebaseFirestoreException.Code.ALREADY_EXISTS,
            FirebaseFirestoreException.Code.CANCELLED,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.INTERNAL,
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.UNKNOWN,
        )

        retryableCodes.forEach { code ->
            assertSame(
                "Expected $code to be retryable",
                ReportDeliveryResult.TransientFailure,
                FirebaseFirestoreException("test", code).toDeliveryResult(),
            )
        }
        assertSame(ReportDeliveryResult.TransientFailure, FirebaseException("test").toDeliveryResult())
    }

    @Test
    fun `classifies rejected and invalid deliveries as permanent`() {
        assertSame(
            ReportDeliveryResult.PermanentFailure,
            FirebaseFirestoreException("test", FirebaseFirestoreException.Code.PERMISSION_DENIED).toDeliveryResult(),
        )
        assertSame(ReportDeliveryResult.PermanentFailure, IllegalStateException("mismatch").toDeliveryResult())
    }

    @Test
    fun `keeps unknown client exceptions retryable`() {
        assertSame(ReportDeliveryResult.TransientFailure, IllegalArgumentException("test").toDeliveryResult())
    }

    private fun payload() = GeneratedContentReportPayload(
        schemaVersion = REPORT_SCHEMA_VERSION,
        reportId = REPORT_ID,
        reportedResponse = "reported response",
        reason = ReportReason.Privacy,
        comment = "comment",
        context = listOf(ReportContextItem("message-id", ChatRole.User, "selected context")),
        mediaPresence = ReportMediaPresence(image = true, audio = false, transcript = false),
        metadata = ReportTechnicalMetadata("1.0", "en-CA", "model", "LiteRT-LM"),
        reportedAtEpochMillis = REPORTED_AT,
    )

    private companion object {
        const val OWNER_UID = "owner-user"
        const val REPORT_ID = "123e4567-e89b-12d3-a456-426614174000"
        const val REPORTED_AT = 1_700_000_000_000L
        const val NINETY_DAYS = 90L * 24L * 60L * 60L * 1_000L
    }
}
