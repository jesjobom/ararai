## ADDED Requirements

### Requirement: CI Android SDK bootstrap uses available packages

The Android quality workflow SHALL configure its SDK setup action to request
only supported Android SDK package identifiers before installing the pinned
toolchain components required by the quality gate.

#### Scenario: SDK setup runs on a current GitHub runner

- **WHEN** the Android quality workflow starts on a pull request or `main` push
- **THEN** SDK setup does not request the retired `tools` package
- **AND** the workflow can proceed to install the pinned SDK, Build Tools, NDK,
  and CMake versions.
