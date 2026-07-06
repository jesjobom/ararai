# local-llm-hub Specification

## ADDED Requirements

### Requirement: Configured Model Download

The app SHALL automatically download the single configured GGUF model when the
configured final model file is missing or invalid.

#### Scenario: Download missing model

- **GIVEN** the configured model file is missing from the app-owned model path
- **WHEN** the app starts with network access
- **THEN** it starts downloading the configured model source
- **AND** reports a downloading state in the debug UI
- **AND** keeps chat submission disabled until validation succeeds.

#### Scenario: Retry failed model download

- **GIVEN** a configured model download fails
- **WHEN** the failure is shown in the debug UI
- **THEN** the user can retry the same configured download
- **AND** the retry does not require selecting a model.

### Requirement: Safe Model File Promotion

The app SHALL keep the final configured model path reserved for validated model
files only.

#### Scenario: Promote validated temporary file

- **GIVEN** the configured model download completed into a sibling temporary
  file
- **AND** the temporary file matches the configured byte size and SHA-256
- **WHEN** validation succeeds
- **THEN** the app atomically renames the temporary file to the configured final
  model path
- **AND** reruns model resolution so the model becomes available.

#### Scenario: Reject invalid temporary file

- **GIVEN** the configured model download completed into a sibling temporary
  file
- **AND** the temporary file fails byte-size or SHA-256 validation
- **WHEN** validation fails
- **THEN** the app does not promote the temporary file to the final model path
- **AND** reports a failed state that can be retried.
