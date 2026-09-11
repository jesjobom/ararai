package com.jesjobom.ararai.widget.runtime

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetExecutionProtocolTest {
    private val tool = WidgetToolCapability("weather_lookup", 1)
    private val capabilities = WidgetProgramCapabilities(
        tools = setOf(tool),
        runtimeValues = WidgetRuntimeValue.entries.toSet(),
        presentation = WidgetPresentationCapability.entries.toSet(),
    )
    private val grant = WidgetExecutionGrant(
        tools = setOf(tool),
        runtimeValues = WidgetRuntimeValue.entries.toSet(),
        presentation = WidgetPresentationCapability.entries.toSet(),
    )
    private val limits = WidgetRequestedLimits(8_388_608, 524_288, 250, 4, 65_536)

    @Test
    fun `runtime projection provides contract-ready language and calendar fields under existing grants`() {
        val context = WidgetRuntimeContext("pt-BR", "America/Sao_Paulo", "2026-09-10T13:25:00-03:00", 42)

        assertEquals(
            """{"apiVersion":1,"language":"pt","localDay":1E+1,"localHour":13,"localMinute":25,""" +
                """"localMonth":9,"localSecond":0,"localTime":"2026-09-10T13:25:00-03:00",""" +
                """"localYear":2026,"locale":"pt-BR","seed":42}""",
            context.toJson(setOf(WidgetRuntimeValue.Locale, WidgetRuntimeValue.LocalTime, WidgetRuntimeValue.Seed)),
        )
        assertEquals(
            """{"apiVersion":1,"timezone":"America/Sao_Paulo"}""",
            context.toJson(setOf(WidgetRuntimeValue.Timezone)),
        )
    }

    @Test
    fun `normalizes a valid bounded plan`() {
        val result = WidgetPlanParser.parse(
            """[{"alias":"today","toolId":"weather_lookup","contractVersion":1,"arguments":{"b":2,"a":1}}]""",
            capabilities,
            grant,
            limits,
        )

        assertEquals(listOf(PlannedWidgetToolCall("today", tool, "{\"a\":1,\"b\":2}")), result)
    }

    @Test
    fun `rejects duplicate aliases excessive calls and non-array plans`() {
        assertInvalidPlan(
            """[
                {"alias":"same","toolId":"weather_lookup","contractVersion":1,"arguments":{}},
                {"alias":"same","toolId":"weather_lookup","contractVersion":1,"arguments":{}}
            ]
            """.trimIndent(),
        )
        val call = """{"alias":"a","toolId":"weather_lookup","contractVersion":1,"arguments":{}}"""
        assertInvalidPlan(List(5) { call.replace("\"a\"", "\"a$it\"") }.joinToString(",", "[", "]"))
        assertInvalidPlan("{}")
        assertInvalidPlan(
            """[{
                "alias":"x","toolId":"weather_lookup",
                "contractVersion":1,"arguments":{"n":NaN}
            }]
            """.trimIndent(),
        )
        assertInvalidPlan(" ".repeat(limits.maxOutputBytes + 1))
    }

    @Test
    fun `rejects invented tools versions transports and non-semantic arguments`() {
        assertInvalidPlan(
            """[{"alias":"x","toolId":"shell","contractVersion":1,"arguments":{}}]""",
        )
        assertInvalidPlan(
            """[{"alias":"x","toolId":"weather_lookup","contractVersion":2,"arguments":{}}]""",
        )
        assertInvalidPlan(
            """[{
                "alias":"x","toolId":"weather_lookup","contractVersion":1,
                "arguments":{},"url":"https://evil.test"
            }]
            """.trimIndent(),
        )
        assertInvalidPlan(
            """[{"alias":"x","toolId":"weather_lookup","contractVersion":1,"arguments":[]}]""",
        )
    }

    @Test
    fun `rejects ungranted tool before execution`() {
        assertThrows(IllegalArgumentException::class.java) {
            WidgetPlanParser.parse(
                """[{"alias":"x","toolId":"weather_lookup","contractVersion":1,"arguments":{}}]""",
                capabilities,
                grant.copy(tools = emptySet()),
                limits,
            )
        }
    }

    @Test
    fun `accepts bounded presentation and provenance-bound https link`() {
        val payload = JsonObject().apply { addProperty("url", "https://example.test/today") }
        val result = WidgetPresentationParser.parse(
            """{
                "type":"card",
                "child":{"type":"column","children":[
                  {"type":"text","text":"Forecast","tone":"neutral"},
                  {"type":"https_link","label":"Source",
                   "url":"https://example.test/today","sourceAlias":"today","sourceField":"url"}
                ]}
            }
            """.trimIndent(),
            capabilities,
            grant,
            listOf(WidgetToolOutcome("today", payload, null)),
            limits,
        )

        assertTrue(result is WidgetPresentationNode.Card)
    }

    @Test
    fun `accepts a bounded nested result path for a selected Wikipedia page`() {
        val payload = com.google.gson.JsonParser.parseString(
            """{"pages":[{"canonicalUrl":"https://en.wikipedia.org/wiki/Ada_Lovelace"}]}""",
        ).asJsonObject

        val result = WidgetPresentationParser.parse(
            linkJson(
                url = "https://en.wikipedia.org/wiki/Ada_Lovelace",
                sourceField = "pages.0.canonicalUrl",
            ),
            capabilities,
            grant,
            listOf(WidgetToolOutcome("today", payload, null)),
            limits,
        )

        assertTrue(result is WidgetPresentationNode.HttpsLink)
    }

    @Test
    fun `rejects unsafe or unproven links and executable fields`() {
        val payload = JsonObject().apply { addProperty("url", "https://example.test") }
        val outcomes = listOf(WidgetToolOutcome("today", payload, null))
        assertInvalidPresentation(
            linkJson("javascript:alert(1)"),
            outcomes,
        )
        assertInvalidPresentation(
            linkJson("https://other.test"),
            outcomes,
        )
        assertInvalidPresentation(
            """{"type":"text","text":"x","tone":"neutral","onClick":"run()"}""",
            outcomes,
        )
        assertInvalidPresentation("""{"type":"html","html":"<script/>"}""", outcomes)
        assertInvalidPresentation(linkJson("https://example.test", "pages.999.canonicalUrl"), outcomes)
        assertInvalidPresentation(linkJson("https://example.test", "pages.-1.canonicalUrl"), outcomes)
    }

    @Test
    fun `rejects depth node child and text bombs`() {
        var deep = """{"type":"text","text":"x","tone":"neutral"}"""
        repeat(9) { deep = """{"type":"card","child":$deep}""" }
        assertInvalidPresentation(deep)

        val text = "x".repeat(WidgetRuntimePolicy.MAX_TEXT_CHARS + 1)
        assertInvalidPresentation("""{"type":"text","text":"$text","tone":"neutral"}""")

        val child = """{"type":"text","text":"x","tone":"neutral"}"""
        assertInvalidPresentation(List(33) { child }.joinToString(",", "{\"type\":\"row\",\"children\":[", "]}"))
        assertInvalidPresentation(" ".repeat(limits.maxOutputBytes + 1))
    }

    @Test
    fun `cached presentation decoder preserves only the fixed bounded tree`() {
        val original = WidgetPresentationNode.Card(
            WidgetPresentationNode.Column(
                listOf(
                    WidgetPresentationNode.Icon("info"),
                    WidgetPresentationNode.Text("Ada Lovelace", "neutral"),
                    WidgetPresentationNode.HttpsLink(
                        label = "Wikipedia",
                        url = "https://en.wikipedia.org/wiki/Ada_Lovelace",
                        sourceAlias = "article",
                        sourceField = "canonicalUrl",
                    ),
                ),
            ),
        )

        assertEquals(original, WidgetPresentationCodec.decodeCached(WidgetPresentationCodec.encode(original)))
    }

    @Test
    fun `cached presentation decoder rejects executable unsupported and oversized content`() {
        assertInvalidCachedPresentation(
            """{"type":"text","text":"x","tone":"neutral","onClick":"run()"}""",
        )
        assertInvalidCachedPresentation("""{"type":"webview","url":"https://example.test"}""")
        assertInvalidCachedPresentation(
            """{"type":"icon","name":"downloaded-code"}""",
        )
        val text = "x".repeat(WidgetRuntimePolicy.MAX_TEXT_CHARS + 1)
        assertInvalidCachedPresentation("""{"type":"text","text":"$text","tone":"neutral"}""")
    }

    private fun assertInvalidPlan(raw: String) {
        assertThrows(Exception::class.java) { WidgetPlanParser.parse(raw, capabilities, grant, limits) }
    }

    private fun assertInvalidPresentation(raw: String, outcomes: List<WidgetToolOutcome> = emptyList()) {
        assertThrows(Exception::class.java) {
            WidgetPresentationParser.parse(raw, capabilities, grant, outcomes, limits)
        }
    }

    private fun assertInvalidCachedPresentation(raw: String) {
        assertThrows(Exception::class.java) { WidgetPresentationCodec.decodeCached(raw) }
    }

    private fun linkJson(url: String, sourceField: String = "url") = """{
        "type":"https_link","label":"x","url":"$url",
        "sourceAlias":"today","sourceField":"$sourceField"
    }
    """.trimIndent()
}
