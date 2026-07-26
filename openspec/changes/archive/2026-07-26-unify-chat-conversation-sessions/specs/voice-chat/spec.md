## MODIFIED Requirements

### Requirement: Voice Chat destination

The app SHALL expose Voice Chat as a dedicated conversation destination that
uses the app's current persisted conversation while retaining its focused
hands-free presentation.

#### Scenario: Open Voice Chat

- **GIVEN** the user is on the home screen
- **WHEN** the user activates any part of the Voice Chat card
- **THEN** Voice Chat resolves the current persisted conversation or creates one
  when none exists
- **AND** the idle screen centers a large action for starting the loop
- **AND** settings are available
- **AND** the current conversation and its session-management controls are
  available
- **AND** prior eligible conversation history is available to the shared
  coordinator without being required in the focused Voice Chat layout.

#### Scenario: Manage conversations from Voice Chat

- **GIVEN** Voice Chat is idle
- **WHEN** the user opens its session controls
- **THEN** the user can create, select, rename, delete, or clear conversations
  through the same interaction used by normal Chat
- **AND** the selected conversation remains shared across both destinations
- **AND** session changes are unavailable while Voice Chat is active or loading.

#### Scenario: Distinguish conversation and utility destinations

- **WHEN** the home screen is displayed
- **THEN** Chat and Voice Chat use the same color scheme
- **AND** Models, Diagnostics, and Settings use the Model Management color scheme
- **AND** every destination card is clickable without an internal action button.

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

## ADDED Requirements

### Requirement: Contextual half-duplex voice loop

Voice Chat SHALL process turns through the shared persisted conversation while
keeping microphone capture inactive during model processing and TTS playback.

#### Scenario: Start the voice loop

- **GIVEN** voice input can be routed to the selected locally available model
- **AND** Voice Chat is idle
- **WHEN** the user activates the central control
- **THEN** the app requests microphone permission if needed
- **AND** starts app-owned audio capture after permission is granted
- **AND** presents the listening state
- **AND** changes the central control to a stop action.

#### Scenario: Process one contextual turn

- **GIVEN** a valid audio turn has completed
- **WHEN** Voice Chat submits it through the shared conversation coordinator
- **THEN** the user turn is persisted in the current conversation
- **AND** an audio-capable model may receive direct audio while transcription
  enriches the persisted turn
- **AND** a text-only model receives the completed local transcript
- **AND** compatible native conversation state receives only the new user turn
- **AND** otherwise bounded persisted history initializes generation context
- **AND** a successful assistant response is persisted in the same conversation.

#### Scenario: Title the first voice turn

- **GIVEN** the current conversation still has the default title
- **WHEN** its first audio message receives a completed transcript
- **THEN** the transcript supplies the automatic conversation title
- **AND** asynchronous assistant persistence does not prevent that title update.

#### Scenario: Preserve the spoken language

- **GIVEN** local Whisper transcription is used for a Voice Chat turn
- **WHEN** the user speaks a Whisper-supported language
- **THEN** transcription uses automatic language detection
- **AND** the transcript remains in the detected spoken language rather than
  being forced to Portuguese.

#### Scenario: Resume listening after one exchange

- **GIVEN** current generation and every queued speech segment have completed
- **AND** the loop has not been stopped
- **WHEN** the contextual exchange finishes
- **THEN** non-canonical temporary capture resources are deleted
- **AND** canonical conversation media and messages remain persisted
- **AND** a fresh recording starts
- **AND** Voice Chat returns to listening with the accumulated conversation
  context.

#### Scenario: Continue the same conversation in normal Chat

- **GIVEN** Voice Chat committed one or more exchanges
- **WHEN** the user opens normal Chat
- **THEN** those exchanges appear in the current persisted conversation
- **AND** the next normal Chat turn continues the same eligible context
- **AND** a compatible live native session can be reused without resending its
  retained transcript.

#### Scenario: Continue a normal Chat conversation by voice

- **GIVEN** normal Chat committed one or more exchanges
- **WHEN** the user opens Voice Chat and submits a turn
- **THEN** Voice Chat uses the same current persisted conversation
- **AND** does not duplicate or resubmit the latest committed normal Chat turn.

## REMOVED Requirements

### Requirement: Stateless half-duplex voice loop

**Reason:** Voice Chat now participates in the shared persisted conversation and
no longer processes every turn independently.

**Migration:** Existing ephemeral Voice Chat turns require no data migration.
New turns use the current persisted app conversation.
