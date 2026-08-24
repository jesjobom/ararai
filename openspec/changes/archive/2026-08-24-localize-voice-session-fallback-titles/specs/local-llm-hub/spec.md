## ADDED Requirements

### Requirement: Voice fallback session titles follow the configured app language

When local transcription cannot provide the first-turn title, the application
SHALL create the dated audio-message or Voice Chat fallback title using the
configured application language and the device's local time zone. The selected
language SHALL apply to both the descriptive text and the appended date and time.

#### Scenario: Audio message without local transcription starts text Chat

- **WHEN** an audio message without an available local transcription starts a
  new text-Chat session
- **THEN** the fallback session title is formatted in the configured application
  language

#### Scenario: Direct-audio turn starts Voice Chat

- **WHEN** a direct-audio turn starts a new Voice Chat session without a local
  transcript
- **THEN** the fallback session title is formatted in the configured application
  language
