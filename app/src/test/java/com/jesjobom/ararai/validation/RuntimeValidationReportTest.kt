package com.jesjobom.ararai.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeValidationReportTest {
    @Test
    fun serializesRequiredPhysicalEvidenceDeterministically() {
        val report = RuntimeValidationReport(
            environment = RuntimeValidationEnvironment(
                generatedAtUtc = "2026-09-05T14:00:00Z",
                manufacturer = "Example",
                model = "Device",
                androidRelease = "16",
                sdkInt = 36,
                buildDisplay = "build-id",
                supportedAbis = listOf("arm64-v8a"),
                appVersion = "202609051400",
                versionCode = 123,
                buildType = "releaseCandidate",
                apkSha256 = "a".repeat(64),
                quickJsLibrarySha256 = "b".repeat(64),
                quickJsLibraryBytes = 456,
            ),
            cases = listOf(
                RuntimeValidationCaseResult(
                    id = "data_only_fixture",
                    passed = true,
                    outcome = "pass",
                    durationMillis = 12,
                    pssBeforeKb = 100,
                    pssPeakKb = 120,
                    pssAfterKb = 110,
                ),
            ),
        )

        val encoded = report.toCanonicalJson()

        assertEquals(encoded, report.toCanonicalJson())
        assertTrue(encoded.contains("\"suiteVersion\":1"))
        assertTrue(encoded.contains("\"overallPassed\":true"))
        assertTrue(encoded.contains("\"supportedAbis\":[\"arm64-v8a\"]"))
        assertTrue(encoded.contains("\"apkSha256\":\"${"a".repeat(64)}\""))
        assertFalse(encoded.contains("exception"))
        assertFalse(encoded.contains("stack"))
    }

    @Test
    fun failsSummaryWhenAnyStableCaseOutcomeFails() {
        val report = RuntimeValidationReport(
            environment = testEnvironment(),
            cases = listOf(
                RuntimeValidationCaseResult("first", true, "pass", 1, 10, 11, 10),
                RuntimeValidationCaseResult("second", false, "unexpected_result", 2, 10, 12, 11),
            ),
        )

        assertFalse(report.overallPassed)
        assertEquals(1, report.passedCount)
        assertEquals(1, report.failedCount)
        assertTrue(report.toCanonicalJson().contains("\"outcome\":\"unexpected_result\""))
    }

    @Test
    fun rejectsUnsupportedArtifactFromPassingSummary() {
        val unsupported = testEnvironment().copy(
            buildType = "debug",
            supportedAbis = listOf("x86_64"),
        )
        val report = RuntimeValidationReport(
            environment = unsupported,
            cases = listOf(RuntimeValidationCaseResult("first", true, "pass", 1, 10, 11, 10)),
        )

        assertFalse(unsupported.accepted)
        assertFalse(report.overallPassed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsArbitraryFailureTextFromReportBoundary() {
        RuntimeValidationCaseResult(
            id = "first",
            passed = false,
            outcome = "credential_secret_from_exception",
            durationMillis = 1,
            pssBeforeKb = 10,
            pssPeakKb = 11,
            pssAfterKb = 10,
        )
    }

    private fun testEnvironment() = RuntimeValidationEnvironment(
        generatedAtUtc = "2026-09-05T14:00:00Z",
        manufacturer = "Example",
        model = "Device",
        androidRelease = "16",
        sdkInt = 36,
        buildDisplay = "build-id",
        supportedAbis = listOf("arm64-v8a"),
        appVersion = "1",
        versionCode = 1,
        buildType = "releaseCandidate",
        apkSha256 = "a".repeat(64),
        quickJsLibrarySha256 = "b".repeat(64),
        quickJsLibraryBytes = 1,
    )
}
