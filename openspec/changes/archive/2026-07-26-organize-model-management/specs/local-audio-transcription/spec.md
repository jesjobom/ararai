## ADDED Requirements

### Requirement: Per-Model Transcription Benchmark Access

Model Management SHALL expose benchmark access on each locally available
transcription model and SHALL run the transcription diagnostic with the exact
model chosen.

#### Scenario: Benchmark a downloaded transcription model

- **GIVEN** a configured transcription model is valid and installed
- **WHEN** the user chooses its benchmark action in the Transcription tab
- **THEN** the app opens the transcription benchmark for that exact model
- **AND** back navigation returns to Model Management.

#### Scenario: Hide benchmark for unavailable transcription model

- **GIVEN** a configured transcription model is missing, downloading, invalid,
  or failed
- **WHEN** its card is displayed
- **THEN** the card does not expose benchmark access
- **AND** retains the appropriate download, cancel, or retry action.

#### Scenario: Use the production transcription thread default

- **GIVEN** normal app transcription uses six CPU threads
- **WHEN** the user opens a transcription model benchmark
- **THEN** the benchmark initializes its selectable thread count to six
- **AND** the user can still select another offered thread count for an
  explicit comparison.
