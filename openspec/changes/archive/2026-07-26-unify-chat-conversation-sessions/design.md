## Context

Normal Chat owns the durable conversation store and already sends a
`PromptRequest` carrying persisted session identity and eligible history.
LiteRT-LM retains one compatible native `Conversation` and sends only new user
content on a verified continuation. Other requests reconstruct a bounded prompt.

Voice Chat version 0 deliberately bypasses this domain. It submits only the
current direct-audio turn, keeps response text in memory, deletes temporary WAV
files, and starts the next turn without history. Integrating the screens by
calling normal Chat UI logic from Voice Chat would couple presentation to
persistence and still leave two cancellation and commit paths.

Persisted history must survive process death and runtime replacement. Native
conversation/KV state is not durable and LiteRT-LM currently retains at most one
conversation. The design therefore separates canonical conversation state from
runtime acceleration.

## Goals / Non-Goals

**Goals:**

- Use one persisted conversation identity and canonical message model from both
  normal Chat and Voice Chat.
- Centralize turn persistence, context projection, generation, cancellation,
  and successful response commit behind a screen-neutral coordinator.
- Preserve distinct normal Chat and Voice Chat screens and interaction loops.
- Reuse a compatible runtime-native conversation incrementally when supported.
- Rehydrate native state from bounded persisted history only when required.
- Prevent cross-conversation context leaks, duplicate turns, and partial-state
  reuse.

**Non-Goals:**

- Persisting or serializing native KV cache across process death or model unload.
- Keeping multiple heavyweight native engines or conversations resident.
- Automatic conversation summarization or durable semantic memory.
- A user-facing action to clear model context without deleting visible history.
- Full-duplex Voice Chat, barge-in, background capture, or session-list redesign.
- Changing Whisper model selection or transcription quality.

## Decisions

### Make persisted conversations canonical

Both destinations address the same app-owned `conversationId`. Sessions,
messages, completed transcripts, and assistant responses in the database are the
recoverable source of truth. A message records a stable turn/message identity,
role, content kind, media/transcription state, completion state, and minimal
origin (`CHAT` or `VOICE_CHAT`) for diagnostics and presentation decisions.
Origin does not create a separate history or alter context semantics.

Legacy normal-Chat messages remain readable through the existing compatibility
path. The schema evolves only where a required field cannot be derived safely;
new fields use backward-compatible defaults.

Voice Chat selects the current persisted conversation when opened. If none
exists, it creates one through the same repository operation as normal Chat.
The selected conversation remains the app-level conversation selection so
moving between destinations continues the same history by default.

### Coordinate turns outside both screens

A screen-neutral conversation coordinator accepts a conversation identity and
structured user input, then owns:

1. idempotently appending or locating the user turn;
2. completing required transcription before generation;
3. projecting eligible persisted history under the selected model's context
   budget;
4. invoking the runtime through the shared inference owner;
5. streaming transient output to the active presenter;
6. atomically committing the completed assistant turn; and
7. invalidating incomplete runtime state after cancellation or failure.

Normal Chat and Voice Chat adapt their UI state machines to coordinator events.
Voice Chat retains its half-duplex listen/process/speak loop and segmented TTS;
normal Chat retains its message list and manual controls. Neither screen builds
its own canonical prompt or writes an assistant message independently.

A stable operation/turn ID makes retries and late callbacks idempotent. Reopening
another destination can observe committed messages, but an in-flight turn has
one foreground owner and is never submitted a second time.

### Project one logical user turn from text or audio

The canonical context projection is independent of which screen captured the
turn. Text input projects as text. Recorded audio persists the original media
and transcript state:

- a text-only model waits for Whisper and projects the completed transcript;
- an audio-capable model may receive direct audio while Whisper enriches the
  persisted message asynchronously;
- later rehydration uses a completed transcript because native audio/KV state is
  not durable;
- if a direct-audio turn has no reconstructible transcript, it remains visible
  but cannot be fabricated into recovered textual context.

This preserves current capability routing while allowing later continuation in
either destination.

### Model native sessions as a capability, not as persistence

The runtime boundary declares whether it supports incremental conversational
state. The coordinator supplies conversation identity, canonical eligible
history, current input, model/profile/configuration identity, and runtime
generation identity.

For a compatible live native session, the runtime verifies that its canonical
transcript matches persisted history and sends only the current user input. For
a missing session, process restart, engine/profile recreation, or conservative
invalidation, it creates a fresh native session, initializes it once from the
eligible system instruction and persisted history, and then processes the
current input incrementally.

For a backend without native incremental sessions, each request receives the
normal bounded reconstructed prompt. The application never treats native state
as the only copy of a conversation.

The effective compatibility key is:

`conversationId + modelId + workloadProfile + generationConfig + runtimeGeneration`

Canonical transcript equality remains an additional guard. Screen origin is
intentionally absent, allowing Chat and Voice Chat to reuse the same compatible
session.

### Invalidate conservatively and retain at most one native conversation

Changing conversation, model, workload profile, sampler/reasoning configuration,
or runtime generation closes incompatible native state. Cancellation, failed
generation, incomplete assistant output, transcript divergence, or engine
recreation also invalidates it. Persisted completed history remains untouched.

The first increment retains the existing single-native-conversation policy.
Switching away can evict the previous native session; returning rehydrates it
from bounded persisted history. A pool would increase RAM pressure and is not
required for cross-screen continuity.

### Commit only complete logical exchanges

The user message is persisted before inference so capture is not silently lost.
The assistant response becomes canonical context only after successful
generation completion and atomic persistence. Streamed drafts may be displayed
and checkpointed according to existing safety behavior, but cancellation or
failure marks them incomplete and prevents native-session reuse from assuming a
successful exchange.

Voice Chat starts TTS from streamed final-answer segments as today. Persistence
and TTS are separate consumers of the same generation events; TTS failure does
not erase a successfully committed assistant answer, while generation failure
does not commit a fabricated complete answer.

## Risks / Trade-offs

- **Cross-screen races duplicate a turn** → stable operation IDs, one foreground
  owner, idempotent append/commit operations, and stale-callback rejection.
- **Native state diverges from persisted history** → exact canonical transcript
  verification before incremental reuse and conservative rehydration.
- **Direct audio cannot be replayed into every runtime after restart** → retain
  original media, require a completed transcript for textual rehydration, and
  omit rather than fabricate unreconstructible content.
- **One retained native conversation makes session switching slower** → accept
  one-time bounded rehydration instead of retaining multiple RAM-heavy sessions.
- **Shared coordination could regress normal Chat** → preserve existing request
  semantics behind characterization tests before migrating Voice Chat.
- **TTS and persistence completion have different lifecycles** → model them as
  independent consumers and make canonical completion depend on generation plus
  persistence, not speech playback.

## Migration Plan

1. Add characterization tests for current Chat persistence, context projection,
   audio routing, and LiteRT-LM compatible continuation.
2. Introduce canonical turn and runtime-session capability contracts behind
   adapters for the existing database and engines.
3. Move normal Chat generation/persistence through the shared coordinator
   without changing product behavior.
4. Connect Voice Chat to persisted conversation selection, canonical turn
   creation, transcription, generation, and assistant commit.
5. Enable cross-screen continuation and native-session reuse without including
   screen origin in compatibility.
6. Validate restart rehydration, session/model/profile changes, legacy data,
   cancellation/failure, and duplicate-turn prevention.

Rollback can route normal Chat through its previous adapter and restore
stateless Voice Chat. New persisted messages remain compatible app-owned
conversation records; no native state rollback is required.

## Open Questions

- Whether Voice Chat should expose session selection directly in its first
  integrated UI or rely on the app-level current conversation plus normal Chat's
  existing session manager.
- Whether a future runtime can safely serialize native conversational state;
  this change intentionally assumes it cannot.
