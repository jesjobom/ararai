## ADDED Requirements

### Requirement: LiteRT-LM Gemma Runtime

The app SHALL support configured Gemma 4 models using the LiteRT-LM runtime and
`.litertlm` artifact format.

#### Scenario: Load configured Gemma LiteRT-LM model

- **GIVEN** the selected configured model declares `runtime=litert_lm`
- **AND** the model artifact is available and valid at its configured app-owned
  path
- **WHEN** chat or benchmark loads the model
- **THEN** the app initializes LiteRT-LM with that exact `.litertlm` file
- **AND** does not pass the file to llama.cpp.

#### Scenario: Prefer GPU for LiteRT-LM Gemma

- **GIVEN** the selected configured LiteRT-LM model declares
  `acceleration=gpu_preferred`
- **WHEN** the app initializes the LiteRT-LM engine
- **THEN** the engine requests the LiteRT-LM GPU backend.

#### Scenario: Surface LiteRT-LM generation failure

- **GIVEN** LiteRT-LM fails to initialize or generate text
- **WHEN** the user runs chat or benchmark
- **THEN** the app reports a controlled generation failure
- **AND** the app does not crash.

### Requirement: Gemma Runtime Catalog Variant

The checked-in model catalog SHALL include a Gemma 4 LiteRT-LM variant separate
from the existing Gemma 4 GGUF fallback.

#### Scenario: Download Gemma LiteRT-LM artifact

- **GIVEN** the Gemma LiteRT-LM catalog entry is selected
- **AND** its configured `.litertlm` file is missing
- **WHEN** the download flow starts
- **THEN** the app downloads the configured `.litertlm` artifact
- **AND** validates size and SHA-256 before making it available.

#### Scenario: Compare Gemma runtimes

- **GIVEN** both Gemma catalog entries are present
- **WHEN** the user views benchmark details
- **THEN** the app shows whether the selected Gemma model uses llama.cpp or
  LiteRT-LM
- **AND** shows the selected acceleration policy.
