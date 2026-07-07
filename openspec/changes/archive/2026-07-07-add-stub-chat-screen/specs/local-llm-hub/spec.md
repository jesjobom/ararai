# local-llm-hub Specification

## MODIFIED Requirements

### Requirement: Home Entry Point

The app SHALL start on a home screen that can grow into a feature hub.

#### Scenario: Launch home

- **WHEN** the app launches
- **THEN** the first visible screen is `Home`
- **AND** the screen exposes an action to open model status
- **AND** the screen exposes an action to open chat.

## ADDED Requirements

### Requirement: Stub Chat Entry Point

The app SHALL expose a dedicated chat screen backed by the fake local inference
engine until native local inference is integrated.

#### Scenario: Open chat from home

- **GIVEN** the user is on `Home`
- **WHEN** the user opens chat
- **THEN** the app shows the chat screen
- **AND** the chat screen shows the current model availability state
- **AND** the chat screen uses the fake/stub `LocalLlmEngine`.

#### Scenario: Return from chat

- **GIVEN** the user is on the chat screen
- **WHEN** the user taps the back action
- **THEN** the app returns to `Home`.

### Requirement: Stub Chat Conversation Flow

The chat screen SHALL support a basic text conversation flow suitable for
validating the user experience before native inference exists.

#### Scenario: Send prompt with available model

- **GIVEN** the configured model is available
- **AND** the user typed a non-blank prompt
- **WHEN** the user sends the prompt
- **THEN** the app appends the user message to the conversation
- **AND** clears the prompt input
- **AND** shows that generation is in progress
- **AND** appends deterministic fake assistant output from the stub engine
- **AND** re-enables sending after generation completes.

#### Scenario: Block send while unavailable or busy

- **GIVEN** the prompt is blank, the configured model is unavailable, or
  generation is already in progress
- **WHEN** the chat screen renders
- **THEN** the send action is disabled.

#### Scenario: Surface generation failure

- **GIVEN** the fake/stub engine reports a generation failure
- **WHEN** generation fails
- **THEN** the chat screen shows an error state
- **AND** preserves the conversation messages already shown
- **AND** allows the user to edit the prompt and try again when sending is
  otherwise allowed.
