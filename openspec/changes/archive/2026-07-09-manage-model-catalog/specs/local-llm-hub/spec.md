## MODIFIED Requirements

### Requirement: Configured Model Startup Resolution

The app SHALL support a configured GGUF model catalog. The existing single-model
configuration format SHALL remain valid and SHALL be interpreted as a catalog
with one default model.

#### Scenario: Load existing selected configured model

- **GIVEN** the selected configured GGUF model exists at its configured
  app-owned path
- **AND** the file passes the configured integrity check
- **WHEN** model resolution runs
- **THEN** the app can pass that file to the local inference engine
- **AND** the model list reports that model as available.

#### Scenario: Download missing selected configured model

- **GIVEN** the selected configured GGUF model is missing or fails integrity
  validation
- **AND** no other configured model is available locally
- **WHEN** the app starts with network access
- **THEN** the app automatically downloads the configured default model to its
  app-owned location
- **AND** validates the downloaded file before loading it.

#### Scenario: Skip default download when another model is available

- **GIVEN** the configured default model is missing
- **AND** another configured model is already available locally
- **WHEN** the app starts
- **THEN** the app does not automatically download the default model
- **AND** the available model is selected for chat.

### Requirement: Fixed Model Configuration

Phase 1 SHALL include checked-in configuration for at least one GGUF model and
its default inference limits.

#### Scenario: Parse configured model catalog

- **WHEN** the app starts
- **THEN** it can parse a configured model catalog
- **AND** each entry defines the model ID, source URL, expected local path,
  integrity metadata, and default inference parameters
- **AND** the default inference parameters include context size, sampling
  values, and maximum generated tokens.

#### Scenario: Keep configured model list static

- **WHEN** the user opens the model management screen
- **THEN** the app shows only models declared by checked-in configuration
- **AND** the UI does not allow arbitrary model entries to be added.

### Requirement: Model Status Screen

The app SHALL expose model download and availability details on a dedicated
model management screen.

#### Scenario: View configured models

- **GIVEN** the user is on `Home`
- **WHEN** the user opens models
- **THEN** the app shows the configured model list
- **AND** each model shows its availability or download state
- **AND** downloading models show progress when available.

#### Scenario: Manage model file

- **GIVEN** the user is viewing the model list
- **WHEN** a configured model is missing, failed, or available
- **THEN** the app offers the applicable action to download, retry, update by
  redownloading, or delete the local model file.

#### Scenario: Select active model

- **GIVEN** the user is viewing the model list
- **WHEN** the user selects a configured model
- **THEN** the selected model becomes the active model state used by chat
- **AND** missing or invalid selected models start the configured download flow.
