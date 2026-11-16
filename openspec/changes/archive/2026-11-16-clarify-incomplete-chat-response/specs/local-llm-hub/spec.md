## MODIFIED Requirements

### Requirement: Durable Incomplete Assistant Response

The app SHALL distinguish a terminal assistant generation that contains
reasoning but no usable final answer from a complete answer, cancellation, and
failure. When runtime token counts suggest that the configured total context
capacity was reached, it SHALL present this as a possibility rather than a
confirmed stop reason; otherwise it SHALL not attribute an unreported cause.
An assistant turn stopped by the user's cancellation SHALL retain a distinct
cancelled status, not an incomplete-generation status.
For a non-blank answer whose native cumulative token count is at or above the
configured context limit, the app SHALL preserve the answer and distinguish a
possible incomplete-output warning from a confirmed failure or cancellation.

#### Scenario: Generation ends after reasoning only

- **GIVEN** the runtime emits non-blank reasoning
- **AND** emits no usable final answer text
- **WHEN** the generation reaches its terminal completion callback
- **THEN** the assistant message is marked incomplete
- **AND** partial reasoning is preserved
- **AND** the turn is not presented as an ordinary successful ellipsis.

#### Scenario: Present an incomplete response

- **GIVEN** an incomplete assistant message exists
- **WHEN** normal Chat renders it
- **THEN** Chat shows an `Incomplete response` indication and controlled
  explanation
- **AND** shows the explanation after the response content, including any partial reasoning and sources
- **AND** shows partial reasoning only when `Show reasoning` is enabled
- **AND** does not guess, repair, or silently replace generated facts.

#### Scenario: Reconstruct legacy and incomplete messages

- **GIVEN** persisted history contains legacy assistant messages without a
  completion-status field and newer incomplete messages
- **WHEN** the conversation is reconstructed after process death
- **THEN** legacy messages default to complete
- **AND** incomplete or cancelled status, optional display reason, and partial reasoning remain available
- **AND** eligible completed history remains unchanged.

#### Scenario: Incomplete response near the configured context capacity

- **GIVEN** Chat has no final answer after terminal completion
- **AND** the runtime's cumulative native conversation token count is near the effective total context capacity
- **WHEN** Chat renders the incomplete assistant message
- **THEN** it explains that the context may have filled before a final answer
- **AND** suggests increasing the configured capacity, disabling reasoning, or starting a new conversation
- **AND** places that guidance at the end of the response where automatic scrolling leaves it visible
- **AND** restores that guidance when the incomplete conversation is reopened
- **AND** does not claim that LiteRT-LM reported a specific stop reason.

#### Scenario: Incomplete response without context-limit evidence

- **GIVEN** Chat has no final answer after terminal completion
- **AND** the cumulative native token count is missing or below the context capacity
- **AND** no tool completed during the turn
- **WHEN** Chat renders the incomplete assistant message
- **THEN** it says the runtime did not report why generation stopped
- **AND** it suggests retrying or starting a new chat rather than blaming the context limit.

#### Scenario: Non-blank output reaches the configured context limit

- **GIVEN** Chat receives non-blank final answer text
- **AND** the runtime reports a cumulative native conversation token count at or above the configured context limit at completion
- **WHEN** the assistant message is displayed or reopened
- **THEN** Chat retains and displays the generated answer
- **AND** shows a yellow notice after the answer explaining that it may be incomplete or unreliable
- **AND** suggests increasing context, disabling reasoning, or starting a new chat
- **AND** does not claim the runtime confirmed truncation
- **AND** the non-blank output remains eligible for user-initiated content reporting.

#### Scenario: Non-blank output below the configured context limit

- **GIVEN** Chat receives non-blank final answer text
- **AND** the native cumulative token count is unavailable or below the configured limit
- **WHEN** the assistant message is displayed
- **THEN** it remains a complete response without a capacity warning.

#### Scenario: A tool completed but the model did not answer

- **GIVEN** a tool completion event occurred in the turn
- **AND** Chat has no final answer after terminal completion
- **AND** the cumulative native token count does not indicate likely context exhaustion
- **WHEN** Chat renders the incomplete assistant message
- **THEN** it explains that a tool ran but the model did not provide a final answer
- **AND** it does not present tool output as a generated answer or infer why the model stopped.

#### Scenario: Existing incomplete message with tool sources

- **GIVEN** a persisted incomplete message predates reason storage but has attached tool sources
- **WHEN** Chat renders that message after an app update
- **THEN** it may explain that a tool ran without a final answer
- **AND** it does not rewrite the stored message or infer a context-limit cause.

#### Scenario: User cancels while the model is loading

- **GIVEN** Chat has persisted the user's turn and is loading the model
- **WHEN** the user cancels generation
- **THEN** the assistant turn is immediately marked cancelled and persisted
- **AND** Chat shows a cancellation notice at the end of the turn rather than an incomplete-response warning.

#### Scenario: User cancels during generation

- **GIVEN** Chat is generating an assistant turn with possible partial output
- **WHEN** the user cancels generation or leaves Chat
- **THEN** partial output is preserved and the assistant turn is marked cancelled immediately
- **AND** the cancelled status and notice remain after reopening the conversation
- **AND** no context-exhaustion warning is attributed to the cancelled turn.

#### Scenario: Historical turn without cancellation status

- **GIVEN** a previously saved assistant turn has no explicit cancellation status
- **WHEN** Chat reconstructs its history
- **THEN** Chat does not retroactively claim that the user cancelled it.
