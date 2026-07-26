## MODIFIED Requirements

### Requirement: Persistent Chat Sessions

The app SHALL persist conversations and their messages locally on the device
and SHALL make the same canonical conversation history available to normal Chat
and Voice Chat.

#### Scenario: Resume session after restart

- **GIVEN** a conversation contains user and assistant messages created through
  normal Chat, Voice Chat, or both
- **WHEN** the app is restarted
- **THEN** the conversation is available in Chat
- **AND** its messages, media, completed transcripts, and completion states are
  restored
- **AND** Voice Chat can continue that same conversation.

#### Scenario: Manage sessions

- **WHEN** the user uses the session controls in normal Chat or idle Voice Chat
- **THEN** the user can create a new conversation
- **AND** switch between conversations
- **AND** rename the current conversation
- **AND** delete a conversation without deleting other conversations
- **AND** the current conversation selection applies when Voice Chat opens.

#### Scenario: Preserve screen origin without splitting history

- **WHEN** a turn is persisted from normal Chat or Voice Chat
- **THEN** it may record its originating interaction mode
- **AND** origin does not create a separate session or context
- **AND** both screens observe one ordered canonical history.

### Requirement: Configured Chat System Prompt

The checked-in app configuration SHALL define a short conversation system prompt
used by the shared coordinator when initializing local generation context for
normal Chat and Voice Chat.

#### Scenario: Build prompt with configured system prompt

- **GIVEN** the configured system prompt is present
- **AND** a persisted conversation has previous eligible messages
- **WHEN** either Chat screen submits a new turn that requires context
  initialization
- **THEN** generation receives the system prompt
- **AND** recent eligible conversation history
- **AND** the new user message
- **AND** a compatible incremental native continuation does not prefill that
  unchanged history again.

### Requirement: Simple Context Window Management

The shared conversation coordinator SHALL project recent eligible persisted
history using a context budget based on the selected model's configured context
size, independently of whether the new turn originates in normal Chat or Voice
Chat.

#### Scenario: Long session prompt construction

- **GIVEN** persisted history is longer than the selected model's context budget
- **WHEN** a new turn requires a reconstructed prompt or native-session
  rehydration
- **THEN** the coordinator includes the newest eligible messages that fit the
  budget
- **AND** omits older messages
- **AND** does not perform automatic summarization yet.

#### Scenario: Compatible incremental continuation

- **GIVEN** the runtime retains compatible native conversation state for the
  same canonical history
- **WHEN** either Chat screen submits the next turn
- **THEN** context budgeting verifies compatibility
- **AND** the runtime receives only the new user content
- **AND** unchanged persisted history is not sent again.

### Requirement: Safe LiteRT-LM Conversation Reuse

The app SHALL treat LiteRT-LM conversation state as an ephemeral execution cache,
reuse it only for a verified compatible continuation from either Chat screen,
and prevent context from crossing persisted conversation or configuration
boundaries.

#### Scenario: Continue a compatible chat session

- **GIVEN** a LiteRT-LM generation completed successfully for a persisted
  conversation
- **AND** the next request identifies the same conversation
- **AND** its eligible history exactly matches the transcript retained by the
  runtime
- **AND** model, modality profile, sampler settings, reasoning mode, and runtime
  generation are unchanged
- **WHEN** the user submits the next message through normal Chat or Voice Chat
- **THEN** the app reuses the existing LiteRT-LM conversation
- **AND** sends only the new user content instead of prefilling the complete
  transcript again
- **AND** screen origin does not make the continuation incompatible.

#### Scenario: Rehydrate missing native state

- **GIVEN** a persisted conversation has eligible history
- **AND** no compatible native conversation is live because the app or runtime
  was recreated or previous state was evicted
- **WHEN** either Chat screen submits the next message
- **THEN** the app creates a fresh native conversation
- **AND** initializes it once from the eligible system instruction and bounded
  persisted history
- **AND** processes the new message as the current incremental turn.

#### Scenario: Start a fresh incompatible conversation

- **GIVEN** there is retained LiteRT-LM conversation state
- **WHEN** the next request belongs to another persisted conversation or has
  incompatible transcript, model, modality profile, sampler settings, reasoning
  mode, or runtime generation
- **THEN** the app closes the retained conversation
- **AND** creates a new conversation initialized from the request's eligible
  system instruction and bounded history
- **AND** persisted history remains unchanged.

#### Scenario: Invalidate partial or failed conversation state

- **GIVEN** a LiteRT-LM generation is cancelled, fails, or does not atomically
  commit a complete assistant response
- **WHEN** cleanup runs
- **THEN** the app closes and discards that native conversation
- **AND** a later request cannot reuse its partial state
- **AND** completed persisted conversation history remains recoverable.

#### Scenario: Keep benchmark runs isolated

- **WHEN** a LiteRT-LM benchmark run starts
- **THEN** it uses a fresh conversation without retained app-conversation context
- **AND** its metrics describe only that benchmark run.

## ADDED Requirements

### Requirement: Shared Conversation Coordination

Normal Chat and Voice Chat SHALL use one screen-neutral coordinator for canonical
turn persistence, context projection, local generation, cancellation, and
successful assistant-response commit.

#### Scenario: Commit one complete exchange

- **GIVEN** either Chat screen submits a user turn
- **WHEN** local generation completes successfully
- **THEN** the coordinator persists the user turn and one complete assistant turn
  in the same canonical conversation
- **AND** both screens observe the committed exchange
- **AND** presentation-specific code does not build a separate canonical prompt
  or assistant message.

#### Scenario: Reject a duplicate operation

- **GIVEN** a user turn already has a stable operation identity
- **WHEN** navigation, retry, lifecycle recreation, or a stale callback attempts
  to submit or commit that operation again
- **THEN** the coordinator does not duplicate the user or assistant message
- **AND** does not invoke a second concurrent generation for that operation.

#### Scenario: Backend lacks incremental sessions

- **GIVEN** the selected runtime does not support native incremental
  conversational state
- **WHEN** either Chat screen submits a turn
- **THEN** the coordinator sends the eligible bounded reconstructed prompt
- **AND** conversation persistence and cross-screen continuity remain available.

### Requirement: Reconstructible Cross-Modality Turns

The canonical conversation projection SHALL represent text and recorded-audio
turns consistently while preserving original media and capability-aware
generation routing.

#### Scenario: Rehydrate a transcribed audio turn

- **GIVEN** a persisted audio message has a completed transcript
- **WHEN** context is reconstructed after native state loss
- **THEN** the transcript represents that user turn in textual history
- **AND** the original audio remains persisted and replayable.

#### Scenario: Do not fabricate unreconstructible audio

- **GIVEN** a persisted direct-audio turn has no completed transcript
- **WHEN** a later runtime requires textual context reconstruction
- **THEN** the coordinator does not invent placeholder content
- **AND** exposes a controlled reconstruction limitation
- **AND** preserves the original message in visible history.
