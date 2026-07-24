## MODIFIED Requirements

### Requirement: Structured Multimodal Messages

The chat domain SHALL represent user messages as structured prompt content,
including a durable transcript state for recorded audio turns.

#### Scenario: Direct audio with asynchronous transcription

- **GIVEN** the selected model supports audio input
- **AND** the device exposes the local audio transcriber
- **WHEN** the user submits recorded audio
- **THEN** the app persists the audio message with pending transcription
- **AND** sends the original audio to the local LLM without waiting
- **AND** updates the same message with the completed transcript or failure.

#### Scenario: Text-only model receives transcribed audio

- **GIVEN** the selected model does not support audio input but supports text
- **AND** the device exposes the local audio transcriber
- **WHEN** the user submits recorded audio
- **THEN** the app transcribes before generation
- **AND** persists the transcript with the audio message
- **AND** sends the transcript to the selected LLM as text.

#### Scenario: Required transcription fails

- **GIVEN** a text-only model is selected
- **WHEN** local transcription fails
- **THEN** generation does not start
- **AND** the persisted audio message exposes the failed status.

#### Scenario: Legacy audio remains readable

- **GIVEN** a persisted audio message predates transcription support
- **WHEN** the session is loaded
- **THEN** the audio remains playable
- **AND** it is not automatically transcribed
- **AND** no placeholder text is added to prompt context.

### Requirement: Capability-Gated Chat Controls

The Chat UI SHALL expose recorded-audio input when either the selected model
accepts direct audio or a local transcriber can convert audio for a text model.

#### Scenario: Text-only voice input is locally available

- **GIVEN** the selected model supports text but not audio
- **AND** Android exposes on-device file transcription
- **WHEN** the composer is displayed
- **THEN** the audio recording action is available.

#### Scenario: No valid audio route

- **GIVEN** the selected model does not support audio
- **AND** local file transcription is unavailable
- **WHEN** the composer is displayed
- **THEN** the audio recording action is unavailable.

## ADDED Requirements

### Requirement: Local Chat Audio Transcription

The app SHALL transcribe new Chat recordings locally through a replaceable
transcriber boundary and SHALL NOT fall back to hosted recognition.

#### Scenario: Android on-device recognizer availability

- **GIVEN** the device runs API 33 or newer
- **AND** Android reports an on-device speech recognizer
- **WHEN** Chat evaluates transcription capability
- **THEN** the app can feed the recorded PCM audio to that recognizer locally.

#### Scenario: Recorded PCM is streamed in real time

- **GIVEN** the Android on-device recognizer is available
- **WHEN** it reports readiness for speech
- **THEN** the app streams PCM in sample-rate-paced chunks
- **AND** closes the audio source after the recording duration
- **AND** uses a timeout derived from that duration plus processing margin.

#### Scenario: Fast segmented recognition succeeds

- **GIVEN** the on-device recognizer supports fast external-audio consumption
- **WHEN** a recording is transcribed
- **THEN** the app sends PCM without artificial real-time delay
- **AND** does not run a paced attempt.

#### Scenario: Fast recognition cannot decode speech

- **GIVEN** fast segmented recognition returns empty, no-match or timeout
- **WHEN** the first attempt fails
- **THEN** the app retries once with sample-rate-paced PCM
- **AND** does not retry unrelated availability, format, pipe or service errors
- **AND** retains both sanitized diagnostics if the retry also fails.

#### Scenario: Recognition formatting is requested

- **WHEN** the app creates the Android recognition intent
- **THEN** it requests quality-optimized formatting
- **AND** normalizes whitespace in returned segments
- **AND** does not synthesize punctuation or change recognized words itself.

#### Scenario: External audio contains multiple speech segments

- **GIVEN** the Android recognizer accepts an external audio source
- **WHEN** it returns one or more segmented results
- **THEN** the app concatenates non-empty segment transcripts in order
- **AND** completes transcription when the segmented session ends.

#### Scenario: Requested locale model is not installed

- **GIVEN** Android can check recognition support
- **AND** the requested locale is unsupported, pending or only downloadable
- **WHEN** local transcription starts
- **THEN** the app fails before streaming with an actionable local-model status
- **AND** does not trigger a model download automatically.

#### Scenario: Recognition support check is unavailable

- **GIVEN** the on-device service cannot perform a recognition support check
- **WHEN** local transcription starts
- **THEN** the app records the check failure in diagnostics
- **AND** still attempts on-device recognition.

#### Scenario: On-device recognizer unavailable

- **GIVEN** the API level or installed recognition service cannot satisfy local
  file transcription
- **WHEN** Chat evaluates transcription capability
- **THEN** the transcriber reports unavailable
- **AND** the app does not use a network recognition fallback.

### Requirement: Audio Transcript Presentation Preference

The app SHALL persist a Chat preference controlling transcript visibility,
enabled by default, without changing transcript processing or context.

#### Scenario: Hide completed transcript

- **GIVEN** an audio message has a completed transcript
- **AND** Show audio transcriptions is disabled
- **WHEN** the message is rendered
- **THEN** the audio remains visible and playable
- **AND** transcript text is hidden
- **AND** the transcript remains persisted and available to prompt context.

### Requirement: On-Device Transcription Diagnostics

The app SHALL retain a sanitized, copyable diagnostic report for successful
and failed local audio transcription without requiring logcat access.

#### Scenario: Inspect failed transcription

- **GIVEN** an audio message has failed transcription
- **AND** diagnostic metadata is available
- **WHEN** the user opens transcription details
- **THEN** the app shows the failure kind, recognizer lifecycle, WAV format,
  pipe byte-count, stream timing/speed ratio, locale support, segment count and
  device metadata
- **AND** allows the report to be copied
- **AND** excludes audio bytes and recognized speech from the report.

#### Scenario: Read failure persisted before diagnostics

- **GIVEN** a failed audio message has no diagnostic fields
- **WHEN** the session is loaded
- **THEN** its existing failure summary remains readable
- **AND** the details action is not shown.

#### Scenario: Inspect successful transcription

- **GIVEN** an audio message has a completed transcript
- **WHEN** the user opens transcription details
- **THEN** the app shows and allows copying the sanitized recognizer lifecycle,
  completion source, WAV, stream, locale, segment and device metadata
- **AND** excludes audio bytes and recognized speech from the report.

#### Scenario: Successful transcription may be partial

- **GIVEN** recognition returns non-empty text
- **AND** completion occurs outside the normal segmented-session end, before
  pipe completion, or before all WAV PCM bytes are sent
- **WHEN** the audio message is rendered
- **THEN** the app retains the transcript as completed
- **AND** warns that the transcription may be incomplete
- **AND** persists the diagnostic reason compatibly.
