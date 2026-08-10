## Context

See `proposal.md` for motivation. Normal Chat currently imports gallery content
through a bounded normalizer into app-owned JPEG storage. `MessageContent`
represents text with images or a standalone audio prompt, while Voice Chat owns a
continuous PCM/VAD loop and persists each completed audio turn into the shared
conversation before generation. This change crosses UI, Android camera and
permission handling, message serialization, transcription routing, runtime
projection, and media cleanup.

The defining constraint is that Voice Chat microphone recording must continue
while a photo is taken. Handing capture to an external camera activity would
move the app through lifecycle transitions that can suspend or dispose its
foreground voice owner, so it cannot provide that guarantee.

## Goals / Non-Goals

**Goals:**

- Reuse one bounded image ingestion pipeline for gallery selections and camera
  output.
- Keep Voice Chat audio capture and VAD alive throughout photo capture while
  restarting only the trailing-silence window at intentional camera boundaries.
- Support automatic capture at the next valid pause while retaining an explicit
  manual capture that takes precedence for the current turn.
- Preserve audio, transcript state, and photo as one canonical user turn.
- Keep permissions, failures, cancellation, and media ownership deterministic
  and testable.

**Non-Goals:**

- Recording video, attaching gallery images from Voice Chat, or supporting more
  than one pending Voice Chat photo per turn.
- Full camera controls such as zoom, flash modes, lens selection, filters, or
  editing.
- Re-sending historical photos with later audio turns beyond existing context
  projection policy.
- Supporting simultaneous media combinations that the selected model/runtime
  does not declare and validate.

## Decisions

### Use an application-owned in-app camera capture boundary

Add a replaceable camera controller that owns preview, capture, permission state,
and lifecycle binding. Normal Chat opens it after the image-source chooser;
Voice Chat presents it as an overlay whose close path returns to the still-active
listening screen. The controller writes a bounded temporary capture and passes it
through the existing image normalizer before it becomes a draft attachment.

This keeps media private and provides a fakeable boundary for JVM/Robolectric
tests. An external `ACTION_IMAGE_CAPTURE` or activity-result camera contract was
rejected because activity pause/recreation conflicts with uninterrupted voice
capture and commonly returns only a thumbnail unless URI ownership is managed
separately.

Use the platform camera stack behind the boundary when it can meet preview and
capture lifecycle requirements without a new dependency. If implementation
inspection proves that unsafe or disproportionately complex, CameraX is the
preferred dependency because it standardizes lifecycle-aware preview/capture;
the dependency decision must be recorded with resolved-version and supply-chain
validation before implementation.

### Extend audio prompt content with image attachments

Evolve `AudioPromptContent` to contain its audio prompt, transcript metadata, and
a bounded list of image attachments. The first UI slice permits zero or one
image, but the domain collection matches text prompt image handling and avoids a
second special-purpose message type. Typed text remains mutually exclusive with
audio.

Serialization must decode existing audio rows with a missing image field as an
empty list, avoiding a destructive SQLite migration. Media-reference extraction
must include both the WAV and every image URI so deletion remains reference-safe.

An alternative that stores the photo as a separate adjacent message was rejected
because persistence, context budgeting, retries, and runtime conversation reuse
could separate the visual input from the audio turn it qualifies.

### Route the combined turn according to model capabilities

For models that accept direct audio and image input together, runtime projection
creates one ordered user content value containing the current image and audio.
The engine validates the combined request before native inference.

For text/image models using local transcription, transcription produces the text
part while retaining the current image on the effective content. The canonical
stored user message still keeps the original audio, transcript state, and image;
only runtime projection changes. If neither combined route is supported, the
Voice Chat camera action is unavailable and defensive validation rejects a
manually constructed request.

### Treat the Voice Chat photo as run-scoped pending media

The Voice Chat coordinator owns at most one normalized pending photo tagged with
the active run identifier. A successful later capture atomically replaces it;
cancellation or failure leaves the prior photo unchanged. The next valid
captured audio turn consumes the pending photo only after media persistence is
ready. Silence and rejected speech do not consume it.

Stop, navigation, owner disposal, model incompatibility, and stale callbacks
clear unsubmitted media through the existing draft/media cleanup boundary.
Operation identifiers prevent late camera callbacks from attaching a photo to a
new run.

### Keep VAD active and reset only the trailing-silence window

Opening the Voice Chat camera keeps `AudioRecord` and normal VAD processing
active. When the user requests the camera, when its preview becomes ready, when
a manual photo completes, and when the flow closes without a photo, the active
capture gate resets only its consecutive-speech and trailing-silence counters.
It preserves already buffered or committed audio, confirmed speech, and total
voiced duration. This gives the user a complete pause interval to frame or
describe the image without discarding earlier speech.

If a valid trailing pause completes while the camera is open and no manual photo
is pending, Voice Chat finalizes the audio capture, requests one automatic frame,
then submits both inputs after bounded image normalization. If a manual capture
is already complete or in flight, that operation has priority and automatic
capture must not produce a competing frame. A bounded automatic-capture failure
falls back to the completed audio turn rather than losing the user's speech.

After either path submits the turn, the camera is closed and the next listening
turn starts without reopening it. The user must explicitly request visual
context for each turn. Pausing the recorder was rejected because it would
discard prompt audio; suspending VAD for the entire camera flow was rejected
because it prevents the agreed pause-driven automatic capture.

### Keep camera errors independent from the voice loop

Camera permission denial, cancellation, and capture/import failure update a
recoverable camera-specific UI state. They do not stop the voice loop. They
close the camera flow, reset the trailing-silence window when capture remains
active, and allow a completed deferred audio turn to continue without an image.
Microphone failures retain the existing central stop/error behavior.

## Risks / Trade-offs

- [Some devices cannot keep camera and microphone active reliably] → Keep the
  boundaries independent, recover camera failure without stopping audio, and
  require physical-device validation across representative vendors.
- [Camera preview/compression increases memory, CPU, and thermal load during
  local audio capture] → Bound capture size before decode, reuse existing image
  limits, release preview resources immediately after capture/close, and record
  device evidence under load.
- [A lifecycle callback may dispose the voice owner] → Use an in-destination
  overlay, retain run IDs across camera callbacks, and add tests that camera open,
  close, denial, and failure never invoke Voice Chat leave/stop paths.
- [A completed audio turn may wait indefinitely for a camera callback] → Tag
  automatic and manual operations, reject stale callbacks, and use a bounded
  fallback that submits the audio without an image.
- [Schema evolution could orphan one of two media files] → Decode absent image
  lists compatibly, extract all media references centrally, and test replacement,
  persistence failure, conversation deletion, and process rehydration.
- [Runtime may advertise individual audio/image support but reject their
  combination] → Add combined-request validation and physical inference tests;
  do not infer combined support from names or individual UI affordances alone.

## Migration Plan

1. Add backward-compatible image attachments to serialized audio content and
   update media reference queries/readers before producing the new shape.
2. Add camera and permission boundaries plus normal Chat source selection.
3. Add Voice Chat pending-photo state and uninterrupted capture overlay.
4. Route and persist combined direct-audio/image and transcribed-text/image
   turns, then enable the action only for validated capability combinations.
5. Run the complete automated quality gate and strict OpenSpec validation;
   separately record physical-device camera/microphone and real-model evidence.

Rollback disables the camera affordances and stops producing audio content with
images. Existing upgraded builds continue to decode older rows, while versions
predating this schema must not be used as a data-compatible downgrade because
they cannot preserve the added image field.
