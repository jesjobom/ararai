## Context

The checked-in Gemma 4 catalog declares `contextTokens=2048`,
`maxTokens=512`, and `temperature=0.7`. Context projection uses both context and
maximum-output values to reserve prompt capacity. LiteRT-LM conversation
sampling uses temperature, but engine creation currently passes
`maxNumTokens=null`.

LiteRT-LM 0.14.0 defines `EngineConfig.maxNumTokens` as the total input-plus-
output capacity backed by the KV cache. Its Kotlin `SamplerConfig` exposes
sampling fields such as temperature, but no independent maximum-output-token
parameter. Therefore mapping the catalog's `maxTokens` to `maxNumTokens` would
be incorrect: it would shrink the complete context rather than cap only the
answer.

The runtime already reports native prefill/decode measurements as
`GenerationMetrics`, currently consumed by benchmark diagnostics. Chat and
Voice Chat do not retain a user-facing last-turn measurement. Both modes also
treat completion as successful when the runtime callback ends even if reasoning
was produced without final answer text.

## Goals / Non-Goals

**Goals:**

- Expose only generation settings that the active LiteRT-LM integration can
  apply truthfully.
- Make the total context window and temperature configurable per model for
  conversational workloads.
- Preserve catalog defaults and allow one-step restoration.
- Recreate native state whenever a changed setting makes reuse unsafe.
- Surface available runtime metrics without confusing them with benchmark
  results.
- Persist and present a clear incomplete-response state across Chat and Voice
  Chat.
- Keep benchmark runs stable and independent from conversational overrides.

**Non-Goals:**

- Claiming to enforce an independent response-token limit unsupported by the
  current SDK.
- Estimating tokens from characters or stopping a stream at an approximate
  boundary.
- Configuring top-k, top-p, seed, reasoning budget, or tool-call budget in this
  increment.
- Automatically changing generated years or other facts.
- Guaranteeing that all models follow prompt guidance or emit complete answers.
- Establishing conservative product limits for a production audience; this
  development build intentionally permits broad physical experimentation.

## Decisions

### Expand and rename the existing configuration destination

The Home action becomes `Assistant configuration` and remains immediately above
Settings. Its screen contains `Instructions`, `Tools`, and `Generation` tabs.
Instructions and tool controls retain their existing behavior.

Generation is a separate tab because model capacity and sampling are neither
assistant instructions nor external tools. Reasoning toggles remain in normal
Chat and Voice Chat settings because they are interaction-mode preferences.
The Generation tab may report whether the selected model supports reasoning but
does not duplicate those toggles.

### Resolve one effective conversational generation configuration per model

The catalog remains the source of default context window and temperature.
Locally persisted overrides are keyed by stable model ID. Selecting another
model resolves that model's defaults or saved overrides; returning to the first
model restores its prior values.

`Restore default` removes overrides for the selected model rather than copying
today's catalog values permanently. A future catalog update can therefore
change the default for models without an explicit user override.

The configuration store accepts finite numeric temperature values and positive
integer context windows within representation/runtime constraints. It does not
impose a small product-safe maximum merely to avoid experimental device
pressure. Runtime initialization failures remain controlled and visible so
physical E2B/E4B testing can establish practical limits.

Each submitted Chat or Voice turn captures an immutable effective generation
configuration. An edit affects future turns and never mutates one already in
flight.

### Bind context and temperature to their real LiteRT-LM controls

The effective total context window maps to
`EngineConfig.maxNumTokens`. Because this value determines KV-cache capacity,
changing it is load-bound: an incompatible engine and retained conversation
must be closed and recreated before the next turn.

The effective temperature maps to `SamplerConfig.temperature`. It is included
in conversation compatibility so retained native conversation state is not
reused across different sampling settings. If the current engine can safely be
retained while only the conversation is recreated, the implementation may do
so; correctness of the effective value takes precedence over avoiding reload.

Context projection derives its input budget from the total context window
without pretending that an exact output reservation is enforced by LiteRT-LM.
Any internal safety reserve must be named as a projection reserve, documented,
and not presented as `Maximum response tokens`.

### Remove misleading independent response-limit semantics

The catalog/domain `maxTokens` field is migrated, removed, or renamed according
to its remaining verified uses. Benchmark can retain a benchmark-specific
decode target because its harness deliberately controls and labels that
workload. Conversational configuration must not expose the old field as an
effective output cap.

The Generation tab shows a read-only statement such as
`Response limit: controlled by model/runtime` and explains that reasoning and
final answer share the total available capacity. No slider or editable field is
shown until the runtime exposes a genuine output-token control.

### Offer understandable temperature presets without hiding the value

The tab provides named presets:

- `Precise`
- `Balanced`
- `Creative`

Each preset maps to one documented numeric value. The exact mappings are
centralized and covered by tests rather than duplicated in Compose code. A
manual/advanced option accepts a finite non-negative value and displays
validation errors without persisting an invalid entry. The selected numeric
effective value remains visible so presets do not obscure actual runtime
configuration.

### Treat last-turn metrics as ephemeral diagnostics

When LiteRT-LM reports them, the most recent completed conversational turn
publishes:

- prefill token count;
- prefill tokens per second;
- decode token count;
- decode tokens per second; and
- time to first token.

These metrics are ephemeral diagnostics associated with the currently selected
model and last conversational run. They are not canonical chat history and are
not required to survive process death. Missing runtime values display as
unavailable rather than being estimated or labeled as tokens.

Benchmark continues to own, display, and isolate its own run metrics. It always
uses fixed benchmark parameters and does not read conversational overrides or
overwrite the Generation tab's last-turn record.

### Model assistant completion explicitly

A terminal turn with non-blank final answer text is complete. A terminal turn
that emitted reasoning but no usable final answer is incomplete. Cancellation
and failure retain their existing distinct meanings and must not be relabeled as
incomplete completion.

The assistant message gains an explicit backward-compatible completion status.
Existing stored messages decode as complete. An incomplete message persists any
partial reasoning, bounded sources that are valid to retain, and a blank or
partial final answer according to the existing atomic message contract.

Normal Chat renders a localized `Incomplete response` indication with a short
explanation instead of presenting an ellipsis as a successful answer. Existing
`Show reasoning` controls whether partial reasoning is visible. The app never
guesses or silently repairs truncated dates such as a three-digit year.

### Recover Voice Chat without speaking empty output

When a voice generation finishes without speakable final text, Voice Chat:

1. persists the shared incomplete assistant message;
2. does not queue empty text, reasoning, protocol output, or an ellipsis for
   TTS;
3. exposes a brief controlled incomplete-response notice; and
4. returns the active half-duplex loop to its next valid state without leaving
   capture or generation stuck.

Opening normal Chat later shows the same incomplete message and, when enabled,
its partial reasoning.

### Strengthen instructions without rewriting facts

The app-owned invariant instruction tells the model to synthesize the best
available answer immediately after the final permitted tool call and to review
modern calendar years for complete four-digit representation before producing
the final answer.

This is guidance, not a guarantee. Application code does not transform `192`
into `1992`, because a syntactic guess could create a false date. Any later
automatic verification or repair loop requires a separate design with explicit
evidence and bounded cost.

## Risks / Trade-offs

- **Large contexts increase memory, load time, or process death** → allow
  development experimentation, apply values honestly, surface controlled load
  failures, and record physical E2B/E4B evidence.
- **Changing context requires expensive engine recreation** → persist the
  override immediately but apply it to the next turn with clear reload state;
  never reuse an incompatible engine.
- **A prompt projection reserve may still be approximate** → name it accurately,
  test deterministic projection, and do not market it as an output limit.
- **Runtime callbacks may not identify the exact stop reason** → classify only
  the observable no-final-answer terminal state, not an unproven token-limit
  cause.
- **Metrics may be missing or reported late** → show only runtime-backed values
  and preserve ordinary conversation completion when diagnostics are absent.
- **Temperature presets imply qualitative guarantees** → expose their numeric
  mapping and describe them as sampling profiles, not accuracy promises.
- **Schema evolution can corrupt old history** → use a backward-compatible
  default of complete and test legacy database/payload reconstruction.

## Migration Plan

1. Characterize LiteRT-LM `maxNumTokens`, sampler application, metrics timing,
   and no-final-answer callbacks with deterministic adapter tests.
2. Introduce the per-model generation preference store and effective
   configuration resolver with catalog fallback.
3. Separate total context, projection reserve, and benchmark decode-target
   semantics; migrate/remove misleading conversational `maxTokens` usage.
4. Bind context and temperature to LiteRT-LM lifecycle/compatibility.
5. Rename the destination and add the Generation tab, presets, restoration,
   limitations, and last-turn metrics.
6. Add backward-compatible assistant completion status and normal Chat
   presentation.
7. Integrate Voice Chat incomplete-response recovery and shared persistence.
8. Strengthen invariant generation guidance and complete automated/physical
   validation.

Rollback removes conversational overrides and restores catalog defaults.
Existing incomplete statuses remain backward-compatible message metadata; an
older build treats their textual content according to its existing decoder.

## Initial implementation choices

- `Precise`, `Balanced`, and `Creative` initially map to `0.2`, `0.7`, and
  `1.0`. Physical E2B/E4B evidence may justify changing these centralized
  values later.
- A context-window edit is saved immediately and the existing loading state
  represents engine recreation when the next conversational workload starts.
- Last-turn diagnostics are retained independently per model for the current
  process, so switching back to a model restores its latest ephemeral metrics.
