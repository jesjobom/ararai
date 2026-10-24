package com.jesjobom.ararai.widget.runtime

import com.google.gson.JsonArray
import com.google.gson.JsonObject

object WidgetProgramParser {
    @Suppress("ReturnCount")
    fun parse(manifestJson: String, source: String): WidgetProgramValidationResult {
        if (
            manifestJson.utf8Size() > WidgetRuntimePolicy.MAX_MANIFEST_BYTES ||
            source.utf8Size() > WidgetRuntimePolicy.MAX_SOURCE_BYTES
        ) {
            return invalid(WidgetRuntimeFailureCode.InvalidProgram)
        }
        if (RESERVED_STDLIB_IDENTIFIER.containsMatchIn(source)) {
            return invalid(WidgetRuntimeFailureCode.InvalidProgram)
        }
        return try {
            val root = StrictJson.parse(manifestJson).requiredObject()
            root.requireFields(
                "schemaVersion",
                "language",
                "apiVersion",
                "entrypoints",
                "capabilities",
                "limits",
                "sourceSha256",
            )
            val schemaVersion = root.requiredInt("schemaVersion")
            val language = root.requiredString("language")
            val apiVersion = root.requiredInt("apiVersion")
            if (
                schemaVersion != WidgetRuntimePolicy.MANIFEST_SCHEMA_VERSION ||
                language != WidgetRuntimePolicy.LANGUAGE ||
                apiVersion != WidgetRuntimePolicy.WIDGET_API_VERSION
            ) {
                return invalid(WidgetRuntimeFailureCode.IncompatibleProgram)
            }
            val entrypoints = parseEntrypoints(root.requiredObject("entrypoints"))
            val capabilities = parseCapabilities(root.requiredObject("capabilities"))
            val limits = parseLimits(root.requiredObject("limits"))
            if (!WidgetRuntimePolicy.accepts(limits)) {
                return invalid(WidgetRuntimeFailureCode.PolicyViolation)
            }
            val digest = root.requiredString("sourceSha256")
            require(SHA256_PATTERN.matches(digest))
            if (sha256(source) != digest) {
                return invalid(WidgetRuntimeFailureCode.IntegrityMismatch)
            }
            val manifest = WidgetProgramManifest(
                schemaVersion = schemaVersion,
                language = language,
                apiVersion = apiVersion,
                entrypoints = entrypoints,
                capabilities = capabilities,
                limits = limits,
                sourceSha256 = digest,
            )
            WidgetProgramValidationResult.Valid(
                WidgetProgram(manifest, StrictJson.canonical(root), source),
            )
        } catch (_: Exception) {
            invalid(WidgetRuntimeFailureCode.InvalidProgram)
        }
    }

    private fun parseEntrypoints(value: JsonObject): WidgetProgramEntrypoints {
        value.requireFields("plan", "render")
        return WidgetProgramEntrypoints(
            plan = value.requiredString("plan"),
            render = value.requiredString("render"),
        )
    }

    private fun parseCapabilities(value: JsonObject): WidgetProgramCapabilities {
        value.requireFields("tools", "runtime", "presentation")
        val tools = value.requiredArray("tools").map { element ->
            val tool = element.requiredObject()
            tool.requireFields("id", "version")
            WidgetToolCapability(tool.requiredString("id"), tool.requiredInt("version"))
        }.toSet().also { require(it.size == value.requiredArray("tools").size()) }
        val runtimeValues = value.requiredArray("runtime").enumSet(WidgetRuntimeValue.entries) { it.wireName }
        val presentation = value.requiredArray("presentation")
            .enumSet(WidgetPresentationCapability.entries) { it.wireName }
        require(runtimeValues.all { it in WidgetRuntimePolicy.supportedRuntimeValues })
        require(presentation.all { it in WidgetRuntimePolicy.supportedPresentation })
        return WidgetProgramCapabilities(tools, runtimeValues, presentation)
    }

    private fun parseLimits(value: JsonObject): WidgetRequestedLimits {
        value.requireFields(
            "memoryBytes",
            "stackBytes",
            "executionMillis",
            "maxToolCalls",
            "maxOutputBytes",
        )
        return WidgetRequestedLimits(
            memoryBytes = value.requiredLong("memoryBytes"),
            stackBytes = value.requiredLong("stackBytes"),
            executionMillis = value.requiredLong("executionMillis"),
            maxToolCalls = value.requiredInt("maxToolCalls"),
            maxOutputBytes = value.requiredInt("maxOutputBytes"),
        )
    }

    private fun <T> JsonArray.enumSet(values: Iterable<T>, wireName: (T) -> String): Set<T> {
        val byName = values.associateBy(wireName)
        val result = map { element -> byName[element.asString] ?: error("Unsupported capability") }.toSet()
        require(result.size == size()) { "Duplicate capability" }
        return result
    }

    private fun invalid(code: WidgetRuntimeFailureCode) = WidgetProgramValidationResult.Invalid(code)
}

private val RESERVED_STDLIB_IDENTIFIER = Regex("\\b__ararai[A-Za-z0-9_$]*")

internal fun JsonObject.requireFields(vararg expected: String) {
    require(keySet() == expected.toSet()) { "Unexpected JSON fields" }
}

internal fun JsonObject.requiredString(name: String): String = get(name)
    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
    ?.asString
    ?: error("Missing string field")

internal fun JsonObject.requiredInt(name: String): Int {
    val value = requiredLong(name)
    require(value in Int.MIN_VALUE..Int.MAX_VALUE)
    return value.toInt()
}

internal fun JsonObject.requiredLong(name: String): Long {
    val primitive = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asJsonPrimitive
        ?: error("Missing number field")
    val decimal = primitive.asBigDecimal
    return decimal.longValueExact()
}

internal fun JsonObject.requiredObject(name: String): JsonObject = get(name)?.requiredObject()
    ?: error("Missing object field")

internal fun JsonObject.requiredArray(name: String): JsonArray = get(name)
    ?.takeIf { it.isJsonArray }
    ?.asJsonArray
    ?: error("Missing array field")

internal fun com.google.gson.JsonElement.requiredObject(): JsonObject = takeIf { it.isJsonObject }?.asJsonObject
    ?: error("Expected JSON object")
