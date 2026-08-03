## ADDED Requirements

### Requirement: Bounded conversation history presentation

Chat SHALL preserve every canonical conversation message while initially loading
only a bounded recent window for presentation. The user SHALL be able to request
older messages in bounded increments without changing their content or order.

#### Scenario: Open a long conversation

- **GIVEN** a conversation contains more messages than the initial display window
- **WHEN** the user opens that conversation
- **THEN** Chat loads and displays the most recent bounded window in chronological order
- **AND** indicates that older messages are available
- **AND** does not delete or rewrite messages outside the window.

#### Scenario: Load older conversation history

- **GIVEN** older messages exist before the displayed window
- **WHEN** the user requests older messages
- **THEN** Chat prepends the next bounded page in canonical chronological order
- **AND** retains the currently displayed messages without duplication
- **AND** eventually reports that no older messages remain.

### Requirement: Responsive conversation persistence

Interactive conversation reads and writes that can touch SQLite or decode stored
payloads SHALL execute away from the Android main thread.

#### Scenario: Submit or switch a conversation

- **WHEN** the user submits a turn or switches, renames, deletes, or clears a conversation
- **THEN** database work executes on the configured persistence dispatcher
- **AND** resulting UI state is published after the operation completes
- **AND** repeated input cannot create duplicate operations while one is active.

### Requirement: Efficient conversation media lookup

Conversation deletion SHALL identify the session's media references without
loading and decoding every message payload.

#### Scenario: Delete a conversation with media

- **WHEN** a conversation containing app-owned media is deleted
- **THEN** the store queries its distinct media references directly
- **AND** cleanup deletes only files no longer referenced by another conversation.

