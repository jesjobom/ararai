## ADDED Requirements

### Requirement: Mobile Inference Benchmark Screen

The app SHALL expose a dedicated benchmark screen for repeatable local
inference measurements.

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
- **WHEN** the user starts the benchmark
- **THEN** the app loads the selected model through the local inference engine
- **AND** generates text with stable benchmark parameters
- **AND** reports load time, first-token latency, generated token count, total
  generation time, and tokens per second.

#### Scenario: Block benchmark for unavailable model

- **GIVEN** the selected configured model is missing, downloading, invalid, or
  failed
- **WHEN** the user views the benchmark screen
- **THEN** the app disables benchmark execution
- **AND** explains that the selected model must be available locally first.
