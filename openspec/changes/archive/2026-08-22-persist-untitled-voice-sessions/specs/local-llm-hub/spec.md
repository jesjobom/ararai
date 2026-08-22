## ADDED Requirements

### Requirement: Durable Voice Sessions Without Transcription

Regular Chat and Voice Chat SHALL persist and distinguish a conversation after
its first audio turn even when local transcription is unavailable, without
requiring a remote service or an additional model invocation solely for titling.

#### Scenario: Complete direct-audio turn without Whisper

- **GIVEN** the selected model accepts audio directly
- **AND** no usable local Whisper model is available
- **WHEN** Voice Chat persists the first captured turn
- **THEN** the session receives a localized timestamp-based Voice Chat title
- **AND** its messages are durably associated with that session.

#### Scenario: Send direct audio from regular Chat without Whisper

- **GIVEN** the selected model accepts audio directly
- **AND** no usable local Whisper model is available
- **WHEN** the first message is recorded and sent from regular Chat
- **THEN** the session receives a localized timestamp-based voice-message title
- **AND** its messages are durably associated with that session.

#### Scenario: Start another session after an untitled voice turn

- **GIVEN** a Voice Chat session was titled by the local fallback
- **WHEN** the user creates another session from normal Chat or idle Voice Chat
- **THEN** the new session has a distinct identity and empty visible history
- **AND** the prior session remains listed with its original messages.

#### Scenario: Prefer a local transcript when available

- **GIVEN** local Whisper transcription is available for the first voice turn
- **WHEN** transcription completes with non-blank text
- **THEN** the session title is derived from that transcript
- **AND** the timestamp fallback does not replace it.
