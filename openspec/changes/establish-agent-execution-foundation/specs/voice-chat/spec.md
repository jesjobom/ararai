## ADDED Requirements

### Requirement: Automated Voice Chat controller characterization

The project SHALL automatically verify the core Voice Chat controller lifecycle
using deterministic local fakes without requiring a microphone, model artifact,
network provider, or text-to-speech engine.

#### Scenario: Prepare an eligible model

- **GIVEN** an available direct-audio model and deterministic local engine
- **WHEN** Voice Chat is entered
- **THEN** automated coverage verifies model loading and audio-workload preparation
- **AND** start remains disabled until preparation completes.

#### Scenario: Complete a direct-audio turn

- **GIVEN** prepared Voice Chat and a deterministic captured turn
- **WHEN** generation emits answer tokens and completes
- **THEN** automated coverage verifies app-owned audio persistence and shared conversation history
- **AND** verifies ordered speech queueing and return to listening.

#### Scenario: Stop or fail an active turn

- **GIVEN** Voice Chat has an active capture or generation
- **WHEN** the user stops the loop or generation fails
- **THEN** automated coverage verifies cancellation, controlled state, and temporary capture cleanup
- **AND** no real platform service is required.

