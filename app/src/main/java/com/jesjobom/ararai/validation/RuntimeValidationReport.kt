package com.jesjobom.ararai.validation

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.jesjobom.ararai.widget.runtime.StrictJson

internal data class RuntimeValidationEnvironment(
    val generatedAtUtc: String,
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
    val buildDisplay: String,
    val supportedAbis: List<String>,
    val appVersion: String,
    val versionCode: Long,
    val buildType: String,
    val apkSha256: String,
    val quickJsLibrarySha256: String,
    val quickJsLibraryBytes: Long,
) {
    init {
        require(generatedAtUtc.isNotBlank())
        require(manufacturer.isNotBlank())
        require(model.isNotBlank())
        require(androidRelease.isNotBlank())
        require(sdkInt > 0)
        require(buildDisplay.isNotBlank())
        require(supportedAbis.isNotEmpty() && supportedAbis.all(String::isNotBlank))
        require(appVersion.isNotBlank())
        require(versionCode > 0)
        require(buildType.isNotBlank())
        require(SHA256.matches(apkSha256))
        require(SHA256.matches(quickJsLibrarySha256))
        require(quickJsLibraryBytes >= 0)
    }

    val accepted: Boolean
        get() = buildType == EXPECTED_BUILD_TYPE &&
            EXPECTED_ABI in supportedAbis &&
            apkSha256 != MISSING_SHA256 &&
            quickJsLibrarySha256 != MISSING_SHA256 &&
            quickJsLibraryBytes > 0

    private companion object {
        const val EXPECTED_BUILD_TYPE = "releaseCandidate"
        const val EXPECTED_ABI = "arm64-v8a"
        const val MISSING_SHA256 = "0000000000000000000000000000000000000000000000000000000000000000"
        val SHA256 = Regex("[0-9a-f]{64}")
    }
}

internal data class RuntimeValidationCaseResult(
    val id: String,
    val passed: Boolean,
    val outcome: String,
    val durationMillis: Long,
    val pssBeforeKb: Long,
    val pssPeakKb: Long,
    val pssAfterKb: Long,
) {
    init {
        require(ID.matches(id))
        require(outcome in STABLE_OUTCOMES)
        require(passed == (outcome == PASS))
        require(durationMillis >= 0)
        require(pssBeforeKb >= 0 && pssPeakKb >= pssBeforeKb && pssPeakKb >= pssAfterKb)
        require(pssAfterKb >= 0)
    }

    private companion object {
        const val PASS = "pass"
        val ID = Regex("[a-z][a-z0-9_]{0,63}")
        val STABLE_OUTCOMES = setOf(
            PASS,
            "unexpected_result",
            "case_timeout",
            "runtime_unavailable",
            "internal_failure",
        )
    }
}

internal data class RuntimeValidationReport(
    val environment: RuntimeValidationEnvironment,
    val cases: List<RuntimeValidationCaseResult>,
) {
    init {
        require(cases.isNotEmpty())
        require(cases.map { it.id }.distinct().size == cases.size)
    }

    val passedCount: Int = cases.count { it.passed }
    val failedCount: Int = cases.size - passedCount
    val overallPassed: Boolean = environment.accepted && failedCount == 0

    fun toCanonicalJson(): String {
        val root = JsonObject().apply {
            addProperty("suiteVersion", SUITE_VERSION)
            addProperty("overallPassed", overallPassed)
            addProperty("passedCount", passedCount)
            addProperty("failedCount", failedCount)
            add("environment", environment.toJson())
            add("cases", JsonArray().also { values -> cases.forEach { values.add(it.toJson()) } })
        }
        return StrictJson.canonical(root)
    }

    private fun RuntimeValidationEnvironment.toJson() = JsonObject().apply {
        addProperty("generatedAtUtc", generatedAtUtc)
        addProperty("manufacturer", manufacturer)
        addProperty("model", model)
        addProperty("androidRelease", androidRelease)
        addProperty("sdkInt", sdkInt)
        addProperty("buildDisplay", buildDisplay)
        add("supportedAbis", JsonArray().also { values -> supportedAbis.forEach(values::add) })
        addProperty("appVersion", appVersion)
        addProperty("versionCode", versionCode)
        addProperty("buildType", buildType)
        addProperty("apkSha256", apkSha256)
        addProperty("quickJsLibrarySha256", quickJsLibrarySha256)
        addProperty("quickJsLibraryBytes", quickJsLibraryBytes)
        addProperty("accepted", accepted)
    }

    private fun RuntimeValidationCaseResult.toJson() = JsonObject().apply {
        addProperty("id", id)
        addProperty("passed", passed)
        addProperty("outcome", outcome)
        addProperty("durationMillis", durationMillis)
        addProperty("pssBeforeKb", pssBeforeKb)
        addProperty("pssPeakKb", pssPeakKb)
        addProperty("pssAfterKb", pssAfterKb)
    }

    private companion object {
        const val SUITE_VERSION = 1
    }
}
