## ADDED Requirements

### Requirement: Internal Back Navigation

The app SHALL handle Android system back from internal screens by returning to
Home instead of exiting the application.

#### Scenario: Back from internal screen

- **GIVEN** the user is on Chat, Benchmark, or Models
- **WHEN** Android system back is pressed
- **THEN** the app returns to Home
- **AND** releases active work associated with the internal screen.

### Requirement: Long Running Operation Cancellation

The app SHALL expose cancellation controls for model download, chat generation,
and benchmark execution.

#### Scenario: Cancel model download

- **GIVEN** a configured model is downloading
- **WHEN** the user cancels the download
- **THEN** the active download job is cancelled
- **AND** the temporary `.part` file is removed
- **AND** the model returns to a non-downloading state.

#### Scenario: Cancel chat generation

- **GIVEN** chat generation is active
- **WHEN** the user cancels generation
- **THEN** active generation is cancelled
- **AND** local runtime resources are unloaded
- **AND** the UI becomes ready for another prompt when the model is available.

#### Scenario: Cancel benchmark run

- **GIVEN** benchmark execution is active
- **WHEN** the user cancels the benchmark
- **THEN** active benchmark work is cancelled
- **AND** local runtime resources are unloaded
- **AND** the benchmark UI reports a cancelled state.

### Requirement: Build Timestamp Version Label

The app SHALL display a build timestamp version label on Home.

#### Scenario: Build creates timestamp version

- **WHEN** the debug APK is built
- **THEN** the app version name is generated from the build timestamp in
  `yyyyMMddHHmm` format
- **AND** Home displays it as `v<timestamp>`.

### Requirement: Stream Model Downloads To Disk

The app SHALL stream model downloads directly to disk instead of buffering the
entire model in memory.

#### Scenario: Download large model artifact

- **GIVEN** a configured model download starts
- **WHEN** bytes are received from the network
- **THEN** the app writes them incrementally to a sibling `.part` file
- **AND** promotes only the validated file to the final model path.

### Requirement: Pruned Test Model Catalog

The checked-in catalog SHALL include only currently useful test models.

#### Scenario: View configured model list

- **WHEN** the user opens Models
- **THEN** the list includes SmolLM2, Llama 3.2, and Gemma 4 LiteRT-LM
- **AND** the list does not include Gemma 4 GGUF CPU or Phi-4.
