package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.jesjobom.ararai.tools.WEB_SEARCH_TOP_RESULTS_TOOL_NAME
import com.jesjobom.ararai.widget.runtime.StrictJson
import com.jesjobom.ararai.widget.runtime.requiredObject
import com.jesjobom.ararai.widget.runtime.requiredString
import com.jesjobom.ararai.widget.runtime.utf8Size

/** Turns a bounded field mapping into app-owned code that renders each returned item. */
internal object WidgetCollectionListArtifact {
    /** Ranked web results have a fixed schema; no model decision is needed for their presentation. */
    fun rankedWebResults(algorithm: WidgetAlgorithmArtifact): String? {
        val call = algorithm.toolCallSteps.singleOrNull()
        val supported = algorithm.renderCapability == WidgetRenderCapability.CollectionList &&
            call?.tool?.id == WEB_SEARCH_TOP_RESULTS_TOOL_NAME && call.tool.version == 1
        return if (supported) {
            JsonObject().apply {
                addProperty("alias", requireNotNull(call).id)
                addProperty("items", "results")
                addProperty("title", "title")
                addProperty("url", "canonicalUrl")
                addProperty("fallback", "No results found")
            }.toString()
        } else {
            null
        }
    }

    fun parse(raw: String, algorithm: WidgetAlgorithmArtifact): WidgetSourceFragmentParseResult = try {
        require(raw.utf8Size() <= WidgetAuthoringPipelinePolicy.MAX_ARTIFACT_BYTES)
        val config = StrictJson.parse(raw).requiredObject()
        require(config.keySet().containsAll(REQUIRED_FIELDS))
        require(config.keySet().all { it in ALLOWED_FIELDS })
        val alias = config.requiredString("alias")
        require(alias in algorithm.toolCallSteps.map(WidgetAlgorithmStep::id))
        val items = config.requiredString("items")
        require(FIELD_PATH.matches(items))
        val title = config.requiredString("title")
        require(ITEM_FIELD.matches(title))
        val fallback = config.requiredString("fallback")
        require(fallback.isNotBlank() && fallback.length <= 256)
        val body = config.optionalItemField("body")
        val url = config.optionalItemField("url")
        val listConfig = JsonObject().apply {
            addProperty("items", items)
            addProperty("title", title)
            body?.let { addProperty("body", it) }
            url?.let { addProperty("url", it) }
            addProperty("fallback", fallback)
        }
        val source = "function render(runtime, outcomes, state) { " +
            "return runtime.collectionList(outcomes, ${JsonPrimitive(alias)}, $listConfig); }"
        require(source.utf8Size() <= WidgetAuthoringPipelinePolicy.MAX_FRAGMENT_BYTES)
        WidgetSourceFragmentParseResult.Valid(
            WidgetSourceFragmentArtifact("render", "render", listOf("runtime", "outcomes", "state"), source),
        )
    } catch (_: RuntimeException) {
        WidgetSourceFragmentParseResult.Invalid(WidgetAuthoringStageFailureCode.InvalidPresentationFields)
    }

    private fun JsonObject.optionalItemField(name: String): String? = get(name)?.let {
        requiredString(name).also { field -> require(ITEM_FIELD.matches(field)) }
    }

    private val REQUIRED_FIELDS = setOf("alias", "items", "title", "fallback")
    private val ALLOWED_FIELDS = REQUIRED_FIELDS + setOf("body", "url")
    private val ITEM_FIELD = Regex("[A-Za-z][A-Za-z0-9_]{0,63}")
    private val FIELD_PATH = Regex("[A-Za-z][A-Za-z0-9_]{0,63}(?:\\.[A-Za-z][A-Za-z0-9_]{0,63}){0,2}")
}
