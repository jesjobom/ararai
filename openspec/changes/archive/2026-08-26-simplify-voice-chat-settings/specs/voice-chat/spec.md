## MODIFIED Requirements

### Requirement: Persistent product settings

Voice Chat SHALL locally persist validated product settings for reasoning,
pause duration, minimum response words, TTS speech rate, VAD mode,
speech-confirmation duration, pre-roll, minimum usable speech duration, capture
source, and requested noise suppression. Its settings dialog SHALL initially
show only reasoning enablement and TTS reading speed, and SHALL place every other
product control behind an advanced disclosure that is collapsed by default.

#### Scenario: Present compact Voice Chat settings

- **WHEN** the user opens Voice Chat settings
- **THEN** the reasoning toggle and TTS reading-speed control are visible
- **AND** reasoning remains gated by the selected model's declared capability
- **AND** an accessible `Advanced` disclosure is visible and collapsed
- **AND** pause duration, minimum response words, VAD provider, VAD sensitivity,
  speech-confirmation duration, pre-roll, minimum usable speech duration,
  capture source, noise suppression, and their supporting experimental text are
  not displayed.

#### Scenario: Expand advanced settings

- **GIVEN** Voice Chat settings are open with `Advanced` collapsed
- **WHEN** the user expands `Advanced`
- **THEN** every existing non-primary Voice Chat control is displayed
- **AND** the pause control is labelled `Pause before answer` and explains that
  it measures trailing silence before submission
- **AND** VAD provider, VAD sensitivity, and capture source use dropdown controls
- **AND** every advanced value retains its current persisted setting.

#### Scenario: Collapse advanced settings

- **GIVEN** the advanced section is expanded
- **WHEN** the user collapses it
- **THEN** the advanced controls are hidden
- **AND** no Voice Chat setting is reset or changed
- **AND** reasoning and reading speed remain visible.

#### Scenario: Reopen Voice Chat settings

- **GIVEN** the user previously expanded the advanced section
- **WHEN** the settings dialog is closed and later opened again
- **THEN** the advanced section starts collapsed
- **AND** all persisted primary and advanced values remain unchanged.

#### Scenario: Configure pause duration

- **WHEN** the user selects 500 through 5,000 milliseconds in 250-millisecond
  increments
- **THEN** the value is persisted locally
- **AND** applies from the next listening cycle
- **AND** defaults to 1,500 milliseconds when no valid value is stored.

#### Scenario: Configure minimum response words

- **WHEN** the user selects 1 through 100 words
- **THEN** the value is persisted locally
- **AND** applies from the next response
- **AND** defaults to 25 words when no valid value is stored.

#### Scenario: Configure TTS speech rate

- **WHEN** the user selects a speech-rate multiplier from 0.5x through 2.0x in
  0.1x increments
- **THEN** the value is persisted locally
- **AND** applies to subsequent Voice Chat speech segments
- **AND** defaults to 1.0x when no valid value is stored.

#### Scenario: Restore invalid settings

- **WHEN** a stored product setting is missing, corrupt, or outside its range
- **THEN** that setting uses its defined default
- **AND** Voice Chat remains usable.

### Requirement: Voice Chat settings persist immediately

The Voice Chat settings dialog SHALL persist every supported change immediately.
It SHALL provide a Close action that only dismisses the dialog and a Reset action
that restores and persists all Voice Chat defaults without dismissing the dialog.
Advanced-section visibility SHALL be transient presentation state and SHALL NOT
change product-setting persistence.

#### Scenario: Change a Voice Chat setting

- **WHEN** the user changes a supported primary or advanced Voice Chat setting
- **THEN** the new value is persisted without a separate save action.

#### Scenario: Reset Voice Chat settings

- **WHEN** the user activates Reset
- **THEN** all primary and advanced Voice Chat settings return to their defaults
  and are persisted
- **AND** the settings dialog remains open
- **AND** Reset does not expand or collapse the advanced section.

#### Scenario: Reset Voice Chat settings while advanced is collapsed

- **GIVEN** one or more advanced settings differ from their defaults
- **AND** the advanced section is collapsed
- **WHEN** the user activates Reset
- **THEN** all primary and advanced Voice Chat settings return to their defaults
  and are persisted
- **AND** the settings dialog remains open
- **AND** the advanced section remains collapsed.

#### Scenario: Reset Voice Chat settings while advanced is expanded

- **GIVEN** the advanced section is expanded
- **WHEN** the user activates Reset
- **THEN** all Voice Chat settings return to their defaults and are persisted
- **AND** the settings dialog remains open
- **AND** the advanced section remains expanded with default values displayed.

#### Scenario: Close Voice Chat settings

- **WHEN** the user activates Close
- **THEN** the dialog closes without changing the current settings.
