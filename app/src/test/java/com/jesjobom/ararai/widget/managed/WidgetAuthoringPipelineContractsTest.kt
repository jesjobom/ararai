@file:Suppress("MaxLineLength")

package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeValue
import com.jesjobom.ararai.widget.runtime.WidgetToolCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetAuthoringPipelineContractsTest {
    @Test
    fun `feasibility accepts a bounded achievable envelope`() {
        val parsed = WidgetFeasibilityParser.parse(achievable().toString(), AVAILABLE_TOOLS)

        assertTrue(parsed is WidgetFeasibilityParseResult.Valid)
        val artifact = (parsed as WidgetFeasibilityParseResult.Valid).artifact
        assertEquals(WidgetFeasibilityOutcome.Achievable, artifact.outcome)
        assertEquals(listOf(TOOL), artifact.tools.map { it.capability })
        assertEquals(setOf(WidgetRuntimeValue.Locale, WidgetRuntimeValue.LocalTime), artifact.runtimeValues)
    }

    @Test
    fun `feasibility rejects additional fields unsupported tools and inconsistent terminal outcomes`() {
        val additional = achievable().apply { addProperty("endpoint", "https://example.com") }
        val invented = achievable().apply {
            getAsJsonArray("tools").single().asJsonObject.addProperty("id", "http_get")
        }
        val terminalWithAuthority = achievable().apply {
            addProperty("outcome", "unachievable")
            addProperty("reason", "No registered operation")
        }

        listOf(additional, invented, terminalWithAuthority).forEach { artifact ->
            assertEquals(
                WidgetFeasibilityParseResult.Invalid(WidgetAuthoringStageFailureCode.InvalidSchema),
                WidgetFeasibilityParser.parse(artifact.toString(), AVAILABLE_TOOLS),
            )
        }
    }

    @Test
    fun `feasibility accepts unachievable and clarification only without authority`() {
        val unachievable = terminal("unachievable", reason = "No supported provider")
        val clarification = terminal("needs_clarification", question = "Which language should be used?")

        assertTrue(
            WidgetFeasibilityParser.parse(unachievable.toString(), AVAILABLE_TOOLS) is
                WidgetFeasibilityParseResult.Valid,
        )
        assertTrue(
            WidgetFeasibilityParser.parse(clarification.toString(), AVAILABLE_TOOLS) is
                WidgetFeasibilityParseResult.Valid,
        )
    }

    @Test
    fun `algorithm accepts ordered independent calls within the frozen envelope`() {
        val feasibility = validFeasibility()
        val parsed = WidgetAlgorithmParser.parse(algorithm().toString(), feasibility)

        assertTrue(parsed is WidgetAlgorithmParseResult.Valid)
        assertEquals(1, (parsed as WidgetAlgorithmParseResult.Valid).artifact.toolCallSteps.size)
        assertEquals(TOOL, parsed.artifact.toolCallSteps.single().tool)
    }

    @Test
    fun `algorithm rejects duplicate cyclic invented and live-result-dependent steps`() {
        val duplicate = algorithm().apply {
            getAsJsonArray("steps").add(getAsJsonArray("steps").last().deepCopy())
        }
        val invented = algorithm().apply {
            getAsJsonArray("steps").last().asJsonObject.addProperty("toolId", "http_get")
        }
        val liveDependent = algorithm().apply {
            getAsJsonArray("steps").add(
                toolStep(
                    id = "second_call",
                    dependsOn = listOf("events_call"),
                ),
            )
        }
        val forwardDependency = algorithm().apply {
            getAsJsonArray("steps").first().asJsonObject
                .getAsJsonArray("dependsOn")
                .add("events_call")
        }

        listOf(duplicate, invented, liveDependent, forwardDependency).forEach { artifact ->
            assertEquals(
                WidgetAlgorithmParseResult.Invalid(WidgetAuthoringStageFailureCode.InvalidAlgorithm),
                WidgetAlgorithmParser.parse(artifact.toString(), validFeasibility()),
            )
        }
    }

    @Test
    fun `source fragment freezes identity signature and one safe generated function`() {
        val parsed = WidgetSourceFragmentParser.parse(
            fragment("call_events", "buildCallEvents", listOf("runtime"), VALID_CALL_SOURCE).toString(),
            expectedArtifactId = "call_events",
            expectedFunctionName = "buildCallEvents",
            expectedInputNames = listOf("runtime"),
        )

        assertTrue(parsed is WidgetSourceFragmentParseResult.Valid)
        assertEquals(VALID_CALL_SOURCE, (parsed as WidgetSourceFragmentParseResult.Valid).artifact.source)
    }

    @Test
    fun `source fragment rejects renamed extra and ambient functions`() {
        val renamed = fragment("call_events", "renamed", listOf("runtime"), VALID_CALL_SOURCE)
        val extra = fragment(
            "call_events",
            "buildCallEvents",
            listOf("runtime"),
            VALID_CALL_SOURCE + "\nfunction hidden() { return 1; }",
        )
        val ambient = fragment(
            "call_events",
            "buildCallEvents",
            listOf("runtime"),
            "function buildCallEvents(runtime) { return Date.now(); }",
        )

        listOf(renamed, extra, ambient).forEach { artifact ->
            assertEquals(
                WidgetSourceFragmentParseResult.Invalid(WidgetAuthoringStageFailureCode.InvalidSource),
                WidgetSourceFragmentParser.parse(
                    artifact.toString(),
                    "call_events",
                    "buildCallEvents",
                    listOf("runtime"),
                ),
            )
        }
    }

    @Test
    fun `attempt budget globally bounds generations and repairs`() {
        val budget = WidgetAuthoringAttemptBudget(maximumGenerations = 3, maximumRepairs = 2)

        assertTrue(budget.consumeGeneration(isRepair = false))
        assertTrue(budget.consumeGeneration(isRepair = true))
        assertTrue(budget.consumeGeneration(isRepair = true))
        assertEquals(2, budget.repairCount())
        assertEquals(false, budget.consumeGeneration(isRepair = false))
        assertEquals(false, budget.consumeGeneration(isRepair = true))
    }

    private fun validFeasibility(): WidgetFeasibilityArtifact = (WidgetFeasibilityParser.parse(achievable().toString(), AVAILABLE_TOOLS) as WidgetFeasibilityParseResult.Valid)
        .artifact

    private fun achievable() = JsonObject().apply {
        addProperty("protocolVersion", 1)
        addProperty("outcome", "achievable")
        addProperty("displayName", "Today in history")
        addProperty("enabled", true)
        addProperty("periodicIntervalHours", 24)
        add(
            "tools",
            JsonArray().apply {
                add(
                    JsonObject().apply {
                        addProperty("id", TOOL.id)
                        addProperty("version", TOOL.version)
                        addProperty("purpose", "Load historical events for the current date")
                    },
                )
            },
        )
        add(
            "runtime",
            JsonArray().apply {
                add("locale")
                add("local_time")
            },
        )
        add(
            "presentation",
            JsonArray().apply {
                add("card")
                add("text")
            },
        )
        add("reason", JsonNull.INSTANCE)
        add("clarificationQuestion", JsonNull.INSTANCE)
    }

    private fun terminal(outcome: String, reason: String? = null, question: String? = null) = achievable().apply {
        addProperty("outcome", outcome)
        add("tools", JsonArray())
        add("runtime", JsonArray())
        add("presentation", JsonArray())
        add("reason", reason?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
        add("clarificationQuestion", question?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
    }

    private fun algorithm() = JsonObject().apply {
        addProperty("protocolVersion", 1)
        add(
            "steps",
            JsonArray().apply {
                add(
                    JsonObject().apply {
                        addProperty("id", "current_date")
                        addProperty("kind", "runtime_input")
                        addProperty("objective", "Read the current local date and language")
                        add("dependsOn", JsonArray())
                        add("toolId", JsonNull.INSTANCE)
                        add("contractVersion", JsonNull.INSTANCE)
                        add(
                            "runtimeInputs",
                            JsonArray().apply {
                                add("locale")
                                add("local_time")
                            },
                        )
                    },
                )
                add(toolStep("events_call", listOf("current_date")))
            },
        )
        addProperty("presentationObjective", "Show one event and a proven Wikipedia link")
    }

    private fun toolStep(id: String, dependsOn: List<String>) = JsonObject().apply {
        addProperty("id", id)
        addProperty("kind", "tool_call")
        addProperty("objective", "Request the events for the current date")
        add("dependsOn", JsonArray().apply { dependsOn.forEach(::add) })
        addProperty("toolId", TOOL.id)
        addProperty("contractVersion", TOOL.version)
        add(
            "runtimeInputs",
            JsonArray().apply {
                add("locale")
                add("local_time")
            },
        )
    }

    private fun fragment(id: String, name: String, inputs: List<String>, source: String) = JsonObject().apply {
        addProperty("protocolVersion", 1)
        addProperty("artifactId", id)
        addProperty("functionName", name)
        add("inputNames", JsonArray().apply { inputs.forEach(::add) })
        addProperty("source", source)
    }

    companion object {
        private val TOOL = WidgetToolCapability("wikipedia_on_this_day", 1)
        private val AVAILABLE_TOOLS = setOf(TOOL)
        private const val VALID_CALL_SOURCE = """function buildCallEvents(runtime) {
  const now = runtime.currentLocalDateTime();
  return {alias:'events',toolId:'wikipedia_on_this_day',contractVersion:1,arguments:{month:now.month,day:now.day,language:runtime.language}};
}"""
    }
}
