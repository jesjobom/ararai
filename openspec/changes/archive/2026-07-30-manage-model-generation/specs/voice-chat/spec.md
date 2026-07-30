## MODIFIED Requirements

### Requirement: Contextual half-duplex voice loop

Voice Chat SHALL process turns through the shared persisted conversation using
its Voice-Chat-specific effective instruction and the selected model's
effective conversational generation settings, keep microphone capture inactive
during model processing, tool execution, and TTS playback, and recover without
speaking empty output when generation is incomplete.

#### Scenario: Start the voice loop

- **GIVEN** voice input can be routed to the selected locally available model
- **AND** Voice Chat is idle
- **WHEN** the user activates the central control
- **THEN** the app requests microphone permission if needed
- **AND** starts app-owned audio capture after permission is granted
- **AND** presents the listening state
- **AND** changes the central control to a stop action.

#### Scenario: Process one contextual turn

- **GIVEN** a valid audio turn has completed
- **WHEN** Voice Chat submits it through the shared conversation coordinator
- **THEN** the user turn is persisted in the current conversation
- **AND** generation uses the Voice-Chat-specific effective instruction
- **AND** generation uses the selected model's effective context window and
  temperature
- **AND** an audio-capable model may receive direct audio while transcription
  enriches the persisted turn
- **AND** a text-only model receives the completed local transcript
- **AND** compatible native conversation state receives only the new user turn
- **AND** otherwise bounded persisted history initializes generation context
- **AND** a successful assistant response is persisted in the same conversation.

#### Scenario: Title the first voice turn

- **GIVEN** the current conversation still has the default title
- **WHEN** its first audio message receives a completed transcript
- **THEN** the transcript supplies the automatic conversation title
- **AND** asynchronous assistant persistence does not prevent that title update.

#### Scenario: Preserve the spoken language

- **GIVEN** local Whisper transcription is used for a Voice Chat turn
- **WHEN** the user speaks a Whisper-supported language
- **THEN** transcription uses automatic language detection
- **AND** the transcript remains in the detected spoken language rather than
  being forced to Portuguese.

#### Scenario: Research during a voice turn

- **GIVEN** Wikipedia is enabled and registered for the selected model
- **WHEN** the model requests Wikipedia while processing a voice turn
- **THEN** Voice Chat enters a bounded research state without asking for a
  per-turn confirmation or command phrase
- **AND** microphone capture remains inactive
- **AND** intermediate tool protocol and raw results are not spoken
- **AND** the final synthesized response is queued for speech
- **AND** validated sources remain available in the shared conversation.

#### Scenario: Preserve voice reasoning for shared history

- **GIVEN** Voice Chat reasoning is enabled for a capable model
- **WHEN** the model emits reasoning and completes the assistant response
- **THEN** Voice Chat does not render or speak the reasoning
- **AND** persists it with the completed assistant message
- **AND** normal Chat displays it when `Show reasoning` is enabled.

#### Scenario: Recover the voice loop after research failure

- **GIVEN** a voice turn requested Wikipedia
- **WHEN** the request fails, times out, or is cancelled
- **THEN** Voice Chat does not announce that research succeeded
- **AND** does not speak internal diagnostics or protocol content
- **AND** the turn follows its normal controlled completion or cancellation path
- **AND** the loop can return to listening when the exchange finishes.

#### Scenario: Finish reasoning without a speakable answer

- **GIVEN** Voice Chat generation emits reasoning but no usable final answer
- **WHEN** the runtime reaches its terminal completion callback
- **THEN** Voice Chat persists an incomplete assistant message with partial
  reasoning in the shared conversation
- **AND** does not queue reasoning, empty text, protocol output, or an ellipsis
  for TTS
- **AND** presents a brief controlled incomplete-response notice
- **AND** the active loop returns to its next valid state.

#### Scenario: Review an incomplete voice response in normal Chat

- **GIVEN** Voice Chat persisted an incomplete assistant response
- **WHEN** the user opens the same session in normal Chat
- **THEN** normal Chat displays its incomplete status
- **AND** displays partial reasoning when `Show reasoning` is enabled
- **AND** does not duplicate the voice turn.

#### Scenario: Resume listening after one exchange

- **GIVEN** current generation, eligible tool execution, and every queued speech
  segment have completed or the response ended incomplete
- **AND** the loop has not been stopped
- **WHEN** the contextual exchange finishes
- **THEN** non-canonical temporary capture and tool-result resources are deleted
- **AND** canonical conversation media, messages, completion status, and bounded
  source metadata remain persisted
- **AND** a fresh recording starts
- **AND** Voice Chat returns to listening with accumulated conversation context.

#### Scenario: Continue the same conversation in normal Chat

- **GIVEN** Voice Chat committed one or more exchanges
- **WHEN** the user opens normal Chat
- **THEN** those exchanges appear in the current persisted conversation
- **AND** the next normal Chat turn continues the same eligible context
- **AND** a compatible live native session can be reused without resending its
  retained transcript.

#### Scenario: Continue a normal Chat conversation by voice

- **GIVEN** normal Chat committed one or more exchanges
- **WHEN** the user opens Voice Chat and submits a turn
- **THEN** Voice Chat uses the same current persisted conversation
- **AND** does not duplicate or resubmit the latest committed normal Chat turn.
