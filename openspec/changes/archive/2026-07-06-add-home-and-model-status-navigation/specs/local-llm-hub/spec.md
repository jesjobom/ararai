# local-llm-hub Specification

## ADDED Requirements

### Requirement: Home Entry Point

The app SHALL start on a home screen that can grow into a feature hub.

#### Scenario: Launch home

- **WHEN** the app launches
- **THEN** the first visible screen is `Home`
- **AND** the screen exposes a single action to open model status
- **AND** the chat screen is not exposed by this change.

### Requirement: Model Status Screen

The app SHALL expose model download and availability details on a dedicated
model status screen.

#### Scenario: View model status

- **GIVEN** the user is on `Home`
- **WHEN** the user opens model status
- **THEN** the app shows the configured model name
- **AND** shows the current model availability or download state
- **AND** shows download progress when available
- **AND** allows retry only when the model download has failed.
