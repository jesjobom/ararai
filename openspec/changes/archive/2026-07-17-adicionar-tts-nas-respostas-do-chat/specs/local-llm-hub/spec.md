## ADDED Requirements

### Requirement: Native speech playback for assistant responses

The Chat SHALL allow the user to play a completed assistant response through the
device's default Android text-to-speech engine without speaking reasoning
content.

#### Scenario: Play a completed assistant response

- **GIVEN** an assistant message has completed
- **AND** its response text is not blank
- **WHEN** the user activates its sound action
- **THEN** the app sends only the response text to the native TTS service
- **AND** does not send the message's reasoning content
- **AND** the sound action becomes a stop action for that message.

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
- **WHEN** the user activates the sound action on another eligible response
- **THEN** the current utterance stops
- **AND** the selected response becomes the only active utterance.

#### Scenario: Native TTS is unavailable

- **GIVEN** the device has no usable TTS engine, language, or voice data
- **WHEN** the user attempts to play a response
- **THEN** Chat reports a controlled playback error
- **AND** the app remains usable
- **AND** the app does not automatically launch an installation flow.

#### Scenario: Leave Chat during playback

- **GIVEN** an assistant response is currently speaking
- **WHEN** Chat leaves composition or its TTS owner is destroyed
- **THEN** speech stops
- **AND** the native TTS resources are released.

#### Scenario: Return to Chat after playback disposal

- **GIVEN** the previous Chat TTS instance was released
- **WHEN** the user returns to Chat and plays an eligible response
- **THEN** a fresh native TTS instance can initialize
- **AND** playback uses the device's configured default language and voice.

