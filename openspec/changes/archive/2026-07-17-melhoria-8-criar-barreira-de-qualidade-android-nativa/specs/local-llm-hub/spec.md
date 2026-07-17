## ADDED Requirements

### Requirement: Layered Android and Native Verification

The project SHALL provide repeatable verification across JVM logic, Android
integration, native runtime boundaries, and documented physical-device checks.

#### Scenario: Validate a proposed source change

- **GIVEN** a change is submitted to the repository
- **WHEN** the automated quality gate runs
- **THEN** JVM unit tests, Android lint, strict OpenSpec validation, and debug assembly execute
- **AND** failures prevent the change from being considered verified.

#### Scenario: Validate Android-specific behavior

- **GIVEN** behavior depends on permissions, content providers, lifecycle, or Android data configuration
- **WHEN** the instrumentation suite runs on a supported target
- **THEN** focused automated checks exercise those boundaries.

#### Scenario: Validate physical-device inference

- **GIVEN** runtime behavior depends on GPU, native libraries, memory, or thermal characteristics
- **WHEN** a release candidate is evaluated on the target physical device
- **THEN** the versioned device matrix is executed
- **AND** results identify the app version, device, model, runtime, and any skipped check without recording private prompts.
