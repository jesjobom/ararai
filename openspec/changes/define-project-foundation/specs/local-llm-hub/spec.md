# local-llm-hub Specification

## ADDED Requirements

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
- **THEN** the selected local model handles inference on-device
- **AND** no remote inference API is called.

### Requirement: Local Model File Selection

The first model source SHALL be a user-selected local GGUF file.

#### Scenario: Select local model

- **GIVEN** a GGUF model file exists on the Android device
- **WHEN** the user selects the file through Android's file picker
- **THEN** the app can pass the selected file to the local inference engine
- **AND** the MVP does not require bundled models, remote downloads, or a
  Hugging Face token.

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

### Requirement: First Vertical Slice

The first implementation slice SHALL be a single-screen debug chat flow backed
by the local inference engine boundary.

#### Scenario: First prompt loop

- **GIVEN** the user has selected a local GGUF model
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
