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

The app SHALL support a configured GGUF model catalog. The existing single-model
configuration format SHALL remain valid and SHALL be interpreted as a catalog
with one default model.

#### Scenario: Load existing selected configured model

- **GIVEN** the selected configured GGUF model exists at its configured
  app-owned path
- **AND** the file passes the configured integrity check
- **WHEN** model resolution runs
- **THEN** the app can pass that file to the local inference engine
- **AND** the model list reports that model as available.

#### Scenario: Download missing selected configured model

- **GIVEN** the selected configured GGUF model is missing or fails integrity
  validation
- **AND** no other configured model is available locally
- **WHEN** the app starts with network access
- **THEN** the app automatically downloads the configured default model to its
  app-owned location
- **AND** validates the downloaded file before loading it.

#### Scenario: Skip default download when another model is available

- **GIVEN** the configured default model is missing
- **AND** another configured model is already available locally
- **WHEN** the app starts
- **THEN** the app does not automatically download the default model
- **AND** the available model is selected for chat.

### Requirement: No External Backend For MVP

The MVP SHALL NOT require an external application backend, external database, or
hosted API to perform its core chat flow.

#### Scenario: Offline-capable core flow

- **GIVEN** the configured model is already available on the device
- **WHEN** the user opens the app and submits a text prompt
- **THEN** the app can produce a response without contacting an external backend.

### Requirement: Runtime Boundary

The application SHALL isolate local model execution behind an inference engine
boundary while allowing the app runtime to use a real native local inference
implementation.

#### Scenario: Runtime replacement

- **WHEN** a future runtime is evaluated
- **THEN** the app can add another engine implementation without rewriting the
  chat UI or configured-model resolution flow.

#### Scenario: Real runtime behind boundary

- **GIVEN** the configured GGUF model is available at the standard app-owned
  path
- **WHEN** the chat flow requests generation
- **THEN** the app uses a real `LocalLlmEngine` implementation behind the engine
  boundary
- **AND** the chat UI does not depend directly on JNI, native handles, or
  runtime-specific types.

### Requirement: First Vertical Slice

The first implementation slice SHALL support a single-screen debug chat flow
backed by the local inference engine boundary and a real local model when the
configured GGUF file is already available.

#### Scenario: First prompt loop

- **GIVEN** the configured GGUF model is available at the standard location
- **WHEN** the user submits one text prompt
- **THEN** the app loads the model through the local inference engine boundary
- **AND** streams generated text back into the chat UI
- **AND** surfaces loading or generation failures in the UI.

#### Scenario: Existing model only

- **GIVEN** the configured GGUF model is missing, invalid, or still downloading
- **WHEN** the user opens chat
- **THEN** the app keeps prompt submission disabled
- **AND** reports the current model state
- **AND** does not ask the user to select another model.

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

Phase 1 SHALL include checked-in configuration for at least one GGUF model and
its default inference limits.

#### Scenario: Parse configured model catalog

- **WHEN** the app starts
- **THEN** it can parse a configured model catalog
- **AND** each entry defines the model ID, source URL, expected local path,
  integrity metadata, and default inference parameters
- **AND** the default inference parameters include context size, sampling
  values, and maximum generated tokens.

#### Scenario: Keep configured model list static

- **WHEN** the user opens the model management screen
- **THEN** the app shows only models declared by checked-in configuration
- **AND** the UI does not allow arbitrary model entries to be added.

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
model management screen.

#### Scenario: View configured models

- **GIVEN** the user is on `Home`
- **WHEN** the user opens models
- **THEN** the app shows the configured model list
- **AND** each model shows its availability or download state
- **AND** downloading models show progress when available.

#### Scenario: Manage model file

- **GIVEN** the user is viewing the model list
- **WHEN** a configured model is missing, failed, or available
- **THEN** the app offers the applicable action to download, retry, update by
  redownloading, or delete the local model file.

#### Scenario: Select active model

- **GIVEN** the user is viewing the model list
- **WHEN** the user selects a configured model
- **THEN** the selected model becomes the active model state used by chat
- **AND** missing or invalid selected models start the configured download flow.

### Requirement: Stub Chat Entry Point

The app SHALL keep the dedicated chat screen reachable from home while replacing
the app runtime chat engine with real local inference once native integration is
available.

#### Scenario: Open chat from home

- **GIVEN** the user is on `Home`
- **WHEN** the user opens chat
- **THEN** the app shows the chat screen
- **AND** the chat screen shows the current model availability state.

#### Scenario: Return from chat

- **GIVEN** the user is on the chat screen
- **WHEN** the user taps the back action
- **THEN** the app returns to `Home`.

#### Scenario: Keep fake engine for tests

- **WHEN** JVM tests need deterministic chat generation behavior
- **THEN** the fake/stub `LocalLlmEngine` remains available for test wiring
- **AND** production app wiring can use the real engine without changing chat UI
  code.

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

### Requirement: Real Local LLM Runtime

The app SHALL provide a real local inference engine for the configured GGUF
model that is already present and valid on the device.

#### Scenario: Load available configured model

- **GIVEN** model startup reports the configured model as available
- **AND** the configured model file exists at the app-owned path
- **WHEN** chat starts real generation
- **THEN** the app loads that exact file through the real local inference engine
- **AND** applies the configured inference defaults
- **AND** does not use a remote inference API.

#### Scenario: Native load failure

- **GIVEN** the configured model is reported available
- **AND** the native runtime fails to load it
- **WHEN** the user attempts generation
- **THEN** the chat screen shows a load error
- **AND** prompt submission becomes available again when the app is otherwise
  ready
- **AND** the app does not crash.

### Requirement: Real Chat Generation Flow

The chat screen SHALL use the real local engine to generate assistant text while
preserving the existing conversation behavior and configured inference limits.

#### Scenario: Stream real assistant output

- **GIVEN** the configured model is available
- **AND** the real local engine is loaded or can be loaded
- **AND** the user typed a non-blank prompt
- **WHEN** the user sends the prompt
- **THEN** the app appends the user message to the conversation
- **AND** creates an assistant message for streamed output
- **AND** formats the prompt using the loaded model's chat template when
  available
- **AND** limits generated output using the configured maximum generated tokens
- **AND** appends generated token text as it arrives
- **AND** re-enables sending after generation completes.

#### Scenario: Block concurrent generation

- **GIVEN** model loading or generation is already in progress
- **WHEN** the chat screen renders
- **THEN** the send action is disabled
- **AND** no second generation request starts.

#### Scenario: Surface real generation failure

- **GIVEN** the real local engine reports a generation failure
- **WHEN** generation fails
- **THEN** the chat screen shows an error state
- **AND** preserves the conversation messages already shown
- **AND** allows the user to edit the prompt and try again when sending is
  otherwise allowed.

### Requirement: Native Runtime Lifecycle

The app SHALL manage native inference resources so chat navigation and
cancellation do not leak active model work.

#### Scenario: Leave chat during generation

- **GIVEN** real generation is in progress
- **WHEN** the user leaves the chat screen
- **THEN** the app cancels active generation
- **AND** releases or schedules release of native inference resources
- **AND** does not append further tokens to the hidden chat screen.

#### Scenario: Unload loaded model

- **GIVEN** a real model context is loaded
- **WHEN** the chat lifecycle ends or the configured model becomes unavailable
- **THEN** the app calls the engine unload path
- **AND** releases native resources associated with the loaded model.

### Requirement: Native Runtime Validation

The real LLM integration SHALL be validated with automated boundary tests and a
physical-device smoke test.

#### Scenario: Automated boundary validation

- **WHEN** the implementation is complete
- **THEN** JVM tests cover chat state transitions for loading, generation,
  completion, failure, and cancellation
- **AND** fake-engine tests continue to pass without native libraries.

#### Scenario: Physical device smoke test

- **GIVEN** a debug APK is installed on a physical Android device
- **AND** the configured GGUF file is already present and valid
- **WHEN** the user opens chat and sends a short prompt
- **THEN** the app produces assistant text from local inference
- **AND** the app remains responsive
- **AND** leaving chat does not crash the app.

### Requirement: GGUF Chat Template Formatting

The native local inference runtime SHALL format user prompts with the loaded
GGUF model's chat template before generation when the model provides one.

#### Scenario: Format single-turn chat prompt

- **GIVEN** a loaded GGUF model exposes a chat template
- **WHEN** the user sends a prompt
- **THEN** the native runtime formats a single user message with assistant
  generation enabled
- **AND** tokenizes the formatted prompt instead of the raw user text.

#### Scenario: Fallback when no template is available

- **GIVEN** a loaded GGUF model does not expose a usable chat template
- **WHEN** the user sends a prompt
- **THEN** the native runtime falls back to the raw user prompt
- **AND** generation still proceeds without crashing.

### Requirement: Configured Generation Token Limit

The app SHALL read the maximum generated-token count from checked-in model
configuration.

#### Scenario: Use configured max tokens

- **GIVEN** the configured model is available
- **AND** the model configuration defines `inference.maxTokens`
- **WHEN** the chat runtime starts generation
- **THEN** the real local engine passes that maximum token count to native
  generation
- **AND** the value is not hardcoded in the engine.

#### Scenario: Reject invalid max tokens

- **GIVEN** the configured model declares a non-positive `inference.maxTokens`
- **WHEN** the app parses model configuration
- **THEN** parsing fails with a configuration error.

### Requirement: Mobile Inference Benchmark Screen

The app SHALL expose a dedicated benchmark screen for repeatable local
inference measurements.

#### Scenario: Open benchmark from home

- **GIVEN** the user is on `Home`
- **WHEN** the user opens benchmark
- **THEN** the app shows a benchmark screen separate from chat and model
  management.

#### Scenario: View stable benchmark parameters

- **GIVEN** the user is viewing the benchmark screen
- **THEN** the app shows the selected model
- **AND** shows the backend label
- **AND** shows the benchmark prompt label, context token limit, and maximum
  generated token limit used for the run.

#### Scenario: Run benchmark for available model

- **GIVEN** the selected configured model is available locally
- **WHEN** the user starts the benchmark
- **THEN** the app loads the selected model through the local inference engine
- **AND** generates text with stable benchmark parameters
- **AND** reports load time, first-token latency, generated token count, total
  generation time, and tokens per second.

#### Scenario: Block benchmark for unavailable model

- **GIVEN** the selected configured model is missing, downloading, invalid, or
  failed
- **WHEN** the user views the benchmark screen
- **THEN** the app disables benchmark execution
- **AND** explains that the selected model must be available locally first.

### Requirement: GPU-Default Local Inference

The app SHALL use GPU-accelerated local inference as the default runtime path
for configured GGUF models when the device supports the native GPU backend.

#### Scenario: Load with GPU offload first

- **GIVEN** a configured GGUF model is available locally
- **WHEN** the real local inference engine loads the model
- **THEN** it requests GPU layer offload before attempting CPU-only loading
- **AND** no user-facing menu or setting is required to enable GPU usage.

#### Scenario: Graceful fallback when GPU load fails

- **GIVEN** the device cannot initialize the native GPU backend for the model
- **WHEN** the real local inference engine attempts to load the model
- **THEN** the app may retry CPU-only loading to keep the flow from crashing
- **AND** the default attempted path remains GPU acceleration.

### Requirement: GPU Runtime Benchmark Label

The benchmark screen SHALL identify the local runtime as GPU-default so
benchmark results are not confused with previous CPU-only measurements.

#### Scenario: View GPU-default benchmark backend

- **WHEN** the user opens the benchmark screen
- **THEN** the backend label identifies the llama.cpp Vulkan/GPU-default runtime.

