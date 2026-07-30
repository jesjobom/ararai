## 1. Characterize runtime and current semantics

- [x] 1.1 Add focused tests that prove LiteRT-LM engine creation receives
  `maxNumTokens` as total context capacity and sampler creation receives the
  effective temperature.
- [x] 1.2 Characterize completion callbacks for final text, reasoning-only
  output, cancellation, failure, and metrics delivery without inferring an
  unavailable stop reason.
- [x] 1.3 Inventory every catalog/domain/benchmark/context-projection use of
  `maxTokens` and record whether it is a benchmark decode target, projection
  reserve, or unsupported conversational response limit.

## 2. Define and persist effective generation settings

- [x] 2.1 Define runtime-neutral conversational generation settings containing
  total context window and temperature with catalog-backed defaults.
- [x] 2.2 Add a local per-model override store keyed by stable model ID;
  removing an override restores the current catalog default.
- [x] 2.3 Add centralized `Precise`, `Balanced`, and `Creative` temperature
  profiles plus validated finite manual input.
- [x] 2.4 Capture one immutable effective settings snapshot for every new
  normal-Chat or Voice-Chat turn.
- [x] 2.5 Add tests for defaults, overrides, restore, model switching, process
  recreation, invalid input, and preset/manual mappings.

## 3. Correct runtime configuration and compatibility

- [x] 3.1 Apply effective total context to LiteRT-LM
  `EngineConfig.maxNumTokens`.
- [x] 3.2 Apply effective temperature to `SamplerConfig.temperature`.
- [x] 3.3 Include all applicable effective generation settings in loaded-engine
  and retained-conversation compatibility; close and recreate incompatible
  native state safely.
- [x] 3.4 Refactor prompt projection to use truthful total-context and
  projection-reserve semantics without claiming an exact response limit.
- [x] 3.5 Remove or rename misleading conversational `maxTokens` catalog/domain
  fields while preserving an explicitly benchmark-owned decode target.
- [x] 3.6 Add engine, coordinator, projection, catalog migration, reload, and
  cancellation tests.

## 4. Add Assistant Configuration generation UI

- [x] 4.1 Rename the Home action and screen title from
  `Instructions and tools` to `Assistant configuration`, retaining its
  position above Settings.
- [x] 4.2 Add a third `Generation` tab while preserving existing Instructions
  and Tools state and navigation.
- [x] 4.3 Show the selected model, effective context window, effective
  temperature, reasoning capability, and restore-default action.
- [x] 4.4 Add context-window editing and temperature preset/manual controls with
  inline validation and clear next-turn/reload behavior.
- [x] 4.5 Show the read-only response-limit/runtime explanation and explain that
  reasoning and final answer share total capacity.
- [x] 4.6 Show available last conversational turn metrics without estimating
  missing token values.
- [x] 4.7 Add Compose tests for tabs, renaming, per-model values, edit/restore,
  invalid inputs, limitations, and metric availability.

## 5. Propagate conversational metrics

- [x] 5.1 Carry LiteRT-LM runtime-backed prefill/decode counts, throughput, and
  time-to-first-token through the shared conversation boundary.
- [x] 5.2 Retain one ephemeral last-turn metric snapshot for conversational UI
  without persisting it as a message.
- [x] 5.3 Keep benchmark configuration and measurements isolated from
  conversational overrides and last-turn metrics.
- [x] 5.4 Add tests for successful metrics, unavailable metrics, model changes,
  failures/cancellation, and benchmark isolation.

## 6. Persist and present incomplete assistant responses

- [x] 6.1 Add a backward-compatible assistant completion status whose legacy
  default is complete.
- [x] 6.2 Classify terminal reasoning-without-final-text as incomplete while
  preserving distinct cancellation and failure semantics.
- [x] 6.3 Persist incomplete status, partial reasoning, and eligible bounded
  metadata atomically with the assistant message.
- [x] 6.4 Render `Incomplete response` and a controlled explanation in normal
  Chat instead of a successful ellipsis; obey the existing `Show reasoning`
  preference for partial reasoning.
- [x] 6.5 Add store migration/legacy decode, ViewModel, reconstruction, restart,
  and Compose rendering tests.

## 7. Recover Voice Chat from incomplete output

- [x] 7.1 Persist the same incomplete assistant message and partial reasoning
  from a reasoning-only Voice Chat generation.
- [x] 7.2 Prevent empty final text, reasoning, protocol output, and placeholder
  ellipses from entering the TTS queue.
- [x] 7.3 Present a brief incomplete-response notice and return the active voice
  loop to its next valid state without stuck capture or generation ownership.
- [x] 7.4 Add Voice Chat state-machine, TTS, shared-history, cancellation, and
  normal-Chat continuity tests.
  Closed by explicit product-owner decision on 2026-07-30: production
  integration and shared Compose coverage are accepted for this increment; the
  deterministic complete captured-audio VoiceChatViewModel harness is deferred
  and is not claimed as implemented.

## 8. Refine invariant answer guidance

- [x] 8.1 Instruct the model to synthesize its best available answer after the
  final allowed tool call instead of continuing to request tools.
- [x] 8.2 Instruct the model to review modern calendar years for complete
  four-digit representation before finalizing.
- [x] 8.3 Verify that the application never silently rewrites suspicious years
  or claims prompt guidance guarantees factual correctness.
- [x] 8.4 Add deterministic prompt-composition tests for normal Chat and Voice
  Chat.

## 9. Quality, documentation, and physical validation

- [x] 9.1 Update README, project context, and device-validation guidance for
  Assistant Configuration, per-model overrides, runtime limitations, metrics,
  and incomplete responses.
- [x] 9.2 Run targeted preference, engine, coordinator, persistence, Chat,
  Voice Chat, benchmark, and Compose tests.
- [x] 9.3 Run Spotless, Detekt, unit tests, Android Lint, debug app build,
  Android-test compilation, strict OpenSpec validation, and
  `git diff --check`.
- [x] 9.4 Physically validate E2B and E4B with multiple context sizes and
  temperature profiles, recording load time, memory/termination behavior,
  response completeness, metrics, model switching, and process recreation.
  Closed with the available physical-device evidence by explicit product-owner
  acceptance on 2026-07-30. Recorded E4B observations include incomplete 2,048,
  successful 6,144, slow/incomplete 8,192, and Android LOW_MEMORY termination at
  32,000 tokens; remaining matrix combinations are not claimed as executed.
- [x] 9.5 Physically validate normal Chat and Voice Chat reasoning-only or
  exhausted-capacity recovery, including Wikipedia use, empty TTS prevention,
  cancellation, and continuation in shared history.
  Closed by explicit product-owner acceptance on 2026-07-30 after repeated
  normal Chat and Voice Chat testing at 6,144 context tokens without observed
  recovery problems.
