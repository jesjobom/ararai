## ADDED Requirements

### Requirement: Android builds use a reproducible Java runtime

The repository SHALL declare one supported Java runtime for the canonical
Android Gradle quality gate and SHALL keep workflow configuration, workflow
labels, local prerequisites, and project documentation aligned with that choice.

#### Scenario: CI runs the Android quality gate

- **WHEN** the canonical Android workflow configures Java and invokes Gradle
- **THEN** it uses the same Java runtime declared for local Android builds

#### Scenario: Firebase tooling needs another runtime

- **WHEN** Firebase emulator tooling requires a different Java version
- **THEN** that invocation is isolated and documented without changing the Android Gradle baseline implicitly
