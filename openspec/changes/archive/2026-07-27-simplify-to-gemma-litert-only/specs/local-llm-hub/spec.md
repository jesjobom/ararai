## ADDED Requirements

### Requirement: Legacy GGUF Artifact Cleanup

The app SHALL remove only the known app-managed artifacts and partial downloads
for chat models removed from the checked-in catalog.

#### Scenario: Clean former managed GGUF downloads

- **GIVEN** a former app-managed GGUF model or its partial download exists
- **WHEN** the post-upgrade model-storage migration runs
- **THEN** the known legacy file is deleted
- **AND** current Gemma and Whisper artifacts remain unchanged.

#### Scenario: Preserve unknown files

- **GIVEN** an unknown file exists in app-owned model storage
- **WHEN** the post-upgrade model-storage migration runs
- **THEN** the unknown file is not deleted.

### Requirement: Supported Model Runtime Metadata

The app SHALL accept only LiteRT-LM chat bundles and Whisper transcription
artifacts in checked-in model runtime metadata.

#### Scenario: Parse supported runtime metadata

- **GIVEN** a catalog entry declares LiteRT-LM or Whisper runtime metadata
- **WHEN** the app parses and resolves the entry
- **THEN** it records the runtime, artifact format, and acceleration policy.

#### Scenario: Reject removed chat runtimes

- **GIVEN** a catalog entry declares llama.cpp or GGUF
- **WHEN** the app parses the entry
- **THEN** catalog validation fails with a controlled unsupported-value error.

### Requirement: Gemma-Only Chat Catalog

The checked-in chat catalog SHALL contain Gemma 4 E2B and E4B LiteRT-LM bundles
and SHALL use Gemma 4 E2B as its default model.

#### Scenario: Download Gemma LiteRT-LM artifact

- **GIVEN** a Gemma LiteRT-LM catalog entry is selected
- **AND** its configured `.litertlm` file is missing
- **WHEN** the download flow starts
- **THEN** the app downloads the configured `.litertlm` artifact
- **AND** validates size and SHA-256 before making it available.

#### Scenario: Present Gemma choices

- **WHEN** the user views configured chat models
- **THEN** Gemma 4 E2B and E4B are available
- **AND** no GGUF chat model is shown
- **AND** their LiteRT-LM runtime and acceleration policy are shown.

## MODIFIED Requirements

### Requirement: Configured Model Startup Resolution

The app SHALL support a configured Gemma 4 LiteRT-LM chat model catalog with one
default model.

#### Scenario: Load existing selected configured model

- **GIVEN** the selected configured Gemma model exists at its configured
  app-owned path
- **AND** the file passes the configured integrity check
- **WHEN** model resolution runs
- **THEN** the app can pass that file to the local inference engine
- **AND** the model list reports that model as available.

#### Scenario: Download missing selected configured model

- **GIVEN** the selected configured Gemma model is missing or fails integrity
  validation
- **AND** no other configured chat model is available locally
- **WHEN** the app starts with network access
- **THEN** the app automatically downloads Gemma 4 E2B as the configured default
- **AND** validates the downloaded file before loading it.

#### Scenario: Skip default download when another model is available

- **GIVEN** Gemma 4 E2B is missing
- **AND** another configured Gemma chat model is already available locally
- **WHEN** the app starts
- **THEN** the app does not automatically download the default model
- **AND** the available model is selected for chat.

### Requirement: Runtime Boundary

The application SHALL isolate Gemma 4 execution behind an inference engine
boundary backed by LiteRT-LM.

#### Scenario: Runtime replacement

- **WHEN** a future runtime is evaluated
- **THEN** the app can add another engine implementation without rewriting the
  chat UI or configured-model resolution flow.

#### Scenario: Real runtime behind boundary

- **GIVEN** a configured Gemma 4 LiteRT-LM model is available
- **WHEN** the chat flow requests generation
- **THEN** the app uses LiteRT-LM behind `LocalLlmEngine`
- **AND** the chat UI does not depend directly on LiteRT-LM types.

### Requirement: Fixed Model Configuration

The checked-in configuration SHALL include Gemma 4 E2B and E4B LiteRT-LM chat
models and supported Whisper transcription models, with Gemma 4 E2B as default.

#### Scenario: Parse configured model catalog

- **WHEN** the app starts
- **THEN** it parses only supported LiteRT-LM chat and Whisper utility entries
- **AND** each entry defines its ID, source URL, expected local path, integrity
  metadata, expected download size, recommended free RAM, runtime, artifact
  format, acceleration, capabilities, and applicable inference parameters.

#### Scenario: Keep configured model list static

- **WHEN** the user opens model management
- **THEN** the app shows only models declared by checked-in configuration
- **AND** it shows no GGUF or llama.cpp chat model
- **AND** the UI does not allow arbitrary model entries to be added.

#### Scenario: Validate model resource metadata

- **GIVEN** a catalog entry declares expected download size or recommended free
  RAM
- **WHEN** the app parses the catalog
- **THEN** each declared value is a positive byte count.

### Requirement: Real Local LLM Runtime

The app SHALL provide LiteRT-LM local inference for configured Gemma 4 bundles
that are present and valid on the device.

#### Scenario: Load available configured model

- **GIVEN** model startup reports a configured Gemma model as available
- **WHEN** chat starts real generation
- **THEN** the app loads that exact `.litertlm` bundle through LiteRT-LM
- **AND** applies the configured inference defaults
- **AND** does not use a remote inference API.

#### Scenario: Native load failure

- **GIVEN** the configured Gemma model is reported available
- **AND** LiteRT-LM fails to load it
- **WHEN** the user attempts generation
- **THEN** the chat screen shows a load error
- **AND** prompt submission becomes available again when otherwise ready
- **AND** the app does not crash.

## REMOVED Requirements

### Requirement: GGUF Chat Template Formatting

**Reason**: GGUF and llama.cpp chat inference are no longer supported.

**Migration**: Gemma 4 chat formatting is owned by LiteRT-LM.

### Requirement: Configured Model Runtime Metadata

**Reason**: The generic legacy metadata contract includes llama.cpp defaults,
GGUF compatibility, and GPU-layer configuration that are no longer supported.

**Migration**: Use `Supported Model Runtime Metadata`, which accepts only
LiteRT-LM chat bundles and Whisper transcription artifacts.

### Requirement: Gemma Runtime Catalog Variant

**Reason**: Gemma LiteRT-LM is no longer a variant alongside a GGUF fallback;
it is the complete supported Chat catalog.

**Migration**: Use `Gemma-Only Chat Catalog`, with E2B as default and E4B as the
higher-resource option.

### Requirement: Runtime-Driven Local Engine Selection

**Reason**: Chat has one supported runtime, LiteRT-LM, so runtime dispatch is no
longer needed.

**Migration**: Wire `LiteRtLmLocalLlmEngine` directly behind
`LocalLlmEngine`; Whisper remains a separate transcription boundary.

### Requirement: Configuration-aware llama.cpp runtime reuse

**Reason**: The llama.cpp runtime and native handles are removed.

**Migration**: LiteRT-LM conversation reuse and disposal requirements remain
authoritative for the supported Gemma models.
