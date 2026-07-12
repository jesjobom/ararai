## ADDED Requirements

### Requirement: Persistent Selected Model

The app SHALL persist the user's selected model ID locally and restore it on
startup when the model still exists in the checked-in catalog.

#### Scenario: Restore selected model

- **GIVEN** the user selected a non-default configured model
- **WHEN** the app is restarted
- **THEN** the previously selected model is selected again
- **AND** the app falls back to the normal catalog selection only if that model
  ID no longer exists.

### Requirement: Persistent Chat Sessions

The app SHALL persist chat sessions and their messages locally on the device.

#### Scenario: Resume session after restart

- **GIVEN** a chat session contains user and assistant messages
- **WHEN** the app is restarted
- **THEN** the session is available in Chat
- **AND** its messages are restored.

#### Scenario: Manage sessions

- **WHEN** the user uses the Chat session controls
- **THEN** the user can create a new session
- **AND** switch between sessions
- **AND** rename the current session
- **AND** delete a session without deleting other sessions.

### Requirement: Configured Chat System Prompt

The checked-in app configuration SHALL define a short Chat system prompt used
when constructing local generation prompts.

#### Scenario: Build prompt with configured system prompt

- **GIVEN** the configured system prompt is present
- **AND** a chat session has previous messages
- **WHEN** the user submits a new prompt
- **THEN** generation receives a prompt containing the system prompt
- **AND** recent session history
- **AND** the new user message.

### Requirement: Simple Context Window Management

The app SHALL include recent session history in generation prompts using a
simple context budget based on the selected model's configured context size.

#### Scenario: Long session prompt construction

- **GIVEN** a session history is longer than the selected model's context budget
- **WHEN** the user submits a new prompt
- **THEN** the app includes the newest messages that fit the budget
- **AND** omits older messages
- **AND** does not perform automatic summarization yet.
