## ADDED Requirements

### Requirement: Chat Settings Overlay

The Chat screen SHALL expose a compact settings overlay for chat-specific
options.

#### Scenario: Open chat settings

- **GIVEN** the user is on the Chat screen
- **WHEN** the user opens Chat settings
- **THEN** the app shows an overlay above the chat content
- **AND** the overlay is visually consistent with the existing session list
  surface
- **AND** the overlay can be dismissed without leaving Chat.

#### Scenario: Future settings can be added

- **GIVEN** the Chat settings overlay is displayed
- **WHEN** new chat-specific options are added later
- **THEN** they can be placed in the same overlay without changing the main
  message list or composer layout.

### Requirement: Reasoning Chat Controls

The Chat settings overlay SHALL provide controls for enabling model reasoning
and for showing reasoning content.

#### Scenario: Gate reasoning controls by selected model

- **GIVEN** the selected model does not declare reasoning request support
- **WHEN** the user opens Chat settings
- **THEN** Enable reasoning is unavailable for that model
- **AND** chat generation requests for that model do not include a
  reasoning-enabled preference.

#### Scenario: Parse configured reasoning capabilities

- **GIVEN** a checked-in model catalog entry declares reasoning request or
  reasoning output support
- **WHEN** the catalog is parsed
- **THEN** the resulting model metadata exposes those reasoning capabilities to
  Chat UI and generation state.

#### Scenario: Enable reasoning for generation

- **GIVEN** the user opens Chat settings
- **AND** the selected model declares reasoning request support
- **WHEN** the user enables reasoning
- **THEN** future chat generation requests include a reasoning-enabled
  preference
- **AND** runtimes or models that do not support reasoning handle the preference
  as unsupported without failing the chat request.

#### Scenario: Hide reasoning content by default

- **GIVEN** the selected runtime returns reasoning content for an assistant
  response
- **AND** Show reasoning is disabled
- **WHEN** the chat message is rendered
- **THEN** the reasoning content is not shown in the conversation
- **AND** the final assistant answer remains visible.

#### Scenario: Show reasoning content when requested

- **GIVEN** the selected runtime returns reasoning content for an assistant
  response
- **AND** Show reasoning is enabled
- **WHEN** the chat message is rendered
- **THEN** the chat message exposes the reasoning content separately from the
  final assistant answer.

### Requirement: Chat Latest-Message Scroll Position

The Chat message list SHALL keep the latest messages visible when the user is
following the bottom of the conversation, while preserving the user's position
when they are reviewing older content.

#### Scenario: Open chat at latest message

- **GIVEN** the selected chat session contains existing messages
- **WHEN** the user enters the Chat screen
- **THEN** the message list scrolls to the latest message.

#### Scenario: Switch session at latest message

- **GIVEN** the user is on the Chat screen
- **AND** another session contains existing messages
- **WHEN** the user switches to that session
- **THEN** the message list scrolls to the latest message in the selected
  session.

#### Scenario: Keep following new content at bottom

- **GIVEN** the Chat message list is already at the bottom
- **WHEN** the user sends a message or assistant output is appended
- **THEN** the message list scrolls as needed to keep the latest content
  visible.

#### Scenario: Preserve position while reviewing older content

- **GIVEN** the Chat message list is not at the bottom
- **WHEN** the user sends a message or assistant output is appended
- **THEN** the message list does not force-scroll to the latest content.
