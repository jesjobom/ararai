## ADDED Requirements

### Requirement: Third-party license disclosure

The application SHALL provide a user-accessible open-source license disclosure
that distinguishes the ArarAI project license from third-party licenses.

#### Scenario: User opens dependency licenses

- **WHEN** the user selects Open-source licenses from Settings
- **THEN** the application shows the resolved Gradle libraries and their
  available attribution and license information
- **AND** the disclosure includes transitive runtime dependencies rather than
  only the dependencies declared directly by the application

#### Scenario: User inspects native and model notices

- **WHEN** the user opens the license disclosure
- **THEN** the application identifies the native whisper.cpp runtime and each
  downloadable model family outside the Gradle graph
- **AND** it provides the reviewed license identifier and upstream source or
  license link for each

#### Scenario: Dependency or catalog artifacts change

- **WHEN** a build dependency, native source revision, or model artifact is
  intentionally updated
- **THEN** the project provides a repeatable process to regenerate or review the
  affected license disclosure
- **AND** unknown or ambiguous license metadata remains visible to maintainers
  for explicit review
