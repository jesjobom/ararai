package com.jesjobom.ararai.widget.runtime

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

enum class WidgetRuntimeValue(val wireName: String) {
    Locale("locale"),
    Timezone("timezone"),
    LocalTime("local_time"),
    Seed("seed"),
}

enum class WidgetPresentationCapability(val wireName: String) {
    Card("card"),
    Column("column"),
    Row("row"),
    Text("text"),
    Value("value"),
    Icon("icon"),
    HttpsLink("https_link"),
}

data class WidgetToolCapability(val id: String, val version: Int) {
    init {
        require(TOOL_ID_PATTERN.matches(id)) { "Invalid widget tool id" }
        require(version > 0) { "Invalid widget tool version" }
    }
}

data class WidgetProgramEntrypoints(val plan: String, val render: String) {
    init {
        require(ENTRYPOINT_PATTERN.matches(plan)) { "Invalid plan entrypoint" }
        require(ENTRYPOINT_PATTERN.matches(render)) { "Invalid render entrypoint" }
        require(plan != render) { "Widget entrypoints must be distinct" }
    }
}

data class WidgetProgramCapabilities(
    val tools: Set<WidgetToolCapability>,
    val runtimeValues: Set<WidgetRuntimeValue>,
    val presentation: Set<WidgetPresentationCapability>,
)

data class WidgetRequestedLimits(
    val memoryBytes: Long,
    val stackBytes: Long,
    val executionMillis: Long,
    val maxToolCalls: Int,
    val maxOutputBytes: Int,
)

data class WidgetProgramManifest(
    val schemaVersion: Int,
    val language: String,
    val apiVersion: Int,
    val entrypoints: WidgetProgramEntrypoints,
    val capabilities: WidgetProgramCapabilities,
    val limits: WidgetRequestedLimits,
    val sourceSha256: String,
)

data class WidgetProgram(
    val manifest: WidgetProgramManifest,
    val canonicalManifestJson: String,
    val source: String,
)

data class WidgetExecutionGrant(
    val tools: Set<WidgetToolCapability>,
    val runtimeValues: Set<WidgetRuntimeValue>,
    val presentation: Set<WidgetPresentationCapability>,
)

data class WidgetRuntimeContext(
    val locale: String,
    val timezone: String,
    val localTime: String,
    val seed: Long?,
)

enum class WidgetRuntimeFailureCode {
    InvalidProgram,
    IncompatibleProgram,
    IntegrityMismatch,
    CapabilityDenied,
    PolicyViolation,
    InvalidPlan,
    InvalidPresentation,
    ScriptError,
    ResourceLimit,
    Cancelled,
    RuntimeUnavailable,
}

sealed interface WidgetProgramValidationResult {
    data class Valid(val program: WidgetProgram) : WidgetProgramValidationResult
    data class Invalid(val code: WidgetRuntimeFailureCode) : WidgetProgramValidationResult
}

object WidgetRuntimePolicy {
    const val MANIFEST_SCHEMA_VERSION = 1
    const val WIDGET_API_VERSION = 1
    const val LANGUAGE = "javascript"
    const val MAX_MANIFEST_BYTES = 16 * 1024
    const val MAX_SOURCE_BYTES = 32 * 1024
    const val MAX_JSON_BYTES = 64 * 1024
    const val MAX_JSON_DEPTH = 16
    const val MAX_JSON_VALUES = 512
    const val MAX_STRING_CHARS = 4_096
    const val MAX_MEMORY_BYTES = 8L * 1024 * 1024
    const val MAX_STACK_BYTES = 512L * 1024
    const val MAX_EXECUTION_MILLIS = 250L
    const val MAX_TOOL_CALLS = 4
    const val MAX_OUTPUT_BYTES = 64 * 1024
    const val MAX_PRESENTATION_DEPTH = 8
    const val MAX_PRESENTATION_NODES = 128
    const val MAX_PRESENTATION_CHILDREN = 32
    const val MAX_TEXT_CHARS = 2_048

    val supportedRuntimeValues = WidgetRuntimeValue.entries.toSet()
    val supportedPresentation = WidgetPresentationCapability.entries.toSet()

    fun accepts(limits: WidgetRequestedLimits): Boolean = limits.memoryBytes in 1024L * 1024..MAX_MEMORY_BYTES &&
        limits.stackBytes in 64L * 1024..MAX_STACK_BYTES &&
        limits.executionMillis in 1..MAX_EXECUTION_MILLIS &&
        limits.maxToolCalls in 0..MAX_TOOL_CALLS &&
        limits.maxOutputBytes in 1..MAX_OUTPUT_BYTES
}

internal val TOOL_ID_PATTERN = Regex("[a-z][a-z0-9_]{0,63}")
internal val ENTRYPOINT_PATTERN = Regex("[A-Za-z_$][A-Za-z0-9_$]{0,63}")
internal val SHA256_PATTERN = Regex("[0-9a-f]{64}")

internal fun String.utf8Size(): Int = toByteArray(StandardCharsets.UTF_8).size

internal fun sha256(value: String): String = MessageDigest
    .getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }
