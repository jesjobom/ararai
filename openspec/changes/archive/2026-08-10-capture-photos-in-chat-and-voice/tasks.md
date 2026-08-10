## 1. Structured Turn Model And Persistence

- [x] 1.1 Add backward-compatible image attachments to audio prompt content and
  its serialized representation, decoding existing audio rows with no image
  field as an empty collection.
- [x] 1.2 Update message rendering, media-reference extraction, conversation
  persistence, deletion, and draft cleanup to treat audio and its image as one
  user turn.
- [x] 1.3 Add focused serialization, rehydration, reference-query, replacement,
  and orphan-cleanup tests for audio turns with and without images.

## 2. Camera Capture Foundation

- [x] 2.1 Implement injectable camera preview/capture and permission boundaries
  with operation identifiers that reject stale callbacks.
- [x] 2.2 Feed bounded camera output through the existing image normalization and
  app-owned media pipeline without retaining partial source files.
- [x] 2.3 Add deterministic tests for permission denial/revocation, cancellation,
  malformed or failed capture, normalization limits, and lifecycle cleanup.
- [x] 2.4 If implementation requires CameraX, add the locked dependency and
  reviewed attribution/supply-chain metadata before integrating it.

## 3. Normal Chat Image Sources

- [x] 3.1 Replace the direct gallery action with a localized source chooser that
  offers `Take photo` and `Choose from gallery` for image-capable models.
- [x] 3.2 Add the in-app capture surface and attach a successful normalized photo
  through the existing Chat draft preview, send, persistence, and removal flow.
- [x] 3.3 Test gallery compatibility, successful camera capture, unchanged drafts
  after denial/cancellation/failure, and capability-gated visibility.
- [x] 3.4 Make historical text and audio image thumbnails open a larger fitted
  preview that can be dismissed without changing conversation position.

## 4. Combined Runtime And Transcription Routing

- [x] 4.1 Extend request validation and LiteRT-LM projection to send current audio
  and image parts as one ordered user turn only for validated combined support.
- [x] 4.2 Preserve the current image when local transcription converts an audio
  turn into effective text content for a text-and-image model.
- [x] 4.3 Update context budgeting and runtime conversation-reuse compatibility
  so the combined turn is neither separated nor silently downgraded.
- [x] 4.4 Add tests for direct audio-plus-image, transcript-plus-image, unsupported
  combinations, runtime failure, cancellation, and retry behavior.

## 5. Voice Chat Photo Capture

- [x] 5.1 Add localized, capability-gated camera controls and a pending-photo
  preview/removal action to Voice Chat while it is listening.
- [x] 5.2 Present in-app camera capture without stopping/restarting microphone
  recording or the active Voice Chat run; keep VAD active and reset only its
  trailing-silence window when the camera opens, capture completes, or the flow
  closes.
- [x] 5.3 Make the active run own one pending normalized photo, atomically replace
  it after later successful capture, retain it across silence/unusable speech,
  and consume it with the next valid audio turn.
- [x] 5.4 Persist and submit the photo with the captured audio or local transcript,
  and clear unsubmitted photos on stop, navigation, disposal, or incompatible
  model change.
- [x] 5.5 Add Voice Chat coordinator and UI tests proving continuous audio
  retention, trailing-silence restart, automatic capture on a valid pause,
  manual-capture priority, audio-only fallback, per-turn camera closure, correct
  association, stale-callback rejection, bounded capture, and cleanup.
- [x] 5.6 Close the camera after automatic or manual capture, consume its photo
  with only the current valid turn, and require explicit reopening in the next
  listening turn.

## 6. Documentation And Validation

- [x] 6.1 Update project context, user documentation, localized strings, privacy
  disclosures, and device-validation instructions for camera capture.
- [x] 6.2 Run targeted unit and Robolectric tests while implementing, then run
  `scripts/quality-gate.sh` and `openspec validate --all --strict`.
- [x] 6.3 On representative physical devices, verify camera permission flows,
  uninterrupted microphone capture, suspended/resumed silence detection, memory
  and thermal behavior, lifecycle cleanup, and real direct-audio-plus-image
  inference; record model, build, and device evidence without inferring it from
  automated tests.
  - Validated by JJ on a physical device on 2026-08-10 using debug APK SHA-256
    `b9b3f28c6f6a8956fad38cf5f804dcc142954b580dba7614fb5913d6d1c26ec3`.
