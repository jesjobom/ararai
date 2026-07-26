## 1. Characterize the existing pipelines

- [x] 1.1 Map normal Chat and Voice Chat input, persistence, context,
  generation, cancellation, TTS, and cleanup paths.
- [x] 1.2 Add characterization tests for normal Chat session persistence,
  bounded context projection, audio capability routing, and successful
  LiteRT-LM incremental continuation.
- [x] 1.3 Define the canonical text/audio/assistant turn model, completion state,
  stable operation identity, and minimal screen-origin metadata.
- [x] 1.4 Document backward-compatible decoding and migration rules for legacy
  sessions, text messages, audio messages, and transcript states.

## 2. Shared conversation coordination

- [x] 2.1 Introduce a screen-neutral coordinator for user-turn append,
  transcription readiness, context projection, generation, cancellation, and
  assistant commit.
- [x] 2.2 Move prompt/context construction out of screen-specific owners while
  preserving configured system instructions and per-model context budgets.
- [x] 2.3 Make user append and completed assistant commit idempotent by stable
  turn/operation identity.
- [x] 2.4 Route normal Chat through the coordinator without regressing session
  controls, streaming, media, titles, transcript presentation, or manual TTS.

## 3. Capability-aware native sessions

- [x] 3.1 Add an explicit runtime capability for incremental conversational
  state and preserve reconstructed-prompt fallback for unsupported backends.
- [x] 3.2 Generalize native-session compatibility to conversation, model,
  workload profile, generation configuration, runtime generation, and exact
  canonical transcript equality.
- [x] 3.3 Reuse a compatible live native session by sending only new user
  content, independent of whether Chat or Voice Chat owns the screen.
- [x] 3.4 Rehydrate a fresh native session once from eligible persisted history
  after process/runtime recreation or conservative invalidation.
- [x] 3.5 Invalidate native state on conversation/model/profile/configuration
  changes, cancellation, failure, incomplete output, or transcript divergence.
- [x] 3.6 Add tests proving that the second compatible turn does not resend
  history and that unsupported runtimes continue receiving bounded reconstructed
  prompts.

## 4. Persisted Voice Chat conversations

- [x] 4.1 Resolve or create the app-level current conversation when Voice Chat
  opens and restore it after navigation or restart.
- [x] 4.2 Persist each Voice Chat user audio turn with transcript state and
  route text-only versus direct-audio models through the shared coordinator.
- [x] 4.3 Persist each successfully completed assistant response atomically while
  retaining incremental TTS and the half-duplex state machine.
- [x] 4.4 Preserve temporary-media deletion without deleting canonical Chat
  media required for replay or later context reconstruction.
- [ ] 4.5 Present controlled recovery when a historical direct-audio turn lacks
  a completed transcript required to rehydrate textual context.

## 5. Cross-screen continuity

- [x] 5.1 Show Voice Chat turns in normal Chat through the same persisted
  conversation and continue normal Chat turns from Voice Chat.
- [x] 5.2 Reuse the same compatible native session across destination changes;
  omit screen origin from the compatibility key.
- [x] 5.3 Prevent duplicate submission or assistant persistence during rapid
  navigation, retries, lifecycle recreation, and stale callbacks.
- [ ] 5.4 Validate conversation switching, model switching, runtime recreation,
  process restart, cancellation, generation failure, TTS failure, and legacy
  data.
- [x] 5.5 Expose shared session creation, selection, rename, deletion, and bulk
  clearing in idle Voice Chat.
- [x] 5.6 Title a default conversation from its first completed Voice Chat
  transcript without racing asynchronous assistant persistence.
- [x] 5.7 Use Whisper automatic language detection so local transcription
  preserves the spoken language.

## 6. Quality and documentation

- [x] 6.1 Run targeted coordinator, persistence, runtime-reuse, Chat, and Voice
  Chat tests.
- [x] 6.2 Run unit tests, Spotless, Detekt, Android Lint, debug APK,
  Android-test compilation, strict OpenSpec validation, and `git diff --check`.
- [ ] 6.3 Validate multi-turn Chat → Voice Chat → Chat continuity and restart
  rehydration on a physical device with text-only and audio-capable models.
- [x] 6.4 Update README/project documentation with canonical persistence,
  native-session lifecycle, fallback behavior, and known reconstruction limits.
