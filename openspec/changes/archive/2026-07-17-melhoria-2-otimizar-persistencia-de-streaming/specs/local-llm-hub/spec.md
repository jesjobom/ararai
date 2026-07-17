## ADDED Requirements

### Requirement: Batched Streamed Response Persistence

The app SHALL present streamed assistant output immediately while persisting
that output at a bounded cadence rather than writing once per generated delta.

#### Scenario: Render frequent generation deltas

- **GIVEN** local inference emits multiple assistant text deltas in rapid succession
- **WHEN** Chat processes those deltas
- **THEN** the visible message updates as deltas arrive
- **AND** durable storage updates are batched according to the documented cadence.

#### Scenario: Flush a completed response

- **GIVEN** assistant content is waiting to be persisted
- **WHEN** generation completes successfully
- **THEN** the complete visible assistant content is persisted before completion handling finishes.

#### Scenario: Preserve an interrupted partial response

- **GIVEN** assistant content has been streamed but not fully persisted
- **WHEN** generation is cancelled, fails, or Chat is left
- **THEN** the latest partial content is flushed through the controlled termination path
- **AND** reopening the session does not silently lose already-visible output.
