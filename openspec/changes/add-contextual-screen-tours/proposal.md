## Why

ArarAI's Home destinations are self-explanatory, and first-run model consent
already explains the only prerequisite that must be surfaced there. Several
interior screens, however, expose capability-dependent or technically nuanced
behavior that is easy to miss from labels alone. A short contextual tour at the
point of use can explain those behaviors without forcing a long application-wide
walkthrough or requiring a model download solely for onboarding.

## What Changes

- Add optional, sequential first-visit tours to normal Chat, Voice Chat, Model
  Management, and Assistant configuration.
- Focus one real control or region at a time with a spotlight, concise text,
  current/total progress, back/next controls, a final completion action, and a
  close action that ends only the current screen's tour.
- Keep Home free of a feature tour; retain the existing one-time first-model
  download consent dialog as its only onboarding surface.
- Explain normal-Chat reasoning enablement and visibility separately, and
  warn that reasoning can increase response time; explain transcript presentation
  when local transcription makes that feature relevant.
- Explain that conversation history remains available without a downloaded Chat
  model, while sending new messages requires a downloaded and selected Chat model.
- Explain Voice Chat reasoning behavior and its camera flow, including manual and
  pause-triggered automatic photo capture, when supported by the active model,
  including the same response-time expectation for reasoning.
- Explain in Model Management that exactly one downloaded Chat model and one
  downloaded Transcription model can be active for their respective workloads,
  and explain how local transcription supports reconstructible conversation
  history and automatic session titles for voice prompts.
- Explain Assistant configuration concepts including tools and their networking
  implications, per-model total context, temperature, and related generation
  semantics.
- Persist completion or dismissal by stable screen-tour/version identifiers.
  Closing or completing one screen's tour does not affect tours on other screens.
- Provide an option to restore all tours by clearing their local completion and
  dismissal records.
- Include conditional steps only when their feature and focus target are
  currently applicable rather than pointing to controls that are absent or
  unavailable.

## Capabilities

### New Capabilities

- `contextual-screen-tours`: optional, capability-aware, per-screen guidance and
  its persisted completion/dismissal policy.

### Modified Capabilities

None. The tours explain existing behavior without changing Chat, Voice Chat,
model selection, transcription, tools, or generation semantics.

## Impact

- Affected code: reusable Compose spotlight/coach-mark presentation, stable
  anchor registration, tour sequencing, local preference persistence, screen
  integration, localized copy, accessibility semantics, and deterministic UI
  tests.
- Existing Home onboarding and model-download consent remain unchanged.
- No model, camera, microphone, transcription, tool, or network operation begins
  because a tour is displayed or advanced.
- No remote analytics or onboarding state is introduced; all tour state remains
  local to the application.
