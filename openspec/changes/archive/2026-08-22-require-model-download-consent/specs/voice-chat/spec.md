## MODIFIED Requirements

### Requirement: Voice Chat destination

The app SHALL expose Voice Chat as a dedicated conversation destination that
uses the app's current persisted conversation while retaining its focused
hands-free presentation, and SHALL prevent entry while no local chat model is
available.

#### Scenario: Open Voice Chat

- **GIVEN** the user is on the home screen
- **AND** a configured chat model is available locally
- **WHEN** the user activates any part of the Voice Chat card
- **THEN** Voice Chat resolves the current persisted conversation or creates one
  when none exists
- **AND** the idle screen centers a large action for starting the loop
- **AND** settings are available
- **AND** the current conversation and its session-management controls are
  available
- **AND** prior eligible conversation history is available to the shared
  coordinator without being required in the focused Voice Chat layout.

#### Scenario: Block Voice Chat without a chat model

- **GIVEN** no configured chat model is available locally
- **WHEN** the home screen is displayed
- **THEN** the Voice Chat card uses a disabled gray visual treatment
- **AND** activating the card does not open Voice Chat
- **AND** the app briefly tells the user to download a model.

#### Scenario: Manage conversations from Voice Chat

- **GIVEN** Voice Chat is idle
- **WHEN** the user opens its session controls
- **THEN** the user can create, select, rename, delete, or clear conversations
  through the same interaction used by normal Chat
- **AND** the selected conversation remains shared across both destinations
- **AND** session changes are unavailable while Voice Chat is active or loading.

#### Scenario: Distinguish conversation and utility destinations

- **WHEN** the home screen is displayed
- **THEN** Chat and Voice Chat use the same color scheme when both are available
- **AND** Models, Diagnostics, and Settings use the Model Management color scheme
- **AND** every available destination card is clickable without an internal action button.

#### Scenario: Selected model cannot accept audio

- **GIVEN** the selected model is locally available but does not declare audio
  input support
- **AND** local transcription is available
- **WHEN** Voice Chat is displayed
- **THEN** the start action is available
- **AND** completed audio turns are transcribed before text generation.

#### Scenario: Voice input cannot be routed

- **GIVEN** the selected model does not accept audio
- **AND** local transcription is unavailable
- **WHEN** Voice Chat is displayed
- **THEN** the start action is unavailable
- **AND** the screen explains that direct-audio support or a local transcription
  model is required
- **AND** it provides a path to model management.

#### Scenario: Prepare the selected model before starting

- **GIVEN** the selected model is locally available
- **AND** voice input can be routed through direct audio or local transcription
- **WHEN** Voice Chat is opened
- **THEN** the app prepares the required workload
- **AND** the start action remains disabled until required preparation completes
- **AND** the first captured turn does not perform avoidable model preparation
  while presenting the thinking state.
