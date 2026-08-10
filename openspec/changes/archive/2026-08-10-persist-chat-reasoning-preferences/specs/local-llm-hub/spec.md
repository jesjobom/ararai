## ADDED Requirements

### Requirement: Persist Normal Chat Reasoning Choices

Normal Chat SHALL persist the user's independent `Use reasoning` and
`Show reasoning` choices locally and restore their effective values whenever
the selected model supports the corresponding reasoning capability.

#### Scenario: Restore reasoning choices after application recreation

- **GIVEN** the user enabled `Use reasoning` and `Show reasoning` in normal Chat
- **AND** the selected model supports reasoning request and output
- **WHEN** the application and Chat controller are recreated
- **THEN** both choices are restored as enabled
- **AND** subsequent generation uses the restored reasoning-request choice
- **AND** message rendering uses the restored reasoning-visibility choice.

#### Scenario: Preserve choices across an unsupported model

- **GIVEN** one or both reasoning choices are stored as enabled
- **WHEN** the selected model does not support the corresponding capability
- **THEN** the unsupported effective control is disabled and off
- **AND** the stored choice is retained
- **AND** selecting a supporting model restores the stored choice.

#### Scenario: Default reasoning choices for an existing installation

- **GIVEN** no normal Chat reasoning choices have previously been stored
- **WHEN** the preference store is initialized
- **THEN** `Use reasoning` defaults to disabled
- **AND** `Show reasoning` defaults to disabled.
