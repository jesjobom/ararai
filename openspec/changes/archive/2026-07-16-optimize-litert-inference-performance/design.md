## Context

Gemma 4 runs through `litertlm-android:0.14.0`, not through ArarAI's
llama.cpp/JNI path. The app already requests `Backend.GPU()` for the language
model, but it also initializes vision on GPU and audio on CPU for every Gemma
load because catalog capabilities are treated as an eager runtime profile.

The benchmark increments its generated-token counter once per
`GenerationEvent.Token`, although that event represents a UI text delta rather
than a tokenizer token. It also divides this count by the entire generation
interval, including prefill and time to first output. LiteRT-LM 0.14 already
provides `Conversation.getBenchmarkInfo()`, including prefill/decode token
counts, time to first token, and distinct prefill/decode throughput.

Each call to `generate` currently creates and closes a LiteRT-LM
`Conversation`, while `PromptRequest` contains the full app-built transcript.
Consequently every turn prefills the complete transcript and the runtime cannot
retain its conversation KV state. The engine boundary also has no chat-session
identity with which to distinguish compatible continuation from a different
or modified transcript.

## Goals / Non-Goals

**Goals:**

- Report semantically correct prefill and decode measurements using native
  runtime statistics.
- Use a text-only LiteRT-LM engine profile for text requests and initialize
  vision/audio processing only when a request requires it.
- Reuse a LiteRT-LM conversation only for verified continuation of the same
  chat transcript and compatible generation settings.
- Give LiteRT-LM an app-owned persistent cache directory.
- Preserve controlled cancellation, model replacement, and failure behavior.

**Non-Goals:**

- Changing model artifacts, quantization, context size, or sampler defaults.
- Optimizing llama.cpp kernels or GPU offload.
- Persisting KV cache across process death or model unload.
- Keeping multiple LiteRT-LM engines or conversations resident concurrently.

## Decisions

### Use runtime performance data instead of streamed UI deltas

Extend runtime observability so a completed generation can publish structured
performance data: prefill token count/rate, decode token count/rate, and time to
first token. The LiteRT-LM bridge obtains this data from
`Conversation.getBenchmarkInfo()` after successful completion and before the
conversation is closed or retained. Benchmark UI uses these values as the
authoritative LiteRT-LM result.

The generic benchmark model will make metric availability explicit. It will
not relabel text chunks as tokens when a runtime cannot provide a trustworthy
token count. This is preferred over tokenizing output again because output-only
retokenization can disagree with the runtime's incremental decoder and omits
special tokens.

### Separate load, prefill, first-token, and decode measurements

Keep model/engine initialization time as load time. Display native prefill and
decode throughput separately, with TTFT as its own latency. End-to-end elapsed
generation time remains useful but is not used as the denominator for decode
tokens per second.

The benchmark continues to use a stable prompt and settings. A benchmark run
starts with a fresh conversation so retained chat state cannot contaminate the
result.

### Use one active workload profile, defaulting to text only

Represent the active LiteRT-LM profile as the modalities needed by the request.
Normal model load initializes a text-only engine (`backend=GPU`, no vision or
audio backend). Before generation, the engine verifies that the active profile
supports the request. An image request recreates the engine with the configured
vision backend; an audio request recreates it with the configured audio
backend. A later text-only request may return to the lean profile.

Reconfiguration closes the retained conversation before closing the engine.
The catalog remains the source of truth for whether a modality is supported;
the profile controls which supported processors are resident, not product
capability.

This is preferred over changing `LocalLlmEngine.load` to accept a prompt because
that would couple all runtimes' model-loading contract to one request. It also
preserves eager text model loading and makes multimodal reconfiguration an
explicit, testable LiteRT-LM concern.

### Reuse conversations only after transcript compatibility validation

Add an optional chat-session identity to `PromptRequest`. For LiteRT-LM, retain
one conversation plus a canonical record of the transcript it has successfully
processed. A following request reuses it only when:

- it has the same non-null chat-session identity;
- its historical messages exactly match the retained canonical transcript;
- the active model and workload profile are unchanged;
- sampler settings and reasoning mode are unchanged; and
- the previous generation completed successfully.

On a compatible continuation, send only the new user content. On the first
turn or an incompatible request, close the old conversation, construct a new
one with the system instruction and eligible historical messages as initial
conversation state, then send the new content. After successful completion,
append both the user input and final assistant output to the retained canonical
transcript.

Benchmark requests omit chat-session identity and therefore always use a fresh
conversation. Cancellation, error, transcript mismatch, model/profile change,
or unload invalidates and closes retained conversation state because a partial
decode cannot safely be assumed reusable.

This is preferred over trusting session ID alone because persisted sessions can
be switched, cleared, or otherwise diverge from the runtime's in-memory state.

### Inject an app-owned cache directory

Construct the Android LiteRT-LM bridge with a directory below the app cache
root, create it when needed, and pass its absolute path through
`EngineConfig.cacheDir`. Cache setup failure falls back to an uncached engine
with diagnostics rather than blocking local inference. Normal chat/session
deletion does not clear compiled runtime cache; Android or an explicit future
cache-management flow owns eviction.

## Risks / Trade-offs

- **[LiteRT-LM native metrics semantics differ by release]** → Keep the bridge
  conversion isolated, test field mapping, and record the dependency version in
  device results.
- **[Structured conversation initialization changes prompt rendering]** → Add
  focused transcript-equivalence tests and compare first-turn output before
  enabling reuse.
- **[Incorrect reuse leaks context across sessions]** → Require both session
  identity and exact transcript compatibility; invalidate conservatively.
- **[Cancellation leaves native conversation state ambiguous]** → Always close
  and discard the conversation after cancellation or error.
- **[Multimodal profile switching adds latency]** → Surface it as engine
  reconfiguration/load time and retain the matching profile for consecutive
  compatible requests.
- **[Cache consumes storage or becomes stale]** → Use the app cache area so the
  OS can reclaim it; fall back safely if initialization fails.

## Migration Plan

1. Introduce optional generation metrics and update benchmark tests/UI without
   changing runtime behavior.
2. Wire LiteRT-LM native benchmark data and verify a fresh benchmark run.
3. Add app-owned cache injection and workload-profile lifecycle tests.
4. Add session identity and conservative conversation reuse behind focused
   transcript/cancellation tests.
5. Run the automated test suite and build debug and release variants.

Rollback is code-only: disable conversation retention and workload-profile
switching while retaining accurate metrics. Cached files can remain because
they live in reclaimable app cache storage.

## Open Questions

- Whether llama.cpp should expose equivalent native prefill/decode metrics in
  the same sprint. The benchmark contract must remain honest either way, but
  the performance investigation is centered on LiteRT-LM Gemma 4.
