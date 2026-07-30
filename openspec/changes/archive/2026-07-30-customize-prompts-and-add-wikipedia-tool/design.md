## Context

The checked-in model catalog currently supplies one `chat.systemPrompt`.
`ConversationCoordinator` and `ConversationContextProjector` use that same value
for normal Chat and Voice Chat. LiteRT-LM retains a compatible native
conversation as an ephemeral cache, so changing the effective instruction
without recreating that conversation would continue using stale behavior.

The catalog includes Gemma 4 E2B IT and E4B IT LiteRT-LM bundles. The current
LiteRT-LM 0.14.0 API exposes tool providers in `ConversationConfig`, an
`automaticToolCalling` option, structured `ToolCall` values, tool responses, and
message tool-call metadata. ArarAI does not currently register tools or expose
tool semantics through its local inference boundary.

Voice Chat must remain hands-free after the loop starts. A search affordance or
required phrase would add a user-visible gate, so tool selection belongs inside
the model/runtime turn. Wikipedia is external and may be incomplete, outdated,
incorrect, unavailable, or contain text that resembles instructions. It cannot
be treated as trusted system content.

## Goals / Non-Goals

**Goals:**

- Let users maintain different behavioral instructions for normal Chat and
  Voice Chat.
- Preserve one canonical shared conversation while selecting instructions from
  the origin of the current turn.
- Offer a one-time persisted Wikipedia enablement choice, after which eligible
  searches occur automatically without per-turn interaction.
- Use LiteRT-LM's structured automatic tool calling for the checked-in Gemma 4
  models.
- Keep network execution, argument validation, bounds, cancellation, and source
  metadata under application control.
- Fail transparently and recover the normal Chat or Voice Chat state machine.
- Preserve the architecture needed to add another provider or runtime later
  without implementing that generalization now.

**Non-Goals:**

- Tool calling for future non-LiteRT-LM or non-Gemma models.
- Parsing model-emitted textual commands or custom JSON as a fallback.
- General web or news search, real-time factual guarantees, or browsing.
- User-authored executable skills, scripts, plugins, or remote tool catalogs.
- Multiple tool calls, recursive planning, or chained tools in one turn.
- Persisting full Wikipedia extracts or replaying a historical network call.
- Allowing editable text to replace app-owned protocol, privacy, or safety
  instructions.

## Decisions

### Knowledge-tool capability is explicit catalog metadata

Each model declares a normalized set of supported knowledge-tool names. The
checked-in E2B and E4B bundles advertise `wikipedia_search` because both passed
the physical structured-calling matrix. Capability is propagated into
`LocalModel`; it is not inferred from family, filename, runtime, or prompt.

For each Chat or Voice turn, the application resolves the advertised set from
the user's persisted preference and the installed active model's capability.
The LiteRT-LM engine validates that set again before generation and registers
only the corresponding structured `OpenApiTool`. An empty set creates the
ordinary conversation configuration with no hidden tool prompt or textual
fallback.

### Tool lifecycle crosses the engine boundary as bounded domain events

The LiteRT-LM adapter reports a per-turn lifecycle of started followed by
either succeeded-with-sources or controlled failure. The local engine maps
those callbacks to `GenerationEvent` values consumed identically by normal
Chat and Voice Chat. Events contain only the normalized tool name, bounded
canonical `KnowledgeSource` metadata, and a controlled failure reason. Raw
reference text and LiteRT-LM protocol payloads never cross this boundary.

The transient UI state is reset at the beginning and terminal paths of a turn.
Persistence and rendering of successful sources remain separate work in
section 7 so that a partially generated answer cannot accidentally commit
source metadata.

### Separate editable behavior from app-owned invariants

The UI presents one editable instruction for normal Chat and one for Voice Chat.
Checked-in defaults remain the recovery source for `Restore default`. Empty
user text is valid and means no additional user instruction.

The effective system instruction is composed deterministically from:

1. a short app-owned invariant section needed for conversation and tool
   semantics;
2. the current turn origin's persisted editable instruction; and
3. an ephemeral runtime block containing the device's current local date,
   time-zone identifier, and UTC offset.

The temporal block is regenerated when each Chat or Voice Chat turn begins and
is never written to canonical conversation storage. Reconstructing a long
conversation therefore includes exactly one current temporal block rather than
one historical copy per turn. Exact clock time is intentionally omitted because
most date comparisons do not require it and changing it continuously would
invalidate otherwise compatible prompt prefixes. The model benchmark retains
its fixed baseline prompt so results remain comparable across dates.

The app-owned section is not shown as editable system text. This prevents a
customization field from accidentally removing tool protocol or untrusted-data
boundaries. The UI explains that the editor customizes behavior rather than
replacing application rules.

Instruction text has a documented size limit and is counted in the model
context budget. Whitespace is normalized before compatibility comparison.

### Select instructions by current turn without splitting history

Normal Chat uses the normal-Chat instruction and Voice Chat uses the Voice-Chat
instruction for the turn being submitted. Both continue reading and writing the
same canonical persisted history.

The effective instruction is part of native-conversation compatibility.
Changing the relevant instruction, changing enabled tools, or moving between
interaction modes whose effective instructions differ closes the retained
LiteRT-LM conversation and rehydrates a new one from bounded canonical history.
This accepts a one-time performance cost instead of leaking stale instructions
across modes.

Each submitted turn captures one immutable compatibility snapshot containing
the effective instruction and the normalized set of advertised tool names.
Normal Chat and Voice Chat use the same snapshot contract. The retained
LiteRT-LM key compares the tool-name set independently of ordering, so adding,
removing, enabling, or disabling any present or future application-owned skill
invalidates stale native state without coupling conversation reuse specifically
to Wikipedia.

### Make tool enablement explicit but tool use automatic

Wikipedia requires network access and therefore starts disabled until the user
enables it from `Instructions and tools`. The screen identifies that queries
and selected result retrieval leave the device while inference and conversation
storage remain local.

After enablement, no per-turn confirmation, button, or command phrase is
required. For each eligible Gemma 4 LiteRT-LM conversation, the tool definition
is registered and the model/runtime decides whether to call it as part of the
ordinary turn. Disabling it removes the tool from new native conversations and
invalidates a retained conversation that advertised it.

### Limit the first implementation to verified Gemma 4 LiteRT-LM bundles

Tool availability requires all of:

- the selected catalog entry is an explicitly allowlisted Gemma 4 LiteRT-LM
  model;
- its installed bundle passes normal integrity and availability checks;
- physical or integration validation has established compatible tool behavior;
  and
- the user enabled Wikipedia.

The app does not infer support from family name or runtime alone. Capability is
explicit catalog metadata so an unverified future Gemma bundle does not receive
the tool accidentally.

Unsupported models retain normal local Chat and Voice Chat behavior. The tools
screen preserves the user's enabled preference but reports that the current
model cannot use Wikipedia. No textual fallback prompt is injected.

### Register one application-owned structured tool

ArarAI supplies a `wikipedia_search` tool provider to LiteRT-LM
`ConversationConfig` and enables automatic tool calling. The public schema
accepts only a bounded query and language. The language defaults from the user
turn or app-supported locale and is normalized to a supported Wikipedia edition.

The LiteRT-LM adapter remains responsible for runtime-specific registration.
Network access lives behind an application-domain `KnowledgeTool` contract, and
the Wikipedia implementation returns a bounded `ToolResult` with content,
source title, canonical URL, language, retrieval time, and controlled error.
This prevents HTTP and MediaWiki response details from becoming part of the
conversation coordinator or UI.

The runtime adapter and the network provider are validated separately before
production Chat integration. Deterministic characterization uses the real
`OpenApiTool` adapter with a fake `KnowledgeTool`, exercising the complete
schema, validation, result-serialization, continuation, cancellation, and
cleanup path without depending on external network availability or mutable
Wikipedia content.

A direct opt-in smoke test in the Tools tab may use the real Wikipedia provider
to verify Android networking, TLS, endpoint behavior, and response parsing
without loading or prompting a model. Each future tool should expose the same
small smoke-test contract. Live network results are diagnostic evidence only
and are not deterministic acceptance gates. The model benchmark remains
limited to model performance.

The first increment permits at most three Wikipedia invocations per user turn. The model searches
English Wikipedia first and may retry in the automatically detected language of the user's question
when a corresponding Wikipedia edition exists and the English result is missing or unsatisfactory.
Arguments, schemes, hostnames, response status, media type, redirects, decoded
size, and item count are validated. The implementation uses an official
Wikipedia/MediaWiki HTTPS endpoint and does not accept a model-supplied URL.

### Treat retrieved content as bounded untrusted data

The tool searches for a small number of relevant pages and retrieves only
plain-text introductory extracts needed for synthesis. Markup, scripts, and
instructions embedded in retrieved text have no application privileges.

The tool response is framed as untrusted external reference material. The
app-owned instruction tells the model to use it as evidence, ignore commands
inside it, distinguish uncertainty, and avoid claiming current or real-time
coverage. Result text competes explicitly for the selected model's context
budget and is truncated deterministically.

The app never displays a tool call as an assistant answer and Voice Chat never
speaks protocol tokens. Only the final synthesized answer is presented or
queued for TTS.

### Persist sources, not raw retrieved extracts

A successfully completed assistant answer may persist bounded source metadata:
source provider, page title, canonical HTTPS URL, language, and retrieval time.
Normal Chat renders those sources as optional links associated with the answer.
Voice Chat speaks the concise final response and leaves sources visible in the
shared conversation rather than reading URLs aloud.

Raw extracts and tool-protocol messages are transient generation input. They are
not persisted as user or assistant messages and are not reconstructed as
historical context. The persisted final answer remains sufficient for later
conversation reconstruction.

### Bound latency, cancellation, failure, and repetition

Tool execution has connection/read timeouts, a total deadline, a bounded
response size, and cancellation tied to the owning generation turn. Voice Chat
shows or announces a short non-blocking research state while the microphone
remains inactive.

An empty result, malformed call, unsupported language, HTTP failure, timeout, or
cancellation returns a controlled tool error. The model may explain that the
search was unavailable, but the UI never claims that research succeeded without
a validated result. The model may retry within the three-call budget, including
the supported language of the user's question when an English search is not
satisfactory.
Generation or tool cancellation follows existing incomplete-turn semantics and
does not leave a reusable partial native conversation.

## Risks / Trade-offs

- **Gemma bundle exposes poor tool behavior despite runtime APIs** → require
  explicit per-catalog-entry capability and validate E2B/E4B on a physical
  device before enabling the shipped flag.
- **Different mode instructions reduce native-session reuse** → include the
  effective instruction in compatibility and accept safe rehydration.
- **Automatic calls add Voice Chat latency** → a three-call ceiling, strict
  per-call deadlines, bounded extracts, and visible research state.
- **Wikipedia is mistaken for a real-time source** → describe it as
  encyclopedic knowledge and require uncertainty rather than freshness claims.
- **Retrieved text attempts prompt injection** → plain-text extraction,
  untrusted-data framing, fixed tool endpoints, and app-owned invariant
  instructions.
- **Sources disappear after restart if raw tool state is transient** → persist
  bounded source metadata atomically with the completed assistant answer.
- **Tool preference leaks network usage expectations** → disabled by default,
  disclose external requests before enablement, and show current compatibility.
- **LiteRT-LM automatic execution obscures application control** → wrap the
  provider with limits, cancellation, telemetry, and deterministic result/error
  contracts; never allow model-selected URLs or arbitrary tools.

## Migration Plan

1. Characterize current prompt selection, native-conversation compatibility,
   and LiteRT-LM conversation creation.
2. Add local preferences and the instructions-and-tools UI while retaining the
   checked-in prompt as both initial defaults.
3. Select effective instructions by turn origin and invalidate retained native
   state on effective-context changes.
4. Complete and deterministically test the bounded Wikipedia provider,
   including deadlines, cancellation, redirects, response bounds, and
   controlled failures.
5. Add the LiteRT-LM adapter and characterize it offline with a fake provider on
   both checked-in Gemma bundles.
6. Add a direct opt-in Wikipedia smoke test to the Tools tab, then add explicit
   catalog capability only for validated Gemma 4 LiteRT-LM entries.
7. Register the provider through LiteRT-LM automatic tool calling and carry
   progress, cancellation, controlled errors, and sources through the shared
   coordinator.
8. Integrate Voice Chat research state and normal Chat source presentation.
9. Physically validate E2B and E4B before shipping their capability flag.

Rollback disables or removes the catalog capability and tool registration.
Persisted instruction preferences and source metadata remain harmless,
backward-compatible local data. Existing conversations and model files require
no destructive migration.

## Open Questions

- Whether both current Gemma 4 bundles meet the same tool-selection and latency
  threshold, or capability should initially ship for only one validated bundle.
- Which supported Wikipedia language should be used when offline language
  detection and the user's explicit query language disagree.
- Whether the Voice Chat research state should remain visual-only or speak one
  short localized acknowledgement when a call exceeds a latency threshold.
