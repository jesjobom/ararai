## Context

ArarAI already records app-owned PCM WAV audio at 16 kHz, submits structured
audio prompts directly to configured LiteRT-LM models, streams assistant text,
identifies response language locally, and plays completed responses with Android
TTS. Voice Chat must connect those pieces into an automatic loop, but pause
detection and incremental TTS have not been validated on physical devices.

Version 0 is deliberately an experimental, stateless slice. It exists to collect
evidence about voice-activity detection (VAD), Android capture preprocessing,
turn timing, local audio inference, segmented TTS, cancellation, and device
resource behavior. It does not establish a persistent conversation model.

Android exposes optional capture effects such as `NoiseSuppressor`,
`AcousticEchoCanceler`, and `AutomaticGainControl`, and capture sources such as
`MIC`, `VOICE_RECOGNITION`, and `VOICE_COMMUNICATION`. Availability and behavior
vary by device. Android does not provide one portable public VAD contract, so the
experiment must compare offline implementations rather than infer suitability
from documentation alone.

## Goals / Non-Goals

**Goals:**

- Provide a dedicated Voice Chat version 0 screen with one primary start/stop
  control and a deterministic half-duplex loop.
- Compare WebRTC VAD and Silero VAD behind one interface on the existing 16 kHz
  PCM stream.
- Compare supported Android audio sources and optional native noise suppression
  without coupling the product flow to device-specific availability.
- Submit only the current audio turn to the local model and speak streamed final
  answer text incrementally.
- Collect bounded local diagnostic measurements that make device tests
  comparable without retaining conversations or recordings.
- Guarantee cleanup under stop, error, navigation, and stale callbacks.

**Non-Goals:**

- Voice Chat sessions, history, persistence, automatic titles, or context from
  previous voice turns.
- Speech-to-text or a transcript of user audio.
- Full-duplex capture, user speech interruption (barge-in), or enabling acoustic
  echo cancellation in version 0.
- Choosing the final VAD, audio source, thresholds, or preprocessing pipeline
  before physical evidence is collected.
- Background or lock-screen recording, inference, or playback.
- Voice, pitch, or per-language settings beyond existing local language
  selection and fallback behavior.

## Decisions

### Make every turn independent and ephemeral

Each turn follows:

`listen → detect end of speech → finalize WAV → infer → speak → delete WAV → listen`

Generation receives the configured system prompt and current direct-audio prompt
only. It receives no earlier user or assistant turn. Version 0 does not create
Voice Chat sessions or messages and does not write assistant output to the Chat
database. The finalized WAV lives only until the exchange completes, fails, or
is canceled; all paths delete it through an idempotent owner.

This removes database migration and misleading continuity while the application
has no transcription with which to reconstruct user context. Existing text Chat
session behavior remains untouched. Sessions can return in a later change with
an explicit representation for user speech.

### Use an explicit half-duplex state machine

A coordinator owns `Idle`, `Listening`, `Processing`, `Speaking`, and recoverable
`Error` states. Microphone capture is active only in `Listening`. During model
processing and speech playback it remains stopped, preventing TTS output from
becoming input. Listening resumes only when both generation and the speech queue
finish and the user has not stopped the loop.

The central stop action works in every active state. It stops recording, cancels
generation, flushes TTS, deletes temporary audio, invalidates the operation ID,
and returns to `Idle`. Leaving the destination uses the same cleanup path.

Before enabling the central start action, the coordinator loads the selected
model and asks the shared runtime to prepare an audio workload. This matters for
LiteRT-LM because its workload-specific session would otherwise begin as
text-only and be replaced lazily by the first direct-audio request, incorrectly
placing model preparation inside the first turn's `Processing` time.

Full-duplex and barge-in are deferred, not rejected. `AcousticEchoCanceler` is a
relevant future candidate because it can attach to an `AudioRecord` session, but
version 0 cannot evaluate interruption while its recorder is intentionally off
during TTS.

### Compare VAD providers behind a frame-level contract

Adapt the current recorder to expose 16-bit mono PCM frames without changing WAV
output. A `VoiceActivityDetector` consumes frames and reports speech probability
or speech/non-speech state. Its coordinator applies the configured trailing-pause
duration only after speech has begun. Leading silence never submits an empty
turn, and unusable captures are discarded.

Version 0 provides two offline implementations:

- WebRTC VAD as the default lightweight baseline;
- Silero VAD as the noise-robust comparison using a bundled model and mobile
  inference runtime.

Both remain replaceable and are selected only through the experimental settings.
Their library artifacts, transitive native size, ABI compatibility, 16 KB page
alignment, licenses, and release provenance must be verified in the spike before
they become production dependencies. A handcrafted RMS threshold may be logged
as a signal/noise metric but is not the turn-decision implementation.

### Treat Android preprocessing as optional experimental input

The recorder is constructed with an explicit capture-source policy. Version 0
allows `MIC`, `VOICE_RECOGNITION`, and `VOICE_COMMUNICATION` when supported so
physical tests can compare how vendor processing affects VAD and model input.

`NoiseSuppressor` is attached to the recorder audio session only when the user
enables the experiment and the effect is available. Its actual created/enabled
state is reported; unsupported devices continue with raw capture. Automatic gain
control is not explicitly enabled because it can raise the ambient noise floor
and distort comparisons. Acoustic echo cancellation is not enabled in version 0
but the audio-effect boundary remains extensible for the future barge-in spike.

Changing VAD, source, or noise suppression while active applies only after the
current loop is stopped and restarted, keeping one capture comparable and avoiding
platform recorder reconfiguration races.

### Persist product settings but not diagnostic samples

Pause duration, minimum response words, and TTS speech rate are user-facing
Voice Chat settings. They persist locally and default to 1,500 ms, 25 words,
and 1.0x speed. Speech rate uses a 0.5x through 2.0x slider in 0.1x increments,
validated before it reaches the Android TTS engine. The persisted key differs
from the earlier five-level experiment and reads that legacy value once for a
compatible in-place upgrade.

VAD provider, capture source, and noise-suppression choice are explicitly marked
experimental. They may persist locally for repeatable device runs, but are not a
stable product contract and must report the effective rather than merely requested
configuration.

### Collect bounded local measurements without recording content

Each exchange produces an in-memory diagnostic record containing timestamps and
durations for speech start/end, selected pause, audio finalization, inference
start, first generation output, first TTS start, completion/cancellation, VAD
provider, effective capture source/effects, coarse noise-floor/level summaries,
and failure category. It contains no PCM, generated response text, prompt content,
or stable user identifier.

The screen does not expose raw diagnostic summaries in its product layout.
Measurements remain bounded in memory for deterministic tests and physical
debugging, are cleared when Voice Chat is destroyed, and are not uploaded or
written to the conversation database.

### Segment only final-answer text and serialize TTS

A pure segmenter consumes cumulative final-answer output, never reasoning. Once
the configured word count is reached, it waits for sentence-ending punctuation
or a line break instead of cutting an unfinished sentence at the latest word.
Short adjacent sentences may be grouped until the threshold is reached.
Generation completion flushes every non-blank residual segment even below the
threshold.

To remain below Android TTS input limits and avoid unnaturally long utterances,
an exceptionally long unfinished sentence is capped at 500 source characters. Only this safety
path may split without a sentence boundary; it prefers preceding clause
punctuation (`;`, `:`, or `,`) and then a word boundary. This trades some initial
speech latency for more natural prosody while keeping pathological model output
playable.

Segments are normalized for speech, prepared for language sequentially, and
played by one FIFO TTS queue. Stored text is irrelevant in version 0 because
responses are not persisted. Generation completion and speech completion remain
separate signals; listening resumes only after both complete. A preparation or
playback failure stops the exchange and exposes a recoverable error.

### Keep spoken text readable without dominating the voice screen

Voice Chat retains the current generated answer in memory for the lifetime of
the destination and presents it in a fixed two-line viewport. Native
`UtteranceProgressListener.onRangeStart` callbacks are translated from each
normalized speech segment back to its source-answer range. The active range is
highlighted and the viewport follows its line. If the selected TTS engine does
not provide range callbacks, the complete active source segment is highlighted
as a deterministic fallback.

Activating the viewport opens a scrollable overlay with the complete current
answer and the same active highlight. Closing the overlay does not interrupt
speech. When speech completes, the highlight is cleared but the two-line
viewport remains positioned at the final spoken lines until the next response
begins. The answer remains ephemeral: it is cleared on the next voice turn and
on foreground-owner disposal, and is never written to Chat persistence or
diagnostics.

## Risks / Trade-offs

- **VAD behavior varies with noise, language, distance, and device processing**
  → compare WebRTC and Silero with the same frame/event contract and capture
  repeatable device metrics before selecting one.
- **Silero may add disproportionate APK/native-runtime cost** → measure packaged
  size, startup, CPU, memory, ABI, and page alignment during the spike; retain
  WebRTC if accuracy improvement is not material.
- **Native noise suppression may be absent or vendor-specific** → treat it as an
  optional capability, record effective state, and keep raw capture functional.
- **`VOICE_COMMUNICATION` may enable opaque AGC/AEC processing** → compare it
  against `MIC` and `VOICE_RECOGNITION`; do not assume it is the best model input.
- **Half-duplex cannot test barge-in or AEC** → keep an extensible effect boundary
  and create a later full-duplex spike rather than drawing conclusions now.
- **Later segments containing names or technical terms may be misidentified** →
  identify the first eligible segment once, retain that language for the rest of
  the streamed response, and reset it before the next turn.
- **TTS range callbacks are engine-dependent and refer to normalized segment
  text** → map matching spoken text back into each source segment and fall back
  to highlighting the segment when a precise source range is unavailable.
- **Late callbacks can restart a stopped loop** → tag every run/turn with IDs and
  ignore callbacks after stop, restart, navigation, or owner destruction.
- **Temporary WAVs can leak on abnormal exits** → use one idempotent owner plus
  startup reconciliation for stale Voice Chat temporary files.
- **Diagnostics can accidentally become sensitive telemetry** → keep only
  bounded timings/configuration/numeric summaries in memory and never store
  audio or text.

## Migration Plan

1. Run a dependency spike for WebRTC/Silero artifacts, licenses, native size,
   ABI/page alignment, and frame-level operation before committing dependencies.
2. Add detector, preprocessing, diagnostics, segmenter, and coordinator contracts
   with deterministic tests.
3. Wire the version 0 UI and direct-audio loop without touching Chat persistence.
4. Compare ten-turn runs across supported arm64 devices and representative quiet,
   fan/TV, street, near/far speech, and language scenarios.
5. Record the selected production direction in a follow-up OpenSpec change; do
   not silently promote experimental controls into permanent settings.

Rollback removes the Voice Chat destination, preferences, bundled VAD assets,
and temporary directory. No database rollback is required and existing Chat data
is unaffected.

## Open Questions

- Which WebRTC and Silero Android artifacts pass provenance, maintenance, size,
  ABI, 16 KB page, and reproducibility checks?
- Which VAD/source/noise-suppression combination meets the physical-device false
  start, false end, missed end, latency, battery, and model-answer quality targets?
- What acceptance thresholds should replace the exploratory ten-turn baseline?
- In a future full-duplex change, can Android AEC prevent TTS feedback reliably
  enough for barge-in across the supported device matrix?
