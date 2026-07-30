## LiteRT-LM 0.14.0 tool-calling characterization

### Static API evidence

The resolved `litertlm-android-0.14.0-api.jar` exposes:

- `ConversationConfig.tools: List<ToolProvider>`;
- `ConversationConfig.automaticToolCalling: Boolean`;
- `ToolProvider` and `OpenApiTool` adapters;
- structured `ToolCall(name, arguments)` message metadata;
- `Content.ToolResponse(name, response)`;
- automatic execution inside `Conversation`, with an internal recurring-call
  limit; and
- cancellation through `Conversation.cancelProcess()`.

This establishes that application-owned structured tools can be registered
without parsing model-generated text protocols. It does not prove that either
checked-in Gemma bundle selects and executes tools reliably.

### Implemented characterization guard

The retained LiteRT-LM conversation key now includes the normalized effective
system instruction. Automated tests verify that a changed instruction prevents
native conversation reuse.

### Physical spike still required

Before either catalog entry declares verified Wikipedia capability, run a
deterministic in-memory `OpenApiTool` on Gemma 4 E2B and E4B and record:

- direct-answer behavior when a tool is unnecessary;
- English and Portuguese tool selection and argument shape;
- tool response continuation into a final answer;
- cancellation during execution;
- repeated-call behavior and latency; and
- absence of tool/protocol tokens in visible or spoken output.

Until this evidence exists, the settings UI preserves Wikipedia preference but
reports the selected model as unavailable and the runtime registers no tool.

### Lifecycle A/B characterization

Physical E4B/GPU testing on LiteRT-LM 0.14.0 verified automatic tool calling
and four-turn reuse in one conversation. The deterministic English and
Portuguese tools were each called exactly once, direct and follow-up turns did
not call a tool, and every turn reached `onDone`.

The same runtime then blocked in `Conversation.close()`. Enabling conversation
constrained decoding and relying on the default `automaticToolCalling=true`,
matching AI Edge Gallery's conversation configuration, did not change the
cleanup failure.

Downgrading only LiteRT-LM to 0.11.0 reproduced the same cleanup timeout, so
the failure was not a 0.14.0 regression. Logcat isolated the block to
`Conversation.close()` invoked synchronously from the terminal `onDone`
callback: cleanup logged its start but never its completion.

The application now records cleanup from the terminal callback and performs
the actual close after returning across a coroutine dispatch boundary. This
avoids re-entering LiteRT-LM session lifecycle code before the runtime has
published terminal task state.

The corrected physical E4B/GPU run on LiteRT-LM 0.14.0 passed:

- four sequential turns in one conversation;
- zero calls for the direct and evidence-follow-up turns;
- exactly one English Alan Turing call and one Portuguese Ada Lovelace call;
- valid structured `query` and `language` arguments;
- final answers grounded in deterministic tool evidence;
- no tool-protocol leakage; and
- completed conversation cleanup without timeout.

The observed first-token latency was 2,316 ms and structured execution
completed in 5,917 ms.

### Completed physical acceptance matrix

Both checked-in bundles were exercised on a physical device with LiteRT-LM
0.14.0 and the production `WikipediaOpenApiTool` backed by the deterministic
fake `KnowledgeTool`. Acceptance required exact call/start counts, valid
language and query arguments, bounded evidence in the final answer, no
automatic retry, no visible protocol leakage, and successful conversation
cleanup.

The E4B bundle
`gemma-4-e4b-it-litert-lm`
(`0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0`)
passed all ten scenarios. The final strengthened single-call comparison used
one started and one completed call and incorporated both the mathematician and
Analytical Engine evidence. Cancellation occurred after the tool started,
recording one start, zero completed calls, `cancelled=true`, and successful
cleanup.

The E2B bundle
`gemma-4-e2b-it-litert-lm`
(`181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`)
also demonstrated the expected behavior in all ten scenarios. Its four-turn
reuse case completed in 3,643 ms with 1,888 ms first-token latency, two tool
starts, and two completed calls. Standalone English search completed in
1,891 ms; the strengthened single-call comparison completed in 3,002 ms; and
cancellation after tool start completed in 3,731 ms with zero completed calls.

The E2B Portuguese answer reproduced the deterministic provider evidence
verbatim but the initial report labeled it failed because that standalone case
expected `programadora` while the provider emitted `programação`. The evaluator
expectation was aligned with the deterministic evidence; no additional
physical run was required because the structured call, Portuguese arguments,
final grounded answer, and cleanup were all directly present in the captured
report.

This evidence completes structured-behavior characterization for both bundles.
Catalog capability remains disabled until explicit metadata and production
eligibility wiring are implemented under tasks 6.1–6.6.

### Opt-in live-network diagnostic

Diagnostics now includes a separate, user-initiated Wikipedia smoke test. It
does not load a model and is not part of deterministic acceptance. One tap
sends the disclosed terms `Alan Turing` to English Wikipedia and `Ada Lovelace`
to Portuguese Wikipedia through the production `WikipediaKnowledgeTool`.

The shareable report records success or controlled failure, elapsed time, and
validated bounded source metadata. It intentionally excludes raw extracts.
Timeout and cancellation continue through the provider's existing controlled
contracts, and leaving or cancelling Diagnostics cancels the owning coroutine.

The physical live-network run passed both checks against the official API:

- English `Alan Turing`: 806 ms, three validated canonical English Wikipedia
  sources, with the exact article ranked first.
- Portuguese `Ada Lovelace`: 701 ms, three validated canonical Portuguese
  Wikipedia sources, with the exact article ranked third.

The variable Portuguese ranking is expected external-search behavior and
confirms that the provider validates and bounds the returned source set without
assuming that the exact-title article is always first. No raw extract or
internal protocol content appeared in the shared diagnostic report.
