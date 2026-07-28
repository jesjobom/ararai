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
completed in 5,917 ms. E2B and the remaining error, cancellation, repetition,
and reliability cases are still required before catalog capability is enabled.
