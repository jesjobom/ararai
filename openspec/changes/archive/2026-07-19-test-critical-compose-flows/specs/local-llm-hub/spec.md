## ADDED Requirements

### Requirement: Automated critical Compose journey coverage

The project SHALL automatically verify critical user journeys across Home,
Chat, Models, and Settings using deterministic local fakes and stable UI semantics.

#### Scenario: Navigate to Chat and submit

- **GIVEN** a deterministic available local model
- **WHEN** the test navigates from Home to Chat and submits a prompt
- **THEN** it observes generation controls and the streamed result through UI semantics.

#### Scenario: Recover from unavailable model state

- **GIVEN** the selected model reports a retryable failure
- **WHEN** the test opens model management and selects Retry
- **THEN** it verifies the retry command and corresponding UI transition.

#### Scenario: Manage sessions and appearance

- **WHEN** tests rename or delete a Chat session and change appearance in Settings
- **THEN** they verify the resulting visible state without relying on pixel coordinates.
