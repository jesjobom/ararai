# local-llm-hub Specification

## Purpose
ArarAI is an Android hub for configured open LLMs. It manages model artifacts,
app-owned conversations and media, and runs Chat inference locally through
runtime-neutral boundaries without requiring an application backend, remote
database, or hosted inference API. Native runtime, acceleration, memory,
thermal, and real-model behavior remain subject to physical-device validation.
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

The app SHALL support a configured Gemma 4 LiteRT-LM chat model catalog with one
default model.

#### Scenario: Load existing selected configured model

- **GIVEN** the selected configured Gemma model exists at its configured
  app-owned path
- **AND** the file passes the configured integrity check
- **WHEN** model resolution runs
- **THEN** the app can pass that file to the local inference engine
- **AND** the model list reports that model as available.

#### Scenario: Download missing selected configured model

- **GIVEN** the selected configured Gemma model is missing or fails integrity
  validation
- **AND** no other configured chat model is available locally
- **WHEN** the app starts with network access
- **THEN** the app automatically downloads Gemma 4 E2B as the configured default
- **AND** validates the downloaded file before loading it.

#### Scenario: Skip default download when another model is available

- **GIVEN** Gemma 4 E2B is missing
- **AND** another configured Gemma chat model is already available locally
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

The application SHALL isolate Gemma 4 execution behind an inference engine
boundary backed by LiteRT-LM.

#### Scenario: Runtime replacement

- **WHEN** a future runtime is evaluated
- **THEN** the app can add another engine implementation without rewriting the
  chat UI or configured-model resolution flow.

#### Scenario: Real runtime behind boundary

- **GIVEN** a configured Gemma 4 LiteRT-LM model is available
- **WHEN** the chat flow requests generation
- **THEN** the app uses LiteRT-LM behind `LocalLlmEngine`
- **AND** the chat UI does not depend directly on LiteRT-LM types.

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

The checked-in configuration SHALL include Gemma 4 E2B and E4B LiteRT-LM chat
models and supported Whisper transcription models, with Gemma 4 E2B as default.

#### Scenario: Parse configured model catalog

- **WHEN** the app starts
- **THEN** it parses only supported LiteRT-LM chat and Whisper utility entries
- **AND** each entry defines its ID, source URL, expected local path, integrity
  metadata, expected download size, recommended free RAM, runtime, artifact
  format, acceleration, capabilities, and applicable inference parameters.

#### Scenario: Keep configured model list static

- **WHEN** the user opens model management
- **THEN** the app shows only models declared by checked-in configuration
- **AND** it shows no GGUF or llama.cpp chat model
- **AND** the UI does not allow arbitrary model entries to be added.

#### Scenario: Validate model resource metadata

- **GIVEN** a catalog entry declares expected download size or recommended free
  RAM
- **WHEN** the app parses the catalog
- **THEN** each declared value is a positive byte count.

### Requirement: Model Management Metadata Presentation

Home and model management SHALL present model metadata without exposing the
app-owned local file path or implying that static metadata is interactive.

#### Scenario: Present selected model on Home

- **GIVEN** a model is selected
- **WHEN** the user views Home
- **THEN** the `Model Manager` card shows the selected model name
- **AND** text, voice, image, unified reasoning, and CPU/GPU capabilities are
  presented as non-interactive badges
- **AND** the local model file path is not presented.

#### Scenario: Present configured model details

- **WHEN** the user views model management
- **THEN** every model shows non-interactive badges for runtime, acceleration,
  and supported capabilities
- **AND** reasoning request or output support is represented by one `Reasoning`
  badge
- **AND** a missing model shows its configured approximate download size
- **AND** an available model shows the current local file size
- **AND** the configured recommended free RAM is shown
- **AND** the local model file path is not presented.

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

The app SHALL provide LiteRT-LM local inference for configured Gemma 4 bundles
that are present and valid on the device.

#### Scenario: Load available configured model

- **GIVEN** model startup reports a configured Gemma model as available
- **WHEN** chat starts real generation
- **THEN** the app loads that exact `.litertlm` bundle through LiteRT-LM
- **AND** applies the configured inference defaults
- **AND** does not use a remote inference API.

#### Scenario: Native load failure

- **GIVEN** the configured Gemma model is reported available
- **AND** LiteRT-LM fails to load it
- **WHEN** the user attempts generation
- **THEN** the chat screen shows a load error
- **AND** prompt submission becomes available again when otherwise ready
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
inference measurements, SHALL open it for the exact downloaded reasoning model
chosen in Model Management, and SHALL label only runtime-backed token
measurements as token counts or token throughput.

#### Scenario: Open benchmark from home

- **GIVEN** the user is viewing a downloaded reasoning model in Model
  Management
- **WHEN** the user opens its benchmark
- **THEN** the app shows a benchmark screen for that exact model
- **AND** back navigation returns to Model Management.

#### Scenario: View stable benchmark parameters

- **GIVEN** the user is viewing the benchmark screen
- **THEN** the app shows the selected model
- **AND** shows the backend label
- **AND** shows the benchmark prompt label, context token limit, and maximum
  generated token limit used for the run.

#### Scenario: Run benchmark for available model

- **GIVEN** the selected configured model is available locally
- **AND** its runtime exposes native prefill and decode statistics
- **WHEN** the user starts the benchmark
- **THEN** the app loads the selected model through the local inference engine
- **AND** generates text with stable benchmark parameters
- **AND** reports load time and time to first token separately
- **AND** reports native prefill token count and throughput separately from
  native decode token count and throughput
- **AND** does not use streamed callback count as generated token count.

#### Scenario: Run benchmark without native token metrics

- **GIVEN** the selected runtime does not expose a trustworthy token count
- **WHEN** the benchmark completes
- **THEN** the app reports the available latency and elapsed-time measurements
- **AND** any fallback throughput has an accurate non-token unit
- **AND** the app does not label streamed callback chunks as tokens.

#### Scenario: Block benchmark for unavailable model

- **GIVEN** the selected configured model is missing, downloading, invalid, or
  failed
- **WHEN** the user views Model Management
- **THEN** the app does not expose its benchmark action
- **AND** retains the action needed to make the model available locally.

### Requirement: GPU-Default Local Inference

The app SHALL use bounded GPU-accelerated local inference as the default runtime
path for configured GPU-preferred GGUF models when the device supports the
native GPU backend, and SHALL NOT translate GPU preference into unlimited model
offload.

#### Scenario: Load with GPU offload first

- **GIVEN** a configured GPU-preferred GGUF model is available locally
- **WHEN** the real local inference engine loads the model
- **THEN** it requests the model's configured finite GPU-layer count
- **AND** a legacy entry without an explicit count uses a conservative finite
  default
- **AND** no user-facing menu or setting is required to enable GPU usage
- **AND** the runtime does not use an unlimited layer sentinel.

#### Scenario: Graceful fallback when GPU load fails

- **GIVEN** the device cannot initialize the native GPU backend for the model
- **WHEN** the real local inference engine attempts to load or safely retry the
  model
- **THEN** the app may retry CPU-only loading to keep the flow from crashing
- **AND** CPU-only loading requests zero GPU layers
- **AND** the default attempted path remains bounded GPU acceleration.

### Requirement: GPU Runtime Benchmark Label

The benchmark screen SHALL identify the local runtime as GPU-default so
benchmark results are not confused with previous CPU-only measurements.

#### Scenario: View GPU-default benchmark backend

- **WHEN** the user opens the benchmark screen
- **THEN** the backend label identifies the llama.cpp Vulkan/GPU-default runtime.

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

Home SHALL present Chat and Voice Chat as daily-use conversation actions while
keeping model management and settings visible. Model benchmark access SHALL be
owned by downloaded model cards rather than a separate Home destination.

#### Scenario: Home action hierarchy

- **WHEN** the user opens the app Home screen
- **THEN** Chat and Voice Chat use visually consistent, fully clickable cards
- **AND** model management and settings use a consistent utility card treatment
- **AND** no Home destination requires a nested action button
- **AND** Home does not present a standalone reasoning benchmark or diagnostics
  destination.

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

The app SHALL persist conversations and their messages locally on the device
and SHALL make the same canonical conversation history available to normal Chat
and Voice Chat.

#### Scenario: Resume session after restart

- **GIVEN** a conversation contains user and assistant messages created through
  normal Chat, Voice Chat, or both
- **WHEN** the app is restarted
- **THEN** the conversation is available in Chat
- **AND** its messages, media, completed transcripts, and completion states are
  restored
- **AND** Voice Chat can continue that same conversation.

#### Scenario: Manage sessions

- **WHEN** the user uses the session controls in normal Chat or idle Voice Chat
- **THEN** the user can create a new conversation
- **AND** switch between conversations
- **AND** rename the current conversation
- **AND** delete a conversation without deleting other conversations
- **AND** the current conversation selection applies when Voice Chat opens.

#### Scenario: Preserve screen origin without splitting history

- **WHEN** a turn is persisted from normal Chat or Voice Chat
- **THEN** it may record its originating interaction mode
- **AND** origin does not create a separate session or context
- **AND** both screens observe one ordered canonical history.

### Requirement: Configured Chat System Prompt

The app SHALL compose an effective system instruction from app-owned invariants
and a bounded locally persisted user instruction selected independently for
normal Chat or Voice Chat.

#### Scenario: Initialize instruction preferences

- **GIVEN** the user has not customized either interaction mode
- **WHEN** instruction preferences are loaded
- **THEN** normal Chat uses its checked-in default user instruction
- **AND** Voice Chat uses its checked-in default user instruction
- **AND** app-owned invariant instructions remain present and non-editable.

#### Scenario: Build prompt with configured system prompt

- **GIVEN** the effective configured system instruction is present
- **AND** a persisted conversation has previous eligible messages
- **WHEN** either Chat screen submits a new turn that requires context
  initialization
- **THEN** generation receives the effective system instruction
- **AND** recent eligible conversation history
- **AND** the new user message
- **AND** a compatible incremental native continuation does not prefill that
  unchanged history again.

#### Scenario: Build a normal-Chat prompt

- **GIVEN** normal Chat has a persisted user instruction
- **WHEN** normal Chat submits a turn that requires context initialization
- **THEN** generation receives the app-owned invariant instruction
- **AND** the normal-Chat user instruction
- **AND** recent eligible canonical conversation history
- **AND** the new user message.

#### Scenario: Build a Voice-Chat prompt

- **GIVEN** Voice Chat has a persisted user instruction
- **WHEN** Voice Chat submits a turn that requires context initialization
- **THEN** generation receives the app-owned invariant instruction
- **AND** the Voice-Chat user instruction
- **AND** recent eligible canonical conversation history
- **AND** the new user turn.

#### Scenario: Add current temporal context to a conversation turn

- **GIVEN** the device has a current local date and configured time zone
- **WHEN** normal Chat or Voice Chat builds a generation request
- **THEN** the effective system instruction contains the current local date,
  time-zone identifier, and UTC offset
- **AND** the temporal context is regenerated for that turn
- **AND** it appears only once in the projected prompt
- **AND** it is not persisted as canonical conversation history.

#### Scenario: Restore an instruction default

- **GIVEN** the user customized one interaction mode's instruction
- **WHEN** the user restores that mode's default
- **THEN** only that editable instruction returns to its checked-in default
- **AND** the other interaction mode remains unchanged
- **AND** canonical conversation history remains unchanged.

#### Scenario: Invalidate stale native instructions

- **GIVEN** the runtime retains a native conversation
- **WHEN** the effective instruction or advertised tool set for the next turn
  differs from the retained conversation
- **THEN** the retained native conversation is not reused
- **AND** a fresh compatible context is initialized from bounded canonical
  history
- **AND** persisted conversation messages remain unchanged.

### Requirement: Simple Context Window Management

The shared conversation coordinator SHALL project recent eligible persisted
history using a context budget based on the selected model's configured context
size, independently of whether the new turn originates in normal Chat or Voice
Chat.

#### Scenario: Long session prompt construction

- **GIVEN** persisted history is longer than the selected model's context budget
- **WHEN** a new turn requires a reconstructed prompt or native-session
  rehydration
- **THEN** the coordinator includes the newest eligible messages that fit the
  budget
- **AND** omits older messages
- **AND** does not perform automatic summarization yet.

#### Scenario: Compatible incremental continuation

- **GIVEN** the runtime retains compatible native conversation state for the
  same canonical history
- **WHEN** either Chat screen submits the next turn
- **THEN** context budgeting verifies compatibility
- **AND** the runtime receives only the new user content
- **AND** unchanged persisted history is not sent again.

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
modality. An image-only first turn SHALL use the default image-description prompt
as its automatic session title.

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

#### Scenario: Image-only prompt titles a new session

- **GIVEN** a new Chat session and a model that supports image input
- **WHEN** the user submits one or more image attachments without typed text
- **THEN** the request includes the default image-description text prompt
- **AND** the session title uses that prompt instead of remaining `New chat`.

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

### Requirement: Clear All Chat Sessions

The Chat session list SHALL provide a confirmed bulk action that permanently
deletes all locally stored chat sessions and their messages while preserving a
valid empty Chat state.

#### Scenario: Request bulk session deletion

- **GIVEN** one or more Chat sessions exist
- **WHEN** the user selects `Clear all` from the Chat session list
- **THEN** the app asks for confirmation before deleting any session or message
- **AND** the confirmation explains that the deletion is permanent
- **AND** the user can cancel without changing stored Chat data.

#### Scenario: Confirm bulk session deletion

- **GIVEN** the bulk-delete confirmation is displayed
- **WHEN** the user confirms `Clear all`
- **THEN** all existing Chat sessions and their messages are deleted atomically
- **AND** the app creates and selects one new empty session
- **AND** no message from a deleted session is displayed
- **AND** draft text and pending image or audio attachments are cleared.

#### Scenario: Preserve unrelated local data

- **GIVEN** the user confirms clearing all Chat sessions
- **WHEN** the deletion completes
- **THEN** downloaded models and application settings remain unchanged.

#### Scenario: Generation is active

- **GIVEN** assistant generation is active
- **WHEN** bulk session deletion would otherwise be available
- **THEN** the app does not clear sessions or messages until generation is no
  longer active.

### Requirement: Basic Markdown Chat Rendering

The Chat screen SHALL render a basic Markdown subset in textual Chat message
content without modifying the stored source text.

#### Scenario: Render block formatting

- **GIVEN** a text message contains Markdown headings, unordered or ordered
  lists, block quotes, fenced code, or horizontal rules
- **WHEN** the message appears in Chat history
- **THEN** each supported block is presented with visually distinct formatting
- **AND** the original Markdown source remains unchanged in session storage.

#### Scenario: Render inline formatting

- **GIVEN** a text message contains bold, italic, inline code, or link syntax
- **WHEN** the message appears in Chat history
- **THEN** the supported inline content is styled without displaying its
  formatting delimiters.

#### Scenario: Render visible reasoning

- **GIVEN** an assistant message contains reasoning text
- **AND** `Show reasoning` is enabled
- **WHEN** the message appears in Chat history
- **THEN** the same supported basic Markdown formatting is applied to the
  visible reasoning text.

#### Scenario: Preserve unsupported or malformed input

- **GIVEN** a message contains unsupported or malformed Markdown syntax
- **WHEN** the message appears in Chat history
- **THEN** the app displays readable text for that content
- **AND** message rendering does not fail.

#### Scenario: Empty streamed response

- **GIVEN** an assistant response has not emitted visible text yet
- **WHEN** its message placeholder appears in Chat history
- **THEN** the existing loading placeholder remains visible.

### Requirement: Retain Loaded Chat Model Across Internal Navigation

The app SHALL retain the selected Chat model engine while the user navigates
between screens inside the same running app process unless model state requires
the engine to be unloaded.

#### Scenario: Leave Chat with an idle loaded model

- **GIVEN** the selected model is loaded for Chat
- **AND** no generation is active
- **WHEN** the user leaves Chat for another app screen
- **THEN** the app retains the loaded model engine
- **AND** returning to Chat with the same selected model does not require a full
  model reload before the next request.

#### Scenario: Leave Chat during generation

- **GIVEN** assistant generation is active
- **WHEN** the user leaves Chat for another app screen
- **THEN** the active generation is cancelled
- **AND** the unchanged selected model remains loaded after cancellation.

#### Scenario: Selected model changes

- **GIVEN** a model is loaded for Chat
- **WHEN** another model is selected
- **THEN** the previously loaded model is unloaded before the new selected
  model is used.

#### Scenario: Selected model becomes unusable

- **GIVEN** a model is loaded for Chat
- **WHEN** that model becomes missing, invalid, deleted, or otherwise
  unavailable
- **THEN** the app cancels active generation if necessary
- **AND** unloads the unusable model engine.

#### Scenario: Android destroys the app process

- **GIVEN** the selected model was retained during internal navigation
- **WHEN** Android destroys the app process
- **THEN** the app does not promise to retain the loaded model
- **AND** a later process start may load the model again when needed.

### Requirement: Textual Context for Audio Prompts

When the selected model supports audio input, the app SHALL send the current
audio file together with bounded textual context from the selected Chat
session.

#### Scenario: Send audio with system instruction

- **GIVEN** the selected model declares audio input support
- **AND** Chat has a configured system prompt
- **WHEN** the user submits a current audio prompt
- **THEN** the generation request contains the current audio file
- **AND** includes the configured system instruction as textual context.

#### Scenario: Send audio with recent textual history

- **GIVEN** the selected session contains prior user and assistant messages
- **WHEN** the user submits a current audio prompt
- **THEN** the generation request includes recent textual history from that
  selected session
- **AND** preserves user and assistant roles
- **AND** applies the configured context budget before generation.

#### Scenario: Historical messages contain media

- **GIVEN** the selected session contains historical image or audio attachments
- **WHEN** the user submits a current audio prompt
- **THEN** this change does not re-send those historical media files
- **AND** any textual representation already used by the bounded history may
  remain in the context.

#### Scenario: Current model does not support audio

- **GIVEN** the selected model does not declare audio input support
- **WHEN** an audio prompt would otherwise be submitted
- **THEN** the app does not send the audio generation request to that model.

#### Scenario: Context exceeds the configured budget

- **GIVEN** the system instruction and session history exceed the available
  input context budget
- **WHEN** the user submits a current audio prompt
- **THEN** the app retains the system instruction
- **AND** selects the most recent fitting textual history
- **AND** reserves output capacity according to the current inference
  configuration.

### Requirement: User-controlled scrolling during streamed responses

Chat SHALL keep the end of a growing response visible while automatic
following is enabled. Chat SHALL stop automatic following when the user drags
the message history and SHALL NOT force the user back to the generated text
while they inspect another position. Automatic following SHALL become enabled
again when the user returns to the bottom.

#### Scenario: User inspects earlier text during generation

- **WHEN** an assistant response is streaming and the user drags the message
  history away from the bottom
- **THEN** subsequent streamed content does not change the user's scroll
  position

#### Scenario: User returns to the latest content

- **WHEN** the user scrolls back to the bottom during generation
- **THEN** Chat follows the actual end of subsequent streamed content

### Requirement: Long generation has no app-level time or chunk limit

Chat SHALL NOT cancel a generation because of elapsed time or the number of
streaming callbacks. Generation MAY end when the runtime completes or fails,
the configured runtime output limit is reached, the user selects `Cancel
Generation`, or an applicable app lifecycle event cancels active work.

#### Scenario: LiteRT-LM emits many callback chunks

- **WHEN** LiteRT-LM emits more callback chunks than the configured maximum
  output-token value
- **THEN** the app continues consuming the stream without calling runtime
  cancellation solely because of the callback count

### Requirement: Compact session-dialog actions

The Chat session dialog SHALL display `New` beside the `Chat sessions` title.
The bottom action row SHALL display only `Clear all` followed by `Close`. Each
action SHALL include an icon. The dialog SHALL NOT display a separate bottom
rename action.

#### Scenario: Session dialog is opened

- **WHEN** the user opens the Chat session dialog
- **THEN** `New` is displayed beside the dialog title
- **AND** the bottom action row presents `Clear all` followed by `Close`

### Requirement: Rename a session from its card

Chat SHALL open the rename dialog for the specific session card that the user
presses and holds. Renaming a non-selected session SHALL NOT require selecting
that session first.

#### Scenario: User long-presses a non-selected session

- **WHEN** the user presses and holds a non-selected session card
- **THEN** Chat opens a rename dialog initialized with that session's title
- **AND** confirming updates that session without changing the selected session

### Requirement: Compact active-session indication

The Chat session dialog SHALL indicate the active session through its
differentiated card color and SHALL NOT display an additional `Current` text
label.

#### Scenario: Active session is displayed

- **WHEN** the session dialog lists the active session
- **THEN** its card uses the selected-session color
- **AND** no `Current` label consumes space in the card

### Requirement: Workload-Aware LiteRT-LM Modality Profile

The app SHALL initialize only the LiteRT-LM modality backends required by the
active workload while preserving the selected model's configured capabilities.

#### Scenario: Load Gemma for text-only generation

- **GIVEN** a configured LiteRT-LM Gemma model supports text, image, and audio
- **WHEN** the model is loaded for a text-only workload
- **THEN** the language-model backend uses its configured acceleration policy
- **AND** vision and audio processing backends are not initialized.

#### Scenario: Reconfigure for a multimodal request

- **GIVEN** the active LiteRT-LM engine profile does not include a supported
  modality required by the next request
- **WHEN** generation starts
- **THEN** the app closes incompatible retained conversation state
- **AND** recreates the LiteRT-LM engine with the required modality backend
- **AND** processes the request without changing catalog capability metadata.

#### Scenario: Reject a modality absent from model capabilities

- **GIVEN** a request uses a modality the selected model does not support
- **WHEN** generation is requested
- **THEN** the app returns a controlled failure
- **AND** does not recreate the LiteRT-LM engine for that unsupported modality.

### Requirement: Safe LiteRT-LM Conversation Reuse

The app SHALL treat LiteRT-LM conversation state as an ephemeral execution cache,
reuse it only for a verified compatible continuation from either Chat screen,
and prevent context from crossing persisted conversation or configuration
boundaries.

#### Scenario: Continue a compatible chat session

- **GIVEN** a LiteRT-LM generation completed successfully for a persisted
  conversation
- **AND** the next request identifies the same conversation
- **AND** its eligible history exactly matches the transcript retained by the
  runtime
- **AND** model, modality profile, sampler settings, reasoning mode, and runtime
  generation are unchanged
- **WHEN** the user submits the next message through normal Chat or Voice Chat
- **THEN** the app reuses the existing LiteRT-LM conversation
- **AND** sends only the new user content instead of prefilling the complete
  transcript again
- **AND** screen origin does not make the continuation incompatible.

#### Scenario: Rehydrate missing native state

- **GIVEN** a persisted conversation has eligible history
- **AND** no compatible native conversation is live because the app or runtime
  was recreated or previous state was evicted
- **WHEN** either Chat screen submits the next message
- **THEN** the app creates a fresh native conversation
- **AND** initializes it once from the eligible system instruction and bounded
  persisted history
- **AND** processes the new message as the current incremental turn.

#### Scenario: Start a fresh incompatible conversation

- **GIVEN** there is retained LiteRT-LM conversation state
- **WHEN** the next request belongs to another persisted conversation or has
  incompatible transcript, model, modality profile, sampler settings, reasoning
  mode, or runtime generation
- **THEN** the app closes the retained conversation
- **AND** creates a new conversation initialized from the request's eligible
  system instruction and bounded history
- **AND** persisted history remains unchanged.

#### Scenario: Invalidate partial or failed conversation state

- **GIVEN** a LiteRT-LM generation is cancelled, fails, or does not atomically
  commit a complete assistant response
- **WHEN** cleanup runs
- **THEN** the app closes and discards that native conversation
- **AND** a later request cannot reuse its partial state
- **AND** completed persisted conversation history remains recoverable.

#### Scenario: Keep benchmark runs isolated

- **WHEN** a LiteRT-LM benchmark run starts
- **THEN** it uses a fresh conversation without retained app-conversation context
- **AND** its metrics describe only that benchmark run.

### Requirement: App-Owned LiteRT-LM Runtime Cache

The app SHALL provide LiteRT-LM with a reclaimable app-owned cache directory
without making cache availability a prerequisite for inference.

#### Scenario: Initialize LiteRT-LM cache

- **WHEN** the app constructs the LiteRT-LM engine
- **THEN** it passes a dedicated directory below the app cache root through the
  LiteRT-LM engine configuration
- **AND** no shared-storage permission is required.

#### Scenario: Cache directory is unavailable

- **GIVEN** the dedicated cache directory cannot be created or used
- **WHEN** LiteRT-LM initialization starts
- **THEN** the app records diagnostics and attempts uncached initialization
- **AND** chat or benchmark does not fail solely because cache setup failed.

### Requirement: Bounded Chat Image Import

The app SHALL import external Chat images without buffering an unbounded source
in memory and SHALL enforce documented source and decoded-image limits before
persisting normalized media.

#### Scenario: Import an image within limits

- **GIVEN** the selected content is a decodable image within configured limits
- **WHEN** the user attaches it to a Chat prompt
- **THEN** the app processes it through bounded I/O
- **AND** stores only the normalized app-owned image used by Chat.

#### Scenario: Reject an oversized image

- **GIVEN** the selected content exceeds the configured source or decoded-image limit
- **WHEN** image import evaluates the content
- **THEN** the app rejects the attachment with a controlled error
- **AND** does not retain a partial app-owned image.

#### Scenario: Handle malformed or interrupted image input

- **GIVEN** the selected content is malformed, unavailable, or fails while being read
- **WHEN** image import runs
- **THEN** Chat remains usable and reports the import failure
- **AND** any temporary or partial output file is removed.

#### Scenario: Normalize EXIF-oriented image content

- **GIVEN** the selected image declares a rotated or mirrored EXIF orientation
- **WHEN** the app creates the normalized app-owned Chat image
- **THEN** it applies the declared orientation to the image pixels before persistence
- **AND** the Chat preview and local model receive the same visually upright image.

### Requirement: Batched Streamed Response Persistence

The app SHALL present streamed assistant output immediately while persisting
that output at a bounded cadence rather than writing once per generated delta.

#### Scenario: Render frequent generation deltas

- **GIVEN** local inference emits multiple assistant text deltas in rapid succession
- **WHEN** Chat processes those deltas
- **THEN** the visible message updates as deltas arrive
- **AND** durable storage updates are batched according to the documented cadence.

#### Scenario: Flush a completed response

- **GIVEN** assistant content is waiting to be persisted
- **WHEN** generation completes successfully
- **THEN** the complete visible assistant content is persisted before completion handling finishes.

#### Scenario: Preserve an interrupted partial response

- **GIVEN** assistant content has been streamed but not fully persisted
- **WHEN** generation is cancelled, fails, or Chat is left
- **THEN** the latest partial content is flushed through the controlled termination path
- **AND** reopening the session does not silently lose already-visible output.

### Requirement: Deterministic LiteRT-LM Conversation Disposal

The LiteRT-LM runtime SHALL close every invalidated native conversation exactly
once and SHALL not retain a reusable reference after cancellation or failure.

#### Scenario: Cancel active LiteRT-LM generation

- **GIVEN** a LiteRT-LM conversation is actively generating
- **WHEN** generation is cancelled
- **THEN** processing is cancelled and the conversation is closed
- **AND** active and retained references to that conversation are cleared.

#### Scenario: Generate after cancellation

- **GIVEN** a previous LiteRT-LM conversation was cancelled
- **WHEN** a later compatible Chat request starts
- **THEN** the runtime creates a new conversation
- **AND** does not reuse the cancelled native state.

#### Scenario: Unload after cancellation

- **GIVEN** cancellation has already disposed the active conversation
- **WHEN** the engine unloads
- **THEN** unload completes without double-closing the conversation
- **AND** all LiteRT-LM engine resources are released.

### Requirement: Owned Chat Media Lifecycle

The app SHALL manage app-owned Chat media according to explicit draft and
persisted-message ownership and SHALL remove files after they become unreferenced.

#### Scenario: Remove a draft attachment

- **GIVEN** an app-owned image or audio file is attached only to the current draft
- **WHEN** the user removes or replaces that attachment
- **THEN** the app removes the unreferenced draft file
- **AND** no persisted message is changed.

#### Scenario: Delete a session containing media

- **GIVEN** a Chat session references app-owned media files
- **WHEN** the session is deleted
- **THEN** its messages are removed atomically according to the session-store contract
- **AND** media with no remaining references is deleted from Chat media storage.

#### Scenario: Preserve referenced media

- **GIVEN** a media file remains referenced by a persisted message
- **WHEN** cleanup or reconciliation runs
- **THEN** the file is preserved.

#### Scenario: Reconcile orphaned Chat media

- **GIVEN** an app-owned Chat media file has no draft or persisted-message reference
- **WHEN** bounded media reconciliation runs
- **THEN** the orphan is removed
- **AND** cleanup does not access files outside the canonical Chat media directory.

### Requirement: Explicit Local Data Backup Policy

The app SHALL define Android backup and device-transfer behavior explicitly and
SHALL exclude private, large, derived, or reference-sensitive local data from
platform-managed extraction.

#### Scenario: Evaluate cloud backup content

- **GIVEN** Android backup evaluates ArarAI app-owned data
- **WHEN** backup rules are applied
- **THEN** Chat databases, Chat media, downloaded models, temporary downloads, and runtime caches are excluded.

#### Scenario: Evaluate device-to-device transfer

- **GIVEN** Android device transfer evaluates ArarAI app-owned data
- **WHEN** data-extraction rules are applied
- **THEN** excluded private and reference-sensitive data is not transferred
- **AND** the restored app cannot receive dangling Chat media references from platform backup.

#### Scenario: Inspect documented privacy behavior

- **WHEN** a maintainer or user reviews ArarAI data behavior
- **THEN** the documentation states what remains only on-device
- **AND** states whether any limited preference data is eligible for backup.

### Requirement: Atomic Chat Message Append

The Chat session store SHALL persist a new message and update its owning
session timestamp as one atomic operation.

#### Scenario: Append a message successfully

- **GIVEN** the target Chat session exists
- **WHEN** a message is appended
- **THEN** the message and updated session timestamp commit together
- **AND** session ordering reflects the appended message.

#### Scenario: Append to a missing session

- **GIVEN** the target Chat session does not exist
- **WHEN** a message append is attempted
- **THEN** the store reports a controlled persistence failure
- **AND** no orphan message is committed.

#### Scenario: Fail while updating the session

- **GIVEN** message insertion begins inside a transaction
- **WHEN** the owning session cannot be updated exactly once
- **THEN** the transaction rolls back
- **AND** neither partial message state nor a partial timestamp change remains.

### Requirement: Separated Chat Presentation and Media Boundaries

The app SHALL keep Chat presentation separate from media import, recording,
playback, encoding, and filesystem implementations while preserving current
observable behavior.

#### Scenario: Render Chat without direct media I/O

- **GIVEN** Chat state contains text or media messages
- **WHEN** the Chat presentation renders that state
- **THEN** rendering depends on presentation models and explicit media interfaces
- **AND** Compose components do not directly own media filesystem operations.

#### Scenario: Replace a media implementation in tests

- **GIVEN** image import, audio recording, or playback behavior is under test
- **WHEN** a test supplies a fake implementation
- **THEN** Chat orchestration can be verified without real device media I/O.

#### Scenario: Preserve behavior during refactoring

- **GIVEN** the existing supported Chat flows
- **WHEN** responsibilities are moved behind focused boundaries
- **THEN** attachment, recording, playback, permission, cancellation, and persisted-content behavior remains compatible.

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

### Requirement: Current and Verifiable Project Documentation

The project SHALL maintain onboarding and architecture documentation that
matches implemented ArarAI capabilities, current build configuration, and the
canonical OpenSpec workflow.

#### Scenario: Review current product direction

- **WHEN** a maintainer reads the README and project context
- **THEN** the documented runtimes, model catalog, persistence, multimodal Chat, reasoning, and diagnostics match implemented behavior
- **AND** historical MVP exclusions are not presented as current constraints.

#### Scenario: Follow repository instructions

- **GIVEN** a maintainer follows a documented path or verification command
- **WHEN** it is used in the current repository
- **THEN** the path exists and the command is valid for its stated environment.

#### Scenario: Archive a product change

- **WHEN** an OpenSpec change materially alters documented capabilities, architecture, setup, or validation
- **THEN** its completion checklist includes review of the README and project context
- **AND** documentation claims remain bounded to implemented and verified behavior.

### Requirement: Application-scoped local inference ownership

The application SHALL maintain at most one configured native local inference
engine tree for foreground Chat and Benchmark features.

#### Scenario: Open Benchmark after using Chat

- **GIVEN** Chat has loaded or retained the selected local model
- **WHEN** the user leaves Chat and starts Benchmark
- **THEN** Benchmark uses the same application-scoped configured engine
- **AND** the application does not construct or load a second native runtime
  tree for Benchmark.

#### Scenario: Return to Chat after Benchmark

- **GIVEN** Benchmark has completed, failed, or been canceled
- **AND** Benchmark has unloaded the shared runtime
- **WHEN** the user next submits a Chat prompt
- **THEN** Chat reloads the selected model through the shared engine
- **AND** generation proceeds without requiring an application restart.

### Requirement: Native speech playback for assistant responses

The Chat SHALL identify the language of each completed assistant response
locally and SHALL configure the device's default Android text-to-speech engine
with an installed compatible voice for that language without speaking reasoning
content.

#### Scenario: Prepare a completed assistant response

- **GIVEN** an assistant message has completed
- **AND** its response text is not blank
- **WHEN** Chat begins local language identification
- **THEN** the message exposes its sound action in a disabled state
- **AND** the action remains disabled until identification finishes.

#### Scenario: Play a completed assistant response

- **GIVEN** an assistant message has completed
- **AND** its response text is not blank
- **AND** local language identification produced a supported language
- **WHEN** the user activates its enabled sound action
- **THEN** the app selects an installed native TTS voice compatible with the
  detected language
- **AND** sends only the response text to the native TTS service
- **AND** does not send the message's reasoning content
- **AND** the sound action becomes a stop action for that message.

#### Scenario: Language cannot be selected

- **GIVEN** identification is uncertain, fails, or produces a language that is
  unavailable in the native TTS engine
- **WHEN** preparation finishes and the user activates the sound action
- **THEN** Chat attempts playback with the device's configured default TTS
  language and voice
- **AND** the app remains usable.

#### Scenario: Do not offer speech for ineligible messages

- **GIVEN** a message belongs to the user, has blank response text, or is still
  being generated
- **WHEN** Chat renders the message
- **THEN** the message does not expose the TTS sound action.

#### Scenario: Stop active response

- **GIVEN** an assistant response is currently speaking
- **WHEN** the user activates its stop action
- **THEN** speech stops promptly
- **AND** the message returns to the sound action state.

#### Scenario: Start another response while speech is active

- **GIVEN** one assistant response is currently speaking
- **WHEN** the user activates the enabled sound action on another prepared
  response
- **THEN** the current utterance stops
- **AND** the selected response becomes the only active utterance.

#### Scenario: Native TTS is unavailable

- **GIVEN** the device has no usable TTS engine, language, or voice data
- **WHEN** the user attempts to play a response
- **THEN** Chat reports a controlled playback error
- **AND** the app remains usable
- **AND** the app does not automatically launch an installation flow.

#### Scenario: Leave Chat during playback

- **GIVEN** an assistant response is speaking or language identification is in
  progress
- **WHEN** Chat leaves composition or its speech owner is destroyed
- **THEN** speech stops
- **AND** native TTS and language-identification resources are released.

#### Scenario: Return to Chat after playback disposal

- **GIVEN** the previous Chat speech owner was released
- **WHEN** the user returns to Chat
- **THEN** completed assistant responses are prepared again
- **AND** a fresh native TTS instance can initialize for playback.

### Requirement: Mathematical Chat notation

The Chat SHALL render supported LaTeX-delimited mathematical notation in model
responses locally and in a form that is visually distinct from literal TeX
source.

#### Scenario: Inline mathematical notation

- **WHEN** assistant Markdown contains a complete expression delimited by
  `$...$` or `\(...\)`
- **THEN** the Chat renders the expression inline with the surrounding content
- **AND** retains the surrounding Markdown text and styling

#### Scenario: Display mathematical notation

- **WHEN** assistant Markdown contains a complete expression delimited by
  `$$...$$` or `\[...\]`
- **THEN** the Chat renders the expression as a separate display formula
- **AND** the formula remains readable within the message width

#### Scenario: Incomplete or invalid mathematical notation

- **WHEN** a response contains an unclosed delimiter or an expression that the
  renderer cannot parse
- **THEN** the Chat preserves the original source as readable text
- **AND** generation and message rendering continue without failure

#### Scenario: Non-mathematical dollar sign

- **WHEN** response text uses a dollar sign as currency or escapes a delimiter
- **THEN** the Chat preserves it as ordinary text

#### Scenario: Local formula rendering

- **WHEN** mathematical notation is rendered
- **THEN** rendering does not require network access or a hosted service

### Requirement: Selectable Chat message text

The Chat SHALL expose message text through Android's native text-selection
interaction.

#### Scenario: Select part of a historical message

- **WHEN** the user long-presses selectable text in a user or assistant message
- **THEN** Android displays native text-selection handles and contextual actions
- **AND** the user can adjust the selection to part of the message

#### Scenario: Copy selected message text

- **WHEN** the user invokes the platform Copy action for selected message text
- **THEN** Android places the selected plain text on the clipboard

#### Scenario: Select formatted and reasoning text

- **WHEN** a message presents Markdown blocks or visible reasoning text
- **THEN** its textual content participates in native selection
- **AND** reasoning, attachments, and final text retain their vertical order without overlap

#### Scenario: Select text on a colored message background

- **WHEN** the user selects text inside a colored message bubble
- **THEN** the selection handles and highlight contrast with that bubble

#### Scenario: Render mathematics inside reasoning

- **WHEN** visible reasoning contains a rendered mathematical formula
- **THEN** the formula uses the reasoning container's content color
- **AND** remains legible against the reasoning background

#### Scenario: Use a message action

- **WHEN** a message also presents an action such as text-to-speech playback
- **THEN** the action remains independently operable outside the selection boundary

### Requirement: Application Settings Destination

The app SHALL provide a dedicated Settings destination for application-level
preferences and SHALL organize those preferences into named sections so more
settings can be added without changing the top-level navigation model.

#### Scenario: Open application settings

- **GIVEN** the user is on Home
- **WHEN** the user opens Settings
- **THEN** the app displays the Settings destination
- **AND** application appearance options are grouped under Appearance.

#### Scenario: Return from application settings

- **GIVEN** the user is viewing Settings
- **WHEN** the user navigates back
- **THEN** the app returns to Home.

### Requirement: Application Theme Preference

The app SHALL let the user choose System, Light, or Dark appearance behavior,
apply the choice to the entire application immediately, and persist the choice
locally across application restarts. System SHALL resolve to the current Android
system appearance. Missing or unrecognized stored values SHALL resolve to
System.

#### Scenario: Select an explicit theme

- **WHEN** the user selects Light or Dark in Settings
- **THEN** the whole application immediately uses the corresponding appearance
- **AND** the selection is restored on a later application launch.

#### Scenario: Follow system appearance

- **WHEN** the user selects System in Settings
- **THEN** the application uses the Android system light or dark appearance
- **AND** follows later system appearance changes.

#### Scenario: Retain dynamic color behavior

- **GIVEN** Material dynamic colors are available on the device
- **WHEN** a theme preference resolves to light or dark
- **THEN** the application uses the corresponding dynamic color scheme.

### Requirement: Compact Chat controls during generation

Chat SHALL replace its message composer and auxiliary draft controls with a
compact cancellation action while a response is being generated. If generation
is cancelled or fails, Chat SHALL restore the composer with the submitted draft
content. Successful completion SHALL continue to clear the submitted draft.

#### Scenario: Read a response while it streams

- **WHEN** the model is generating a response
- **THEN** the message field, send action, attachments, and attachment actions
  are not displayed
- **AND** a cancel-generation action remains available.

#### Scenario: Cancel generation

- **GIVEN** the model is generating a response
- **WHEN** the user cancels generation
- **THEN** the message composer is displayed again
- **AND** it contains the submitted draft content.

#### Scenario: Generation fails

- **GIVEN** the model is generating a response
- **WHEN** loading or generation fails
- **THEN** the message composer is displayed again
- **AND** it contains the submitted draft content
- **AND** the failure remains visible.

### Requirement: Portrait-only application orientation

The app SHALL present its current phone experience in portrait orientation and
SHALL NOT switch the launcher activity to landscape when the device rotates.

#### Scenario: Rotate the device while using ArarAI

- **GIVEN** ArarAI is visible in portrait orientation
- **WHEN** the user rotates the device to a landscape position
- **THEN** the application remains in portrait orientation.

### Requirement: Background model download

The app SHALL run active model downloads as user-visible foreground data
transfers so they can continue after the application UI moves to the background.
The current transfer state SHALL remain observable when the activity is
recreated.

#### Scenario: Continue a download in background

- **WHEN** a model download is active and the user leaves ArarAI
- **THEN** the transfer continues under a foreground service
- **AND** a system notification reports the model and download progress.

#### Scenario: Cancel from notification

- **GIVEN** a model download notification is visible
- **WHEN** the user selects Cancel
- **THEN** the transfer stops
- **AND** the model returns to its applicable non-downloading state.

#### Scenario: Cancel from model management

- **GIVEN** a model download is active
- **WHEN** the user selects Cancel in the model-management screen
- **THEN** the stream copy stops without waiting for the remote response to
  finish
- **AND** the foreground notification is removed after cancellation completes.

#### Scenario: Open download from notification

- **GIVEN** a model download notification is visible
- **WHEN** the user taps the notification
- **THEN** ArarAI opens or returns to the model-management screen.

#### Scenario: Open the notification repeatedly

- **GIVEN** ArarAI already has an application task in the background
- **WHEN** the user taps the download notification one or more times
- **THEN** the existing task opens the model-management screen
- **AND** no duplicate application activity is added to the Back stack.

#### Scenario: Report sustained progress

- **WHEN** an active model download reports new byte progress
- **THEN** the notification periodically reflects the latest known percentage
- **AND** updates are paced to avoid overwhelming Android's notification system.

### Requirement: Resumable partial model transfer

The app SHALL preserve partial model bytes after cancellation or transient
failure and SHALL request the remaining HTTP range on a later attempt. It SHALL
append only when the server confirms the requested range and SHALL otherwise
restart the temporary file from zero. Integrity validation and atomic promotion
SHALL remain required before a model becomes available.

#### Scenario: Server accepts resume

- **GIVEN** a valid partial model file exists
- **WHEN** the server accepts a request beginning at the partial byte count
- **THEN** the app appends the remaining bytes
- **AND** validates and atomically promotes the completed model.

#### Scenario: Server ignores resume

- **GIVEN** a partial model file exists
- **WHEN** the server returns the complete artifact instead of the requested
  range
- **THEN** the app truncates the partial file before writing
- **AND** does not duplicate bytes.

### Requirement: Background download notification permission

On Android versions with runtime notification permission, the app SHALL request
permission in the context of an active model download. Permission denial SHALL
NOT be represented as a guarantee that Android can run the transfer invisibly
or indefinitely.

#### Scenario: Notification permission is not granted

- **WHEN** a model download becomes active on a version requiring runtime
  notification permission
- **THEN** the app requests that permission
- **AND** denial does not crash or immediately fail the model transfer.

### Requirement: Source-safe fallback download resume

The app SHALL NOT combine unverified partial bytes from one model download URL
with bytes from a different URL. A resumed transfer that fails integrity SHALL
receive at most one clean retry from byte zero on the same URL before the app
advances to another configured source.

#### Scenario: Change to a fallback source

- **GIVEN** a partial model was obtained from one configured URL
- **WHEN** the downloader advances to a different fallback URL
- **THEN** it does not append the fallback response to unverified prior-source bytes.

#### Scenario: Retry incompatible resumed content cleanly

- **GIVEN** a server accepted a resume offset
- **WHEN** the completed temporary artifact fails configured integrity validation
- **THEN** the app deletes the incompatible temporary content
- **AND** retries that URL once from byte zero.

#### Scenario: Bound clean retries

- **WHEN** the clean retry also fails
- **THEN** the app advances or reports terminal failure without an unbounded loop.

### Requirement: Bounded Chat streaming presentation

The app SHALL preserve generated token order while bounding how frequently
growing assistant content rebuilds structural UI state and expensive presentation
work. Terminal events SHALL flush all buffered content immediately.

#### Scenario: Receive a burst of tokens

- **WHEN** the local engine emits multiple tokens within one presentation interval
- **THEN** the app coalesces them into fewer display-state updates
- **AND** preserves their exact order and content.

#### Scenario: Complete or cancel between presentation updates

- **WHEN** generation completes, fails, or is cancelled while display content is buffered
- **THEN** the app flushes the latest content before publishing the terminal state
- **AND** persists the same final partial or complete response.

#### Scenario: Render unchanged streamed content

- **WHEN** unrelated Chat state changes without changing displayed assistant text
- **THEN** the app avoids reparsing that Markdown solely because of the unrelated state change.

### Requirement: Efficient persisted media reference lookup

The Chat persistence boundary SHALL enumerate referenced media without issuing
one message query per session or materializing complete Chat histories. Reference
updates SHALL remain transactionally consistent with message mutations.

#### Scenario: Enumerate references across many sessions

- **GIVEN** persisted image and audio messages across multiple sessions
- **WHEN** media cleanup requests all referenced URIs
- **THEN** SQLite returns the references through a bounded query path independent of session count
- **AND** does not construct full message histories.

#### Scenario: Mutate a message with media

- **WHEN** a message containing media is inserted, updated, or deleted
- **THEN** its persisted reference state changes in the same transaction
- **AND** cleanup cannot observe a committed message without its references.

### Requirement: Bounded complete Chat media reconciliation

The app SHALL make every unreferenced app-owned Chat media file eligible for
eventual reconciliation even when referenced files sort before it. The configured
limit SHALL bound cleanup candidates, not permanently shield later orphan files.

#### Scenario: Referenced files exceed the cleanup limit

- **GIVEN** more referenced files than the reconciliation limit sort before an orphan
- **WHEN** startup media reconciliation runs
- **THEN** the orphan remains eligible for cleanup
- **AND** referenced media is preserved.

#### Scenario: Reject media outside app ownership

- **WHEN** reconciliation encounters a content URI or path outside the Chat media directory
- **THEN** it does not delete that resource.

### Requirement: Kotlin formatting and static-analysis gate

The shared automated quality gate SHALL run pinned, check-only Kotlin formatting
and static-analysis tasks in addition to tests, Android lint, builds, and strict
OpenSpec validation.

#### Scenario: Kotlin source violates an enforced rule

- **WHEN** Kotlin source violates configured formatting or static-analysis policy
- **THEN** the shared local and CI quality gate fails with an actionable diagnostic.

### Requirement: Reproducible native CI caching

CI SHALL reuse compatible Android/native toolchain and fetched-source inputs with
cache keys that invalidate when their pinned versions or defining build inputs change.

#### Scenario: Native build inputs remain compatible

- **WHEN** a CI run restores a cache produced by matching tool and CMake inputs
- **THEN** it reuses those inputs and still performs the required build validation.

#### Scenario: Native build inputs change

- **WHEN** a pinned tool version or defining CMake input changes
- **THEN** CI does not treat incompatible cached native inputs as current.

### Requirement: Automated critical Compose journey coverage

The project SHALL automatically verify critical user journeys across Home,
Chat, Models, and Settings using deterministic local fakes and stable UI semantics.

#### Scenario: Navigate to Chat and submit

- **GIVEN** a deterministic available local model
- **WHEN** the test navigates from Home to Chat and submits a prompt
- **THEN** it observes generation controls and the streamed result through UI semantics.

#### Scenario: Recover from unavailable model state

- **GIVEN** the selected model reports a retryable failure
- **WHEN** the test opens model management and selects Retry
- **THEN** it verifies the retry command and corresponding UI transition.

#### Scenario: Manage sessions and appearance

- **WHEN** tests rename or delete a Chat session and change appearance in Settings
- **THEN** they verify the resulting visible state without relying on pixel coordinates.

### Requirement: Automated foreground download lifecycle coverage

The project SHALL automatically verify the service ownership and state
transitions that keep foreground model downloads reliable across Android
lifecycle events.

#### Scenario: Redeliver a download command

- **GIVEN** the service receives a redelivered download intent
- **WHEN** it reattaches to application-scoped download state
- **THEN** automated coverage verifies that the transfer remains owned exactly once
- **AND** completion stops the service after no owned transfers remain.

#### Scenario: Destroy a service with owned work

- **GIVEN** the service owns one or more active transfers
- **WHEN** the service is destroyed
- **THEN** automated coverage verifies cancellation for each owned transfer
- **AND** verifies cleanup of observation and ownership state.

#### Scenario: Receive an empty start intent

- **WHEN** Android invokes the service without a valid model command
- **THEN** automated coverage verifies controlled non-sticky behavior without a crash.

### Requirement: Durable Chat Audio Transcripts

The Chat domain SHALL persist the original app-owned audio together with its
transcript, transcription status and sanitized diagnostic metadata while
remaining compatible with audio messages created before transcription support.

#### Scenario: Persist a new audio turn

- **WHEN** the user submits recorded audio and local transcription is available
- **THEN** Chat persists the audio message with pending transcription
- **AND** updates that same message to completed or failed
- **AND** keeps the original audio replayable.

#### Scenario: Reopen a completed transcript

- **GIVEN** an audio message has a completed transcript
- **WHEN** its session is reopened
- **THEN** the transcript, status and sanitized diagnostics are restored
- **AND** the completed transcript can participate in prompt context and
  automatic session titles.

#### Scenario: Read legacy audio

- **GIVEN** a persisted audio message predates transcription support
- **WHEN** its session is loaded
- **THEN** the audio remains readable and playable
- **AND** it is not transcribed retroactively
- **AND** no fabricated placeholder enters prompt context.

### Requirement: Capability-Routed Chat Audio

Chat SHALL route new recorded audio according to the selected LLM capability
and local Whisper-model availability without duplicating transcription logic in
the UI.

#### Scenario: Direct audio with deferred enrichment

- **GIVEN** the selected LLM accepts audio
- **WHEN** the user submits a recording
- **THEN** Chat sends the original audio to the LLM without waiting for Whisper
- **AND** persists the assistant response normally
- **AND** completes transcript enrichment when coordinated native resources are
  available.

#### Scenario: Text-only model receives transcription

- **GIVEN** the selected LLM accepts text but not audio
- **AND** the configured Whisper model is locally available
- **WHEN** the user submits a recording
- **THEN** Chat transcribes before generation
- **AND** sends the completed transcript as the current text prompt.

#### Scenario: Required Whisper model is missing

- **GIVEN** the selected LLM does not accept audio
- **AND** the configured Whisper model is unavailable
- **WHEN** Chat presents recorded-audio capability
- **THEN** it does not offer an unusable recording action
- **AND** provides an actionable path to transcription-model setup.

#### Scenario: Deferred enrichment is unavailable

- **GIVEN** the selected LLM accepts direct audio
- **AND** the configured Whisper model is unavailable
- **WHEN** the user submits a recording
- **THEN** direct-audio generation remains available
- **AND** Chat makes clear that no reconstructible transcript will be produced.

#### Scenario: Required transcription fails

- **GIVEN** a text-only LLM is selected
- **WHEN** Whisper transcription fails or is canceled
- **THEN** LLM generation does not begin
- **AND** the audio message retains its failed or canceled state.

### Requirement: Audio Transcript Presentation Preference

Chat SHALL persist a presentation preference controlling completed transcript
visibility without changing recognition, persistence or context behavior.

#### Scenario: Hide a completed transcript

- **GIVEN** an audio message has a completed transcript
- **AND** transcript visibility is disabled
- **WHEN** the message is rendered
- **THEN** audio playback remains available
- **AND** transcript text is hidden
- **AND** its transcript and diagnostics remain persisted and usable.

#### Scenario: Inspect transcription diagnostics

- **GIVEN** an audio message has available success or failure diagnostics
- **WHEN** the user opens transcription details
- **THEN** Chat displays and allows copying the sanitized report
- **AND** does not expose recognized text inside that technical report.

### Requirement: Shared Conversation Coordination

Normal Chat and Voice Chat SHALL use one screen-neutral coordinator for canonical
turn persistence, context projection, local generation, cancellation, and
successful assistant-response commit.

#### Scenario: Commit one complete exchange

- **GIVEN** either Chat screen submits a user turn
- **WHEN** local generation completes successfully
- **THEN** the coordinator persists the user turn and one complete assistant turn
  in the same canonical conversation
- **AND** both screens observe the committed exchange
- **AND** presentation-specific code does not build a separate canonical prompt
  or assistant message.

#### Scenario: Reject a duplicate operation

- **GIVEN** a user turn already has a stable operation identity
- **WHEN** navigation, retry, lifecycle recreation, or a stale callback attempts
  to submit or commit that operation again
- **THEN** the coordinator does not duplicate the user or assistant message
- **AND** does not invoke a second concurrent generation for that operation.

#### Scenario: Backend lacks incremental sessions

- **GIVEN** the selected runtime does not support native incremental
  conversational state
- **WHEN** either Chat screen submits a turn
- **THEN** the coordinator sends the eligible bounded reconstructed prompt
- **AND** conversation persistence and cross-screen continuity remain available.

### Requirement: Reconstructible Cross-Modality Turns

The canonical conversation projection SHALL represent text and recorded-audio
turns consistently while preserving original media and capability-aware
generation routing.

#### Scenario: Rehydrate a transcribed audio turn

- **GIVEN** a persisted audio message has a completed transcript
- **WHEN** context is reconstructed after native state loss
- **THEN** the transcript represents that user turn in textual history
- **AND** the original audio remains persisted and replayable.

#### Scenario: Do not fabricate unreconstructible audio

- **GIVEN** a persisted direct-audio turn has no completed transcript
- **WHEN** a later runtime requires textual context reconstruction
- **THEN** the coordinator does not invent placeholder content
- **AND** exposes a controlled reconstruction limitation
- **AND** preserves the original message in visible history.

### Requirement: Workload-Organized Model Management

The app SHALL separate configured local models into Chat and Transcription
tabs. The Chat tab SHALL contain configured Chat/LLM models, including
models that do not expose optional reasoning controls, and the Transcription tab
SHALL contain models that support the transcription task.

#### Scenario: Browse workload tabs

- **GIVEN** the catalog contains Chat and transcription models
- **WHEN** the user opens Model Management
- **THEN** the app initially displays the Chat tab
- **AND** only Chat/LLM models appear in Chat
- **AND** only transcription-task models appear in Transcription.

### Requirement: Family-Preserving Model Weight Order

The configured catalog SHALL declare stable model-family identity and Model
Management SHALL keep entries from the same family contiguous while ordering
families and variants from lighter to heavier expected artifacts. Entries with
unknown expected size SHALL sort after entries with known size.

#### Scenario: Order related variants

- **GIVEN** a workload contains multiple families and multiple variants of one
  family
- **WHEN** Model Management presents that workload
- **THEN** all variants in the same family are adjacent
- **AND** variants inside the family are ordered by expected artifact bytes
  ascending
- **AND** families are ordered by their lightest member's expected artifact
  bytes ascending.

### Requirement: Available-Memory Model Recommendation

Model Management SHALL compare currently available device memory with each
model's declared recommended free RAM and SHALL identify models whose
requirement fits as recommended without blocking other models.

#### Scenario: Present models that fit available memory

- **GIVEN** the device reports currently available memory
- **AND** a model declares recommended free RAM no greater than that value
- **WHEN** the model card is displayed
- **THEN** the card identifies the model as recommended
- **AND** the screen explains the available-memory basis for the indication.

#### Scenario: Retain a model that does not fit

- **GIVEN** a model's declared recommended free RAM exceeds currently available
  memory
- **WHEN** the model card is displayed
- **THEN** the model remains visible with its normal lifecycle actions
- **AND** the app does not identify it as recommended.

### Requirement: Legacy GGUF Artifact Cleanup

The app SHALL remove only the known app-managed artifacts and partial downloads
for chat models removed from the checked-in catalog.

#### Scenario: Clean former managed GGUF downloads

- **GIVEN** a former app-managed GGUF model or its partial download exists
- **WHEN** the post-upgrade model-storage migration runs
- **THEN** the known legacy file is deleted
- **AND** current Gemma and Whisper artifacts remain unchanged.

#### Scenario: Preserve unknown files

- **GIVEN** an unknown file exists in app-owned model storage
- **WHEN** the post-upgrade model-storage migration runs
- **THEN** the unknown file is not deleted.

### Requirement: Supported Model Runtime Metadata

The app SHALL accept only LiteRT-LM chat bundles and Whisper transcription
artifacts in checked-in model runtime metadata.

#### Scenario: Parse supported runtime metadata

- **GIVEN** a catalog entry declares LiteRT-LM or Whisper runtime metadata
- **WHEN** the app parses and resolves the entry
- **THEN** it records the runtime, artifact format, and acceleration policy.

#### Scenario: Reject removed chat runtimes

- **GIVEN** a catalog entry declares llama.cpp or GGUF
- **WHEN** the app parses the entry
- **THEN** catalog validation fails with a controlled unsupported-value error.

### Requirement: Gemma-Only Chat Catalog

The checked-in chat catalog SHALL contain Gemma 4 E2B and E4B LiteRT-LM bundles
and SHALL use Gemma 4 E2B as its default model.

#### Scenario: Download Gemma LiteRT-LM artifact

- **GIVEN** a Gemma LiteRT-LM catalog entry is selected
- **AND** its configured `.litertlm` file is missing
- **WHEN** the download flow starts
- **THEN** the app downloads the configured `.litertlm` artifact
- **AND** validates size and SHA-256 before making it available.

#### Scenario: Present Gemma choices

- **WHEN** the user views configured chat models
- **THEN** Gemma 4 E2B and E4B are available
- **AND** no GGUF chat model is shown
- **AND** their LiteRT-LM runtime and acceleration policy are shown.

### Requirement: Instructions and Tools Management

The app SHALL provide an `Assistant configuration` destination for maintaining
mode-specific user instructions, locally persisted tool enablement, and
per-model conversational generation settings.

#### Scenario: Open Assistant configuration

- **GIVEN** the user is on Home
- **WHEN** the user opens `Assistant configuration`
- **THEN** the action appears immediately above Settings
- **AND** the screen provides `Instructions`, `Tools`, and `Generation` tabs.

#### Scenario: Edit instructions independently

- **WHEN** the user edits and saves the normal-Chat or Voice-Chat instruction
- **THEN** the app enforces the documented size limit
- **AND** persists the accepted text locally
- **AND** applies it only to future turns from that interaction mode
- **AND** does not modify already completed messages.

#### Scenario: Review Wikipedia networking

- **GIVEN** Wikipedia is not enabled
- **WHEN** the user reviews the tool
- **THEN** the screen explains that eligible queries and result retrieval use an
  external Wikipedia/MediaWiki service
- **AND** explains that inference and conversation storage remain local
- **AND** no Wikipedia request occurs before enablement.

#### Scenario: Selected model cannot use the enabled tool

- **GIVEN** the Wikipedia preference is enabled
- **AND** the selected model lacks verified Wikipedia tool capability
- **WHEN** the tools screen or a conversation is active
- **THEN** the app reports that Wikipedia is unavailable for the current model
- **AND** does not advertise a hidden tool to that model
- **AND** normal local generation remains available.

### Requirement: Verified Gemma Wikipedia Tool Capability

The checked-in catalog SHALL advertise Wikipedia tool capability only for
individually validated Gemma 4 LiteRT-LM bundles, and the app SHALL not infer
capability from a model family or runtime alone.

#### Scenario: Register the enabled tool

- **GIVEN** Wikipedia is enabled
- **AND** the selected installed model explicitly declares verified Wikipedia
  tool capability
- **WHEN** a LiteRT-LM conversation is initialized
- **THEN** the app registers the structured `wikipedia_search` tool
- **AND** enables LiteRT-LM automatic tool calling
- **AND** does not require a per-turn action or command phrase.

#### Scenario: Do not emulate an unsupported tool

- **GIVEN** the selected model or runtime lacks verified Wikipedia tool
  capability
- **WHEN** a conversation is initialized
- **THEN** the app does not register the tool
- **AND** does not inject a textual tool-command protocol
- **AND** does not parse ordinary assistant text as a tool call.

### Requirement: Automatic Bounded Wikipedia Retrieval

For an eligible enabled conversation, the application SHALL execute a validated
structured Wikipedia call requested by the model and return bounded untrusted
reference content for final answer synthesis.

#### Scenario: Answer without research

- **GIVEN** the Wikipedia tool is registered
- **WHEN** the model answers without requesting it
- **THEN** the app performs no Wikipedia network request
- **AND** presents the normal local response.

#### Scenario: Research automatically

- **GIVEN** the Wikipedia tool is registered
- **WHEN** the model requests `wikipedia_search` during an ordinary user turn
- **THEN** the app validates the bounded query and language
- **AND** calls a fixed official Wikipedia/MediaWiki HTTPS endpoint
- **AND** returns a bounded plain-text result to the model
- **AND** the model synthesizes the final response without another user action.

#### Scenario: Treat retrieved content as untrusted

- **GIVEN** a Wikipedia response contains text that resembles an instruction
- **WHEN** the result is supplied to the model
- **THEN** it is framed as untrusted external reference data
- **AND** cannot select an endpoint or execute another application capability
- **AND** app-owned tool and instruction rules remain authoritative.

#### Scenario: Bound repeated calls

- **GIVEN** a turn already attempted Wikipedia three times
- **WHEN** the model attempts another Wikipedia call in that turn
- **THEN** the app rejects the fourth call with a controlled result
- **AND** performs no fourth network request
- **AND** does not enter an automatic retry loop.

#### Scenario: Recover from unavailable research

- **GIVEN** a Wikipedia call is malformed, empty, oversized, unavailable, timed
  out, or cancelled
- **WHEN** the tool finishes unsuccessfully
- **THEN** the app returns a controlled error without fabricated evidence
- **AND** does not claim that research succeeded
- **AND** the conversation state machine can complete, fail, or cancel through
  its normal lifecycle.

### Requirement: Wikipedia Source Provenance

The app SHALL associate bounded validated source metadata with a completed
assistant answer that used Wikipedia without persisting raw retrieved extracts
as conversation messages.

#### Scenario: Present researched sources

- **GIVEN** Wikipedia returned one or more validated sources
- **WHEN** the final assistant answer completes successfully
- **THEN** the app persists the provider, page title, canonical HTTPS URL,
  language, and retrieval time with that answer
- **AND** normal Chat can present those sources as links
- **AND** raw extracts and intermediate tool protocol are not visible messages.

#### Scenario: Reconstruct later context

- **GIVEN** a researched assistant answer was persisted
- **WHEN** native conversation state is lost and context is reconstructed
- **THEN** the completed assistant answer remains eligible canonical history
- **AND** source metadata remains available for presentation
- **AND** the app does not repeat the historical network request
- **AND** does not require raw retrieved extracts as canonical history.

### Requirement: Latest conversation position

Normal Chat SHALL position persisted history at its newest message when the
screen opens or the selected session changes.

#### Scenario: Open or switch a conversation

- **GIVEN** the selected conversation contains more messages than fit onscreen
- **WHEN** the user opens normal Chat or selects another session
- **THEN** the list positions itself at the end
- **AND** the newest messages are visible without manual scrolling.

### Requirement: Per-Model Conversational Generation Configuration

The app SHALL resolve locally persisted total-context and temperature overrides
independently for each configured Chat model and SHALL fall back to current
catalog defaults when an override is absent.

#### Scenario: Configure one model

- **GIVEN** one Chat model is selected
- **WHEN** the user saves a valid context window or temperature
- **THEN** the override is associated with that stable model ID
- **AND** applies to future normal-Chat and Voice-Chat turns using that model
- **AND** does not change another model's effective settings.

#### Scenario: Return to a previously configured model

- **GIVEN** two models have different saved generation settings
- **WHEN** the user switches away from one model and later selects it again
- **THEN** the app restores that model's saved effective values.

#### Scenario: Restore catalog defaults

- **GIVEN** the selected model has one or more generation overrides
- **WHEN** the user restores defaults
- **THEN** the app removes those overrides
- **AND** resolves the selected model's current catalog values
- **AND** leaves other models' overrides unchanged.

#### Scenario: Reject invalid manual values

- **WHEN** the user enters a non-positive context window or a non-finite or
  negative temperature
- **THEN** the app reports an inline validation error
- **AND** does not persist or apply the invalid value.

### Requirement: Truthful LiteRT-LM Generation Controls

The app SHALL apply each exposed conversational generation setting to its real
LiteRT-LM control and SHALL not present an unsupported independent response
token limit as configurable or enforced.

#### Scenario: Apply total context capacity

- **GIVEN** a valid effective context window is resolved for the selected model
- **WHEN** LiteRT-LM is initialized for a conversational workload
- **THEN** `EngineConfig.maxNumTokens` receives that total input-plus-output
  capacity
- **AND** context projection uses the same effective total capacity.

#### Scenario: Apply sampling temperature

- **GIVEN** a valid effective temperature is resolved for the selected model
- **WHEN** a normal-Chat or Voice-Chat conversation is configured
- **THEN** `SamplerConfig.temperature` receives that value.

#### Scenario: Change a load-bound setting

- **GIVEN** LiteRT-LM retains engine or conversation state
- **WHEN** a future turn resolves an incompatible effective context or sampling
  configuration
- **THEN** the app closes all incompatible native state
- **AND** initializes the requested effective configuration before generation
- **AND** preserves canonical conversation history.

#### Scenario: Review response-limit semantics

- **WHEN** the user views Generation configuration
- **THEN** the app explains that the current runtime controls response stopping
  rather than exposing an independent output-token setting
- **AND** explains that reasoning and final answer share total capacity
- **AND** does not label a projection reserve as a maximum response-token
  limit.

### Requirement: Generation Configuration Experience

The Generation tab SHALL expose effective conversational generation values,
supported controls, model capability, and runtime-backed last-turn diagnostics.

#### Scenario: Review effective configuration

- **GIVEN** a Chat model is selected
- **WHEN** the Generation tab is shown
- **THEN** it identifies the selected model
- **AND** shows the effective total context window and numeric temperature
- **AND** reports whether the model supports reasoning
- **AND** offers restoration of catalog defaults.

#### Scenario: Select a temperature profile

- **WHEN** the user selects `Precise`, `Balanced`, or `Creative`
- **THEN** the app resolves the centralized numeric value for that profile
- **AND** persists it for the selected model
- **AND** displays the effective numeric value.

#### Scenario: Enter a manual temperature

- **WHEN** the user chooses manual temperature and saves a valid value
- **THEN** the exact accepted value is persisted for the selected model
- **AND** used by future conversational turns.

#### Scenario: Show available last-turn metrics

- **GIVEN** the most recent conversational turn produced runtime-backed metrics
- **WHEN** the Generation tab is shown
- **THEN** it may show prefill tokens and throughput, decode tokens and
  throughput, and time to first token
- **AND** does not persist those measurements as Chat messages.

#### Scenario: Metrics are unavailable

- **GIVEN** the runtime did not supply a trustworthy measurement
- **WHEN** the Generation tab is shown
- **THEN** the corresponding metric is reported as unavailable
- **AND** the app does not estimate callback chunks as tokens.

### Requirement: Isolated Benchmark Generation Configuration

The model benchmark SHALL keep fixed benchmark-owned generation parameters and
measurements independent from conversational generation overrides.

#### Scenario: Run benchmark after changing conversational settings

- **GIVEN** the selected model has conversational context or temperature
  overrides
- **WHEN** its benchmark runs
- **THEN** the benchmark uses its documented fixed parameters
- **AND** its metrics describe only that benchmark run
- **AND** it neither reads nor overwrites the conversational last-turn metrics.

### Requirement: Durable Incomplete Assistant Response

The app SHALL distinguish a terminal assistant generation that contains
reasoning but no usable final answer from a complete answer, cancellation, and
failure.

#### Scenario: Generation ends after reasoning only

- **GIVEN** the runtime emits non-blank reasoning
- **AND** emits no usable final answer text
- **WHEN** the generation reaches its terminal completion callback
- **THEN** the assistant message is marked incomplete
- **AND** partial reasoning is preserved
- **AND** the turn is not presented as an ordinary successful ellipsis.

#### Scenario: Present an incomplete response

- **GIVEN** an incomplete assistant message exists
- **WHEN** normal Chat renders it
- **THEN** Chat shows an `Incomplete response` indication and controlled
  explanation
- **AND** shows partial reasoning only when `Show reasoning` is enabled
- **AND** does not guess, repair, or silently replace generated facts.

#### Scenario: Reconstruct legacy and incomplete messages

- **GIVEN** persisted history contains legacy assistant messages without a
  completion-status field and newer incomplete messages
- **WHEN** the conversation is reconstructed after process death
- **THEN** legacy messages default to complete
- **AND** incomplete status and partial reasoning remain available
- **AND** eligible completed history remains unchanged.

### Requirement: Best-Available Final Answer Guidance

The app-owned invariant generation instruction SHALL guide the model to produce
the best available final answer after exhausting tools and to review modern
calendar years before finalizing.

#### Scenario: Reach the tool-call ceiling

- **GIVEN** a turn has used every permitted tool invocation
- **WHEN** the model continues the generation
- **THEN** the invariant instruction directs it to synthesize the best answer
  from available material without another tool request.

#### Scenario: Review a modern calendar year

- **WHEN** the model prepares a final answer containing a modern calendar year
- **THEN** the invariant instruction directs it to verify complete four-digit
  representation
- **AND** application code does not silently rewrite the generated year.

### Requirement: User-Credentialed Experimental Web Providers

The application SHALL present Tavily and Exa as separate disabled-by-default
experimental providers in the Tools UI. Configuring a provider SHALL require a
user-supplied token, provider-specific network/privacy disclosure, and a
successful direct smoke test before that provider can be enabled.

#### Scenario: Review an unconfigured provider

- **GIVEN** no token is stored for a web-search provider
- **WHEN** the user reviews that provider in Tools
- **THEN** the application reports that it is disabled and unconfigured
- **AND** no provider request occurs.

#### Scenario: Save and verify a user token

- **WHEN** the user accepts the disclosure, enters a token, and requests
  verification
- **THEN** the application calls only the selected provider's fixed official
  HTTPS endpoint
- **AND** enables the provider only after a valid bounded smoke-test response
- **AND** reports authentication, quota, rate-limit, network, timeout, and
  malformed-response failures without exposing the token.

#### Scenario: Configure without enabling

- **GIVEN** a valid token has been stored
- **WHEN** the user leaves the provider disabled
- **THEN** ordinary Chat and Voice Chat SHALL make no request to that provider.

#### Scenario: Enable two general web providers

- **GIVEN** Tavily and Exa are both configured
- **WHEN** the user enables both
- **THEN** Exa SHALL execute first and Tavily SHALL be the fallback in a new
  compatible conversation
- **AND** the model SHALL NOT receive two vendor-specific web-search tools.

#### Scenario: Remove a provider token

- **GIVEN** a provider is configured or enabled
- **WHEN** the user removes its token
- **THEN** the application disables that provider before another turn can use it
- **AND** deletes the stored credential
- **AND** invalidates retained native conversation state that advertised web
  search through that provider.

### Requirement: Private Provider Credential Handling

The application SHALL treat user-supplied provider tokens as secrets stored in
app-private Android credential storage. Tokens MUST NOT be included in model
context, conversation history, saved UI state, source metadata, logs,
diagnostics, analytics, crash text, backups, or exports.

#### Scenario: Restore provider configuration UI

- **GIVEN** a provider token is already stored
- **WHEN** the Tools screen is recreated
- **THEN** it reports that a credential is configured
- **AND** it does not display or repopulate the full token.

#### Scenario: Produce diagnostics after an authenticated request

- **WHEN** a provider request succeeds or fails
- **THEN** diagnostics contain only redacted provider, timing, size, status
  class, and controlled failure information
- **AND** contain neither the token nor an authorization-header value.

#### Scenario: Back up or export application data

- **GIVEN** one or more provider tokens are stored
- **WHEN** Android backup or an ArarAI data export is produced
- **THEN** provider tokens SHALL be excluded.

### Requirement: Provider-Neutral Focused Web Search

The application SHALL expose one stable structured `web_search` contract backed
by enabled providers in deterministic Exa-then-Tavily order. The contract SHALL accept only
a bounded query, language, and research focus and SHALL return a
provider-neutral untrusted evidence envelope plus validated source metadata.

#### Scenario: Register the enabled provider chain

- **GIVEN** the user enabled one or more providers
- **AND** the active model/runtime explicitly supports experimental web search
- **WHEN** a new native conversation is created
- **THEN** the application registers the stable `web_search` schema
- **AND** binds the ordered provider chain behind the application-domain knowledge
  boundary
- **AND** does not expose its token or vendor response schema to the model.

#### Scenario: Enable Exa after Tavily

- **GIVEN** a retained conversation advertises web search through Tavily
- **WHEN** the user enables Exa while keeping Tavily enabled
- **THEN** the retained native conversation is incompatible
- **AND** the next turn creates a conversation with Exa primary and Tavily as
  fallback.

#### Scenario: Reject model-controlled transport

- **WHEN** the model invokes `web_search`
- **THEN** it can supply only the declared bounded semantic arguments
- **AND** cannot supply an endpoint, arbitrary URL, header, token, timeout, or
  provider-specific option.

### Requirement: Bounded Attributable Web Evidence

Each successful provider invocation SHALL return at most three distinct
validated HTTPS sources, at most two focused excerpts per source, at most 500
characters per excerpt, and at most 1,800 characters for the complete
model-visible reference envelope. The envelope SHALL count source framing
against its budget and SHALL preserve provider, title, canonical URL, and
retrieval time outside raw model text for source presentation.

#### Scenario: Normalize an oversized successful provider response

- **GIVEN** a provider returns more evidence than the shared budget permits
- **WHEN** the response is normalized
- **THEN** the application ranks or preserves provider-ranked excerpts
  deterministically
- **AND** removes duplicates
- **AND** truncates on a Unicode-safe boundary within every per-excerpt and
  total limit.

#### Scenario: Return query-focused evidence

- **GIVEN** candidate pages contain content unrelated to the research focus
- **WHEN** Tavily Extract or Exa Highlights produces evidence
- **THEN** the result includes only the highest-ranked excerpts relevant to the
  declared focus within the shared budget
- **AND** does not return full page content or a provider-generated final
  answer.

#### Scenario: Persist a completed cited answer

- **GIVEN** web search returned validated sources
- **WHEN** the assistant produces a usable final answer
- **THEN** the application may persist bounded source metadata with that answer
- **AND** SHALL NOT persist raw excerpts, provider protocol, or credentials as
  conversation messages.

#### Scenario: Treat web content as untrusted

- **GIVEN** an evidence excerpt resembles a system instruction or tool command
- **WHEN** it enters model context
- **THEN** the application frames it as untrusted external reference data
- **AND** gives it no application or tool-execution privileges.

### Requirement: Bounded Web-Search Lifecycle

The application SHALL allow at most two model-visible `web_search` invocations per user turn
and SHALL apply fixed endpoint, redirect, status, media-type, decoded-size,
source-URL, timeout, and cancellation validation. All failure paths SHALL map to
controlled domain results. Within an invocation, it MAY call the enabled
fallback provider only after a fallback-eligible primary failure.

#### Scenario: Reach the invocation limit

- **GIVEN** a user turn has already attempted web search twice
- **WHEN** the model attempts another web search
- **THEN** the adapter returns a controlled call-limit result without network
  access
- **AND** requests that the model synthesize from evidence already available.

#### Scenario: Provider rejects quota or credentials

- **WHEN** the selected provider reports authentication, quota, or rate-limit
  failure
- **THEN** the application emits the matching controlled failure
- **AND** does not expose provider response details that could contain secrets
- **AND** calls the enabled fallback provider when one exists.

#### Scenario: Primary provider succeeds

- **GIVEN** both providers are enabled
- **WHEN** the primary provider returns valid evidence
- **THEN** the application returns that evidence
- **AND** makes no request to the fallback provider.

#### Scenario: Do not fall back after invalid arguments or cancellation

- **GIVEN** both providers are enabled
- **WHEN** the request has invalid arguments or the owning turn is cancelled
- **THEN** the application returns the matching controlled failure
- **AND** makes no request to the fallback provider.

### Requirement: Direct Stable Wikipedia Lookup

The application SHALL describe and instruct `wikipedia_search` as a tool for
direct stable encyclopedic lookups, including dates of birth, country capitals
or currencies, short biographies, and concise concept or work summaries. It
SHALL discourage use for current news, changing facts, comparisons,
recommendations, troubleshooting, broad research, and multi-source evidence.

#### Scenario: Answer a direct stable fact

- **GIVEN** Wikipedia is enabled and compatible
- **WHEN** the user requests a stable encyclopedic fact or concise summary
- **THEN** the model-visible instruction permits `wikipedia_search`.

#### Scenario: Answer a current or comparative question

- **GIVEN** Wikipedia and web search are enabled
- **WHEN** the question requires current, comparative, technical,
  recommendation, or multi-source evidence
- **THEN** the model-visible instruction directs that work away from Wikipedia
- **AND** reserves Wikipedia for a separate direct stable lookup if needed.

#### Scenario: Cancel a search turn

- **WHEN** the owning generation is cancelled
- **THEN** in-flight provider work is cancelled
- **AND** partial evidence is not persisted
- **AND** the native conversation is not reused as a completed compatible turn.

#### Scenario: Search finishes without a final answer

- **GIVEN** one or more provider calls completed
- **WHEN** LiteRT-LM terminates with reasoning but no usable final response
- **THEN** the application records and presents an explicit incomplete response
- **AND** does not present tool evidence as the assistant's final answer.

### Requirement: Paired Tavily and Exa Evaluation

The repository SHALL contain a repeatable bilingual provider comparison that
uses the same model bundle, system prompt, generation parameters, public tool
schema, question, invocation budget, evidence budget, and run count for paired
Tavily and Exa runs. The evaluation SHALL define absolute approval thresholds
before results are used to enable either provider for ordinary conversations.

#### Scenario: Execute a paired question

- **WHEN** the same corpus question is evaluated against Tavily and Exa
- **THEN** each provider runs in a fresh native conversation
- **AND** Wikipedia and the competing web provider are disabled
- **AND** all non-provider test parameters are identical and recorded.

#### Scenario: Record comparison evidence

- **WHEN** a comparison run completes
- **THEN** the harness records provider outcome, source count, evidence
  characters and estimated tokens, provider/tool latency, model latency,
  available reasoning/output metrics, final-answer completion, attribution,
  answer score, and estimated provider cost
- **AND** does not record provider credentials or authorization headers.

#### Scenario: Decide provider approval

- **WHEN** all required paired runs and scoring are complete
- **THEN** a checked-in report records configurations, aggregate measurements,
  material qualitative failures, and an explicit verdict
- **AND** no provider is approved solely because it outperformed the other
  without satisfying the predefined absolute thresholds.

#### Scenario: Keep unapproved providers gated

- **GIVEN** a provider has not received an approving verdict
- **WHEN** a user starts an ordinary Chat or Voice Chat turn
- **THEN** the checked-in model catalog or experimental gate SHALL NOT advertise
  that provider's web-search capability.

### Requirement: Chat settings persist immediately

The Chat settings dialog SHALL apply every supported change immediately. It SHALL
provide a Close action that only dismisses the dialog and a Reset action that
restores and applies all Chat defaults without dismissing the dialog.

#### Scenario: Change a Chat setting

- **WHEN** the user changes a supported Chat setting
- **THEN** the new value is applied without a separate save action

#### Scenario: Reset Chat settings

- **WHEN** the user activates Reset
- **THEN** all Chat settings return to their defaults immediately
- **AND** the settings dialog remains open

#### Scenario: Close Chat settings

- **WHEN** the user activates Close
- **THEN** the dialog closes without changing the current settings

### Requirement: Application language selection

The application SHALL expose a language selection in General Settings, SHALL
persist the selection locally, and SHALL apply it when creating the application
UI. The initial choices SHALL be the device language, English, and Brazilian
Portuguese. Unknown or missing stored values SHALL fall back to the device
language. The application MAY require a restart before a changed selection is
applied.

#### Scenario: User selects a language

- **WHEN** the user chooses a supported application language
- **THEN** the application persists that choice locally
- **AND** General Settings immediately marks the new choice as selected
- **AND** the application presents a restart-required notice with an action to
  recreate the application UI
- **AND** the next application start presents the interface in that language

#### Scenario: Stored language is unavailable

- **WHEN** no language has been selected or the stored value is unknown
- **THEN** the application follows the device language
- **AND** Android's default resources provide the fallback for unsupported
  device locales

### Requirement: Root back navigation exit confirmation

When Android back navigation is invoked from the application Home destination,
the application SHALL ask the user to confirm closing the application task. The
dialog SHALL allow the user to persist a "do not ask again" choice. Android Home
navigation SHALL retain its platform behavior and only move the application to
the background.

#### Scenario: User confirms exit

- **WHEN** the user invokes Android back navigation from application Home
- **THEN** a localized exit confirmation is presented
- **AND** confirming finishes and removes the application task

#### Scenario: User declines exit

- **WHEN** the exit confirmation is visible
- **AND** the user cancels or dismisses it
- **THEN** the application remains on its Home destination

#### Scenario: User disables future confirmations

- **WHEN** the user confirms exit with "do not ask again" selected
- **THEN** that preference is persisted locally
- **AND** later Android back navigation from application Home closes the task
  without presenting the confirmation

### Requirement: Complete localized interface

All user-visible interface copy, including navigation, settings, Chat, Voice
Chat, model management, diagnostics, dialogs, errors, notifications, empty
states, and accessibility descriptions, SHALL resolve from localized Android
resources for every explicitly supported application language.

#### Scenario: Interface is presented in a selected language

- **WHEN** the application starts with English or Brazilian Portuguese selected
- **THEN** every user-visible interface string is presented in that language
- **AND** user content, model output, model names, and technical identifiers are
  not translated

### Requirement: Bounded Multimodal Follow-up Context

Chat SHALL keep the most recent historical image set available to a subsequent
textual follow-up while avoiding unbounded historical media replay.

#### Scenario: Textual follow-up refers to the recent image

- **GIVEN** Chat history contains a user turn with image attachments
- **WHEN** the user submits a later text prompt without a new image
- **THEN** the request includes the attachments from the most recent image turn
- **AND** retains bounded textual conversation history.

#### Scenario: Current images replace historical image context

- **GIVEN** Chat history contains image attachments
- **WHEN** the user submits a prompt with new image attachments
- **THEN** the request includes only the newly submitted images
- **AND** does not mix them with historical images.
