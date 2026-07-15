## ADDED Requirements

### Requirement: In-Place Audio Prompt Recording

The Chat composer SHALL let the user record an audio prompt directly when the
selected model and runtime support audio input.

#### Scenario: Start audio recording

- **GIVEN** the selected model supports audio input
- **AND** the current draft has no text or image attachment
- **WHEN** the user starts audio recording from the Chat composer
- **THEN** the app requests microphone permission if needed
- **AND** opens the recording dialog
- **AND** starts recording into app-owned storage after permission is granted.

#### Scenario: Review recorded audio

- **GIVEN** audio recording is active
- **WHEN** the user stops recording
- **THEN** the app shows the recorded prompt with duration
- **AND** stores the recording in a runtime-decodable PCM WAV container
- **AND** the user can replay the recording in the dialog
- **AND** the user can cancel and delete the recording
- **AND** the user can send the recording from the dialog.

#### Scenario: Send recorded audio prompt

- **GIVEN** the user stopped and reviewed a recorded audio prompt
- **WHEN** the user submits Chat
- **THEN** generation receives an `AudioPrompt` backed by the recorded local file
- **AND** the recorded audio prompt is persisted with the chat message.

#### Scenario: Replay persisted audio prompt

- **GIVEN** a chat message contains an audio prompt
- **WHEN** the message is shown in chat history
- **THEN** the user can replay the audio from that message.

#### Scenario: Hide recording for unsupported model

- **GIVEN** the selected model does not support audio input
- **WHEN** the Chat composer is displayed
- **THEN** no audio recording action is presented.
