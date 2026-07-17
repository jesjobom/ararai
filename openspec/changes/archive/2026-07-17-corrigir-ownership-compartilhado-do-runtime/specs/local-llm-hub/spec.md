## ADDED Requirements

### Requirement: Application-scoped local inference ownership

The application SHALL maintain at most one configured native local inference
engine tree for foreground Chat and Benchmark features.

#### Scenario: Open Benchmark after using Chat

- **GIVEN** Chat has loaded or retained the selected local model
- **WHEN** the user leaves Chat and starts Benchmark
- **THEN** Benchmark uses the same application-scoped configured engine
- **AND** the application does not construct or load a second native runtime
  tree for Benchmark.

#### Scenario: Return to Chat after Benchmark

- **GIVEN** Benchmark has completed, failed, or been canceled
- **AND** Benchmark has unloaded the shared runtime
- **WHEN** the user next submits a Chat prompt
- **THEN** Chat reloads the selected model through the shared engine
- **AND** generation proceeds without requiring an application restart.

