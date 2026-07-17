## MODIFIED Requirements

### Requirement: Mobile Inference Benchmark Screen

The app SHALL expose a dedicated benchmark screen for repeatable local
inference measurements and SHALL label only runtime-backed token measurements
as token counts or token throughput.

#### Scenario: Open benchmark from home

- **GIVEN** the user is on `Home`
- **WHEN** the user opens benchmark
- **THEN** the app shows a benchmark screen separate from chat and model
  management.

#### Scenario: View stable benchmark parameters

- **GIVEN** the user is viewing the benchmark screen
- **THEN** the app shows the selected model
- **AND** shows the backend label
- **AND** shows the benchmark prompt label, context token limit, and maximum
  generated token limit used for the run.

#### Scenario: Run benchmark for available model

- **GIVEN** the selected configured model is available locally
- **AND** its runtime exposes native prefill and decode statistics
- **WHEN** the user starts the benchmark
- **THEN** the app loads the selected model through the local inference engine
- **AND** generates text with stable benchmark parameters
- **AND** reports load time and time to first token separately
- **AND** reports native prefill token count and throughput separately from
  native decode token count and throughput
- **AND** does not use streamed callback count as generated token count.

#### Scenario: Run benchmark without native token metrics

- **GIVEN** the selected runtime does not expose a trustworthy token count
- **WHEN** the benchmark completes
- **THEN** the app reports the available latency and elapsed-time measurements
- **AND** any fallback throughput has an accurate non-token unit
- **AND** the app does not label streamed callback chunks as tokens.

#### Scenario: Block benchmark for unavailable model

- **GIVEN** the selected configured model is missing, downloading, invalid, or
  failed
- **WHEN** the user views the benchmark screen
- **THEN** the app disables benchmark execution
- **AND** explains that the selected model must be available locally first.

## ADDED Requirements

### Requirement: Workload-Aware LiteRT-LM Modality Profile

The app SHALL initialize only the LiteRT-LM modality backends required by the
active workload while preserving the selected model's configured capabilities.

#### Scenario: Load Gemma for text-only generation

- **GIVEN** a configured LiteRT-LM Gemma model supports text, image, and audio
- **WHEN** the model is loaded for a text-only workload
- **THEN** the language-model backend uses its configured acceleration policy
- **AND** vision and audio processing backends are not initialized.

#### Scenario: Reconfigure for a multimodal request

- **GIVEN** the active LiteRT-LM engine profile does not include a supported
  modality required by the next request
- **WHEN** generation starts
- **THEN** the app closes incompatible retained conversation state
- **AND** recreates the LiteRT-LM engine with the required modality backend
- **AND** processes the request without changing catalog capability metadata.

#### Scenario: Reject a modality absent from model capabilities

- **GIVEN** a request uses a modality the selected model does not support
- **WHEN** generation is requested
- **THEN** the app returns a controlled failure
- **AND** does not recreate the LiteRT-LM engine for that unsupported modality.

### Requirement: Safe LiteRT-LM Conversation Reuse

The app SHALL reuse LiteRT-LM conversation state only for a verified compatible
continuation and SHALL prevent conversation context from crossing chat-session
or configuration boundaries.

#### Scenario: Continue a compatible chat session

- **GIVEN** a LiteRT-LM generation completed successfully for a persisted chat
  session
- **AND** the next request identifies the same session
- **AND** its history exactly matches the transcript retained by the runtime
- **AND** model, modality profile, sampler settings, and reasoning mode are
  unchanged
- **WHEN** the user submits the next message
- **THEN** the app reuses the existing LiteRT-LM conversation
- **AND** sends only the new user content instead of prefilling the complete
  transcript again.

#### Scenario: Start a fresh incompatible conversation

- **GIVEN** there is retained LiteRT-LM conversation state
- **WHEN** the next request belongs to another session or has incompatible
  transcript, model, modality profile, sampler settings, or reasoning mode
- **THEN** the app closes the retained conversation
- **AND** creates a new conversation initialized from the request's eligible
  system instruction and history.

#### Scenario: Invalidate partial or failed conversation state

- **GIVEN** a LiteRT-LM generation is cancelled or fails
- **WHEN** cleanup runs
- **THEN** the app closes and discards that conversation
- **AND** a later request cannot reuse its partial native state.

#### Scenario: Keep benchmark runs isolated

- **WHEN** a LiteRT-LM benchmark run starts
- **THEN** it uses a fresh conversation without retained chat-session context
- **AND** its metrics describe only that benchmark run.

### Requirement: App-Owned LiteRT-LM Runtime Cache

The app SHALL provide LiteRT-LM with a reclaimable app-owned cache directory
without making cache availability a prerequisite for inference.

#### Scenario: Initialize LiteRT-LM cache

- **WHEN** the app constructs the LiteRT-LM engine
- **THEN** it passes a dedicated directory below the app cache root through the
  LiteRT-LM engine configuration
- **AND** no shared-storage permission is required.

#### Scenario: Cache directory is unavailable

- **GIVEN** the dedicated cache directory cannot be created or used
- **WHEN** LiteRT-LM initialization starts
- **THEN** the app records diagnostics and attempts uncached initialization
- **AND** chat or benchmark does not fail solely because cache setup failed.
