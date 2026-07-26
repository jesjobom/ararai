## Why

Normal Chat already persists sessions and can reuse compatible LiteRT-LM native
conversation state, while Voice Chat version 0 is intentionally stateless and
uses a separate generation pipeline. This makes a conversation started by voice
invisible to normal Chat, prevents continuity across destinations, and risks
divergent persistence, context, and runtime-reuse behavior as both experiences
evolve.

ArarAI needs one conversation domain shared by both screens. Persisted sessions
and messages must remain the recoverable source of truth, while a runtime-native
conversation is used as an ephemeral execution cache when the selected backend
supports incremental continuation.

## What Changes

- Define one canonical persisted conversation and message model for normal Chat
  and Voice Chat, including text turns, recorded audio with transcript state,
  assistant responses, and the minimum origin metadata needed by each screen.
- Extract shared conversation coordination for persistence, context
  construction, generation, cancellation, and successful-turn commit. The two
  screens retain their distinct interaction and presentation behavior.
- Make Voice Chat select or create a persisted conversation, append every
  completed user/assistant exchange, and resume that conversation after
  navigation or process restart.
- Allow the same persisted conversation to move between normal Chat and Voice
  Chat without duplicating a turn, resubmitting the current message, or leaking
  context from another conversation.
- Generalize native conversation reuse by capability. A compatible live native
  session receives only the new user content; a missing or invalid session is
  rehydrated once from eligible persisted history; a backend without incremental
  session support receives a bounded reconstructed prompt.
- Keep runtime-native state ephemeral. Conversation/model/configuration changes,
  engine recreation, cancellation, failure, or transcript divergence invalidate
  it without deleting persisted history.

This change covers the first integration increment only. User-facing context
clearing, automatic conversation summaries, durable KV-cache serialization,
full-duplex voice interruption, and redesign of the session-management UI are
deferred.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `local-llm-hub`: Share persisted conversations and context coordination
  between normal Chat and Voice Chat, with capability-aware incremental native
  session reuse and safe rehydration.
- `voice-chat`: Replace stateless independent turns with a persistent,
  contextual half-duplex conversation that remains interoperable with normal
  Chat.

## Impact

- Conversation domain: canonical message representation, repository operations,
  turn identity/idempotency, and context projection.
- Runtime boundary: explicit incremental-session capability and lifecycle,
  transcript compatibility checks, rehydration, and fallback prompt execution.
- Voice Chat: session selection/creation, message persistence, transcript
  completion, response commit, recovery, and temporary-media ownership.
- Normal Chat: adoption of the shared coordinator without regressing existing
  session controls, audio routing, context limits, or LiteRT-LM reuse.
- Tests: migration/legacy messages, cross-screen continuation, restart and
  rehydration, model/session switching, cancellation/failure, and duplicate-turn
  prevention.
