# local-llm-hub Specification

## Purpose
TBD - created by archiving change define-project-foundation. Update Purpose after archive.
## Requirements
### Requirement: Android SDK 36 Target

The application SHALL target Android SDK 36 for the initial MVP.

#### Scenario: MVP build target

- **WHEN** the Android project scaffold is created
- **THEN** its target SDK is set to Android SDK 36
- **AND** compatibility with older Android versions is not treated as an MVP
  requirement.

### Requirement: Android Application Identity

The Android namespace and application ID SHALL start as `com.jesjobom.ararai`.

#### Scenario: Android project scaffold identity

- **WHEN** the Android project scaffold is created
- **THEN** the application namespace is `com.jesjobom.ararai`
- **AND** the application ID is `com.jesjobom.ararai`.

### Requirement: Initial Kotlin And Compose Versions

The project SHALL start with Kotlin 2.3.21 and Jetpack Compose BOM 2026.06.00.

#### Scenario: Compose scaffold dependencies

- **WHEN** the Android project scaffold is created
- **THEN** the Kotlin Android plugin version is 2.3.21
- **AND** the Compose Compiler Gradle plugin version is 2.3.21
- **AND** Compose libraries are aligned through BOM 2026.06.00.

### Requirement: Local-Only Inference

The application SHALL run LLM inference on the Android device for the MVP.

#### Scenario: Prompt execution

- **WHEN** the user submits a prompt
- **THEN** the configured local model handles inference on-device
- **AND** no remote inference API is called.

### Requirement: Configured Model Startup Resolution

The first model source SHALL be one fixed GGUF model declared in checked-in
application configuration.

#### Scenario: Load existing configured model

- **GIVEN** the configured GGUF model exists at the configured app-owned path
- **AND** the file passes the configured integrity check
- **WHEN** the app starts
- **THEN** the app can pass that file to the local inference engine
- **AND** the MVP does not expose model selection.

#### Scenario: Download missing configured model

- **GIVEN** the configured GGUF model is missing or fails integrity validation
- **WHEN** the app starts with network access
- **THEN** the app automatically downloads the configured model to the standard
  app-owned location
- **AND** validates the downloaded file before loading it
- **AND** does not require the user to choose a model.

### Requirement: No External Backend For MVP

The MVP SHALL NOT require an external application backend, external database, or
hosted API to perform its core chat flow.

#### Scenario: Offline-capable core flow

- **GIVEN** the configured model is already available on the device
- **WHEN** the user opens the app and submits a text prompt
- **THEN** the app can produce a response without contacting an external backend.

### Requirement: Runtime Boundary

The application SHALL isolate local model execution behind an inference engine
boundary.

#### Scenario: Runtime replacement

- **WHEN** a future runtime is evaluated
- **THEN** the app can add another engine implementation without rewriting the
  chat UI or configured-model resolution flow.

### Requirement: First Vertical Slice

The first implementation slice SHALL be a single-screen debug chat flow backed
by the local inference engine boundary.

#### Scenario: First prompt loop

- **GIVEN** the configured GGUF model is available at the standard location
- **WHEN** the user submits one text prompt
- **THEN** the app loads the model through the local inference engine boundary
- **AND** streams generated text back into the chat UI
- **AND** surfaces loading or generation failures in the UI.

### Requirement: Physical Device Test Loop

The early test loop SHALL prioritize a physical Android device over the Android
emulator.

#### Scenario: Debug APK validation

- **WHEN** a debug APK is built in the OpenClaw container
- **THEN** it is copied to
  `/home/node/.openclaw/jarvis/artifacts/ararai/app-debug.apk`
- **AND** the copied APK is transferred to an environment with ADB access
- **AND** installed on the physical device for validation.

