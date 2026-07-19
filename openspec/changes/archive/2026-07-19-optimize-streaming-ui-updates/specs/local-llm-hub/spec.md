## ADDED Requirements

### Requirement: Bounded Chat streaming presentation

The app SHALL preserve generated token order while bounding how frequently
growing assistant content rebuilds structural UI state and expensive presentation
work. Terminal events SHALL flush all buffered content immediately.

#### Scenario: Receive a burst of tokens

- **WHEN** the local engine emits multiple tokens within one presentation interval
- **THEN** the app coalesces them into fewer display-state updates
- **AND** preserves their exact order and content.

#### Scenario: Complete or cancel between presentation updates

- **WHEN** generation completes, fails, or is cancelled while display content is buffered
- **THEN** the app flushes the latest content before publishing the terminal state
- **AND** persists the same final partial or complete response.

#### Scenario: Render unchanged streamed content

- **WHEN** unrelated Chat state changes without changing displayed assistant text
- **THEN** the app avoids reparsing that Markdown solely because of the unrelated state change.
