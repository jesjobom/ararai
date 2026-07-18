## ADDED Requirements

### Requirement: Portrait-only application orientation

The app SHALL present its current phone experience in portrait orientation and
SHALL NOT switch the launcher activity to landscape when the device rotates.

#### Scenario: Rotate the device while using ArarAI

- **GIVEN** ArarAI is visible in portrait orientation
- **WHEN** the user rotates the device to a landscape position
- **THEN** the application remains in portrait orientation.
