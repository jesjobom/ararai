## ADDED Requirements

### Requirement: Clear All Chat Sessions

The Chat session list SHALL provide a confirmed bulk action that permanently
deletes all locally stored chat sessions and their messages while preserving a
valid empty Chat state.

#### Scenario: Request bulk session deletion

- **GIVEN** one or more Chat sessions exist
- **WHEN** the user selects `Clear all` from the Chat session list
- **THEN** the app asks for confirmation before deleting any session or message
- **AND** the confirmation explains that the deletion is permanent
- **AND** the user can cancel without changing stored Chat data.

#### Scenario: Confirm bulk session deletion

- **GIVEN** the bulk-delete confirmation is displayed
- **WHEN** the user confirms `Clear all`
- **THEN** all existing Chat sessions and their messages are deleted atomically
- **AND** the app creates and selects one new empty session
- **AND** no message from a deleted session is displayed
- **AND** draft text and pending image or audio attachments are cleared.

#### Scenario: Preserve unrelated local data

- **GIVEN** the user confirms clearing all Chat sessions
- **WHEN** the deletion completes
- **THEN** downloaded models and application settings remain unchanged.

#### Scenario: Generation is active

- **GIVEN** assistant generation is active
- **WHEN** bulk session deletion would otherwise be available
- **THEN** the app does not clear sessions or messages until generation is no
  longer active.
