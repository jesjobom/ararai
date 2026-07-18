## ADDED Requirements

### Requirement: Selectable Chat message text

The Chat SHALL expose message text through Android's native text-selection
interaction.

#### Scenario: Select part of a historical message

- **WHEN** the user long-presses selectable text in a user or assistant message
- **THEN** Android displays native text-selection handles and contextual actions
- **AND** the user can adjust the selection to part of the message

#### Scenario: Copy selected message text

- **WHEN** the user invokes the platform Copy action for selected message text
- **THEN** Android places the selected plain text on the clipboard

#### Scenario: Select formatted and reasoning text

- **WHEN** a message presents Markdown blocks or visible reasoning text
- **THEN** its textual content participates in native selection
- **AND** reasoning, attachments, and final text retain their vertical order without overlap

#### Scenario: Select text on a colored message background

- **WHEN** the user selects text inside a colored message bubble
- **THEN** the selection handles and highlight contrast with that bubble

#### Scenario: Render mathematics inside reasoning

- **WHEN** visible reasoning contains a rendered mathematical formula
- **THEN** the formula uses the reasoning container's content color
- **AND** remains legible against the reasoning background

#### Scenario: Use a message action

- **WHEN** a message also presents an action such as text-to-speech playback
- **THEN** the action remains independently operable outside the selection boundary
