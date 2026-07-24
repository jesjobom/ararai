## 1. Contract

- [x] 1.1 Define persisted transcript/status compatibility and two routing modes.
- [x] 1.2 Define on-device availability and presentation preference behavior.

## 2. Domain and persistence

- [x] 2.1 Extend audio message content with transcript and status.
- [x] 2.2 Persist and decode the new fields compatibly with legacy audio.
- [x] 2.3 Include only completed transcripts in bounded prompt context and titles.

## 3. Transcription and routing

- [x] 3.1 Add the replaceable transcriber boundary and Android on-device adapter.
- [x] 3.2 Run asynchronous enrichment for audio-capable models.
- [x] 3.3 Run blocking transcription-to-text for text-only models.
- [x] 3.4 Gate recording on either direct-audio or local-transcriber capability.

## 4. Presentation

- [x] 4.1 Persist the show-transcriptions preference enabled by default.
- [x] 4.2 Render pending, completed and failed transcription states.

## 5. Validation and documentation

- [x] 5.1 Add domain, persistence, routing, preference and adapter tests.
- [x] 5.2 Run formatting, static analysis, unit tests, lint/build and strict OpenSpec validation.
- [x] 5.3 Update product/privacy documentation and record physical-device limitations.

## 6. On-device diagnostics

- [x] 6.1 Model typed transcription failures and a sanitized diagnostic report.
- [x] 6.2 Capture recognizer lifecycle, WAV, pipe, device, locale and result metadata.
- [x] 6.3 Persist diagnostic fields compatibly and expose details/copy in Chat.
- [x] 6.4 Add tests and rerun the relevant quality gates.

## 7. Real-time PCM delivery

- [x] 7.1 Start pipe delivery only after the recognizer reports readiness.
- [x] 7.2 Pace PCM in 20 ms chunks and derive timeout from audio duration.
- [x] 7.3 Record expected/actual stream timing and speed ratio.
- [x] 7.4 Add timing calculation tests and rerun quality gates.

## 8. Segmented recognition and locale support

- [x] 8.1 Check installed/pending/supported on-device languages before listening.
- [x] 8.2 Enable segmented external-audio sessions and aggregate segment results.
- [x] 8.3 Extend sanitized diagnostics with locale support and segment metadata.
- [x] 8.4 Add support/aggregation tests and rerun all quality gates.

## 9. Adaptive performance and formatting

- [x] 9.1 Use fast segmented delivery as the default transcription attempt.
- [x] 9.2 Retry once with paced PCM only for empty/no-match/timeout outcomes.
- [x] 9.3 Request quality formatting and safely normalize segment whitespace.
- [x] 9.4 Preserve both attempt diagnostics when fallback also fails.
- [x] 9.5 Add intent/routing/normalization tests and rerun all quality gates.

## 10. Successful transcription diagnostics

- [x] 10.1 Return and persist a sanitized diagnostic report for successful transcription.
- [x] 10.2 Detect and persist potentially partial success outcomes.
- [x] 10.3 Expose copyable details and a partial-transcription warning in Chat.
- [x] 10.4 Add compatibility/assessment tests and rerun all quality gates.
