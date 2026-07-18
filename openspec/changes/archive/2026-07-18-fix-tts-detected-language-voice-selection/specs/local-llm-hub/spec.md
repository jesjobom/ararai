## MODIFIED Requirements

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
