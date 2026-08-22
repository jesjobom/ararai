## MODIFIED Requirements

### Requirement: Instructions and Tools Management

The app SHALL provide an `Assistant configuration` destination for maintaining
mode-specific user instructions, locally persisted enablement for external-
knowledge and local-compute tools, per-model conversational generation
settings, and global audio settings.

#### Scenario: Open Assistant configuration

- **GIVEN** the user is on Home
- **WHEN** the user opens `Assistant configuration`
- **THEN** the action appears immediately above Settings
- **AND** the screen provides `Instructions`, `Tools`, `Generation`, and `Audio` tabs
- **AND** the tab row scrolls horizontally when the labels do not fit
- **AND** each tab label remains on one line
- **AND** the first tab starts at the available leading edge without decorative padding
- **AND** a directional control appears only at each edge that has hidden tab content
- **AND** the Tools tab distinguishes external-network tools from local-compute tools.

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

## ADDED Requirements

### Requirement: Configurable Local Transcription Language

The app SHALL persist a global language preference for local Whisper
transcription independently from the application interface language, SHALL use
automatic detection by default, and SHALL apply the resolved choice to both
normal Chat and Voice Chat.

#### Scenario: Detect spoken language automatically

- **GIVEN** the transcription language is `Automatic`
- **WHEN** a new audio turn requires local transcription
- **THEN** the app asks Whisper to detect the spoken language.

#### Scenario: Follow system or interface language

- **GIVEN** the transcription language follows `System` or `Interface`
- **WHEN** a new audio turn requires local transcription
- **THEN** the app resolves the current corresponding locale at transcription time
- **AND** supplies its base language to Whisper.

#### Scenario: Use a fixed language

- **GIVEN** the user selects English or Portuguese
- **WHEN** a new audio turn requires local transcription
- **THEN** the app supplies the selected base language to Whisper.

#### Scenario: Preserve completed transcripts

- **GIVEN** one or more audio messages were already transcribed
- **WHEN** the user changes the transcription language
- **THEN** the new choice applies only to later transcriptions
- **AND** existing transcript text and diagnostics remain unchanged.
