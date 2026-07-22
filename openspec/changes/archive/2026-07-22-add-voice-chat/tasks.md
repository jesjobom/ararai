## 1. Version 0 Dependency Spike

- [ ] 1.1 Evaluate candidate WebRTC and Silero Android artifacts for provenance,
  maintenance, license, reproducible resolution, API 28 compatibility, arm64 ABI,
  16 KB page alignment, packaged size, startup, CPU, and memory cost.
- [ ] 1.2 Build a throwaway 16 kHz PCM frame harness that runs both VAD providers
  over the same quiet, speech, fan/TV, street, near/far, and multilingual samples.
- [ ] 1.3 Verify `MIC`, `VOICE_RECOGNITION`, and `VOICE_COMMUNICATION` plus requested
  versus effective `NoiseSuppressor` state on supported physical devices.
- [ ] 1.4 Record the chosen artifacts and integration constraints in the design
  before merging production dependencies; do not proceed if licensing, native
  packaging, or maintenance evidence is inadequate.

## 2. Stateless Audio Pipeline

- [x] 2.1 Define testable PCM-frame recorder, VAD provider, capture policy, optional
  audio-effect, temporary-file owner, and deterministic clock boundaries.
- [x] 2.2 Adapt the current PCM WAV recorder to publish frames without regressing
  Chat audio prompt recording or changing the model input format.
- [ ] 2.3 Implement WebRTC and Silero VAD adapters with shared speech-start,
  trailing-pause, leading-silence, unusable-capture, and provider-reset tests.
- [ ] 2.4 Implement optional native noise suppression and explicit capture-source
  selection with requested/effective state reporting and safe fallback tests.
- [ ] 2.5 Implement idempotent per-turn deletion and stale Voice Chat temporary-file
  reconciliation isolated from persistent Chat media.
- [x] 2.6 Add configurable speech confirmation, bounded pre-roll, minimum usable
  speech duration, and VAD aggressiveness for physical false-start testing.

## 3. Incremental Speech Pipeline

- [x] 3.1 Implement a pure cumulative-response segmenter with word counting,
  sentence/word boundaries, normalization, reasoning exclusion, residual flush,
  and fragmented-callback tests.
- [x] 3.2 Add sequential local language preparation and a FIFO native TTS queue
  with completion, stop/close idempotence, fallback voice, ordering, and failure
  tests without regressing manual Chat TTS.
- [x] 3.3 Propagate native TTS range progress through source-aware queued speech
  segments, with source-offset mapping, stale-callback protection, and a
  segment-level fallback for engines that omit precise ranges.
- [x] 3.4 Replace normal word-count cuts with sentence/newline-aware streaming
  boundaries and retain a tested hard-limit fallback for exceptionally long
  unfinished sentences.

## 4. Voice Loop and Diagnostics

- [ ] 4.1 Implement the Idle/Listening/Processing/Speaking/Error coordinator with
  run/turn IDs and tests for every transition, stop path, and stale callback.
- [x] 4.2 Submit only the configured system prompt and current direct-audio turn
  through the application-scoped runtime, with no Voice Chat store or prior-turn
  context.
- [ ] 4.3 Ensure listening resumes only after generation and queued speech finish,
  with no microphone/TTS overlap and cleanup on permission, model, inference,
  speech, navigation, and owner failures.
- [x] 4.4 Implement bounded in-memory diagnostic events and summaries without PCM,
  prompt/response text, stable identifiers, upload, or conversation persistence.
- [ ] 4.5 Add deterministic tests for diagnostic timing, effective configuration,
  bounds, reset, and disposal.

## 5. Version 0 UI and Preferences

- [x] 5.1 Add the Voice Chat home entry and destination with unavailable-model
  guidance and a path to model management.
- [x] 5.2 Build the portrait Compose screen with an accessible large start/stop
  control and listening/processing/speaking/error presentation.
- [x] 5.3 Add validated persistent pause-duration and minimum-word preferences with
  defaults, bounds, corrupt-value tests, and a dismissible settings overlay.
- [x] 5.4 Add clearly labelled experimental controls for VAD provider, capture
  source, and noise suppression, applying changes only on loop restart and showing
  effective state.
- [x] 5.5 Present the current response in a persistent two-line synchronized
  reading viewport and open a scrollable full-response overlay with the same
  active highlight when activated.
- [x] 5.6 Replace the five-level TTS rate experiment with a validated persistent
  0.5x-2.0x slider, including legacy preference migration, and apply
  it to every queued Voice Chat response segment without changing manual Chat
  playback speed.
- [ ] 5.7 Add Compose journey tests for settings, experiments, permission, stop,
  and errors while keeping raw diagnostic summaries out of the product screen.
- [x] 5.8 Align Voice Chat with the centered title/back-arrow pattern, remove raw
  diagnostics from the product screen, convert multi-option settings to
  dropdowns, and distinguish the Home entry with the tertiary container color.

## 6. Physical Comparison and Decision

- [ ] 6.1 Execute at least ten consecutive exchanges per candidate pipeline on the
  supported arm64 device matrix in quiet, fan/TV, street, near/far, and multilingual
  conditions.
- [ ] 6.2 Measure false starts, false ends, missed ends, pause latency, audio-to-first
  output, audio-to-first TTS, answer quality, cancellation, CPU, memory, battery,
  temperature, and packaged-size impact.
- [ ] 6.3 Verify no echo capture, microphone/TTS overlap, stale callbacks, leaked
  temporary WAVs, persisted conversation data, or regressions in Chat audio/TTS.
- [ ] 6.4 Document the evidence and create a follow-up OpenSpec decision for the
  production VAD/source/filter pipeline and any future sessions/context.

## 7. Quality and Documentation

- [x] 7.1 Update README, OpenSpec project context, privacy/storage documentation,
  and device-validation guidance with version 0 stateless/experimental limits.
- [x] 7.2 Run targeted tests, the full project quality gate, and
  `openspec validate --all --strict`.
- [ ] 7.3 Archive the change only after implementation, physical evidence, and
  follow-up production-direction documentation are complete.
