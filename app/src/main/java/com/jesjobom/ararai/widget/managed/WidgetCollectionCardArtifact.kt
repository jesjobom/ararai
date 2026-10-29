package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.jesjobom.ararai.tools.WEB_SEARCH_CONTENT_TOOL_NAME
import com.jesjobom.ararai.widget.runtime.StrictJson
import com.jesjobom.ararai.widget.runtime.WidgetToolCapability
import com.jesjobom.ararai.widget.runtime.requiredObject
import com.jesjobom.ararai.widget.runtime.requiredString
import com.jesjobom.ararai.widget.runtime.utf8Size

/** Converts a bounded model decision into application-owned JavaScript. */
internal object WidgetCollectionCardArtifact {
    /** Version 2 already selects one page and supplies its complete introduction. */
    fun selectedWikipediaPage(algorithm: WidgetAlgorithmArtifact): String? {
        val call = algorithm.toolCallSteps.singleOrNull()
        if (algorithm.renderCapability != WidgetRenderCapability.CollectionCard ||
            algorithm.steps.size != 1 ||
            call?.tool != WidgetToolCapability("wikipedia_pages", 2)
        ) {
            return null
        }
        return JsonObject().apply {
            addProperty("alias", call.id)
            addProperty("items", "pages")
            addProperty("title", "title")
            addProperty("body", "extract")
            addProperty("url", "canonicalUrl")
            addProperty("fallback", "Nenhuma página encontrada.")
            addProperty("selection", "first")
        }.toString()
    }

    /** A date event has a bounded year field, so its requested title can be derived locally. */
    fun eventYearTitle(
        algorithm: WidgetAlgorithmArtifact,
        prompt: WidgetAuthoringPrompt,
        feasibility: WidgetFeasibilityArtifact,
    ): String? {
        val call = algorithm.toolCallSteps.singleOrNull()
        if (!requestedEventYearTitle(prompt, feasibility) ||
            algorithm.renderCapability != WidgetRenderCapability.CollectionCard ||
            call?.tool != WidgetToolCapability("wikipedia_on_this_day", 1)
        ) {
            return null
        }
        val random = Regex("(?i)\\b(?:aleat[oó]ri[oa]?|random)\\b").containsMatchIn(prompt.userInstruction)
        return JsonObject().apply {
            addProperty("alias", call.id)
            addProperty("items", "events")
            addProperty("title", "title")
            addProperty("titleSuffixField", "year")
            addProperty("body", "text")
            addProperty("url", "canonicalUrl")
            addProperty("fallback", "Nenhum evento encontrado para hoje.")
            addProperty("selection", if (random) "seeded_random" else "first")
        }.toString()
    }

    /** The highest-ranked content result has a fixed mapping and needs no model-authored code. */
    fun rankedWebContent(
        algorithm: WidgetAlgorithmArtifact,
        prompt: WidgetAuthoringPrompt,
        feasibility: WidgetFeasibilityArtifact,
    ): String? {
        val call = algorithm.toolCallSteps.singleOrNull()
        val supportedRequest = requestedRankedWebContentCard(prompt, feasibility) &&
            algorithm.renderCapability == WidgetRenderCapability.CollectionCard
        val supportedTool = call?.tool?.id == WEB_SEARCH_CONTENT_TOOL_NAME && call.tool.version == 1
        return if (supportedRequest && supportedTool) {
            JsonObject().apply {
                addProperty("alias", requireNotNull(call).id)
                addProperty("items", "results")
                addProperty("title", "title")
                addProperty("body", "excerpt")
                addProperty("url", "canonicalUrl")
                addProperty("fallback", "No results found")
                addProperty("selection", "first")
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
        val titleSuffixField = config.optionalItemField("titleSuffixField")
        val fallback = config.requiredString("fallback")
        require(fallback.isNotBlank() && fallback.length <= 256)
        val selection = config.requiredString("selection")
        require(selection in setOf("first", "seeded_random"))
        val body = config.optionalItemField("body")
        val url = config.optionalItemField("url")
        val cardConfig = JsonObject().apply {
            addProperty("items", items)
            addProperty("title", title)
            titleSuffixField?.let { addProperty("titleSuffixField", it) }
            body?.let { addProperty("body", it) }
            url?.let { addProperty("url", it) }
            addProperty("fallback", fallback)
            addProperty("selection", selection)
        }
        val source = "function render(runtime, outcomes, state) { " +
            "return runtime.collectionCard(outcomes, ${JsonPrimitive(alias)}, $cardConfig); }"
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

    private val REQUIRED_FIELDS = setOf("alias", "items", "title", "fallback", "selection")
    private val ALLOWED_FIELDS = REQUIRED_FIELDS + setOf("body", "url", "titleSuffixField")
    private val ITEM_FIELD = Regex("[A-Za-z][A-Za-z0-9_]{0,63}")
    private val FIELD_PATH = Regex("[A-Za-z][A-Za-z0-9_]{0,63}(?:\\.[A-Za-z][A-Za-z0-9_]{0,63}){0,2}")
}
