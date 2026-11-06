package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Presentation routing shared by the algorithm schema, context, and controlled repair. */
internal enum class WidgetAlgorithmPresentationRoute(
    val capability: WidgetRenderCapability,
    val summary: String,
) {
    DirectItem(
        WidgetRenderCapability.CollectionCard,
        "One unfiltered item from one tool-result array, selected first or seeded-random in the later card stage. " +
            "No post-result selection step, filtering, or conditional selection.",
    ),
    DirectList(
        WidgetRenderCapability.CollectionList,
        "Up to ten unmodified items from one tool-result array, in order. " +
            "Formatting and links need no post-result step; no filtering.",
    ),
    ProcessedResult(
        WidgetRenderCapability.CustomSource,
        "Bounded deterministic JavaScript render for actual filtering, comparisons, conditional selection, " +
            "or other layouts over returned data only.",
    ),
    ;

    fun policy(toolId: String? = null, selection: WidgetToolSelectionMode? = null): JsonObject = JsonObject().apply {
        addProperty("renderCapability", capability.wireName)
        toolId?.let { addProperty("toolId", it) }
        selection?.let { addProperty("selectionInCardStage", it.wireName) }
        addProperty("postResultStepForPresentationOnly", false)
    }
}

internal object WidgetAlgorithmPromptContract {
    const val OBJECTIVE: String =
        "Preserve required data operations as typed steps. Presentation-only selection, formatting, and links " +
            "are not transform steps. Choose a renderCapability from the validated catalog. " +
            "Runtime-conditioned queries may use collection modes; tool calls cannot read live results."

    fun schema(): String = JsonParser.parseString(WidgetAuthoringStageSchemas.algorithmTemplate).asJsonObject.apply {
        val properties = getAsJsonObject("parameters").getAsJsonObject("properties")
        val stepProperties = properties.getAsJsonObject("steps").getAsJsonObject("items")
            .getAsJsonObject("properties")
        stepProperties.getAsJsonObject("kind").add(
            "enum",
            enumArray(WidgetAlgorithmStepKind.entries.map { it.wireName }),
        )
        stepProperties.getAsJsonObject("selectionMode").add(
            "enum",
            enumArray(WidgetToolSelectionMode.entries.map { it.wireName }),
        )
        properties.getAsJsonObject("renderCapability").add(
            "enum",
            enumArray(WidgetAlgorithmPresentationRoute.entries.map { it.capability.wireName }),
        )
    }.toString()

    fun renderCatalog(): JsonArray = JsonArray().apply {
        WidgetAlgorithmPresentationRoute.entries.forEach { route ->
            add(
                JsonObject().apply {
                    addProperty("id", route.capability.wireName)
                    addProperty("summary", route.summary)
                },
            )
        }
    }

    fun randomItemPolicy(): JsonObject = WidgetAlgorithmPresentationRoute.DirectItem.policy(
        selection = WidgetToolSelectionMode.SeededRandom,
    )

    fun repairInstruction(): String {
        val kinds = WidgetAlgorithmStepKind.entries.joinToString { it.wireName }
        val capabilities = WidgetAlgorithmPresentationRoute.entries.joinToString { it.capability.wireName }
        val direct = WidgetAlgorithmPresentationRoute.DirectItem.capability.wireName
        val list = WidgetAlgorithmPresentationRoute.DirectList.capability.wireName
        val processed = WidgetAlgorithmPresentationRoute.ProcessedResult.capability.wireName
        val random = WidgetToolSelectionMode.SeededRandom.wireName
        return "steps[].kind accepts only $kinds; renderCapability accepts only $capabilities. " +
            "For one unfiltered random item use $direct; $random is chosen in the later card stage, " +
            "not in an extra step. " +
            "For an unmodified list use $list; for actual post-result filtering or comparison use $processed. " +
            "Dependent steps require dependsOn; selectionMode applies only to wikipedia_pages version 2 tool calls."
    }
}

private fun enumArray(values: List<String>): JsonArray = JsonArray().apply { values.forEach(::add) }
