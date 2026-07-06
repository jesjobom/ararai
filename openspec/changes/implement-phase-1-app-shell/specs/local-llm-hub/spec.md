# local-llm-hub Specification

## ADDED Requirements

### Requirement: Phase 1 Android Scaffold

Phase 1 SHALL create a buildable Android application scaffold for ArarAI.

#### Scenario: Buildable debug app

- **WHEN** the phase 1 scaffold is complete
- **THEN** the repository can build a debug APK for application ID
  `com.jesjobom.ararai`
- **AND** the app uses the approved Android SDK, Kotlin, Compose, Gradle, NDK,
  and CMake baseline.

### Requirement: Fixed Model Configuration

Phase 1 SHALL include checked-in configuration for exactly one GGUF model.

#### Scenario: Parse configured model

- **WHEN** the app starts
- **THEN** it can parse a single configured model entry
- **AND** the entry defines the model ID, source URL, expected local path,
  integrity metadata, and default inference parameters
- **AND** no model picker or user-facing model choice is exposed.

### Requirement: Model Resolution State

Phase 1 SHALL resolve the configured model through a testable model-resolution
boundary before chat generation is enabled.

#### Scenario: Existing valid model

- **GIVEN** the configured model exists at the standard app-owned path
- **AND** it passes integrity validation
- **WHEN** model resolution runs
- **THEN** the app reports the model as available for the inference engine.

#### Scenario: Missing or invalid model

- **GIVEN** the configured model is missing or fails integrity validation
- **WHEN** model resolution runs
- **THEN** the app reports that download is needed or in progress
- **AND** chat submission remains disabled until the model becomes available.

### Requirement: Debug Chat Shell

Phase 1 SHALL provide a single-screen Compose debug chat shell backed by the
local inference engine boundary.

#### Scenario: Fake streamed response

- **GIVEN** the configured model is available
- **AND** the fake local inference engine is active
- **WHEN** the user submits a prompt
- **THEN** the UI streams deterministic response text into the chat view
- **AND** completion and error events are visible in UI state.

### Requirement: Debug APK Handoff

Phase 1 SHALL provide a repeatable way to hand off the debug APK from the
OpenClaw container to the external ADB environment.

#### Scenario: Copy debug APK artifact

- **WHEN** a debug APK build succeeds
- **THEN** the APK can be copied to
  `/home/node/.openclaw/jarvis/artifacts/ararai/app-debug.apk`
- **AND** the copy target remains outside Git-tracked project files.
