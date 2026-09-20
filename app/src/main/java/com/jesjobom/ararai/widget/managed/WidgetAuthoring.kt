@file:Suppress("MaxLineLength", "ReturnCount", "TooGenericExceptionCaught", "TooManyFunctions")

package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jesjobom.ararai.engine.EphemeralLocalLlmTool
import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.GenerationFailureKind
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.PromptChatMessage
import com.jesjobom.ararai.engine.PromptChatRole
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.PROPOSE_WIDGET_TOOL_NAME
import com.jesjobom.ararai.tools.ApplicationToolCategory
import com.jesjobom.ararai.tools.ApplicationToolConsumer
import com.jesjobom.ararai.tools.ApplicationToolContract
import com.jesjobom.ararai.widget.runtime.StrictJson
import com.jesjobom.ararai.widget.runtime.WidgetPresentationCapability
import com.jesjobom.ararai.widget.runtime.WidgetProgramParser
import com.jesjobom.ararai.widget.runtime.WidgetProgramValidationResult
import com.jesjobom.ararai.widget.runtime.WidgetRuntimePolicy
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeValue
import com.jesjobom.ararai.widget.runtime.WidgetToolCapability
import com.jesjobom.ararai.widget.runtime.requireFields
import com.jesjobom.ararai.widget.runtime.requiredArray
import com.jesjobom.ararai.widget.runtime.requiredInt
import com.jesjobom.ararai.widget.runtime.requiredObject
import com.jesjobom.ararai.widget.runtime.requiredString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal data class UntrustedWidgetProposal(
    val displayName: String,
    val enabled: Boolean,
    val periodicIntervalHours: Long?,
    val tools: Set<WidgetToolCapability>,
    val runtimeValues: Set<WidgetRuntimeValue>,
    val presentation: Set<WidgetPresentationCapability>,
    val source: String,
)

internal enum class WidgetProposalFailureCode {
    Malformed,
    TooLarge,
    UnsupportedSchedule,
    UnsupportedTool,
    UnsupportedCapability,
}

internal sealed interface WidgetProposalParseResult {
    data class Valid(val proposal: UntrustedWidgetProposal) : WidgetProposalParseResult
    data class Invalid(val code: WidgetProposalFailureCode) : WidgetProposalParseResult
}

internal object WidgetProposalParser {
    @Suppress("ReturnCount")
    fun parse(
        raw: String,
        availableTools: Set<WidgetToolCapability>,
    ): WidgetProposalParseResult {
        if (raw.utf8Size() > ManagedWidgetPolicy.MAX_PROPOSAL_BYTES) return invalid(WidgetProposalFailureCode.TooLarge)
        return try {
            val root = StrictJson.parse(
                raw = raw,
                maxStringChars = ManagedWidgetPolicy.MAX_PROGRAM_SOURCE_BYTES,
            ).requiredObject()
            root.requireFields(
                "proposalVersion",
                "displayName",
                "enabled",
                "periodicIntervalHours",
                "tools",
                "runtime",
                "presentation",
                "source",
            )
            require(root.requiredInt("proposalVersion") == PROPOSAL_SCHEMA_VERSION)
            val displayName = root.requiredString("displayName")
            require(displayName.isNotBlank() && displayName.length <= MAX_DISPLAY_NAME_CHARS)
            val enabled = root.requiredBoolean("enabled")
            val interval = root.optionalLong("periodicIntervalHours")
            if (interval != null && interval !in ManagedWidgetPolicy.SUPPORTED_PERIODIC_INTERVAL_HOURS) {
                return invalid(WidgetProposalFailureCode.UnsupportedSchedule)
            }
            val tools = root.requiredArray("tools").map { element ->
                val tool = element.requiredObject()
                tool.requireFields("id", "version")
                WidgetToolCapability(tool.requiredString("id"), tool.requiredInt("version"))
            }.toSet().also { require(it.size == root.requiredArray("tools").size()) }
            if (!availableTools.containsAll(tools)) return invalid(WidgetProposalFailureCode.UnsupportedTool)
            val runtime = root.requiredArray("runtime").enumSet(WidgetRuntimeValue.entries) { it.wireName }
            val presentation = root.requiredArray("presentation")
                .enumSet(WidgetPresentationCapability.entries) { it.wireName }
            val source = root.requiredString("source")
            require(source.isNotBlank() && source.utf8Size() <= ManagedWidgetPolicy.MAX_PROGRAM_SOURCE_BYTES)
            WidgetProposalParseResult.Valid(
                UntrustedWidgetProposal(displayName, enabled, interval, tools, runtime, presentation, source),
            )
        } catch (_: UnsupportedCapabilityException) {
            invalid(WidgetProposalFailureCode.UnsupportedCapability)
        } catch (_: RuntimeException) {
            invalid(WidgetProposalFailureCode.Malformed)
        }
    }

    private fun invalid(code: WidgetProposalFailureCode) = WidgetProposalParseResult.Invalid(code)
}

internal data class WidgetAuthoringPrompt(
    val userInstruction: String,
    val apiContextJson: String,
) {
    init {
        require(userInstruction.isNotBlank() && userInstruction.length <= MAX_AUTHORING_PROMPT_CHARS)
        require(apiContextJson.utf8Size() <= ManagedWidgetPolicy.MAX_PROPOSAL_CONTEXT_BYTES)
    }
}

internal object WidgetAuthoringContextBuilder {
    fun build(
        userInstruction: String,
        toolContracts: Collection<ApplicationToolContract>,
        currentDefinition: ManagedWidgetDefinition? = null,
        currentRevision: WidgetProgramRevision? = null,
    ): WidgetAuthoringPrompt {
        require((currentDefinition == null) == (currentRevision == null))
        if (currentDefinition != null) require(currentDefinition.id == currentRevision?.widgetId)
        val widgetToolContracts = toolContracts.filter { ApplicationToolConsumer.Widget in it.consumers }
        val root = JsonObject().apply {
            addProperty("widgetApiVersion", WidgetRuntimePolicy.WIDGET_API_VERSION)
            add("authoringTask", authoringTaskContext(isEdit = currentDefinition != null))
            add(
                "supportedIntervalsHours",
                JsonArray().also { values ->
                    ManagedWidgetPolicy.SUPPORTED_PERIODIC_INTERVAL_HOURS.sorted().forEach(values::add)
                },
            )
            add(
                "supportedRuntimeValues",
                JsonArray().also { values ->
                    WidgetRuntimePolicy.supportedRuntimeValues.map(WidgetRuntimeValue::wireName).sorted().forEach(values::add)
                },
            )
            add(
                "supportedPresentation",
                JsonArray().also { values ->
                    WidgetRuntimePolicy.supportedPresentation
                        .map(WidgetPresentationCapability::wireName)
                        .sorted()
                        .forEach(values::add)
                },
            )
            addProperty("maxSourceBytes", ManagedWidgetPolicy.MAX_PROGRAM_SOURCE_BYTES)
            add("programApi", programApiContext())
            add("tools", widgetToolContracts.toAuthoringTools())
            if (widgetToolContracts.any { it.id == WIKIPEDIA_ON_THIS_DAY_TOOL_ID && it.version == 1 }) {
                add("wikipediaBehaviorGuidance", wikipediaBehaviorGuidance())
            }
            add("currentWidget", currentDefinition?.toAuthoringJson(currentRevision!!) ?: JsonNull.INSTANCE)
        }
        return WidgetAuthoringPrompt(userInstruction, StrictJson.canonical(root))
    }

    private fun authoringTaskContext(isEdit: Boolean): JsonObject = JsonObject().apply {
        addProperty("mode", if (isEdit) "edit" else "create")
        addProperty(
            "instructionSemantics",
            "The user instruction describes the desired widget behavior and content. The current authoring UI " +
                "already establishes this as a widget create or edit operation; do not require the instruction to " +
                "repeat the operation or the word widget.",
        )
    }

    private fun programApiContext(): JsonObject = JsonObject().apply {
        addProperty("planEntrypoint", "function plan(runtime) -> array of tool calls")
        addProperty("renderEntrypoint", "function render(runtime, outcomes, state) -> one presentation node")
        addProperty(
            "toolCallShape",
            "{alias,toolId,contractVersion,arguments}; aliases are unique lowercase identifiers",
        )
        addProperty(
            "outcomeShape",
            "outcomes[alias] is {status:'success',payload:<tool result>} or {status:'failure',reason:<code>}",
        )
        addProperty(
            "presentationNodes",
            "card:{child}; column/row:{children}; text/value:{text,tone}; icon:{name}; " +
                "https_link:{label,url,sourceAlias,sourceField}",
        )
        addProperty("tones", "neutral, muted, positive, warning")
        addProperty("icons", "calendar, clock, info, link, location, warning")
        addProperty(
            "runtimeStandardLibrary",
            "locale grants runtime.locale and runtime.language; local_time grants runtime.currentLocalDateTime() " +
                "returning immutable {iso,year,month,day,hour,minute,second,timezone}; timezone grants " +
                "runtime.timezone; seed grants runtime.seed and runtime.seededIndex(length).",
        )
        addProperty(
            "randomSelection",
            "When the user asks for a random, varied, shuffled, or selected item from a bounded list, including " +
                "aleatório, variado, or sorteado, grant seed and use runtime.seededIndex(length). Math.random is " +
                "unavailable. A fresh execution may select a different item; the same runtime snapshot must select " +
                "the same index.",
        )
        addProperty(
            "linkProvenance",
            "https_link.url must exactly copy a tool-result URL. sourceField is a dotted result path such as " +
                "events.0.canonicalUrl or pages.0.canonicalUrl; array indexes are decimal path segments.",
        )
        addProperty(
            "executionRules",
            "Use synchronous deterministic data-only JavaScript. No network, storage, imports, eval, async, " +
                "timers, host APIs, HTML, callbacks, or arbitrary endpoints. Use only granted context fields.",
        )
        addProperty("stateShape", "state.observations is a bounded array of this widget's typed observations")
        addProperty(
            "wikipediaExample",
            "For a historical event on a calendar date, the call function reads " +
                "runtime.currentLocalDateTime() and passes its month/day plus runtime.language directly to " +
                "wikipedia_on_this_day@1. Render checks outcomes.events.status, selects " +
                "payload.events[runtime.seededIndex(payload.events.length)], and returns a card containing " +
                "the event year and text plus an https_link whose sourceField is 'events.' + selectedIndex + " +
                "'.canonicalUrl'. Use wikipedia_pages@1 instead for direct stable encyclopedic page lookup.",
        )
    }

    private fun wikipediaBehaviorGuidance(): JsonObject = JsonObject().apply {
        addProperty("toolId", WIKIPEDIA_ON_THIS_DAY_TOOL_ID)
        addProperty("contractVersion", 1)
        addProperty("runtime", "local_time, locale, and seed")
        addProperty("arguments", "month/day from runtime.currentLocalDateTime(); language from runtime.language")
        addProperty("selection", "runtime.seededIndex(events.length)")
        addProperty("linkProvenance", "copy canonicalUrl and declare the exact events.N.canonicalUrl source field")
    }

    private fun Collection<ApplicationToolContract>.toAuthoringTools(): JsonArray = JsonArray().also { output ->
        asSequence()
            .filter { ApplicationToolConsumer.Widget in it.consumers }
            .sortedWith(compareBy(ApplicationToolContract::id, ApplicationToolContract::version))
            .forEach { contract ->
                output.add(
                    JsonObject().apply {
                        addProperty("id", contract.id)
                        addProperty("version", contract.version)
                        addProperty("displayName", contract.displayName)
                        addProperty("networkRequired", contract.category == ApplicationToolCategory.ExternalKnowledge)
                        add("inputSchema", JsonParser.parseString(contract.inputSchemaJson))
                        add("outputSchema", JsonParser.parseString(contract.outputSchemaJson))
                    },
                )
            }
    }

    private fun ManagedWidgetDefinition.toAuthoringJson(revision: WidgetProgramRevision): JsonObject {
        val parsed = WidgetProgramParser.parse(revision.manifestJson, revision.source)
        require(parsed is WidgetProgramValidationResult.Valid)
        return JsonObject().apply {
            addProperty("displayName", displayName)
            addProperty("enabled", enabled)
            add(
                "periodicIntervalHours",
                periodicIntervalHours?.let { com.google.gson.JsonPrimitive(it) } ?: JsonNull.INSTANCE,
            )
            add(
                "capabilities",
                JsonParser.parseString(parsed.program.canonicalManifestJson).asJsonObject.get("capabilities"),
            )
            addProperty("source", revision.source)
        }
    }
}

internal sealed interface WidgetAuthorshipResult {
    data class Proposed(val rawArgumentsJson: String) : WidgetAuthorshipResult
    data object IneligibleModel : WidgetAuthorshipResult
    data object ModelLoadFailed : WidgetAuthorshipResult
    data object NoProposal : WidgetAuthorshipResult
    data object GenerationTimedOut : WidgetAuthorshipResult
    data class GenerationFailed(val kind: GenerationFailureKind) : WidgetAuthorshipResult
}

internal class WidgetAuthoringModelController(
    private val engine: LocalLlmEngine,
    private val maxContextTokens: Int = MAX_WIDGET_AUTHORING_CONTEXT_TOKENS,
    private val generationTimeoutMillis: Long = WIDGET_AUTHORING_TIMEOUT_MILLIS,
) {
    init {
        require(maxContextTokens > 0)
        require(generationTimeoutMillis > 0)
    }

    suspend fun propose(
        model: LocalModel,
        inference: InferenceConfig,
        prompt: WidgetAuthoringPrompt,
    ): WidgetAuthorshipResult {
        if (!model.toolCapabilities.supportsAuthoring(PROPOSE_WIDGET_TOOL_NAME)) {
            return WidgetAuthorshipResult.IneligibleModel
        }
        val authoringInference = inference.copy(
            // Authoring has a fixed, isolated budget. Inheriting a smaller Chat setting
            // can leave too little room for the production API context, full proposal
            // schema, and generated source even though the selected model supports this
            // bounded workload.
            contextTokens = maxContextTokens,
            temperature = inference.temperature.coerceAtMost(MAX_WIDGET_AUTHORING_TEMPERATURE),
        )
        try {
            // Widget authorship is a short, isolated structured task. Reusing a large Chat
            // context allocates an unnecessarily large native KV cache and can make Android
            // kill the process before the proposal is captured.
            engine.load(model, authoringInference)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            return WidgetAuthorshipResult.ModelLoadFailed
        }
        return try {
            withTimeoutOrNull(generationTimeoutMillis) { captureProposal(prompt) }
                ?: WidgetAuthorshipResult.GenerationTimedOut
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            WidgetAuthorshipResult.GenerationFailed(GenerationFailureKind.Unexpected)
        }
    }

    private suspend fun captureProposal(prompt: WidgetAuthoringPrompt): WidgetAuthorshipResult {
        val captureTool = WidgetProposalCaptureTool()
        val request = PromptRequest(
            content = com.jesjobom.ararai.chat.MessageContent.TextPrompt(prompt.userInstruction),
            chatMessages = listOf(
                PromptChatMessage(PromptChatRole.System, authoringSystemInstruction(prompt.apiContextJson)),
                PromptChatMessage(PromptChatRole.User, prompt.userInstruction),
            ),
            chatSessionId = null,
            advertisedToolNames = setOf(PROPOSE_WIDGET_TOOL_NAME),
            ephemeralTools = listOf(captureTool),
        )
        // Do not cancel the native generation from inside OpenApiTool.execute(). LiteRT-LM calls
        // that adapter synchronously; resuming this coroutine on capture and cancelling there can
        // re-enter native conversation lifecycle code before the tool callback returns. Wait for
        // the terminal callback, then accept the first captured proposal.
        val terminal = engine.generate(request)
            .firstOrNull { it is GenerationEvent.Completed || it is GenerationEvent.Failed }
        return captureTool.capturedOrNull()?.let(WidgetAuthorshipResult::Proposed)
            ?: if (terminal is GenerationEvent.Failed) {
                WidgetAuthorshipResult.GenerationFailed(terminal.kind)
            } else {
                WidgetAuthorshipResult.NoProposal
            }
    }
}

internal class WidgetProposalCaptureTool : EphemeralLocalLlmTool {
    override val name: String = PROPOSE_WIDGET_TOOL_NAME
    override val descriptionJson: String = TOOL_DESCRIPTION
    private val calls = AtomicInteger()
    private val captured = AtomicReference<String?>()

    override fun execute(argumentsJson: String): String = if (calls.incrementAndGet() == 1) {
        captured.set(argumentsJson)
        """{"accepted":true}"""
    } else {
        """{"accepted":false,"error":"CALL_LIMIT_REACHED"}"""
    }

    fun capturedOrNull(): String? = captured.get()

    companion object {
        const val TOOL_DESCRIPTION = """{"name":"propose_widget","description":"Submit exactly one complete untrusted widget draft for application validation. This captures a proposal only; it does not execute or save it.","parameters":{"type":"object","additionalProperties":false,"properties":{"proposalVersion":{"type":"integer","const":1},"displayName":{"type":"string","minLength":1,"maxLength":80},"enabled":{"type":"boolean"},"periodicIntervalHours":{"type":["integer","null"],"enum":[null,1,6,12,24]},"tools":{"type":"array","maxItems":4,"items":{"type":"object","additionalProperties":false,"properties":{"id":{"type":"string","pattern":"^[a-z][a-z0-9_]{0,63}$"},"version":{"type":"integer","minimum":1}},"required":["id","version"]}},"runtime":{"type":"array","uniqueItems":true,"items":{"type":"string","enum":["locale","timezone","local_time","seed"]}},"presentation":{"type":"array","uniqueItems":true,"items":{"type":"string","enum":["card","column","row","text","value","icon","https_link"]}},"source":{"type":"string","minLength":1,"maxLength":32768}},"required":["proposalVersion","displayName","enabled","periodicIntervalHours","tools","runtime","presentation","source"]}}"""
    }
}

internal fun authoringSystemInstruction(apiContextJson: String): String = "You author ArarAI widget drafts. Use only the supplied API context. " +
    "Call propose_widget exactly once with a complete proposal; do not answer in plain text. " +
    "If a valid proposal example matches the request, copy its source exactly unless user-facing text, schedule, or " +
    "explicitly requested behavior must change. Preserve its plan arguments; use the contract-ready runtime fields " +
    "directly and never reparse locale or localTime for tool arguments. " +
    "The application assigns identity, revision, digests, grants, and limits. API context: $apiContextJson"

private fun JsonObject.requiredBoolean(name: String): Boolean = get(name)
    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
    ?.asBoolean
    ?: error("Missing boolean field")

private fun JsonObject.optionalLong(name: String): Long? {
    val value = get(name) ?: error("Missing nullable number field")
    if (value.isJsonNull) return null
    require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
    return value.asBigDecimal.longValueExact()
}

private fun <T> JsonArray.enumSet(values: Iterable<T>, wireName: (T) -> String): Set<T> {
    val byName = values.associateBy(wireName)
    val result = map { element ->
        val raw = element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            ?: throw UnsupportedCapabilityException()
        byName[raw] ?: throw UnsupportedCapabilityException()
    }.toSet()
    require(result.size == size())
    return result
}

private class UnsupportedCapabilityException : IllegalArgumentException()

private const val PROPOSAL_SCHEMA_VERSION = 1
private const val WIKIPEDIA_ON_THIS_DAY_TOOL_ID = "wikipedia_on_this_day"
private const val MAX_DISPLAY_NAME_CHARS = 80
private const val MAX_AUTHORING_PROMPT_CHARS = 4_096
internal const val MAX_WIDGET_AUTHORING_CONTEXT_TOKENS = 4_096
internal const val MAX_WIDGET_AUTHORING_TEMPERATURE = 0.2f
internal const val WIDGET_AUTHORING_TIMEOUT_MILLIS = 90_000L

private const val WIKIPEDIA_REFERENCE_SOURCE = """function plan(context) {
  return [{alias:'events',toolId:'wikipedia_on_this_day',contractVersion:1,arguments:{month:context.localMonth,day:context.localDay,language:context.language}}];
}
function render(context, outcomes, state) {
  const result = outcomes.events;
  if (!result || result.status !== 'success' || !result.payload.events.length) return {type:'text',text:'No event available',tone:'warning'};
  const index = Math.abs(Number(context.seed)) % result.payload.events.length;
  const event = result.payload.events[index];
  return {type:'card',child:{type:'column',children:[{type:'row',children:[{type:'icon',name:'calendar'},{type:'value',text:String(event.year),tone:'neutral'}]},{type:'text',text:event.text,tone:'neutral'},{type:'https_link',label:'Read related article',url:event.canonicalUrl,sourceAlias:'events',sourceField:'events.' + index + '.canonicalUrl'}]}};
}"""
