# local-llm-hub Specification

## ADDED Requirements

### Requirement: Android SDK 36 Target

The application SHALL target Android SDK 36 for the initial MVP.

#### Scenario: MVP build target

- **WHEN** the Android project scaffold is created
- **THEN** its target SDK is set to Android SDK 36
- **AND** compatibility with older Android versions is not treated as an MVP
  requirement.

### Requirement: Local-Only Inference

The application SHALL run LLM inference on the Android device for the MVP.

#### Scenario: Prompt execution

- **WHEN** the user submits a prompt
- **THEN** the selected local model handles inference on-device
- **AND** no remote inference API is called.

### Requirement: No External Backend For MVP

The MVP SHALL NOT require an external application backend, external database, or
hosted API to perform its core chat flow.

#### Scenario: Offline-capable core flow

- **GIVEN** a supported model is already available on the device
- **WHEN** the user opens the app and submits a text prompt
- **THEN** the app can produce a response without contacting an external backend.

### Requirement: Runtime Boundary

The application SHALL isolate local model execution behind an inference engine
boundary.

#### Scenario: Runtime replacement

- **WHEN** a future runtime is evaluated
- **THEN** the app can add another engine implementation without rewriting the
  chat UI or model-selection flow.

### Requirement: Physical Device Test Loop

The early test loop SHALL prioritize a physical Android device over the Android
emulator.

#### Scenario: Debug APK validation

- **WHEN** a debug APK is built in the OpenClaw container
- **THEN** it is transferred to an environment with ADB access
- **AND** installed on the physical device for validation.
