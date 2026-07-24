## ADDED Requirements

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
