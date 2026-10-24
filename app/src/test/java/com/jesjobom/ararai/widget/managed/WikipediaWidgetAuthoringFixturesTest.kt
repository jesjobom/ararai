@file:Suppress("MaxLineLength")

package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.jesjobom.ararai.tools.ApplicationTool
import com.jesjobom.ararai.tools.ApplicationToolCategory
import com.jesjobom.ararai.tools.ApplicationToolConsumer
import com.jesjobom.ararai.tools.ApplicationToolContract
import com.jesjobom.ararai.tools.ApplicationToolOperationalState
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.tools.applicationToolBinding
import com.jesjobom.ararai.widget.runtime.PlannedWidgetToolCall
import com.jesjobom.ararai.widget.runtime.WidgetPresentationCapability
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeValue
import com.jesjobom.ararai.widget.runtime.WidgetToolCapability
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WikipediaWidgetAuthoringFixturesTest {
    @Test
    fun `English and Portuguese create fixtures use date locale seed and safe Wikipedia presentation`() = runTest {
        listOf(
            WikipediaAuthoringFixture.englishCreate(),
            WikipediaAuthoringFixture.portugueseCreate(),
        ).forEach { fixture ->
            val parsed = WidgetProposalParser.parse(fixture.proposalJson, setOf(WIKIPEDIA_TOOL))
            assertTrue(parsed is WidgetProposalParseResult.Valid)
            val proposal = (parsed as WidgetProposalParseResult.Valid).proposal
            assertEquals(setOf(WIKIPEDIA_TOOL), proposal.tools)
            assertTrue(proposal.runtimeValues.containsAll(setOf(WidgetRuntimeValue.Locale, WidgetRuntimeValue.LocalTime, WidgetRuntimeValue.Seed)))
            assertTrue(proposal.presentation.containsAll(setOf(WidgetPresentationCapability.Card, WidgetPresentationCapability.HttpsLink)))
            assertTrue(proposal.source.contains("context.localTime"))
            assertTrue(proposal.source.contains("context.seed"))
            assertTrue(proposal.source.contains("events."))
            assertFalse(proposal.source.contains("fetch("))

            val built = builder(fixture.language).build(fixture.proposalJson, CONTEXT)
            assertTrue(built is WidgetDraftBuildResult.Valid)
            val draft = (built as WidgetDraftBuildResult.Valid).draft
            assertEquals(24L, draft.summary.periodicIntervalHours)
            assertEquals(setOf("open_validated_https_link"), draft.summary.actions)
            assertEquals(listOf("wikipedia_on_this_day"), draft.summary.plannedToolIds)
        }
    }

    @Test
    fun `edit fixture produces a complete replacement and semantic source diff`() = runTest {
        val initial = (builder("en").build(WikipediaAuthoringFixture.englishCreate().proposalJson, CONTEXT) as WidgetDraftBuildResult.Valid).draft
        val edited = (builder("en").build(WikipediaAuthoringFixture.englishEdit().proposalJson, CONTEXT) as WidgetDraftBuildResult.Valid).draft
        val definition = ManagedWidgetDefinition(
            id = "widget-1",
            displayName = initial.summary.displayName,
            enabled = true,
            periodicIntervalHours = initial.summary.periodicIntervalHours,
            activeRevision = 1,
            consentDigest = initial.consentDigest,
            status = ManagedWidgetStatus.Ready,
            createdAtMillis = 1,
            updatedAtMillis = 1,
        )
        val revision = WidgetProgramRevision(
            widgetId = definition.id,
            revision = 1,
            manifestJson = initial.program.canonicalManifestJson,
            source = initial.program.source,
            programDigest = initial.programDigest,
            createdAtMillis = 1,
        )

        val diff = diffWidgetDraft(definition, revision, edited)

        assertTrue(diff.displayNameChanged)
        assertTrue(diff.source.changed)
        assertFalse(diff.expandsAuthority)
    }

    @Test
    fun `impossible arbitrary endpoint fixture is rejected before planning`() {
        val result = WidgetProposalParser.parse(
            WikipediaAuthoringFixture.impossibleEndpoint(),
            setOf(WIKIPEDIA_TOOL),
        )

        assertEquals(
            WidgetProposalParseResult.Invalid(WidgetProposalFailureCode.UnsupportedTool),
            result,
        )
    }

    private fun builder(language: String): WidgetDraftBuilder = WidgetDraftBuilder(
        registry = registry(),
        planner = WidgetDraftPlanner { _, _, _ ->
            WidgetDraftPlanResult.Valid(
                listOf(
                    PlannedWidgetToolCall(
                        alias = "events",
                        tool = WIKIPEDIA_TOOL,
                        argumentsJson = """{"month":9,"day":7,"language":"$language"}""",
                    ),
                ),
            )
        },
    )

    private fun registry() = ApplicationToolRegistry(
        listOf(
            applicationToolBinding(
                contract = ApplicationToolContract(
                    id = WIKIPEDIA_TOOL.id,
                    version = WIKIPEDIA_TOOL.version,
                    displayName = "Wikipedia events by date",
                    category = ApplicationToolCategory.ExternalKnowledge,
                    consumers = setOf(ApplicationToolConsumer.Model, ApplicationToolConsumer.Widget),
                    inputSchemaJson = "{}",
                    outputSchemaJson = "{}",
                ),
                state = { ApplicationToolOperationalState(enabled = true, ready = true) },
                executor = ApplicationTool("Wikipedia events by date", ApplicationToolCategory.ExternalKnowledge) { _: Unit -> Unit },
                decodeArguments = { arguments ->
                    Unit.takeIf {
                        arguments.keySet() == setOf("month", "day", "language") &&
                            arguments.get("language")?.asString in setOf("en", "pt")
                    }
                },
                encodeResult = { "{}" },
            ),
        ),
    )

    companion object {
        private val WIKIPEDIA_TOOL = WidgetToolCapability("wikipedia_on_this_day", 1)
        private val CONTEXT = WidgetRuntimeContext("en-CA", "America/Toronto", "2026-09-07T12:00:00-04:00", 42)
    }
}

internal data class WikipediaAuthoringFixture(
    val language: String,
    val proposalJson: String,
) {
    companion object {
        fun englishCreate() = fixture("Today in history", "en", SOURCE_EN)
        fun portugueseCreate() = fixture("Hoje na história", "pt", SOURCE_PT)
        fun englishEdit() = fixture("A historical event today", "en", SOURCE_EN.replace("Read related article", "Open Wikipedia article"))

        fun impossibleEndpoint(): String = proposal(
            displayName = "Remote page",
            language = "en",
            source = SOURCE_EN,
            toolId = "http_get",
        )

        private fun fixture(name: String, language: String, source: String) = WikipediaAuthoringFixture(
            language,
            proposal(name, language, source),
        )

        private fun proposal(
            displayName: String,
            language: String,
            source: String,
            toolId: String = "wikipedia_on_this_day",
        ): String = JsonObject().apply {
            addProperty("proposalVersion", 1)
            addProperty("displayName", displayName)
            addProperty("enabled", true)
            addProperty("periodicIntervalHours", 24)
            add(
                "tools",
                JsonArray().apply {
                    add(
                        JsonObject().apply {
                            addProperty("id", toolId)
                            addProperty("version", 1)
                        },
                    )
                },
            )
            add("runtime", JsonArray().apply { listOf("locale", "local_time", "seed").forEach(::add) })
            add(
                "presentation",
                JsonArray().apply {
                    listOf("card", "column", "row", "text", "value", "icon", "https_link").forEach(::add)
                },
            )
            addProperty("source", source.replace("__LANGUAGE__", language))
        }.toString()

        private const val SOURCE_EN = """function plan(context) {
  const date = context.localTime.slice(5, 10).split('-');
  return [{alias:'events',toolId:'wikipedia_on_this_day',contractVersion:1,arguments:{month:Number(date[0]),day:Number(date[1]),language:'__LANGUAGE__'}}];
}
function render(context, outcomes, state) {
  const result = outcomes.events;
  if (!result || result.status !== 'success' || !result.payload.events.length) return {type:'text',text:'No event available',tone:'warning'};
  const index = Math.abs(Number(context.seed)) % result.payload.events.length;
  const event = result.payload.events[index];
  return {type:'card',child:{type:'column',children:[{type:'row',children:[{type:'icon',name:'calendar'},{type:'value',text:String(event.year),tone:'neutral'}]},{type:'text',text:event.text,tone:'neutral'},{type:'https_link',label:'Read related article',url:event.canonicalUrl,sourceAlias:'events',sourceField:'events.' + index + '.canonicalUrl'}]}};
}"""

        private const val SOURCE_PT = """function plan(context) {
  const data = context.localTime.slice(5, 10).split('-');
  return [{alias:'events',toolId:'wikipedia_on_this_day',contractVersion:1,arguments:{month:Number(data[0]),day:Number(data[1]),language:'__LANGUAGE__'}}];
}
function render(context, outcomes, state) {
  const result = outcomes.events;
  if (!result || result.status !== 'success' || !result.payload.events.length) return {type:'text',text:'Nenhum evento disponível',tone:'warning'};
  const index = Math.abs(Number(context.seed)) % result.payload.events.length;
  const event = result.payload.events[index];
  return {type:'card',child:{type:'column',children:[{type:'row',children:[{type:'icon',name:'calendar'},{type:'value',text:String(event.year),tone:'neutral'}]},{type:'text',text:event.text,tone:'neutral'},{type:'https_link',label:'Ler artigo relacionado',url:event.canonicalUrl,sourceAlias:'events',sourceField:'events.' + index + '.canonicalUrl'}]}};
}"""
    }
}
