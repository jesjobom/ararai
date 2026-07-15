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

### Requirement: Configured Model Runtime Metadata

The app SHALL allow each configured model catalog entry to declare its local
inference runtime, artifact format, and acceleration policy.

#### Scenario: Parse runtime metadata from catalog

- **GIVEN** a configured model catalog entry includes runtime metadata
- **WHEN** the app parses the catalog
- **THEN** the model config records the runtime, artifact format, and
  acceleration policy.

#### Scenario: Default legacy GGUF entries to llama.cpp

- **GIVEN** a legacy configured model entry omits runtime metadata
- **WHEN** the app parses the entry
- **THEN** it defaults to the llama.cpp runtime
- **AND** defaults to the GGUF artifact format
- **AND** defaults to GPU-preferred acceleration.

### Requirement: Runtime-Driven Local Engine Selection

The app SHALL choose local inference behavior from the selected model's
configured runtime metadata rather than hardcoded model IDs.

#### Scenario: Load llama.cpp model with configured acceleration

- **GIVEN** a selected available model uses the llama.cpp runtime
- **AND** its acceleration policy is CPU-only
- **WHEN** the app loads the model for chat or benchmark
- **THEN** it requests CPU-only llama.cpp inference.

#### Scenario: Reject unsupported configured runtime

- **GIVEN** a selected available model uses a runtime not implemented by the app
- **WHEN** the app attempts to load the model
- **THEN** generation or benchmark execution fails with a controlled error.

### Requirement: Runtime Metadata In Benchmark

The benchmark screen SHALL display the selected model runtime so results can be
compared across runtime libraries and acceleration policies.

#### Scenario: View selected runtime in benchmark details

- **GIVEN** the user opens the benchmark screen
- **WHEN** a configured model is selected
- **THEN** the benchmark details show the selected model's runtime and
  acceleration policy.

### Requirement: LiteRT-LM Gemma Runtime

The app SHALL support configured Gemma 4 models using the LiteRT-LM runtime and
`.litertlm` artifact format.

#### Scenario: Load configured Gemma LiteRT-LM model

- **GIVEN** the selected configured model declares `runtime=litert_lm`
- **AND** the model artifact is available and valid at its configured app-owned
  path
- **WHEN** chat or benchmark loads the model
- **THEN** the app initializes LiteRT-LM with that exact `.litertlm` file
- **AND** does not pass the file to llama.cpp.

#### Scenario: Prefer GPU for LiteRT-LM Gemma

- **GIVEN** the selected configured LiteRT-LM model declares
  `acceleration=gpu_preferred`
- **WHEN** the app initializes the LiteRT-LM engine
- **THEN** the engine requests the LiteRT-LM GPU backend.

#### Scenario: Surface LiteRT-LM generation failure

- **GIVEN** LiteRT-LM fails to initialize or generate text
- **WHEN** the user runs chat or benchmark
- **THEN** the app reports a controlled generation failure
- **AND** the app does not crash.

### Requirement: Gemma Runtime Catalog Variant

The checked-in model catalog SHALL include a Gemma 4 LiteRT-LM variant separate
from the existing Gemma 4 GGUF fallback.

#### Scenario: Download Gemma LiteRT-LM artifact

- **GIVEN** the Gemma LiteRT-LM catalog entry is selected
- **AND** its configured `.litertlm` file is missing
- **WHEN** the download flow starts
- **THEN** the app downloads the configured `.litertlm` artifact
- **AND** validates size and SHA-256 before making it available.

#### Scenario: Compare Gemma runtimes

- **GIVEN** both Gemma catalog entries are present
- **WHEN** the user views benchmark details
- **THEN** the app shows whether the selected Gemma model uses llama.cpp or
  LiteRT-LM
- **AND** shows the selected acceleration policy.

### Requirement: Internal Back Navigation

The app SHALL handle Android system back from internal screens by returning to
Home instead of exiting the application.

#### Scenario: Back from internal screen

- **GIVEN** the user is on Chat, Benchmark, or Models
- **WHEN** Android system back is pressed
- **THEN** the app returns to Home
- **AND** releases active work associated with the internal screen.

### Requirement: Long Running Operation Cancellation

The app SHALL expose cancellation controls for model download, chat generation,
and benchmark execution.

#### Scenario: Cancel model download

- **GIVEN** a configured model is downloading
- **WHEN** the user cancels the download
- **THEN** the active download job is cancelled
- **AND** the temporary `.part` file is removed
- **AND** the model returns to a non-downloading state.

#### Scenario: Cancel chat generation

- **GIVEN** chat generation is active
- **WHEN** the user cancels generation
- **THEN** active generation is cancelled
- **AND** local runtime resources are unloaded
- **AND** the UI becomes ready for another prompt when the model is available.

#### Scenario: Cancel benchmark run

- **GIVEN** benchmark execution is active
- **WHEN** the user cancels the benchmark
- **THEN** active benchmark work is cancelled
- **AND** local runtime resources are unloaded
- **AND** the benchmark UI reports a cancelled state.

### Requirement: Build Timestamp Version Label

The app SHALL display a build timestamp version label on Home.

#### Scenario: Build creates timestamp version

- **WHEN** the debug APK is built
- **THEN** the app version name is generated from the build timestamp in
  `yyyyMMddHHmm` format
- **AND** Home displays it as `v<timestamp>`.

### Requirement: Stream Model Downloads To Disk

The app SHALL stream model downloads directly to disk instead of buffering the
entire model in memory.

#### Scenario: Download large model artifact

- **GIVEN** a configured model download starts
- **WHEN** bytes are received from the network
- **THEN** the app writes them incrementally to a sibling `.part` file
- **AND** promotes only the validated file to the final model path.

### Requirement: Pruned Test Model Catalog

The checked-in catalog SHALL include only currently useful test models.

#### Scenario: View configured model list

- **WHEN** the user opens Models
- **THEN** the list includes SmolLM2, Llama 3.2, and Gemma 4 LiteRT-LM
- **AND** the list does not include Gemma 4 GGUF CPU or Phi-4.

### Requirement: Material App Foundation

The app SHALL use a consistent Material 3 foundation for primary screens,
including a shared theme, top-level navigation treatment, and predictable
spacing/action hierarchy.

#### Scenario: Daily-use screen structure

- **WHEN** the user opens Home, Chat, Models, or Diagnostics
- **THEN** each screen uses the shared Material app theme
- **AND** screen titles, back navigation, content spacing, buttons, progress,
  and error states follow a consistent Material 3 treatment.

### Requirement: Chat-Centered Home

Home SHALL present Chat as the primary daily-use action while keeping model
management visible and diagnostics secondary.

#### Scenario: Home action hierarchy

- **WHEN** the user opens the app Home screen
- **THEN** the primary action opens Chat
- **AND** model management is available from Home
- **AND** benchmark access is presented as a secondary diagnostic action, not as
  a model-comparison or benchmark-history workflow.

### Requirement: Benchmark Diagnostic Scope

Benchmark UI SHALL remain an on-demand diagnostics surface and SHALL NOT add
benchmark history or model-comparison workflows.

#### Scenario: Diagnostic benchmark only

- **WHEN** the user opens the benchmark screen
- **THEN** the UI presents the selected model's current diagnostic run controls
- **AND** it does not present benchmark history
- **AND** it does not compare multiple models.

### Requirement: Persistent Selected Model

The app SHALL persist the user's selected model ID locally and restore it on
startup when the model still exists in the checked-in catalog.

#### Scenario: Restore selected model

- **GIVEN** the user selected a non-default configured model
- **WHEN** the app is restarted
- **THEN** the previously selected model is selected again
- **AND** the app falls back to the normal catalog selection only if that model
  ID no longer exists.

### Requirement: Persistent Chat Sessions

The app SHALL persist chat sessions and their messages locally on the device.

#### Scenario: Resume session after restart

- **GIVEN** a chat session contains user and assistant messages
- **WHEN** the app is restarted
- **THEN** the session is available in Chat
- **AND** its messages are restored.

#### Scenario: Manage sessions

- **WHEN** the user uses the Chat session controls
- **THEN** the user can create a new session
- **AND** switch between sessions
- **AND** rename the current session
- **AND** delete a session without deleting other sessions.

### Requirement: Configured Chat System Prompt

The checked-in app configuration SHALL define a short Chat system prompt used
when constructing local generation prompts.

#### Scenario: Build prompt with configured system prompt

- **GIVEN** the configured system prompt is present
- **AND** a chat session has previous messages
- **WHEN** the user submits a new prompt
- **THEN** generation receives a prompt containing the system prompt
- **AND** recent session history
- **AND** the new user message.

### Requirement: Simple Context Window Management

The app SHALL include recent session history in generation prompts using a
simple context budget based on the selected model's configured context size.

#### Scenario: Long session prompt construction

- **GIVEN** a session history is longer than the selected model's context budget
- **WHEN** the user submits a new prompt
- **THEN** the app includes the newest messages that fit the budget
- **AND** omits older messages
- **AND** does not perform automatic summarization yet.

### Requirement: Model Input Capability Metadata

The configured model catalog SHALL declare the input modalities supported by each
model/runtime combination.

#### Scenario: Default existing models to text input

- **GIVEN** a configured model entry does not declare explicit input
  capabilities
- **WHEN** the model catalog is parsed
- **THEN** the app treats text input as supported
- **AND** treats image and audio input as unsupported.

#### Scenario: Parse explicit multimodal capabilities

- **GIVEN** a configured model entry declares text, image, or audio input
  capabilities
- **WHEN** the model catalog is parsed
- **THEN** the resulting model metadata exposes those capabilities to chat UI
  state and runtime validation.

#### Scenario: Do not infer media support from model name

- **GIVEN** a configured model name or artifact URL contains words that suggest
  vision, image, audio, or multimodal support
- **WHEN** the model entry lacks explicit media input capabilities
- **THEN** the app does not show image or audio input controls for that model.

### Requirement: Structured Multimodal Messages

The chat domain SHALL represent user messages as structured prompt content where
images are attachments to text prompts and audio is an alternative prompt
modality.

#### Scenario: Text-only message remains supported

- **GIVEN** the selected model supports text input
- **WHEN** the user submits a non-empty text prompt with no media
- **THEN** the chat request contains a text prompt with no image attachments
- **AND** generation proceeds through the selected local runtime.

#### Scenario: Text prompt with image attachments

- **GIVEN** the selected model supports image input
- **AND** the selected model supports text input
- **WHEN** the user submits a text prompt with one or more image attachments
- **THEN** the chat request contains the text prompt and image attachments
- **AND** the app sends the structured request to the local inference engine.

#### Scenario: Image-only prompt

- **GIVEN** the selected model supports image input
- **WHEN** the user submits one or more image attachments without typed text
- **THEN** the send action is enabled
- **AND** the chat request includes a default image-description text prompt
- **AND** the app sends the image attachments to the local inference engine.

#### Scenario: Audio prompt

- **GIVEN** the selected model supports audio input
- **WHEN** the user submits an audio prompt
- **THEN** the chat request contains audio prompt content
- **AND** the app sends the audio directly to the local inference engine
- **AND** the app does not transcribe the audio before generation.

#### Scenario: Audio prompt cannot include text

- **GIVEN** the user has selected or recorded an audio prompt
- **WHEN** the chat draft is in audio prompt mode
- **THEN** the app does not allow accompanying text to be submitted in the same
  request.

#### Scenario: Empty draft cannot be submitted

- **GIVEN** the chat draft has no text prompt, no image attachment, and no
  audio prompt
- **WHEN** the user opens the chat composer
- **THEN** the send action remains disabled.

### Requirement: Image Input Normalization

The app SHALL copy selected image inputs into app-owned storage and normalize
large camera images before sending them to multimodal runtimes.

#### Scenario: Normalize selected image

- **GIVEN** the user selects an image attachment
- **WHEN** the app imports the image
- **THEN** it decodes the selected image
- **AND** writes an app-owned JPEG copy
- **AND** constrains the longest side to a fixed mobile-friendly input size
- **AND** sends the normalized file path to the local inference engine.

#### Scenario: Render image thumbnail in chat

- **GIVEN** a draft or stored chat message contains an image attachment
- **WHEN** the chat screen renders it
- **THEN** the user can see a thumbnail preview of the local image file.

### Requirement: Capability-Gated Chat Controls

The chat UI SHALL expose media input controls only when the selected model and
runtime support the corresponding modality.

#### Scenario: Hide image action for image-unsupported model

- **GIVEN** the selected model does not support image input
- **WHEN** the chat composer is displayed
- **THEN** no image attachment action is presented.

#### Scenario: Hide audio action for audio-unsupported model

- **GIVEN** the selected model does not support audio input
- **WHEN** the chat composer is displayed
- **THEN** no audio attachment action is presented.

#### Scenario: Show supported media actions

- **GIVEN** the selected model supports image and audio input
- **AND** the selected runtime implementation supports both modalities
- **WHEN** the chat composer is displayed
- **THEN** the image and audio actions are presented.

### Requirement: Multimodal Runtime Boundary

The local inference engine boundary SHALL accept structured message parts and
validate modality support before generation.

#### Scenario: Reject unsupported multimodal request before inference

- **GIVEN** the selected runtime is text-only
- **AND** a chat request contains image attachments or an audio prompt
- **WHEN** generation is requested
- **THEN** the app returns a controlled generation failure
- **AND** it does not call the native text-only inference path.

#### Scenario: Send multimodal content through LiteRT-LM

- **GIVEN** the selected model uses the LiteRT-LM runtime
- **AND** the selected model declares support for every input modality in the
  request
- **WHEN** generation is requested with a text prompt, image attachments, or an
  audio prompt
- **THEN** the LiteRT-LM engine converts that structured request to LiteRT-LM
  content
- **AND** sends them to the LiteRT-LM conversation API.

#### Scenario: Keep llama.cpp text-only for this change

- **GIVEN** the selected model uses the llama.cpp runtime
- **WHEN** chat generation is requested
- **THEN** text-only requests continue to use the existing llama.cpp path
- **AND** requests containing image attachments or audio prompts are rejected
  before JNI.

### Requirement: Multimodal Chat Persistence

The app SHALL persist structured chat content locally so multimodal chat
history can be displayed after app restart.

#### Scenario: Persist structured chat content

- **GIVEN** a submitted user message contains a text prompt with image
  attachments or an audio prompt
- **WHEN** the message is stored
- **THEN** the prompt content is persisted
- **AND** image and audio references point to app-owned local media files.

#### Scenario: Migrate existing text-only history

- **GIVEN** existing persisted chat messages contain only text
- **WHEN** the app opens the upgraded chat store
- **THEN** each existing message is represented as structured text-prompt
  content with no image attachments
- **AND** the existing conversation remains visible.

#### Scenario: Render stored multimodal messages

- **GIVEN** chat history contains stored image attachments or audio prompts
- **WHEN** the chat screen renders the conversation
- **THEN** the user can see image attachments with their text prompt
- **AND** audio prompts render as standalone prompt messages.

### Requirement: Defensive llama.cpp Sampling

The llama.cpp runtime SHALL use a sampler chain that reduces common degenerate
local-generation loops.

#### Scenario: Initialize defensive sampler chain

- **GIVEN** a llama.cpp model is loaded
- **WHEN** the native runtime initializes generation
- **THEN** it configures top-k sampling
- **AND** top-p sampling
- **AND** min-p sampling
- **AND** a repeat penalty over recent tokens
- **AND** temperature sampling before distribution sampling.

### Requirement: Structured llama.cpp Chat Template Input

The llama.cpp runtime SHALL apply GGUF chat templates to structured chat
messages instead of to a preformatted transcript embedded inside one user
message.

#### Scenario: Pass separate roles to native chat template

- **GIVEN** a text-only llama.cpp generation request includes a system prompt,
  prior user/assistant turns, and a current user prompt
- **WHEN** generation starts for a model that exposes a GGUF chat template
- **THEN** the native template receives separate `system`, `user`, and
  `assistant` role messages in chronological order
- **AND** the current user message is the last supplied chat message
- **AND** generation uses the formatted template output with assistant
  generation prompt enabled.

#### Scenario: Fallback without native chat template

- **GIVEN** a text-only llama.cpp generation request includes structured chat
  messages
- **AND** the loaded GGUF model does not expose a usable chat template
- **WHEN** generation starts
- **THEN** the app falls back to a plain transcript representation
- **AND** the fallback transcript preserves the same chronological roles and
  current user prompt.

### Requirement: Chat Settings Overlay

The Chat screen SHALL expose a compact settings overlay for chat-specific
options.

#### Scenario: Open chat settings

- **GIVEN** the user is on the Chat screen
- **WHEN** the user opens Chat settings
- **THEN** the app shows an overlay above the chat content
- **AND** the overlay is visually consistent with the existing session list
  surface
- **AND** the overlay can be dismissed without leaving Chat.

#### Scenario: Future settings can be added

- **GIVEN** the Chat settings overlay is displayed
- **WHEN** new chat-specific options are added later
- **THEN** they can be placed in the same overlay without changing the main
  message list or composer layout.

### Requirement: Reasoning Chat Controls

The Chat settings overlay SHALL provide controls for enabling model reasoning
and for showing reasoning content.

#### Scenario: Gate reasoning controls by selected model

- **GIVEN** the selected model does not declare reasoning request support
- **WHEN** the user opens Chat settings
- **THEN** Enable reasoning is unavailable for that model
- **AND** chat generation requests for that model do not include a
  reasoning-enabled preference.

#### Scenario: Parse configured reasoning capabilities

- **GIVEN** a checked-in model catalog entry declares reasoning request or
  reasoning output support
- **WHEN** the catalog is parsed
- **THEN** the resulting model metadata exposes those reasoning capabilities to
  Chat UI and generation state.

#### Scenario: Enable reasoning for generation

- **GIVEN** the user opens Chat settings
- **AND** the selected model declares reasoning request support
- **WHEN** the user enables reasoning
- **THEN** future chat generation requests include a reasoning-enabled
  preference
- **AND** runtimes or models that do not support reasoning handle the preference
  as unsupported without failing the chat request.

#### Scenario: Hide reasoning content by default

- **GIVEN** the selected runtime returns reasoning content for an assistant
  response
- **AND** Show reasoning is disabled
- **WHEN** the chat message is rendered
- **THEN** the reasoning content is not shown in the conversation
- **AND** the final assistant answer remains visible.

#### Scenario: Show reasoning content when requested

- **GIVEN** the selected runtime returns reasoning content for an assistant
  response
- **AND** Show reasoning is enabled
- **WHEN** the chat message is rendered
- **THEN** the chat message exposes the reasoning content separately from the
  final assistant answer.

### Requirement: Chat Latest-Message Scroll Position

The Chat message list SHALL keep the latest messages visible when the user is
following the bottom of the conversation, while preserving the user's position
when they are reviewing older content.

#### Scenario: Open chat at latest message

- **GIVEN** the selected chat session contains existing messages
- **WHEN** the user enters the Chat screen
- **THEN** the message list scrolls to the latest message.

#### Scenario: Switch session at latest message

- **GIVEN** the user is on the Chat screen
- **AND** another session contains existing messages
- **WHEN** the user switches to that session
- **THEN** the message list scrolls to the latest message in the selected
  session.

#### Scenario: Keep following new content at bottom

- **GIVEN** the Chat message list is already at the bottom
- **WHEN** the user sends a message or assistant output is appended
- **THEN** the message list scrolls as needed to keep the latest content
  visible.

#### Scenario: Preserve position while reviewing older content

- **GIVEN** the Chat message list is not at the bottom
- **WHEN** the user sends a message or assistant output is appended
- **THEN** the message list does not force-scroll to the latest content.

### Requirement: In-Place Audio Prompt Recording

The Chat composer SHALL let the user record an audio prompt directly when the
selected model and runtime support audio input.

#### Scenario: Start audio recording

- **GIVEN** the selected model supports audio input
- **AND** the current draft has no text or image attachment
- **WHEN** the user starts audio recording from the Chat composer
- **THEN** the app requests microphone permission if needed
- **AND** opens the recording dialog
- **AND** starts recording into app-owned storage after permission is granted.

#### Scenario: Review recorded audio

- **GIVEN** audio recording is active
- **WHEN** the user stops recording
- **THEN** the app shows the recorded prompt with duration
- **AND** stores the recording in a runtime-decodable PCM WAV container
- **AND** the user can replay the recording in the dialog
- **AND** the user can cancel and delete the recording
- **AND** the user can send the recording from the dialog.

#### Scenario: Send recorded audio prompt

- **GIVEN** the user stopped and reviewed a recorded audio prompt
- **WHEN** the user submits Chat
- **THEN** generation receives an `AudioPrompt` backed by the recorded local file
- **AND** the recorded audio prompt is persisted with the chat message.

#### Scenario: Replay persisted audio prompt

- **GIVEN** a chat message contains an audio prompt
- **WHEN** the message is shown in chat history
- **THEN** the user can replay the audio from that message.

#### Scenario: Hide recording for unsupported model

- **GIVEN** the selected model does not support audio input
- **WHEN** the Chat composer is displayed
- **THEN** no audio recording action is presented.

