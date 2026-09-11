package com.jesjobom.ararai.widget.runtime

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.jesjobom.ararai.tools.ApplicationToolRejection
import com.jesjobom.ararai.widget.WidgetToolExecutionGateway
import com.jesjobom.ararai.widget.WidgetToolExecutionResult
import com.jesjobom.ararai.widget.WidgetToolInvocation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class PlannedWidgetToolCall(
    val alias: String,
    val tool: WidgetToolCapability,
    val argumentsJson: String,
)

internal data class WidgetToolOutcome(
    val alias: String,
    val payload: JsonObject?,
    val rejection: ApplicationToolRejection?,
) {
    init {
        require((payload == null) xor (rejection == null)) {
            "A widget tool outcome must contain exactly one result"
        }
    }
}

internal fun interface WidgetProgramToolExecutor {
    suspend fun execute(call: PlannedWidgetToolCall): WidgetToolOutcome
}

internal class GatewayWidgetProgramToolExecutor(
    private val gateway: WidgetToolExecutionGateway,
) : WidgetProgramToolExecutor {
    override suspend fun execute(call: PlannedWidgetToolCall): WidgetToolOutcome = when (
        val result = gateway.execute(
            WidgetToolInvocation(call.tool.id, call.tool.version, call.argumentsJson),
        )
    ) {
        is WidgetToolExecutionResult.Success -> {
            val payload = runCatching {
                require(result.payloadJson.utf8Size() <= WidgetRuntimePolicy.MAX_JSON_BYTES)
                StrictJson.parse(result.payloadJson).requiredObject()
            }.getOrNull()
            if (payload == null) {
                WidgetToolOutcome(call.alias, null, ApplicationToolRejection.Unavailable)
            } else {
                WidgetToolOutcome(call.alias, payload, null)
            }
        }
        is WidgetToolExecutionResult.Failure -> WidgetToolOutcome(call.alias, null, result.reason)
    }
}

internal object WidgetPlanParser {
    fun parse(
        raw: String,
        capabilities: WidgetProgramCapabilities,
        grant: WidgetExecutionGrant,
        limits: WidgetRequestedLimits,
    ): List<PlannedWidgetToolCall> {
        require(raw.utf8Size() <= limits.maxOutputBytes)
        val array = StrictJson.parse(raw).takeIf { it.isJsonArray }?.asJsonArray
            ?: error("Plan must be an array")
        require(array.size() <= limits.maxToolCalls)
        val aliases = mutableSetOf<String>()
        return array.map { element ->
            val value = element.requiredObject()
            value.requireFields("alias", "toolId", "contractVersion", "arguments")
            val alias = value.requiredString("alias")
            require(ALIAS_PATTERN.matches(alias) && aliases.add(alias))
            val tool = WidgetToolCapability(
                value.requiredString("toolId"),
                value.requiredInt("contractVersion"),
            )
            require(tool in capabilities.tools && tool in grant.tools)
            val arguments = value.get("arguments").requiredObject()
            PlannedWidgetToolCall(alias, tool, StrictJson.canonical(arguments))
        }
    }

    private val ALIAS_PATTERN = Regex("[a-z][a-z0-9_]{0,31}")
}

internal sealed interface WidgetPresentationNode {
    data class Card(val child: WidgetPresentationNode) : WidgetPresentationNode
    data class Column(val children: List<WidgetPresentationNode>) : WidgetPresentationNode
    data class Row(val children: List<WidgetPresentationNode>) : WidgetPresentationNode
    data class Text(val text: String, val tone: String) : WidgetPresentationNode
    data class Value(val text: String, val tone: String) : WidgetPresentationNode
    data class Icon(val name: String) : WidgetPresentationNode
    data class HttpsLink(
        val label: String,
        val url: String,
        val sourceAlias: String,
        val sourceField: String,
    ) : WidgetPresentationNode
}

internal object WidgetPresentationParser {
    fun parse(
        raw: String,
        capabilities: WidgetProgramCapabilities,
        grant: WidgetExecutionGrant,
        outcomes: List<WidgetToolOutcome>,
        limits: WidgetRequestedLimits,
    ): WidgetPresentationNode {
        require(raw.utf8Size() <= limits.maxOutputBytes)
        val counter = Counter()
        return parseNode(
            StrictJson.parse(raw).requiredObject(),
            depth = 1,
            counter = counter,
            allowed = capabilities.presentation.intersect(grant.presentation),
            outcomes = outcomes.associateBy { it.alias },
        )
    }

    private fun parseNode(
        value: JsonObject,
        depth: Int,
        counter: Counter,
        allowed: Set<WidgetPresentationCapability>,
        outcomes: Map<String, WidgetToolOutcome>,
    ): WidgetPresentationNode {
        require(depth <= WidgetRuntimePolicy.MAX_PRESENTATION_DEPTH)
        require(++counter.nodes <= WidgetRuntimePolicy.MAX_PRESENTATION_NODES)
        return when (value.requiredString("type")) {
            "card" -> {
                require(WidgetPresentationCapability.Card in allowed)
                value.requireFields("type", "child")
                WidgetPresentationNode.Card(
                    parseNode(value.get("child").requiredObject(), depth + 1, counter, allowed, outcomes),
                )
            }
            "column", "row" -> parseCollection(value, depth, counter, allowed, outcomes)
            "text", "value" -> parseText(value, allowed)
            "icon" -> {
                require(WidgetPresentationCapability.Icon in allowed)
                value.requireFields("type", "name")
                val name = value.requiredString("name")
                require(name in ICONS)
                WidgetPresentationNode.Icon(name)
            }
            "https_link" -> parseLink(value, allowed, outcomes)
            else -> error("Unsupported presentation node")
        }
    }

    private fun parseCollection(
        value: JsonObject,
        depth: Int,
        counter: Counter,
        allowed: Set<WidgetPresentationCapability>,
        outcomes: Map<String, WidgetToolOutcome>,
    ): WidgetPresentationNode {
        value.requireFields("type", "children")
        val type = value.requiredString("type")
        val capability = if (type == "column") {
            WidgetPresentationCapability.Column
        } else {
            WidgetPresentationCapability.Row
        }
        require(capability in allowed)
        val children = value.requiredArray("children")
        require(children.size() in 1..WidgetRuntimePolicy.MAX_PRESENTATION_CHILDREN)
        val parsed = children.map { parseNode(it.requiredObject(), depth + 1, counter, allowed, outcomes) }
        return if (type == "column") {
            WidgetPresentationNode.Column(parsed)
        } else {
            WidgetPresentationNode.Row(parsed)
        }
    }

    private fun parseText(
        value: JsonObject,
        allowed: Set<WidgetPresentationCapability>,
    ): WidgetPresentationNode {
        value.requireFields("type", "text", "tone")
        val type = value.requiredString("type")
        val capability = if (type == "text") {
            WidgetPresentationCapability.Text
        } else {
            WidgetPresentationCapability.Value
        }
        require(capability in allowed)
        val text = value.requiredString("text")
        val tone = value.requiredString("tone")
        require(text.length <= WidgetRuntimePolicy.MAX_TEXT_CHARS && tone in TONES)
        return if (type == "text") {
            WidgetPresentationNode.Text(text, tone)
        } else {
            WidgetPresentationNode.Value(text, tone)
        }
    }

    private fun parseLink(
        value: JsonObject,
        allowed: Set<WidgetPresentationCapability>,
        outcomes: Map<String, WidgetToolOutcome>,
    ): WidgetPresentationNode.HttpsLink {
        require(WidgetPresentationCapability.HttpsLink in allowed)
        value.requireFields("type", "label", "url", "sourceAlias", "sourceField")
        val label = value.requiredString("label")
        val url = value.requiredString("url")
        val alias = value.requiredString("sourceAlias")
        val field = value.requiredString("sourceField")
        require(label.length <= WidgetRuntimePolicy.MAX_TEXT_CHARS)
        require(SOURCE_FIELD_PATTERN.matches(field))
        require(url.startsWith("https://") && url.length <= MAX_URL_CHARS)
        val sourceUrl = outcomes[alias]?.payload?.resolveSourcePath(field)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
        require(sourceUrl == url)
        return WidgetPresentationNode.HttpsLink(label, url, alias, field)
    }

    private class Counter(var nodes: Int = 0)

    private val TONES = setOf("neutral", "muted", "positive", "warning")
    private val ICONS = setOf("calendar", "clock", "info", "link", "location", "warning")
    private val SOURCE_FIELD_PATTERN = SOURCE_PATH_PATTERN
    private const val MAX_URL_CHARS = 2_048
}

internal object WidgetPresentationCodec {
    fun encode(node: WidgetPresentationNode): String = StrictJson.canonical(node.toJson())

    /**
     * Decodes a presentation that was previously validated and stored by the app.
     *
     * The persistence boundary is treated as untrusted here: corrupt or manually
     * modified rows still have to satisfy the complete renderer-neutral tree limits.
     * Link provenance is checked during execution before a cache is written; the UI
     * applies its application-owned destination allowlist again before opening one.
     */
    fun decodeCached(raw: String): WidgetPresentationNode {
        require(raw.utf8Size() <= WidgetRuntimePolicy.MAX_OUTPUT_BYTES)
        return decodeNode(
            value = StrictJson.parse(raw).requiredObject(),
            depth = 1,
            counter = Counter(),
        )
    }

    private fun WidgetPresentationNode.toJson(): JsonObject = JsonObject().also { value ->
        when (this) {
            is WidgetPresentationNode.Card -> {
                value.addProperty("type", "card")
                value.add("child", child.toJson())
            }
            is WidgetPresentationNode.Column -> {
                value.addProperty("type", "column")
                value.add("children", children.toJsonArray())
            }
            is WidgetPresentationNode.Row -> {
                value.addProperty("type", "row")
                value.add("children", children.toJsonArray())
            }
            is WidgetPresentationNode.Text -> {
                value.addProperty("type", "text")
                value.addProperty("text", text)
                value.addProperty("tone", tone)
            }
            is WidgetPresentationNode.Value -> {
                value.addProperty("type", "value")
                value.addProperty("text", text)
                value.addProperty("tone", tone)
            }
            is WidgetPresentationNode.Icon -> {
                value.addProperty("type", "icon")
                value.addProperty("name", name)
            }
            is WidgetPresentationNode.HttpsLink -> {
                value.addProperty("type", "https_link")
                value.addProperty("label", label)
                value.addProperty("url", url)
                value.addProperty("sourceAlias", sourceAlias)
                value.addProperty("sourceField", sourceField)
            }
        }
    }

    private fun List<WidgetPresentationNode>.toJsonArray(): JsonArray = JsonArray().also { array ->
        forEach { array.add(it.toJson()) }
    }

    private fun decodeNode(
        value: JsonObject,
        depth: Int,
        counter: Counter,
    ): WidgetPresentationNode {
        require(depth <= WidgetRuntimePolicy.MAX_PRESENTATION_DEPTH)
        require(++counter.nodes <= WidgetRuntimePolicy.MAX_PRESENTATION_NODES)
        return when (value.requiredString("type")) {
            "card" -> {
                value.requireFields("type", "child")
                WidgetPresentationNode.Card(
                    decodeNode(value.get("child").requiredObject(), depth + 1, counter),
                )
            }
            "column", "row" -> decodeCollection(value, depth, counter)
            "text", "value" -> decodeText(value)
            "icon" -> {
                value.requireFields("type", "name")
                WidgetPresentationNode.Icon(value.requiredString("name").also { require(it in CACHED_ICONS) })
            }
            "https_link" -> decodeLink(value)
            else -> error("Unsupported cached presentation node")
        }
    }

    private fun decodeCollection(
        value: JsonObject,
        depth: Int,
        counter: Counter,
    ): WidgetPresentationNode {
        value.requireFields("type", "children")
        val type = value.requiredString("type")
        val children = value.requiredArray("children")
        require(children.size() in 1..WidgetRuntimePolicy.MAX_PRESENTATION_CHILDREN)
        val decoded = children.map { decodeNode(it.requiredObject(), depth + 1, counter) }
        return if (type == "column") WidgetPresentationNode.Column(decoded) else WidgetPresentationNode.Row(decoded)
    }

    private fun decodeText(value: JsonObject): WidgetPresentationNode {
        value.requireFields("type", "text", "tone")
        val type = value.requiredString("type")
        val text = value.requiredString("text")
        val tone = value.requiredString("tone")
        require(text.length <= WidgetRuntimePolicy.MAX_TEXT_CHARS && tone in CACHED_TONES)
        return if (type == "text") WidgetPresentationNode.Text(text, tone) else WidgetPresentationNode.Value(text, tone)
    }

    private fun decodeLink(value: JsonObject): WidgetPresentationNode.HttpsLink {
        value.requireFields("type", "label", "url", "sourceAlias", "sourceField")
        val label = value.requiredString("label")
        val url = value.requiredString("url")
        val alias = value.requiredString("sourceAlias")
        val field = value.requiredString("sourceField")
        require(label.length <= WidgetRuntimePolicy.MAX_TEXT_CHARS)
        require(CACHED_ALIAS_PATTERN.matches(alias) && CACHED_SOURCE_FIELD_PATTERN.matches(field))
        require(url.startsWith("https://") && url.length <= CACHED_MAX_URL_CHARS)
        return WidgetPresentationNode.HttpsLink(label, url, alias, field)
    }

    private class Counter(var nodes: Int = 0)

    private val CACHED_TONES = setOf("neutral", "muted", "positive", "warning")
    private val CACHED_ICONS = setOf("calendar", "clock", "info", "link", "location", "warning")
    private val CACHED_ALIAS_PATTERN = Regex("[a-z][a-z0-9_]{0,31}")
    private val CACHED_SOURCE_FIELD_PATTERN = SOURCE_PATH_PATTERN
    private const val CACHED_MAX_URL_CHARS = 2_048
}

private fun JsonElement.resolveSourcePath(path: String): JsonElement? = path.split('.').fold(this as JsonElement?) {
        current,
        segment,
    ->
    when {
        current?.isJsonObject == true -> current.asJsonObject.get(segment)
        current?.isJsonArray == true -> segment.toIntOrNull()?.let { index -> current.asJsonArray.getOrNull(index) }
        else -> null
    }
}

private fun JsonArray.getOrNull(index: Int): JsonElement? = if (index in 0..<size()) get(index) else null

private val SOURCE_PATH_PATTERN =
    Regex("[A-Za-z][A-Za-z0-9_]{0,63}(?:\\.(?:[A-Za-z][A-Za-z0-9_]{0,63}|[0-9]{1,2})){0,3}")

internal fun WidgetRuntimeContext.toJson(capabilities: Set<WidgetRuntimeValue>): String {
    val result = JsonObject()
    result.addProperty("apiVersion", WidgetRuntimePolicy.WIDGET_API_VERSION)
    if (WidgetRuntimeValue.Locale in capabilities) {
        result.addProperty("locale", locale)
        result.addProperty("language", locale.substringBefore('-').lowercase())
    }
    if (WidgetRuntimeValue.Timezone in capabilities) result.addProperty("timezone", timezone)
    if (WidgetRuntimeValue.LocalTime in capabilities) {
        result.addProperty("localTime", localTime)
        val dateTime = ISO_LOCAL_DATE_TIME_PREFIX.matchAt(localTime, 0)
            ?: error("Local time must start with an ISO local date and time")
        result.addProperty("localYear", dateTime.groupValues[1].toInt())
        result.addProperty("localMonth", dateTime.groupValues[2].toInt())
        result.addProperty("localDay", dateTime.groupValues[3].toInt())
        result.addProperty("localHour", dateTime.groupValues[4].toInt())
        result.addProperty("localMinute", dateTime.groupValues[5].toInt())
        result.addProperty("localSecond", dateTime.groupValues[6].toInt())
    }
    if (WidgetRuntimeValue.Seed in capabilities) {
        requireNotNull(seed) { "A seed grant requires an explicit seed" }
        result.addProperty("seed", seed)
    }
    return StrictJson.canonical(result)
}

private val ISO_LOCAL_DATE_TIME_PREFIX = Regex("(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})")

internal fun List<WidgetToolOutcome>.toJson(): String {
    val root = JsonObject()
    forEach { outcome ->
        val value = JsonObject()
        if (outcome.payload != null) {
            value.addProperty("status", "success")
            value.add("payload", StrictJson.normalize(outcome.payload))
        } else {
            value.addProperty("status", "failure")
            value.addProperty("reason", outcome.rejection?.name?.replaceFirstChar(Char::lowercase) ?: "unavailable")
        }
        root.add(outcome.alias, value)
    }
    val encoded = root.toString()
    require(encoded.utf8Size() <= WidgetRuntimePolicy.MAX_JSON_BYTES)
    return StrictJson.canonical(StrictJson.parse(encoded))
}

internal fun canonicalState(raw: String): String {
    require(raw.utf8Size() <= WidgetRuntimePolicy.MAX_JSON_BYTES)
    return StrictJson.canonical(StrictJson.parse(raw).requiredObject())
}
