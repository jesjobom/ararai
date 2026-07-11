## ADDED Requirements

### Requirement: Configured Model Runtime Metadata

The app SHALL allow each configured model catalog entry to declare its local
inference runtime, artifact format, and acceleration policy.

#### Scenario: Parse runtime metadata from catalog

- **GIVEN** a configured model catalog entry includes runtime metadata
- **WHEN** the app parses the catalog
- **THEN** the model config records the runtime, artifact format, and
  acceleration policy.

#### Scenario: Default legacy GGUF entries to llama.cpp

- **GIVEN** a legacy configured model entry omits runtime metadata
- **WHEN** the app parses the entry
- **THEN** it defaults to the llama.cpp runtime
- **AND** defaults to the GGUF artifact format
- **AND** defaults to GPU-preferred acceleration.

### Requirement: Runtime-Driven Local Engine Selection

The app SHALL choose local inference behavior from the selected model's
configured runtime metadata rather than hardcoded model IDs.

#### Scenario: Load llama.cpp model with configured acceleration

- **GIVEN** a selected available model uses the llama.cpp runtime
- **AND** its acceleration policy is CPU-only
- **WHEN** the app loads the model for chat or benchmark
- **THEN** it requests CPU-only llama.cpp inference.

#### Scenario: Reject unsupported configured runtime

- **GIVEN** a selected available model uses a runtime not implemented by the app
- **WHEN** the app attempts to load the model
- **THEN** generation or benchmark execution fails with a controlled error.

### Requirement: Runtime Metadata In Benchmark

The benchmark screen SHALL display the selected model runtime so results can be
compared across runtime libraries and acceleration policies.

#### Scenario: View selected runtime in benchmark details

- **GIVEN** the user opens the benchmark screen
- **WHEN** a configured model is selected
- **THEN** the benchmark details show the selected model's runtime and
  acceleration policy.
