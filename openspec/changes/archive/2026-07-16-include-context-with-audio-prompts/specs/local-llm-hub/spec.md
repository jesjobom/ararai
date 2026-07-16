## ADDED Requirements

### Requirement: Textual Context for Audio Prompts

When the selected model supports audio input, the app SHALL send the current
audio file together with bounded textual context from the selected Chat
session.

#### Scenario: Send audio with system instruction

- **GIVEN** the selected model declares audio input support
- **AND** Chat has a configured system prompt
- **WHEN** the user submits a current audio prompt
- **THEN** the generation request contains the current audio file
- **AND** includes the configured system instruction as textual context.

#### Scenario: Send audio with recent textual history

- **GIVEN** the selected session contains prior user and assistant messages
- **WHEN** the user submits a current audio prompt
- **THEN** the generation request includes recent textual history from that
  selected session
- **AND** preserves user and assistant roles
- **AND** applies the configured context budget before generation.

#### Scenario: Historical messages contain media

- **GIVEN** the selected session contains historical image or audio attachments
- **WHEN** the user submits a current audio prompt
- **THEN** this change does not re-send those historical media files
- **AND** any textual representation already used by the bounded history may
  remain in the context.

#### Scenario: Current model does not support audio

- **GIVEN** the selected model does not declare audio input support
- **WHEN** an audio prompt would otherwise be submitted
- **THEN** the app does not send the audio generation request to that model.

#### Scenario: Context exceeds the configured budget

- **GIVEN** the system instruction and session history exceed the available
  input context budget
- **WHEN** the user submits a current audio prompt
- **THEN** the app retains the system instruction
- **AND** selects the most recent fitting textual history
- **AND** reserves output capacity according to the current inference
  configuration.
