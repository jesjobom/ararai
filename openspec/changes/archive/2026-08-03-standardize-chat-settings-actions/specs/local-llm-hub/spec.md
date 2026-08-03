## ADDED Requirements

### Requirement: Chat settings persist immediately

The Chat settings dialog SHALL apply every supported change immediately. It SHALL
provide a Close action that only dismisses the dialog and a Reset action that
restores and applies all Chat defaults without dismissing the dialog.

#### Scenario: Change a Chat setting

- **WHEN** the user changes a supported Chat setting
- **THEN** the new value is applied without a separate save action

#### Scenario: Reset Chat settings

- **WHEN** the user activates Reset
- **THEN** all Chat settings return to their defaults immediately
- **AND** the settings dialog remains open

#### Scenario: Close Chat settings

- **WHEN** the user activates Close
- **THEN** the dialog closes without changing the current settings
