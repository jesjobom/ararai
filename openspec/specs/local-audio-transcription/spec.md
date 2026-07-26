# local-audio-transcription Specification

## Purpose
TBD - created by archiving change adopt-whisper-cpp-transcription. Update Purpose after archive.
## Requirements
### Requirement: Managed Local Whisper Runtime

The app SHALL transcribe app-owned recorded audio locally through a pinned
whisper.cpp runtime behind a replaceable transcriber boundary and SHALL NOT use
hosted transcription or automatic Android speech-recognizer fallback.

#### Scenario: Transcribe a complete recording

- **GIVEN** the configured Whisper model is locally available and valid
- **WHEN** the app transcribes a supported app-owned PCM WAV
- **THEN** whisper.cpp processes the complete recording on the device
- **AND** returns one normalized final transcript
- **AND** no audio or transcript is sent to a remote service.

#### Scenario: Runtime is replaceable

- **WHEN** Chat requests transcription
- **THEN** Chat depends on the transcriber boundary rather than JNI or
  whisper.cpp-specific types
- **AND** fake implementations remain available for automated routing tests.

#### Scenario: Empty final output

- **WHEN** whisper.cpp completes without non-blank recognized text
- **THEN** the transcriber returns a typed empty-result failure
- **AND** does not persist a completed transcript.

### Requirement: Validated ASR Model Lifecycle

The app SHALL manage the configured Whisper model as an app-owned,
integrity-validated `UTILITY` artifact in the unified Model Manager. It SHALL
not be embedded in the APK or represented as a selectable Chat LLM.

#### Scenario: Show Whisper in the unified manager

- **WHEN** the Model Manager displays the configured Whisper artifact
- **THEN** it shows typed `Utility` and `Audio transcription` metadata
- **AND** offers the same download, progress, retry, update and deletion
  lifecycle used for other managed local artifacts
- **AND** does not offer a Chat selection action.

#### Scenario: Choose a transcription model

- **WHEN** the Model Manager is opened
- **THEN** it presents multilingual quantized `base` and `small` models as
  transcription utilities with their recommended free RAM
- **AND** allows either or both to be downloaded and removed independently
- **AND** Chat prefers `base` when both are available and otherwise uses the
  available model.

#### Scenario: Test an installed candidate

- **GIVEN** an experimental transcription model is valid and installed
- **WHEN** the user chooses to test it with an app-owned recording
- **THEN** the app transcribes the complete recording using that exact model
- **AND** reports model, quantization, load time, transcription time, real-time
  factor, threads, memory observations and recognized text for local comparison
- **AND** the report is shared only through an explicit user copy action.

#### Scenario: Query models for a feature

- **WHEN** Chat, benchmark or transcription requests compatible models
- **THEN** the catalog filters artifacts by typed purpose and task
- **AND** runtime-specific loaders receive only compatible artifact metadata.

#### Scenario: Present missing model

- **GIVEN** the configured Whisper artifact is missing or invalid
- **WHEN** transcription capability is evaluated
- **THEN** the app reports transcription setup as required
- **AND** presents its approximate download size
- **AND** does not load the invalid artifact.

#### Scenario: Explicitly download the model

- **GIVEN** the Whisper model is missing
- **WHEN** the user explicitly starts its download
- **THEN** the app downloads into a temporary app-owned file
- **AND** reports progress and recoverable failure
- **AND** does not require a hosted application backend.

#### Scenario: Promote a valid model

- **GIVEN** the temporary model matches configured byte size and SHA-256
- **WHEN** download validation completes
- **THEN** the app atomically promotes it to the configured final path
- **AND** transcription becomes available.

#### Scenario: Reject an invalid model

- **GIVEN** a downloaded model fails configured integrity validation
- **WHEN** validation completes
- **THEN** the final model path remains unavailable
- **AND** the user can retry without retaining the invalid artifact as usable.

#### Scenario: Delete the transcription model

- **GIVEN** no transcription is active
- **WHEN** the user deletes the local Whisper model
- **THEN** the app removes only that configured ASR artifact
- **AND** existing persisted audio and transcripts remain intact.

### Requirement: Native Runtime Isolation

The app SHALL isolate whisper.cpp from the LLM native runtime.

#### Scenario: Load both native runtimes

- **GIVEN** the APK contains llama.cpp and whisper.cpp runtime libraries
- **WHEN** the process loads both libraries
- **THEN** their pinned GGML build targets and symbols do not collide
- **AND** the app remains able to invoke each runtime through its own boundary.

#### Scenario: Native failure

- **WHEN** model loading or native decoding fails
- **THEN** the app reports a typed recoverable transcription failure
- **AND** does not crash the process.

### Requirement: Capability-Aware Chat Routing

The app SHALL use local Whisper transcripts for text-only Chat models and SHALL
preserve direct recorded-audio input for models that support audio.

#### Scenario: Audio-capable model receives direct audio

- **GIVEN** a selected LLM accepts direct audio
- **WHEN** the user submits recorded audio
- **THEN** direct-audio generation can run without waiting for Whisper
- **AND** Whisper enriches the persisted message asynchronously.

#### Scenario: Text-only model waits for transcription

- **GIVEN** the selected LLM accepts text but not audio
- **WHEN** the user submits recorded audio
- **THEN** Whisper completes first
- **AND** the final transcript becomes the current text prompt.

### Requirement: Sanitized Whisper Diagnostics

The app SHALL persist copyable success or failure diagnostics sufficient to
measure Whisper execution without including audio samples, recognized text,
app secrets or stable user identifiers.

#### Scenario: Inspect successful transcription

- **WHEN** Whisper completes a transcript
- **THEN** diagnostics identify the runtime revision, configured model ID,
  audio duration, execution duration, real-time factor and thread count
- **AND** the user can copy the report from the audio message.

#### Scenario: Inspect failed transcription

- **WHEN** Whisper transcription fails
- **THEN** diagnostics include a stable failure category and sanitized native
  timing/model metadata
- **AND** exclude audio and recognized content.

### Requirement: Physical Whisper Acceptance Gate

The default Whisper configuration SHALL be selected using a repeatable
physical-device comparison before Android speech recognition is removed from
production wiring.

#### Scenario: Compare candidate models

- **GIVEN** representative pt-BR recordings and the target Samsung device
- **WHEN** candidate multilingual quantized models are evaluated
- **THEN** the report compares artifact size, load time, transcription time,
  real-time factor, peak process memory, output quality and cancellation
- **AND** records the rationale for the checked-in default.

#### Scenario: Validate coexistence with local LLMs

- **WHEN** the selected Whisper candidate is tested with each supported native
  LLM runtime loaded in the same APK
- **THEN** transcription and subsequent generation complete without symbol
  collision, process crash or uncontrolled concurrent native work.

### Requirement: Per-Model Transcription Benchmark Access

Model Management SHALL expose benchmark access on each locally available
transcription model and SHALL run the transcription diagnostic with the exact
model chosen.

#### Scenario: Benchmark a downloaded transcription model

- **GIVEN** a configured transcription model is valid and installed
- **WHEN** the user chooses its benchmark action in the Transcription tab
- **THEN** the app opens the transcription benchmark for that exact model
- **AND** back navigation returns to Model Management.

#### Scenario: Hide benchmark for unavailable transcription model

- **GIVEN** a configured transcription model is missing, downloading, invalid,
  or failed
- **WHEN** its card is displayed
- **THEN** the card does not expose benchmark access
- **AND** retains the appropriate download, cancel, or retry action.

#### Scenario: Use the production transcription thread default

- **GIVEN** normal app transcription uses six CPU threads
- **WHEN** the user opens a transcription model benchmark
- **THEN** the benchmark initializes its selectable thread count to six
- **AND** the user can still select another offered thread count for an
  explicit comparison.
