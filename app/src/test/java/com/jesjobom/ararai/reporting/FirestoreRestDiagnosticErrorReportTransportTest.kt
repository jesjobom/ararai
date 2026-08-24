package com.jesjobom.ararai.reporting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FirestoreRestDiagnosticErrorReportTransportTest {
    @Test
    fun `builds one create-only Firestore commit with server creation time`() {
        val commit = payload().toFirestoreCommit(PROJECT_ID, OWNER_UID)
        val writes = commit.getJSONArray("writes")

        assertEquals(1, writes.length())
        val write = writes.getJSONObject(0)
        assertFalse(write.getJSONObject("currentDocument").getBoolean("exists"))
        assertEquals(
            "REQUEST_TIME",
            write.getJSONArray("updateTransforms").getJSONObject(0).getString("setToServerValue"),
        )
        assertEquals(
            "projects/$PROJECT_ID/databases/(default)/documents/diagnostic_error_reports/${OWNER_UID}_${REPORT_ID}",
            write.getJSONObject("update").getString("name"),
        )
    }

    @Test
    fun `serializes only the allowlisted diagnostic document fields`() {
        val fields = payload().toFirestoreCommit(PROJECT_ID, OWNER_UID)
            .getJSONArray("writes")
            .getJSONObject(0)
            .getJSONObject("update")
            .getJSONObject("fields")

        assertEquals(
            setOf(
                "schemaVersion",
                "reportId",
                "ownerUid",
                "category",
                "stage",
                "exceptionType",
                "exceptionSummary",
                "stackSummary",
                "metadata",
                "reportedAt",
                "expiresAt",
            ),
            fields.keys().asSequence().toSet(),
        )
        assertEquals(OWNER_UID, fields.getJSONObject("ownerUid").getString("stringValue"))
        assertEquals(
            "tool_call_parsing",
            fields.getJSONObject("category").getString("stringValue"),
        )
        val metadata = fields.getJSONObject("metadata")
            .getJSONObject("mapValue")
            .getJSONObject("fields")
        assertEquals("6144", metadata.getJSONObject("contextTokens").getString("integerValue"))
        assertTrue(metadata.getJSONObject("reasoningEnabled").getBoolean("booleanValue"))
    }

    private fun payload() = DiagnosticErrorReportPayload(
        reportId = REPORT_ID,
        category = DiagnosticErrorCategory.ToolCallParsing,
        stage = "chat_generation",
        exceptionType = "IllegalArgumentException",
        exceptionSummary = "The local runtime could not parse a model tool call.",
        stackSummary = listOf("com.jesjobom.ararai.chat.ChatViewModel.generate:123"),
        appVersion = "1.0-test",
        androidApiLevel = 36,
        localeTag = "pt-BR",
        modelId = "gemma-test",
        runtime = "litert_lm",
        contextTokens = 6144,
        reasoningEnabled = true,
        enabledToolNames = listOf("web_search"),
        reportedAtEpochMillis = 1_787_500_000_000,
    )

    private companion object {
        const val PROJECT_ID = "ararai-report-test"
        const val OWNER_UID = "owner-user"
        const val REPORT_ID = "123e4567-e89b-42d3-a456-426614174000"
    }
}
