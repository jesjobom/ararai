## ADDED Requirements

### Requirement: One-tap continuation after stage review

When a successfully completed widget-authoring stage is awaiting review, one explicit Avançar action in the authoring UI SHALL accept that stage and start the next stage attempt if one exists and the selected authoring configuration is available. The application SHALL continue to stop for review after each generated attempt; it SHALL NOT accept an artifact merely because generation finished.

#### Scenario: Continue to the next stage

- **WHEN** the user taps Avançar on a successful stage with a next stage and valid model configuration
- **THEN** the current artifact is durably accepted and the next attempt is submitted without a second tap
- **AND** that next attempt remains cancellable and subject to normal single-flight and device-state checks.

#### Scenario: No next stage or non-applied acceptance

- **WHEN** feasibility cannot proceed, final render is ready for preview, or the acceptance was not applied
- **THEN** no further model attempt is started.

#### Scenario: Configuration unavailable

- **WHEN** the user taps Avançar on a successful stage but the authoring model configuration is unavailable
- **THEN** the checkpoint is accepted and the next stage remains available to start later, without losing accepted work.
