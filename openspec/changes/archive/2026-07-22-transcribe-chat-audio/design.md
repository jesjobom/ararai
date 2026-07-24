# Design: Local transcription enrichment for Chat audio

## Decisions

### Keep the transcript on the message content

`AudioPrompt` continues to describe media. `AudioPromptContent` owns the
derived transcript and its status because that representation survives media
policy changes and belongs to the conversational turn.

Legacy five-field audio payloads decode as `NotRequested`; new audio messages
begin as `Pending` and finish as `Completed` or `Failed`.

### Separate transcription from inference

`AudioTranscriber` exposes availability independently from LLM capabilities.
The initial Android adapter uses only `createOnDeviceSpeechRecognizer` and
feeds raw PCM from the app-owned WAV through `RecognizerIntent.EXTRA_AUDIO_SOURCE`.
It is available only on API 33+ when Android reports an on-device recognizer.

The adapter remains replaceable by a future app-managed ASR runtime.

Both Android delivery modes wait for `onReadyForSpeech`. The paced fallback
writes PCM to the recognizer pipe at its recorded sample rate in 20 ms chunks,
emulating a real-time microphone source for recognition services that do not
consume `EXTRA_AUDIO_SOURCE` as an arbitrarily fast file stream. Each mode uses
a timeout appropriate to its delivery behavior.

For performance, segmented recognition first delivers PCM without artificial
pacing. Only empty/no-match/timeout outcomes retry once with real-time 20 ms
pacing. Non-recognition failures are not retried. If both attempts fail, the
persisted sanitized diagnostic includes both attempt reports.

The adapter enables `EXTRA_SEGMENTED_SESSION` for the external audio source,
collects `onSegmentResults`, and completes on `onEndOfSegmentedSession`.
Standard final results remain a defensive fallback for recognition services
that do not honor segmented callbacks.

The intent requests `FORMATTING_OPTIMIZE_QUALITY`. The returned text receives
only whitespace normalization; the app does not invent punctuation or rewrite
recognized words when the platform service ignores formatting.

Before listening, `checkRecognitionSupport` evaluates the requested locale.
An explicitly unsupported, downloadable-only or pending locale produces a
typed unavailable failure with diagnostics. A service that cannot perform the
support check is recorded but still allowed to attempt recognition for device
compatibility; no model download is triggered automatically.

### Route by selected-model capability

- Audio-capable LLM: persist pending audio, start transcription independently,
  and submit the original audio without waiting.
- Text-only LLM: persist pending audio, await transcription, then submit the
  completed transcript as the current text prompt. Failure blocks generation.

Completed transcripts participate in bounded context. Pending, failed and
legacy-untranscribed audio turns contribute no fabricated user text.

### Visibility is presentation-only

`showAudioTranscriptions` is a persisted Chat preference enabled by default.
Disabling it hides completed transcript text but does not disable recognition,
persistence, titles or context use.

## Failure and lifecycle

- Direct-audio generation may complete when asynchronous transcription fails.
- Text-only generation does not start when synchronous transcription fails.
- The WAV remains persisted and replayable after success or failure.
- Recognition cancellation closes its pipe and destroys the recognizer.
- Actual recognizer availability, language packs and accuracy require device
  validation.

### Preserve sanitized failure diagnostics

Transcription failures retain a stable failure kind, a user-facing summary and
an optional sanitized diagnostic report on the audio message. The report may
contain recognizer callback timing, Android/device and locale information, WAV
metadata, pipe byte counts, recognizer error codes and result counts. It must
not contain audio bytes, recognized text or app secrets.

The report also records expected audio duration, actual stream duration and
their speed ratio so device-specific source-consumption behavior can be
verified without logcat.

Successful transcription retains the same sanitized execution report. Chat
exposes it through the details action even when transcript text is hidden. A
success is marked as potentially partial when it completes through a fallback
callback instead of the segmented-session end, before pipe completion, or
after sending fewer PCM bytes than the WAV contains. This warning does not
rewrite or discard recognized text; it makes device-specific truncation
observable before callback policy is changed.

The message keeps its concise failure summary. A separate details action shows
and copies the report so physical-device failures can be diagnosed without
logcat. Legacy and already persisted messages remain valid when no diagnostic
fields exist.
