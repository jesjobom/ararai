## ADDED Requirements

### Requirement: Workload-Organized Model Management

The app SHALL separate configured local models into Chat and Transcription
tabs. The Chat tab SHALL contain configured Chat/LLM models, including
models that do not expose optional reasoning controls, and the Transcription tab
SHALL contain models that support the transcription task.

#### Scenario: Browse workload tabs

- **GIVEN** the catalog contains Chat and transcription models
- **WHEN** the user opens Model Management
- **THEN** the app initially displays the Chat tab
- **AND** only Chat/LLM models appear in Chat
- **AND** only transcription-task models appear in Transcription.

### Requirement: Family-Preserving Model Weight Order

The configured catalog SHALL declare stable model-family identity and Model
Management SHALL keep entries from the same family contiguous while ordering
families and variants from lighter to heavier expected artifacts. Entries with
unknown expected size SHALL sort after entries with known size.

#### Scenario: Order related variants

- **GIVEN** a workload contains multiple families and multiple variants of one
  family
- **WHEN** Model Management presents that workload
- **THEN** all variants in the same family are adjacent
- **AND** variants inside the family are ordered by expected artifact bytes
  ascending
- **AND** families are ordered by their lightest member's expected artifact
  bytes ascending.

### Requirement: Available-Memory Model Recommendation

Model Management SHALL compare currently available device memory with each
model's declared recommended free RAM and SHALL identify models whose
requirement fits as recommended without blocking other models.

#### Scenario: Present models that fit available memory

- **GIVEN** the device reports currently available memory
- **AND** a model declares recommended free RAM no greater than that value
- **WHEN** the model card is displayed
- **THEN** the card identifies the model as recommended
- **AND** the screen explains the available-memory basis for the indication.

#### Scenario: Retain a model that does not fit

- **GIVEN** a model's declared recommended free RAM exceeds currently available
  memory
- **WHEN** the model card is displayed
- **THEN** the model remains visible with its normal lifecycle actions
- **AND** the app does not identify it as recommended.

## MODIFIED Requirements

### Requirement: Mobile Inference Benchmark Screen

The app SHALL expose a dedicated benchmark screen for repeatable local
inference measurements, SHALL open it for the exact downloaded reasoning model
chosen in Model Management, and SHALL label only runtime-backed token
measurements as token counts or token throughput.

#### Scenario: Open benchmark from home

- **GIVEN** the user is viewing a downloaded reasoning model in Model
  Management
- **WHEN** the user opens its benchmark
- **THEN** the app shows a benchmark screen for that exact model
- **AND** back navigation returns to Model Management.

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
- **WHEN** the user views Model Management
- **THEN** the app does not expose its benchmark action
- **AND** retains the action needed to make the model available locally.

### Requirement: Chat-Centered Home

Home SHALL present Chat and Voice Chat as daily-use conversation actions while
keeping model management and settings visible. Model benchmark access SHALL be
owned by downloaded model cards rather than a separate Home destination.

#### Scenario: Home action hierarchy

- **WHEN** the user opens the app Home screen
- **THEN** Chat and Voice Chat use visually consistent, fully clickable cards
- **AND** model management and settings use a consistent utility card treatment
- **AND** no Home destination requires a nested action button
- **AND** Home does not present a standalone reasoning benchmark or diagnostics
  destination.
