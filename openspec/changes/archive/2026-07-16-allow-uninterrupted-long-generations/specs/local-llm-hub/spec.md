## ADDED Requirements

### Requirement: User-controlled scrolling during streamed responses

Chat SHALL keep the end of a growing response visible while automatic
following is enabled. Chat SHALL stop automatic following when the user drags
the message history and SHALL NOT force the user back to the generated text
while they inspect another position. Automatic following SHALL become enabled
again when the user returns to the bottom.

#### Scenario: User inspects earlier text during generation

- **WHEN** an assistant response is streaming and the user drags the message
  history away from the bottom
- **THEN** subsequent streamed content does not change the user's scroll
  position

#### Scenario: User returns to the latest content

- **WHEN** the user scrolls back to the bottom during generation
- **THEN** Chat follows the actual end of subsequent streamed content

### Requirement: Long generation has no app-level time or chunk limit

Chat SHALL NOT cancel a generation because of elapsed time or the number of
streaming callbacks. Generation MAY end when the runtime completes or fails,
the configured runtime output limit is reached, the user selects `Cancel
Generation`, or an applicable app lifecycle event cancels active work.

#### Scenario: LiteRT-LM emits many callback chunks

- **WHEN** LiteRT-LM emits more callback chunks than the configured maximum
  output-token value
- **THEN** the app continues consuming the stream without calling runtime
  cancellation solely because of the callback count
