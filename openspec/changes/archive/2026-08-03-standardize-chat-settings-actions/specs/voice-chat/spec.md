## ADDED Requirements

### Requirement: Voice Chat settings persist immediately

The Voice Chat settings dialog SHALL persist every supported change immediately.
It SHALL provide a Close action that only dismisses the dialog and a Reset action
that restores and persists all Voice Chat defaults without dismissing the dialog.

#### Scenario: Change a Voice Chat setting

- **WHEN** the user changes a supported Voice Chat setting
- **THEN** the new value is persisted without a separate save action

#### Scenario: Reset Voice Chat settings

- **WHEN** the user activates Reset
- **THEN** all Voice Chat settings return to their defaults and are persisted
- **AND** the settings dialog remains open

#### Scenario: Close Voice Chat settings

- **WHEN** the user activates Close
- **THEN** the dialog closes without changing the current settings
