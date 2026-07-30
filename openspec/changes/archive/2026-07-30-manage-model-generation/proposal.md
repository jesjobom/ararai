## Why

ArarAI exposes reasoning controls for Chat and Voice Chat, but the generation
parameters declared in the model catalog are not yet consistently connected to
the LiteRT-LM runtime or available for experimentation. In particular,
`contextTokens` is not applied to LiteRT-LM `EngineConfig.maxNumTokens`, while
the catalog's `maxTokens` field suggests an independent response limit that
LiteRT-LM 0.14.0 does not expose.

Recent physical testing also showed a related failure mode: a model can spend
its available generation capacity on reasoning and tool calls, then finish
without a usable final answer. The app currently presents that terminal state
like an ordinary completion, sometimes rendering only an ellipsis. Users need
honest runtime controls, visible measurements, and an explicit incomplete
response state.

## What Changes

- Rename the Home destination from `Instructions and tools` to
  `Assistant configuration`, retaining its position above Settings.
- Add a `Generation` tab alongside `Instructions` and `Tools`.
- Let the user configure the total context window and sampling temperature
  independently for each model, restore catalog defaults, and apply the
  effective values to Chat and Voice Chat.
- Apply the total context window to LiteRT-LM `EngineConfig.maxNumTokens` and
  recreate incompatible engine/conversation state when a load-bound generation
  parameter changes.
- Offer temperature presets (`Precise`, `Balanced`, and `Creative`) plus a
  validated manual value.
- Show the selected model, effective generation values, runtime limitations,
  reasoning capability, and available metrics from the most recent
  conversational turn.
- Remove or rename the misleading catalog/domain `maxTokens` concept where it
  purports to limit a LiteRT-LM response. The UI reports that the response limit
  is controlled by the model/runtime because LiteRT-LM 0.14.0 has no separate
  Kotlin `maxOutputTokens` control.
- Preserve fixed benchmark-owned generation parameters so user overrides do not
  invalidate comparisons between runs.
- Detect a terminal generation with reasoning but no usable final answer,
  persist it as an incomplete assistant response, and render an explicit
  explanation in normal Chat.
- Keep partial reasoning available under normal Chat's existing
  `Show reasoning` preference.
- Make Voice Chat avoid empty TTS, report the incomplete result briefly, persist
  the shared incomplete message, and recover its hands-free loop.
- Strengthen app-owned generation instructions so a model synthesizes its best
  available answer after exhausting tools and reviews modern years for complete
  four-digit output before finalizing.

This change does not add an estimated stream-cancellation response limit, a
separate reasoning-token budget, arbitrary sampler controls, automatic repair
of suspicious years, or benchmark customization.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `local-llm-hub`: Add per-model conversational generation settings, effective
  LiteRT-LM context configuration, recent-turn metrics, Assistant Configuration
  navigation, and durable incomplete-response presentation.
- `voice-chat`: Apply the selected model's conversational generation settings
  and recover safely when reasoning finishes without a speakable final answer.

## Impact

- Home/navigation and configuration UI: renamed destination and a third tab for
  generation controls, effective values, limitations, and metrics.
- Preferences: per-model context-window and temperature overrides with
  catalog-default fallback.
- Model/catalog domain: distinguish total runtime context from a genuine output
  token limit and remove misleading unsupported semantics.
- LiteRT-LM boundary: bind context capacity to `maxNumTokens`, bind temperature
  to `SamplerConfig`, invalidate incompatible retained state, and expose
  conversational metrics.
- Chat persistence/UI: record and reconstruct an explicit assistant completion
  status without silently altering generated facts.
- Voice Chat: suppress empty TTS, expose a controlled incomplete-response
  notice, and resume the loop.
- Prompt policy: synthesize after the final allowed tool call and review modern
  year formatting without rewriting model output in application code.
- Benchmark: preserve its existing isolated, fixed configuration and metrics.
- Validation: preference migration, per-model switching, engine reload,
  context projection, presets/manual temperature, incomplete responses, shared
  Chat/Voice history, benchmark isolation, and physical E2B/E4B behavior.
