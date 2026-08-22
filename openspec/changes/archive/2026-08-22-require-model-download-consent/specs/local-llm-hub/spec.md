## MODIFIED Requirements

### Requirement: Configured Model Startup Resolution

The app SHALL support a configured Gemma 4 LiteRT-LM chat model catalog with one
default model and SHALL NOT initiate a model download solely because no local
chat model is available at startup.

#### Scenario: Load existing selected configured model

- **GIVEN** the selected configured Gemma model exists at its configured
  app-owned path
- **AND** the file passes the configured integrity check
- **WHEN** model resolution runs
- **THEN** the app can pass that file to the local inference engine
- **AND** the model list reports that model as available.

#### Scenario: Download missing selected configured model

- **GIVEN** no configured chat model is available locally
- **WHEN** the app starts with or without network access
- **THEN** the app reports the configured default model as missing or invalid
- **AND** does not start a model download without a user download action.

#### Scenario: Skip default download when another model is available

- **GIVEN** Gemma 4 E2B is missing
- **AND** another configured Gemma chat model is already available locally
- **WHEN** the app starts
- **THEN** the app does not download the default model
- **AND** the available model is selected for chat.

### Requirement: Configured Model Download

The app SHALL download configured model artifacts only in response to an
explicit user download or retry action.

#### Scenario: Download missing model

- **GIVEN** the configured model file is missing from the app-owned model path
- **WHEN** the user chooses to download that model
- **THEN** the app starts downloading the configured model source immediately
  on the current network
- **AND** reports a downloading state and progress
- **AND** keeps chat submission disabled until validation succeeds.

#### Scenario: Retry failed model download

- **GIVEN** a configured model download fails
- **WHEN** the failure is shown to the user
- **THEN** the user can retry the same configured download
- **AND** the retry does not require selecting another model.

### Requirement: Chat-Centered Home

Home SHALL present Chat and Voice Chat as daily-use conversation actions while
keeping model management and settings visible. Model benchmark access SHALL be
owned by downloaded model cards rather than a separate Home destination.

#### Scenario: Home action hierarchy

- **GIVEN** a configured chat model is available locally
- **WHEN** the user opens the app Home screen
- **THEN** Chat and Voice Chat use visually consistent, fully clickable cards
- **AND** model management and settings use a consistent utility card treatment
- **AND** no Home destination requires a nested action button
- **AND** Home does not present a standalone reasoning benchmark or diagnostics
  destination.

#### Scenario: Home action hierarchy without a chat model

- **GIVEN** no configured chat model is available locally
- **WHEN** the user opens the app Home screen
- **THEN** normal Chat remains visually available and opens its conversation history
- **AND** Voice Chat uses a disabled visual treatment
- **AND** tapping Voice Chat shows brief guidance to download a model
- **AND** Model Management remains available.

## ADDED Requirements

### Requirement: First-Launch Model Download Consent

The app SHALL explain the local model prerequisite once on the first application
launch that has no available local chat model, SHALL require an explicit user
action before beginning the default-model transfer, and SHALL persist that the
prompt was handled.

#### Scenario: Offer model choices on first launch

- **GIVEN** the application has not previously handled the model download prompt
- **AND** no configured chat model is available locally
- **WHEN** Home is first displayed
- **THEN** a dialog explains that a model is required for Chat functionality
- **AND** identifies the configured default model and its approximate download size
- **AND** offers actions to download the default model, open Model Management, or close the dialog.

#### Scenario: Download the default model from the prompt

- **GIVEN** the first-launch model dialog is displayed
- **WHEN** the user chooses to download the default model
- **THEN** the existing download flow starts immediately on the current network
- **AND** the prompt is recorded as handled
- **AND** no additional network-type confirmation is required.

#### Scenario: Open Model Management from the prompt

- **GIVEN** the first-launch model dialog is displayed
- **WHEN** the user chooses to view the model list
- **THEN** the prompt is recorded as handled
- **AND** the app opens Model Management without starting a download.

#### Scenario: Close the prompt without downloading

- **GIVEN** the first-launch model dialog is displayed
- **WHEN** the user closes or dismisses it
- **THEN** the prompt is recorded as handled
- **AND** no model download starts.

#### Scenario: Do not repeat the handled prompt

- **GIVEN** the first-launch model prompt was previously handled
- **AND** no configured chat model is available locally
- **WHEN** the app is launched again
- **THEN** the dialog is not shown again
- **AND** model download guidance remains discoverable from unavailable actions and Model Management.

#### Scenario: Skip the prompt when a model already exists

- **GIVEN** at least one configured chat model is available locally
- **WHEN** the app is launched for the first time
- **THEN** the first-launch model dialog is not shown.

### Requirement: Model-Unavailable Normal Chat

Normal Chat SHALL remain accessible without a local chat model so users can view
and manage conversation history, while preventing all new message submission
until a model becomes available.

#### Scenario: Browse Chat without a model

- **GIVEN** no configured chat model is available locally
- **WHEN** the user opens normal Chat
- **THEN** existing conversation history and session-management actions remain available
- **AND** the composer does not allow a text, audio, image, or combined message to be submitted.

#### Scenario: Interact with the unavailable composer

- **GIVEN** no configured chat model is available locally
- **WHEN** the user taps or otherwise attempts to use the message composer
- **THEN** the app shows brief guidance that a model must be downloaded
- **AND** does not append or submit a message.

#### Scenario: Restore composer availability

- **GIVEN** normal Chat is open without an available model
- **WHEN** a configured chat model becomes locally available and valid
- **THEN** composer availability follows the normal prompt and generation-state rules
- **AND** no application restart is required.
