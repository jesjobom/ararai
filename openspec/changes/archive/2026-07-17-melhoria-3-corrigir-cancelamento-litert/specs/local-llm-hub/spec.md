## ADDED Requirements

### Requirement: Deterministic LiteRT-LM Conversation Disposal

The LiteRT-LM runtime SHALL close every invalidated native conversation exactly
once and SHALL not retain a reusable reference after cancellation or failure.

#### Scenario: Cancel active LiteRT-LM generation

- **GIVEN** a LiteRT-LM conversation is actively generating
- **WHEN** generation is cancelled
- **THEN** processing is cancelled and the conversation is closed
- **AND** active and retained references to that conversation are cleared.

#### Scenario: Generate after cancellation

- **GIVEN** a previous LiteRT-LM conversation was cancelled
- **WHEN** a later compatible Chat request starts
- **THEN** the runtime creates a new conversation
- **AND** does not reuse the cancelled native state.

#### Scenario: Unload after cancellation

- **GIVEN** cancellation has already disposed the active conversation
- **WHEN** the engine unloads
- **THEN** unload completes without double-closing the conversation
- **AND** all LiteRT-LM engine resources are released.
