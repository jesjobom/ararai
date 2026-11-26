## ADDED Requirements

### Requirement: Receive Android shares in Chat
ArarAI SHALL accept Android single-item sharing of plain text or an image, reuse the existing Chat session selector, and populate the normal composer only after the user chooses an existing session or creates a new one. It SHALL NOT submit a message automatically. Images SHALL use the existing bounded normalization and capability checks. Unreadable or unsupported payloads SHALL NOT enter the composer as attachments.

#### Scenario: Share text with an existing conversation
- **WHEN** the user shares plain text and selects an existing conversation
- **THEN** that conversation opens with the shared text editable in the composer and no new message in its history.

#### Scenario: Share an image with a new conversation
- **WHEN** the user shares a readable image, creates a conversation, and the selected model supports image input
- **THEN** the normalized image appears as a removable attachment without initiating generation.

#### Scenario: Cancel session selection
- **WHEN** the user closes the session selector without choosing a destination
- **THEN** the shared content is not inserted into the composer or history.
