## MODIFIED Requirements

### Requirement: Contextual half-duplex voice loop

Voice Chat SHALL process turns through the shared persisted conversation using
its Voice-Chat-specific effective instruction, keep microphone capture inactive
during model processing, tool execution, and TTS playback, and resume the loop
without a per-turn tool confirmation.

#### Scenario: Process one contextual turn

- **GIVEN** a valid audio turn has completed
- **WHEN** Voice Chat submits it through the shared conversation coordinator
- **THEN** the user turn is persisted in the current conversation
- **AND** generation uses the Voice-Chat-specific effective instruction
- **AND** an audio-capable model may receive direct audio while transcription
  enriches the persisted turn
- **AND** a text-only model receives the completed local transcript
- **AND** compatible native conversation state receives only the new user turn
- **AND** otherwise bounded persisted history initializes generation context
- **AND** a successful assistant response is persisted in the same conversation.

#### Scenario: Research during a voice turn

- **GIVEN** Wikipedia is enabled and registered for the selected model
- **WHEN** the model requests Wikipedia while processing a voice turn
- **THEN** Voice Chat enters a bounded research state without asking for a
  per-turn confirmation or command phrase
- **AND** microphone capture remains inactive
- **AND** intermediate tool protocol and raw results are not spoken
- **AND** the final synthesized response is queued for speech
- **AND** validated sources remain available in the shared conversation.

#### Scenario: Recover the voice loop after research failure

- **GIVEN** a voice turn requested Wikipedia
- **WHEN** the request fails, times out, or is cancelled
- **THEN** Voice Chat does not announce that research succeeded
- **AND** does not speak internal diagnostics or protocol content
- **AND** the turn follows its normal controlled completion or cancellation path
- **AND** the loop can return to listening when the exchange finishes.

#### Scenario: Resume listening after one exchange

- **GIVEN** current generation, eligible tool execution, and every queued speech
  segment have completed
- **AND** the loop has not been stopped
- **WHEN** the contextual exchange finishes
- **THEN** non-canonical temporary capture and tool-result resources are deleted
- **AND** canonical conversation media, messages, and bounded source metadata
  remain persisted
- **AND** a fresh recording starts
- **AND** Voice Chat returns to listening with accumulated conversation context.
