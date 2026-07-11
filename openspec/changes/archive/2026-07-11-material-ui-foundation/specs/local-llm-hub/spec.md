## ADDED Requirements

### Requirement: Material App Foundation

The app SHALL use a consistent Material 3 foundation for primary screens,
including a shared theme, top-level navigation treatment, and predictable
spacing/action hierarchy.

#### Scenario: Daily-use screen structure

- **WHEN** the user opens Home, Chat, Models, or Diagnostics
- **THEN** each screen uses the shared Material app theme
- **AND** screen titles, back navigation, content spacing, buttons, progress,
  and error states follow a consistent Material 3 treatment.

### Requirement: Chat-Centered Home

Home SHALL present Chat as the primary daily-use action while keeping model
management visible and diagnostics secondary.

#### Scenario: Home action hierarchy

- **WHEN** the user opens the app Home screen
- **THEN** the primary action opens Chat
- **AND** model management is available from Home
- **AND** benchmark access is presented as a secondary diagnostic action, not as
  a model-comparison or benchmark-history workflow.

### Requirement: Benchmark Diagnostic Scope

Benchmark UI SHALL remain an on-demand diagnostics surface and SHALL NOT add
benchmark history or model-comparison workflows.

#### Scenario: Diagnostic benchmark only

- **WHEN** the user opens the benchmark screen
- **THEN** the UI presents the selected model's current diagnostic run controls
- **AND** it does not present benchmark history
- **AND** it does not compare multiple models.
