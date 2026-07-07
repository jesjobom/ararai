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

### Requirement: Configured Model Download

The app SHALL automatically download the single configured GGUF model when the
configured final model file is missing or invalid.

#### Scenario: Download missing model

- **GIVEN** the configured model file is missing from the app-owned model path
- **WHEN** the app starts with network access
- **THEN** it starts downloading the configured model source
- **AND** reports a downloading state in the debug UI
- **AND** keeps chat submission disabled until validation succeeds.

#### Scenario: Retry failed model download

- **GIVEN** a configured model download fails
- **WHEN** the failure is shown in the debug UI
- **THEN** the user can retry the same configured download
- **AND** the retry does not require selecting a model.

### Requirement: Safe Model File Promotion

The app SHALL keep the final configured model path reserved for validated model
files only.

#### Scenario: Promote validated temporary file

- **GIVEN** the configured model download completed into a sibling temporary
  file
- **AND** the temporary file matches the configured byte size and SHA-256
- **WHEN** validation succeeds
- **THEN** the app atomically renames the temporary file to the configured final
  model path
- **AND** reruns model resolution so the model becomes available.

#### Scenario: Reject invalid temporary file

- **GIVEN** the configured model download completed into a sibling temporary
  file
- **AND** the temporary file fails byte-size or SHA-256 validation
- **WHEN** validation fails
- **THEN** the app does not promote the temporary file to the final model path
- **AND** reports a failed state that can be retried.

### Requirement: Model Download Progress

The app SHALL surface progress while downloading the configured model.

#### Scenario: Known-size progress

- **GIVEN** the configured model has an expected byte size
- **WHEN** the model download is in progress
- **THEN** the app reports downloaded bytes and total bytes in startup state
- **AND** the debug UI renders progress derived from those values.

#### Scenario: Retry disabled during progress

- **GIVEN** the model download is in progress
- **WHEN** the debug UI renders the downloading state
- **THEN** retry is not shown as an available action.

### Requirement: Home Entry Point

The app SHALL start on a home screen that can grow into a feature hub.

#### Scenario: Launch home

- **WHEN** the app launches
- **THEN** the first visible screen is `Home`
- **AND** the screen exposes an action to open model status
- **AND** the screen exposes an action to open chat.

### Requirement: Model Status Screen

The app SHALL expose model download and availability details on a dedicated
model status screen.

#### Scenario: View model status

- **GIVEN** the user is on `Home`
- **WHEN** the user opens model status
- **THEN** the app shows the configured model name
- **AND** shows the current model availability or download state
- **AND** shows download progress when available
- **AND** allows retry only when the model download has failed.

### Requirement: Stub Chat Entry Point

The app SHALL expose a dedicated chat screen backed by the fake local inference
engine until native local inference is integrated.

#### Scenario: Open chat from home

- **GIVEN** the user is on `Home`
- **WHEN** the user opens chat
- **THEN** the app shows the chat screen
- **AND** the chat screen shows the current model availability state
- **AND** the chat screen uses the fake/stub `LocalLlmEngine`.

#### Scenario: Return from chat

- **GIVEN** the user is on the chat screen
- **WHEN** the user taps the back action
- **THEN** the app returns to `Home`.

### Requirement: Stub Chat Conversation Flow

The chat screen SHALL support a basic text conversation flow suitable for
validating the user experience before native inference exists.

#### Scenario: Send prompt with available model

- **GIVEN** the configured model is available
- **AND** the user typed a non-blank prompt
- **WHEN** the user sends the prompt
- **THEN** the app appends the user message to the conversation
- **AND** clears the prompt input
- **AND** shows that generation is in progress
- **AND** appends deterministic fake assistant output from the stub engine
- **AND** re-enables sending after generation completes.

#### Scenario: Block send while unavailable or busy

- **GIVEN** the prompt is blank, the configured model is unavailable, or
  generation is already in progress
- **WHEN** the chat screen renders
- **THEN** the send action is disabled.

#### Scenario: Surface generation failure

- **GIVEN** the fake/stub engine reports a generation failure
- **WHEN** generation fails
- **THEN** the chat screen shows an error state
- **AND** preserves the conversation messages already shown
- **AND** allows the user to edit the prompt and try again when sending is
  otherwise allowed.

